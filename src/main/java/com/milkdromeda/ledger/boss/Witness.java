package com.milkdromeda.ledger.boss;

import com.milkdromeda.ledger.watch.WatchRecord;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
 */
public final class Witness {

    /** Interactions across the whole world before it wakes up on its own. */
    public static final long AWAKENING = 10_000L;

    private static final int HALO_SIZE = 24;
    private static final double HALO_RADIUS = 3.4;

    private final ServerLevel level;
    private final LivingEntity core;
    private final ServerBossEvent bar;
    private final List<ArmorStand> halo = new ArrayList<>();
    private final List<ItemStack> blocks;
    private final float maxHealth;

    private int stage = 1;
    private int age;
    private boolean finished;

    private Witness(ServerLevel level, LivingEntity core, List<ItemStack> blocks, float maxHealth) {
        this.level = level;
        this.core = core;
        this.blocks = blocks;
        this.maxHealth = maxHealth;
        this.bar = new ServerBossEvent(UUID.randomUUID(),
                Component.literal("THE WITNESS").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD),
                BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
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

        float health = 400.0f + 120.0f * Math.max(0, level.players().size() - 1);
        core.setCustomName(Component.literal("THE WITNESS")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
        core.setCustomNameVisible(true);
        core.setInvisible(true);
        if (core.getAttribute(Attributes.MAX_HEALTH) != null) {
            core.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        }
        core.setHealth(health);
        if (core instanceof Mob mob) {
            mob.setPersistenceRequired();
        }

        Witness witness = new Witness(level, core, blocksFrom(records), health);
        witness.buildHalo();
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

    private void buildHalo() {
        EntityType<?> standType = BuiltInRegistries.ENTITY_TYPE
                .getOptional(Identifier.withDefaultNamespace("armor_stand")).orElse(null);
        if (standType == null) {
            return;
        }
        for (int i = 0; i < HALO_SIZE; i++) {
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

        float fraction = Math.max(0.0f, core.getHealth() / maxHealth);
        bar.setProgress(fraction);
        updateStage(fraction);
        spinHalo();

        if (age % 60 == 0) {
            taunt();
        }
        if (age % Math.max(20, 90 - stage * 20) == 0) {
            strike();
        }
    }

    /** The blocks orbit the core, faster and wider the angrier it gets. */
    private void spinHalo() {
        Vec3 centre = core.position().add(0, 1.6, 0);
        double spin = age * (0.03 + stage * 0.015);
        for (int i = 0; i < halo.size(); i++) {
            ArmorStand stand = halo.get(i);
            if (!stand.isAlive()) {
                continue;
            }
            double angle = spin + (Math.PI * 2 * i / halo.size());
            double ring = HALO_RADIUS + (i % 3) * 0.7;
            double lift = Math.sin(spin * 1.7 + i) * 1.2 + (i % 4) * 0.5;
            stand.teleportTo(centre.x + Math.cos(angle) * ring,
                    centre.y + lift - 1.4,
                    centre.z + Math.sin(angle) * ring);
        }
    }

    private void updateStage(float fraction) {
        int next = fraction <= 0.25f ? 4 : fraction <= 0.5f ? 3 : fraction <= 0.75f ? 2 : 1;
        if (next == stage) {
            return;
        }
        stage = next;
        level.playSound(null, core.blockPosition(), SoundEvents.WITHER_HURT,
                SoundSource.HOSTILE, 3.0f, 0.5f);
        String line = switch (stage) {
            case 2 -> "You have been walking for a long time. I know exactly how far.";
            case 3 -> "I know what is in your hands right now.";
            default -> "There is nothing about you that I did not write down.";
        };
        broadcast(line);
    }

    /**
     * It reads your inventory back to you. This is the point of the fight: it
     * does not threaten, it recites.
     */
    private void taunt() {
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return;
        }
        ServerPlayer target = players.get(age / 60 % players.size());
        ItemStack held = target.getMainHandItem();
        String name = target.getGameProfile().name();
        broadcast(held.isEmpty()
                ? name + ", your hands are empty. That is new."
                : name + ", you are holding " + held.getHoverName().getString() + ". I wrote that down.");
    }

    /** Drags everyone toward it and hurts them. Escalates with the stage. */
    private void strike() {
        Vec3 centre = core.position();
        for (ServerPlayer player : level.players()) {
            double distance = player.position().distanceTo(centre);
            if (distance > 26) {
                continue;
            }
            Vec3 pull = centre.subtract(player.position()).normalize().scale(0.35 + stage * 0.1);
            player.push(pull.x, 0.25, pull.z);
            player.hurtServer(level, level.damageSources().magic(), 2.0f + stage * 1.5f);
        }
        level.playSound(null, core.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.HOSTILE, 2.0f, 0.8f);
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
