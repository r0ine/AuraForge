package com.r0ine.auraforge.command

import com.r0ine.auraforge.AuraForgePlugin
import com.r0ine.auraforge.api.SpellElement
import com.r0ine.auraforge.core.SmithRecord
import com.r0ine.auraforge.core.giveOrDrop
import com.r0ine.auraforge.core.runFor
import com.r0ine.auraforge.core.tag
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Player

class AuraForgeCommand(private val plugin: AuraForgePlugin) : TabExecutor {

    private val text get() = plugin.text

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        when (args.firstOrNull()?.lowercase()) {
            "give" -> giveCore(sender, args)
            "item" -> giveForged(sender, args)
            "altar" -> giveAltarCore(sender, args)
            "rune" -> giveRune(sender, args)
            "scroll" -> giveScroll(sender, args)
            "recipes" -> plugin.sendRecipes(sender)
            "spells" -> plugin.sendSpells(sender)
            "elements" -> sendElements(sender)
            "stats" -> stats(sender, args)
            "list" -> list(sender)
            "reload" -> reload(sender)
            else -> text.send(sender, "help")
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): List<String> {
        val sub = args.firstOrNull()?.lowercase()
        val options = when (args.size) {
            1 -> SUBCOMMANDS.filter { (_, permission) -> permission == null || sender.hasPermission(permission) }.keys
            2 -> when (sub) {
                "give", "stats", "altar" -> onlineNames()
                "item" -> plugin.recipes.ids
                "rune" -> SpellElement.entries.map { it.key }
                "scroll" -> plugin.spells.book.ids
                else -> emptyList()
            }
            3 -> when (sub) {
                "give", "altar" -> listOf("1", "16", "64")
                "item" -> listOf("50", "75", "95")
                "rune" -> listOf("1", "4", "16")
                "scroll" -> listOf("3", "6", "9")
                else -> emptyList()
            }
            4 -> when (sub) {
                "item", "scroll" -> onlineNames()
                "rune" -> onlineNames()
                else -> emptyList()
            }
            else -> emptyList()
        }
        return options.filter { it.startsWith(args.last(), ignoreCase = true) }
    }

    private fun giveCore(sender: CommandSender, args: Array<out String>) {
        if (!sender.allowed("auraforge.give")) return
        val target = target(sender, args.getOrNull(1)) ?: return
        val amount = args.getOrNull(2)?.toIntOrNull()?.coerceIn(1, 64) ?: 1
        val item = plugin.core.create(amount)
        plugin.runFor(target) { target.giveOrDrop(item) }
        text.send(sender, "give-core", tag("player", target.name), tag("amount", amount.toString()))
    }

    private fun giveAltarCore(sender: CommandSender, args: Array<out String>) {
        if (!sender.allowed("auraforge.give")) return
        val target = target(sender, args.getOrNull(1)) ?: return
        val amount = args.getOrNull(2)?.toIntOrNull()?.coerceIn(1, 64) ?: 1
        val item = plugin.spellItems.createAltarCore(amount)
        plugin.runFor(target) { target.giveOrDrop(item) }
        text.send(sender, "give-altar", tag("player", target.name), tag("amount", amount.toString()))
    }

    private fun giveRune(sender: CommandSender, args: Array<out String>) {
        if (!sender.allowed("auraforge.give")) return
        val raw = args.getOrNull(1) ?: return text.send(sender, "rune-usage")
        val element = SpellElement.byKey(raw) ?: return text.send(sender, "unknown-element", tag("key", raw))
        val amount = args.getOrNull(2)?.toIntOrNull()?.coerceIn(1, 64) ?: 1
        val target = target(sender, args.getOrNull(3)) ?: return
        val item = plugin.spellItems.createRune(element, amount)
        plugin.runFor(target) { target.giveOrDrop(item) }
        text.send(sender, "give-rune", tag("player", target.name), tag("element", text.render("element-${element.key}")), tag("amount", amount.toString()))
    }

    private fun giveScroll(sender: CommandSender, args: Array<out String>) {
        if (!sender.allowed("auraforge.give")) return
        val spell = args.getOrNull(1)?.let { plugin.spells[it] } ?: return text.send(sender, "unknown-spell")
        val power = args.getOrNull(2)?.toIntOrNull()?.coerceIn(1, 10) ?: 7
        val target = target(sender, args.getOrNull(3)) ?: return
        val item = plugin.spellItems.createScroll(spell, power, sender.name)
        plugin.runFor(target) { target.giveOrDrop(item) }
        text.send(sender, "give-scroll", tag("item", item.displayName()), tag("player", target.name))
    }

