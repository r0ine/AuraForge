package com.r0ine.auraforge.spell

import com.r0ine.auraforge.api.SpellElement
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.bukkit.Material
import java.util.logging.Logger

data class Spell(
    val id: String,
    val name: String,
    val description: String,
    val elements: List<SpellElement>,
    val effect: String,
    val cooldown: Double,
    val power: Double,
    val radius: Double,
    val range: Double,
    val duration: Int,
    val result: Material,
) {
    fun matches(a: SpellElement, b: SpellElement): Boolean {
        if (elements.size != 2) return false
        return (elements[0] == a && elements[1] == b) || (elements[0] == b && elements[1] == a)
    }
}

class SpellBook {

    @Serializable
    private data class Entry(
        val name: String = "",
        val description: String = "",
        val elements: List<SpellElement> = emptyList(),
        val effect: String = "",
        val cooldown: Double = 10.0,
        val power: Double = 4.0,
        val radius: Double = 5.0,
        val range: Double = 10.0,
        val duration: Int = 60,
        val result: String = "PAPER",
        @SerialName("combo") val combo: String = "",
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Volatile
    private var spellsById: Map<String, Spell> = emptyMap()

    val all: Collection<Spell> get() = spellsById.values
    val ids: List<String> get() = spellsById.keys.sorted()

    operator fun get(id: String): Spell? = spellsById[id.lowercase()]

    fun combine(a: SpellElement, b: SpellElement): Spell? =
        spellsById.values.firstOrNull { it.matches(a, b) }

    fun load(entries: Map<String, JsonElement>, logger: Logger) {
        val loaded = LinkedHashMap<String, Spell>()
        for ((rawId, element) in entries) {
            val id = rawId.lowercase()
            val entry = runCatching { json.decodeFromJsonElement(Entry.serializer(), element) }.getOrElse {
                logger.warning("Büyü '$id' okunamadı: ${it.message}")
                continue
            }
            val elements = entry.elements.ifEmpty {
                entry.combo.split("+", "-", ",", " ").mapNotNull { SpellElement.byKey(it.trim()) }
            }
            if (elements.isEmpty()) {
                logger.warning("Büyü '$id' hiçbir elementle eşleşmiyor.")
                continue
            }
            val material = Material.matchMaterial(entry.result)?.takeIf { it.isItem } ?: Material.PAPER
            loaded[id] = Spell(
                id = id,
                name = entry.name.ifBlank { id },
                description = entry.description,
                elements = elements,
                effect = entry.effect.ifBlank { id },
                cooldown = entry.cooldown.coerceAtLeast(0.5),
                power = entry.power.coerceAtLeast(0.1),
                radius = entry.radius.coerceIn(1.0, 32.0),
                range = entry.range.coerceIn(1.0, 60.0),
                duration = entry.duration.coerceIn(5, 600),
                result = material,
            )
        }
        spellsById = loaded
    }
}
