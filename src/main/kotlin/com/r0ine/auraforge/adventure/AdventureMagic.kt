package com.r0ine.auraforge.adventure

import com.r0ine.auraforge.AuraForgePlugin
import org.bukkit.FluidCollisionMode
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import kotlin.math.ceil

class AdventureMagic(private val plugin: AuraForgePlugin) {
    private val adventure get() = plugin.adventure
    private val combat get() = plugin.adventureCombat

    fun cast(player: Player): Boolean {
        val profile = adventure.profile(player)
        val spell = LearnedSpell.entries.firstOrNull { it.id == profile.selected && it.id in profile.studied } ?: return false
        if (!player.hasPermission("auraforge.cast")) {
            plugin.text.bar(player, "no-permission")
            return true
        }
        val now = System.currentTimeMillis()
        val remaining = maxOf(profile.cooldowns[spell.id] ?: 0, profile.cooldowns["global"] ?: 0) - now
        if (remaining > 0) {
            adventure.tell(player, "Büyü için ${ceil(remaining / 1000.0).toInt()} saniye bekle.")
            return true
        }
        val mana = profile.regenerated(now, adventure.manaMaximum(player))
        val cost = ceil(spell.mana * if (adventure.pieces(player, ArmorSet.ASTRAL) >= 4) 0.8 else 1.0)
        if (mana < cost) {
            adventure.tell(player, "${cost.toInt()} mana gerekli; mevcut ${mana.toInt()}.")
            return true
        }
        val targeted = spell in setOf(LearnedSpell.CINDER_LANCE, LearnedSpell.GLACIAL_PRISON, LearnedSpell.VOID_HOOK, LearnedSpell.ECLIPSE)
        val range = if (spell == LearnedSpell.GLACIAL_PRISON || spell == LearnedSpell.VOID_HOOK) 16.0 else 18.0
        val target = if (targeted) target(player, range) else null
        if (targeted && target == null) {
            adventure.tell(player, "Menzilde görünür bir hedef yok.")
            return true
        }
        adventure.change(player) {
            it.copy(mana = mana - cost, manaAt = now,
                cooldowns = it.cooldowns.filterValues { until -> until > now } + (spell.id to now + spell.cooldown * 1000L) + ("global" to now + 1000))
        }
        when (spell) {
            LearnedSpell.CINDER_LANCE -> combat.hit(player, target!!, 6.0) { target.fireTicks = maxOf(target.fireTicks, 80) }
            LearnedSpell.GLACIAL_PRISON -> combat.hit(player, target!!, 4.0) {
                target.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 3))
                target.freezeTicks = target.maxFreezeTicks
            }
            LearnedSpell.STORM_LINK -> nearby(player, 8.0).take(4).forEach {
                combat.hit(player, it, 5.0) { it.world.strikeLightningEffect(it.location) }
            }
            LearnedSpell.STONE_SKIN -> player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, 120, 1))
            LearnedSpell.VERDANT_PULSE -> {
                combat.heal(player, 6.0)
                player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 80, 0))
            }
            LearnedSpell.VOID_HOOK -> combat.hit(player, target!!, 4.0) {
                val pull = player.location.toVector().subtract(target.location.toVector())
                if (pull.lengthSquared() > 0.01) target.velocity = pull.normalize().multiply(0.9).setY(0.25)
            }
            LearnedSpell.SOLAR_NOVA -> {
                player.fireTicks = 0
                nearby(player, 6.0).take(8).forEach { combat.hit(player, it, 7.0) }
            }
            LearnedSpell.FROST_SHATTER -> nearby(player, 7.0).take(8).forEach {
                val damage = if (it.freezeTicks > 0 || it.hasPotionEffect(PotionEffectType.SLOWNESS)) 9.0 else 5.0
                combat.hit(player, it, damage) { it.freezeTicks = 0 }
            }
            LearnedSpell.WINDSTEP -> {
                player.velocity = player.eyeLocation.direction.multiply(1.2).setY(0.35)
                player.addPotionEffect(PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0))
                player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 100, 1))
            }
            LearnedSpell.ECLIPSE -> {
                val maximum = target!!.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
                combat.hit(player, target, if (target.health < maximum * 0.3) 12.0 else 8.0)
            }
        }
        val center = target?.location?.add(0.0, 1.0, 0.0) ?: player.location.add(0.0, 1.0, 0.0)
        val particle = when (spell) {
            LearnedSpell.CINDER_LANCE, LearnedSpell.SOLAR_NOVA -> Particle.FLAME
            LearnedSpell.GLACIAL_PRISON, LearnedSpell.FROST_SHATTER -> Particle.SNOWFLAKE
            LearnedSpell.VOID_HOOK, LearnedSpell.ECLIPSE -> Particle.PORTAL
            LearnedSpell.VERDANT_PULSE -> Particle.HAPPY_VILLAGER
            LearnedSpell.STONE_SKIN -> Particle.ENCHANT
            LearnedSpell.STORM_LINK -> Particle.ELECTRIC_SPARK
            LearnedSpell.WINDSTEP -> Particle.CLOUD
        }
        player.world.spawnParticle(particle, center, 45, 0.8, 0.6, 0.8, 0.04)
        player.playSound(player.location, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.1f)
        player.sendActionBar(plugin.text.parse("<light_purple>${spell.title} <dark_gray>· <aqua>Mana ${(mana - cost).toInt()}"))
        return true
    }

    private fun target(player: Player, range: Double): LivingEntity? = player.world.rayTrace(
        player.eyeLocation, player.eyeLocation.direction, range, FluidCollisionMode.NEVER, true, 0.25,
    ) { it is LivingEntity && combat.canHit(player, it) }?.hitEntity as? LivingEntity

    private fun nearby(player: Player, radius: Double): List<LivingEntity> = player.getNearbyEntities(radius, radius, radius)
        .filterIsInstance<LivingEntity>()
        .filter { combat.canHit(player, it) && it.location.distanceSquared(player.location) <= radius * radius && player.hasLineOfSight(it) }
        .sortedBy { it.location.distanceSquared(player.location) }
}
