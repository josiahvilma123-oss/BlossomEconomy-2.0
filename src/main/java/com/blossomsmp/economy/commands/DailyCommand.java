package com.blossomsmp.economy.commands;

import com.blossomsmp.economy.BlossomEconomy;
import com.blossomsmp.economy.DailyManager;
import com.blossomsmp.economy.util.Text;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

/** /daily - claim your daily reward */
public class DailyCommand implements TabExecutor {

    private final BlossomEconomy plugin;

    public DailyCommand(BlossomEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (!plugin.getConfig().getBoolean("daily-rewards.enabled", true)) {
            player.sendMessage(plugin.msg("daily-disabled"));
            return true;
        }
        DailyManager.ClaimResult result = plugin.getDaily().claim(player.getUniqueId());
        if (!result.success()) {
            player.sendMessage(plugin.msg("daily-wait", "%time%", Text.duration(result.waitMillis())));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
            return true;
        }
        player.sendMessage(plugin.msg("daily-claimed",
                "%day%", String.valueOf(result.streak()),
                "%amount%", Text.money(result.reward())));
        player.sendMessage(plugin.msg("daily-next", "%amount%", Text.money(result.nextReward())));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
