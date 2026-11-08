package com.r0ine.auraforge.arcane

import com.r0ine.auraforge.AuraForgePlugin
import com.r0ine.auraforge.api.SpellElement
import net.kyori.adventure.text.Component
import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.Entity
import org.bukkit.entity.Interaction
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.TextDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class AltarVisuals(
    private val plugin: AuraForgePlugin,
    private val altar: Altar,
    private val world: World,
) {
    private val center = altar.pos.center(world)
    private val entities = mutableListOf<Entity>()
    private var base: BlockDisplay? = null
    private var label: TextDisplay? = null
    private val runes = arrayOfNulls<ItemDisplay>(2)

    fun spawn() {
        base = world.spawn(altar.pos.corner(world), BlockDisplay::class.java) { d ->
            d.block = plugin.settings.arcane.blockData()
            d.brightness = Display.Brightness(15, 15)
            d.isPersistent = false
            d.setGravity(false)
            d.isInvulnerable = true
        }.also { entities += it }

        entities += world.spawn(center, Interaction::class.java) { hitbox ->
            hitbox.interactionWidth = 1.02f
            hitbox.interactionHeight = 1.4f
            hitbox.isResponsive = true
            hitbox.setGravity(false)
            hitbox.isInvulnerable = true
            hitbox.isPersistent = false
            hitbox.persistentDataContainer.set(plugin.keys.altar, PersistentDataType.STRING, altar.id.toString())
        }

        label = world.spawn(center.clone().add(0.0, plugin.settings.arcane.hologramHeight, 0.0), TextDisplay::class.java) { d ->
            d.billboard = Display.Billboard.CENTER
            d.alignment = TextDisplay.TextAlignment.CENTER
            d.isShadowed = true
            d.backgroundColor = Color.fromARGB(110, 10, 8, 30)
            d.lineWidth = 220
            d.setGravity(false)
            d.isInvulnerable = true
            d.isPersistent = false
        }.also { entities += it }
    }

    fun sync(rune1: SpellElement?, rune2: SpellElement?, status: Component) {
        label?.text(status)
        updateRune(0, rune1)
        updateRune(1, rune2)
    }

    private fun updateRune(index: Int, element: SpellElement?) {
        val existing = runes[index]
        if (element == null) {
            existing?.remove()
            if (existing != null) entities.remove(existing)
            runes[index] = null
            return
        }
        val disp = existing ?: world.spawn(center.clone().add(0.0, 1.1, 0.0), ItemDisplay::class.java) { d ->
            d.setItemStack(ItemStack(element.rune))
            d.itemDisplayTransform = ItemDisplay.ItemDisplayTransform.FIXED
            d.brightness = Display.Brightness(15, 15)
            d.setGravity(false)
            d.isPersistent = false
            d.isInvulnerable = true
        }.also { entities += it; runes[index] = it }
        disp.setItemStack(ItemStack(element.rune))
    }

    fun animate(phase: Double, interval: Int, combining: Boolean) {
        base?.let { block ->
            block.interpolationDelay = 0
            block.interpolationDuration = interval
            block.transformation = Transformation(
                Vector3f(0f, (sin(phase * 0.5) * 0.04).toFloat(), 0f),
                Quaternionf().rotateY((phase * 0.12).toFloat()),
                Vector3f(1f),
                Quaternionf(),
            )
        }
        runes.forEachIndexed { index, display ->
            if (display == null) return@forEachIndexed
            val offset = if (combining) phase * 1.4 else phase * 0.6
            val angle = offset + index * PI
            val radius = if (combining) 0.5 - (phase % 1.0) * 0.4 else 0.55
            val px = cos(angle) * radius
            val pz = sin(angle) * radius
            val py = 1.15 + sin(phase + index) * 0.12
            display.interpolationDelay = 0
            display.interpolationDuration = interval
            display.transformation = Transformation(
                Vector3f(px.toFloat(), py.toFloat() - 0.5f, pz.toFloat()),
                Quaternionf().rotateY((phase * 1.8 + index).toFloat()),
                Vector3f(0.4f),
                Quaternionf(),
            )
        }
    }

    fun ambient() {
        world.spawnParticle(Particle.ENCHANT, center.clone().add(0.0, 1.5, 0.0), 6, 0.6, 0.4, 0.6, 0.6)
    }

    fun combineBurst(result: SpellElement) {
        val point = center.clone().add(0.0, 1.3, 0.0)
        world.spawnParticle(result.particle, point, 40, 0.5, 0.5, 0.5, 0.15)
        world.spawnParticle(Particle.FLASH, point, 1)
        world.playSound(point, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.4f)
        world.playSound(point, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 0.8f)
    }

    fun fail() {
        val point = center.clone().add(0.0, 1.0, 0.0)
        world.spawnParticle(Particle.SMOKE, point, 20, 0.5, 0.5, 0.5, 0.05)
        world.playSound(point, Sound.ENTITY_VILLAGER_NO, 1f, 0.7f)
    }

    fun remove() {
        entities.forEach { it.remove() }
        entities.clear()
        runes.fill(null)
        base = null
        label = null
    }
}
