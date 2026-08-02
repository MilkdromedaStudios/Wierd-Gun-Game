package com.milkdromeda.ledger.combat;

import com.milkdromeda.ledger.gun.GunStats;
import com.milkdromeda.ledger.gun.GunTrait;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Everything between pulling the trigger and something regretting it.
 * <p>
 * Rounds are hitscan: a ray is walked from the shooter's eye out to the gun's
 * range, stopping at the first block or living thing it meets. That is resolved
 * in the same tick, which suits a game where the fastest builds fire twenty
 * times a second — simulated projectiles at that rate would be a thousand
 * entities a minute for no visible gain.
 * <p>
 * Pellets, spread, damage, range and every trait come from the merged
 * {@link GunStats}, so the balance pass that governs the bench governs the
 * bullets too.
 */
public final class ShotEngine {

    /** How far apart trail particles are placed, in blocks. */
    private static final double TRAIL_STEP = 0.7;

    private ShotEngine() {
    }

    /**
     * Fires one trigger pull. Ammo is expected to have been spent by the caller.
     *
     * @param aiming true when the shooter is sneaking, which tightens the spread
     */
    public static void fire(ServerPlayer shooter, GunStats stats, boolean aiming) {
        ServerLevel level = shooter.level();
        Vec3 eye = shooter.getEyePosition();
        double spread = effectiveSpread(shooter, stats, aiming);

        for (int pellet = 0; pellet < stats.pellets(); pellet++) {
            shoot(level, shooter, stats, eye, spreadDirection(shooter, spread));
        }

        muzzle(level, shooter, eye, stats);
        recoil(shooter, stats);
    }

    private static double effectiveSpread(ServerPlayer shooter, GunStats stats, boolean aiming) {
        double spread = stats.spread();
        if (aiming) {
            spread *= 0.35;
        }
        if (shooter.isSprinting()) {
            spread *= 1.5;
        }
        if (!shooter.onGround()) {
            spread *= 1.4;
        }
        return spread;
    }

