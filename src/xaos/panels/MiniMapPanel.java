package xaos.panels;

import java.nio.ByteBuffer;

import org.lwjgl.opengl.GL11;

import xaos.main.Game;
import xaos.main.World;
import xaos.tiles.Cell;
import xaos.tiles.Tile;
import xaos.tiles.entities.Entity;
import xaos.tiles.terrain.Terrain;
import xaos.tiles.terrain.TerrainManager;
import xaos.utils.ColorGL;
import xaos.utils.ImageData;
import xaos.utils.Point3D;
import xaos.utils.Point3DShort;
import xaos.utils.TextureData;
import xaos.utils.UtilsGL;
import xaos.utils.perf.Category;
import xaos.utils.perf.PerfStats;
import xaos.utils.perf.Span;
import xaos.utils.perf.SpanHandle;

public final class MiniMapPanel {

    // Perf telemetry handle for the minimap render pass.
    private static final SpanHandle SPAN_FRAME_RENDER_MINIMAP =
        PerfStats.span ("frame.render.minimap", Category.RENDERING_FRAME); //$NON-NLS-1$

//    public static int TEXTURE_MINIMAPS_INDEX = 32;
    private static TextureData[] minimapTextures;

    private static final Tile YELLOW_TILE = new Tile("ui_yellow"); //$NON-NLS-1$

    public static int renderX;
    public static int renderY;
    public static int renderWidth;
    public static int renderHeight;

    private static boolean[] texturesReload;
    private static int textureRefreshRate;

    public static void initialize(int x, int y, int width, int height) {
        renderX = x;
        renderY = y;
        renderWidth = width;
        renderHeight = height;
        textureRefreshRate = Game.FPS_INGAME;

        if (minimapTextures != null) {
            for (TextureData textureData : minimapTextures) {
                UtilsGL.deleteTexture(textureData);
            }
        }
        minimapTextures = new TextureData[World.MAP_DEPTH];
        texturesReload = null;
    }

