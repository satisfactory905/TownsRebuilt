package xaos.utils.perf;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfStatsDumperTest {

	@BeforeEach
	void setUp () {
		PerfStats.resetForTesting ();
	}

	@AfterEach
	void tearDown () {
		PerfStats.resetForTesting ();
	}

	@Test
	void integration_dumperWritesHeaderAndDataRow (@TempDir Path tempDir) throws Exception {
		Path csv = tempDir.resolve ("perf.csv");
		// Short period so the test stays under a second.
		PerfStatsConfig cfg = new PerfStatsConfig (
			true,
			csv,
			200L,
			EnumSet.allOf (Category.class));

		PerfStats.init (cfg);

		// Declare a span and counter, then drive a couple of records from
		// a worker thread so the first-period CSV row has non-zero data.
		final SpanHandle span = PerfStats.span ("test.span", Category.RENDERING_FRAME);
		final CounterHandle counter = PerfStats.counter ("test.counter", Category.RENDERING_GL);

		Thread driver = new Thread (() -> {
			for (int i = 0; i < 50; i++) {
				try (Span s = span.start ()) {
					try { Thread.sleep (1); } catch (InterruptedException ie) {
						Thread.currentThread ().interrupt ();
						return;
					}
				}
				counter.inc ();
			}
		});
		driver.start ();
		// Sleep ~1.2 * period to let the dumper write at least one row.
		Thread.sleep ((long) (1.2 * cfg.getPeriodMs ()));
		driver.join ();

		PerfStats.shutdown ();

		// File should exist and have at least a header line + one data row.
		assertTrue (Files.exists (csv), "CSV file must be created at " + csv);
		List<String> lines = Files.readAllLines (csv, StandardCharsets.UTF_8);
		assertTrue (lines.size () >= 2,
			"expected header + >=1 data row, got " + lines.size () + " lines: " + lines);

		// First line must be the header -- starts with the two fixed cols.
		String header = lines.get (0);
		assertTrue (header.startsWith ("timestamp_iso,seconds_since_start"),
			"header malformed: " + header);
		// Header must mention our test metric columns.
		assertTrue (header.contains ("test.span_count"), "header missing test.span: " + header);
		assertTrue (header.contains ("test.counter"), "header missing test.counter: " + header);

		// Subsequent lines must each be parseable as the same number of cells.
		String[] hcols = header.split (",", -1);
		// Find at least one row that records non-zero for our span or counter.
		boolean foundData = false;
		for (int i = 1; i < lines.size (); i++) {
			String line = lines.get (i);
			// Skip a re-emitted header (the dumper rewrites the header
			// when new metrics show up; harmless for this test).
			if (line.equals (header)) continue;
			String[] cells = line.split (",", -1);
			assertTrue (cells.length == hcols.length,
				"row " + i + " has " + cells.length + " cells, expected " + hcols.length
					+ "\nheader: " + header + "\nrow: " + line);
			// Find the test.counter column.
			int counterIdx = -1;
			int spanCountIdx = -1;
			for (int c = 0; c < hcols.length; c++) {
				if (hcols[c].equals ("test.counter")) counterIdx = c;
				if (hcols[c].equals ("test.span_count")) spanCountIdx = c;
			}
			assertTrue (counterIdx >= 0 && spanCountIdx >= 0,
				"could not find test metric columns in header");
			long counterVal = Long.parseLong (cells[counterIdx]);
			long spanCount = Long.parseLong (cells[spanCountIdx]);
			if (counterVal > 0L || spanCount > 0L) {
				foundData = true;
			}
			// Sanity-check: every cell parses as a number (other than the
			// timestamp at column 0).
			for (int c = 1; c < cells.length; c++) {
				try {
					if (cells[c].contains (".")) {
						Double.parseDouble (cells[c]);
					} else {
						Long.parseLong (cells[c]);
					}
				} catch (NumberFormatException nfe) {
					assertTrue (false,
						"cell " + c + " in row " + i + " is not numeric: '" + cells[c] + "'");
				}
			}
		}
		assertTrue (foundData,
			"expected at least one row with non-zero test data: " + lines);
	}

	@Test
	void integration_masterDisabled_noFileCreated (@TempDir Path tempDir) throws IOException, InterruptedException {
		Path csv = tempDir.resolve ("nofile.csv");
		PerfStatsConfig cfg = new PerfStatsConfig (
			false, // master off
			csv,
			100L,
			EnumSet.allOf (Category.class));
		PerfStats.init (cfg);
		Thread.sleep (250L);
		PerfStats.shutdown ();
		assertFalse (Files.exists (csv),
			"master-disabled init should not create a CSV; found one at " + csv);
	}
}
