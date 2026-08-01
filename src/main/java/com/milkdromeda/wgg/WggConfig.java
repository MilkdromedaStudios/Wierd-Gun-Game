package com.milkdromeda.wgg;

import org.bukkit.configuration.file.FileConfiguration;

/** Typed view over config.yml, re-read on {@code /wgg reload}. */
public final class WggConfig {

    private boolean explosionsBreakBlocks;
    private boolean friendlyFire;
    private boolean selfDamageFromExplosions;
    private boolean showTrails;
    private double globalDamageMultiplier;

    private int tournamentRounds;
    private double bossBaseHealth;
    private double bossHealthPerRound;
    private double bossHealthPerPlayer;
    private int minionsPerRound;
    private int arenaRadius;
    private int countdownSeconds;
    private int intermissionSeconds;

    public void load(FileConfiguration config) {
        explosionsBreakBlocks = config.getBoolean("combat.explosions-break-blocks", false);
        friendlyFire = config.getBoolean("combat.friendly-fire", true);
        selfDamageFromExplosions = config.getBoolean("combat.self-damage-from-explosions", true);
        showTrails = config.getBoolean("combat.show-bullet-trails", true);
        globalDamageMultiplier = config.getDouble("combat.global-damage-multiplier", 1.0);

        tournamentRounds = config.getInt("tournament.rounds", 5);
        bossBaseHealth = config.getDouble("tournament.boss-base-health", 220.0);
        bossHealthPerRound = config.getDouble("tournament.boss-health-per-round", 140.0);
        bossHealthPerPlayer = config.getDouble("tournament.boss-health-per-player", 60.0);
        minionsPerRound = config.getInt("tournament.minions-per-round", 3);
        arenaRadius = config.getInt("tournament.arena-radius", 40);
        countdownSeconds = config.getInt("tournament.countdown-seconds", 10);
        intermissionSeconds = config.getInt("tournament.intermission-seconds", 15);
    }

    public boolean explosionsBreakBlocks() { return explosionsBreakBlocks; }
    public boolean friendlyFire() { return friendlyFire; }
    public boolean selfDamageFromExplosions() { return selfDamageFromExplosions; }
    public boolean showTrails() { return showTrails; }
    public double globalDamageMultiplier() { return globalDamageMultiplier; }

    public int tournamentRounds() { return tournamentRounds; }
    public double bossBaseHealth() { return bossBaseHealth; }
    public double bossHealthPerRound() { return bossHealthPerRound; }
    public double bossHealthPerPlayer() { return bossHealthPerPlayer; }
    public int minionsPerRound() { return minionsPerRound; }
    public int arenaRadius() { return arenaRadius; }
    public int countdownSeconds() { return countdownSeconds; }
    public int intermissionSeconds() { return intermissionSeconds; }
}
