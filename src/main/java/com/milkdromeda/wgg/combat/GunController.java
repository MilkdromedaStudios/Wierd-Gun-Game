package com.milkdromeda.wgg.combat;

import com.milkdromeda.wgg.WeirdGunGamePlugin;
import com.milkdromeda.wgg.gun.FireMode;
import com.milkdromeda.wgg.gun.GunBlueprint;
import com.milkdromeda.wgg.gun.GunItem;
import com.milkdromeda.wgg.gun.GunStats;
import com.milkdromeda.wgg.gun.GunTrait;
import com.milkdromeda.wgg.util.Text;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Owns everything stateful about holding a gun: the trigger, the reload timer,
 * aiming down sights, minigun spin-up and the per-tick housekeeping.
 * <p>
 * Bukkit only reports a right-click on air once per click, so held-trigger
 * weapons use a rolling {@code triggerHeldUntil} window that each new click
 * refreshes. Clicking a block repeats naturally, and Paper's
 * {@code isHandRaised()} is checked too, so all three sustain paths work.
 */
public final class GunController {

    /** How long one right-click keeps an automatic weapon firing, in ticks. */
    private static final long TRIGGER_HOLD_TICKS = 25L;

    private static final NamespacedKey HANDLING_KEY = new NamespacedKey("wgg", "handling");

    private final WeirdGunGamePlugin plugin;
    private final ShotEngine shots;
    private final Map<UUID, Session> sessions = new HashMap<>();

    private long currentTick;
    private BukkitTask tickTask;

    public GunController(WeirdGunGamePlugin plugin, ShotEngine shots) {
        this.plugin = plugin;
        this.shots = shots;
    }

    /** Per-player gun state. Lives only while the player is online. */
    private static final class Session {
        long nextShotTick;
        long triggerHeldUntil;
        boolean aiming;
        boolean zoomApplied;
        boolean reloading;
        int spinUp;
        boolean charging;
        long chargeCompleteTick;
        int regenCounter;
        double appliedHandling;
        int steadyTicks;
        Location lastPosition;
        String scopeReadout = "";
        BukkitTask reloadTask;
        String reloadingParts;
    }

