package com.r0ine.auraforge.command

import com.r0ine.auraforge.AuraForgePlugin
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
            "recipes" -> plugin.sendRecipes(sender)
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
                "give", "stats" -> onlineNames()
                "item" -> plugin.recipes.ids
                else -> emptyList()
            }
            3 -> when (sub) {
                "give" -> listOf("1", "16", "64")
                "item" -> listOf("50", "75", "95")
                else -> emptyList()
            }
            4 -> if (sub == "item") onlineNames() else emptyList()
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

    private fun giveForged(sender: CommandSender, args: Array<out String>) {
        if (!sender.allowed("auraforge.give")) return
        val recipe = args.getOrNull(1)?.let { plugin.recipes[it] } ?: return text.send(sender, "unknown-recipe")
        val quality = args.getOrNull(2)?.toIntOrNull()?.coerceIn(1, 100) ?: 75
        val target = target(sender, args.getOrNull(3)) ?: return
        val item = plugin.items.create(recipe, quality, sender.name)
        plugin.runFor(target) { target.giveOrDrop(item) }
        text.send(sender, "give-item", tag("item", item.displayName()), tag("player", target.name))
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
        if (owned.isEmpty()) return text.send(player, "list-empty")
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
    }

    private fun reload(sender: CommandSender) {
        if (!sender.allowed("auraforge.reload")) return
        if (!plugin.reloadSettings()) return text.send(sender, "reload-failed")
        plugin.forges.all().forEach(plugin.forges::refresh)
        text.send(sender, "reloaded", tag("count", plugin.recipes.all.size.toString()))
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
            "list" to null,
            "stats" to null,
            "give" to "auraforge.give",
            "item" to "auraforge.give",
            "reload" to "auraforge.reload",
        )
    }
}
