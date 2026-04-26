package xaos.main;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-tile cache of happy items visible from each cell. See
 * docs/superpowers/specs/2026-04-24-happiness-cache-design.md for the full
 * design. Owned by World; one instance per World.
 *
 * Threading: main-thread-only. AStar background thread does not access this
 * cache. No synchronization.
 */
public final class HappinessCache {

    /**
     * Maximum LOSCurrent value supported. Citizens with LOSCurrent > MAX_LOS
     * will see only items within distance MAX_LOS. Computed from XML data
     * as base 12 + 4 slots * (max item LOS 5 + max prefix LOS 4) = 48.
     * Excludes the LOSPCT (180%) effect multiplier from "Gods lightning".
     */
    public static final int MAX_LOS = 48;

    private final int w;
    private final int h;
    private final int d;

    /**
     * Per-cell list of int[]{happiness, distance} entries. null when no
     * entries have been computed yet OR when no happy items are visible
     * from the cell. Both cases are indistinguishable at read time, which
     * is intentional: both yield "nothing to sample" at query time.
     */
    private final List<int[]>[][][] cells;

    /**
     * Marks tiles whose cache is out of date. Wall/mining/discovery events
     * mark tiles dirty; the next read rebuilds them lazily. Item events
     * update directly without dirty-marking.
     */
    private final boolean[][][] dirty;

    @SuppressWarnings("unchecked")
    public HappinessCache(int w, int h, int d) {
        this.w = w;
        this.h = h;
        this.d = d;
        this.cells = (List<int[]>[][][]) new List<?>[w][h][d];
        this.dirty = new boolean[w][h][d];
        // All tiles start dirty so the first read forces a build.
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                for (int z = 0; z < d; z++) {
                    dirty[x][y][z] = true;
                }
            }
        }
    }

    /**
     * Returns the list of {happiness, distance} entries visible from
     * (x, y, z). Lazily rebuilds the tile if dirty. The returned list is
     * the unfiltered cache (entries up to MAX_LOS); callers filter by
     * the citizen's actual LOSCurrent during reservoir sampling.
     *
     * Caller MUST NOT modify the returned list.
     */
    public List<int[]> getVisibleHappyItems(int x, int y, int z) {
        if (dirty[x][y][z]) {
            buildCacheForTile(x, y, z);
        }
        List<int[]> list = cells[x][y][z];
        return list != null ? list : java.util.Collections.emptyList();
    }

    /** See spec; called when a happy item is placed at (x, y, z). */
    public void onItemPlaced(int x, int y, int z, int happiness) {
        if (happiness == 0) return;
        for (int tx = x - MAX_LOS; tx <= x + MAX_LOS; tx++) {
            for (int ty = y - MAX_LOS; ty <= y + MAX_LOS; ty++) {
                if (!xaos.utils.Utils.isInsideMap(tx, ty, z)) continue;
                // If the tile is currently dirty (cache will be rebuilt on next read),
                // there's no list to update — skip the bresenham + add.
                if (dirty[tx][ty][z]) continue;
                // If the tile has never been built, no entry exists; skip.
                if (cells[tx][ty][z] == null) continue;
                boolean visible = (tx == x && ty == y)
                        || xaos.utils.Utils.bresenhamLineExists(tx, ty, x, y, z)
                        || xaos.utils.Utils.bresenhamLineExists(x, y, tx, ty, z);
                if (visible) {
                    int distance = Math.max(Math.abs(tx - x), Math.abs(ty - y));
                    cells[tx][ty][z].add(new int[]{ happiness, distance });
                }
            }
        }
    }

    /** See spec; called when a happy item is removed from (x, y, z). */
    public void onItemRemoved(int x, int y, int z, int happiness) {
        if (happiness == 0) return;
        for (int tx = x - MAX_LOS; tx <= x + MAX_LOS; tx++) {
            for (int ty = y - MAX_LOS; ty <= y + MAX_LOS; ty++) {
                if (!xaos.utils.Utils.isInsideMap(tx, ty, z)) continue;
                if (dirty[tx][ty][z]) continue;
                if (cells[tx][ty][z] == null) continue;
                boolean visible = (tx == x && ty == y)
                        || xaos.utils.Utils.bresenhamLineExists(tx, ty, x, y, z)
                        || xaos.utils.Utils.bresenhamLineExists(x, y, tx, ty, z);
                if (visible) {
                    int distance = Math.max(Math.abs(tx - x), Math.abs(ty - y));
                    // Remove the first matching entry. If multiple identical happy
                    // items overlap (rare), removing one is correct since each item
                    // contributes one entry.
                    java.util.List<int[]> list = cells[tx][ty][z];
                    for (int i = 0; i < list.size(); i++) {
                        int[] entry = list.get(i);
                        if (entry[0] == happiness && entry[1] == distance) {
                            list.remove(i);
                            break;
                        }
                    }
                }
            }
        }
    }

    /** See spec; called when a wall is built/destroyed/locked-changed at (x, y, z). */
    public void onWallChanged(int x, int y, int z) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    /** See spec; called when a cell's mined state changes at (x, y, z). */
    public void onMiningChanged(int x, int y, int z) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    /** See spec; called when a cell at (x, y, z) is discovered for the first time. */
    public void onDiscovered(int x, int y, int z) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    private void buildCacheForTile(int x, int y, int z) {
        List<int[]> list = new ArrayList<>();
        for (int ix = x - MAX_LOS; ix <= x + MAX_LOS; ix++) {
            for (int iy = y - MAX_LOS; iy <= y + MAX_LOS; iy++) {
                if (!xaos.utils.Utils.isInsideMap(ix, iy, z)) continue;
                xaos.tiles.Cell cell = World.getCell(ix, iy, z);
                if (!cell.hasEntity()) continue;
                xaos.tiles.entities.items.ItemManagerItem imi =
                    xaos.tiles.entities.items.ItemManager.getItem(cell.getEntity().getIniHeader());
                if (imi == null || imi.getHappiness() == 0) continue;
                boolean visible = (ix == x && iy == y)
                        || xaos.utils.Utils.bresenhamLineExists(x, y, ix, iy, z)
                        || xaos.utils.Utils.bresenhamLineExists(ix, iy, x, y, z);
                if (visible) {
                    int distance = Math.max(Math.abs(ix - x), Math.abs(iy - y));
                    list.add(new int[]{ imi.getHappiness(), distance });
                }
            }
        }
        cells[x][y][z] = list;
        dirty[x][y][z] = false;
    }
}
