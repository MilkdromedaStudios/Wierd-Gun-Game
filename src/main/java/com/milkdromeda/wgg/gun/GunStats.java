package com.milkdromeda.wgg.gun;

import java.util.EnumSet;
import java.util.Set;

/**
 * The resolved numbers for an assembled gun.
 * <p>
 * Parts mutate one of these in section order (barrel, core, grip, magazine,
 * sight, stock), then {@link #balance()} runs once at the end. Balance is what
 * keeps "crazy" from turning into "one-shots the whole server": individual
 * stats get hard caps, and then the whole gun gets held to a sustained-damage
 * budget. Stack every damage part you like — the gun will just fire slower or
 * hit softer to stay inside the budget.
 */
public final class GunStats {

    // ---- hard ceilings -----------------------------------------------------
    public static final double MAX_PELLET_DAMAGE = 24.0;
    public static final double MAX_BURST_DAMAGE = 46.0;
    public static final double MAX_DPS = 34.0;
    public static final double MAX_EXPLOSION = 3.6;
    public static final double MAX_RANGE = 140.0;
    public static final int MAX_MAG = 250;
    public static final double MAX_HEADSHOT = 3.0;
    public static final int MAX_PIERCE = 6;
    public static final double MAX_LIFESTEAL = 0.35;
    public static final double MAX_CRIT = 0.5;
    public static final double MIN_FIRE_RATE_TICKS = 1.0;
    public static final double MIN_DAMAGE = 0.5;

    /** How much effective damage one point of explosion power is worth to the budget. */
    private static final double EXPLOSION_WEIGHT = 3.5;
    /** Explosions never shrink below this fraction of their listed power. */
    private static final double EXPLOSION_SCALE_FLOOR = 0.55;

    // ---- core numbers ------------------------------------------------------
    private double damage = 5.0;
    private int pellets = 1;
    private double fireRateTicks = 8.0;
    private double spread = 2.0;
    private double range = 48.0;
    private int magSize = 12;
    private int reloadTicks = 40;
    private double velocity = 999.0;
    private double gravity = 0.0;

    // ---- flavour numbers ---------------------------------------------------
    private double explosionPower = 0.0;
    private double recoil = 0.0;
    private double knockback = 0.15;
    private double headshotMultiplier = 1.5;
    private int chargeTicks = 0;
    private int spinUpTicks = 0;
    private int zoom = 0;
    private int pierceCount = 0;
    private double homingStrength = 0.0;
    private int burstCount = 1;
    private double lifesteal = 0.0;
    private double critChance = 0.0;
    private double jamChance = 0.0;
    private double lightningChance = 0.0;
    private double moveSpeedModifier = 0.0;
    private int ammoRegenTicks = 0;

    private FireMode fireMode = FireMode.SEMI;
    private final Set<GunTrait> traits = EnumSet.noneOf(GunTrait.class);

    /** How much the balance pass had to walk the gun back, 0.0-1.0. */
    private double nerfApplied = 0.0;

    // ---- accessors ---------------------------------------------------------
    public double damage() { return damage; }
    public int pellets() { return pellets; }
    public double fireRateTicks() { return fireRateTicks; }
    public double spread() { return spread; }
    public double range() { return range; }
    public int magSize() { return magSize; }
    public int reloadTicks() { return reloadTicks; }
    public double velocity() { return velocity; }
    public double gravity() { return gravity; }
    public double explosionPower() { return explosionPower; }
    public double recoil() { return recoil; }
    public double knockback() { return knockback; }
    public double headshotMultiplier() { return headshotMultiplier; }
    public int chargeTicks() { return chargeTicks; }
    public int spinUpTicks() { return spinUpTicks; }
    public int zoom() { return zoom; }
    public int pierceCount() { return pierceCount; }
    public double homingStrength() { return homingStrength; }
    public int burstCount() { return burstCount; }
    public double lifesteal() { return lifesteal; }
    public double critChance() { return critChance; }
    public double jamChance() { return jamChance; }
    public double lightningChance() { return lightningChance; }
    public double moveSpeedModifier() { return moveSpeedModifier; }
    public int ammoRegenTicks() { return ammoRegenTicks; }
    public FireMode fireMode() { return fireMode; }
    public Set<GunTrait> traits() { return traits; }
    public double nerfApplied() { return nerfApplied; }

    public boolean has(GunTrait trait) {
        return traits.contains(trait);
    }

    /** Instant-hit rounds (beams, bullets) versus simulated travel (rockets, lobs). */
    public boolean isHitscan() {
        return velocity >= 100.0;
    }

