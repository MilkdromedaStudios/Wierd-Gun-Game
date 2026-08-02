package com.milkdromeda.ledger.boss;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.List;

/**
 * The cow.
 * <p>
 * It arrives after the Witness dies, in the pause where everyone is reading the
 * loot and deciding they have won. It is an ordinary cow in every respect that
 * the game can measure. It is not hostile. It has no AI goals worth mentioning.
 * <p>
 * Then it kills everybody.
 * <p>
 * The joke only lands if the cow is genuinely unremarkable right up to the
 * moment it is not, so nothing here is telegraphed: no boss bar, no glow, no
 * ominous naming until the last possible tick.
 */
public final class TheCow {

    /** Ticks between the Witness dying and the cow wandering in. */
    public static final int ARRIVAL_DELAY = 100;

    /** Ticks the cow spends being a completely normal cow before it does the thing. */
    public static final int PATIENCE = 140;

    private TheCow() {
    }

    /**
     * Spawns the cow. Harmless, for now.
     * <p>
     * The type is resolved from the registry rather than a static field so this
     * keeps working if the constant is renamed or moved again, which it has been.
     */
    public static Entity arrive(ServerLevel level, BlockPos where) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE
                .getOptional(Identifier.withDefaultNamespace("cow"))
                .orElse(null);
        if (type == null) {
            return null;
        }
        Entity cow = type.spawn(level, where, EntitySpawnReason.EVENT);
        if (cow == null) {
            return null;
        }
        cow.setCustomName(Component.literal("Cow").withStyle(ChatFormatting.WHITE));
        cow.setCustomNameVisible(false);
        cow.setInvulnerable(true);
        if (cow instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
        return cow;
    }

    /**
     * The turn. Everything still standing stops standing.
     *
     * @param victims everyone who thought the fight was over
     */
    public static void turn(ServerLevel level, Entity cow, List<ServerPlayer> victims) {
        BlockPos where = cow.blockPosition();

        cow.setCustomName(Component.literal("Cow").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        cow.setCustomNameVisible(true);
        cow.setGlowingTag(true);

        level.playSound(null, where, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 4.0f, 0.35f);
        level.playSound(null, where, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.0f, 0.4f);

        for (ServerPlayer victim : victims) {
            victim.sendSystemMessage(Component.literal("Moo.")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            // Not damage. There is no fight here and no armour calculation to do.
            victim.hurtServer(level, cowDamage(level), Float.MAX_VALUE);
            victim.setHealth(0.0f);
        }
    }

    private static DamageSource cowDamage(ServerLevel level) {
        return level.damageSources().generic();
    }

    /**
     * Waits, then turns. The delay is the whole joke: long enough that everyone
     * has stopped paying attention to the cow.
     */
    public static void scheduleTurn(ServerLevel level, Entity cow, int delayTicks) {
        PENDING.add(new Pending(cow, level.getServer().getTickCount() + delayTicks));
    }

    private record Pending(Entity cow, int dueTick) { }

    private static final List<Pending> PENDING = new java.util.ArrayList<>();

    /** Driven from the server tick; fires any cow whose patience has run out. */
    public static void tick(net.minecraft.server.MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        int now = server.getTickCount();
        PENDING.removeIf(pending -> {
            if (now < pending.dueTick()) {
                return false;
            }
            if (pending.cow().isAlive() && pending.cow().level() instanceof ServerLevel level) {
                turn(level, pending.cow(), level.players());
            }
            return true;
        });
    }
}
