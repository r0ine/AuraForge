package com.r0ine.auraforge.crafting

import com.r0ine.auraforge.core.SmithRecord
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

class SmithStats {

    private val records = ConcurrentHashMap<UUID, SmithRecord>()

    fun load(saved: Map<String, SmithRecord>) {
        saved.forEach { (key, record) ->
            runCatching { UUID.fromString(key) }.getOrNull()?.let { records[it] = record }
        }
    }

    fun snapshot(): Map<String, SmithRecord> = records.entries.associate { it.key.toString() to it.value }

    operator fun get(id: UUID): SmithRecord? = records[id]

    fun find(name: String): SmithRecord? = records.values.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun record(id: UUID, name: String, quality: Int, perfectHits: Int) {
        records.compute(id) { _, old ->
            val base = old ?: SmithRecord(name)
            base.copy(
                name = name,
                crafted = base.crafted + 1,
                perfect = base.perfect + perfectHits,
                best = max(base.best, quality),
            )
        }
    }
}
