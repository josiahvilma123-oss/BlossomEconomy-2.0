package com.blossomsmp.economy.menus;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/** Marks an inventory as one of our menus and remembers what each slot does. */
public class MenuHolder implements InventoryHolder {

    public enum Type {
        SHOP_MAIN,
        SHOP_CATEGORY,
        SELL,
        AH_BROWSE,
        AH_CONFIRM,
        AH_MINE,
        AH_COLLECT
    }

    private final Type type;
    private final String data;
    private final int page;
    private final Map<Integer, String> slotActions = new HashMap<>();
    private Inventory inventory;

    public MenuHolder(Type type, String data) {
        this(type, data, 0);
    }

    public MenuHolder(Type type, String data, int page) {
        this.type = type;
        this.data = data;
        this.page = page;
    }

    public Type getType() {
        return type;
    }

    /** Category id for shop menus, listing id for the confirm menu. */
    public String getData() {
        return data;
    }

    public int getPage() {
        return page;
    }

    public Map<Integer, String> getSlotActions() {
        return slotActions;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
