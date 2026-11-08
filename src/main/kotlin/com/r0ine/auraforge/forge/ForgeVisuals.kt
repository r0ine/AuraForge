package com.r0ine.auraforge.forge

import com.r0ine.auraforge.AuraForgePlugin
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

class ForgeVisuals(
    private val plugin: AuraForgePlugin,
    private val forge: Forge,
    private val world: World,
) {
    private val center = forge.pos.center(world)
    private val orbitAnchor = center.clone().add(0.0, 1.2, 0.0)
    private val entities = mutableListOf<Entity>()
    private val orbit = mutableListOf<ItemDisplay>()
    private var label: TextDisplay? = null

    fun spawn() {
        val settings = plugin.settings.forge

        entities += world.spawn(forge.pos.corner(world), BlockDisplay::class.java) { display ->
            display.block = settings.blockData()
            display.prepare()
        }

        entities += world.spawn(center, Interaction::class.java) { hitbox ->
            hitbox.interactionWidth = 1.02f
            hitbox.interactionHeight = 1.02f
            hitbox.isResponsive = true
            hitbox.prepare()
            hitbox.persistentDataContainer.set(plugin.keys.forge, PersistentDataType.STRING, forge.id.toString())
        }

        val text = world.spawn(center.clone().add(0.0, settings.hologramHeight, 0.0), TextDisplay::class.java) { display ->
            display.billboard = Display.Billboard.CENTER
            display.alignment = TextDisplay.TextAlignment.CENTER
            display.isShadowed = true
            display.backgroundColor = Color.fromARGB(110, 12, 10, 16)
            display.lineWidth = 220
            display.prepare()
        }
        entities += text
        label = text
    }

    fun sync(items: List<ItemStack>, text: Component) {
        label?.text(text)
        while (orbit.size > items.size) {
            orbit.removeAt(orbit.lastIndex).remove()
        }
        items.forEachIndexed { index, item ->
            val display = orbit.getOrNull(index) ?: spawnOrbiter().also { orbit += it }
            display.setItemStack(item)
        }
    }

    fun animate(phase: Double, heat: Double, interval: Int) {
        val count = orbit.size
        if (count == 0) return
        val radius = if (count == 1) 0.0 else 0.45 - heat * 0.15
        orbit.forEachIndexed { index, display ->
            val angle = phase + index * (2 * PI / count)
            val bob = sin(phase * 1.6 + index) * 0.07 + heat * 0.15
            display.interpolationDelay = 0
            display.interpolationDuration = interval
            display.transformation = Transformation(
                Vector3f((cos(angle) * radius).toFloat(), bob.toFloat(), (sin(angle) * radius).toFloat()),
                Quaternionf().rotateY((phase * 1.4 + index).toFloat()),
                Vector3f(ITEM_SCALE),
                Quaternionf(),
            )
        }
    }

    fun ambient() {
        world.spawnParticle(Particle.SMALL_FLAME, center.clone().add(0.0, 1.02, 0.0), 2, 0.25, 0.0, 0.25, 0.005)
    }

    fun sparks(strength: Double) {
        val point = orbitAnchor
        world.spawnParticle(Particle.CRIT, point, (6 + strength * 18).toInt(), 0.25, 0.15, 0.25, 0.25)
        if (strength >= 1.0) world.spawnParticle(Particle.LAVA, point, 4, 0.2, 0.1, 0.2, 0.0)
    }

    fun burst() {
        world.spawnParticle(Particle.FLAME, orbitAnchor, 40, 0.3, 0.3, 0.3, 0.06)
        world.spawnParticle(Particle.ELECTRIC_SPARK, orbitAnchor, 25, 0.4, 0.4, 0.4, 0.1)
        world.playSound(center, Sound.BLOCK_ANVIL_USE, 0.9f, 0.6f)
        world.playSound(center, Sound.BLOCK_FIRE_EXTINGUISH, 0.6f, 1.2f)
    }

    fun remove() {
        orbit.forEach { it.remove() }
        orbit.clear()
        entities.forEach { it.remove() }
        entities.clear()
        label = null
    }

    private fun spawnOrbiter(): ItemDisplay = world.spawn(orbitAnchor, ItemDisplay::class.java) { display ->
        display.itemDisplayTransform = ItemDisplay.ItemDisplayTransform.FIXED
        display.brightness = Display.Brightness(15, 15)
        display.transformation = Transformation(Vector3f(), Quaternionf(), Vector3f(ITEM_SCALE), Quaternionf())
        display.prepare()
    }

    private fun Entity.prepare() {
        isPersistent = false
        setGravity(false)
        isInvulnerable = true
        isSilent = true
    }

    private companion object {
        const val ITEM_SCALE = 0.42f
    }
}
