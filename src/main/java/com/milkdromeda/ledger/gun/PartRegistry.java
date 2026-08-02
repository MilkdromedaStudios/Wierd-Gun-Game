package com.milkdromeda.ledger.gun;


import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Every gun part in the game: ten per section, sixty total.
 * <p>
 * Parts are deliberately lopsided — a part that gives you something big takes
 * something else away. The overall power ceiling is handled centrally by
 * {@link GunStats#balance()}, so parts here are free to be greedy.
 */
public final class PartRegistry {

    private static final Map<PartSection, List<GunPart>> BY_SECTION = new EnumMap<>(PartSection.class);
    private static final Map<String, GunPart> BY_ID = new HashMap<>();

    static {
        registerBarrels();
        registerCores();
        registerGrips();
        registerMagazines();
        registerSights();
        registerStocks();
    }

    private PartRegistry() {
    }

    // ------------------------------------------------------------------ BARREL

    private static void registerBarrels() {
        add("snubnose", PartSection.BARREL, "Snubnose Stub", Rarity.COMMON,
                "Barely a barrel. Fits in a pocket, hits like a pocket.",
                List.of("Very fast fire rate", "Short range", "Snappy reload"),
                s -> s.damage(-1.0).fireRate(-3).range(-20).spread(1.5).reload(-6));

        add("long_rifled", PartSection.BARREL, "Long Rifled Barrel", Rarity.UNCOMMON,
                "Precision-machined, absurdly long, slightly unwieldy.",
                List.of("Big damage and range", "Tight spread", "Slower fire rate"),
                s -> s.damage(5.0).range(40).fireRate(4).spread(-1.2).headshot(0.4));

        add("boomstick", PartSection.BARREL, "Boomstick Bore", Rarity.UNCOMMON,
                "A very wide hole. Physics does the rest.",
                List.of("Fires 7 pellets", "Huge spread", "Heavy knockback"),
                s -> s.pellets(6).damage(-2.2).spread(7).range(-20).fireRate(6).knockback(0.35));

        add("rpg_tube", PartSection.BARREL, "RPG Launch Tube", Rarity.EXOTIC,
                "Shoulder-mounted, poorly regulated, deeply satisfying.",
                List.of("Fires travelling rockets", "Big explosions", "Very slow to cycle"),
                // Ammo count is the magazine's job — a barrel that also gutted the
                // mag left the RPG build sitting on a single rocket.
                s -> s.damage(3.0).explosion(2.6).setVelocity(1.25).setGravity(0.012)
                        .fireRate(26).spread(1).range(12).knockback(0.5)
                        .trait(GunTrait.EXPLOSIVE));

        add("gatling", PartSection.BARREL, "Gatling Cluster", Rarity.RARE,
                "Six barrels on a lazy susan. Needs a moment to get going.",
                List.of("Spins up, then never stops", "Enormous fire rate", "Wild spread"),
                s -> s.fireRate(-6).damage(-2.5).spread(3.5).mag(18).range(-8)
                        .mode(FireMode.SPINUP).spinUp(14));

        add("noodle", PartSection.BARREL, "Wet Noodle Barrel", Rarity.CURSED,
                "It flops. The bullets flop. Everything flops.",
                List.of("Bullets arc and bounce", "Terrible accuracy", "Weirdly punchy"),
                s -> s.setVelocity(0.9).setGravity(0.05).spread(9).damage(2.0)
                        .fireRate(-2).knockback(0.3).trait(GunTrait.BOUNCE));

        add("tesla_rod", PartSection.BARREL, "Tesla Rod", Rarity.RARE,
                "Grounded, allegedly.",
                List.of("Chance to call lightning on hit", "Good range"),
                s -> s.damage(1.5).lightning(0.22).range(14).fireRate(3)
                        .trait(GunTrait.LIGHTNING));

        add("frostbite", PartSection.BARREL, "Frostbite Pipe", Rarity.RARE,
                "Permanently frosted over. Do not lick.",
                List.of("Chills and slows targets", "Steady handling"),
                s -> s.damage(1.0).fireRate(2).range(6).trait(GunTrait.FREEZE));

        add("cursed_bone", PartSection.BARREL, "Cursed Bone Barrel", Rarity.CURSED,
                "Whose bone? Nobody has asked, and nobody will.",
                List.of("Applies wither", "Drains life back to you", "Fewer rounds"),
                s -> s.damage(3.5).spread(2).mag(-3).lifesteal(0.20)
                        .trait(GunTrait.WITHER, GunTrait.LIFESTEAL));

        add("singularity", PartSection.BARREL, "Singularity Muzzle", Rarity.EXOTIC,
                "Contains a very small, very annoyed black hole.",
                List.of("Impacts drag everything inward", "Slow travelling shots"),
                s -> s.damage(2.0).setVelocity(1.6).fireRate(10).range(10).mag(-5)
                        .trait(GunTrait.VORTEX));
    }

    // -------------------------------------------------------------------- CORE

    private static void registerCores() {
        add("iron_core", PartSection.CORE, "Iron Core", Rarity.COMMON,
                "Does exactly what it says. Refreshingly honest.",
                List.of("Semi-automatic", "Reliable damage"),
                s -> s.mode(FireMode.SEMI).damage(1.5).fireRate(-1));

        add("redstone_reactor", PartSection.CORE, "Redstone Reactor", Rarity.UNCOMMON,
                "Clicks so fast it forgot how to stop.",
                List.of("Full auto — hold to fire", "Bigger magazine", "Looser spread"),
                s -> s.mode(FireMode.AUTO).fireRate(-3).damage(-1.0).spread(1.5).mag(8));

        add("blaze_heart", PartSection.CORE, "Blaze Heart", Rarity.RARE,
                "Still beating. Still furious.",
                List.of("3-round burst", "Sets targets alight"),
                s -> s.mode(FireMode.BURST).burst(3).damage(1.0).fireRate(2)
                        .trait(GunTrait.INCENDIARY));

        add("ender_core", PartSection.CORE, "Ender Core", Rarity.RARE,
                "Shots arrive slightly before you fire them.",
                List.of("Rounds pierce one target", "Hits blink you forward"),
                s -> s.pierce(1).damage(1.0).range(10)
                        .trait(GunTrait.BLINK, GunTrait.PIERCE));

        add("tnt_core", PartSection.CORE, "TNT Core", Rarity.EXOTIC,
                "Explosive bullets. Every single one of them.",
                List.of("Every round detonates", "Slower fire rate", "Fewer rounds"),
                s -> s.explosion(1.5).damage(1.0).fireRate(5).mag(-3)
                        .trait(GunTrait.EXPLOSIVE));

        add("amethyst_resonator", PartSection.CORE, "Amethyst Resonator", Rarity.RARE,
                "Hums a perfect A. Then removes someone.",
                List.of("Charge up, release a piercing beam", "Massive damage and range"),
                s -> s.mode(FireMode.CHARGE).charge(18).damage(9.0).pierce(3)
                        .range(30).mag(-3).fireRate(4).trait(GunTrait.PIERCE));

        add("slime_core", PartSection.CORE, "Slime Core", Rarity.UNCOMMON,
                "Boing. Boing. Boing. Ow.",
                List.of("Bouncing rounds", "Heavy knockback", "Low damage"),
                s -> s.damage(-1.5).fireRate(-2).knockback(0.5).setVelocity(1.8).setGravity(0.03)
                        .trait(GunTrait.BOUNCE));

        add("nether_star_core", PartSection.CORE, "Nether Star Core", Rarity.EXOTIC,
                "Overqualified for this. Overheats to prove it.",
                List.of("Enormous per-shot damage", "Long cooldown between shots"),
                s -> s.damage(6.0).mag(-4).fireRate(6).headshot(0.5).range(20));

        add("goat_horn_core", PartSection.CORE, "Goat Horn Core", Rarity.RARE,
                "Every shot is a note. Every note is a war crime.",
                List.of("Impacts release a shockwave", "Buffs nearby allies"),
                s -> s.knockback(0.5).damage(0.5).fireRate(3)
                        .trait(GunTrait.SHOCKWAVE, GunTrait.RALLY));

        add("chicken_core", PartSection.CORE, "Chicken Core", Rarity.CURSED,
                "It fires chickens. We tried to stop it. We could not.",
                List.of("Fires live chickens", "Full auto", "Extremely inaccurate"),
                s -> s.mode(FireMode.AUTO).damage(-2.0).fireRate(-3).spread(5).mag(10)
                        .setVelocity(1.4).setGravity(0.04)
                        .trait(GunTrait.POULTRY, GunTrait.BOUNCE));
    }

    // -------------------------------------------------------------------- GRIP

    private static void registerGrips() {
        add("duct_tape", PartSection.GRIP, "Duct Tape Grip", Rarity.COMMON,
                "Held together by optimism and adhesive.",
                List.of("Very fast reload", "Bad accuracy", "Occasionally jams"),
                s -> s.spread(3).reload(-12).damage(0.5).jam(0.05));

        add("rubber_grip", PartSection.GRIP, "Ergonomic Rubber Grip", Rarity.COMMON,
                "Comfortable. Boring. Genuinely fine.",
                List.of("Tighter spread", "Less recoil"),
                s -> s.spread(-1.5).recoil(-0.1).reload(-4));

        add("bipod", PartSection.GRIP, "Bipod Grip", Rarity.UNCOMMON,
                "Two little legs. Enormous confidence.",
                List.of("Great accuracy and range", "Slows you down"),
                s -> s.spread(-2.5).range(14).moveSpeed(-0.10).fireRate(1));

        add("angled_foregrip", PartSection.GRIP, "Angled Foregrip", Rarity.UNCOMMON,
                "Angled at the one degree that matters.",
                List.of("Accurate on the move", "Slightly faster fire"),
                s -> s.spread(-2).fireRate(-1).moveSpeed(0.05));

        add("vertical_foregrip", PartSection.GRIP, "Vertical Foregrip", Rarity.UNCOMMON,
                "Straight up and down, like your aim now is.",
                List.of("Big recoil reduction", "Tight spread"),
                s -> s.recoil(-0.25).spread(-1.8).damage(0.5));

        add("honey_grip", PartSection.GRIP, "Honey Grip", Rarity.RARE,
                "You will never drop this gun. You may never put it down either.",
                List.of("Rock-steady aim", "Noticeably slows you", "Slow reload"),
                s -> s.spread(-3.5).recoil(-0.4).moveSpeed(-0.18).reload(8));

        add("rocket_grip", PartSection.GRIP, "Rocket Grip", Rarity.EXOTIC,
                "The recoil is not a bug. The recoil is transport.",
                List.of("Recoil launches you backwards", "Rocket jumping enabled", "Extra damage"),
                s -> s.recoil(1.1).damage(2.0).spread(2.5).trait(GunTrait.LAUNCH));

        add("squid_grip", PartSection.GRIP, "Squid Grip", Rarity.CURSED,
                "Eight points of contact. None of them helpful.",
                List.of("Much faster fire rate", "Sprays everywhere", "Bigger magazine"),
                s -> s.fireRate(-2.5).spread(4).damage(-0.5).mag(6));

        add("golden_grip", PartSection.GRIP, "Golden Grip", Rarity.RARE,
                "Solid gold. Soft, heavy, and visible from orbit.",
                List.of("Strong damage bonus", "Good crit chance", "Soft metal jams sometimes"),
                s -> s.damage(2.5).crit(0.12).jam(0.03));

        add("skeleton_hand", PartSection.GRIP, "Skeleton Hand Grip", Rarity.CURSED,
                "It grips back. Firmly. Constantly.",
                List.of("Great headshot multiplier", "Drains a little life"),
                s -> s.damage(1.5).fireRate(-1).spread(1).lifesteal(0.08).headshot(0.35)
                        .trait(GunTrait.LIFESTEAL));
    }

    // ---------------------------------------------------------------- MAGAZINE

    private static void registerMagazines() {
        add("stick_mag", PartSection.MAGAZINE, "Stick Mag", Rarity.COMMON,
                "Eight rounds. Count them. You will need to.",
                List.of("8 rounds", "Very fast reload", "Extra damage"),
                s -> s.mag(-4).reload(-12).damage(1.0));

        add("extended_mag", PartSection.MAGAZINE, "Extended Mag", Rarity.UNCOMMON,
                "Sticks out awkwardly. Worth it.",
                List.of("+18 rounds", "Slightly slower reload"),
                s -> s.mag(18).reload(6));

        add("drum_mag", PartSection.MAGAZINE, "Drum Mag", Rarity.RARE,
                "A bucket of ammunition, bolted on sideways.",
                List.of("+48 rounds", "Slow reload", "A bit heavy"),
                s -> s.mag(48).reload(30).moveSpeed(-0.05).damage(-0.5));

        add("ammo_belt", PartSection.MAGAZINE, "Ammo Belt", Rarity.EXOTIC,
                "Feeds from a box you are dragging behind you.",
                List.of("+138 rounds", "Brutal reload time", "Heavy and inaccurate"),
                s -> s.mag(138).reload(90).moveSpeed(-0.12).damage(-1.5).spread(1));

        add("rocket_rack", PartSection.MAGAZINE, "Rocket Rack", Rarity.EXOTIC,
                "Three rockets, stacked with more hope than engineering.",
                List.of("3 rockets", "Rounds explode", "Very slow reload"),
                s -> s.mag(-9).reload(40).explosion(1.2).damage(2.0)
                        .trait(GunTrait.EXPLOSIVE));

        add("shell_box", PartSection.MAGAZINE, "Shell Box", Rarity.UNCOMMON,
                "Loose shells rattling in a wooden crate.",
                List.of("+3 pellets per shot", "6 shells", "Wider spread"),
                s -> s.mag(-6).reload(14).pellets(3).damage(-0.5).spread(2));

        add("bottomless_satchel", PartSection.MAGAZINE, "Bottomless Satchel", Rarity.CURSED,
                "There is no bottom. There is also no quality control.",
                List.of("+88 rounds", "Weak rounds", "Jams often"),
                s -> s.mag(88).reload(50).damage(-2.5).jam(0.12).trait(GunTrait.JAM));

        add("nuke_mag", PartSection.MAGAZINE, "Nuke Mag", Rarity.EXOTIC,
                "One round. You get one. Make it count.",
                List.of("Single devastating round", "Huge explosion", "Punishing reload"),
                s -> s.mag(-11).reload(130).damage(14.0).explosion(2.2).fireRate(20)
                        .knockback(0.6).trait(GunTrait.EXPLOSIVE));

        add("hopper_feed", PartSection.MAGAZINE, "Hopper Feed", Rarity.RARE,
                "Quietly tops itself up while you are busy panicking.",
                List.of("Refills itself over time", "+6 rounds"),
                s -> s.ammoRegen(30).mag(6).reload(18).trait(GunTrait.REGEN_AMMO));

        add("soul_jar", PartSection.MAGAZINE, "Soul Jar Mag", Rarity.CURSED,
                "Runs on the recently deceased. Very efficient.",
                List.of("Kills refund ammo", "Slight life drain"),
                s -> s.mag(4).reload(10).lifesteal(0.06)
                        .trait(GunTrait.KILL_REFUND, GunTrait.LIFESTEAL));
    }

    // ------------------------------------------------------------------- SIGHT

    private static void registerSights() {
        add("iron_sights", PartSection.SIGHT, "Iron Sights", Rarity.COMMON,
                "A notch and a bump. It has worked for centuries.",
                List.of("Small accuracy bump", "No downsides"),
                s -> s.spread(-0.8));

        add("red_dot", PartSection.SIGHT, "Red Dot", Rarity.COMMON,
                "Put the dot on the thing. Remove the thing.",
                List.of("Better accuracy", "Slight zoom"),
                s -> s.spread(-1.6).zoom(1));

        add("holo", PartSection.SIGHT, "Holographic Sight", Rarity.UNCOMMON,
                "A floating rectangle of pure confidence.",
                List.of("Good accuracy", "Faster handling"),
                s -> s.spread(-2.2).zoom(1).fireRate(-0.5));

        add("sniper_scope", PartSection.SIGHT, "Sniper Scope", Rarity.RARE,
                "You can read their nametag from another biome.",
                List.of("Huge zoom, range and damage", "Great headshots", "Slow and heavy"),
                s -> s.spread(-3).zoom(3).range(40).damage(4.0).headshot(0.6)
                        .fireRate(4).moveSpeed(-0.08));

        add("thermal", PartSection.SIGHT, "Thermal Scope", Rarity.RARE,
                "Everything is orange and everything is a target.",
                List.of("Tags targets you hit", "Moderate zoom", "Extra range"),
                s -> s.zoom(2).spread(-1.5).range(16).trait(GunTrait.GLOW_TARGET));

        add("laser_pointer", PartSection.SIGHT, "Laser Pointer", Rarity.UNCOMMON,
                "No zoom, no fuss, just a very rude red line.",
                List.of("Excellent hipfire accuracy", "Extra range"),
                s -> s.spread(-2.8).damage(0.5).range(10));

        add("googly_eyes", PartSection.SIGHT, "Googly Eyes", Rarity.CURSED,
                "They rattle when you aim. That is the whole mechanism.",
                List.of("Awful accuracy", "Enormous crit chance"),
                s -> s.spread(6).crit(0.30).damage(1.5).trait(GunTrait.TRICKSHOT));

        add("seeker_eye", PartSection.SIGHT, "Eye of the Seeker", Rarity.EXOTIC,
                "It is looking at them. It will keep looking at them.",
                List.of("Rounds home in on targets", "Slightly weaker rounds"),
                s -> s.homing(0.55).damage(-1.0).range(12).trait(GunTrait.HOMING));

        add("cracked_monocle", PartSection.SIGHT, "Cracked Monocle", Rarity.CURSED,
                "One good eye, one terrible decision.",
                List.of("Colossal headshot damage", "Genuinely bad accuracy"),
                s -> s.headshot(1.2).spread(4).damage(1.0).zoom(2));

        add("target_computer", PartSection.SIGHT, "Target Computer", Rarity.EXOTIC,
                "It aims for you. It is not proud of you.",
                List.of("Locks on — shots basically cannot miss", "Weak rounds"),
                s -> s.spread(-4).damage(-2.5).range(8).fireRate(1).trait(GunTrait.LOCK_ON));
    }

    // ------------------------------------------------------------------- STOCK

    private static void registerStocks() {
        add("wooden_stock", PartSection.STOCK, "Wooden Stock", Rarity.COMMON,
                "Whittled by someone who had time.",
                List.of("Balanced all round"),
                s -> s.spread(-0.8).recoil(-0.1).reload(-2));

        add("tactical_stock", PartSection.STOCK, "Tactical Stock", Rarity.UNCOMMON,
                "Adjustable in eleven directions, six of them useful.",
                List.of("Much faster reload", "Less recoil"),
                s -> s.recoil(-0.3).reload(-14).spread(-1.2));

        add("anvil_stock", PartSection.STOCK, "Heavy Anvil Stock", Rarity.RARE,
                "An anvil. On the back. Of a gun.",
                List.of("Superb stability", "Extra damage", "Slows you right down"),
                s -> s.spread(-3.5).recoil(-0.6).moveSpeed(-0.20).damage(1.5).reload(10));

        add("skeleton_stock", PartSection.STOCK, "Skeleton Stock", Rarity.UNCOMMON,
                "Hollowed out until only the idea of a stock remains.",
                List.of("Makes you faster", "Fast reload", "Wobbly aim"),
                s -> s.moveSpeed(0.14).spread(2).reload(-10).damage(-0.5));

        add("slime_pad", PartSection.STOCK, "Slime Pad Stock", Rarity.RARE,
                "Absorbs recoil. Also absorbs sweat, dust, and small animals.",
                List.of("Soaks up recoil", "Impacts push targets away"),
                s -> s.recoil(-0.5).knockback(0.35).spread(-1).trait(GunTrait.SHOCKWAVE));

        add("booster_stock", PartSection.STOCK, "Rocket Booster Stock", Rarity.EXOTIC,
                "Reloading is now a mobility option.",
                List.of("Very fast reload", "Faster movement", "Hits blink you forward"),
                s -> s.reload(-20).moveSpeed(0.10).damage(0.5).trait(GunTrait.BLINK));

        add("cactus_stock", PartSection.STOCK, "Cactus Stock", Rarity.CURSED,
                "Hurts to hold. Hurts them more.",
                List.of("Huge damage bonus", "Hurts you a little when you fire"),
                s -> s.damage(4.5).spread(1.5).trait(GunTrait.THORNS));

        add("cushion_stock", PartSection.STOCK, "Cushion Stock", Rarity.RARE,
                "Safety first. Explosions second.",
                List.of("Immune to your own explosions", "Less recoil"),
                s -> s.recoil(-0.2).spread(-0.5).trait(GunTrait.SELF_SAFE));

        add("jukebox_stock", PartSection.STOCK, "Jukebox Stock", Rarity.EXOTIC,
                "Every shot drops a beat. Your team gets hyped.",
                List.of("Firing buffs nearby allies", "Faster reload"),
                s -> s.damage(1.0).reload(-6).trait(GunTrait.RALLY));

        add("void_stock", PartSection.STOCK, "Void Stock", Rarity.CURSED,
                "Made of the gap between two places.",
                List.of("Rounds punch through targets", "Extra range"),
                s -> s.pierce(2).damage(1.0).range(14).spread(1).trait(GunTrait.PIERCE));
    }

    // ----------------------------------------------------------------- helpers

    private static void add(String id, PartSection section, String name, Rarity rarity,
                            String flavor, List<String> perks, Consumer<GunStats> apply) {
        GunPart part = new GunPart(id, section, name, flavor, rarity, perks, apply);
        BY_SECTION.computeIfAbsent(section, k -> new ArrayList<>()).add(part);
        BY_ID.put(id, part);
    }

    public static List<GunPart> of(PartSection section) {
        return Collections.unmodifiableList(BY_SECTION.getOrDefault(section, List.of()));
    }

    public static GunPart byId(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    /** The part a fresh, unconfigured gun starts with. */
    public static GunPart defaultFor(PartSection section) {
        return of(section).get(0);
    }

    public static GunPart randomFor(PartSection section) {
        List<GunPart> parts = of(section);
        return parts.get(ThreadLocalRandom.current().nextInt(parts.size()));
    }

    public static int total() {
        return BY_ID.size();
    }

    public static List<String> allIds() {
        return List.copyOf(BY_ID.keySet());
    }
}
