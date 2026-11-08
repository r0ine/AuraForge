package com.r0ine.auraforge.arcane

import com.r0ine.auraforge.AuraForgePlugin
import com.r0ine.auraforge.api.SpellElement
import com.r0ine.auraforge.core.giveOrDrop
import com.r0ine.auraforge.core.rich
import com.r0ine.auraforge.core.tag
import com.r0ine.auraforge.spell.Spell
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AltarMenu(val altarId: UUID, val user: UUID) : InventoryHolder {
    lateinit var view: Inventory
    override fun getInventory(): Inventory = view
}

class AltarMenus(private val plugin: AuraForgePlugin) : Listener {

    private val open = ConcurrentHashMap<UUID, AltarMenu>()

    fun open(player: Player, altar: Altar) {
        if (!altar.activeUser.compareAndSet(null, player.uniqueId) && altar.activeUser.get() != player.uniqueId) {
            plugin.text.bar(player, "altar-busy")
            return
        }
        val menu = AltarMenu(altar.id, player.uniqueId)
        menu.view = Bukkit.createInventory(menu, 27, plugin.text.render("altar-menu-title"))
        draw(menu.view, altar)
        open[player.uniqueId] = menu
        player.openInventory(menu.view)
        plugin.altars.refresh(altar)
    }

    private fun draw(inventory: Inventory, altar: Altar) {
        val text = plugin.text
        val keptRune1 = inventory.getItem(RUNE_1)?.takeIf { plugin.spellItems.isRune(it) }
        val keptRune2 = inventory.getItem(RUNE_2)?.takeIf { plugin.spellItems.isRune(it) }
        val filler = icon(Material.GRAY_STAINED_GLASS_PANE, Component.empty(), hidden = true)
        for (slot in 0 until inventory.size) inventory.setItem(slot, filler)

        inventory.setItem(RUNE_1, keptRune1 ?: icon(Material.PURPLE_STAINED_GLASS_PANE, text.item("altar-slot-left"), text.lines("altar-slot-hint")))
        inventory.setItem(RUNE_2, keptRune2 ?: icon(Material.PURPLE_STAINED_GLASS_PANE, text.item("altar-slot-right"), text.lines("altar-slot-hint")))
        val slot1 = keptRune1
        val slot2 = keptRune2

        val a = plugin.spellItems.readRune(slot1)
        val b = plugin.spellItems.readRune(slot2)
        val spell = if (a != null && b != null) plugin.spells.book.combine(a, b) else null

        inventory.setItem(
            RESULT,
            when {
                spell != null -> icon(
                    spell.result,
                    text.item("altar-result-name", rich("name", spell.name)),
                    text.lines("altar-result-lore", tag("elements", spell.elements.joinToString(" <dark_gray>+</dark_gray> ") {
                        "<color:#${String.format("%06X", it.rgb)}>${it.display}</color>"
                    })),
                )
                a == null && b == null -> icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE, text.item("altar-result-empty"))
                else -> icon(Material.BARRIER, text.item("altar-result-unknown"))
            },
        )

        inventory.setItem(COMBINE, when (spell) {
            null -> icon(Material.BARRIER, text.item("altar-combine-disabled"), text.lines("altar-combine-disabled-lore"))
            else -> icon(Material.AMETHYST_CLUSTER, text.item("altar-combine"), text.lines("altar-combine-lore", rich("name", spell.name)))
        })
        inventory.setItem(CANCEL, icon(Material.HOPPER, text.item("altar-return")))
        inventory.setItem(RECIPES, icon(Material.KNOWLEDGE_BOOK, text.item("altar-recipes"), text.lines("altar-recipes-lore")))
        inventory.setItem(DISMANTLE, icon(Material.ENCHANTING_TABLE, text.item("altar-dismantle"), text.lines("altar-dismantle-lore")))

        plugin.altars.refresh(altar, a, b, spell)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val top = event.view.topInventory
        val menu = top.getHolder(false) as? AltarMenu ?: return
        val player = event.whoClicked as? Player ?: return
        val altar = plugin.altars[menu.altarId]
        if (altar == null || !plugin.altars.canUse(player, altar)) {
            player.closeInventory()
            return
        }

        val raw = event.rawSlot
        if (event.clickedInventory !== top) {
            if (event.isShiftClick) {
                event.isCancelled = true
                val source = event.currentItem ?: return
                if (!plugin.spellItems.isRune(source)) return
                placeIntoEmptySlot(top, source, event)
                draw(top, altar)
            }
            return
        }

