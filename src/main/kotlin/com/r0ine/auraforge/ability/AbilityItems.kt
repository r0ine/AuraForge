package com.r0ine.auraforge.ability

import com.r0ine.auraforge.AuraForgePlugin
import com.r0ine.auraforge.api.AbilityType
import com.r0ine.auraforge.core.isArmor
import com.r0ine.auraforge.core.noItalic
import com.r0ine.auraforge.core.rich
import com.r0ine.auraforge.core.seconds
import com.r0ine.auraforge.core.tag
import com.r0ine.auraforge.crafting.ForgeRecipe
import com.r0ine.auraforge.progress.Progress
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
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

data class ForgedItem(val ability: AbilityType, val quality: Int, val recipe: String, val material: Material) {
    val isArmor: Boolean get() = material.isArmor()
}

class AbilityItems(private val plugin: AuraForgePlugin) {

    fun create(recipe: ForgeRecipe, quality: Int, smith: String): ItemStack =
        ItemStack(recipe.result).apply {
            editMeta { meta ->
                meta.persistentDataContainer.apply {
                    set(plugin.keys.ability, PersistentDataType.STRING, recipe.ability.key)
                    set(plugin.keys.quality, PersistentDataType.INTEGER, quality)
                    set(plugin.keys.recipe, PersistentDataType.STRING, recipe.id)
                    set(plugin.keys.smith, PersistentDataType.STRING, smith)
                    set(plugin.keys.forgedAt, PersistentDataType.LONG, System.currentTimeMillis())
                }
                Progress().write(meta, plugin.keys)
                decorate(meta, recipe.result)
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
            material = item.type,
        )
    }

    fun progress(item: ItemStack?): Progress? {
        if (item == null || read(item) == null) return null
        return Progress.read(item.itemMeta, plugin.keys)
    }

    fun update(item: ItemStack, change: (Progress) -> Progress): Progress? {
        if (read(item) == null) return null
        var result: Progress? = null
        item.editMeta { meta ->
            val next = change(Progress.read(meta, plugin.keys))
            next.write(meta, plugin.keys)
            decorate(meta, item.type)
            result = next
        }
        return result
    }

    private fun decorate(meta: ItemMeta, material: Material) {
        val text = plugin.text
        val data = meta.persistentDataContainer
        val ability = data.get(plugin.keys.ability, PersistentDataType.STRING)?.let(AbilityType::byKey) ?: return
        val quality = data.getOrDefault(plugin.keys.quality, PersistentDataType.INTEGER, 0)
        val smith = data.getOrDefault(plugin.keys.smith, PersistentDataType.STRING, "?")
        val recipe = data.get(plugin.keys.recipe, PersistentDataType.STRING)?.let { plugin.recipes[it] }
        val progress = Progress.read(meta, plugin.keys)
        val armor = material.isArmor()

        val tier = Tier.of(quality)
        val tierName = text.render(tier.key)
        val boost = plugin.progress.abilityBoost(progress)
        val power = plugin.abilities.powerFactor(quality) * boost.power
        val cooldown = plugin.abilities.cooldownMillis(ability, quality) * boost.cooldown / 1000.0

        val baseName = text.item("item-name", rich("name", recipe?.name ?: material.name.lowercase()), tag("tier", tierName))
        meta.displayName(
            if (progress.plus > 0) {
                Component.text("+${progress.plus} ", plusColor(progress.plus), TextDecoration.BOLD).noItalic().append(baseName)
            } else {
                baseName
            },
        )
        meta.lore(
            buildList {
                recipe?.description?.takeIf { it.isNotBlank() }?.let { add(text.parse(it).noItalic()) }
                add(text.item("lore-ability", tag("ability", text.render("ability-${ability.key}"))))
                add(text.item("lore-quality", tag("quality", quality.toString()), tag("tier", tierName)))
                add(text.item("lore-power", tag("power", (power * 100).roundToInt().toString()), tag("cooldown", seconds(cooldown))))
                if (progress.plus > 0) {
                    val perPlus = if (armor) plugin.settings.enhance.armorPerPlus else plugin.settings.enhance.damagePerPlus
                    add(
                        text.item(
                            if (armor) "lore-plus-armor" else "lore-plus",
                            tag("plus", progress.plus.toString()),
                            tag("bonus", (progress.plus * perPlus * 100).roundToInt().toString()),
                        ),
                    )
                }
                if (!armor) addAll(progressLines(progress))
                add(text.item(if (armor) "lore-use-armor" else "lore-use-weapon"))
                add(text.item("lore-smith", tag("smith", smith)))
            },
        )
        meta.setEnchantmentGlintOverride(tier == Tier.LEGENDARY || progress.plus >= 7)
    }

    private fun progressLines(progress: Progress): List<Component> = buildList {
        val text = plugin.text
        val need = plugin.progress.xpToNext(progress.level)
        val maxed = progress.level >= plugin.settings.progression.maxLevel
        add(
            text.item(
                if (maxed) "lore-level-max" else "lore-level",
                tag("level", progress.level.toString()),
                tag("bar", xpBar(if (maxed) 1.0 else progress.xp.toDouble() / need)),
                tag("xp", progress.xp.toString()),
                tag("need", need.toString()),
            ),
        )
        progress.element?.let {
            add(text.item("lore-element", rich("element", "<color:#${String.format("%06X", it.rgb)}>${it.display}</color>")))
        }
        if (progress.points > 0) add(text.item("lore-points", tag("points", progress.points.toString())))
        if (progress.talents.isEmpty()) return@buildList
        add(text.item("lore-talents"))
        progress.talents.entries.sortedBy { it.key.ordinal }.forEach { (talent, rank) ->
            add(
                Component.text("  ◆ ", TextColor.fromHexString("#${talent.branch.hex}"))
                    .append(Component.text("${talent.display} ", NamedTextColor.WHITE))
                    .append(Component.text("$rank/${talent.maxRank}", NamedTextColor.DARK_GRAY))
                    .noItalic(),
            )
        }
    }

    private fun xpBar(ratio: Double): String {
        val filled = (ratio.coerceIn(0.0, 1.0) * 12).roundToInt()
        return "▰".repeat(filled) + "▱".repeat(12 - filled)
    }

    companion object {
        fun plusColor(plus: Int): TextColor = when {
            plus >= 10 -> TextColor.color(0xFF4FD8)
            plus >= 7 -> NamedTextColor.GOLD
            plus >= 4 -> NamedTextColor.AQUA
            else -> NamedTextColor.GREEN
        }
    }
}
