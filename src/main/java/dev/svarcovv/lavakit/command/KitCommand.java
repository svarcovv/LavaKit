package dev.svarcovv.lavakit.command;

import dev.svarcovv.lavakit.LavaKit;
import dev.svarcovv.lavakit.menu.KitMenuHolder;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.ArrayList;
import java.util.List;

public class KitCommand implements CommandExecutor {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('&').hexColors().build();

    private static final int MAX_SLOT = 54;

    private static final int[] MAIN_GRAY_SLOTS = {
            2, 6, 10, 16, 38, 42, 46, 48, 50, 52
    };

    private static final int[] MAIN_ORANGE_SLOTS = {
            1, 3, 5, 7, 9, 17, 27, 35, 37, 43, 47, 51
    };

    private static final Sound SND_MENU_OPEN = Sound.BLOCK_CHEST_OPEN;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Команда доступна только игрокам."));
            return true;
        }
        openMainMenu(player);
        return true;
    }

    public static void openMainMenu(Player player) {
        FileConfiguration config = LavaKit.getInstance().getConfig();
        Inventory menu = new KitMenuHolder(null, LavaKit.getInstance().getMenuTitle()).getInventory();

        applyFillers(menu);

        ConfigurationSection kitsSection = config.getConfigurationSection("kits");
        if (kitsSection != null) {
            for (String key : kitsSection.getKeys(false)) {
                String path = "kits." + key;

                if (!config.isInt(path + ".slot")) {
                    LavaKit.getInstance().getLogger().warning(
                            "Кит '" + key + "' не имеет параметра slot - пропущен.");
                    continue;
                }

                int slot = config.getInt(path + ".slot");
                if (slot < 0 || slot >= MAX_SLOT) {
                    LavaKit.getInstance().getLogger().warning(
                            "Кит '" + key + "' имеет некорректный slot=" + slot + " - пропущен.");
                    continue;
                }

                Color color = parseColor(config.getString(path + ".rgb", "255, 255, 255"));
                createKitButton(menu, slot, color, path, config);
            }
        }

        int linkSlot = config.getInt("buttons.link.slot", 4);
        if (linkSlot >= 0 && linkSlot < MAX_SLOT) {
            createSimpleButton(menu, linkSlot, Material.BELL, "buttons.link", config);
        }

        int backSlot = config.getInt("buttons.back.slot", 49);
        if (backSlot >= 0 && backSlot < MAX_SLOT) {
            createSimpleButton(menu, backSlot, Material.SPECTRAL_ARROW, "buttons.back", config);
        }

        player.openInventory(menu);
        player.playSound(player.getLocation(), SND_MENU_OPEN, 0.5f, 1.4f);
    }

    private static void applyFillers(Inventory menu) {
        for (int slot : MAIN_GRAY_SLOTS) {
            if (slot >= 0 && slot < menu.getSize()) {
                menu.setItem(slot, createFiller(Material.GRAY_STAINED_GLASS_PANE));
            }
        }

        for (int slot : MAIN_ORANGE_SLOTS) {
            if (slot >= 0 && slot < menu.getSize()) {
                menu.setItem(slot, createFiller(Material.ORANGE_STAINED_GLASS_PANE));
            }
        }
    }

    private static ItemStack createFiller(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            applyHideFlags(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static void applyHideFlags(ItemMeta meta) {
        if (meta == null) return;
        try {
            NamespacedKey dummyKey = new NamespacedKey("lavakit", "dummy");
            AttributeModifier dummyModifier = new AttributeModifier(
                    dummyKey,
                    0.0,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.ANY
            );

            meta.addAttributeModifier(Attribute.LUCK, dummyModifier);
        } catch (Exception e) {
        }

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.addItemFlags(ItemFlag.HIDE_ARMOR_TRIM);
        meta.addItemFlags(ItemFlag.HIDE_STORED_ENCHANTS);
        meta.addItemFlags(ItemFlag.HIDE_DYE);
    }

    private static Color parseColor(String raw) {
        if (raw == null || raw.isEmpty()) return Color.WHITE;
        String cleaned = raw.trim();

        if (cleaned.contains(",")) {
            String[] parts = cleaned.split(",");
            if (parts.length == 3) {
                try {
                    int r = clamp(Integer.parseInt(parts[0].trim()));
                    int g = clamp(Integer.parseInt(parts[1].trim()));
                    int b = clamp(Integer.parseInt(parts[2].trim()));
                    return Color.fromRGB(r, g, b);
                } catch (NumberFormatException e) {
                    return Color.WHITE;
                }
            }
            return Color.WHITE;
        }

        if (cleaned.startsWith("#")) cleaned = cleaned.substring(1);
        else if (cleaned.startsWith("0x") || cleaned.startsWith("0X")) cleaned = cleaned.substring(2);
        try {
            return Color.fromRGB(Integer.parseInt(cleaned, 16) & 0xFFFFFF);
        } catch (NumberFormatException e) {
            return Color.WHITE;
        }
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static void createKitButton(Inventory menu, int slot, Color color, String path, FileConfiguration config) {
        ItemStack item = new ItemStack(Material.LEATHER_CHESTPLATE);
        ItemMeta rawMeta = item.getItemMeta();
        if (rawMeta instanceof LeatherArmorMeta meta) {
            meta.setColor(color);
            setupMeta(meta, path, config);
            item.setItemMeta(meta);
        }
        menu.setItem(slot, item);
    }

    private static void createSimpleButton(Inventory menu, int slot, Material material, String path, FileConfiguration config) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            setupMeta(meta, path, config);
            item.setItemMeta(meta);
        }
        menu.setItem(slot, item);
    }

    private static void setupMeta(ItemMeta meta, String path, FileConfiguration config) {
        String name = config.getString(path + ".name", "");
        if (!name.isEmpty()) {
            meta.displayName(LEGACY.deserialize(name)
                    .decoration(TextDecoration.ITALIC, false));
        }

        List<String> loreLines = config.getStringList(path + ".lore");
        if (!loreLines.isEmpty()) {
            List<Component> loreComponents = new ArrayList<>(loreLines.size());
            for (String line : loreLines) {
                loreComponents.add(LEGACY.deserialize(line)
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(loreComponents);
        }

        applyHideFlags(meta);
    }
}