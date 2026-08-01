package com.milkdromeda.wgg.tournament;

import com.milkdromeda.wgg.WeirdGunGamePlugin;
import com.milkdromeda.wgg.util.Text;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.SlimeSplitEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Runs the one active tournament, and owns the handful of events that only
 * matter while a tournament is going.
 */
public final class TournamentManager implements Listener {

    private final WeirdGunGamePlugin plugin;
    private Tournament active;

    public TournamentManager(WeirdGunGamePlugin plugin) {
        this.plugin = plugin;
    }

    public Tournament active() {
        return active;
    }

    public boolean isRunning() {
        return active != null && active.state() != Tournament.State.FINISHED;
    }

    /**
     * Starts a tournament centred on the caller, auto-entering everyone nearby.
     *
     * @return an error message, or null when the tournament started
     */
    public String start(Player starter, int rounds) {
        if (isRunning()) {
            return "A tournament is already running. Stop it with /wgg tournament stop.";
        }
        Location arena = starter.getLocation().clone();
        active = new Tournament(plugin, arena, rounds);
        active.add(starter);

        int radius = plugin.wggConfig().arenaRadius();
        for (Entity nearby : starter.getNearbyEntities(radius, radius, radius)) {
            if (nearby instanceof Player player && player.getGameMode() != GameMode.SPECTATOR) {
                active.add(player);
            }
        }

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            player.sendMessage(Text.msg("<gradient:#ff8a3d:#ff4fd8><bold>SUPERBOX TOURNAMENT</bold></gradient>"
                    + " <gray>starting — " + rounds + " rounds. <white>/wgg tournament join</white></gray>"));
        }

        active.beginCountdown();
        return null;
    }

    public String join(Player player) {
        if (!isRunning()) {
            return "There is no tournament running right now.";
        }
        if (active.contains(player)) {
            return "You are already in the tournament.";
        }
        if (!active.add(player)) {
            return "You can only join during the countdown or between rounds.";
        }
        return null;
    }

    public String leave(Player player) {
        if (!isRunning() || !active.contains(player)) {
            return "You are not in a tournament.";
        }
        active.remove(player);
        return null;
    }

    public String stop() {
        if (!isRunning()) {
            return "There is no tournament running right now.";
        }
        active.finish(false);
        active = null;
        return null;
    }

    public void shutdown() {
        if (isRunning()) {
            active.finish(false);
        }
        active = null;
    }

    /**
     * Whether two players are on the same side, which is what the friendly-fire
     * setting keys off. Outside a tournament nobody is teamed, so normal PvP
     * rules apply.
     */
    public boolean sameTeam(Player a, Player b) {
        return isRunning() && active.contains(a) && active.contains(b);
    }

    // ------------------------------------------------------------------ events

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!isRunning()) {
            return;
        }
        Player player = event.getEntity();
        if (!active.contains(player)) {
            return;
        }
        // Keep the kit; the tournament hands out a fresh one next round anyway.
        event.setKeepInventory(true);
        event.getDrops().clear();
        active.onDeath(player);
    }

    /**
     * Downed players sit the round out in spectator. This has to happen on
     * respawn — a gamemode set during the death event gets overwritten by the
     * respawn itself — and one tick later, once the respawn location is final.
     */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!isRunning()) {
            return;
        }
        Player player = event.getPlayer();
        if (!active.contains(player) || !active.isDowned(player)) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (isRunning() && active.isDowned(player)) {
                active.sendToSpectate(player);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (isRunning() && active.contains(event.getPlayer())) {
            active.remove(event.getPlayer());
        }
    }

    /** The Superbox is one box. It does not get to become four smaller boxes. */
    @EventHandler
    public void onSlimeSplit(SlimeSplitEvent event) {
        if (SuperboxBoss.isBossEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /** Boss and minion corpses should not litter the arena with magma cream. */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        String tag = event.getEntity().getPersistentDataContainer()
                .get(com.milkdromeda.wgg.util.Keys.BOSS_TAG, org.bukkit.persistence.PersistentDataType.STRING);
        if (tag == null) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(SuperboxBoss.BOSS_TAG_VALUE.equals(tag) ? 60 : 8);
    }
}
