package me.sunmc.utils;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * Utility class for serializing and deserializing ItemStacks to/from Base64 strings.
 *
 * <p>This class provides efficient serialization of ItemStack arrays and individual items
 * using Bukkit's built-in serialization with Base64 encoding.</p>
 *
 * @author SunMC Development Team
 * @version 1.0.0
 */
public class ItemStackSerializer {

    /**
     * Serializes an array of ItemStacks to a Base64 string.
     *
     * @param items the ItemStack array
     * @return Base64 encoded string
     */
    public static String serialize(ItemStack[] items) {
        if (items == null || items.length == 0) {
            return "";
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {

            dataOutput.writeInt(items.length);

            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }

            return Base64.getEncoder().encodeToString(outputStream.toByteArray());

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * Deserializes an array of ItemStacks from a Base64 string.
     *
     * @param data the Base64 encoded string
     * @return the ItemStack array
     */
    @Contract("null -> new")
    public static ItemStack @NotNull [] deserialize(String data) {
        if (data == null || data.isEmpty()) {
            return new ItemStack[0];
        }

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {

            int length = dataInput.readInt();
            ItemStack[] items = new ItemStack[length];

            for (int i = 0; i < length; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }

            return items;

        } catch (Exception e) {
            e.printStackTrace();
            return new ItemStack[0];
        }
    }

    /**
     * Serializes a single ItemStack to a Base64 string.
     *
     * @param item the ItemStack
     * @return Base64 encoded string
     */
    public static String serializeItem(ItemStack item) {
        if (item == null) {
            return "";
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {

            dataOutput.writeObject(item);

            return Base64.getEncoder().encodeToString(outputStream.toByteArray());

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * Deserializes a single ItemStack from a Base64 string.
     *
     * @param data the Base64 encoded string
     * @return the ItemStack or null
     */
    public static ItemStack deserializeItem(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {

            return (ItemStack) dataInput.readObject();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}