    // ---- mutators used by parts -------------------------------------------
    // Every one returns this so parts read as a single fluent statement.

    public GunStats damage(double delta) { this.damage += delta; return this; }
    public GunStats damageMult(double factor) { this.damage *= factor; return this; }
    public GunStats pellets(int delta) { this.pellets += delta; return this; }
    public GunStats fireRate(double delta) { this.fireRateTicks += delta; return this; }
    public GunStats fireRateMult(double factor) { this.fireRateTicks *= factor; return this; }
    public GunStats spread(double delta) { this.spread += delta; return this; }
    public GunStats spreadMult(double factor) { this.spread *= factor; return this; }
    public GunStats range(double delta) { this.range += delta; return this; }
    public GunStats rangeMult(double factor) { this.range *= factor; return this; }
    public GunStats mag(int delta) { this.magSize += delta; return this; }
    public GunStats magMult(double factor) { this.magSize = (int) Math.round(this.magSize * factor); return this; }
    public GunStats reload(int delta) { this.reloadTicks += delta; return this; }
    public GunStats reloadMult(double factor) { this.reloadTicks = (int) Math.round(this.reloadTicks * factor); return this; }
    public GunStats setVelocity(double value) { this.velocity = value; return this; }
    public GunStats setGravity(double value) { this.gravity = value; return this; }
    public GunStats explosion(double delta) { this.explosionPower += delta; return this; }
    public GunStats recoil(double delta) { this.recoil += delta; return this; }
    public GunStats knockback(double delta) { this.knockback += delta; return this; }
    public GunStats headshot(double delta) { this.headshotMultiplier += delta; return this; }
    public GunStats charge(int delta) { this.chargeTicks += delta; return this; }
    public GunStats spinUp(int delta) { this.spinUpTicks += delta; return this; }
    public GunStats zoom(int value) { this.zoom = Math.max(this.zoom, value); return this; }
    public GunStats pierce(int delta) { this.pierceCount += delta; return this; }
    public GunStats homing(double delta) { this.homingStrength += delta; return this; }
    public GunStats burst(int value) { this.burstCount = Math.max(this.burstCount, value); return this; }
    public GunStats lifesteal(double delta) { this.lifesteal += delta; return this; }
    public GunStats crit(double delta) { this.critChance += delta; return this; }
    public GunStats jam(double delta) { this.jamChance += delta; return this; }
    public GunStats lightning(double delta) { this.lightningChance += delta; return this; }
    public GunStats moveSpeed(double delta) { this.moveSpeedModifier += delta; return this; }
    public GunStats ammoRegen(int ticks) { this.ammoRegenTicks = ticks; return this; }

    public GunStats mode(FireMode mode) {
        this.fireMode = mode;
        return this;
    }

    public GunStats trait(GunTrait... added) {
        for (GunTrait t : added) {
            traits.add(t);
        }
        return this;
    }

    // ---- balance -----------------------------------------------------------

