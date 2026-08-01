package com.milkdromeda.wgg.menu;

import com.milkdromeda.wgg.WeirdGunGamePlugin;
import com.milkdromeda.wgg.gun.GunBlueprint;
import com.milkdromeda.wgg.gun.GunItem;
import com.milkdromeda.wgg.gun.GunPreset;
import com.milkdromeda.wgg.gun.GunStats;
import com.milkdromeda.wgg.gun.PartSection;
import com.milkdromeda.wgg.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Ready-made guns. Left-click takes one, right-click loads it into the bench to tinker with. */
public final class ArmoryMenu extends Menu {

    private static final int[] PRESET_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    private static final int BACK_SLOT = 49;

    public ArmoryMenu(WeirdGunGamePlugin plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected Component title() {
        return Text.mm("<dark_gray>⚙ </dark_gray><gradient:#facc15:#ff8a3d><bold>ARMOURY</bold></gradient>");
    }

    @Override
    protected int size() {
        return 54;
    }

    @Override
    protected void render() {
        List<GunPreset> presets = GunPreset.all();
        for (int i = 0; i < presets.size() && i < PRESET_SLOTS.length; i++) {
            set(PRESET_SLOTS[i], presetIcon(presets.get(i)));
        }

        set(BACK_SLOT, icon(Material.ARROW, "<gray><bold>Back to the bench</bold>",
                List.of("<dark_gray>Return to gun assembly")));

        fillEmpty(Material.BLACK_STAINED_GLASS_PANE);
    }

    private ItemStack presetIcon(GunPreset preset) {
        GunBlueprint blueprint = preset.toBlueprint();
        GunStats stats = blueprint.stats();

        List<String> lore = new ArrayList<>();
        lore.add("<dark_gray><italic>" + preset.description() + "</italic>");
        lore.add("");
        lore.add("<gray>" + stats.fireMode().displayName()
                + " <dark_gray>•</dark_gray> <gray>Power <white>" + stats.powerScore()
                + " <dark_gray>•</dark_gray> <gray>Weird <white>" + stats.weirdnessScore());
        lore.add("<gray>Damage <white>" + Text.num(stats.damage())
                + (stats.pellets() > 1 ? " <dark_gray>x" + stats.pellets() : "")
                + "   <gray>Mag <white>" + stats.magSize());
        lore.add("<gray>DPS <white>" + Text.num(stats.sustainedDps())
                + "   <gray>Range <white>" + Text.num(stats.range()) + "<dark_gray>m");
        lore.add("");
        for (PartSection section : PartSection.values()) {
            lore.add("<dark_gray> " + section.displayName() + ": </dark_gray>"
                    + blueprint.get(section).rarity().color() + blueprint.get(section).name());
        }
        lore.add("");
        lore.add("<#4ade80>Left-click <gray>to take this gun");
        lore.add("<#7dd3fc>Right-click <gray>to load it into the bench");

        return icon(preset.icon(), "<#facc15><bold>" + preset.name() + "</bold>", lore);
    }

    @Override
    public void onClick(int slot, ClickType clickType) {
        if (slot == BACK_SLOT) {
            click(0.9f);
            new WorkbenchMenu(plugin, player).open();
            return;
        }

        List<GunPreset> presets = GunPreset.all();
        for (int i = 0; i < PRESET_SLOTS.length && i < presets.size(); i++) {
            if (slot != PRESET_SLOTS[i]) {
                continue;
            }
            GunPreset preset = presets.get(i);
            if (clickType.isRightClick()) {
                plugin.benches().set(player, preset.toBlueprint());
                player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 0.8f, 1.2f);
                new WorkbenchMenu(plugin, player).open();
            } else {
                give(GunItem.build(preset.toBlueprint()));
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.4f);
                player.sendMessage(Text.msg("<gray>Took <white>" + preset.name() + "</white>.</gray>"));
                player.closeInventory();
            }
            return;
        }
    }
}
