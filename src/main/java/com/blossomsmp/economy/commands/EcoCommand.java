package com.blossomsmp.economy.commands;

import com.blossomsmp.economy.BlossomEconomy;
import com.blossomsmp.economy.EconomyManager;
import com.blossomsmp.economy.util.Text;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.List;
import java.util.Locale;

/** /eco <give|take|set|reset> <player> [amount]  and  /eco reload */
public class EcoCommand implements TabExecutor {

    private static final List<String> ACTIONS = List.of("give", "take", "set", "reset", "reload", "resetmarket");

    private final BlossomEconomy plugin;

    public EcoCommand(BlossomEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("blossom.admin")) {
            sender.sendMessage(Text.color("&cYou don't have permission."));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reload();
            sender.sendMessage(plugin.msg("eco-reloaded"));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("resetmarket")) {
            plugin.getMarket().resetAll();
            sender.sendMessage(plugin.msg("market-reset"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.msg("eco-usage"));
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        EconomyManager eco = plugin.getEconomy();
        OfflinePlayer target = CommandUtil.findPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.msg("player-not-found"));
            return true;
        }
        eco.createAccount(target.getUniqueId(), target.getName());

        if (action.equals("reset")) {
            eco.setBalance(target.getUniqueId(), plugin.getConfig().getDouble("starting-balance", 0));
        } else {
            if (args.length < 3) {
                sender.sendMessage(plugin.msg("eco-usage"));
                return true;
            }
            Double amount = action.equals("set") && args[2].equals("0") ? Double.valueOf(0) : Text.parseAmount(args[2]);
            if (amount == null) {
                sender.sendMessage(plugin.msg("invalid-amount"));
                return true;
            }
            double current = eco.getBalance(target.getUniqueId());
            switch (action) {
                case "give" -> eco.deposit(target.getUniqueId(), amount);
                case "take" -> eco.setBalance(target.getUniqueId(), current - amount);
                case "set" -> eco.setBalance(target.getUniqueId(), amount);
                default -> {
                    sender.sendMessage(plugin.msg("eco-usage"));
                    return true;
                }
            }
        }
        eco.save();
        sender.sendMessage(plugin.msg("eco-done",
                "%player%", CommandUtil.nameOf(target, args[1]),
                "%amount%", Text.money(eco.getBalance(target.getUniqueId()))));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("blossom.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return CommandUtil.filter(ACTIONS, args[0]);
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("reload") && !args[0].equalsIgnoreCase("resetmarket")) {
            return CommandUtil.onlineNames(args[1]);
        }
        if (args.length == 3 && !args[0].equalsIgnoreCase("reset") && !args[0].equalsIgnoreCase("reload")
                && !args[0].equalsIgnoreCase("resetmarket")) {
            return CommandUtil.filter(List.of("100", "1k", "10k", "100k", "1m"), args[2]);
        }
        return List.of();
    }
}
