package xaos.generator;

import java.util.ArrayList;

import xaos.main.World;
import xaos.tiles.Cell;
import xaos.tiles.entities.living.LivingEntity;
import xaos.tiles.entities.living.LivingEntityManager;
import xaos.tiles.entities.living.LivingEntityManagerItem;
import xaos.utils.Log;
import xaos.utils.Messages;
import xaos.utils.Utils;

public class LivingEntityGenerator extends Generator {

    public final static String XML_FILE = "gen_livingentities.xml"; //$NON-NLS-1$

    /**
     * Lee del gen_livingentities.xml
     */
    public static void generateLivingEntities(Cell[][][] cells, String sCampaignID, String sMissionID) {
        // Leemos el gen_livingentities.xml (si esta en una mision se carga de otro sitio)
        Generator generator = new Generator();
        ArrayList<String> alPaths = Utils.getPathToFile(XML_FILE, sCampaignID, sMissionID);

        for (int i = 0; i < alPaths.size(); i++) {
            Generator.read(alPaths.get(i), generator, i == 0);
        }

        GeneratorItem item;

        // Recorremos el generator
        for (int i = 0; i < generator.getList().size(); i++) {
            item = generator.getList().get(i);
            if (item.getName() == LivingEntityGeneratorItem.ITEM_ADD) {
                addLivingEntity(item, cells);
            } else {
                Log.log(Log.LEVEL_ERROR, Messages.getString("LivingEntityGenerator.0") + item.getName() + Messages.getString("ItemGenerator.1") + XML_FILE + "]", "LivingEntityGenerator"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            }
        }
    }

    private static boolean checkHabitatHeight(LivingEntityManagerItem lemi, int x, int y, int z) {
        if (z >= World.MAP_NUM_LEVELS_OUTSIDE) {
            return true;
        }

        // Level principal
        if (lemi.getHabitatHeightMin() != -1) {
            if (z <= (World.MAP_NUM_LEVELS_OUTSIDE - lemi.getHabitatHeightMin())) {
                return false;
            }
        }
        if (lemi.getHabitatHeightMax() != -1) {
            if (z >= (World.MAP_NUM_LEVELS_OUTSIDE - lemi.getHabitatHeightMin())) {
                return false;
            }
        }

        return true;
    }

    /**
     * Mete una living entity en el mundo
     *
     * @param item
     */
    private static void addLivingEntity(GeneratorItem item, Cell[][][] cells) {
        // Leemos todos los nodos
        String entity = null, pointx = null, pointy = null, level = null;
        int qtty = 0;

        for (int i = 0; i < item.getList().size(); i++) {
            if (item.getList().get(i).getName().equalsIgnoreCase(LivingEntityGeneratorItem.ITEM_ADD_ENTITY)) {
                entity = item.getList().get(i).getValue();
            } else if (item.getList().get(i).getName().equalsIgnoreCase(LivingEntityGeneratorItem.ITEM_ADD_QTTY)) {
                qtty = Utils.launchDice(item.getList().get(i).getValue());
            } else if (item.getList().get(i).getName().equalsIgnoreCase(LivingEntityGeneratorItem.ITEM_ADD_POINTX)) {
                pointx = item.getList().get(i).getValue();
            } else if (item.getList().get(i).getName().equalsIgnoreCase(LivingEntityGeneratorItem.ITEM_ADD_POINTY)) {
                pointy = item.getList().get(i).getValue();
            } else if (item.getList().get(i).getName().equalsIgnoreCase(LivingEntityGeneratorItem.ITEM_ADD_LEVEL)) {
                level = item.getList().get(i).getValue();
            }
        }

        // Todo cargado, procedemos
        if (entity == null || entity.length() == 0) {
            Log.log(Log.LEVEL_ERROR, Messages.getString("LivingEntityGenerator.4"), "LivingEntityGenerator"); //$NON-NLS-1$ //$NON-NLS-2$
            return;
        }

        LivingEntityManagerItem lemi = LivingEntityManager.getItem(entity);
        if (lemi == null) {
            Log.log(Log.LEVEL_ERROR, Messages.getString("LivingEntityGenerator.5") + " [" + entity + "]", "LivingEntityGenerator"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            return;
        }

        int x, y, z;
        Cell cell;
        boolean bOK;
        int tryes = 200; // Intenta meter 200 veces, si no puede resta 1 al qtty
        while (qtty > 0) {

            x = Utils.launchDice(pointx);
            y = Utils.launchDice(pointy);
            z = Utils.launchDice(level);

            if (x < 0 || x >= World.MAP_WIDTH || y < 0 || y >= World.MAP_WIDTH || z < 0 || z >= World.MAP_DEPTH) {
                Log.log(Log.LEVEL_ERROR, Messages.getString("LivingEntityGenerator.2") + lemi.getIniHeader() + "]", "LivingEntityGenerator"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                tryes = 200;
                qtty--;
                continue;
            }

            bOK = true;
            cell = cells[x][y][z];
            while (cell.isDigged() && cell.isMined()) {
                z++;
                if (z >= (World.MAP_DEPTH - 1)) {
                    bOK = false;
                    break;
                }
                cell = cells[x][y][z];
            }

            if (bOK && (LivingEntity.isCellAllowed(x, y, z) || cells[x][y][z].isCave()) && checkHabitatHeight(lemi, x, y, z) && (lemi.getHabitat() == null || z == (World.MAP_DEPTH - 1) || lemi.getHabitat().size() == 0 || lemi.getHabitat().contains(cells[x][y][z + 1].getTerrain().getTerrainID()))) {
                World.addNewLiving(lemi.getIniHeader(), lemi.getType(), cells[x][y][z].isDiscovered(), x, y, z);

                tryes = 200;
                qtty--;
            } else {
                tryes--;
                if (tryes == 0) {
                    tryes = 200;
                    qtty--;
                }
            }
        }
    }
}
