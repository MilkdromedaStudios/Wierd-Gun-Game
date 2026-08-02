package com.milkdromeda.ledger.boss;

import com.milkdromeda.ledger.Ledger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Owns the one Witness that can exist at a time, and decides when it wakes.
 * <p>
 * The trigger is the world's total interaction count rather than a per-player
 * one, so on a shared server everybody's mining contributes to the same tally
 * and the thing that eventually stands up belongs to all of them.
 */
public final class WitnessManager {

    private Witness active;
    private boolean awoken;

    /** Checked every tick; cheap because the sum is only walked when nothing is active. */
    public void tick(MinecraftServer server) {
        if (active != null) {
            active.tick();
            if (active.isFinished()) {
                active = null;
            }
            return;
        }
        if (awoken) {
            return;
        }
        if (Ledger.watch().worldTotalInteractions() >= Witness.AWAKENING) {
            ServerPlayer host = server.getPlayerList().getPlayers().stream().findFirst().orElse(null);
            if (host != null) {
                awoken = true;
                summon(host);
            }
        }
    }

    /**
     * Assembles the Witness near a player.
     *
     * @return false if it could not be spawned
     */
    public boolean summon(ServerPlayer near) {
        if (active != null) {
            return false;
        }
        ServerLevel level = near.level();
        BlockPos where = near.blockPosition().above(3);
        active = Witness.assemble(level, where, Ledger.watch().all());
        return active != null;
    }

    public boolean isActive() {
        return active != null;
    }

    /**
     * Where a hit on {@code target} should actually land.
     * <p>
     * Rounds that strike the Witness's halo are moved onto its core, so the
     * boss's hitbox is the whole three-metre wheel of blocks rather than the
     * invisible entity at the middle of it.
     *
     * @return the core when the target is part of the active Witness, otherwise
     *         the target unchanged
     */
    public LivingEntity resolveTarget(LivingEntity target) {
        if (active != null && !active.isFinished() && active.isBody(target)) {
            return active.core();
        }
        return target;
    }

    /** Whether this entity is part of the active Witness, halo included. */
    public boolean isBody(LivingEntity target) {
        return active != null && !active.isFinished() && active.isBody(target);
    }

    public void stop() {
        if (active != null) {
            active.despawn();
            active = null;
        }
    }
}
