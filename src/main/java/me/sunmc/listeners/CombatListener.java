package me.sunmc.listeners;

import me.sunmc.PlayerProfile;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Handles combat tagging for profile switch restrictions.
 *
 * @author SunMC Development Team
 * @version 1.0.0
 */
public class CombatListener implements Listener {

    private final PlayerProfile plugin;

    public CombatListener(PlayerProfile plugin) {
        this.plugin = plugin;
    }

    /**
     * Tags players when they damage or are damaged by other players.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamagePlayer(EntityDamageByEntityEvent event) {
        if (!plugin.getConfigManager().isCancelInCombat()) {
            return;
        }

        if (event.getEntity() instanceof Player victim &&
                event.getDamager() instanceof Player attacker) {

            // Tag both players
            plugin.getSwitchManager().tagCombat(victim.getUniqueId());
            plugin.getSwitchManager().tagCombat(attacker.getUniqueId());
        }
    }
}