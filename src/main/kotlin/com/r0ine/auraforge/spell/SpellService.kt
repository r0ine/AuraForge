package com.r0ine.auraforge.spell

import com.r0ine.auraforge.AuraForgePlugin
import com.r0ine.auraforge.core.rich
import com.r0ine.auraforge.core.seconds
import com.r0ine.auraforge.core.tag
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SpellService(private val plugin: AuraForgePlugin) {

    val book: SpellBook = SpellBook()

    private val cooldowns = ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>>()
    private val lastCast = ConcurrentHashMap<UUID, Long>()

    operator fun get(id: String): Spell? = book[id]

    fun cast(player: Player, scroll: ScrollData): Boolean {
        val text = plugin.text
        val now = System.currentTimeMillis()
        if (now - (lastCast[player.uniqueId] ?: 0L) < 300) return false

        val readyAt = cooldowns[player.uniqueId]?.get(scroll.spell.id) ?: 0L
        if (readyAt > now) {
            text.bar(
                player,
                "spell-cooldown",
                rich("name", scroll.spell.name),
                tag("seconds", seconds((readyAt - now) / 1000.0)),
            )
            return false
        }

        val power = plugin.spellItems.powerFactor(scroll.power)
        val tuned = scroll.spell.copy(
            power = scroll.spell.power * power,
            radius = scroll.spell.radius * (1.0 + (power - 1.0) * 0.4),
            range = scroll.spell.range * (1.0 + (power - 1.0) * 0.25),
            duration = (scroll.spell.duration * (1.0 + (power - 1.0) * 0.2)).toInt(),
        )
        plugin.spellEffects.launch(player, tuned)

        val cooldownMs = (scroll.spell.cooldown * 1000 * plugin.spellItems.cooldownMultiplier(scroll.power)).toLong()
        cooldowns.computeIfAbsent(player.uniqueId) { ConcurrentHashMap() }[scroll.spell.id] = now + cooldownMs
        lastCast[player.uniqueId] = now
        text.bar(player, "spell-cast", rich("name", scroll.spell.name))
        return true
    }

    fun forget(playerId: UUID) {
        lastCast.remove(playerId)
        val now = System.currentTimeMillis()
        cooldowns.computeIfPresent(playerId) { _, timers ->
            timers.values.removeIf { it <= now }
            timers.takeIf { it.isNotEmpty() }
        }
    }
}
