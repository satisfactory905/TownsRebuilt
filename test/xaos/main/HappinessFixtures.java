package xaos.main;

import xaos.tiles.Cell;
import xaos.tiles.entities.Entity;
import xaos.tiles.entities.items.Item;
import xaos.tiles.entities.items.ItemManagerItem;
import xaos.tiles.terrain.Terrain;
import xaos.utils.Point3DShort;

/**
 * Test-only synthetic-world builder. Mounts a small Cell[][][] onto the
 * static World.cells field for the duration of the test, then restores
 * the previous value on close. Keep it short -- anything more elaborate
 * means we are testing too much at once.
 *
 * API notes from Step 1 audit (2026-04-24):
 *   - Cell.setCoordinates only accepts Point3DShort (no short-triple overload),
 *     so we call Point3DShort.getPoolInstance(x, y, z) when constructing cells.
 *   - Entity (and therefore Item) has setCoordinates(int, int, int), so Item
 *     placement uses the int-triple overload directly.
 *   - World.MAP_WIDTH and MAP_HEIGHT are public static final short (200) and
 *     cannot be reassigned; only MAP_DEPTH is mutable. Tests that exercise
 *     Utils.isInsideMap treat the fixture as a sub-region of the 200x200 grid.
 *   - Cell.setDiscovered(true) has a side-effect that calls Game.getWorld(),
 *     which is null in a test context with no live game. Workaround: set the
 *     Cell.FLAG_DISCOVERED bit directly via setFlags(). Cell.FLAG_DISCOVERED
 *     and Cell.FLAG_MINED are both public static final int constants, and
 *     Cell.setFlags() is public, so no reflection is needed.
 *   - Cell() no-arg constructor leaves terrain == null; setFluid() will NPE
 *     without an explicit terrain. The fixture assigns a fresh new Terrain()
 *     (no-arg, empty, safe in tests) to every cell immediately after creation.
 *     Do NOT use Terrain(String) -- it calls TerrainManager.getItem() and will
 *     NPE in tests.
 *   - Cell.setEntity calls Game.getWorld().addItemToBeHauled(...), which NPEs
 *     in tests. The fixture bypasses it by writing the private Cell.entity field
 *     directly via reflection. See setCellEntityRaw().
 *   - Terrain.FLUID_TYPE_WATER (from the original plan) does not exist; the
 *     actual constant is Terrain.FLUIDS_WATER.
 */
public final class HappinessFixtures implements AutoCloseable {

    // Cell.setEntity calls Game.getWorld().addItemToBeHauled(...), which NPEs in tests.
    // Bypass it by writing the private field directly.
    private static final java.lang.reflect.Field CELL_ENTITY_FIELD;
    static {
        try {
            CELL_ENTITY_FIELD = xaos.tiles.Cell.class.getDeclaredField("entity");
            CELL_ENTITY_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static void setCellEntityRaw(Cell cell, Entity entity) {
        try {
            CELL_ENTITY_FIELD.set(cell, entity);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("setAccessible() should have allowed this", e);
        }
    }

    public final int w;
    public final int h;
    public final int d;
    public final Cell[][][] cells;
    private final Cell[][][] previousCells;
    private final short previousMapDepth;

    public HappinessFixtures(int w, int h, int d) {
        this.w = w;
        this.h = h;
        this.d = d;
        this.cells = new Cell[w][h][d];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                for (int z = 0; z < d; z++) {
                    Cell c = new Cell();
                    c.setCoordinates(Point3DShort.getPoolInstance(x, y, z));
                    // Cell() no-arg leaves terrain == null; setFluid() will NPE without this.
                    c.setTerrain(new xaos.tiles.terrain.Terrain());
                    // setDiscovered(true) calls Game.getWorld() as a side effect, which
                    // NPEs in tests. Set the flag bits directly instead.
                    c.setFlags(c.getFlags() | Cell.FLAG_DISCOVERED | Cell.FLAG_MINED);
                    cells[x][y][z] = c;
                }
            }
        }
        previousCells = World.cells;
        previousMapDepth = World.MAP_DEPTH;
        World.cells = cells;
        World.MAP_DEPTH = (short) d;
    }

