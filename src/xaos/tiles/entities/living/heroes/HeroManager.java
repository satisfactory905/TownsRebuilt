package xaos.tiles.entities.living.heroes;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import xaos.Towns;
import xaos.data.HeroPrerequisite;
import xaos.main.Game;
import xaos.utils.Log;
import xaos.utils.Messages;
import xaos.utils.UtilsXML;

public class HeroManager {

    /**
     * Lista de livingEntitiesManagerItems
     */
    private static HashMap<String, ArrayList<HeroPrerequisite>> hmComePrerequisites;
    private static HashMap<String, ArrayList<HeroPrerequisite>> hmStayPrerequisites;
    private static HashMap<String, HeroBehaviour> hmBehaviours;
    private static HashMap<String, HeroSkills> hmSkills;

    /**
     * Carga los livingEntities en la hash. Usa los .xml
     */
    private static void loadHerosData() {
        hmComePrerequisites = new HashMap<String, ArrayList<HeroPrerequisite>>();
        hmStayPrerequisites = new HashMap<String, ArrayList<HeroPrerequisite>>();
        hmBehaviours = new HashMap<String, HeroBehaviour>();
        hmSkills = new HashMap<String, HeroSkills>();

        // Cargar de fichero
        loadXML(Towns.getPropertiesString("DATA_FOLDER") + "heroes.xml"); //$NON-NLS-1$ //$NON-NLS-2$

        // Mods
        File fUserFolder = new File(Game.getUserFolder());
        if (!fUserFolder.exists() || !fUserFolder.isDirectory()) {
            return;
        }

        ArrayList<String> alMods = Game.getModsLoaded();
        if (alMods != null && alMods.size() > 0) {
            for (int i = 0; i < alMods.size(); i++) {
                String sModActionsPath = fUserFolder.getAbsolutePath() + System.getProperty("file.separator") + Game.MODS_FOLDER1 + System.getProperty("file.separator") + alMods.get(i) + System.getProperty("file.separator") + Towns.getPropertiesString("DATA_FOLDER") + "heroes.xml"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                File fIni = new File(sModActionsPath);
                if (fIni.exists()) {
                    loadXML(sModActionsPath);
                }
            }
        }
    }

    public static ArrayList<HeroPrerequisite> getComePrerequisites(String sIniHeader) {
        if (hmComePrerequisites == null) {
            loadHerosData();
        }

        return hmComePrerequisites.get(sIniHeader);
    }

    public static ArrayList<HeroPrerequisite> getStayPrerequisites(String sIniHeader) {
        if (hmStayPrerequisites == null) {
            loadHerosData();
        }

        return hmStayPrerequisites.get(sIniHeader);
    }

    public static HeroBehaviour getBehaviour(String sID) {
        if (hmBehaviours == null) {
            loadHerosData();
        }

        return hmBehaviours.get(sID);
    }

    public static HeroSkills getSkills(String sID) {
        if (hmSkills == null) {
            loadHerosData();
        }

        return hmSkills.get(sID);
    }

