package me.sunmc;

import me.sunmc.api.ProfileAPI;
import me.sunmc.command.ProfileCommand;
import me.sunmc.config.ConfigManager;
import me.sunmc.gui.ProfileGUI;
import me.sunmc.listener.*;
import me.sunmc.manager.PacketManager;
import me.sunmc.manager.ProfileManager;
import me.sunmc.manager.SwitchManager;
import me.sunmc.storage.DatabaseManager;
import me.sunmc.util.ReflectionUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * PlayerProfile - Advanced multi-profile management system for Minecraft servers.
 *
 * <p>This plugin allows players to maintain multiple completely isolated profiles,
 * each with their own inventory, permissions, XP, and more. Perfect for servers
 * where players need different contexts (e.g., admin vs player mode).</p>
 *
 * <p>Version 1.0.2 Features:
 * <ul>
 *   <li>Client-side GUI using PacketEvents (zero server load)</li>
 *   <li>Enhanced database with connection pooling and caching</li>
 *   <li>Batch operations for improved performance</li>
 *   <li>Comprehensive metrics tracking</li>
 * </ul>
 * </p>
 *
 * @author SunMC Development Team
 * @version 1.0.2
 */
public final class PlayerProfile extends JavaPlugin {

    private static PlayerProfile instance;

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private ProfileManager profileManager;
    private SwitchManager switchManager;
    private PacketManager packetManager;
    private ProfileGUI profileGUI;
    private ExecutorService executorService;

    /**
     * Gets the singleton instance of the plugin.
     *
     * @return the plugin instance
     */
    public static PlayerProfile getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        long startTime = System.currentTimeMillis();

        int threadCount = Runtime.getRuntime().availableProcessors();
        this.executorService = Executors.newFixedThreadPool(threadCount, r -> {
            Thread thread = new Thread(r);
            thread.setName("PlayerProfile-Worker-" + thread.getId());
            thread.setDaemon(true);
            return thread;
        });

        getLogger().info("Initializing PlayerProfile v1.0.2...");

        this.configManager = new ConfigManager(this);
        this.configManager.loadConfig();

        // Initialize enhanced database manager
        this.databaseManager = new DatabaseManager(this);

        this.profileManager = new ProfileManager(this);
        this.switchManager = new SwitchManager(this);

        this.packetManager = new PacketManager(this);
        this.packetManager.initialize();

        // Initialize GUI system
        this.profileGUI = new ProfileGUI(this);
        getLogger().info("Client-side GUI system initialized");

        ProfileAPI.initialize(this);

        ProfileCommand profileCommand = new ProfileCommand(this);
        Objects.requireNonNull(getCommand("profile")).setExecutor(profileCommand);
        Objects.requireNonNull(getCommand("profile")).setTabCompleter(profileCommand);

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new DamageListener(this), this);
        getServer().getPluginManager().registerEvents(new MovementListener(this), this);
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);

        if (getServer().getPluginManager().getPlugin("LuckPerms") != null) {
            getLogger().info("LuckPerms integration enabled");
        } else {
            getLogger().warning("LuckPerms not found - permission contexts will not be available");
        }

        // Start metrics reporting
        startMetricsReporting();

        long loadTime = System.currentTimeMillis() - startTime;
        getLogger().info(String.format("PlayerProfile v1.0.2 enabled successfully in %dms", loadTime));
        getLogger().info("Public API available via ProfileAPI.getInstance()");
        getLogger().info("GUI system: Client-side rendering (zero TPS impact)");
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling PlayerProfile plugin...");

        if (switchManager != null) {
            switchManager.cancelAllSwitches();
        }

        if (packetManager != null) {
            packetManager.shutdown();
        }

        if (profileManager != null) {
            profileManager.shutdown();
        }

        if (databaseManager != null) {
            databaseManager.shutdown();
        }

        ReflectionUtils.clearCaches();

        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        getLogger().info("PlayerProfile disabled successfully");
    }

    /**
     * Starts periodic metrics reporting.
     */
    private void startMetricsReporting() {
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            DatabaseManager.DatabaseMetrics metrics = databaseManager.getMetrics();
            getLogger().info("Database Metrics: " + metrics.getSummary());
        }, 12000L, 12000L); // Every 10 minutes
    }

    /**
     * Gets the configuration manager.
     *
     * @return the configuration manager instance
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * Gets the database manager.
     *
     * @return the database manager instance
     */
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    /**
     * Gets the profile manager.
     *
     * @return the profile manager instance
     */
    public ProfileManager getProfileManager() {
        return profileManager;
    }

    /**
     * Gets the switch manager.
     *
     * @return the switch manager instance
     */
    public SwitchManager getSwitchManager() {
        return switchManager;
    }

    /**
     * Gets the packet manager.
     *
     * @return the packet manager instance
     */
    public PacketManager getPacketManager() {
        return packetManager;
    }

    /**
     * Gets the GUI manager.
     *
     * @return the GUI manager instance
     */
    public ProfileGUI getProfileGUI() {
        return profileGUI;
    }

    /**
     * Gets the thread pool executor for async operations.
     *
     * @return the executor service
     */
    public ExecutorService getExecutorService() {
        return executorService;
    }

    /**
     * Runs a task asynchronously using the plugin's thread pool.
     *
     * @param task the task to execute
     */
    public void runAsync(Runnable task) {
        executorService.submit(() -> {
            try {
                task.run();
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error executing async task", e);
            }
        });
    }

    /**
     * Runs a task synchronously on the main server thread.
     *
     * @param task the task to execute
     */
    public void runSync(Runnable task) {
        getServer().getScheduler().runTask(this, task);
    }

    /**
     * Sends a formatted message to a player.
     *
     * @param player  the target player
     * @param message the message to send
     * @param color   the text color
     */
    public void sendMessage(@NotNull Player player, String message, NamedTextColor color) {
        Component component = Component.text()
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(Component.text("Profiles", NamedTextColor.GOLD))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .append(Component.text(message, color))
                .build();

        player.sendMessage(component);
    }
}