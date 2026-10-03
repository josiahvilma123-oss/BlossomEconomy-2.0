package com.blossomsmp.economy;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Daily login rewards with streaks.
 * Claim once every cooldown-hours. Claiming again within streak-reset-hours keeps the streak going,
 * otherwise it starts again at day 1. Saved in plugins/BlossomEconomy/daily.yml.
 */
public class DailyManager {

    public record ClaimResult(boolean success, int streak, double reward, double nextReward, long waitMillis) {
    }

    private record Entry(long lastClaim, int streak) {
    }

    private final BlossomEconomy plugin;
    private final File file;
    private final Map<UUID, Entry> data = new HashMap<>();

    public DailyManager(BlossomEconomy plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "daily.yml");
    }

    public void load() {
        data.clear();
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
                data.put(UUID.fromString(key), new Entry(section.getLong(key + ".last-claim"),
                        Math.max(0, section.getInt(key + ".streak"))));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Skipping invalid entry in daily.yml: " + key);
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Entry> entry : data.entrySet()) {
            yaml.set("players." + entry.getKey() + ".last-claim", entry.getValue().lastClaim());
            yaml.set("players." + entry.getKey() + ".streak", entry.getValue().streak());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save daily.yml: " + e.getMessage());
        }
    }

    private long cooldownMillis() {
        return Math.max(1, plugin.getConfig().getLong("daily-rewards.cooldown-hours", 24)) * 3_600_000L;
    }

    private long resetMillis() {
        return Math.max(cooldownMillis() / 3_600_000L,
                plugin.getConfig().getLong("daily-rewards.streak-reset-hours", 48)) * 3_600_000L;
    }

    /** Reward for a streak day (day 1 = first item; after the list ends, the last amount repeats). */
    public double rewardFor(int day) {
        List<Double> rewards = plugin.getConfig().getDoubleList("daily-rewards.rewards");
        if (rewards.isEmpty()) {
            return 0;
        }
        int index = Math.min(Math.max(1, day), rewards.size()) - 1;
        return Math.max(0, rewards.get(index));
    }

    /** Milliseconds until this player can claim (0 = can claim now). */
    public long timeUntilClaim(UUID player) {
        Entry entry = data.get(player);
        if (entry == null) {
            return 0;
        }
        return Math.max(0, entry.lastClaim() + cooldownMillis() - System.currentTimeMillis());
    }

    /** The streak a player has right now (0 if they never claimed or their streak ran out). */
    public int currentStreak(UUID player) {
        Entry entry = data.get(player);
        if (entry == null) {
            return 0;
        }
        if (System.currentTimeMillis() - entry.lastClaim() > resetMillis()) {
            return 0;
        }
        return entry.streak();
    }

    /** What the next /daily will pay this player. */
    public double nextReward(UUID player) {
        return rewardFor(currentStreak(player) + 1);
    }

    public ClaimResult claim(UUID player) {
        long now = System.currentTimeMillis();
        long wait = timeUntilClaim(player);
        Entry entry = data.get(player);
        if (wait > 0) {
            int streak = entry == null ? 0 : entry.streak();
            return new ClaimResult(false, streak, 0, rewardFor(streak + 1), wait);
        }
        int streak = (entry != null && now - entry.lastClaim() <= resetMillis()) ? entry.streak() + 1 : 1;
        double reward = rewardFor(streak);
        data.put(player, new Entry(now, streak));
        save();
        if (reward > 0) {
            plugin.getEconomy().deposit(player, reward);
        }
        return new ClaimResult(true, streak, reward, rewardFor(streak + 1), cooldownMillis());
    }
}
