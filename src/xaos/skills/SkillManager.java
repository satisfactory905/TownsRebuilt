package xaos.skills;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import xaos.Towns;
import xaos.effects.EffectManager;
import xaos.main.Game;
import xaos.utils.Log;
import xaos.utils.Messages;
import xaos.utils.UtilsXML;

public class SkillManager {

    private static HashMap<String, SkillManagerItem> skillList;

    private static void loadItems() {
        skillList = new HashMap<String, SkillManagerItem>();

        // Cargar de fichero
        loadXMLSkills(Towns.getPropertiesString("DATA_FOLDER") + "skills.xml", true); //$NON-NLS-1$ //$NON-NLS-2$

        // Mods
        File fUserFolder = new File(Game.getUserFolder());
        if (!fUserFolder.exists() || !fUserFolder.isDirectory()) {
            return;
        }

        ArrayList<String> alMods = Game.getModsLoaded();
        if (alMods != null && alMods.size() > 0) {
            for (int i = 0; i < alMods.size(); i++) {
                String sModActionsPath = fUserFolder.getAbsolutePath() + System.getProperty("file.separator") + Game.MODS_FOLDER1 + System.getProperty("file.separator") + alMods.get(i) + System.getProperty("file.separator") + Towns.getPropertiesString("DATA_FOLDER") + "skills.xml"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                File fIni = new File(sModActionsPath);
                if (fIni.exists()) {
                    loadXMLSkills(sModActionsPath, false);
                }
            }
        }
    }

    public static SkillManagerItem getItem(String sIniHeader) {
        if (skillList == null) {
            loadItems();
        }

        if (sIniHeader == null) {
            return null;
        }

        return skillList.get(sIniHeader);
    }

    private static void loadXMLSkills(String sXMLName, boolean bLoadingMain) {
        try {
            Document doc = UtilsXML.loadXMLFile(sXMLName); //$NON-NLS-1$

			// Tenemos el documento XML parseado
            // Lo recorremos entero y vamos anadiendo las skills (SkillManagerItem) a la hash
            NodeList nodeList = doc.getDocumentElement().getChildNodes();
            Node node;
            SkillManagerItem item;
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
                                skillList.remove(sIDToDelete);
                            }
                        }
                        continue;
                    }

                    boolean bExists = skillList.containsKey(sIniHeader);
                    // Mod cambiando valores de un item que ya existe?
                    boolean bModChangingValues = (bExists && !bLoadingMain);

                    if (bModChangingValues) {
                        item = skillList.get(sIniHeader);

                        String sAux = UtilsXML.getChildValue(node.getChildNodes(), "name"); //$NON-NLS-1$
                        if (sAux != null) {
                            item.setName(sAux);
                        }
                        sAux = UtilsXML.getChildValue(node.getChildNodes(), "coolDown"); //$NON-NLS-1$
                        if (sAux != null) {
                            item.setCoolDown(sAux);
                        }
                        sAux = UtilsXML.getChildValue(node.getChildNodes(), "use"); //$NON-NLS-1$
                        if (sAux != null) {
                            item.setUse(sAux);
                        }

                        // Especiales
                        sAux = UtilsXML.getChildValue(node.getChildNodes(), "taunt"); //$NON-NLS-1$
                        if (sAux != null) {
                            item.setTaunt(sAux);
                        }
                        sAux = UtilsXML.getChildValue(node.getChildNodes(), "raiseDead"); //$NON-NLS-1$
                        if (sAux != null) {
                            item.setRaiseDead(sAux);
                        }
                        sAux = UtilsXML.getChildValue(node.getChildNodes(), "maxRaised"); //$NON-NLS-1$
                        if (sAux != null) {
                            item.setMaxRaised(sAux);
                        }
                    } else {
                        item = new SkillManagerItem();
                        item.setIniHeader(sIniHeader);
                        item.setName(UtilsXML.getChildValue(node.getChildNodes(), "name")); //$NON-NLS-1$
                        item.setCoolDown(UtilsXML.getChildValue(node.getChildNodes(), "coolDown")); //$NON-NLS-1$
                        item.setUse(UtilsXML.getChildValue(node.getChildNodes(), "use")); //$NON-NLS-1$

                        // Especiales
                        item.setTaunt(UtilsXML.getChildValue(node.getChildNodes(), "taunt")); //$NON-NLS-1$
                        item.setRaiseDead(UtilsXML.getChildValue(node.getChildNodes(), "raiseDead")); //$NON-NLS-1$
                        item.setMaxRaised(UtilsXML.getChildValue(node.getChildNodes(), "maxRaised")); //$NON-NLS-1$
                    }

                    // Effects
                    item.setEffects(loadEffectData(node.getChildNodes()));

                    // Lo anadimos a la hash
                    skillList.put(sIniHeader, item);
                }
            }
        } catch (Exception e) {
            Log.log(Log.LEVEL_ERROR, Messages.getString("SkillManager.0") + e.toString() + "]", "SkillManager"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            Game.exit();
        }
    }

    private static ArrayList<SkillEffectItem> loadEffectData(NodeList nodeList) throws Exception {
        ArrayList<SkillEffectItem> alEffects = new ArrayList<SkillEffectItem>();

        Node node;
        for (int i = 0; i < nodeList.getLength(); i++) {
            node = nodeList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals("effect")) { //$NON-NLS-1$
                SkillEffectItem skillEffectItem = new SkillEffectItem();
                skillEffectItem.setId(UtilsXML.getChildValue(node.getChildNodes(), "id")); //$NON-NLS-1$
                if (skillEffectItem.getId() == null) {
                    throw new Exception(Messages.getString("SkillManager.1")); //$NON-NLS-1$
                }
                skillEffectItem.setTarget(UtilsXML.getChildValue(node.getChildNodes(), "target")); //$NON-NLS-1$
                alEffects.add(skillEffectItem);

                // Miramos que exista el efecto
                if (EffectManager.getItem(skillEffectItem.getId()) == null) {
                    throw new Exception(Messages.getString("SkillManager.2") + skillEffectItem.getId() + "]"); //$NON-NLS-1$ //$NON-NLS-2$
                }
            }
        }

        return alEffects;
    }

    public static void clear() {
        skillList = null;
    }
}
