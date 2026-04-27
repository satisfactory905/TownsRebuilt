package xaos.utils.perf;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvWriterTest {

	// ---------------------------------------------------------------- header

	@Test
	void header_alphabeticallySorted_acrossAllMetricTypes () {
		// Mix of spans, counters, samples; verify column order is fully
		// alphabetical-by-metric-name regardless of which type each metric
		// is.
		SortedMap<String, Histogram> spans = new TreeMap<> ();
		spans.put ("frame.total", new Histogram ());
		spans.put ("path.search", new Histogram ());
		SortedMap<String, AtomicLong> counters = new TreeMap<> ();
		counters.put ("gl.bind_texture", new AtomicLong ());
		counters.put ("alpha.counter", new AtomicLong ());
		SortedMap<String, SampleHandle> samples = new TreeMap<> ();
		samples.put ("heap.used_mb", new SampleHandle ("heap.used_mb", Category.ENGINE_GC, () -> 0L));

		String h = CsvWriter.header (spans, counters, samples);
		String[] cols = h.split (",", -1);
		assertEquals ("timestamp_iso", cols[0]);
		assertEquals ("seconds_since_start", cols[1]);

		// Expected metric order: alpha.counter, frame.total (6 cols), gl.bind_texture, heap.used_mb, path.search (6 cols)
		// = 1 + 6 + 1 + 1 + 6 = 15 metric cells, plus 2 fixed = 17.
		assertEquals (17, cols.length, "got cols=" + java.util.Arrays.toString (cols));
		assertEquals ("alpha.counter", cols[2]);
		// span columns expand to 6, in fixed suffix order
		assertEquals ("frame.total_count", cols[3]);
		assertEquals ("frame.total_total_ns", cols[4]);
		assertEquals ("frame.total_min_ns", cols[5]);
		assertEquals ("frame.total_max_ns", cols[6]);
		assertEquals ("frame.total_p50_ns", cols[7]);
		assertEquals ("frame.total_p99_ns", cols[8]);
		assertEquals ("gl.bind_texture", cols[9]);
		assertEquals ("heap.used_mb", cols[10]);
		assertEquals ("path.search_count", cols[11]);
		assertEquals ("path.search_total_ns", cols[12]);
		assertEquals ("path.search_min_ns", cols[13]);
		assertEquals ("path.search_max_ns", cols[14]);
		assertEquals ("path.search_p50_ns", cols[15]);
		assertEquals ("path.search_p99_ns", cols[16]);
	}

	@Test
	void header_emptyMetricSet_yieldsOnlyTimestampColumns () {
		String h = CsvWriter.header (new TreeMap<> (), new TreeMap<> (), new TreeMap<> ());
		assertEquals ("timestamp_iso,seconds_since_start", h);
	}

	@Test
	void header_spanContributesSixColumnsInOrder () {
		SortedMap<String, Histogram> spans = new TreeMap<> ();
		spans.put ("the.span", new Histogram ());
		List<String> cols = CsvWriter.metricColumns (spans, new TreeMap<> (), new TreeMap<> ());
		assertEquals (6, cols.size ());
		assertEquals ("the.span_count", cols.get (0));
		assertEquals ("the.span_total_ns", cols.get (1));
		assertEquals ("the.span_min_ns", cols.get (2));
		assertEquals ("the.span_max_ns", cols.get (3));
		assertEquals ("the.span_p50_ns", cols.get (4));
		assertEquals ("the.span_p99_ns", cols.get (5));
	}

	// ---------------------------------------------------------------- row

	@Test
	void row_populatedSpan_writesAllSixCells () {
		Histogram h = new Histogram ();
		for (int i = 0; i < 100; i++) {
			h.record (1_000_000L + i * 10_000L);
		}
		Histogram.Snapshot snap = h.snapshotAndReset ();
		SortedMap<String, Histogram.Snapshot> spans = new TreeMap<> ();
		spans.put ("s", snap);

		String row = CsvWriter.row (
			"2026-04-26T00:00:00Z",
			1.0,
			spans,
			new TreeMap<> (),
			new TreeMap<> ());
		String[] cells = row.split (",", -1);
		// 2 fixed + 6 span cells = 8.
		assertEquals (8, cells.length);
		assertEquals ("2026-04-26T00:00:00Z", cells[0]);
		assertEquals ("1.0", cells[1]);
		assertEquals ("100", cells[2]); // count
		// total_ns: sum 1ms to 1.99ms approximately
		long totalNs = Long.parseLong (cells[3]);
		assertTrue (totalNs > 0L);
		assertEquals (Long.toString (snap.totalNanos ()), cells[3]);
		assertEquals (Long.toString (snap.minNanos ()), cells[4]);
		assertEquals (Long.toString (snap.maxNanos ()), cells[5]);
	}

	@Test
	void row_emptyPeriod_yieldsZerosNotBlanks () {
		// Empty histogram for one of the spans, populated for the other.
		Histogram empty = new Histogram ();
		SortedMap<String, Histogram.Snapshot> spans = new TreeMap<> ();
		spans.put ("a.empty", empty.snapshotAndReset ());
		String row = CsvWriter.row ("ts", 0.5, spans, new TreeMap<> (), new TreeMap<> ());
		String[] cells = row.split (",", -1);
		assertEquals (8, cells.length);
		// Six zeros, in order.
		for (int i = 2; i < 8; i++) {
			assertEquals ("0", cells[i],
				"empty period must emit zero in column " + i + ", got '" + cells[i] + "'");
		}
	}

	@Test
	void row_counterCellWritesSumOverPeriod () {
		SortedMap<String, Long> counters = new TreeMap<> ();
		counters.put ("c", 42L);
		String row = CsvWriter.row ("ts", 0.5, new TreeMap<> (), counters, new TreeMap<> ());
		String[] cells = row.split (",", -1);
		// 2 fixed + 1 counter = 3.
		assertEquals (3, cells.length);
		assertEquals ("42", cells[2]);
	}

	@Test
	void row_sampleCellWritesLatestValue () {
		SortedMap<String, Long> samples = new TreeMap<> ();
		samples.put ("g", 1234L);
		String row = CsvWriter.row ("ts", 0.5, new TreeMap<> (), new TreeMap<> (), samples);
		String[] cells = row.split (",", -1);
		assertEquals (3, cells.length);
		assertEquals ("1234", cells[2]);
	}

	@Test
	void row_columnOrderStableAcrossRuns () {
		// Two independent rows with the same metric set must produce
		// identical column ordering -- alphabetical, regardless of the
		// insertion order of the input maps.
		Histogram hh = new Histogram ();
		hh.record (1_000_000L);
		Histogram.Snapshot s = hh.snapshotAndReset ();

		// Reverse insertion order in two TreeMaps: TreeMap auto-sorts so
		// the output is the same. Sanity check via metricColumns().
		SortedMap<String, Histogram.Snapshot> spans = new TreeMap<> ();
		spans.put ("z", s);
		spans.put ("a", s);
		SortedMap<String, Long> counters = new TreeMap<> ();
		counters.put ("m", 1L);

		String row1 = CsvWriter.row ("ts", 0.5, spans, counters, new TreeMap<> ());
		String row2 = CsvWriter.row ("ts", 0.5, spans, counters, new TreeMap<> ());
		assertEquals (row1, row2);

		String[] cells = row1.split (",", -1);
		// 2 fixed + 6 (a) + 1 (m) + 6 (z) = 15
		assertEquals (15, cells.length);
		// 'a' span cells then 'm' counter then 'z' span cells.
		// We just need to verify a comes before m comes before z.
		String header = CsvWriter.header (spans, counters, new TreeMap<> ());
		String[] hcols = header.split (",", -1);
		int aIdx = -1, mIdx = -1, zIdx = -1;
		for (int i = 0; i < hcols.length; i++) {
			if (hcols[i].startsWith ("a_")) { aIdx = i; break; }
		}
		for (int i = 0; i < hcols.length; i++) {
			if (hcols[i].equals ("m")) { mIdx = i; break; }
		}
		for (int i = 0; i < hcols.length; i++) {
			if (hcols[i].startsWith ("z_")) { zIdx = i; break; }
		}
		assertTrue (aIdx >= 0 && mIdx > aIdx && zIdx > mIdx,
			"alphabetical order violated: a@" + aIdx + " m@" + mIdx + " z@" + zIdx);
	}

	@Test
	void row_secondsSinceStart_oneDecimal_localeIndependent () {
		// Even on a locale that prefers comma decimals, the output must
		// be dot-separated.
		String row = CsvWriter.row ("ts", 13.25, new TreeMap<> (), new TreeMap<> (), new TreeMap<> ());
		String[] cells = row.split (",", -1);
		assertEquals ("13.3", cells[1]); // rounded to one decimal
	}
}
