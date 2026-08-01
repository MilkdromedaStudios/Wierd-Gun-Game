package com.milkdromeda.wgg.tournament;

import com.milkdromeda.wgg.WeirdGunGamePlugin;
import com.milkdromeda.wgg.util.Keys;
import com.milkdromeda.wgg.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * THE SUPERBOX.
 * <p>
 * A magma cube the size of a small building, because the boss is supposed to be
 * a box and magma cubes are the only genuinely cubic mob in the game. It runs
 * three phases that escalate as its health drops, and its attacks are picked at
 * random from whatever that phase allows so no two rounds play the same.
 */
public final class SuperboxBoss {

    public static final String BOSS_TAG_VALUE = "superbox";
    public static final String MINION_TAG_VALUE = "minibox";

    private static final int BOSS_SIZE = 6;
    private static final int MINION_SIZE = 2;
    private static final double MINION_HEALTH = 30.0;

    private final WeirdGunGamePlugin plugin;
    private final Tournament tournament;
    private final int round;
    private final double maxHealth;

    private MagmaCube entity;
    private BossBar bossBar;
    private BukkitTask brainTask;
    private final List<MagmaCube> minions = new ArrayList<>();

    private int attackCooldown;
    private int phase = 1;
    private boolean defeated;

    public SuperboxBoss(WeirdGunGamePlugin plugin, Tournament tournament, int round, double maxHealth) {
        this.plugin = plugin;
        this.tournament = tournament;
        this.round = round;
        this.maxHealth = maxHealth;
    }

    // ------------------------------------------------------------------ spawn

    public void spawn(Location center) {
        World world = center.getWorld();
        Location spawnAt = center.clone().add(0, 1, 0);

        entity = world.spawn(spawnAt, MagmaCube.class, cube -> {
            cube.setSize(BOSS_SIZE);
            cube.setRemoveWhenFarAway(false);
            cube.setPersistent(true);
            cube.customName(Text.mm("<gradient:#ff4fd8:#ff8a3d><bold>SUPERBOX</bold></gradient>"
                    + " <dark_gray>— Round " + round + "</dark_gray>"));
            cube.setCustomNameVisible(true);
            cube.getPersistentDataContainer().set(Keys.BOSS_TAG, PersistentDataType.STRING, BOSS_TAG_VALUE);

            setAttribute(cube, Attribute.MAX_HEALTH, maxHealth);
            setAttribute(cube, Attribute.KNOCKBACK_RESISTANCE, 1.0);
            setAttribute(cube, Attribute.MOVEMENT_SPEED, 0.28);
            setAttribute(cube, Attribute.FOLLOW_RANGE, 64.0);
            cube.setHealth(maxHealth);
        });

        bossBar = BossBar.bossBar(
                Text.mm("<gradient:#ff4fd8:#ff8a3d><bold>SUPERBOX</bold></gradient> <dark_gray>Round "
                        + round + "</dark_gray>"),
                1.0f, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_10);
        tournament.participants().forEach(player -> player.showBossBar(bossBar));

        world.playSound(spawnAt, Sound.ENTITY_WITHER_SPAWN, 1.6f, 0.6f);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, spawnAt, 3, 1.5, 1.5, 1.5, 0);

