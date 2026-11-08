package com.r0ine.auraforge.ability

import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Bu fonksiyonlar oyuncunun kendi thread'inde çağrılmak üzere yazıldı (olay dinleyicileri zaten orada çalışır).

fun Player.launchForward(power: Double) {
    val direction = location.direction.setY(0.0)
    if (direction.lengthSquared() < 1.0E-4) return
    velocity = direction.normalize().multiply(power).setY(0.3)
    world.spawnParticle(Particle.CLOUD, location.add(0.0, 0.3, 0.0), 14, 0.25, 0.1, 0.25, 0.03)
    world.playSound(location, Sound.ENTITY_BREEZE_WIND_BURST, 0.9f, 1.3f)
}

fun Player.leap(power: Double) {
    val forward = location.direction.setY(0.0)
    val push = if (forward.lengthSquared() < 1.0E-4) Vector() else forward.normalize().multiply(power * 0.45)
    velocity = push.setY(power)
    world.spawnParticle(Particle.CLOUD, location, 20, 0.3, 0.05, 0.3, 0.05)
    world.playSound(location, Sound.ENTITY_BREEZE_JUMP, 1f, 0.9f)
}

fun Player.triggerShockwave(radius: Double, damage: Double) {
    val origin = location
    world.playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.7f)
    world.spawnParticle(Particle.EXPLOSION, origin, 1)
    for (step in 0 until 32) {
        val angle = step * PI / 16
        val point = origin.clone().add(cos(angle) * radius, 0.15, sin(angle) * radius)
        world.spawnParticle(Particle.CRIT, point, 2, 0.1, 0.05, 0.1, 0.0)
    }

    val center = origin.toVector()
    getNearbyEntities(radius, radius / 2, radius)
        .asSequence()
        .filterIsInstance<LivingEntity>()
        .filter { it !is ArmorStand && !it.isInvulnerable && canHit(it) && Bukkit.isOwnedByCurrentRegion(it) }
        .forEach { target ->
            val offset = target.location.toVector().subtract(center).setY(0.0)
            val falloff = 1.0 - (offset.length() / radius).coerceIn(0.0, 1.0) * 0.5
            val push = if (offset.lengthSquared() < 1.0E-4) {
                Vector(0.0, 0.6, 0.0)
            } else {
                offset.normalize().multiply(1.25 * falloff).setY(0.45)
            }
            target.damage(damage * falloff, this)
            target.velocity = push
        }
}

fun Player.guard(ticks: Int, amplifier: Int) {
    addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, ticks, amplifier, false, true, true))
    addPotionEffect(PotionEffect(PotionEffectType.ABSORPTION, ticks, 0, false, true, true))
    world.playSound(location, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 0.8f)
    world.spawnParticle(Particle.WAX_ON, location.add(0.0, 1.0, 0.0), 24, 0.4, 0.6, 0.4, 0.02)
}

private fun Player.canHit(target: LivingEntity): Boolean {
    if (target !is Player) return true
    return world.pvp && target.gameMode in setOf(GameMode.SURVIVAL, GameMode.ADVENTURE)
}
