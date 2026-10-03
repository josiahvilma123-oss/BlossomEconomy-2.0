package com.blossomsmp.economy.commands;

import com.blossomsmp.economy.BlossomEconomy;
import com.blossomsmp.economy.menus.Menus;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

/** /sell - opens the sell chest. /sell hand - sells the item in your hand */
public class SellCommand implements TabExecutor {

    private final BlossomEconomy plugin;

    public SellCommand(BlossomEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (!plugin.getConfig().getBoolean("allow-creative-selling", true)
                && player.getGameMode() == GameMode.CREATIVE) {
            player.sendMessage(plugin.msg("creative-sell-blocked"));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("hand")) {
            Menus.sellHand(plugin, player);
        } else {
            Menus.openSell(plugin, player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && "hand".startsWith(args[0].toLowerCase())) {
            return List.of("hand");
        }
        return List.of();
    }
}
