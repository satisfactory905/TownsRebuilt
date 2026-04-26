package xaos.main;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import xaos.data.CitizenData;
import xaos.data.LivingEntityData;
import xaos.tiles.entities.items.ItemManager;
import xaos.tiles.entities.items.ItemManagerItem;
import xaos.tiles.entities.living.Citizen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage of the cache integration in {@link World#modifyHappiness}
 * (the private overload that takes a single Citizen). Builds a synthetic world
 * with one happy item, mounts a HappinessCache on a fresh World instance via
 * reflection, constructs a Citizen reflectively (Citizen's normal constructor
 * is XML-driven and pulls in the Names/Campaign infrastructure), then verifies
 * that calling modifyHappiness advances the citizen's happiness by the visible
 * item's happiness value.
 *
 * <p>Tied to the equivalence tests in HappinessCacheTest: those prove the cache
 * matches the prior bresenham scan; this test proves the World call site
 * actually consumes the cache and applies the increment.
 */
class WorldModifyHappinessTest {

    private static ItemManagerItem happyItemH3;  // happiness == 3

    @BeforeAll
    static void loadHappyItem() {
        injectSyntheticItem();
        happyItemH3 = ItemManager.getItem("goldthrone");
        if (happyItemH3 == null || happyItemH3.getHappiness() != 3) {
            throw new IllegalStateException("Test setup needs an item with happiness == 3");
        }
    }

    @SuppressWarnings("unchecked")
    private static void injectSyntheticItem() {
        try {
            Field itemListField = ItemManager.class.getDeclaredField("itemList");
            itemListField.setAccessible(true);
            Map<String, ItemManagerItem> itemList = (Map<String, ItemManagerItem>) itemListField.get(null);
            if (itemList == null) {
                itemList = new HashMap<>();
                itemListField.set(null, itemList);
            }
            if (!itemList.containsKey("goldthrone")) {
                ItemManagerItem h3 = new ItemManagerItem("goldthrone", "furniture");
                h3.setHappiness(3);
                itemList.put("goldthrone", h3);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Cannot inject synthetic ItemManagerItem", e);
        }
    }

    @Test
    void modifyHappinessAdvancesHappinessWhenCacheReportsVisibleItem() throws Exception {
        try (HappinessFixtures fx = new HappinessFixtures(100, 100, 1)) {
            // Place a happy item adjacent to where the citizen will stand.
            // (5, 5) sees (5, 7) over open ground at Chebyshev distance 2.
            fx.placeHappyItem(5, 7, 0, happyItemH3);

            // Build the cache against the populated fixture and force the citizen's
            // tile to be built so the cache holds an entry for it.
            HappinessCache cache = new HappinessCache(fx.w, fx.h, fx.d);
            assertEquals(1, cache.getVisibleHappyItems(5, 5, 0).size(),
                "precondition: cache must already see the item before modifyHappiness runs");

            // Construct a fresh World and inject the cache via reflection. World has no
            // public setter for happinessCache (it's a transient field rebuilt at the end
            // of generateAll/readExternal); reflection is the only path.
            World world = new World();
            Field hcField = World.class.getDeclaredField("happinessCache");
            hcField.setAccessible(true);
            hcField.set(world, cache);

            // Construct a Citizen reflectively. The XML-driven constructor pulls in Names /
            // Campaign / Game machinery that isn't available in unit tests; the no-arg
            // constructor leaves citizenData / livingEntityData null, so we set them by
            // direct field write. Same pattern as HappinessFixtures uses for Cell.entity.
            Citizen citizen = new Citizen();
            citizen.setCoordinates(5, 5, 0);

            // Wire CitizenData with non-zero work/idle counters (modifyHappiness early-returns
            // if either is zero) and a starting happiness of 0.
            CitizenData cd = new CitizenData();
            cd.setHappinessWorkCounter(1);
            cd.setHappinessIdleCounter(1);
            cd.setHappiness(0);
            Field citizenDataField = Citizen.class.getDeclaredField("citizenData");
            citizenDataField.setAccessible(true);
            citizenDataField.set(citizen, cd);

            // Wire LivingEntityData with an LOSCurrent that comfortably covers the
            // distance to the happy item (Chebyshev distance 2; LOS=12 is generous).
            LivingEntityData led = new LivingEntityData();
            led.setLOSCurrent(12);
            // LivingEntity.livingEntityData is private; setLivingEntityData is public.
            citizen.setLivingEntityData(led);

            // Reach into the private modifyHappiness(Citizen) overload.
            Method modifyHappiness = World.class.getDeclaredMethod("modifyHappiness", Citizen.class);
            modifyHappiness.setAccessible(true);

            int before = cd.getHappiness();
            modifyHappiness.invoke(world, citizen);
            int after = cd.getHappiness();

            // The cache reports exactly one visible entry (happiness=3, distance=2). With
            // reservoir sampling of size 1 over a single entry, the selected happiness is
            // deterministically 3 — Utils.getRandomBetween(1, 1) == 1 — and the citizen
            // gains exactly that amount.
            assertEquals(3, after - before,
                "modifyHappiness should add the cached item's happiness exactly once");
            assertTrue(after > before, "happiness should have increased");
        }
    }
}
