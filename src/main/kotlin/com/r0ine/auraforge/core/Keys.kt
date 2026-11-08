package com.r0ine.auraforge.core

import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import org.bukkit.plugin.Plugin

class Keys(plugin: Plugin) {
    val forge = NamespacedKey(plugin, "forge")
    val core = NamespacedKey(plugin, "core")
    val coreRecipe = NamespacedKey(plugin, "forge_core")
    val ability = NamespacedKey(plugin, "ability")
    val quality = NamespacedKey(plugin, "quality")
    val recipe = NamespacedKey(plugin, "recipe")
    val smith = NamespacedKey(plugin, "smith")
    val forgedAt = NamespacedKey(plugin, "forged_at")
}

private val ARMOR_SLOTS = setOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)

fun Material.isArmor(): Boolean = equipmentSlot in ARMOR_SLOTS

/** Oyuncunun kendi thread'inde çağrılmalı. Sığmayan eşyalar ayağının dibine düşer. */
fun Player.giveOrDrop(vararg items: ItemStack) {
    inventory.addItem(*items).values.forEach { world.dropItem(location, it) }
}
