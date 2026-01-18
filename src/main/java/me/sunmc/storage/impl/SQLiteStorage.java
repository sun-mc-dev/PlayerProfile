package me.sunmc.storage.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.sunmc.PlayerProfile;
import me.sunmc.model.ProfileData;
import me.sunmc.storage.DataStorage;
import me.sunmc.utils.ItemStackSerializer;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * SQLite implementation of DataStorage.
 *
 * <p>This implementation uses SQLite for local file-based storage of profile data.
 * All operations are thread-safe using read-write locks.</p>
 *
 * @author SunMC Development Team
 * @version 1.0.0
 */
public class SQLiteStorage implements DataStorage {

    private final PlayerProfile plugin;
    private final Gson gson;
    private final ReentrantReadWriteLock lock;
    private Connection connection;

    /**
     * Constructs a new SQLiteStorage instance.
     *
     * @param plugin the plugin instance
     */
    public SQLiteStorage(PlayerProfile plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
        this.lock = new ReentrantReadWriteLock();
    }

    @Override
    public void initialize() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            File dbFile = new File(dataFolder, "profiles.db");
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

            connection = DriverManager.getConnection(url);

            createTables();

            plugin.getLogger().info("SQLite storage initialized");

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize SQLite storage: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Creates necessary database tables.
     */
    private void createTables() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS profiles (" +
                "player_uuid TEXT NOT NULL, " +
                "profile_name TEXT NOT NULL, " +
                "inventory TEXT, " +
                "armor TEXT, " +
                "offhand TEXT, " +
                "ender_chest TEXT, " +
                "experience INTEGER, " +
                "level INTEGER, " +
                "exp REAL, " +
                "health REAL, " +
                "food_level INTEGER, " +
                "saturation REAL, " +
                "potion_effects TEXT, " +
                "game_mode TEXT, " +
                "is_vanished INTEGER, " +
                "last_used BIGINT, " +
                "created_at BIGINT, " +
                "PRIMARY KEY (player_uuid, profile_name)" +
                ")";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    @Override
    public Map<String, ProfileData> loadProfiles(@NotNull UUID playerUUID) {
        lock.readLock().lock();
        try {
            Map<String, ProfileData> profiles = new HashMap<>();

            String sql = "SELECT * FROM profiles WHERE player_uuid = ?";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());

                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    ProfileData profile = deserializeProfile(rs);
                    profiles.put(profile.getProfileName(), profile);
                }
            }

