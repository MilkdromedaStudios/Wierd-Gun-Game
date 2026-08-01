package com.milkdromeda.wgg.menu;

import com.milkdromeda.wgg.WeirdGunGamePlugin;
import com.milkdromeda.wgg.gun.GunBlueprint;
import com.milkdromeda.wgg.gun.GunPart;
import com.milkdromeda.wgg.gun.GunStats;
import com.milkdromeda.wgg.gun.PartRegistry;
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

/** The ten parts available for one section. Click one to fit it. */
public final class SectionMenu extends Menu {

    /** Two centred rows of five. */
    private static final int[] PART_SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};

    private static final int INFO_SLOT = 4;
    private static final int BACK_SLOT = 49;

    private final PartSection section;

    public SectionMenu(WeirdGunGamePlugin plugin, Player player, PartSection section) {
        super(plugin, player);
        this.section = section;
    }

    @Override
    protected Component title() {
        return Text.mm("<dark_gray>⚙ </dark_gray>" + section.color() + "<bold>"
                + section.displayName().toUpperCase() + "</bold>");
    }

    @Override
    protected int size() {
        return 54;
    }

    @Override
    protected void render() {
        GunBlueprint blueprint = plugin.benches().get(player);
        GunPart fitted = blueprint.get(section);
        List<GunPart> parts = PartRegistry.of(section);

        set(INFO_SLOT, icon(section.icon(), section.color() + "<bold>" + section.displayName() + "</bold>",
                List.of("<gray>" + section.blurb(), "",
                        "<dark_gray>" + parts.size() + " parts available")));

        for (int i = 0; i < parts.size() && i < PART_SLOTS.length; i++) {
            set(PART_SLOTS[i], partIcon(parts.get(i), blueprint, fitted));
        }

        set(BACK_SLOT, icon(Material.ARROW, "<gray><bold>Back to the bench</bold>",
                List.of("<dark_gray>Return to gun assembly")));

        fillEmpty(Material.BLACK_STAINED_GLASS_PANE);
    }

    private ItemStack partIcon(GunPart part, GunBlueprint blueprint, GunPart fitted) {
        boolean selected = part.id().equals(fitted.id());

        List<String> lore = new ArrayList<>();
        lore.add("<dark_gray><italic>" + part.flavor() + "</italic>");
        lore.add("");
        lore.add(part.rarity().color() + part.rarity().displayName());
        lore.add("");
        for (String perk : part.perks()) {
            lore.add("<dark_gray> ▸ </dark_gray><gray>" + perk);
        }
        lore.add("");
        lore.addAll(previewDelta(blueprint, part));
        lore.add("");
        lore.add(selected
                ? "<#4ade80><bold>✔ FITTED</bold>"
                : "<dark_gray>Click to fit this part");

        ItemStack item = icon(part.icon(), part.rarity().color() + "<bold>" + part.name() + "</bold>", lore);
        return selected ? glowing(item) : item;
    }

    /**
     * Shows what swapping to this part would do to the finished gun. Cheap to
     * compute — assembling a blueprint is just six lambdas and a clamp.
     */
    private List<String> previewDelta(GunBlueprint current, GunPart candidate) {
        GunStats before = current.stats();

        GunBlueprint hypothetical = GunBlueprint.deserialize(current.serialize());
        hypothetical.set(candidate);
        GunStats after = hypothetical.stats();

        List<String> lines = new ArrayList<>();
        lines.add("<#7dd3fc><bold>If fitted</bold>");
        addDelta(lines, "Damage", before.damage(), after.damage(), false);
        addDelta(lines, "DPS", before.sustainedDps(), after.sustainedDps(), false);
        addDelta(lines, "Magazine", before.magSize(), after.magSize(), false);
        addDelta(lines, "Range", before.range(), after.range(), false);
        addDelta(lines, "Spread", before.spread(), after.spread(), true);
        addDelta(lines, "Reload", before.reloadTicks() / 20.0, after.reloadTicks() / 20.0, true);
        if (lines.size() == 1) {
            lines.add("<dark_gray> ▸ </dark_gray><gray>No change to the core stats");
        }
        return lines;
    }

    /** @param lowerIsBetter true for stats like spread and reload time */
    private void addDelta(List<String> lines, String label, double before, double after, boolean lowerIsBetter) {
        double delta = after - before;
        if (Math.abs(delta) < 0.05) {
            return;
        }
        boolean good = lowerIsBetter ? delta < 0 : delta > 0;
        String color = good ? "<#4ade80>" : "<#f43f5e>";
        String sign = delta > 0 ? "+" : "";
        lines.add("<dark_gray> ▸ </dark_gray><gray>" + label + " " + color + sign + Text.num(delta)
                + " <dark_gray>(" + Text.num(after) + ")");
    }

    @Override
    public void onClick(int slot, ClickType clickType) {
        if (slot == BACK_SLOT) {
            click(0.9f);
            new WorkbenchMenu(plugin, player).open();
            return;
        }

        List<GunPart> parts = PartRegistry.of(section);
        for (int i = 0; i < PART_SLOTS.length && i < parts.size(); i++) {
            if (slot == PART_SLOTS[i]) {
                GunPart part = parts.get(i);
                plugin.benches().get(player).set(part);
                player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 0.8f, 1.3f);
                player.sendActionBar(Text.mm(section.color() + section.displayName()
                        + " <dark_gray>→</dark_gray> " + part.rarity().color() + part.name()));
                refresh();
                return;
            }
        }
    }
}
