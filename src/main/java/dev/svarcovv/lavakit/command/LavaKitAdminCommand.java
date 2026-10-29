package dev.svarcovv.lavakit.command;

import dev.svarcovv.lavakit.LavaKit;
import dev.svarcovv.lavakit.diagnostic.ItemDiagnostics;
import dev.svarcovv.lavakit.kit.KitRepository;
import dev.svarcovv.lavakit.kit.KitUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class LavaKitAdminCommand implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean give = args.length > 0 && args[0].equalsIgnoreCase("give");
        boolean legacyGive = args.length >= 3 && args[0].equalsIgnoreCase("kit")
                && args[2].equalsIgnoreCase("give");
        if (give || legacyGive) {
            if (sender.hasPermission("lavakit.give") || sender.hasPermission("lavakit.admin")) {
                handleGive(args, legacyGive);
            }
            return true;
        }
        if (!sender.hasPermission("lavakit.admin")) {
            message(sender, "У вас нет прав на использование этой команды");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            LavaKit.getInstance().reloadPluginConfig();
            message(sender, "Конфигурация перезагружена");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("compare")) {
            ItemDiagnostics.compare(sender, LavaKit.getInstance());
            return true;
        }
        if (!(sender instanceof Player player)) {
            message(sender, "Использование: /lavakit give <кит> <игрок> <количество> или /lavakit reload");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("admin")) {
            handleAdmin(player, args);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            String name = KitRepository.normalize(args[1]);
            try {
                kits().saveNormal(name, inventoryItems(player));
                message(player, "Содержимое инвентаря сохранено в кит " + name);
            } catch (IOException e) {
                message(player, e.getMessage());
            }
        } else {
            message(player, "Команды: /lavakit admin, /lavakit add <кит>, /lavakit give <кит> <игрок> <количество>");
        }
        return true;
    }

    private void handleGive(String[] args, boolean legacy) {
        int playerIndex = legacy ? 3 : 2;
        if (args.length != playerIndex + 1 && args.length != playerIndex + 2) return;
        String name = KitRepository.normalize(args[1]);
        if (!KitRepository.validName(name) || (!kits().isNormal(name) && !kits().isAdmin(name))) return;
        int copies = 1;
        if (args.length == playerIndex + 2) {
            try {
                copies = Integer.parseInt(args[playerIndex + 1]);
            } catch (NumberFormatException e) {
                return;
            }
        }
        if (copies < 1) return;
        Player target = Bukkit.getPlayerExact(args[playerIndex]);
        if (target == null) return;
        ItemStack[] items = kits().load(name);
        if (items != null) KitUtils.giveItems(target, items, copies);
    }

    private void handleAdmin(Player player, String[] args) {
        if (args.length <= 1 || (args.length == 2 && args[1].equalsIgnoreCase("list"))) {
            LavaKit.getInstance().getAdminKitMenu().openList(player, 0);
            return;
        }
        if (args.length != 3) {
            message(player, "/lavakit admin create|view|edit|delete <название>");
            return;
        }
        String name = KitRepository.normalize(args[2]);
        if (!KitRepository.validName(name)) {
            message(player, "Название: от 1 до 32 латинских букв, цифр, знаков _ или -");
            return;
        }
        if (args[1].equalsIgnoreCase("create")) {
            try {
                kits().createAdmin(name, inventoryItems(player));
                LavaKit.getInstance().getAdminKitMenu().openKit(player, name, false);
                message(player, "Админский кит " + name + " создан");
            } catch (IOException e) {
                message(player, e.getMessage());
            }
            return;
        }
        if (!kits().isAdmin(name)) {
            message(player, "Админский кит не найден");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "view" -> LavaKit.getInstance().getAdminKitMenu().openKit(player, name, false);
            case "edit" -> LavaKit.getInstance().getAdminKitMenu().openKit(player, name, true);
            case "delete" -> LavaKit.getInstance().getAdminKitMenu().openDelete(player, name);
            default -> message(player, "/lavakit admin create|view|edit|delete <название>");
        }
    }

    private static ItemStack[] inventoryItems(Player player) {
        return Arrays.stream(player.getInventory().getStorageContents())
                .filter(item -> item != null && !item.getType().isAir() && item.getAmount() > 0)
                .map(ItemStack::clone).toArray(ItemStack[]::new);
    }

    private static KitRepository kits() {
        return LavaKit.getInstance().getKitRepository();
    }

    private static void message(CommandSender sender, String text) {
        sender.sendMessage(Component.text(text, NamedTextColor.GOLD));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission("lavakit.admin");
        boolean canGive = admin || sender.hasPermission("lavakit.give");
        if (!canGive) return List.of();
        List<String> options = List.of();
        if (args.length == 1) {
            options = admin ? List.of("add", "admin", "compare", "give", "kit", "reload") : List.of("give");
        } else if (args[0].equalsIgnoreCase("give")) {
            options = switch (args.length) {
                case 2 -> kits().allNames();
                case 3 -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                case 4 -> List.of("1", "2", "3");
                default -> List.of();
            };
        } else if (admin && args[0].equalsIgnoreCase("kit")) {
            options = switch (args.length) {
                case 2 -> kits().allNames();
                case 3 -> List.of("give");
                case 4 -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                case 5 -> List.of("1", "2", "3");
                default -> List.of();
            };
        } else if (admin && args[0].equalsIgnoreCase("add") && args.length == 2) {
            options = kits().normalNames();
        } else if (admin && args[0].equalsIgnoreCase("admin")) {
            if (args.length == 2) options = List.of("create", "list", "view", "edit", "delete");
            if (args.length == 3 && List.of("view", "edit", "delete").contains(args[1].toLowerCase(Locale.ROOT))) {
                options = kits().adminNames();
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
