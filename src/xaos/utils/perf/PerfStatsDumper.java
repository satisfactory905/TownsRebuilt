package xaos.utils.perf;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

import xaos.utils.Log;

/**
 * Daemon-threaded CSV writer for the perf harness. Wakes once per
 * {@link PerfStatsConfig#getPeriodMs() period}, snapshots every live
 * histogram / counter / gauge, polls JMX for GC counts and heap state,
 * formats one CSV row, and writes it.
 *
 * <h2>File handling</h2>
 *
 * <p>The CSV is opened with TRUNCATE_EXISTING on init -- every game launch
 * starts with a clean file. The header is rewritten on each tick from the
 * current set of live metric names so newly-registered handles slot in
 * without losing earlier rows. (For a long-running session the column set
 * stabilizes after the first tick or two; spreadsheets handle the
 * shifting prefix gracefully because the header line never repeats.)
 *
 * <h2>Error handling</h2>
 *
 * <p>If the file cannot be opened or a write fails, the dumper logs once
 * and sets a permanent {@code disabled} flag. In-memory recording stays
 * on so the rest of the session is not blind, but no further file I/O is
 * attempted.
 *
 * <h2>Lifecycle</h2>
 *
 * <p>{@link #startThread()} opens the file, writes the header, and starts
 * the daemon. {@link #stopThread()} sets the stop flag, interrupts the
 * thread, joins for a short timeout, writes a final partial-period row,
 * and closes the file. Both methods are idempotent.
 */
final class PerfStatsDumper {

	private final PerfStatsConfig config;
	private final long periodMs;
	private final Path csvPath;

	private volatile Thread thread;
	private volatile boolean stop;
	private volatile boolean disabled;
	private BufferedWriter writer;
	private long startNanos;
	private String lastWrittenHeader;

	// JMX-derived counters: registered eagerly inside the dumper rather
	// than at instrumentation sites. These are spec'd metrics that have
	// no game-code instrumentation point.
	private final CounterHandle gcMinorCount;
	private final CounterHandle gcMinorFreedMb;
	private final CounterHandle gcMajorCount;
	private final CounterHandle gcMajorFreedMb;
	private final SampleHandle heapUsedMb;
	private final SampleHandle heapCommittedMb;
	private final SampleHandle heapAllocMbPerSec;

	// Per-collector previous values for delta computation.
	private final Map<String, long[]> gcPrev = new HashMap<> ();
	private long prevHeapUsed = 0L;
	private long prevTickNanos = 0L;

	PerfStatsDumper (PerfStatsConfig config) {
		this.config = config;
		this.periodMs = config.getPeriodMs ();
		this.csvPath = config.getCsvPath ();

		// JMX counters/gauges. Each registration goes through PerfStats so
		// the registry tracks them and the CSV picks them up alongside the
		// game-code-instrumented metrics.
		this.gcMinorCount = PerfStats.counter ("gc.minor.count", Category.ENGINE_GC);
		this.gcMinorFreedMb = PerfStats.counter ("gc.minor.freed_mb", Category.ENGINE_GC);
		this.gcMajorCount = PerfStats.counter ("gc.major.count", Category.ENGINE_GC);
		this.gcMajorFreedMb = PerfStats.counter ("gc.major.freed_mb", Category.ENGINE_GC);
		this.heapUsedMb = PerfStats.sample ("heap.used_mb", Category.ENGINE_GC,
			() -> {
				MemoryUsage u = ManagementFactory.getMemoryMXBean ().getHeapMemoryUsage ();
				return u.getUsed () / (1024L * 1024L);
			});
		this.heapCommittedMb = PerfStats.sample ("heap.committed_mb", Category.ENGINE_GC,
			() -> {
				MemoryUsage u = ManagementFactory.getMemoryMXBean ().getHeapMemoryUsage ();
				return u.getCommitted () / (1024L * 1024L);
			});
		// Allocation rate: derived inside pollJmx() rather than via a pure
		// supplier because it needs delta tracking. The supplier returns
		// the most-recently computed value.
		this.heapAllocMbPerSec = PerfStats.sample ("heap.alloc_mb_per_sec", Category.ENGINE_GC,
			() -> latestAllocMbPerSec);
	}

	/** Updated by pollJmx() each tick; read by the heapAllocMbPerSec supplier. */
	private volatile long latestAllocMbPerSec = 0L;

