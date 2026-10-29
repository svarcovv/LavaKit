package dev.svarcovv.lavakit.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

final class AdminKitMenuHolder implements InventoryHolder {
    enum Screen { LIST, VIEW, EDIT, DELETE }

    final Screen screen;
    final String kitName;
    final int page;
    final List<String> names;
    final String snapshot;
    private final Inventory inventory;

    AdminKitMenuHolder(Screen screen, String kitName, int page, List<String> names,
                       String snapshot, Component title) {
        this.screen = screen;
        this.kitName = kitName;
        this.page = page;
        this.names = List.copyOf(names);
        this.snapshot = snapshot;
        this.inventory = Bukkit.createInventory(this, 54, title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
