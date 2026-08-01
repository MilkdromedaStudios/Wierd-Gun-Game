package com.milkdromeda.ledger.gun;


import java.util.List;
import java.util.function.Consumer;

/**
 * One component of a gun.
 *
 * @param id      stable identifier stored on the item, never shown to players
 * @param section which of the six slots this fits
 * @param name    display name
 * @param icon    menu icon
 * @param flavor  one-line description
 * @param rarity  purely cosmetic tier
 * @param perks   short bullet points shown under the part in the menu
 * @param apply   mutates the running {@link GunStats} while the gun is assembled
 */
public record GunPart(
        String id,
        PartSection section,
        String name,
        String icon,
        String flavor,
        Rarity rarity,
        List<String> perks,
        Consumer<GunStats> apply
) {

    public void applyTo(GunStats stats) {
        apply.accept(stats);
    }
}
