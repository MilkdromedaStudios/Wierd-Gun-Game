package com.milkdromeda.ledger.boss;

import com.milkdromeda.ledger.watch.WatchRecord;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * THE WITNESS.
 * <p>
 * The record does not stay a record. Once the world has been catalogued enough
 * times the tally stands up, wearing the blocks it watched you take, and asks
 * you about them.
 * <p>
 * The body is a halo of invisible armour stands each balancing one block on its
 * head, orbiting an invisible core. That is a deliberate choice rather than a
 * compromise: the blocks are picked from what the players actually mined, so
 * every Witness is assembled out of its own victims' habits. It also needs no
 * client mod, no resource pack, no third-party library and no mixin — armour
 * stand equipment is plain public API.
 * <p>
 * <b>Eight stages.</b> The shape of the fight is borrowed from Cracker's Wither
 * Storm: it does not simply get angrier as its health drops, it gets
 * <i>bigger</i>. Every stage adds another ring of blocks to its body, widens the
 * halo, spins it faster and unlocks one more thing it can do to you. From the
 * third stage on it stops waiting for you to hand it blocks and starts tearing
 * them out of the world, and each one it takes is welded onto its body — so the
 * longer the fight runs, the larger the thing you are fighting and the emptier
 * the ground you are standing on.
 * <p>
 * Its hitbox grows with it: {@code WitnessManager} moves any hit on the halo
 * onto the core, so a stage-eight Witness is a wheel of blocks ten metres across
 * that is almost impossible to miss — which is the only reason the fight stays
 * winnable while it is doing all of this to you.
 * <p>
 * It is worth five thousand points of damage, plus fifteen hundred for every
 * player past the first. That number cannot live in the entity — vanilla clamps
 * the health attribute to something far smaller — so the core's own health is
 * used only as a damage sensor and the real pool is kept here.
 */
public final class Witness {

    /** Interactions across the whole world before it wakes up on its own. */
    public static final long AWAKENING = 10_000L;

    /** How many stages it climbs through on the way down. */
    public static final int STAGES = 8;

    /**
     * How much damage it is actually worth, and what each extra player adds.
     * <p>
     * This is not the entity's health. Vanilla clamps the {@code max_health}
     * attribute — a magma cube would not take more than about 150 however
     * politely it was asked — which is far too small for a fight with eight
     * stages. So the entity's own health is only a damage sensor: whatever it
     * loses each tick is subtracted from this pool and then topped straight back
     * up, and the pool is what the boss bar, the stages and dying all read from.
     * Every source counts, because it measures health actually lost rather than
     * trying to intercept each way of dealing it.
     */
    private static final float POOL = 5000.0f;
    private static final float POOL_PER_EXTRA_PLAYER = 1500.0f;

    /** The body it wakes up with, and what each stage welds on. */
    private static final int HALO_START = 24;
    private static final int HALO_PER_STAGE = 12;
    private static final int HALO_MAX = 140;

    private static final double RADIUS_START = 3.4;
    private static final double RADIUS_PER_STAGE = 0.9;

    /** Blocks per tick the core closes on you. Deliberately unhurried. */
    private static final double DRIFT_SPEED = 0.055;

    /** How far above your feet it wants to sit. */
    private static final double HOVER = 2.6;

    /** It stops here rather than pressing into your face, where nothing is visible. */
    private static final double STANDOFF = 7.0;

    /** How far it can reach to pull you, and to tear the world up. */
    private static final double REACH = 30.0;

    private final ServerLevel level;
    private final LivingEntity core;
    private final ServerBossEvent bar;
    private final List<ArmorStand> halo = new ArrayList<>();
    private final List<ItemStack> blocks;
    private final float poolMax;

    private float pool;
    private float lastSeenHealth;
    private int stage = 1;
    private int age;
    private boolean finished;
    private int devoured;

    private Witness(ServerLevel level, LivingEntity core, List<ItemStack> blocks, float poolMax) {
        this.level = level;
        this.core = core;
        this.blocks = blocks;
        this.poolMax = poolMax;
        this.pool = poolMax;
        this.lastSeenHealth = core.getMaxHealth();
        this.bar = new ServerBossEvent(UUID.randomUUID(), title(1),
                BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
        this.bar.setDarkenScreen(true);
        this.bar.setPlayBossMusic(true);
    }

    private static Component title(int stage) {
        return Component.literal("THE WITNESS  ·  Stage " + stage + "/" + STAGES)
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD);
    }

