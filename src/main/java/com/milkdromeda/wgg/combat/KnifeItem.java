package com.milkdromeda.wgg.combat;

import com.milkdromeda.wgg.util.Keys;
import com.milkdromeda.wgg.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class KnifeItem {

    private KnifeItem() {
    }

    public static ItemStack build(Knife knife) {
        ItemStack item = new ItemStack(knife.icon());
        ItemMeta meta = item.getItemMeta();

        meta.getPersistentDataContainer().set(Keys.KNIFE_ID, PersistentDataType.STRING, knife.id());
        meta.displayName(Text.mm(knife.rarity().color() + "<bold>" + knife.name() + "</bold>"));
        meta.lore(buildLore(knife));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

        // Damage is applied by the listener so effects can modify it, but attack
        // speed has to be a real attribute for the vanilla swing timer to match.
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                new NamespacedKey("wgg", "knife_speed"),
                knife.attackSpeed() - 4.0,
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.MAINHAND));

        item.setItemMeta(meta);
        return item;
    }

    private static List<Component> buildLore(Knife knife) {
        List<Component> lore = new ArrayList<>();
        lore.add(Text.mm("<dark_gray><italic>" + knife.flavor() + "</italic></dark_gray>"));
        lore.add(Component.empty());
        lore.add(Text.mm("<gray>Damage <white>" + Text.num(knife.damage())));
        lore.add(Text.mm("<gray>Swing speed <white>" + Text.num(knife.attackSpeed()) + "<dark_gray>/s</dark_gray>"));
        if (knife.backstab() > 1.0) {
            lore.add(Text.mm("<gray>Backstab <white>x" + Text.num(knife.backstab())));
        }
        if (knife.knockback() > 0) {
            lore.add(Text.mm("<gray>Knockback <white>+" + Text.num(knife.knockback())));
        }
        if (knife.lifesteal() > 0) {
            lore.add(Text.mm("<gray>Lifesteal <white>" + Math.round(knife.lifesteal() * 100) + "%"));
        }
        if (knife.bossBonus() > 1.0) {
            lore.add(Text.mm("<gray>Vs. Superbox <white>x" + Text.num(knife.bossBonus())));
        }
        if (!knife.effects().isEmpty()) {
            lore.add(Component.empty());
            for (Knife.KnifeEffect effect : knife.effects()) {
                lore.add(Text.mm("<dark_gray> ▸ </dark_gray><#ffc9f0>" + effect.displayName()
                        + " <dark_gray>— " + effect.description() + "</dark_gray>"));
            }
        }
        return lore;
    }

    public static Knife knifeOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer()
                .get(Keys.KNIFE_ID, PersistentDataType.STRING);
        return id == null ? null : Knife.byId(id);
    }

    public static boolean isKnife(ItemStack item) {
        return knifeOf(item) != null;
    }
}
