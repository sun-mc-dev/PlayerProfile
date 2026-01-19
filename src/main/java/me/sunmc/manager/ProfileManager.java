package me.sunmc.manager;

import me.sunmc.PlayerProfile;
import me.sunmc.api.event.ProfileCreateEvent;
import me.sunmc.api.event.ProfileDeleteEvent;
import me.sunmc.api.event.ProfileLoadEvent;
import me.sunmc.model.ProfileData;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player profiles, including creation, deletion, loading, and saving.
 *
 * <p>This manager handles all profile-related operations and maintains an in-memory
 * cache of active profiles for performance. All operations are thread-safe and
 * async-optimized.</p>
 *
 * @author SunMC Development Team
 * @version 1.0.0
 */
public class ProfileManager {

    private final PlayerProfile plugin;

    // Cache: UUID -> (ProfileName -> ProfileData)
    private final Map<UUID, Map<String, ProfileData>> profileCache;

    // Current active profile per player
    private final Map<UUID, String> activeProfiles;

    /**
     * Constructs a new ProfileManager.
     *
     * @param plugin the plugin instance
     */
    public ProfileManager(PlayerProfile plugin) {
        this.plugin = plugin;
        this.profileCache = new ConcurrentHashMap<>();
        this.activeProfiles = new ConcurrentHashMap<>();
    }

    /**
     * Loads all profiles for a player asynchronously.
     *
     * @param player the player
     * @return a CompletableFuture that completes when profiles are loaded
     */
    public CompletableFuture<Void> loadPlayerProfiles(Player player) {
        return CompletableFuture.runAsync(() -> {
            UUID uuid = player.getUniqueId();

            Map<String, ProfileData> profiles = plugin.getDataStorage()
                    .loadProfiles(uuid);

            // If no profiles exist, create default
            if (profiles.isEmpty()) {
                String defaultName = plugin.getConfigManager().getDefaultProfileName();
                ProfileData defaultProfile = createProfileFromPlayer(player, defaultName);
                profiles.put(defaultName, defaultProfile);
                plugin.getDataStorage().saveProfile(defaultProfile);
                activeProfiles.put(uuid, defaultName);
            } else {
                // Set first profile as active
                activeProfiles.put(uuid, profiles.keySet().iterator().next());
            }

            profileCache.put(uuid, profiles);

            // Fire event on main thread
            plugin.runSync(() -> {
                ProfileLoadEvent event = new ProfileLoadEvent(player, new ArrayList<>(profiles.keySet()));
                plugin.getServer().getPluginManager().callEvent(event);
            });

        }, plugin.getExecutorService());
    }

    /**
     * Creates a new profile from the player's current state.
     *
     * @param player      the player
     * @param profileName the name for the new profile
     * @return the created ProfileData
     */
    private @NotNull ProfileData createProfileFromPlayer(@NotNull Player player, String profileName) {
        ProfileData data = new ProfileData(player.getUniqueId(), profileName);

        // Save current player state
        data.setInventory(player.getInventory().getStorageContents().clone());
        data.setArmorContents(player.getInventory().getArmorContents().clone());
        data.setOffHand(player.getInventory().getItemInOffHand().clone());
        data.setEnderChest(player.getEnderChest().getStorageContents().clone());

        data.setExperience(player.getTotalExperience());
        data.setLevel(player.getLevel());
        data.setExp(player.getExp());

        data.setHealth(player.getHealth());
        data.setFoodLevel(player.getFoodLevel());
        data.setSaturation(player.getSaturation());

        data.setPotionEffects(new ArrayList<>(player.getActivePotionEffects()));
        data.setGameMode(player.getGameMode());

        return data;
    }

