package xaos.utils.perf;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Pre-resolved handle to a counter metric. Lazy-binding mirror of
 * {@link SpanHandle}. {@link #inc()} and {@link #add(long)} compile to a
 * volatile read + branch on the disabled path (~10 ns) or a single
 * {@code AtomicLong} update on the enabled path (~5 ns).
 */
public final class CounterHandle {

	private final String name;
	private final Category category;

	private volatile AtomicLong backing;

	CounterHandle (String name, Category category) {
		this.name = name;
		this.category = category;
	}

	public String name () { return name; }
	public Category category () { return category; }

	public void inc () {
		AtomicLong a = resolve ();
		if (a == null) return;
		a.incrementAndGet ();
	}

	public void add (long n) {
		AtomicLong a = resolve ();
		if (a == null) return;
		a.addAndGet (n);
	}

	private AtomicLong resolve () {
		AtomicLong a = backing;
		if (a == PerfStats.DISABLED_COUNTER) return null;
		if (a != null) return a;
		PerfStats.Registry r = PerfStats.registry ();
		if (r == null) return null;
		AtomicLong resolved = r.resolveCounter (name, category);
		if (resolved == null) {
			backing = PerfStats.DISABLED_COUNTER;
			return null;
		}
		backing = resolved;
		return resolved;
	}

	void invalidate () {
		this.backing = null;
	}
}
