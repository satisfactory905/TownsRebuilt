package xaos.zones;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import xaos.Towns;
import xaos.main.Game;
import xaos.utils.Log;
import xaos.utils.Messages;
import xaos.utils.UtilsXML;

public class ZoneManager {

    private static HashMap<String, ZoneManagerItem> zoneList;

    private static void loadItems() {
        zoneList = new HashMap<String, ZoneManagerItem>();
        // Cargar de fichero
        loadXMLZones(Towns.getPropertiesString("DATA_FOLDER") + "zones.xml", true); //$NON-NLS-1$ //$NON-NLS-2$

        // Mods
        File fUserFolder = new File(Game.getUserFolder());
        if (!fUserFolder.exists() || !fUserFolder.isDirectory()) {
            return;
        }

        ArrayList<String> alMods = Game.getModsLoaded();
        if (alMods != null && alMods.size() > 0) {
            for (int i = 0; i < alMods.size(); i++) {
                String sModActionsPath = fUserFolder.getAbsolutePath() + System.getProperty("file.separator") + Game.MODS_FOLDER1 + System.getProperty("file.separator") + alMods.get(i) + System.getProperty("file.separator") + Towns.getPropertiesString("DATA_FOLDER") + "zones.xml"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                File fIni = new File(sModActionsPath);
                if (fIni.exists()) {
                    loadXMLZones(sModActionsPath, false);
                }
            }
        }
    }

    public static ZoneManagerItem getItem(String sIniHeader) {
        if (zoneList == null) {
            loadItems();
        }

        if (sIniHeader == null) {
            return null;
        }

        return zoneList.get(sIniHeader);
    }

    private static void loadXMLZones(String sXMLName, boolean bLoadingMain) {
        try {
            Document doc = UtilsXML.loadXMLFile(sXMLName); //$NON-NLS-1$

			// Tenemos el documento XML parseado
            // Lo recorremos entero y vamos anadiendo las zonas (ZoneManagerItem) a la hash
            NodeList nodeList = doc.getDocumentElement().getChildNodes();
            Node node;
            ZoneManagerItem item;
            String sIniHeader;
            for (int i = 0; i < nodeList.getLength(); i++) {
                node = nodeList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    sIniHeader = node.getNodeName();
                    if (sIniHeader != null && sIniHeader.equalsIgnoreCase("DELETE")) {
                        // Miramos que ID quiere borrar
                        if (node.getAttributes() != null && node.getAttributes().getNamedItem("id") != null) {
                            String sIDToDelete = node.getAttributes().getNamedItem("id").getNodeValue();
                            if (sIDToDelete != null) {
                                zoneList.remove(sIDToDelete);
                            }
                        }
                        continue;
                    }

                    boolean bExists = zoneList.containsKey(sIniHeader);
                    // Mod cambiando valores de un item que ya existe?
                    boolean bModChangingValues = (bExists && !bLoadingMain);

                    if (bModChangingValues) {
                        item = zoneList.get(sIniHeader);
                        String sAux = UtilsXML.getChildValue(node.getChildNodes(), "name"); //$NON-NLS-1$
                        if (sAux != null) {
                            item.setName(sAux);
                        }
                        sAux = UtilsXML.getChildValue(node.getChildNodes(), "type"); //$NON-NLS-1$
                        if (sAux != null) {
                            item.setType(sAux);
                        }
                        sAux = UtilsXML.getChildValue(node.getChildNodes(), "minWidth"); //$NON-NLS-1$
                        if (sAux != null) {
                            item.setMinWidth(sAux);
                        }
                        sAux = UtilsXML.getChildValue(node.getChildNodes(), "minHeight"); //$NON-NLS-1$
                        if (sAux != null) {
                            item.setMinHeight(sAux);
                        }

                        ArrayList<String> alNeighbors = UtilsXML.getChildValues(node.getChildNodes(), "neighbor"); //$NON-NLS-1$
                        if (alNeighbors != null && alNeighbors.size() > 0) {
                            item.setNeighbors(alNeighbors);
                        }
                    } else {
                        item = new ZoneManagerItem();
                        item.setIniHeader(sIniHeader);
                        item.setName(UtilsXML.getChildValue(node.getChildNodes(), "name")); //$NON-NLS-1$
                        item.setType(UtilsXML.getChildValue(node.getChildNodes(), "type")); //$NON-NLS-1$
                        item.setMinWidth(UtilsXML.getChildValue(node.getChildNodes(), "minWidth")); //$NON-NLS-1$
                        item.setMinHeight(UtilsXML.getChildValue(node.getChildNodes(), "minHeight")); //$NON-NLS-1$

                        item.setNeighbors(UtilsXML.getChildValues(node.getChildNodes(), "neighbor")); //$NON-NLS-1$
                    }

                    // Lo anadimos a la hash
                    zoneList.put(sIniHeader, item);
                }
            }
        } catch (Exception e) {
            Log.log(Log.LEVEL_ERROR, Messages.getString("ZoneManager.0") + " [" + e.toString() + "]", "ZoneManager"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            Game.exit();
        }
    }

    public static void clear() {
        zoneList = null;
    }
}
