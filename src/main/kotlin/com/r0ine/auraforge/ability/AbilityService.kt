package com.r0ine.auraforge.ability

import com.r0ine.auraforge.AuraForgePlugin
import com.r0ine.auraforge.api.AbilityType
import com.r0ine.auraforge.core.seconds
import com.r0ine.auraforge.core.tag
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

class AbilityService(private val plugin: AuraForgePlugin) {

    private val cooldowns = ConcurrentHashMap<UUID, ConcurrentHashMap<AbilityType, Long>>()
    private val lastCast = ConcurrentHashMap<UUID, Long>()
    private val fallGrace = ConcurrentHashMap<UUID, Long>()

    fun powerFactor(quality: Int, setPieces: Int = 0): Double {
        val scaling = plugin.settings.quality
        val base = scaling.powerMin + (scaling.powerMax - scaling.powerMin) * quality.coerceIn(0, 100) / 100.0
        val set = plugin.settings.setBonus
        return if (setPieces >= set.minPieces) base + set.perPiece * setPieces else base
    }

    fun cooldownMillis(type: AbilityType, quality: Int): Long {
        val reduction = plugin.settings.quality.cooldownReduction * quality.coerceIn(0, 100) / 100.0
        return (plugin.settings.ability(type).cooldown * 1000 * (1 - reduction)).toLong()
    }

    fun Player.castAbility(type: AbilityType, quality: Int = 50, setPieces: Int = 0): Boolean {
        val now = System.currentTimeMillis()
        if (now - (lastCast[uniqueId] ?: 0L) < 250) return false

        val abilityName = plugin.text.render("ability-${type.key}")
        val readyAt = cooldowns[uniqueId]?.get(type) ?: 0L
        if (readyAt > now) {
            plugin.text.bar(this, "ability-cooldown", tag("ability", abilityName), tag("seconds", seconds((readyAt - now) / 1000.0)))
            return false
        }

        val settings = plugin.settings.ability(type)
        val factor = powerFactor(quality, setPieces)
        when (type) {
            AbilityType.DASH -> {
                launchForward(settings.power * factor)
                fallGrace[uniqueId] = now + 3_000
            }
            AbilityType.LEAP -> {
                leap(settings.power * factor)
                fallGrace[uniqueId] = now + 6_000
            }
            AbilityType.SHOCKWAVE -> triggerShockwave(settings.radius * (1 + (factor - 1) / 2), settings.damage * factor)
            AbilityType.GUARD -> guard((settings.duration * factor).roundToInt(), settings.amplifier)
        }

        lastCast[uniqueId] = now
        cooldowns.computeIfAbsent(uniqueId) { ConcurrentHashMap() }[type] = now + cooldownMillis(type, quality)
        plugin.text.bar(this, "ability-used", tag("ability", abilityName))
        return true
    }

    fun armorPieces(player: Player, type: AbilityType): Int =
        player.inventory.armorContents.count { plugin.items.read(it)?.ability == type }

    fun consumeFallGrace(playerId: UUID): Boolean {
        val until = fallGrace.remove(playerId) ?: return false
        return until >= System.currentTimeMillis()
    }

    fun forget(playerId: UUID) {
        lastCast.remove(playerId)
        fallGrace.remove(playerId)
        val now = System.currentTimeMillis()
        cooldowns.computeIfPresent(playerId) { _, timers ->
            timers.values.removeIf { it <= now }
            timers.takeIf { it.isNotEmpty() }
        }
    }
}
