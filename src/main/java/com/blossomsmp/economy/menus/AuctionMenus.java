package com.blossomsmp.economy.menus;

import com.blossomsmp.economy.AuctionManager;
import com.blossomsmp.economy.AuctionManager.Listing;
import com.blossomsmp.economy.BlossomEconomy;
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
import java.util.UUID;

/** The /ah menus. */
public final class AuctionMenus {

    private static final int PER_PAGE = 45;

    private AuctionMenus() {
    }

    // ------------------------------------------------------------------
    // Browse
    // ------------------------------------------------------------------

    public static void openBrowse(BlossomEconomy plugin, Player player, int page) {
        List<Listing> active = plugin.getAuctions().getActive();
        int pages = Math.max(1, (active.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));

        MenuHolder holder = new MenuHolder(MenuHolder.Type.AH_BROWSE, null, page);
        String title = plugin.getConfig().getString("auction.title", "&#FF69B4&l❀ Auction House")
                + " &8(" + (page + 1) + "/" + pages + ")";
        Inventory inv = Bukkit.createInventory(holder, 54, Text.color(title));
        holder.setInventory(inv);

        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < active.size(); i++) {
            Listing listing = active.get(start + i);
            boolean own = listing.seller().equals(player.getUniqueId());
            inv.setItem(i, display(listing, own ? "&7This is your listing" : "&#FFB6C1Click to buy"));
            holder.getSlotActions().put(i, "buy:" + listing.id());
        }

