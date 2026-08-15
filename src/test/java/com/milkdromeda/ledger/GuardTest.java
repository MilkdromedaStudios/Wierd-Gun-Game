package com.milkdromeda.ledger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The guard exists so that a bug in this mod costs a feature rather than the
 * world, so the one thing it must never do is rethrow.
 */
class GuardTest {

    @Test
    @DisplayName("a handler that throws does not take the tick with it")
    void swallowsFailures() {
        assertDoesNotThrow(() -> Guard.run("test/exception",
                () -> { throw new IllegalStateException("boom"); }));
    }

    @Test
    @DisplayName("even an Error is caught — the world matters more than the stack")
    void swallowsErrors() {
        assertDoesNotThrow(() -> Guard.run("test/error",
                () -> { throw new StackOverflowError("boom"); }));
    }

    @Test
    @DisplayName("work still runs, and its result is not disturbed")
    void runsTheBody() {
        AtomicInteger counter = new AtomicInteger();
        Guard.run("test/ok", counter::incrementAndGet);
        Guard.run("test/ok", counter::incrementAndGet);
        assertEquals(2, counter.get());
        assertEquals(0, Guard.failures("test/ok"));
    }

    @Test
    @DisplayName("repeat failures are counted, so a handler failing every tick cannot flood the log")
    void countsRepeats() {
        for (int i = 0; i < 50; i++) {
            Guard.run("test/repeat", () -> { throw new RuntimeException("again"); });
        }
        assertEquals(50, Guard.failures("test/repeat"));
        assertTrue(Guard.failures().containsKey("test/repeat"));
    }
}
