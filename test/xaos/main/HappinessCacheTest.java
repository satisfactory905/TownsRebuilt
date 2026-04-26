package xaos.main;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import xaos.tiles.entities.items.ItemManager;
import xaos.tiles.entities.items.ItemManagerItem;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HappinessCacheTest {

    private static ItemManagerItem happyItemH1;  // happiness == 1
    private static ItemManagerItem happyItemH2;  // happiness == 2

    @BeforeAll
    static void loadHappyItems() {
        // Inject items via reflection FIRST (before any ItemManager.getItem() call)
        // so that ItemManager.itemList is non-null and loadItems() is never triggered.
        // loadItems() calls Towns.getPropertiesString() → loadPropertiesMain() →
        // Game.exit() when towns.ini is missing (always the case in unit-test JVMs).
        // Mirrors HappinessReferenceTest's approach exactly.
        injectSyntheticItems();
        happyItemH1 = ItemManager.getItem("woodenbed");
        happyItemH2 = ItemManager.getItem("purplethrone");
        if (happyItemH1 == null || happyItemH1.getHappiness() != 1) {
            throw new IllegalStateException("Test setup needs an item with happiness == 1");
        }
        if (happyItemH2 == null || happyItemH2.getHappiness() != 2) {
            throw new IllegalStateException("Test setup needs an item with happiness == 2");
        }
    }

    @SuppressWarnings("unchecked")
    private static void injectSyntheticItems() {
        try {
            java.lang.reflect.Field itemListField = ItemManager.class.getDeclaredField("itemList");
            itemListField.setAccessible(true);
            Map<String, ItemManagerItem> itemList = (Map<String, ItemManagerItem>) itemListField.get(null);
            if (itemList == null) {
                itemList = new HashMap<>();
                itemListField.set(null, itemList);
            }
            if (!itemList.containsKey("woodenbed")) {
                ItemManagerItem h1 = new ItemManagerItem("woodenbed", "furniture");
                h1.setHappiness(1);
                itemList.put("woodenbed", h1);
            }
            if (!itemList.containsKey("purplethrone")) {
                ItemManagerItem h2 = new ItemManagerItem("purplethrone", "furniture");
                h2.setHappiness(2);
                itemList.put("purplethrone", h2);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Cannot inject synthetic ItemManagerItems", e);
        }
    }

    @Test
    void cacheMatchesReferenceForSimpleSingleItemWorld() {
        // Fixture must be wide enough that scanning from positions 3..7 with
        // LOS=MAX_LOS=48 stays within array bounds. isInsideMap uses MAP_WIDTH=200
        // (a fixed constant), so any ix in [0, w) must be a valid array index.
        // Max scan index = 7 + 48 = 55, so we need w >= 56. Use 56x56.
        try (HappinessFixtures fx = new HappinessFixtures(56, 56, 1)) {
            fx.placeHappyItem(5, 5, 0, happyItemH1);
            HappinessCache cache = new HappinessCache(fx.w, fx.h, fx.d);

            for (int x = 3; x <= 7; x++) {
                for (int y = 3; y <= 7; y++) {
                    List<int[]> reference = HappinessReference.referenceVisibleHappyItems(x, y, 0, HappinessCache.MAX_LOS);
                    List<int[]> cached = cache.getVisibleHappyItems(x, y, 0);
                    assertEqualEntrySets(reference, cached, x, y, 0);
                }
            }
        }
    }

    @org.junit.jupiter.api.Test
    void cacheMatchesReferenceWithUnminedBlocker() {
        // 100 = 9 (max scan origin) + 48 (MAX_LOS) + buffer. Keeps array bounds safe.
        try (HappinessFixtures fx = new HappinessFixtures(100, 100, 1)) {
            fx.placeHappyItem(7, 10, 0, happyItemH2);
            // Unmined cell at (7, 8) blocks lines passing through.
            fx.setUnmined(7, 8, 0);

            HappinessCache cache = new HappinessCache(fx.w, fx.h, fx.d);

            for (int x = 5; x <= 9; x++) {
                for (int y = 5; y <= 9; y++) {
                    List<int[]> reference = HappinessReference.referenceVisibleHappyItems(x, y, 0, HappinessCache.MAX_LOS);
                    List<int[]> cached = cache.getVisibleHappyItems(x, y, 0);
                    assertEqualEntrySets(reference, cached, x, y, 0);
                }
            }
        }
    }

    @org.junit.jupiter.api.Test
    void cacheMatchesReferenceWithFluidBlocker() {
        // 100 = 9 (max scan origin) + 48 (MAX_LOS) + buffer. Keeps array bounds safe.
        try (HappinessFixtures fx = new HappinessFixtures(100, 100, 1)) {
            fx.placeHappyItem(7, 10, 0, happyItemH2);
            // Water at (7, 8) blocks LOS in current isCellAllowed semantics.
            fx.setFluid(7, 8, 0, xaos.tiles.terrain.Terrain.FLUIDS_WATER);

            HappinessCache cache = new HappinessCache(fx.w, fx.h, fx.d);

            for (int x = 5; x <= 9; x++) {
                for (int y = 5; y <= 9; y++) {
                    List<int[]> reference = HappinessReference.referenceVisibleHappyItems(x, y, 0, HappinessCache.MAX_LOS);
                    List<int[]> cached = cache.getVisibleHappyItems(x, y, 0);
                    assertEqualEntrySets(reference, cached, x, y, 0);
                }
            }
        }
    }

    @org.junit.jupiter.api.Test
    void cacheMatchesReferenceWithUndiscoveredBlocker() {
        // 100 = 9 (max scan origin) + 48 (MAX_LOS) + buffer. Keeps array bounds safe.
        try (HappinessFixtures fx = new HappinessFixtures(100, 100, 1)) {
            fx.placeHappyItem(7, 10, 0, happyItemH1);
            fx.setUndiscovered(7, 8, 0);

            HappinessCache cache = new HappinessCache(fx.w, fx.h, fx.d);

            for (int x = 5; x <= 9; x++) {
                for (int y = 5; y <= 9; y++) {
                    List<int[]> reference = HappinessReference.referenceVisibleHappyItems(x, y, 0, HappinessCache.MAX_LOS);
                    List<int[]> cached = cache.getVisibleHappyItems(x, y, 0);
                    assertEqualEntrySets(reference, cached, x, y, 0);
                }
            }
        }
    }

    @org.junit.jupiter.api.Test
    void cacheMatchesReferenceWithMultipleDistinctItems() {
        // 100 = 19 (max scan origin) + 48 (MAX_LOS) + buffer. Keeps array bounds safe.
        try (HappinessFixtures fx = new HappinessFixtures(100, 100, 1)) {
            fx.placeHappyItem(5, 5, 0, happyItemH1);    // happiness 1
            fx.placeHappyItem(10, 10, 0, happyItemH2);  // happiness 2
            fx.placeHappyItem(15, 5, 0, happyItemH1);   // happiness 1
            fx.placeHappyItem(7, 12, 0, happyItemH2);   // happiness 2

            HappinessCache cache = new HappinessCache(fx.w, fx.h, fx.d);

            for (int x = 0; x < 20; x++) {
                for (int y = 0; y < 20; y++) {
                    List<int[]> reference = HappinessReference.referenceVisibleHappyItems(x, y, 0, HappinessCache.MAX_LOS);
                    List<int[]> cached = cache.getVisibleHappyItems(x, y, 0);
                    assertEqualEntrySets(reference, cached, x, y, 0);
                }
            }
        }
    }

    /**
     * Strongest correctness check: equivalence across a deliberately-varied
     * world that mixes every blocker type (wall/unmined, fluid, undiscovered)
     * with multiple happy items at multiple happiness values. Iterates every
     * cell at multiple LOS values; if cache and reference ever disagree, the
     * failure message pinpoints the (x, y, los) reproduction case.
     *
     * This is the reference-equivalence "fuzzer" the spec calls for.
     */
    @org.junit.jupiter.api.Test
    void cacheMatchesReferenceForVariedWorldAcrossAllPositionsAndManyLOSValues() {
        try (HappinessFixtures fx = HappinessFixtures.buildVariedWorld(happyItemsMap())) {
            HappinessCache cache = new HappinessCache(fx.w, fx.h, fx.d);

            // Iterate every (x, y) with several LOS values that cover both
            // typical-citizen ranges (5-12) and edge cases at the bound.
            int[] losValues = { 1, 5, 10, 24, HappinessCache.MAX_LOS };
            for (int x = 0; x < 20; x++) {
                for (int y = 0; y < 20; y++) {
                    // Skip cells that are unmined / undiscovered / fluid: the
                    // citizen wouldn't be standing there anyway, and the
                    // reference scan from such a cell isn't a meaningful query.
                    xaos.tiles.Cell cell = World.getCell(x, y, 0);
                    if (!cell.isDiscovered() || !cell.isMined() || cell.getTerrain().hasFluids()) continue;

                    List<int[]> referenceFull = HappinessReference.referenceVisibleHappyItems(x, y, 0, HappinessCache.MAX_LOS);
                    List<int[]> cached = cache.getVisibleHappyItems(x, y, 0);
                    assertEqualEntrySets(referenceFull, cached, x, y, 0);

                    // Also verify the per-LOS filtering at the call-site shape:
                    // the reference at LOS L should match the cached entries
                    // filtered to distance <= L.
                    for (int los : losValues) {
                        List<int[]> referenceAtLOS = HappinessReference.referenceVisibleHappyItems(x, y, 0, los);
                        List<int[]> cachedAtLOS = new java.util.ArrayList<>();
                        for (int[] entry : cached) {
                            if (entry[1] <= los) cachedAtLOS.add(entry);
                        }
                        assertEqualEntrySets(referenceAtLOS, cachedAtLOS, x, y, 0);
                    }
                }
            }
        }
    }

    @org.junit.jupiter.api.Test
    void onItemPlaced_addsEntryToVisibleTilesAfterCacheBuilt() {
        try (HappinessFixtures fx = new HappinessFixtures(100, 100, 1)) {
            HappinessCache cache = new HappinessCache(fx.w, fx.h, fx.d);
            // Force the (5,5,0) tile to be built (currently empty).
            cache.getVisibleHappyItems(5, 5, 0);
            assertEquals(0, cache.getVisibleHappyItems(5, 5, 0).size());

            // Now place a happy item adjacent and notify cache.
            fx.placeHappyItem(5, 7, 0, happyItemH1);
            cache.onItemPlaced(5, 7, 0, happyItemH1.getHappiness());

            // Tile (5,5,0) should now see the item.
            List<int[]> result = cache.getVisibleHappyItems(5, 5, 0);
            assertEquals(1, result.size());
            assertEquals(happyItemH1.getHappiness(), result.get(0)[0]);
            assertEquals(2, result.get(0)[1]);
        }
    }

    @org.junit.jupiter.api.Test
    void onItemRemoved_removesEntryFromAffectedTiles() {
        try (HappinessFixtures fx = new HappinessFixtures(100, 100, 1)) {
            fx.placeHappyItem(5, 7, 0, happyItemH1);
            HappinessCache cache = new HappinessCache(fx.w, fx.h, fx.d);
            // Force build so the entry is in the cache.
            assertEquals(1, cache.getVisibleHappyItems(5, 5, 0).size());

            cache.onItemRemoved(5, 7, 0, happyItemH1.getHappiness());

            assertEquals(0, cache.getVisibleHappyItems(5, 5, 0).size());
        }
    }

    @org.junit.jupiter.api.Test
    void onItemPlaced_skipsZeroHappinessItems() {
        try (HappinessFixtures fx = new HappinessFixtures(100, 100, 1)) {
            HappinessCache cache = new HappinessCache(fx.w, fx.h, fx.d);
            cache.getVisibleHappyItems(5, 5, 0); // force build

            cache.onItemPlaced(5, 7, 0, 0);

            assertEquals(0, cache.getVisibleHappyItems(5, 5, 0).size(),
                "happiness=0 must be a no-op");
        }
    }

    /** Compares two lists ignoring order. Entries are int[]{happiness, distance}. */
    static void assertEqualEntrySets(List<int[]> expected, List<int[]> actual, int x, int y, int z) {
        assertEquals(expected.size(), actual.size(),
            "size mismatch at (" + x + "," + y + "," + z + "): expected=" + expected.size() + " actual=" + actual.size());
        java.util.Comparator<int[]> cmp = (a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]);
        List<int[]> e = new java.util.ArrayList<>(expected); e.sort(cmp);
        List<int[]> a = new java.util.ArrayList<>(actual); a.sort(cmp);
        for (int i = 0; i < e.size(); i++) {
            assertEquals(e.get(i)[0], a.get(i)[0], "happiness mismatch at index " + i + " for (" + x + "," + y + "," + z + ")");
            assertEquals(e.get(i)[1], a.get(i)[1], "distance mismatch at index " + i + " for (" + x + "," + y + "," + z + ")");
        }
    }

    /** Convenience: build a {1: happyItemH1, 2: happyItemH2} map for buildVariedWorld. */
    static Map<Integer, ItemManagerItem> happyItemsMap() {
        Map<Integer, ItemManagerItem> m = new HashMap<>();
        m.put(1, happyItemH1);
        m.put(2, happyItemH2);
        return m;
    }
}
