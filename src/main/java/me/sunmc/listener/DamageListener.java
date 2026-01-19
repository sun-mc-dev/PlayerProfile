package me.sunmc.listener;

import me.sunmc.PlayerProfile;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Cancels profile switches when players take damage.
 *
 * @author SunMC Development Team
 * @version 1.0.0
 */
public class DamageListener implements Listener {

    private final PlayerProfile plugin;

    public DamageListener(PlayerProfile plugin) {
        this.plugin = plugin;
    }

    /**
     * Cancels switch if player takes damage during warmup.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!plugin.getConfigManager().isCancelOnDamage()) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (plugin.getSwitchManager().isSwitching(player.getUniqueId())) {
            plugin.getSwitchManager().cancelSwitch(player.getUniqueId(), "you took damage");
        }
    }
}