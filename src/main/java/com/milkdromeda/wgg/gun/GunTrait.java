package com.milkdromeda.wgg.gun;

/**
 * Special behaviours a part can bolt onto a gun. Traits stack, which is where
 * most of the "weird" in Weird Gun Game comes from — an explosive homing
 * lightning bullet that also steals health is a completely legal build.
 */
public enum GunTrait {

    EXPLOSIVE("Explosive", "Rounds detonate on impact."),
    INCENDIARY("Incendiary", "Sets targets on fire."),
    HOMING("Homing", "Rounds curve toward the nearest target."),
    BOUNCE("Ricochet", "Rounds bounce off walls instead of stopping."),
    LIGHTNING("Shocking", "Impacts have a chance to call lightning."),
    FREEZE("Cryo", "Impacts slow and chill the target."),
    WITHER("Cursed", "Impacts apply wither."),
    LIFESTEAL("Vampiric", "Heals you for part of the damage dealt."),
    PIERCE("Piercing", "Rounds punch through targets."),
    VORTEX("Singularity", "Impacts drag nearby entities inward."),
    LAUNCH("Rocket Recoil", "Recoil throws you backwards. Rocket jumping is a feature."),
    POULTRY("Poultry", "Fires live chickens. No further questions."),
    GLOW_TARGET("Thermal", "Tags whatever you hit so everyone can see it."),
    LOCK_ON("Auto-Lock", "Snaps toward a target for a guaranteed hit."),
    SELF_SAFE("Padded", "You take no damage from your own explosions."),
    JAM("Unreliable", "Occasionally jams and wastes the shot."),
    KILL_REFUND("Soul-Fed", "Kills refund ammo."),
    TRICKSHOT("Trickshot", "Random spread, but big crit chance."),
    BLINK("Blink", "Landing a hit teleports you toward the impact."),
    SHOCKWAVE("Shockwave", "Impacts release a knockback pulse."),
    REGEN_AMMO("Auto-Feed", "Slowly refills its own magazine over time."),
    THORNS("Thorny", "Hurts a little to hold. Worth it."),
    RALLY("Rally", "Buffs nearby allies when you fire.");

    private final String displayName;
    private final String description;

    GunTrait(String displayName, String description) {
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
