package com.blossomsmp.economy.hooks;

import com.blossomsmp.economy.BlossomEconomy;
import com.blossomsmp.economy.util.Text;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Locale;

/**
 * Placeholders for TAB / holograms:
 *   %blossom_streak%       -> 3
 *   %blossom_daily%        -> Ready!  or  5h 20m
 *   %blossom_next_reward%  -> $200
 *   %blossom_balance%      -> $4.4M
 */
public final class BlossomPlaceholders extends PlaceholderExpansion {

    private final BlossomEconomy plugin;

    public BlossomPlaceholders(BlossomEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "blossom";
    }

    @Override
    public String getAuthor() {
        return "BlossomSMP";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // stay registered after /papi reload
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) {
            return "";
        }
        switch (params.toLowerCase(Locale.ROOT)) {
            case "streak":
                return String.valueOf(plugin.getDaily().currentStreak(player.getUniqueId()));
            case "daily": {
                long wait = plugin.getDaily().timeUntilClaim(player.getUniqueId());
                return wait <= 0 ? "Ready!" : Text.duration(wait);
            }
            case "next_reward":
                return Text.shortMoney(plugin.getDaily().nextReward(player.getUniqueId()));
            case "balance":
                return Text.shortMoney(plugin.getEconomy().getBalance(player.getUniqueId()));
            default:
                return null;
        }
    }
}
