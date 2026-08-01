package com.milkdromeda.wgg.gun;

import com.milkdromeda.wgg.util.Keys;
import com.milkdromeda.wgg.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a {@link GunBlueprint} into a real, holdable item and back again.
 * <p>
 * Only the part ids and the current ammo count live on the item — every stat is
 * recomputed from the parts on demand. That means rebalancing a part in
 * {@link PartRegistry} instantly updates every gun already in the world.
 */
public final class GunItem {

    private GunItem() {
    }

    /** The item material stands in for the gun's archetype until a resource pack says otherwise. */
    private static Material materialFor(GunStats stats) {
        return switch (stats.fireMode()) {
            case AUTO -> Material.DIAMOND_HOE;
            case BURST -> Material.GOLDEN_HOE;
            case CHARGE -> Material.NETHERITE_HOE;
            case SPINUP -> Material.NETHERITE_HOE;
            case SEMI -> Material.IRON_HOE;
        };
    }

    public static ItemStack build(GunBlueprint blueprint) {
        GunStats stats = blueprint.stats();
        ItemStack item = new ItemStack(materialFor(stats));
        ItemMeta meta = item.getItemMeta();

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.GUN_PARTS, PersistentDataType.STRING, blueprint.serialize());
        pdc.set(Keys.GUN_AMMO, PersistentDataType.INTEGER, stats.magSize());
        if (blueprint.customName() != null) {
            pdc.set(Keys.GUN_NAME, PersistentDataType.STRING, blueprint.customName());
        }

        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);

        decorate(item, blueprint, stats, stats.magSize());
        return item;
    }

    public static boolean isGun(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(Keys.GUN_PARTS, PersistentDataType.STRING);
    }

    public static GunBlueprint blueprintOf(ItemStack item) {
        if (!isGun(item)) {
            return null;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        GunBlueprint blueprint = GunBlueprint.deserialize(pdc.get(Keys.GUN_PARTS, PersistentDataType.STRING));
        String name = pdc.get(Keys.GUN_NAME, PersistentDataType.STRING);
        if (name != null) {
            blueprint.setCustomName(name);
        }
        return blueprint;
    }

    public static int ammoOf(ItemStack item) {
        if (!isGun(item)) {
            return 0;
        }
        Integer ammo = item.getItemMeta().getPersistentDataContainer()
                .get(Keys.GUN_AMMO, PersistentDataType.INTEGER);
        return ammo == null ? 0 : ammo;
    }

    /** Writes a new ammo count and refreshes the item's name, lore and ammo bar. */
    public static void setAmmo(ItemStack item, int ammo) {
        GunBlueprint blueprint = blueprintOf(item);
        if (blueprint == null) {
            return;
        }
        GunStats stats = blueprint.stats();
        int clamped = Math.max(0, Math.min(stats.magSize(), ammo));

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.GUN_AMMO, PersistentDataType.INTEGER, clamped);
        item.setItemMeta(meta);

        decorate(item, blueprint, stats, clamped);
    }

    // ------------------------------------------------------------------- lore

    private static void decorate(ItemStack item, GunBlueprint blueprint, GunStats stats, int ammo) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm("<gradient:#ff8a3d:#ff4fd8><bold>" + blueprint.displayName() + "</bold></gradient>"));
        meta.lore(buildLore(blueprint, stats, ammo));

        // No durability-bar ammo gauge here: the client only draws that bar for
        // damageable items, and these are unbreakable so a gun can never be
        // destroyed mid-fight. Ammo lives in the lore and the action bar instead.
        item.setItemMeta(meta);
    }

    public static List<Component> buildLore(GunBlueprint blueprint, GunStats stats, int ammo) {
        List<Component> lore = new ArrayList<>();

        lore.add(Text.mm("<dark_gray>" + stats.fireMode().displayName()
                + " • Power " + stats.powerScore()
                + " • Weirdness " + stats.weirdnessScore() + "</dark_gray>"));
        lore.add(Component.empty());

        lore.add(Text.mm("<gray>Ammo <white>" + ammo + "<dark_gray>/</dark_gray><white>" + stats.magSize()
                + "  " + Text.bar(ammo, Math.max(1, stats.magSize()), 10, "<#ff8a3d>", "<dark_gray>")));
        lore.add(Text.mm("<gray>Damage <white>" + Text.num(stats.damage())
                + (stats.pellets() > 1 ? " <dark_gray>x" + stats.pellets() + " pellets</dark_gray>" : "")));
        lore.add(Text.mm("<gray>Fire rate <white>" + Text.num(20.0 / stats.fireRateTicks()) + "<dark_gray>/s</dark_gray>"
                + "   <gray>Reload <white>" + Text.num(stats.reloadTicks() / 20.0) + "<dark_gray>s</dark_gray>"));
        lore.add(Text.mm("<gray>Range <white>" + Text.num(stats.range()) + "<dark_gray>m</dark_gray>"
                + "   <gray>Spread <white>" + Text.num(stats.spread()) + "<dark_gray>°</dark_gray>"));
        if (stats.explosionPower() > 0) {
            lore.add(Text.mm("<gray>Blast <white>" + Text.num(stats.explosionPower())));
        }
        lore.add(Text.mm("<gray>Sustained DPS <white>" + Text.num(stats.sustainedDps())
                + "  " + Text.bar(stats.sustainedDps(), GunStats.MAX_DPS, 10, "<#f43f5e>", "<dark_gray>")));

        if (!stats.traits().isEmpty()) {
            lore.add(Component.empty());
            lore.add(Text.mm("<#ff4fd8><bold>Traits</bold></#ff4fd8>"));
            for (GunTrait trait : stats.traits()) {
                lore.add(Text.mm("<dark_gray> ▸ </dark_gray><#ffc9f0>" + trait.displayName()
                        + " <dark_gray>— " + trait.description() + "</dark_gray>"));
            }
        }

        lore.add(Component.empty());
        lore.add(Text.mm("<#7dd3fc><bold>Parts</bold></#7dd3fc>"));
        for (PartSection section : PartSection.values()) {
            GunPart part = blueprint.get(section);
            lore.add(Text.mm("<dark_gray> " + section.displayName() + ": </dark_gray>"
                    + part.rarity().color() + part.name()));
        }

        lore.add(Component.empty());
        lore.add(Text.mm("<dark_gray>Right-click fire • F reload • Left-click aim</dark_gray>"));
        return lore;
    }
}
