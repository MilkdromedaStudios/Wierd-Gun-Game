package com.milkdromeda.wgg.gun;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The balance pass is the only thing standing between "creative build" and
 * "one player deletes the server", so it is checked exhaustively rather than
 * by sampling: all 10^6 legal guns are assembled and measured.
 * <p>
 * This is fast because assembling a blueprint is six lambdas and some
 * arithmetic — no Bukkit objects are touched.
 */
class GunBalanceTest {

    /** Floating-point slack for the cap comparisons. */
    private static final double EPSILON = 1e-6;

    @Test
    @DisplayName("every section offers exactly ten parts")
    void sectionsAreComplete() {
        for (PartSection section : PartSection.values()) {
            assertEquals(10, PartRegistry.of(section).size(),
                    () -> section + " should offer ten parts");
        }
        assertEquals(60, PartRegistry.total());
    }

    @Test
    @DisplayName("part ids are unique and resolvable")
    void partIdsResolve() {
        for (PartSection section : PartSection.values()) {
            for (GunPart part : PartRegistry.of(section)) {
                assertEquals(part, PartRegistry.byId(part.id()),
                        () -> "id " + part.id() + " should resolve back to itself");
                assertEquals(section, part.section());
            }
        }
    }

    @Test
    @DisplayName("every preset is built from real parts and stays inside the caps")
    void presetsAreValid() {
        for (GunPreset preset : GunPreset.all()) {
            assertEquals(6, preset.parts().length,
                    () -> preset.id() + " should name one part per section");
            for (String id : preset.parts()) {
                assertNotNull(PartRegistry.byId(id),
                        () -> preset.id() + " references unknown part " + id);
            }
            GunBlueprint blueprint = preset.toBlueprint();
            assertWithinCaps(blueprint.stats(), preset.id());
            assertTrue(blueprint.stats().magSize() >= 1,
                    () -> preset.id() + " should hold at least one round");
        }
    }

    @Test
    @DisplayName("the presets the game promises actually behave like their namesakes")
    void signaturePresetsBehaveAsAdvertised() {
        GunStats shotgun = GunPreset.byId("shotgun").toBlueprint().stats();
        assertTrue(shotgun.pellets() >= 7, "the shotgun should fire a spread of pellets");

        GunStats rpg = GunPreset.byId("rpg").toBlueprint().stats();
        assertTrue(rpg.explosionPower() > 2.0, "the RPG should explode properly");
        assertTrue(rpg.has(GunTrait.EXPLOSIVE));
        assertFalse(rpg.isHitscan(), "rockets should travel rather than hit instantly");
        assertTrue(rpg.magSize() >= 3, "the RPG should carry a rack of rockets, not one");

        GunStats maxigun = GunPreset.byId("maxigun").toBlueprint().stats();
        assertEquals(FireMode.AUTO, maxigun.fireMode());
        assertTrue(maxigun.magSize() >= 100, "the Maxigun should hold a belt of ammo");

        GunStats sniper = GunPreset.byId("sniper").toBlueprint().stats();
        assertTrue(sniper.range() >= 100, "the sniper should reach across the map");
        assertTrue(sniper.zoom() >= 2, "the sniper should have a real scope");

        GunStats ak = GunPreset.byId("ak").toBlueprint().stats();
        assertEquals(FireMode.AUTO, ak.fireMode(), "the AK should be full auto");

        GunStats boomRifle = GunPreset.byId("boomrifle").toBlueprint().stats();
        assertTrue(boomRifle.has(GunTrait.EXPLOSIVE), "explosive bullets should explode");
    }

    @Test
    @DisplayName("all 1,000,000 possible guns respect every balance cap")
    void everyPossibleGunIsBalanced() {
        List<List<GunPart>> sections = new ArrayList<>();
        for (PartSection section : PartSection.values()) {
            sections.add(PartRegistry.of(section));
        }

        long checked = 0;
        double highestDps = 0.0;

        for (GunPart barrel : sections.get(0)) {
            for (GunPart core : sections.get(1)) {
                for (GunPart grip : sections.get(2)) {
                    for (GunPart magazine : sections.get(3)) {
                        for (GunPart sight : sections.get(4)) {
                            for (GunPart stock : sections.get(5)) {
                                GunBlueprint blueprint = new GunBlueprint();
                                blueprint.set(barrel);
                                blueprint.set(core);
                                blueprint.set(grip);
                                blueprint.set(magazine);
                                blueprint.set(sight);
                                blueprint.set(stock);

                                GunStats stats = blueprint.stats();
                                String id = barrel.id() + "/" + core.id() + "/" + grip.id()
                                        + "/" + magazine.id() + "/" + sight.id() + "/" + stock.id();

                                assertWithinCaps(stats, id);
                                assertEquals(blueprint.serialize(),
                                        GunBlueprint.deserialize(blueprint.serialize()).serialize(),
                                        () -> "blueprint should survive a serialize round trip: " + id);
                                assertFalse(blueprint.displayName().isBlank(),
                                        () -> "every gun should get a name: " + id);

                                highestDps = Math.max(highestDps, stats.sustainedDps());
                                checked++;
                            }
                        }
                    }
                }
            }
        }

        assertEquals(1_000_000L, checked, "every combination should have been measured");
        // The cap should actually bite somewhere, otherwise it is not doing anything.
        assertTrue(highestDps > GunStats.MAX_DPS - 0.5,
                "some build should push right up against the DPS cap");
    }

    private static void assertWithinCaps(GunStats stats, String id) {
        double dps = stats.sustainedDps();
        assertTrue(Double.isFinite(dps), () -> id + " produced a non-finite DPS");
        assertTrue(dps <= GunStats.MAX_DPS + EPSILON,
                () -> id + " exceeds the DPS cap: " + dps);
        assertTrue(stats.damage() > 0, () -> id + " has non-positive damage");
        assertTrue(stats.damage() <= GunStats.MAX_PELLET_DAMAGE + EPSILON,
                () -> id + " exceeds per-pellet damage: " + stats.damage());
        assertTrue(stats.damage() * stats.pellets() <= GunStats.MAX_BURST_DAMAGE + EPSILON,
                () -> id + " exceeds burst damage");
        assertTrue(stats.explosionPower() <= GunStats.MAX_EXPLOSION + EPSILON,
                () -> id + " exceeds explosion power");
        assertTrue(stats.magSize() >= 1 && stats.magSize() <= GunStats.MAX_MAG,
                () -> id + " has an impossible magazine: " + stats.magSize());
        assertTrue(stats.fireRateTicks() >= GunStats.MIN_FIRE_RATE_TICKS,
                () -> id + " cycles faster than the tick floor");
        assertTrue(stats.range() >= 6 && stats.range() <= GunStats.MAX_RANGE + EPSILON,
                () -> id + " has an out-of-bounds range: " + stats.range());
        assertTrue(stats.reloadTicks() >= 8 && stats.reloadTicks() <= 300,
                () -> id + " has an out-of-bounds reload: " + stats.reloadTicks());
        assertTrue(stats.pierceCount() <= GunStats.MAX_PIERCE, () -> id + " pierces too much");
        assertTrue(stats.lifesteal() <= GunStats.MAX_LIFESTEAL + EPSILON, () -> id + " lifesteals too much");
        assertTrue(stats.critChance() <= GunStats.MAX_CRIT + EPSILON, () -> id + " crits too much");
    }
}
