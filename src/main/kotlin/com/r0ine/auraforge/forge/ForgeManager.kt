package com.r0ine.auraforge.forge

import com.r0ine.auraforge.AuraForgePlugin
import com.r0ine.auraforge.core.ForgeRecord
import com.r0ine.auraforge.core.RegionDispatcher
import com.r0ine.auraforge.core.giveOrDrop
import com.r0ine.auraforge.core.rich
import com.r0ine.auraforge.core.runAt
import com.r0ine.auraforge.core.tag
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ForgeManager(private val plugin: AuraForgePlugin) {

    private data class ChunkRef(val world: String, val x: Int, val z: Int)

    private val forges = ConcurrentHashMap<UUID, Forge>()
    private val byPos = ConcurrentHashMap<BlockPos, Forge>()
    private val byChunk = ConcurrentHashMap<ChunkRef, MutableSet<Forge>>()
    private val animations = ConcurrentHashMap<UUID, Job>()

    private val Forge.chunk get() = ChunkRef(pos.world, pos.chunkX, pos.chunkZ)

    fun all(): Collection<Forge> = forges.values

    operator fun get(id: UUID): Forge? = forges[id]

    fun at(pos: BlockPos): Forge? = byPos[pos]

    fun ownedBy(owner: UUID): List<Forge> = forges.values.filter { it.owner == owner }

    fun inChunk(world: World, x: Int, z: Int): List<Forge> = byChunk[ChunkRef(world.name, x, z)]?.toList().orEmpty()

    fun canUse(player: Player, forge: Forge): Boolean =
        forge.owner == player.uniqueId || player.hasPermission("auraforge.admin")

    fun load(records: List<ForgeRecord>) {
        for (record in records) {
            val id = runCatching { UUID.fromString(record.id) }.getOrNull()
            val owner = runCatching { UUID.fromString(record.owner) }.getOrNull()
            if (id == null || owner == null) {
                plugin.logger.warning("Bozuk tezgah kaydı atlandı: ${record.id}")
                continue
            }
            val stacks = record.items.mapNotNull { encoded ->
                Forge.StoredStack.decode(encoded).also {
                    if (it == null) plugin.logger.warning("Tezgah ${record.id} içindeki bir eşya okunamadı, atlandı.")
                }
            }
            register(Forge(id, owner, record.ownerName, BlockPos(record.world, record.x, record.y, record.z), stacks, record.crafts))
        }
    }

    fun records(): List<ForgeRecord> = forges.values.map { it.record() }

    fun create(owner: Player, block: Block): Forge {
        val forge = Forge(UUID.randomUUID(), owner.uniqueId, owner.name, BlockPos.of(block), emptyList(), 0)
        register(forge)
        plugin.markDirty()
        show(forge)
        return forge
    }

    fun dismantle(forge: Forge): List<ItemStack> {
        forges.remove(forge.id)
        byPos.remove(forge.pos, forge)
        byChunk[forge.chunk]?.remove(forge)
        hide(forge)
        plugin.markDirty()
        return forge.clear()
    }

    private fun register(forge: Forge) {
        forges[forge.id] = forge
        byPos[forge.pos] = forge
        byChunk.computeIfAbsent(forge.chunk) { ConcurrentHashMap.newKeySet() }.add(forge)
    }

    fun showLoaded() {
        forges.values.groupBy { it.chunk }.forEach { (chunk, list) ->
            val world = Bukkit.getWorld(chunk.world) ?: return@forEach
            Bukkit.getRegionScheduler().execute(plugin, world, chunk.x, chunk.z) {
                if (world.isChunkLoaded(chunk.x, chunk.z)) list.forEach(::show)
            }
        }
    }

    fun show(forge: Forge) {
        val world = Bukkit.getWorld(forge.pos.world) ?: return
        val dispatcher = RegionDispatcher(plugin, world, forge.pos.chunkX, forge.pos.chunkZ)
        val job = plugin.scope.launch(dispatcher, CoroutineStart.LAZY) { animate(forge, world, dispatcher) }
        if (animations.putIfAbsent(forge.id, job) == null) {
            job.invokeOnCompletion { animations.remove(forge.id, job) }
            job.start()
        } else {
            job.cancel()
        }
    }

    fun hide(forge: Forge) {
        animations[forge.id]?.cancel()
    }

    private suspend fun animate(forge: Forge, world: World, dispatcher: RegionDispatcher) {
        if (forges[forge.id] !== forge || !world.isChunkLoaded(forge.pos.chunkX, forge.pos.chunkZ)) return

        val visuals = ForgeVisuals(plugin, forge, world)
        try {
            visuals.spawn()
            forge.visuals = visuals
            visuals.sync(forge.items(), label(forge))

            val center = forge.pos.center(world)
            var phase = 0.0
            var tick = 0L
            var nextScan = 0L
            var watched = true
            while (true) {
                val settings = plugin.settings.forge
                val interval = settings.animationInterval
                if (tick >= nextScan) {
                    watched = center.getNearbyPlayers(settings.viewDistance).isNotEmpty()
                    nextScan = tick + 20
                }
                if (watched) {
                    val heat = if (forge.busy) 1.0 else 0.0
                    phase += (0.07 + heat * 0.2) * interval
                    visuals.animate(phase, heat, interval.toInt())
                    if (tick % 40 < interval) visuals.ambient()
                }
                tick += interval
                dispatcher.sleep(interval)
            }
        } finally {
            visuals.remove()
            if (forge.visuals === visuals) forge.visuals = null
        }
    }

    fun refresh(forge: Forge) {
        val world = Bukkit.getWorld(forge.pos.world) ?: return
        plugin.runAt(forge.pos.center(world)) {
            forge.visuals?.sync(forge.items(), label(forge))
        }
    }

    fun effect(forge: Forge, action: ForgeVisuals.() -> Unit) {
        val world = Bukkit.getWorld(forge.pos.world) ?: return
        plugin.runAt(forge.pos.center(world)) {
            forge.visuals?.action()
        }
    }

    fun insert(player: Player, forge: Forge, item: ItemStack): Boolean {
        val text = plugin.text
        val max = plugin.settings.forge.maxItems
        when {
            forge.busy -> text.bar(player, "forge-busy")
            plugin.core.isCore(item) || plugin.items.read(item) != null -> text.bar(player, "forge-rejected")
            !forge.add(item, max) -> text.bar(player, "forge-full", tag("max", max.toString()))
            else -> {
                refresh(forge)
                plugin.markDirty()
                player.playSound(player.location, Sound.ENTITY_ITEM_FRAME_ADD_ITEM, 0.8f, 0.9f)
                text.bar(
                    player,
                    "forge-item-added",
                    tag("item", item.asOne().displayName()),
                    tag("count", forge.size.toString()),
                    tag("max", max.toString()),
                )
                return true
            }
        }
        return false
    }

    fun take(player: Player, forge: Forge, index: Int? = null): Boolean {
        if (forge.busy) {
            plugin.text.bar(player, "forge-busy")
            return false
        }
        val item = (if (index == null) forge.removeLast() else forge.removeAt(index)) ?: run {
            plugin.text.bar(player, "forge-empty")
            return false
        }
        player.giveOrDrop(item)
        refresh(forge)
        plugin.markDirty()
        player.playSound(player.location, Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.1f)
        plugin.text.bar(player, "forge-item-taken", tag("item", item.displayName()))
        return true
    }

    fun takeAll(player: Player, forge: Forge) {
        if (forge.busy) {
            plugin.text.bar(player, "forge-busy")
            return
        }
        val items = forge.clear()
        if (items.isEmpty()) {
            plugin.text.bar(player, "forge-empty")
            return
        }
        player.giveOrDrop(*items.toTypedArray())
        refresh(forge)
        plugin.markDirty()
        player.playSound(player.location, Sound.ENTITY_ITEM_PICKUP, 0.6f, 0.8f)
    }

    private fun label(forge: Forge): Component {
        val text = plugin.text
        val crafter = forge.crafter.get()?.let { Bukkit.getPlayer(it)?.name }
        val recipe = plugin.recipes.match(forge.materials())
        val status = when {
            crafter != null -> text.render("hologram-crafting", tag("player", crafter))
            recipe != null -> text.render("hologram-ready", rich("recipe", recipe.name))
            forge.size == 0 -> text.render("hologram-empty")
            else -> text.render("hologram-unknown")
        }
        return text.render(
            "hologram",
            tag("owner", forge.ownerName),
            tag("count", forge.size.toString()),
            tag("max", plugin.settings.forge.maxItems.toString()),
            tag("status", status),
        )
    }
}
