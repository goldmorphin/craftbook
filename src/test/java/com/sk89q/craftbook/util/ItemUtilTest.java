package com.sk89q.craftbook.util;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(ItemUtil.class)
public class ItemUtilTest {

    @Test
    public void testAddToStack() {

        ItemStack mockStack = newMockItemStack(Material.SAND,(byte) 0, 38);
        ItemStack mockStack2 = newMockItemStack(Material.SAND, (byte) 0, 10);

        when(mockStack.getMaxStackSize()).thenReturn(48);

        assertTrue(ItemUtil.addToStack(mockStack, mockStack2) == null);

        verify(mockStack).setAmount(48);

        when(mockStack2.getAmount()).thenReturn(11);

        ItemUtil.addToStack(mockStack, mockStack2);

        verify(mockStack, times(2)).setAmount(48);
        verify(mockStack2).setAmount(1);
    }

    @Test
    public void testFilterItems() {

        ArrayList<ItemStack> items = new ArrayList<ItemStack>();
        items.add(newMockItemStack(Material.GRASS,(byte) 0,1));
        items.add(newMockItemStack(Material.GRAVEL,(byte) 0,1));
        items.add(newMockItemStack(Material.STONE,(byte) 0,1));
        HashSet<ItemStack> inclusions = new HashSet<ItemStack>();
        inclusions.add(newMockItemStack(Material.GRASS,(byte) 0,1));
        List<ItemStack> filtered = ItemUtil.filterItems(items, inclusions, null);
        assertTrue(filtered.size() == 1);
        HashSet<ItemStack> exclusions = new HashSet<ItemStack>();
        exclusions.add(newMockItemStack(Material.GRAVEL,(byte) 0,1));
        filtered = ItemUtil.filterItems(items, null, exclusions);
        assertTrue(filtered.size() == 2);
    }

    @Test
    public void testAreItemsSimilar() {

        ItemStack test1 = newMockItemStack(Material.WOOD,(byte) 4,1);
        ItemStack test2 = newMockItemStack(Material.WOOD,(byte) 8,1);
        assertTrue(ItemUtil.areItemsSimilar(test1, test2));
        test2 = newMockItemStack(Material.GRASS,(byte) 8,1);
        assertTrue(!ItemUtil.areItemsSimilar(test1, test2));
    }

    @Test
    public void testAreItemsIdentical() {

        ItemStack test1 = newMockItemStack(Material.WOOD,(byte) 4,1);
        ItemStack test2 = newMockItemStack(Material.WOOD,(byte) 8,1);
        assertTrue(!ItemUtil.areItemsIdentical(test1, test2));
        test2 = newMockItemStack(Material.GRASS,(byte) 8,1);
        assertTrue(!ItemUtil.areItemsIdentical(test1, test2));
        test2 = newMockItemStack(Material.WOOD,(byte) 4,1);
        assertTrue(ItemUtil.areItemsIdentical(test1, test2));
    }

    @Test
    public void testIsStackValid() {

        ItemStack mockStack = newMockItemStack(Material.AIR,(byte) 0,5);
        assertTrue(!ItemUtil.isStackValid(mockStack));
        when(mockStack.getTypeId()).thenReturn(20);
        when(mockStack.getAmount()).thenReturn(5);
        assertTrue(ItemUtil.isStackValid(mockStack));
        when(mockStack.getAmount()).thenReturn(-60);
        assertTrue(!ItemUtil.isStackValid(mockStack));
        assertTrue(!ItemUtil.isStackValid(null));
    }

    @Test
    public void testTakeFromEntity() {

        Item entity = mock(Item.class);
        when(entity.isDead()).thenReturn(true);
        assertTrue(!ItemUtil.takeFromItemEntity(null, 1));
        assertTrue(!ItemUtil.takeFromItemEntity(entity, 1));
        when(entity.isDead()).thenReturn(false);
        when(entity.getItemStack()).thenReturn(ItemSyntax.getItem("2:0*20"));
        assertTrue(!ItemUtil.takeFromItemEntity(entity, 21));
        assertTrue(ItemUtil.takeFromItemEntity(entity, 2));
        assertTrue(ItemUtil.takeFromItemEntity(entity, 18));
        verify(entity).remove();
    }

    @Test
    public void testIsCookable() {

        ItemStack ingredient = newMockItemStack(Material.RAW_CHICKEN, (byte) 0, 1);
        assertTrue(ItemUtil.isCookable(ingredient));
    }

    @Test
    public void testIsSmeltable() {

        ItemStack ingredient = newMockItemStack(Material.CLAY, (byte) 0, 1);
        assertTrue(ItemUtil.isSmeltable(ingredient));
    }

    @Test
    public void testGetItem() {

        ItemStack ret1 = ItemSyntax.getItem("2");
        assertTrue(ret1.getTypeId() == 2);
        assertTrue(ret1.getData().getData() == -1);
        assertTrue(ret1.getAmount() == 1);
        ret1 = ItemSyntax.getItem("2:5");
        assertTrue(ret1.getTypeId() == 2);
        assertTrue(ret1.getData().getData() == 5);
        assertTrue(ret1.getAmount() == 1);
        ret1 = ItemSyntax.getItem("2:5*4");
        assertTrue(ret1.getTypeId() == 2);
        assertTrue(ret1.getData().getData() == 5);
        assertTrue(ret1.getAmount() == 4);
        ret1 = ItemSyntax.getItem("2*4");
        assertTrue(ret1.getTypeId() == 2);
        assertTrue(ret1.getData().getData() == -1);
        assertTrue(ret1.getAmount() == 4);
    }

    public ItemStack newMockItemStack(Material id, byte data, int amount) {

        ItemStack mockStack = mock(ItemStack.class);
        when(mockStack.getAmount()).thenReturn(amount);
        when(mockStack.getType()).thenReturn(id);
        when(mockStack.getTypeId()).thenReturn(id.getId());
        when(mockStack.getData()).thenReturn(new MaterialData(id, data));
        when(mockStack.getDurability()).thenReturn((short) data);

        return mockStack;
    }
}