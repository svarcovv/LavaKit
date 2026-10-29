package dev.svarcovv.lavakit.menu;

import dev.svarcovv.lavakit.LavaKit;
import dev.svarcovv.lavakit.kit.KitRepository;
import dev.svarcovv.lavakit.storage.SerializationUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static dev.svarcovv.lavakit.menu.AdminKitMenuHolder.Screen;

public final class AdminKitMenu implements Listener {
    private static final int CONTENT_SIZE = 45;
    private final LavaKit plugin;
    private final KitRepository kits;

    public AdminKitMenu(LavaKit plugin, KitRepository kits) {
        this.plugin = plugin;
        this.kits = kits;
    }

    public boolean isMenu(Inventory inventory) {
        return inventory.getHolder() instanceof AdminKitMenuHolder;
    }

    public void openList(Player player, int requestedPage) {
        if (!player.hasPermission("lavakit.admin")) return;
        List<String> names = kits.adminNames();
        int page = Math.clamp(requestedPage, 0, Math.max(0, (names.size() - 1) / CONTENT_SIZE));
        AdminKitMenuHolder holder = new AdminKitMenuHolder(Screen.LIST, null, page, names, null,
                Component.text("Админские киты | " + (page + 1)));
        Inventory menu = holder.getInventory();
        int offset = page * CONTENT_SIZE;
        for (int slot = 0; slot < CONTENT_SIZE && offset + slot < names.size(); slot++) {
            String name = names.get(offset + slot);
            menu.setItem(slot, button(Material.CHEST, name,
                    "ЛКМ - просмотр", "ПКМ - редактирование"));
        }
        footer(menu);
        if (page > 0) menu.setItem(45, button(Material.ARROW, "Предыдущая страница"));
        menu.setItem(49, button(Material.BOOK, "Создание кита",
                "/lavakit admin create <название>", "Сохраняет содержимое вашего инвентаря"));
        if (offset + CONTENT_SIZE < names.size()) {
            menu.setItem(53, button(Material.ARROW, "Следующая страница"));
        }
        player.openInventory(menu);
    }

    public void openKit(Player player, String name, boolean edit) {
        openKit(player, name, edit, 0);
    }

    private void openKit(Player player, String name, boolean edit, int page) {
        if (!player.hasPermission("lavakit.admin")) return;
        String snapshot = kits.readAdmin(name);
        ItemStack[] items = SerializationUtils.itemStackArrayFromBase64(snapshot);
        if (items == null || items.length > CONTENT_SIZE) {
            message(player, "Не удалось открыть содержимое кита");
            return;
        }
        AdminKitMenuHolder holder = new AdminKitMenuHolder(edit ? Screen.EDIT : Screen.VIEW,
                name, page, List.of(), snapshot,
                Component.text((edit ? "Редактирование: " : "Просмотр: ") + name));
        Inventory menu = holder.getInventory();
        for (int slot = 0; slot < items.length; slot++) {
            if (items[slot] != null) menu.setItem(slot, items[slot].clone());
        }
        footer(menu);
        menu.setItem(45, button(Material.ARROW, edit ? "Отмена" : "К списку китов"));
        if (edit) {
            menu.setItem(47, button(Material.BOOK, "Редактирование",
                    "Нажмите на предмет в своём инвентаре, чтобы добавить копию",
                    "Нажмите на предмет в ките, чтобы убрать его",
                    "Изменения применяются кнопкой Сохранить"));
            menu.setItem(49, button(Material.LIME_DYE, "Сохранить"));
            menu.setItem(53, button(Material.BARRIER, "Очистить содержимое"));
        } else {
            menu.setItem(49, button(Material.WRITABLE_BOOK, "Редактировать"));
            menu.setItem(53, button(Material.RED_DYE, "Удалить кит"));
        }
        player.openInventory(menu);
    }

    public void openDelete(Player player, String name) {
        openDelete(player, name, 0);
    }