    /**
     * Clamp everything into sane territory, then hold the whole gun to a
     * sustained-damage budget. Called once after all six parts have applied.
     */
    public void balance() {
        // Raw clamps first, so the DPS maths below sees believable inputs.
        pellets = clampInt(pellets, 1, 24);
        fireRateTicks = Math.max(MIN_FIRE_RATE_TICKS, fireRateTicks);
        spread = clamp(spread, 0.0, 45.0);
        range = clamp(range, 6.0, MAX_RANGE);
        magSize = clampInt(magSize, 1, MAX_MAG);
        reloadTicks = clampInt(reloadTicks, 8, 300);
        velocity = clamp(velocity, 0.35, 999.0);
        gravity = clamp(gravity, 0.0, 0.12);
        explosionPower = clamp(explosionPower, 0.0, MAX_EXPLOSION);
        recoil = clamp(recoil, 0.0, 1.6);
        knockback = clamp(knockback, 0.0, 1.4);
        headshotMultiplier = clamp(headshotMultiplier, 1.0, MAX_HEADSHOT);
        chargeTicks = clampInt(chargeTicks, 0, 60);
        spinUpTicks = clampInt(spinUpTicks, 0, 40);
        zoom = clampInt(zoom, 0, 3);
        pierceCount = clampInt(pierceCount, 0, MAX_PIERCE);
        homingStrength = clamp(homingStrength, 0.0, 0.85);
        burstCount = clampInt(burstCount, 1, 6);
        lifesteal = clamp(lifesteal, 0.0, MAX_LIFESTEAL);
        critChance = clamp(critChance, 0.0, MAX_CRIT);
        jamChance = clamp(jamChance, 0.0, 0.25);
        lightningChance = clamp(lightningChance, 0.0, 0.4);
        moveSpeedModifier = clamp(moveSpeedModifier, -0.45, 0.35);
        damage = Math.max(MIN_DAMAGE, damage);

        double before = damage;

        // Per-pellet and per-trigger-pull ceilings.
        damage = Math.min(damage, MAX_PELLET_DAMAGE);
        if (damage * pellets > MAX_BURST_DAMAGE) {
            damage = MAX_BURST_DAMAGE / pellets;
        }

        double rounds = (double) pellets * burstCount;
        double minDamageShare = MIN_DAMAGE * rounds;

        // A wall of pellets at maximum fire rate can breach the budget even at
        // the minimum damage per pellet — there is no damage number low enough
        // to save it. When that happens the gun cycles slower instead, which is
        // also the honest answer: greedy builds get heavy.
        double requiredSeconds = minDamageShare / MAX_DPS;
        if (secondsPerPull() < requiredSeconds) {
            double chargeSeconds = fireMode == FireMode.CHARGE ? chargeTicks / 20.0 : 0.0;
            fireRateTicks = Math.max(MIN_FIRE_RATE_TICKS, (requiredSeconds - chargeSeconds) * 20.0);
        }

        // Sustained damage budget. Explosions count toward it, otherwise a
        // rocket spammer sails past the cap on splash alone.
        //
        // Explosions are only allowed to shrink so far, because a rocket that
        // does not go bang is not a rocket. Whatever budget the blast keeps is
        // taken out of bullet damage instead, and if the blast alone would eat
        // the entire budget it gets pulled back until the damage floor fits.
        // Solved directly rather than scaled iteratively so the result lands
        // exactly on the cap.
        double perPull = perPullDamage();
        double budget = MAX_DPS * secondsPerPull();
        if (perPull > budget) {
            double uniformScale = budget / perPull;
            explosionPower *= Math.max(EXPLOSION_SCALE_FLOOR, uniformScale);

            double damageShare = budget - explosionShare();

            if (damageShare < minDamageShare) {
                damageShare = minDamageShare;
                explosionPower = Math.max(0.0, budget - minDamageShare) / (EXPLOSION_WEIGHT * burstCount);
            }
            damage = damageShare / rounds;
        }

        damage = Math.max(MIN_DAMAGE, damage);
        nerfApplied = before <= 0 ? 0.0 : clamp(1.0 - (damage / before), 0.0, 1.0);
    }

    /**
     * Rough sustained damage per second, ignoring reloads. Explosion power is
     * folded in as extra effective damage so splash builds are budgeted too.
     */
    public double sustainedDps() {
        return perPullDamage() / secondsPerPull();
    }

    /** Total effective damage from one trigger pull, blast included. */
    private double perPullDamage() {
        return damage * pellets * burstCount + explosionShare();
    }

    /** The slice of a trigger pull's damage that comes from the explosion. */
    private double explosionShare() {
        return explosionPower * EXPLOSION_WEIGHT * burstCount;
    }

    private double secondsPerPull() {
        double seconds = fireRateTicks / 20.0;
        if (fireMode == FireMode.CHARGE) {
            seconds += chargeTicks / 20.0;
        }
        return Math.max(0.05, seconds);
    }

    /** 0-100 summary used for the power bar in the menu. */
    public int powerScore() {
        double score = sustainedDps() / MAX_DPS * 62.0
                + Math.min(range / MAX_RANGE, 1.0) * 12.0
                + Math.min(magSize / 60.0, 1.0) * 10.0
                + Math.min(traits.size() / 6.0, 1.0) * 10.0
                + Math.max(0.0, 1.0 - spread / 12.0) * 6.0;
        return clampInt((int) Math.round(score), 1, 100);
    }

    /** How silly this thing is, used purely for bragging rights in the lore. */
    public int weirdnessScore() {
        int score = traits.size() * 9;
        if (pellets >= 8) score += 8;
        if (magSize >= 100) score += 10;
        if (magSize <= 2) score += 10;
        if (spread >= 12) score += 8;
        if (gravity > 0.05) score += 6;
        if (has(GunTrait.POULTRY)) score += 25;
        if (fireMode == FireMode.SPINUP) score += 6;
        return clampInt(score, 1, 100);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
