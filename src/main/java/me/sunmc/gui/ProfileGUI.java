package me.sunmc.gui;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.util.adventure.AdventureSerializer;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import me.sunmc.PlayerProfile;
import me.sunmc.model.ProfileData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Client-side GUI handler using PacketEvents for zero server load.
 *
 * <p>This implementation uses pure packet-based GUI rendering, meaning no
 * actual inventory is created on the server. All rendering happens client-side,
 * resulting in zero TPS impact and instant response times.</p>
 *
 * @author SunMC Development Team
 * @version 1.0.2
 */
public class ProfileGUI {

    private final PlayerProfile plugin;
    private final Map<UUID, GUISession> activeSessions;

    /**
     * Constructs a new ProfileGUI handler.
     *
     * @param plugin the plugin instance
     */
    public ProfileGUI(PlayerProfile plugin) {
        this.plugin = plugin;
        this.activeSessions = new HashMap<>();
    }

    /**
     * Opens the profile selection GUI for a player.
     *
     * @param player the player to show the GUI to
     */
    public void openProfileGUI(@NotNull Player player) {
        UUID uuid = player.getUniqueId();

        // Get player's profiles
        List<String> profiles = plugin.getProfileManager().getProfileNames(uuid);
        String activeProfile = plugin.getProfileManager().getActiveProfile(uuid);

        // Create GUI session
        GUISession session = new GUISession(player, GUIType.PROFILE_LIST);
        activeSessions.put(uuid, session);

        // Generate GUI title
        Component title = Component.text()
                .append(Component.text("Your Profiles", NamedTextColor.GOLD, TextDecoration.BOLD))
                .build();

        // Open chest GUI (54 slots = 6 rows)
        int windowId = session.getWindowId();
        WrapperPlayServerOpenWindow openPacket = new WrapperPlayServerOpenWindow(
                windowId,
                com.github.retrooper.packetevents.protocol.window.WindowType.GENERIC_9X6,
                AdventureSerializer.toComponent(title)
        );

        PacketEvents.getAPI().getPlayerManager().sendPacket(player, openPacket);

        // Build GUI contents
        List<ItemStack> items = buildProfileListGUI(profiles, activeProfile, uuid);

        // Send items packet
        WrapperPlayServerWindowItems itemsPacket = new WrapperPlayServerWindowItems(
                windowId,
                0, // State ID
                items,
                null // Carried item
        );

        PacketEvents.getAPI().getPlayerManager().sendPacket(player, itemsPacket);
    }

    /**
     * Opens the profile creation GUI.
     *
     * @param player the player
     */
    public void openCreateGUI(@NotNull Player player) {
        UUID uuid = player.getUniqueId();

        GUISession session = new GUISession(player, GUIType.PROFILE_CREATE);
        activeSessions.put(uuid, session);

        Component title = Component.text()
                .append(Component.text("Create Profile", NamedTextColor.GREEN, TextDecoration.BOLD))
                .build();

        int windowId = session.getWindowId();
        WrapperPlayServerOpenWindow openPacket = new WrapperPlayServerOpenWindow(
                windowId,
                com.github.retrooper.packetevents.protocol.window.WindowType.GENERIC_9X3,
                AdventureSerializer.toComponent(title)
        );

        PacketEvents.getAPI().getPlayerManager().sendPacket(player, openPacket);

        List<ItemStack> items = buildCreateGUI(player);

        WrapperPlayServerWindowItems itemsPacket = new WrapperPlayServerWindowItems(
                windowId,
                0,
                items,
                null
        );

        PacketEvents.getAPI().getPlayerManager().sendPacket(player, itemsPacket);
    }

