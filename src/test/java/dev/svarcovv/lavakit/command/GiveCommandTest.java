package dev.svarcovv.lavakit.command;

import dev.svarcovv.lavakit.LavaKit;
import dev.svarcovv.lavakit.kit.KitRepository;
import dev.svarcovv.lavakit.kit.KitUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class GiveCommandTest {
    @Test
    void issuesBothCommandFormatsSilently() {
        LavaKit plugin = mock(LavaKit.class);
        KitRepository kits = mock(KitRepository.class);
        CommandSender console = mock(CommandSender.class);
        Player player = mock(Player.class);
        ItemStack[] items = {mock(ItemStack.class)};
        when(console.hasPermission("lavakit.give")).thenReturn(true);
        when(plugin.getKitRepository()).thenReturn(kits);
        when(kits.isAdmin("don")).thenReturn(true);
        when(kits.load("don")).thenReturn(items);
        try (var singleton = mockStatic(LavaKit.class);
             var bukkit = mockStatic(Bukkit.class);
             var delivery = mockStatic(KitUtils.class)) {
            singleton.when(LavaKit::getInstance).thenReturn(plugin);
            bukkit.when(() -> Bukkit.getPlayerExact("Tester")).thenReturn(player);
            LavaKitAdminCommand executor = new LavaKitAdminCommand();
            assertTrue(executor.onCommand(console, mock(Command.class), "lavakit",
                    new String[]{"give", "DON", "Tester", "3"}));
            delivery.verify(() -> KitUtils.giveItems(player, items, 3));
            executor.onCommand(console, mock(Command.class), "lavakit",
                    new String[]{"kit", "don", "give", "Tester"});
            delivery.verify(() -> KitUtils.giveItems(player, items, 1));
            verify(console, times(2)).hasPermission("lavakit.give");
            verifyNoMoreInteractions(console);
            verifyNoInteractions(player);
        }
    }

    @Test
    void invalidCountNeverDeliversOrSendsFeedback() {
        LavaKit plugin = mock(LavaKit.class);
        KitRepository kits = mock(KitRepository.class);
        CommandSender console = mock(CommandSender.class);
        when(console.hasPermission("lavakit.give")).thenReturn(true);
        when(plugin.getKitRepository()).thenReturn(kits);
        when(kits.isAdmin("don")).thenReturn(true);
        try (var singleton = mockStatic(LavaKit.class); var delivery = mockStatic(KitUtils.class)) {
            singleton.when(LavaKit::getInstance).thenReturn(plugin);
            LavaKitAdminCommand executor = new LavaKitAdminCommand();
            for (String count : new String[]{"0", "-1", "2147483648", "1.5", "x"}) {
                executor.onCommand(console, mock(Command.class), "lavakit",
                        new String[]{"give", "don", "Tester", count});
            }
            delivery.verifyNoInteractions();
            verify(console, times(5)).hasPermission("lavakit.give");
            verifyNoMoreInteractions(console);
        }
    }
}
