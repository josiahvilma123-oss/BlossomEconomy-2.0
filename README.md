# ❀ BlossomEconomy

The all-in-one economy plugin for Blossom SMP (Paper 1.21.11, needs Vault).

## Features
- Money system that every Vault plugin uses (auction house, Jobs, Orders, TAB scoreboard)
- `/balance [player]` (aliases `/bal`, `/money`)
- `/pay <player> <amount>` (supports `2.5k`, `1m`)
- `/baltop` - top 10 richest players
- **Live market**: selling an item lowers its price, buying raises it, prices slowly recover
- `/shop` - pink shop menu with live prices and ▲▼ trends
- `/ah` - built-in auction house (`/ah sell <price>`, `/ah mine`, `/ah collect`), with tax and expiry
- About 190 items priced by value
- `/sell` sells the item in your hand
- Items not in the price list are priced automatically from their crafting recipe
- Holding an item shows its value above the hotbar
- Damaged items sell for less, enchantments add value, enchanted books can be sold
- `/worth` - live price of the item in your hand
- `/eco give|take|set|reset <player> <amount>` `/eco reload` and `/eco resetmarket` (permission `blossom.admin`)
- PvP kill rewards with an anti-farm cooldown
- `/discord` with a clickable join button
- `/daily` rewards with login streaks (day 1 $100 up to day 7 $1,000)
- Starting balance for new players
- Everything (prices, messages, colours) is editable in `config.yml`

Data files in `plugins/BlossomEconomy/`: `balances.yml`, `market.yml`, `auctions.yml`.

## Building the .jar

### Option 1: IntelliJ IDEA (on your PC)
1. Install **IntelliJ IDEA Community** (free). When it asks, let it download **JDK 21**.
2. File > Open > select this `BlossomEconomy` folder (the one with `pom.xml`).
3. Wait for it to finish loading (bottom-right progress bar).
4. Open the **Maven** tab on the right > BlossomEconomy > Lifecycle > double-click **package**.
5. Your plugin is at `target/BlossomEconomy.jar`.

### Option 2: GitHub (no installing)
1. Make a new repository on GitHub and upload all these files (including the `.github` folder).
2. Go to the **Actions** tab and wait for the green tick.
3. Click the finished run and download **BlossomEconomy** at the bottom.

## Installing on the server
1. Make a backup.
2. Remove the old `BlossomSMP.jar`, `EconomyShopGUI`, `zAuctionHouse` and `zMenu` if installed.
3. EssentialsX is not needed.
4. Remove the `sell: - sellgui` alias from `commands.yml`.
5. Put `BlossomEconomy.jar` in `plugins` and restart.
6. Upgrading from 1.0: delete the old `plugins/BlossomEconomy/config.yml` (keep `balances.yml`) so the new config is created.
