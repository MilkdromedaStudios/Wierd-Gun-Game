package com.milkdromeda.wgg.menu;

import com.milkdromeda.wgg.gun.GunBlueprint;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Each player's work-in-progress gun, kept between menu screens so you can pop
 * out to the armoury and come back without losing your build.
 */
public final class BenchStore {

    private final Map<UUID, GunBlueprint> benches = new HashMap<>();

    public GunBlueprint get(Player player) {
        return benches.computeIfAbsent(player.getUniqueId(), id -> new GunBlueprint());
    }

    public void set(Player player, GunBlueprint blueprint) {
        benches.put(player.getUniqueId(), blueprint);
    }

    public void clear(Player player) {
        benches.remove(player.getUniqueId());
    }
}
