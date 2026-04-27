package xaos.utils.perf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import xaos.utils.Log;

/**
 * Static facade over the perf-telemetry harness.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>Game.{@code <init>} calls {@link #init(PerfStatsConfig)} once.
 *   <li>Recording sites declare {@link SpanHandle}, {@link CounterHandle},
 *       and {@link SampleHandle} fields via the static factories on this
 *       class. Handles can be declared in classes loaded *before*
 *       {@link #init} runs -- they bind lazily.
 *   <li>The daemon dumper writes one CSV row per
 *       {@link PerfStatsConfig#getPeriodMs() period}, plus a final partial
 *       row on shutdown.
 *   <li>{@link #shutdown()} flushes and tears everything down.
 * </ol>
 *
 * <h2>Thread safety</h2>
 *
 * <p>All public methods are safe to call from any thread. Init and shutdown
 * synchronize on the class itself; recording paths use volatile + atomics
 * exclusively.
 */
public final class PerfStats {

	/**
	 * Sentinel histogram returned to a {@link SpanHandle} when the metric
	 * is permanently disabled. Reference equality is used to short-circuit
	 * the slow path in {@link SpanHandle#start()}.
	 *
	 * <p>This object is never written to or read from for its content; it
	 * is purely a tombstone. We allocate one here so the SpanHandle can
	 * detect "disabled" without an extra boolean field.
	 */
	static final Histogram DISABLED_HISTOGRAM = new Histogram ();

	/**
	 * Sentinel counter for {@link CounterHandle}. Same idea as
	 * {@link #DISABLED_HISTOGRAM}.
	 */
	static final AtomicLong DISABLED_COUNTER = new AtomicLong (0L);

	/**
	 * Catalog of every span and counter that v1 instrumentation declares.
	 * Walked once in {@link #init(PerfStatsConfig)} so that the registry
	 * pre-creates every Histogram / AtomicLong in enabled categories before
	 * the dumper writes its first CSV row -- otherwise the schema grows row
	 * by row as classes load and handles fire for the first time, and the
	 * resulting CSV has multiple header lines that confuse spreadsheet
	 * imports.
	 *
	 * <p>Samples (gauges) don't appear here -- {@link #sample(String,
	 * Category, java.util.function.LongSupplier)} already pre-registers
	 * eagerly because the supplier is provided at declaration time.
	 *
	 * <p>Adding a new instrumented metric: append a row to this list. The
	 * unit tests verify catalog-vs-spec coverage. Forgetting to add a row
	 * is *not* a crash -- the metric still works via lazy registration --
	 * but late-registered metrics trigger a header re-emit mid-CSV which
	 * is the exact thing this catalog exists to prevent.
	 */
	static final List<MetricSpec> KNOWN_METRICS = List.of (
		// RENDERING_FRAME
		MetricSpec.span    ("frame.total",                                Category.RENDERING_FRAME),
		MetricSpec.span    ("frame.render.world",                         Category.RENDERING_FRAME),
		MetricSpec.span    ("frame.render.mouse",                         Category.RENDERING_FRAME),
		MetricSpec.span    ("frame.render.ui",                            Category.RENDERING_FRAME),
		MetricSpec.span    ("frame.render.minimap",                       Category.RENDERING_FRAME),
		MetricSpec.span    ("frame.swap",                                 Category.RENDERING_FRAME),
		// RENDERING_GL
		MetricSpec.counter ("gl.bind_texture",                            Category.RENDERING_GL),
		MetricSpec.counter ("gl.draw_texture",                            Category.RENDERING_GL),
		MetricSpec.counter ("gl.begin_quads",                             Category.RENDERING_GL),
		MetricSpec.counter ("gl.clear",                                   Category.RENDERING_GL),
		// ENGINE_SIM
		MetricSpec.span    ("sim.tick",                                   Category.ENGINE_SIM),
		MetricSpec.span    ("sim.tasks",                                  Category.ENGINE_SIM),
		MetricSpec.counter ("sim.entities_iterated",                      Category.ENGINE_SIM),
		// ENGINE_PATH
		MetricSpec.span    ("path.search",                                Category.ENGINE_PATH),
		MetricSpec.counter ("path.search.count",                          Category.ENGINE_PATH),
		MetricSpec.counter ("path.queue.depth",                           Category.ENGINE_PATH),
		// ENGINE_HAPPINESS
		MetricSpec.span    ("happiness.cache.build.full",                 Category.ENGINE_HAPPINESS),
		MetricSpec.counter ("happiness.cache.build.tile",                 Category.ENGINE_HAPPINESS),
		MetricSpec.counter ("happiness.cache.invalidate.item_added",      Category.ENGINE_HAPPINESS),
		MetricSpec.counter ("happiness.cache.invalidate.item_removed",    Category.ENGINE_HAPPINESS),
		MetricSpec.counter ("happiness.cache.invalidate.wall_changed",    Category.ENGINE_HAPPINESS),
		MetricSpec.counter ("happiness.cache.invalidate.mined",           Category.ENGINE_HAPPINESS),
		MetricSpec.counter ("happiness.cache.invalidate.discovered",      Category.ENGINE_HAPPINESS),
		MetricSpec.counter ("happiness.cache.tiles_dirtied",              Category.ENGINE_HAPPINESS),
		MetricSpec.span    ("happiness.recalc.citizen",                   Category.ENGINE_HAPPINESS),
		// ENGINE_GC
		MetricSpec.span    ("gc.minor.duration",                          Category.ENGINE_GC),
		MetricSpec.counter ("gc.minor.count",                             Category.ENGINE_GC),
		MetricSpec.counter ("gc.minor.freed_mb",                          Category.ENGINE_GC),
		MetricSpec.span    ("gc.major.duration",                          Category.ENGINE_GC),
		MetricSpec.counter ("gc.major.count",                             Category.ENGINE_GC),
		MetricSpec.counter ("gc.major.freed_mb",                          Category.ENGINE_GC)
	);

