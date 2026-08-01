package com.milkdromeda.wgg.gun;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A chosen part for each of the six sections, plus the resolved stats.
 * <p>
 * Stats are computed lazily and cached, and any change to the parts throws the
 * cache away. The part order matters: barrel sets the archetype, core overrides
 * the fire mode, and the handling parts refine what is left.
 */
public final class GunBlueprint {

    private final Map<PartSection, GunPart> parts = new EnumMap<>(PartSection.class);
    private GunStats cachedStats;
    private String customName;

    public GunBlueprint() {
        for (PartSection section : PartSection.values()) {
            parts.put(section, PartRegistry.defaultFor(section));
        }
    }

    public static GunBlueprint of(String... partIds) {
        GunBlueprint blueprint = new GunBlueprint();
        for (String id : partIds) {
            GunPart part = PartRegistry.byId(id);
            if (part != null) {
                blueprint.set(part);
            }
        }
        return blueprint;
    }

    public static GunBlueprint random() {
        GunBlueprint blueprint = new GunBlueprint();
        for (PartSection section : PartSection.values()) {
            blueprint.set(PartRegistry.randomFor(section));
        }
        return blueprint;
    }

    public GunPart get(PartSection section) {
        return parts.get(section);
    }

    public void set(GunPart part) {
        Objects.requireNonNull(part, "part");
        parts.put(part.section(), part);
        cachedStats = null;
    }

    public void setCustomName(String name) {
        this.customName = name;
    }

    public String customName() {
        return customName;
    }

    public GunStats stats() {
        if (cachedStats == null) {
            GunStats stats = new GunStats();
            for (PartSection section : PartSection.values()) {
                parts.get(section).applyTo(stats);
            }
            stats.balance();
            cachedStats = stats;
        }
        return cachedStats;
    }

    /** Comma-joined part ids, in section order — this is what gets stored on the item. */
    public String serialize() {
        List<String> ids = new ArrayList<>(PartSection.values().length);
        for (PartSection section : PartSection.values()) {
            ids.add(parts.get(section).id());
        }
        return String.join(",", ids);
    }

    public static GunBlueprint deserialize(String data) {
        GunBlueprint blueprint = new GunBlueprint();
        if (data == null || data.isBlank()) {
            return blueprint;
        }
        for (String id : data.split(",")) {
            GunPart part = PartRegistry.byId(id.trim());
            if (part != null) {
                blueprint.set(part);
            }
        }
        return blueprint;
    }

    // ------------------------------------------------------------------ naming

    private static final Map<String, String> BARREL_NOUNS = Map.ofEntries(
            Map.entry("snubnose", "Snub"),
            Map.entry("long_rifled", "Longshot"),
            Map.entry("boomstick", "Boomstick"),
            Map.entry("rpg_tube", "RPG"),
            Map.entry("gatling", "Maxigun"),
            Map.entry("noodle", "Noodler"),
            Map.entry("tesla_rod", "Zapper"),
            Map.entry("frostbite", "Chiller"),
            Map.entry("cursed_bone", "Bonecarver"),
            Map.entry("singularity", "Voidmaw"));

    private static final Map<String, String> CORE_PREFIXES = Map.ofEntries(
            Map.entry("iron_core", "Standard"),
            Map.entry("redstone_reactor", "Rapid"),
            Map.entry("blaze_heart", "Burning"),
            Map.entry("ender_core", "Phasing"),
            Map.entry("tnt_core", "Detonating"),
            Map.entry("amethyst_resonator", "Resonant"),
            Map.entry("slime_core", "Bouncy"),
            Map.entry("nether_star_core", "Overcharged"),
            Map.entry("goat_horn_core", "Screaming"),
            Map.entry("chicken_core", "Clucking"));

    /**
     * Builds a name from the barrel and core, then tacks on a title earned by
     * the gun's weirdest trait. Deterministic, so rebuilding the same parts
     * always gives you back the same gun name.
     */
    public String displayName() {
        if (customName != null && !customName.isBlank()) {
            return customName;
        }
        String prefix = CORE_PREFIXES.getOrDefault(get(PartSection.CORE).id(), "Odd");
        String noun = BARREL_NOUNS.getOrDefault(get(PartSection.BARREL).id(), "Gun");
        String title = title();
        String mark = title.isEmpty() ? " Mk." + (Math.abs(serialize().hashCode()) % 89 + 1) : " " + title;
        return prefix + " " + noun + mark;
    }

    private String title() {
        GunStats stats = stats();
        if (stats.has(GunTrait.POULTRY)) return "of the Flock";
        if (stats.has(GunTrait.VORTEX)) return "of the Abyss";
        if (stats.has(GunTrait.WITHER)) return "of Bad News";
        if (stats.has(GunTrait.LIGHTNING)) return "of the Storm";
        if (stats.has(GunTrait.EXPLOSIVE) && stats.explosionPower() >= 2.5) return "of Regret";
        if (stats.has(GunTrait.HOMING)) return "of Inevitability";
        if (stats.has(GunTrait.FREEZE)) return "of the Long Winter";
        if (stats.has(GunTrait.LAUNCH)) return "of Poor Life Choices";
        if (stats.has(GunTrait.TRICKSHOT)) return "of Pure Luck";
        return "";
    }
}
