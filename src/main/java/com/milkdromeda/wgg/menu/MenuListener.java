package com.milkdromeda.wgg.menu;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Routes clicks to the {@link Menu} that owns the inventory, and blocks item theft. */
public final class MenuListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Menu menu)) {
            return;
        }
        // Nothing in a WGG menu is ever a real item the player can take.
        event.setCancelled(true);

        if (!event.getInventory().equals(event.getClickedInventory())) {
            return;
        }
        menu.onClick(event.getSlot(), event.getClick());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }
}
