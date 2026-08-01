package com.milkdromeda.wgg.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Persistent-data keys used on gun and knife items. Initialised once on enable. */
public final class Keys {

    public static NamespacedKey GUN_PARTS;
    public static NamespacedKey GUN_AMMO;
    public static NamespacedKey GUN_NAME;
    public static NamespacedKey KNIFE_ID;
    public static NamespacedKey BOSS_TAG;

    private Keys() {
    }

    public static void init(Plugin plugin) {
        GUN_PARTS = new NamespacedKey(plugin, "gun_parts");
        GUN_AMMO = new NamespacedKey(plugin, "gun_ammo");
        GUN_NAME = new NamespacedKey(plugin, "gun_name");
        KNIFE_ID = new NamespacedKey(plugin, "knife_id");
        BOSS_TAG = new NamespacedKey(plugin, "boss_tag");
    }
}
