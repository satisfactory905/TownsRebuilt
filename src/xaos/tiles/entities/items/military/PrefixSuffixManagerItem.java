package xaos.tiles.entities.items.military;

import xaos.utils.Messages;
import xaos.utils.Utils;

public class PrefixSuffixManagerItem {

    private String id; // Solo se usa al cargar mods y lost <delete>
    private String type;
    private String name;
    private String attack;
    private String attackSpeed;
    private String defense;
    private String healthPoints; // MAX
    private String damage;
    private String LOS;
    private String movePCT;
    private String walkSpeed;

    public PrefixSuffixData getRandom() {
        PrefixSuffixData psd = new PrefixSuffixData();
        psd.setName(getName());
        psd.setAttack(Utils.launchDice(getAttack()));
        psd.setAttackSpeed(Utils.launchDice(getAttackSpeed()));
        psd.setDefense(Utils.launchDice(getDefense()));
        psd.setHealthPoints(Utils.launchDice(getHealthPoints()));
        psd.setDamage(Utils.launchDice(getDamage()));
        psd.setLOS(Utils.launchDice(getLOS()));
        psd.setMovePCT(Utils.launchDice(getMovePCT()));
        psd.setWalkSpeed(Utils.launchDice(getWalkSpeed()));

        return psd;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setType(String type) throws Exception {
        if (!type.equalsIgnoreCase(PrefixSuffixData.TYPE_PREFIX) && !type.equalsIgnoreCase(PrefixSuffixData.TYPE_SUFFIX)) {
            throw new Exception(Messages.getString("PrefixSuffixData.2") + type + "]"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public void setWalkSpeed(String walkSpeed) {
        this.walkSpeed = walkSpeed;
    }

    public String getWalkSpeed() {
        return walkSpeed;
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

    public void setMovePCT(String movePCT) {
        this.movePCT = movePCT;
    }

    public String getMovePCT() {
        return movePCT;
    }
}
