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

    /**
     * Notifies the cache that a wall was built, destroyed, or had its lock state
     * changed at (x, y, z). Marks every tile within {@code 2 * MAX_LOS} of the
     * change as dirty for lazy rebuild on next read.
     *
     * <p>Caller should only invoke when the world state actually changed.
     * Redundant calls are safe but cost a {@code 2 * MAX_LOS} neighborhood
     * dirty-mark for nothing.
     */
    public void onWallChanged(int x, int y, int z) {
        markNeighborhoodDirty(x, y, z);
    }

    /**
     * Notifies the cache that a cell's mined state changed at (x, y, z)
     * (e.g., a tile was mined out or a new rock deposit appeared). Marks
     * every tile within {@code 2 * MAX_LOS} of the change as dirty for
     * lazy rebuild on next read.
     *
     * <p>Caller should only invoke when the world state actually changed.
     * Redundant calls are safe but cost a {@code 2 * MAX_LOS} neighborhood
     * dirty-mark for nothing.
     */
    public void onMiningChanged(int x, int y, int z) {
        markNeighborhoodDirty(x, y, z);
    }

    /**
     * Notifies the cache that a cell at (x, y, z) had its discovery state
     * change (e.g., a tile was first revealed by a citizen). Marks every
     * tile within {@code 2 * MAX_LOS} of the change as dirty for lazy
     * rebuild on next read.
     *
     * <p>Caller should only invoke when the world state actually changed.
     * Redundant calls are safe but cost a {@code 2 * MAX_LOS} neighborhood
     * dirty-mark for nothing.
     */
    public void onDiscovered(int x, int y, int z) {
        markNeighborhoodDirty(x, y, z);
    }

    /**
     * Marks every tile within a 2*MAX_LOS radius of (x, y, z) as dirty.
     * X and Y are clamped to [0, w-1] and [0, h-1] to avoid array-bounds
     * exceptions when the changed cell is near the map edge. Z is not clamped
     * because Z layers are independent in the current model.
     */
    private void markNeighborhoodDirty(int x, int y, int z) {
        // 2*MAX_LOS: a wall at W can affect lines passing through W from any pair
        // (A, B) where each is up to MAX_LOS from W.
        int radius = 2 * MAX_LOS;
        int xMin = Math.max(0, x - radius);
        int xMax = Math.min(w - 1, x + radius);
        int yMin = Math.max(0, y - radius);
        int yMax = Math.min(h - 1, y + radius);
        for (int tx = xMin; tx <= xMax; tx++) {
            for (int ty = yMin; ty <= yMax; ty++) {
                dirty[tx][ty][z] = true;
            }
        }
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
