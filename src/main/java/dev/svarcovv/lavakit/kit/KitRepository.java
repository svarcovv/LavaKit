package dev.svarcovv.lavakit.kit;

import dev.svarcovv.lavakit.LavaKit;
import dev.svarcovv.lavakit.storage.SerializationUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeSet;
import java.util.regex.Pattern;

public final class KitRepository {
    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,32}");
    private final LavaKit plugin;
    private final Path normalFolder;
    private final Path adminFolder;

    public KitRepository(LavaKit plugin) {
        this.plugin = plugin;
        this.normalFolder = plugin.getDataFolder().toPath().resolve("kits");
        this.adminFolder = plugin.getDataFolder().toPath().resolve("admin-kits");
    }

    public void initialize() throws IOException {
        Files.createDirectories(normalFolder);
        Files.createDirectories(adminFolder);
    }

    public static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public static boolean validName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    public boolean isNormal(String name) {
        return validName(name) && plugin.getConfig().isConfigurationSection("kits." + name);
    }

    public boolean isAdmin(String name) {
        return validName(name) && Files.isRegularFile(adminFolder.resolve(name + ".yml"));
    }

    public List<String> normalNames() {
        ConfigurationSection kits = plugin.getConfig().getConfigurationSection("kits");
        return kits == null ? List.of() : kits.getKeys(false).stream()
                .filter(KitRepository::validName).sorted().toList();
    }

    public List<String> adminNames() {
        try (var files = Files.list(adminFolder)) {
            return files.filter(Files::isRegularFile).map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".yml"))
                    .map(name -> name.substring(0, name.length() - 4))
                    .filter(KitRepository::validName).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public List<String> allNames() {
        TreeSet<String> names = new TreeSet<>(normalNames());
        names.addAll(adminNames());
        return List.copyOf(names);
    }

    public String readNormal(String name) {
        return read(normalFolder, name);
    }

    public String readAdmin(String name) {
        return read(adminFolder, name);
    }

    public ItemStack[] load(String name) {
        String encoded = isNormal(name) ? readNormal(name) : readAdmin(name);
        return SerializationUtils.itemStackArrayFromBase64(encoded);
    }

    public void saveNormal(String name, ItemStack[] items) throws IOException {
        if (!isNormal(name)) throw new IOException("Такой кит не зарегистрирован");
        write(normalFolder, name, items);
    }

    public void createAdmin(String name, ItemStack[] items) throws IOException {
        if (!validName(name)) throw new IOException("Недопустимое имя кита");
        if (isNormal(name) || isAdmin(name)) throw new IOException("Кит с таким именем уже существует");
        write(adminFolder, name, items);
    }

    public void saveAdmin(String name, ItemStack[] items, String expected) throws IOException {
        checkUnchanged(name, expected);
        write(adminFolder, name, items);
    }

    public void deleteAdmin(String name, String expected) throws IOException {
        checkUnchanged(name, expected);
        Files.delete(adminFolder.resolve(name + ".yml"));
    }

    private void checkUnchanged(String name, String expected) throws IOException {
        if (!isAdmin(name) || expected == null || !Objects.equals(expected, readAdmin(name))) {
            throw new IOException("Кит изменён или удалён. Откройте меню заново");
        }
    }

    private String read(Path folder, String name) {
        if (!validName(name)) return null;
        Path file = folder.resolve(name + ".yml");
        if (!Files.isRegularFile(file)) return null;
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file.toFile());
            return config.getString("items");
        } catch (Exception e) {
            return null;
        }
    }

    private void write(Path folder, String name, ItemStack[] items) throws IOException {
        if (!validName(name)) throw new IOException("Недопустимое имя кита");
        if (items == null || java.util.Arrays.stream(items)
                .noneMatch(item -> item != null && !item.getType().isAir() && item.getAmount() > 0)) {
            throw new IOException("Нельзя сохранить пустой кит");
        }
        String encoded = SerializationUtils.itemStackArrayToBase64(items);
        if (encoded.isEmpty()) throw new IOException("Не удалось сохранить данные предметов");
        YamlConfiguration config = new YamlConfiguration();
        config.set("items", encoded);
        Path temporary = Files.createTempFile(folder, name + "-", ".tmp");
        try {
            config.save(temporary.toFile());
            try {
                Files.move(temporary, folder.resolve(name + ".yml"),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, folder.resolve(name + ".yml"), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
