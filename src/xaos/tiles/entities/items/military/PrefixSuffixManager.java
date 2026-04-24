package xaos.tiles.entities.items.military;

import java.io.File;
import java.util.ArrayList;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import xaos.Towns;
import xaos.main.Game;
import xaos.utils.Log;
import xaos.utils.Messages;
import xaos.utils.Utils;
import xaos.utils.UtilsXML;

public class PrefixSuffixManager {

    private static ArrayList<PrefixSuffixManagerItem> alPrefixes;
    private static ArrayList<PrefixSuffixManagerItem> alSuffixes;

    private static void loadData() {
        if (alPrefixes == null || alSuffixes == null) {
            alPrefixes = new ArrayList<PrefixSuffixManagerItem>();
            alSuffixes = new ArrayList<PrefixSuffixManagerItem>();

            loadXMLData(Towns.getPropertiesString("DATA_FOLDER") + "prefixsuffix.xml");

            // Mods
            File fUserFolder = new File(Game.getUserFolder());
            if (!fUserFolder.exists() || !fUserFolder.isDirectory()) {
                return;
            }

            ArrayList<String> alMods = Game.getModsLoaded();
            if (alMods != null && alMods.size() > 0) {
                for (int i = 0; i < alMods.size(); i++) {
                    String sModActionsPath = fUserFolder.getAbsolutePath() + System.getProperty("file.separator") + Game.MODS_FOLDER1 + System.getProperty("file.separator") + alMods.get(i) + System.getProperty("file.separator") + Towns.getPropertiesString("DATA_FOLDER") + "prefixsuffix.xml"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    File fIni = new File(sModActionsPath);
                    if (fIni.exists()) {
                        loadXMLData(sModActionsPath);
                    }
                }
            }
        }
    }

    private static void loadXMLData(String sXMLPath) {
        try {
            Document doc = UtilsXML.loadXMLFile(sXMLPath); //$NON-NLS-1$ //$NON-NLS-2$

			// Tenemos el documento XML parseado
            // Lo recorremos entero y vamos anadiendo los prefijos/sufijos en el array que toque
            NodeList nodeList = doc.getDocumentElement().getChildNodes();
            Node node;
            for (int i = 0; i < nodeList.getLength(); i++) {
                node = nodeList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    String sName = node.getNodeName();
                    if (sName != null && sName.equalsIgnoreCase("DELETE")) {
                        // Miramos que ID quiere borrar
                        if (node.getAttributes() != null && node.getAttributes().getNamedItem("id") != null) {
                            String sIDToDelete = node.getAttributes().getNamedItem("id").getNodeValue();
                            if (sIDToDelete != null) {
                                // Buscamos y lo borramos
                                int ps = alPrefixes.size() - 1;
                                while (ps >= 0) {
                                    if (alPrefixes.get(ps).getId().equalsIgnoreCase(sIDToDelete)) {
                                        alPrefixes.remove(ps);
                                    }
                                    ps--;
                                }
                                ps = alSuffixes.size() - 1;
                                while (ps >= 0) {
                                    if (alSuffixes.get(ps).getId().equalsIgnoreCase(sIDToDelete)) {
                                        alSuffixes.remove(ps);
                                    }
                                    ps--;
                                }
                            }
                        }
                        continue;
                    }

                    PrefixSuffixManagerItem psmi = new PrefixSuffixManagerItem();

                    psmi.setType(node.getNodeName());
                    psmi.setId(UtilsXML.getChildValue(node.getChildNodes(), "id")); //$NON-NLS-1$
                    psmi.setName(UtilsXML.getChildValue(node.getChildNodes(), "name")); //$NON-NLS-1$
                    psmi.setAttack(UtilsXML.getChildValue(node.getChildNodes(), "attack")); //$NON-NLS-1$
                    psmi.setAttackSpeed(UtilsXML.getChildValue(node.getChildNodes(), "attackSpeed")); //$NON-NLS-1$
                    psmi.setDefense(UtilsXML.getChildValue(node.getChildNodes(), "defense")); //$NON-NLS-1$
                    psmi.setHealthPoints(UtilsXML.getChildValue(node.getChildNodes(), "healthPoints")); //$NON-NLS-1$
                    psmi.setDamage(UtilsXML.getChildValue(node.getChildNodes(), "damage")); //$NON-NLS-1$
                    psmi.setLOS(UtilsXML.getChildValue(node.getChildNodes(), "LOS")); //$NON-NLS-1$
                    psmi.setMovePCT(UtilsXML.getChildValue(node.getChildNodes(), "movePCT")); //$NON-NLS-1$
                    psmi.setWalkSpeed(UtilsXML.getChildValue(node.getChildNodes(), "walkSpeed")); //$NON-NLS-1$

                    // Lo anadimos donde toque
                    if (psmi.getType().equalsIgnoreCase(PrefixSuffixData.TYPE_PREFIX)) {
                        alPrefixes.add(psmi);
                    } else {
                        alSuffixes.add(psmi);
                    }
                }
            }
        } catch (Exception e) {
            Log.log(Log.LEVEL_ERROR, Messages.getString("PrefixSuffixManager.0") + e.toString() + "]", "PrefixSuffixManager"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            Game.exit();
        }
    }

    public static PrefixSuffixManagerItem getRandomPrefix() {
        loadData();
        if (alPrefixes.size() == 0) {
            return null;
        }
        return alPrefixes.get(Utils.getRandomBetween(0, alPrefixes.size() - 1));
    }

    public static PrefixSuffixManagerItem getRandomSuffix() {
        loadData();
        if (alSuffixes.size() == 0) {
            return null;
        }
        return alSuffixes.get(Utils.getRandomBetween(0, alSuffixes.size() - 1));
    }

    public static void clear() {
        alPrefixes = null;
        alSuffixes = null;
    }
}