	/**
	 * Spec for one entry in {@link #KNOWN_METRICS}: name + category + which
	 * registry slot it occupies (span vs counter).
	 */
	static final class MetricSpec {

		enum Type { SPAN, COUNTER }

		final String name;
		final Category category;
		final Type type;

		private MetricSpec (String name, Category category, Type type) {
			this.name = name;
			this.category = category;
			this.type = type;
		}

		static MetricSpec span (String name, Category category) {
			return new MetricSpec (name, category, Type.SPAN);
		}

		static MetricSpec counter (String name, Category category) {
			return new MetricSpec (name, category, Type.COUNTER);
		}
	}

	/**
	 * The single live registry. Volatile so handles see init/shutdown
	 * transitions promptly. {@code null} before init / after shutdown.
	 */
	private static volatile Registry registry;

	/**
	 * Tracking lists for handle invalidation across init/shutdown. We keep
	 * weak ownership intentionally light -- this is a debug feature, leaks
	 * are bounded by the number of declared metrics.
	 */
	private static final List<SpanHandle> spanHandles = Collections.synchronizedList (new ArrayList<> ());
	private static final List<CounterHandle> counterHandles = Collections.synchronizedList (new ArrayList<> ());
	private static final List<SampleHandle> sampleHandles = Collections.synchronizedList (new ArrayList<> ());

	private static volatile PerfStatsDumper dumper;
	private static volatile GcListener gcListener;

	private PerfStats () {
		// utility class -- no instances
	}