        Menus.fill(plugin, inv, 45, 54);
        if (page > 0) {
            inv.setItem(45, Menus.item(Material.ARROW, "&#FF69B4&lPrevious Page", List.of()));
            holder.getSlotActions().put(45, "page:" + (page - 1));
        }
        inv.setItem(47, Menus.item(Material.CHEST, "&#FF69B4&lYour Listings",
                List.of("&7See and cancel your items for sale")));
        holder.getSlotActions().put(47, "mine");
        inv.setItem(49, Menus.item(Material.SUNFLOWER, "&#FF69B4&lHow to sell", List.of(
                "&7Hold an item and type",
                "&f/ah sell <price>",
                "",
                "&7Your balance: &a" + Text.money(plugin.getEconomy().getBalance(player.getUniqueId())))));
        int waiting = plugin.getAuctions().getReturns(player.getUniqueId()).size();
        inv.setItem(51, Menus.item(Material.ENDER_CHEST, "&#FF69B4&lCollect Items", List.of(
                "&7Expired and cancelled items",
                "&7Waiting: &f" + waiting)));
        holder.getSlotActions().put(51, "collect");
        if (page < pages - 1) {
            inv.setItem(53, Menus.item(Material.ARROW, "&#FF69B4&lNext Page", List.of()));
            holder.getSlotActions().put(53, "page:" + (page + 1));
        }
        player.openInventory(inv);
        Menus.click(player);
    }

    // ------------------------------------------------------------------
    // Confirm purchase
    // ------------------------------------------------------------------

    public static void openConfirm(BlossomEconomy plugin, Player player, UUID listingId) {
        Listing listing = plugin.getAuctions().get(listingId);
        if (listing == null || listing.isExpired()) {
            player.sendMessage(plugin.msg("ah-gone"));
            openBrowse(plugin, player, 0);
            return;
        }
        if (listing.seller().equals(player.getUniqueId())) {
            player.sendMessage(plugin.msg("ah-own"));
            Menus.fail(player);
            return;
        }
        MenuHolder holder = new MenuHolder(MenuHolder.Type.AH_CONFIRM, listingId.toString());
        Inventory inv = Bukkit.createInventory(holder, 27, Text.color("&#FF69B4&lConfirm Purchase"));
        holder.setInventory(inv);
        Menus.fill(plugin, inv, 0, 27);
        inv.setItem(11, Menus.item(Material.LIME_STAINED_GLASS_PANE, "&a&lBUY",
                List.of("&7Pay &a" + Text.money(listing.price()))));
        holder.getSlotActions().put(11, "confirm");
        inv.setItem(13, display(listing, ""));
        inv.setItem(15, Menus.item(Material.RED_STAINED_GLASS_PANE, "&c&lCANCEL", List.of()));
        holder.getSlotActions().put(15, "back");
        player.openInventory(inv);
        Menus.click(player);
    }

    public static void buy(BlossomEconomy plugin, Player buyer, UUID listingId) {
        AuctionManager auctions = plugin.getAuctions();
        Listing listing = auctions.get(listingId);
        if (listing == null || listing.isExpired()) {
            buyer.sendMessage(plugin.msg("ah-gone"));
            openBrowse(plugin, buyer, 0);
            return;
        }
        if (listing.seller().equals(buyer.getUniqueId())) {
            buyer.sendMessage(plugin.msg("ah-own"));
            return;
        }
        ItemStack item = listing.item();
        if (!Items.fits(buyer.getInventory(), item, item.getAmount())) {
            buyer.sendMessage(plugin.msg("inventory-full"));
            Menus.fail(buyer);
            return;
        }
        if (!plugin.getEconomy().withdraw(buyer.getUniqueId(), listing.price())) {
            buyer.sendMessage(plugin.msg("not-enough-money",
                    "%amount%", Text.money(plugin.getEconomy().getBalance(buyer.getUniqueId()))));
            Menus.fail(buyer);
            return;
        }
        if (auctions.remove(listingId) == null) {
            // Someone else bought it a moment earlier: refund
            plugin.getEconomy().deposit(buyer.getUniqueId(), listing.price());
            buyer.sendMessage(plugin.msg("ah-gone"));
            return;
        }
        Items.give(buyer, item.clone());

        double taxPercent = Math.max(0, Math.min(100, plugin.getConfig().getDouble("auction.tax-percent", 5)));
        double tax = Math.round(listing.price() * taxPercent) / 100.0;
        double earned = Math.round((listing.price() - tax) * 100.0) / 100.0;
        plugin.getEconomy().deposit(listing.seller(), earned);

        String itemName = Text.itemName(item.getType());
        buyer.sendMessage(plugin.msg("ah-bought", "%item%", itemName,
                "%count%", String.valueOf(item.getAmount()), "%amount%", Text.money(listing.price())));
        buyer.playSound(buyer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
        Player seller = Bukkit.getPlayer(listing.seller());
        if (seller != null) {
            seller.sendMessage(plugin.msg("ah-sold", "%item%", itemName, "%player%", buyer.getName(),
                    "%amount%", Text.money(earned), "%tax%", Text.money(tax)));
            seller.playSound(seller.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
        }
        buyer.closeInventory();
    }

    // ------------------------------------------------------------------
    // Your listings
    // ------------------------------------------------------------------

    public static void openMine(BlossomEconomy plugin, Player player) {
        MenuHolder holder = new MenuHolder(MenuHolder.Type.AH_MINE, null);
        Inventory inv = Bukkit.createInventory(holder, 54, Text.color("&#FF69B4&lYour Listings"));
        holder.setInventory(inv);
        List<Listing> mine = plugin.getAuctions().getBySeller(player.getUniqueId());
        for (int i = 0; i < mine.size() && i < PER_PAGE; i++) {
            inv.setItem(i, display(mine.get(i), "&cClick to cancel"));
            holder.getSlotActions().put(i, "cancel:" + mine.get(i).id());
        }
        Menus.fill(plugin, inv, 45, 54);
        inv.setItem(49, Menus.item(Material.ARROW, "&#FF69B4&lBack", List.of()));
        holder.getSlotActions().put(49, "back");
        player.openInventory(inv);
        Menus.click(player);
    }

    public static void cancel(BlossomEconomy plugin, Player player, UUID listingId) {
        Listing listing = plugin.getAuctions().get(listingId);
        if (listing == null || !listing.seller().equals(player.getUniqueId())) {
            openMine(plugin, player);
            return;
        }
        if (plugin.getAuctions().remove(listingId) == null) {
            openMine(plugin, player);
            return;
        }
        ItemStack item = listing.item().clone();
        if (Items.fits(player.getInventory(), item, item.getAmount())) {
            Items.give(player, item);
        } else {
            plugin.getAuctions().addReturn(player.getUniqueId(), item);
        }
        player.sendMessage(plugin.msg("ah-cancelled", "%item%", Text.itemName(item.getType())));
        openMine(plugin, player);
    }

    // ------------------------------------------------------------------
    // Collect
    // ------------------------------------------------------------------

    public static void openCollect(BlossomEconomy plugin, Player player) {
        MenuHolder holder = new MenuHolder(MenuHolder.Type.AH_COLLECT, null);
        Inventory inv = Bukkit.createInventory(holder, 54, Text.color("&#FF69B4&lCollect Items"));
        holder.setInventory(inv);
        List<ItemStack> items = plugin.getAuctions().getReturns(player.getUniqueId());
        for (int i = 0; i < items.size() && i < PER_PAGE; i++) {
            inv.setItem(i, items.get(i).clone());
            holder.getSlotActions().put(i, "take:" + i);
        }
        Menus.fill(plugin, inv, 45, 54);
        inv.setItem(49, Menus.item(Material.ARROW, "&#FF69B4&lBack", List.of()));
        holder.getSlotActions().put(49, "back");
        player.openInventory(inv);
        Menus.click(player);
    }

    public static void take(BlossomEconomy plugin, Player player, int index) {
        List<ItemStack> items = plugin.getAuctions().getReturns(player.getUniqueId());
        if (index < 0 || index >= items.size()) {
            openCollect(plugin, player);
            return;
        }
        ItemStack preview = items.get(index);
        if (!Items.fits(player.getInventory(), preview, preview.getAmount())) {
            player.sendMessage(plugin.msg("inventory-full"));
            Menus.fail(player);
            return;
        }
        ItemStack item = plugin.getAuctions().takeReturn(player.getUniqueId(), index);
        if (item != null) {
            Items.give(player, item);
            player.sendMessage(plugin.msg("ah-collected", "%item%", Text.itemName(item.getType())));
        }
        openCollect(plugin, player);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** A copy of the listed item with price, seller and time left added to its lore. */
    @SuppressWarnings("deprecation")
    private static ItemStack display(Listing listing, String action) {
        ItemStack display = listing.item().clone();
        ItemMeta meta = display.getItemMeta();
        if (meta == null) {
            return display;
        }
        List<String> lore = meta.hasLore() && meta.getLore() != null
                ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add(Text.color("&7Price: &a" + Text.money(listing.price())));
        lore.add(Text.color("&7Seller: &f" + listing.sellerName()));
        lore.add(Text.color("&7Ends in: &f" + Text.duration(listing.expires() - System.currentTimeMillis())));
        if (action != null && !action.isEmpty()) {
            lore.add("");
            lore.add(Text.color(action));
        }
        meta.setLore(lore);
        display.setItemMeta(meta);
        return display;
    }
}
