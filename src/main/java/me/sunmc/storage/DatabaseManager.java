package me.sunmc.storage;

import me.sunmc.PlayerProfile;
import me.sunmc.model.ProfileData;
import me.sunmc.storage.impl.SQLiteStorage;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enhanced database manager with connection pooling, batch operations,
 * and performance optimizations.
 *
 * <p>This manager provides:
 * <ul>
 *   <li>Connection pooling for better resource management</li>
 *   <li>Batch operations for bulk saves/loads</li>
 *   <li>Query result caching with TTL</li>
 *   <li>Automatic retry logic for failed operations</li>
 *   <li>Metrics tracking for performance monitoring</li>
 * </ul>
 * </p>
 *
 * @author SunMC Development Team
 * @version 1.0.2
 */
public class DatabaseManager {

    private final PlayerProfile plugin;
    private final DataStorage storage;
    private final ExecutorService batchExecutor;
    private final Queue<DataStorage> connectionPool;
    private final int poolSize;
    private final ConcurrentLinkedQueue<BatchOperation> batchQueue;
    private final Map<String, CacheEntry<ProfileData>> cache;
    private final long cacheTTL;
    private final DatabaseMetrics metrics;
    private final int maxRetries;
    private final long retryDelayMs;
    private ScheduledExecutorService batchScheduler;

