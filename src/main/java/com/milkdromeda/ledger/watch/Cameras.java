package com.milkdromeda.ledger.watch;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Rotations;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The cameras.
 * <p>
 * Small observers that appear in the trees and in the dark places underground.
 * They do nothing. They do not track, they do not report, they do not shoot —
 * they hang there with the lens angled down, and that is the entire feature.
 * <p>
 * Doing nothing is the point. The book from Earth has been implying for several
 * volumes that something is watching; a camera that visibly reacts would answer
 * the question, whereas one that simply exists leaves it open. The record is
 * kept by {@link WatchService} regardless of whether any camera can see you,
 * which is the joke.
 * <p>
 * Each one is an invisible armour stand wearing an observer block on its head,
 * so no client mod or resource pack is required. {@code models/camera.bbmodel}
 * is the authored model for anyone who wants to replace the look.
 */
public final class Cameras {

    /** How often a spawn is considered, in ticks. Once every ten seconds. */
    private static final int SPAWN_INTERVAL = 200;

    /** Chance per interval, per player, that one appears nearby. */
    private static final double SPAWN_CHANCE = 0.35;

    /** How many can exist near one player before no more are placed. */
    private static final int NEARBY_LIMIT = 4;

    private static final int SEARCH_RADIUS = 40;
    private static final double CROWDING_RADIUS = 48.0;

    /** Pitched down. The lens is looking at the floor, and at you when you cross it. */
    private static final Rotations LOOKING_DOWN = new Rotations(52.0f, 0.0f, 0.0f);

    private int tick;

    public void register() {
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }

    private void tick(MinecraftServer server) {
        if (++tick % SPAWN_INTERVAL != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ThreadLocalRandom.current().nextDouble() < SPAWN_CHANCE) {
                considerSpawn(player);
            }
        }
    }

    private void considerSpawn(ServerPlayer player) {
        ServerLevel level = player.level();
        if (countNearby(level, player) >= NEARBY_LIMIT) {
            return;
        }
        BlockPos spot = findSpot(level, player);
        if (spot != null) {
            place(level, spot);
        }
    }

    private int countNearby(ServerLevel level, ServerPlayer player) {
        return level.getEntitiesOfClass(ArmorStand.class,
                player.getBoundingBox().inflate(CROWDING_RADIUS),
                stand -> stand.hasCustomName()
                        && "Camera".equals(stand.getCustomName().getString())).size();
    }

    /**
     * Looks for somewhere a camera would plausibly have been put: tucked under
     * the canopy up top, or in open air in a cave below.
     */
    private BlockPos findSpot(ServerLevel level, ServerPlayer player) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        BlockPos origin = player.blockPosition();

        for (int attempt = 0; attempt < 12; attempt++) {
            int x = origin.getX() + random.nextInt(-SEARCH_RADIUS, SEARCH_RADIUS);
            int z = origin.getZ() + random.nextInt(-SEARCH_RADIUS, SEARCH_RADIUS);

            boolean underground = player.getY() < 50;
            int y = underground
                    ? origin.getY() + random.nextInt(-8, 9)
                    : level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + random.nextInt(2, 6);

            BlockPos candidate = new BlockPos(x, y, z);
            // It needs air to hang in, and something solid above to hang from.
            if (level.getBlockState(candidate).isAir()
                    && !level.getBlockState(candidate.above(2)).isAir()
                    && candidate.getY() > level.getMinY() + 2) {
                return candidate;
            }
        }
        return null;
    }

    private void place(ServerLevel level, BlockPos where) {
        EntityType<?> standType = BuiltInRegistries.ENTITY_TYPE
                .getOptional(Identifier.withDefaultNamespace("armor_stand")).orElse(null);
        if (standType == null) {
            return;
        }
        Entity spawned = standType.spawn(level, where, EntitySpawnReason.EVENT);
        if (!(spawned instanceof ArmorStand camera)) {
            return;
        }

        camera.setInvisible(true);
        camera.setNoGravity(true);
        camera.setInvulnerable(true);
        camera.setSilent(true);
        camera.setNoBasePlate(true);
        camera.setCustomName(net.minecraft.network.chat.Component.literal("Camera"));
        camera.setCustomNameVisible(false);
        camera.setHeadPose(LOOKING_DOWN);
        camera.setYRot(ThreadLocalRandom.current().nextFloat() * 360.0f);
        camera.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.OBSERVER));
    }

    /** Removes every camera near a player. Used by the debug command. */
    public int clear(ServerPlayer player) {
        ServerLevel level = player.level();
        var cameras = level.getEntitiesOfClass(ArmorStand.class,
                player.getBoundingBox().inflate(CROWDING_RADIUS * 2),
                stand -> stand.hasCustomName()
                        && "Camera".equals(stand.getCustomName().getString()));
        cameras.forEach(Entity::discard);
        return cameras.size();
    }

    /** Places one directly, for looking at it without waiting. */
    public boolean placeNear(ServerPlayer player) {
        ServerLevel level = player.level();
        BlockPos spot = findSpot(level, player);
        if (spot == null) {
            return false;
        }
        place(level, spot);
        return true;
    }
}
