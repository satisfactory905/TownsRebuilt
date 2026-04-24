package xaos.generator;

import java.util.ArrayList;

import xaos.main.World;
import xaos.tiles.Cell;
import xaos.tiles.entities.items.Item;
import xaos.tiles.entities.items.ItemManager;
import xaos.tiles.entities.items.ItemManagerItem;
import xaos.utils.Log;
import xaos.utils.Messages;
import xaos.utils.Utils;

public class ItemGenerator extends Generator {

    public final static String XML_FILE = "gen_items.xml"; //$NON-NLS-1$

    /**
     * Lee del gen_items.xml y genera los items
     */
    public static void generateItems(Cell[][][] cells, String sCampaignID, String sMissionID) {
        // Leemos el gen_items.xml (si esta en una mision se carga de otro sitio)
        Generator generator = new Generator();
        ArrayList<String> alPaths = Utils.getPathToFile(XML_FILE, sCampaignID, sMissionID);

        for (int i = 0; i < alPaths.size(); i++) {
            Generator.read(alPaths.get(i), generator, i == 0);
        }

        GeneratorItem item;

        // Recorremos el generator
        for (int i = 0; i < generator.getList().size(); i++) {
            item = generator.getList().get(i);
            if (item.getName() == ItemGeneratorItem.ITEM_ADD) {
                addItem(item, cells);
            } else {
                Log.log(Log.LEVEL_ERROR, Messages.getString("ItemGenerator.0") + item.getName() + Messages.getString("ItemGenerator.1") + XML_FILE + "]", "ItemGenerator"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            }
        }
    }

    /**
     * Mete un item en el mundo
     *
     * @param item
     */
    private static void addItem(GeneratorItem item, Cell[][][] cells) {
        // Leemos todos los nodos
        String entity = null, pointx = null, pointy = null, level = null;
        int qtty = 0;

        for (int i = 0; i < item.getList().size(); i++) {
            if (item.getList().get(i).getName().equalsIgnoreCase(ItemGeneratorItem.ITEM_ADD_ITEM)) {
                entity = item.getList().get(i).getValue();
            } else if (item.getList().get(i).getName().equalsIgnoreCase(ItemGeneratorItem.ITEM_ADD_QTTY)) {
                qtty = Utils.launchDice(item.getList().get(i).getValue());
            } else if (item.getList().get(i).getName().equalsIgnoreCase(ItemGeneratorItem.ITEM_ADD_POINTX)) {
                pointx = item.getList().get(i).getValue();
            } else if (item.getList().get(i).getName().equalsIgnoreCase(ItemGeneratorItem.ITEM_ADD_POINTY)) {
                pointy = item.getList().get(i).getValue();
            } else if (item.getList().get(i).getName().equalsIgnoreCase(ItemGeneratorItem.ITEM_ADD_LEVEL)) {
                level = item.getList().get(i).getValue();
            }
        }

        // Todo cargado, procedemos
        if (entity == null || entity.length() == 0) {
            Log.log(Log.LEVEL_ERROR, Messages.getString("ItemGenerator.4"), "ItemGenerator"); //$NON-NLS-1$ //$NON-NLS-2$
            return;
        }

        ItemManagerItem imi = ItemManager.getItem(entity);
        if (imi == null) {
            Log.log(Log.LEVEL_ERROR, Messages.getString("ItemGenerator.6") + entity + "]", "ItemGenerator"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            return;
        }

        Cell cell;
        boolean bOK;
        int x, y, z;
        int tryes = 200; // Intenta meter 50 veces, si no puede resta 1 al qtty
        while (qtty > 0) {
            x = Utils.launchDice(pointx);
            y = Utils.launchDice(pointy);
            z = Utils.launchDice(level);

            if (x < 0 || x >= World.MAP_WIDTH) {
                Log.log(Log.LEVEL_ERROR, Messages.getString("ItemGenerator.2") + imi.getIniHeader() + "]", "ItemGenerator"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                tryes = 200;
                qtty--;
                continue;
            }
            if (y < 0 || y >= World.MAP_WIDTH) {
                Log.log(Log.LEVEL_ERROR, Messages.getString("ItemGenerator.7") + imi.getIniHeader() + "]", "ItemGenerator"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                tryes = 200;
                qtty--;
                continue;
            }
            if (z < 0 || z >= World.MAP_DEPTH) {
                Log.log(Log.LEVEL_ERROR, Messages.getString("ItemGenerator.10") + imi.getIniHeader() + "]", "ItemGenerator"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                tryes = 200;
                qtty--;
                continue;
            }

            bOK = true;
            cell = cells[x][y][z];
            while (Item.shouldFall(cells, imi, cell.getCoordinates())) {
                z++;
                if (z >= (World.MAP_DEPTH - 1)) {
                    bOK = false;
                    break;
                }
                cell = cells[x][y][z];
            }

            if (bOK && Item.isCellAvailableForItem(imi, x, y, z, false, false)) {
                Item itemEntity = Item.createItem(imi);
                itemEntity.setOperative(true);
                World.getCell(x, y, z).setEntity(itemEntity);
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
