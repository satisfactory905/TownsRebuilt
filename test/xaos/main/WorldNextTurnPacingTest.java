package xaos.main;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldNextTurnPacingTest {

	private World world;

	@BeforeEach
	void setup () {
		world = new World ();
		world.setLastTurnTimeNanosForTest (0L);
		World.SPEED = 5;
		World.setTurnsPerSecond ();
	}

	@Test
	void shouldRunTick_firstCall_bootstrapsAndReturnsFalse () {
		boolean run = world.shouldRunTick (1_000_000_000L);
		assertFalse (run, "first call should bootstrap, not run a tick");
		assertEquals (1_000_000_000L, world.getLastTurnTimeNanosForTest ());
	}

	@Test
	void shouldRunTick_beforeIntervalElapses_returnsFalse () {
		world.setLastTurnTimeNanosForTest (1_000_000_000L);
		// SPEED 5 interval = 33_333_333 ns; check just before
		boolean run = world.shouldRunTick (1_000_000_000L + 33_333_332L);
		assertFalse (run);
	}

	@Test
	void shouldRunTick_atIntervalElapses_returnsTrue () {
		world.setLastTurnTimeNanosForTest (1_000_000_000L);
		boolean run = world.shouldRunTick (1_000_000_000L + 33_333_333L);
		assertTrue (run);
	}

	@Test
	void shouldRunTick_wayPastInterval_returnsTrueOnce_clockNotYetAdvanced () {
		// The check just says "yes, run a tick now". The post-work
		// markTickComplete is what advances the clock. shouldRunTick
		// itself does not mutate lastTurnTimeNanos in this branch.
		world.setLastTurnTimeNanosForTest (1_000_000_000L);
		boolean run = world.shouldRunTick (1_000_000_000L + 33_333_333L * 100);
		assertTrue (run);
		assertEquals (1_000_000_000L, world.getLastTurnTimeNanosForTest (),
			"shouldRunTick must not advance the clock; markTickComplete does");
	}

	@Test
	void markTickComplete_setsLastTurnTimeToProvidedValue () {
		world.setLastTurnTimeNanosForTest (1_000_000_000L);
		world.markTickComplete (5_000_000_000L);
		assertEquals (5_000_000_000L, world.getLastTurnTimeNanosForTest ());
	}

	@Test
	void shouldRunTick_thenMarkComplete_givesSimulatedNoCatchUpBehavior () {
		// Simulate: long pause (10x interval), then a tick fires, work takes
		// some time, post-work timestamp = work-end. The next interval
		// is then measured from work-end, not from the original lastTurnTime.
		world.setLastTurnTimeNanosForTest (1_000_000_000L);
		long longPause = 1_000_000_000L + 33_333_333L * 10;
		assertTrue (world.shouldRunTick (longPause));
		long workEnd = longPause + 50_000_000L;
		world.markTickComplete (workEnd);
		// Now check: at workEnd + interval - 1, no tick. At workEnd + interval, tick.
		assertFalse (world.shouldRunTick (workEnd + 33_333_332L));
		assertTrue (world.shouldRunTick (workEnd + 33_333_333L));
	}

	@Test
	void setTurnsPerSecond_speedFiveYieldsThirtyTurnsPerSecondInterval () {
		World.SPEED = 5;
		World.setTurnsPerSecond ();
		assertEquals (33_333_333L, World.getTurnIntervalNanosForTest ());
	}

	@Test
	void setTurnsPerSecond_speedOneYieldsApproximatelyFourPointThreeTurnsPerSecond () {
		World.SPEED = 1;
		World.setTurnsPerSecond ();
		assertEquals (233_333_333L, World.getTurnIntervalNanosForTest ());
	}

	@Test
	void setTurnsPerSecond_speedChangeUpdatesIntervalImmediately () {
		World.SPEED = 1;
		World.setTurnsPerSecond ();
		long slow = World.getTurnIntervalNanosForTest ();
		World.SPEED = 5;
		World.setTurnsPerSecond ();
		long fast = World.getTurnIntervalNanosForTest ();
		assertTrue (slow > fast, "speed 1 interval (" + slow + ") must be larger than speed 5 (" + fast + ")");
	}
}
