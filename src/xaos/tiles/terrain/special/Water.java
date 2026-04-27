package xaos.tiles.terrain.special;

import xaos.tiles.Tile;
import xaos.utils.Messages;

public class Water {

    private static final long serialVersionUID = 2201137814233532108L;

    public final static Tile[] TERRAIN_WATER = {
        new Tile("water"), //$NON-NLS-1$
        new Tile("watermin"), //$NON-NLS-1$
        new Tile("watermax"), //$NON-NLS-1$
    };

    public Water() {
        for (int i = 0; i < TERRAIN_WATER.length; i++) {
            TERRAIN_WATER[i].setTextureID(TERRAIN_WATER[i].getIniHeader(), "water"); //$NON-NLS-1$

            // Updateamos los frames y tal de animaciones
            TERRAIN_WATER[i].setAnimationTiles(TERRAIN_WATER[0].getAnimationTiles());
            TERRAIN_WATER[i].setAnimationFrameDelay(TERRAIN_WATER[0].getAnimationFrameDelay());
            TERRAIN_WATER[i].setCurrentAnimationTile(TERRAIN_WATER[0].getCurrentAnimationTile());
        }
    }

    public Tile getWaterCursor(int iCount) {
        if (iCount > 4) {
            return TERRAIN_WATER[2];
        } else if (iCount < 3) {
            return TERRAIN_WATER[1];
        } else {
            return TERRAIN_WATER[0];
        }
    }

    public static String getTileName() {
        return Messages.getString("Water.0"); //$NON-NLS-1$
    }

    public void updateAnimation() {
        for (int i = 0; i < TERRAIN_WATER.length; i++) {
            TERRAIN_WATER[i].updateAnimation(false);
        }
    }
}
