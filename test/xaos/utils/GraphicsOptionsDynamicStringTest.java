package xaos.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import xaos.main.Game;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the __VSYNC__ and __FPS_CAP__ token substitutions added to
 * {@link Utils#getDynamicString(String)} for the Graphics options menu.
 *
 * The dynamic-string mechanism is what gives the menu rows their live values
 * (e.g. the "VSync: ON" label flipping to "VSync: OFF" on click). Both tokens
 * are pure functions of {@code Game.isVsync()} and {@code Game.FPS_CAP}, so
 * they can be exhaustively tested without any UI scaffolding.
 *
 * The {@code Game} class holds these as static fields; each test snapshots and
 * restores them so test order does not matter.
 */
class GraphicsOptionsDynamicStringTest {

    private boolean originalVsync;
    private int originalFpsCap;

    @BeforeEach
    void snapshotGameState() {
        originalVsync = Game.isVsync();
        originalFpsCap = Game.FPS_CAP;
    }

    @AfterEach
    void restoreGameState() {
        Game.setVsync(originalVsync);
        Game.FPS_CAP = originalFpsCap;
    }

    // -- __VSYNC__ ---------------------------------------------------------

    @Test
    void vsyncToken_resolvesToOn_whenVsyncTrue() {
        Game.setVsync(true);
        assertEquals("ON", Utils.getDynamicString("__VSYNC__"));
    }

    @Test
    void vsyncToken_resolvesToOff_whenVsyncFalse() {
        Game.setVsync(false);
        assertEquals("OFF", Utils.getDynamicString("__VSYNC__"));
    }

    @Test
    void vsyncToken_substitutesInsideLabel() {
        Game.setVsync(true);
        assertEquals("VSync ON", Utils.getDynamicString("VSync __VSYNC__"));
    }

    // -- __FPS_CAP__ -------------------------------------------------------

    @Test
    void fpsCapToken_resolvesToUnlimited_whenZero() {
        Game.FPS_CAP = 0;
        assertEquals("Unlimited", Utils.getDynamicString("__FPS_CAP__"));
    }

    @Test
    void fpsCapToken_resolvesToNumeric_whenPositive() {
        Game.FPS_CAP = 60;
        assertEquals("60", Utils.getDynamicString("__FPS_CAP__"));
    }

    @Test
    void fpsCapToken_resolvesToNumeric_forOffListValue() {
        // Out-of-list values must display honestly — see design spec.
        Game.FPS_CAP = 75;
        assertEquals("75", Utils.getDynamicString("__FPS_CAP__"));
    }

    @Test
    void fpsCapToken_resolvesToUnlimited_whenNegative() {
        // Defensive: <= 0 collapses to "Unlimited" rather than printing "-1".
        Game.FPS_CAP = -1;
        assertEquals("Unlimited", Utils.getDynamicString("__FPS_CAP__"));
    }

    @Test
    void fpsCapToken_substitutesInsideLabel() {
        Game.FPS_CAP = 144;
        assertEquals("FPS Cap: 144", Utils.getDynamicString("FPS Cap: __FPS_CAP__"));
    }

    // -- Cross-cutting -----------------------------------------------------

    @Test
    void bothTokens_resolveInOneString() {
        Game.setVsync(false);
        Game.FPS_CAP = 240;
        assertEquals("VSync OFF / FPS 240",
                Utils.getDynamicString("VSync __VSYNC__ / FPS __FPS_CAP__"));
    }

    @Test
    void noTokens_passesThroughUnchanged() {
        Game.setVsync(true);
        Game.FPS_CAP = 60;
        assertEquals("plain label", Utils.getDynamicString("plain label"));
    }

    @Test
    void empty_passesThroughUnchanged() {
        assertEquals("", Utils.getDynamicString(""));
    }
}