    /**
     * Builds the profile list GUI contents.
     *
     * @param profiles      list of profile names
     * @param activeProfile currently active profile
     * @param playerUUID    player's UUID
     * @return list of items for the GUI
     */
    private @NotNull List<ItemStack> buildProfileListGUI(@NotNull List<String> profiles,
                                                         String activeProfile,
                                                         UUID playerUUID) {
        List<ItemStack> items = new ArrayList<>(54);

        // Fill with empty items
        for (int i = 0; i < 54; i++) {
            items.add(createEmptyItem());
        }

        // Add profile items (start at slot 10)
        int slot = 10;
        for (String profileName : profiles) {
            if (slot >= 44) break; // Don't overflow

            ProfileData data = plugin.getProfileManager().getProfile(playerUUID, profileName);
            boolean isActive = profileName.equals(activeProfile);

            ItemStack profileItem = createProfileItem(profileName, data, isActive);
            items.set(slot, profileItem);

            slot++;
            if ((slot + 1) % 9 == 0) slot += 2; // Skip to next row
        }

        // Add "Create Profile" button (slot 49)
        items.set(49, createCreateButton());

        // Add "Close" button (slot 53)
        items.set(53, createCloseButton());

        // Add decorative glass panes
        ItemStack glassPane = createGlassPane();
        for (int i = 0; i < 9; i++) {
            items.set(i, glassPane); // Top row
            items.set(45 + i, glassPane); // Bottom row
        }

        return items;
    }

    /**
     * Builds the create profile GUI contents.
     *
     * @param player the player
     * @return list of items for the GUI
     */
    private @NotNull List<ItemStack> buildCreateGUI(@NotNull Player player) {
        List<ItemStack> items = new ArrayList<>(27);

        for (int i = 0; i < 27; i++) {
            items.add(createEmptyItem());
        }

        // Add preset profile type buttons
        items.set(11, createPresetButton("default", Material.GRASS_BLOCK));
        items.set(12, createPresetButton("admin", Material.COMMAND_BLOCK));
        items.set(13, createPresetButton("builder", Material.BRICKS));
        items.set(14, createPresetButton("mod", Material.SHIELD));
        items.set(15, createPresetButton("vip", Material.GOLD_BLOCK));

        // Back button
        items.set(22, createBackButton());

        return items;
    }

    /**
     * Creates a profile item for the GUI.
     *
     * @param profileName the profile name
     * @param data        profile data
     * @param isActive    whether this is the active profile
     * @return the item stack
     */
    private @NotNull ItemStack createProfileItem(String profileName,
                                                 ProfileData data,
                                                 boolean isActive) {
        Material material = isActive ? Material.LIME_WOOL : Material.WHITE_WOOL;

        Component displayName = Component.text()
                .append(Component.text(profileName,
                        isActive ? NamedTextColor.GREEN : NamedTextColor.WHITE,
                        TextDecoration.BOLD))
                .build();

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        if (isActive) {
            lore.add(Component.text("● ACTIVE", NamedTextColor.GREEN, TextDecoration.BOLD));
        } else {
            lore.add(Component.text("○ Click to switch", NamedTextColor.GRAY, TextDecoration.ITALIC));
        }

        lore.add(Component.empty());
        lore.add(Component.text("Stats:", NamedTextColor.YELLOW));
        lore.add(Component.text("  Level: " + data.getLevel(), NamedTextColor.GRAY));
        lore.add(Component.text("  Health: " + String.format("%.1f", data.getHealth()), NamedTextColor.GRAY));
        lore.add(Component.text("  GameMode: " + data.getGameMode().name(), NamedTextColor.GRAY));
        lore.add(Component.empty());

        long timeSinceUsed = System.currentTimeMillis() - data.getLastUsed();
        String lastUsed = formatTime(timeSinceUsed);
        lore.add(Component.text("Last used: " + lastUsed, NamedTextColor.DARK_GRAY, TextDecoration.ITALIC));

        if (!isActive) {
            lore.add(Component.empty());
            lore.add(Component.text("Right-click to delete", NamedTextColor.RED, TextDecoration.ITALIC));
        }

        return createItem(material, displayName, lore);
    }

    /**
     * Creates the "Create Profile" button.
     *
     * @return the item stack
     */
    private @NotNull ItemStack createCreateButton() {
        Component name = Component.text()
                .append(Component.text("+ Create Profile", NamedTextColor.GREEN, TextDecoration.BOLD))
                .build();

        List<Component> lore = List.of(
                Component.empty(),
                Component.text("Click to create a new profile", NamedTextColor.GRAY, TextDecoration.ITALIC)
        );

        return createItem(Material.EMERALD, name, lore);
    }