    private void openDelete(Player player, String name, int page) {
        if (!player.hasPermission("lavakit.admin")) return;
        String snapshot = kits.readAdmin(name);
        if (snapshot == null) {
            message(player, "Кит не найден или файл повреждён");
            return;
        }
        AdminKitMenuHolder holder = new AdminKitMenuHolder(Screen.DELETE, name, page,
                List.of(), snapshot, Component.text("Удалить кит: " + name + "?"));
        Inventory menu = holder.getInventory();
        footer(menu);
        menu.setItem(22, button(Material.CHEST, name, "Удаление нельзя отменить"));
        menu.setItem(47, button(Material.LIME_DYE, "Подтвердить удаление"));
        menu.setItem(51, button(Material.ARROW, "Отмена"));
        player.openInventory(menu);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory menu = event.getView().getTopInventory();
        if (!(menu.getHolder() instanceof AdminKitMenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.hasPermission("lavakit.admin")) return;
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        int rawSlot = event.getRawSlot();
        boolean ownInventory = event.getClickedInventory() == player.getInventory();
        ItemStack clicked = event.getCurrentItem();
        ItemStack copy = clicked == null ? null : clicked.clone();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !player.hasPermission("lavakit.admin")
                    || player.getOpenInventory().getTopInventory() != menu) return;
            handleClick(player, holder, rawSlot, ownInventory, copy, event.getClick() == ClickType.RIGHT);
        });
    }

    private void handleClick(Player player, AdminKitMenuHolder holder, int slot,
                             boolean ownInventory, ItemStack copy, boolean right) {
        Inventory menu = holder.getInventory();
        if (holder.screen == Screen.EDIT && ownInventory) {
            if (copy == null || copy.getType().isAir()) return;
            for (int i = 0; i < CONTENT_SIZE; i++) {
                if (menu.getItem(i) == null || menu.getItem(i).getType().isAir()) {
                    menu.setItem(i, copy);
                    return;
                }
            }
            message(player, "В ките нет свободных слотов");
            return;
        }
        if (slot < 0 || slot >= menu.getSize()) return;
        if (holder.screen == Screen.LIST) {
            int index = holder.page * CONTENT_SIZE + slot;
            if (slot < CONTENT_SIZE && index < holder.names.size()) {
                openKit(player, holder.names.get(index), right, holder.page);
            } else if (slot == 45) {
                openList(player, holder.page - 1);
            } else if (slot == 53) {
                openList(player, holder.page + 1);
            }
        } else if (holder.screen == Screen.VIEW) {
            if (slot == 45) openList(player, holder.page);
            if (slot == 49) openKit(player, holder.kitName, true, holder.page);
            if (slot == 53) openDelete(player, holder.kitName, holder.page);
        } else if (holder.screen == Screen.EDIT) {
            if (slot < CONTENT_SIZE) menu.setItem(slot, null);
            if (slot == 45) openKit(player, holder.kitName, false, holder.page);
            if (slot == 53) {
                for (int i = 0; i < CONTENT_SIZE; i++) menu.setItem(i, null);
            }
            if (slot == 49) {
                try {
                    kits.saveAdmin(holder.kitName, Arrays.copyOf(menu.getContents(), CONTENT_SIZE), holder.snapshot);
                    openKit(player, holder.kitName, false, holder.page);
                    message(player, "Кит сохранён");
                } catch (IOException e) {
                    message(player, e.getMessage());
                }
            }
        } else if (holder.screen == Screen.DELETE) {
            if (slot == 51) openList(player, holder.page);
            if (slot == 47) {
                try {
                    kits.deleteAdmin(holder.kitName, holder.snapshot);
                    openList(player, holder.page);
                    message(player, "Кит удалён");
                } catch (IOException e) {
                    message(player, e.getMessage());
                }
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (isMenu(event.getView().getTopInventory())) event.setCancelled(true);
    }

    private static void footer(Inventory menu) {
        for (int slot = CONTENT_SIZE; slot < menu.getSize(); slot++) {
            menu.setItem(slot, button(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
    }

    private static ItemStack button(Material material, String title, String... lines) {
        ItemStack button = new ItemStack(material);
        button.editMeta(meta -> {
            meta.displayName(Component.text(title, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (String line : lines) {
                lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
        });
        return button;
    }

    private static void message(Player player, String text) {
        player.sendMessage(Component.text(text, NamedTextColor.GOLD));
    }
}
