package me.sunmc.listeners;

import me.sunmc.PlayerProfile;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Handles player join and quit events for profile management.
 *
 * @author SunMC Development Team
 * @version 1.0.0
 */
public class PlayerListener implements Listener {

    private final PlayerProfile plugin;

    public PlayerListener(PlayerProfile plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads player profiles when they join.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();

        plugin.getProfileManager().loadPlayerProfiles(player)
                .thenRun(() -> {
                    String activeProfile = plugin.getProfileManager().getActiveProfile(player.getUniqueId());
                    plugin.getLogger().info("Loaded profiles for " + player.getName() +
                            " (active: " + activeProfile + ")");
                });
    }

    /**
     * Saves and unloads player profiles when they quit.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Cancel any pending switch
        plugin.getSwitchManager().cancelSwitch(uuid, "player quit");

        // Remove combat tag
        plugin.getSwitchManager().removeCombatTag(uuid);

        // Save current state
        String activeProfile = plugin.getProfileManager().getActiveProfile(uuid);
        if (activeProfile != null) {
            plugin.getProfileManager().saveCurrentState(player, activeProfile)
                    .thenRun(() -> {
                        // Unload from cache
                        plugin.getProfileManager().unloadPlayer(uuid);
                        plugin.getLogger().info("Saved and unloaded profiles for " + player.getName());
                    });
        }
    }
}