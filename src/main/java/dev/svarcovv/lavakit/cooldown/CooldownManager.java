package dev.svarcovv.lavakit.cooldown;

import dev.svarcovv.lavakit.LavaKit;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {

    private final LavaKit plugin;
    private final File file;
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    public CooldownManager(LavaKit plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "cooldowns.yml");
        load();
    }

    public long getRemainingMs(UUID uuid, String kit) {
        Map<String, Long> playerCooldowns = cooldowns.get(uuid);
        if (playerCooldowns == null) return 0L;
        Long expiry = playerCooldowns.get(kit);
        if (expiry == null) return 0L;
        long remaining = expiry - System.currentTimeMillis();
        return remaining > 0 ? remaining : 0L;
    }

    public void set(UUID uuid, String kit, int seconds) {
        if (seconds <= 0) return;
        long expiry = System.currentTimeMillis() + seconds * 1000L;
        cooldowns.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(kit, expiry);
    }

    public void load() {
        cooldowns.clear();
        if (!file.exists()) return;

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yml.getConfigurationSection("cooldowns");
        if (players == null) return;

        long now = System.currentTimeMillis();
        for (String uuidStr : players.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ConfigurationSection kits = players.getConfigurationSection(uuidStr);
            if (kits == null) continue;

            Map<String, Long> map = new ConcurrentHashMap<>();
            for (String kit : kits.getKeys(false)) {
                long expiry = kits.getLong(kit, 0L);
                if (expiry > now) {
                    map.put(kit, expiry);
                }
            }
            if (!map.isEmpty()) {
                cooldowns.put(uuid, map);
            }
        }
    }

    public synchronized void save() {
        long now = System.currentTimeMillis();
        YamlConfiguration yml = new YamlConfiguration();

        cooldowns.entrySet().removeIf(entry -> {
            entry.getValue().entrySet().removeIf(kit -> kit.getValue() <= now);
            return entry.getValue().isEmpty();
        });

        for (Map.Entry<UUID, Map<String, Long>> entry : cooldowns.entrySet()) {
            String uuidStr = entry.getKey().toString();
            for (Map.Entry<String, Long> kitEntry : entry.getValue().entrySet()) {
                yml.set("cooldowns." + uuidStr + "." + kitEntry.getKey(), kitEntry.getValue());
            }
        }

        try {
            File folder = plugin.getDataFolder();
            if (!folder.exists() && !folder.mkdirs()) {
                plugin.getLogger().warning("Не удалось создать папку плагина для cooldowns.yml");
                return;
            }
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить cooldowns.yml: " + e.getMessage());
        }
    }
}