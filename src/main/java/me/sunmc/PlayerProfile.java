package me.sunmc;

import me.sunmc.api.ProfileAPI;
import me.sunmc.commands.ProfileCommand;
import me.sunmc.config.ConfigManager;
import me.sunmc.listeners.CombatListener;
import me.sunmc.listeners.DamageListener;
import me.sunmc.listeners.MovementListener;
import me.sunmc.listeners.PlayerListener;
import me.sunmc.managers.PacketManager;
import me.sunmc.managers.ProfileManager;
import me.sunmc.managers.SwitchManager;
import me.sunmc.storage.DataStorage;
import me.sunmc.storage.impl.SQLiteStorage;
import me.sunmc.utils.ReflectionUtils;
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
 * <p>Features:
 * <ul>
 *   <li>Unlimited profiles per player (permission-based)</li>
 *   <li>Complete data isolation between profiles</li>
 *   <li>Combat and movement protection during switches</li>
 *   <li>LuckPerms integration for permission contexts</li>
 *   <li>Async operations for optimal performance</li>
 *   <li>Configurable warmup timers and restrictions</li>
 * </ul>
 * </p>
 *
 * @author SunMC Development Team
 * @version 1.0.0
 */
public final class PlayerProfile extends JavaPlugin {

    private static PlayerProfile instance;

    private ConfigManager configManager;
    private DataStorage dataStorage;
    private ProfileManager profileManager;
    private SwitchManager switchManager;
    private PacketManager packetManager;
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

        getLogger().info("Initializing PlayerProfile plugin...");

        this.configManager = new ConfigManager(this);
        this.configManager.loadConfig();

        this.dataStorage = new SQLiteStorage(this);
        this.dataStorage.initialize();

        this.profileManager = new ProfileManager(this);
        this.switchManager = new SwitchManager(this);

        this.packetManager = new PacketManager(this);
        this.packetManager.initialize();

        ProfileAPI.initialize(this);

        ProfileCommand profileCommand = new ProfileCommand(this);
        Objects.requireNonNull(getCommand("profile")).setExecutor(profileCommand);
        Objects.requireNonNull(getCommand("profile")).setTabCompleter(profileCommand);

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new DamageListener(this), this);
        getServer().getPluginManager().registerEvents(new MovementListener(this), this);

        if (getServer().getPluginManager().getPlugin("LuckPerms") != null) {
            getLogger().info("LuckPerms integration enabled");
        } else {
            getLogger().warning("LuckPerms not found - permission contexts will not be available");
        }

        long loadTime = System.currentTimeMillis() - startTime;
        getLogger().info(String.format("PlayerProfile enabled successfully in %dms", loadTime));
        getLogger().info("Public API available via ProfileAPI.getInstance()");
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

        if (dataStorage != null) {
            dataStorage.close();
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
     * Gets the configuration manager.
     *
     * @return the configuration manager instance
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * Gets the data storage handler.
     *
     * @return the data storage instance
     */
    public DataStorage getDataStorage() {
        return dataStorage;
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