	/**
	 * Initializes the harness with the given config. Idempotent: a second
	 * call after the first short-circuits the existing dumper and re-binds
	 * everything against the new config. Failures are logged and degrade
	 * to "all handles disabled" for the rest of the session -- the game
	 * never crashes because of perf-init.
	 */
	public static synchronized void init (PerfStatsConfig config) {
		try {
			if (registry != null) {
				// Re-init: clean up previous state first.
				shutdownInternal ();
			}
			Registry r = new Registry (config);
			registry = r;
			invalidateAllHandles ();

			// Pre-register every known metric in enabled categories so the
			// dumper's first CSV row carries the full schema. Without this,
			// classes that load later (MainPanel, UIPanel, ...) lazy-register
			// their handles only on first use, which causes the dumper to
			// re-emit the header line mid-CSV every time a new metric appears.
			// Disabled categories are skipped (resolveSpan/Counter null-out).
			for (MetricSpec ms : KNOWN_METRICS) {
				if (ms.type == MetricSpec.Type.SPAN) {
					r.resolveSpan (ms.name, ms.category);
				} else {
					r.resolveCounter (ms.name, ms.category);
				}
			}

			if (!config.isMasterEnabled ()) {
				Log.log (Log.LEVEL_DEBUG,
					"PerfStats master switch is off -- no dumper, all handles disabled.",
					"PerfStats");
				return;
			}

			// Wire GC listeners (independent of the dumper -- the listener
			// path records via SpanHandle.recordRaw, so the histograms get
			// populated even if file I/O later fails).
			GcListener gl = new GcListener ();
			gl.install ();
			gcListener = gl;

			// Open the file and start the dumper. If the file open fails,
			// the dumper logs and sets a "disabled" flag; recording stays on.
			PerfStatsDumper d = new PerfStatsDumper (config);
			d.startThread ();
			dumper = d;
			Log.log (Log.LEVEL_DEBUG,
				"PerfStats initialized: csv=" + config.getCsvPath ()
					+ ", periodMs=" + config.getPeriodMs ()
					+ ", categories=" + config.getEnabledCategories (),
				"PerfStats");
		} catch (Throwable t) {
			Log.log (Log.LEVEL_ERROR,
				"PerfStats.init failed: " + t + " -- harness disabled for this session.",
				"PerfStats");
			// Leave registry null -- handles will all return disabled.
			registry = null;
		}
	}

	/**
	 * Stops the dumper, unregisters listeners, flushes any partial-tick
	 * row, and resets all handle bindings. Safe to call multiple times.
	 */
	public static synchronized void shutdown () {
		try {
			shutdownInternal ();
			Log.log (Log.LEVEL_DEBUG, "PerfStats shut down.", "PerfStats");
		} catch (Throwable t) {
			Log.log (Log.LEVEL_ERROR, "PerfStats.shutdown error: " + t, "PerfStats");
		}
	}

	private static void shutdownInternal () {
		PerfStatsDumper d = dumper;
		dumper = null;
		if (d != null) {
			d.stopThread ();
		}
		GcListener gl = gcListener;
		gcListener = null;
		if (gl != null) {
			gl.uninstall ();
		}
		registry = null;
		invalidateAllHandles ();
	}

	private static void invalidateAllHandles () {
		synchronized (spanHandles) {
			for (SpanHandle h : spanHandles) h.invalidate ();
		}
		synchronized (counterHandles) {
			for (CounterHandle h : counterHandles) h.invalidate ();
		}
		synchronized (sampleHandles) {
			for (SampleHandle h : sampleHandles) h.invalidate ();
		}
	}

	// ---------------------------------------------------------------- factories

	/**
	 * Resolve or declare a span metric. The first call with a given
	 * (name, category) pair creates the handle and tracks it. Subsequent
	 * calls with the same name *return a fresh handle* but pointing at the
	 * same underlying registry slot -- the handle's lazy-resolve picks up
	 * the same Histogram. Conventionally callers cache the returned handle
	 * in a {@code static final} field.
	 */
	public static SpanHandle span (String name, Category category) {
		SpanHandle h = new SpanHandle (name, category);
		spanHandles.add (h);
		return h;
	}

	public static CounterHandle counter (String name, Category category) {
		CounterHandle h = new CounterHandle (name, category);
		counterHandles.add (h);
		return h;
	}

	public static SampleHandle sample (String name, Category category, LongSupplier supplier) {
		SampleHandle h = new SampleHandle (name, category, supplier);
		sampleHandles.add (h);
		// Eagerly attempt to register so the dumper sees this metric on the
		// next CSV row even before the first sample() call.
		Registry r = registry;
		if (r != null && r.config != null && r.config.isCategoryEnabled (category)) {
			r.registerSample (h);
		}
		return h;
	}

	// Test-only / dumper-only accessors ---------------------------------------

	/**
	 * Returns the live registry, or null if PerfStats is not currently
	 * initialized. Used by the dumper to walk all live histograms /
	 * counters / samples for a CSV row.
	 */
	static Registry registry () { return registry; }

	/**
	 * Test-only: clear ALL state including handle-tracking lists. Used by
	 * unit tests so they can re-init from a clean slate.
	 */
	static synchronized void resetForTesting () {
		shutdownInternal ();
		spanHandles.clear ();
		counterHandles.clear ();
		sampleHandles.clear ();
	}