        for (Player player : tournament.participants()) {
            player.showTitle(Title.title(
                    Text.mm("<gradient:#ff4fd8:#ff8a3d><bold>SUPERBOX</bold></gradient>"),
                    Text.mm("<gray>Round " + round + " — " + (int) maxHealth + " HP</gray>"),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(600))));
        }

        brainTask = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 20L, 10L);
    }

    private static void setAttribute(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    // ------------------------------------------------------------------- brain

    private void tick() {
        if (defeated) {
            return;
        }
        if (entity == null || entity.isDead() || !entity.isValid()) {
            onDefeated();
            return;
        }

        double healthFraction = entity.getHealth() / maxHealth;
        bossBar.progress((float) Math.max(0.0, Math.min(1.0, healthFraction)));
        updatePhase(healthFraction);

        List<Player> targets = tournament.livingParticipants();
        if (targets.isEmpty()) {
            return;
        }

        // Keep it moving toward someone even if vanilla AI loses interest.
        Player nearest = nearest(targets);
        if (nearest != null && entity.getTarget() == null) {
            entity.setTarget(nearest);
        }

        if (--attackCooldown > 0) {
            return;
        }
        attackCooldown = switch (phase) {
            case 3 -> 5;
            case 2 -> 7;
            default -> 10;
        };

        chooseAttack(nearest, targets);
    }

    private void updatePhase(double healthFraction) {
        int next = healthFraction <= 0.33 ? 3 : healthFraction <= 0.66 ? 2 : 1;
        if (next == phase) {
            return;
        }
        phase = next;

        World world = entity.getWorld();
        world.playSound(entity.getLocation(), Sound.ENTITY_WITHER_HURT, 1.5f, 0.5f);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, entity.getLocation(), 2, 1, 1, 1, 0);

        String message = switch (phase) {
            case 2 -> "<#facc15>The Superbox has drawn its guns.";
            case 3 -> "<#f43f5e>The Superbox is <bold>ENRAGED</bold>.";
            default -> "";
        };
        for (Player player : tournament.participants()) {
            player.sendMessage(Text.msg(message));
            player.showTitle(Title.title(
                    Text.mm("<#f43f5e><bold>PHASE " + phase + "</bold>"),
                    Text.mm(message),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(1), Duration.ofMillis(400))));
        }

        if (phase == 3) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false));
            setAttribute(entity, Attribute.MOVEMENT_SPEED, 0.4);
        }
    }

    private void chooseAttack(Player nearest, List<Player> targets) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Runnable> options = new ArrayList<>();

        options.add(this::slam);
        if (minions.size() < 6) {
            options.add(() -> summonMinions(plugin.wggConfig().minionsPerRound()));
        }
        if (phase >= 2) {
            options.add(() -> volley(nearest));
            options.add(() -> laserSweep(nearest));
        }
        if (phase >= 3) {
            options.add(() -> barrage(targets));
            options.add(() -> volley(nearest));
        }

        options.get(random.nextInt(options.size())).run();
    }

    // ----------------------------------------------------------------- attacks

    /** Leaps, then flattens everything nearby on landing. */
    private void slam() {
        World world = entity.getWorld();
        entity.setVelocity(new Vector(0, 0.85, 0));
        world.playSound(entity.getLocation(), Sound.ENTITY_MAGMA_CUBE_JUMP, 1.5f, 0.5f);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity == null || entity.isDead()) {
                    return;
                }
                Location impact = entity.getLocation();
                world.spawnParticle(Particle.EXPLOSION, impact, 8, 2.5, 0.4, 2.5, 0);
                world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.6f);

                for (Player player : tournament.livingParticipants()) {
                    double distance = player.getLocation().distance(impact);
                    if (distance > 8.0) {
                        continue;
                    }
                    double damage = 8.0 * (1.0 - distance / 8.0) + 2.0;
                    player.setNoDamageTicks(0);
                    player.damage(damage, entity);
                    Vector push = player.getLocation().toVector().subtract(impact.toVector());
                    if (push.lengthSquared() > 0.01) {
                        player.setVelocity(push.normalize().multiply(0.9).setY(0.55));
                    }
                }
            }
        }.runTaskLater(plugin, 22L);
    }

    /** Phase 2: the box has guns now. Five explosive rounds, lobbed at a player. */
    private void volley(Player target) {
        if (target == null) {
            return;
        }
        World world = entity.getWorld();
        world.playSound(entity.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.6f, 0.7f);

        new BukkitRunnable() {
            int fired;

            @Override
            public void run() {
                if (fired++ >= 5 || entity == null || entity.isDead() || !target.isOnline()) {
                    cancel();
                    return;
                }
                Location muzzle = entity.getEyeLocation();
                Vector direction = target.getEyeLocation().toVector()
                        .subtract(muzzle.toVector()).normalize();
                launchShell(muzzle, direction);
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    /** A slow travelling shell that detonates on the first thing it touches. */
    private void launchShell(Location origin, Vector direction) {
        new BukkitRunnable() {
            Location position = origin.clone();
            final Vector velocity = direction.clone().multiply(1.1);
            int ticks;

            @Override
            public void run() {
                if (++ticks > 60) {
                    cancel();
                    return;
                }
                World world = position.getWorld();
                position = position.add(velocity);
                world.spawnParticle(Particle.FLAME, position, 3, 0.05, 0.05, 0.05, 0.01);

                if (!position.getBlock().isPassable()) {
                    detonate(position, 2.2, 7.0);
                    cancel();
                    return;
                }
                for (Player player : tournament.livingParticipants()) {
                    if (player.getLocation().add(0, 1, 0).distance(position) < 1.6) {
                        detonate(position, 2.2, 7.0);
                        cancel();
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Phase 2: sweeps a damaging beam across the arena toward a player. */
    private void laserSweep(Player target) {
        if (target == null) {
            return;
        }
        World world = entity.getWorld();
        world.playSound(entity.getLocation(), Sound.ENTITY_GUARDIAN_ATTACK, 1.6f, 0.6f);
        Location origin = entity.getEyeLocation();
        Vector toTarget = target.getEyeLocation().toVector().subtract(origin.toVector()).normalize();

        new BukkitRunnable() {
            int step;

            @Override
            public void run() {
                if (step++ > 12 || entity == null || entity.isDead()) {
                    cancel();
                    return;
                }
                // Rotate the beam a few degrees each tick so it sweeps rather than snipes.
                double angle = Math.toRadians((step - 6) * 4.0);
                Vector swept = rotateAroundY(toTarget, angle);

                Location cursor = origin.clone();
                for (int i = 0; i < 40; i++) {
                    cursor.add(swept.clone().multiply(1.0));
                    if (!cursor.getBlock().isPassable()) {
                        break;
                    }
                    world.spawnParticle(Particle.ELECTRIC_SPARK, cursor, 2, 0.05, 0.05, 0.05, 0.01);
                    for (Player player : tournament.livingParticipants()) {
                        if (player.getLocation().add(0, 1, 0).distance(cursor) < 1.5) {
                            player.setNoDamageTicks(0);
                            player.damage(3.5, entity);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /** Phase 3: explosions rain on everyone at once. */
    private void barrage(List<Player> targets) {
        World world = entity.getWorld();
        world.playSound(entity.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.8f, 0.5f);

        for (Player player : targets) {
            Location marked = player.getLocation().clone();
            world.spawnParticle(Particle.FLAME, marked.clone().add(0, 0.2, 0), 30, 1.2, 0.1, 1.2, 0.01);
            new BukkitRunnable() {
                @Override
                public void run() {
                    detonate(marked, 2.6, 8.0);
                }
            }.runTaskLater(plugin, 30L);
        }
    }

    private void detonate(Location center, double radius, double damage) {
        World world = center.getWorld();
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1, 0, 0, 0, 0);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.9f);

        for (Player player : tournament.livingParticipants()) {
            double distance = player.getLocation().distance(center);
            if (distance > radius * 1.8) {
                continue;
            }
            double falloff = 1.0 - Math.min(1.0, distance / (radius * 1.8));
            player.setNoDamageTicks(0);
            player.damage(damage * falloff, entity);
            Vector push = player.getLocation().toVector().subtract(center.toVector());
            if (push.lengthSquared() > 0.01) {
                player.setVelocity(player.getVelocity().add(push.normalize().multiply(0.6).setY(0.35)));
            }
        }
    }

    public void summonMinions(int count) {
        World world = entity.getWorld();
        minions.removeIf(minion -> minion == null || minion.isDead() || !minion.isValid());

        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2 * i / count;
            Location at = entity.getLocation().clone()
                    .add(Math.cos(angle) * 3.5, 1.0, Math.sin(angle) * 3.5);

            MagmaCube minion = world.spawn(at, MagmaCube.class, cube -> {
                cube.setSize(MINION_SIZE);
                cube.setRemoveWhenFarAway(false);
                cube.customName(Text.mm("<#ff8a3d>Minibox</#ff8a3d>"));
                cube.setCustomNameVisible(true);
                cube.getPersistentDataContainer()
                        .set(Keys.BOSS_TAG, PersistentDataType.STRING, MINION_TAG_VALUE);
                setAttribute(cube, Attribute.MAX_HEALTH, MINION_HEALTH);
                setAttribute(cube, Attribute.KNOCKBACK_RESISTANCE, 0.4);
                cube.setHealth(MINION_HEALTH);
                cube.setGlowing(true);
            });
            minions.add(minion);
        }
        world.playSound(entity.getLocation(), Sound.ENTITY_MAGMA_CUBE_SQUISH, 1.4f, 0.7f);
    }

    // ------------------------------------------------------------------ finish

    private void onDefeated() {
        if (defeated) {
            return;
        }
        defeated = true;
        cleanupTasks();
        tournament.onBossDefeated(round);
    }

    /** Removes the boss and every minion, e.g. when a tournament is cancelled. */
    public void despawn() {
        defeated = true;
        cleanupTasks();
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
        for (MagmaCube minion : minions) {
            if (minion != null && minion.isValid()) {
                minion.remove();
            }
        }
        minions.clear();
    }

    private void cleanupTasks() {
        if (brainTask != null) {
            brainTask.cancel();
            brainTask = null;
        }
        if (bossBar != null) {
            tournament.participants().forEach(player -> player.hideBossBar(bossBar));
        }
    }

    public Location location() {
        return entity == null ? null : entity.getLocation();
    }

    public boolean isAlive() {
        return entity != null && !entity.isDead() && entity.isValid();
    }

    private Player nearest(List<Player> candidates) {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        Location here = entity.getLocation();
        for (Player player : candidates) {
            if (!player.getWorld().equals(here.getWorld())) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(here);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private static Vector rotateAroundY(Vector vector, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vector(
                vector.getX() * cos - vector.getZ() * sin,
                vector.getY(),
                vector.getX() * sin + vector.getZ() * cos).normalize();
    }

    /** True when the entity carries one of the boss tags, used for knife bonuses and cleanup. */
    public static boolean isBossEntity(Entity entity) {
        return entity.getPersistentDataContainer().has(Keys.BOSS_TAG, PersistentDataType.STRING);
    }
}
