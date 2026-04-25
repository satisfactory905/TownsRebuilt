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
