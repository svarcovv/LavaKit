package dev.svarcovv.lavakit.kit;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class KitDeliveryTest {
    @Test
    void deliversMultipleCopiesAndKeepsSourcesUnchanged() {
        ItemStack source = stack(12, 64, "reward");
        ItemStack existing = stack(50, 64, "reward");
        ItemStack[] planned = KitUtils.planStorage(new ItemStack[]{existing, null}, 64,
                new ItemStack[]{source}, 3);
        assertNotNull(planned);
        assertEquals(64, planned[0].getAmount());
        assertEquals(22, planned[1].getAmount());
        assertEquals(12, source.getAmount());
        assertEquals(50, existing.getAmount());
        assertTrue(planned[1].isSimilar(source));
    }

    @Test
    void keepsDistinctMetadataInSeparateStacks() {
        ItemStack existing = stack(60, 64, "custom-a");
        ItemStack source = stack(4, 64, "custom-b");
        ItemStack[] planned = KitUtils.planStorage(new ItemStack[]{existing, null}, 64,
                new ItemStack[]{source}, 1);
        assertNotNull(planned);
        assertEquals(60, planned[0].getAmount());
        assertEquals(4, planned[1].getAmount());
        assertFalse(planned[0].isSimilar(source));
        assertTrue(planned[1].isSimilar(source));
    }

    @Test
    void honoursCustomStackLimitsAndUnstackableItems() {
        ItemStack source = stack(1, 1, "sword");
        ItemStack[] planned = KitUtils.planStorage(new ItemStack[3], 64, new ItemStack[]{source}, 3);
        assertNotNull(planned);
        for (ItemStack item : planned) assertEquals(1, item.getAmount());
        assertNull(KitUtils.planStorage(new ItemStack[2], 64, new ItemStack[]{source}, 3));
        assertNull(KitUtils.planStorage(new ItemStack[1], 16,
                new ItemStack[]{stack(17, 64, "reward")}, 1));
    }

    @Test
    void refusesWholeRewardWithoutChangingInventory() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        ItemStack existing = stack(63, 64, "reward");
        when(inventory.getStorageContents()).thenReturn(new ItemStack[]{existing});
        when(inventory.getMaxStackSize()).thenReturn(64);
        assertFalse(KitUtils.giveItems(player, new ItemStack[]{stack(2, 64, "reward")}, 1));
        verify(inventory, never()).setStorageContents(any(ItemStack[].class));
        assertEquals(63, existing.getAmount());
    }

    @Test
    void rejectsInvalidAndExtremeCountsWithoutOverflow() {
        ItemStack[] items = {stack(64, 64, "reward")};
        for (int copies : new int[]{0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertNull(KitUtils.planStorage(new ItemStack[36], 64, items, copies));
        }
        assertNull(KitUtils.planStorage(new ItemStack[36], 64, new ItemStack[]{null}, 1));
        assertNull(KitUtils.planStorage(new ItemStack[36], 64, null, 1));
    }

    private static ItemStack stack(int amount, int max, String identity) {
        ItemStack item = mock(ItemStack.class, withSettings().name(identity));
        AtomicInteger count = new AtomicInteger(amount);
        when(item.getType()).thenReturn(mock(Material.class));
        when(item.getAmount()).thenAnswer(call -> count.get());
        when(item.getMaxStackSize()).thenReturn(max);
        doAnswer(call -> { count.set(call.getArgument(0)); return null; }).when(item).setAmount(anyInt());
        when(item.clone()).thenAnswer(call -> stack(count.get(), max, identity));
        when(item.isSimilar(any())).thenAnswer(call -> {
            ItemStack other = call.getArgument(0);
            return other != null && mockingDetails(other).getMockCreationSettings().getMockName()
                    .toString().equals(identity);
        });
        return item;
    }
}
