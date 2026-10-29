package dev.svarcovv.lavakit.menu;

import dev.svarcovv.lavakit.LavaKit;
import dev.svarcovv.lavakit.command.KitCommand;
import dev.svarcovv.lavakit.kit.KitUtils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KitMenuListener implements Listener {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('&').hexColors().build();

    private static final Map<UUID, Long> clickCooldown = new ConcurrentHashMap<>();
    private static final long CLICK_COOLDOWN_MS = 700L;

    private static final int DEFAULT_LINK_SLOT = 4;
    private static final int DEFAULT_BACK_SLOT = 49;

    private static final int DEFAULT_CLAIM_SLOT = 40;
    private static final int DEFAULT_PREVIEW_BACK_SLOT = 49;
    private static final int DEFAULT_BUY_SLOT = 44;

    private static final int[] PREVIEW_GRAY_SLOTS  = {0, 1, 7, 8, 45, 53};
    private static final int[] PREVIEW_WHITE_SLOTS = {2, 6, 36, 44, 46, 52, 39, 40, 41};

    private static final Sound SND_MENU_OPEN    = Sound.BLOCK_CHEST_OPEN;
    private static final Sound SND_PREVIEW_OPEN = Sound.UI_BUTTON_CLICK;
    private static final Sound SND_BACK         = Sound.UI_BUTTON_CLICK;
    private static final Sound SND_CLAIM        = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
    private static final Sound SND_BUY          = Sound.ENTITY_PLAYER_LEVELUP;
    private static final Sound SND_ERROR        = Sound.ENTITY_VILLAGER_NO;

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        Inventory menu = event.getView().getTopInventory();
        if (!(menu.getHolder() instanceof KitMenuHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != menu) return;
        if (event.getClick() != org.bukkit.event.inventory.ClickType.LEFT
                && event.getClick() != org.bukkit.event.inventory.ClickType.RIGHT) return;
        Bukkit.getScheduler().runTask(LavaKit.getInstance(), () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory() == menu) {
                handleMenuClick(event);
            }
        });
    }

    private void handleMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory topInventory = event.getView().getTopInventory();
        Component title = event.getView().title();
        UUID uuid = player.getUniqueId();
        FileConfiguration config = LavaKit.getInstance().getConfig();

        if (((KitMenuHolder) topInventory.getHolder()).kitName() != null) {
            event.setCancelled(true);
            if (event.getClickedInventory() == null || !event.getClickedInventory().equals(topInventory)) return;

            ItemStack currentItem = event.getCurrentItem();
            if (currentItem == null || currentItem.getType() == Material.AIR) return;

            int slot = event.getSlot();
            String kitName = ((KitMenuHolder) topInventory.getHolder()).kitName();
            if (kitName == null) return;

            if (slot == config.getInt("buttons.preview-back.slot", DEFAULT_PREVIEW_BACK_SLOT)) {
                if (isOnCooldown(player)) return;
                play(player, SND_BACK, 0.6f, 0.9f);

                KitCommand.openMainMenu(player);
                return;
            }

            if (slot == config.getInt("buttons.claim.slot", DEFAULT_CLAIM_SLOT)) {
                if (isOnCooldown(player)) return;

                String perm = config.getString("kits." + kitName + ".permission", "");
                if (!perm.isEmpty() && !player.hasPermission(perm)) {
                    sendErrorTitle(player, "&6Нет прав", "&fУ вас нет прав на этот набор");
                    return;
                }

                long remainingCooldown = LavaKit.getInstance().getCooldownManager()
                        .getRemainingMs(uuid, kitName);
                if (remainingCooldown > 0) {
                    sendErrorTitle(player, "&6До набора осталось",
                            "&f" + formatCooldown(remainingCooldown));
                    return;
                }

                ItemStack[] items = KitUtils.loadKitItems(kitName);
                if (items == null || items.length == 0 || !KitUtils.canFit(player, items)) {
                    sendErrorTitle(player, "&6Нет места", "&fОсвободите место в инвентаре");
                    return;
                }

                if (!KitUtils.giveItems(player, items)) {
                    sendErrorTitle(player, "&6Нет места", "&fОсвободите место в инвентаре");
                    return;
                }
                LavaKit.getInstance().getCooldownManager().set(uuid, kitName,
                        config.getInt("kits." + kitName + ".cooldown", 0));
                sendSuccessTitle(player, "&6Набор получен", "&fПриятной игры", SND_CLAIM);
                return;
            }

            if (slot == config.getInt("buttons.buy.slot", DEFAULT_BUY_SLOT)) {
                if (isOnCooldown(player)) return;

                if (LavaKit.getInstance().getPointsAPI() == null) {
                    sendErrorTitle(player, "&6Ошибка", "&fИнтеграция выключена");
                    return;
                }

                ItemStack[] items = KitUtils.loadKitItems(kitName);
                if (items == null || items.length == 0 || !KitUtils.canFit(player, items)) {
                    sendErrorTitle(player, "&6Нет места", "&fОсвободите место в инвентаре");
                    return;
                }

                int price = config.getInt("kits." + kitName + ".price", 100);
                if (price < 0) {
                    LavaKit.getInstance().getLogger().warning("Отрицательная цена кита: " + kitName);
                    sendErrorTitle(player, "&6Ошибка", "&fНекорректная цена набора");
                    return;
                }
                int balance = LavaKit.getInstance().getPointsAPI().look(uuid);
                if (balance < price) {
                    sendErrorTitle(player, "&6Не хватает осколков", "&fУ вас недостаточно средств");
                    return;
                }

                if (price > 0 && !LavaKit.getInstance().getPointsAPI().take(uuid, price)) {
                    sendErrorTitle(player, "&6Ошибка", "&fНе удалось списать средства");
                    return;
                }
                if (!KitUtils.giveItems(player, items)) {
                    if (price > 0) LavaKit.getInstance().getPointsAPI().give(uuid, price);
                    sendErrorTitle(player, "&6Нет места", "&fОсвободите место в инвентаре");
                    return;
                }
                sendSuccessTitle(player, "&6Набор куплен", "&fПриятной игры", SND_BUY);
                return;
            }

            return;
        }

        if (((KitMenuHolder) topInventory.getHolder()).kitName() == null) {
            event.setCancelled(true);
            if (event.getClickedInventory() == null || !event.getClickedInventory().equals(topInventory)) return;

            ItemStack currentItem = event.getCurrentItem();
            if (currentItem == null || currentItem.getType() == Material.AIR) return;

            int slot = event.getSlot();

            if (slot == config.getInt("buttons.link.slot", DEFAULT_LINK_SLOT)) {
                if (isOnCooldown(player)) return;
                play(player, SND_PREVIEW_OPEN, 0.6f, 1.0f);
                player.closeInventory();
                player.performCommand("link");
                return;
            }
            if (slot == config.getInt("buttons.back.slot", DEFAULT_BACK_SLOT)) {
                if (isOnCooldown(player)) return;
                play(player, SND_BACK, 0.6f, 0.9f);
                player.closeInventory();
                player.performCommand("menu");
                return;
            }

            ConfigurationSection kitsSection = config.getConfigurationSection("kits");
            if (kitsSection != null) {
                for (String key : kitsSection.getKeys(false)) {
                    String path = "kits." + key;
                    if (!config.isInt(path + ".slot")) continue;
                    if (config.getInt(path + ".slot") == slot) {
                        if (isOnCooldown(player)) return;
                        openPreviewMenu(player, key);
                        return;
                    }
                }
            }
        }
    }

    private static void sendErrorTitle(Player player, String titleLine, String subtitleLine) {
        player.closeInventory();
        player.playSound(player.getLocation(), SND_ERROR, 0.7f, 1.0f);
        player.showTitle(Title.title(
                LEGACY.deserialize(titleLine),
                LEGACY.deserialize(subtitleLine),
                Title.Times.times(
                        Duration.ofMillis(200),
                        Duration.ofSeconds(2),
                        Duration.ofMillis(500))));
    }

    private static void sendSuccessTitle(Player player, String titleLine, String subtitleLine, Sound sound) {
        player.closeInventory();
        player.playSound(player.getLocation(), sound, 0.8f, 1.4f);
        player.showTitle(Title.title(
                LEGACY.deserialize(titleLine),
                LEGACY.deserialize(subtitleLine),
                Title.Times.times(
                        Duration.ofMillis(200),
                        Duration.ofSeconds(2),
                        Duration.ofMillis(500))));
    }

    private static String formatCooldown(long millis) {
        long totalSeconds = (millis + 999L) / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    private boolean isOnCooldown(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = clickCooldown.get(uuid);
        if (last != null && (now - last) < CLICK_COOLDOWN_MS) {
            return true;
        }
        clickCooldown.put(uuid, now);
        return false;
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTopInventory().getHolder() instanceof KitMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        clickCooldown.remove(uuid);
    }

    private void openPreviewMenu(Player player, String kitName) {
        FileConfiguration config = LavaKit.getInstance().getConfig();
        String titleStr = config.getString("preview-title", "&8Просмотр кита: %kit%")
                .replace("%kit%", kitName);
        Component title = LEGACY.deserialize(titleStr);

        Inventory preview = new KitMenuHolder(kitName, title).getInventory();

        applyPreviewFillers(preview);

        ItemStack[] items = KitUtils.loadKitItems(kitName);
        if (items != null) {
            int startSlot = 9;
            for (ItemStack item : items) {
                if (startSlot >= 36) break;
                if (item != null && item.getType() != Material.AIR) {
                    preview.setItem(startSlot, item.clone());
                }
                startSlot++;
            }
        }

        setSafeButton(preview, config.getInt("buttons.claim.slot", DEFAULT_CLAIM_SLOT),
                Material.LIME_DYE, "buttons.claim", config, 0);
        setSafeButton(preview, config.getInt("buttons.preview-back.slot", DEFAULT_PREVIEW_BACK_SLOT),
                Material.SPECTRAL_ARROW, "buttons.preview-back", config, 0);
        setSafeButton(preview, config.getInt("buttons.buy.slot", DEFAULT_BUY_SLOT),
                Material.RED_DYE, "buttons.buy", config,
                config.getInt("kits." + kitName + ".price", 100));

        player.openInventory(preview);

        play(player, SND_PREVIEW_OPEN, 0.6f, 1.2f);
    }

    private void applyPreviewFillers(Inventory preview) {
        for (int slot : PREVIEW_GRAY_SLOTS) {
            if (slot >= 0 && slot < preview.getSize()) {
                preview.setItem(slot, createFiller(Material.GRAY_STAINED_GLASS_PANE));
            }
        }
        for (int slot : PREVIEW_WHITE_SLOTS) {
            if (slot >= 0 && slot < preview.getSize()) {
                preview.setItem(slot, createFiller(Material.WHITE_STAINED_GLASS_PANE));
            }
        }
    }

    private ItemStack createFiller(Material material) {
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
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.addItemFlags(ItemFlag.HIDE_DESTROYS);
        meta.addItemFlags(ItemFlag.HIDE_PLACED_ON);
        meta.addItemFlags(ItemFlag.HIDE_ARMOR_TRIM);
        meta.addItemFlags(ItemFlag.HIDE_STORED_ENCHANTS);
    }

    private void setSafeButton(Inventory inv, int slot, Material mat, String path,
                               FileConfiguration config, int price) {
        if (slot < 0 || slot >= inv.getSize()) return;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = config.getString(path + ".name", "");
            if (!name.isEmpty()) {
                meta.displayName(LEGACY.deserialize(name)
                        .decoration(TextDecoration.ITALIC, false));
            }
            List<String> lore = config.getStringList(path + ".lore");
            if (!lore.isEmpty()) {
                List<Component> compLore = new ArrayList<>(lore.size());
                for (String line : lore) {
                    compLore.add(LEGACY.deserialize(line.replace("%price%", String.valueOf(price)))
                            .decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(compLore);
            }
            applyHideFlags(meta);
            item.setItemMeta(meta);
        }
        inv.setItem(slot, item);
    }

    private static void play(Player player, Sound sound, float volume, float pitch) {
        player.playSound(player.getLocation(), sound, volume, pitch);
    }
}