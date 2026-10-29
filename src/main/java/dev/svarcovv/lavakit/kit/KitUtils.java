package dev.svarcovv.lavakit.kit;

import dev.svarcovv.lavakit.LavaKit;
import dev.svarcovv.lavakit.storage.SerializationUtils;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class KitUtils {

    private KitUtils() {}

    public static ItemStack[] loadKitItems(String kitName) {
        String base64 = getKitItemsBase64(kitName);
        if (base64 == null) return null;
        return SerializationUtils.itemStackArrayFromBase64(base64);
    }

    public static String getKitItemsBase64(String kitName) {
        return LavaKit.getInstance().getKitRepository().readNormal(kitName);
    }

    public static boolean canFit(Player player, ItemStack[] items) {
        return planStorage(player.getInventory().getStorageContents(),
                player.getInventory().getMaxStackSize(), items) != null;
    }

    public static boolean giveItems(Player player, ItemStack[] items) {
        return giveItems(player, items, 1);
    }

    public static boolean giveItems(Player player, ItemStack[] items, int copies) {
        ItemStack[] planned = planStorage(player.getInventory().getStorageContents(),
                player.getInventory().getMaxStackSize(), items, copies);
        if (planned == null) return false;
        player.getInventory().setStorageContents(planned);
        return true;
    }

    static ItemStack[] planStorage(ItemStack[] contents, int inventoryMax, ItemStack[] items) {
        return planStorage(contents, inventoryMax, items, 1);
    }

    static ItemStack[] planStorage(ItemStack[] contents, int inventoryMax, ItemStack[] items, int copies) {
        if (items == null || items.length == 0 || inventoryMax < 1 || copies < 1) return null;
        ItemStack[] result = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            result[i] = contents[i] == null ? null : contents[i].clone();
        }
        boolean hasItems = false;
        for (ItemStack source : items) {
            if (source == null || source.getType().isAir() || source.getAmount() <= 0) continue;
            hasItems = true;
            long remaining = (long) source.getAmount() * copies;
            int max = Math.min(inventoryMax, source.getMaxStackSize());
            if (max < 1) return null;
            for (ItemStack existing : result) {
                if (existing == null || !existing.isSimilar(source)) continue;
                int moved = (int) Math.min(remaining, Math.max(0, max - existing.getAmount()));
                existing.setAmount(existing.getAmount() + moved);
                remaining -= moved;
                if (remaining == 0) break;
            }
            for (int slot = 0; slot < result.length && remaining > 0; slot++) {
                if (result[slot] != null && !result[slot].getType().isAir()) continue;
                ItemStack part = source.clone();
                int moved = (int) Math.min(remaining, max);
                part.setAmount(moved);
                result[slot] = part;
                remaining -= moved;
            }
            if (remaining > 0) return null;
        }
        return hasItems ? result : null;
    }
}
