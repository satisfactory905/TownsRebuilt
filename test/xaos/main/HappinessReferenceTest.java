package xaos.main;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import xaos.tiles.entities.items.ItemManager;
import xaos.tiles.entities.items.ItemManagerItem;
import xaos.tiles.terrain.Terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HappinessReferenceTest {

    private static ItemManagerItem happyItemH1;  // happiness == 1
    private static ItemManagerItem happyItemH2;  // happiness == 2

    /**
     * Injects synthetic ItemManagerItems directly into ItemManager's private
     * itemList map via reflection, bypassing XML loading (which requires
     * towns.ini and is not available in the test JVM).
     *
     * IDs chosen from items.xml:
     *   "woodenbed"    -> happiness 1  (line ~4674)
     *   "purplethrone" -> happiness 2  (line ~4858)
     *
     * Using the real XML ids means downstream tests that eventually load the
     * real XML will see the same keys — but here we inject them synthetically
     * so no filesystem dependency is needed.
     */
    @BeforeAll
    static void loadHappyItems() throws Exception {
        // Access the private static itemList field.
        Field itemListField = ItemManager.class.getDeclaredField("itemList");
        itemListField.setAccessible(true);

        @SuppressWarnings("unchecked")
        HashMap<String, ItemManagerItem> itemList =
                (HashMap<String, ItemManagerItem>) itemListField.get(null);

        // If ItemManager was previously loaded (e.g. by another test class),
        // reuse the existing map; otherwise create a fresh one.
        if (itemList == null) {
            itemList = new HashMap<>();
            itemListField.set(null, itemList);
        }

        // happiness == 1
        if (!itemList.containsKey("woodenbed")) {
            ItemManagerItem h1 = new ItemManagerItem("woodenbed", "furniture");
            h1.setHappiness(1);
            itemList.put("woodenbed", h1);
        }

        // happiness == 2
        if (!itemList.containsKey("purplethrone")) {
            ItemManagerItem h2 = new ItemManagerItem("purplethrone", "furniture");
            h2.setHappiness(2);
            itemList.put("purplethrone", h2);
        }

        happyItemH1 = ItemManager.getItem("woodenbed");
        happyItemH2 = ItemManager.getItem("purplethrone");

        if (happyItemH1 == null || happyItemH1.getHappiness() != 1) {
            throw new IllegalStateException("Test setup needs an item with happiness == 1");
        }
        if (happyItemH2 == null || happyItemH2.getHappiness() != 2) {
            throw new IllegalStateException("Test setup needs an item with happiness == 2");
        }
    }

    @Test
    void singleHappyItemAtSelfTileIsVisible() {
        // Citizen at (3,3), LOS=3. Scan covers x: 0..6, y: 0..6 -- fit in 7x7.
        try (HappinessFixtures fx = new HappinessFixtures(7, 7, 1)) {
            fx.placeHappyItem(3, 3, 0, happyItemH1);

            List<int[]> result = HappinessReference.referenceVisibleHappyItems(3, 3, 0, 3);

            assertEquals(1, result.size());
            assertEquals(1, result.get(0)[0]);
            assertEquals(0, result.get(0)[1], "self-tile distance is 0");
        }
    }

    @Test
    void happyItemOutsideLOSNotVisible() {
        // Citizen at (10,10), LOS=5. Scan covers x: 5..15, y: 5..15 -- fit in 16x16.
        // Item at (0,0): Chebyshev distance = max(10,10) = 10, outside LOS=5.
        try (HappinessFixtures fx = new HappinessFixtures(16, 16, 1)) {
            fx.placeHappyItem(0, 0, 0, happyItemH1);

            List<int[]> result = HappinessReference.referenceVisibleHappyItems(10, 10, 0, 5);

            assertTrue(result.isEmpty(), "item at distance 10 with LOS=5 must not be visible");
        }
    }

    @Test
    void happyItemBlockedByWall_unminedTerrain_isNotVisible() {
        // Citizen at (5,5), LOS=5. Scan covers x: 0..10, y: 0..10 -- fit in 11x11.
        // Item at (5,7), unmined at (5,6) blocking the straight column.
        try (HappinessFixtures fx = new HappinessFixtures(11, 11, 1)) {
            fx.placeHappyItem(5, 7, 0, happyItemH2);
            fx.setUnmined(5, 6, 0);

            List<int[]> result = HappinessReference.referenceVisibleHappyItems(5, 5, 0, 5);

            assertTrue(result.isEmpty(),
                "item at (5,7) blocked by unmined cell at (5,6) must not appear visible from (5,5)");
        }
    }

    @Test
    void highHappinessItemBlockedByFluid_isNotVisible() {
        // Citizen at (5,5), LOS=5. Scan covers x: 0..10, y: 0..10 -- fit in 11x11.
        // Item at (5,7), fluid at (5,6) blocking the straight column.
        // Current isCellAllowed treats fluid cells as blockers; preserved.
        try (HappinessFixtures fx = new HappinessFixtures(11, 11, 1)) {
            fx.placeHappyItem(5, 7, 0, happyItemH2);
            fx.setFluid(5, 6, 0, Terrain.FLUIDS_WATER);

            List<int[]> result = HappinessReference.referenceVisibleHappyItems(5, 5, 0, 5);

            assertTrue(result.isEmpty(),
                "happiness=2 item at (5,7) blocked by water at (5,6) must not appear visible (fluids block LOS in current code)");
        }
    }

    @Test
    void happyItemInUndiscoveredRegion_isNotVisible() {
        // Citizen at (5,5), LOS=5. Scan covers x: 0..10, y: 0..10 -- fit in 11x11.
        // Item at (5,7), undiscovered at (5,6) blocking the straight column.
        try (HappinessFixtures fx = new HappinessFixtures(11, 11, 1)) {
            fx.placeHappyItem(5, 7, 0, happyItemH1);
            fx.setUndiscovered(5, 6, 0);  // line from (5,5) to (5,7) passes through here

            List<int[]> result = HappinessReference.referenceVisibleHappyItems(5, 5, 0, 5);

            assertTrue(result.isEmpty(),
                "item visible only through an undiscovered cell must not appear visible");
        }
    }

    @Test
    void multipleHappyItemsAllReportedWithCorrectDistances() {
        try (HappinessFixtures fx = new HappinessFixtures(11, 11, 1)) {
            fx.placeHappyItem(5, 5, 0, happyItemH1);  // happiness 1, distance 0
            fx.placeHappyItem(7, 5, 0, happyItemH2);  // happiness 2, distance 2
            fx.placeHappyItem(5, 8, 0, happyItemH1);  // happiness 1, distance 3

            List<int[]> result = HappinessReference.referenceVisibleHappyItems(5, 5, 0, 5);

            assertEquals(3, result.size());
            // Sort by (happiness, distance) for deterministic indexing.
            result.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
            assertEquals(1, result.get(0)[0]); assertEquals(0, result.get(0)[1]);
            assertEquals(1, result.get(1)[0]); assertEquals(3, result.get(1)[1]);
            assertEquals(2, result.get(2)[0]); assertEquals(2, result.get(2)[1]);
        }
    }

    @Test
    void variedWorldMixesHappinessValuesAndBlockerTypes() {
        // Smoke check on the buildVariedWorld helper itself: it produces a
        // world where the reference helper finds *some* items at common
        // citizen positions. Specific counts aren't asserted here -- the
        // strong correctness check comes from the equivalence tests in
        // Task 5 against the same fixture.
        Map<Integer, ItemManagerItem> items = new HashMap<>();
        items.put(1, happyItemH1);
        items.put(2, happyItemH2);
        try (HappinessFixtures fx = HappinessFixtures.buildVariedWorld(items)) {
            // Open area near the (5,6) item -- citizen at (5,5) should see it.
            List<int[]> openArea = HappinessReference.referenceVisibleHappyItems(5, 5, 0, 10);
            assertTrue(openArea.size() >= 1,
                "open-area citizen at (5,5,0) should see at least one item");
        }
    }
}
