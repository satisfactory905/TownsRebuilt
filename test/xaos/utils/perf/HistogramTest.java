package xaos.utils.perf;

import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistogramTest {

	// ---------------------------------------------------------------- bucketing

	@Test
	void bucketIndex_minBoundary () {
		// d == MIN_NANOS is the lower edge of bucket 0.
		assertEquals (0, Histogram.bucketIndex (Histogram.MIN_NANOS));
		// d well below the floor still lands in bucket 0.
		assertEquals (0, Histogram.bucketIndex (1L));
		assertEquals (0, Histogram.bucketIndex (0L));
		assertEquals (0, Histogram.bucketIndex (-1L));
	}

	@Test
	void bucketIndex_binaryDecadeBoundaries () {
		// Each binary decade (factor-of-2 doubling) should be exactly 4
		// buckets up. With MIN_NANOS = 10us: 10us -> bucket 0, 20us -> 4,
		// 40us -> 8, etc.
		assertEquals (0, Histogram.bucketIndex (10_000L));
		assertEquals (4, Histogram.bucketIndex (20_000L));
		assertEquals (8, Histogram.bucketIndex (40_000L));
		assertEquals (12, Histogram.bucketIndex (80_000L));
		assertEquals (16, Histogram.bucketIndex (160_000L));
		assertEquals (20, Histogram.bucketIndex (320_000L));
		assertEquals (24, Histogram.bucketIndex (640_000L));
		assertEquals (28, Histogram.bucketIndex (1_280_000L));
		assertEquals (32, Histogram.bucketIndex (2_560_000L));
	}

	@Test
	void bucketIndex_subBucketsWithinBinaryDecade () {
		// Within the [20us, 40us) binary decade (buckets 4..7) the four
		// sub-bucket boundaries are at 20us * 2^(k/4):
		//   bucket 4: [20.0, 23.78) us
		//   bucket 5: [23.78, 28.28) us
		//   bucket 6: [28.28, 33.64) us
		//   bucket 7: [33.64, 40.00) us
		assertEquals (4, Histogram.bucketIndex (21_000L));
		assertEquals (5, Histogram.bucketIndex (25_000L));
		assertEquals (6, Histogram.bucketIndex (30_000L));
		assertEquals (7, Histogram.bucketIndex (38_000L));
		assertEquals (8, Histogram.bucketIndex (40_000L));
	}

	@Test
	void bucketIndex_overflowClampsToTopBucket () {
		// Above 10us * 2^16 ~= 655 ms is the top bucket. 10s and above
		// clamp -- the spec explicitly accepts this for long-outlier events
		// like world-gen-at-startup.
		assertEquals (Histogram.NUM_BUCKETS - 1, Histogram.bucketIndex (10_000_000_000L)); // 10 s
		assertEquals (Histogram.NUM_BUCKETS - 1, Histogram.bucketIndex (Long.MAX_VALUE));
	}

	// ---------------------------------------------------------------- record + snapshot

	@Test
	void record_basicCounts () {
		Histogram h = new Histogram ();
		h.record (1_000_000L);  // 1 ms
		h.record (2_000_000L);  // 2 ms
		h.record (5_000_000L);  // 5 ms
		Histogram.Snapshot s = h.snapshotAndReset ();
		assertEquals (3L, s.count ());
		assertEquals (8_000_000L, s.totalNanos ());
		assertEquals (1_000_000L, s.minNanos ());
		assertEquals (5_000_000L, s.maxNanos ());
	}

	@Test
	void snapshotAndReset_emptyHistogramYieldsZeros () {
		Histogram h = new Histogram ();
		Histogram.Snapshot s = h.snapshotAndReset ();
		assertEquals (0L, s.count ());
		assertEquals (0L, s.totalNanos ());
		assertEquals (0L, s.minNanos ());
		assertEquals (0L, s.maxNanos ());
		// Empty histograms percentile to zero, not Long.MAX_VALUE.
		assertEquals (0L, s.percentile (0.50));
		assertEquals (0L, s.percentile (0.99));
	}

	@Test
	void snapshotAndReset_clearsState () {
		Histogram h = new Histogram ();
		h.record (1_000_000L);
		Histogram.Snapshot s1 = h.snapshotAndReset ();
		assertEquals (1L, s1.count ());
		Histogram.Snapshot s2 = h.snapshotAndReset ();
		// Second snapshot is empty -- no double-counting.
		assertEquals (0L, s2.count ());
	}

	@Test
	void snapshotAndReset_doesNotLoseRecords () {
		Histogram h = new Histogram ();
		final int N = 1000;
		for (int i = 0; i < N; i++) h.record (1_000_000L + i);
		Histogram.Snapshot s = h.snapshotAndReset ();
		assertEquals (N, s.count ());
		// Each value is unique within a tight range -- min/max should be
		// the first and last respectively.
		assertEquals (1_000_000L, s.minNanos ());
		assertEquals (1_000_000L + N - 1, s.maxNanos ());
	}

	// ---------------------------------------------------------------- percentiles

	// The implementation uses 4 binary-decade sub-buckets (~19% edge-to-edge
	// bucket width) with geometric in-bucket interpolation. p50 and p99 hold
	// within ~10% of analytical truth on smooth distributions, matching the
	// spec's stated test bar. Test inputs stay above MIN_NANOS (10 us) and
	// below the ~655 ms upper edge (see Histogram.java's javadoc for the
	// range rationale).

	@Test
	void percentile_uniformDistribution () {
		// 10000 values uniform in [1ms, 5ms] -- p50 should be ~3ms,
		// p99 ~= 4.96ms. Values fit comfortably below the 6.55ms upper edge.
		Histogram h = new Histogram ();
		Random rng = new Random (42);
		final int N = 10_000;
		long minVal = 1_000_000L;
		long range = 4_000_000L; // 1..5 ms
		for (int i = 0; i < N; i++) {
			h.record (minVal + (long) (rng.nextDouble () * range));
		}
		Histogram.Snapshot s = h.snapshotAndReset ();
		long p50 = s.percentile (0.50);
		long p99 = s.percentile (0.99);

		assertWithinPct (p50, 3_000_000L, 0.10, "uniform p50");
		assertWithinPct (p99, 4_960_000L, 0.10, "uniform p99");
	}

	@Test
	void percentile_normalDistribution () {
		// Normal centered at 2ms with stddev 0.4ms. p50 = 2ms, p99 ~= 2.93ms.
		// Tightly bounded so test values stay well within histogram range.
		Histogram h = new Histogram ();
		Random rng = new Random (1234);
		final int N = 20_000;
		double mean = 2_000_000.0;
		double sd = 400_000.0;
		for (int i = 0; i < N; i++) {
			double v = mean + sd * rng.nextGaussian ();
			if (v < 100.0) v = 100.0;
			h.record ((long) v);
		}
		Histogram.Snapshot s = h.snapshotAndReset ();
		long p50 = s.percentile (0.50);
		long p99 = s.percentile (0.99);
		assertWithinPct (p50, 2_000_000L, 0.10, "normal p50");
		assertWithinPct (p99, 2_930_000L, 0.10, "normal p99");
	}

	@Test
	void percentile_logNormalDistribution () {
		// Log-normal centered at log(1ms) with sigma 0.4. Median = e^mean
		// = 1ms; p99 closed-form = exp(log(1e6) + 0.4 * 2.326) = 1e6 *
		// exp(0.9305) ~= 2.535e6 = 2.535ms.
		Histogram h = new Histogram ();
		Random rng = new Random (777);
		final int N = 20_000;
		double mu = Math.log (1_000_000.0);
		double sigma = 0.4;
		for (int i = 0; i < N; i++) {
			double v = Math.exp (mu + sigma * rng.nextGaussian ());
			h.record ((long) v);
		}
		Histogram.Snapshot s = h.snapshotAndReset ();
		long p50 = s.percentile (0.50);
		long p99 = s.percentile (0.99);
		assertWithinPct (p50, 1_000_000L, 0.10, "lognormal p50");
		assertWithinPct (p99, 2_535_000L, 0.10, "lognormal p99");
	}

	@Test
	void percentile_singleValue () {
		Histogram h = new Histogram ();
		h.record (5_000_000L);
		Histogram.Snapshot s = h.snapshotAndReset ();
		// All percentiles for a single value should be that value.
		long p50 = s.percentile (0.50);
		// Within bucket-width tolerance.
		assertWithinPct (p50, 5_000_000L, 0.78, "single-value p50");
	}

	// ---------------------------------------------------------------- thread safety

	@Test
	void record_concurrentRecordingPreservesCountAndSum () throws InterruptedException {
		Histogram h = new Histogram ();
		final int THREADS = 8;
		final int PER_THREAD = 50_000;
		final long FIXED_VAL = 1_500_000L; // 1.5ms
		Thread[] ts = new Thread[THREADS];
		final CountDownLatch ready = new CountDownLatch (THREADS);
		final CountDownLatch go = new CountDownLatch (1);
		for (int i = 0; i < THREADS; i++) {
			ts[i] = new Thread (() -> {
				ready.countDown ();
				try {
					go.await ();
				} catch (InterruptedException ie) {
					Thread.currentThread ().interrupt ();
					return;
				}
				for (int j = 0; j < PER_THREAD; j++) {
					h.record (FIXED_VAL);
				}
			});
			ts[i].start ();
		}
		ready.await ();
		go.countDown ();
		for (Thread t : ts) t.join ();
		Histogram.Snapshot s = h.snapshotAndReset ();
		assertEquals ((long) THREADS * PER_THREAD, s.count ());
		assertEquals ((long) THREADS * PER_THREAD * FIXED_VAL, s.totalNanos ());
		assertEquals (FIXED_VAL, s.minNanos ());
		assertEquals (FIXED_VAL, s.maxNanos ());
	}

	@Test
	void snapshotAndReset_concurrentWithRecorders_doesNotLoseAggregateCount () throws InterruptedException {
		// Drive many recorders while a single snapshotter snapshots
		// repeatedly. The sum of all snapshot counts plus the final
		// snapshot count must equal the total recorded count.
		final Histogram h = new Histogram ();
		final int RECORDERS = 4;
		final int PER_THREAD = 25_000;
		final CountDownLatch ready = new CountDownLatch (RECORDERS);
		final CountDownLatch go = new CountDownLatch (1);
		final AtomicReference<Throwable> err = new AtomicReference<> ();
		Thread[] recorders = new Thread[RECORDERS];
		for (int i = 0; i < RECORDERS; i++) {
			recorders[i] = new Thread (() -> {
				ready.countDown ();
				try {
					go.await ();
					for (int j = 0; j < PER_THREAD; j++) {
						h.record (1_000_000L);
					}
				} catch (Throwable t) {
					err.set (t);
				}
			});
			recorders[i].start ();
		}

		ready.await ();
		long[] totalSeen = { 0L };
		Thread snapper = new Thread (() -> {
			try {
				go.await ();
				while (true) {
					Histogram.Snapshot s = h.snapshotAndReset ();
					totalSeen[0] += s.count ();
					boolean allDone = true;
					for (Thread r : recorders) if (r.isAlive ()) { allDone = false; break; }
					if (allDone) {
						// One more snapshot to drain the tail.
						totalSeen[0] += h.snapshotAndReset ().count ();
						break;
					}
				}
			} catch (Throwable t) {
				err.set (t);
			}
		});
		snapper.start ();

		go.countDown ();
		for (Thread r : recorders) r.join ();
		snapper.join ();

		assertEquals (null, err.get ());
		assertEquals ((long) RECORDERS * PER_THREAD, totalSeen[0],
			"snapshot loop must observe every record exactly once");
	}

	// ---------------------------------------------------------------- helpers

	private static void assertWithinPct (long actual, long expected, double pct, String label) {
		long tolerance = (long) Math.abs (expected * pct);
		long diff = Math.abs (actual - expected);
		assertTrue (diff <= tolerance,
			label + ": actual=" + actual + " expected=" + expected
				+ " (tolerance " + (pct * 100) + "%, diff=" + diff + ", limit=" + tolerance + ")");
	}
}
