package com.blossomsmp.economy.commands;

import com.blossomsmp.economy.BlossomEconomy;
import com.blossomsmp.economy.menus.AuctionMenus;
import com.blossomsmp.economy.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** /ah, /ah sell <price>, /ah mine, /ah collect */
public class AuctionCommand implements TabExecutor {

    private final BlossomEconomy plugin;

    public AuctionCommand(BlossomEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (args.length == 0) {
            AuctionMenus.openBrowse(plugin, player, 0);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "sell" -> sell(player, args);
            case "mine", "my", "listings" -> AuctionMenus.openMine(plugin, player);
            case "collect" -> AuctionMenus.openCollect(plugin, player);
            default -> player.sendMessage(plugin.msg("ah-usage"));
        }
        return true;
    }

    private void sell(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.msg("ah-usage"));
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage(plugin.msg("hold-item"));
            return;
        }
        Double price = Text.parseAmount(args[1]);
        double min = plugin.getConfig().getDouble("auction.min-price", 1);
        double max = plugin.getConfig().getDouble("auction.max-price", 1_000_000_000_000D);
        if (price == null || price < min || price > max) {
            player.sendMessage(plugin.msg("ah-price-range", "%min%", Text.money(min), "%max%", Text.shortMoney(max)));
            return;
        }
        int limit = plugin.getConfig().getInt("auction.max-listings", 5);
        if (plugin.getAuctions().getBySeller(player.getUniqueId()).size() >= limit) {
            player.sendMessage(plugin.msg("ah-max", "%max%", String.valueOf(limit)));
            return;
        }
        ItemStack item = hand.clone();
        player.getInventory().setItemInMainHand(null);
        plugin.getAuctions().create(player, item, price);
        player.sendMessage(plugin.msg("ah-listed",
                "%count%", String.valueOf(item.getAmount()),
                "%item%", Text.itemName(item.getType()),
                "%amount%", Text.money(price)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return CommandUtil.filter(List.of("sell", "mine", "collect"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
            return CommandUtil.filter(List.of("100", "1k", "10k", "100k", "1m"), args[1]);
        }
        return List.of();
    }
}