    private static void loadXML(String sXMLName) {
        try {
            Document doc = UtilsXML.loadXMLFile(sXMLName); //$NON-NLS-1$

            // Tenemos el documento XML parseado
            NodeList nodeList = doc.getDocumentElement().getChildNodes();
            Node node;
            String sID;
            for (int i = 0; i < nodeList.getLength(); i++) {
                node = nodeList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    int iType = 0;
                    if (node.getNodeName().equals("comePrerequisites")) { //$NON-NLS-1$
                        iType = 1;
                    } else if (node.getNodeName().equals("stayPrerequisites")) { //$NON-NLS-1$
                        iType = 2;
                    } else if (node.getNodeName().equals("behaviours")) { //$NON-NLS-1$
                        iType = 3;
                    } else if (node.getNodeName().equals("skills")) { //$NON-NLS-1$
                        iType = 4;
                    } else {
                        throw new Exception(Messages.getString("HeroManager.3") + node.getNodeName() + "]"); //$NON-NLS-1$ //$NON-NLS-2$
                    }

                    // Tenemos el nodo de prerequisitos
                    NodeList nodesPrerequisites = node.getChildNodes();
                    Node nodePrerequisite;
                    for (int j = 0; j < nodesPrerequisites.getLength(); j++) {
                        nodePrerequisite = nodesPrerequisites.item(j);
                        if (nodePrerequisite.getNodeType() == Node.ELEMENT_NODE) {
                            sID = nodePrerequisite.getNodeName();

                            ArrayList<HeroPrerequisite> alPrerequisites = new ArrayList<HeroPrerequisite>();
                            HeroBehaviour behaviour = new HeroBehaviour();
                            String sHeroSkills = null;
                            String sHeroSkillLevels = null;

                            // Tenemos el prerequisito, ahora miramos todo su contenido
                            NodeList nodePrerequisiteChilds = nodePrerequisite.getChildNodes();
                            Node nodePrerequisiteChild;
                            for (int v = 0; v < nodePrerequisiteChilds.getLength(); v++) {
                                nodePrerequisiteChild = nodePrerequisiteChilds.item(v);
                                if (nodePrerequisiteChild.getNodeType() == Node.ELEMENT_NODE) {
                                    String sChildID = nodePrerequisiteChild.getNodeName();

                                    if (iType == 1 || iType == 2) {
                                        // Tenemos el prerequisito, ahora miramos todo su contenido
                                        HeroPrerequisite hp = new HeroPrerequisite();
                                        hp.setId(sChildID);
                                        hp.setValue(UtilsXML.getChildValue(nodePrerequisiteChilds, sChildID));

                                        // Lo anadimos a la lista
                                        alPrerequisites.add(hp);
                                    } else if (iType == 3) {
                                        // Behaviours
                                        if (sChildID.equalsIgnoreCase("IDLEPCT")) { //$NON-NLS-1$
                                            behaviour.setIdlePCT(UtilsXML.getChildValue(nodePrerequisiteChilds, sChildID));
                                        } else if (sChildID.equalsIgnoreCase("EXPLOREPCT")) { //$NON-NLS-1$
                                            behaviour.setExplorePCT(UtilsXML.getChildValue(nodePrerequisiteChilds, sChildID));
                                        } else {
                                            throw new Exception(Messages.getString("HeroManager.1") + sChildID + "]"); //$NON-NLS-1$ //$NON-NLS-2$
                                        }
                                    } else if (iType == 4) {
                                        // Skills
                                        if (sChildID.equalsIgnoreCase("SKILLS")) { //$NON-NLS-1$
                                            sHeroSkills = UtilsXML.getChildValue(nodePrerequisiteChilds, sChildID);
                                        } else if (sChildID.equalsIgnoreCase("LEVELS")) { //$NON-NLS-1$
                                            sHeroSkillLevels = UtilsXML.getChildValue(nodePrerequisiteChilds, sChildID);
                                        } else {
                                            throw new Exception("Unknown skill tag [" + sChildID + "]");
                                        }
                                    }
                                }
                            }

                            // Anadimos la lista a la hash que toque
                            if (iType == 1) {
                                hmComePrerequisites.put(sID, alPrerequisites);
                            } else if (iType == 2) {
                                hmStayPrerequisites.put(sID, alPrerequisites);
                            } else if (iType == 3) {
                                if (behaviour.checkPCTs()) {
                                    hmBehaviours.put(sID, behaviour);
                                } else {
                                    throw new Exception(Messages.getString("HeroManager.4") + sID + "]"); //$NON-NLS-1$ //$NON-NLS-2$
                                }
                            } else if (iType == 4) {
                                hmSkills.put(sID, new HeroSkills(sHeroSkills, sHeroSkillLevels));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.log(Log.LEVEL_ERROR, Messages.getString("HeroManager.0") + sXMLName + "] [" + e.toString() + "]", "HeroManager"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            Game.exit();
        }
    }

    public static void clear() {
        hmComePrerequisites = null;
        hmStayPrerequisites = null;
        hmBehaviours = null;
        hmSkills = null;
    }
}
