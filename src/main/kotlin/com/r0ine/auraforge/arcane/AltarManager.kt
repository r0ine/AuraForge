package com.r0ine.auraforge.arcane

import com.r0ine.auraforge.AuraForgePlugin
import com.r0ine.auraforge.core.AltarRecord
import com.r0ine.auraforge.core.RegionDispatcher
import com.r0ine.auraforge.core.rich
import com.r0ine.auraforge.core.runAt
import com.r0ine.auraforge.core.tag
import com.r0ine.auraforge.forge.BlockPos
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AltarManager(private val plugin: AuraForgePlugin) {

    private data class ChunkRef(val world: String, val x: Int, val z: Int)

    private val altars = ConcurrentHashMap<UUID, Altar>()
    private val byPos = ConcurrentHashMap<BlockPos, Altar>()
    private val byChunk = ConcurrentHashMap<ChunkRef, MutableSet<Altar>>()
    private val animations = ConcurrentHashMap<UUID, Job>()

    private val Altar.chunk get() = ChunkRef(pos.world, pos.chunkX, pos.chunkZ)

    fun all(): Collection<Altar> = altars.values
    operator fun get(id: UUID): Altar? = altars[id]
    fun at(pos: BlockPos): Altar? = byPos[pos]
    fun ownedBy(owner: UUID): List<Altar> = altars.values.filter { it.owner == owner }
    fun inChunk(world: World, x: Int, z: Int): List<Altar> = byChunk[ChunkRef(world.name, x, z)]?.toList().orEmpty()

    fun canUse(player: Player, altar: Altar): Boolean =
        altar.owner == player.uniqueId || player.hasPermission("auraforge.admin")

    fun load(records: List<AltarRecord>) {
        for (record in records) {
            val id = runCatching { UUID.fromString(record.id) }.getOrNull()
            val owner = runCatching { UUID.fromString(record.owner) }.getOrNull()
            if (id == null || owner == null) {
                plugin.logger.warning("Bozuk masa kaydı atlandı: ${record.id}")
                continue
            }
            register(Altar(id, owner, record.ownerName, BlockPos(record.world, record.x, record.y, record.z), record.combines))
        }
    }

    fun records(): List<AltarRecord> = altars.values.map { it.record() }

    fun create(owner: Player, block: Block): Altar {
        val altar = Altar(UUID.randomUUID(), owner.uniqueId, owner.name, BlockPos.of(block), 0)
        register(altar)
        plugin.markDirty()
        show(altar)
        return altar
    }

    fun dismantle(altar: Altar) {
        altars.remove(altar.id)
        byPos.remove(altar.pos, altar)
        byChunk[altar.chunk]?.remove(altar)
        hide(altar)
        plugin.markDirty()
    }

    private fun register(altar: Altar) {
        altars[altar.id] = altar
        byPos[altar.pos] = altar
        byChunk.computeIfAbsent(altar.chunk) { ConcurrentHashMap.newKeySet() }.add(altar)
    }

    fun showLoaded() {
        altars.values.groupBy { it.chunk }.forEach { (chunk, list) ->
            val world = Bukkit.getWorld(chunk.world) ?: return@forEach
            Bukkit.getRegionScheduler().execute(plugin, world, chunk.x, chunk.z) {
                if (world.isChunkLoaded(chunk.x, chunk.z)) list.forEach(::show)
            }
        }
    }

    fun show(altar: Altar) {
        val world = Bukkit.getWorld(altar.pos.world) ?: return
        val dispatcher = RegionDispatcher(plugin, world, altar.pos.chunkX, altar.pos.chunkZ)
        val job = plugin.scope.launch(dispatcher, CoroutineStart.LAZY) { animate(altar, world, dispatcher) }
        if (animations.putIfAbsent(altar.id, job) == null) {
            job.invokeOnCompletion { animations.remove(altar.id, job) }
            job.start()
        } else {
            job.cancel()
        }
    }

    fun hide(altar: Altar) {
        animations[altar.id]?.cancel()
    }

    private suspend fun animate(altar: Altar, world: World, dispatcher: RegionDispatcher) {
        if (altars[altar.id] !== altar || !world.isChunkLoaded(altar.pos.chunkX, altar.pos.chunkZ)) return
        val visuals = AltarVisuals(plugin, altar, world)
        try {
            visuals.spawn()
            altar.visuals = visuals
            visuals.sync(null, null, label(altar, null, null, null))

            val center = altar.pos.center(world)
            var phase = 0.0
            var tick = 0L
            var nextScan = 0L
            var watched = true
            while (true) {
                val interval = plugin.settings.arcane.animationInterval.toInt()
                if (tick >= nextScan) {
                    watched = center.getNearbyPlayers(plugin.settings.forge.viewDistance).isNotEmpty()
                    nextScan = tick + 20
                }
                if (watched) {
                    phase += 0.1 * interval
                    visuals.animate(phase, interval, altar.busy)
                    if (tick % 30 < interval) visuals.ambient()
                }
                tick += interval
                dispatcher.sleep(interval.toLong())
            }
        } finally {
            visuals.remove()
            if (altar.visuals === visuals) altar.visuals = null
        }
    }

    fun refresh(altar: Altar, rune1: com.r0ine.auraforge.api.SpellElement? = null, rune2: com.r0ine.auraforge.api.SpellElement? = null, preview: com.r0ine.auraforge.spell.Spell? = null) {
        val world = Bukkit.getWorld(altar.pos.world) ?: return
        plugin.runAt(altar.pos.center(world)) {
            altar.visuals?.sync(rune1, rune2, label(altar, rune1, rune2, preview))
        }
    }

    fun effect(altar: Altar, action: AltarVisuals.() -> Unit) {
        val world = Bukkit.getWorld(altar.pos.world) ?: return
        plugin.runAt(altar.pos.center(world)) {
            altar.visuals?.action()
        }
    }

    private fun label(
        altar: Altar,
        rune1: com.r0ine.auraforge.api.SpellElement?,
        rune2: com.r0ine.auraforge.api.SpellElement?,
        preview: com.r0ine.auraforge.spell.Spell?,
    ): Component {
        val text = plugin.text
        val user = altar.activeUser.get()?.let { Bukkit.getPlayer(it)?.name }
        val status = when {
            user != null -> text.render("altar-hologram-active", tag("player", user))
            preview != null -> text.render("altar-hologram-ready", rich("spell", preview.name))
            rune1 != null && rune2 != null -> text.render("altar-hologram-unknown")
            rune1 != null || rune2 != null -> text.render("altar-hologram-half")
            else -> text.render("altar-hologram-empty")
        }
        return text.render(
            "altar-hologram",
            tag("owner", altar.ownerName),
            tag("combines", altar.combines.toString()),
            tag("status", status),
        )
    }
}
