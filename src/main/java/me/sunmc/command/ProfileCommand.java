package me.sunmc.command;

import me.sunmc.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Handles all /profile commands and tab completion.
 *
 * <p>Version 1.0.2 adds GUI support for visual profile management.</p>
 *
 * @author SunMC Development Team
 * @version 1.0.2
 */
public class ProfileCommand implements TabExecutor {

    private final PlayerProfile plugin;

    /**
     * Constructs a new ProfileCommand handler.
     *
     * @param plugin the plugin instance
     */
    public ProfileCommand(PlayerProfile plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command!", NamedTextColor.RED));
            return true;
        }

        // No args or "gui" subcommand opens the GUI
        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("gui"))) {
            plugin.getProfileGUI().openProfileGUI(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list" -> handleList(player);
            case "current" -> handleCurrent(player);
            case "create" -> handleCreate(player, args);
            case "switch" -> handleSwitch(player, args);
            case "delete" -> handleDelete(player, args);
            case "metrics" -> handleMetrics(player);
            default -> sendHelp(player);
        }

        return true;
    }

    /**
     * Sends help message to player.
     */
    private void sendHelp(@NotNull Player player) {
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text("Profile Commands", NamedTextColor.YELLOW, TextDecoration.BOLD));
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text("/profile", NamedTextColor.GRAY)
                .append(Component.text(" - Open GUI menu", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/profile gui", NamedTextColor.GRAY)
                .append(Component.text(" - Open GUI menu", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/profile list", NamedTextColor.GRAY)
                .append(Component.text(" - List all your profiles", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/profile current", NamedTextColor.GRAY)
                .append(Component.text(" - Show current profile", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/profile create <name>", NamedTextColor.GRAY)
                .append(Component.text(" - Create a new profile", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/profile switch <name>", NamedTextColor.GRAY)
                .append(Component.text(" - Switch to a profile", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/profile delete <name>", NamedTextColor.GRAY)
                .append(Component.text(" - Delete a profile", NamedTextColor.WHITE)));

        if (player.hasPermission("profiles.admin")) {
            player.sendMessage(Component.text("/profile metrics", NamedTextColor.GRAY)
                    .append(Component.text(" - View database metrics", NamedTextColor.WHITE)));
        }

        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD, TextDecoration.BOLD));
    }

    /**
     * Handles the list subcommand.
     */
    private void handleList(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        List<String> profiles = plugin.getProfileManager().getProfileNames(uuid);
        String currentProfile = plugin.getProfileManager().getActiveProfile(uuid);

        if (profiles.isEmpty()) {
            plugin.sendMessage(player, "You have no profiles!", NamedTextColor.RED);
            return;
        }

        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Your Profiles:", NamedTextColor.YELLOW, TextDecoration.BOLD));

        for (String profile : profiles) {
            Component line = Component.text("  • ", NamedTextColor.GRAY)
                    .append(Component.text(profile,
                            profile.equals(currentProfile) ? NamedTextColor.GREEN : NamedTextColor.WHITE));

            if (profile.equals(currentProfile)) {
                line = line.append(Component.text(" (active)", NamedTextColor.GRAY, TextDecoration.ITALIC));
            }

            player.sendMessage(line);
        }

        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Tip: Use /profile gui for visual management!",
                NamedTextColor.GRAY, TextDecoration.ITALIC));
    }

    /**
     * Handles the current subcommand.
     */
    private void handleCurrent(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        String currentProfile = plugin.getProfileManager().getActiveProfile(uuid);

        if (currentProfile == null) {
            plugin.sendMessage(player, "No active profile!", NamedTextColor.RED);
            return;
        }

        plugin.sendMessage(player,
                "Current profile: " + currentProfile,
                NamedTextColor.GREEN);
    }

    /**
     * Handles the create subcommand.
     */
    private void handleCreate(Player player, String @NotNull [] args) {
        if (args.length < 2) {
            plugin.sendMessage(player, "Usage: /profile create <name>", NamedTextColor.RED);
            return;
        }

        String profileName = args[1].toLowerCase();

        // Validate profile name
        if (!isValidProfileName(profileName)) {
            plugin.sendMessage(player,
                    "Invalid profile name! Use only letters, numbers, and underscores.",
                    NamedTextColor.RED);
            return;
        }

        if (profileName.length() > plugin.getConfigManager().getMaxProfileNameLength()) {
            plugin.sendMessage(player,
                    "Profile name too long! Maximum " +
                            plugin.getConfigManager().getMaxProfileNameLength() + " characters.",
                    NamedTextColor.RED);
            return;
        }

        // Check permission for specific profile name
        if (!player.hasPermission("profiles.create." + profileName)) {
            plugin.sendMessage(player,
                    "You don't have permission to create a profile named '" + profileName + "'!",
                    NamedTextColor.RED);
            return;
        }

        // Check profile limit
        UUID uuid = player.getUniqueId();
        List<String> existingProfiles = plugin.getProfileManager().getProfileNames(uuid);
        int maxProfiles = getMaxProfiles(player);

        if (maxProfiles != -1 && existingProfiles.size() >= maxProfiles) {
            plugin.sendMessage(player,
                    "You've reached your profile limit! (" + maxProfiles + ")",
                    NamedTextColor.RED);
            return;
        }

        plugin.sendMessage(player, "Creating profile...", NamedTextColor.YELLOW);

        plugin.getProfileManager().createProfile(player, profileName)
                .thenAccept(success -> plugin.runSync(() -> {
                    if (success) {
                        plugin.sendMessage(player,
                                "Profile '" + profileName + "' created successfully!",
                                NamedTextColor.GREEN);
                    } else {
                        plugin.sendMessage(player,
                                "Failed to create profile! It may already exist.",
                                NamedTextColor.RED);
                    }
                }));
    }

    /**
     * Handles the switch subcommand.
     */
    private void handleSwitch(Player player, String @NotNull [] args) {
        if (args.length < 2) {
            plugin.sendMessage(player, "Usage: /profile switch <name>", NamedTextColor.RED);
            return;
        }

        String profileName = args[1].toLowerCase();

        plugin.getSwitchManager().initiateSwitch(player, profileName)
                .thenAccept(success -> {
                    if (!success) {
                        // Error message already sent by SwitchManager
                    }
                });
    }

    /**
     * Handles the delete subcommand.
     */
    private void handleDelete(Player player, String @NotNull [] args) {
        if (args.length < 2) {
            plugin.sendMessage(player, "Usage: /profile delete <name>", NamedTextColor.RED);
            return;
        }

        String profileName = args[1].toLowerCase();
        UUID uuid = player.getUniqueId();

        // Cannot delete active profile
        if (profileName.equals(plugin.getProfileManager().getActiveProfile(uuid))) {
            plugin.sendMessage(player,
                    "You cannot delete your active profile! Switch to another profile first.",
                    NamedTextColor.RED);
            return;
        }

        // Cannot delete default profile
        if (profileName.equals(plugin.getConfigManager().getDefaultProfileName())) {
            plugin.sendMessage(player,
                    "You cannot delete the default profile!",
                    NamedTextColor.RED);
            return;
        }

        plugin.sendMessage(player, "Deleting profile...", NamedTextColor.YELLOW);

        plugin.getProfileManager().deleteProfile(player, profileName)
                .thenAccept(success -> plugin.runSync(() -> {
                    if (success) {
                        plugin.sendMessage(player,
                                "Profile '" + profileName + "' deleted successfully!",
                                NamedTextColor.GREEN);
                    } else {
                        plugin.sendMessage(player,
                                "Failed to delete profile! It may not exist.",
                                NamedTextColor.RED);
                    }
                }));
    }

    /**
     * Handles the metrics subcommand (admin only).
     */
    private void handleMetrics(@NotNull Player player) {
        if (!player.hasPermission("profiles.admin")) {
            plugin.sendMessage(player, "You don't have permission to view metrics!", NamedTextColor.RED);
            return;
        }

        var metrics = plugin.getDatabaseManager().getMetrics();

        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text("Database Metrics", NamedTextColor.YELLOW, TextDecoration.BOLD));
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD, TextDecoration.BOLD));

        player.sendMessage(Component.text("Queries Executed: ", NamedTextColor.GRAY)
                .append(Component.text(metrics.getQueriesExecuted(), NamedTextColor.WHITE)));

        player.sendMessage(Component.text("Cache Hit Rate: ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%.2f%%", metrics.getCacheHitRate()), NamedTextColor.GREEN)));

        player.sendMessage(Component.text("Cache Hits: ", NamedTextColor.GRAY)
                .append(Component.text(metrics.getCacheHits(), NamedTextColor.WHITE)));

        player.sendMessage(Component.text("Cache Misses: ", NamedTextColor.GRAY)
                .append(Component.text(metrics.getCacheMisses(), NamedTextColor.WHITE)));

        player.sendMessage(Component.text("Avg Query Time: ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%.2fms", metrics.getAverageQueryTimeMs()), NamedTextColor.YELLOW)));

        player.sendMessage(Component.text("Errors: ", NamedTextColor.GRAY)
                .append(Component.text(metrics.getErrors(),
                        metrics.getErrors() > 0 ? NamedTextColor.RED : NamedTextColor.GREEN)));

        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD, TextDecoration.BOLD));
    }

    /**
     * Validates a profile name.
     */
    @Contract(pure = true)
    private boolean isValidProfileName(@NotNull String name) {
        return name.matches("[a-zA-Z0-9_]+");
    }

    /**
     * Gets the maximum number of profiles a player can have.
     */
    private int getMaxProfiles(@NotNull Player player) {
        if (player.hasPermission("profiles.max.unlimited")) {
            return -1; // Unlimited
        }

        // Check for specific limits (higher numbers take priority)
        for (int i = 100; i >= 1; i--) {
            if (player.hasPermission("profiles.max." + i)) {
                return i;
            }
        }

        return 1; // Default to 1 profile
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>(
                    Stream.of("gui", "list", "current", "create", "switch", "delete")
                            .filter(s -> s.startsWith(args[0].toLowerCase()))
                            .toList()
            );

            if (player.hasPermission("profiles.admin")) {
                completions.add("metrics");
            }

            return completions;
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("switch") || subCommand.equals("delete")) {
                UUID uuid = player.getUniqueId();
                return plugin.getProfileManager().getProfileNames(uuid)
                        .stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return new ArrayList<>();
    }
}