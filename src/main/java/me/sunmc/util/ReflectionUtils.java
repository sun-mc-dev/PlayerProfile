package me.sunmc.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for reflection operations.
 *
 * <p>Provides methods for accessing NMS (Net Minecraft Server) classes and methods
 * for advanced operations that aren't exposed through the Bukkit API. Uses caching
 * for performance optimization.</p>
 *
 * @author SunMC Development Team
 * @version 1.0.0
 */
public class ReflectionUtils {

    private static final Map<String, Class<?>> CLASS_CACHE = new HashMap<>();
    private static final Map<String, Method> METHOD_CACHE = new HashMap<>();
    private static final Map<String, Field> FIELD_CACHE = new HashMap<>();

    private static String cachedVersion;

    /**
     * Gets the NMS version string.
     *
     * @return the version string (e.g., "v1_21_R1")
     */
    public static String getVersion() {
        if (cachedVersion != null) {
            return cachedVersion;
        }

        String packageName = Bukkit.getServer().getClass().getPackage().getName();
        cachedVersion = packageName.substring(packageName.lastIndexOf('.') + 1);
        return cachedVersion;
    }

    /**
     * Gets an NMS class by name.
     *
     * @param className the class name (without package)
     * @return the class
     * @throws ClassNotFoundException if class not found
     */
    public static Class<?> getNMSClass(String className) throws ClassNotFoundException {
        String key = "nms_" + className;

        if (CLASS_CACHE.containsKey(key)) {
            return CLASS_CACHE.get(key);
        }

        String fullName = "net.minecraft.server." + getVersion() + "." + className;
        Class<?> clazz = Class.forName(fullName);
        CLASS_CACHE.put(key, clazz);

        return clazz;
    }

    /**
     * Gets a CraftBukkit class by name.
     *
     * @param className the class name (without package)
     * @return the class
     * @throws ClassNotFoundException if class not found
     */
    public static Class<?> getCraftClass(String className) throws ClassNotFoundException {
        String key = "craft_" + className;

        if (CLASS_CACHE.containsKey(key)) {
            return CLASS_CACHE.get(key);
        }

        String fullName = "org.bukkit.craftbukkit." + getVersion() + "." + className;
        Class<?> clazz = Class.forName(fullName);
        CLASS_CACHE.put(key, clazz);

        return clazz;
    }

    /**
     * Gets a method from a class by name and parameter types.
     *
     * @param clazz      the class
     * @param methodName the method name
     * @param paramTypes the parameter types
     * @return the method
     * @throws NoSuchMethodException if method not found
     */
    public static Method getMethod(@NotNull Class<?> clazz, String methodName, Class<?>... paramTypes)
            throws NoSuchMethodException {
        String key = clazz.getName() + "#" + methodName + "#" + Arrays.toString(paramTypes);

        if (METHOD_CACHE.containsKey(key)) {
            return METHOD_CACHE.get(key);
        }

        Method method = clazz.getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        METHOD_CACHE.put(key, method);

        return method;
    }

    /**
     * Gets a field from a class by name.
     *
     * @param clazz     the class
     * @param fieldName the field name
     * @return the field
     * @throws NoSuchFieldException if field not found
     */
    public static Field getField(@NotNull Class<?> clazz, String fieldName) throws NoSuchFieldException {
        String key = clazz.getName() + "#" + fieldName;

        if (FIELD_CACHE.containsKey(key)) {
            return FIELD_CACHE.get(key);
        }

        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        FIELD_CACHE.put(key, field);

        return field;
    }

    /**
     * Invokes a method on an object.
     *
     * @param obj    the object
     * @param method the method
     * @param args   the arguments
     * @return the return value
     * @throws Exception if invocation fails
     */
    public static Object invoke(Object obj, @NotNull Method method, Object... args) throws Exception {
        return method.invoke(obj, args);
    }

    /**
     * Gets the value of a field from an object.
     *
     * @param obj   the object
     * @param field the field
     * @return the field value
     * @throws Exception if access fails
     */
    @Contract(pure = true)
    public static Object getFieldValue(Object obj, @NotNull Field field) throws Exception {
        return field.get(obj);
    }

    /**
     * Sets the value of a field on an object.
     *
     * @param obj   the object
     * @param field the field
     * @param value the new value
     * @throws Exception if access fails
     */
    public static void setFieldValue(Object obj, @NotNull Field field, Object value) throws Exception {
        field.set(obj, value);
    }

    /**
     * Gets the NMS EntityPlayer object from a Bukkit Player.
     *
     * @param player the Bukkit player
     * @return the NMS EntityPlayer
     * @throws Exception if reflection fails
     */
    public static Object getHandle(@NotNull Player player) throws Exception {
        Method getHandle = getMethod(player.getClass(), "getHandle");
        return invoke(player, getHandle);
    }

    /**
     * Gets the player's connection object.
     *
     * @param player the Bukkit player
     * @return the player connection
     * @throws Exception if reflection fails
     */
    public static Object getConnection(Player player) throws Exception {
        Object entityPlayer = getHandle(player);
        Field connectionField = getField(entityPlayer.getClass(), "playerConnection");
        return getFieldValue(entityPlayer, connectionField);
    }

    /**
     * Sends a packet to a player using reflection.
     *
     * @param player the player
     * @param packet the NMS packet object
     * @throws Exception if sending fails
     */
    public static void sendPacket(Player player, @NotNull Object packet) throws Exception {
        Object connection = getConnection(player);
        Method sendPacket = getMethod(connection.getClass(), "sendPacket", packet.getClass());
        invoke(connection, sendPacket, packet);
    }

    /**
     * Creates a new instance of a class using reflection.
     *
     * @param clazz the class
     * @param args  the constructor arguments
     * @return the new instance
     * @throws Exception if instantiation fails
     */
    public static @NotNull Object newInstance(@NotNull Class<?> clazz, Object... args) throws Exception {
        Class<?>[] paramTypes = Arrays.stream(args)
                .map(Object::getClass)
                .toArray(Class<?>[]::new);

        Constructor<?> constructor = clazz.getDeclaredConstructor(paramTypes);
        constructor.setAccessible(true);

        return constructor.newInstance(args);
    }

    /**
     * Clears all reflection caches.
     * Should be called on plugin disable.
     */
    public static void clearCaches() {
        CLASS_CACHE.clear();
        METHOD_CACHE.clear();
        FIELD_CACHE.clear();
        cachedVersion = null;
    }

    /**
     * Checks if a class exists.
     *
     * @param className the full class name
     * @return true if the class exists
     */
    public static boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Gets the Minecraft server version (e.g., 1.21.4).
     *
     * @return the server version
     */
    public static @NotNull String getMinecraftVersion() {
        return Bukkit.getVersion().split("MC: ")[1].replace(")", "");
    }
}