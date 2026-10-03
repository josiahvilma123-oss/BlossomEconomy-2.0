package com.blossomsmp.economy.commands;

import com.blossomsmp.economy.BlossomEconomy;
import com.blossomsmp.economy.MarketManager;
import com.blossomsmp.economy.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** /worth - live price of the item in your hand */
public class WorthCommand implements TabExecutor {

    private final BlossomEconomy plugin;

    public WorthCommand(BlossomEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage(plugin.msg("hold-item"));
            return true;
        }
        MarketManager market = plugin.getMarket();
        if (!market.canSell(hand)) {
            player.sendMessage(plugin.msg("worthless"));
            return true;
        }
        player.sendMessage(plugin.msg("worth",
                "%item%", Text.itemName(hand.getType()),
                "%amount%", Text.money(market.sellPrice(hand)),
                "%stack%", Text.money(market.quoteSell(hand)),
                "%trend%", Text.color(market.trend(hand.getType()))));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
