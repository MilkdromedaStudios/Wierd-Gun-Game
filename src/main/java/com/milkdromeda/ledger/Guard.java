package com.milkdromeda.ledger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps a mistake in this mod from taking the world down with it.
 * <p>
 * An exception thrown inside a server tick handler is not a caught error in
 * Minecraft — it ends the tick loop, shuts the integrated server down and drops
 * the player back to the world list. From the outside that looks like the Play
 * button being broken: black screen, then the lobby again, with nothing to
 * suggest a mod was involved. Every one of this mod's handlers runs on that
 * thread, so any bad block position, missing entity or null in a hundred lines
 * of boss code is one keystroke away from making a world look unopenable.
 * <p>
 * So every handler is wrapped. A failure is logged with its stack and the tick
 * carries on: the boss might stop doing something, but the world stays open and
 * the log says exactly which part gave up and why. Repeated failures are counted
 * rather than reprinted, because a handler that throws every tick would write
 * twenty stack traces a second and bury the first one.
 */
public final class Guard {

    private static final Logger LOGGER = LoggerFactory.getLogger("Ledger");

    /** How many times a given failure is printed in full before it is only counted. */
    private static final int LOUD = 3;

    private static final Map<String, Integer> SEEN = new ConcurrentHashMap<>();

    private Guard() {
    }

    /**
     * Runs a piece of this mod, swallowing anything it throws.
     *
     * @param what human-readable name of the part, used in the log line
     */
    public static void run(String what, Runnable body) {
        try {
            body.run();
        } catch (Throwable failure) {
            report(what, failure);
        }
    }

    private static void report(String what, Throwable failure) {
        int count = SEEN.merge(what, 1, Integer::sum);
        if (count > LOUD) {
            return;
        }
        LOGGER.error("Ledger: {} threw, and was ignored so the world stays open.", what, failure);
        if (count == LOUD) {
            LOGGER.error("Ledger: further failures in {} will be counted, not printed.", what);
        }
    }

    /** How many times a named part has failed. Exposed for {@code /ledger record}. */
    public static int failures(String what) {
        return SEEN.getOrDefault(what, 0);
    }

    /** Every part that has failed at least once, and how often. */
    public static Map<String, Integer> failures() {
        return Map.copyOf(SEEN);
    }
}
