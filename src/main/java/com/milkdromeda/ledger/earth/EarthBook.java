package com.milkdromeda.ledger.earth;

import com.milkdromeda.ledger.watch.WatchRecord;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The book nobody wrote.
 * <p>
 * Once a player has been recorded enough times, a written book signed
 * <em>Earth</em> turns up in their hotbar. It is not a warning and it does not
 * threaten anything; it simply reads their own statistics back to them, and each
 * successive volume knows a little more and is a little less polite about it.
 * <p>
 * The numbers are real. Everything printed here comes straight out of the
 * player's {@link WatchRecord}, which is what makes it unsettling rather than
 * decorative.
 */
public final class EarthBook {

    /** Interactions between one volume and the next. */
    public static final long INTERVAL = 300L;

    private EarthBook() {
    }

    /** How many volumes this record has earned so far. */
    public static long volumesEarned(WatchRecord record) {
        return record.totalInteractions() / INTERVAL;
    }

    /**
     * Hands over the next volume if one is due.
     *
     * @return true if a book was delivered
     */
    public static boolean considerDelivery(ServerPlayer player, WatchRecord record) {
        long earned = volumesEarned(record);
        if (earned <= record.booksReceived()) {
            return false;
        }
        long volume = record.booksReceived() + 1;
        deliver(player, record, volume);
        record.recordBook(record.totalInteractions());
        return true;
    }

    /** Slips the book into the hotbar, or the player's feet if there is no room. */
    public static void deliver(ServerPlayer player, WatchRecord record, long volume) {
        ItemStack book = write(record, volume);

        int slot = firstFreeHotbarSlot(player);
        if (slot >= 0) {
            player.getInventory().setItem(slot, book);
        } else if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }

        player.level().playSound(null, player.blockPosition(),
                SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.9f, 0.6f);
        player.sendSystemMessage(Component.literal("Something has been added to your inventory.")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static int firstFreeHotbarSlot(ServerPlayer player) {
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getItem(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    // --------------------------------------------------------------- authoring

    public static ItemStack write(WatchRecord record, long volume) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);

        List<Filterable<Component>> pages = new ArrayList<>();
        for (String page : pagesFor(record, volume)) {
            pages.add(Filterable.passThrough(Component.literal(page)));
        }

        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough("The Ledger, Vol. " + volume),
                "Earth",
                0,
                pages,
                true));
        book.set(DataComponents.CUSTOM_NAME,
                Component.literal("The Ledger, Vol. " + volume).withStyle(ChatFormatting.DARK_PURPLE));
        return book;
    }

    /**
     * Escalates with the volume number: the first is almost friendly, the later
     * ones are not. Every figure quoted is taken from the record.
     */
    private static List<String> pagesFor(WatchRecord record, long volume) {
        List<String> pages = new ArrayList<>();
        Map<String, Long> summary = record.summary();

        StringBuilder opening = new StringBuilder();
        if (volume == 1) {
            opening.append("Hello.\n\nI have been keeping a record. I am not sure when I started. "
                    + "It seemed worth doing.\n\nHere is what I have so far.");
        } else if (volume == 2) {
            opening.append("You kept the last one. Good.\n\nI have carried on counting. "
                    + "You have been busy since we spoke.");
        } else if (volume < 5) {
            opening.append("Volume ").append(volume).append(".\n\nYou may have noticed that I am "
                    + "always correct about these numbers.\n\nThat should tell you something "
                    + "about how I get them.");
        } else {
            opening.append("Volume ").append(volume).append(".\n\nI can see you right now.\n\n"
                    + "Not as a figure of speech.");
        }
        pages.add(opening.toString());

        StringBuilder figures = new StringBuilder("THE RECORD\n\n");
        for (Map.Entry<String, Long> entry : summary.entrySet()) {
            figures.append(entry.getValue()).append(" ").append(entry.getKey()).append("\n");
        }
        figures.append("\n").append(record.totalInteractions()).append(" total.");
        pages.add(figures.toString());

        List<Map.Entry<String, Integer>> topMined = record.topMined(5);
        if (!topMined.isEmpty()) {
            StringBuilder taken = new StringBuilder("WHAT YOU TOOK\n\n");
            for (Map.Entry<String, Integer> entry : topMined) {
                taken.append(entry.getValue()).append(" x ").append(pretty(entry.getKey())).append("\n");
            }
            taken.append("\nI am not asking for it back.");
            pages.add(taken.toString());
        }

        if (volume >= 2) {
            pages.add("A QUESTION\n\nHow do you think I am counting?\n\n"
                    + "You have never seen me follow you. You have never seen me at all.\n\n"
                    + "Look up more often.");
        }
        if (volume >= 3) {
            pages.add("They are small.\n\nThey sit in the leaves, and in the dark places "
                    + "underground where you go looking for the good stone.\n\n"
                    + "They do not move much. They only turn.");
        }
        if (volume >= 4) {
            pages.add("You have not broken any of them yet. That is fine. "
                    + "They are not the point.\n\nThe point is the total.\n\n"
                    + "When it is large enough, it stops being a record and starts "
                    + "being a shape.");
        }
        if (volume >= 5) {
            long remaining = Math.max(0, 10_000L - record.totalInteractions());
            pages.add("It is nearly finished.\n\n"
                    + (remaining > 0 ? remaining + " to go." : "It is finished.")
                    + "\n\nI would slow down, but I do not think you will.");
        }

        pages.add("— Earth");
        return pages;
    }

    /** {@code minecraft:deepslate_diamond_ore} reads better as {@code deepslate diamond ore}. */
    private static String pretty(String registryId) {
        int colon = registryId.indexOf(':');
        return (colon >= 0 ? registryId.substring(colon + 1) : registryId).replace('_', ' ');
    }
}
