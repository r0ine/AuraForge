package com.r0ine.auraforge.progress

import com.r0ine.auraforge.api.SpellElement
import com.r0ine.auraforge.core.Keys
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType

data class Progress(
    val level: Int = 1,
    val xp: Int = 0,
    val plus: Int = 0,
    val element: SpellElement? = null,
    val talents: Map<Talent, Int> = emptyMap(),
) {
    val spent: Int get() = talents.values.sum()
    val points: Int get() = (level - 1 - spent).coerceAtLeast(0)

    fun rank(talent: Talent): Int = talents[talent] ?: 0

    fun spentIn(branch: Branch): Int = talents.filterKeys { it.branch == branch }.values.sum()

    fun has(talent: Talent): Boolean = rank(talent) > 0

    fun value(talent: Talent): Double = talent.value(rank(talent))

    fun lockReason(talent: Talent): Lock? {
        if (rank(talent) >= talent.maxRank) return Lock.MAXED
        talent.rival()?.let { if (has(it)) return Lock.RIVAL }
        if (talent.row > 0) {
            if (Talent.inRow(talent.branch, talent.row - 1).none { has(it) }) return Lock.PARENT
            if (spentIn(talent.branch) < talent.row * 2) return Lock.DEPTH
        }
        if (points <= 0) return Lock.POINTS
        return null
    }

    fun write(meta: ItemMeta, keys: Keys) {
        meta.persistentDataContainer.apply {
            set(keys.level, PersistentDataType.INTEGER, level)
            set(keys.xp, PersistentDataType.INTEGER, xp)
            set(keys.plus, PersistentDataType.INTEGER, plus)
            if (element == null) remove(keys.socket) else set(keys.socket, PersistentDataType.STRING, element.key)
            set(keys.talents, PersistentDataType.STRING, talents.entries.joinToString(",") { "${it.key.key}:${it.value}" })
        }
    }

    enum class Lock { MAXED, RIVAL, PARENT, DEPTH, POINTS }

    companion object {
        fun read(meta: ItemMeta, keys: Keys): Progress {
            val data = meta.persistentDataContainer
            val talents = data.get(keys.talents, PersistentDataType.STRING).orEmpty()
                .split(',')
                .mapNotNull { entry ->
                    val (key, rank) = entry.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                    val talent = Talent.byKey(key) ?: return@mapNotNull null
                    rank.toIntOrNull()?.coerceIn(1, talent.maxRank)?.let { talent to it }
                }
                .toMap()
            return Progress(
                level = data.getOrDefault(keys.level, PersistentDataType.INTEGER, 1).coerceAtLeast(1),
                xp = data.getOrDefault(keys.xp, PersistentDataType.INTEGER, 0).coerceAtLeast(0),
                plus = data.getOrDefault(keys.plus, PersistentDataType.INTEGER, 0).coerceAtLeast(0),
                element = data.get(keys.socket, PersistentDataType.STRING)?.let(SpellElement::byKey),
                talents = talents,
            )
        }
    }
}
