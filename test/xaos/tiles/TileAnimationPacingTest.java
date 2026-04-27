package xaos.tiles;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import xaos.main.Game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TileAnimationPacingTest {

	private static final long ONE_FRAME_30FPS_NANOS = 33_333_333L;

	private Tile tile;
	private long durationNanos;

	@BeforeEach
	void setup () {
		tile = new Tile ();
		tile.setAnimationTiles (4);
		tile.setAnimationFrameDelay (7);   // 7 frames @ 30 FPS = ~233ms per anim step
		tile.setLastAnimationFrameNanosForTest (0L);
		durationNanos = 7L * ONE_FRAME_30FPS_NANOS;
	}

	@AfterEach
	void teardown () {
		Game.setFrameNowForTest (0L);
	}

	@Test
	void tryAdvanceAnimation_firstCall_bootstrapsAndDoesNotAdvance () {
		boolean advanced = tile.tryAdvanceAnimation (1_000_000_000L, durationNanos);
		assertFalse (advanced, "first call should bootstrap, not advance");
		// lastAnimationFrameNanos should now be in [now - duration, now]
		long last = tile.getLastAnimationFrameNanosForTest ();
		assertTrue (last >= 1_000_000_000L - durationNanos && last <= 1_000_000_000L,
			"bootstrap should set lastAnimationFrameNanos to a randomized phase, was " + last);
	}

	@Test
	void tryAdvanceAnimation_beforeIntervalElapses_doesNotAdvance () {
		tile.setLastAnimationFrameNanosForTest (1_000L);
		boolean advanced = tile.tryAdvanceAnimation (1_000L + durationNanos - 1, durationNanos);
		assertFalse (advanced);
	}

	@Test
	void tryAdvanceAnimation_atIntervalElapses_advancesOnce () {
		tile.setLastAnimationFrameNanosForTest (1_000L);
		boolean advanced = tile.tryAdvanceAnimation (1_000L + durationNanos, durationNanos);
		assertTrue (advanced);
		assertEquals (1_000L + durationNanos, tile.getLastAnimationFrameNanosForTest ());
	}

	@Test
	void tryAdvanceAnimation_wayPastInterval_advancesOnceNotMultiple () {
		// "No catch-up" rule: even if 10x the interval has passed, advance only once.
		tile.setLastAnimationFrameNanosForTest (1_000L);
		boolean advanced = tile.tryAdvanceAnimation (1_000L + durationNanos * 10, durationNanos);
		assertTrue (advanced);
		long last = tile.getLastAnimationFrameNanosForTest ();
		assertEquals (1_000L + durationNanos * 10, last,
			"timestamp advances to now (no catch-up), so the next interval starts here");
	}

	@Test
	void updateAnimation_doesNothingIfAnimationTilesIsOneOrLess () {
		tile.setAnimationTiles (1);
		int before = tile.getCurrentAnimationTile ();
		Game.setFrameNowForTest (10_000_000_000L);
		tile.updateAnimation (false);
		assertEquals (before, tile.getCurrentAnimationTile ());
	}
}
