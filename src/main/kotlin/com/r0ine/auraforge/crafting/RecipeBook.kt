package com.r0ine.auraforge.crafting

import com.r0ine.auraforge.api.AbilityType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import org.bukkit.Material
import java.util.logging.Logger

class ForgeRecipe(
    val id: String,
    val ability: AbilityType,
    val result: Material,
    val name: String,
    val description: String,
    val ingredients: Map<Material, Int>,
) {
    fun ingredientList(): Component = Component.join(
        JoinConfiguration.commas(true),
        ingredients.map { (material, count) ->
            Component.text("${count}x ").append(Component.translatable(material.translationKey()))
        },
    )
}

class RecipeBook {

    @Serializable
    private data class Entry(
        val ability: AbilityType,
        val result: String,
        val name: String = "",
        val description: String = "",
        val ingredients: Map<String, Int> = emptyMap(),
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Volatile
    private var recipes: Map<String, ForgeRecipe> = emptyMap()

    val all: Collection<ForgeRecipe> get() = recipes.values

    val ids: List<String> get() = recipes.keys.sorted()

    operator fun get(id: String): ForgeRecipe? = recipes[id.lowercase()]

    fun match(materials: Map<Material, Int>): ForgeRecipe? {
        if (materials.isEmpty()) return null
        return recipes.values.firstOrNull { it.ingredients == materials }
    }

    fun load(entries: Map<String, JsonElement>, maxItems: Int, logger: Logger) {
        val loaded = LinkedHashMap<String, ForgeRecipe>()
        for ((rawId, element) in entries) {
            val id = rawId.lowercase()
            val entry = runCatching { json.decodeFromJsonElement(Entry.serializer(), element) }.getOrElse {
                logger.warning("Tarif '$id' okunamadı: ${it.message}")
                continue
            }
            val result = Material.matchMaterial(entry.result)?.takeIf { it.isItem }
            if (result == null) {
                logger.warning("Tarif '$id': '${entry.result}' geçerli bir eşya değil.")
                continue
            }
            val ingredients = entry.ingredients.mapNotNull { (name, count) ->
                val material = Material.matchMaterial(name)?.takeIf { it.isItem && !it.isAir }
                if (material == null) logger.warning("Tarif '$id': '$name' geçerli bir malzeme değil.")
                material?.let { it to count.coerceAtLeast(1) }
            }.toMap()
            val total = ingredients.values.sum()
            when {
                ingredients.size != entry.ingredients.size -> continue
                ingredients.isEmpty() -> logger.warning("Tarif '$id' hiç malzeme içermiyor.")
                total > maxItems -> logger.warning("Tarif '$id' $total malzeme istiyor ama tezgah $maxItems alıyor.")
                loaded.values.any { it.ingredients == ingredients } ->
                    logger.warning("Tarif '$id' başka bir tarifle aynı malzemeleri kullanıyor, atlandı.")
                else -> loaded[id] = ForgeRecipe(id, entry.ability, result, entry.name.ifBlank { id }, entry.description, ingredients)
            }
        }
        recipes = loaded
    }
}
