package xaos.gods;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import xaos.Towns;
import xaos.events.EventManager;
import xaos.main.Game;
import xaos.tiles.entities.items.ItemManager;
import xaos.utils.Log;
import xaos.utils.Messages;
import xaos.utils.Utils;
import xaos.utils.UtilsXML;

public class GodManager {

    private static HashMap<String, GodManagerItem> godsList;

    public static void loadItems() {
        godsList = new HashMap<String, GodManagerItem>();

        // Cargar de fichero
        loadXMLEvents(Towns.getPropertiesString("DATA_FOLDER") + "gods.xml", true); //$NON-NLS-1$ //$NON-NLS-2$

        // Mods
        File fUserFolder = new File(Game.getUserFolder());
        if (!fUserFolder.exists() || !fUserFolder.isDirectory()) {
            return;
        }

        ArrayList<String> alMods = Game.getModsLoaded();
        if (alMods != null && alMods.size() > 0) {
            for (int i = 0; i < alMods.size(); i++) {
                String sModActionsPath = fUserFolder.getAbsolutePath() + System.getProperty("file.separator") + Game.MODS_FOLDER1 + System.getProperty("file.separator") + alMods.get(i) + System.getProperty("file.separator") + Towns.getPropertiesString("DATA_FOLDER") + "gods.xml"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                File fIni = new File(sModActionsPath);
                if (fIni.exists()) {
                    loadXMLEvents(sModActionsPath, false);
                }
            }
        }

        // Miramos que los itemsLike, itemsDislike y eventos existan
        Iterator<GodManagerItem> itEffects = godsList.values().iterator();
        GodManagerItem gmi;
        while (itEffects.hasNext()) {
            gmi = itEffects.next();

            // itemsLike
            if (gmi.getItemsLike() != null && gmi.getItemsDislike().size() > 0) {
                for (int i = 0; i < gmi.getItemsLike().size(); i++) {
                    if (ItemManager.getItem(gmi.getItemsLike().get(i)) == null) {
                        Log.log(Log.LEVEL_ERROR, Messages.getString("GodManager.2") + " [" + gmi.getItemsLike().get(i) + "]", "GodManager"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                        Game.exit();
                    }
                }
            }

            // itemsDislike
            if (gmi.getItemsDislike() != null && gmi.getItemsDislike().size() > 0) {
                for (int i = 0; i < gmi.getItemsDislike().size(); i++) {
                    if (ItemManager.getItem(gmi.getItemsDislike().get(i)) == null) {
                        Log.log(Log.LEVEL_ERROR, Messages.getString("GodManager.6") + " [" + gmi.getItemsDislike().get(i) + "]", "GodManager"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                        Game.exit();
                    }
                }
            }

            // eventsWhenHappy
            if (gmi.getEventsWhenHappy() != null && gmi.getEventsWhenHappy().size() > 0) {
                for (int i = 0; i < gmi.getEventsWhenHappy().size(); i++) {
                    if (EventManager.getItem(gmi.getEventsWhenHappy().get(i)) == null) {
                        Log.log(Log.LEVEL_ERROR, Messages.getString("GodManager.3") + " [" + gmi.getEventsWhenHappy().get(i) + "]", "GodManager"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                        Game.exit();
                    }
                }
            }

            // eventsWhenReallyHappy
            if (gmi.getEventsWhenReallyHappy() != null && gmi.getEventsWhenReallyHappy().size() > 0) {
                for (int i = 0; i < gmi.getEventsWhenReallyHappy().size(); i++) {
                    if (EventManager.getItem(gmi.getEventsWhenReallyHappy().get(i)) == null) {
                        Log.log(Log.LEVEL_ERROR, Messages.getString("GodManager.8") + " [" + gmi.getEventsWhenReallyHappy().get(i) + "]", "GodManager"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                        Game.exit();
                    }
                }
            }

            // eventsWhenAngry
            if (gmi.getEventsWhenAngry() != null && gmi.getEventsWhenAngry().size() > 0) {
                for (int i = 0; i < gmi.getEventsWhenAngry().size(); i++) {
                    if (EventManager.getItem(gmi.getEventsWhenAngry().get(i)) == null) {
                        Log.log(Log.LEVEL_ERROR, Messages.getString("GodManager.12") + " [" + gmi.getEventsWhenAngry().get(i) + "]", "GodManager"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                        Game.exit();
                    }
                }
            }

            // eventsWhenReallyAngry
            if (gmi.getEventsWhenReallyAngry() != null && gmi.getEventsWhenReallyAngry().size() > 0) {
                for (int i = 0; i < gmi.getEventsWhenReallyAngry().size(); i++) {
                    if (EventManager.getItem(gmi.getEventsWhenReallyAngry().get(i)) == null) {
                        Log.log(Log.LEVEL_ERROR, Messages.getString("GodManager.16") + " [" + gmi.getEventsWhenReallyAngry().get(i) + "]", "GodManager"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                        Game.exit();
                    }
                }
            }
        }
    }

    public static GodManagerItem getItem(String sIniHeader) {
        if (sIniHeader == null) {
            return null;
        }

        if (godsList == null) {
            loadItems();
        }

        return godsList.get(sIniHeader);
    }

    public static GodManagerItem getItemAtRandom() {
        if (godsList == null) {
            loadItems();
        }

        if (godsList.size() == 0) {
            return null;
        }

        int iIndex = Utils.getRandomBetween(0, godsList.size() - 1);
        Iterator<String> itGods = godsList.keySet().iterator();
        while (iIndex > 0) {
            iIndex--;
            itGods.next();
        }
        return godsList.get(itGods.next());
    }

    private static void loadXMLEvents(String sXMLName, boolean bLoadingMain) {

        try {
            Document doc = UtilsXML.loadXMLFile(sXMLName); //$NON-NLS-1$

			// Tenemos el documento XML parseado
            // Lo recorremos entero y vamos anadiendo las skills (SkillManagerItem) a la hash
            NodeList nodeList = doc.getDocumentElement().getChildNodes();
            Node node;
            GodManagerItem gmi;
            String sIniHeader;
            for (int i = 0; i < nodeList.getLength(); i++) {
                node = nodeList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    String sName = node.getNodeName();
                    if (sName != null && sName.equalsIgnoreCase("DELETE")) { //$NON-NLS-1$
                        // Miramos que ID quiere borrar
                        if (node.getAttributes() != null && node.getAttributes().getNamedItem("id") != null) { //$NON-NLS-1$
                            String sIDToDelete = node.getAttributes().getNamedItem("id").getNodeValue(); //$NON-NLS-1$
                            if (sIDToDelete != null) {
                                godsList.remove(sIDToDelete);
                            }
                        }
                        continue;
                    }

                    sIniHeader = UtilsXML.getChildValue(node.getChildNodes(), "id"); //$NON-NLS-1$
                    if (sIniHeader == null || sIniHeader.length() == 0) {
                        throw new Exception(Messages.getString("GodManager.0")); //$NON-NLS-1$
                    }

                    boolean bExists = godsList.containsKey(sIniHeader);

                    // Mod cambiando valores de un item que ya existe?
                    boolean bModChangingValues = (bExists && !bLoadingMain);

                    if (bModChangingValues) {
                        gmi = godsList.get(sIniHeader);
                    } else {
                        gmi = new GodManagerItem();
                        gmi.setId(sIniHeader);
                    }

                    if (bModChangingValues) {
                        String sAux = UtilsXML.getChildValue(node.getChildNodes(), "namePool"); //$NON-NLS-1$
                        if (sAux != null) {
                            gmi.setNamePool(sAux);
                        }
                        sAux = UtilsXML.getChildValue(node.getChildNodes(), "surName"); //$NON-NLS-1$
                        if (sAux != null) {
                            gmi.setSurName(sAux);
                        }
                        sAux = UtilsXML.getChildValue(node.getChildNodes(), "itemsLike"); //$NON-NLS-1$
                        if (sAux != null) {
                            gmi.setItemsLike(Utils.getArray(sAux));
                        }
                        sAux = UtilsXML.getChildValue(node.getChildNodes(), "itemsLikePCT"); //$NON-NLS-1$
                        if (sAux != null) {
                            gmi.setItemsLikePCT(Utils.getArray(sAux));
                        }
                        sAux = UtilsXML.getChildValue(node.getChildNodes(), "itemsDislike"); //$NON-NLS-1$
                        if (sAux != null) {
                            gmi.setItemsDislike(Utils.getArray(sAux));
                        }
                        sAux = UtilsXML.getChildValue(node.getChildNodes(), "itemsDislikePCT"); //$NON-NLS-1$
                        if (sAux != null) {
                            gmi.setItemsDislikePCT(Utils.getArray(sAux));
                        }
                        sAux = UtilsXML.getChildValue(node.getChildNodes(), "eventsWhenHappy"); //$NON-NLS-1$
                        if (sAux != null) {
                            gmi.setEventsWhenHappy(Utils.getArray(sAux));
                        }
                        sAux = UtilsXML.getChildValue(node.getChildNodes(), "eventsWhenReallyHappy"); //$NON-NLS-1$
                        if (sAux != null) {
                            gmi.setEventsWhenReallyHappy(Utils.getArray(sAux));
                        }
                        sAux = UtilsXML.getChildValue(node.getChildNodes(), "eventsWhenAngry"); //$NON-NLS-1$
                        if (sAux != null) {
                            gmi.setEventsWhenAngry(Utils.getArray(sAux));
                        }
                        sAux = UtilsXML.getChildValue(node.getChildNodes(), "eventsWhenReallyAngry"); //$NON-NLS-1$
                        if (sAux != null) {
                            gmi.setEventsWhenReallyAngry(Utils.getArray(sAux));
                        }
                    } else {
                        gmi.setNamePool(UtilsXML.getChildValue(node.getChildNodes(), "namePool")); //$NON-NLS-1$
                        gmi.setSurName(UtilsXML.getChildValue(node.getChildNodes(), "surName")); //$NON-NLS-1$
                        gmi.setItemsLike(Utils.getArray(UtilsXML.getChildValue(node.getChildNodes(), "itemsLike"))); //$NON-NLS-1$
                        gmi.setItemsLikePCT(Utils.getArray(UtilsXML.getChildValue(node.getChildNodes(), "itemsLikePCT"))); //$NON-NLS-1$
                        gmi.setItemsDislike(Utils.getArray(UtilsXML.getChildValue(node.getChildNodes(), "itemsDislike"))); //$NON-NLS-1$
                        gmi.setItemsDislikePCT(Utils.getArray(UtilsXML.getChildValue(node.getChildNodes(), "itemsDislikePCT"))); //$NON-NLS-1$
                        gmi.setEventsWhenHappy(Utils.getArray(UtilsXML.getChildValue(node.getChildNodes(), "eventsWhenHappy"))); //$NON-NLS-1$
                        gmi.setEventsWhenReallyHappy(Utils.getArray(UtilsXML.getChildValue(node.getChildNodes(), "eventsWhenReallyHappy"))); //$NON-NLS-1$
                        gmi.setEventsWhenAngry(Utils.getArray(UtilsXML.getChildValue(node.getChildNodes(), "eventsWhenAngry"))); //$NON-NLS-1$
                        gmi.setEventsWhenReallyAngry(Utils.getArray(UtilsXML.getChildValue(node.getChildNodes(), "eventsWhenReallyAngry"))); //$NON-NLS-1$
                    }

                    // Lo anadimos a la hash
                    godsList.put(sIniHeader, gmi);
                }
            }
        } catch (Exception e) {
            Log.log(Log.LEVEL_ERROR, Messages.getString("GodManager.1") + " [" + e.getMessage() + "]", "GodManager"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            Game.exit();
        }
    }

    public static HashMap<String, GodManagerItem> getAllItems() {
        if (godsList == null) {
            loadItems();
        }

        return godsList;
    }

    public static void clear() {
        godsList = null;
    }
}
