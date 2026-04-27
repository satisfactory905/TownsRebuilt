package xaos.tiles.terrain.special;

import xaos.tiles.Tile;
import xaos.utils.Messages;

public class Lava {

    private static final long serialVersionUID = 8093200100906408032L;

    public final static Tile[] TERRAIN_LAVA = {
        new Tile("lava"), //$NON-NLS-1$
        new Tile("lavamin"), //$NON-NLS-1$
        new Tile("lavamax"), //$NON-NLS-1$
    };

    public Lava() {
        for (int i = 0; i < TERRAIN_LAVA.length; i++) {
            TERRAIN_LAVA[i].setTextureID(TERRAIN_LAVA[i].getIniHeader(), "lava"); //$NON-NLS-1$

            // Updateamos los frames y tal de animaciones
            TERRAIN_LAVA[i].setAnimationTiles(TERRAIN_LAVA[0].getAnimationTiles());
            TERRAIN_LAVA[i].setAnimationFrameDelay(TERRAIN_LAVA[0].getAnimationFrameDelay());
            TERRAIN_LAVA[i].setCurrentAnimationTile(TERRAIN_LAVA[0].getCurrentAnimationTile());
        }
    }

    public Tile getLavaCursor(int iCount) {
        if (iCount > 4) {
            return TERRAIN_LAVA[2];
        } else if (iCount < 3) {
            return TERRAIN_LAVA[1];
        } else {
            return TERRAIN_LAVA[0];
        }
    }

    public static String getTileName() {
        return Messages.getString("Lava.0"); //$NON-NLS-1$
    }

    public void updateAnimation() {
        for (int i = 0; i < TERRAIN_LAVA.length; i++) {
            TERRAIN_LAVA[i].updateAnimation(false);
        }
    }
}
