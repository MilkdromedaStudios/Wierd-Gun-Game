package com.milkdromeda.wgg;

import com.milkdromeda.wgg.combat.CombatListener;
import com.milkdromeda.wgg.combat.GunController;
import com.milkdromeda.wgg.combat.ShotEngine;
import com.milkdromeda.wgg.command.WggCommand;
import com.milkdromeda.wgg.gun.PartRegistry;
import com.milkdromeda.wgg.menu.BenchStore;
import com.milkdromeda.wgg.menu.MenuListener;
import com.milkdromeda.wgg.tournament.TournamentManager;
import com.milkdromeda.wgg.util.Keys;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class WeirdGunGamePlugin extends JavaPlugin {

    private final WggConfig wggConfig = new WggConfig();

    private ShotEngine shotEngine;
    private GunController gunController;
    private BenchStore benches;
    private TournamentManager tournaments;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        wggConfig.load(getConfig());
        Keys.init(this);

        shotEngine = new ShotEngine(this);
        gunController = new GunController(this, shotEngine);
        benches = new BenchStore();
        tournaments = new TournamentManager(this);

        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        getServer().getPluginManager().registerEvents(tournaments, this);

        PluginCommand command = getCommand("wgg");
        if (command != null) {
            WggCommand handler = new WggCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

        gunController.start();

        getLogger().info("Weird Gun Game enabled with " + PartRegistry.total()
                + " parts across 6 sections. Go be strange.");
    }

    @Override
    public void onDisable() {
        if (tournaments != null) {
            tournaments.shutdown();
        }
        if (gunController != null) {
            gunController.shutdown();
        }
    }

    public void reloadWggConfig() {
        reloadConfig();
        wggConfig.load(getConfig());
    }

    public WggConfig wggConfig() {
        return wggConfig;
    }

    public ShotEngine shotEngine() {
        return shotEngine;
    }

    public GunController gunController() {
        return gunController;
    }

    public BenchStore benches() {
        return benches;
    }

    public TournamentManager tournaments() {
        return tournaments;
    }
}
