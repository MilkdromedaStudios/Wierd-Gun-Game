package com.milkdromeda.wgg.menu;

import com.milkdromeda.wgg.WeirdGunGamePlugin;
import com.milkdromeda.wgg.gun.GunBlueprint;
import com.milkdromeda.wgg.gun.GunItem;
import com.milkdromeda.wgg.gun.GunPart;
import com.milkdromeda.wgg.gun.GunStats;
import com.milkdromeda.wgg.gun.GunTrait;
import com.milkdromeda.wgg.gun.PartSection;
import com.milkdromeda.wgg.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * The gun bench. Six section slots down the left, a live preview of what you
 * have built in the middle, and the buttons that turn it into a real gun along
 * the bottom.
 */
public final class WorkbenchMenu extends Menu {

    /** Section slots, in {@link PartSection} order. */
    private static final int[] SECTION_SLOTS = {10, 11, 19, 20, 28, 29};

    private static final int PREVIEW_SLOT = 24;
    private static final int HELP_SLOT = 16;
    private static final int ARMORY_SLOT = 45;
    private static final int RANDOM_SLOT = 47;
    private static final int ASSEMBLE_SLOT = 49;
    private static final int RESET_SLOT = 51;
    private static final int KNIFE_SLOT = 53;

    public WorkbenchMenu(WeirdGunGamePlugin plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected Component title() {
        return Text.mm("<dark_gray>⚙ </dark_gray><gradient:#ff8a3d:#ff4fd8><bold>GUN BENCH</bold></gradient>");
    }

    @Override
    protected int size() {
        return 54;
    }

    private GunBlueprint blueprint() {
        return plugin.benches().get(player);
    }

    @Override
    protected void render() {
        GunBlueprint blueprint = blueprint();
        GunStats stats = blueprint.stats();

        PartSection[] sections = PartSection.values();
        for (int i = 0; i < sections.length; i++) {
            set(SECTION_SLOTS[i], sectionIcon(sections[i], blueprint.get(sections[i])));
        }

        set(PREVIEW_SLOT, previewIcon(blueprint, stats));
        set(HELP_SLOT, helpIcon());

        set(ARMORY_SLOT, icon(Material.CHEST, "<#facc15><bold>Armoury</bold>",
                List.of("<gray>Ready-made guns: RPG, AK, sniper,",
                        "<gray>shotgun, Maxigun and friends.",
                        "",
                        "<dark_gray>Click to browse")));

        set(RANDOM_SLOT, icon(Material.ENDER_PEARL, "<#a78bfa><bold>Randomise</bold>",
                List.of("<gray>Roll all six sections at once.",
                        "<gray>Results not guaranteed to be sensible.",
                        "",
                        "<dark_gray>Click to roll")));

        set(ASSEMBLE_SLOT, glowing(icon(Material.NETHER_STAR,
                "<gradient:#4ade80:#a3e635><bold>ASSEMBLE GUN</bold></gradient>",
                List.of("<gray>Build <white>" + blueprint.displayName() + "</white>",
                        "<gray>and put it in your inventory.",
                        "",
                        "<dark_gray>Click to build"))));

        set(RESET_SLOT, icon(Material.BARRIER, "<#f43f5e><bold>Reset</bold>",
                List.of("<gray>Put every section back to its",
                        "<gray>default part.",
                        "",
                        "<dark_gray>Click to reset")));

        set(KNIFE_SLOT, icon(Material.IRON_SWORD, "<#7dd3fc><bold>Knife Rack</bold>",
                List.of("<gray>Ten melee sidearms for when",
                        "<gray>reloading is not an option.",
                        "",
                        "<dark_gray>Click to browse")));

        fillEmpty(Material.GRAY_STAINED_GLASS_PANE);
    }

    private ItemStack sectionIcon(PartSection section, GunPart part) {
        List<String> lore = new ArrayList<>();
        lore.add("<dark_gray>" + section.blurb());
        lore.add("");
        lore.add("<gray>Fitted: " + part.rarity().color() + "<bold>" + part.name() + "</bold>");
        lore.add("<dark_gray><italic>" + part.flavor() + "</italic>");
        lore.add("");
        for (String perk : part.perks()) {
            lore.add("<dark_gray> ▸ </dark_gray><gray>" + perk);
        }
        lore.add("");
        lore.add("<dark_gray>Click to see all 10 " + section.displayName().toLowerCase() + "s");

        ItemStack item = icon(part.icon(), section.color() + "<bold>" + section.displayName() + "</bold>", lore);
        return glowing(item);
    }

    private ItemStack previewIcon(GunBlueprint blueprint, GunStats stats) {
        ItemStack preview = GunItem.build(blueprint);
        ItemMeta meta = preview.getItemMeta();

        List<Component> lore = new ArrayList<>(GunItem.buildLore(blueprint, stats, stats.magSize()));
        lore.add(Component.empty());
        lore.add(Text.mm("<gray>Power     " + Text.bar(stats.powerScore(), 100, 12, "<#f43f5e>", "<dark_gray>")
                + " <white>" + stats.powerScore()));
        lore.add(Text.mm("<gray>Weirdness " + Text.bar(stats.weirdnessScore(), 100, 12, "<#ff4fd8>", "<dark_gray>")
                + " <white>" + stats.weirdnessScore()));
        if (stats.nerfApplied() > 0.02) {
            lore.add(Component.empty());
            lore.add(Text.mm("<#facc15>⚠ The balancer trimmed this build by "
                    + Math.round(stats.nerfApplied() * 100) + "%."));
            lore.add(Text.mm("<dark_gray>Stacking damage parts hits the sustained-DPS cap."));
        }
        meta.lore(lore);
        preview.setItemMeta(meta);
        return preview;
    }

    private ItemStack helpIcon() {
        return icon(Material.WRITABLE_BOOK, "<#7dd3fc><bold>How this works</bold>", List.of(
                "<gray>Pick one part for each of the six",
                "<gray>sections, then hit <white>Assemble</white>.",
                "",
                "<gray>Parts are deliberately lopsided. Anything",
                "<gray>that gives you a lot takes something away.",
                "",
                "<gray>A sustained-damage cap runs over the",
                "<gray>finished gun, so wild builds stay fun",
                "<gray>instead of ending the round instantly.",
                "",
                "<#ff8a3d><bold>Controls</bold>",
                "<dark_gray> ▸ </dark_gray><gray>Right-click <white>fire</white>",
                "<dark_gray> ▸ </dark_gray><gray>Hold right-click for automatics",
                "<dark_gray> ▸ </dark_gray><gray><white>F</white> reload",
                "<dark_gray> ▸ </dark_gray><gray>Left-click <white>aim down sights</white>"));
    }

    @Override
    public void onClick(int slot, ClickType clickType) {
        PartSection[] sections = PartSection.values();
        for (int i = 0; i < SECTION_SLOTS.length; i++) {
            if (slot == SECTION_SLOTS[i]) {
                click(1.2f);
                new SectionMenu(plugin, player, sections[i]).open();
                return;
            }
        }

        switch (slot) {
            case ASSEMBLE_SLOT -> {
                GunBlueprint blueprint = blueprint();
                give(GunItem.build(blueprint));
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.4f);
                player.sendMessage(Text.msg("<gray>Built <white>" + blueprint.displayName()
                        + "</white>. Go and be strange.</gray>"));
                player.closeInventory();
            }
            case RANDOM_SLOT -> {
                plugin.benches().set(player, GunBlueprint.random());
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.6f);
                refresh();
            }
            case RESET_SLOT -> {
                plugin.benches().set(player, new GunBlueprint());
                click(0.8f);
                refresh();
            }
            case ARMORY_SLOT -> {
                click(1.2f);
                new ArmoryMenu(plugin, player).open();
            }
            case KNIFE_SLOT -> {
                click(1.2f);
                new KnifeMenu(plugin, player).open();
            }
            default -> { }
        }
    }

    /** Small nudge used elsewhere: does this build carry a trait worth shouting about? */
    public static boolean isSpicy(GunStats stats) {
        return stats.has(GunTrait.EXPLOSIVE) || stats.has(GunTrait.VORTEX) || stats.has(GunTrait.LIGHTNING);
    }
}
