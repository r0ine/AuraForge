package com.r0ine.auraforge.forge

import com.r0ine.auraforge.AuraForgePlugin
import com.r0ine.auraforge.core.tag
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Entity
import org.bukkit.entity.Interaction
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ForgeListener(private val plugin: AuraForgePlugin) : Listener {

    private val lastUse = ConcurrentHashMap<UUID, Long>()

    @EventHandler(priority = EventPriority.HIGH)
    fun onCoreUse(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        if (!plugin.core.isCore(event.item)) return
        event.isCancelled = true

        val hand = event.hand ?: return
        val clicked = event.clickedBlock ?: return
        val player = event.player
        val text = plugin.text

        if (!player.hasPermission("auraforge.place")) {
            text.send(player, "no-permission")
            return
        }

        val target = if (clicked.isReplaceable) clicked else clicked.getRelative(event.blockFace)
        if (!target.isReplaceable || plugin.forges.at(BlockPos.of(target)) != null) {
            text.bar(player, "forge-blocked")
            return
        }

        val limit = plugin.settings.forge.maxPerPlayer
        if (limit > 0 && !player.hasPermission("auraforge.admin") && plugin.forges.ownedBy(player.uniqueId).size >= limit) {
            text.bar(player, "forge-limit", tag("max", limit.toString()))
            return
        }

        val held = player.inventory.getItem(hand)
        val probe = BlockPlaceEvent(target, target.state, clicked, held, player, true, hand)
        if (!probe.callEvent() || !probe.canBuild()) {
            text.bar(player, "forge-protected")
            return
        }

        player.inventory.setItem(hand, if (held.amount > 1) held.apply { amount -= 1 } else null)
        plugin.forges.create(player, target)
        player.world.playSound(target.location, Sound.BLOCK_SMITHING_TABLE_USE, 1f, 0.8f)
        text.bar(player, "forge-placed")
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onForgeUse(event: PlayerInteractEntityEvent) {
        val forge = forgeOf(event.rightClicked) ?: return
        event.isCancelled = true
        if (event.hand != EquipmentSlot.HAND) return

        val player = event.player
        if (plugin.crafting.press(player)) return
        if (!plugin.forges.canUse(player, forge)) {
            plugin.text.bar(player, "forge-not-yours", tag("owner", forge.ownerName))
            return
        }
        if (!debounce(player)) return

        val hand = player.inventory.itemInMainHand
        when {
            !hand.isEmpty -> {
                if (plugin.forges.insert(player, forge, hand)) {
                    player.inventory.setItemInMainHand(if (hand.amount > 1) hand.apply { amount -= 1 } else null)
                }
            }
            player.isSneaking -> plugin.crafting.start(player, forge)
            else -> plugin.menus.open(player, forge)
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onForgeUseAt(event: PlayerInteractAtEntityEvent) {
        if (forgeOf(event.rightClicked) != null) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onForgeHit(event: PrePlayerAttackEntityEvent) {
        val forge = forgeOf(event.attacked) ?: return
        event.isCancelled = true
        val player = event.player
        if (!plugin.forges.canUse(player, forge) || !debounce(player)) return
        plugin.forges.take(player, forge)
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (plugin.forges.at(BlockPos.of(event.block)) != null) event.isCancelled = true
    }

    @EventHandler
    fun onChunkLoad(event: ChunkLoadEvent) {
        val chunk = event.chunk
        val forges = plugin.forges.inChunk(chunk.world, chunk.x, chunk.z)
        if (forges.isEmpty()) return
        Bukkit.getRegionScheduler().execute(plugin, chunk.world, chunk.x, chunk.z) {
            forges.forEach(plugin.forges::show)
        }
    }

    @EventHandler
    fun onChunkUnload(event: ChunkUnloadEvent) {
        val chunk = event.chunk
        plugin.forges.inChunk(chunk.world, chunk.x, chunk.z).forEach(plugin.forges::hide)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        lastUse.remove(event.player.uniqueId)
    }

    private fun forgeOf(entity: Entity): Forge? {
        if (entity !is Interaction) return null
        val raw = entity.persistentDataContainer.get(plugin.keys.forge, PersistentDataType.STRING) ?: return null
        val id = runCatching { UUID.fromString(raw) }.getOrNull() ?: return null
        return plugin.forges[id]
    }

    private fun debounce(player: Player): Boolean {
        val now = System.currentTimeMillis()
        val previous = lastUse.put(player.uniqueId, now) ?: 0L
        return now - previous > 150
    }
}
