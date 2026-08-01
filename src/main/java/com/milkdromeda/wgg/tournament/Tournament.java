package com.milkdromeda.wgg.tournament;

import com.milkdromeda.wgg.WeirdGunGamePlugin;
import com.milkdromeda.wgg.combat.Knife;
import com.milkdromeda.wgg.combat.KnifeItem;
import com.milkdromeda.wgg.gun.GunBlueprint;
import com.milkdromeda.wgg.gun.GunItem;
import com.milkdromeda.wgg.util.Text;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One run of the Superbox tournament: a countdown, then a sequence of rounds
 * against progressively nastier Superboxes with a breather in between.
 * <p>
 * Players who die are put into spectator for the rest of the round and revived
 * at the next intermission. The run ends when the last round's boss dies
 * (victory) or when everybody is down at the same time (defeat).
 */
public final class Tournament {

    public enum State { COUNTDOWN, ROUND, INTERMISSION, FINISHED }

    private final WeirdGunGamePlugin plugin;
    private final Location arena;
    private final int totalRounds;

    private final Set<UUID> participants = new LinkedHashSet<>();
    private final Set<UUID> downed = new LinkedHashSet<>();
    private final Map<UUID, GameMode> originalModes = new HashMap<>();

    private State state = State.COUNTDOWN;
    private int round;
    private SuperboxBoss boss;
    private BukkitTask timerTask;

    public Tournament(WeirdGunGamePlugin plugin, Location arena, int totalRounds) {
        this.plugin = plugin;
        this.arena = arena;
        this.totalRounds = Math.max(1, totalRounds);
    }

    // ------------------------------------------------------------- membership

    public boolean add(Player player) {
        if (state != State.COUNTDOWN && state != State.INTERMISSION) {
            return false;
        }
        if (!participants.add(player.getUniqueId())) {
            return false;
        }
        originalModes.put(player.getUniqueId(), player.getGameMode());
        broadcast("<#4ade80>" + player.getName() + "</#4ade80> <gray>joined the tournament ("
                + participants.size() + " fighters).</gray>");
        return true;
    }

    public void remove(Player player) {
        participants.remove(player.getUniqueId());
        downed.remove(player.getUniqueId());
        restoreMode(player);
    }

    public boolean contains(Player player) {
        return participants.contains(player.getUniqueId());
    }

