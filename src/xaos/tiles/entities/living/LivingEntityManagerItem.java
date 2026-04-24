package xaos.tiles.entities.living;

import java.awt.Point;
import java.util.ArrayList;

import xaos.caravans.CaravanManager;
import xaos.data.DropData;
import xaos.data.LivingEntityData;
import xaos.effects.EffectManager;
import xaos.tiles.entities.items.ItemManager;
import xaos.tiles.terrain.TerrainManager;
import xaos.tiles.terrain.TerrainManagerItem;
import xaos.utils.Messages;
import xaos.utils.Utils;
import xaos.utils.UtilsAL;
import xaos.utils.UtilsIniHeaders;

public class LivingEntityManagerItem {

    public static final String TYPE_CITIZEN = "CITIZEN"; //$NON-NLS-1$
    public static final String TYPE_ENEMY = "ENEMY"; //$NON-NLS-1$
    public static final String TYPE_FRIENDLY = "FRIENDLY"; //$NON-NLS-1$
    public static final String TYPE_ALLY = "ALLY"; //$NON-NLS-1$
    public static final String TYPE_HERO = "HERO"; //$NON-NLS-1$

    private String iniHeader;
    private int type;
    private int level;

    private String name;
    private String namePoolTag;
    private String surnamePoolTag;
    private boolean female;
    private String attack;
    private String attackSpeed;
    private String defense;
    private String healthPoints;
    private String attackVerb;
    private String attackVerbInfinitive;
    private String damage;
    private String LOS;
    private String movePCT;
    private String walkSpeed;
    private boolean grouping;
    private int HPregeneration;

    // Age
    private String maxAge; // Edad maxima del living
    private String maxAgeLiving; // Living que "suelta" al morir

    // Equipment
    private ArrayList<String> equip;

    // Equipment
    private ArrayList<String> equipAllowed;

    // Habitat
    private ArrayList<Integer> habitat;
    private int habitatHeightMin;
    private int habitatHeightMax;

    // Hate
    private String hate;

    private String building; // Edificio donde se puede spawnear
    private int buildingTime; // Tiempo que tarda en spawnear

    // Drops
    private ArrayList<DropData> dropData;

    // Alternative graphics
    private ArrayList<String> altGraphics;

    // Actions
    private ArrayList<String> actions;

    // Directions
    private boolean facingDirections;

    // Hungry & sleep
    private String maxHungryTurns;
    private String maxSleepTurns;

    // Heros
    private String heroComePrerequisite;
    private String heroStayPrerequisite;
    private String heroBehaviour;
    private String heroSkills;

    // Moral
    private String moral;

    // Eat zone
    private String eatZone;

    // Follow
    private int[] followEntity;
    private String followTurns;

    // Effects
    private ArrayList<String> effects;
    private ArrayList<String> effectsImmune;

    // Evade traps
    private boolean evadeTraps;

    // Caravan
    private String caravan;

    // Steal
    private int[] steal;
    private int[] stealLivings;

    // FX
    private String fxDead;

    // Food
    private int[] foodNeeded;
    private int foodNeededTurns;
    private int foodNeededDieTurns;

    // Animated when idle
    private boolean animatedWhenIdle;

    // Work/Idle counters
    private String workCounterPCT;
    private String idleCounterPCT;

    public LivingEntityManagerItem() {
    }

