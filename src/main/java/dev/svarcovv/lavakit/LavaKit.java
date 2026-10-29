package dev.svarcovv.lavakit;

import dev.svarcovv.lavakit.command.KitCommand;
import dev.svarcovv.lavakit.command.LavaKitAdminCommand;
import dev.svarcovv.lavakit.menu.KitMenuListener;
import dev.svarcovv.lavakit.menu.AdminKitMenu;
import dev.svarcovv.lavakit.menu.KitMenuHolder;
import dev.svarcovv.lavakit.kit.KitRepository;
import dev.svarcovv.lavakit.cooldown.CooldownManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class LavaKit extends JavaPlugin {

    private static LavaKit instance;

    private static final Component DEFAULT_TITLE =
            Component.text("Меню китов | LavaKit");

    private static final long COOLDOWN_AUTOSAVE_INTERVAL_TICKS = 20L * 60L * 5L;

    private PlayerPointsAPI pointsAPI;
    private Component menuTitle = DEFAULT_TITLE;
    private CooldownManager cooldownManager;
    private KitRepository kitRepository;
    private AdminKitMenu adminKitMenu;
    private BukkitTask cooldownAutosaveTask;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        this.kitRepository = new KitRepository(this);
        try {
            kitRepository.initialize();
        } catch (java.io.IOException e) {
            getLogger().severe("Не удалось создать папки китов: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.adminKitMenu = new AdminKitMenu(this, kitRepository);
        loadMenuTitle();
        setupPlayerPoints();
        this.cooldownManager = new CooldownManager(this);
        registerCommands();
        getServer().getPluginManager().registerEvents(new KitMenuListener(), this);
        getServer().getPluginManager().registerEvents(adminKitMenu, this);
        scheduleCooldownAutosave();
    }

    @Override
    public void onDisable() {
        for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
            org.bukkit.inventory.Inventory menu = player.getOpenInventory().getTopInventory();
            if (menu.getHolder() instanceof KitMenuHolder || (adminKitMenu != null && adminKitMenu.isMenu(menu))) {
                player.closeInventory();
            }
        }
        if (cooldownAutosaveTask != null) {
            cooldownAutosaveTask.cancel();
            cooldownAutosaveTask = null;
        }
        if (cooldownManager != null) {
            cooldownManager.save();
        }
    }

    public void loadMenuTitle() {
        String titleStr = getConfig().getString("menu-title", "&#89001BМеню китов | LavaKit");
        this.menuTitle = parseComponent(titleStr);
    }

    private Component parseComponent(String raw) {
        if (raw == null || raw.isEmpty()) {
            return DEFAULT_TITLE;
        }
        try {
            if (raw.indexOf('§') >= 0) {
                return LegacyComponentSerializer.builder()
                        .character('§')
                        .hexColors()
                        .useUnusualXRepeatedCharacterHexFormat()
                        .build()
                        .deserialize(raw);
            }
            if (raw.indexOf('&') >= 0) {
                return LegacyComponentSerializer.builder()
                        .character('&')
                        .hexColors()
                        .build()
                        .deserialize(raw);
            }
            return MiniMessage.miniMessage().deserialize(raw);
        } catch (Exception e) {
            getLogger().warning("Не удалось распарсить строку '" + raw + "': " + e.getMessage());
            return DEFAULT_TITLE;
        }
    }

    public void reloadPluginConfig() {
        reloadConfig();
        loadMenuTitle();
    }

    private void setupPlayerPoints() {
        if (getServer().getPluginManager().isPluginEnabled("PlayerPoints")) {
            try {
                this.pointsAPI = PlayerPoints.getInstance().getAPI();
            } catch (Throwable e) {
                getLogger().warning("Не удалось подключить PlayerPoints API: " + e.getMessage());
            }
        }
    }

    private void scheduleCooldownAutosave() {
        cooldownAutosaveTask = getServer().getScheduler().runTaskTimerAsynchronously(
                this,
                () -> {
                    if (cooldownManager != null) {
                        cooldownManager.save();
                    }
                },
                COOLDOWN_AUTOSAVE_INTERVAL_TICKS,
                COOLDOWN_AUTOSAVE_INTERVAL_TICKS);
    }

    private void registerCommands() {
        PluginCommand kitCmd = getCommand("kit");
        if (kitCmd != null) {
            kitCmd.setExecutor(new KitCommand());
        } else {
            getLogger().severe("Команда /kit не найдена в plugin.yml!");
        }

        PluginCommand adminCmd = getCommand("lavakit");
        if (adminCmd != null) {
            LavaKitAdminCommand exe = new LavaKitAdminCommand();
            adminCmd.setExecutor(exe);
            adminCmd.setTabCompleter(exe);
        } else {
            getLogger().severe("Команда /lavakit не найдена в plugin.yml!");
        }
    }

    public static LavaKit getInstance() {
        return instance;
    }

    public PlayerPointsAPI getPointsAPI() {
        return pointsAPI;
    }

    public Component getMenuTitle() {
        return menuTitle;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public KitRepository getKitRepository() {
        return kitRepository;
    }

    public AdminKitMenu getAdminKitMenu() {
        return adminKitMenu;
    }
}
