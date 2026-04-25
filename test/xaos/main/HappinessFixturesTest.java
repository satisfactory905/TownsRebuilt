package xaos.main;

import org.junit.jupiter.api.Test;

import xaos.tiles.Cell;
import xaos.tiles.entities.items.ItemManagerItem;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HappinessFixturesTest {

    @Test
    void fixtureMountsAndDisposesCleanly() {
        Cell[][][] before = World.cells;
        try (HappinessFixtures fx = new HappinessFixtures(5, 5, 1)) {
            assertSame(fx.cells, World.cells, "fixture should mount its cells onto World.cells");
            Cell c = World.cells[2][2][0];
            assertNotNull(c);
            assertTrue(c.isDiscovered());
            assertTrue(c.isMined());
        }
        assertSame(before, World.cells, "fixture should restore previous cells on close");
    }

    @Test
    void buildVariedWorldDoesNotNPE() {
        // Sanity check that buildVariedWorld can run end-to-end without
        // tripping the fluid/terrain or setEntity NPEs that were latent
        // in the initial implementation.
        java.util.Map<Integer, ItemManagerItem> items = new java.util.HashMap<>();
        ItemManagerItem dummy = new ItemManagerItem("test_happy_h1", "item");
        dummy.setHappiness(1);
        items.put(1, dummy);
        ItemManagerItem dummy2 = new ItemManagerItem("test_happy_h2", "item");
        dummy2.setHappiness(2);
        items.put(2, dummy2);

        try (HappinessFixtures fx = HappinessFixtures.buildVariedWorld(items)) {
            // Just confirm we get here without exception.
            assertNotNull(fx.cells);
            assertSame(fx.cells, World.cells);
        }
    }
}
