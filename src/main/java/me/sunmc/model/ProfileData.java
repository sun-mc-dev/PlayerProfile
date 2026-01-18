package me.sunmc.model;

import org.bukkit.GameMode;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

/**
 * Represents a complete player profile with all associated data.
 *
 * <p>This class stores all persistent data for a single profile, including
 * inventory, stats, permissions context, and other player-specific information.</p>
 *
 * @author SunMC Development Team
 * @version 1.0.0
 */
public class ProfileData {

    private final UUID playerUUID;
    private final String profileName;
    private ItemStack[] inventory;
    private ItemStack[] armorContents;
    private ItemStack offHand;
    private ItemStack[] enderChest;
    private int experience;
    private int level;
    private float exp;
    private double health;
    private int foodLevel;
    private float saturation;
    private Collection<PotionEffect> potionEffects;
    private GameMode gameMode;
    private boolean isVanished;
    private long lastUsed;
    private long createdAt;

    /**
     * Constructs a new ProfileData instance.
     *
     * @param playerUUID  the UUID of the player who owns this profile
     * @param profileName the name of this profile
     */
    public ProfileData(UUID playerUUID, String profileName) {
        this.playerUUID = playerUUID;
        this.profileName = profileName;
        this.inventory = new ItemStack[36];
        this.armorContents = new ItemStack[4];
        this.enderChest = new ItemStack[27];
        this.potionEffects = new ArrayList<>();
        this.gameMode = GameMode.SURVIVAL;
        this.health = 20.0;
        this.foodLevel = 20;
        this.saturation = 5.0f;
        this.isVanished = false;

        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.lastUsed = now;
    }

    /**
     * Gets the UUID of the player who owns this profile.
     *
     * @return the player's UUID
     */
    public UUID getPlayerUUID() {
        return playerUUID;
    }

    /**
     * Gets the name of this profile.
     *
     * @return the profile name
     */
    public String getProfileName() {
        return profileName;
    }

    /**
     * Gets the inventory contents.
     *
     * @return the inventory array
     */
    public ItemStack[] getInventory() {
        return inventory;
    }

    /**
     * Sets the inventory contents.
     *
     * @param inventory the new inventory array
     */
    public void setInventory(ItemStack[] inventory) {
        this.inventory = inventory;
    }

    /**
     * Gets the armor contents.
     *
     * @return the armor array
     */
    public ItemStack[] getArmorContents() {
        return armorContents;
    }

    /**
     * Sets the armor contents.
     *
     * @param armorContents the new armor array
     */
    public void setArmorContents(ItemStack[] armorContents) {
        this.armorContents = armorContents;
    }

    /**
     * Gets the off-hand item.
     *
     * @return the off-hand item
     */
    public ItemStack getOffHand() {
        return offHand;
    }

    /**
     * Sets the off-hand item.
     *
     * @param offHand the new off-hand item
     */
    public void setOffHand(ItemStack offHand) {
        this.offHand = offHand;
    }

    /**
     * Gets the ender chest contents.
     *
     * @return the ender chest array
     */
    public ItemStack[] getEnderChest() {
        return enderChest;
    }

    /**
     * Sets the ender chest contents.
     *
     * @param enderChest the new ender chest array
     */
    public void setEnderChest(ItemStack[] enderChest) {
        this.enderChest = enderChest;
    }

    /**
     * Gets the total experience points.
     *
     * @return the experience points
     */
    public int getExperience() {
        return experience;
    }

    /**
     * Sets the total experience points.
     *
     * @param experience the new experience value
     */
    public void setExperience(int experience) {
        this.experience = experience;
    }

    /**
     * Gets the experience level.
     *
     * @return the level
     */
    public int getLevel() {
        return level;
    }

    /**
     * Sets the experience level.
     *
     * @param level the new level
     */
    public void setLevel(int level) {
        this.level = level;
    }

    /**
     * Gets the experience progress to next level.
     *
     * @return the exp progress (0.0-1.0)
     */
    public float getExp() {
        return exp;
    }

    /**
     * Sets the experience progress to next level.
     *
     * @param exp the new exp progress
     */
    public void setExp(float exp) {
        this.exp = exp;
    }

    /**
     * Gets the health value.
     *
     * @return the health
     */
    public double getHealth() {
        return health;
    }

    /**
     * Sets the health value.
     *
     * @param health the new health value
     */
    public void setHealth(double health) {
        this.health = health;
    }

    /**
     * Gets the food level.
     *
     * @return the food level
     */
    public int getFoodLevel() {
        return foodLevel;
    }

    /**
     * Sets the food level.
     *
     * @param foodLevel the new food level
     */
    public void setFoodLevel(int foodLevel) {
        this.foodLevel = foodLevel;
    }

    /**
     * Gets the saturation level.
     *
     * @return the saturation
     */
    public float getSaturation() {
        return saturation;
    }

    /**
     * Sets the saturation level.
     *
     * @param saturation the new saturation
     */
    public void setSaturation(float saturation) {
        this.saturation = saturation;
    }

    /**
     * Gets all active potion effects.
     *
     * @return the potion effects collection
     */
    public Collection<PotionEffect> getPotionEffects() {
        return potionEffects;
    }

    /**
     * Sets the potion effects.
     *
     * @param potionEffects the new potion effects
     */
    public void setPotionEffects(Collection<PotionEffect> potionEffects) {
        this.potionEffects = potionEffects;
    }

    /**
     * Gets the game mode.
     *
     * @return the game mode
     */
    public GameMode getGameMode() {
        return gameMode;
    }

    /**
     * Sets the game mode.
     *
     * @param gameMode the new game mode
     */
    public void setGameMode(GameMode gameMode) {
        this.gameMode = gameMode;
    }

    /**
     * Checks if the player is vanished in this profile.
     *
     * @return true if vanished
     */
    public boolean isVanished() {
        return isVanished;
    }

    /**
     * Sets the vanish state.
     *
     * @param vanished the new vanish state
     */
    public void setVanished(boolean vanished) {
        isVanished = vanished;
    }

    /**
     * Gets the timestamp when this profile was last used.
     *
     * @return the last used timestamp
     */
    public long getLastUsed() {
        return lastUsed;
    }

    /**
     * Sets the last used timestamp.
     *
     * @param lastUsed the new timestamp
     */
    public void setLastUsed(long lastUsed) {
        this.lastUsed = lastUsed;
    }

    /**
     * Gets the timestamp when this profile was created.
     *
     * @return the creation timestamp
     */
    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the creation timestamp.
     *
     * @param createdAt the new timestamp
     */
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}