    public LivingEntityManagerItem(LivingEntityManagerItem lemi) {
        this.iniHeader = new String(lemi.getIniHeader());
        this.type = lemi.getType();
        this.level = lemi.getLevel();

        this.name = lemi.getName();
        this.namePoolTag = lemi.getNamePoolTag();
        this.surnamePoolTag = lemi.getSurnamePoolTag();
        this.female = lemi.isFemale();
        this.attack = lemi.getAttack();
        this.attackSpeed = lemi.getAttackSpeed();
        this.defense = lemi.getDefense();
        this.healthPoints = lemi.getHealthPoints();
        this.attackVerb = lemi.getAttackVerb();
        this.attackVerbInfinitive = lemi.getAttackVerbInfinitive();
        this.damage = lemi.getDamage();
        this.LOS = lemi.getLOS();
        this.movePCT = lemi.getMovePCT();
        this.walkSpeed = lemi.getWalkSpeed();
        this.grouping = lemi.hasGrouping();
        this.HPregeneration = lemi.getHPRegeneration();

        this.maxAge = lemi.getMaxAge();
        this.maxAgeLiving = lemi.getMaxAgeLiving();

        this.equip = lemi.getEquip();
        this.equipAllowed = lemi.getEquipAllowed();

        this.habitat = lemi.getHabitat();
        this.habitatHeightMin = lemi.getHabitatHeightMin();
        this.habitatHeightMax = lemi.getHabitatHeightMax();

        this.hate = lemi.getHate();

        this.building = lemi.getBuilding();
        this.buildingTime = lemi.getBuildingTime();

        this.dropData = lemi.getDropData();

        this.altGraphics = lemi.getAltGraphics();

        this.actions = lemi.getActions();

        this.facingDirections = lemi.isFacingDirections();

        this.maxHungryTurns = lemi.getMaxHungryTurns();
        this.maxSleepTurns = lemi.getMaxSleepTurns();

        this.heroComePrerequisite = lemi.getHeroComePrerequisite();
        this.heroStayPrerequisite = lemi.getHeroStayPrerequisite();
        this.heroBehaviour = lemi.getHeroBehaviour();
        this.heroSkills = lemi.getHeroSkills();

        this.moral = lemi.getMoral();
        this.eatZone = lemi.getEatZone();

        this.followEntity = lemi.getFollowEntity();
        this.followTurns = lemi.getFollowTurns();

        this.effects = lemi.getEffects();
        this.effectsImmune = lemi.getEffectsImmune();

        this.evadeTraps = lemi.isEvadeTraps();

        this.caravan = lemi.getCaravan();

        this.steal = lemi.getSteal();
        this.stealLivings = lemi.getStealLivings();

        this.fxDead = lemi.getFxDead();

        this.foodNeeded = lemi.getFoodNeeded();
        this.foodNeededTurns = lemi.getFoodNeededTurns();
        this.foodNeededTurns = lemi.getFoodNeededDieTurns();

        this.animatedWhenIdle = lemi.isAnimatedWhenIdle();

        this.workCounterPCT = lemi.getWorkCounterPCT();
        this.idleCounterPCT = lemi.getIdleCounterPCT();
    }

    public LivingEntityData getRandom() {
        // Se ponen aqui las cosas que hay que lanzar dados, las cosas fijas no hace falta, siempre se pueden consultar de un LivingEntityManagerItem
        LivingEntityData led = new LivingEntityData();
        led.setName(getName());

        // Attack
        led.setAttackBase(Utils.launchDice(getAttack()));

        // Attack speed
        led.setAttackSpeedBase(Utils.launchDice(getAttackSpeed()));

        // Defense
        led.setDefenseBase(Utils.launchDice(getDefense()));

        // HPs
        int iAux = Utils.launchDice(getHealthPoints());
        led.setHealthPoints(iAux);
        led.setHealthPointsMAXBase(iAux);

        // Damage
        led.setDamageBase(Utils.launchDice(getDamage()));

        // LOS
        led.setLOSBase(Utils.launchDice(getLOS()));

        // movePCT
        led.setMovePCTBase(Utils.launchDice(getMovePCT()));

        // Walk speed
        led.setWalkSpeedBase(Utils.launchDice(getWalkSpeed()));

        // Moral
        led.setMoral(Utils.launchDice(getMoral()));

        led.setFollowTurnsCounter(Utils.launchDice(getFollowTurns()));

        return led;
    }

    public void setIniHeader(String iniHeader) {
        this.iniHeader = iniHeader;
    }

    public String getIniHeader() {
        return iniHeader;
    }