    /**
     * Assembles the Witness out of the blocks the given records show were taken.
     *
     * @return null if the core entity could not be spawned
     */
    public static Witness assemble(ServerLevel level, BlockPos where, Map<UUID, WatchRecord> records) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE
                .getOptional(Identifier.withDefaultNamespace("magma_cube")).orElse(null);
        if (type == null) {
            return null;
        }
        Entity spawned = type.spawn(level, where, EntitySpawnReason.EVENT);
        if (!(spawned instanceof LivingEntity core)) {
            return null;
        }

        core.setCustomName(Component.literal("THE WITNESS")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
        core.setCustomNameVisible(true);
        core.setInvisible(true);
        // Ask for a lot; the attribute will clamp to whatever it feels like. The
        // number does not matter, because the pool is the real health bar.
        if (core.getAttribute(Attributes.MAX_HEALTH) != null) {
            core.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024.0);
        }
        core.setHealth(core.getMaxHealth());
        // The core is a magma cube only because something invisible had to hold
        // the health bar. Left with its own AI it hops away like a slime, lands
        // on the floor and drags its halo out of sight, which is neither
        // frightening nor possible to shoot. It hangs still instead, and this
        // class moves it.
        core.setNoGravity(true);
        if (core instanceof Mob mob) {
            mob.setPersistenceRequired();
            mob.setNoAi(true);
        }

