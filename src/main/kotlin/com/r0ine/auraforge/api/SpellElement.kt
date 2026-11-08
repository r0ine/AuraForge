package com.r0ine.auraforge.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle

@Serializable
enum class SpellElement(
    val display: String,
    val rgb: Int,
    val particle: Particle,
    val rune: Material,
    val accent: Material,
) {
    @SerialName("fire")
    FIRE("Ateş", 0xFF5A3C, Particle.FLAME, Material.BLAZE_POWDER, Material.MAGMA_BLOCK),

    @SerialName("wind")
    WIND("Rüzgâr", 0xA8E6CF, Particle.CLOUD, Material.FEATHER, Material.WHITE_WOOL),

    @SerialName("ice")
    ICE("Buz", 0x9AD5FF, Particle.SNOWFLAKE, Material.BLUE_ICE, Material.PACKED_ICE),

    @SerialName("thunder")
    THUNDER("Şimşek", 0xFFF176, Particle.ELECTRIC_SPARK, Material.LIGHTNING_ROD, Material.YELLOW_WOOL),

    @SerialName("earth")
    EARTH("Toprak", 0xC38B5A, Particle.DUST_PLUME, Material.FLINT, Material.DEEPSLATE);

    val key: String get() = name.lowercase()
    val color: Color get() = Color.fromRGB(rgb)

    companion object {
        fun byKey(key: String): SpellElement? = entries.firstOrNull { it.key == key.lowercase() }
        fun byRune(material: Material): SpellElement? = entries.firstOrNull { it.rune == material }
    }
}
