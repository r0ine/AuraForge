package com.r0ine.auraforge.menu

import com.r0ine.auraforge.AuraForgePlugin
import com.r0ine.auraforge.core.giveOrDrop
import com.r0ine.auraforge.core.rich
import com.r0ine.auraforge.core.tag
import com.r0ine.auraforge.forge.Forge
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import java.util.UUID

class ForgeMenu(val forgeId: UUID) : InventoryHolder {
    lateinit var view: Inventory

    override fun getInventory(): Inventory = view
}

class ForgeMenus(private val plugin: AuraForgePlugin) : Listener {

    fun open(player: Player, forge: Forge) {
        val menu = ForgeMenu(forge.id)
        menu.view = Bukkit.createInventory(menu, 27, plugin.text.render("menu-title"))
        draw(menu.view, forge)
        player.openInventory(menu.view)
    }

    private fun draw(inventory: Inventory, forge: Forge) {
        val text = plugin.text
        val filler = icon(Material.GRAY_STAINED_GLASS_PANE, Component.empty(), hidden = true)
        for (slot in 0 until inventory.size) inventory.setItem(slot, filler)

        val items = forge.items()
        val max = plugin.settings.forge.maxItems
        CONTENT_SLOTS.forEachIndexed { index, slot ->
            val shown = when {
                index < items.size -> items[index].apply {
                    editMeta { it.lore((it.lore() ?: emptyList()) + text.lines("menu-slot-taken-lore")) }
                }
                index < max -> icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE, text.item("menu-slot-empty"), text.lines("menu-slot-empty-lore"))
                else -> filler
            }
            inventory.setItem(slot, shown)
        }

        inventory.setItem(RECIPES, icon(Material.KNOWLEDGE_BOOK, text.item("menu-recipes"), text.lines("menu-recipes-lore")))
        inventory.setItem(TAKE_ALL, icon(Material.HOPPER, text.item("menu-take-all")))
        inventory.setItem(22, icon(Material.COMPASS, text.parse("<gold>+ Macera"), listOf(text.parse("<gray>Silah gelişimi, büyüler, setler ve wiki"))))

        val recipe = plugin.recipes.match(forge.materials())
        inventory.setItem(
            CRAFT,
            if (recipe == null) {
                icon(Material.BARRIER, text.item("menu-craft-none"), text.lines("menu-craft-none-lore"))
            } else {
                icon(
                    Material.ANVIL,
                    text.item("menu-craft", rich("recipe", recipe.name)),
                    text.lines("menu-craft-lore", tag("ability", text.render("ability-${recipe.ability.key}"))),
                )
            },
        )
        inventory.setItem(DISMANTLE, icon(Material.SMITHING_TABLE, text.item("menu-dismantle"), text.lines("menu-dismantle-lore")))
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val top = event.view.topInventory
        val menu = top.getHolder(false) as? ForgeMenu ?: return
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val forge = plugin.forges[menu.forgeId]
        if (forge == null || !plugin.forges.canUse(player, forge)) {
            player.closeInventory()
            return
        }

        if (event.clickedInventory !== top) {
            if (event.isShiftClick) insertFrom(event, player, forge)
            return
        }

        when (val slot = event.rawSlot) {
            22 -> {
                player.scheduler.run(plugin, { plugin.adventureMenus.open(player) }, null)
                return
            }
            in CONTENT_SLOTS -> plugin.forges.take(player, forge, slot - CONTENT_SLOTS.first)
            TAKE_ALL -> plugin.forges.takeAll(player, forge)
            RECIPES -> {
                player.closeInventory()
                plugin.sendRecipes(player)
                return
            }
            CRAFT -> {
                player.closeInventory()
                plugin.crafting.start(player, forge)
                return
            }
            DISMANTLE -> {
                dismantle(player, forge)
                return
            }
            else -> return
        }
        draw(top, forge)
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.getHolder(false) is ForgeMenu) event.isCancelled = true
    }

    private fun insertFrom(event: InventoryClickEvent, player: Player, forge: Forge) {
        val stack = event.currentItem ?: return
        if (stack.isEmpty) return
        if (!plugin.forges.insert(player, forge, stack)) return
        event.clickedInventory?.setItem(event.slot, if (stack.amount > 1) stack.apply { amount -= 1 } else null)
        draw(event.view.topInventory, forge)
    }

    private fun dismantle(player: Player, forge: Forge) {
        if (forge.busy) {
            plugin.text.bar(player, "forge-busy")
            return
        }
        player.closeInventory()
        val items = plugin.forges.dismantle(forge)
        player.giveOrDrop(*items.toTypedArray(), plugin.core.create())
        player.playSound(player.location, Sound.BLOCK_ANVIL_DESTROY, 0.6f, 1.2f)
        plugin.text.bar(player, "forge-dismantled")
    }

    private fun icon(material: Material, name: Component, lore: List<Component> = emptyList(), hidden: Boolean = false) =
        ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(name)
                meta.lore(lore)
                meta.isHideTooltip = hidden
            }
        }

    private companion object {
        val CONTENT_SLOTS = 10..16
        const val RECIPES = 19
        const val TAKE_ALL = 21
        const val CRAFT = 23
        const val DISMANTLE = 25
    }
}
