package com.blossomsmp.economy.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;

/** Inventory helpers. */
public final class Items {

    private Items() {
    }

    /** True if the inventory has room for `amount` items like `sample`. */
    public static boolean fits(PlayerInventory inventory, ItemStack sample, int amount) {
        int maxStack = Math.max(1, sample.getMaxStackSize());
        int space = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack == null || stack.getType().isAir()) {
                space += maxStack;
            } else if (stack.isSimilar(sample)) {
                space += Math.max(0, maxStack - stack.getAmount());
            }
            if (space >= amount) {
                return true;
            }
        }
        return space >= amount;
    }

    /** Gives items, splitting into stacks and dropping anything that doesn't fit. */
    public static void give(Player player, ItemStack item, int amount) {
        int maxStack = Math.max(1, item.getMaxStackSize());
        int left = amount;
        while (left > 0) {
            int size = Math.min(left, maxStack);
            ItemStack stack = item.clone();
            stack.setAmount(size);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            left -= size;
        }
    }

    /** Gives a single stack as-is, dropping it if it doesn't fit. */
    public static void give(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }
}
