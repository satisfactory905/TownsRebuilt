package xaos.utils.perf;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfStatsTest {

	@BeforeEach
	void setUp () {
		PerfStats.resetForTesting ();
	}

	@AfterEach
	void tearDown () {
		PerfStats.resetForTesting ();
	}

	private static PerfStatsConfig configWithCategories (Path csvPath, Category... cats) {
		EnumSet<Category> set = EnumSet.noneOf (Category.class);
		for (Category c : cats) set.add (c);
		return new PerfStatsConfig (true, csvPath, 1000L, set);
	}

	private static PerfStatsConfig configMasterOff (Path csvPath) {
		return new PerfStatsConfig (false, csvPath, 1000L, EnumSet.allOf (Category.class));
	}

	// ---------------------------------------------------------------- handle resolution

	@Test
	void span_handleCreated_thenInit_resolvesToHistogram () {
		// Handle declared *before* init. Spec calls this out: lazy
		// resolution, decoupled from class-load order.
		SpanHandle handle = PerfStats.span ("test.metric", Category.RENDERING_FRAME);
		assertNotNull (handle);

		// Before init, calls are no-ops (no NPE, no exception).
		try (Span s = handle.start ()) {
			// must not throw
		}

		PerfStats.init (configWithCategories (null, Category.RENDERING_FRAME));
		// Now records should land in a real histogram.
		try (Span s = handle.start ()) {
			Thread.sleep (1);
		} catch (InterruptedException ie) {
			Thread.currentThread ().interrupt ();
		}

		PerfStats.Registry r = PerfStats.registry ();
		assertNotNull (r);
		Histogram h = r.resolveSpan ("test.metric", Category.RENDERING_FRAME);
		assertNotNull (h);
		Histogram.Snapshot s = h.snapshotAndReset ();
		assertEquals (1L, s.count ());
	}

	@Test
	void counter_handleCreated_thenInit_increments () {
		CounterHandle c = PerfStats.counter ("c.metric", Category.RENDERING_GL);
		// Pre-init: no-op.
		c.inc ();
		c.add (5);
		assertEquals (null, PerfStats.registry ());

		PerfStats.init (configWithCategories (null, Category.RENDERING_GL));
		c.inc ();
		c.add (10);

		PerfStats.Registry r = PerfStats.registry ();
		assertEquals (11L, r.resolveCounter ("c.metric", Category.RENDERING_GL).get ());
	}

	@Test
	void span_subsequentResolves_reuseSameHistogram () {
		PerfStats.init (configWithCategories (null, Category.ENGINE_SIM));
		PerfStats.Registry r = PerfStats.registry ();
		Histogram first = r.resolveSpan ("re.use", Category.ENGINE_SIM);
		Histogram second = r.resolveSpan ("re.use", Category.ENGINE_SIM);
		assertSame (first, second);
	}

	// ---------------------------------------------------------------- category gating

	@Test
	void disabledCategory_spanIsNoOp () {
		// Master on, but RENDERING_FRAME is NOT in the enabled set.
		PerfStats.init (configWithCategories (null, Category.ENGINE_SIM));
		SpanHandle disabled = PerfStats.span ("disabled.span", Category.RENDERING_FRAME);
		// 5 records into a disabled handle should NOT show up in any
		// histogram in the registry.
		for (int i = 0; i < 5; i++) {
			try (Span s = disabled.start ()) {
				// closes cleanly even though disabled
			}
		}
		// Verify the histogram was never created.
		PerfStats.Registry r = PerfStats.registry ();
		Histogram h = r.resolveSpan ("disabled.span", Category.RENDERING_FRAME);
		// resolveSpan returns null for a disabled category.
		assertEquals (null, h);
	}

	@Test
	void disabledCategory_counterIsNoOp () {
		PerfStats.init (configWithCategories (null, Category.ENGINE_SIM));
		CounterHandle c = PerfStats.counter ("disabled.c", Category.RENDERING_GL);
		c.inc ();
		c.add (100);
		PerfStats.Registry r = PerfStats.registry ();
		assertEquals (null, r.resolveCounter ("disabled.c", Category.RENDERING_GL));
	}

	@Test
	void masterDisabled_everythingNoOp () {
		PerfStats.init (configMasterOff (null));
		SpanHandle sh = PerfStats.span ("any", Category.RENDERING_FRAME);
		CounterHandle ch = PerfStats.counter ("any.c", Category.ENGINE_SIM);
		try (Span s = sh.start ()) { /* nothing */ }
		ch.inc ();
		PerfStats.Registry r = PerfStats.registry ();
		// Master off => no histogram or counter is ever created.
		assertEquals (null, r.resolveSpan ("any", Category.RENDERING_FRAME));
		assertEquals (null, r.resolveCounter ("any.c", Category.ENGINE_SIM));
	}

	// ---------------------------------------------------------------- recordRaw

	@Test
	void recordRaw_equivalentToSpanClose () {
		PerfStats.init (configWithCategories (null, Category.ENGINE_GC));
		SpanHandle h = PerfStats.span ("gc.x", Category.ENGINE_GC);
		// Record both ways with the same nominal duration.
		h.recordRaw (5_000_000L);
		h.recordRaw (10_000_000L);

		PerfStats.Registry r = PerfStats.registry ();
		Histogram hist = r.resolveSpan ("gc.x", Category.ENGINE_GC);
		Histogram.Snapshot snap = hist.snapshotAndReset ();
		assertEquals (2L, snap.count ());
		assertEquals (15_000_000L, snap.totalNanos ());
		assertEquals (5_000_000L, snap.minNanos ());
		assertEquals (10_000_000L, snap.maxNanos ());
	}

	@Test
	void recordRaw_disabledHandle_isNoOp () {
		PerfStats.init (configWithCategories (null, Category.ENGINE_SIM));
		// ENGINE_GC is NOT enabled, so recordRaw must drop.
		SpanHandle h = PerfStats.span ("gc.disabled", Category.ENGINE_GC);
		h.recordRaw (1_000_000L);
		PerfStats.Registry r = PerfStats.registry ();
		assertEquals (null, r.resolveSpan ("gc.disabled", Category.ENGINE_GC));
	}

	// ---------------------------------------------------------------- shutdown / re-init

	@Test
	void shutdown_invalidatesHandles_reInitRebinds () {
		// First run: collect a record.
		PerfStats.init (configWithCategories (null, Category.RENDERING_FRAME));
		SpanHandle h = PerfStats.span ("life.cycle", Category.RENDERING_FRAME);
		try (Span s = h.start ()) { /* nothing */ }
		PerfStats.Registry r1 = PerfStats.registry ();
		assertNotNull (r1);
		Histogram h1 = r1.resolveSpan ("life.cycle", Category.RENDERING_FRAME);
		assertNotNull (h1);

		// Shutdown: cached binding must be cleared.
		PerfStats.shutdown ();
		assertEquals (null, PerfStats.registry ());

		// Re-init: same handle must work again.
		PerfStats.init (configWithCategories (null, Category.RENDERING_FRAME));
		try (Span s = h.start ()) { /* nothing */ }
		PerfStats.Registry r2 = PerfStats.registry ();
		Histogram h2 = r2.resolveSpan ("life.cycle", Category.RENDERING_FRAME);
		assertNotNull (h2);
		// New histogram instance (registry is fresh).
		assertTrue (h2.snapshotAndReset ().count () == 1L);
	}
}