        float pool = POOL + POOL_PER_EXTRA_PLAYER * Math.max(0, level.players().size() - 1);
        Witness witness = new Witness(level, core, blocksFrom(records), pool);
        witness.growTo(HALO_START);
        witness.announce();
        return witness;
    }

    /** The blocks it wears are the blocks people took, most-taken first. */
    private static List<ItemStack> blocksFrom(Map<UUID, WatchRecord> records) {
        List<ItemStack> stacks = new ArrayList<>();
        for (WatchRecord record : records.values()) {
            for (Map.Entry<String, Integer> entry : record.topMined(6)) {
                Identifier id = Identifier.tryParse(entry.getKey());
                if (id == null) {
                    continue;
                }
                BuiltInRegistries.ITEM.getOptional(id)
                        .ifPresent(item -> stacks.add(new ItemStack(item)));
            }
        }
        if (stacks.isEmpty()) {
            // Nothing recorded yet, so it turns up wearing plain stone instead.
            stacks.add(new ItemStack(Items.STONE));
        }
        return stacks;
    }

    // ------------------------------------------------------------------- body

    /** Grows the halo to the given size. Never shrinks; it only ever accretes. */
    private void growTo(int size) {
        EntityType<?> standType = BuiltInRegistries.ENTITY_TYPE
                .getOptional(Identifier.withDefaultNamespace("armor_stand")).orElse(null);
        if (standType == null) {
            return;
        }
        for (int i = halo.size(); i < Math.min(size, HALO_MAX); i++) {
            Entity spawned = standType.spawn(level, core.blockPosition(), EntitySpawnReason.EVENT);
            if (!(spawned instanceof ArmorStand stand)) {
                continue;
            }
            stand.setInvisible(true);
            stand.setNoGravity(true);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setItemSlot(EquipmentSlot.HEAD, blocks.get(i % blocks.size()).copy());
            halo.add(stand);
        }
    }

    /** How wide the wheel is at the current stage. */
    private double radius() {
        return RADIUS_START + RADIUS_PER_STAGE * (stage - 1);
    }

    private void announce() {
        for (ServerPlayer player : level.players()) {
            bar.addPlayer(player);
            player.sendSystemMessage(Component.literal("I have finished counting.")
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
        level.playSound(null, core.blockPosition(), SoundEvents.WITHER_SPAWN,
                SoundSource.HOSTILE, 4.0f, 0.5f);
    }

    // ------------------------------------------------------------------- tick

    public boolean isFinished() {
        return finished;
    }

    public LivingEntity core() {
        return core;
    }

    public int stage() {
        return stage;
    }

    public int devoured() {
        return devoured;
    }

    /**
     * Whether a hit on this entity should count as a hit on the Witness.
     * <p>
     * The halo is what you can see and what you will aim at, so shooting a block
     * it is wearing has to hurt it. Without this the fight is unwinnable: the
     * stands are invulnerable, they orbit between you and the core, and every
     * round stops on one of them.
     */
    public boolean isBody(LivingEntity entity) {
        if (entity == core) {
            return true;
        }
        for (ArmorStand stand : halo) {
            if (stand == entity) {
                return true;
            }
        }
        return false;
    }

    /** Drives the body, the stages and the attacks. Called every server tick. */
    public void tick() {
        if (finished) {
            return;
        }
        if (!core.isAlive()) {
            die();
            return;
        }
        age++;

        float fraction = drainPool();
        if (fraction <= 0.0f) {
            core.setHealth(0.0f);
            die();
            return;
        }
        bar.setProgress(fraction);
        updateStage(fraction);
        drift();
        spinHalo();
        attack();
    }

    /**
     * Moves whatever damage the core took this tick into the pool, then heals it
     * back to full so it can absorb more. The entity is a sensor, not a health
     * bar — see {@link #POOL}.
     *
     * @return how much of the pool is left, 0 to 1
     */
    private float drainPool() {
        float now = core.getHealth();
        float taken = lastSeenHealth - now;
        if (taken > 0.0f) {
            pool -= taken;
        }
        float full = core.getMaxHealth();
        if (now < full) {
            core.setHealth(full);
        }
        // Topping it up leaves vanilla's invulnerability window in place, and
        // that window compares against the last hit it took — so a second blow
        // of the same size inside a second is ignored outright. Guns clear this
        // themselves; clearing it here means everything else lands too, and the
        // pool is worth the same five thousand however you spend it.
        core.invulnerableTime = 0;
        lastSeenHealth = full;
        return Math.max(0.0f, pool / poolMax);
    }

    /**
     * Closes on the nearest player at walking pace and then hangs there.
     * <p>
     * Slow on purpose: the thing is a wall of blocks the size of a house, and it
     * does not need to hurry. It also has to stay in front of whoever is
     * shooting at it, or the fight is spent hunting for an invisible slime.
     */
    private void drift() {
        ServerPlayer nearest = nearest();
        if (nearest == null) {
            return;
        }
        // Nothing shoves it. Without gravity or AI there is nothing to damp the
        // knockback a gun applies, so every round it takes would push it a little
        // further away until the fight was happening at the edge of render
        // distance. Clearing the velocity each tick makes this method the only
        // thing that decides where it is.
        core.setDeltaMovement(Vec3.ZERO);

        Vec3 toPlayer = nearest.position().add(0.0, HOVER, 0.0).subtract(core.position());
        if (toPlayer.length() <= STANDOFF) {
            return;
        }
        // It speeds up as it grows. By the last stage it is genuinely chasing you.
        double speed = DRIFT_SPEED * (1.0 + 0.18 * (stage - 1));
        Vec3 step = toPlayer.normalize().scale(speed);
        Vec3 wanted = new Vec3(core.getX() + step.x, core.getY() + step.y, core.getZ() + step.z);
        core.teleportTo(wanted.x, surface(wanted), wanted.z);
    }

    /**
     * Lifts a position clear of anything solid.
     * <p>
     * It travels in a straight line toward whoever it is chasing, which walks it
     * into hillsides, and an entity inside a block suffocates. The first build of
     * this fight quietly killed its own boss that way — 1024 health down to 150
     * without a shot fired.
     */
    private double surface(Vec3 wanted) {
        for (int lift = 0; lift < 24; lift++) {
            BlockPos at = BlockPos.containing(wanted.x, wanted.y + lift, wanted.z);
            if (level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()) {
                return wanted.y + lift;
            }
        }
        return wanted.y;
    }

    /** The blocks orbit the core, faster and wider the angrier it gets. */
    private void spinHalo() {
        Vec3 centre = core.position().add(0, 1.6, 0);
        double spin = age * (0.03 + stage * 0.012);
        double radius = radius();
        for (int i = 0; i < halo.size(); i++) {
            ArmorStand stand = halo.get(i);
            if (!stand.isAlive()) {
                continue;
            }
            double angle = spin + (Math.PI * 2 * i / halo.size());
            double ring = radius + (i % 4) * 0.8;
            double lift = Math.sin(spin * 1.7 + i) * (1.2 + stage * 0.25) + (i % 5) * 0.5;
            stand.teleportTo(centre.x + Math.cos(angle) * ring,
                    centre.y + lift - 1.4,
                    centre.z + Math.sin(angle) * ring);
        }
    }

    // ----------------------------------------------------------------- stages

    private void updateStage(float fraction) {
        // Eight even bands, stage 1 at full health down to stage 8 at the end.
        int next = Math.min(STAGES, Math.max(1, STAGES - (int) Math.floor(fraction * STAGES)));
        if (next <= stage) {
            return;
        }
        stage = next;

        growTo(HALO_START + HALO_PER_STAGE * (stage - 1));
        bar.setName(title(stage));
        if (stage >= 4) {
            bar.setCreateWorldFog(true);
        }
        if (stage >= 6) {
            bar.setColor(BossEvent.BossBarColor.RED);
        }

        level.playSound(null, core.blockPosition(), SoundEvents.WITHER_HURT,
                SoundSource.HOSTILE, 4.0f, Math.max(0.4f, 1.0f - stage * 0.07f));
        level.playSound(null, core.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.HOSTILE, 3.0f, 0.5f);
        broadcast(STAGE_LINES[Math.min(STAGE_LINES.length - 1, stage - 1)]);
    }

    /** What it says as it grows. One per stage. */
    private static final String[] STAGE_LINES = {
            "I have finished counting.",
            "You have been walking for a long time. I know exactly how far.",
            "You took these from the world. I am taking them back.",
            "I know what is in your hands right now.",
            "You did not think anyone was writing it down.",
            "Every block you have ever broken is somewhere on me.",
            "There is nothing about you that I did not write down.",
            "The record is the world now.",
    };

    // ---------------------------------------------------------------- attacks

    /**
     * One dispatcher, so every stage's abilities keep running once unlocked and
     * the whole thing gets denser rather than merely different.
     */
    private void attack() {
        if (age % TAUNT_INTERVAL == 0) {
            taunt();
        }
        // Stage 1+: the pull-and-hurt it has always had, faster each stage.
        if (age % Math.max(14, 80 - stage * 8) == 0) {
            strike();
        }
        // Stage 2+: it reaches, and you go where it wants.
        if (stage >= 2 && age % Math.max(30, 90 - stage * 7) == 0) {
            tractor();
        }
        // Stage 3+: it stops asking and starts eating the world.
        if (stage >= 3 && age % Math.max(2, 9 - stage) == 0) {
            devour();
        }
        // Stage 4+: the rot.
        if (stage >= 4 && age % 100 == 0) {
            wither();
        }
        // Stage 5+: it turns the lights off.
        if (stage >= 5 && age % 140 == 0) {
            blind();
        }
        // Stage 6+: it throws back what it took.
        if (stage >= 6 && age % Math.max(40, 90 - stage * 6) == 0) {
            volley();
        }
        // Stage 7+: off your feet.
        if (stage >= 7 && age % 110 == 0) {
            lift();
        }
        // Stage 8: the ground itself.
        if (stage >= 8 && age % 30 == 0) {
            collapse();
        }
    }

    /**
     * It reads your inventory back to you. This is the point of the fight: it
     * does not threaten, it recites.
     * <p>
     * Quietly, though. An earlier version said "I wrote that down" after every
     * observation, every three seconds, which filled the chat with one repeated
     * string and announced the whole conceit rather than letting it land. It
     * speaks four times less often now and never says the same thing twice in a
     * row, because a thing that notices what you are holding is unsettling and a
     * thing that keeps telling you it noticed is not.
     */
    private void taunt() {
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return;
        }
        int turn = age / TAUNT_INTERVAL;
        ServerPlayer target = players.get(turn % players.size());
        ItemStack held = target.getMainHandItem();
        String name = target.getGameProfile().name();

        String[] lines = held.isEmpty() ? EMPTY_HANDED : HOLDING;
        String line = lines[turn % lines.length];
        broadcast(line.replace("{name}", name)
                .replace("{item}", held.isEmpty() ? "" : held.getHoverName().getString()));
    }

    /** Ticks between remarks. Long enough that each one is noticed. */
    private static final int TAUNT_INTERVAL = 240;

    private static final String[] HOLDING = {
            "The {item}. Yes.",
            "{name} is holding the {item}.",
            "You have had the {item} out for a while now.",
            "The {item} again.",
            "{name}. The {item}. I know where every part of it came from.",
            "You built the {item} out of things you took.",
    };

    private static final String[] EMPTY_HANDED = {
            "{name}, your hands are empty.",
            "Nothing in your hands. That is new.",
            "{name} has stopped holding things.",
    };

    /** Drags everyone toward it and hurts them. Escalates with the stage. */
    private void strike() {
        Vec3 centre = core.position();
        for (ServerPlayer player : inReach()) {
            Vec3 pull = centre.subtract(player.position()).normalize().scale(0.3 + stage * 0.08);
            player.push(pull.x, 0.22, pull.z);
            player.hurtServer(level, level.damageSources().magic(), 1.5f + stage * 1.1f);
        }
        level.playSound(null, core.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.HOSTILE, 2.0f, 0.8f);
    }

    /**
     * The tractor beam. Not damage — displacement. It hauls you off whatever you
     * were standing behind and into the open, which is the whole point.
     */
    private void tractor() {
        Vec3 centre = core.position();
        for (ServerPlayer player : inReach()) {
            Vec3 pull = centre.subtract(player.position()).normalize().scale(0.9 + stage * 0.12);
            player.push(pull.x, 0.45, pull.z);
            player.hurtMarked = true;
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1, false, false));
            beam(player);
        }
        level.playSound(null, core.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.HOSTILE, 3.0f, 0.5f);
    }

    /** A line of pull particles from a victim to the core, so the beam is visible. */
    private void beam(ServerPlayer player) {
        Vec3 from = player.getEyePosition();
        Vec3 to = core.position().add(0, 1.6, 0);
        int steps = (int) Math.min(48, from.distanceTo(to) * 2);
        Vec3 step = to.subtract(from).scale(1.0 / Math.max(1, steps));
        Vec3 cursor = from;
        for (int i = 0; i < steps; i++) {
            cursor = cursor.add(step);
            level.sendParticles(ParticleTypes.PORTAL, cursor.x, cursor.y, cursor.z, 1, 0, 0, 0, 0);
        }
    }

    /**
     * It tears a block out of the world and welds it on.
     * <p>
     * This is the stage-three turn and the reason the fight escalates: the body
     * is made of what it has taken, so every block it eats is one more block of
     * boss. It will not touch anything unbreakable.
     */
    private void devour() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        BlockPos centre = core.blockPosition();
        int span = (int) (radius() + 6);

        for (int attempt = 0; attempt < 12; attempt++) {
            BlockPos target = centre.offset(
                    random.nextInt(-span, span + 1),
                    random.nextInt(-span, span + 1),
                    random.nextInt(-span, span + 1));
            if (target.getY() <= level.getMinY() + 1) {
                continue;
            }
            BlockState state = level.getBlockState(target);
            if (state.isAir() || !level.getFluidState(target).isEmpty()) {
                continue;
            }
            // Bedrock and friends report a negative hardness. It is not that hungry.
            if (state.getDestroySpeed(level, target) < 0) {
                continue;
            }

            level.sendParticles(ParticleTypes.SMOKE,
                    target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.02);
            level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
            devoured++;

            Item item = state.getBlock().asItem();
            if (item != Items.AIR) {
                blocks.add(new ItemStack(item));
                wear(item);
            }
            level.playSound(null, target, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 1.2f, 0.6f);
            return;
        }
    }

    /** Puts a freshly eaten block onto a halo piece, so the body visibly changes. */
    private void wear(Item item) {
        if (halo.isEmpty()) {
            return;
        }
        ArmorStand stand = halo.get(ThreadLocalRandom.current().nextInt(halo.size()));
        if (stand.isAlive()) {
            stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(item));
        }
    }

    /** Stage 4: the rot. */
    private void wither() {
        for (ServerPlayer player : inReach()) {
            player.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, stage >= 7 ? 1 : 0, false, true));
        }
        level.playSound(null, core.blockPosition(), SoundEvents.WITHER_SHOOT,
                SoundSource.HOSTILE, 2.0f, 0.6f);
    }

    /** Stage 5: it turns the lights off. */
    private void blind() {
        for (ServerPlayer player : inReach()) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false));
        }
        level.playSound(null, core.blockPosition(), SoundEvents.WARDEN_ROAR,
                SoundSource.HOSTILE, 3.0f, 0.5f);
    }

    /**
     * Stage 6: it throws back what it took. Blasts land around each player —
     * hand-rolled, because vanilla explosions here would erase the arena in
     * seconds and the point is the pressure, not the crater.
     */
    private void volley() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (ServerPlayer player : inReach()) {
            for (int shot = 0; shot < 1 + (stage - 5); shot++) {
                Vec3 where = player.position().add(
                        random.nextDouble(-3.5, 3.5), 0.5, random.nextDouble(-3.5, 3.5));
                level.sendParticles(ParticleTypes.EXPLOSION, where.x, where.y, where.z, 2, 0.6, 0.6, 0.6, 0.0);
                level.playSound(null, BlockPos.containing(where), SoundEvents.GENERIC_EXPLODE.value(),
                        SoundSource.HOSTILE, 1.4f, 1.1f);
                if (player.position().distanceTo(where) < 3.2) {
                    player.hurtServer(level, level.damageSources().explosion(core, core), 3.0f + stage * 0.6f);
                    player.push(0, 0.35, 0);
                }
            }
        }
    }

    /** Stage 7: off your feet, so the fall does the rest. */
    private void lift() {
        for (ServerPlayer player : inReach()) {
            player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 45, 2, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0, false, false));
        }
        level.playSound(null, core.blockPosition(), SoundEvents.BEACON_POWER_SELECT,
                SoundSource.HOSTILE, 3.0f, 0.4f);
    }

    /**
     * Stage 8: it eats faster than you can run. Several blocks a tick, biased
     * toward the ground people are standing on.
     */
    private void collapse() {
        for (int i = 0; i < 6; i++) {
            devour();
        }
        for (ServerPlayer player : inReach()) {
            BlockPos under = player.blockPosition().below();
            BlockState state = level.getBlockState(under);
            if (!state.isAir() && state.getDestroySpeed(level, under) >= 0) {
                level.setBlockAndUpdate(under, Blocks.AIR.defaultBlockState());
                devoured++;
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private ServerPlayer nearest() {
        ServerPlayer nearest = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            double distance = player.distanceToSqr(core);
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    /** Everyone close enough to be worth doing something to. */
    private List<ServerPlayer> inReach() {
        List<ServerPlayer> found = new ArrayList<>();
        Vec3 centre = core.position();
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && player.position().distanceTo(centre) <= REACH) {
                found.add(player);
            }
        }
        return found;
    }

    // ------------------------------------------------------------------ death

    private void die() {
        finished = true;
        bar.removeAllPlayers();
        for (ArmorStand stand : halo) {
            if (stand.isAlive()) {
                stand.discard();
            }
        }
        halo.clear();

        broadcast("The record is closed.");
        if (devoured > 0) {
            broadcast("It took " + devoured + " blocks with it.");
        }
        level.playSound(null, core.blockPosition(), SoundEvents.WITHER_DEATH,
                SoundSource.HOSTILE, 4.0f, 0.7f);

        // And then, once everyone has relaxed, the cow.
        Entity cow = TheCow.arrive(level, core.blockPosition().above());
        if (cow != null) {
            TheCow.scheduleTurn(level, cow, TheCow.PATIENCE);
        }
    }

    public void despawn() {
        finished = true;
        bar.removeAllPlayers();
        halo.forEach(Entity::discard);
        halo.clear();
        core.discard();
    }

    private void broadcast(String message) {
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.DARK_PURPLE));
        }
    }
}
