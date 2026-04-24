package xaos.gods;

import java.util.ArrayList;

import xaos.data.GodData;
import xaos.main.Game;
import xaos.utils.Messages;
import xaos.utils.Names;
import xaos.utils.Utils;

/**
 * Clase de tipo "managerItem", no es la que se anade a la lista de gods
 */
public class GodManagerItem {

    private String id;
    private String namePool;
    private String surName;

    private ArrayList<String> itemsLike;
    private ArrayList<Integer> itemsLikePCT;
    private ArrayList<String> itemsDislike;
    private ArrayList<Integer> itemsDislikePCT;

    private ArrayList<String> eventsWhenHappy;
    private ArrayList<String> eventsWhenReallyHappy;
    private ArrayList<String> eventsWhenAngry;
    private ArrayList<String> eventsWhenReallyAngry;

    public GodData getGodDataInstance() {
        GodData godData = new GodData(id);

        // Full name
        String sAux;
        if (Game.getWorld() != null) {
            sAux = Names.getName(getNamePool(), Game.getWorld().getCampaignID(), Game.getWorld().getMissionID());
        } else {
            sAux = Names.getName(getNamePool(), null, null);
        }
        if (sAux == null) {
            if (Utils.getRandomBetween(1, 2) == 1) {
                sAux = "Xavi"; //$NON-NLS-1$
            } else {
                sAux = "Supermalparit"; //$NON-NLS-1$
            }
        }
        sAux += getSurName();

        godData.setFullName(sAux);

        return godData;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setNamePool(String namePool) throws Exception {
        this.namePool = namePool;

        if (this.namePool == null || this.namePool.length() == 0) {
            throw new Exception(Messages.getString("GodManagerItem.0")); //$NON-NLS-1$
        } else {
            if (Names.getName(this.namePool, null, null) == null) {
                throw new Exception(Messages.getString("GodManagerItem.1")); //$NON-NLS-1$
            }
        }
    }

    public String getNamePool() {
        return namePool;
    }

    public void setSurName(String surName) throws Exception {
        this.surName = surName;

        if (this.surName == null || this.surName.length() == 0) {
            throw new Exception(Messages.getString("GodManagerItem.2")); //$NON-NLS-1$
        }
    }

    public String getSurName() {
        return surName;
    }

    public void setItemsLike(ArrayList<String> itemsLike) {
        this.itemsLike = itemsLike;
    }

    public ArrayList<String> getItemsLike() {
        return itemsLike;
    }

    public void setItemsLikePCTInteger(ArrayList<Integer> itemsLikePCT) {
        this.itemsLikePCT = itemsLikePCT;
    }

    public void setItemsLikePCT(ArrayList<String> itemsLikePCT) throws Exception {
        if (itemsLikePCT == null) {
            setItemsLikePCTInteger(null);

            // Si habia items, BAM, error
            if (getItemsLike() != null && getItemsLike().size() > 0) {
                throw new Exception(Messages.getString("GodManagerItem.4")); //$NON-NLS-1$
            }
        } else {
            // Si no hay items o el tamano no es el mismo, BAM, error
            if (getItemsLike() == null || getItemsLike().size() != itemsLikePCT.size()) {
                throw new Exception(Messages.getString("GodManagerItem.4")); //$NON-NLS-1$
            }
            ArrayList<Integer> alPCT = new ArrayList<Integer>(itemsLikePCT.size());
            for (int i = 0; i < itemsLikePCT.size(); i++) {
                try {
                    alPCT.add(Integer.parseInt(itemsLikePCT.get(i)));
                } catch (Exception e) {
                    throw new Exception(Messages.getString("GodManagerItem.3") + " [" + itemsLikePCT.get(i) + "]"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                }
            }
            setItemsLikePCTInteger(alPCT);
        }
    }

    public ArrayList<Integer> getItemsLikePCT() {
        return itemsLikePCT;
    }

    public void setItemsDislike(ArrayList<String> itemsDislike) {
        this.itemsDislike = itemsDislike;
    }

    public ArrayList<String> getItemsDislike() {
        return itemsDislike;
    }

    public void setItemsDislikePCTInteger(ArrayList<Integer> itemsDislikePCT) {
        this.itemsDislikePCT = itemsDislikePCT;
    }

    public void setItemsDislikePCT(ArrayList<String> itemsDislikePCT) throws Exception {
        if (itemsDislikePCT == null) {
            setItemsDislikePCTInteger(null);

            // Si habia items, BAM, error
            if (getItemsDislike() != null && getItemsDislike().size() > 0) {
                throw new Exception(Messages.getString("GodManagerItem.7")); //$NON-NLS-1$
            }
        } else {
            // Si no hay items o el tamano no es el mismo, BAM, error
            if (getItemsDislike() == null || getItemsDislike().size() != itemsDislikePCT.size()) {
                throw new Exception(Messages.getString("GodManagerItem.7")); //$NON-NLS-1$
            }
            ArrayList<Integer> alPCT = new ArrayList<Integer>(itemsDislikePCT.size());
            for (int i = 0; i < itemsDislikePCT.size(); i++) {
                try {
                    alPCT.add(Integer.parseInt(itemsDislikePCT.get(i)));
                } catch (Exception e) {
                    throw new Exception(Messages.getString("GodManagerItem.6") + " [" + itemsDislikePCT.get(i) + "]"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                }
            }
            setItemsDislikePCTInteger(alPCT);
        }
    }

    public ArrayList<Integer> getItemsDislikePCT() {
        return itemsDislikePCT;
    }

    public void setEventsWhenHappy(ArrayList<String> eventsWhenHappy) {
        this.eventsWhenHappy = eventsWhenHappy;
    }

    public ArrayList<String> getEventsWhenHappy() {
        return eventsWhenHappy;
    }

    public void setEventsWhenReallyHappy(ArrayList<String> eventsWhenReallyHappy) {
        this.eventsWhenReallyHappy = eventsWhenReallyHappy;
    }

    public ArrayList<String> getEventsWhenReallyHappy() {
        return eventsWhenReallyHappy;
    }

    public void setEventsWhenAngry(ArrayList<String> eventsWhenAngry) {
        this.eventsWhenAngry = eventsWhenAngry;
    }

    public ArrayList<String> getEventsWhenAngry() {
        return eventsWhenAngry;
    }

    public void setEventsWhenReallyAngry(ArrayList<String> eventsWhenReallyAngry) {
        this.eventsWhenReallyAngry = eventsWhenReallyAngry;
    }

    public ArrayList<String> getEventsWhenReallyAngry() {
        return eventsWhenReallyAngry;
    }
}
