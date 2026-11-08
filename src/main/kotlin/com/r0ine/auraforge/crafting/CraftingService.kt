package com.r0ine.auraforge.crafting

import com.r0ine.auraforge.AuraForgePlugin
import com.r0ine.auraforge.ability.Tier
import com.r0ine.auraforge.api.ForgeCraftEvent
import com.r0ine.auraforge.core.CraftingSettings
import com.r0ine.auraforge.core.EntityDispatcher
import com.r0ine.auraforge.core.giveOrDrop
import com.r0ine.auraforge.core.rich
import com.r0ine.auraforge.core.tag
import com.r0ine.auraforge.forge.Forge
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.title.Title
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

class CraftingService(private val plugin: AuraForgePlugin) : Listener {

    private enum class Hit(val score: Double, val message: String) {
        PERFECT(1.0, "hit-perfect"),
        GOOD(0.7, "hit-good"),
        NEAR(0.35, "hit-near"),
        MISS(0.0, "hit-miss"),
    }

    private class Session(
        val player: Player,
        val forge: Forge,
        val recipe: ForgeRecipe,
        val dispatcher: EntityDispatcher,
    ) {
        val pressedAt = AtomicLong()
        lateinit var job: Job
    }

    private val sessions = ConcurrentHashMap<UUID, Session>()

    fun isCrafting(player: Player): Boolean = sessions.containsKey(player.uniqueId)

    fun press(player: Player): Boolean {
        val session = sessions[player.uniqueId] ?: return false
        session.pressedAt.compareAndSet(0L, System.nanoTime())
        return true
    }

    fun start(player: Player, forge: Forge) {
        val text = plugin.text
        if (isCrafting(player)) return text.bar(player, "crafting-already")
        if (forge.size == 0) return text.bar(player, "forge-empty")
        val recipe = plugin.recipes.match(forge.materials()) ?: return text.bar(player, "crafting-no-recipe")
        if (!forge.crafter.compareAndSet(null, player.uniqueId)) return text.bar(player, "forge-busy")

        val session = Session(player, forge, recipe, EntityDispatcher(plugin, player))
        session.job = plugin.scope.launch(session.dispatcher, CoroutineStart.LAZY) {
            try {
                session.run()
            } finally {
                sessions.remove(player.uniqueId, session)
                forge.crafter.compareAndSet(player.uniqueId, null)
                plugin.forges.refresh(forge)
            }
        }
        sessions[player.uniqueId] = session
        session.job.start()
    }

    fun cancel(playerId: UUID) {
        sessions[playerId]?.job?.cancel()
    }

    @EventHandler(ignoreCancelled = true)
    fun onSneak(event: PlayerToggleSneakEvent) {
        if (event.isSneaking) press(event.player)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        cancel(event.player.uniqueId)
    }

    private suspend fun Session.run() {
        val settings = plugin.settings.crafting
        plugin.forges.refresh(forge)
        player.playSound(player.location, Sound.BLOCK_SMITHING_TABLE_USE, 1f, 0.9f)
        plugin.text.bar(player, "crafting-start", rich("recipe", recipe.name))
        dispatcher.sleep(20)

        val hits = ArrayList<Hit>(settings.rounds)
        for (round in 0 until settings.rounds) {
            if (!nearForge()) return plugin.text.bar(player, "crafting-walked-away")
            val hit = strike(round, settings)
            hits += hit
            react(hit)
            dispatcher.sleep(settings.pauseTicks)
        }
        if (!nearForge()) return plugin.text.bar(player, "crafting-walked-away")
        finish(hits)
    }

    private suspend fun Session.strike(round: Int, settings: CraftingSettings): Hit {
        val sweepNanos = (settings.sweepMs * settings.speedUp.pow(round)).toLong().coerceAtLeast(350) * 1_000_000
        val half = settings.zoneWidth / 2
        val target = Random.nextDouble(0.3 + half, 1.0 - half)
        pressedAt.set(0L)
        val startedAt = System.nanoTime()

        while (true) {
            val pressed = pressedAt.get()
            val moment = if (pressed != 0L) pressed else System.nanoTime()
            val position = (moment - startedAt).toDouble() / sweepNanos
            if (pressed != 0L) return judge(position, target, settings)
            if (position >= 1.0) return Hit.MISS

            player.sendActionBar(
                plugin.text.render(
                    "crafting-bar",
                    tag("bar", bar(position, target, settings)),
                    tag("round", "${round + 1}"),
                    tag("rounds", "${settings.rounds}"),
                ),
            )
            dispatcher.sleep(1)
        }
    }

