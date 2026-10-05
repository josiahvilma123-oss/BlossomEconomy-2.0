package com.blossomsmp.economy;

import com.blossomsmp.economy.commands.AuctionCommand;
import com.blossomsmp.economy.commands.BalanceCommand;
import com.blossomsmp.economy.commands.DailyCommand;
import com.blossomsmp.economy.commands.DiscordCommand;
import com.blossomsmp.economy.commands.BaltopCommand;
import com.blossomsmp.economy.commands.EcoCommand;
import com.blossomsmp.economy.commands.PayCommand;
import com.blossomsmp.economy.commands.SellCommand;
import com.blossomsmp.economy.commands.ShopCommand;
import com.blossomsmp.economy.commands.WorthCommand;
import com.blossomsmp.economy.listeners.KillRewardListener;
import com.blossomsmp.economy.listeners.MenuListener;
import com.blossomsmp.economy.listeners.PlayerListener;
import com.blossomsmp.economy.listeners.PriceDisplayTask;
import com.blossomsmp.economy.menus.MenuHolder;
import com.blossomsmp.economy.menus.Menus;
import com.blossomsmp.economy.util.Text;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * BlossomEconomy - the all-in-one Blossom SMP economy.
 * Money, live market prices, /shop, /sell, /ah, /pay, /baltop, /eco, /worth and PvP kill rewards.
 */
public final class BlossomEconomy extends JavaPlugin {

    private EconomyManager economy;
    private MarketManager market;
    private ShopManager shop;
    private AuctionManager auctions;
    private DailyManager daily;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        economy = new EconomyManager(this);
        economy.load();
        market = new MarketManager(this);
        market.loadConfig();
        market.loadData();
        shop = new ShopManager(this);
        shop.load();
        auctions = new AuctionManager(this);
        auctions.load();
        daily = new DailyManager(this);
        daily.load();

        // Become the server's money for every Vault plugin
        getServer().getServicesManager().register(Economy.class, new VaultEconomy(this, economy),
                this, ServicePriority.Highest);

        register("balance", new BalanceCommand(this));
        register("pay", new PayCommand(this));
        register("baltop", new BaltopCommand(this));
        register("eco", new EcoCommand(this));
        register("shop", new ShopCommand(this));
        register("sell", new SellCommand(this));
        register("worth", new WorthCommand(this));
        register("ah", new AuctionCommand(this));
        register("daily", new DailyCommand(this));
        register("sellgui", new com.blossomsmp.economy.commands.SellGuiCommand(this));
        register("discord", new DiscordCommand(this));

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new MenuListener(this), this);
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new KillRewardListener(this), this);

        long interval = 20L * 60L * Math.max(1, getConfig().getInt("autosave-minutes", 5));
        getServer().getScheduler().runTaskTimer(this, () -> {
            economy.saveIfDirty();
            market.saveIfDirty();
        }, interval, interval);

        long recovery = 20L * 60L * Math.max(1, getConfig().getInt("market.recovery-minutes", 5));
        getServer().getScheduler().runTaskTimer(this, market::recover, recovery, recovery);

        // Show the value of the held item above the hotbar
        getServer().getScheduler().runTaskTimer(this, new PriceDisplayTask(this), 40L, 10L);

        // Expire old auction listings every minute
        getServer().getScheduler().runTaskTimer(this, auctions::checkExpired, 20L * 60L, 20L * 60L);

        for (Player player : Bukkit.getOnlinePlayers()) {
            economy.createAccount(player.getUniqueId(), player.getName());
        }
        // Share placeholders (%blossom_streak% etc.) with the scoreboard
        if (pm.getPlugin("PlaceholderAPI") != null) {
            new com.blossomsmp.economy.hooks.BlossomPlaceholders(this).register();
            getLogger().info("Hooked into PlaceholderAPI (%blossom_...% placeholders).");
        }
        getLogger().info("BlossomEconomy is enabled and registered with Vault.");
    }

    @Override
    public void onDisable() {
        // Sell anything left in open sell menus so nobody loses items on shutdown
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof MenuHolder holder) {
                if (holder.getType() == MenuHolder.Type.SELL && economy != null && market != null) {
                    Menus.sellMenuContents(this, player, top);
                }
                player.closeInventory();
            }
        }
        if (economy != null) {
            economy.save();
        }
        if (market != null) {
            market.save();
        }
        if (auctions != null) {
            auctions.save();
        }
        if (daily != null) {
            daily.save();
        }
        getServer().getServicesManager().unregisterAll(this);
    }

    private void register(String name, TabExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Command '" + name + "' is missing from plugin.yml!");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    /** /eco reload */
    public void reload() {
        reloadConfig();
        market.loadConfig();
        shop.load();
    }

    /** Gets a message from config.yml with the prefix and replacements ("%key%", "value", ...). */
    public String msg(String key, String... replacements) {
        String text = getConfig().getString("messages." + key, "&cMissing message: " + key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        return Text.color(getConfig().getString("messages.prefix", "") + text);
    }

    public EconomyManager getEconomy() {
        return economy;
    }

    public ShopManager getShop() {
        return shop;
    }

    public MarketManager getMarket() {
        return market;
    }

    public AuctionManager getAuctions() {
        return auctions;
    }

    public DailyManager getDaily() {
        return daily;
    }
}
