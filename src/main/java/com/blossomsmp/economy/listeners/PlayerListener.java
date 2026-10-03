package com.blossomsmp.economy.listeners;

import com.blossomsmp.economy.BlossomEconomy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerListener implements Listener {

    private final BlossomEconomy plugin;

    public PlayerListener(BlossomEconomy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getEconomy().createAccount(player.getUniqueId(), player.getName());

        if (plugin.getConfig().getBoolean("daily-rewards.enabled", true)
                && plugin.getDaily().timeUntilClaim(player.getUniqueId()) == 0) {
            player.sendMessage(plugin.msg("daily-reminder"));
        }

        int waiting = plugin.getAuctions().getReturns(player.getUniqueId()).size();
        if (waiting > 0) {
            player.sendMessage(plugin.msg("ah-collect-reminder", "%count%", String.valueOf(waiting)));
        }
    }
}
