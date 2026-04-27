package xaos.utils.perf;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;

/**
 * Pure-functional CSV header / row formatting helpers extracted from the
 * dumper for testability.
 *
 * <p>Schema (per spec): two fixed leading columns, then every enabled
 * metric in alphabetical order. Spans contribute six columns; counters and
 * gauges contribute one each. Empty spans are emitted as
 * {@code 0,0,0,0,0,0} -- never blank -- so spreadsheets can sum/average
 * without surprises.
 *
 * <p>This class is intentionally allocation-light per call but doesn't
 * micro-optimize -- the dumper runs at 1 Hz, not on the hot path.
 */
public final class CsvWriter {

	/** Suffixes appended to a span name to produce its six column headers, in order. */
	public static final String[] SPAN_SUFFIXES = {
		"_count", "_total_ns", "_min_ns", "_max_ns", "_p50_ns", "_p99_ns"
	};

	private CsvWriter () {
		// utility class -- no instances
	}

	/**
	 * Build the CSV header line (no trailing newline). Column order:
	 * <ol>
	 *   <li>{@code timestamp_iso}
	 *   <li>{@code seconds_since_start}
	 *   <li>For each metric in alphabetical order across all three maps,
	 *       one or six columns as appropriate.
	 * </ol>
	 *
	 * <p>Sorting is by the metric *name*, not by category -- a span called
	 * "frame.total" comes before a counter called "gl.bind_texture", and
	 * before a gauge called "heap.used_mb". Within each column-set the
	 * span's six columns come out in {@link #SPAN_SUFFIXES} order.
	 */
	public static String header (SortedMap<String, ?> spans,
								 SortedMap<String, ?> counters,
								 SortedMap<String, ?> samples) {
		List<String> cols = new ArrayList<> ();
		cols.add ("timestamp_iso");
		cols.add ("seconds_since_start");
		appendColumns (cols, spans, counters, samples);
		return joinCsv (cols);
	}

	/**
	 * Convenience wrapper: returns the metric column names *only* (no
	 * leading timestamp / seconds_since_start). Used by tests that want to
	 * verify the metric-name half of the schema without re-parsing.
	 */
	public static List<String> metricColumns (SortedMap<String, ?> spans,
											  SortedMap<String, ?> counters,
											  SortedMap<String, ?> samples) {
		List<String> cols = new ArrayList<> ();
		appendColumns (cols, spans, counters, samples);
		return cols;
	}

	private static void appendColumns (List<String> cols,
									   SortedMap<String, ?> spans,
									   SortedMap<String, ?> counters,
									   SortedMap<String, ?> samples) {
		// Merge all metric names into one alphabetical list so the column
		// order is stable and deterministic across runs and across category
		// toggles -- toggling a category off simply removes its columns
		// from the merged list, no shuffling.
		List<String> allNames = new ArrayList<> ();
		allNames.addAll (spans.keySet ());
		allNames.addAll (counters.keySet ());
		allNames.addAll (samples.keySet ());
		java.util.Collections.sort (allNames);
		for (String name : allNames) {
			if (spans.containsKey (name)) {
				for (String sfx : SPAN_SUFFIXES) {
					cols.add (name + sfx);
				}
			} else if (counters.containsKey (name)) {
				cols.add (name);
			} else if (samples.containsKey (name)) {
				cols.add (name);
			}
		}
	}

	/**
	 * Build a CSV data row (no trailing newline) from the given snapshots.
	 *
	 * @param timestampIso      ISO-8601 timestamp string for the leading
	 *                          {@code timestamp_iso} column
	 * @param secondsSinceStart elapsed seconds (real wall-clock) since the
	 *                          dumper started -- written with one decimal
	 * @param spanSnapshots     map: metric name -&gt; Histogram.Snapshot for
	 *                          this period (empty period =&gt; pass an empty
	 *                          Snapshot, not null, to emit zero-row)
	 * @param counterDeltas     map: metric name -&gt; long sum-over-period
	 * @param sampleValues      map: metric name -&gt; long latest value at
	 *                          row time
	 */
	public static String row (String timestampIso,
							  double secondsSinceStart,
							  SortedMap<String, Histogram.Snapshot> spanSnapshots,
							  SortedMap<String, Long> counterDeltas,
							  SortedMap<String, Long> sampleValues) {
		List<String> cells = new ArrayList<> ();
		cells.add (timestampIso);
		cells.add (formatSeconds (secondsSinceStart));

		List<String> allNames = new ArrayList<> ();
		allNames.addAll (spanSnapshots.keySet ());
		allNames.addAll (counterDeltas.keySet ());
		allNames.addAll (sampleValues.keySet ());
		java.util.Collections.sort (allNames);
		for (String name : allNames) {
			if (spanSnapshots.containsKey (name)) {
				Histogram.Snapshot s = spanSnapshots.get (name);
				if (s == null || s.count () == 0L) {
					// Empty period -- six zeros rather than blanks.
					cells.add ("0");
					cells.add ("0");
					cells.add ("0");
					cells.add ("0");
					cells.add ("0");
					cells.add ("0");
				} else {
					cells.add (Long.toString (s.count ()));
					cells.add (Long.toString (s.totalNanos ()));
					cells.add (Long.toString (s.minNanos ()));
					cells.add (Long.toString (s.maxNanos ()));
					cells.add (Long.toString (s.percentile (0.50)));
					cells.add (Long.toString (s.percentile (0.99)));
				}
			} else if (counterDeltas.containsKey (name)) {
				Long v = counterDeltas.get (name);
				cells.add (Long.toString (v == null ? 0L : v.longValue ()));
			} else if (sampleValues.containsKey (name)) {
				Long v = sampleValues.get (name);
				cells.add (Long.toString (v == null ? 0L : v.longValue ()));
			}
		}
		return joinCsv (cells);
	}

	/**
	 * Format the seconds_since_start cell. We want a single-decimal-place
	 * value -- {@code "13.0"}, {@code "13.2"} -- not the locale-dependent
	 * default of {@link Double#toString} which can produce
	 * {@code "1.3E1"} for very small or large numbers.
	 */
	static String formatSeconds (double secs) {
		// One decimal, dot-separated regardless of locale.
		return String.format (java.util.Locale.ROOT, "%.1f", secs);
	}

	private static String joinCsv (List<String> cells) {
		StringBuilder sb = new StringBuilder ();
		for (int i = 0; i < cells.size (); i++) {
			if (i > 0) sb.append (',');
			sb.append (cells.get (i));
		}
		return sb.toString ();
	}
}
