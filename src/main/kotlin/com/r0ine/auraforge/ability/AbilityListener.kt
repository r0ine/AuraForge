package com.r0ine.auraforge.ability

import com.destroystokyo.paper.event.player.PlayerJumpEvent
import com.r0ine.auraforge.AuraForgePlugin
import com.r0ine.auraforge.core.isArmor
import com.r0ine.auraforge.progress.AbilityBoost
import com.r0ine.auraforge.progress.Talent
import org.bukkit.Sound
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import kotlin.random.Random
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import kotlin.math.roundToInt

class AbilityListener(private val plugin: AuraForgePlugin) : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onUse(event: PlayerInteractEvent) {
        if (event.hand != org.bukkit.inventory.EquipmentSlot.HAND) return
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        if (event.useItemInHand() == Event.Result.DENY) return
        val item = event.item ?: return
        if (item.type.isArmor()) return
        val forged = plugin.items.read(item) ?: return
        val player = event.player
        if (plugin.crafting.isCrafting(player)) return
        if (player.isSneaking && player.hasPermission("auraforge.use") && plugin.adventureMagic.cast(player)) {
            event.setUseItemInHand(Event.Result.DENY)
            event.setUseInteractedBlock(Event.Result.DENY)
            return
        }

        val pieces = plugin.abilities.armorPieces(player, forged.ability)
        val progress = plugin.items.progress(item)
        val boost = plugin.progress.abilityBoost(progress)
        val cast = with(plugin.abilities) { player.castAbility(forged.ability, forged.quality, pieces, boost) }
        if (!cast) return
        if (item.type != Material.SHIELD) event.setUseItemInHand(Event.Result.DENY)
        if (progress == null) return

        if (progress.has(Talent.ECHO) && Random.nextDouble() < progress.value(Talent.ECHO)) {
            plugin.abilities.resetCooldown(player.uniqueId, forged.ability)
            plugin.text.bar(player, "proc-echo")
            player.playSound(player.location, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 1.6f)
        }
        if (progress.has(Talent.STORM_STEP)) {
            player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 60, 1, false, false, true))
            player.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, 60, 0, false, false, true))
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onJump(event: PlayerJumpEvent) {
        val player = event.player
        if (!player.isSneaking || plugin.crafting.isCrafting(player)) return

        val pieces = player.inventory.armorContents.mapNotNull { plugin.items.read(it) }
        if (pieces.isEmpty()) return
        val set = pieces.groupBy { it.ability }
            .values
            .maxWith(compareBy({ it.size }, { group -> group.maxOf { it.quality } }))
        val quality = set.map { it.quality }.average().roundToInt()
        val plus = player.inventory.armorContents
            .filter { plugin.items.read(it)?.ability == set.first().ability }
            .sumOf { plugin.items.progress(it)?.plus ?: 0 }
        val boost = AbilityBoost(1.0 + plus * plugin.settings.enhance.powerPerPlus / set.size, 1.0)
        with(plugin.abilities) { player.castAbility(set.first().ability, quality, set.size, boost) }
    }

    @EventHandler(ignoreCancelled = true)
    fun onFall(event: EntityDamageEvent) {
        if (event.cause != EntityDamageEvent.DamageCause.FALL) return
        val player = event.entity as? Player ?: return
        if (plugin.abilities.consumeFallGrace(player.uniqueId)) event.isCancelled = true
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        plugin.abilities.forget(event.player.uniqueId)
        plugin.progress.forget(event.player.uniqueId)
    }
}