    public static void render () {
        try (Span sMinimap = SPAN_FRAME_RENDER_MINIMAP.start ()) {
            if (texturesReload == null) {
                loadTextures();
            }

            // Paint minimap
            Point3D pointView = Game.getWorld().getView();

            if (texturesReload[pointView.z] && textureRefreshRate == 0) {
                texturesReload[pointView.z] = false;
                reloadTexture(pointView.z);
            }

            textureRefreshRate--;
            if (textureRefreshRate < 0) {
                textureRefreshRate = Game.FPS_INGAME; // Cada segundo refresh
            }

            // Pintamos la textura
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, minimapTextures[pointView.z].getTextureID());
            GL11.glTexEnvf(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
            UtilsGL.glBegin(GL11.GL_QUADS);
            UtilsGL.drawTexture(renderX, renderY + renderHeight / 2, renderX + renderWidth / 2, renderY + renderHeight, renderX + renderWidth, renderY + renderHeight / 2, renderX + renderWidth / 2, renderY, 0, 0, 1, 1);
            UtilsGL.glEnd();

            // Calculamos el tamano del cuadrado amarillo teniendo en cuenta el tamano del mainpanel y del minimapa
            int iSquareX = (pointView.x + pointView.y - (World.MAP_WIDTH - World.MAP_HEIGHT) / 2) / 2;
            int iSquareY = (pointView.y - pointView.x + (World.MAP_WIDTH + World.MAP_HEIGHT) / 2) / 2;
            int iSquareWidth = ((MainPanel.renderWidth / Tile.TERRAIN_ICON_WIDTH) * renderWidth) / World.MAP_WIDTH;
            int iSquareHeight = ((MainPanel.renderHeight / Tile.TERRAIN_ICON_HEIGHT) * renderHeight) / World.MAP_HEIGHT;

            // Lo pasamos al tamano que toca
            iSquareX = (iSquareX * renderWidth) / World.MAP_WIDTH;
            iSquareY = (iSquareY * renderHeight) / World.MAP_HEIGHT;

            // Lo posicionamos en la pantalla (restamos tambien la mitad del cuadradito amarillo para que quede centrado)
            iSquareX += (renderX - iSquareWidth / 2);
            iSquareY += (renderY - iSquareHeight / 2);

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, YELLOW_TILE.getTextureID());
            GL11.glTexEnvf(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_REPLACE);
            UtilsGL.glBegin(GL11.GL_QUADS);
            // Pintamos el cuadrado amarillo
            UtilsGL.drawTexture(iSquareX, iSquareY, iSquareX + iSquareWidth, iSquareY + 1, YELLOW_TILE.getTileSetTexX0(), YELLOW_TILE.getTileSetTexY0(), YELLOW_TILE.getTileSetTexX1(), YELLOW_TILE.getTileSetTexY1());
            UtilsGL.drawTexture(iSquareX, iSquareY + iSquareHeight, iSquareX + iSquareWidth, iSquareY + iSquareHeight + 1, YELLOW_TILE.getTileSetTexX0(), YELLOW_TILE.getTileSetTexY0(), YELLOW_TILE.getTileSetTexX1(), YELLOW_TILE.getTileSetTexY1());
            UtilsGL.drawTexture(iSquareX, iSquareY, iSquareX + 1, iSquareY + iSquareHeight, YELLOW_TILE.getTileSetTexX0(), YELLOW_TILE.getTileSetTexY0(), YELLOW_TILE.getTileSetTexX1(), YELLOW_TILE.getTileSetTexY1());
            UtilsGL.drawTexture(iSquareX + iSquareWidth, iSquareY, iSquareX + iSquareWidth + 1, iSquareY + iSquareHeight, YELLOW_TILE.getTileSetTexX0(), YELLOW_TILE.getTileSetTexY0(), YELLOW_TILE.getTileSetTexX1(), YELLOW_TILE.getTileSetTexY1());
            UtilsGL.glEnd();
        }

    }

    /**
     * Carga las texturas de cada nivel y va poniendo los IDs en el array de ID
     * de texturas
     *
     */
    private static void loadTextures() {
        texturesReload = new boolean[World.MAP_DEPTH];
        for (int i = 0; i < World.MAP_DEPTH; i++) {
            texturesReload[i] = false;
            reloadTexture(i);
        }
    }

    /**
     * Reload a texture
     *
     * @param level
     */
    private static void reloadTexture(int level) {
        Cell cell;
        Entity entity;
        ColorGL color;

        boolean newTexture = false;
        ImageData imageData = minimapTextures[level];
        if (imageData == null) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(3 * World.MAP_WIDTH * World.MAP_HEIGHT);
            imageData = new ImageData(World.MAP_WIDTH, World.MAP_HEIGHT, buffer, GL11.GL_RGB);
            newTexture = true;
        }

        final ByteBuffer buffer = imageData.getImagePixels();
        buffer.rewind();
        for (int y = 0; y < World.MAP_HEIGHT; y++) {
            for (int x = 0; x < World.MAP_WIDTH; x++) {
                cell = World.getCell(x, y, level);
                entity = cell.getEntity();
                if (entity != null && entity.getColorMiniMap() != null) {
                    color = entity.getColorMiniMap();
                } else {
                    color = getCellColor(cell);
                }

                if (color == null) {
                    buffer.put((byte) 0);
                    buffer.put((byte) 0);
                    buffer.put((byte) 0);
                } else {
                    buffer.put((byte) (color.r * 255));
                    buffer.put((byte) (color.g * 255));
                    buffer.put((byte) (color.b * 255));
                }
            }
        }
        buffer.rewind();
        buffer.limit(buffer.capacity());

        if (newTexture) {
            minimapTextures[level] = UtilsGL.loadTexture(imageData, GL11.GL_REPLACE);
        } else {
            UtilsGL.reloadTexture(minimapTextures[level]);
        }
    }

    public static void setMinimapReload(int level) {
        if (texturesReload != null && level < texturesReload.length) {
            for (int i = 0; i <= level; i++) {
                texturesReload[i] = true;
            }
        }
    }

    /**
     * Retorna el color a pintar de una celda segun el terreno y/o lo que haya
     * en ella
     *
     * @param x
     * @param y
     * @param z
     * @return
     */
    private static ColorGL getCellColor(Cell cell) {
        // Terrain
        if (cell.isDiscovered()) {
            if (cell.getTerrain().hasFluids()) {
                if (cell.getTerrain().getFluidType() == Terrain.FLUIDS_WATER) {
                    return World.getTileWater(Terrain.FLUIDS_COUNT_MAX).getColorMiniMap();
                } else {
                    return World.getTileLava(Terrain.FLUIDS_COUNT_MAX).getColorMiniMap();
                }
            } else {
                if (cell.isMined()) {
                    // DIGGED, buscamos la primera celda sin digar y le aplicamos un darkeness
                    Point3DShort p3d = cell.getCoordinates();
                    if (p3d.z < (World.MAP_DEPTH - 1)) {
                        Cell cellUnder;
                        float fColor = 256f - 24f;
                        for (int i = p3d.z + 1; i < World.MAP_DEPTH; i++) {
                            cellUnder = World.getCell(p3d.x, p3d.y, i);
                            if (!cellUnder.isMined() || cellUnder.getTerrain().hasFluids()) {
                                if (fColor < 56f) {
                                    fColor = 56f;
                                }
                                fColor /= 256f;
                                ColorGL color = getCellColor(cellUnder);
                                if (color != null) {
                                    color = new ColorGL(color.r * fColor, color.g * fColor, color.b * fColor);
                                }
                                return color;
                            }
                            fColor -= 8f;
                        }
                        return null;
                    } else {
                        return null;
                    }
                } else if (cell.hasStockPile()) {
                    return World.getTileStockpile().getColorMiniMap();
                } else {
                    return TerrainManager.getTileByTileID(cell.getTerrain().getTerrainTileID()).getColorMiniMap();
                }
            }
        } else {
            return World.getTileUnknown().getColorMiniMap();
        }
    }

    /**
     * Indica si el mouse esta encima del minimapa. Como se dibuja en forma de
     * diamante hacemos el calculo necesario
     *
     * @param x
     * @param y
     * @return
     */
    public static boolean isMouseOver(int x, int y) {
        int x200 = (x * World.MAP_WIDTH) / renderWidth;
        int y200 = (y * World.MAP_HEIGHT) / renderHeight;
        int mapX = (x200 - y200) + World.MAP_WIDTH / 2;
        int mapY = (x200 + y200) - World.MAP_HEIGHT / 2;

        return (mapX >= 0 && mapX < World.MAP_WIDTH && mapY >= 0 && mapY < World.MAP_HEIGHT);
    }

    /**
     * Mouse pressed
     *
     * @param x
     * @param y
     * @param mouseButton
     */
    public static void mousePressed(int x, int y, int mouseButton) {
        int x200 = (x * World.MAP_WIDTH) / renderWidth;
        int y200 = (y * World.MAP_HEIGHT) / renderHeight;
        int mapX = (x200 - y200) + World.MAP_WIDTH / 2;
        int mapY = (x200 + y200) - World.MAP_HEIGHT / 2;

        if (mapX >= 0 && mapX < World.MAP_WIDTH && mapY >= 0 && mapY < World.MAP_HEIGHT) {
            Game.getWorld().setView(mapX, mapY);
            textureRefreshRate = 0;
        }
    }

    public static void resize(int renderX, int renderY, int renderWidth, int renderHeight) {
        MiniMapPanel.renderX = renderX;
        MiniMapPanel.renderY = renderY;
        MiniMapPanel.renderWidth = renderWidth;
        MiniMapPanel.renderHeight = renderHeight;
    }

    /**
     * Limpia todos los datos (se usa cuando se sale de la partida y se va al
     * menu principal)
     */
    public static void clear() {
        texturesReload = null;
    }
}
