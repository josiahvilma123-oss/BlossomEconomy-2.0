package com.blossomsmp.economy.commands;

import com.blossomsmp.economy.BlossomEconomy;
import com.blossomsmp.economy.EconomyManager;
import com.blossomsmp.economy.util.Text;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

/** /pay <player> <amount> */
public class PayCommand implements TabExecutor {

    private final BlossomEconomy plugin;

    public PayCommand(BlossomEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(Text.color("&cUsage: /pay <player> <amount>"));
            return true;
        }
        EconomyManager eco = plugin.getEconomy();
        OfflinePlayer target = CommandUtil.findPlayer(args[0]);
        if (target == null || !eco.hasAccount(target.getUniqueId())) {
            player.sendMessage(plugin.msg("player-not-found"));
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(plugin.msg("pay-self"));
            return true;
        }
        Double amount = Text.parseAmount(args[1]);
        if (amount == null) {
            player.sendMessage(plugin.msg("invalid-amount"));
            return true;
        }
        double min = plugin.getConfig().getDouble("min-pay", 1);
        if (amount < min) {
            player.sendMessage(plugin.msg("pay-min", "%amount%", Text.money(min)));
            return true;
        }
        if (!eco.transfer(player.getUniqueId(), target.getUniqueId(), amount)) {
            player.sendMessage(plugin.msg("not-enough-money",
                    "%amount%", Text.money(eco.getBalance(player.getUniqueId()))));
            return true;
        }
        String targetName = CommandUtil.nameOf(target, args[0]);
        player.sendMessage(plugin.msg("pay-sent", "%amount%", Text.money(amount), "%player%", targetName));
        Player online = target.getPlayer();
        if (online != null) {
            online.sendMessage(plugin.msg("pay-received", "%amount%", Text.money(amount), "%player%", player.getName()));
            online.playSound(online.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return CommandUtil.onlineNames(args[0]);
        }
        if (args.length == 2) {
            return CommandUtil.filter(List.of("100", "1k", "10k", "100k", "1m"), args[1]);
        }
        return List.of();
    }
}
