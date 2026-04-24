package xaos.tiles.entities.buildings;

import java.util.ArrayList;

import xaos.tiles.entities.items.ItemManager;
import xaos.tiles.entities.items.ItemManagerItem;
import xaos.tiles.entities.living.LivingEntityManager;
import xaos.tiles.entities.living.LivingEntityManagerItem;
import xaos.tiles.terrain.TerrainManager;
import xaos.utils.Log;
import xaos.utils.Messages;
import xaos.utils.Point3DShort;
import xaos.utils.UtilsIniHeaders;

public class BuildingManagerItem {

    private String iniHeader;
    private String name;
    private ArrayList<String> descriptions; // Descripciones
    private String type;
    private short width;
    private short height;
    private String groundData;
    private Point3DShort entranceBaseCoordinates;
    private boolean canBeBuiltUnderground;
    private ArrayList<Integer> mustBeBuiltOver;
    private boolean mineTerrain;
    private ArrayList<int[]> prerequisites;
    private ArrayList<int[]> prerequisitesFriendly;

    private boolean automatic;

    public BuildingManagerItem(String sIniHeader, String sName, short iWidth, short iHeight) {
        iniHeader = sIniHeader;
        name = sName;
        width = iWidth;
        height = iHeight;
        prerequisites = new ArrayList<int[]>();
        prerequisitesFriendly = new ArrayList<int[]>();
    }

    public void setIniHeader(String iniHeader) {
        this.iniHeader = iniHeader;
    }

