package dev.svarcovv.lavakit.kit;

import dev.svarcovv.lavakit.LavaKit;
import dev.svarcovv.lavakit.storage.SerializationUtils;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KitRepositoryTest {
    @TempDir Path folder;
    private KitRepository repository;

    @BeforeEach
    void setUp() {
        LavaKit plugin = mock(LavaKit.class);
        when(plugin.getDataFolder()).thenReturn(folder.toFile());
        YamlConfiguration config = new YamlConfiguration();
        config.set("kits.spirit.price", 100);
        when(plugin.getConfig()).thenReturn(config);
        repository = new KitRepository(plugin);
    }

    @Test
    void initializesNewFoldersWithoutChangingExistingData() throws IOException {
        Files.createDirectories(folder.resolve("kits"));
        Files.writeString(folder.resolve("kits/spirit.yml"), "items: legacy-data\n");
        Files.writeString(folder.resolve("config.yml"), "menu-title: old title\n");
        Files.writeString(folder.resolve("cooldowns.yml"), "cooldowns: {}\n");
        repository.initialize();
        repository.initialize();
        assertTrue(Files.isDirectory(folder.resolve("admin-kits")));
        assertEquals("legacy-data", repository.readNormal("spirit"));
        assertEquals("menu-title: old title\n", Files.readString(folder.resolve("config.yml")));
        assertEquals("cooldowns: {}\n", Files.readString(folder.resolve("cooldowns.yml")));
    }

    @Test
    void isolatesAdminKitsAndRejectsDuplicateNames() throws IOException {
        repository.initialize();
        repository.createAdmin("don", items());
        String encoded = repository.readAdmin("don");
        assertNotNull(encoded);
        assertNull(repository.readNormal("don"));
        assertEquals(java.util.List.of("don"), repository.adminNames());
        assertFalse(repository.isNormal("don"));
        assertThrows(IOException.class, () -> repository.createAdmin("don", items()));
        assertThrows(IOException.class, () -> repository.createAdmin("spirit", items()));
        assertEquals(encoded, repository.readAdmin("don"));
        try (var files = Files.list(folder.resolve("admin-kits"))) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void preventsStaleEditsDeletionAndEmptyReplacement() throws IOException {
        repository.initialize();
        repository.createAdmin("don", items());
        String snapshot = repository.readAdmin("don");
        assertThrows(IOException.class, () -> repository.saveAdmin("don", new ItemStack[0], snapshot));
        assertEquals(snapshot, repository.readAdmin("don"));
        assertThrows(IOException.class, () -> repository.saveAdmin("don", items(), "stale"));
        assertThrows(IOException.class, () -> repository.deleteAdmin("don", "stale"));
        repository.deleteAdmin("don", snapshot);
        assertFalse(repository.isAdmin("don"));
        assertThrows(IOException.class, () -> repository.saveAdmin("don", items(), snapshot));
    }

    @Test
    void rejectsPathTraversalAndCorruptFiles() throws IOException {
        repository.initialize();
        assertThrows(IOException.class, () -> repository.createAdmin("../outside", items()));
        assertNull(repository.readNormal("../outside"));
        assertNull(repository.readAdmin("../outside"));
        assertNull(SerializationUtils.itemStackArrayFromBase64("corrupt"));
        Files.writeString(folder.resolve("admin-kits/broken.yml"), "items: [broken yaml");
        assertNull(repository.readAdmin("broken"));
    }

    private static ItemStack[] items() {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(mock(Material.class));
        when(item.getAmount()).thenReturn(1);
        when(item.serializeAsBytes()).thenReturn(new byte[]{1, 2, 3});
        return new ItemStack[]{item};
    }
}
