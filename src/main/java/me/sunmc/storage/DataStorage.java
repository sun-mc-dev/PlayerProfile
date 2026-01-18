package me.sunmc.storage;

import me.sunmc.model.ProfileData;

import java.util.Map;
import java.util.UUID;

/**
 * Interface for profile data storage implementations.
 *
 * <p>Implementations of this interface handle persistent storage of profile data,
 * supporting various backends (SQLite, MySQL, MongoDB, etc.).</p>
 *
 * @author SunMC Development Team
 * @version 1.0.0
 */
public interface DataStorage {

    /**
     * Initializes the storage backend.
     * Creates necessary tables/collections if they don't exist.
     */
    void initialize();

    /**
     * Loads all profiles for a player.
     *
     * @param playerUUID the player's UUID
     * @return map of profile names to ProfileData
     */
    Map<String, ProfileData> loadProfiles(UUID playerUUID);

    /**
     * Saves a profile to storage.
     *
     * @param profile the profile to save
     * @return true if successful
     */
    boolean saveProfile(ProfileData profile);

    /**
     * Deletes a profile from storage.
     *
     * @param playerUUID  the player's UUID
     * @param profileName the profile name
     * @return true if successful
     */
    boolean deleteProfile(UUID playerUUID, String profileName);

    /**
     * Checks if a profile exists.
     *
     * @param playerUUID  the player's UUID
     * @param profileName the profile name
     * @return true if exists
     */
    boolean profileExists(UUID playerUUID, String profileName);

    /**
     * Closes the storage connection.
     */
    void close();
}