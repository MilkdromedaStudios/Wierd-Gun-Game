package com.milkdromeda.wgg.gun;

public enum Rarity {

    COMMON("Common", "<#b0bec5>"),
    UNCOMMON("Uncommon", "<#4ade80>"),
    RARE("Rare", "<#38bdf8>"),
    EXOTIC("Exotic", "<#fbbf24>"),
    CURSED("Cursed", "<#f43f5e>");

    private final String displayName;
    private final String color;

    Rarity(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String displayName() {
        return displayName;
    }

    public String color() {
        return color;
    }
}
