package me.sunmc.api;

import me.sunmc.PlayerProfile;
import me.sunmc.model.ProfileData;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public API for interacting with the PlayerProfile plugin.
 *
 * <p>This API provides a stable interface for external plugins to interact with
 * player profiles, including querying, creating, deleting, and switching profiles.</p>
 *
 * <p><strong>Example Usage:</strong></p>
 * <pre>{@code
 * ProfileAPI api = ProfileAPI.getInstance();
 *
 * // Get a player's profiles
 * List<String> profiles = api.getProfiles(player.getUniqueId());
 *
 * // Switch profiles
 * api.switchProfile(player, "admin").thenAccept(success -> {
 *     if (success) {
 *         player.sendMessage("Switched to admin profile!");
 *     }
 * });
 *
 * // Create a new profile
 * api.createProfile(player, "builder").thenAccept(success -> {
 *     if (success) {
 *         player.sendMessage("Builder profile created!");
 *     }
 * });
 * }</pre>
 *
 * @author SunMC Development Team
 * @version 1.0.0
 */
public class ProfileAPI {

    private static ProfileAPI instance;
    private final PlayerProfile plugin;

    private ProfileAPI(PlayerProfile plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes the API instance.
     *
     * @param plugin the plugin instance
     */
    public static void initialize(PlayerProfile plugin) {
        instance = new ProfileAPI(plugin);
    }

    /**
     * Gets the API instance.
     *
     * @return the ProfileAPI instance
     * @throws IllegalStateException if API not initialized
     */
    public static ProfileAPI getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ProfileAPI not initialized!");
        }
        return instance;
    }

    /**
     * Gets all profile names for a player.
     *
     * @param playerUUID the player's UUID
     * @return list of profile names
     */
    public List<String> getProfiles(UUID playerUUID) {
        return plugin.getProfileManager().getProfileNames(playerUUID);
    }

    /**
     * Gets the currently active profile for a player.
     *
     * @param playerUUID the player's UUID
     * @return the active profile name or null if not found
     */
    public String getActiveProfile(UUID playerUUID) {
        return plugin.getProfileManager().getActiveProfile(playerUUID);
    }

    /**
     * Gets detailed data for a specific profile.
     *
     * @param playerUUID  the player's UUID
     * @param profileName the profile name
     * @return the ProfileData or null if not found
     */
    public ProfileData getProfileData(UUID playerUUID, String profileName) {
        return plugin.getProfileManager().getProfile(playerUUID, profileName);
    }

    /**
     * Checks if a profile exists.
     *
     * @param playerUUID  the player's UUID
     * @param profileName the profile name
     * @return true if the profile exists
     */
    public boolean profileExists(UUID playerUUID, String profileName) {
        return plugin.getProfileManager().getProfile(playerUUID, profileName) != null;
    }

    /**
     * Creates a new profile for a player.
     *
     * @param player      the player
     * @param profileName the name for the new profile
     * @return a CompletableFuture that completes with true if successful
     */
    public CompletableFuture<Boolean> createProfile(Player player, String profileName) {
        return plugin.getProfileManager().createProfile(player, profileName);
    }

    /**
     * Deletes a profile.
     *
     * @param player      the player
     * @param profileName the profile name
     * @return a CompletableFuture that completes with true if successful
     */
    public CompletableFuture<Boolean> deleteProfile(Player player, String profileName) {
        return plugin.getProfileManager().deleteProfile(player, profileName);
    }

    /**
     * Switches a player to a different profile.
     *
     * <p>This method respects all configured restrictions such as combat tags,
     * warmup timers, and movement cancellation.</p>
     *
     * @param player      the player
     * @param profileName the target profile name
     * @return a CompletableFuture that completes with true if successful
     */
    public CompletableFuture<Boolean> switchProfile(Player player, String profileName) {
        return plugin.getSwitchManager().initiateSwitch(player, profileName);
    }

    /**
     * Checks if a player is currently switching profiles.
     *
     * @param playerUUID the player's UUID
     * @return true if the player is switching
     */
    public boolean isSwitching(UUID playerUUID) {
        return plugin.getSwitchManager().isSwitching(playerUUID);
    }

    /**
     * Checks if a player is in combat.
     *
     * @param playerUUID the player's UUID
     * @return true if in combat
     */
    public boolean isInCombat(UUID playerUUID) {
        return plugin.getSwitchManager().isInCombat(playerUUID);
    }

    /**
     * Gets the remaining combat tag time for a player.
     *
     * @param playerUUID the player's UUID
     * @return remaining time in seconds, or 0 if not in combat
     */
    public long getRemainingCombatTime(UUID playerUUID) {
        return plugin.getSwitchManager().getRemainingCombatTime(playerUUID);
    }

    /**
     * Tags a player as in combat.
     *
     * <p>This can be used by external plugins to integrate with the
     * profile switching system.</p>
     *
     * @param playerUUID the player's UUID
     */
    public void tagCombat(UUID playerUUID) {
        plugin.getSwitchManager().tagCombat(playerUUID);
    }

    /**
     * Removes a player's combat tag.
     *
     * @param playerUUID the player's UUID
     */
    public void removeCombatTag(UUID playerUUID) {
        plugin.getSwitchManager().removeCombatTag(playerUUID);
    }

    /**
     * Saves the current player state to a profile.
     *
     * <p>This can be useful for forcing a save before a critical operation.</p>
     *
     * @param player      the player
     * @param profileName the profile name
     * @return a CompletableFuture that completes when saved
     */
    public CompletableFuture<Void> saveProfile(Player player, String profileName) {
        return plugin.getProfileManager().saveCurrentState(player, profileName);
    }

    /**
     * Forces a profile switch without warmup or restrictions.
     *
     * <p><strong>Warning:</strong> This bypasses all safety checks and should
     * only be used in specific scenarios where instant switching is required.</p>
     *
     * @param player      the player
     * @param profileName the target profile name
     * @return true if successful
     */
    public boolean forceSwitch(@NotNull Player player, String profileName) {
        UUID uuid = player.getUniqueId();
        String currentProfile = plugin.getProfileManager().getActiveProfile(uuid);

        if (currentProfile == null || currentProfile.equals(profileName)) {
            return false;
        }

        ProfileData targetData = plugin.getProfileManager().getProfile(uuid, profileName);
        if (targetData == null) {
            return false;
        }

        // Save current
        plugin.getProfileManager().saveCurrentState(player, currentProfile).join();

        // Apply new
        plugin.getProfileManager().applyProfile(player, targetData);
        plugin.getProfileManager().setActiveProfile(uuid, profileName);

        return true;
    }
}