    /** Jitters the look vector by up to {@code degrees} on both axes. */
    private static Vec3 spreadDirection(ServerPlayer shooter, double degrees) {
        Vec3 look = shooter.getLookAngle();
        if (degrees <= 0.01) {
            return look;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double yaw = Math.toRadians(random.nextDouble(-degrees, degrees));
        double pitch = Math.toRadians(random.nextDouble(-degrees, degrees));
        return look.xRot((float) pitch).yRot((float) yaw).normalize();
    }

    /**
     * Walks one pellet out to its range, piercing as far as the gun allows.
     */
    private static void shoot(ServerLevel level, ServerPlayer shooter, GunStats stats,
                              Vec3 from, Vec3 direction) {
        Vec3 origin = from;
        double remaining = stats.range();
        int piercesLeft = stats.pierceCount();

        while (remaining > 0.1) {
            Vec3 end = origin.add(direction.scale(remaining));

            // The wall comes first: it bounds how far entities can be checked.
            BlockHitResult blockHit = level.clip(new ClipContext(
                    origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
            Vec3 limit = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();

            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                    shooter, origin, limit,
                    new AABB(origin, limit).inflate(1.0),
                    candidate -> candidate.isAlive() && candidate != shooter
                            && candidate instanceof LivingEntity,
                    0.3);

            if (entityHit != null) {
                Vec3 point = entityHit.getLocation();
                trail(level, origin, point, stats);
                hit(level, shooter, stats, (LivingEntity) entityHit.getEntity(), point);

                if (piercesLeft <= 0) {
                    return;
                }
                piercesLeft--;
                remaining -= origin.distanceTo(point) + 0.2;
                origin = point.add(direction.scale(0.2));
                continue;
            }

            trail(level, origin, limit, stats);
            if (blockHit.getType() != HitResult.Type.MISS) {
                level.sendParticles(ParticleTypes.CRIT, limit.x, limit.y, limit.z, 4, 0.05, 0.05, 0.05, 0.02);
                if (stats.explosionPower() > 0) {
                    detonate(level, shooter, stats, limit);
                }
            }
            return;
        }
    }

    /** Applies damage and every on-hit trait the gun carries. */
    private static void hit(ServerLevel level, ServerPlayer shooter, GunStats stats,
                            LivingEntity target, Vec3 point) {
        double damage = stats.damage();

        // A hit above the eyeline counts as a headshot.
        boolean headshot = point.y >= target.getEyePosition().y - 0.18;
        if (headshot) {
            damage *= stats.headshotMultiplier();
        }
        if (stats.critChance() > 0 && ThreadLocalRandom.current().nextDouble() < stats.critChance()) {
            damage *= 1.8;
        }

        // Vanilla invulnerability frames would throttle fast guns to a fraction
        // of their listed rate, so they are cleared before every round lands.
        target.invulnerableTime = 0;
        target.hurtServer(level, level.damageSources().playerAttack(shooter), (float) damage);

        if (headshot) {
            level.sendParticles(ParticleTypes.CRIT, point.x, point.y, point.z, 10, 0.2, 0.2, 0.2, 0.3);
            level.playSound(null, shooter.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.PLAYERS, 0.7f, 1.7f);
        }
        if (stats.knockback() > 0) {
            Vec3 push = target.position().subtract(shooter.position()).normalize().scale(stats.knockback());
            target.push(push.x, stats.knockback() * 0.35, push.z);
        }
        if (stats.lifesteal() > 0) {
            shooter.heal((float) (damage * stats.lifesteal()));
        }
        if (stats.has(GunTrait.INCENDIARY)) {
            target.igniteForSeconds(3);
        }
        if (stats.explosionPower() > 0) {
            detonate(level, shooter, stats, point);
        }
    }

    /**
     * A hand-rolled blast. Vanilla's would grind the world into craters, and the
     * point here is the damage falloff, not the terrain.
     */
    private static void detonate(ServerLevel level, ServerPlayer shooter, GunStats stats, Vec3 centre) {
        double radius = stats.explosionPower() * 1.7;
        level.sendParticles(ParticleTypes.EXPLOSION, centre.x, centre.y, centre.z, 3,
                radius * 0.3, radius * 0.3, radius * 0.3, 0.0);
        level.playSound(null, shooter.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, 1.2f, 1.0f);

        AABB blast = new AABB(centre, centre).inflate(radius);
        for (Entity entity : level.getEntities(shooter, blast, candidate -> candidate instanceof LivingEntity)) {
            double distance = entity.position().distanceTo(centre);
            if (distance > radius) {
                continue;
            }
            double falloff = 1.0 - distance / radius;
            entity.invulnerableTime = 0;
            entity.hurtServer(level, level.damageSources().explosion(shooter, shooter),
                    (float) (stats.explosionPower() * 3.2 * falloff));
        }
    }

    private static void trail(ServerLevel level, Vec3 from, Vec3 to, GunStats stats) {
        double length = from.distanceTo(to);
        if (length < 0.05) {
            return;
        }
        Vec3 step = to.subtract(from).normalize().scale(TRAIL_STEP);
        Vec3 cursor = from;
        for (int i = 0; i < Math.min(80, (int) (length / TRAIL_STEP)); i++) {
            cursor = cursor.add(step);
            level.sendParticles(trailParticle(stats), cursor.x, cursor.y, cursor.z, 1, 0, 0, 0, 0);
        }
    }

    private static net.minecraft.core.particles.SimpleParticleType trailParticle(GunStats stats) {
        if (stats.has(GunTrait.INCENDIARY)) return ParticleTypes.FLAME;
        if (stats.has(GunTrait.LIGHTNING)) return ParticleTypes.ELECTRIC_SPARK;
        if (stats.has(GunTrait.FREEZE)) return ParticleTypes.SNOWFLAKE;
        if (stats.has(GunTrait.EXPLOSIVE)) return ParticleTypes.LARGE_SMOKE;
        return ParticleTypes.END_ROD;
    }

    private static void muzzle(ServerLevel level, ServerPlayer shooter, Vec3 eye, GunStats stats) {
        Vec3 muzzle = eye.add(shooter.getLookAngle().scale(0.8));
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 4, 0.05, 0.05, 0.05, 0.01);

        float pitch = (float) Math.max(0.5, Math.min(2.0, 2.2 - stats.damage() * 0.06));
        level.playSound(null, shooter.blockPosition(),
                stats.pellets() > 3 ? SoundEvents.FIREWORK_ROCKET_BLAST : SoundEvents.BLAZE_SHOOT,
                SoundSource.PLAYERS, 1.0f, pitch);
    }

    private static void recoil(ServerPlayer shooter, GunStats stats) {
        if (stats.recoil() <= 0.02) {
            return;
        }
        Vec3 kick = shooter.getLookAngle().scale(-stats.recoil() * 0.5);
        // Rocket Recoil is a mobility option, not a drawback.
        double lift = stats.has(GunTrait.LAUNCH) ? 0.42 : 0.0;
        shooter.push(kick.x, Math.max(kick.y, lift), kick.z);
        shooter.hurtMarked = true;
    }
}
