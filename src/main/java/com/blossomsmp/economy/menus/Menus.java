package com.blossomsmp.economy.menus;

import com.blossomsmp.economy.BlossomEconomy;
import com.blossomsmp.economy.MarketManager;
import com.blossomsmp.economy.ShopManager;
import com.blossomsmp.economy.util.Items;
import com.blossomsmp.economy.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Shop and sell menus, plus the buying and selling logic. */
public final class Menus {

    public static final String ACTION_BACK = "back";
    public static final int CATEGORY_BACK_SLOT = 49;
    public static final int CATEGORY_BALANCE_SLOT = 53;
    private static final int MAIN_BALANCE_SLOT = 13;

    private Menus() {
    }

    // ------------------------------------------------------------------
    // Shop
    // ------------------------------------------------------------------

    public static void openShop(BlossomEconomy plugin, Player player) {
        MenuHolder holder = new MenuHolder(MenuHolder.Type.SHOP_MAIN, null);
        Inventory inv = Bukkit.createInventory(holder, 27,
                Text.color(plugin.getConfig().getString("shop.title", "&#FF69B4&l❀ Blossom Shop")));
        holder.setInventory(inv);
        fill(plugin, inv, 0, 27);

        int nextFreeSlot = 10;
        for (ShopManager.Category category : plugin.getShop().getCategories()) {
            int slot = category.slot();
            if (slot < 0 || slot >= 27 || slot == MAIN_BALANCE_SLOT || holder.getSlotActions().containsKey(slot)) {
                while (nextFreeSlot < 27 && (nextFreeSlot == MAIN_BALANCE_SLOT
                        || holder.getSlotActions().containsKey(nextFreeSlot))) {
                    nextFreeSlot++;
                }
                if (nextFreeSlot >= 27) {
                    break;
                }
                slot = nextFreeSlot;
            }
            inv.setItem(slot, item(category.icon(), category.name(), List.of(
                    "&7" + category.items().size() + " items",
                    "",
                    "&#FFB6C1Click to browse")));
            holder.getSlotActions().put(slot, category.id());
        }
        inv.setItem(MAIN_BALANCE_SLOT, balanceItem(plugin, player));
        player.openInventory(inv);
        click(player);
    }

    public static void openCategory(BlossomEconomy plugin, Player player, String categoryId) {
        ShopManager.Category category = plugin.getShop().getCategory(categoryId);
        if (category == null) {
            openShop(plugin, player);
            return;
        }
        MenuHolder holder = new MenuHolder(MenuHolder.Type.SHOP_CATEGORY, categoryId);
        Inventory inv = Bukkit.createInventory(holder, 54, Text.color(category.name()));
        holder.setInventory(inv);
        renderCategory(plugin, player, inv, holder, category);
        player.openInventory(inv);
        click(player);
    }

    /** Draws (or redraws) a category page with live prices. */
    public static void renderCategory(BlossomEconomy plugin, Player player, Inventory inv,
                                      MenuHolder holder, ShopManager.Category category) {
        MarketManager market = plugin.getMarket();
        List<Material> items = category.items();
        for (int i = 0; i < items.size() && i < ShopManager.MAX_ITEMS_PER_CATEGORY; i++) {
            Material material = items.get(i);
            inv.setItem(i, item(material, "&f" + Text.itemName(material), List.of(
                    "&7Buy: &a" + Text.money(market.buyPrice(material)) + " &8each",
                    "&7Sells for: &a" + Text.money(market.sellPrice(material)) + " &8each",
                    "&7Market: " + market.trend(material),
                    "",
                    "&#FFB6C1Left-click &8» &7Buy 1 &8(&a" + Text.money(market.quoteBuy(material, 1)) + "&8)",
                    "&#FFB6C1Right-click &8» &7Buy 16 &8(&a" + Text.money(market.quoteBuy(material, 16)) + "&8)",
                    "&#FFB6C1Shift-click &8» &7Buy 64 &8(&a" + Text.money(market.quoteBuy(material, 64)) + "&8)")));
        }
        fill(plugin, inv, 45, 54);
        inv.setItem(CATEGORY_BACK_SLOT, item(Material.ARROW, "&#FF69B4&lBack", List.of("&7Return to the shop")));
        holder.getSlotActions().put(CATEGORY_BACK_SLOT, ACTION_BACK);
        inv.setItem(CATEGORY_BALANCE_SLOT, balanceItem(plugin, player));
    }

