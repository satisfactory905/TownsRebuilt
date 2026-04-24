package xaos.generator;

import java.util.ArrayList;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import xaos.main.Game;
import xaos.utils.Log;
import xaos.utils.Messages;
import xaos.utils.UtilsXML;

public class Generator {

    private ArrayList<GeneratorItem> list;

    public static Generator read(String sFile, Generator generator, boolean bFirst) {
        ArrayList<GeneratorItem> lista = generator.getList();
        if (lista == null) {
            lista = new ArrayList<GeneratorItem>();
        }

        // Leemos el gen_map.xml
        Document doc;
        try {
            doc = UtilsXML.loadXMLFile(sFile);

            // Lo recorremos y vamos generando la clase
            fillGenerator(lista, doc.getChildNodes().item(0), bFirst);

            generator.setList(lista);
        } catch (Exception e) {
            Log.log(Log.LEVEL_ERROR, Messages.getString("Generator.0") + sFile + "]", "Generator"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            Game.exit();
        }

        return generator;
    }

    public ArrayList<GeneratorItem> getList() {
        return list;
    }

    public void setList(ArrayList<GeneratorItem> list) {
        this.list = list;
    }

    /**
     * Rellena el Generator con los nodos pasados
     *
     * @param generator
     * @param childs
     */
    private static void fillGenerator(ArrayList<GeneratorItem> list, Node node, boolean bFirst) {
        if (node != null) {
            Node child;
            for (int i = 0, n = node.getChildNodes().getLength(); i < n; i++) {
                child = node.getChildNodes().item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    String sNodename = child.getNodeName();
                    String sID = null;
                    NamedNodeMap attributes = child.getAttributes();
                    if (attributes != null) {
                        Node nodeAttributes = attributes.getNamedItem("id"); //$NON-NLS-1$
                        if (nodeAttributes != null) {
                            sID = nodeAttributes.getNodeValue();
                        }
                    }

                    // Miramos si el ID ya existe
                    int iIndexExists = -1;
                    if (sID != null && sID.trim().length() > 0) {
                        for (int index = 0; index < list.size(); index++) {
                            GeneratorItem gi = list.get(index);
                            if (gi.getId() != null && gi.getId().equals(sID)) {
                                // Bingo
                                iIndexExists = index;
                                break;
                            }
                        }
                    }

                    // Si ya existe lo borramos
                    if (iIndexExists != -1) {
                        if (bFirst) {
                            Log.log(Log.LEVEL_DEBUG, Messages.getString("Generator.1") + " [" + sID + "]", "Generator"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                        }
                        list.remove(iIndexExists);
                    }

                    if (sNodename != null && sNodename.equalsIgnoreCase("DELETE")) { //$NON-NLS-1$
                        // Delete
                        // No hay que hacer nada
                    } else {
                        GeneratorItem gi = new GeneratorItem();
                        gi.setId(sID);
                        gi.setName(child.getNodeName());
                        gi.setList(getNodes(child));
                        // Si lo hemos borrado ponemos el nuevo item en la posicion que estaba
                        if (iIndexExists != -1) {
                            list.add(iIndexExists, gi);
                        } else {
                            list.add(gi);
                        }
                    }
                }
            }
        }
    }

    /**
     * A partir de un item del .xml devuelve todos los nodos
     *
     * @param node
     * @return
     */
    private static ArrayList<GeneratorNode> getNodes(Node node) {
        ArrayList<GeneratorNode> alReturn = new ArrayList<GeneratorNode>();
        Node child;
        for (int i = 0, n = node.getChildNodes().getLength(); i < n; i++) {
            child = node.getChildNodes().item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && child.getFirstChild().getNodeValue() != null && child.getFirstChild().getNodeValue().trim().length() > 0) {
                alReturn.add(new GeneratorNode(child.getNodeName(), child.getChildNodes().item(0).getNodeValue()));
            }
        }

        return alReturn;
    }
}
