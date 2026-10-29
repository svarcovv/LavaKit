package dev.svarcovv.lavakit.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class KitMenuHolder implements InventoryHolder {
    private final String kitName;
    private final Inventory inventory;

    public KitMenuHolder(String kitName, Component title) {
        this.kitName = kitName;
        this.inventory = Bukkit.createInventory(this, 54, title);
    }

    String kitName() { return kitName; }

    @Override
    public Inventory getInventory() { return inventory; }
}
