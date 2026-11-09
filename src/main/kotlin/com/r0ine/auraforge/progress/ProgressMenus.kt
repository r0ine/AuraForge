package com.r0ine.auraforge.progress

import com.r0ine.auraforge.AuraForgePlugin
import com.r0ine.auraforge.api.AbilityType
import com.r0ine.auraforge.api.SpellElement
import com.r0ine.auraforge.core.noItalic
import com.r0ine.auraforge.core.rich
import com.r0ine.auraforge.core.seconds
import com.r0ine.auraforge.core.tag
import com.google.common.collect.ImmutableMultimap
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
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
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import java.util.UUID

sealed class ProgressView(val altarId: UUID?) : InventoryHolder {
    lateinit var view: Inventory
    override fun getInventory(): Inventory = view

    class Hub(altarId: UUID?) : ProgressView(altarId)
    class Tree(altarId: UUID?) : ProgressView(altarId)
    class Codex(altarId: UUID?, val page: Page) : ProgressView(altarId)

    enum class Page(val icon: Material) {
        MAIN(Material.WRITABLE_BOOK),
        ELEMENTS(Material.BLAZE_POWDER),
        SPELLS(Material.ENCHANTED_BOOK),
        RECIPES(Material.ANVIL),
        ABILITIES(Material.FEATHER),
        TALENTS(Material.OAK_SAPLING),
        ENHANCE(Material.AMETHYST_SHARD),
    }
}

class ProgressMenus(private val plugin: AuraForgePlugin) : Listener {

    private val text get() = plugin.text

    fun openHub(player: Player, altarId: UUID?) {
        val holder = ProgressView.Hub(altarId)
        holder.view = Bukkit.createInventory(holder, 45, text.render("hub-title"))
        drawHub(player, holder)
        player.openInventory(holder.view)
    }

    fun openTree(player: Player, altarId: UUID?) {
        if (plugin.progress.weaponIn(player) == null) return text.bar(player, "hub-no-weapon")
        val holder = ProgressView.Tree(altarId)
        holder.view = Bukkit.createInventory(holder, 54, text.render("tree-title"))
        drawTree(player, holder)
        player.openInventory(holder.view)
    }

    fun openCodex(player: Player, altarId: UUID?, page: ProgressView.Page = ProgressView.Page.MAIN) {
        val holder = ProgressView.Codex(altarId, page)
        val size = if (page == ProgressView.Page.MAIN) 27 else 54
        holder.view = Bukkit.createInventory(holder, size, text.render("codex-title-${page.name.lowercase()}"))
        drawCodex(holder)
        player.openInventory(holder.view)
    }

    private fun drawHub(player: Player, holder: ProgressView.Hub) {
        val inventory = holder.view
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE)
        inventory.setItem(38, icon(Material.COMPASS, text.parse("<gold>+ Macera"), listOf(text.parse("<gray>20 mühür, 10 büyü, setler ve maceracı rehberi"))))
        for (slot in listOf(1, 2, 3, 5, 6, 7)) inventory.setItem(slot, pane(Material.PURPLE_STAINED_GLASS_PANE))

        val item = player.inventory.itemInMainHand
        val forged = plugin.items.read(item)
        val progress = plugin.items.progress(item)
        if (forged == null || progress == null) {
            inventory.setItem(PREVIEW, icon(Material.BARRIER, text.item("hub-empty"), text.lines("hub-empty-lore")))
            inventory.setItem(CODEX_BUTTON, icon(Material.WRITABLE_BOOK, text.item("hub-codex"), text.lines("hub-codex-lore")))
            if (holder.altarId != null) inventory.setItem(BACK, icon(Material.ARROW, text.item("menu-back")))
            return
        }

        inventory.setItem(PREVIEW, item.clone())

        val enhance = plugin.settings.enhance
        val plus = progress.plus
        val enhanceIcon = if (plus >= enhance.maxPlus) {
            icon(Material.NETHERITE_BLOCK, text.item("hub-enhance-max", tag("plus", plugin.progress.plusLabel(plus))))
        } else {
            val lore = buildList {
                add(text.item("hub-enhance-chance", tag("chance", enhance.chance(plus).toString())))
                add(text.item("hub-enhance-cost"))
                addAll(plugin.progress.costLines(plus))
                if (plus >= enhance.downgradeFrom) add(text.item("hub-enhance-risk"))
                add(Component.empty())
                add(text.item(if (holder.altarId == null) "hub-enhance-need-altar" else "hub-enhance-click"))
            }
            icon(Material.ANVIL, text.item("hub-enhance", tag("from", plugin.progress.plusLabel(plus)), tag("to", plugin.progress.plusLabel(plus + 1))), lore)
        }
        inventory.setItem(ENHANCE, enhanceIcon)