    private fun giveForged(sender: CommandSender, args: Array<out String>) {
        if (!sender.allowed("auraforge.give")) return
        val recipe = args.getOrNull(1)?.let { plugin.recipes[it] } ?: return text.send(sender, "unknown-recipe")
        val quality = args.getOrNull(2)?.toIntOrNull()?.coerceIn(1, 100) ?: 75
        val target = target(sender, args.getOrNull(3)) ?: return
        val item = plugin.items.create(recipe, quality, sender.name)
        plugin.runFor(target) { target.giveOrDrop(item) }
        text.send(sender, "give-item", tag("item", item.displayName()), tag("player", target.name))
    }

    private fun sendElements(sender: CommandSender) {
        text.send(sender, "elements-header")
        SpellElement.entries.forEach { element ->
            text.send(
                sender,
                "element-line",
                tag("name", text.render("element-${element.key}")),
                tag("rune", element.rune.name.lowercase()),
            )
        }
    }

    private fun stats(sender: CommandSender, args: Array<out String>) {
        val name = args.getOrNull(1)
        val record: SmithRecord? = when {
            name != null -> Bukkit.getPlayerExact(name)?.let { plugin.smiths[it.uniqueId] } ?: plugin.smiths.find(name)
            sender is Player -> plugin.smiths[sender.uniqueId]
            else -> return text.send(sender, "player-only")
        }
        if (record == null) return text.send(sender, "stats-empty")
        text.send(
            sender,
            "stats",
            tag("player", record.name),
            tag("crafted", record.crafted.toString()),
            tag("perfect", record.perfect.toString()),
            tag("best", record.best.toString()),
        )
    }

    private fun list(sender: CommandSender) {
        val player = sender as? Player ?: return text.send(sender, "player-only")
        val owned = plugin.forges.ownedBy(player.uniqueId)
        val ownedAltars = plugin.altars.ownedBy(player.uniqueId)
        if (owned.isEmpty() && ownedAltars.isEmpty()) return text.send(player, "list-empty")
        text.send(player, "list-header")
        owned.forEach { forge ->
            text.send(
                player,
                "list-line",
                tag("world", forge.pos.world),
                tag("x", forge.pos.x.toString()),
                tag("y", forge.pos.y.toString()),
                tag("z", forge.pos.z.toString()),
                tag("count", forge.size.toString()),
                tag("crafts", forge.crafts.toString()),
            )
        }
        ownedAltars.forEach { altar ->
            text.send(
                player,
                "list-altar-line",
                tag("world", altar.pos.world),
                tag("x", altar.pos.x.toString()),
                tag("y", altar.pos.y.toString()),
                tag("z", altar.pos.z.toString()),
                tag("combines", altar.combines.toString()),
            )
        }
    }

    private fun reload(sender: CommandSender) {
        if (!sender.allowed("auraforge.reload")) return
        if (!plugin.reloadSettings()) return text.send(sender, "reload-failed")
        plugin.forges.all().forEach(plugin.forges::refresh)
        plugin.altars.all().forEach { plugin.altars.refresh(it) }
        text.send(sender, "reloaded", tag("recipes", plugin.recipes.all.size.toString()), tag("spells", plugin.spells.book.all.size.toString()))
    }

    private fun target(sender: CommandSender, name: String?): Player? {
        if (name != null) {
            return Bukkit.getPlayerExact(name) ?: run {
                text.send(sender, "player-not-found")
                null
            }
        }
        return sender as? Player ?: run {
            text.send(sender, "player-only")
            null
        }
    }

    private fun CommandSender.allowed(permission: String): Boolean {
        if (hasPermission(permission)) return true
        text.send(this, "no-permission")
        return false
    }

    private fun onlineNames(): List<String> = Bukkit.getOnlinePlayers().map { it.name }

    private companion object {
        val SUBCOMMANDS = linkedMapOf(
            "recipes" to null,
            "spells" to null,
            "elements" to null,
            "list" to null,
            "stats" to null,
            "give" to "auraforge.give",
            "item" to "auraforge.give",
            "altar" to "auraforge.give",
            "rune" to "auraforge.give",
            "scroll" to "auraforge.give",
            "reload" to "auraforge.reload",
        )
    }
}
