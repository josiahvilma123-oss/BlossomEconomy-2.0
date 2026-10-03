package com.blossomsmp.economy.hooks;

import com.blossomsmp.economy.BlossomEconomy;
import com.blossomsmp.economy.MarketManager;
import com.blossomsmp.economy.menus.MenuHolder;
import com.blossomsmp.economy.util.Text;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Adds a "~$ 1M" worth line to item tooltips.
 *
 * The line is added ONLY to the packets sent to the player, so the real items
 * on the server are never changed (they still stack, sell and save normally).
 * Needs ProtocolLib. This class is only loaded when ProtocolLib is installed.
 */
public class TooltipPrices implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final BlossomEconomy plugin;
    private PacketAdapter adapter;
    private BukkitTask refreshTask;

    // Snapshots made on the main thread, read from the network thread
    private volatile Map<Material, Double> prices = new EnumMap<>(Material.class);
    private volatile boolean enabled = true;
    private volatile boolean hideInCreative = true;
    private volatile String format = "&7~&a$ &f%each%";
    private volatile String stackFormat = "&7~&a$ &f%each% &8| &a$ &f%stack% &7total";
    private volatile String marker = "~$";

    public TooltipPrices(BlossomEconomy plugin) {
        this.plugin = plugin;
    }

    public void start() {
        refresh();
        // Keep prices up to date with the live market (every 5 seconds)
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 100L, 100L);
        Bukkit.getPluginManager().registerEvents(this, plugin);

        adapter = new PacketAdapter(plugin, ListenerPriority.NORMAL,
                PacketType.Play.Server.SET_SLOT,
                PacketType.Play.Server.WINDOW_ITEMS,
                PacketType.Play.Client.SET_CREATIVE_SLOT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                try {
                    handleOutgoing(event);
                } catch (Throwable ignored) {
                    // Never break inventories because of a tooltip
                }
            }

            @Override
            public void onPacketReceiving(PacketEvent event) {
                try {
                    handleCreative(event);
                } catch (Throwable ignored) {
                }
            }
        };
        ProtocolLibrary.getProtocolManager().addPacketListener(adapter);

        // Show the new tooltips straight away
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.updateInventory();
        }
    }

    public void stop() {
        if (adapter != null) {
            ProtocolLibrary.getProtocolManager().removePacketListener(adapter);
            adapter = null;
        }
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        HandlerList.unregisterAll(this);
    }

    /** Main thread: copies the current prices and settings for the network thread. */
    public void refresh() {
        enabled = plugin.getConfig().getBoolean("tooltip-prices.enabled", true);
        hideInCreative = plugin.getConfig().getBoolean("tooltip-prices.hide-in-creative", true);
        format = plugin.getConfig().getString("tooltip-prices.format", "&7~&a$ &f%each%");
        stackFormat = plugin.getConfig().getString("tooltip-prices.stack-format",
                "&7~&a$ &f%each% &8| &a$ &f%stack% &7total");

        // The text before the first placeholder, used to find our line again
        String start = format.contains("%") ? format.substring(0, format.indexOf('%')) : format;
        marker = ChatColor.stripColor(Text.color(start)).trim();

        MarketManager market = plugin.getMarket();
        Map<Material, Double> fresh = new EnumMap<>(Material.class);
        for (Material material : Material.values()) {
            if (material.name().startsWith("LEGACY_")) {
                continue;
            }
            try {
                if (!material.isItem() || material.isAir()) {
                    continue;
                }
                double price = market.sellPrice(material);
                if (price > 0) {
                    fresh.put(material, price);
                }
            } catch (Throwable ignored) {
            }
        }
        prices = fresh;
    }

    // ------------------------------------------------------------------
    // Outgoing: add the worth line
    // ------------------------------------------------------------------

    private void handleOutgoing(PacketEvent event) {
        if (!enabled || event.isPlayerTemporary()) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null || (hideInCreative && player.getGameMode() == GameMode.CREATIVE)) {
            return;
        }
        PacketContainer packet = event.getPacket().shallowClone();
        int windowId = packet.getIntegers().read(0);

        // Our own menus (shop, auction house) already show prices - leave their top slots alone
        boolean skipTop = false;
        int topSize = 0;
        if (windowId > 0) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof MenuHolder holder && holder.getType() != MenuHolder.Type.SELL) {
                skipTop = true;
                topSize = top.getSize();
            }
        }

        if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
            if (packet.getIntegers().size() < 3) {
                return;
            }
            int slot = packet.getIntegers().read(2);
            if (skipTop && slot < topSize) {
                return;
            }
            ItemStack item = packet.getItemModifier().read(0);
            ItemStack changed = withPrice(item);
            if (changed != null) {
                packet.getItemModifier().write(0, changed);
                event.setPacket(packet);
            }
            return;
        }

        // WINDOW_ITEMS: the whole inventory at once
        List<ItemStack> items = packet.getItemListModifier().read(0);
        if (items == null) {
            return;
        }
        if (skipTop) {
            topSize = Math.max(0, items.size() - 36); // the last 36 slots are the player's inventory
        }
        List<ItemStack> out = new ArrayList<>(items.size());
        boolean any = false;
        for (int i = 0; i < items.size(); i++) {
            ItemStack item = items.get(i);
            ItemStack changed = (skipTop && i < topSize) ? null : withPrice(item);
            if (changed != null) {
                out.add(changed);
                any = true;
            } else {
                out.add(item);
            }
        }
        if (any) {
            packet.getItemListModifier().write(0, out);
            event.setPacket(packet);
        }
    }

    /** Returns a COPY of the item with the worth line, or null if it has no value. */
    private ItemStack withPrice(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        MarketManager market = plugin.getMarket();
        Double base = prices.get(item.getType());
        double bonus = market.enchantBonus(item);
        if ((base == null || base <= 0) && bonus <= 0) {
            return null;
        }
        double each = (base == null ? 0 : base) * market.durabilityMultiplier(item) + bonus;
        each = Math.round(each * 100.0) / 100.0;
        if (each <= 0) {
            return null;
        }

        String line = (item.getAmount() > 1 ? stackFormat : format)
                .replace("%each%", number(each))
                .replace("%stack%", number(each * item.getAmount()))
                .replace("%amount%", String.valueOf(item.getAmount()));

        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) {
            return null;
        }
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Text.component(line).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        copy.setItemMeta(meta);
        return copy;
    }

    /** 1500 -> "1.5K", 1000000 -> "1M", 12.5 -> "12.5" */
    private static String number(double amount) {
        String text = Text.shortMoney(amount);
        return text.startsWith("$") ? text.substring(1) : text;
    }

    // ------------------------------------------------------------------
    // Incoming: creative mode sends items back - remove our line so it never becomes real
    // ------------------------------------------------------------------

    private void handleCreative(PacketEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.SET_CREATIVE_SLOT || marker.isEmpty()) {
            return;
        }
        PacketContainer packet = event.getPacket();
        ItemStack item = packet.getItemModifier().read(0);
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return;
        }
        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null || meta.lore() == null) {
            return;
        }
        List<Component> lore = new ArrayList<>(meta.lore());
        boolean removed = lore.removeIf(c -> PLAIN.serialize(c).trim().startsWith(marker));
        if (removed) {
            meta.lore(lore.isEmpty() ? null : lore);
            copy.setItemMeta(meta);
            packet.getItemModifier().write(0, copy);
        }
    }

    /** Switching game mode resends the inventory so tooltips appear/disappear correctly. */
    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
    }
}
