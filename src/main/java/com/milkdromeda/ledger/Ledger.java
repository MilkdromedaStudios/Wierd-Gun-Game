package com.milkdromeda.ledger;

import com.milkdromeda.ledger.watch.WatchService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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

    public static WatchService watch() {
        return watch;
    }

    @Override
    public void onInitialize() {
        watch = new WatchService();
        watch.register();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            watch.load(server);
            LOGGER.info("Ledger is open. Everything from here is recorded.");
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> watch.save());
    }
}