    /** Marks (x,y,z) as unmined (acts as a solid blocker for LOS, e.g., a hill or rock). */
    public void setUnmined(int x, int y, int z) {
        Cell c = cells[x][y][z];
        c.setFlags(c.getFlags() & ~Cell.FLAG_MINED);
    }

    /** Marks (x,y,z) as undiscovered. */
    public void setUndiscovered(int x, int y, int z) {
        Cell c = cells[x][y][z];
        c.setFlags(c.getFlags() & ~Cell.FLAG_DISCOVERED);
    }

    /**
     * Adds fluid (water/lava) to (x, y, z). Type is e.g.
     * Terrain.FLUIDS_WATER. Fluids block LOS in current isCellAllowed
     * semantics -- preserved by the cache.
     */
    public void setFluid(int x, int y, int z, int fluidType) {
        cells[x][y][z].getTerrain().setFluidType(fluidType);
        cells[x][y][z].getTerrain().setFluidCount(1);
    }

    /**
     * Places a happy item at (x, y, z) using the given ItemManagerItem
     * (which carries the happiness value). Caller must construct or look
     * up the imi.
     */
    public void placeHappyItem(int x, int y, int z, ItemManagerItem imi) {
        Item item = new Item();
        item.setIniHeader(imi.getIniHeader());
        item.setCoordinates(x, y, z);
        item.setID(World.getNextEntityID());
        setCellEntityRaw(cells[x][y][z], item);
    }

    /**
     * Constructs a world with a deliberate variety of obstacles and items,
     * intended for parametric equivalence tests. The 20x20x1 world contains:
     *   - Several happy items at varying coordinates (different happiness
     *     values, supplied by the caller in itemsByHappiness).
     *   - A vertical wall column at x=10 spanning a few rows.
     *   - An unmined hill cluster around (3, 15).
     *   - A fluid moat at y=5 ((14,5),(15,5),(16,5)) with the h2 happy item at
     *     (15,4) north of the moat, so any query from y>=5 is blocked by fluid.
     *   - One undiscovered region around (17, 17).
     *
     * Caller passes a map of happiness-value -> ItemManagerItem so the
     * test runner can supply real items. At minimum supply happiness 1 and 2.
     */
    public static HappinessFixtures buildVariedWorld(java.util.Map<Integer, ItemManagerItem> itemsByHappiness) {
        if (!itemsByHappiness.containsKey(1) || !itemsByHappiness.containsKey(2)) {
            throw new IllegalArgumentException("buildVariedWorld requires items with happiness 1 and 2");
        }
        HappinessFixtures fx = new HappinessFixtures(20, 20, 1);
        ItemManagerItem h1 = itemsByHappiness.get(1);
        ItemManagerItem h2 = itemsByHappiness.get(2);

        // Happy items spread across the map (mix of values).
        fx.placeHappyItem(2, 2, 0, h1);
        fx.placeHappyItem(5, 6, 0, h2);
        fx.placeHappyItem(8, 8, 0, h1);
        fx.placeHappyItem(12, 12, 0, h2);
        fx.placeHappyItem(16, 4, 0, h1);
        // High-happiness item placed deliberately past a fluid pool:
        fx.placeHappyItem(15, 4, 0, h2);

        // Wall column at x=10, y=8..12 (blocks LOS lines through it).
        for (int y = 8; y <= 12; y++) {
            fx.setUnmined(10, y, 0);
        }

        // Unmined hill cluster around (3, 15).
        fx.setUnmined(3, 15, 0);
        fx.setUnmined(3, 16, 0);
        fx.setUnmined(4, 15, 0);

        // Fluid moat at row y=5 (cells (14,5),(15,5),(16,5)). The h2 happy item at (15,4)
        // is north of the moat; any test query from y>=5 to (15,4) must cross fluid and is
        // blocked under current isCellAllowed semantics.
        fx.setFluid(14, 5, 0, Terrain.FLUIDS_WATER);
        fx.setFluid(15, 5, 0, Terrain.FLUIDS_WATER);
        fx.setFluid(16, 5, 0, Terrain.FLUIDS_WATER);

        // Undiscovered patch.
        fx.setUndiscovered(17, 17, 0);
        fx.setUndiscovered(17, 18, 0);
        fx.setUndiscovered(18, 17, 0);

        return fx;
    }

    @Override
    public void close() {
        World.cells = previousCells;
        World.MAP_DEPTH = previousMapDepth;
    }
}
