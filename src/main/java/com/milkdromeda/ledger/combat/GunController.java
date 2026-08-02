package com.milkdromeda.ledger.combat;

import com.milkdromeda.ledger.Ledger;
import com.milkdromeda.ledger.gun.FireMode;
import com.milkdromeda.ledger.gun.GunBlueprint;
import com.milkdromeda.ledger.gun.GunItem;
import com.milkdromeda.ledger.gun.GunStats;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The trigger, the magazine and the reload timer.
 * <p>
 * Right-click fires. Holding right-click keeps automatics going: Minecraft only
 * reports a click on air once, so a held trigger is modelled as a short window
 * that each click refreshes and the tick loop keeps firing inside. Sneaking
 * counts as aiming and tightens the spread.
 * <p>
 * Reloading starts by itself when the magazine runs dry, so there is no key to
 * learn and no way to be stuck holding an empty gun.
 */
public final class GunController {

    /** How long one click keeps an automatic firing, in ticks. */
    private static final int TRIGGER_WINDOW = 12;

    private final Map<UUID, Session> sessions = new HashMap<>();

    /** Per-player firing state. Lives only while they are online. */
    private static final class Session {
        long nextShotTick;
        long triggerHeldUntil;
        long reloadDoneTick;
        boolean reloading;
    }

    private Session session(ServerPlayer player) {
        return sessions.computeIfAbsent(player.getUUID(), id -> new Session());
    }

    public void register() {
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer shooter)) {
                return InteractionResult.PASS;
            }
            ItemStack held = shooter.getMainHandItem();
            if (!GunItem.isGun(held)) {
                return InteractionResult.PASS;
            }
            pullTrigger(shooter, held);
            // Consume so the hoe underneath never tills anything.
            return InteractionResult.SUCCESS;
        });

        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }

    // ---------------------------------------------------------------- trigger

    private void pullTrigger(ServerPlayer shooter, ItemStack gun) {
        Session session = session(shooter);
        long now = shooter.level().getServer().getTickCount();
        GunStats stats = GunItem.blueprintOf(gun).stats();

        session.triggerHeldUntil = now + TRIGGER_WINDOW;

        // Automatics are driven by the tick loop; everything else fires here so
        // a single click gives a single round with no perceptible delay.
        if (!stats.fireMode().isSustained()) {
            attemptShot(shooter, gun, stats, session, now);
        }
    }

    private void tick(MinecraftServer server) {
        long now = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Session session = sessions.get(player.getUUID());
            if (session == null) {
                continue;
            }
            ItemStack held = player.getMainHandItem();
            if (!GunItem.isGun(held)) {
                session.reloading = false;
                continue;
            }
            GunStats stats = GunItem.blueprintOf(held).stats();

            if (session.reloading) {
                finishReload(player, held, stats, session, now);
                continue;
            }
            if (stats.fireMode().isSustained() && now <= session.triggerHeldUntil) {
                attemptShot(player, held, stats, session, now);
            }
        }
    }

    /** Spends a round and fires, or starts a reload if the magazine is empty. */
    private void attemptShot(ServerPlayer shooter, ItemStack gun, GunStats stats,
                             Session session, long now) {
        if (session.reloading || now < session.nextShotTick) {
            return;
        }
        int ammo = GunItem.ammoOf(gun);
        if (ammo <= 0) {
            beginReload(shooter, stats, session, now);
            return;
        }

        GunItem.setAmmo(gun, ammo - 1);
        session.nextShotTick = now + Math.max(1, Math.round(stats.fireRateTicks()));

        ShotEngine.fire(shooter, stats, shooter.isShiftKeyDown());
        Ledger.watch().of(shooter).recordUsed("ledger:gun_fired");
        showAmmo(shooter, ammo - 1, stats.magSize());
    }

    // ----------------------------------------------------------------- reload

    private void beginReload(ServerPlayer shooter, GunStats stats, Session session, long now) {
        session.reloading = true;
        session.reloadDoneTick = now + stats.reloadTicks();
        shooter.level().playSound(null, shooter.blockPosition(), SoundEvents.PISTON_CONTRACT,
                SoundSource.PLAYERS, 0.6f, 1.2f);
        shooter.sendSystemMessage(
                Component.literal("Reloading…").withStyle(ChatFormatting.YELLOW), true);
    }

    private void finishReload(ServerPlayer shooter, ItemStack gun, GunStats stats,
                              Session session, long now) {
        if (now < session.reloadDoneTick) {
            return;
        }
        session.reloading = false;
        GunItem.setAmmo(gun, stats.magSize());
        shooter.level().playSound(null, shooter.blockPosition(), SoundEvents.IRON_TRAPDOOR_CLOSE,
                SoundSource.PLAYERS, 0.8f, 1.4f);
        showAmmo(shooter, stats.magSize(), stats.magSize());
    }

    private void showAmmo(ServerPlayer shooter, int ammo, int magSize) {
        ChatFormatting colour = ammo == 0 ? ChatFormatting.RED
                : ammo <= magSize * 0.25 ? ChatFormatting.YELLOW : ChatFormatting.GOLD;
        shooter.sendSystemMessage(
                Component.literal(ammo + " / " + magSize).withStyle(colour), true);
    }

    public void forget(ServerPlayer player) {
        sessions.remove(player.getUUID());
    }
}