    public static void buy(BlossomEconomy plugin, Player player, Material material, int amount) {
        MarketManager market = plugin.getMarket();
        double cost = market.quoteBuy(material, amount);
        if (cost <= 0) {
            return;
        }
        if (!Items.fits(player.getInventory(), new ItemStack(material), amount)) {
            player.sendMessage(plugin.msg("inventory-full"));
            fail(player);
            return;
        }
        if (!plugin.getEconomy().withdraw(player.getUniqueId(), cost)) {
            player.sendMessage(plugin.msg("not-enough-money",
                    "%amount%", Text.money(plugin.getEconomy().getBalance(player.getUniqueId()))));
            fail(player);
            return;
        }
        market.buy(material, amount);
        Items.give(player, new ItemStack(material), amount);
        player.sendMessage(plugin.msg("bought",
                "%count%", String.valueOf(amount),
                "%item%", Text.itemName(material),
                "%amount%", Text.money(cost)));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
    }

    // ------------------------------------------------------------------
    // Selling
    // ------------------------------------------------------------------

    public static void openSell(BlossomEconomy plugin, Player player) {
        MenuHolder holder = new MenuHolder(MenuHolder.Type.SELL, null);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Text.color(plugin.getConfig().getString("menus.sell-title", "&#FF69B4&l❀ Sell")));
        holder.setInventory(inv);
        player.openInventory(inv);
        click(player);
    }

    /** Sells everything sellable in the sell menu and gives the rest back. */
    public static void sellMenuContents(BlossomEconomy plugin, Player player, Inventory inv) {
        MarketManager market = plugin.getMarket();
        List<ItemStack> unsold = new ArrayList<>();
        double total = 0;
        int count = 0;
        for (ItemStack stack : inv.getContents()) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (market.canSell(stack) && market.quoteSell(stack) > 0) {
                total += market.sell(stack);
                count += stack.getAmount();
            } else {
                unsold.add(stack.clone());
            }
        }
        inv.clear();
        for (ItemStack stack : unsold) {
            Items.give(player, stack);
        }
        finishSale(plugin, player, total, count, !unsold.isEmpty(), true);
    }

    /** /sell hand */
    public static void sellHand(BlossomEconomy plugin, Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage(plugin.msg("hold-item"));
            return;
        }
        if (!plugin.getMarket().canSell(hand) || plugin.getMarket().quoteSell(hand) <= 0) {
            player.sendMessage(plugin.msg("worthless"));
            fail(player);
            return;
        }
        int count = hand.getAmount();
        double total = plugin.getMarket().sell(hand);
        player.getInventory().setItemInMainHand(null);
        finishSale(plugin, player, total, count, false, false);
    }

    private static void finishSale(BlossomEconomy plugin, Player player, double total, int count,
                                   boolean returned, boolean silentIfEmpty) {
        total = Math.round(total * 100.0) / 100.0;
        if (count > 0 && total > 0) {
            plugin.getEconomy().deposit(player.getUniqueId(), total);
            player.sendMessage(plugin.msg("sold", "%count%", String.valueOf(count), "%amount%", Text.money(total)));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
            if (returned) {
                player.sendMessage(plugin.msg("returned"));
            }
            return;
        }
        if (returned) {
            player.sendMessage(plugin.msg("nothing-sold"));
            player.sendMessage(plugin.msg("returned"));
            fail(player);
        } else if (!silentIfEmpty) {
            player.sendMessage(plugin.msg("nothing-sold"));
            fail(player);
        }
    }

    // ------------------------------------------------------------------
    // Shared item helpers
    // ------------------------------------------------------------------

    public static void fill(BlossomEconomy plugin, Inventory inv, int from, int to) {
        Material filler = Material.matchMaterial(plugin.getConfig().getString("menus.filler", "PINK_STAINED_GLASS_PANE"));
        if (filler == null || !filler.isItem() || filler.isAir()) {
            filler = Material.PINK_STAINED_GLASS_PANE;
        }
        ItemStack glass = item(filler, " ", List.of());
        for (int i = from; i < to; i++) {
            inv.setItem(i, glass);
        }
    }

    public static ItemStack balanceItem(BlossomEconomy plugin, Player player) {
        double balance = plugin.getEconomy().getBalance(player.getUniqueId());
        return item(Material.SUNFLOWER, "&#FF69B4&lYour Balance", List.of(
                "&a" + Text.money(balance),
                "&8(" + Text.shortMoney(balance) + ")"));
    }

    @SuppressWarnings("deprecation")
    public static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            List<String> coloured = new ArrayList<>();
            for (String line : lore) {
                coloured.add(Text.color(line));
            }
            meta.setLore(coloured);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static void click(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }

    public static void fail(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
    }
}
