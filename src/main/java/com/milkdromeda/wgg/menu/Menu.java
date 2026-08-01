package com.milkdromeda.wgg.menu;

import com.milkdromeda.wgg.WeirdGunGamePlugin;
import com.milkdromeda.wgg.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Base for every chest GUI in the plugin.
 * <p>
 * The menu is its own {@link InventoryHolder}, so click routing is just an
 * {@code instanceof} check on the inventory holder — no bookkeeping map, and no
 * chance of a stale entry pointing at the wrong screen.
 */
public abstract class Menu implements InventoryHolder {

    protected final WeirdGunGamePlugin plugin;
    protected final Player player;
    private Inventory inventory;

    protected Menu(WeirdGunGamePlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    protected abstract Component title();

    protected abstract int size();

    /** Fills the inventory. Called on open and whenever the menu needs refreshing. */
    protected abstract void render();

    public abstract void onClick(int slot, ClickType click);

    @Override
    public Inventory getInventory() {
        if (inventory == null) {
            inventory = plugin.getServer().createInventory(this, size(), title());
        }
        return inventory;
    }

    public void open() {
        getInventory().clear();
        render();
        player.openInventory(getInventory());
    }

    public void refresh() {
        getInventory().clear();
        render();
    }

    // ------------------------------------------------------------------ helpers

    protected void set(int slot, ItemStack item) {
        if (slot >= 0 && slot < getInventory().getSize()) {
            getInventory().setItem(slot, item);
        }
    }

    protected static ItemStack icon(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm(name));
        meta.lore(loreLines.stream().map(Text::mm).toList());
        item.setItemMeta(meta);
        return item;
    }

    protected static ItemStack glowing(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    /** Paints the border and any empty slot with dark filler panes. */
    protected void fillEmpty(Material material) {
        ItemStack filler = icon(material, " ", List.of());
        for (int slot = 0; slot < getInventory().getSize(); slot++) {
            if (getInventory().getItem(slot) == null) {
                getInventory().setItem(slot, filler);
            }
        }
    }

    protected void give(ItemStack item) {
        player.getInventory().addItem(item).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    protected void click(float pitch) {
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.6f, pitch);
    }
}
