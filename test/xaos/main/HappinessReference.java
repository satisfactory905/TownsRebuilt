package xaos.main;

import java.util.ArrayList;
import java.util.List;

import xaos.tiles.Cell;
import xaos.tiles.entities.items.ItemManager;
import xaos.tiles.entities.items.ItemManagerItem;
import xaos.utils.Utils;

/**
 * Test-only reference implementation of the LOS happiness scan from
 * World.modifyHappiness(Citizen) at the time of the cache extraction.
 * Used solely as ground truth in HappinessCache equivalence tests.
 *
 * Returns the list of {happiness, distance} pairs visible from (x, y, z)
 * within the given LOS. Distance is Chebyshev (max(|dx|, |dy|)) matching
 * the original square-iteration boundary. The reservoir-sample / random
 * select happens at the call site in production; this helper returns the
 * pre-sample list so tests can compare sets.
 */
public final class HappinessReference {

    private HappinessReference() {}

    public static List<int[]> referenceVisibleHappyItems(int x, int y, int z, int los) {
        List<int[]> out = new ArrayList<>();
        for (int ix = x - los; ix <= x + los; ix++) {
            for (int iy = y - los; iy <= y + los; iy++) {
                if (!Utils.isInsideMap(ix, iy, z)) {
                    continue;
                }
                Cell cell = World.getCell(ix, iy, z);
                if (!cell.hasEntity()) {
                    continue;
                }
                ItemManagerItem imi = ItemManager.getItem(cell.getEntity().getIniHeader());
                if (imi == null || imi.getHappiness() == 0) {
                    continue;
                }
                boolean visible = (ix == x && iy == y)
                        || Utils.bresenhamLineExists(x, y, ix, iy, z)
                        || Utils.bresenhamLineExists(ix, iy, x, y, z);
                if (visible) {
                    int distance = Math.max(Math.abs(ix - x), Math.abs(iy - y));
                    out.add(new int[]{ imi.getHappiness(), distance });
                }
            }
        }
        return out;
    }
}
