package com.milkdromeda.ledger.watch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.milkdromeda.ledger.Ledger;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The front desk for everything that gets written down.
 * <p>
 * Every other system talks to this rather than touching records directly,
 * because recording a thing is never just recording a thing: each entry can push
 * a player over the threshold for their next book from Earth, and eventually
 * over the threshold that wakes the Witness.
 */
public final class WatchService {

    /** How often walking distance is sampled, in ticks. Cheap and good enough. */
    private static final int MOVEMENT_SAMPLE_TICKS = 20;

    /** How often records are flushed, in ticks. Players break a lot of blocks; disk is slow. */
    private static final int AUTOSAVE_TICKS = 20 * 60 * 2;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type RECORD_MAP = new TypeToken<Map<String, StoredRecord>>() { }.getType();

    private final Map<UUID, WatchRecord> records = new HashMap<>();
    private final Map<UUID, double[]> lastSampled = new HashMap<>();

    private MinecraftServer server;
    private Path file;
    private boolean dirty;
    private int tick;

    public WatchRecord of(UUID id) {
        return records.computeIfAbsent(id, key -> new WatchRecord());
    }

    public WatchRecord of(ServerPlayer player) {
        WatchRecord record = of(player.getUUID());
        record.name(player.getGameProfile().name());
        return record;
    }

    public Map<UUID, WatchRecord> all() {
        return Map.copyOf(records);
    }

    /** Everything every player has ever done. This is what eventually summons the Witness. */
    public long worldTotalInteractions() {
        long total = 0;
        for (WatchRecord record : records.values()) {
            total += record.totalInteractions();
        }
        return total;
    }

    // ---------------------------------------------------------------- events

    public void register() {
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                of(serverPlayer).recordMined(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
                dirty = true;
            }
        });

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                ItemStack stack = player.getItemInHand(hand);
                if (!stack.isEmpty()) {
                    of(serverPlayer).recordPlaced(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                    dirty = true;
                }
            }
            return net.minecraft.world.InteractionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                ItemStack stack = player.getItemInHand(hand);
                if (!stack.isEmpty()) {
                    of(serverPlayer).recordUsed(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                    dirty = true;
                }
            }
            return net.minecraft.world.InteractionResult.PASS;
        });

        ServerTickEvents.END_SERVER_TICK.register(this::onTick);
        ServerLifecycleEvents.SERVER_STOPPING.register(stopping -> save());
    }

    private void onTick(MinecraftServer running) {
        tick++;
        if (tick % MOVEMENT_SAMPLE_TICKS == 0) {
            sampleMovement(running);
        }
        if (tick % AUTOSAVE_TICKS == 0) {
            save();
        }
    }

    /**
     * Distance is sampled on a timer rather than hooked into movement, because
     * movement fires many times per player per tick and this only needs to be
     * roughly right.
     */
    private void sampleMovement(MinecraftServer running) {
        for (ServerPlayer player : running.getPlayerList().getPlayers()) {
            double[] previous = lastSampled.put(player.getUUID(),
                    new double[] {player.getX(), player.getZ()});
            if (previous == null) {
                continue;
            }
            // Horizontal only: falling down a hole is not exploration.
            double dx = player.getX() - previous[0];
            double dz = player.getZ() - previous[1];
            long blocks = (long) Math.floor(Math.sqrt(dx * dx + dz * dz));
            if (blocks > 0) {
                of(player).addWalked(blocks);
                dirty = true;
            }
        }
    }

    public void died(ServerPlayer player) {
        of(player).recordDeath();
        dirty = true;
    }

    public void mobKilled(ServerPlayer player) {
        of(player).recordMobKill();
        dirty = true;
    }

    // --------------------------------------------------------------- storage

    public void load(MinecraftServer running) {
        this.server = running;
        this.file = running.getWorldPath(LevelResource.ROOT).resolve("ledger-records.json");

        records.clear();
        if (!Files.exists(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, StoredRecord> stored = GSON.fromJson(reader, RECORD_MAP);
            if (stored == null) {
                return;
            }
            stored.forEach((key, value) -> {
                UUID id;
                try {
                    id = UUID.fromString(key);
                } catch (IllegalArgumentException ignored) {
                    return;
                }
                records.put(id, value.toRecord());
            });
            Ledger.LOGGER.info("Loaded {} watch records.", records.size());
        } catch (IOException | RuntimeException exception) {
            Ledger.LOGGER.warn("Could not read the ledger: {}", exception.toString());
        }
    }

    public void save() {
        if (!dirty || file == null) {
            return;
        }
        Map<String, StoredRecord> stored = new HashMap<>();
        records.forEach((id, record) -> stored.put(id.toString(), StoredRecord.from(record)));

        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(stored, RECORD_MAP, writer);
            }
            dirty = false;
        } catch (IOException exception) {
            Ledger.LOGGER.warn("Could not write the ledger: {}", exception.toString());
        }
    }

    public MinecraftServer server() {
        return server;
    }

    /** Flat, stable-on-disk shape. Kept separate so the live record can change freely. */
    private static final class StoredRecord {
        String name = "";
        Map<String, Integer> mined = new HashMap<>();
        Map<String, Integer> placed = new HashMap<>();
        Map<String, Integer> used = new HashMap<>();
        long walked;
        long mobs;
        long deaths;
        long books;
        long lastBook;

        static StoredRecord from(WatchRecord record) {
            StoredRecord stored = new StoredRecord();
            stored.name = record.name();
            stored.mined = record.minedRaw();
            stored.placed = record.placedRaw();
            stored.used = record.usedRaw();
            stored.walked = record.blocksWalked();
            stored.mobs = record.mobsKilled();
            stored.deaths = record.deaths();
            stored.books = record.booksReceived();
            stored.lastBook = record.lastBookAt();
            return stored;
        }

        WatchRecord toRecord() {
            WatchRecord record = new WatchRecord();
            record.name(name == null ? "" : name);
            if (mined != null) {
                record.minedRaw().putAll(mined);
            }
            if (placed != null) {
                record.placedRaw().putAll(placed);
            }
            if (used != null) {
                record.usedRaw().putAll(used);
            }
            record.restore(walked, mobs, deaths, books, lastBook);
            return record;
        }
    }
}
