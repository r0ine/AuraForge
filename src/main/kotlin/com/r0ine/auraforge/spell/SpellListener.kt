package com.r0ine.auraforge.spell

import com.r0ine.auraforge.AuraForgePlugin
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent

class SpellListener(private val plugin: AuraForgePlugin) : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onUseScroll(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        if (event.useItemInHand() == Event.Result.DENY) return
        val item = event.item ?: return
        val scroll = plugin.spellItems.readScroll(item) ?: return
        val player = event.player
        if (!player.hasPermission("auraforge.cast")) return

        if (plugin.spells.cast(player, scroll)) {
            event.setUseItemInHand(Event.Result.DENY)
            event.setUseInteractedBlock(Event.Result.DENY)
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        plugin.spells.forget(event.player.uniqueId)
    }
}