    private Session session(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), id -> new Session());
    }

    public void start() {
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                currentTick++;
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    tickPlayer(player);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        for (Session session : sessions.values()) {
            if (session.reloadTask != null) {
                session.reloadTask.cancel();
            }
        }
        sessions.clear();
    }

    public void clear(Player player) {
        Session session = sessions.remove(player.getUniqueId());
        if (session != null) {
            if (session.reloadTask != null) {
                session.reloadTask.cancel();
            }
            clearZoom(player, session);
        }
        removeHandling(player);
    }

    // ------------------------------------------------------------- tick loop

    private void tickPlayer(Player player) {
        Session session = sessions.get(player.getUniqueId());
        ItemStack held = player.getInventory().getItemInMainHand();

        if (!GunItem.isGun(held)) {
            if (session != null) {
                if (session.reloadTask != null) {
                    session.reloadTask.cancel();
                    session.reloadTask = null;
                    session.reloading = false;
                }
                // Aiming applies a slowness effect for the scope zoom. Putting
                // the gun away has to take it back off, or the player is stuck
                // walking at scope speed forever.
                stopAiming(player, session);
                session.spinUp = 0;
                session.charging = false;
                if (session.appliedHandling != 0) {
                    removeHandling(player);
                    session.appliedHandling = 0;
                }
            }
            return;
        }

        if (session == null) {
            session = session(player);
        }
        GunBlueprint blueprint = GunItem.blueprintOf(held);
        GunStats stats = blueprint.stats();

        applyHandling(player, session, stats);
        tickScope(player, session, stats);
        tickAmmoRegen(player, session, stats, held);
        tickCharge(player, session, stats, held);
        tickSustainedFire(player, session, stats, held);

        if (!session.reloading) {
            showAmmoBar(player, stats, GunItem.ammoOf(held), session);
        }
    }

    /** Ticks required standing still before a scope counts as steadied. */
    private static final int STEADY_TICKS_REQUIRED = 20;

    /** Minimum zoom level that gets the full scope treatment. */
    private static final int SCOPE_ZOOM = 2;

    /**
     * The scope. While aiming a real optic, a marker is drawn at the exact point
     * the round would land — sent only to the shooter, so it is their scope and
     * not a laser everyone can see — along with a range and target readout.
     * <p>
     * Holding still while crouched steadies the scope, which removes spread
     * entirely rather than adding damage. That keeps snipers precise without
     * letting them punch through the balance caps.
     */
    private void tickScope(Player player, Session session, GunStats stats) {
        if (!session.aiming || stats.zoom() < SCOPE_ZOOM) {
            session.steadyTicks = 0;
            session.scopeReadout = "";
            return;
        }

        Location current = player.getLocation();
        boolean stationary = session.lastPosition != null
                && session.lastPosition.getWorld().equals(current.getWorld())
                && session.lastPosition.distanceSquared(current) < 0.002;
        session.lastPosition = current.clone();

        if (stationary && player.isSneaking()) {
            session.steadyTicks = Math.min(STEADY_TICKS_REQUIRED, session.steadyTicks + 1);
        } else {
            session.steadyTicks = 0;
        }
        boolean steady = session.steadyTicks >= STEADY_TICKS_REQUIRED;

        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        RayTraceResult hit = player.getWorld().rayTrace(eye, direction, stats.range(),
                FluidCollisionMode.NEVER, true, 0.4,
                entity -> entity instanceof LivingEntity && !entity.equals(player) && !entity.isDead());

        if (hit == null) {
            session.scopeReadout = steady
                    ? " <dark_gray>|</dark_gray> <#4ade80>STEADY</#4ade80>"
                    : " <dark_gray>|</dark_gray> <#a3e635>SCOPED</#a3e635>";
            return;
        }

        Location mark = hit.getHitPosition().toLocation(player.getWorld());
        // Player-scoped particle: only the shooter sees their own aim point.
        player.spawnParticle(Particle.DUST, mark, 1, 0, 0, 0, 0,
                new Particle.DustOptions(steady
                        ? org.bukkit.Color.fromRGB(0x4A, 0xDE, 0x80)
                        : org.bukkit.Color.fromRGB(0xF4, 0x3F, 0x5E), 0.6f));

        String distance = Text.num(eye.distance(mark)) + "m";
        Entity target = hit.getHitEntity();
        String label = target instanceof Player hostile ? " <white>" + hostile.getName() + "</white>"
                : target instanceof LivingEntity living
                ? " <white>" + living.getType().name().toLowerCase().replace('_', ' ') + "</white>"
                : "";

        session.scopeReadout = " <dark_gray>|</dark_gray> "
                + (steady ? "<#4ade80>STEADY" : "<#a3e635>SCOPED")
                + " <dark_gray>" + distance + "</dark_gray>" + label;
    }

    private ShotEngine.Stance stanceOf(Session session) {
        if (!session.aiming) {
            return ShotEngine.Stance.HIP;
        }
        return session.steadyTicks >= STEADY_TICKS_REQUIRED
                ? ShotEngine.Stance.STEADY
                : ShotEngine.Stance.AIMED;
    }

    private void tickAmmoRegen(Player player, Session session, GunStats stats, ItemStack held) {
        if (!stats.has(GunTrait.REGEN_AMMO) || stats.ammoRegenTicks() <= 0) {
            return;
        }
        int ammo = GunItem.ammoOf(held);
        if (ammo >= stats.magSize()) {
            session.regenCounter = 0;
            return;
        }
        if (++session.regenCounter >= stats.ammoRegenTicks()) {
            session.regenCounter = 0;
            writeAmmo(player, held, ammo + 1);
        }
    }

    private void tickCharge(Player player, Session session, GunStats stats, ItemStack held) {
        if (!session.charging) {
            return;
        }
        if (stats.fireMode() != FireMode.CHARGE) {
            session.charging = false;
            return;
        }
        long remaining = session.chargeCompleteTick - currentTick;
        if (remaining > 0) {
            double progress = 1.0 - (double) remaining / Math.max(1, stats.chargeTicks());
            player.sendActionBar(Text.mm("<#a78bfa>Charging  "
                    + Text.bar(progress, 1.0, 20, "<#c084fc>", "<dark_gray>")));
            if (remaining % 4 == 0) {
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                        0.5f, 0.6f + (float) progress);
            }
            return;
        }
        session.charging = false;
        discharge(player, session, stats, held);
    }

    private void tickSustainedFire(Player player, Session session, GunStats stats, ItemStack held) {
        if (!stats.fireMode().isSustained()) {
            session.spinUp = 0;
            return;
        }
        boolean triggerDown = currentTick <= session.triggerHeldUntil || player.isHandRaised();
        if (!triggerDown || session.reloading) {
            if (session.spinUp > 0) {
                session.spinUp = Math.max(0, session.spinUp - 2);
            }
            return;
        }

        if (stats.spinUpTicks() > 0 && session.spinUp < stats.spinUpTicks()) {
            session.spinUp++;
            player.sendActionBar(Text.mm("<#facc15>Spinning up  "
                    + Text.bar(session.spinUp, stats.spinUpTicks(), 16, "<#facc15>", "<dark_gray>")));
            if (session.spinUp % 3 == 0) {
                player.playSound(player.getLocation(), Sound.BLOCK_PISTON_EXTEND, 0.6f,
                        0.6f + 1.2f * session.spinUp / Math.max(1, stats.spinUpTicks()));
            }
            return;
        }
        attemptShot(player, session, stats, held);
    }

    // --------------------------------------------------------------- triggers

    /** Called from the right-click listener. */
    public void pullTrigger(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!GunItem.isGun(held)) {
            return;
        }
        Session session = session(player);
        GunStats stats = GunItem.blueprintOf(held).stats();

        session.triggerHeldUntil = currentTick + TRIGGER_HOLD_TICKS;

        switch (stats.fireMode()) {
            case AUTO, SPINUP -> {
                // Handled by the tick loop while the trigger window is open.
            }
            case CHARGE -> {
                if (!session.charging && !session.reloading && currentTick >= session.nextShotTick) {
                    if (GunItem.ammoOf(held) <= 0) {
                        dryFire(player, session, held);
                        return;
                    }
                    session.charging = true;
                    session.chargeCompleteTick = currentTick + stats.chargeTicks();
                }
            }
            case BURST -> fireBurst(player, session, stats);
            case SEMI -> attemptShot(player, session, stats, held);
        }
    }

    public void releaseTrigger(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session != null) {
            session.triggerHeldUntil = 0;
        }
    }

    private void fireBurst(Player player, Session session, GunStats stats) {
        if (session.reloading || currentTick < session.nextShotTick) {
            return;
        }
        int rounds = stats.burstCount();
        session.nextShotTick = currentTick + (long) Math.ceil(stats.fireRateTicks()) + rounds * 2L;

        new BukkitRunnable() {
            int fired;

            @Override
            public void run() {
                ItemStack current = player.getInventory().getItemInMainHand();
                if (fired >= rounds || !player.isOnline() || !GunItem.isGun(current)) {
                    cancel();
                    return;
                }
                fired++;
                Session live = session(player);
                if (!spendAmmo(player, live, current, stats)) {
                    cancel();
                    return;
                }
                shots.fire(player, stats, stanceOf(live));
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void discharge(Player player, Session session, GunStats stats, ItemStack held) {
        if (!spendAmmo(player, session, held, stats)) {
            return;
        }
        session.nextShotTick = currentTick + (long) Math.ceil(stats.fireRateTicks());
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.7f, 1.4f);
        shots.fire(player, stats, stanceOf(session));
    }

    private void attemptShot(Player player, Session session, GunStats stats, ItemStack held) {
        if (session.reloading || currentTick < session.nextShotTick) {
            return;
        }
        if (!spendAmmo(player, session, held, stats)) {
            return;
        }

        double interval = stats.fireRateTicks();
        if (stats.fireMode() == FireMode.SPINUP && stats.spinUpTicks() > 0) {
            // Fully spun up fires at the listed rate; cold barrels are three times slower.
            double ratio = (double) session.spinUp / stats.spinUpTicks();
            interval = stats.fireRateTicks() * (3.0 - 2.0 * ratio);
        }
        session.nextShotTick = currentTick + Math.max(1L, (long) Math.round(interval));

        shots.fire(player, stats, stanceOf(session));
    }

    /**
     * Consumes one round, handling empty magazines and jams.
     *
     * @return true if the shot should actually go off
     */
    private boolean spendAmmo(Player player, Session session, ItemStack held, GunStats stats) {
        int ammo = GunItem.ammoOf(held);
        if (ammo <= 0) {
            dryFire(player, session, held);
            return false;
        }
        writeAmmo(player, held, ammo - 1);

        if (stats.jamChance() > 0 && ThreadLocalRandom.current().nextDouble() < stats.jamChance()) {
            session.nextShotTick = currentTick + 14;
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1.0f, 0.7f);
            player.sendActionBar(Text.mm("<#f43f5e><bold>JAMMED</bold></#f43f5e> <gray>— shake it out</gray>"));
            return false;
        }
        return true;
    }

    private void dryFire(Player player, Session session, ItemStack held) {
        session.nextShotTick = currentTick + 8;
        player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1.0f, 1.6f);
        player.sendActionBar(Text.mm("<#f43f5e>Empty <gray>— press <white>F</white> to reload</gray>"));
        startReload(player);
    }

    // ---------------------------------------------------------------- reload

    public void startReload(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!GunItem.isGun(held)) {
            return;
        }
        Session session = session(player);
        if (session.reloading) {
            return;
        }
        GunBlueprint blueprint = GunItem.blueprintOf(held);
        GunStats stats = blueprint.stats();
        if (GunItem.ammoOf(held) >= stats.magSize()) {
            return;
        }

        session.reloading = true;
        session.charging = false;
        session.spinUp = 0;
        session.reloadingParts = blueprint.serialize();
        int duration = stats.reloadTicks();

        if (stats.has(GunTrait.BLINK)) {
            // Booster stock: reloading is a mobility option.
            player.setVelocity(player.getLocation().getDirection().multiply(0.55).setY(0.28));
            player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.7f, 1.5f);
        }

        session.reloadTask = new BukkitRunnable() {
            int elapsed;

            @Override
            public void run() {
                ItemStack current = player.getInventory().getItemInMainHand();
                if (!player.isOnline() || !GunItem.isGun(current)
                        || !session.reloadingParts.equals(GunItem.blueprintOf(current).serialize())) {
                    session.reloading = false;
                    session.reloadTask = null;
                    cancel();
                    return;
                }

                elapsed += 2;
                if (elapsed >= duration) {
                    writeAmmo(player, current, stats.magSize());
                    session.reloading = false;
                    session.reloadTask = null;
                    player.playSound(player.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.9f, 1.4f);
                    player.sendActionBar(Text.mm("<#4ade80><bold>Reloaded</bold></#4ade80>"));
                    cancel();
                    return;
                }

                player.sendActionBar(Text.mm("<#facc15>Reloading  "
                        + Text.bar(elapsed, duration, 20, "<#facc15>", "<dark_gray>")));
                if (elapsed % 10 < 2) {
                    player.playSound(player.getLocation(), Sound.BLOCK_PISTON_CONTRACT, 0.5f, 1.2f);
                }
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    public void refundAmmo(Player player, int rounds) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!GunItem.isGun(held)) {
            return;
        }
        writeAmmo(player, held, GunItem.ammoOf(held) + rounds);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.8f);
    }

    /**
     * Updates the round count and puts the stack back in the player's hand.
     * The stack handed out by the inventory is a live mirror, so the write-back
     * is belt-and-braces — but ammo desyncing from the item would be a
     * miserable bug to chase, so it is worth the one extra call.
     */
    private void writeAmmo(Player player, ItemStack gun, int ammo) {
        GunItem.setAmmo(gun, ammo);
        player.getInventory().setItemInMainHand(gun);
    }

    // ------------------------------------------------------------------- aim

    public void toggleAim(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!GunItem.isGun(held)) {
            return;
        }
        Session session = session(player);
        GunStats stats = GunItem.blueprintOf(held).stats();
        session.aiming = !session.aiming;

        if (session.aiming) {
            if (stats.zoom() > 0) {
                session.zoomApplied = true;
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                        Integer.MAX_VALUE, Math.min(4, 1 + stats.zoom()), false, false, false));
            }
            player.playSound(player.getLocation(), Sound.ITEM_SPYGLASS_USE, 0.7f, 1.2f);
        } else {
            clearZoom(player, session);
            player.playSound(player.getLocation(), Sound.ITEM_SPYGLASS_STOP_USING, 0.7f, 1.2f);
        }
    }

    private void stopAiming(Player player, Session session) {
        session.aiming = false;
        clearZoom(player, session);
    }

    /** Only strips slowness we put on, so we never eat an effect from elsewhere. */
    private void clearZoom(Player player, Session session) {
        if (session.zoomApplied) {
            session.zoomApplied = false;
            player.removePotionEffect(PotionEffectType.SLOWNESS);
        }
    }

    public boolean isAiming(Player player) {
        Session session = sessions.get(player.getUniqueId());
        return session != null && session.aiming;
    }

    // -------------------------------------------------------------- handling

    /** Applies the gun's weight as a movement-speed modifier, only when it changes. */
    private void applyHandling(Player player, Session session, GunStats stats) {
        double target = stats.moveSpeedModifier();
        if (Math.abs(target - session.appliedHandling) < 0.001) {
            return;
        }
        removeHandling(player);
        if (Math.abs(target) > 0.001) {
            AttributeInstance attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
            if (attribute != null) {
                attribute.addModifier(new AttributeModifier(HANDLING_KEY, target,
                        AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
            }
        }
        session.appliedHandling = target;
    }

    private void removeHandling(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attribute == null) {
            return;
        }
        attribute.getModifiers().stream()
                .filter(modifier -> HANDLING_KEY.equals(modifier.getKey()))
                .toList()
                .forEach(attribute::removeModifier);
    }

    private void showAmmoBar(Player player, GunStats stats, int ammo, Session session) {
        String color = ammo == 0 ? "<#f43f5e>" : ammo <= stats.magSize() * 0.25 ? "<#facc15>" : "<#ff8a3d>";
        String aim = !session.scopeReadout.isEmpty() ? session.scopeReadout
                : session.aiming ? " <dark_gray>|</dark_gray> <#a3e635>ADS</#a3e635>" : "";
        player.sendActionBar(Text.mm(color + "<bold>" + ammo + "</bold>"
                + "<dark_gray>/" + stats.magSize() + "</dark_gray>  "
                + Text.bar(ammo, Math.max(1, stats.magSize()), 15, color, "<dark_gray>") + aim));
    }
}
