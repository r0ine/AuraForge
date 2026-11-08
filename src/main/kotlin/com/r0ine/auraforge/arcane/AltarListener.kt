package com.r0ine.auraforge.arcane

import com.r0ine.auraforge.AuraForgePlugin
import com.r0ine.auraforge.core.tag
import com.r0ine.auraforge.forge.BlockPos
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Entity
import org.bukkit.entity.Interaction
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

class AltarListener(private val plugin: AuraForgePlugin) : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onCoreUse(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        if (!plugin.spellItems.isAltarCore(event.item)) return
        event.isCancelled = true

        val hand = event.hand ?: return
        val clicked = event.clickedBlock ?: return
        val player = event.player
        val text = plugin.text

        if (!player.hasPermission("auraforge.altar")) return text.send(player, "no-permission")

        val target = if (clicked.isReplaceable) clicked else clicked.getRelative(event.blockFace)
        val targetPos = BlockPos.of(target)
        if (!target.isReplaceable || plugin.forges.at(targetPos) != null || plugin.altars.at(targetPos) != null) {
            return text.bar(player, "altar-blocked")
        }

        val limit = plugin.settings.arcane.maxPerPlayer
        if (limit > 0 && !player.hasPermission("auraforge.admin") && plugin.altars.ownedBy(player.uniqueId).size >= limit) {
            return text.bar(player, "altar-limit", tag("max", limit.toString()))
        }

        val held = player.inventory.getItem(hand)
        val probe = BlockPlaceEvent(target, target.state, clicked, held, player, true, hand)
        if (!probe.callEvent() || !probe.canBuild()) return text.bar(player, "altar-protected")

        player.inventory.setItem(hand, if (held.amount > 1) held.apply { amount -= 1 } else null)
        plugin.altars.create(player, target)
        player.world.playSound(target.location, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.4f)
        text.bar(player, "altar-placed")
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onAltarUse(event: PlayerInteractEntityEvent) {
        val altar = altarOf(event.rightClicked) ?: return
        event.isCancelled = true
        if (event.hand != EquipmentSlot.HAND) return
        val player = event.player

        if (!plugin.altars.canUse(player, altar)) {
            plugin.text.bar(player, "altar-not-yours", tag("owner", altar.ownerName))
            return
        }
        if (altar.busy && altar.activeUser.get() != player.uniqueId) {
            plugin.text.bar(player, "altar-busy")
            return
        }
        plugin.altarMenus.open(player, altar)
    }

    @EventHandler(ignoreCancelled = true)
    fun onAltarUseAt(event: PlayerInteractAtEntityEvent) {
        if (altarOf(event.rightClicked) != null) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onAltarHit(event: PrePlayerAttackEntityEvent) {
        val altar = altarOf(event.attacked) ?: return
        event.isCancelled = true
        val player = event.player
        if (!plugin.altars.canUse(player, altar)) return
        plugin.altarMenus.open(player, altar)
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (plugin.altars.at(BlockPos.of(event.block)) != null) event.isCancelled = true
    }

    @EventHandler
    fun onChunkLoad(event: ChunkLoadEvent) {
        val chunk = event.chunk
        val list = plugin.altars.inChunk(chunk.world, chunk.x, chunk.z)
        if (list.isEmpty()) return
        Bukkit.getRegionScheduler().execute(plugin, chunk.world, chunk.x, chunk.z) {
            list.forEach(plugin.altars::show)
        }
    }

    @EventHandler
    fun onChunkUnload(event: ChunkUnloadEvent) {
        val chunk = event.chunk
        plugin.altars.inChunk(chunk.world, chunk.x, chunk.z).forEach(plugin.altars::hide)
    }

    private fun altarOf(entity: Entity): Altar? {
        if (entity !is Interaction) return null
        val raw = entity.persistentDataContainer.get(plugin.keys.altar, PersistentDataType.STRING) ?: return null
        val id = runCatching { UUID.fromString(raw) }.getOrNull() ?: return null
        return plugin.altars[id]
    }
}
