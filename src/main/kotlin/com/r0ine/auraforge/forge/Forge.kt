package com.r0ine.auraforge.forge

import com.r0ine.auraforge.core.ForgeRecord
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.inventory.ItemStack
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

data class BlockPos(val world: String, val x: Int, val y: Int, val z: Int) {
    val chunkX: Int get() = x shr 4
    val chunkZ: Int get() = z shr 4

    fun corner(world: World) = Location(world, x.toDouble(), y.toDouble(), z.toDouble())

    fun center(world: World) = Location(world, x + 0.5, y.toDouble(), z + 0.5)

    companion object {
        fun of(block: Block) = BlockPos(block.world.name, block.x, block.y, block.z)
    }
}

/**
 * Tezgahın verisi. İçerik kendi kilidiyle korunur, bu yüzden hangi bölge thread'inden
 * okunduğu önemli değil. Dünyadaki görüntüsü [ForgeVisuals] üzerinden ayrıca yönetilir.
 */
class Forge(
    val id: UUID,
    val owner: UUID,
    ownerName: String,
    val pos: BlockPos,
    stacks: List<StoredStack>,
    crafts: Int,
) {
    class StoredStack(val item: ItemStack, val encoded: String) {
        companion object {
            fun of(item: ItemStack): StoredStack {
                val single = item.asOne()
                return StoredStack(single, Base64.getEncoder().encodeToString(single.serializeAsBytes()))
            }

            fun decode(encoded: String): StoredStack? = runCatching {
                StoredStack(ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded)), encoded)
            }.getOrNull()
        }
    }

    private val lock = Any()
    private var stacks = stacks

    @Volatile
    var ownerName = ownerName

    @Volatile
    var crafts = crafts
        private set

    val crafter = AtomicReference<UUID?>(null)

    @Volatile
    internal var visuals: ForgeVisuals? = null

    val busy: Boolean get() = crafter.get() != null

    val size: Int get() = synchronized(lock) { stacks.size }

    fun items(): List<ItemStack> = synchronized(lock) { stacks.map { it.item.clone() } }

    fun materials(): Map<Material, Int> = synchronized(lock) { stacks.groupingBy { it.item.type }.eachCount() }

    fun add(item: ItemStack, limit: Int): Boolean = synchronized(lock) {
        if (stacks.size >= limit) return false
        stacks = stacks + StoredStack.of(item)
        true
    }

    fun removeAt(index: Int): ItemStack? = synchronized(lock) {
        val removed = stacks.getOrNull(index) ?: return null
        stacks = stacks.filterIndexed { i, _ -> i != index }
        removed.item.clone()
    }

    fun removeLast(): ItemStack? = synchronized(lock) { removeAt(stacks.lastIndex) }

    fun clear(): List<ItemStack> = synchronized(lock) {
        val taken = stacks.map { it.item.clone() }
        stacks = emptyList()
        taken
    }

    fun consume(ingredients: Map<Material, Int>): Boolean = synchronized(lock) {
        if (stacks.groupingBy { it.item.type }.eachCount() != ingredients) return false
        stacks = emptyList()
        crafts++
        true
    }

    fun record(): ForgeRecord = synchronized(lock) {
        ForgeRecord(
            id = id.toString(),
            owner = owner.toString(),
            ownerName = ownerName,
            world = pos.world,
            x = pos.x,
            y = pos.y,
            z = pos.z,
            items = stacks.map { it.encoded },
            crafts = crafts,
        )
    }
}
