package com.milkdromeda.wgg.combat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnifeTest {

    /** Roughly a netherite sword swinging at vanilla speed. Nothing should beat it outright. */
    private static final double MELEE_DPS_CEILING = 18.0;

    @Test
    @DisplayName("the rack holds ten distinct, resolvable knives")
    void knivesAreDistinct() {
        assertEquals(10, Knife.all().size());

        Set<String> ids = new HashSet<>();
        for (Knife knife : Knife.all()) {
            assertTrue(ids.add(knife.id()), () -> "duplicate knife id: " + knife.id());
            assertNotNull(Knife.byId(knife.id()));
        }
    }

    @Test
    @DisplayName("no knife out-damages a gun build over time")
    void knivesStayInTheirLane() {
        for (Knife knife : Knife.all()) {
            assertTrue(knife.damage() > 0, () -> knife.id() + " should do damage");
            assertTrue(knife.attackSpeed() > 0, () -> knife.id() + " should be swingable");
            assertTrue(knife.backstab() >= 1.0, () -> knife.id() + " should never punish a backstab");
            assertTrue(knife.lifesteal() <= 0.5, () -> knife.id() + " lifesteals too much");

            double dps = knife.damage() * knife.attackSpeed();
            assertTrue(dps <= MELEE_DPS_CEILING,
                    () -> knife.id() + " melee DPS is out of hand: " + dps);
        }
    }

    @Test
    @DisplayName("the boxcutter is the anti-Superbox tool it claims to be")
    void boxcutterCountersTheBoss() {
        Knife boxcutter = Knife.byId("boxcutter");
        assertNotNull(boxcutter);
        assertTrue(boxcutter.bossBonus() > 1.5, "the boxcutter should shine against the Superbox");
        assertTrue(boxcutter.has(Knife.KnifeEffect.TRUE_DAMAGE));
    }
}