    /**
     * Constructs a new DatabaseManager.
     *
     * @param plugin the plugin instance
     */
    public DatabaseManager(PlayerProfile plugin) {
        this.plugin = plugin;
        this.poolSize = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        this.connectionPool = new ConcurrentLinkedQueue<>();
        this.batchQueue = new ConcurrentLinkedQueue<>();
        this.cache = new ConcurrentHashMap<>();
        this.cacheTTL = 300000; // 5 minutes
        this.metrics = new DatabaseMetrics();
        this.maxRetries = 3;
        this.retryDelayMs = 1000;
        this.storage = new SQLiteStorage(plugin);
        this.storage.initialize();

        // Initialize connection pool
        for (int i = 0; i < poolSize - 1; i++) {
            DataStorage pooledStorage = new SQLiteStorage(plugin);
            pooledStorage.initialize();
            connectionPool.offer(pooledStorage);
        }

        // Initialize batch executor
        this.batchExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setName("ProfileDB-Batch");
            thread.setDaemon(true);
            return thread;
        });

        startBatchProcessor();
        startCacheCleanup();

        plugin.getLogger().info("Database manager initialized with pool size: " + poolSize);
    }

    /**
     * Gets a connection from the pool.
     *
     * @return a DataStorage instance
     */
    private DataStorage getConnection() {
        DataStorage conn = connectionPool.poll();
        return conn != null ? conn : storage;
    }

    /**
     * Returns a connection to the pool.
     *
     * @param connection the connection to return
     */
    private void returnConnection(DataStorage connection) {
        if (connection != storage) {
            connectionPool.offer(connection);
        }
    }

    /**
     * Loads profiles with caching and retry logic.
     *
     * @param playerUUID the player's UUID
     * @return CompletableFuture with profile map
     */
    public CompletableFuture<Map<String, ProfileData>> loadProfilesAsync(UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.nanoTime();

            try {
                // Check cache first
                Map<String, ProfileData> cached = getCachedProfiles(playerUUID);
                if (cached != null) {
                    metrics.recordCacheHit();
                    metrics.recordQueryTime(System.nanoTime() - startTime);
                    return cached;
                }

                metrics.recordCacheMiss();

                // Load from database with retry
                Map<String, ProfileData> profiles = loadWithRetry(playerUUID);

                // Cache results
                cacheProfiles(playerUUID, profiles);

                metrics.recordQueryTime(System.nanoTime() - startTime);
                metrics.incrementQueriesExecuted();

                return profiles;

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load profiles: " + e.getMessage());
                metrics.incrementErrors();
                return new HashMap<>();
            }
        }, plugin.getExecutorService());
    }

    /**
     * Loads profiles with retry logic.
     *
     * @param playerUUID the player's UUID
     * @return profile map
     */
    private Map<String, ProfileData> loadWithRetry(UUID playerUUID) {
        int attempts = 0;
        Exception lastException = null;

        while (attempts < maxRetries) {
            DataStorage conn = getConnection();
            try {
                Map<String, ProfileData> profiles = conn.loadProfiles(playerUUID);
                returnConnection(conn);
                return profiles;

            } catch (Exception e) {
                lastException = e;
                returnConnection(conn);
                attempts++;

                if (attempts < maxRetries) {
                    try {
                        Thread.sleep(retryDelayMs * attempts);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        throw new RuntimeException("Failed after " + maxRetries + " attempts", lastException);
    }

    /**
     * Saves a profile with batching support.
     *
     * @param profile the profile to save
     * @return CompletableFuture with success status
     */
    public CompletableFuture<Boolean> saveProfileAsync(@NotNull ProfileData profile) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.nanoTime();

            try {
                // Update cache
                updateCache(profile);

                // Save to database
                DataStorage conn = getConnection();
                boolean success = conn.saveProfile(profile);
                returnConnection(conn);

                metrics.recordQueryTime(System.nanoTime() - startTime);
                metrics.incrementQueriesExecuted();

                return success;

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to save profile: " + e.getMessage());
                metrics.incrementErrors();
                return false;
            }
        }, plugin.getExecutorService());
    }

    /**
     * Batch saves multiple profiles efficiently.
     *
     * @param profiles list of profiles to save
     * @return CompletableFuture with success count
     */
    public CompletableFuture<Integer> batchSaveProfiles(@NotNull List<ProfileData> profiles) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.nanoTime();
            int successCount = 0;

            DataStorage conn = getConnection();

            try {
                for (ProfileData profile : profiles) {
                    try {
                        if (conn.saveProfile(profile)) {
                            successCount++;
                            updateCache(profile);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to save profile in batch: " + e.getMessage());
                        metrics.incrementErrors();
                    }
                }

                metrics.recordQueryTime(System.nanoTime() - startTime);
                metrics.incrementQueriesExecuted();

            } finally {
                returnConnection(conn);
            }

            return successCount;
        }, batchExecutor);
    }

    /**
     * Adds a save operation to the batch queue.
     *
     * @param profile the profile to queue
     */
    public void queueBatchSave(@NotNull ProfileData profile) {
        batchQueue.offer(new BatchOperation(BatchOperationType.SAVE, profile));
    }

    /**
     * Starts the batch processor.
     */
    private void startBatchProcessor() {
        batchScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setName("ProfileDB-BatchProcessor");
            thread.setDaemon(true);
            return thread;
        });

        batchScheduler.scheduleAtFixedRate(() -> {
            try {
                processBatchQueue();
            } catch (Exception e) {
                plugin.getLogger().severe("Error processing batch queue: " + e.getMessage());
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    /**
     * Processes the batch queue.
     */
    private void processBatchQueue() {
        if (batchQueue.isEmpty()) {
            return;
        }

        List<ProfileData> toSave = new ArrayList<>();
        BatchOperation operation;

        while ((operation = batchQueue.poll()) != null) {
            if (operation.type() == BatchOperationType.SAVE) {
                toSave.add(operation.profile());
            }
        }

        if (!toSave.isEmpty()) {
            batchSaveProfiles(toSave).thenAccept(count -> {
                plugin.getLogger().fine("Batch saved " + count + "/" + toSave.size() + " profiles");
            });
        }
    }

    /**
     * Gets cached profiles for a player.
     *
     * @param playerUUID the player's UUID
     * @return cached profiles or null if not cached/expired
     */
    private @Nullable Map<String, ProfileData> getCachedProfiles(UUID playerUUID) {
        Map<String, ProfileData> result = new HashMap<>();
        boolean allFound = true;

        // Try to get all profiles from cache
        DataStorage conn = getConnection();
        Map<String, ProfileData> allProfiles = conn.loadProfiles(playerUUID);
        returnConnection(conn);

        for (String profileName : allProfiles.keySet()) {
            String cacheKey = getCacheKey(playerUUID, profileName);
            CacheEntry<ProfileData> entry = cache.get(cacheKey);

            if (entry != null && !entry.isExpired()) {
                result.put(profileName, entry.getValue());
            } else {
                allFound = false;
                break;
            }
        }

        return allFound && !result.isEmpty() ? result : null;
    }

    /**
     * Caches profiles for a player.
     *
     * @param playerUUID the player's UUID
     * @param profiles   the profiles to cache
     */
    private void cacheProfiles(UUID playerUUID, @NotNull Map<String, ProfileData> profiles) {
        for (Map.Entry<String, ProfileData> entry : profiles.entrySet()) {
            String cacheKey = getCacheKey(playerUUID, entry.getKey());
            cache.put(cacheKey, new CacheEntry<>(entry.getValue(), cacheTTL));
        }
    }

    /**
     * Updates a single profile in cache.
     *
     * @param profile the profile to cache
     */
    private void updateCache(@NotNull ProfileData profile) {
        String cacheKey = getCacheKey(profile.getPlayerUUID(), profile.getProfileName());
        cache.put(cacheKey, new CacheEntry<>(profile, cacheTTL));
    }

    /**
     * Invalidates cache for a player.
     *
     * @param playerUUID the player's UUID
     */
    public void invalidateCache(UUID playerUUID) {
        cache.keySet().removeIf(key -> key.startsWith(playerUUID.toString()));
    }

    /**
     * Generates a cache key.
     *
     * @param playerUUID  player UUID
     * @param profileName profile name
     * @return cache key
     */
    @Contract(pure = true)
    private @NotNull String getCacheKey(@NotNull UUID playerUUID, String profileName) {
        return playerUUID + ":" + profileName;
    }

    /**
     * Starts cache cleanup task.
     */
    private void startCacheCleanup() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            cache.entrySet().removeIf(entry -> entry.getValue().isExpired());

            int size = cache.size();
            if (size > 0) {
                plugin.getLogger().fine("Cache cleanup: " + size + " entries remaining");
            }
        }, 6000L, 6000L); // Every 5 minutes
    }

    /**
     * Gets the primary storage instance.
     *
     * @return the storage instance
     */
    public DataStorage getStorage() {
        return storage;
    }

    /**
     * Gets performance metrics.
     *
     * @return the metrics instance
     */
    public DatabaseMetrics getMetrics() {
        return metrics;
    }

    /**
     * Shuts down the database manager.
     */
    public void shutdown() {
        plugin.getLogger().info("Shutting down database manager...");

        // Process remaining batch operations
        processBatchQueue();

        // Shutdown executors
        if (batchScheduler != null) {
            batchScheduler.shutdown();
        }

        batchExecutor.shutdown();

        try {
            if (!batchExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                batchExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            batchExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Close all connections
        storage.close();

        DataStorage conn;
        while ((conn = connectionPool.poll()) != null) {
            conn.close();
        }

        // Clear cache
        cache.clear();

        // Log metrics
        plugin.getLogger().info("Final metrics: " + metrics.getSummary());
    }

    /**
     * Batch operation types.
     */
    private enum BatchOperationType {
        SAVE,
        DELETE
    }

    /**
     * Cache entry with TTL.
     */
    private static class CacheEntry<T> {
        private final T value;
        private final long expiryTime;

        public CacheEntry(T value, long ttl) {
            this.value = value;
            this.expiryTime = System.currentTimeMillis() + ttl;
        }

        public T getValue() {
            return value;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }

    /**
     * Batch operation wrapper.
     */
    private record BatchOperation(BatchOperationType type, ProfileData profile) {
    }

    /**
     * Database performance metrics.
     */
    public static class DatabaseMetrics {
        private final AtomicLong queriesExecuted;
        private final AtomicLong cacheHits;
        private final AtomicLong cacheMisses;
        private final AtomicLong errors;
        private final AtomicLong totalQueryTimeNanos;

        public DatabaseMetrics() {
            this.queriesExecuted = new AtomicLong(0);
            this.cacheHits = new AtomicLong(0);
            this.cacheMisses = new AtomicLong(0);
            this.errors = new AtomicLong(0);
            this.totalQueryTimeNanos = new AtomicLong(0);
        }

        public void incrementQueriesExecuted() {
            queriesExecuted.incrementAndGet();
        }

        public void recordCacheHit() {
            cacheHits.incrementAndGet();
        }

        public void recordCacheMiss() {
            cacheMisses.incrementAndGet();
        }

        public void incrementErrors() {
            errors.incrementAndGet();
        }

        public void recordQueryTime(long nanos) {
            totalQueryTimeNanos.addAndGet(nanos);
        }

        public long getQueriesExecuted() {
            return queriesExecuted.get();
        }

        public long getCacheHits() {
            return cacheHits.get();
        }

        public long getCacheMisses() {
            return cacheMisses.get();
        }

        public double getCacheHitRate() {
            long total = cacheHits.get() + cacheMisses.get();
            return total > 0 ? (double) cacheHits.get() / total * 100.0 : 0.0;
        }

        public long getErrors() {
            return errors.get();
        }

        public double getAverageQueryTimeMs() {
            long queries = queriesExecuted.get();
            return queries > 0 ? totalQueryTimeNanos.get() / (double) queries / 1_000_000.0 : 0.0;
        }

        public String getSummary() {
            return String.format(
                    "Queries: %d, Cache Hit Rate: %.2f%%, Avg Query Time: %.2fms, Errors: %d",
                    getQueriesExecuted(),
                    getCacheHitRate(),
                    getAverageQueryTimeMs(),
                    getErrors()
            );
        }

        public void reset() {
            queriesExecuted.set(0);
            cacheHits.set(0);
            cacheMisses.set(0);
            errors.set(0);
            totalQueryTimeNanos.set(0);
        }
    }
}