	// =================================================================
	//  Inner: Registry
	// =================================================================

	/**
	 * Stores the live {@link Histogram} and {@link AtomicLong} backing
	 * objects keyed by metric name (within a category). Every backing is
	 * created lazily on first resolve so that handles declared as
	 * {@code static final} fields don't allocate until the metric is
	 * actually used.
	 */
	static final class Registry {

		final PerfStatsConfig config;

		// Per-category sub-registries. ConcurrentHashMap so concurrent
		// first-use across recording threads is safe without a global
		// lock.
		private final Map<String, Histogram> spans = new ConcurrentHashMap<> ();
		private final Map<String, AtomicLong> counters = new ConcurrentHashMap<> ();
		// Sample metadata: for the dumper to walk and pull latest values.
		private final Map<String, SampleHandle> samples = new ConcurrentHashMap<> ();

		// Sorted views, refreshed atomically. Used by the dumper to render
		// alphabetical-column CSV.
		private volatile boolean dirty = true;

		Registry (PerfStatsConfig config) {
			this.config = config;
		}

		PerfStatsConfig config () {
			return config;
		}

		Histogram resolveSpan (String name, Category category) {
			if (config == null || !config.isCategoryEnabled (category)) return null;
			Histogram h = spans.get (name);
			if (h == null) {
				Histogram fresh = new Histogram ();
				h = spans.putIfAbsent (name, fresh);
				if (h == null) {
					h = fresh;
					dirty = true;
				}
			}
			return h;
		}

		AtomicLong resolveCounter (String name, Category category) {
			if (config == null || !config.isCategoryEnabled (category)) return null;
			AtomicLong a = counters.get (name);
			if (a == null) {
				AtomicLong fresh = new AtomicLong (0L);
				a = counters.putIfAbsent (name, fresh);
				if (a == null) {
					a = fresh;
					dirty = true;
				}
			}
			return a;
		}

		boolean resolveSample (String name, Category category) {
			if (config == null || !config.isCategoryEnabled (category)) return false;
			// We need the SampleHandle itself for the dumper to call into.
			// The factory passed it in via registerSample. For now register
			// on the fly by walking the global list -- SampleHandle has the
			// supplier, we just record the name for the CSV column.
			// (Mapping is filled in by registerSample below.)
			return true;
		}

		/**
		 * Called by the SampleHandle path after a successful resolve so the
		 * dumper has a way to enumerate live samples.
		 */
		void registerSample (SampleHandle h) {
			if (config == null || !config.isCategoryEnabled (h.category ())) return;
			if (samples.putIfAbsent (h.name (), h) == null) {
				dirty = true;
			}
		}

		/**
		 * Snapshot the current set of live metric names for CSV-header
		 * purposes. Returned in alphabetical order so the dumper can write
		 * a deterministic header. Each map is unmodifiable.
		 */
		LiveMetrics liveMetrics () {
			SortedMap<String, Histogram> sortedSpans = new TreeMap<> (spans);
			SortedMap<String, AtomicLong> sortedCounters = new TreeMap<> (counters);
			SortedMap<String, SampleHandle> sortedSamples = new TreeMap<> (samples);
			return new LiveMetrics (sortedSpans, sortedCounters, sortedSamples);
		}
	}

	/**
	 * Snapshot of "what metrics are currently live". Held by the dumper
	 * across one CSV-row render. Stable views: the underlying maps may
	 * gain new keys after this snapshot is taken; the new metrics will
	 * appear in the *next* row's header (we re-read header on each tick to
	 * pick them up -- a minor schema-stability concession that the spec
	 * accepts implicitly via "Adding new metrics in future optimization
	 * work is a one-handle-declaration change with no schema migration").
	 */
	static final class LiveMetrics {
		final SortedMap<String, Histogram> spans;
		final SortedMap<String, AtomicLong> counters;
		final SortedMap<String, SampleHandle> samples;

		LiveMetrics (SortedMap<String, Histogram> spans,
					 SortedMap<String, AtomicLong> counters,
					 SortedMap<String, SampleHandle> samples) {
			this.spans = spans;
			this.counters = counters;
			this.samples = samples;
		}
	}

}
