package xaos.utils.perf;

/**
 * Auto-closeable scope object returned by {@link SpanHandle#start()}.
 *
 * <p>On {@link #close()} it records {@code System.nanoTime() - startNanos}
 * into the owning handle's histogram. Closing twice is harmless: the second
 * close is a no-op because the histogram reference is cleared.
 *
 * <p>Two flavors of Span exist:
 * <ul>
 *   <li>An {@code ENABLED} instance that stores a back-reference to the
 *       {@link Histogram} it should write to and the start-time stamp.
 *   <li>A {@link #DISABLED} singleton that does nothing on close. The hot
 *       path returns this when the underlying handle is disabled, so a
 *       try-with-resources block for a disabled category boils down to a
 *       single field load + a single branch.
 * </ul>
 */
public final class Span implements AutoCloseable {

	/**
	 * Shared no-op Span. Returned by every disabled {@link SpanHandle} so
	 * that a try-with-resources block compiles to no observable work on the
	 * hot path.
	 */
	public static final Span DISABLED = new Span (null, 0L);

	private Histogram backing;
	private long startNanos;

	Span (Histogram backing, long startNanos) {
		this.backing = backing;
		this.startNanos = startNanos;
	}

	/**
	 * Reuses this Span object for a new measurement. Used by the enabled
	 * handle to avoid allocating a fresh Span per call -- but only when the
	 * caller doesn't share the Span across threads. Currently unused; the
	 * production path allocates a fresh Span per start() to keep the model
	 * trivially thread-safe. Kept for future micro-optimization.
	 */
	void rearm (Histogram backing, long startNanos) {
		this.backing = backing;
		this.startNanos = startNanos;
	}

	@Override
	public void close () {
		Histogram h = this.backing;
		if (h == null) return;
		long elapsed = System.nanoTime () - startNanos;
		h.record (elapsed);
		// Clear so a stray re-close is a no-op rather than double-count.
		this.backing = null;
	}
}
