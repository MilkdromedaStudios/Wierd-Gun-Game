package com.milkdromeda.ledger.gun;


import java.util.ArrayList;
import java.util.List;

/**
 * Pre-built guns for players who want to skip straight to the shooting.
 * Every preset is a legal blueprint — you can open any of these in the
 * workbench and start swapping parts around.
 */
public record GunPreset(String id, String name, String description, String[] parts) {

    private static final List<GunPreset> PRESETS = new ArrayList<>();

    static {
        add("ak", "The AK", "The one everybody already knows how to use.",
                "long_rifled", "redstone_reactor", "vertical_foregrip", "extended_mag", "red_dot", "wooden_stock");

        add("sniper", "Longshot Sniper", "Charge it, hold your breath, delete a distant problem.",
                "long_rifled", "amethyst_resonator", "bipod", "stick_mag", "sniper_scope", "anvil_stock");

        add("shotgun", "Boomstick Shotgun", "Seven pellets of close-range conversation.",
                "boomstick", "iron_core", "angled_foregrip", "shell_box", "iron_sights", "tactical_stock");

        add("rpg", "The RPG", "Three rockets. No refunds. Padded stock so you survive your own work.",
                "rpg_tube", "iron_core", "rocket_grip", "rocket_rack", "iron_sights", "cushion_stock");

        add("maxigun", "The Maxigun", "156 rounds on a belt. Spins up, then reshapes the horizon.",
                "gatling", "redstone_reactor", "honey_grip", "ammo_belt", "laser_pointer", "anvil_stock");

        add("boomrifle", "Boom Rifle", "Explosive bullets, full auto, padded so you keep your legs.",
                "long_rifled", "tnt_core", "vertical_foregrip", "extended_mag", "red_dot", "cushion_stock");

        add("chicken_cannon", "Chicken Cannon", "Fires live chickens at industrial rates. Genuinely effective.",
                "boomstick", "chicken_core", "squid_grip", "drum_mag", "googly_eyes", "skeleton_stock");

        add("nuke_pistol", "Nuke Pistol", "One round, one crater, one very long reload.",
                "snubnose", "tnt_core", "rubber_grip", "nuke_mag", "laser_pointer", "cushion_stock");

        add("blackhole", "Black Hole Blaster", "Pulls the whole room into one convenient pile.",
                "singularity", "ender_core", "bipod", "hopper_feed", "seeker_eye", "void_stock");

        add("stormbringer", "Stormbringer", "Full auto lightning. The weather forecast is you.",
                "tesla_rod", "redstone_reactor", "vertical_foregrip", "drum_mag", "thermal", "jukebox_stock");

        add("frostbite", "Frostbite SMG", "Freezes them in place, then keeps going.",
                "frostbite", "redstone_reactor", "angled_foregrip", "extended_mag", "holo", "tactical_stock");

        add("noodle_nailer", "Noodle Nailer", "Floppy, bouncy, and somehow lethal. Nobody understands it.",
                "noodle", "slime_core", "squid_grip", "bottomless_satchel", "googly_eyes", "slime_pad");
    }

    private static void add(String id, String name, String description, String... parts) {
        PRESETS.add(new GunPreset(id, name, description, parts));
    }

    public GunBlueprint toBlueprint() {
        GunBlueprint blueprint = GunBlueprint.of(parts);
        blueprint.setCustomName(name);
        return blueprint;
    }

    public static List<GunPreset> all() {
        return List.copyOf(PRESETS);
    }

    public static GunPreset byId(String id) {
        for (GunPreset preset : PRESETS) {
            if (preset.id().equalsIgnoreCase(id)) {
                return preset;
            }
        }
        return null;
    }
}
