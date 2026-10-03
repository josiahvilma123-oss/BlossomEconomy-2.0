package com.blossomsmp.economy.commands;

import com.blossomsmp.economy.BlossomEconomy;
import com.blossomsmp.economy.util.Text;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

/** /balance [player] */
public class BalanceCommand implements TabExecutor {

    private final BlossomEconomy plugin;

    public BalanceCommand(BlossomEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.msg("players-only"));
                return true;
            }
            double balance = plugin.getEconomy().getBalance(player.getUniqueId());
            player.sendMessage(plugin.msg("balance-self", "%amount%", Text.money(balance)));
            return true;
        }
        OfflinePlayer target = CommandUtil.findPlayer(args[0]);
        if (target == null || !plugin.getEconomy().hasAccount(target.getUniqueId())) {
            sender.sendMessage(plugin.msg("player-not-found"));
            return true;
        }
        double balance = plugin.getEconomy().getBalance(target.getUniqueId());
        sender.sendMessage(plugin.msg("balance-other",
                "%player%", CommandUtil.nameOf(target, args[0]), "%amount%", Text.money(balance)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? CommandUtil.onlineNames(args[0]) : List.of();
    }
}
