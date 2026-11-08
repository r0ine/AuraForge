package com.r0ine.auraforge.api

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack

/**
 * Dövme bitip sonuç eşyası oyuncuya verilmeden hemen önce, oyuncunun thread'inde çağrılır.
 * İptal edilirse malzemeler tezgahta kalır; [result] değiştirilerek farklı bir eşya verilebilir.
 */
class ForgeCraftEvent(
    player: Player,
    val recipeId: String,
    val ability: AbilityType,
    val quality: Int,
    var result: ItemStack,
) : PlayerEvent(player), Cancellable {

    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled

    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
