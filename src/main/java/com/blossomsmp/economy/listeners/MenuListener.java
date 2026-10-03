package com.blossomsmp.economy.listeners;

import com.blossomsmp.economy.BlossomEconomy;
import com.blossomsmp.economy.ShopManager;
import com.blossomsmp.economy.menus.AuctionMenus;
import com.blossomsmp.economy.menus.MenuHolder;
import com.blossomsmp.economy.menus.Menus;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.UUID;

public class MenuListener implements Listener {

    private final BlossomEconomy plugin;

    public MenuListener(BlossomEconomy plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder holder)) {
            return;
        }
        if (holder.getType() == MenuHolder.Type.SELL) {
            return; // players can freely move items in the sell menu
        }

        // Every other menu: nothing can be taken or moved
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) {
            return;
        }
        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT
                && click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) {
            return;
        }
        String action = holder.getSlotActions().get(slot);

        switch (holder.getType()) {
            case SHOP_MAIN -> {
                if (action != null) {
                    Menus.openCategory(plugin, player, action);
                }
            }
            case SHOP_CATEGORY -> handleCategoryClick(player, top, holder, slot, action, click);
            case AH_BROWSE -> handleBrowseClick(player, action);
            case AH_CONFIRM -> {
                if ("confirm".equals(action)) {
                    UUID id = parseUuid(holder.getData());
                    if (id != null) {
                        AuctionMenus.buy(plugin, player, id);
                    }
                } else if ("back".equals(action)) {
                    AuctionMenus.openBrowse(plugin, player, 0);
                }
            }
            case AH_MINE -> {
                if ("back".equals(action)) {
                    AuctionMenus.openBrowse(plugin, player, 0);
                } else if (action != null && action.startsWith("cancel:")) {
                    UUID id = parseUuid(action.substring(7));
                    if (id != null) {
                        AuctionMenus.cancel(plugin, player, id);
                    }
                }
            }
            case AH_COLLECT -> {
                if ("back".equals(action)) {
                    AuctionMenus.openBrowse(plugin, player, 0);
                } else if (action != null && action.startsWith("take:")) {
                    try {
                        AuctionMenus.take(plugin, player, Integer.parseInt(action.substring(5)));
                    } catch (NumberFormatException ignored) {
                        // not a valid slot action
                    }
                }
            }
            default -> {
            }
        }
    }

    private void handleCategoryClick(Player player, Inventory top, MenuHolder holder, int slot,
                                     String action, ClickType click) {
        if (Menus.ACTION_BACK.equals(action)) {
            Menus.openShop(plugin, player);
            return;
        }
        ShopManager.Category category = plugin.getShop().getCategory(holder.getData());
        if (category == null) {
            player.closeInventory();
            return;
        }
        List<Material> items = category.items();
        if (slot >= items.size() || slot >= ShopManager.MAX_ITEMS_PER_CATEGORY) {
            return;
        }
        int amount = click.isShiftClick() ? 64 : click.isRightClick() ? 16 : 1;
        Menus.buy(plugin, player, items.get(slot), amount);
        Menus.renderCategory(plugin, player, top, holder, category);
    }

    private void handleBrowseClick(Player player, String action) {
        if (action == null) {
            return;
        }
        if (action.startsWith("buy:")) {
            UUID id = parseUuid(action.substring(4));
            if (id != null) {
                AuctionMenus.openConfirm(plugin, player, id);
            }
        } else if (action.startsWith("page:")) {
            try {
                AuctionMenus.openBrowse(plugin, player, Integer.parseInt(action.substring(5)));
            } catch (NumberFormatException ignored) {
                // not a valid page
            }
        } else if (action.equals("mine")) {
            AuctionMenus.openMine(plugin, player);
        } else if (action.equals("collect")) {
            AuctionMenus.openCollect(plugin, player);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof MenuHolder holder && holder.getType() != MenuHolder.Type.SELL) {
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < top.getSize()) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        if (inv.getHolder() instanceof MenuHolder holder
                && holder.getType() == MenuHolder.Type.SELL
                && event.getPlayer() instanceof Player player) {
            Menus.sellMenuContents(plugin, player, inv);
        }
    }

    private static UUID parseUuid(String text) {
        try {
            return text == null ? null : UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