    /**
     * Creates a new empty profile for a player.
     *
     * @param player      the player
     * @param profileName the profile name
     * @return a CompletableFuture with the result (true if successful)
     */
    public CompletableFuture<Boolean> createProfile(@NotNull Player player, String profileName) {
        UUID uuid = player.getUniqueId();

        // Check if profile already exists
        Map<String, ProfileData> profiles = profileCache.get(uuid);
        if (profiles != null && profiles.containsKey(profileName)) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            // Fire event on main thread
            CompletableFuture<Boolean> eventResult = new CompletableFuture<>();
            plugin.runSync(() -> {
                ProfileCreateEvent event = new ProfileCreateEvent(player, profileName);
                plugin.getServer().getPluginManager().callEvent(event);
                eventResult.complete(!event.isCancelled());
            });

            if (!eventResult.join()) {
                return false;
            }

            // Create empty profile
            ProfileData data = new ProfileData(uuid, profileName);

            // Save to database
            plugin.getDataStorage().saveProfile(data);

            // Add to cache
            profileCache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                    .put(profileName, data);

            return true;

        }, plugin.getExecutorService());
    }

    /**
     * Deletes a profile.
     *
     * @param player      the player
     * @param profileName the profile name
     * @return a CompletableFuture with the result (true if successful)
     */
    public CompletableFuture<Boolean> deleteProfile(@NotNull Player player, @NotNull String profileName) {
        UUID uuid = player.getUniqueId();

        // Cannot delete active profile
        if (profileName.equals(activeProfiles.get(uuid))) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            // Fire event on main thread
            CompletableFuture<Boolean> eventResult = new CompletableFuture<>();
            plugin.runSync(() -> {
                ProfileDeleteEvent event = new ProfileDeleteEvent(player, profileName);
                plugin.getServer().getPluginManager().callEvent(event);
                eventResult.complete(!event.isCancelled());
            });

            if (!eventResult.join()) {
                return false;
            }

            // Remove from database
            boolean deleted = plugin.getDataStorage().deleteProfile(uuid, profileName);

            if (deleted) {
                // Remove from cache
                Map<String, ProfileData> profiles = profileCache.get(uuid);
                if (profiles != null) {
                    profiles.remove(profileName);
                }
            }

            return deleted;

        }, plugin.getExecutorService());
    }

    /**
     * Gets all profile names for a player.
     *
     * @param uuid the player's UUID
     * @return list of profile names
     */
    public List<String> getProfileNames(UUID uuid) {
        Map<String, ProfileData> profiles = profileCache.get(uuid);
        return profiles != null ? new ArrayList<>(profiles.keySet()) : new ArrayList<>();
    }

    /**
     * Gets a specific profile.
     *
     * @param uuid        the player's UUID
     * @param profileName the profile name
     * @return the ProfileData or null if not found
     */
    public ProfileData getProfile(UUID uuid, String profileName) {
        Map<String, ProfileData> profiles = profileCache.get(uuid);
        return profiles != null ? profiles.get(profileName) : null;
    }

    /**
     * Gets the currently active profile name.
     *
     * @param uuid the player's UUID
     * @return the active profile name or null
     */
    public String getActiveProfile(UUID uuid) {
        return activeProfiles.get(uuid);
    }

    /**
     * Sets the active profile.
     *
     * @param uuid        the player's UUID
     * @param profileName the profile name
     */
    public void setActiveProfile(UUID uuid, String profileName) {
        activeProfiles.put(uuid, profileName);
    }

    /**
     * Saves the current player state to a profile.
     *
     * @param player      the player
     * @param profileName the profile name
     * @return a CompletableFuture that completes when saved
     */
    public CompletableFuture<Void> saveCurrentState(Player player, String profileName) {
        return CompletableFuture.runAsync(() -> {
            UUID uuid = player.getUniqueId();
            ProfileData data = getProfile(uuid, profileName);

            if (data == null) {
                return;
            }

            // Capture current state on main thread
            CompletableFuture<Void> captureState = new CompletableFuture<>();
            plugin.runSync(() -> {
                data.setInventory(player.getInventory().getStorageContents().clone());
                data.setArmorContents(player.getInventory().getArmorContents().clone());
                data.setOffHand(player.getInventory().getItemInOffHand().clone());
                data.setEnderChest(player.getEnderChest().getStorageContents().clone());

                data.setExperience(player.getTotalExperience());
                data.setLevel(player.getLevel());
                data.setExp(player.getExp());

                data.setHealth(player.getHealth());
                data.setFoodLevel(player.getFoodLevel());
                data.setSaturation(player.getSaturation());

                data.setPotionEffects(new ArrayList<>(player.getActivePotionEffects()));
                data.setGameMode(player.getGameMode());

                data.setLastUsed(System.currentTimeMillis());

                captureState.complete(null);
            });

            captureState.join();

            // Save to database
            plugin.getDataStorage().saveProfile(data);

        }, plugin.getExecutorService());
    }

    /**
     * Applies a profile's data to a player.
     *
     * @param player the player
     * @param data   the profile data
     */
    public void applyProfile(@NotNull Player player, @NotNull ProfileData data) {
        // Must be called on main thread
        player.getInventory().setStorageContents(data.getInventory().clone());
        player.getInventory().setArmorContents(data.getArmorContents().clone());
        player.getInventory().setItemInOffHand(data.getOffHand() != null ?
                data.getOffHand().clone() : null);
        player.getEnderChest().setStorageContents(data.getEnderChest().clone());

        player.setTotalExperience(data.getExperience());
        player.setLevel(data.getLevel());
        player.setExp(data.getExp());

        player.setHealth(Math.min(data.getHealth(), player.getMaxHealth()));
        player.setFoodLevel(data.getFoodLevel());
        player.setSaturation(data.getSaturation());

        // Clear existing effects
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        // Apply new effects
        for (PotionEffect effect : data.getPotionEffects()) {
            player.addPotionEffect(effect);
        }

        // Optional: gamemode and vanish
        if (plugin.getConfigManager().isSwitchGamemode()) {
            player.setGameMode(data.getGameMode());
        }
    }

    /**
     * Saves all cached profiles to database.
     */
    public void saveAll() {
        profileCache.forEach((uuid, profiles) -> {
            profiles.values().forEach(profile -> {
                plugin.getDataStorage().saveProfile(profile);
            });
        });
    }

    /**
     * Unloads a player's profiles from cache.
     *
     * @param uuid the player's UUID
     */
    public void unloadPlayer(UUID uuid) {
        profileCache.remove(uuid);
        activeProfiles.remove(uuid);
    }

    /**
     * Shuts down the manager and saves all data.
     */
    public void shutdown() {
        saveAll();
        profileCache.clear();
        activeProfiles.clear();
    }
}