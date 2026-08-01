package com.milkdromeda.wgg.combat;

import com.milkdromeda.wgg.WeirdGunGamePlugin;
import com.milkdromeda.wgg.gun.GunItem;
import com.milkdromeda.wgg.util.Keys;
import com.milkdromeda.wgg.util.Text;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/** Translates player input into gun and knife behaviour. */
public final class CombatListener implements Listener {

    private final WeirdGunGamePlugin plugin;

    public CombatListener(WeirdGunGamePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }

        if (GunItem.isGun(item)) {
            // Guns are hoes underneath; stop them tilling the arena floor.
            event.setCancelled(true);
            switch (event.getAction()) {
                case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> plugin.gunController().pullTrigger(player);
                case LEFT_CLICK_AIR, LEFT_CLICK_BLOCK -> plugin.gunController().toggleAim(player);
                default -> { }
            }
            return;
        }

        Knife heldKnife = KnifeItem.knifeOf(item);
        if (heldKnife != null && (event.getAction() == Action.RIGHT_CLICK_AIR
                || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            // Also stops knives that happen to be food (the Baguette) being eaten.
            event.setCancelled(true);
            if (heldKnife.has(Knife.KnifeEffect.DEFLECT)) {
                plugin.parries().start(player);
            }
        }
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        if (KnifeItem.isKnife(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /** F (swap hands) reloads instead of swapping, whenever a gun is held. */
    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (!GunItem.isGun(event.getMainHandItem()) && !GunItem.isGun(event.getOffHandItem())) {
            return;
        }
        event.setCancelled(true);
        plugin.gunController().startReload(event.getPlayer());
    }

    @EventHandler
    public void onHeldChange(PlayerItemHeldEvent event) {
        plugin.gunController().releaseTrigger(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.gunController().clear(event.getPlayer());
        plugin.parries().clear(event.getPlayer());
    }

    // ------------------------------------------------------------------ knives

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent event) {
        // Gunshots, explosions and bleed ticks arrive here too, because they are
        // dealt as damage from the player. Their numbers are already final.
        if (PluginDamage.inProgress()) {
            return;
        }
        if (!(event.getDamager() instanceof Player attacker)
                || !(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        ItemStack weapon = attacker.getInventory().getItemInMainHand();

        // Guns make terrible clubs.
        if (GunItem.isGun(weapon)) {
            event.setDamage(1.0);
            return;
        }

        Knife knife = KnifeItem.knifeOf(weapon);
        if (knife == null) {
            return;
        }

        if (victim instanceof Player target && !plugin.wggConfig().friendlyFire()
                && plugin.tournaments().sameTeam(attacker, target)) {
            event.setCancelled(true);
            return;
        }

        double damage = knife.damage();
        boolean backstab = isBehind(attacker, victim);
        if (backstab) {
            damage *= knife.backstab();
        }
        if (knife.has(Knife.KnifeEffect.CRIT)
                && ThreadLocalRandom.current().nextDouble() < 0.35) {
            damage *= 1.9;
            victim.getWorld().spawnParticle(Particle.CRIT, victim.getEyeLocation(), 14, 0.3, 0.3, 0.3, 0.4);
        }
        if (isBossMinion(victim)) {
            damage *= knife.bossBonus();
        }
        damage *= plugin.wggConfig().globalDamageMultiplier();

        event.setDamage(damage);
        if (knife.has(Knife.KnifeEffect.TRUE_DAMAGE)) {
            // Zero the reduction modifiers rather than editing health directly,
            // so vanilla still handles death, credit and death messages.
            for (EntityDamageEvent.DamageModifier modifier : EntityDamageEvent.DamageModifier.values()) {
                if (modifier != EntityDamageEvent.DamageModifier.BASE && event.isApplicable(modifier)) {
                    event.setDamage(modifier, 0.0);
                }
            }
        }

        if (backstab) {
            attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.6f);
            victim.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, victim.getEyeLocation(), 6,
                    0.2, 0.2, 0.2, 0.1);
        }
        if (knife.knockback() > 0) {
            Vector push = victim.getLocation().toVector().subtract(attacker.getLocation().toVector());
            if (push.lengthSquared() > 0.01) {
                victim.setVelocity(victim.getVelocity()
                        .add(push.normalize().multiply(knife.knockback()).setY(knife.knockback() * 0.35)));
            }
        }
        if (knife.lifesteal() > 0) {
            double max = attacker.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
            attacker.setHealth(Math.min(max, attacker.getHealth() + damage * knife.lifesteal()));
            attacker.getWorld().spawnParticle(Particle.HEART, attacker.getLocation().add(0, 2, 0),
                    1, 0.2, 0.2, 0.2, 0);
        }

        applyKnifeEffects(attacker, victim, knife);
    }

    private void applyKnifeEffects(Player attacker, LivingEntity victim, Knife knife) {
        for (Knife.KnifeEffect effect : knife.effects()) {
            switch (effect) {
                case BLEED -> bleed(attacker, victim);
                case FREEZE -> {
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 2, false, true));
                    victim.setFreezeTicks(Math.min(victim.getMaxFreezeTicks(), victim.getFreezeTicks() + 120));
                }
                case POISON -> victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 0, false, true));
                case SLIP -> {
                    Vector slide = victim.getLocation().toVector()
                            .subtract(attacker.getLocation().toVector());
                    if (slide.lengthSquared() > 0.01) {
                        victim.setVelocity(slide.normalize().multiply(1.1).setY(0.12));
                    }
                }
                case BLINK -> blinkBehind(attacker, victim);
                case CRIT, TRUE_DAMAGE, DEFLECT -> { /* handled elsewhere */ }
            }
        }
    }

    private void bleed(Player attacker, LivingEntity victim) {
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (++ticks > 4 || victim.isDead() || !victim.isValid()) {
                    cancel();
                    return;
                }
                PluginDamage.apply(victim, 1.0, attacker);
                victim.getWorld().spawnParticle(Particle.DUST, victim.getLocation().add(0, 1, 0), 5,
                        0.2, 0.3, 0.2, 0,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(0x8B, 0x00, 0x00), 1.0f));
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void blinkBehind(Player attacker, LivingEntity victim) {
        Location behind = victim.getLocation().clone()
                .add(victim.getLocation().getDirection().multiply(-1.6));
        if (!behind.getBlock().isPassable() || !behind.clone().add(0, 1, 0).getBlock().isPassable()) {
            return;
        }
        behind.setDirection(victim.getLocation().getDirection());
        attacker.getWorld().spawnParticle(Particle.REVERSE_PORTAL, attacker.getLocation(), 20,
                0.3, 0.6, 0.3, 0.1);
        attacker.teleport(behind);
        attacker.playSound(behind, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.4f);
    }

    private boolean isBehind(Player attacker, LivingEntity victim) {
        Vector victimFacing = victim.getLocation().getDirection().setY(0);
        Vector toAttacker = attacker.getLocation().toVector()
                .subtract(victim.getLocation().toVector()).setY(0);
        if (victimFacing.lengthSquared() < 0.01 || toAttacker.lengthSquared() < 0.01) {
            return false;
        }
        return victimFacing.normalize().dot(toAttacker.normalize()) < -0.4;
    }

    private boolean isBossMinion(LivingEntity entity) {
        return entity.getPersistentDataContainer().has(Keys.BOSS_TAG, PersistentDataType.STRING);
    }

}
