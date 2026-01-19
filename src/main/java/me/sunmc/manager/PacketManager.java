package me.sunmc.manager;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetExperience;
import me.sunmc.PlayerProfile;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Manages PacketEvents integration for enhanced profile switching effects.
 *
 * <p>This manager uses PacketEvents to provide smooth visual transitions
 * during profile switches, including experience bar animations and other
 * client-side updates.</p>
 *
 * @author SunMC Development Team
 * @version 1.0.0
 */
public class PacketManager extends PacketListenerAbstract {

    private final PlayerProfile plugin;

    /**
     * Constructs a new PacketManager.
     *
     * @param plugin the plugin instance
     */
    public PacketManager(PlayerProfile plugin) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
    }

    /**
     * Initializes PacketEvents integration.
     */
    public void initialize() {
        if (!isPacketEventsAvailable()) {
            plugin.getLogger().warning("PacketEvents not found - advanced features disabled");
            return;
        }

        PacketEvents.getAPI().getEventManager().registerListener(this);
        plugin.getLogger().info("PacketEvents integration enabled");
    }

    /**
     * Shuts down PacketEvents integration.
     */
    public void shutdown() {
        if (isPacketEventsAvailable()) {
            PacketEvents.getAPI().getEventManager().unregisterListener(this);
        }
    }

    /**
     * Checks if PacketEvents is available.
     *
     * @return true if PacketEvents is loaded
     */
    private boolean isPacketEventsAvailable() {
        try {
            Class.forName("com.github.retrooper.packetevents.PacketEvents");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Sends a smooth experience update to a player.
     *
     * @param player   the player
     * @param level    the new level
     * @param exp      the new exp progress
     * @param totalExp the total experience
     */
    public void sendExperienceUpdate(Player player, int level, float exp, int totalExp) {
        if (!isPacketEventsAvailable()) {
            return;
        }

        WrapperPlayServerSetExperience packet = new WrapperPlayServerSetExperience(
                exp, level, totalExp
        );

        PacketEvents.getAPI().getPlayerManager()
                .sendPacket(player, packet);
    }

//   (NOT IMPLEMENTED YET)
//    /**
//     * Sends a title packet with custom fade times for profile switches.
//     *
//     * @param player   the player
//     * @param title    the title text
//     * @param subtitle the subtitle text
//     * @param fadeIn   fade in time in ticks
//     * @param stay time in ticks
//     * @param fadeOut  fade out time in ticks
//     */
//    public void sendCustomTitle(Player player, String title, String subtitle,
//                                int fadeIn, int stay, int fadeOut) {
//        if (!isPacketEventsAvailable()) {
//            // Fallback to standard Adventure API
//            return;
//        }
//
//        // Note: PacketEvents title sending would go here
//        // For now, we'll use the standard API
//        plugin.runSync(() -> {
//            Component titleComp = Component.text(title);
//            Component subtitleComp = Component.text(subtitle);
//
//            player.showTitle(Title.title(
//                    titleComp,
//                    subtitleComp,
//                    Title.Times.times(
//                            Duration.ofMillis(fadeIn * 50L),
//                            Duration.ofMillis(stay * 50L),
//                            Duration.ofMillis(fadeOut * 50L)
//                    )
//            ));
//        });
//    }

    /**
     * Intercepts experience packets during profile switches.
     * This prevents visual glitches when XP changes rapidly.
     */
    @Override
    public void onPacketSend(@NotNull PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.SET_EXPERIENCE) {
            Player player = event.getPlayer();
            UUID uuid = player.getUniqueId();

            // If player is currently switching, delay XP packet
            if (plugin.getSwitchManager().isSwitching(uuid)) {
                event.setCancelled(true);

                // Resend after switch completes
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (!plugin.getSwitchManager().isSwitching(uuid)) {
                        sendExperienceUpdate(player,
                                player.getLevel(),
                                player.getExp(),
                                player.getTotalExperience());
                    }
                }, 5L);
            }
        }
    }
}