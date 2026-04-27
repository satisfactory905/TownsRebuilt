package xaos.utils.perf;

/**
 * Pre-resolved handle to a span metric. Declare once as a {@code static final}
 * next to the code being measured, then call {@link #start()} (or
 * {@link #recordRaw(long)} for externally-measured durations) on the hot
 * path.
 *
 * <p>Handles support the spec's lazy-resolution model: it's safe to declare
 * a handle in a class loaded *before* {@link PerfStats#init(PerfStatsConfig)}
 * runs. The handle binds to its backing {@link Histogram} on first use after
 * init -- before init, every {@code start()} returns {@link Span#DISABLED}.
 *
 * <p>Once init has run with the owning {@link Category} disabled, the handle
 * locks into a permanent disabled state with no further registry lookups.
 */
public final class SpanHandle {

	private final String name;
	private final Category category;

	/**
	 * Resolution state. {@code null} = not yet resolved (re-check the
	 * registry on next call). {@code DISABLED_HISTOGRAM} = resolved to
	 * disabled, fast no-op forever. Anything else = the live histogram.
	 *
	 * <p>volatile so the dumper thread, the recording thread, and JMX
	 * listener threads all see a consistent view without locking.
	 */
	private volatile Histogram backing;

	SpanHandle (String name, Category category) {
		this.name = name;
		this.category = category;
	}

	public String name () { return name; }
	public Category category () { return category; }

	/**
	 * Begin a measurement. If the metric is enabled, returns a fresh Span
	 * whose close() records the elapsed nanos into the histogram. If
	 * disabled, returns {@link Span#DISABLED} which closes for free.
	 */
	public Span start () {
		Histogram h = resolve ();
		if (h == null) return Span.DISABLED;
		return new Span (h, System.nanoTime ());
	}

	/**
	 * Record a duration that was measured externally (e.g. from a JMX GC
	 * notification's {@code getDuration()}). Unit: nanoseconds. Disabled
	 * handles drop the call.
	 */
	public void recordRaw (long durationNanos) {
		Histogram h = resolve ();
		if (h == null) return;
		h.record (durationNanos);
	}

	/**
	 * Force-resolves the handle and returns the backing histogram, or
	 * {@code null} if the metric is disabled. Keeps the slow path off the
	 * common case: once the handle is bound, the volatile read is the only
	 * cost.
	 */
	private Histogram resolve () {
		Histogram h = backing;
		if (h == PerfStats.DISABLED_HISTOGRAM) return null;
		if (h != null) return h;
		// Slow path: first call (or pre-init call). Look up the live
		// registry from PerfStats -- *not* a captured-at-construction
		// reference, because handles may be declared before init runs.
		PerfStats.Registry r = PerfStats.registry ();
		if (r == null) return null;
		Histogram resolved = r.resolveSpan (name, category);
		if (resolved == null) {
			// Disabled forever: store the sentinel so we never re-check.
			backing = PerfStats.DISABLED_HISTOGRAM;
			return null;
		}
		backing = resolved;
		return resolved;
	}

	/**
	 * Drops any cached resolution. Called by PerfStats.shutdown() so a
	 * subsequent re-init binds against the new registry. Test-only in
	 * practice.
	 */
	void invalidate () {
		this.backing = null;
	}
}
