package com.milkdromeda.wgg.menu;

import com.milkdromeda.wgg.WeirdGunGamePlugin;
import com.milkdromeda.wgg.combat.Knife;
import com.milkdromeda.wgg.combat.KnifeItem;
import com.milkdromeda.wgg.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Ten melee sidearms. Click one to take it. */
public final class KnifeMenu extends Menu {

    private static final int[] KNIFE_SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};
    private static final int BACK_SLOT = 49;

    public KnifeMenu(WeirdGunGamePlugin plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected Component title() {
        return Text.mm("<dark_gray>⚙ </dark_gray><gradient:#7dd3fc:#c084fc><bold>KNIFE RACK</bold></gradient>");
    }

    @Override
    protected int size() {
        return 54;
    }

    @Override
    protected void render() {
        List<Knife> knives = Knife.all();
        for (int i = 0; i < knives.size() && i < KNIFE_SLOTS.length; i++) {
            set(KNIFE_SLOTS[i], knifeIcon(knives.get(i)));
        }

        set(4, icon(Material.IRON_SWORD, "<#7dd3fc><bold>Knife Rack</bold>",
                List.of("<gray>Every gun build deserves a backup.",
                        "<gray>Backstabs hit from behind for extra damage.",
                        "",
                        "<dark_gray>" + knives.size() + " knives available")));

        set(BACK_SLOT, icon(Material.ARROW, "<gray><bold>Back to the bench</bold>",
                List.of("<dark_gray>Return to gun assembly")));

        fillEmpty(Material.BLACK_STAINED_GLASS_PANE);
    }

    private ItemStack knifeIcon(Knife knife) {
        List<String> lore = new ArrayList<>();
        lore.add("<dark_gray><italic>" + knife.flavor() + "</italic>");
        lore.add("");
        lore.add(knife.rarity().color() + knife.rarity().displayName());
        lore.add("");
        lore.add("<gray>Damage <white>" + Text.num(knife.damage())
                + "   <gray>Speed <white>" + Text.num(knife.attackSpeed()) + "<dark_gray>/s");
        if (knife.backstab() > 1.0) {
            lore.add("<gray>Backstab <white>x" + Text.num(knife.backstab()));
        }
        if (knife.lifesteal() > 0) {
            lore.add("<gray>Lifesteal <white>" + Math.round(knife.lifesteal() * 100) + "%");
        }
        if (knife.bossBonus() > 1.0) {
            lore.add("<gray>Vs. Superbox <white>x" + Text.num(knife.bossBonus()));
        }
        if (!knife.effects().isEmpty()) {
            lore.add("");
            for (Knife.KnifeEffect effect : knife.effects()) {
                lore.add("<dark_gray> ▸ </dark_gray><#ffc9f0>" + effect.displayName()
                        + " <dark_gray>— " + effect.description());
            }
        }
        lore.add("");
        lore.add("<dark_gray>Click to take this knife");

        return icon(knife.icon(), knife.rarity().color() + "<bold>" + knife.name() + "</bold>", lore);
    }

    @Override
    public void onClick(int slot, ClickType clickType) {
        if (slot == BACK_SLOT) {
            click(0.9f);
            new WorkbenchMenu(plugin, player).open();
            return;
        }

        List<Knife> knives = Knife.all();
        for (int i = 0; i < KNIFE_SLOTS.length && i < knives.size(); i++) {
            if (slot == KNIFE_SLOTS[i]) {
                Knife knife = knives.get(i);
                give(KnifeItem.build(knife));
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.6f);
                player.sendMessage(Text.msg("<gray>Took the <white>" + knife.name() + "</white>.</gray>"));
                player.closeInventory();
                return;
            }
        }
    }
}
