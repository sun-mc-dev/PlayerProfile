package me.sunmc.listener;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import me.sunmc.PlayerProfile;
import me.sunmc.gui.ProfileGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Handles GUI interactions using PacketEvents.
 *
 * <p>This listener intercepts window click and close packets to handle
 * client-side GUI interactions without creating server-side inventories.</p>
 *
 * @author SunMC Development Team
 * @version 1.0.2
 */
public class GUIListener extends PacketListenerAbstract implements Listener {

    private final PlayerProfile plugin;

    /**
     * Constructs a new GUIListener.
     *
     * @param plugin the plugin instance
     */
    public GUIListener(PlayerProfile plugin) {
        super(PacketListenerPriority.HIGH);
        this.plugin = plugin;
    }

    /**
     * Handles incoming packets for GUI interactions.
     *
     * @param event the packet receive event
     */
    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
            handleClickWindow(event);
        } else if (event.getPacketType() == PacketType.Play.Client.CLOSE_WINDOW) {
            handleCloseWindow(event);
        }
    }

    /**
     * Handles window click packets.
     *
     * @param event the packet event
     */
    private void handleClickWindow(PacketReceiveEvent event) {
        WrapperPlayClientClickWindow packet = new WrapperPlayClientClickWindow(event);

        Player player = (Player) event.getPlayer();
        ProfileGUI.GUISession session = plugin.getProfileGUI().getSession(player.getUniqueId());

        if (session == null) {
            return;
        }

        // Cancel the packet to prevent actual inventory interaction
        event.setCancelled(true);

        int windowId = packet.getWindowId();
        int slot = packet.getSlot();

        // Determine if right click (button == 1)
        boolean isRightClick = packet.getButton() == 1;

        // Handle the click on the main thread
        plugin.runSync(() -> {
            plugin.getProfileGUI().handleClick(player, windowId, slot, isRightClick);
        });
    }

    /**
     * Handles window close packets.
     *
     * @param event the packet event
     */
    private void handleCloseWindow(PacketReceiveEvent event) {
        Player player = (Player) event.getPlayer();

        ProfileGUI.GUISession session = plugin.getProfileGUI().getSession(player.getUniqueId());
        if (session != null) {
            plugin.runSync(() -> {
                plugin.getProfileGUI().cleanup(player.getUniqueId());
            });
        }
    }

    /**
     * Cleans up GUI sessions when players quit.
     *
     * @param event the quit event
     */
    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        plugin.getProfileGUI().cleanup(event.getPlayer().getUniqueId());
    }
}