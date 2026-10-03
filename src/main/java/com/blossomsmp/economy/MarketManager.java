package com.blossomsmp.economy;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Live market prices.
 *
 * Every item has a "worth" (its normal sell price). It comes from the 'prices' list in config.yml,
 * or - for items not in the list - is worked out from the item's crafting recipe
 * (a diamond pickaxe is worth about 3 diamonds + 2 sticks).
 * Every item also has a market factor that starts at 1.0:
 *   - selling an item lowers its factor (price goes down)
 *   - buying an item from /shop raises its factor (price goes up)
 *   - over time every factor drifts back towards 1.0
 *
 * Sell price = worth x factor
 * Buy price  = worth x buy-multiplier x max(1, factor)   (buy prices never drop below normal,
 *                                                         which stops buy-cheap/craft/sell tricks)
 */
public class MarketManager {

    private final BlossomEconomy plugin;
    private final File file;
    private final Map<Material, Double> worth = new EnumMap<>(Material.class);
    private final Map<Material, Double> factors = new EnumMap<>(Material.class);
    private final Map<Material, Double> derived = new EnumMap<>(Material.class);
    private final Set<Material> computing = EnumSet.noneOf(Material.class);

    private double buyMultiplier = 3.0;
    private double impact = 0.001;
    private double minFactor = 0.25;
    private double maxFactor = 3.0;
    private double recoveryPercent = 5;
    private boolean autoPricing = true;
    /** Fixed shop buy prices from the buy-prices section (ignore buy-multiplier). */
    private final Map<Material, Double> buyOverrides = new EnumMap<>(Material.class);
    private double craftMultiplier = 0.9;
    private double enchantValuePerLevel = 5;
    private boolean dirty = false;