        when (raw) {
            RUNE_1, RUNE_2 -> {
                val cursor = event.cursor
                val existing = top.getItem(raw)?.takeIf { it.type != Material.GRAY_STAINED_GLASS_PANE && it.type != Material.PURPLE_STAINED_GLASS_PANE }
                when {
                    cursor.isEmpty && existing != null -> {
                        event.isCancelled = true
                        event.setCursor(existing)
                        top.setItem(raw, icon(Material.PURPLE_STAINED_GLASS_PANE, plugin.text.item(if (raw == RUNE_1) "altar-slot-left" else "altar-slot-right"), plugin.text.lines("altar-slot-hint")))
                        draw(top, altar)
                    }
                    !cursor.isEmpty && plugin.spellItems.isRune(cursor) -> {
                        event.isCancelled = true
                        val taken = cursor.clone().apply { amount = 1 }
                        if (cursor.amount > 1) cursor.amount -= 1 else event.setCursor(null)
                        if (existing != null) player.giveOrDrop(existing)
                        top.setItem(raw, taken)
                        draw(top, altar)
                    }
                    else -> event.isCancelled = true
                }
            }
            COMBINE -> {
                event.isCancelled = true
                combine(player, altar, top)
            }
            CANCEL -> {
                event.isCancelled = true
                player.closeInventory()
            }
            RECIPES -> {
                event.isCancelled = true
                player.closeInventory()
                plugin.sendSpells(player)
            }
            DISMANTLE -> {
                event.isCancelled = true
                dismantle(player, altar, top)
            }
            else -> event.isCancelled = true
        }
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        val top = event.view.topInventory
        if (top.getHolder(false) !is AltarMenu) return
        val slots = event.rawSlots.filter { it < top.size }
        if (slots.isEmpty()) return
        if (slots.any { it != RUNE_1 && it != RUNE_2 } || !plugin.spellItems.isRune(event.oldCursor)) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        val menu = event.inventory.getHolder(false) as? AltarMenu ?: return
        val player = event.player as? Player ?: return
        open.remove(player.uniqueId)

        val runes = listOfNotNull(
            event.inventory.getItem(RUNE_1)?.takeIf { plugin.spellItems.isRune(it) },
            event.inventory.getItem(RUNE_2)?.takeIf { plugin.spellItems.isRune(it) },
        )
        if (runes.isNotEmpty()) player.giveOrDrop(*runes.toTypedArray())

        val altar = plugin.altars[menu.altarId] ?: return
        altar.activeUser.compareAndSet(player.uniqueId, null)
        plugin.altars.refresh(altar)
    }

    private fun combine(player: Player, altar: Altar, top: Inventory) {
        val slot1 = top.getItem(RUNE_1)
        val slot2 = top.getItem(RUNE_2)
        val a = plugin.spellItems.readRune(slot1)
        val b = plugin.spellItems.readRune(slot2)
        if (a == null || b == null) return plugin.text.bar(player, "altar-need-both")
        val spell = plugin.spells.book.combine(a, b) ?: run {
            plugin.text.bar(player, "altar-no-recipe")
            plugin.altars.effect(altar) { fail() }
            return
        }
        val power = computePower(a, b)
        val scroll = plugin.spellItems.createScroll(spell, power, player.name)
        top.setItem(RUNE_1, icon(Material.PURPLE_STAINED_GLASS_PANE, plugin.text.item("altar-slot-left"), plugin.text.lines("altar-slot-hint")))
        top.setItem(RUNE_2, icon(Material.PURPLE_STAINED_GLASS_PANE, plugin.text.item("altar-slot-right"), plugin.text.lines("altar-slot-hint")))
        player.giveOrDrop(scroll)
        altar.recordCombine()
        plugin.markDirty()
        plugin.altars.effect(altar) { combineBurst(spell.elements.first()) }
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f)
        plugin.text.bar(player, "altar-combined", rich("name", spell.name))
        draw(top, altar)
    }

    private fun computePower(a: SpellElement, b: SpellElement): Int {
        val base = if (a == b) 6 else 5
        return (base + (Math.random() * 3).toInt()).coerceIn(1, 10)
    }

    private fun dismantle(player: Player, altar: Altar, top: Inventory) {
        val runes = listOfNotNull(
            top.getItem(RUNE_1)?.takeIf { plugin.spellItems.isRune(it) },
            top.getItem(RUNE_2)?.takeIf { plugin.spellItems.isRune(it) },
        )
        top.setItem(RUNE_1, null)
        top.setItem(RUNE_2, null)
        player.closeInventory()
        plugin.altars.dismantle(altar)
        player.giveOrDrop(*runes.toTypedArray(), plugin.spellItems.createAltarCore())
        player.playSound(player.location, Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 1f)
        plugin.text.bar(player, "altar-dismantled")
    }

    private fun placeIntoEmptySlot(top: Inventory, stack: ItemStack, event: InventoryClickEvent) {
        for (slot in listOf(RUNE_1, RUNE_2)) {
            val existing = top.getItem(slot)
            if (existing != null && plugin.spellItems.isRune(existing)) continue
            top.setItem(slot, stack.clone().apply { amount = 1 })
            if (stack.amount > 1) stack.amount -= 1 else event.clickedInventory?.setItem(event.slot, null)
            return
        }
    }

    private fun icon(material: Material, name: Component, lore: List<Component> = emptyList(), hidden: Boolean = false) =
        ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(name)
                meta.lore(lore)
                meta.isHideTooltip = hidden
            }
        }

    companion object {
        const val RUNE_1 = 11
        const val RUNE_2 = 15
        const val RESULT = 13
        const val COMBINE = 22
        const val CANCEL = 18
        const val RECIPES = 19
        const val DISMANTLE = 26
    }
}
