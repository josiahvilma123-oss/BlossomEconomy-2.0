package com.blossomsmp.economy.listeners;

import com.blossomsmp.economy.BlossomEconomy;
import com.blossomsmp.economy.EconomyManager;
import com.blossomsmp.economy.util.Text;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.HashMap;
import java.util.Map;

public class KillRewardListener implements Listener {

    private final BlossomEconomy plugin;
    private final Map<String, Long> lastRewarded = new HashMap<>();

    public KillRewardListener(BlossomEconomy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(PlayerDeathEvent event) {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("kill-rewards.enabled", true)) {
            return;
        }
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        // Anti kill-farming: only reward the same killer/victim pair once per cooldown
        long now = System.currentTimeMillis();
        long cooldownMs = Math.max(0, config.getLong("kill-rewards.same-victim-cooldown", 300)) * 1000L;
        String key = killer.getUniqueId() + ":" + victim.getUniqueId();
        Long last = lastRewarded.get(key);
        if (last != null && now - last < cooldownMs) {
            return;
        }
        lastRewarded.put(key, now);
        lastRewarded.values().removeIf(time -> now - time > Math.max(cooldownMs, 60_000L));

        EconomyManager eco = plugin.getEconomy();
        double reward = Math.max(0, config.getDouble("kill-rewards.amount", 50));
        double stealPercent = Math.max(0, Math.min(100, config.getDouble("kill-rewards.steal-percent", 0)));
        if (stealPercent > 0) {
            double stolen = Math.round(eco.getBalance(victim.getUniqueId()) * stealPercent) / 100.0;
            if (stolen > 0 && eco.withdraw(victim.getUniqueId(), stolen)) {
                reward += stolen;
                victim.sendMessage(plugin.msg("kill-lost",
                        "%player%", killer.getName(), "%amount%", Text.money(stolen)));
            }
        }
        if (reward <= 0) {
            return;
        }
        eco.deposit(killer.getUniqueId(), reward);
        killer.sendMessage(plugin.msg("kill-reward",
                "%player%", victim.getName(), "%amount%", Text.money(reward)));
    }
}
