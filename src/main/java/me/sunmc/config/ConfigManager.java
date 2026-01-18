package me.sunmc.config;

import me.sunmc.PlayerProfile;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Manages plugin configuration and provides type-safe access to config values.
 *
 * <p>This class handles loading, validation, and retrieval of all configuration
 * options for the PlayerProfile plugin. All configuration values are cached
 * for performance.</p>
 *
 * @author SunMC Development Team
 * @version 1.0.0
 */
public class ConfigManager {

    private final PlayerProfile plugin;
    private FileConfiguration config;
    private int switchWarmup;
    private boolean cancelOnMove;
    private boolean cancelOnDamage;
    private boolean cancelInCombat;
    private int combatTagDuration;
    private boolean switchGamemode;
    private boolean switchVanish;
    private String defaultProfileName;
    private int maxProfileNameLength;

    /**
     * Constructs a new ConfigManager.
     *
     * @param plugin the plugin instance
     */
    public ConfigManager(PlayerProfile plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads and validates the configuration file.
     * Creates default config if it doesn't exist.
     */
    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        this.switchWarmup = config.getInt("switch.warmup-seconds", 5);
        this.cancelOnMove = config.getBoolean("switch.cancel-on-move", true);
        this.cancelOnDamage = config.getBoolean("switch.cancel-on-damage", true);
        this.cancelInCombat = config.getBoolean("switch.cancel-in-combat", true);
        this.combatTagDuration = config.getInt("switch.combat-tag-duration", 10);
        this.switchGamemode = config.getBoolean("switch.change-gamemode", false);
        this.switchVanish = config.getBoolean("switch.change-vanish", false);
        this.defaultProfileName = config.getString("profiles.default-name", "default");
        this.maxProfileNameLength = config.getInt("profiles.max-name-length", 16);

        plugin.getLogger().info("Configuration loaded successfully");
    }

    /**
     * Reloads the configuration from disk.
     */
    public void reloadConfig() {
        loadConfig();
    }

    /**
     * Gets the warmup time in seconds before a profile switch.
     *
     * @return warmup time in seconds
     */
    public int getSwitchWarmup() {
        return switchWarmup;
    }

    /**
     * Checks if profile switches should be cancelled when the player moves.
     *
     * @return true if movement cancels switches
     */
    public boolean isCancelOnMove() {
        return cancelOnMove;
    }

    /**
     * Checks if profile switches should be cancelled when the player takes damage.
     *
     * @return true if damage cancels switches
     */
    public boolean isCancelOnDamage() {
        return cancelOnDamage;
    }

    /**
     * Checks if profile switches should be blocked while in combat.
     *
     * @return true if combat blocks switches
     */
    public boolean isCancelInCombat() {
        return cancelInCombat;
    }

    /**
     * Gets the duration in seconds that a player is considered in combat.
     *
     * @return combat tag duration in seconds
     */
    public int getCombatTagDuration() {
        return combatTagDuration;
    }

    /**
     * Checks if gamemode should be switched with profiles.
     *
     * @return true if gamemode should be switched
     */
    public boolean isSwitchGamemode() {
        return switchGamemode;
    }

    /**
     * Checks if vanish state should be switched with profiles.
     *
     * @return true if vanish should be switched
     */
    public boolean isSwitchVanish() {
        return switchVanish;
    }

    /**
     * Gets the default profile name for new players.
     *
     * @return the default profile name
     */
    public String getDefaultProfileName() {
        return defaultProfileName;
    }

    /**
     * Gets the maximum allowed length for profile names.
     *
     * @return maximum profile name length
     */
    public int getMaxProfileNameLength() {
        return maxProfileNameLength;
    }

    /**
     * Gets a message from the configuration.
     *
     * @param path         the message path
     * @param defaultValue the default value if not found
     * @return the configured message
     */
    public String getMessage(String path, String defaultValue) {
        return config.getString("messages." + path, defaultValue);
    }
}