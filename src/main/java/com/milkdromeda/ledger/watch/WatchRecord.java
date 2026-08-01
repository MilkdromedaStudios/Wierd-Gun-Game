package com.milkdromeda.ledger.watch;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything Earth knows about one player.
 * <p>
 * This is the spine of the mod. Every counter here is eventually read back to
 * the player — first as statistics in a book they did not write, and later, out
 * loud, by something that assembled itself out of the blocks they mined.
 * <p>
 * Blocks and items are keyed by their registry id as a string rather than by a
 * game object, so a record stays readable and loadable even if a block stops
 * existing in a later version.
 */
public final class WatchRecord {

    private String name = "";

    private final Map<String, Integer> mined = new HashMap<>();
    private final Map<String, Integer> placed = new HashMap<>();
    private final Map<String, Integer> used = new HashMap<>();

    private long blocksWalked;
    private long mobsKilled;
    private long deaths;
    private long booksReceived;
    private long lastBookAt;

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    // ------------------------------------------------------------- recording

    public void recordMined(String blockId) {
        mined.merge(blockId, 1, Integer::sum);
    }

    public void recordPlaced(String blockId) {
        placed.merge(blockId, 1, Integer::sum);
    }

    public void recordUsed(String itemId) {
        used.merge(itemId, 1, Integer::sum);
    }

    public void addWalked(long blocks) {
        blocksWalked += blocks;
    }

    public void recordMobKill() {
        mobsKilled++;
    }

    public void recordDeath() {
        deaths++;
    }

    public void recordBook(long atInteractions) {
        booksReceived++;
        lastBookAt = atInteractions;
    }

    // -------------------------------------------------------------- readouts

    public long totalMined() {
        return sum(mined);
    }

    public long totalPlaced() {
        return sum(placed);
    }

    public long totalUsed() {
        return sum(used);
    }

    private static long sum(Map<String, Integer> counts) {
        long total = 0;
        for (int value : counts.values()) {
            total += value;
        }
        return total;
    }

    /**
     * The single number the whole escalation runs on: everything the player has
     * done to the world, plus every block they have walked across.
     */
    public long totalInteractions() {
        return totalMined() + totalPlaced() + totalUsed() + blocksWalked;
    }

    /** What this player has taken the most of, most first. */
    public List<Map.Entry<String, Integer>> topMined(int limit) {
        return top(mined, limit);
    }

    public List<Map.Entry<String, Integer>> topPlaced(int limit) {
        return top(placed, limit);
    }

    public List<Map.Entry<String, Integer>> topUsed(int limit) {
        return top(used, limit);
    }

    private static List<Map.Entry<String, Integer>> top(Map<String, Integer> counts, int limit) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .toList();
    }

    public int minedCount(String blockId) {
        return mined.getOrDefault(blockId, 0);
    }

    public long blocksWalked() { return blocksWalked; }
    public long mobsKilled() { return mobsKilled; }
    public long deaths() { return deaths; }
    public long booksReceived() { return booksReceived; }
    public long lastBookAt() { return lastBookAt; }

    Map<String, Integer> minedRaw() { return mined; }
    Map<String, Integer> placedRaw() { return placed; }
    Map<String, Integer> usedRaw() { return used; }

    void restore(long walked, long mobs, long deaths, long books, long lastBook) {
        this.blocksWalked = walked;
        this.mobsKilled = mobs;
        this.deaths = deaths;
        this.booksReceived = books;
        this.lastBookAt = lastBook;
    }

    /** Ordered snapshot used when rendering the book and the Witness's taunts. */
    public Map<String, Long> summary() {
        Map<String, Long> out = new LinkedHashMap<>();
        out.put("blocks mined", totalMined());
        out.put("blocks placed", totalPlaced());
        out.put("blocks walked", blocksWalked);
        out.put("items used", totalUsed());
        out.put("creatures killed", mobsKilled);
        out.put("times died", deaths);
        return out;
    }
}
