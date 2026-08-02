package com.milkdromeda.ledger.item;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

import java.util.List;

/**
 * The three items this mod draws its own art for.
 * <p>
 * Everything used to ride on vanilla stand-ins — a hoe for a gun, a blaze rod
 * for a barrel — which kept the mod installable on the server alone but meant
 * every gun in the game looked like a farming tool. These are real registered
 * items with real textures instead. The cost is honest and worth stating: a
 * client without the mod now sees missing models, so it has to be installed on
 * both sides.
 * <p>
 * There are only three of them because the look is chosen by a
 * {@code custom_model_data} string rather than by the item. One {@link #GUN}
 * covers ten silhouettes, one {@link #ICON} covers sixty-five, and the item
 * definitions in {@code assets/ledger/items/} do the picking. Adding a part
 * means adding a sprite, not a registry entry.
 */
public final class LedgerItems {

    public static final String NAMESPACE = "ledger";

    /** What you hold. Its model follows the barrel the gun was merged from. */
    public static final Item GUN = register("gun", 1);

    /** A button in the gun bench: a part, a preset or a piece of menu furniture. */
    public static final Item ICON = register("icon", 1);

    /** Worn on a camera's head. A model, not a sprite — it hangs in the world. */
    public static final Item CAMERA = register("camera", 1);

    private LedgerItems() {
    }

    private static Item register(String path, int stackSize) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath(NAMESPACE, path));
        return Registry.register(BuiltInRegistries.ITEM, key,
                new Item(new Item.Properties().setId(key).stacksTo(stackSize)));
    }

    /**
     * Class-loads this holder so its static registrations run. Fabric calls the
     * entrypoint before the registries freeze, which is the only window items
     * can be added in.
     */
    public static void bootstrap() {
        // Touching the class is the whole job; the fields do the work.
    }

    /**
     * Points a stack at one of the generated models.
     *
     * @param variant a model name under {@code assets/ledger/models/item/},
     *                such as {@code gun_long_rifled} or {@code part_red_dot}
     */
    public static ItemStack withModel(Item item, String variant) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.of(), List.of(variant), List.of()));
        return stack;
    }
}
