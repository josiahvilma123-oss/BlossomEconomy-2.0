package com.blossomsmp.economy;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stores every player's balance in plugins/BlossomEconomy/balances.yml.
 * All methods are synchronized so other plugins can safely use it through Vault.
 */
public class EconomyManager {

    private final BlossomEconomy plugin;
    private final File file;
    private final Map<UUID, Double> balances = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private boolean dirty = false;

    public EconomyManager(BlossomEconomy plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "balances.yml");
    }

    public synchronized void load() {
        balances.clear();
        names.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("players");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                balances.put(id, round(Math.max(0, section.getDouble(key + ".balance"))));
                String name = section.getString(key + ".name");
                if (name != null) {
                    names.put(id, name);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Skipping invalid entry in balances.yml: " + key);
            }
        }
        plugin.getLogger().info("Loaded " + balances.size() + " balances.");
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
            String path = "players." + entry.getKey();
            yaml.set(path + ".name", names.get(entry.getKey()));
            yaml.set(path + ".balance", entry.getValue());
        }
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().severe("Could not create the plugin folder.");
                return;
            }
            yaml.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save balances.yml: " + e.getMessage());
        }
    }

    public synchronized void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    public synchronized boolean hasAccount(UUID id) {
        return balances.containsKey(id);
    }

    /** Creates an account with the starting balance. Returns true if a new account was made. */
    public synchronized boolean createAccount(UUID id, String name) {
        if (name != null && !name.equals(names.put(id, name))) {
            dirty = true;
        }
        if (balances.containsKey(id)) {
            return false;
        }
        balances.put(id, round(Math.max(0, plugin.getConfig().getDouble("starting-balance", 0))));
        dirty = true;
        return true;
    }

    public synchronized double getBalance(UUID id) {
        return balances.getOrDefault(id, 0.0);
    }

    public synchronized void setBalance(UUID id, double amount) {
        balances.put(id, round(Math.max(0, amount)));
        dirty = true;
    }

    public synchronized void deposit(UUID id, double amount) {
        if (amount <= 0) {
            return;
        }
        setBalance(id, getBalance(id) + amount);
    }

    /** Removes money only if the player has enough. */
    public synchronized boolean withdraw(UUID id, double amount) {
        if (amount < 0) {
            return false;
        }
        double balance = getBalance(id);
        if (balance + 0.000001 < amount) {
            return false;
        }
        setBalance(id, balance - amount);
        return true;
    }

    public synchronized boolean transfer(UUID from, UUID to, double amount) {
        if (!withdraw(from, amount)) {
            return false;
        }
        deposit(to, amount);
        return true;
    }

    public synchronized String getName(UUID id) {
        String name = names.get(id);
        if (name != null) {
            return name;
        }
        String bukkitName = Bukkit.getOfflinePlayer(id).getName();
        return bukkitName != null ? bukkitName : id.toString().substring(0, 8);
    }

    public synchronized List<Map.Entry<UUID, Double>> top(int limit) {
        List<Map.Entry<UUID, Double>> list = new ArrayList<>();
        for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
            list.add(Map.entry(entry.getKey(), entry.getValue()));
        }
        list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return list.size() > limit ? new ArrayList<>(list.subList(0, limit)) : list;
    }

    /** 1 = richest. */
    public synchronized int rank(UUID id) {
        double balance = getBalance(id);
        int rank = 1;
        for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
            if (!entry.getKey().equals(id) && entry.getValue() > balance) {
                rank++;
            }
        }
        return rank;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
