package xaos.utils;

/**
 * Pure hit-detection utilities for UI and camera-edge logic.
 *
 * <p>Static helpers only -- this class has no instance state and cannot be
 * subclassed or instantiated. Kept intentionally small so its contents stay
 * discoverable; if a future addition would stray beyond hit-testing, split
 * into a new utility class rather than bloating this one.
 */
public final class HitDetection {

    private HitDetection() {
        // utility class -- no instances
    }

    /**
     * Returns true when the point (x, y) lies within the half-open rectangle
     * [rectX, rectX + rectW) x [rectY, rectY + rectH). Left and top edges
     * are inclusive; right and bottom are exclusive. This matches the inline
     * bounds-check pattern historically used across Game, UIPanel, and
     * MainPanel exactly -- do not change the edge semantics without auditing
     * every call site.
     */
    public static boolean isPointInRect(int x, int y, int rectX, int rectY, int rectW, int rectH) {
        return x >= rectX && x < rectX + rectW
            && y >= rectY && y < rectY + rectH;
    }
}
