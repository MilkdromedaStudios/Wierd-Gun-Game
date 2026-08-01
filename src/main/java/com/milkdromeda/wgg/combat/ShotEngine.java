package com.milkdromeda.wgg.combat;

import com.milkdromeda.wgg.WeirdGunGamePlugin;
import com.milkdromeda.wgg.gun.GunStats;
import com.milkdromeda.wgg.gun.GunTrait;
import com.milkdromeda.wgg.util.Text;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Everything that happens between pulling the trigger and something regretting it.
 * <p>
 * Fast rounds are hitscan — a ray trace out to the gun's range, resolved the
 * same tick. Slow rounds (rockets, lobbed chickens, bouncing noodles) become a
 * lightweight simulated projectile that steps forward each tick and can be
 * affected by gravity and homing. Both paths converge on
 * {@link #impact} so traits behave identically either way.
 */
public final class ShotEngine {

    private static final double PIERCE_STEP = 0.15;
    private static final int MAX_BOUNCES = 3;
    private static final int PROJECTILE_MAX_TICKS = 120;

    private final WeirdGunGamePlugin plugin;

    public ShotEngine(WeirdGunGamePlugin plugin) {
        this.plugin = plugin;
    }

    /** How settled the shooter is, which is all that separates a sniper from a stick. */
    public enum Stance {
        /** Firing from the hip. */
        HIP(1.0),
        /** Aiming down sights. */
        AIMED(0.35),
        /** Scoped, crouched and holding still — the shot goes exactly where the dot is. */
        STEADY(0.0);

        private final double spreadFactor;

        Stance(double spreadFactor) {
            this.spreadFactor = spreadFactor;
        }

        public double spreadFactor() {
            return spreadFactor;
        }
    }

    // ------------------------------------------------------------------ firing

    /** Fires a single trigger pull. Ammo is expected to already be spent by the caller. */
    public void fire(Player shooter, GunStats stats, Stance stance) {
        World world = shooter.getWorld();
        Location eye = shooter.getEyeLocation();
        double spread = effectiveSpread(shooter, stats, stance);

        for (int pellet = 0; pellet < stats.pellets(); pellet++) {
            Vector direction = spreadDirection(eye, spread);
            if (stats.has(GunTrait.LOCK_ON)) {
                Vector locked = lockOn(shooter, eye, stats);
                if (locked != null) {
                    direction = locked;
                }
            }
            launch(shooter, stats, eye.clone(), direction, true);
        }

        muzzleEffects(shooter, world, eye, stats);
        applyRecoil(shooter, stats);

        if (stats.has(GunTrait.THORNS)) {
            shooter.setNoDamageTicks(0);
            shooter.damage(0.5);
        }
        if (stats.has(GunTrait.RALLY)) {
            rally(shooter);
        }
    }

    /**
     * Sends one round on its way down whichever path the gun uses.
     *
     * @param deflectable false for rounds already parried once, so a katana duel
     *                    cannot bounce a single bullet back and forth forever
     */
    private void launch(Player shooter, GunStats stats, Location origin, Vector direction, boolean deflectable) {
        if (stats.isHitscan()) {
            traceHitscan(shooter, stats, origin, direction, deflectable);
        } else {
            new ProjectileShot(shooter, stats, origin, direction, deflectable).runTaskTimer(plugin, 0L, 1L);
        }
    }

    private double effectiveSpread(Player shooter, GunStats stats, Stance stance) {
        double spread = stats.spread() * stance.spreadFactor();
        if (stance == Stance.STEADY) {
            // A steadied scope is pinpoint by definition; the other modifiers
            // would only reintroduce wobble.
            return 0.0;
        }
        if (shooter.isSneaking()) {
            spread *= 0.7;
        }
        if (shooter.isSprinting()) {
            spread *= 1.5;
        }
        if (!shooter.isOnGround()) {
            spread *= 1.4;
        }
        return spread;
    }

    private Vector spreadDirection(Location eye, double spreadDegrees) {
        if (spreadDegrees <= 0.01) {
            return eye.getDirection();
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location jittered = eye.clone();
        jittered.setYaw(jittered.getYaw() + (float) random.nextDouble(-spreadDegrees, spreadDegrees));
        jittered.setPitch(jittered.getPitch() + (float) random.nextDouble(-spreadDegrees, spreadDegrees));
        return jittered.getDirection();
    }

    /** Snaps to the nearest valid target inside a forward cone, for Target Computer builds. */
    private Vector lockOn(Player shooter, Location eye, GunStats stats) {
        LivingEntity best = null;
        double bestDot = 0.86; // roughly a 30 degree cone
        Vector look = eye.getDirection();
        for (Entity entity : shooter.getNearbyEntities(stats.range(), stats.range(), stats.range())) {
            if (!isValidTarget(entity, shooter)) {
                continue;
            }
            LivingEntity living = (LivingEntity) entity;
            Vector toTarget = living.getEyeLocation().toVector().subtract(eye.toVector());
            double distance = toTarget.length();
            if (distance < 0.1 || distance > stats.range()) {
                continue;
            }
            double dot = toTarget.clone().multiply(1.0 / distance).dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                best = living;
            }
        }
        if (best == null) {
            return null;
        }
        return best.getEyeLocation().toVector().subtract(eye.toVector()).normalize();
    }

    // ---------------------------------------------------------------- hitscan

    private void traceHitscan(Player shooter, GunStats stats, Location origin, Vector direction,
                              boolean deflectable) {
        World world = shooter.getWorld();
        Set<UUID> alreadyHit = new HashSet<>();
        int pierceLeft = stats.pierceCount();
        int bouncesLeft = stats.has(GunTrait.BOUNCE) ? MAX_BOUNCES : 0;
        double remaining = stats.range();

        while (remaining > 0.1) {
            RayTraceResult blockHit = world.rayTraceBlocks(origin, direction, remaining,
                    FluidCollisionMode.NEVER, true);
            RayTraceResult entityHit = world.rayTraceEntities(origin, direction, remaining, 0.4,
                    entity -> isValidTarget(entity, shooter) && !alreadyHit.contains(entity.getUniqueId()));

            double blockDistance = distanceTo(blockHit, origin);
            double entityDistance = distanceTo(entityHit, origin);

            if (entityHit != null && entityDistance <= blockDistance) {
                Location point = entityHit.getHitPosition().toLocation(world);
                trail(world, origin, point, stats);
                LivingEntity target = (LivingEntity) entityHit.getHitEntity();
                alreadyHit.add(target.getUniqueId());
                impact(shooter, stats, point, target, isHeadshot(target, point), direction, deflectable);

                if (pierceLeft <= 0) {
                    return;
                }
                pierceLeft--;
                remaining -= entityDistance + PIERCE_STEP;
                origin = point.clone().add(direction.clone().multiply(PIERCE_STEP));
                continue;
            }

            if (blockHit != null) {
                Location point = blockHit.getHitPosition().toLocation(world);
                trail(world, origin, point, stats);
                if (bouncesLeft > 0 && blockHit.getHitBlockFace() != null) {
                    bouncesLeft--;
                    direction = reflect(direction, blockHit.getHitBlockFace());
                    remaining -= blockDistance + 0.2;
                    origin = point.clone().add(direction.clone().multiply(0.2));
                    world.spawnParticle(Particle.CRIT, point, 4, 0.1, 0.1, 0.1, 0.02);
                    world.playSound(point, Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, 1.8f);
                    continue;
                }
                impact(shooter, stats, point, null, false, direction, false);
                return;
            }

            trail(world, origin, origin.clone().add(direction.clone().multiply(remaining)), stats);
            return;
        }
    }

    private static double distanceTo(RayTraceResult result, Location origin) {
        return result == null ? Double.MAX_VALUE : result.getHitPosition().distance(origin.toVector());
    }

    private static Vector reflect(Vector direction, BlockFace face) {
        Vector normal = face.getDirection().normalize();
        return direction.clone().subtract(normal.multiply(2 * direction.dot(normal))).normalize();
    }

    private static boolean isHeadshot(LivingEntity target, Location point) {
        return point.getY() >= target.getEyeLocation().getY() - 0.18;
    }

    // ------------------------------------------------------------- projectiles

    /** A simulated round: steps forward each tick, obeys gravity and homing. */
    private final class ProjectileShot extends BukkitRunnable {

        private final Player shooter;
        private final GunStats stats;
        private final Set<UUID> alreadyHit = new HashSet<>();
        private Location position;
        private Vector velocity;
        private int pierceLeft;
        private int bouncesLeft;
        private double travelled;
        private int ticks;
        private final boolean deflectable;

        private ProjectileShot(Player shooter, GunStats stats, Location origin, Vector direction,
                               boolean deflectable) {
            this.shooter = shooter;
            this.stats = stats;
            this.position = origin;
            this.velocity = direction.clone().normalize().multiply(stats.velocity());
            this.pierceLeft = stats.pierceCount();
            this.bouncesLeft = stats.has(GunTrait.BOUNCE) ? MAX_BOUNCES : 0;
            this.deflectable = deflectable;
        }

        @Override
        public void run() {
            if (++ticks > PROJECTILE_MAX_TICKS || travelled > stats.range() || !shooter.isOnline()) {
                cancel();
                return;
            }

            if (stats.homingStrength() > 0) {
                applyHoming();
            }
            if (stats.gravity() > 0) {
                velocity.setY(velocity.getY() - stats.gravity());
            }

            World world = position.getWorld();
            double stepLength = velocity.length();
            if (stepLength < 0.01) {
                cancel();
                return;
            }
            Vector direction = velocity.clone().multiply(1.0 / stepLength);

            RayTraceResult blockHit = world.rayTraceBlocks(position, direction, stepLength,
                    FluidCollisionMode.NEVER, true);
            RayTraceResult entityHit = world.rayTraceEntities(position, direction, stepLength, 0.4,
                    entity -> isValidTarget(entity, shooter) && !alreadyHit.contains(entity.getUniqueId()));

            double blockDistance = distanceTo(blockHit, position);
            double entityDistance = distanceTo(entityHit, position);

            if (entityHit != null && entityDistance <= blockDistance) {
                Location point = entityHit.getHitPosition().toLocation(world);
                trail(world, position, point, stats);
                LivingEntity target = (LivingEntity) entityHit.getHitEntity();
                alreadyHit.add(target.getUniqueId());
                impact(shooter, stats, point, target, isHeadshot(target, point), direction, deflectable);
                if (pierceLeft <= 0) {
                    cancel();
                    return;
                }
                pierceLeft--;
                position = point.clone().add(direction.clone().multiply(PIERCE_STEP));
                travelled += entityDistance;
                return;
            }

            if (blockHit != null) {
                Location point = blockHit.getHitPosition().toLocation(world);
                trail(world, position, point, stats);
                if (bouncesLeft > 0 && blockHit.getHitBlockFace() != null) {
                    bouncesLeft--;
                    Vector bounced = reflect(direction, blockHit.getHitBlockFace());
                    velocity = bounced.multiply(stepLength * 0.8);
                    position = point.clone().add(bounced.clone().normalize().multiply(0.2));
                    travelled += blockDistance;
                    world.playSound(point, Sound.BLOCK_SLIME_BLOCK_HIT, 0.6f, 1.4f);
                    return;
                }
                impact(shooter, stats, point, null, false, direction, false);
                cancel();
                return;
            }

            Location next = position.clone().add(velocity);
            trail(world, position, next, stats);
            position = next;
            travelled += stepLength;
        }

        private void applyHoming() {
            LivingEntity best = null;
            double bestDistance = 16.0;
            for (Entity entity : position.getWorld().getNearbyEntities(position, 16, 16, 16)) {
                if (!isValidTarget(entity, shooter) || alreadyHit.contains(entity.getUniqueId())) {
                    continue;
                }
                double distance = entity.getLocation().distance(position);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = (LivingEntity) entity;
                }
            }
            if (best == null) {
                return;
            }
            double speed = velocity.length();
            Vector toTarget = best.getEyeLocation().toVector().subtract(position.toVector()).normalize();
            velocity = velocity.normalize()
                    .multiply(1.0 - stats.homingStrength() * 0.3)
                    .add(toTarget.multiply(stats.homingStrength() * 0.3))
                    .normalize()
                    .multiply(speed);
        }
    }

    // ------------------------------------------------------------------ impact

    /**
     * Resolves a round landing: damage, then every trait the gun carries.
     *
     * @param target may be null when the round hit a wall or the ground
     */
    private void impact(Player shooter, GunStats stats, Location point, LivingEntity target, boolean headshot,
                        Vector direction, boolean deflectable) {
        World world = point.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (deflectable && target instanceof Player defender
                && plugin.parries().deflects(defender, direction)) {
            deflect(shooter, defender, stats, point);
            return;
        }

        if (target != null) {
            double damage = stats.damage() * plugin.wggConfig().globalDamageMultiplier();
            if (headshot) {
                damage *= stats.headshotMultiplier();
            }
            boolean crit = stats.critChance() > 0 && random.nextDouble() < stats.critChance();
            if (crit) {
                damage *= 1.8;
            }

            applyDamage(shooter, target, damage);

            if (headshot || crit) {
                world.spawnParticle(Particle.CRIT, target.getEyeLocation(), 12, 0.2, 0.2, 0.2, 0.35);
                shooter.playSound(shooter.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f,
                        crit ? 1.9f : 1.5f);
            }

            if (stats.knockback() > 0) {
                Vector push = target.getLocation().toVector()
                        .subtract(shooter.getLocation().toVector());
                if (push.lengthSquared() > 0.01) {
                    target.setVelocity(target.getVelocity().add(
                            push.normalize().multiply(stats.knockback()).setY(stats.knockback() * 0.4)));
                }
            }
            if (stats.lifesteal() > 0) {
                heal(shooter, damage * stats.lifesteal());
            }
            if (stats.has(GunTrait.INCENDIARY)) {
                target.setFireTicks(Math.max(target.getFireTicks(), 60));
            }
            if (stats.has(GunTrait.FREEZE)) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, false, true));
                target.setFreezeTicks(Math.min(target.getMaxFreezeTicks(), target.getFreezeTicks() + 80));
            }
            if (stats.has(GunTrait.WITHER)) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0, false, true));
            }
            if (stats.has(GunTrait.GLOW_TARGET)) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 120, 0, false, false));
            }
            if (stats.has(GunTrait.BLINK)) {
                blinkToward(shooter, point);
            }
            if (stats.has(GunTrait.KILL_REFUND) && target.isDead()) {
                plugin.gunController().refundAmmo(shooter, 3);
            }
        }

        if (stats.explosionPower() > 0) {
            explode(shooter, point, stats.explosionPower(), stats.has(GunTrait.SELF_SAFE));
        }
        if (stats.has(GunTrait.LIGHTNING) && random.nextDouble() < stats.lightningChance()) {
            world.strikeLightningEffect(point);
            world.playSound(point, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.3f);
            for (Entity entity : world.getNearbyEntities(point, 2.5, 3, 2.5)) {
                if (isValidTarget(entity, shooter)) {
                    applyDamage(shooter, (LivingEntity) entity, 4.0);
                }
            }
        }
        if (stats.has(GunTrait.VORTEX)) {
            vortex(shooter, point);
        }
        if (stats.has(GunTrait.SHOCKWAVE)) {
            shockwave(shooter, point);
        }
        if (stats.has(GunTrait.POULTRY)) {
            world.playSound(point, Sound.ENTITY_CHICKEN_HURT, 0.8f, 1.2f);
            if (random.nextDouble() < 0.10) {
                Chicken chicken = world.spawn(point, Chicken.class);
                chicken.setInvulnerable(false);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (chicken.isValid()) {
                            chicken.getWorld().spawnParticle(Particle.POOF, chicken.getLocation(), 6,
                                    0.2, 0.2, 0.2, 0.02);
                            chicken.remove();
                        }
                    }
                }.runTaskLater(plugin, 100L);
            }
        }

        world.spawnParticle(Particle.SMOKE, point, 5, 0.08, 0.08, 0.08, 0.01);
    }

    /**
     * A katana parry: the round stops dead and is sent straight back down its
     * own flight path at whoever fired it, keeping the original gun's stats.
     * The return shot is marked undeflectable so two katanas cannot rally a
     * single bullet between them indefinitely.
     */
    private void deflect(Player shooter, Player defender, GunStats stats, Location point) {
        World world = point.getWorld();
        world.playSound(point, Sound.BLOCK_ANVIL_LAND, 0.9f, 1.9f);
        world.spawnParticle(Particle.CRIT, point, 24, 0.25, 0.25, 0.25, 0.5);
        world.spawnParticle(Particle.ELECTRIC_SPARK, point, 14, 0.2, 0.2, 0.2, 0.2);
        world.spawnParticle(Particle.SWEEP_ATTACK, point, 2, 0.2, 0.2, 0.2, 0);

        defender.sendActionBar(Text.mm("<#7dd3fc><bold>DEFLECTED</bold></#7dd3fc> <gray>— returned to sender</gray>"));
        shooter.playSound(shooter.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 2.0f);

        Location origin = defender.getEyeLocation();
        Vector back = shooter.getEyeLocation().toVector().subtract(origin.toVector());
        if (back.lengthSquared() < 0.01) {
            return;
        }
        launch(defender, stats, origin, back.normalize(), false);
    }

    /** Applies damage while bypassing vanilla invulnerability frames, so fast guns actually fire fast. */
    private void applyDamage(Player shooter, LivingEntity target, double damage) {
        if (target instanceof Player victim && !plugin.wggConfig().friendlyFire()
                && plugin.tournaments().sameTeam(shooter, victim)) {
            return;
        }
        PluginDamage.apply(target, damage, shooter);
    }

    private void heal(Player player, double amount) {
        double max = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        player.setHealth(Math.min(max, player.getHealth() + amount));
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 2, 0), 1, 0.2, 0.2, 0.2, 0);
    }

    /**
     * A hand-rolled explosion. Vanilla's would either grief the arena or ignore
     * the Cushion Stock, so this does the falloff, knockback and self-safety itself.
     */
    public void explode(Player shooter, Location center, double power, boolean selfSafe) {
        World world = center.getWorld();
        double radius = power * 1.7;

        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.LARGE_SMOKE, center, 24, radius * 0.3, radius * 0.3, radius * 0.3, 0.05);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 1.0f + (float) (0.4 - power * 0.1));

        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || living.isDead()) {
                continue;
            }
            double distance = living.getLocation().distance(center);
            if (distance > radius) {
                continue;
            }
            double falloff = 1.0 - (distance / radius);
            double damage = power * 3.2 * falloff;

            if (living.equals(shooter)) {
                if (selfSafe || !plugin.wggConfig().selfDamageFromExplosions()) {
                    // Still get the shove — rocket jumping needs it — but no damage.
                    push(living, center, power * falloff * 0.9);
                    continue;
                }
                damage *= 0.5;
            } else if (!isValidTarget(living, shooter)) {
                continue;
            }

            applyDamage(shooter, living, damage);
            push(living, center, power * falloff * 0.6);
        }

        if (plugin.wggConfig().explosionsBreakBlocks()) {
            world.createExplosion(center, (float) power, false, true, shooter);
        }
    }

    private void push(LivingEntity entity, Location center, double strength) {
        Vector away = entity.getLocation().toVector().subtract(center.toVector());
        if (away.lengthSquared() < 0.01) {
            away = new Vector(0, 1, 0);
        }
        entity.setVelocity(entity.getVelocity().add(away.normalize().multiply(strength).setY(strength * 0.6)));
    }

    private void vortex(Player shooter, Location center) {
        World world = center.getWorld();
        world.spawnParticle(Particle.PORTAL, center, 60, 0.4, 0.4, 0.4, 1.2);
        world.playSound(center, Sound.BLOCK_PORTAL_TRIGGER, 0.5f, 1.8f);
        for (Entity entity : world.getNearbyEntities(center, 6, 6, 6)) {
            if (!(entity instanceof LivingEntity living) || living.equals(shooter)) {
                continue;
            }
            Vector pull = center.toVector().subtract(living.getLocation().toVector());
            if (pull.lengthSquared() < 0.01) {
                continue;
            }
            living.setVelocity(living.getVelocity().add(pull.normalize().multiply(0.55)));
        }
    }

    private void shockwave(Player shooter, Location center) {
        World world = center.getWorld();
        world.spawnParticle(Particle.SONIC_BOOM, center, 1, 0, 0, 0, 0);
        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 1.6f);
        for (Entity entity : world.getNearbyEntities(center, 4, 4, 4)) {
            if (!(entity instanceof LivingEntity living) || living.equals(shooter)) {
                continue;
            }
            push(living, center, 0.9);
        }
    }

    private void blinkToward(Player shooter, Location point) {
        Vector toPoint = point.toVector().subtract(shooter.getLocation().toVector());
        double distance = toPoint.length();
        if (distance < 3.0) {
            return;
        }
        Location destination = shooter.getLocation().add(toPoint.normalize().multiply(Math.min(distance - 2.0, 6.0)));
        destination.setY(shooter.getLocation().getY());
        if (destination.getBlock().isPassable() && destination.clone().add(0, 1, 0).getBlock().isPassable()) {
            shooter.getWorld().spawnParticle(Particle.REVERSE_PORTAL, shooter.getLocation(), 20, 0.3, 0.6, 0.3, 0.1);
            shooter.teleport(destination.setDirection(shooter.getLocation().getDirection()));
            shooter.playSound(shooter.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.5f);
        }
    }

    private void rally(Player shooter) {
        for (Entity entity : shooter.getNearbyEntities(8, 4, 8)) {
            if (entity instanceof Player ally && plugin.tournaments().sameTeam(shooter, ally)) {
                ally.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0, true, false));
                ally.getWorld().spawnParticle(Particle.NOTE, ally.getLocation().add(0, 2.2, 0), 1, 0.3, 0.1, 0.3, 1);
            }
        }
    }

    // ------------------------------------------------------------- presentation

    private void muzzleEffects(Player shooter, World world, Location eye, GunStats stats) {
        Location muzzle = eye.clone().add(eye.getDirection().multiply(0.8));
        world.spawnParticle(Particle.SMOKE, muzzle, 4, 0.05, 0.05, 0.05, 0.02);

        float pitch = (float) Math.max(0.5, Math.min(2.0, 2.2 - stats.damage() * 0.06));
        if (stats.explosionPower() > 0) {
            world.playSound(muzzle, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.6f);
        } else if (stats.pellets() > 3) {
            world.playSound(muzzle, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 0.8f);
        } else if (stats.has(GunTrait.POULTRY)) {
            world.playSound(muzzle, Sound.ENTITY_CHICKEN_EGG, 1.0f, 1.4f);
        } else {
            world.playSound(muzzle, Sound.ENTITY_BLAZE_SHOOT, 0.9f, pitch);
        }
    }

    private void applyRecoil(Player shooter, GunStats stats) {
        if (stats.recoil() <= 0.02) {
            return;
        }
        Vector kick = shooter.getEyeLocation().getDirection().multiply(-stats.recoil() * 0.55);
        if (stats.has(GunTrait.LAUNCH)) {
            kick.setY(Math.max(kick.getY(), 0.42));
        }
        shooter.setVelocity(shooter.getVelocity().add(kick));
    }

    private void trail(World world, Location from, Location to, GunStats stats) {
        if (!plugin.wggConfig().showTrails()) {
            return;
        }
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        if (length < 0.05) {
            return;
        }
        Vector step = delta.multiply(1.0 / length).multiply(0.7);
        int points = Math.min(80, (int) (length / 0.7));
        Location cursor = from.clone();

        // Resolved once per trail rather than once per particle — this runs for
        // every pellet of every shot.
        Particle particle = trailParticle(stats);
        Particle.DustOptions dust = particle == Particle.DUST ? trailDust(stats) : null;

        for (int i = 0; i < points; i++) {
            cursor.add(step);
            world.spawnParticle(particle, cursor, 1, 0, 0, 0, 0, dust);
        }
    }

    private Particle trailParticle(GunStats stats) {
        if (stats.has(GunTrait.LIGHTNING)) return Particle.ELECTRIC_SPARK;
        if (stats.has(GunTrait.FREEZE)) return Particle.SNOWFLAKE;
        if (stats.has(GunTrait.INCENDIARY)) return Particle.FLAME;
        if (stats.has(GunTrait.WITHER)) return Particle.SOUL_FIRE_FLAME;
        if (stats.has(GunTrait.VORTEX)) return Particle.REVERSE_PORTAL;
        if (stats.has(GunTrait.EXPLOSIVE)) return Particle.LARGE_SMOKE;
        if (stats.has(GunTrait.POULTRY)) return Particle.ITEM_SLIME;
        return Particle.DUST;
    }

    private Particle.DustOptions trailDust(GunStats stats) {
        Color color = stats.has(GunTrait.HOMING) ? Color.fromRGB(0x7D, 0xD3, 0xFC)
                : stats.damage() > 12 ? Color.fromRGB(0xF4, 0x3F, 0x5E)
                : Color.fromRGB(0xFF, 0xD1, 0x8A);
        return new Particle.DustOptions(color, 0.7f);
    }

    /** A target we are allowed to shoot: alive, not the shooter, not a spectator. */
    public boolean isValidTarget(Entity entity, Player shooter) {
        if (!(entity instanceof LivingEntity living) || living.isDead() || living.equals(shooter)) {
            return false;
        }
        if (living instanceof Player player) {
            return player.getGameMode() != org.bukkit.GameMode.SPECTATOR
                    && player.getGameMode() != org.bukkit.GameMode.CREATIVE;
        }
        return true;
    }
}
