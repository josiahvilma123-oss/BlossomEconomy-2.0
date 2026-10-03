package com.blossomsmp.economy.commands;

import com.blossomsmp.economy.BlossomEconomy;
import com.blossomsmp.economy.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.List;

/** /discord - pink message with a clickable join button */
public class DiscordCommand implements TabExecutor {

    private final BlossomEconomy plugin;

    public DiscordCommand(BlossomEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String link = plugin.getConfig().getString("discord.link", "");
        if (link.isEmpty()) {
            sender.sendMessage(Text.color("&cThe Discord link hasn't been set in the config yet."));
            return true;
        }
        if (!link.startsWith("http://") && !link.startsWith("https://")) {
            link = "https://" + link;
        }
        String message = plugin.getConfig().getString("discord.message", "&#FF69B4&l❀ &7Join our Discord!");
        String button = plugin.getConfig().getString("discord.button", "&#FF69B4&l[CLICK TO JOIN]");
        String hover = plugin.getConfig().getString("discord.hover", "&7Click to open &f%link%");

        Component clickable = Text.component(button)
                .clickEvent(ClickEvent.openUrl(link))
                .hoverEvent(HoverEvent.showText(Text.component(hover.replace("%link%", link))));

        sender.sendMessage(Component.empty());
        sender.sendMessage(Text.component(message.replace("%link%", link)));
        sender.sendMessage(clickable);
        sender.sendMessage(Component.empty());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
