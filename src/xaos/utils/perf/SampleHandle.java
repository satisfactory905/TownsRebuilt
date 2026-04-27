package xaos.utils.perf;

import java.util.function.LongSupplier;

/**
 * Pre-resolved handle to a sampled-gauge metric. The dumper thread invokes
 * {@link #sample()} once per tick; the supplier returns whatever instant
 * value should be recorded into the CSV (e.g. heap MB used, queue depth).
 *
 * <p>Sample handles do not carry a histogram or counter -- they store the
 * latest sampled value for the dumper to read. If the category is disabled,
 * {@link #sample()} is a no-op and {@link #latestValue()} returns 0.
 */
public final class SampleHandle {

	private final String name;
	private final Category category;
	private final LongSupplier supplier;

	/**
	 * Tri-state: UNRESOLVED (until first read after init), DISABLED, or
	 * ENABLED. We track explicitly rather than re-using the supplier
	 * reference because a disabled handle should *never* invoke the user's
	 * supplier even on the slow path.
	 */
	private volatile int state = STATE_UNRESOLVED;
	static final int STATE_UNRESOLVED = 0;
	static final int STATE_DISABLED = 1;
	static final int STATE_ENABLED = 2;

	private volatile long latestValue = 0L;

	SampleHandle (String name, Category category, LongSupplier supplier) {
		this.name = name;
		this.category = category;
		this.supplier = supplier;
	}

	public String name () { return name; }
	public Category category () { return category; }

	/**
	 * Invoke the supplier (if enabled) and store its result. Called by the
	 * dumper thread once per period.
	 */
	public void sample () {
		int s = state;
		if (s == STATE_DISABLED) return;
		if (s == STATE_UNRESOLVED) {
			s = doResolve ();
			if (s != STATE_ENABLED) return;
		}
		try {
			latestValue = supplier.getAsLong ();
		} catch (Throwable t) {
			// Never let a misbehaving supplier kill the dumper thread.
			latestValue = 0L;
		}
	}

	/**
	 * Returns the most-recently-sampled value, or 0 if {@link #sample()} has
	 * never been called or the metric is disabled.
	 */
	public long latestValue () {
		return latestValue;
	}

	/**
	 * Whether this handle is enabled (i.e. the registry resolved it
	 * successfully). Until first use this triggers a deferred resolve.
	 */
	public boolean isEnabled () {
		int s = state;
		if (s == STATE_ENABLED) return true;
		if (s == STATE_DISABLED) return false;
		return doResolve () == STATE_ENABLED;
	}

	private int doResolve () {
		PerfStats.Registry r = PerfStats.registry ();
		if (r == null) return STATE_UNRESOLVED;
		boolean enabled = r.resolveSample (name, category);
		int newState = enabled ? STATE_ENABLED : STATE_DISABLED;
		state = newState;
		if (enabled) {
			r.registerSample (this);
		}
		return newState;
	}

	void invalidate () {
		this.state = STATE_UNRESOLVED;
		this.latestValue = 0L;
	}
}