    public MarketManager(BlossomEconomy plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "market.yml");
    }

    // ------------------------------------------------------------------
    // Loading / saving
    // ------------------------------------------------------------------

    public void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        buyMultiplier = Math.max(1.0, config.getDouble("market.buy-multiplier", 3.0));
        impact = clamp(config.getDouble("market.impact-per-item", 0.001), 0, 0.5);
        minFactor = clamp(config.getDouble("market.min-price-percent", 25) / 100.0, 0.01, 1);
        maxFactor = Math.max(1, config.getDouble("market.max-price-percent", 300) / 100.0);
        recoveryPercent = clamp(config.getDouble("market.recovery-percent", 5), 0, 100);
        autoPricing = config.getBoolean("auto-pricing.enabled", true);
        craftMultiplier = clamp(config.getDouble("auto-pricing.craft-value-percent", 90) / 100.0, 0.01, 1);
        enchantValuePerLevel = Math.max(0, config.getDouble("enchant-value-per-level", 5));

        worth.clear();
        derived.clear();
        ConfigurationSection prices = config.getConfigurationSection("prices");
        if (prices != null) {
            for (String key : prices.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                double value = prices.getDouble(key);
                if (material == null || !material.isItem() || material.isAir() || value <= 0) {
                    plugin.getLogger().warning("Prices: skipping invalid item '" + key + "'");
                    continue;
                }
                worth.put(material, value);
            }
        }
        buyOverrides.clear();
        ConfigurationSection buyPrices = config.getConfigurationSection("buy-prices");
        if (buyPrices != null) {
            for (String key : buyPrices.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                double value = buyPrices.getDouble(key);
                if (material == null || !material.isItem() || material.isAir() || value <= 0) {
                    plugin.getLogger().warning("Buy prices: skipping invalid item '" + key + "'");
                    continue;
                }
                buyOverrides.put(material, value);
            }
        }
        plugin.getLogger().info("Loaded " + worth.size() + " item prices and " + buyOverrides.size() + " fixed buy prices.");
    }

    public void loadData() {
        factors.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("factors");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material != null) {
                factors.put(material, clamp(section.getDouble(key, 1.0), minFactor, maxFactor));
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<Material, Double> entry : factors.entrySet()) {
            yaml.set("factors." + entry.getKey().name(), Math.round(entry.getValue() * 10000.0) / 10000.0);
        }
        try {
            yaml.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save market.yml: " + e.getMessage());
        }
    }

    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    // ------------------------------------------------------------------
    // Prices
    // ------------------------------------------------------------------

    public boolean hasPrice(Material material) {
        return getWorth(material) > 0;
    }

    /** Normal sell price of one item (0 = can't be sold). */
    public double getWorth(Material material) {
        return worthAt(material, 0);
    }

    public double getFactor(Material material) {
        return factors.getOrDefault(material, 1.0);
    }

    /** Can this item be sold? Anything with a value or with enchantments can. */
    public boolean canSell(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        return getWorth(item.getType()) > 0 || enchantBonus(item) > 0;
    }

    /** 1.0 for undamaged items, 0.5 for a half-broken tool, and so on. */
    public double durabilityMultiplier(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable) || !damageable.hasDamage()) {
            return 1.0;
        }
        int max = damageable.hasMaxDamage() ? damageable.getMaxDamage() : item.getType().getMaxDurability();
        if (max <= 0) {
            return 1.0;
        }
        return clamp((max - damageable.getDamage()) / (double) max, 0, 1);
    }

    /** Extra money for enchantments (including enchanted books), for ONE item. */
    public double enchantBonus(ItemStack item) {
        if (enchantValuePerLevel <= 0) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }
        int levels = 0;
        for (int level : meta.getEnchants().values()) {
            levels += Math.max(0, level);
        }
        if (meta instanceof EnchantmentStorageMeta book) {
            for (Map.Entry<Enchantment, Integer> entry : book.getStoredEnchants().entrySet()) {
                levels += Math.max(0, entry.getValue());
            }
        }
        return round(levels * enchantValuePerLevel);
    }

    // ------------------------------------------------------------------
    // Automatic prices from crafting recipes
    // ------------------------------------------------------------------

    private double worthAt(Material material, int depth) {
        if (material == null || material.isAir() || !material.isItem()) {
            return 0;
        }
        Double explicit = worth.get(material);
        if (explicit != null) {
            return explicit;
        }
        if (!autoPricing) {
            return 0;
        }
        Double cached = derived.get(material);
        if (cached != null) {
            return cached;
        }
        if (depth > 8 || !computing.add(material)) {
            return 0; // too deep, or a recipe loop
        }
        double best = 0;
        try {
            for (Recipe recipe : Bukkit.getRecipesFor(new ItemStack(material))) {
                double value = recipeValue(recipe, depth);
                if (value > 0 && (best == 0 || value < best)) {
                    best = value;
                }
            }
        } catch (RuntimeException e) {
            best = 0;
        } finally {
            computing.remove(material);
        }
        double result = round(best * craftMultiplier);
        if (depth == 0 || result > 0) {
            derived.put(material, result);
        }
        return result;
    }

    /** Value of ONE result item of a recipe, or 0 if any ingredient has no value. */
    private double recipeValue(Recipe recipe, int depth) {
        List<RecipeChoice> inputs = new ArrayList<>();
        if (recipe instanceof ShapedRecipe shaped) {
            Map<Character, RecipeChoice> choices = shaped.getChoiceMap();
            for (String row : shaped.getShape()) {
                for (char c : row.toCharArray()) {
                    RecipeChoice choice = choices.get(c);
                    if (choice != null) {
                        inputs.add(choice);
                    }
                }
            }
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            inputs.addAll(shapeless.getChoiceList());
        } else if (recipe instanceof CookingRecipe<?> cooking) {
            inputs.add(cooking.getInputChoice());
        } else if (recipe instanceof StonecuttingRecipe cutting) {
            inputs.add(cutting.getInputChoice());
        } else if (recipe instanceof SmithingRecipe smithing) {
            if (smithing instanceof SmithingTransformRecipe transform) {
                inputs.add(transform.getTemplate());
            }
            inputs.add(smithing.getBase());
            inputs.add(smithing.getAddition());
        } else {
            return 0;
        }
        if (inputs.isEmpty()) {
            return 0;
        }
        double total = 0;
        for (RecipeChoice choice : inputs) {
            double value = choiceValue(choice, depth);
            if (value <= 0) {
                return 0;
            }
            total += value;
        }
        return total / Math.max(1, recipe.getResult().getAmount());
    }

    /** Cheapest valued option of a recipe ingredient slot. */
    private double choiceValue(RecipeChoice choice, int depth) {
        List<Material> options = new ArrayList<>();
        if (choice instanceof RecipeChoice.MaterialChoice materialChoice) {
            options.addAll(materialChoice.getChoices());
        } else if (choice instanceof RecipeChoice.ExactChoice exactChoice) {
            for (ItemStack stack : exactChoice.getChoices()) {
                options.add(stack.getType());
            }
        }
        double best = 0;
        for (Material option : options) {
            double value = worthAt(option, depth + 1);
            if (value > 0 && (best == 0 || value < best)) {
                best = value;
            }
        }
        return best;
    }

    /** Current sell price for one item. */
    public double sellPrice(Material material) {
        return round(getWorth(material) * getFactor(material));
    }

    /** Normal shop price of one item before market changes. */
    private double baseBuy(Material material) {
        Double fixed = buyOverrides.get(material);
        if (fixed != null) {
            return fixed;
        }
        return getWorth(material) * buyMultiplier;
    }

    /** True if the shop can sell this item (it has a price or a fixed buy price). */
    public boolean canBuy(Material material) {
        return baseBuy(material) > 0;
    }

    /** Current buy price for one item. */
    public double buyPrice(Material material) {
        return round(baseBuy(material) * Math.max(1.0, getFactor(material)));
    }

    /** Current sell price for ONE of this exact item (damage and enchantments included). */
    public double sellPrice(ItemStack item) {
        return round(getWorth(item.getType()) * durabilityMultiplier(item) * getFactor(item.getType())
                + enchantBonus(item));
    }

    /** Money for selling this whole stack, without selling it. */
    public double quoteSell(ItemStack item) {
        return simulate(item.getType(), item.getAmount(), true, false,
                durabilityMultiplier(item), enchantBonus(item));
    }

    /** Sells this whole stack and moves the price down. Returns the money earned. */
    public double sell(ItemStack item) {
        return simulate(item.getType(), item.getAmount(), true, true,
                durabilityMultiplier(item), enchantBonus(item));
    }

    public double quoteBuy(Material material, int amount) {
        return simulate(material, amount, false, false, 1.0, 0);
    }

    /** Buys and moves the price up. Returns the cost. */
    public double buy(Material material, int amount) {
        return simulate(material, amount, false, true, 1.0, 0);
    }

    private double simulate(Material material, int amount, boolean selling, boolean apply,
                            double multiplier, double bonus) {
        double base = selling ? getWorth(material) * multiplier : baseBuy(material);
        if ((base <= 0 && bonus <= 0) || amount <= 0) {
            return 0;
        }
        double factor = getFactor(material);
        double total = 0;
        for (int i = 0; i < amount; i++) {
            if (selling) {
                total += base * factor + bonus;
                if (base > 0) {
                    factor = Math.max(minFactor, factor * (1 - impact));
                }
            } else {
                total += base * Math.max(1.0, factor);
                factor = Math.min(maxFactor, factor * (1 + impact));
            }
        }
        if (apply && base > 0) {
            factors.put(material, factor);
            dirty = true;
        }
        return round(total);
    }

    /** "▲ +12%", "▼ -30%" or "● 0%" with colours. */
    public String trend(Material material) {
        double factor = getFactor(material);
        long percent = Math.round((factor - 1) * 100);
        if (percent > 0) {
            return "&a▲ +" + percent + "%";
        }
        if (percent < 0) {
            return "&c▼ " + percent + "%";
        }
        return "&7● normal";
    }

    /** Moves every price a little back towards normal. Called on a timer. */
    public void recover() {
        if (recoveryPercent <= 0 || factors.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Material, Double>> it = factors.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Material, Double> entry = it.next();
            double factor = entry.getValue();
            factor += (1.0 - factor) * (recoveryPercent / 100.0);
            if (Math.abs(1.0 - factor) < 0.005) {
                it.remove();
            } else {
                entry.setValue(factor);
            }
        }
        dirty = true;
    }

    /** Puts every price back to normal. */
    public void resetAll() {
        factors.clear();
        dirty = true;
        save();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
