package com.blossomsmp.economy;

import com.blossomsmp.economy.util.Text;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Collections;
import java.util.List;

/**
 * Makes BlossomEconomy the server's money for every plugin that uses Vault
 * (auction house, jobs, orders, scoreboard placeholders...).
 */
@SuppressWarnings("deprecation")
public class VaultEconomy implements Economy {

    private static final String NO_BANKS = "BlossomEconomy does not support banks.";

    private final BlossomEconomy plugin;
    private final EconomyManager eco;

    public VaultEconomy(BlossomEconomy plugin, EconomyManager eco) {
        this.plugin = plugin;
        this.eco = eco;
    }

    // ---- General ----

    @Override
    public boolean isEnabled() {
        return plugin.isEnabled();
    }

    @Override
    public String getName() {
        return "BlossomEconomy";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return 2;
    }

    @Override
    public String format(double amount) {
        return Text.money(amount);
    }

    @Override
    public String currencyNamePlural() {
        return "Dollars";
    }

    @Override
    public String currencyNameSingular() {
        return "Dollar";
    }

    // ---- Accounts ----

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return player != null && eco.hasAccount(player.getUniqueId());
    }

    @Override
    public boolean hasAccount(String playerName) {
        return hasAccount(byName(playerName));
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return player != null && eco.createAccount(player.getUniqueId(), player.getName());
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        return createPlayerAccount(byName(playerName));
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    // ---- Balances ----

    @Override
    public double getBalance(OfflinePlayer player) {
        return player == null ? 0 : eco.getBalance(player.getUniqueId());
    }

    @Override
    public double getBalance(String playerName) {
        return getBalance(byName(playerName));
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) + 0.000001 >= amount;
    }

    @Override
    public boolean has(String playerName, double amount) {
        return has(byName(playerName), amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    // ---- Withdraw / deposit ----

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (player == null) {
            return new EconomyResponse(0, 0, ResponseType.FAILURE, "Player cannot be null");
        }
        if (amount < 0) {
            return new EconomyResponse(0, getBalance(player), ResponseType.FAILURE, "Cannot withdraw a negative amount");
        }
        if (!eco.withdraw(player.getUniqueId(), amount)) {
            return new EconomyResponse(0, getBalance(player), ResponseType.FAILURE, "Insufficient funds");
        }
        return new EconomyResponse(amount, getBalance(player), ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return withdrawPlayer(byName(playerName), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (player == null) {
            return new EconomyResponse(0, 0, ResponseType.FAILURE, "Player cannot be null");
        }
        if (amount < 0) {
            return new EconomyResponse(0, getBalance(player), ResponseType.FAILURE, "Cannot deposit a negative amount");
        }
        eco.createAccount(player.getUniqueId(), player.getName());
        eco.deposit(player.getUniqueId(), amount);
        return new EconomyResponse(amount, getBalance(player), ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return depositPlayer(byName(playerName), amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    // ---- Banks (not supported) ----

    @Override
    public EconomyResponse createBank(String name, String player) {
        return noBanks();
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    public List<String> getBanks() {
        return Collections.emptyList();
    }

    // ---- Helpers ----

    private static EconomyResponse noBanks() {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, NO_BANKS);
    }

    private static OfflinePlayer byName(String name) {
        return name == null ? null : Bukkit.getOfflinePlayer(name);
    }
}