    public String getIniHeader() {
        return iniHeader;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescriptions(ArrayList<String> descriptions) {
        this.descriptions = descriptions;
    }

    public ArrayList<String> getDescriptions() {
        return descriptions;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public short getWidth() {
        return width;
    }

    public void setWidth(short width) {
        this.width = width;
    }

    public short getHeight() {
        return height;
    }

    public void setHeight(short height) {
        this.height = height;
    }

    public void setGroundData(String groundData) {
        if (groundData == null || groundData.length() == 0 || groundData.length() != (getWidth() * getHeight()) || groundData.indexOf(Building.GROUND_ENTRANCE) == -1) {
            if (groundData != null && groundData.length() > 0) {
                Log.log(Log.LEVEL_ERROR, Messages.getString("BuildingManagerItem.0") + getIniHeader() + Messages.getString("BuildingManagerItem.1"), getClass().toString()); //$NON-NLS-1$ //$NON-NLS-2$
            }

            // Entrance en la primera casilla
            StringBuffer sBuffer = new StringBuffer();
            sBuffer.append(Building.GROUND_ENTRANCE);
            for (int i = 1; i < (getHeight() * getWidth()); i++) {
                // Las demas 0 -> No transitable
                sBuffer.append(Building.GROUND_NON_TRANSITABLE);
            }

            this.groundData = sBuffer.toString();
        } else {
            this.groundData = groundData;
        }

        // Seteamos la entrance coordinates
        short iEntrance = (short) this.groundData.indexOf(Building.GROUND_ENTRANCE);
        short yEntrance = (short) (iEntrance / getWidth());
        short xEntrance = (short) (iEntrance - (yEntrance * getWidth()));
        setEntranceBaseCoordinates(Point3DShort.getPoolInstance(xEntrance, yEntrance, (short) 0));
    }

    public String getGroundData() {
        return groundData;
    }

    public void setEntranceBaseCoordinates(Point3DShort entranceBaseCoordinates) {
        this.entranceBaseCoordinates = entranceBaseCoordinates;
    }

    public Point3DShort getEntranceBaseCoordinates() {
        return entranceBaseCoordinates;
    }

    public void setCanBeBuiltUnderground(boolean canBeBuiltUnderground) {
        this.canBeBuiltUnderground = canBeBuiltUnderground;
    }

    public void setCanBeBuiltUnderground(String sCanBeBuiltUnderground) {
        // Por defecto es true
        if (sCanBeBuiltUnderground == null || sCanBeBuiltUnderground.trim().length() == 0) {
            setCanBeBuiltUnderground(true);
        } else {
            setCanBeBuiltUnderground(Boolean.parseBoolean(sCanBeBuiltUnderground));
        }
    }

    public boolean canBeBuiltUnderground() {
        return canBeBuiltUnderground;
    }

    public void setMustBeBuiltOver(ArrayList<String> mustBeBuiltOver) {
        if (mustBeBuiltOver != null) {
            ArrayList<Integer> alMustBeBuiltOver = new ArrayList<Integer>(mustBeBuiltOver.size());
            for (int i = 0; i < mustBeBuiltOver.size(); i++) {
                alMustBeBuiltOver.add(TerrainManager.getItem(mustBeBuiltOver.get(i)).getTerrainID());
            }

            this.mustBeBuiltOver = alMustBeBuiltOver;
        }
    }

    public ArrayList<Integer> getMustBeBuiltOver() {
        return mustBeBuiltOver;
    }

    public void setMineTerrain(boolean mineTerrain) {
        this.mineTerrain = mineTerrain;
    }

    public void setMineTerrain(String sMineTerrain) {
        setMineTerrain(Boolean.parseBoolean(sMineTerrain));
    }

    public boolean isMineTerrain() {
        return mineTerrain;
    }

    public ArrayList<int[]> getPrerequisites() {
        if (prerequisites == null) {
            return null;
        }

        // Devolvemos una copia, siempre
        ArrayList<int[]> alReturn = new ArrayList<int[]>(prerequisites.size());
        for (int i = 0; i < prerequisites.size(); i++) {
            int[] aAux = new int[prerequisites.get(i).length];
            for (int j = 0; j < aAux.length; j++) {
                aAux[j] = prerequisites.get(i)[j];
            }
            alReturn.add(aAux);
        }

        return alReturn;
    }

    public void setPrerequisites(ArrayList<String> prerequisites) throws Exception {
        if (prerequisites == null) {
            this.prerequisites = null;
            return;
        }

        ItemManagerItem imi;
        String sHeader;
        this.prerequisites = new ArrayList<int[]>();
        for (int i = 0; i < prerequisites.size(); i++) {
            String sList = prerequisites.get(i);
            int[] aInts = UtilsIniHeaders.getIntsArray(sList);
            if (aInts != null) {
                for (int items = 0; items < aInts.length; items++) {
                    sHeader = UtilsIniHeaders.getStringIniHeader(aInts[items]);
                    imi = ItemManager.getItem(sHeader);
                    if (imi == null) {
                        throw new Exception(Messages.getString("BuildingManagerItem.2") + sHeader + "]"); //$NON-NLS-1$ //$NON-NLS-2$
                    }
                }
                this.prerequisites.add(aInts);
            }
        }

        if (this.prerequisites.size() == 0) {
            this.prerequisites = null;
        }
    }

    public ArrayList<int[]> getPrerequisitesFriendly() {
        if (prerequisitesFriendly == null) {
            return null;
        }

        // Devolvemos una copia, siempre
        ArrayList<int[]> alReturn = new ArrayList<int[]>(prerequisitesFriendly.size());
        for (int i = 0; i < prerequisitesFriendly.size(); i++) {
            int[] aAux = new int[prerequisitesFriendly.get(i).length];
            for (int j = 0; j < aAux.length; j++) {
                aAux[j] = prerequisitesFriendly.get(i)[j];
            }
            alReturn.add(aAux);
        }

        return alReturn;
    }

    public void setPrerequisitesFriendly(ArrayList<String> prerequisitesFriendly) throws Exception {
        if (prerequisitesFriendly == null) {
            this.prerequisitesFriendly = null;
            return;
        }

        LivingEntityManagerItem lemi;
        String sHeader;
        this.prerequisitesFriendly = new ArrayList<int[]>();
        for (int i = 0; i < prerequisitesFriendly.size(); i++) {
            String sList = prerequisitesFriendly.get(i);
            int[] aInts = UtilsIniHeaders.getIntsArray(sList);
            if (aInts != null) {
                for (int items = 0; items < aInts.length; items++) {
                    sHeader = UtilsIniHeaders.getStringIniHeader(aInts[items]);
                    lemi = LivingEntityManager.getItem(sHeader);
                    if (lemi == null) {
                        throw new Exception(Messages.getString("BuildingManagerItem.4") + sHeader + "]"); //$NON-NLS-1$ //$NON-NLS-2$
                    }
                }
                this.prerequisitesFriendly.add(aInts);
            }
        }

        if (this.prerequisitesFriendly.size() == 0) {
            this.prerequisitesFriendly = null;
        }
    }

    public void setAutomatic(String sAutomatic) {
        setAutomatic(Boolean.parseBoolean(sAutomatic));
    }

    public void setAutomatic(boolean automatic) {
        this.automatic = automatic;
    }

    public boolean isAutomatic() {
        return automatic;
    }
}