            return profiles;

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load profiles: " + e.getMessage());
            return new HashMap<>();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean saveProfile(@NotNull ProfileData profile) {
        lock.writeLock().lock();
        try {
            String sql = "INSERT OR REPLACE INTO profiles " +
                    "(player_uuid, profile_name, inventory, armor, offhand, ender_chest, " +
                    "experience, level, exp, health, food_level, saturation, " +
                    "potion_effects, game_mode, is_vanished, last_used, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, profile.getPlayerUUID().toString());
                stmt.setString(2, profile.getProfileName());
                stmt.setString(3, ItemStackSerializer.serialize(profile.getInventory()));
                stmt.setString(4, ItemStackSerializer.serialize(profile.getArmorContents()));
                stmt.setString(5, ItemStackSerializer.serializeItem(profile.getOffHand()));
                stmt.setString(6, ItemStackSerializer.serialize(profile.getEnderChest()));
                stmt.setInt(7, profile.getExperience());
                stmt.setInt(8, profile.getLevel());
                stmt.setFloat(9, profile.getExp());
                stmt.setDouble(10, profile.getHealth());
                stmt.setInt(11, profile.getFoodLevel());
                stmt.setFloat(12, profile.getSaturation());
                stmt.setString(13, serializePotionEffects(profile.getPotionEffects()));
                stmt.setString(14, profile.getGameMode().name());
                stmt.setInt(15, profile.isVanished() ? 1 : 0);
                stmt.setLong(16, profile.getLastUsed());
                stmt.setLong(17, profile.getCreatedAt());

                stmt.executeUpdate();
                return true;
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save profile: " + e.getMessage());
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean deleteProfile(@NotNull UUID playerUUID, String profileName) {
        lock.writeLock().lock();
        try {
            String sql = "DELETE FROM profiles WHERE player_uuid = ? AND profile_name = ?";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, profileName);

                return stmt.executeUpdate() > 0;
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to delete profile: " + e.getMessage());
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean profileExists(@NotNull UUID playerUUID, String profileName) {
        lock.readLock().lock();
        try {
            String sql = "SELECT 1 FROM profiles WHERE player_uuid = ? AND profile_name = ?";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, profileName);

                ResultSet rs = stmt.executeQuery();
                return rs.next();
            }

        } catch (SQLException e) {
            return false;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("SQLite connection closed");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error closing SQLite connection: " + e.getMessage());
        }
    }

    /**
     * Deserializes a ProfileData from a ResultSet.
     */
    private @NotNull ProfileData deserializeProfile(@NotNull ResultSet rs) throws SQLException {
        UUID uuid = UUID.fromString(rs.getString("player_uuid"));
        String name = rs.getString("profile_name");

        ProfileData profile = new ProfileData(uuid, name);

        profile.setInventory(ItemStackSerializer.deserialize(rs.getString("inventory")));
        profile.setArmorContents(ItemStackSerializer.deserialize(rs.getString("armor")));
        profile.setOffHand(ItemStackSerializer.deserializeItem(rs.getString("offhand")));
        profile.setEnderChest(ItemStackSerializer.deserialize(rs.getString("ender_chest")));

        profile.setExperience(rs.getInt("experience"));
        profile.setLevel(rs.getInt("level"));
        profile.setExp(rs.getFloat("exp"));

        profile.setHealth(rs.getDouble("health"));
        profile.setFoodLevel(rs.getInt("food_level"));
        profile.setSaturation(rs.getFloat("saturation"));

        profile.setPotionEffects(deserializePotionEffects(rs.getString("potion_effects")));
        profile.setGameMode(GameMode.valueOf(rs.getString("game_mode")));
        profile.setVanished(rs.getInt("is_vanished") == 1);

        profile.setLastUsed(rs.getLong("last_used"));
        profile.setCreatedAt(rs.getLong("created_at"));

        return profile;
    }

    /**
     * Serializes potion effects to JSON.
     */
    private String serializePotionEffects(@NotNull Collection<PotionEffect> effects) {
        List<Map<String, Object>> data = new ArrayList<>();

        for (PotionEffect effect : effects) {
            Map<String, Object> map = new HashMap<>();
            map.put("type", effect.getType().getKey().toString());
            map.put("duration", effect.getDuration());
            map.put("amplifier", effect.getAmplifier());
            map.put("ambient", effect.isAmbient());
            map.put("particles", effect.hasParticles());
            map.put("icon", effect.hasIcon());
            data.add(map);
        }

        return gson.toJson(data);
    }

    /**
     * Deserializes potion effects from JSON.
     */
    @SuppressWarnings("unchecked")
    private @NotNull Collection<PotionEffect> deserializePotionEffects(String json) {
        List<PotionEffect> effects = new ArrayList<>();

        if (json == null || json.isEmpty()) {
            return effects;
        }

        try {
            List<Map<String, Object>> data = gson.fromJson(json, List.class);

            for (Map<String, Object> map : data) {
                String typeKey = (String) map.get("type");
                PotionEffectType type = Registry.POTION_EFFECT_TYPE
                        .get(Objects.requireNonNull(NamespacedKey.fromString(typeKey)));

                if (type != null) {
                    int duration = ((Number) map.get("duration")).intValue();
                    int amplifier = ((Number) map.get("amplifier")).intValue();
                    boolean ambient = (boolean) map.get("ambient");
                    boolean particles = (boolean) map.get("particles");
                    boolean icon = (boolean) map.get("icon");

                    effects.add(new PotionEffect(type, duration, amplifier, ambient, particles, icon));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to deserialize potion effects: " + e.getMessage());
        }

        return effects;
    }
}