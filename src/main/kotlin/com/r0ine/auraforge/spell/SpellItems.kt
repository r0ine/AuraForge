package com.r0ine.auraforge.spell

import com.r0ine.auraforge.AuraForgePlugin
import com.r0ine.auraforge.api.SpellElement
import com.r0ine.auraforge.core.noItalic
import com.r0ine.auraforge.core.rich
import com.r0ine.auraforge.core.seconds
import com.r0ine.auraforge.core.tag
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.persistence.PersistentDataType
import kotlin.math.roundToInt

data class ScrollData(val spell: Spell, val power: Int)

class SpellItems(private val plugin: AuraForgePlugin) {

    fun createRune(element: SpellElement, amount: Int = 1): ItemStack =
        ItemStack(element.rune, amount).apply {
            editMeta { meta ->
                meta.displayName(plugin.text.item("rune-name", tag("element", plugin.text.render("element-${element.key}"))))
                meta.lore(plugin.text.lines("rune-lore", tag("element", plugin.text.render("element-${element.key}"))))
                meta.setEnchantmentGlintOverride(true)
                meta.persistentDataContainer.set(plugin.keys.rune, PersistentDataType.STRING, element.key)
                meta.persistentDataContainer.set(plugin.keys.element, PersistentDataType.STRING, element.key)
            }
        }

    fun isRune(item: ItemStack?): Boolean = readRune(item) != null

    fun readRune(item: ItemStack?): SpellElement? {
        if (item == null || item.isEmpty || !item.hasItemMeta()) return null
        val raw = item.itemMeta.persistentDataContainer.get(plugin.keys.rune, PersistentDataType.STRING) ?: return null
        return SpellElement.byKey(raw)
    }

    fun createScroll(spell: Spell, power: Int, author: String): ItemStack {
        val text = plugin.text
        val keys = plugin.keys
        val tier = scrollTier(power)
        val elementLine = spell.elements.joinToString(" <dark_gray>+</dark_gray> ") {
            "<color:#${String.format("%06X", it.rgb)}>${it.display}</color>"
        }
        return ItemStack(spell.result).apply {
            editMeta { meta ->
                meta.displayName(text.item("scroll-name", rich("name", spell.name), tag("tier", text.render(tier))))
                val lore = buildList {
                    if (spell.description.isNotBlank()) add(text.parse(spell.description).noItalic())
                    add(text.item("scroll-lore-elements", rich("elements", elementLine)))
                    add(text.item("scroll-lore-power", tag("power", (power * 10).toString())))
                    add(text.item("scroll-lore-cooldown", tag("cooldown", seconds(spell.cooldown * cooldownMultiplier(power)))))
                    if (author.isNotBlank()) add(text.item("scroll-lore-author", tag("author", author)))
                    add(text.item("scroll-lore-use"))
                }
                meta.lore(lore)
                meta.setEnchantmentGlintOverride(power >= 8)
                meta.persistentDataContainer.apply {
                    set(keys.spell, PersistentDataType.STRING, spell.id)
                    set(keys.scrollPower, PersistentDataType.INTEGER, power)
                }
            }
        }
    }

    fun readScroll(item: ItemStack?): ScrollData? {
        if (item == null || item.isEmpty || !item.hasItemMeta()) return null
        val data = item.itemMeta.persistentDataContainer
        val id = data.get(plugin.keys.spell, PersistentDataType.STRING) ?: return null
        val spell = plugin.spells[id] ?: return null
        val power = data.getOrDefault(plugin.keys.scrollPower, PersistentDataType.INTEGER, 5).coerceIn(1, 10)
        return ScrollData(spell, power)
    }

    fun createAltarCore(amount: Int = 1): ItemStack =
        ItemStack(plugin.settings.arcane.coreMaterial(), amount).apply {
            editMeta { meta ->
                meta.displayName(plugin.text.item("altar-core-name"))
                meta.lore(plugin.text.lines("altar-core-lore"))
                meta.setEnchantmentGlintOverride(true)
                meta.persistentDataContainer.set(plugin.keys.altarCore, PersistentDataType.BYTE, 1)
            }
        }

    fun isAltarCore(item: ItemStack?): Boolean {
        if (item == null || item.isEmpty || !item.hasItemMeta()) return false
        return item.itemMeta.persistentDataContainer.has(plugin.keys.altarCore, PersistentDataType.BYTE)
    }

    fun registerRecipes() {
        if (plugin.settings.arcane.coreRecipe) {
            val recipe = ShapedRecipe(plugin.keys.altarCoreRecipe, createAltarCore())
                .shape("LAL", "AEA", "LAL")
                .setIngredient('L', Material.LAPIS_LAZULI)
                .setIngredient('A', Material.AMETHYST_SHARD)
                .setIngredient('E', Material.ECHO_SHARD)
            Bukkit.addRecipe(recipe)
        }
    }

    fun unregisterRecipes() {
        Bukkit.removeRecipe(plugin.keys.altarCoreRecipe)
    }

    fun cooldownMultiplier(power: Int): Double = 1.0 - (power - 1) * 0.05

    fun powerFactor(power: Int): Double = 0.7 + power * 0.08

    private fun scrollTier(power: Int): String = when {
        power >= 9 -> "scroll-tier-arcane"
        power >= 7 -> "scroll-tier-mystic"
        power >= 4 -> "scroll-tier-steady"
        else -> "scroll-tier-flicker"
    }
}
