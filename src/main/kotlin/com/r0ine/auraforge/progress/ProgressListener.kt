package com.r0ine.auraforge.progress

import com.r0ine.auraforge.AuraForgePlugin
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import kotlin.math.roundToInt
import kotlin.random.Random

class ProgressListener(private val plugin: AuraForgePlugin) : Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onHit(event: EntityDamageByEntityEvent) {
        if (event.cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK && event.cause != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return
        val player = event.damager as? Player ?: return
        val target = event.entity as? LivingEntity ?: return
        if (target is ArmorStand) return
        val (_, progress) = plugin.progress.weaponIn(player) ?: return

        var multiplier = 1.0 + progress.plus * plugin.settings.enhance.damagePerPlus + progress.value(Talent.KEEN_EDGE)
        val targetMax = target.getAttribute(Attribute.MAX_HEALTH)?.value ?: target.health
        if (progress.has(Talent.EXECUTE) && target.health < targetMax * 0.3) multiplier += progress.value(Talent.EXECUTE)
        val playerMax = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
        if (progress.has(Talent.BERSERK) && player.health < playerMax * 0.4) multiplier += progress.value(Talent.BERSERK)

        if (progress.has(Talent.CRIT) && Random.nextDouble() < progress.value(Talent.CRIT)) {
            multiplier *= 1.5
            target.world.spawnParticle(Particle.CRIT, target.location.add(0.0, target.height * 0.7, 0.0), 20, 0.3, 0.3, 0.3, 0.3)
            player.playSound(player.location, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.8f)
        }

        var damage = event.damage * multiplier
        val element = progress.element
        if (element != null && event.cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK && plugin.progress.canHit(player, target)) {
            val chance = plugin.settings.progression.elementChance + progress.value(Talent.RESONANCE)
            if (Random.nextDouble() < chance) damage += plugin.progress.onProc(player, target, element, progress)
        }
        event.damage = damage

        if (progress.has(Talent.HEAVY_BLOW) && Random.nextDouble() < progress.value(Talent.HEAVY_BLOW)) {
            target.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 40, 1))
            target.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, 40, 0))
            target.world.playSound(target.location, Sound.BLOCK_ANVIL_LAND, 0.4f, 1.6f)
        }
        if (progress.has(Talent.BLEED) && Random.nextDouble() < progress.value(Talent.BLEED)) {
            target.addPotionEffect(PotionEffect(PotionEffectType.WITHER, 60, 0))
        }
        if (progress.has(Talent.LIFESTEAL)) {
            player.health = (player.health + damage * progress.value(Talent.LIFESTEAL)).coerceAtMost(playerMax)
        }

        plugin.progress.addXp(player, plugin.settings.progression.xpPerHit)
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onHurt(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        val melee = event.cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK || event.cause == EntityDamageEvent.DamageCause.PROJECTILE
        if (!melee) return

        val dodge = plugin.progress.weaponIn(player)?.second?.value(Talent.EVASION) ?: 0.0
        if (dodge > 0 && Random.nextDouble() < dodge) {
            event.isCancelled = true
            player.world.spawnParticle(Particle.CLOUD, player.location.add(0.0, 1.0, 0.0), 12, 0.3, 0.5, 0.3, 0.02)
            player.playSound(player.location, Sound.ENTITY_BREEZE_JUMP, 0.8f, 1.5f)
            plugin.text.bar(player, "proc-evade")
            return
        }

        val reduction = plugin.progress.armorReduction(player)
        if (reduction > 0) event.damage = event.damage * (1 - reduction)
    }

    @EventHandler(ignoreCancelled = true)
    fun onDeath(event: EntityDeathEvent) {
        val victim = event.entity
        if (victim is ArmorStand) return
        val killer = victim.killer ?: return
        val (_, progress) = plugin.progress.weaponIn(killer) ?: return
        val maxHealth = victim.getAttribute(Attribute.MAX_HEALTH)?.value ?: 10.0
        plugin.progress.addXp(killer, (maxHealth * plugin.settings.progression.xpPerHealth).roundToInt())
        plugin.progress.onKill(killer, progress)
    }
}