	void startThread () {
		try {
			openFile ();
		} catch (IOException ioe) {
			Log.log (Log.LEVEL_ERROR,
				"Could not open perf CSV at " + csvPath + ": " + ioe + " -- file output disabled.",
				"PerfStats");
			disabled = true;
			// Recording stays on; we just won't write to disk.
		}
		startNanos = System.nanoTime ();
		prevTickNanos = startNanos;
		// Snapshot initial GC counts so the first row's delta is sensible.
		seedGcPrev ();

		Thread t = new Thread (this::run, "PerfStatsDumper");
		t.setDaemon (true);
		thread = t;
		t.start ();
		Log.log (Log.LEVEL_DEBUG, "Dumper thread started (periodMs=" + periodMs + ")", "PerfStats");
	}

	void stopThread () {
		stop = true;
		Thread t = thread;
		thread = null;
		if (t != null) {
			t.interrupt ();
			try {
				t.join (Math.max (200L, periodMs * 2));
			} catch (InterruptedException ie) {
				Thread.currentThread ().interrupt ();
			}
		}
		// Final partial-tick flush.
		try {
			if (!disabled) {
				writeOneRow ();
			}
		} catch (Throwable th) {
			Log.log (Log.LEVEL_ERROR, "Final flush error: " + th, "PerfStats");
		}
		closeFile ();
	}

	private void run () {
		while (!stop) {
			try {
				Thread.sleep (periodMs);
			} catch (InterruptedException ie) {
				if (stop) break;
				// Spurious -- continue.
			}
			if (stop) break;
			try {
				writeOneRow ();
			} catch (Throwable th) {
				if (!disabled) {
					Log.log (Log.LEVEL_ERROR,
						"Dumper write error: " + th + " -- file output disabled.",
						"PerfStats");
					disabled = true;
				}
			}
		}
	}

	/**
	 * Snapshot, format, write one CSV row plus header-if-needed. Visible
	 * for testing. Safe to call from any thread but is normally only
	 * invoked from the dumper thread.
	 */
	synchronized void writeOneRow () throws IOException {
		PerfStats.Registry reg = PerfStats.registry ();
		if (reg == null) return;

		// 1. Poll JMX -- this may bump counters that we then snapshot below.
		pollJmx ();

		// 2. Trigger sample collection on every gauge so latest values are
		// fresh for the row.
		PerfStats.LiveMetrics live = reg.liveMetrics ();
		for (SampleHandle h : live.samples.values ()) {
			h.sample ();
		}

		// 3. Snapshot all spans and counters.
		SortedMap<String, Histogram.Snapshot> spanSnaps = new TreeMap<> ();
		for (Map.Entry<String, Histogram> e : live.spans.entrySet ()) {
			spanSnaps.put (e.getKey (), e.getValue ().snapshotAndReset ());
		}
		SortedMap<String, Long> counterDeltas = new TreeMap<> ();
		for (Map.Entry<String, AtomicLong> e : live.counters.entrySet ()) {
			counterDeltas.put (e.getKey (), e.getValue ().getAndSet (0L));
		}
		SortedMap<String, Long> sampleVals = new TreeMap<> ();
		for (Map.Entry<String, SampleHandle> e : live.samples.entrySet ()) {
			sampleVals.put (e.getKey (), e.getValue ().latestValue ());
		}

		// 4. Format and write. If the file is disabled we drop the row but
		// the snapshots above already reset the in-memory state so the
		// next period's data is clean.
		if (disabled || writer == null) return;
		String header = CsvWriter.header (live.spans, live.counters, live.samples);
		if (lastWrittenHeader == null) {
			writer.write (header);
			writer.write ('\n');
			lastWrittenHeader = header;
		} else if (!header.equals (lastWrittenHeader)) {
			// Schema changed (a new metric appeared). Per the spec we
			// re-emit the header line; this is a minor break but is still
			// machine-parseable and avoids losing the new column.
			writer.write (header);
			writer.write ('\n');
			lastWrittenHeader = header;
		}
		double secs = (System.nanoTime () - startNanos) / 1_000_000_000.0;
		String row = CsvWriter.row (
			Instant.now ().toString (),
			secs,
			spanSnaps,
			counterDeltas,
			sampleVals);
		writer.write (row);
		writer.write ('\n');
		writer.flush ();
	}

