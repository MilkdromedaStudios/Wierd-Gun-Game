package com.milkdromeda.ledger.menu;

import com.milkdromeda.ledger.gun.GunBlueprint;
import com.milkdromeda.ledger.gun.GunItem;
import com.milkdromeda.ledger.item.LedgerItems;
import com.milkdromeda.ledger.gun.GunPart;
import com.milkdromeda.ledger.gun.GunPreset;
import com.milkdromeda.ledger.gun.GunStats;
import com.milkdromeda.ledger.gun.PartRegistry;
import com.milkdromeda.ledger.gun.PartSection;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/**
 * The gun bench: pick a part for each of the six sections, watch the preview
 * update, then merge it all into one gun.
 * <p>
 * This is a plain six-row chest menu with the slots treated as buttons. Clicks
 * are intercepted in {@link #clicked} and never reach the underlying container,
 * so nothing in here can be picked up, dragged out or duplicated.
 */
public final class GunBenchMenu extends ChestMenu {

    /** Section buttons, in {@link PartSection} order. */
    private static final int[] SECTION_SLOTS = {10, 11, 19, 20, 28, 29};
    /** Where a section's ten parts are laid out once you open one. */
    private static final int[] PART_SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};
    private static final int[] PRESET_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23};

    private static final int PREVIEW_SLOT = 24;
    private static final int PRESETS_SLOT = 45;
    private static final int RANDOM_SLOT = 47;
    private static final int MERGE_SLOT = 49;
    private static final int RESET_SLOT = 51;
    private static final int BACK_SLOT = 53;

    /** Which screen the bench is currently showing. */
    private enum View { BENCH, SECTION, PRESETS }

    private final Container container;
    private final Player player;
    private final ServerPlayer serverPlayer;
    private final GunBlueprint blueprint;

    private View view = View.BENCH;
    private PartSection openSection;

    public GunBenchMenu(int syncId, Inventory inventory, GunBlueprint blueprint) {
        this(syncId, inventory, new SimpleContainer(54), blueprint);
    }

    private GunBenchMenu(int syncId, Inventory inventory, Container container, GunBlueprint blueprint) {
        super(MenuType.GENERIC_9x6, syncId, inventory, container, 6);
        this.container = container;
        this.player = inventory.player;
        this.serverPlayer = inventory.player instanceof ServerPlayer sp ? sp : null;
        this.blueprint = blueprint;
        render();
    }

    @Override
    public boolean stillValid(Player who) {
        return true;
    }

    /** Nothing in this menu is a real item, so every click is a button press. */
    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player who) {
        if (slotId < 0 || slotId >= container.getContainerSize()) {
            // Clicks in the player's own inventory are ignored entirely rather
            // than passed through, so a shift-click cannot pull buttons out.
            return;
        }
        onButton(slotId, button == 1 || input == ContainerInput.QUICK_MOVE);
    }

    private void onButton(int slot, boolean secondary) {
        switch (view) {
            case BENCH -> onBenchClick(slot);
            case SECTION -> onSectionClick(slot);
            case PRESETS -> onPresetClick(slot, secondary);
        }
    }

    // ----------------------------------------------------------------- bench

    private void onBenchClick(int slot) {
        PartSection[] sections = PartSection.values();
        for (int i = 0; i < SECTION_SLOTS.length; i++) {
            if (slot == SECTION_SLOTS[i]) {
                openSection = sections[i];
                view = View.SECTION;
                click(1.2f);
                render();
                return;
            }
        }
        switch (slot) {
            case MERGE_SLOT -> {
                give(GunItem.merge(blueprint));
                playSound(SoundEvents.ANVIL_USE, 1.4f);
                player.sendSystemMessage(Component.literal("Merged " + blueprint.displayName() + ".")
                        .withStyle(ChatFormatting.GRAY));
                closeMenu();
            }
            case RANDOM_SLOT -> {
                GunBlueprint rolled = GunBlueprint.random();
                for (PartSection section : PartSection.values()) {
                    blueprint.set(rolled.get(section));
                }
                blueprint.setCustomName(null);
                click(1.6f);
                render();
            }
            case RESET_SLOT -> {
                for (PartSection section : PartSection.values()) {
                    blueprint.set(PartRegistry.defaultFor(section));
                }
                blueprint.setCustomName(null);
                click(0.8f);
                render();
            }
            case PRESETS_SLOT -> {
                view = View.PRESETS;
                click(1.2f);
                render();
            }
            default -> { }
        }
    }

    private void onSectionClick(int slot) {
        if (slot == BACK_SLOT) {
            view = View.BENCH;
            click(0.9f);
            render();
            return;
        }
        List<GunPart> parts = PartRegistry.of(openSection);
        for (int i = 0; i < PART_SLOTS.length && i < parts.size(); i++) {
            if (slot == PART_SLOTS[i]) {
                blueprint.set(parts.get(i));
                blueprint.setCustomName(null);
                playSound(SoundEvents.SMITHING_TABLE_USE, 1.3f);
                view = View.BENCH;
                render();
                return;
            }
        }
    }

    private void onPresetClick(int slot, boolean secondary) {
        if (slot == BACK_SLOT) {
            view = View.BENCH;
            click(0.9f);
            render();
            return;
        }
        List<GunPreset> presets = GunPreset.all();
        for (int i = 0; i < PRESET_SLOTS.length && i < presets.size(); i++) {
            if (slot != PRESET_SLOTS[i]) {
                continue;
            }
            GunPreset preset = presets.get(i);
            GunBlueprint loaded = preset.toBlueprint();
            for (PartSection section : PartSection.values()) {
                blueprint.set(loaded.get(section));
            }
            blueprint.setCustomName(preset.name());
            if (secondary) {
                view = View.BENCH;
                render();
            } else {
                give(GunItem.merge(blueprint));
                playSound(SoundEvents.ANVIL_USE, 1.4f);
                closeMenu();
            }
            return;
        }
    }

    // --------------------------------------------------------------- drawing

    private void render() {
        for (int i = 0; i < container.getContainerSize(); i++) {
            container.setItem(i, ItemStack.EMPTY);
        }
        switch (view) {
            case BENCH -> renderBench();
            case SECTION -> renderSection();
            case PRESETS -> renderPresets();
        }
        broadcastChanges();
    }

    private void renderBench() {
        GunStats stats = blueprint.stats();
        PartSection[] sections = PartSection.values();
        for (int i = 0; i < sections.length; i++) {
            GunPart part = blueprint.get(sections[i]);
            put(SECTION_SLOTS[i], "part_" + part.id(), sections[i].displayName() + ": " + part.name(),
                    List.of(part.flavor(), "", "Click to change"));
        }

        ItemStack preview = GunItem.merge(blueprint);
        container.setItem(PREVIEW_SLOT, preview);

        put(PRESETS_SLOT, "icon_presets", "Presets",
                List.of("RPG, AK, sniper, shotgun, Maxigun…", "", "Click to browse"));
        put(RANDOM_SLOT, "icon_random", "Randomise",
                List.of("Roll all six sections at once."));
        put(MERGE_SLOT, "icon_merge", "MERGE INTO GUN",
                List.of("Combine all six parts into", blueprint.displayName(),
                        "", "Power " + stats.powerScore() + " • DPS " + Math.round(stats.sustainedDps())));
        put(RESET_SLOT, "icon_reset", "Reset", List.of("Back to default parts."));
    }

    private void renderSection() {
        List<GunPart> parts = PartRegistry.of(openSection);
        GunPart fitted = blueprint.get(openSection);
        for (int i = 0; i < parts.size() && i < PART_SLOTS.length; i++) {
            GunPart part = parts.get(i);
            List<String> lore = new ArrayList<>();
            lore.add(part.flavor());
            lore.add("");
            lore.addAll(part.perks());
            lore.add("");
            lore.add(part.id().equals(fitted.id()) ? "✔ Fitted" : "Click to fit");
            put(PART_SLOTS[i], "part_" + part.id(), part.name(), lore);
        }
        put(BACK_SLOT, "icon_back", "Back", List.of("Return to the bench"));
    }

    private void renderPresets() {
        List<GunPreset> presets = GunPreset.all();
        for (int i = 0; i < presets.size() && i < PRESET_SLOTS.length; i++) {
            GunPreset preset = presets.get(i);
            GunStats stats = preset.toBlueprint().stats();
            put(PRESET_SLOTS[i], GunItem.modelFor(preset.toBlueprint()), preset.name(), List.of(
                    preset.description(), "",
                    stats.fireMode().displayName() + " • DPS " + Math.round(stats.sustainedDps())
                            + " • Mag " + stats.magSize(),
                    "", "Left-click to take", "Right-click to load onto the bench"));
        }
        put(BACK_SLOT, "icon_back", "Back", List.of("Return to the bench"));
    }

    /** @param variant a generated model name, e.g. {@code part_red_dot} or {@code gun_gatling} */
    private void put(int slot, String variant, String name, List<String> lore) {
        ItemStack stack = variant.startsWith("gun_")
                ? LedgerItems.withModel(LedgerItems.GUN, variant)
                : GunItem.icon(variant);
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal(name).withStyle(ChatFormatting.YELLOW));
        stack.set(DataComponents.LORE, new ItemLore(lore.stream()
                .map(line -> (Component) Component.literal(line).withStyle(ChatFormatting.GRAY))
                .toList()));
        container.setItem(slot, stack);
    }

    private void give(ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private void click(float pitch) {
        playSound(SoundEvents.UI_BUTTON_CLICK.value(), pitch);
    }

    private void playSound(net.minecraft.sounds.SoundEvent sound, float pitch) {
        player.playSound(sound, 0.7f, pitch);
    }

    /** Only a real server player has a screen to close. */
    private void closeMenu() {
        if (serverPlayer != null) {
            serverPlayer.closeContainer();
        }
    }

    /** Buttons are not items, so quick-move must never move anything. */
    @Override
    public ItemStack quickMoveStack(Player who, int index) {
        return ItemStack.EMPTY;
    }
}
