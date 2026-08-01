package com.milkdromeda.ledger.menu;

import com.milkdromeda.ledger.gun.GunBlueprint;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Each player's work-in-progress gun, kept between menu screens and between
 * openings so you can wander off mid-build and come back to it.
 */
public final class BenchStore {

    private final Map<UUID, GunBlueprint> benches = new HashMap<>();

    public GunBlueprint of(ServerPlayer player) {
        return benches.computeIfAbsent(player.getUUID(), id -> new GunBlueprint());
    }

    public void set(ServerPlayer player, GunBlueprint blueprint) {
        benches.put(player.getUUID(), blueprint);
    }

    public void forget(ServerPlayer player) {
        benches.remove(player.getUUID());
    }
}
