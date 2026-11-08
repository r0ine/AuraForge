package com.r0ine.auraforge.progress

import com.r0ine.auraforge.AuraForgePlugin
import com.r0ine.auraforge.ability.AbilityItems
import com.r0ine.auraforge.api.SpellElement
import com.r0ine.auraforge.core.rich
import com.r0ine.auraforge.core.tag
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Vector3f
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.random.Random

data class AbilityBoost(val power: Double, val cooldown: Double) {
    companion object {
        val NONE = AbilityBoost(1.0, 1.0)
    }
}

class ProgressService(private val plugin: AuraForgePlugin) {

    private val procCounters = ConcurrentHashMap<UUID, Int>()

    private val settings get() = plugin.settings.progression
    private val enhanceSettings get() = plugin.settings.enhance

    fun xpToNext(level: Int): Int = settings.xpBase + settings.xpStep * (level - 1)

    fun abilityBoost(progress: Progress?): AbilityBoost {
        if (progress == null) return AbilityBoost.NONE
        val power = 1.0 + progress.plus * enhanceSettings.powerPerPlus + progress.value(Talent.SURGE)
        val cooldown = (1.0 - progress.value(Talent.TEMPO)).coerceAtLeast(0.4)
        return AbilityBoost(power, cooldown)
    }

    fun weaponIn(player: Player): Pair<ItemStack, Progress>? {
        val item = player.inventory.itemInMainHand
        val forged = plugin.items.read(item) ?: return null
        if (forged.isArmor) return null
        return item to (plugin.items.progress(item) ?: return null)
    }

    fun armorReduction(player: Player): Double {
        val plus = player.inventory.armorContents.sumOf { plugin.items.progress(it)?.plus ?: 0 }
        return (plus * enhanceSettings.armorPerPlus).coerceAtMost(0.4)
    }

