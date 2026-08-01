package com.milkdromeda.ledger.gun;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Part icons are registry ids now rather than an enum, which means a typo is no
 * longer a compile error — it silently becomes a barrier block in the menu.
 * These checks are the replacement for that lost safety.
 */
class PartIconTest {

    /** Same shape Identifier.tryParse accepts: namespace:path, both lowercase. */
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    @Test
    @DisplayName("every part icon is a well-formed registry id")
    void partIconsAreWellFormed() {
        for (PartSection section : PartSection.values()) {
            for (GunPart part : PartRegistry.of(section)) {
                assertTrue(IDENTIFIER.matcher(part.icon()).matches(),
                        () -> part.id() + " has a malformed icon id: " + part.icon());
            }
            assertTrue(IDENTIFIER.matcher(section.icon()).matches(),
                    () -> section + " has a malformed icon id: " + section.icon());
        }
        for (GunPreset preset : GunPreset.all()) {
            assertTrue(IDENTIFIER.matcher(preset.icon()).matches(),
                    () -> preset.id() + " has a malformed icon id: " + preset.icon());
        }
    }

    @Test
    @DisplayName("part ids are unique across the whole registry")
    void partIdsAreUnique() {
        Set<String> seen = new HashSet<>();
        for (PartSection section : PartSection.values()) {
            for (GunPart part : PartRegistry.of(section)) {
                assertTrue(seen.add(part.id()), () -> "duplicate part id: " + part.id());
                assertEquals(part, PartRegistry.byId(part.id()));
            }
        }
        assertEquals(60, seen.size());
    }

    @Test
    @DisplayName("merging always yields a complete, six-part gun")
    void mergingIsTotal() {
        GunBlueprint blueprint = new GunBlueprint();
        for (PartSection section : PartSection.values()) {
            assertNotNull(blueprint.get(section), () -> section + " should always be filled");
        }
        // A blueprint built from nothing, from junk, or from a partial list is
        // still a legal gun; merging must never produce a hole.
        for (String data : new String[] {"", "not_a_part", "snubnose", "snubnose,tnt_core"}) {
            GunBlueprint parsed = GunBlueprint.deserialize(data);
            for (PartSection section : PartSection.values()) {
                assertNotNull(parsed.get(section), () -> "hole in section " + section + " from '" + data + "'");
            }
            assertTrue(parsed.stats().magSize() >= 1);
            assertEquals(6, parsed.serialize().split(",").length);
        }
    }
}
