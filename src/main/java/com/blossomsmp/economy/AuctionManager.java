package com.blossomsmp.economy;

import com.blossomsmp.economy.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stores auction listings and items waiting to be collected (expired or cancelled).
 * Saved to plugins/BlossomEconomy/auctions.yml after every change so nothing is lost or duplicated.
 */
public class AuctionManager {

    public record Listing(UUID id, UUID seller, String sellerName, ItemStack item,
                          double price, long created, long expires) {

        public boolean isExpired() {
            return System.currentTimeMillis() >= expires;
        }
    }

    private final BlossomEconomy plugin;
    private final File file;
    private final Map<UUID, Listing> listings = new LinkedHashMap<>();
    private final Map<UUID, List<ItemStack>> returns = new HashMap<>();

    public AuctionManager(BlossomEconomy plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "auctions.yml");
    }

    // ------------------------------------------------------------------
    // Loading / saving
    // ------------------------------------------------------------------

    public void load() {
        listings.clear();
        returns.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("listings");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    ConfigurationSection s = section.getConfigurationSection(key);
                    if (s == null) {
                        continue;
                    }
                    ItemStack item = decode(s.getString("item"));
                    if (item == null) {
                        continue;
                    }
                    Listing listing = new Listing(UUID.fromString(key),
                            UUID.fromString(s.getString("seller", "")),
                            s.getString("seller-name", "?"),
                            item,
                            s.getDouble("price"),
                            s.getLong("created"),
                            s.getLong("expires"));
                    listings.put(listing.id(), listing);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Skipping broken auction listing " + key);
                }
            }
        }
        ConfigurationSection ret = yaml.getConfigurationSection("returns");
        if (ret != null) {
            for (String key : ret.getKeys(false)) {
                try {
                    List<ItemStack> items = new ArrayList<>();
                    for (String encoded : ret.getStringList(key)) {
                        ItemStack item = decode(encoded);
                        if (item != null) {
                            items.add(item);
                        }
                    }
                    if (!items.isEmpty()) {
                        returns.put(UUID.fromString(key), items);
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Skipping broken auction returns for " + key);
                }
            }
        }
        plugin.getLogger().info("Loaded " + listings.size() + " auction listings.");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Listing listing : listings.values()) {
            String path = "listings." + listing.id();
            yaml.set(path + ".seller", listing.seller().toString());
            yaml.set(path + ".seller-name", listing.sellerName());
            yaml.set(path + ".price", listing.price());
            yaml.set(path + ".created", listing.created());
            yaml.set(path + ".expires", listing.expires());
            yaml.set(path + ".item", encode(listing.item()));
        }
        for (Map.Entry<UUID, List<ItemStack>> entry : returns.entrySet()) {
            List<String> encoded = new ArrayList<>();
            for (ItemStack item : entry.getValue()) {
                encoded.add(encode(item));
            }
            yaml.set("returns." + entry.getKey(), encoded);
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save auctions.yml: " + e.getMessage());
        }
    }

    private static String encode(ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    private ItemStack decode(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(data));
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Could not load an auction item: " + e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Listings
    // ------------------------------------------------------------------

    /** Active listings, newest first. */
    public List<Listing> getActive() {
        List<Listing> list = new ArrayList<>();
        for (Listing listing : listings.values()) {
            if (!listing.isExpired()) {
                list.add(listing);
            }
        }
        list.sort((a, b) -> Long.compare(b.created(), a.created()));
        return list;
    }

    public List<Listing> getBySeller(UUID seller) {
        List<Listing> list = new ArrayList<>();
        for (Listing listing : listings.values()) {
            if (listing.seller().equals(seller)) {
                list.add(listing);
            }
        }
        list.sort((a, b) -> Long.compare(b.created(), a.created()));
        return list;
    }

    public Listing get(UUID id) {
        return listings.get(id);
    }

    public Listing create(Player seller, ItemStack item, double price) {
        long now = System.currentTimeMillis();
        long hours = Math.max(1, plugin.getConfig().getLong("auction.duration-hours", 48));
        Listing listing = new Listing(UUID.randomUUID(), seller.getUniqueId(), seller.getName(),
                item.clone(), price, now, now + hours * 3_600_000L);
        listings.put(listing.id(), listing);
        save();
        return listing;
    }

    /** Removes a listing. Returns it, or null if it was already gone. */
    public Listing remove(UUID id) {
        Listing removed = listings.remove(id);
        if (removed != null) {
            save();
        }
        return removed;
    }

    // ------------------------------------------------------------------
    // Returns (expired / cancelled items waiting to be collected)
    // ------------------------------------------------------------------

    public List<ItemStack> getReturns(UUID player) {
        return returns.getOrDefault(player, List.of());
    }

    public void addReturn(UUID player, ItemStack item) {
        returns.computeIfAbsent(player, k -> new ArrayList<>()).add(item.clone());
        save();
    }

    /** Takes one item out of the returns list. Returns null if the index is invalid. */
    public ItemStack takeReturn(UUID player, int index) {
        List<ItemStack> list = returns.get(player);
        if (list == null || index < 0 || index >= list.size()) {
            return null;
        }
        ItemStack item = list.remove(index);
        if (list.isEmpty()) {
            returns.remove(player);
        }
        save();
        return item;
    }

    /** Moves expired listings to their sellers' returns. Called every minute. */
    public void checkExpired() {
        boolean changed = false;
        Iterator<Listing> it = listings.values().iterator();
        while (it.hasNext()) {
            Listing listing = it.next();
            if (!listing.isExpired()) {
                continue;
            }
            it.remove();
            returns.computeIfAbsent(listing.seller(), k -> new ArrayList<>()).add(listing.item().clone());
            changed = true;
            Player online = Bukkit.getPlayer(listing.seller());
            if (online != null) {
                online.sendMessage(plugin.msg("ah-expired", "%item%", Text.itemName(listing.item().getType())));
            }
        }
        if (changed) {
            save();
        }
    }
}
