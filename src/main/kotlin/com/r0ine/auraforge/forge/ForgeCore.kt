package com.r0ine.auraforge.forge

import com.r0ine.auraforge.AuraForgePlugin
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.persistence.PersistentDataType

class ForgeCore(private val plugin: AuraForgePlugin) {

    fun create(amount: Int = 1): ItemStack =
        ItemStack(plugin.settings.forge.coreMaterial(), amount).apply {
            editMeta { meta ->
                meta.displayName(plugin.text.item("core-name"))
                meta.lore(plugin.text.lines("core-lore"))
                meta.setEnchantmentGlintOverride(true)
                meta.persistentDataContainer.set(plugin.keys.core, PersistentDataType.BYTE, 1)
            }
        }

    fun isCore(item: ItemStack?): Boolean {
        if (item == null || item.isEmpty || !item.hasItemMeta()) return false
        return item.itemMeta.persistentDataContainer.has(plugin.keys.core, PersistentDataType.BYTE)
    }

    fun registerRecipe() {
        val recipe = ShapedRecipe(plugin.keys.coreRecipe, create())
            .shape("IBI", "BAB", "IBI")
            .setIngredient('I', Material.IRON_INGOT)
            .setIngredient('B', Material.BLAZE_POWDER)
            .setIngredient('A', Material.ANVIL)
        Bukkit.addRecipe(recipe)
    }

    fun unregisterRecipe() {
        Bukkit.removeRecipe(plugin.keys.coreRecipe)
    }
}
