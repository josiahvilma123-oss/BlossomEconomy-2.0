package com.blossomsmp.economy;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads the /shop categories. Prices come from the MarketManager. */
public class ShopManager {

    public static final int MAX_ITEMS_PER_CATEGORY = 45;

    public record Category(String id, String name, Material icon, int slot, List<Material> items) {
    }

    private final BlossomEconomy plugin;
    private final Map<String, Category> categories = new LinkedHashMap<>();

    public ShopManager(BlossomEconomy plugin) {
        this.plugin = plugin;
    }

    public void load() {
        categories.clear();
        ConfigurationSection cats = plugin.getConfig().getConfigurationSection("shop.categories");
        if (cats == null) {
            return;
        }
        for (String id : cats.getKeys(false)) {
            ConfigurationSection section = cats.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            List<Material> items = new ArrayList<>();
            for (String key : section.getStringList("items")) {
                Material material = Material.matchMaterial(key);
                if (material == null || !plugin.getMarket().canBuy(material)) {
                    plugin.getLogger().warning("Shop: '" + key + "' in category " + id
                            + " is not a valid item or has no price under 'prices'.");
                    continue;
                }
                if (items.size() >= MAX_ITEMS_PER_CATEGORY) {
                    plugin.getLogger().warning("Shop: category " + id + " has more than "
                            + MAX_ITEMS_PER_CATEGORY + " items, the rest are ignored.");
                    break;
                }
                items.add(material);
            }
            Material icon = Material.matchMaterial(section.getString("icon", "CHEST"));
            if (icon == null || !icon.isItem() || icon.isAir()) {
                icon = Material.CHEST;
            }
            categories.put(id, new Category(id, section.getString("name", id), icon,
                    section.getInt("slot", -1), Collections.unmodifiableList(items)));
        }
        plugin.getLogger().info("Loaded " + categories.size() + " shop categories.");
    }

    public Collection<Category> getCategories() {
        return categories.values();
    }

    public Category getCategory(String id) {
        return id == null ? null : categories.get(id);
    }
}
