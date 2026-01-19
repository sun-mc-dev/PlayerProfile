package me.sunmc.manager;

import me.sunmc.PlayerProfile;
import me.sunmc.api.event.ProfileSwitchEvent;
import me.sunmc.model.ProfileData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages profile switching operations, including warmup timers and cancellation logic.
 *
 * <p>This manager handles the complex process of switching profiles, including
 * validation, warmup timers, movement detection, and applying the new profile state.</p>
 *
 * @author SunMC Development Team
 * @version 1.0.0
 */
public class SwitchManager {

    private final PlayerProfile plugin;

    // Active switch requests
    private final Map<UUID, SwitchRequest> activeSwitches;

    // Combat tags
    private final Map<UUID, Long> combatTags;

    /**
     * Constructs a new SwitchManager.
     *
     * @param plugin the plugin instance
     */
    public SwitchManager(PlayerProfile plugin) {
        this.plugin = plugin;
        this.activeSwitches = new ConcurrentHashMap<>();
        this.combatTags = new ConcurrentHashMap<>();

        // Start combat tag cleanup task
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin,
                this::cleanupCombatTags, 20L, 20L);
    }

    /**
     * Initiates a profile switch with warmup.
     *
     * @param player        the player
     * @param targetProfile the target profile name
     * @return a CompletableFuture with the result (true if successful)
     */
    public CompletableFuture<Boolean> initiateSwitch(Player player, String targetProfile) {
        UUID uuid = player.getUniqueId();

        // Check if already switching
        if (activeSwitches.containsKey(uuid)) {
            plugin.sendMessage(player, "You are already switching profiles!", NamedTextColor.RED);
            return CompletableFuture.completedFuture(false);
        }

        // Check combat tag
        if (plugin.getConfigManager().isCancelInCombat() && isInCombat(uuid)) {
            long remaining = getRemainingCombatTime(uuid);
            plugin.sendMessage(player,
                    "You cannot switch profiles while in combat! (" + remaining + "s remaining)",
                    NamedTextColor.RED);
            return CompletableFuture.completedFuture(false);
        }

        // Verify profile exists
        ProfileData targetData = plugin.getProfileManager().getProfile(uuid, targetProfile);
        if (targetData == null) {
            plugin.sendMessage(player, "Profile '" + targetProfile + "' not found!", NamedTextColor.RED);
            return CompletableFuture.completedFuture(false);
        }

        String currentProfile = plugin.getProfileManager().getActiveProfile(uuid);
        if (currentProfile.equals(targetProfile)) {
            plugin.sendMessage(player, "You are already using this profile!", NamedTextColor.RED);
            return CompletableFuture.completedFuture(false);
        }

        // Fire pre-switch event
        ProfileSwitchEvent event = new ProfileSwitchEvent(player, currentProfile, targetProfile, false);
        plugin.getServer().getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            plugin.sendMessage(player, "Profile switch was cancelled!", NamedTextColor.RED);
            return CompletableFuture.completedFuture(false);
        }

        int warmup = plugin.getConfigManager().getSwitchWarmup();

        if (warmup <= 0) {
            // Instant switch
            return performSwitch(player, currentProfile, targetProfile);
        }

        // Create switch request with warmup
        Location startLocation = player.getLocation().clone();
        SwitchRequest request = new SwitchRequest(uuid, currentProfile, targetProfile, startLocation);
        activeSwitches.put(uuid, request);

        plugin.sendMessage(player,
                "Switching to profile '" + targetProfile + "' in " + warmup + " seconds...",
                NamedTextColor.YELLOW);
        plugin.sendMessage(player, "Do not move or take damage!", NamedTextColor.GRAY);

        // Show title
        Component title = Component.text("Switching Profile", NamedTextColor.GOLD);
        Component subtitle = Component.text(warmup + "s", NamedTextColor.YELLOW);
        player.showTitle(Title.title(title, subtitle,
                Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(warmup), Duration.ofMillis(250))));

        // Schedule countdown
        request.startCountdown(warmup);

        return request.getFuture();
    }

    /**
     * Performs the actual profile switch.
     *
     * @param player      the player
     * @param fromProfile the source profile name
     * @param toProfile   the target profile name
     * @return a CompletableFuture with the result
     */
    private CompletableFuture<Boolean> performSwitch(Player player, String fromProfile, String toProfile) {
        UUID uuid = player.getUniqueId();

        return CompletableFuture.supplyAsync(() -> {
            // Save current profile state
            plugin.getProfileManager().saveCurrentState(player, fromProfile).join();

            // Load target profile
            ProfileData targetData = plugin.getProfileManager().getProfile(uuid, toProfile);
            if (targetData == null) {
                plugin.runSync(() -> {
                    plugin.sendMessage(player, "Failed to load profile!", NamedTextColor.RED);
                });
                return false;
            }

            // Apply on main thread
            plugin.runSync(() -> {
                plugin.getProfileManager().applyProfile(player, targetData);
                plugin.getProfileManager().setActiveProfile(uuid, toProfile);

                // Fire post-switch event
                ProfileSwitchEvent postEvent = new ProfileSwitchEvent(player, fromProfile, toProfile, true);
                plugin.getServer().getPluginManager().callEvent(postEvent);

                plugin.sendMessage(player,
                        "Successfully switched to profile '" + toProfile + "'!",
                        NamedTextColor.GREEN);

                // Show title
                Component title = Component.text("Profile Switched", NamedTextColor.GREEN);
                Component subtitle = Component.text(toProfile, NamedTextColor.GOLD);
                player.showTitle(Title.title(title, subtitle));
            });

            return true;

        }, plugin.getExecutorService());
    }

    /**
     * Cancels an active switch request.
     *
     * @param uuid   the player's UUID
     * @param reason the cancellation reason
     */
    public void cancelSwitch(UUID uuid, String reason) {
        SwitchRequest request = activeSwitches.remove(uuid);
        if (request != null) {
            request.cancel();

            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                plugin.sendMessage(player, "Profile switch cancelled: " + reason, NamedTextColor.RED);
                player.clearTitle();
            }
        }
    }

    /**
     * Checks if a player has an active switch request.
     *
     * @param uuid the player's UUID
     * @return true if switching
     */
    public boolean isSwitching(UUID uuid) {
        return activeSwitches.containsKey(uuid);
    }

    /**
     * Gets the start location of an active switch.
     *
     * @param uuid the player's UUID
     * @return the start location or null
     */
    public Location getSwitchStartLocation(UUID uuid) {
        SwitchRequest request = activeSwitches.get(uuid);
        return request != null ? request.getStartLocation() : null;
    }

    /**
     * Tags a player as in combat.
     *
     * @param uuid the player's UUID
     */
    public void tagCombat(UUID uuid) {
        long duration = plugin.getConfigManager().getCombatTagDuration() * 1000L;
        combatTags.put(uuid, System.currentTimeMillis() + duration);
    }

    /**
     * Checks if a player is in combat.
     *
     * @param uuid the player's UUID
     * @return true if in combat
     */
    public boolean isInCombat(UUID uuid) {
        Long expiry = combatTags.get(uuid);
        if (expiry == null) {
            return false;
        }

        if (System.currentTimeMillis() > expiry) {
            combatTags.remove(uuid);
            return false;
        }

        return true;
    }

    /**
     * Gets remaining combat tag time in seconds.
     *
     * @param uuid the player's UUID
     * @return remaining time or 0
     */
    public long getRemainingCombatTime(UUID uuid) {
        Long expiry = combatTags.get(uuid);
        if (expiry == null) {
            return 0;
        }

        long remaining = (expiry - System.currentTimeMillis()) / 1000L;
        return Math.max(0, remaining);
    }

    /**
     * Removes a player's combat tag.
     *
     * @param uuid the player's UUID
     */
    public void removeCombatTag(UUID uuid) {
        combatTags.remove(uuid);
    }

    /**
     * Cleans up expired combat tags.
     */
    private void cleanupCombatTags() {
        long now = System.currentTimeMillis();
        combatTags.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    /**
     * Cancels all active switches.
     */
    public void cancelAllSwitches() {
        activeSwitches.keySet().forEach(uuid -> cancelSwitch(uuid, "plugin disabled"));
    }

    /**
     * Represents an active profile switch request.
     */
    private class SwitchRequest {
        private final UUID playerUUID;
        private final String fromProfile;
        private final String toProfile;
        private final Location startLocation;
        private final CompletableFuture<Boolean> future;
        private BukkitTask task;

        public SwitchRequest(UUID playerUUID, String fromProfile, String toProfile, Location startLocation) {
            this.playerUUID = playerUUID;
            this.fromProfile = fromProfile;
            this.toProfile = toProfile;
            this.startLocation = startLocation;
            this.future = new CompletableFuture<>();
        }

        public void startCountdown(int seconds) {
            final int[] remaining = {seconds};

            this.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                Player player = plugin.getServer().getPlayer(playerUUID);

                if (player == null || !player.isOnline()) {
                    cancel();
                    return;
                }

                remaining[0]--;

                if (remaining[0] <= 0) {
                    task.cancel();
                    activeSwitches.remove(playerUUID);

                    performSwitch(player, fromProfile, toProfile)
                            .thenAccept(future::complete);
                } else {
                    // Update title
                    Component subtitle = Component.text(remaining[0] + "s", NamedTextColor.YELLOW);
                    player.sendActionBar(subtitle);
                }
            }, 20L, 20L);
        }

        public void cancel() {
            if (task != null) {
                task.cancel();
            }
            future.complete(false);
        }

        public Location getStartLocation() {
            return startLocation;
        }

        public CompletableFuture<Boolean> getFuture() {
            return future;
        }
    }
}