        if (forged.isArmor) {
            inventory.setItem(SOCKET, icon(Material.GRAY_DYE, text.item("hub-socket-armor"), text.lines("hub-socket-armor-lore")))
            inventory.setItem(TREE_BUTTON, icon(Material.DEAD_BUSH, text.item("hub-tree-armor"), text.lines("hub-tree-armor-lore")))
        } else {
            val element = progress.element
            inventory.setItem(
                SOCKET,
                if (element == null) {
                    icon(Material.PURPLE_STAINED_GLASS_PANE, text.item("hub-socket-empty"), text.lines("hub-socket-lore"))
                } else {
                    icon(element.rune, text.item("hub-socket", rich("element", plugin.progress.elementTag(element))), text.lines("hub-socket-lore"), glint = true)
                },
            )
            inventory.setItem(
                TREE_BUTTON,
                icon(
                    Material.OAK_SAPLING,
                    text.item("hub-tree"),
                    text.lines("hub-tree-lore", tag("points", progress.points.toString()), tag("level", progress.level.toString())),
                    glint = progress.points > 0,
                ).apply { amount = progress.points.coerceIn(1, 64) },
            )
        }
        inventory.setItem(CODEX_BUTTON, icon(Material.WRITABLE_BOOK, text.item("hub-codex"), text.lines("hub-codex-lore")))
        if (holder.altarId != null) inventory.setItem(BACK, icon(Material.ARROW, text.item("menu-back")))
        inventory.setItem(CLOSE, icon(Material.BARRIER, text.item("menu-close")))
    }

    private fun drawTree(player: Player, holder: ProgressView.Tree) {
        val inventory = holder.view
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE)
        val progress = plugin.progress.weaponIn(player)?.second ?: return

        for (branch in Branch.entries) {
            for (row in 0..4) {
                val nodes = Talent.inRow(branch, row)
                if (nodes.size > 1) {
                    val open = row == 0 || Talent.inRow(branch, row - 1).any { progress.has(it) }
                    inventory.setItem(
                        row * 9 + branch.column + 1,
                        pane(if (open) branch.pane else Material.GRAY_STAINED_GLASS_PANE, text.item("tree-fork")),
                    )
                }
                nodes.forEach { inventory.setItem(it.slot, node(it, progress)) }
            }
        }

        inventory.setItem(45, icon(Material.ARROW, text.item("menu-back")))
        Branch.entries.forEachIndexed { index, branch ->
            inventory.setItem(
                46 + index,
                pane(
                    branch.pane,
                    Component.text(branch.display, TextColor.fromHexString("#${branch.hex}"), TextDecoration.BOLD).noItalic(),
                    text.lines("tree-branch-lore", tag("spent", progress.spentIn(branch).toString())),
                ),
            )
        }
        inventory.setItem(
            49,
            icon(
                Material.EXPERIENCE_BOTTLE,
                text.item("tree-info", tag("level", progress.level.toString())),
                text.lines("tree-info-lore", tag("points", progress.points.toString()), tag("spent", progress.spent.toString())),
            ),
        )
        inventory.setItem(51, icon(Material.LAVA_BUCKET, text.item("tree-reset"), text.lines("tree-reset-lore", tag("levels", plugin.settings.progression.resetLevels.toString()))))
        inventory.setItem(53, icon(Material.WRITABLE_BOOK, text.item("hub-codex")))
    }

    private fun node(talent: Talent, progress: Progress): ItemStack {
        val rank = progress.rank(talent)
        val lock = progress.lockReason(talent)
        val color = TextColor.fromHexString("#${talent.branch.hex}")
        val name = Component.text(talent.display, if (rank > 0 || lock == null) color else NamedTextColor.GRAY, TextDecoration.BOLD)
            .append(Component.text("  $rank/${talent.maxRank}", NamedTextColor.DARK_GRAY).decoration(TextDecoration.BOLD, false))
            .noItalic()
        val lore = buildList {
            add(text.item("tree-row", tag("branch", talent.branch.display), tag("row", (talent.row + 1).toString())))
            if (rank > 0) add(text.item("tree-now", tag("effect", talent.describe(talent.value(rank)))))
            if (rank < talent.maxRank) add(text.item("tree-next", tag("effect", talent.describe(talent.value(rank + 1)))))
            talent.rival()?.let { add(text.item("tree-choice", tag("rival", it.display))) }
            add(Component.empty())
            add(
                when (lock) {
                    null -> text.item("tree-learn")
                    Progress.Lock.MAXED -> text.item("tree-maxed")
                    Progress.Lock.RIVAL -> text.item("tree-locked-rival")
                    Progress.Lock.PARENT -> text.item("tree-locked-parent")
                    Progress.Lock.DEPTH -> text.item("tree-locked-depth", tag("need", (talent.row * 2).toString()))
                    Progress.Lock.POINTS -> text.item("tree-locked-points")
                },
            )
        }
        val locked = lock == Progress.Lock.RIVAL || lock == Progress.Lock.PARENT || lock == Progress.Lock.DEPTH
        return icon(if (locked && rank == 0) Material.GRAY_DYE else talent.icon, name, lore, glint = rank > 0).apply {
            amount = rank.coerceAtLeast(1)
        }
    }

    private fun drawCodex(holder: ProgressView.Codex) {
        val inventory = holder.view
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE)
        when (holder.page) {
            ProgressView.Page.MAIN -> {
                ProgressView.Page.entries.drop(1).forEachIndexed { index, page ->
                    val key = "codex-${page.name.lowercase()}"
                    inventory.setItem(10 + index, icon(page.icon, text.item(key), text.lines("$key-lore")))
                }
                inventory.setItem(16, icon(Material.EXPERIENCE_BOTTLE, text.item("codex-guide"), text.lines("codex-guide-lore")))
                inventory.setItem(22, icon(Material.ARROW, text.item("menu-back")))
                return
            }
            ProgressView.Page.ELEMENTS -> SpellElement.entries.forEachIndexed { index, element ->
                val spells = plugin.spells.book.all.filter { element in it.elements }.joinToString("<gray>, ") { it.name }
                inventory.setItem(
                    index,
                    icon(
                        element.rune,
                        text.item("codex-element-name", rich("element", plugin.progress.elementTag(element))),
                        text.lines(
                            "codex-element-lore",
                            tag("rune", Component.translatable(element.rune.translationKey())),
                            rich("proc", text.raw("codex-proc-${element.key}")),
                            rich("spells", spells.ifBlank { "-" }),
                        ),
                    ),
                )
            }
            ProgressView.Page.SPELLS -> plugin.spells.book.all.take(45).forEachIndexed { index, spell ->
                inventory.setItem(
                    index,
                    icon(
                        spell.result,
                        text.parse(spell.name).noItalic(),
                        buildList {
                            add(text.item("codex-spell-elements", rich("elements", spell.elements.joinToString(" <dark_gray>+</dark_gray> ") { plugin.progress.elementTag(it) })))
                            if (spell.description.isNotBlank()) add(text.parse(spell.description).noItalic())
                            add(text.item("codex-spell-stats", tag("cooldown", seconds(spell.cooldown)), tag("radius", seconds(spell.radius))))
                        },
                    ),
                )
            }
            ProgressView.Page.RECIPES -> plugin.recipes.all.take(45).forEachIndexed { index, recipe ->
                inventory.setItem(
                    index,
                    icon(
                        recipe.result,
                        text.parse(recipe.name).noItalic(),
                        buildList {
                            add(text.item("lore-ability", tag("ability", text.render("ability-${recipe.ability.key}"))))
                            add(text.item("codex-recipe-ingredients"))
                            recipe.ingredients.forEach { (material, amount) ->
                                add(text.item("enhance-cost-line", tag("amount", amount.toString()), tag("material", Component.translatable(material.translationKey()))))
                            }
                        },
                    ),
                )
            }
            ProgressView.Page.ABILITIES -> AbilityType.entries.forEachIndexed { index, type ->
                val settings = plugin.settings.ability(type)
                inventory.setItem(
                    index + 20,
                    icon(
                        ABILITY_ICONS[type] ?: Material.PAPER,
                        text.item("ability-${type.key}"),
                        text.lines("codex-ability-${type.key}") + text.item("codex-ability-cooldown", tag("cooldown", seconds(settings.cooldown))),
                    ),
                )
            }
            ProgressView.Page.TALENTS -> Talent.entries.forEach { talent ->
                val color = TextColor.fromHexString("#${talent.branch.hex}")
                inventory.setItem(
                    talent.slot,
                    icon(
                        talent.icon,
                        Component.text(talent.display, color, TextDecoration.BOLD).noItalic(),
                        buildList {
                            add(text.item("tree-row", tag("branch", talent.branch.display), tag("row", (talent.row + 1).toString())))
                            add(text.item("codex-talent-max", tag("effect", talent.describe(talent.value(talent.maxRank))), tag("max", talent.maxRank.toString())))
                            talent.rival()?.let { add(text.item("tree-choice", tag("rival", it.display))) }
                            if (talent.row > 0) add(text.item("codex-talent-need", tag("need", (talent.row * 2).toString())))
                        },
                    ),
                )
            }
            ProgressView.Page.ENHANCE -> {
                val enhance = plugin.settings.enhance
                for (plus in 0 until enhance.maxPlus) {
                    inventory.setItem(
                        plus + if (plus >= 9) 10 else 0,
                        icon(
                            if (plus + 1 >= enhance.broadcastFrom) Material.AMETHYST_CLUSTER else Material.AMETHYST_SHARD,
                            text.item("codex-enhance-step", tag("from", plugin.progress.plusLabel(plus)), tag("to", plugin.progress.plusLabel(plus + 1))),
                            buildList {
                                add(text.item("hub-enhance-chance", tag("chance", enhance.chance(plus).toString())))
                                add(text.item("hub-enhance-cost"))
                                addAll(plugin.progress.costLines(plus))
                                if (plus >= enhance.downgradeFrom) add(text.item("hub-enhance-risk"))
                            },
                        ).apply { amount = plus + 1 },
                    )
                }
                inventory.setItem(
                    31,
                    icon(
                        Material.KNOWLEDGE_BOOK,
                        text.item("codex-enhance-info"),
                        text.lines(
                            "codex-enhance-info-lore",
                            tag("damage", (enhance.damagePerPlus * 100).toString().removeSuffix(".0")),
                            tag("power", (enhance.powerPerPlus * 100).toString().removeSuffix(".0")),
                            tag("armor", (enhance.armorPerPlus * 100).toString().removeSuffix(".0")),
                        ),
                    ),
                )
            }
        }
        inventory.setItem(49, icon(Material.ARROW, text.item("menu-back")))
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.getHolder(false) as? ProgressView ?: return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        val top = event.view.topInventory

        if (event.clickedInventory !== top) {
            if (holder is ProgressView.Hub && event.isShiftClick) socketFrom(event, player, holder)
            return
        }

        when (holder) {
            is ProgressView.Hub -> when (event.rawSlot) {
                38 -> player.scheduler.run(plugin, { plugin.adventureMenus.open(player) }, null)
                ENHANCE -> enhance(player, holder)
                TREE_BUTTON -> openTree(player, holder.altarId)
                CODEX_BUTTON -> openCodex(player, holder.altarId)
                BACK -> backToAltar(player, holder.altarId)
                CLOSE -> player.closeInventory()
            }
            is ProgressView.Tree -> when (val slot = event.rawSlot) {
                45 -> openHub(player, holder.altarId)
                51 -> if (plugin.progress.reset(player)) drawTree(player, holder)
                53 -> openCodex(player, holder.altarId)
                else -> Talent.at(slot)?.let { talent ->
                    when (val lock = plugin.progress.learn(player, talent)) {
                        null -> plugin.text.bar(player, "talent-learned", tag("talent", talent.display))
                        else -> {
                            plugin.text.bar(player, "tree-bar-${lock.name.lowercase()}")
                            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.6f, 1.2f)
                        }
                    }
                    drawTree(player, holder)
                }
            }
            is ProgressView.Codex -> {
                if (holder.page == ProgressView.Page.MAIN) {
                    when (val slot = event.rawSlot) {
                        in 10..15 -> openCodex(player, holder.altarId, ProgressView.Page.entries[slot - 9])
                        22 -> openHub(player, holder.altarId)
                    }
                } else if (event.rawSlot == 49) {
                    openCodex(player, holder.altarId)
                }
            }
        }
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.getHolder(false) is ProgressView) event.isCancelled = true
    }

    private fun enhance(player: Player, holder: ProgressView.Hub) {
        val altar = holder.altarId?.let { plugin.altars[it] }
        if (altar == null || !plugin.altars.canUse(player, altar)) return text.bar(player, "hub-enhance-need-altar")

        when (plugin.progress.enhance(player)) {
            ProgressService.EnhanceResult.SUCCESS -> {
                plugin.altars.effect(altar) { enhanceBurst(true) }
                player.playSound(player.location, Sound.BLOCK_ANVIL_USE, 1f, 1.4f)
                text.bar(player, "enhance-success", tag("item", player.inventory.itemInMainHand.displayName()))
            }
            ProgressService.EnhanceResult.FAILED -> {
                plugin.altars.effect(altar) { enhanceBurst(false) }
                player.playSound(player.location, Sound.BLOCK_ANVIL_DESTROY, 0.8f, 1.2f)
                text.bar(player, "enhance-failed")
            }
            ProgressService.EnhanceResult.DOWNGRADED -> {
                plugin.altars.effect(altar) { enhanceBurst(false) }
                player.playSound(player.location, Sound.ENTITY_ITEM_BREAK, 1f, 0.7f)
                text.bar(player, "enhance-downgraded")
            }
            ProgressService.EnhanceResult.MAXED -> text.bar(player, "enhance-maxed")
            ProgressService.EnhanceResult.NO_ITEM -> text.bar(player, "hub-no-weapon")
            ProgressService.EnhanceResult.NO_MATERIALS -> {
                text.bar(player, "enhance-no-materials")
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.6f, 1f)
            }
        }
        drawHub(player, holder)
    }

    private fun socketFrom(event: InventoryClickEvent, player: Player, holder: ProgressView.Hub) {
        val stack = event.currentItem ?: return
        val element = plugin.spellItems.readRune(stack) ?: return
        if (event.slot == player.inventory.heldItemSlot) return
        if (plugin.progress.weaponIn(player) == null) return text.bar(player, "hub-no-weapon")
        if (!plugin.progress.socket(player, element)) return
        event.clickedInventory?.setItem(event.slot, if (stack.amount > 1) stack.apply { amount -= 1 } else null)
        drawHub(player, holder)
    }

    private fun backToAltar(player: Player, altarId: UUID?) {
        val altar = altarId?.let { plugin.altars[it] } ?: return player.closeInventory()
        plugin.altarMenus.open(player, altar)
    }

    private fun fill(inventory: Inventory, material: Material) {
        val filler = pane(material)
        for (slot in 0 until inventory.size) inventory.setItem(slot, filler)
    }

    private fun pane(material: Material, name: Component? = null, lore: List<Component> = emptyList()) =
        if (name == null) icon(material, Component.empty(), hidden = true) else icon(material, name, lore)

    private fun icon(
        material: Material,
        name: Component,
        lore: List<Component> = emptyList(),
        hidden: Boolean = false,
        glint: Boolean = false,
    ) = ItemStack(material).apply {
        editMeta { meta ->
            meta.displayName(name)
            meta.lore(lore)
            meta.isHideTooltip = hidden
            if (glint) meta.setEnchantmentGlintOverride(true)
            meta.attributeModifiers = ImmutableMultimap.of()
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
        }
    }

    private companion object {
        const val PREVIEW = 13
        const val ENHANCE = 29
        const val SOCKET = 31
        const val TREE_BUTTON = 33
        const val CODEX_BUTTON = 40
        const val BACK = 36
        const val CLOSE = 44

        val ABILITY_ICONS = mapOf(
            AbilityType.DASH to Material.FEATHER,
            AbilityType.LEAP to Material.RABBIT_FOOT,
            AbilityType.SHOCKWAVE to Material.TNT,
            AbilityType.GUARD to Material.SHIELD,
            AbilityType.WAVE to Material.HEART_OF_THE_SEA,
        )
    }
}
