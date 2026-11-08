package com.r0ine.auraforge.ability

import com.r0ine.auraforge.AuraForgePlugin
import com.r0ine.auraforge.api.AbilityType
import com.r0ine.auraforge.core.isArmor
import com.r0ine.auraforge.core.noItalic
import com.r0ine.auraforge.core.rich
import com.r0ine.auraforge.core.seconds
import com.r0ine.auraforge.core.tag
import com.r0ine.auraforge.crafting.ForgeRecipe
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import kotlin.math.roundToInt

enum class Tier(val min: Int) {
    CRACKED(0),
    STURDY(40),
    FINE(70),
    LEGENDARY(90);

    val key: String get() = "tier-${name.lowercase()}"

    companion object {
        fun of(quality: Int): Tier = entries.last { quality >= it.min }
    }
}

data class ForgedItem(val ability: AbilityType, val quality: Int, val recipe: String)

class AbilityItems(private val plugin: AuraForgePlugin) {

    fun create(recipe: ForgeRecipe, quality: Int, smith: String): ItemStack {
        val text = plugin.text
        val keys = plugin.keys
        val tier = Tier.of(quality)
        val power = plugin.abilities.powerFactor(quality)
        val cooldown = plugin.abilities.cooldownMillis(recipe.ability, quality) / 1000.0
        val tierName = text.render(tier.key)

        return ItemStack(recipe.result).apply {
            editMeta { meta ->
                meta.displayName(text.item("item-name", rich("name", recipe.name), tag("tier", tierName)))
                meta.lore(
                    buildList {
                        if (recipe.description.isNotBlank()) add(text.parse(recipe.description).noItalic())
                        add(text.item("lore-ability", tag("ability", text.render("ability-${recipe.ability.key}"))))
                        add(text.item("lore-quality", tag("quality", quality.toString()), tag("tier", tierName)))
                        add(text.item("lore-power", tag("power", (power * 100).roundToInt().toString()), tag("cooldown", seconds(cooldown))))
                        add(text.item(if (recipe.result.isArmor()) "lore-use-armor" else "lore-use-weapon"))
                        add(text.item("lore-smith", tag("smith", smith)))
                    },
                )
                meta.setEnchantmentGlintOverride(tier == Tier.LEGENDARY)
                meta.persistentDataContainer.apply {
                    set(keys.ability, PersistentDataType.STRING, recipe.ability.key)
                    set(keys.quality, PersistentDataType.INTEGER, quality)
                    set(keys.recipe, PersistentDataType.STRING, recipe.id)
                    set(keys.smith, PersistentDataType.STRING, smith)
                    set(keys.forgedAt, PersistentDataType.LONG, System.currentTimeMillis())
                }
            }
        }
    }

    fun read(item: ItemStack?): ForgedItem? {
        if (item == null || item.isEmpty || !item.hasItemMeta()) return null
        val data = item.itemMeta.persistentDataContainer
        val ability = data.get(plugin.keys.ability, PersistentDataType.STRING)?.let(AbilityType::byKey) ?: return null
        return ForgedItem(
            ability = ability,
            quality = data.getOrDefault(plugin.keys.quality, PersistentDataType.INTEGER, 0),
            recipe = data.getOrDefault(plugin.keys.recipe, PersistentDataType.STRING, ""),
        )
    }
}
