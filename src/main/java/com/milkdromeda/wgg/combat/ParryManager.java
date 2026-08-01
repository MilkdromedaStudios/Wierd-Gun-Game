package com.milkdromeda.wgg.combat;

import com.milkdromeda.wgg.util.Text;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The katana parry window.
 * <p>
 * Right-clicking a katana opens a short stance during which incoming rounds are
 * knocked back toward whoever fired them. The window is deliberately short and
 * on a cooldown, and only covers the arc you are actually facing, so parrying is
 * a read rather than a permanent shield.
 * <p>
 * Times are tracked in milliseconds rather than ticks so this has no dependency
 * on the gun tick loop.
 */
public final class ParryManager {

    private static final long ACTIVE_MILLIS = 1_100L;
    private static final long COOLDOWN_MILLIS = 5_000L;

    /** How wide the parry arc is: 1.0 is straight ahead, 0.0 is a full hemisphere. */
    private static final double FRONT_ARC_DOT = 0.25;

    private final Map<UUID, Long> activeUntil = new HashMap<>();
    private final Map<UUID, Long> readyAt = new HashMap<>();

    /**
     * Opens the parry stance.
     *
     * @return false if the katana is still on cooldown
     */
    public boolean start(Player player) {
        long now = System.currentTimeMillis();
        long ready = readyAt.getOrDefault(player.getUniqueId(), 0L);
        if (now < ready) {
            player.sendActionBar(Text.mm("<dark_gray>Katana recovering… "
                    + Text.num((ready - now) / 1000.0) + "s"));
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.6f, 1.4f);
            return false;
        }

        activeUntil.put(player.getUniqueId(), now + ACTIVE_MILLIS);
        readyAt.put(player.getUniqueId(), now + COOLDOWN_MILLIS);

        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 0.8f, 1.6f);
        player.sendActionBar(Text.mm("<#7dd3fc><bold>PARRY</bold></#7dd3fc> <gray>— deflecting</gray>"));
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK,
                player.getEyeLocation().add(player.getLocation().getDirection().multiply(1.2)),
                3, 0.3, 0.3, 0.3, 0);
        return true;
    }

    public boolean isParrying(Player player) {
        Long until = activeUntil.get(player.getUniqueId());
        return until != null && System.currentTimeMillis() < until;
    }

    /**
     * Whether a round travelling in {@code direction} should bounce off this
     * player. Only rounds arriving from the front are parried — getting shot in
     * the back still hurts.
     */
    public boolean deflects(Player defender, Vector direction) {
        if (!isParrying(defender)) {
            return false;
        }
        Vector facing = defender.getEyeLocation().getDirection().normalize();
        Vector incoming = direction.clone().normalize().multiply(-1);
        return facing.dot(incoming) >= FRONT_ARC_DOT;
    }

    public void clear(Player player) {
        activeUntil.remove(player.getUniqueId());
        readyAt.remove(player.getUniqueId());
    }
}
