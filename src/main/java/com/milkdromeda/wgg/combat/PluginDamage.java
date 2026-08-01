package com.milkdromeda.wgg.combat;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/**
 * Applies damage that this plugin has already calculated, and marks it as ours
 * on the way through.
 * <p>
 * This exists because {@code LivingEntity#damage(amount, source)} fires a
 * regular {@link org.bukkit.event.entity.EntityDamageByEntityEvent}, which our
 * own melee handler then listens to. Without a marker, a gunshot looks exactly
 * like a punch from a player holding a gun, and the melee handler happily
 * overwrites the carefully balanced shot damage. Bleed ticks had the same
 * problem in reverse, re-applying full knife damage every second.
 * <p>
 * Bukkit events are dispatched synchronously on the server thread, so a plain
 * depth counter is sufficient and cheaper than tracking entities. It is a
 * counter rather than a boolean because damage can legitimately nest — an
 * explosion that kills something can trigger further effects.
 */
public final class PluginDamage {

    private static int depth;

    private PluginDamage() {
    }

    /** Deals pre-calculated damage, bypassing vanilla invulnerability frames. */
    public static void apply(LivingEntity target, double amount, Entity source) {
        depth++;
        try {
            target.setNoDamageTicks(0);
            target.damage(Math.max(0.1, amount), source);
        } finally {
            depth--;
        }
    }

    /**
     * True while a damage event originates from {@link #apply}. Handlers that
     * would otherwise recalculate damage must bail out when this is set.
     */
    public static boolean inProgress() {
        return depth > 0;
    }
}
