package dev.svarcovv.lavakit.diagnostic;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;

public final class ItemDiagnostics {
    public static void compare(CommandSender sender, JavaPlugin plugin) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Команда доступна только игрокам."));
            return;
        }
        ItemStack left = player.getInventory().getItemInMainHand().clone();
        ItemStack right = player.getInventory().getItemInOffHand().clone();
        if (left.getType().isAir() || right.getType().isAir()) {
            player.sendMessage(Component.text("§6Возьми сравниваемые предметы в основную и вторую руку."));
            return;
        }
        boolean similar = left.isSimilar(right);
        player.sendMessage(Component.text(similar
                ? "§aМатериал и метаданные совпадают. Количество при сравнении не учитывается."
                : "§eПредметы отличаются материалом или скрытыми данными."));
        try {
            YamlConfiguration dump = new YamlConfiguration();
            dump.set("similar-ignoring-amount", similar);
            writeItem(dump, "main-hand", left);
            writeItem(dump, "off-hand", right);
            Path folder = plugin.getDataFolder().toPath().resolve("diagnostics");
            Files.createDirectories(folder);
            Path target = Files.createTempFile(folder, "compare-", ".yml");
            Files.writeString(target, dump.saveToString(), StandardCharsets.UTF_8);
            player.sendMessage(Component.text("§7Сравнение сохранено: plugins/" + plugin.getName() + "/diagnostics/" + target.getFileName()));
        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось сохранить сравнение: " + e.getMessage());
            player.sendMessage(Component.text("§cНе удалось сохранить файл сравнения."));
        }
    }
    private static void writeItem(YamlConfiguration dump, String path, ItemStack item) {
        dump.set(path + ".material", item.getType().name());
        dump.set(path + ".amount", item.getAmount());
        dump.set(path + ".components", item.getItemMeta().getAsComponentString());
        dump.set(path + ".serialized", Base64.getEncoder().encodeToString(item.serializeAsBytes()));
    }
}