    public List<Player> participants() {
        List<Player> online = new ArrayList<>();
        for (UUID id : participants) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null && player.isOnline()) {
                online.add(player);
            }
        }
        return online;
    }

    /** Everyone still standing this round. */
    public List<Player> livingParticipants() {
        List<Player> living = new ArrayList<>();
        for (Player player : participants()) {
            if (!downed.contains(player.getUniqueId()) && player.getGameMode() != GameMode.SPECTATOR) {
                living.add(player);
            }
        }
        return living;
    }

    public boolean isDowned(Player player) {
        return downed.contains(player.getUniqueId());
    }

    /**
     * Puts a downed player into spectator at the arena. Called after respawn
     * rather than on death, because a gamemode change during the death event
     * is undone by the respawn that follows it.
     */
    public void sendToSpectate(Player player) {
        if (!isDowned(player)) {
            return;
        }
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(arena.clone().add(0, 3, 0));
        player.sendMessage(Text.msg("<gray>Spectating until the next round.</gray>"));
    }

    public State state() {
        return state;
    }

    public int round() {
        return round;
    }

    public int totalRounds() {
        return totalRounds;
    }

    public Location arena() {
        return arena;
    }

    public SuperboxBoss boss() {
        return boss;
    }

    // ------------------------------------------------------------------ flow

    public void beginCountdown() {
        int seconds = plugin.wggConfig().countdownSeconds();
        broadcast("<gray>The Superbox tournament begins in <white>" + seconds
                + "</white> seconds. <#7dd3fc>/wgg tournament join</#7dd3fc> to enter.</gray>");

        timerTask = new BukkitRunnable() {
            int remaining = seconds;

            @Override
            public void run() {
                if (state != State.COUNTDOWN) {
                    cancel();
                    return;
                }
                if (remaining <= 0) {
                    cancel();
                    if (participants().isEmpty()) {
                        broadcast("<#f43f5e>Nobody entered. Tournament cancelled.");
                        finish(false);
                        return;
                    }
                    beginRound(1);
                    return;
                }
                if (remaining <= 5 || remaining % 5 == 0) {
                    for (Player player : participants()) {
                        player.showTitle(Title.title(
                                Text.mm("<#facc15><bold>" + remaining + "</bold>"),
                                Text.mm("<gray>Get ready</gray>"),
                                Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(200))));
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f,
                                remaining <= 3 ? 1.8f : 1.2f);
                    }
                }
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void beginRound(int number) {
        state = State.ROUND;
        round = number;
        downed.clear();

        for (Player player : participants()) {
            revive(player);
            giveLoadout(player);
            player.showTitle(Title.title(
                    Text.mm("<gradient:#ff8a3d:#ff4fd8><bold>ROUND " + number + "</bold></gradient>"),
                    Text.mm("<gray>of " + totalRounds + "</gray>"),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
        }

        double health = plugin.wggConfig().bossBaseHealth()
                + plugin.wggConfig().bossHealthPerRound() * (number - 1)
                + plugin.wggConfig().bossHealthPerPlayer() * Math.max(0, participants().size() - 1);

        boss = new SuperboxBoss(plugin, this, number, health);
        boss.spawn(arena);
    }

    /** Called by the boss when it dies. */
    public void onBossDefeated(int defeatedRound) {
        if (state != State.ROUND || defeatedRound != round) {
            return;
        }

        for (Player player : participants()) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }

        if (round >= totalRounds) {
            finish(true);
            return;
        }

        state = State.INTERMISSION;
        int seconds = plugin.wggConfig().intermissionSeconds();
        broadcast("<#4ade80><bold>Round " + round + " cleared.</bold></#4ade80> <gray>Next Superbox in "
                + seconds + "s — rebuild at <white>/wgg bench</white>.</gray>");

        for (Player player : participants()) {
            revive(player);
            player.showTitle(Title.title(
                    Text.mm("<#4ade80><bold>ROUND CLEARED</bold>"),
                    Text.mm("<gray>Rebuild your gun — /wgg bench</gray>"),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))));
        }

        timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state == State.INTERMISSION) {
                    beginRound(round + 1);
                }
            }
        }.runTaskLater(plugin, seconds * 20L);
    }

    /** Called from the death listener. */
    public void onDeath(Player player) {
        if (!contains(player) || state != State.ROUND) {
            return;
        }
        downed.add(player.getUniqueId());
        player.sendMessage(Text.msg("<#f43f5e>You are down.</#f43f5e> <gray>Back in at the next round.</gray>"));
        broadcast("<gray>" + player.getName() + " was flattened by the Superbox.</gray>");

        if (livingParticipants().isEmpty()) {
            broadcast("<#f43f5e><bold>Everybody is down. The Superbox wins.</bold>");
            finish(false);
        }
    }

    public void finish(boolean victory) {
        state = State.FINISHED;
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        if (boss != null) {
            boss.despawn();
            boss = null;
        }

        for (Player player : participants()) {
            revive(player);
            restoreMode(player);
            if (victory) {
                player.showTitle(Title.title(
                        Text.mm("<gradient:#4ade80:#a3e635><bold>VICTORY</bold></gradient>"),
                        Text.mm("<gray>You out-weirded the Superbox</gray>"),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofSeconds(1))));
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
                awardPrize(player);
            } else {
                player.showTitle(Title.title(
                        Text.mm("<#f43f5e><bold>DEFEAT</bold>"),
                        Text.mm("<gray>The Superbox remains undefeated</gray>"),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(800))));
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.6f, 0.7f);
            }
        }

        if (victory) {
            broadcast("<gradient:#4ade80:#a3e635><bold>The Superbox has fallen after " + totalRounds
                    + " rounds.</bold></gradient>");
        }
    }

    // --------------------------------------------------------------- utilities

    /**
     * Hands out this round's kit: whatever the player currently has on their
     * bench, plus a knife. Building a better gun between rounds is the point.
     */
    private void giveLoadout(Player player) {
        GunBlueprint blueprint = plugin.benches().get(player);
        ItemStack gun = GunItem.build(blueprint);
        player.getInventory().addItem(gun).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));

        List<Knife> knives = Knife.all();
        Knife knife = knives.get(ThreadLocalRandom.current().nextInt(knives.size()));
        player.getInventory().addItem(KnifeItem.build(knife)).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));

        player.sendMessage(Text.msg("<gray>Loadout: <white>" + blueprint.displayName()
                + "</white> and a <white>" + knife.name() + "</white>.</gray>"));
    }

    private void revive(Player player) {
        double max = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        player.setHealth(max);
        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        downed.remove(player.getUniqueId());
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(originalModes.getOrDefault(player.getUniqueId(), GameMode.SURVIVAL));
            player.teleport(arena.clone().add(0, 1, 0));
        }
    }

    private void restoreMode(Player player) {
        GameMode original = originalModes.remove(player.getUniqueId());
        if (original != null && player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(original);
        }
    }

    private void awardPrize(Player player) {
        GunBlueprint prize = GunBlueprint.random();
        prize.setCustomName("Champion's " + prize.displayName());
        player.getInventory().addItem(GunItem.build(prize)).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        player.giveExpLevels(10);
        player.sendMessage(Text.msg("<gray>Prize: <white>" + prize.displayName() + "</white>.</gray>"));
    }

    private void broadcast(String message) {
        for (Player player : participants()) {
            player.sendMessage(Text.msg(message));
        }
    }
}