    public void setType(String type) throws Exception {
        if (type == null) {
            throw new Exception(Messages.getString("LivingEntityManagerItem.4")); //$NON-NLS-1$
        } else {
            if (type.equalsIgnoreCase(TYPE_CITIZEN)) {
                this.type = LivingEntity.TYPE_CITIZEN;
            } else if (type.equalsIgnoreCase(TYPE_ENEMY)) {
                this.type = LivingEntity.TYPE_ENEMY;
            } else if (type.equalsIgnoreCase(TYPE_FRIENDLY)) {
                this.type = LivingEntity.TYPE_FRIENDLY;
            } else if (type.equalsIgnoreCase(TYPE_HERO)) {
                this.type = LivingEntity.TYPE_HERO;
            } else if (type.equalsIgnoreCase(TYPE_ALLY)) {
                this.type = LivingEntity.TYPE_ALLY;
            } else {
                throw new Exception(Messages.getString("LivingEntityManagerItem.10") + type + "]"); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }
    }

    public int getType() {
        return type;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public void setLevel(String sLevel) {
        setLevel(Utils.getInteger(sLevel, 0));
    }

    public int getLevel() {
        return level;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setNamePoolTag(String namePoolTag) {
        this.namePoolTag = namePoolTag;
    }

    public String getNamePoolTag() {
        return namePoolTag;
    }

    public void setSurnamePoolTag(String surnamePoolTag) {
        this.surnamePoolTag = surnamePoolTag;
    }

    public String getSurnamePoolTag() {
        return surnamePoolTag;
    }

    public void setFemale(boolean female) {
        this.female = female;
    }

    public void setFemale(String sFemale) {
        setFemale(Boolean.parseBoolean(sFemale));
    }

    public boolean isFemale() {
        return female;
    }

    public String getAttack() {
        return attack;
    }

    public void setAttack(String attack) {
        this.attack = attack;
    }

    public void setAttackSpeed(String attackSpeed) {
        this.attackSpeed = attackSpeed;
    }

    public String getAttackSpeed() {
        return attackSpeed;
    }

    public String getDefense() {
        return defense;
    }

    public void setDefense(String defense) {
        this.defense = defense;
    }

    public String getHealthPoints() {
        return healthPoints;
    }

    public void setHealthPoints(String healthPoints) {
        this.healthPoints = healthPoints;
    }

    public String getAttackVerb() {
        return attackVerb;
    }

    public void setAttackVerb(String attackVerb) {
        this.attackVerb = attackVerb;
    }

    public void setAttackVerbInfinitive(String attackVerbInfinitive) {
        this.attackVerbInfinitive = attackVerbInfinitive;
    }

    public String getAttackVerbInfinitive() {
        return attackVerbInfinitive;
    }

    public String getDamage() {
        return damage;
    }

    public void setDamage(String damage) {
        this.damage = damage;
    }

    public void setLOS(String lOS) {
        LOS = lOS;
    }

    public String getLOS() {
        return LOS;
    }

    public void setDropData(ArrayList<DropData> dropData) {
        this.dropData = dropData;
    }

    public ArrayList<DropData> getDropData() {
        // No hace falta devolver copia porque no se va a modificar
        return dropData;
    }

    public void setMovePCT(String sMovePCT) {
        this.movePCT = sMovePCT;
    }

    public String getMovePCT() {
        return movePCT;
    }

    public void setWalkSpeed(String walkSpeed) {
        this.walkSpeed = walkSpeed;
    }

    public String getWalkSpeed() {
        return walkSpeed;
    }

    public void setGrouping(boolean grouping) {
        this.grouping = grouping;
    }

    public void setGrouping(String sGrouping) {
        setGrouping(Boolean.parseBoolean(sGrouping));
    }

    public boolean hasGrouping() {
        return grouping;
    }

    public void setHPRegeneration(int iHPR) {
        this.HPregeneration = iHPR;
    }

    public void setHPRegeneration(String sHPR) {
        setHPRegeneration(Utils.getInteger(sHPR, 0));
    }

    public int getHPRegeneration() {
        return HPregeneration;
    }

    public String getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(String sMaxAge) {
        this.maxAge = sMaxAge;
    }

    public String getMaxAgeLiving() {
        return maxAgeLiving;
    }

    public void setMaxAgeLiving(String maxAgeLiving) {
        this.maxAgeLiving = maxAgeLiving;
    }

    public void setEquip(ArrayList<String> equip) {
        this.equip = equip;
    }

    public ArrayList<String> getEquip() {
        return equip;
    }

    public void setEquipAllowed(ArrayList<String> equipAllowed) {
        this.equipAllowed = equipAllowed;
    }

    public void setEquipAllowed(String sEquipAllowed) {
        setEquipAllowed(Utils.getArray(sEquipAllowed));
    }

    public ArrayList<String> getEquipAllowed() {
        return equipAllowed;
    }

    public void setHabitat(ArrayList<Integer> habitat) {
        this.habitat = habitat;
    }

    public void setHabitatAsString(ArrayList<String> habitat) throws Exception {
        if (habitat != null) {
            ArrayList<Integer> alHabitat = new ArrayList<Integer>(habitat.size());
            TerrainManagerItem tmi;
            for (int i = 0; i < habitat.size(); i++) {
                tmi = TerrainManager.getItem(habitat.get(i));
                if (tmi == null) {
                    throw new Exception(Messages.getString("LivingEntityManagerItem.9") + habitat.get(i) + "]"); //$NON-NLS-1$ //$NON-NLS-2$
                }
                alHabitat.add(tmi.getTerrainID());
            }
            setHabitat(alHabitat);
        }
    }

    public ArrayList<Integer> getHabitat() {
        return habitat;
    }

    public int getHabitatHeightMin() {
        return habitatHeightMin;
    }

    public void setHabitatHeightMin(String sHabitatHeightMin) {
        setHabitatHeightMin(Utils.getInteger(sHabitatHeightMin, -1));
    }

    public void setHabitatHeightMin(int habitatHeightMin) {
        this.habitatHeightMin = habitatHeightMin;
    }

    public int getHabitatHeightMax() {
        return habitatHeightMax;
    }

    public void setHabitatHeightMax(int habitatHeightMax) {
        this.habitatHeightMax = habitatHeightMax;
    }

    public void setHabitatHeightMax(String sHabitatHeightMax) {
        setHabitatHeightMax(Utils.getInteger(sHabitatHeightMax, -1));
    }

    /**
     * @param hate the hate to set
     */
    public void setHate(String hate) {
        this.hate = hate;
    }

    /**
     * @return the hate
     */
    public String getHate() {
        return hate;
    }

    public void setBuilding(String building) {
        this.building = building;
    }

    public String getBuilding() {
        return building;
    }

    public void setBuildingTime(int buildingTime) {
        this.buildingTime = buildingTime;
    }

    public void setBuildingTime(String sBuildingTime) {
        setBuildingTime(Utils.launchDice(sBuildingTime));
    }

    public int getBuildingTime() {
        return buildingTime;
    }

    public void setAltGraphics(ArrayList<String> altGraphics) {
        this.altGraphics = altGraphics;
    }

    public ArrayList<String> getAltGraphics() {
        return altGraphics;
    }

    public void setActions(ArrayList<String> actions) {
        this.actions = actions;
    }

    public ArrayList<String> getActions() {
        return actions;
    }

    public boolean hasActions() {
        return actions != null && actions.size() > 0;
    }

    public void setFacingDirections(boolean facingDirections) {
//		if (!facingDirections) {
//			System.out.println (getIniHeader ());
//		}
        this.facingDirections = facingDirections;
    }

    public void setFacingDirections(String sFacingDirections) {
        setFacingDirections(Boolean.parseBoolean(sFacingDirections));
    }

    public boolean isFacingDirections() {
        return facingDirections;
    }

    public String getMaxHungryTurns() {
        return maxHungryTurns;
    }

    public void setMaxHungryTurns(String maxHungryTurns) throws Exception {
        // Solo se llama si es citizen o hero
        if (maxHungryTurns == null || maxHungryTurns.trim().length() == 0) {
            throw new Exception(Messages.getString("LivingEntityManagerItem.0")); //$NON-NLS-1$
        }

        if (Utils.launchDice(maxHungryTurns) == 0) {
            throw new Exception(Messages.getString("LivingEntityManagerItem.1") + maxHungryTurns + "]"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        this.maxHungryTurns = maxHungryTurns;
    }

    public String getMaxSleepTurns() {
        return maxSleepTurns;
    }

    public void setMaxSleepTurns(String maxSleepTurns) throws Exception {
        // Solo se llama si es citizen o hero
        if (maxSleepTurns == null || maxSleepTurns.trim().length() == 0) {
            throw new Exception(Messages.getString("LivingEntityManagerItem.3")); //$NON-NLS-1$
        }

        if (Utils.launchDice(maxSleepTurns) == 0) {
            throw new Exception(Messages.getString("LivingEntityManagerItem.5") + maxSleepTurns + "]"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        this.maxSleepTurns = maxSleepTurns;
    }

    public void setHeroComePrerequisite(String heroPrerequisite) {
        this.heroComePrerequisite = heroPrerequisite;
    }

    public String getHeroComePrerequisite() {
        return heroComePrerequisite;
    }

    public void setHeroStayPrerequisite(String heroPrerequisite) {
        this.heroStayPrerequisite = heroPrerequisite;
    }

    public String getHeroStayPrerequisite() {
        return heroStayPrerequisite;
    }

    public void setHeroBehaviour(String heroBehaviour) {
        this.heroBehaviour = heroBehaviour;
    }

    public String getHeroBehaviour() {
        return heroBehaviour;
    }

    public void setHeroSkills(String sHeroSkills) {
        this.heroSkills = sHeroSkills;
    }

    public String getHeroSkills() {
        return heroSkills;
    }

    public void setMoral(String moral) {
        this.moral = moral;
    }

    public String getMoral() {
        return moral;
    }

    public void setEatZone(String eatZone) {
        this.eatZone = eatZone;
    }

    public String getEatZone() {
        return eatZone;
    }

    public void setFollowEntity(String followEntity) {
        ArrayList<String> alFollows = Utils.getArray(followEntity);
        if (alFollows == null) {
            this.followEntity = null;
        } else {
            this.followEntity = UtilsIniHeaders.getIntsArray(alFollows);
        }
    }

    public int[] getFollowEntity() {
        return followEntity;
    }

    public void setFollowTurns(String followTurns) {
        this.followTurns = followTurns;
    }

    public String getFollowTurns() {
        return followTurns;
    }

    public void setEffects(ArrayList<String> effects) throws Exception {
        if (effects != null) {
            for (int i = 0; i < effects.size(); i++) {
                if (EffectManager.getItem(effects.get(i)) == null) {
                    throw new Exception(Messages.getString("LivingEntityManagerItem.2") + effects.get(i) + "]"); //$NON-NLS-1$ //$NON-NLS-2$
                }
            }
        }

        this.effects = effects;
    }

    public void setEffects(String sEffects) throws Exception {
        setEffects(Utils.getArray(sEffects));
    }

    public ArrayList<String> getEffects() {
        return effects;
    }

    public void setEffectsImmune(ArrayList<String> effectsImmune) throws Exception {
        if (effectsImmune != null) {
            for (int i = 0; i < effectsImmune.size(); i++) {
                if (EffectManager.getItem(effectsImmune.get(i)) == null) {
                    throw new Exception(Messages.getString("LivingEntityManagerItem.2") + effectsImmune.get(i) + "]"); //$NON-NLS-1$ //$NON-NLS-2$
                }
            }
        }

        this.effectsImmune = effectsImmune;
    }

    public void setEffectsImmune(String sEffectsImmune) throws Exception {
        setEffectsImmune(Utils.getArray(sEffectsImmune));
    }

    public ArrayList<String> getEffectsImmune() {
        return effectsImmune;
    }

    public void setEvadeTraps(boolean evadeTraps) {
        this.evadeTraps = evadeTraps;
    }

    public void setEvadeTraps(String sEvadeTraps) {
        setEvadeTraps(Boolean.parseBoolean(sEvadeTraps));
    }

    public boolean isEvadeTraps() {
        return evadeTraps;
    }

    public void setCaravan(String sCaravan) throws Exception {
        if (sCaravan != null) {
            if (CaravanManager.getItem(sCaravan) == null) {
                throw new Exception(Messages.getString("LivingEntityManagerItem.8") + sCaravan + "]"); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }

        this.caravan = sCaravan;
    }

    public String getCaravan() {
        return caravan;
    }

    public void setSteal(ArrayList<String> steal) throws Exception {
        if (steal != null) {
            // Pasamos los types a items
            ArrayList<String> alItems = new ArrayList<String>();
            for (int i = 0; i < steal.size(); i++) {
                ArrayList<String> alTypeItems = ItemManager.getItemsByType(steal.get(i));
                if (alTypeItems.size() == 0) {
                    // No es tipo, asumimos item de momento
                    if (!alItems.contains(steal.get(i))) {
                        alItems.add(steal.get(i));
                    }
                } else {
                    // Tipo! Obtenemos todos los items
                    if (alTypeItems != null) {
                        for (int j = 0; j < alTypeItems.size(); j++) {
                            if (!alItems.contains(alTypeItems.get(j))) {
                                alItems.add(alTypeItems.get(j));
                            }
                        }
                    }
                }
            }

            // Llegados aqui tenemos una lista de items, comprobamos que existan
            for (int i = 0; i < alItems.size(); i++) {
                if (ItemManager.getItem(alItems.get(i)) == null) {
                    throw new Exception(Messages.getString("LivingEntityManagerItem.11") + " [" + alItems.get(i) + "]"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                }
            }

            // Creamos el objeto final
            this.steal = new int[alItems.size()];
            for (int i = 0; i < alItems.size(); i++) {
                this.steal[i] = UtilsIniHeaders.getIntIniHeader(alItems.get(i));
            }
        } else {
            this.steal = null;
        }
    }

    public void setSteal(String sSteal) throws Exception {
        setSteal(Utils.getArray(sSteal));
    }

    public int[] getSteal() {
        return steal;
    }

    public void setStealLivings(ArrayList<String> alStealLivings) {
        if (alStealLivings == null) {
            this.stealLivings = null;
        } else {
            this.stealLivings = new int[alStealLivings.size()];
            for (int i = 0; i < alStealLivings.size(); i++) {
                this.stealLivings[i] = UtilsIniHeaders.getIntIniHeader(alStealLivings.get(i));
            }
        }
    }

    public void setStealLivings(String sStealLivings) {
        setStealLivings(Utils.getArray(sStealLivings));
    }

    public int[] getStealLivings() {
        return stealLivings;
    }

    public void setFxDead(String fxDead) throws Exception {
        if (fxDead == null || fxDead.trim().length() == 0) {
            this.fxDead = null;
        } else {
            this.fxDead = fxDead.trim();
            if (!UtilsAL.exists(this.fxDead)) {
                throw new Exception(Messages.getString("LivingEntityManagerItem.12") + " [" + this.fxDead + "]"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            }
        }
    }

    public String getFxDead() {
        return fxDead;
    }

    public void setFoodNeeded(ArrayList<String> alFoodNeeded) {
        if (alFoodNeeded == null) {
            this.foodNeeded = null;
        } else {
            this.foodNeeded = new int[alFoodNeeded.size()];
            for (int i = 0; i < alFoodNeeded.size(); i++) {
                this.foodNeeded[i] = UtilsIniHeaders.getIntIniHeader(alFoodNeeded.get(i));
            }
        }
    }

    public void setFoodNeeded(String foodNeeded) {
        setFoodNeeded(Utils.getArray(foodNeeded));
    }

    public void setFoodNeeded(int[] foodNeeded) {
        this.foodNeeded = foodNeeded;
    }

    public int[] getFoodNeeded() {
        return foodNeeded;
    }

    public void setFoodNeededTurns(String foodNeededTurns) throws Exception {
        if (foodNeededTurns == null || foodNeededTurns.length() == 0) {
            setFoodNeededTurns(0);
        } else {
            try {
                setFoodNeededTurns(Integer.parseInt(foodNeededTurns));
            } catch (NumberFormatException e) {
                throw new Exception(Messages.getString("LivingEntityManagerItem.13") + "[" + foodNeededTurns + "]"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            }
        }
    }

    public void setFoodNeededTurns(int foodNeededTurns) throws Exception {
        this.foodNeededTurns = foodNeededTurns;
        if (foodNeededTurns != 0 && (foodNeeded == null || foodNeeded.length == 0)) {
            throw new Exception(Messages.getString("LivingEntityManagerItem.14")); //$NON-NLS-1$
        }
    }

    public int getFoodNeededTurns() {
        return foodNeededTurns;
    }

    public void setFoodNeededDieTurns(int foodNeededDieTurns) throws Exception {
        this.foodNeededDieTurns = foodNeededDieTurns;

        if (foodNeededDieTurns == 0) {
            if (getFoodNeededTurns() != 0) {
                throw new Exception(Messages.getString("LivingEntityManagerItem.15")); //$NON-NLS-1$
            }
        }
    }

    public void setFoodNeededDieTurns(String sFoodNeededDieTurns) throws Exception {
        if (sFoodNeededDieTurns == null || sFoodNeededDieTurns.length() == 0) {
            setFoodNeededDieTurns(0);
        } else {
            try {
                setFoodNeededDieTurns(Integer.parseInt(sFoodNeededDieTurns));
            } catch (NumberFormatException e) {
                throw new Exception(Messages.getString("LivingEntityManagerItem.13") + "[" + sFoodNeededDieTurns + "]"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            }
        }
    }

    public int getFoodNeededDieTurns() {
        return foodNeededDieTurns;
    }

    public void setAnimatedWhenIdle(boolean animatedWhenIdle) {
        this.animatedWhenIdle = animatedWhenIdle;
    }

    public void setAnimatedWhenIdle(String sAnimatedWhenIdle) {
        setAnimatedWhenIdle(Boolean.parseBoolean(sAnimatedWhenIdle));
    }

    public boolean isAnimatedWhenIdle() {
        return animatedWhenIdle;
    }

    public void setWorkCounterPCT(String sWorkCounterPCT) throws Exception {
        if (type == LivingEntity.TYPE_CITIZEN) {
            Point p = Utils.getDiceMinMax(sWorkCounterPCT);
            if (p.x <= 0 || p.y <= 0) {
                throw new Exception(Messages.getString("LivingEntityManagerItem.16")); //$NON-NLS-1$
            }
        }
        this.workCounterPCT = sWorkCounterPCT;
    }

    public String getWorkCounterPCT() {
        return workCounterPCT;
    }

    public void setIdleCounterPCT(String sIdleCounterPCT) throws Exception {
        if (type == LivingEntity.TYPE_CITIZEN) {
            Point p = Utils.getDiceMinMax(sIdleCounterPCT);
            if (p.x <= 0 || p.y <= 0) {
                throw new Exception(Messages.getString("LivingEntityManagerItem.17")); //$NON-NLS-1$
            }
        }
        this.idleCounterPCT = sIdleCounterPCT;
    }

    public String getIdleCounterPCT() {
        return idleCounterPCT;
    }
}
