package xaos.utils.perf;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Fixed-memory, lock-free histogram for nanosecond durations.
 *
 * <h2>Bucketing</h2>
 *
 * <p>64 logarithmic buckets with 4 sub-buckets per <i>binary</i> decade
 * (each factor-of-2 doubling is split into 4 buckets). The original spec
 * proposed a {@code floor(log2(d/100ns)) * 4 + sub} formula and a
 * "100 ns to ~10 s" range, which were internally inconsistent with each
 * other -- the formula plus 64 buckets only covers 16 binary octaves, so
 * either the range or the bucket count had to give. The accuracy bar (~10%
 * percentile error on smooth distributions) is what forces the narrow
 * binary-octave buckets, so we keep the 4-sub-bucket structure and shift
 * the lower edge up to 10 us. That gives a working range of
 * 10 us up to 10 us * 2^16 ~= 655 ms, which fits every metric this
 * harness instruments (frame.total at 33 ms, sim.tick up to tens of ms,
 * gc.major.duration at ZGC's typical 100-300 ms cycles, path.search at
 * tens to hundreds of us). Sub-10us span overhead is dominated by the
 * recording machinery itself (~200-500 ns per close()), so losing
 * resolution below 10 us is acceptable.
 *
 * <p>Operational consequence: durations above ~655 ms (e.g. a multi-second
 * save load, world generation at startup) all land in bucket 63 with
 * cumulative count and sum tracked correctly, but their p99 reads back as
 * ">= 655 ms" rather than the true value. Such outliers are rare and the
 * use case (correlate a stutter with the in-period GC events) doesn't
 * need precise long-tail timing.
 *
 * <p>Concretely, bucket boundaries are at {@code 10us * 2^(b/4)}:
 * <ul>
 *   <li>b=0:  [10 us,    ~11.9 us)
 *   <li>b=4:  [20 us,    ~23.8 us)
 *   <li>b=20: [320 us,   ~380 us)
 *   <li>b=40: [10.24 ms, ~12.18 ms)
 *   <li>b=56: [163.8 ms, ~194.7 ms)
 *   <li>b=60: [327.7 ms, ~389.6 ms)
 *   <li>b=63: [551.4 ms, infinity)
 * </ul>
 *
 * Relative error per bucket is {@code 2^(1/4) - 1 ~= 19%}; the percentile
 * estimate uses geometric (log-linear) interpolation which keeps p50/p99
 * within ~10% of analytical truth on smooth distributions.
 *
 * <h2>Concurrency</h2>
 *
 * <p>{@link #record(long)} is lock-free: one bucket-array CAS-free
 * incrementAndGet plus three atomic updates (count, sum, max compareAndSet
 * loop, min compareAndSet loop). No allocations.
 *
 * <p>{@link #snapshotAndReset()} is single-writer (the dumper thread) but
 * tolerates concurrent recorders. It uses {@link AtomicLongArray#getAndSet}
 * per bucket and on the scalar fields. Records that interleave with the
 * snapshot are absorbed into the next period -- the spec explicitly accepts
 * this.
 */
public final class Histogram {

	/** Number of logarithmic buckets; spec-mandated. */
	public static final int NUM_BUCKETS = 64;

	/** Sub-buckets per binary (factor-of-2) decade. */
	public static final int SUB_BUCKETS_PER_DECADE = 4;

	/** Lower edge of bucket 0, in nanoseconds (10 microseconds). */
	public static final long MIN_NANOS = 10_000L;

	private static final double LN_2 = Math.log (2.0);

	/**
	 * Cached lower-edge value of every bucket, in nanoseconds. Used for the
	 * percentile interpolation. {@code BUCKET_LOW_EDGE[b] = MIN_NANOS *
	 * 2^(b / 4.0)}. The top entry is the top of the highest bucket
	 * (effectively +infinity for the snapshot's purposes).
	 */
	private static final double[] BUCKET_LOW_EDGE = new double[NUM_BUCKETS + 1];

	static {
		for (int b = 0; b <= NUM_BUCKETS; b++) {
			BUCKET_LOW_EDGE[b] = MIN_NANOS * Math.pow (2.0, b / (double) SUB_BUCKETS_PER_DECADE);
		}
	}

	private final AtomicLongArray buckets = new AtomicLongArray (NUM_BUCKETS);
	private final AtomicLong count = new AtomicLong (0L);
	private final AtomicLong sum = new AtomicLong (0L);
	private final AtomicLong min = new AtomicLong (Long.MAX_VALUE);
	private final AtomicLong max = new AtomicLong (0L);

	/**
	 * Returns the bucket index for a given duration in nanoseconds. Negative
	 * or zero durations land in bucket 0; values above the top edge clamp to
	 * bucket {@code NUM_BUCKETS - 1}.
	 */
	public static int bucketIndex (long durationNanos) {
		if (durationNanos <= MIN_NANOS) return 0;
		// 4 * log2(d / MIN_NANOS), with a small epsilon so the exact boundary
		// values (MIN_NANOS * 2^k) land in bucket k*4 cleanly rather than
		// drifting by 1 due to floating-point ULP at the edge.
		double scaled = SUB_BUCKETS_PER_DECADE * (Math.log (durationNanos / (double) MIN_NANOS) / LN_2);
		int b = (int) Math.floor (scaled + 1e-9);
		if (b < 0) return 0;
		if (b >= NUM_BUCKETS) return NUM_BUCKETS - 1;
		return b;
	}

	/**
	 * Records one observation. Safe to call from any thread.
	 */
	public void record (long durationNanos) {
		// Negative durations make no physical sense; clamp to zero so the
		// rest of the math doesn't have to special-case them.
		long d = durationNanos < 0L ? 0L : durationNanos;
		buckets.incrementAndGet (bucketIndex (d));
		count.incrementAndGet ();
		sum.addAndGet (d);
		// CAS-loop min/max: the typical case is one CAS, two on contention.
		long curMin;
		do {
			curMin = min.get ();
			if (d >= curMin) break;
		} while (!min.compareAndSet (curMin, d));
		long curMax;
		do {
			curMax = max.get ();
			if (d <= curMax) break;
		} while (!max.compareAndSet (curMax, d));
	}

	/**
	 * Atomically snapshot all internal state and reset to empty. Called by
	 * the dumper thread once per period. Concurrent recorders may have their
	 * writes split across the snapshot/next-period boundary; that's
	 * acceptable for statistical estimates and is the documented behavior.
	 */
	public Snapshot snapshotAndReset () {
		long[] buckCopy = new long[NUM_BUCKETS];
		long bucketSum = 0L;
		for (int i = 0; i < NUM_BUCKETS; i++) {
			long v = buckets.getAndSet (i, 0L);
			buckCopy[i] = v;
			bucketSum += v;
		}
		// Reset the scalars, but use the bucket sum as the authoritative
		// count -- this avoids the visible inconsistency where a record()
		// in flight has bumped count but not yet bumped its bucket. The
		// reverse case (bucket bumped, count not yet) just means the next
		// snapshot is "missing" that record, not that this one over-reports.
		count.getAndSet (0L);
		long totalNanos = sum.getAndSet (0L);
		long minVal = min.getAndSet (Long.MAX_VALUE);
		long maxVal = max.getAndSet (0L);
		// Empty period: report zeros, not Long.MAX_VALUE.
		if (bucketSum == 0L) {
			minVal = 0L;
			maxVal = 0L;
			totalNanos = 0L;
		} else if (minVal == Long.MAX_VALUE) {
			// Shouldn't happen if bucketSum > 0, but defensively normalize.
			minVal = 0L;
		}
		return new Snapshot (buckCopy, bucketSum, totalNanos, minVal, maxVal);
	}

	/**
	 * Read-only snapshot of histogram state. Returned by
	 * {@link #snapshotAndReset()}; never mutated after construction.
	 */
	public static final class Snapshot {

		private final long[] buckets;
		private final long count;
		private final long totalNanos;
		private final long minNanos;
		private final long maxNanos;

		Snapshot (long[] buckets, long count, long totalNanos, long minNanos, long maxNanos) {
			this.buckets = buckets;
			this.count = count;
			this.totalNanos = totalNanos;
			this.minNanos = minNanos;
			this.maxNanos = maxNanos;
		}

		public long count () { return count; }
		public long totalNanos () { return totalNanos; }
		public long minNanos () { return minNanos; }
		public long maxNanos () { return maxNanos; }

		/**
		 * Estimates the p-th percentile (0.0..1.0) using linear interpolation
		 * inside the containing bucket. Returns 0 for an empty snapshot.
		 *
		 * <p>The returned value is in nanoseconds and is bounded by the
		 * containing bucket's edges, so a uniform / normal distribution
		 * estimate stays within ~10% of the analytical answer (sub-bucket
		 * relative width is 10^(1/4) - 1 ~= 78%, but the linear
		 * interpolation halves the worst case in practice).
		 */
		public long percentile (double p) {
			if (count <= 0L) return 0L;
			if (p <= 0.0) return minNanos;
			if (p >= 1.0) return maxNanos;
			double target = p * count;
			long cumulative = 0L;
			for (int b = 0; b < NUM_BUCKETS; b++) {
				long bv = buckets[b];
				if (bv == 0L) continue;
				if (cumulative + bv >= target) {
					// Geometric (log-linear) interpolation inside this
					// bucket. Buckets are log-spaced so a sample's natural
					// position within a bucket is more uniform in log
					// space than in linear space; this halves the
					// percentile estimation error compared to linear interp
					// at the wide-bucket tail (a 78%-wide bucket goes from
					// up-to-~33% mid-bucket linear error down to ~17%).
					double low = BUCKET_LOW_EDGE[b];
					double high = BUCKET_LOW_EDGE[b + 1];
					double frac = (target - cumulative) / bv;
					if (frac < 0.0) frac = 0.0;
					if (frac > 1.0) frac = 1.0;
					double estimate = low * Math.pow (high / low, frac);
					// Clamp into observed [min, max] -- this absorbs the
					// bucket-width error at the tails on small samples.
					if (estimate < minNanos) estimate = minNanos;
					if (estimate > maxNanos) estimate = maxNanos;
					return (long) estimate;
				}
				cumulative += bv;
			}
			return maxNanos;
		}

		/**
		 * Returns a defensive copy of the bucket counts. Test-only -- the
		 * production CSV-writer path doesn't need this.
		 */
		public long[] bucketsCopy () {
			long[] out = new long[buckets.length];
			System.arraycopy (buckets, 0, out, 0, buckets.length);
			return out;
		}
	}
}
