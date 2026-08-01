package com.milkdromeda.ledger.gun;


/**
 * The six slots every gun is built from. Order here is the order the workbench
 * lays them out, left to right.
 */
public enum PartSection {

    BARREL("Barrel", "<#ff8a3d>", "minecraft:iron_ingot",
            "Where the bullet comes out. Sets damage, range and what you actually shoot."),
    CORE("Core", "<#ff4fd8>", "minecraft:nether_star",
            "The soul of the gun. Sets fire mode and the elemental nonsense."),
    GRIP("Grip", "<#7dd3fc>", "minecraft:leather",
            "How you hold it. Sets spread, recoil and handling."),
    MAGAZINE("Magazine", "<#facc15>", "minecraft:hopper",
            "Bullet storage. Sets ammo count and reload speed."),
    SIGHT("Sight", "<#a3e635>", "minecraft:spyglass",
            "Aiming aid. Sets accuracy, zoom and targeting tricks."),
    STOCK("Stock", "<#c084fc>", "minecraft:oak_planks",
            "The bit against your shoulder. Sets stability and utility.");

    private final String displayName;
    private final String color;
    private final String icon;
    private final String blurb;

    PartSection(String displayName, String color, String icon, String blurb) {
        this.displayName = displayName;
        this.color = color;
        this.icon = icon;
        this.blurb = blurb;
    }

    public String displayName() {
        return displayName;
    }

    /** MiniMessage colour tag used everywhere this section is rendered. */
    public String color() {
        return color;
    }

    public String icon() {
        return icon;
    }

    public String blurb() {
        return blurb;
    }

    public String id() {
        return name().toLowerCase();
    }

    public static PartSection byId(String id) {
        for (PartSection section : values()) {
            if (section.id().equalsIgnoreCase(id)) {
                return section;
            }
        }
        return null;
    }
}
