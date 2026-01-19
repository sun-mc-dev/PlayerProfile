package me.sunmc.listener;

import me.sunmc.PlayerProfile;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Cancels profile switches when players move.
 *
 * @author SunMC Development Team
 * @version 1.0.0
 */
public class MovementListener implements Listener {

    private final PlayerProfile plugin;

    public MovementListener(PlayerProfile plugin) {
        this.plugin = plugin;
    }

    /**
     * Cancels switch if player moves during warmup.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!plugin.getConfigManager().isCancelOnMove()) {
            return;
        }

        Player player = event.getPlayer();

        if (!plugin.getSwitchManager().isSwitching(player.getUniqueId())) {
            return;
        }

        // Check if player actually moved (not just head rotation)
        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null) {
            return;
        }

        if (from.getBlockX() != to.getBlockX() ||
                from.getBlockY() != to.getBlockY() ||
                from.getBlockZ() != to.getBlockZ()) {

            plugin.getSwitchManager().cancelSwitch(player.getUniqueId(), "you moved");
        }
    }
}