    fun addXp(player: Player, amount: Int) {
        if (amount <= 0) return
        val item = player.inventory.itemInMainHand
        val before = weaponIn(player)?.second ?: return
        val max = settings.maxLevel
        if (before.level >= max) return

        val after = plugin.items.update(item) { progress ->
            var level = progress.level
            var xp = progress.xp + amount
            while (level < max && xp >= xpToNext(level)) {
                xp -= xpToNext(level)
                level++
            }
            progress.copy(level = level, xp = if (level >= max) 0 else xp)
        } ?: return
        player.inventory.setItemInMainHand(item)

        if (after.level > before.level) {
            plugin.text.send(player, "level-up", tag("item", item.displayName()), tag("level", after.level.toString()), tag("points", after.points.toString()))
            player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f)
            playLevelUp(player, item)
        }
    }

    fun learn(player: Player, talent: Talent): Progress.Lock? {
        val (item, progress) = weaponIn(player) ?: return Progress.Lock.POINTS
        progress.lockReason(talent)?.let { return it }
        plugin.items.update(item) { it.copy(talents = it.talents + (talent to it.rank(talent) + 1)) }
        player.inventory.setItemInMainHand(item)
        player.playSound(player.location, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.4f)
        player.world.spawnParticle(Particle.ENCHANT, player.location.add(0.0, 1.2, 0.0), 30, 0.4, 0.6, 0.4, 0.8)
        return null
    }

    fun reset(player: Player): Boolean {
        val (item, progress) = weaponIn(player) ?: return false
        if (progress.talents.isEmpty()) return false
        val cost = settings.resetLevels
        if (!player.hasPermission("auraforge.admin") && player.level < cost) {
            plugin.text.bar(player, "talent-reset-cost", tag("levels", cost.toString()))
            return false
        }
        if (!player.hasPermission("auraforge.admin")) player.level -= cost
        plugin.items.update(item) { it.copy(talents = emptyMap()) }
        player.inventory.setItemInMainHand(item)
        player.playSound(player.location, Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.6f)
        plugin.text.bar(player, "talent-reset")
        return true
    }

    fun socket(player: Player, element: SpellElement): Boolean {
        val (item, progress) = weaponIn(player) ?: return false
        if (progress.element == element) {
            plugin.text.bar(player, "socket-same")
            return false
        }
        plugin.items.update(item) { it.copy(element = element) }
        player.inventory.setItemInMainHand(item)
        player.playSound(player.location, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 1.2f)
        player.world.spawnParticle(element.particle, player.location.add(0.0, 1.0, 0.0), 40, 0.5, 0.6, 0.5, 0.05)
        plugin.text.bar(player, "socket-done", rich("element", elementTag(element)))
        return true
    }

    enum class EnhanceResult { SUCCESS, FAILED, DOWNGRADED, MAXED, NO_ITEM, NO_MATERIALS }

    fun enhance(player: Player): EnhanceResult {
        val item = player.inventory.itemInMainHand
        val progress = plugin.items.progress(item) ?: return EnhanceResult.NO_ITEM
        if (progress.plus >= enhanceSettings.maxPlus) return EnhanceResult.MAXED

        val cost = enhanceSettings.cost(progress.plus)
        if (cost.any { (material, amount) -> !player.inventory.containsAtLeast(ItemStack(material), amount) }) {
            return EnhanceResult.NO_MATERIALS
        }
        cost.forEach { (material, amount) -> player.inventory.removeItem(ItemStack(material, amount)) }

        val roll = Random.nextInt(100)
        val result = when {
            roll < enhanceSettings.chance(progress.plus) -> EnhanceResult.SUCCESS
            progress.plus >= enhanceSettings.downgradeFrom -> EnhanceResult.DOWNGRADED
            else -> EnhanceResult.FAILED
        }
        val next = when (result) {
            EnhanceResult.SUCCESS -> progress.plus + 1
            EnhanceResult.DOWNGRADED -> progress.plus - 1
            else -> progress.plus
        }
        if (next != progress.plus) {
            plugin.items.update(item) { it.copy(plus = next) }
            player.inventory.setItemInMainHand(item)
        }
        if (result == EnhanceResult.SUCCESS && next >= enhanceSettings.broadcastFrom) {
            val message = plugin.text.render("enhance-broadcast", tag("player", player.name), tag("item", item.displayName()))
            Bukkit.getOnlinePlayers().forEach { it.sendMessage(message) }
        }
        return result
    }

    fun costLines(plus: Int): List<Component> {
        val cost = enhanceSettings.cost(plus)
        if (cost.isEmpty()) return listOf(plugin.text.item("enhance-free"))
        return cost.map { (material, amount) ->
            plugin.text.item("enhance-cost-line", tag("amount", amount.toString()), tag("material", Component.translatable(material.translationKey())))
        }
    }

    fun onProc(player: Player, target: LivingEntity, element: SpellElement, progress: Progress): Double {
        val strength = 1.0 + progress.value(Talent.AMPLIFY)
        val extra = applyElement(player, target, element, strength)

        if (progress.has(Talent.CHAIN) && Random.nextDouble() < progress.value(Talent.CHAIN)) {
            target.getNearbyEntities(4.0, 2.0, 4.0)
                .filterIsInstance<LivingEntity>()
                .firstOrNull { it != player && canHit(player, it) }
                ?.let { second ->
                    val bonus = applyElement(player, second, element, strength * 0.7)
                    if (bonus > 0) second.damage(bonus)
                    player.world.spawnParticle(element.particle, second.location.add(0.0, 1.0, 0.0), 12, 0.3, 0.5, 0.3, 0.02)
                }
        }
        if (progress.has(Talent.SIPHON)) {
            val max = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0
            player.health = (player.health + progress.value(Talent.SIPHON)).coerceAtMost(max)
        }
        if (progress.has(Talent.ELEMENT_WARD)) {
            player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, 60, 0, false, false, true))
        }

        val count = procCounters.merge(player.uniqueId, 1, Int::plus) ?: 1
        if (progress.has(Talent.OVERLOAD) && count % 5 == 0) overload(player, target, element, strength)
        if (progress.has(Talent.AVATAR) && count % 12 == 0) avatar(player, element)
        return extra
    }

    private fun applyElement(player: Player, target: LivingEntity, element: SpellElement, strength: Double): Double {
        val world = target.world
        val at = target.location.add(0.0, target.height / 2, 0.0)
        world.spawnParticle(element.particle, at, 16, 0.3, 0.4, 0.3, 0.03)
        return when (element) {
            SpellElement.FIRE -> {
                target.fireTicks = maxOf(target.fireTicks, (60 * strength).toInt())
                world.playSound(at, Sound.ITEM_FIRECHARGE_USE, 0.6f, 1.3f)
                1.0 * strength
            }
            SpellElement.WIND -> {
                val push = target.location.toVector().subtract(player.location.toVector()).setY(0)
                if (push.lengthSquared() > 0.001) push.normalize()
                target.velocity = push.multiply(0.6 * strength).setY(0.55 * strength)
                world.playSound(at, Sound.ENTITY_BREEZE_WIND_BURST, 0.7f, 1.2f)
                0.5 * strength
            }
            SpellElement.ICE -> {
                target.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, (50 * strength).toInt(), 1))
                target.freezeTicks = (target.freezeTicks + (60 * strength).toInt()).coerceAtMost(target.maxFreezeTicks + 80)
                world.playSound(at, Sound.BLOCK_GLASS_BREAK, 0.6f, 1.6f)
                1.0 * strength
            }
            SpellElement.THUNDER -> {
                world.strikeLightningEffect(target.location)
                2.5 * strength
            }
            SpellElement.EARTH -> {
                target.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, (40 * strength).toInt(), 0))
                world.spawnParticle(Particle.BLOCK, target.location, 30, 0.5, 0.1, 0.5, Material.DIRT.createBlockData())
                world.playSound(at, Sound.BLOCK_ROOTED_DIRT_BREAK, 1f, 0.6f)
                1.8 * strength
            }
        }
    }

    private fun overload(player: Player, target: LivingEntity, element: SpellElement, strength: Double) {
        val center = target.location.add(0.0, 0.8, 0.0)
        val world = center.world
        world.spawnParticle(Particle.EXPLOSION, center, 1)
        world.spawnParticle(element.particle, center, 80, 1.6, 0.8, 1.6, 0.1)
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.4f)
        target.getNearbyEntities(3.5, 2.0, 3.5)
            .filterIsInstance<LivingEntity>()
            .filter { it != player && canHit(player, it) }
            .forEach {
                it.damage(3.0 * strength)
                it.velocity = it.location.toVector().subtract(center.toVector()).normalize().multiply(0.5).setY(0.35)
            }
        plugin.text.bar(player, "proc-overload", rich("element", elementTag(element)))
    }

    private fun avatar(player: Player, element: SpellElement) {
        val spell = plugin.spells.book.combine(element, element) ?: return
        plugin.spellEffects.launch(
            player,
            spell.copy(power = spell.power * 0.6, radius = spell.radius * 0.8, duration = (spell.duration * 0.6).toInt()),
        )
        plugin.text.bar(player, "proc-avatar", rich("name", spell.name))
    }

    fun canHit(player: Player, entity: LivingEntity): Boolean = when (entity) {
        is ArmorStand -> false
        is Player -> entity != player && player.world.pvp && entity.gameMode.let {
            it != org.bukkit.GameMode.CREATIVE && it != org.bukkit.GameMode.SPECTATOR
        }
        else -> !entity.isInvulnerable
    }

    fun onKill(player: Player, progress: Progress) {
        if (progress.has(Talent.HUNT)) {
            player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 60, progress.rank(Talent.HUNT) - 1, false, false, true))
        }
        if (progress.has(Talent.MOMENTUM)) {
            plugin.abilities.shorten(player.uniqueId, (progress.value(Talent.MOMENTUM) * 1000).toLong())
        }
    }

    fun forget(playerId: UUID) {
        procCounters.remove(playerId)
    }

    private fun playLevelUp(player: Player, item: ItemStack) {
        val origin = player.location.add(0.0, 2.3, 0.0)
        val dispatcher = plugin.spellEffects.dispatcherFor(origin) ?: return
        plugin.scope.launch(dispatcher) {
            val world = origin.world
            val display = world.spawn(origin, ItemDisplay::class.java) { entity ->
                entity.setItemStack(item.clone())
                entity.isPersistent = false
                entity.billboard = Display.Billboard.FIXED
                entity.brightness = Display.Brightness(15, 15)
                entity.transformation = Transformation(Vector3f(), AxisAngle4f(), Vector3f(0.1f), AxisAngle4f())
            }
            dispatcher.sleep(2)
            val steps = 6
            for (step in 1..steps) {
                if (!display.isValid) break
                val angle = (step * PI / 2).toFloat()
                display.interpolationDelay = 0
                display.interpolationDuration = 5
                display.transformation = Transformation(
                    Vector3f(0f, step * 0.12f, 0f),
                    AxisAngle4f(angle, 0f, 1f, 0f),
                    Vector3f(0.9f),
                    AxisAngle4f(),
                )
                world.spawnParticle(Particle.TOTEM_OF_UNDYING, origin.clone().add(0.0, step * 0.12, 0.0), 6, 0.3, 0.2, 0.3, 0.2)
                dispatcher.sleep(5)
            }
            world.spawnParticle(Particle.FIREWORK, origin.clone().add(0.0, 0.8, 0.0), 30, 0.2, 0.2, 0.2, 0.15)
            display.remove()
        }
    }

    fun elementTag(element: SpellElement): String = "<color:#${String.format("%06X", element.rgb)}>${element.display}</color>"

    fun plusLabel(plus: Int): Component =
        Component.text("+$plus", AbilityItems.plusColor(plus))
}
