package com.milkdromeda.ledger;

import com.milkdromeda.ledger.boss.TheCow;
import com.milkdromeda.ledger.boss.WitnessManager;
import com.milkdromeda.ledger.command.LedgerCommands;
import com.milkdromeda.ledger.menu.BenchStore;
import com.milkdromeda.ledger.watch.WatchService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ledger.
 * <p>
 * Every block broken, every block placed, every step taken is written down. For
 * a long time nothing comes of it. Then a book turns up in your hotbar that you
 * did not write, signed by someone calling themselves Earth, and it knows how
 * much stone you have taken.
 */
public final class Ledger implements ModInitializer {

    public static final String MOD_ID = "ledger";
    public static final Logger LOGGER = LoggerFactory.getLogger("Ledger");

    private static WatchService watch;
    private static BenchStore benches;
    private static WitnessManager witness;

    public static WatchService watch() {
        return watch;
    }

    public static BenchStore benches() {
        return benches;
    }

    public static WitnessManager witness() {
        return witness;
    }

    @Override
    public void onInitialize() {
        watch = new WatchService();
        watch.register();
        benches = new BenchStore();
        LedgerCommands.register();
        witness = new WitnessManager();
        ServerTickEvents.END_SERVER_TICK.register(TheCow::tick);
        ServerTickEvents.END_SERVER_TICK.register(server -> witness.tick(server));

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            watch.load(server);
            LOGGER.info("Ledger is open. Everything from here is recorded.");
        });
    }
}