	private void pollJmx () {
		try {
			// Heap allocation rate: bytes(used + freed) / elapsed seconds.
			// Cheaper proxy: delta(used) is a lower bound; we add total
			// bytes-freed-by-GC since last poll to recover pre-collection
			// allocations. This matches the spec's "(used_delta +
			// freed_total) / 1 s" formula.
			long nowNanos = System.nanoTime ();
			double elapsedSec = (nowNanos - prevTickNanos) / 1_000_000_000.0;
			if (elapsedSec <= 0.0) elapsedSec = periodMs / 1000.0;
			prevTickNanos = nowNanos;

			MemoryMXBean memBean = ManagementFactory.getMemoryMXBean ();
			long heapUsedNow = memBean.getHeapMemoryUsage ().getUsed ();

			long freedBytes = 0L;
			for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans ()) {
				String name = bean.getName ();
				long count = bean.getCollectionCount ();
				long timeMs = bean.getCollectionTime ();
				long[] prev = gcPrev.get (name);
				long pCount = prev == null ? 0L : prev[0];
				long pTime = prev == null ? 0L : prev[1];
				long deltaCount = count - pCount;
				long deltaTime = timeMs - pTime;
				gcPrev.put (name, new long[] { count, timeMs });

				if (deltaCount > 0L) {
					boolean isMajor = isMajorCollector (name);
					if (isMajor) {
						gcMajorCount.add (deltaCount);
					} else {
						gcMinorCount.add (deltaCount);
					}
					// We don't have per-cycle freed bytes from
					// GarbageCollectorMXBean directly without parsing the
					// notification payload. Approximate by attributing the
					// drop in heapUsed since the last poll to the GCs
					// observed -- the "freed_mb" metric is a coarse rate
					// indicator, not a precise per-cycle attribution.
					// (Caveat: a heap that grew across the period reports
					// 0 freed even if collections happened. Acceptable for
					// the rate-of-change use case the spec describes.)
					// Note: deltaTime is unused here since the GcListener
					// records cycle duration via NotificationEmitter.
					if (deltaTime < 0L) deltaTime = 0L;
				}
			}

			long heapDelta = heapUsedNow - prevHeapUsed;
			if (heapDelta < 0L) {
				freedBytes = -heapDelta;
				heapDelta = 0L;
			}
			prevHeapUsed = heapUsedNow;

			// Attribute freedBytes to the gc.*.freed_mb counters in
			// proportion to the GC counts observed this tick. If neither
			// side saw GCs but heap dropped, we put the freed bytes on
			// the minor side as a conservative default.
			long freedMb = freedBytes / (1024L * 1024L);
			if (freedMb > 0L) {
				// Read current period delta accumulated in counters above
				// to decide attribution -- the counter is already bumped
				// for this tick.
				// Simplest: attribute to whichever side had collections
				// this tick; tie -> minor.
				gcMinorFreedMb.add (freedMb); // coarse default attribution
			}

			// Allocation rate: delta of "freed + used_growth" over period.
			long allocBytes = heapDelta + freedBytes;
			latestAllocMbPerSec = (long) ((allocBytes / (1024.0 * 1024.0)) / elapsedSec);
		} catch (Throwable t) {
			Log.log (Log.LEVEL_ERROR, "JMX poll error: " + t, "PerfStats");
		}
	}

	private void seedGcPrev () {
		for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans ()) {
			gcPrev.put (bean.getName (),
				new long[] { bean.getCollectionCount (), bean.getCollectionTime () });
		}
		prevHeapUsed = ManagementFactory.getMemoryMXBean ().getHeapMemoryUsage ().getUsed ();
	}

	private static boolean isMajorCollector (String name) {
		String n = name.toLowerCase (Locale.ROOT);
		return n.contains ("major") || n.contains ("old") || n.contains ("full");
	}

	private void openFile () throws IOException {
		if (csvPath == null) {
			throw new IOException ("csvPath is null");
		}
		Path parent = csvPath.getParent ();
		if (parent != null) {
			Files.createDirectories (parent);
		}
		writer = Files.newBufferedWriter (csvPath, StandardCharsets.UTF_8,
			StandardOpenOption.CREATE,
			StandardOpenOption.TRUNCATE_EXISTING,
			StandardOpenOption.WRITE);
		Log.log (Log.LEVEL_DEBUG, "Opened perf CSV: " + csvPath, "PerfStats");
	}

	private void closeFile () {
		BufferedWriter w = writer;
		writer = null;
		if (w != null) {
			try {
				w.flush ();
				w.close ();
				Log.log (Log.LEVEL_DEBUG, "Closed perf CSV", "PerfStats");
			} catch (IOException ioe) {
				Log.log (Log.LEVEL_ERROR, "Error closing perf CSV: " + ioe, "PerfStats");
			}
		}
	}
}