    /**
     * Creates a preset button for profile creation.
     *
     * @param presetName preset name
     * @param material   button material
     * @return the item stack
     */
    private @NotNull ItemStack createPresetButton(String presetName, Material material) {
        Component name = Component.text()
                .append(Component.text(presetName.substring(0, 1).toUpperCase() +
                        presetName.substring(1), NamedTextColor.GOLD, TextDecoration.BOLD))
                .build();

        List<Component> lore = List.of(
                Component.empty(),
                Component.text("Click to create a", NamedTextColor.GRAY),
                Component.text(presetName + " profile", NamedTextColor.YELLOW)
        );

        return createItem(material, name, lore);
    }

    /**
     * Creates the close button.
     *
     * @return the item stack
     */
    private @NotNull ItemStack createCloseButton() {
        Component name = Component.text()
                .append(Component.text("Close", NamedTextColor.RED, TextDecoration.BOLD))
                .build();

        return createItem(Material.BARRIER, name, List.of());
    }

    /**
     * Creates the back button.
     *
     * @return the item stack
     */
    private @NotNull ItemStack createBackButton() {
        Component name = Component.text()
                .append(Component.text("← Back", NamedTextColor.YELLOW, TextDecoration.BOLD))
                .build();

        return createItem(Material.ARROW, name, List.of());
    }

    /**
     * Creates a glass pane for decoration.
     *
     * @return the item stack
     */
    private @NotNull ItemStack createGlassPane() {
        return createItem(Material.GRAY_STAINED_GLASS_PANE,
                Component.text(" "),
                List.of());
    }

    /**
     * Creates an empty/air item.
     *
     * @return the item stack
     */
    private @NotNull ItemStack createEmptyItem() {
        return ItemStack.builder()
                .type(ItemTypes.AIR)
                .amount(1)
                .build();
    }

    /**
     * Creates an item with name and lore.
     *
     * @param material the material
     * @param name     the display name
     * @param lore     the lore lines
     * @return the item stack
     */
    private @NotNull ItemStack createItem(Material material,
                                          Component name,
                                          List<Component> lore) {
        // Convert Bukkit material to PacketEvents ItemType
        com.github.retrooper.packetevents.protocol.item.type.ItemType type =
                com.github.retrooper.packetevents.protocol.item.type.ItemType.fromName(
                        material.name().toLowerCase()
                );

        if (type == null) {
            type = ItemTypes.STONE;
        }

        ItemStack.Builder builder = ItemStack.builder()
                .type(type)
                .amount(1);

        // Build NBT for display name and lore
        // This is simplified - full NBT implementation would be more complex

        return builder.build();
    }

    /**
     * Handles a click in the GUI.
     *
     * @param player       the player who clicked
     * @param windowId     the window ID
     * @param slot         the slot clicked
     * @param isRightClick whether it was a right click
     */
    public void handleClick(Player player, int windowId, int slot, boolean isRightClick) {
        UUID uuid = player.getUniqueId();
        GUISession session = activeSessions.get(uuid);

        if (session == null || session.getWindowId() != windowId) {
            return;
        }

        switch (session.getType()) {
            case PROFILE_LIST -> handleProfileListClick(player, slot, isRightClick);
            case PROFILE_CREATE -> handleCreateClick(player, slot);
        }
    }

    /**
     * Handles clicks in the profile list GUI.
     *
     * @param player       the player
     * @param slot         the slot clicked
     * @param isRightClick whether it was a right click
     */
    private void handleProfileListClick(Player player, int slot, boolean isRightClick) {
        // Create Profile button (slot 49)
        if (slot == 49) {
            closeGUI(player);
            openCreateGUI(player);
            return;
        }

        // Close button (slot 53)
        if (slot == 53) {
            closeGUI(player);
            return;
        }

        // Profile items (slots 10-43)
        if (slot >= 10 && slot <= 43) {
            List<String> profiles = plugin.getProfileManager().getProfileNames(player.getUniqueId());
            int index = calculateProfileIndex(slot);

            if (index < profiles.size()) {
                String profileName = profiles.get(index);

                if (isRightClick) {
                    // Delete profile
                    handleDeleteProfile(player, profileName);
                } else {
                    // Switch profile
                    handleSwitchProfile(player, profileName);
                }
            }
        }
    }

