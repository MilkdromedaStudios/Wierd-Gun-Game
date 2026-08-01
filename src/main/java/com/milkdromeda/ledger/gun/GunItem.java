package com.milkdromeda.ledger.gun;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Where six loose parts become one gun.
 * <p>
 * Merging is the whole trick: a barrel, core, grip, magazine, sight and stock go
 * in, their effects are applied in that order, the balance pass runs once over
 * the result, and what comes out is a single item that remembers which parts
 * made it. Only the part ids and the ammo count are stored — every stat is
 * recomputed from the parts on demand, so rebalancing a part in
 * {@link PartRegistry} instantly updates every gun already in the world.
 */
public final class GunItem {

    private static final String TAG_PARTS = "ledger_parts";
    private static final String TAG_AMMO = "ledger_ammo";
    private static final String TAG_NAME = "ledger_name";

    private GunItem() {
    }

    /** The item a gun rides on. Server-side only, so this stands in for a model. */
    private static Item baseItem(GunStats stats) {
        return switch (stats.fireMode()) {
            case AUTO -> Items.DIAMOND_HOE;
            case BURST -> Items.GOLDEN_HOE;
            case CHARGE, SPINUP -> Items.NETHERITE_HOE;
            case SEMI -> Items.IRON_HOE;
        };
    }

    /** Merges a blueprint's six parts into one finished gun. */
    public static ItemStack merge(GunBlueprint blueprint) {
        GunStats stats = blueprint.stats();
        ItemStack stack = new ItemStack(baseItem(stats));

        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_PARTS, blueprint.serialize());
        tag.putInt(TAG_AMMO, stats.magSize());
        if (blueprint.customName() != null && !blueprint.customName().isBlank()) {
            tag.putString(TAG_NAME, blueprint.customName());
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.MAX_STACK_SIZE, 1);

        decorate(stack, blueprint, stats, stats.magSize());
        return stack;
    }

    public static boolean isGun(ItemStack stack) {
        return partsOf(stack).isPresent();
    }

    private static Optional<String> partsOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return Optional.empty();
        }
        return data.copyTag().getString(TAG_PARTS);
    }

    public static GunBlueprint blueprintOf(ItemStack stack) {
        Optional<String> parts = partsOf(stack);
        if (parts.isEmpty()) {
            return null;
        }
        GunBlueprint blueprint = GunBlueprint.deserialize(parts.get());
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            data.copyTag().getString(TAG_NAME).ifPresent(blueprint::setCustomName);
        }
        return blueprint;
    }

    public static int ammoOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return 0;
        }
        return data.copyTag().getInt(TAG_AMMO).orElse(0);
    }

    /** Writes a new round count and refreshes the tooltip. */
    public static void setAmmo(ItemStack stack, int ammo) {
        GunBlueprint blueprint = blueprintOf(stack);
        if (blueprint == null) {
            return;
        }
        GunStats stats = blueprint.stats();
        int clamped = Math.max(0, Math.min(stats.magSize(), ammo));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(TAG_AMMO, clamped));
        decorate(stack, blueprint, stats, clamped);
    }

    // ------------------------------------------------------------------ lore

    private static void decorate(ItemStack stack, GunBlueprint blueprint, GunStats stats, int ammo) {
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal(blueprint.displayName()).withStyle(ChatFormatting.GOLD));
        stack.set(DataComponents.LORE, new ItemLore(buildLore(blueprint, stats, ammo)));
    }

    public static List<Component> buildLore(GunBlueprint blueprint, GunStats stats, int ammo) {
        List<Component> lore = new ArrayList<>();

        lore.add(grey(stats.fireMode().displayName()
                + " • Power " + stats.powerScore()
                + " • Weirdness " + stats.weirdnessScore()));
        lore.add(Component.empty());
        lore.add(grey("Ammo " + ammo + "/" + stats.magSize()));
        lore.add(grey("Damage " + trim(stats.damage())
                + (stats.pellets() > 1 ? " x" + stats.pellets() + " pellets" : "")));
        lore.add(grey("Fire rate " + trim(20.0 / stats.fireRateTicks()) + "/s"
                + "   Reload " + trim(stats.reloadTicks() / 20.0) + "s"));
        lore.add(grey("Range " + trim(stats.range()) + "m   Spread " + trim(stats.spread()) + "°"));
        if (stats.explosionPower() > 0) {
            lore.add(grey("Blast " + trim(stats.explosionPower())));
        }
        lore.add(grey("Sustained DPS " + trim(stats.sustainedDps())));

        if (!stats.traits().isEmpty()) {
            lore.add(Component.empty());
            for (GunTrait trait : stats.traits()) {
                lore.add(Component.literal(" ▸ " + trait.displayName() + " — " + trait.description())
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }

        lore.add(Component.empty());
        for (PartSection section : PartSection.values()) {
            GunPart part = blueprint.get(section);
            lore.add(grey(" " + section.displayName() + ": " + part.name()));
        }
        return lore;
    }

    private static Component grey(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }

    private static String trim(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.05) {
            return String.valueOf((int) Math.rint(value));
        }
        return String.format("%.1f", value);
    }

    /** Resolves a part's icon id to a real item, falling back to a barrier if it is unknown. */
    public static Item iconItem(String registryId) {
        Identifier key = Identifier.tryParse(registryId);
        if (key == null) {
            return Items.BARRIER;
        }
        return BuiltInRegistries.ITEM.getOptional(key).orElse(Items.BARRIER);
    }
}
