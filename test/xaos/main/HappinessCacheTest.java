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

    @org.junit.jupiter.api.Test
    void onWallChanged_marksAffectedTilesDirtyForLazyRebuild() {
        try (HappinessFixtures fx = new HappinessFixtures(100, 100, 1)) {
            fx.placeHappyItem(7, 10, 0, happyItemH2);
            HappinessCache cache = new HappinessCache(fx.w, fx.h, fx.d);

            // Build (7, 5, 0) — citizen tile sees item across open ground.
            List<int[]> beforeWall = cache.getVisibleHappyItems(7, 5, 0);
            assertEquals(1, beforeWall.size(), "before wall, item should be visible");

            // Build a wall blocking the bresenham line.
            fx.setUnmined(7, 8, 0);
            cache.onWallChanged(7, 8, 0);

            // Cache should rebuild on next read; item should no longer be visible.
            List<int[]> afterWall = cache.getVisibleHappyItems(7, 5, 0);
            // Verify against reference for ground truth:
            List<int[]> reference = HappinessReference.referenceVisibleHappyItems(7, 5, 0, HappinessCache.MAX_LOS);
            assertEqualEntrySets(reference, afterWall, 7, 5, 0);
        }
    }

    @org.junit.jupiter.api.Test
    void onMiningChanged_marksAffectedTilesDirtyForLazyRebuild() {
        try (HappinessFixtures fx = new HappinessFixtures(100, 100, 1)) {
            fx.placeHappyItem(7, 10, 0, happyItemH2);
            HappinessCache cache = new HappinessCache(fx.w, fx.h, fx.d);

            // Before mining change: item visible across open ground.
            List<int[]> before = cache.getVisibleHappyItems(7, 5, 0);
            assertEquals(1, before.size(), "before mining change, item should be visible");

            // Cell at (7, 8) becomes unmined (a freshly-deposited blocker).
            // This is the mining-state-changed event the cache must respond to.
            fx.setUnmined(7, 8, 0);
            cache.onMiningChanged(7, 8, 0);

            // Cache should rebuild on next read; item should no longer be visible.
            List<int[]> after = cache.getVisibleHappyItems(7, 5, 0);
            List<int[]> reference = HappinessReference.referenceVisibleHappyItems(7, 5, 0, HappinessCache.MAX_LOS);
            assertEqualEntrySets(reference, after, 7, 5, 0);
            assertEquals(0, after.size(),
                "item must no longer be visible after mining change blocks the line");
        }
    }

    @org.junit.jupiter.api.Test
    void onDiscovered_marksAffectedTilesDirtyForLazyRebuild() {
        try (HappinessFixtures fx = new HappinessFixtures(100, 100, 1)) {
            fx.placeHappyItem(7, 10, 0, happyItemH1);
            HappinessCache cache = new HappinessCache(fx.w, fx.h, fx.d);

            // Before discovery change: item visible across open (discovered) ground.
            List<int[]> before = cache.getVisibleHappyItems(7, 5, 0);
            assertEquals(1, before.size(), "before discovery change, item should be visible");

            // Set (7, 8) undiscovered — this models the inverse of a discovery
            // event for testing purposes, since `Cell.setDiscovered(true)` NPEs
            // in test contexts (it calls Game.getWorld().discoverFloor()). The
            // cache's dirty-marking behavior is symmetric: it doesn't care
            // which direction the discovery flag flipped, only that something
            // changed. We invoke onDiscovered after the change to verify it
            // marks the neighborhood dirty for rebuild.
            fx.setUndiscovered(7, 8, 0);
            cache.onDiscovered(7, 8, 0);

            // Cache should rebuild on next read; item should no longer be visible
            // (line passes through an undiscovered cell, which isCellAllowed treats
            // as a blocker).
            List<int[]> after = cache.getVisibleHappyItems(7, 5, 0);
            List<int[]> reference = HappinessReference.referenceVisibleHappyItems(7, 5, 0, HappinessCache.MAX_LOS);
            assertEqualEntrySets(reference, after, 7, 5, 0);
            assertEquals(0, after.size(),
                "item must no longer be visible after discovery state change blocks the line");
        }
    }

    /**
     * Save/load equivalence: the HappinessCache is a transient field on World
     * (not serialized). On save, the cache is dropped; on load, World.readExternal
     * constructs a fresh HappinessCache(w,h,d) at the end (all tiles dirty), and
     * the next read lazily rebuilds.
     *
     * Doing a true Externalizable byte-level round-trip would require constructing
     * a complete World with Game dependencies (out of scope for the synthetic
     * fixture infrastructure). Instead, we verify the *logical equivalent*:
     * a cache that arrives at a state via incremental hooks must produce the
     * same visibility as a freshly-constructed cache against the same final
     * world state. If this holds, save/load is trivially correct (load is just
     * "discard old cache, build fresh").
     */
    @org.junit.jupiter.api.Test
    void saveLoadEquivalence_freshCacheMatchesCacheBuiltViaHooks() {
        try (HappinessFixtures fx = HappinessFixtures.buildVariedWorld(happyItemsMap())) {
            // cache_A: built against the initial varied world. Force build at a
            // few sample positions so any later hook calls operate on already-
            // populated tiles (the dirty-marking incremental code paths).
            HappinessCache cacheA = new HappinessCache(fx.w, fx.h, fx.d);

            // Sample positions chosen to exercise distinct scenarios:
            //   (4, 4, 0)   - open ground; sees multiple items (h1@(2,2), h2@(5,6), h1@(8,8))
            //   (12, 4, 0)  - near the wall column at x=10 / fluid moat at y=5
            //   (5, 13, 0)  - near the unmined hill cluster at (3,15)/(4,15)
            //   (16, 16, 0) - near the undiscovered patch at (17,17)
            //   (6, 6, 0)   - near the upcoming mutation site at (6, 7) so the
            //                 dirty-mark neighborhood actually overlaps it
            int[][] samples = {
                { 4, 4, 0 },
                { 12, 4, 0 },
                { 5, 13, 0 },
                { 16, 16, 0 },
                { 6, 6, 0 },
            };

            // Force-build the sample tiles (ensures incremental updates have
            // populated state to mutate, not just dirty stubs).
            for (int[] s : samples) cacheA.getVisibleHappyItems(s[0], s[1], s[2]);

            // ---- Mutate the world AND fire matching hooks on cache_A ----

            // (a) Place an additional happy item near sample (6,6).
            //     Hits the onItemPlaced incremental insertion path.
            fx.placeHappyItem(6, 7, 0, happyItemH1);
            cacheA.onItemPlaced(6, 7, 0, happyItemH1.getHappiness());

            // (b) Remove an existing happy item (the one at (8, 8) from the
            //     varied world). buildVariedWorld placed an h1 there.
            //     Cell.setEntity(null) calls Game.getWorld() and NPEs in tests
            //     (same reason the fixture uses raw field writes for placement);
            //     mirror the fixture's reflective bypass to clear the entity.
            //     Hits the onItemRemoved incremental removal path.
            try {
                java.lang.reflect.Field entityField = xaos.tiles.Cell.class.getDeclaredField("entity");
                entityField.setAccessible(true);
                entityField.set(fx.cells[8][8][0], null);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new IllegalStateException("Cannot clear cell entity reflectively", e);
            }
            cacheA.onItemRemoved(8, 8, 0, happyItemH1.getHappiness());

            // (c) Change wall state: add a new unmined cell that wasn't there
            //     before. Exercises onWallChanged dirty-marking radius.
            fx.setUnmined(7, 7, 0);
            cacheA.onWallChanged(7, 7, 0);

            // (d) Change mining state at a different cell. Exercises
            //     onMiningChanged dirty-marking.
            fx.setUnmined(13, 4, 0);
            cacheA.onMiningChanged(13, 4, 0);

            // (e) Change discovery state: mark a cell undiscovered.
            //     Exercises onDiscovered dirty-marking.
            fx.setUndiscovered(15, 15, 0);
            cacheA.onDiscovered(15, 15, 0);

            // ---- cache_B: build fresh against the post-mutation world ----
            // This simulates exactly what happens at load time.
            HappinessCache cacheB = new HappinessCache(fx.w, fx.h, fx.d);

            // ---- Compare visibility at each sample position ----
            for (int[] s : samples) {
                List<int[]> a = cacheA.getVisibleHappyItems(s[0], s[1], s[2]);
                List<int[]> b = cacheB.getVisibleHappyItems(s[0], s[1], s[2]);
                assertEqualEntrySets(a, b, s[0], s[1], s[2]);
            }
        }
    }

    // Radius-coverage test (verifying 2*MAX_LOS vs MAX_LOS) was evaluated and deferred.
    // Rationale: radius `2 * MAX_LOS` is established by code review of the
    // `markNeighborhoodDirty` helper itself. A behavioral test would need a tile T at
    // distance >MAX_LOS+1 from a wall W but with W within MAX_LOS of an item, requiring
    // careful coordinate setup. Deferred to Task 12 cleanup if needed.

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