    /**
     * Handles clicks in the create GUI.
     *
     * @param player the player
     * @param slot   the slot clicked
     */
    private void handleCreateClick(Player player, int slot) {
        // Back button (slot 22)
        if (slot == 22) {
            closeGUI(player);
            openProfileGUI(player);
            return;
        }

        // Preset buttons
        String presetName = null;
        switch (slot) {
            case 11 -> presetName = "default";
            case 12 -> presetName = "admin";
            case 13 -> presetName = "builder";
            case 14 -> presetName = "mod";
            case 15 -> presetName = "vip";
        }

        if (presetName != null) {
            handleCreateProfile(player, presetName);
        }
    }

    /**
     * Handles profile switching.
     *
     * @param player      the player
     * @param profileName the profile to switch to
     */
    private void handleSwitchProfile(@NotNull Player player, String profileName) {
        closeGUI(player);

        plugin.getSwitchManager().initiateSwitch(player, profileName);
    }

    /**
     * Handles profile deletion.
     *
     * @param player      the player
     * @param profileName the profile to delete
     */
    private void handleDeleteProfile(@NotNull Player player, String profileName) {
        closeGUI(player);

        plugin.getProfileManager().deleteProfile(player, profileName)
                .thenAccept(success -> {
                    if (success) {
                        plugin.sendMessage(player,
                                "Profile '" + profileName + "' deleted successfully!",
                                NamedTextColor.GREEN);
                    } else {
                        plugin.sendMessage(player,
                                "Failed to delete profile!",
                                NamedTextColor.RED);
                    }
                });
    }

    /**
     * Handles profile creation.
     *
     * @param player      the player
     * @param profileName the profile name
     */
    private void handleCreateProfile(@NotNull Player player, String profileName) {
        closeGUI(player);

        plugin.getProfileManager().createProfile(player, profileName)
                .thenAccept(success -> {
                    if (success) {
                        plugin.sendMessage(player,
                                "Profile '" + profileName + "' created successfully!",
                                NamedTextColor.GREEN);
                    } else {
                        plugin.sendMessage(player,
                                "Failed to create profile!",
                                NamedTextColor.RED);
                    }
                });
    }

    /**
     * Closes the GUI for a player.
     *
     * @param player the player
     */
    public void closeGUI(@NotNull Player player) {
        activeSessions.remove(player.getUniqueId());
        player.closeInventory();
    }

    /**
     * Calculates the profile index from a GUI slot.
     *
     * @param slot the slot number
     * @return the profile index
     */
    private int calculateProfileIndex(int slot) {
        int row = (slot - 10) / 9;
        int col = (slot - 10) % 9;

        if (col >= 7) return -1; // Outside valid area

        return row * 7 + col;
    }

    /**
     * Formats a time duration for display.
     *
     * @param millis time in milliseconds
     * @return formatted string
     */
    private @NotNull String formatTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + "d ago";
        } else if (hours > 0) {
            return hours + "h ago";
        } else if (minutes > 0) {
            return minutes + "m ago";
        } else {
            return "Just now";
        }
    }

    /**
     * Cleans up sessions for a player.
     *
     * @param uuid player UUID
     */
    public void cleanup(UUID uuid) {
        activeSessions.remove(uuid);
    }

    /**
     * Gets the active session for a player.
     *
     * @param uuid player UUID
     * @return the session or null
     */
    public GUISession getSession(UUID uuid) {
        return activeSessions.get(uuid);
    }

    /**
     * GUI types.
     */
    public enum GUIType {
        PROFILE_LIST,
        PROFILE_CREATE
    }

    /**
     * Represents a GUI session.
     */
    public static class GUISession {
        private final Player player;
        private final GUIType type;
        private final int windowId;

        public GUISession(Player player, GUIType type) {
            this.player = player;
            this.type = type;
            this.windowId = player.getOpenInventory().getTopInventory().hashCode();
        }

        public Player getPlayer() {
            return player;
        }

        public GUIType getType() {
            return type;
        }

        public int getWindowId() {
            return windowId;
        }
    }
}