    private fun judge(position: Double, target: Double, settings: CraftingSettings): Hit {
        val distance = abs(position - target)
        return when {
            distance <= settings.perfectWidth / 2 -> Hit.PERFECT
            distance <= settings.zoneWidth / 2 -> Hit.GOOD
            distance <= settings.zoneWidth / 2 + 0.07 -> Hit.NEAR
            else -> Hit.MISS
        }
    }

    private fun bar(position: Double, target: Double, settings: CraftingSettings): Component {
        val cells = settings.barLength
        val marker = (position * cells).toInt().coerceIn(0, cells - 1)
        val builder = Component.text()
        var runColor: TextColor? = null
        val run = StringBuilder()

        fun flush() {
            if (run.isNotEmpty()) builder.append(Component.text(run.toString(), runColor))
            run.clear()
        }

        for (cell in 0 until cells) {
            val distance = abs((cell + 0.5) / cells - target)
            val color = when {
                cell == marker -> NamedTextColor.WHITE
                distance <= settings.perfectWidth / 2 -> NamedTextColor.GOLD
                distance <= settings.zoneWidth / 2 -> NamedTextColor.GREEN
                else -> NamedTextColor.DARK_GRAY
            }
            if (color != runColor) {
                flush()
                runColor = color
            }
            run.append(if (cell == marker) '┃' else '▌')
        }
        flush()
        return builder.build()
    }

    private fun Session.react(hit: Hit) {
        val (sound, pitch) = when (hit) {
            Hit.PERFECT -> Sound.BLOCK_ANVIL_USE to 1.6f
            Hit.GOOD -> Sound.BLOCK_ANVIL_USE to 1.15f
            Hit.NEAR -> Sound.BLOCK_ANVIL_LAND to 0.8f
            Hit.MISS -> Sound.BLOCK_NOTE_BLOCK_BASS to 0.5f
        }
        player.world.playSound(player.location, sound, 0.7f, pitch)
        if (hit == Hit.PERFECT) player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 2f)
        plugin.forges.effect(forge) { sparks(hit.score) }
        plugin.text.bar(player, hit.message)
    }

    private fun Session.finish(hits: List<Hit>) {
        val text = plugin.text
        val quality = (hits.sumOf { it.score } / hits.size * 100).roundToInt().coerceIn(1, 100)
        val forged = plugin.items.create(recipe, quality, player.name)

        val event = ForgeCraftEvent(player, recipe.id, recipe.ability, quality, forged)
        if (!event.callEvent()) return text.bar(player, "crafting-cancelled")
        if (!forge.consume(recipe.ingredients)) return text.bar(player, "crafting-recipe-changed")

        val result = event.result
        plugin.markDirty()
        plugin.forges.effect(forge) { burst() }
        player.giveOrDrop(result)
        plugin.smiths.record(player.uniqueId, player.name, quality, hits.count { it == Hit.PERFECT })

        val tier = Tier.of(quality)
        text.send(
            player,
            "crafting-done",
            tag("item", result.displayName()),
            tag("quality", quality.toString()),
            tag("tier", text.render(tier.key)),
        )
        if (tier == Tier.LEGENDARY) {
            player.showTitle(
                Title.title(
                    text.render("title-legendary"),
                    text.render("subtitle-legendary", tag("item", result.displayName())),
                    Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2200), Duration.ofMillis(600)),
                ),
            )
            player.playSound(player.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.1f)
        } else {
            player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f)
        }
    }

    private fun Session.nearForge(): Boolean {
        if (!player.isOnline || plugin.forges[forge.id] !== forge) return false
        if (player.world.name != forge.pos.world) return false
        return player.location.distanceSquared(forge.pos.center(player.world)) <= 64.0
    }
}
