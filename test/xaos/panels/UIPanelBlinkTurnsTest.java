package xaos.panels;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UIPanelBlinkTurnsTest {

	private static final long ONE_SECOND_NANOS = 1_000_000_000L;

	@BeforeEach
	void resetBlinkState () {
		UIPanel.resetBlinkStateForTest ();
	}

	@Test
	void advanceBlinkTurns_atCycleStart_yieldsZero () {
		UIPanel.advanceBlinkTurns (0L);
		assertEquals (0, UIPanel.blinkTurns);
	}

	@Test
	void advanceBlinkTurns_atQuarterCycle_yieldsAroundQuarterMax () {
		UIPanel.advanceBlinkTurns (0L);
		UIPanel.advanceBlinkTurns (ONE_SECOND_NANOS / 4);
		// MAX_BLINK_TURNS = 30; 30/4 = 7 (integer truncation)
		assertEquals (7, UIPanel.blinkTurns);
	}

	@Test
	void advanceBlinkTurns_atHalfCycle_yieldsAroundHalfMax () {
		UIPanel.advanceBlinkTurns (0L);
		UIPanel.advanceBlinkTurns (ONE_SECOND_NANOS / 2);
		// 30/2 = 15
		assertEquals (15, UIPanel.blinkTurns);
	}

	@Test
	void advanceBlinkTurns_justBeforeCycleEnd_yieldsMaxMinusOne () {
		UIPanel.advanceBlinkTurns (0L);
		UIPanel.advanceBlinkTurns (ONE_SECOND_NANOS - 1);
		assertEquals (UIPanel.MAX_BLINK_TURNS - 1, UIPanel.blinkTurns);
	}

	@Test
	void advanceBlinkTurns_atCycleEnd_resetsToZero () {
		UIPanel.advanceBlinkTurns (0L);
		UIPanel.advanceBlinkTurns (ONE_SECOND_NANOS);
		assertEquals (0, UIPanel.blinkTurns);
	}

	@Test
	void advanceBlinkTurns_pastCycleEnd_resetsAndStartsFreshCycle () {
		UIPanel.advanceBlinkTurns (0L);
		UIPanel.advanceBlinkTurns (ONE_SECOND_NANOS + ONE_SECOND_NANOS / 4);
		// Cycle resets at 1s; the additional 1/4s puts us at ~7
		assertEquals (0, UIPanel.blinkTurns);
		UIPanel.advanceBlinkTurns (ONE_SECOND_NANOS + ONE_SECOND_NANOS / 2);
		// Now 1/2 second into the new cycle
		assertEquals (15, UIPanel.blinkTurns);
	}

	@Test
	void advanceBlinkTurns_blinkOnAtHalfCycle () {
		// The most common consumer pattern: blinkTurns >= MAX_BLINK_TURNS / 2
		// should be true exactly at and after the half-cycle mark.
		UIPanel.advanceBlinkTurns (0L);
		UIPanel.advanceBlinkTurns ((ONE_SECOND_NANOS / 2) - 1);
		assertTrue (UIPanel.blinkTurns < UIPanel.MAX_BLINK_TURNS / 2);
		UIPanel.advanceBlinkTurns (ONE_SECOND_NANOS / 2);
		assertTrue (UIPanel.blinkTurns >= UIPanel.MAX_BLINK_TURNS / 2);
	}
}
