package com.r0ine.auraforge.core

import com.r0ine.auraforge.api.AbilityType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

@Serializable
data class Settings(
    val forge: ForgeSettings = ForgeSettings(),
    val crafting: CraftingSettings = CraftingSettings(),
    val quality: QualitySettings = QualitySettings(),
    val abilities: Map<AbilityType, AbilitySettings> = emptyMap(),
    @SerialName("set-bonus") val setBonus: SetBonusSettings = SetBonusSettings(),
    @SerialName("autosave-seconds") val autosaveSeconds: Int = 30,
    val recipes: Map<String, JsonElement> = emptyMap(),
    val messages: Map<String, JsonElement> = emptyMap(),
) {
    fun ability(type: AbilityType): AbilitySettings = abilities[type] ?: AbilitySettings()

    fun normalized(): Settings {
        val zone = crafting.zoneWidth.coerceIn(0.05, 0.45)
        return copy(
            forge = forge.copy(
                maxItems = forge.maxItems.coerceIn(1, 7),
                maxPerPlayer = forge.maxPerPlayer.coerceAtLeast(0),
                animationInterval = forge.animationInterval.coerceIn(1, 10),
                viewDistance = forge.viewDistance.coerceIn(8.0, 128.0),
            ),
            crafting = crafting.copy(
                rounds = crafting.rounds.coerceIn(1, 6),
                sweepMs = crafting.sweepMs.coerceIn(400, 6000),
                speedUp = crafting.speedUp.coerceIn(0.5, 1.0),
                zoneWidth = zone,
                perfectWidth = crafting.perfectWidth.coerceIn(0.01, zone),
                barLength = crafting.barLength.coerceIn(12, 60),
                pauseTicks = crafting.pauseTicks.coerceIn(2, 60),
            ),
            quality = quality.copy(
                powerMin = quality.powerMin.coerceIn(0.1, 5.0),
                powerMax = quality.powerMax.coerceIn(quality.powerMin, 5.0),
                cooldownReduction = quality.cooldownReduction.coerceIn(0.0, 0.9),
            ),
            setBonus = setBonus.copy(minPieces = setBonus.minPieces.coerceIn(1, 4)),
            autosaveSeconds = autosaveSeconds.coerceIn(10, 3600),
        )
    }
}

@Serializable
data class ForgeSettings(
    @SerialName("core-item") val coreItem: String = "NETHER_STAR",
    @SerialName("core-recipe") val coreRecipe: Boolean = true,
    val block: String = "SMITHING_TABLE",
    @SerialName("max-items") val maxItems: Int = 6,
    @SerialName("max-per-player") val maxPerPlayer: Int = 3,
    @SerialName("hologram-height") val hologramHeight: Double = 2.1,
    @SerialName("animation-interval") val animationInterval: Long = 2,
    @SerialName("view-distance") val viewDistance: Double = 32.0,
) {
    fun coreMaterial(): Material = Material.matchMaterial(coreItem)?.takeIf { it.isItem } ?: Material.NETHER_STAR

    fun blockData(): BlockData =
        (Material.matchMaterial(block)?.takeIf { it.isBlock } ?: Material.SMITHING_TABLE).createBlockData()
}

@Serializable
data class CraftingSettings(
    val rounds: Int = 3,
    @SerialName("sweep-ms") val sweepMs: Long = 1600,
    @SerialName("speed-up") val speedUp: Double = 0.8,
    @SerialName("zone-width") val zoneWidth: Double = 0.2,
    @SerialName("perfect-width") val perfectWidth: Double = 0.06,
    @SerialName("bar-length") val barLength: Int = 32,
    @SerialName("pause-ticks") val pauseTicks: Long = 14,
)

@Serializable
data class QualitySettings(
    @SerialName("power-min") val powerMin: Double = 0.8,
    @SerialName("power-max") val powerMax: Double = 1.35,
    @SerialName("cooldown-reduction") val cooldownReduction: Double = 0.3,
)

@Serializable
data class AbilitySettings(
    val cooldown: Double = 5.0,
    val power: Double = 1.0,
    val radius: Double = 4.0,
    val damage: Double = 4.0,
    val duration: Int = 80,
    val amplifier: Int = 0,
)

@Serializable
data class SetBonusSettings(
    @SerialName("min-pieces") val minPieces: Int = 2,
    @SerialName("per-piece") val perPiece: Double = 0.06,
)

object SettingsLoader {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun load(plugin: JavaPlugin): Settings {
        val file = File(plugin.dataFolder, "config.yml")
        if (!file.exists()) plugin.saveResource("config.yml", false)

        val user = YamlConfiguration.loadConfiguration(file)
        val bundled = plugin.getResource("config.yml")
            ?.reader(Charsets.UTF_8)
            ?.use { YamlConfiguration.loadConfiguration(it) }

        val loaded = json.decodeFromJsonElement(Settings.serializer(), user.toJson())
        val defaults = bundled?.let { json.decodeFromJsonElement(Settings.serializer(), it.toJson()) } ?: Settings()

        return loaded.copy(
            abilities = defaults.abilities + loaded.abilities,
            recipes = if (user.contains("recipes")) loaded.recipes else defaults.recipes,
            messages = defaults.messages + loaded.messages,
        ).normalized()
    }

    private fun Any?.toJson(): JsonElement = when (this) {
        null -> JsonNull
        is ConfigurationSection -> JsonObject(getKeys(false).associateWith { get(it).toJson() })
        is Map<*, *> -> JsonObject(entries.associate { (key, value) -> key.toString() to value.toJson() })
        is List<*> -> JsonArray(map { it.toJson() })
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        else -> JsonPrimitive(toString())
    }
}
