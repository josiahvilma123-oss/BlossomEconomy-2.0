package com.blossomsmp.economy.commands;

import com.blossomsmp.economy.BlossomEconomy;
import com.blossomsmp.economy.EconomyManager;
import com.blossomsmp.economy.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** /baltop - the 10 richest players */
public class BaltopCommand implements TabExecutor {

    private final BlossomEconomy plugin;

    public BaltopCommand(BlossomEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        EconomyManager eco = plugin.getEconomy();
        sender.sendMessage(Text.color(plugin.getConfig().getString("messages.baltop-header", "&dRichest Players")));
        List<Map.Entry<UUID, Double>> top = eco.top(10);
        String line = plugin.getConfig().getString("messages.baltop-line", "#%rank% %player% - %amount%");
        int rank = 1;
        for (Map.Entry<UUID, Double> entry : top) {
            sender.sendMessage(Text.color(line
                    .replace("%rank%", String.valueOf(rank++))
                    .replace("%player%", eco.getName(entry.getKey()))
                    .replace("%amount%", Text.shortMoney(entry.getValue()))));
        }
        if (sender instanceof Player player) {
            sender.sendMessage(Text.color(plugin.getConfig().getString("messages.baltop-footer", "Your rank: #%rank%")
                    .replace("%rank%", String.valueOf(eco.rank(player.getUniqueId())))));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
