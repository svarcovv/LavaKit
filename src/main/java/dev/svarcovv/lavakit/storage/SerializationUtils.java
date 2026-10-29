package dev.svarcovv.lavakit.storage;

import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Base64;

public final class SerializationUtils {

    private static final int MAX_ITEMS = 1024;
    private static final int MAX_ITEM_BYTES = 1024 * 1024;

    private SerializationUtils() {}

    public static String itemStackArrayToBase64(ItemStack[] items) {
        if (items == null || items.length > MAX_ITEMS) return "";

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             DataOutputStream dataOutput = new DataOutputStream(outputStream)) {

            dataOutput.writeInt(items.length);
            for (ItemStack item : items) {
                if (item == null || item.getType().isAir()) {
                    dataOutput.writeInt(0);
                } else {
                    byte[] bytes = item.serializeAsBytes();
                    if (bytes.length > MAX_ITEM_BYTES) return "";
                    dataOutput.writeInt(bytes.length);
                    dataOutput.write(bytes);
                }
            }
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    public static ItemStack[] itemStackArrayFromBase64(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }

        byte[] rawData;
        try {
            rawData = Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException e) {
            return null;
        }

        try (DataInputStream dataInput = new DataInputStream(new ByteArrayInputStream(rawData))) {
            int length = dataInput.readInt();
            if (length < 0 || length > MAX_ITEMS) {
                return null;
            }

            ItemStack[] items = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                int byteLength = dataInput.readInt();
                if (byteLength < 0 || byteLength > MAX_ITEM_BYTES) {
                    return null;
                }
                if (byteLength == 0) {
                    items[i] = null;
                    continue;
                }
                byte[] bytes = new byte[byteLength];
                dataInput.readFully(bytes);
                items[i] = ItemStack.deserializeBytes(bytes);
            }
            if (dataInput.available() != 0) {
                return null;
            }
            return items;
        } catch (Exception e) {
            return null;
        }
    }
}
