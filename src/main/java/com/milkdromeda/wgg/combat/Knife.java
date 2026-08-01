package com.milkdromeda.wgg.combat;

import com.milkdromeda.wgg.gun.Rarity;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Melee sidearms. Every gun build gets one, because at some point the
 * Superbox will be standing directly on top of you and reloading is not an
 * option.
 *
 * @param damage     replaces the vanilla weapon damage entirely
 * @param attackSpeed vanilla attack-speed attribute (4.0 is a sword, higher swings faster)
 * @param backstab   damage multiplier when you hit something from behind
 * @param knockback  extra knockback on top of vanilla
 * @param lifesteal  fraction of damage dealt returned as health
 * @param bossBonus  extra multiplier against the Superbox and its minions
 */
public record Knife(
        String id,
        String name,
        Material icon,
        Rarity rarity,
        String flavor,
        double damage,
        double attackSpeed,
        double backstab,
        double knockback,
        double lifesteal,
        double bossBonus,
        List<KnifeEffect> effects
) {

    public enum KnifeEffect {
        BLEED("Bleed", "Targets keep taking damage for a few seconds."),
        FREEZE("Chill", "Slows and freezes whatever you stab."),
        BLINK("Blink Strike", "Teleports you behind your target."),
        TRUE_DAMAGE("Armour Piercing", "Ignores armour entirely."),
        SLIP("Buttered", "Targets slide away helplessly."),
        POISON("Toxic", "Applies poison."),
        CRIT("Keen", "High chance of a big critical hit.");

        private final String displayName;
        private final String description;

        KnifeEffect(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String displayName() {
            return displayName;
        }

        public String description() {
            return description;
        }
    }

    private static final List<Knife> KNIVES = new ArrayList<>();

    static {
        add(new Knife("butter_knife", "Butter Knife", Material.GOLDEN_SHOVEL, Rarity.COMMON,
                "Technically a weapon. Legally, cutlery.",
                3.5, 4.0, 1.2, 0.5, 0.0, 1.0, List.of(KnifeEffect.SLIP)));

        add(new Knife("combat_knife", "Combat Knife", Material.IRON_SWORD, Rarity.UNCOMMON,
                "Serious, matte black, does exactly one job.",
                6.0, 2.4, 1.8, 0.0, 0.0, 1.0, List.of()));

        add(new Knife("karambit", "Karambit", Material.IRON_SHOVEL, Rarity.RARE,
                "Curved, hooked, and extremely unfair from behind.",
                5.0, 3.2, 3.0, 0.0, 0.0, 1.0, List.of(KnifeEffect.BLEED)));

        add(new Knife("cleaver", "Butcher's Cleaver", Material.STONE_AXE, Rarity.UNCOMMON,
                "Heavy, blunt, and honest about it.",
                8.5, 1.2, 1.2, 1.1, 0.0, 1.15, List.of()));

        add(new Knife("ender_shiv", "Ender Shiv", Material.NETHERITE_SHOVEL, Rarity.EXOTIC,
                "You are already behind them. You were always behind them.",
                5.5, 2.8, 2.2, 0.0, 0.0, 1.0, List.of(KnifeEffect.BLINK)));

        add(new Knife("frost_fang", "Frost Fang", Material.DIAMOND_SHOVEL, Rarity.RARE,
                "Cold enough that the wound forgets to bleed.",
                5.5, 2.6, 1.5, 0.2, 0.0, 1.0, List.of(KnifeEffect.FREEZE)));

        add(new Knife("vampire_fang", "Vampire Fang", Material.NETHERITE_SWORD, Rarity.CURSED,
                "Drinks first, asks questions never.",
                5.5, 2.4, 1.6, 0.0, 0.40, 1.0, List.of()));

        add(new Knife("baguette", "Baguette", Material.BREAD, Rarity.CURSED,
                "Day-old. Structurally, that is the point.",
                4.5, 2.0, 1.3, 2.0, 0.0, 1.0, List.of()));

        add(new Knife("diamond_shank", "Diamond Shank", Material.DIAMOND_SWORD, Rarity.RARE,
                "Improvised from something far too expensive.",
                7.0, 2.0, 1.7, 0.0, 0.0, 1.0, List.of(KnifeEffect.CRIT)));

        add(new Knife("boxcutter", "Boxcutter", Material.SHEARS, Rarity.EXOTIC,
                "Designed for boxes. The Superbox is a box.",
                4.0, 3.6, 1.5, 0.0, 0.0, 2.2, List.of(KnifeEffect.TRUE_DAMAGE)));
    }

    private static void add(Knife knife) {
        KNIVES.add(knife);
    }

    public boolean has(KnifeEffect effect) {
        return effects.contains(effect);
    }

    public static List<Knife> all() {
        return List.copyOf(KNIVES);
    }

    public static Knife byId(String id) {
        for (Knife knife : KNIVES) {
            if (knife.id().equalsIgnoreCase(id)) {
                return knife;
            }
        }
        return null;
    }
}
