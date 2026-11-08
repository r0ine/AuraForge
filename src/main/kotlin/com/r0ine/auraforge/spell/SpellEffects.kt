package com.r0ine.auraforge.spell

import com.r0ine.auraforge.AuraForgePlugin
import com.r0ine.auraforge.api.SpellElement
import com.r0ine.auraforge.core.RegionDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.block.data.BlockData
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class SpellEffects(private val plugin: AuraForgePlugin) {

    fun dispatcherFor(location: Location): RegionDispatcher? {
        val world = location.world ?: return null
        return RegionDispatcher(plugin, world, location.blockX shr 4, location.blockZ shr 4)
    }

    fun playShieldWave(player: Player, radius: Double, power: Double, damage: Double): Job {
        val dispatcher = dispatcherFor(player.location) ?: return plugin.scope.launch { }
        val spell = Spell(
            id = "shield_wave",
            name = "Kalkan Dalgası",
            description = "",
            elements = listOf(com.r0ine.auraforge.api.SpellElement.WIND),
            effect = "gale_wave",
            cooldown = 0.0,
            power = power,
            radius = radius,
            range = 0.0,
            duration = 20,
            result = org.bukkit.Material.AIR,
        )
        return plugin.scope.launch(dispatcher) { galeWave(player, spell.copy(power = damage), dispatcher) }
    }

    fun launch(player: Player, spell: Spell): Job {
        val dispatcher = dispatcherFor(player.location) ?: return plugin.scope.launch { }
        return plugin.scope.launch(dispatcher) {
            when (spell.effect) {
                "gale_wave" -> galeWave(player, spell, dispatcher)
                "fire_tornado" -> fireTornado(player, spell, dispatcher)
                "plasma_lance" -> plasmaLance(player, spell, dispatcher)
                "magma_pillar" -> magmaPillar(player, spell, dispatcher)
                "glacier_wall" -> glacierWall(player, spell, dispatcher)
                "thunderclap" -> thunderclap(player, spell, dispatcher)
                "blizzard" -> blizzard(player, spell, dispatcher)
                "inferno" -> inferno(player, spell, dispatcher)
                "storm_surge" -> stormSurge(player, spell, dispatcher)
                "stone_fist" -> stoneFist(player, spell, dispatcher)
                "steam_burst" -> steamBurst(player, spell, dispatcher)
                "dust_devil" -> dustDevil(player, spell, dispatcher)
                else -> genericBurst(player, spell)
            }
        }
    }

    private suspend fun galeWave(player: Player, spell: Spell, dispatcher: RegionDispatcher) {
        val world = player.world
        val center = player.location.clone().add(0.0, 0.1, 0.0)
        val radius = spell.radius
        val damage = spell.power * 3.0
        val segments = 20
        val displays = mutableListOf<BlockDisplay>()

        world.playSound(center, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.4f, 1.6f)
        world.playSound(center, Sound.ENTITY_BREEZE_WIND_BURST, 1.2f, 0.7f)

        for (i in 0 until segments) {
            val angle = i * (2.0 * PI / segments)
            val point = center.clone().add(cos(angle) * 0.4, 0.0, sin(angle) * 0.4)
            val display = world.spawn(point, BlockDisplay::class.java) { d ->
                d.block = Material.WHITE_STAINED_GLASS.createBlockData()
                d.brightness = Display.Brightness(15, 15)
                d.transformation = Transformation(
                    Vector3f(-0.1f, 0f, -0.1f),
                    Quaternionf().rotateY(angle.toFloat()),
                    Vector3f(0.2f, 0.4f, 0.2f),
                    Quaternionf(),
                )
                d.isPersistent = false
                d.setGravity(false)
            }
            displays += display
        }

        val hit = HashSet<java.util.UUID>()
        val steps = 20
        for (step in 1..steps) {
            val ratio = step.toDouble() / steps
            val currentRadius = radius * ratio
            displays.forEachIndexed { index, display ->
                val angle = index * (2.0 * PI / segments)
                display.interpolationDelay = 0
                display.interpolationDuration = 1
                display.transformation = Transformation(
                    Vector3f((cos(angle) * currentRadius - 0.1).toFloat(), 0f, (sin(angle) * currentRadius - 0.1).toFloat()),
                    Quaternionf().rotateY((angle + ratio * PI).toFloat()),
                    Vector3f(0.2f, (0.6 * (1.0 - ratio * 0.5)).toFloat(), 0.2f),
                    Quaternionf(),
                )
            }

            world.spawnParticle(Particle.CLOUD, center, 10, currentRadius, 0.1, currentRadius, 0.0)
            world.spawnParticle(Particle.SWEEP_ATTACK, center.clone().add(0.0, 0.5, 0.0), 2, currentRadius * 0.7, 0.3, currentRadius * 0.7, 0.0)

            val centerVec = center.toVector()
            player.getNearbyEntities(currentRadius + 0.5, 2.0, currentRadius + 0.5)
                .asSequence()
                .filterIsInstance<LivingEntity>()
                .filter { it.uniqueId != player.uniqueId && it.uniqueId !in hit && canHit(player, it) }
                .filter { it is LivingEntity && it !is ArmorStand && !it.isInvulnerable }
                .filter { Bukkit.isOwnedByCurrentRegion(it) }
                .filter {
                    val d = it.location.toVector().setY(centerVec.y).distance(centerVec)
                    d in (currentRadius - 1.0)..(currentRadius + 1.0)
                }
                .forEach { target ->
                    hit += target.uniqueId
                    val push = target.location.toVector().subtract(centerVec).setY(0.0)
                    val dir = if (push.lengthSquared() < 1e-4) Vector(cos(step * 0.3), 0.0, sin(step * 0.3)) else push.normalize()
                    target.velocity = dir.multiply(spell.power * 2.0).setY(0.75)
                    target.damage(damage, player)
                }
            dispatcher.sleep(1)
        }

        displays.forEach { it.remove() }
    }

    private suspend fun fireTornado(player: Player, spell: Spell, dispatcher: RegionDispatcher) {
        val world = player.world
        val anchor = player.location.add(player.location.direction.setY(0.0).normalize().multiply(spell.range * 0.5))
        val direction = player.location.direction.setY(0.0).let {
            if (it.lengthSquared() < 1e-4) Vector(1.0, 0.0, 0.0) else it.normalize()
        }
        val ticks = spell.duration
        val displays = (0 until 14).map { tier ->
            val y = tier * 0.42
            world.spawn(anchor.clone().add(0.0, y, 0.0), ItemDisplay::class.java) { d ->
                d.setItemStack(ItemStack(if (tier % 2 == 0) Material.FIRE_CHARGE else Material.MAGMA_CREAM))
                d.itemDisplayTransform = ItemDisplay.ItemDisplayTransform.FIXED
                d.brightness = Display.Brightness(15, 15)
                d.transformation = Transformation(
                    Vector3f(0f, 0f, 0f),
                    Quaternionf(),
                    Vector3f(0.6f + tier * 0.03f),
                    Quaternionf(),
                )
                d.isPersistent = false
                d.setGravity(false)
            }
        }

        world.playSound(anchor, Sound.ITEM_FIRECHARGE_USE, 1.5f, 0.4f)
        world.playSound(anchor, Sound.ENTITY_BLAZE_SHOOT, 1f, 0.6f)

        val center = anchor.clone()
        for (tick in 0 until ticks) {
            val progress = tick.toDouble() / ticks
            val drift = direction.clone().multiply(progress * spell.range * 0.6)
            val core = anchor.clone().add(drift)
            center.x = core.x; center.y = core.y; center.z = core.z

            displays.forEachIndexed { tier, display ->
                val baseAngle = tick * 0.6 + tier * 0.9
                val wobble = sin(tick * 0.15 + tier) * 0.15
                val radius = 0.6 + wobble + tier * 0.03
                val px = core.x + cos(baseAngle) * radius
                val pz = core.z + sin(baseAngle) * radius
                val py = core.y + tier * 0.42
                display.teleport(Location(world, px, py, pz))
                display.interpolationDelay = 0
                display.interpolationDuration = 2
                display.transformation = Transformation(
                    Vector3f(-0.3f, 0f, -0.3f),
                    Quaternionf().rotateY(baseAngle.toFloat()).rotateX((tier * 0.1).toFloat()),
                    Vector3f(0.55f + tier * 0.03f),
                    Quaternionf(),
                )
            }
            world.spawnParticle(Particle.FLAME, core.clone().add(0.0, 2.0, 0.0), 12, 0.6, 1.5, 0.6, 0.05)
            world.spawnParticle(Particle.LAVA, core.clone().add(0.0, 1.0, 0.0), 1, 0.3, 0.5, 0.3, 0.0)
            world.spawnParticle(Particle.LARGE_SMOKE, core.clone().add(0.0, 3.0, 0.0), 4, 0.6, 0.3, 0.6, 0.02)

            if (tick % 8 == 0) world.playSound(core, Sound.BLOCK_FIRE_AMBIENT, 1f, 0.4f)

            player.getNearbyEntities(spell.radius + 1, 4.0, spell.radius + 1)
                .asSequence()
                .filterIsInstance<LivingEntity>()
                .filter { it.uniqueId != player.uniqueId && canHit(player, it) && !it.isInvulnerable && it !is ArmorStand }
                .filter { Bukkit.isOwnedByCurrentRegion(it) }
                .forEach { target ->
                    val distance = target.location.distance(core)
                    if (distance <= spell.radius) {
                        val pull = core.toVector().subtract(target.location.toVector()).setY(0.0)
                        if (pull.lengthSquared() > 0.1) target.velocity = pull.normalize().multiply(0.35).setY(0.08)
                        if (tick % 10 == 0) {
                            target.damage(spell.power * 1.4, player)
                            target.fireTicks = max(target.fireTicks, 60)
                        }
                    }
                }
            dispatcher.sleep(1)
        }

        displays.forEach { display ->
            display.interpolationDelay = 0
            display.interpolationDuration = 10
            display.transformation = Transformation(
                Vector3f(0f, 0f, 0f),
                Quaternionf(),
                Vector3f(0.01f),
                Quaternionf(),
            )
        }
        dispatcher.sleep(10)
        displays.forEach { it.remove() }
    }

    private suspend fun plasmaLance(player: Player, spell: Spell, dispatcher: RegionDispatcher) {
        val world = player.world
        val eye = player.eyeLocation
        val direction = eye.direction.normalize()
        val beam = world.spawn(eye, BlockDisplay::class.java) { d ->
            d.block = Material.END_ROD.createBlockData()
            d.brightness = Display.Brightness(15, 15)
            d.transformation = Transformation(
                Vector3f(-0.1f, -0.1f, 0f),
                Quaternionf(),
                Vector3f(0.2f, 0.2f, spell.range.toFloat()),
                lookAt(direction),
            )
            d.isPersistent = false
            d.setGravity(false)
        }
        beam.teleport(eye)

        world.playSound(eye, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.3f, 1.8f)
        world.playSound(eye, Sound.ITEM_TRIDENT_THUNDER, 1.1f, 1.5f)

        val stepCount = 24
        for (step in 0..stepCount) {
            val reach = spell.range * step / stepCount
            val tip = eye.clone().add(direction.clone().multiply(reach))
            world.spawnParticle(Particle.ELECTRIC_SPARK, tip, 8, 0.12, 0.12, 0.12, 0.05)
            world.spawnParticle(Particle.FLAME, tip, 2, 0.1, 0.1, 0.1, 0.01)
            if (step % 4 == 0) {
                world.getNearbyLivingEntities(tip, 1.4)
                    .filter { it.uniqueId != player.uniqueId && canHit(player, it) && !it.isInvulnerable && it !is ArmorStand }
                    .filter { Bukkit.isOwnedByCurrentRegion(it) }
                    .forEach { target ->
                        target.damage(spell.power * 2.2, player)
                        target.fireTicks = max(target.fireTicks, 40)
                        target.velocity = direction.clone().multiply(0.7)
                    }
            }
            dispatcher.sleep(1)
        }
        beam.remove()
    }

    private suspend fun magmaPillar(player: Player, spell: Spell, dispatcher: RegionDispatcher) {
        val world = player.world
        val target = aimedBlock(player, spell.range) ?: return
        val base = Location(world, target.blockX + 0.5, target.y, target.blockZ + 0.5)
        world.playSound(base, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.5f)
        world.playSound(base, Sound.BLOCK_LAVA_EXTINGUISH, 1.3f, 0.8f)
        val columnHeight = 5
        val pillars = mutableListOf<BlockDisplay>()
        for (step in 0 until columnHeight) {
            val source = base.clone().add(0.0, -1.0, 0.0)
            val display = world.spawn(source, BlockDisplay::class.java) { d ->
                d.block = (if (step == columnHeight - 1) Material.FIRE else if (step % 2 == 0) Material.MAGMA_BLOCK else Material.NETHERRACK).createBlockData()
                d.brightness = Display.Brightness(15, 15)
                d.transformation = Transformation(
                    Vector3f(-0.5f, -1f, -0.5f),
                    Quaternionf(),
                    Vector3f(0.01f),
                    Quaternionf(),
                )
                d.isPersistent = false
                d.setGravity(false)
            }
            display.interpolationDelay = step * 2
            display.interpolationDuration = 6
            display.transformation = Transformation(
                Vector3f(-0.5f, step.toFloat(), -0.5f),
                Quaternionf(),
                Vector3f(1f, 1f, 1f),
                Quaternionf(),
            )
            pillars += display
        }

        for (tick in 0 until 40) {
            world.spawnParticle(Particle.LAVA, base.clone().add(0.0, 1.5, 0.0), 3, 0.5, 2.0, 0.5, 0.0)
            world.spawnParticle(Particle.FLAME, base.clone().add(0.0, 2.5, 0.0), 6, 0.6, 1.0, 0.6, 0.03)
            if (tick == 10) {
                world.getNearbyLivingEntities(base.clone().add(0.0, 1.5, 0.0), spell.radius)
                    .filter { it.uniqueId != player.uniqueId && canHit(player, it) && !it.isInvulnerable && it !is ArmorStand }
                    .filter { Bukkit.isOwnedByCurrentRegion(it) }
                    .forEach { target ->
                        target.damage(spell.power * 4.0, player)
                        target.fireTicks = max(target.fireTicks, 100)
                        target.velocity = Vector(0.0, 0.9, 0.0)
                    }
            }
            dispatcher.sleep(1)
        }
        pillars.forEach {
            it.interpolationDelay = 0
            it.interpolationDuration = 8
            it.transformation = Transformation(Vector3f(-0.5f, -1f, -0.5f), Quaternionf(), Vector3f(0.01f), Quaternionf())
        }
        dispatcher.sleep(8)
        pillars.forEach { it.remove() }
    }

    private suspend fun glacierWall(player: Player, spell: Spell, dispatcher: RegionDispatcher) {
        val world = player.world
        val direction = player.location.direction.setY(0.0).let {
            if (it.lengthSquared() < 1e-4) Vector(1.0, 0.0, 0.0) else it.normalize()
        }
        val side = Vector(-direction.z, 0.0, direction.x)
        val front = player.location.clone().add(direction.clone().multiply(2.5))
        val displays = mutableListOf<BlockDisplay>()

        world.playSound(front, Sound.BLOCK_GLASS_BREAK, 1.5f, 0.6f)
        world.playSound(front, Sound.BLOCK_AMETHYST_CLUSTER_PLACE, 1.4f, 0.8f)

        val length = 5
        val height = 3
        for (offset in -length / 2..length / 2) {
            for (level in 0 until height) {
                val source = front.clone().add(side.clone().multiply(offset.toDouble())).add(0.0, level.toDouble() - 3.0, 0.0)
                val display = world.spawn(source, BlockDisplay::class.java) { d ->
                    d.block = (if (level == height - 1) Material.PACKED_ICE else Material.BLUE_ICE).createBlockData()
                    d.brightness = Display.Brightness(15, 15)
                    d.transformation = Transformation(
                        Vector3f(-0.5f, -0.5f, -0.5f),
                        Quaternionf(),
                        Vector3f(0.01f),
                        Quaternionf(),
                    )
                    d.isPersistent = false
                    d.setGravity(false)
                }
                display.interpolationDelay = kotlin.math.abs(offset) * 2
                display.interpolationDuration = 8
                display.transformation = Transformation(
                    Vector3f(-0.5f, 2.5f, -0.5f),
                    Quaternionf(),
                    Vector3f(1f),
                    Quaternionf(),
                )
                displays += display
            }
        }

        for (tick in 0 until spell.duration) {
            if (tick % 6 == 0) world.spawnParticle(Particle.SNOWFLAKE, front.clone().add(0.0, 1.0, 0.0), 20, length * 0.6, 1.0, 0.4, 0.02)
            if (tick == 4) {
                world.getNearbyLivingEntities(front, length * 0.8)
                    .filter { it.uniqueId != player.uniqueId && canHit(player, it) && !it.isInvulnerable && it !is ArmorStand }
                    .filter { Bukkit.isOwnedByCurrentRegion(it) }
                    .forEach { target ->
                        target.damage(spell.power * 1.5, player)
                        target.freezeTicks = max(target.freezeTicks, 140)
                        target.velocity = direction.clone().multiply(-0.5).setY(0.3)
                    }
            }
            dispatcher.sleep(1)
        }
        displays.forEach {
            it.interpolationDelay = 0
            it.interpolationDuration = 10
            it.transformation = Transformation(Vector3f(-0.5f, -1.5f, -0.5f), Quaternionf(), Vector3f(0.01f), Quaternionf())
        }
        dispatcher.sleep(10)
        displays.forEach { it.remove() }
    }

    private suspend fun thunderclap(player: Player, spell: Spell, dispatcher: RegionDispatcher) {
        val world = player.world
        val target = aimedBlock(player, spell.range) ?: player.location.add(player.location.direction.multiply(3.0))
        val arrival = Location(world, target.blockX + 0.5, target.y + 1.0, target.blockZ + 0.5, player.location.yaw, player.location.pitch)

        world.playSound(player.location, Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 1.6f)
        world.spawnParticle(Particle.PORTAL, player.location, 30, 0.4, 1.0, 0.4, 0.3)
        player.teleport(arrival)
        world.playSound(arrival, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.4f, 1.3f)
        world.spawnParticle(Particle.FLASH, arrival, 1)

        val segments = 24
        val displays = (0 until segments).map { i ->
            val angle = i * (2.0 * PI / segments)
            val point = arrival.clone().add(cos(angle) * 0.4, 0.05, sin(angle) * 0.4)
            world.spawn(point, BlockDisplay::class.java) { d ->
                d.block = Material.LIGHTNING_ROD.createBlockData()
                d.brightness = Display.Brightness(15, 15)
                d.transformation = Transformation(
                    Vector3f(-0.08f, 0f, -0.08f),
                    Quaternionf().rotateY(angle.toFloat()),
                    Vector3f(0.16f, 0.5f, 0.16f),
                    Quaternionf(),
                )
                d.isPersistent = false
                d.setGravity(false)
            }
        }

        val hit = HashSet<java.util.UUID>()
        val steps = 14
        for (step in 1..steps) {
            val radius = spell.radius * step / steps
            displays.forEachIndexed { i, display ->
                val angle = i * (2.0 * PI / segments)
                display.interpolationDelay = 0
                display.interpolationDuration = 1
                display.transformation = Transformation(
                    Vector3f((cos(angle) * radius - 0.08).toFloat(), 0f, (sin(angle) * radius - 0.08).toFloat()),
                    Quaternionf().rotateY(angle.toFloat()),
                    Vector3f(0.16f, (0.7 - step * 0.03).toFloat().coerceAtLeast(0.2f), 0.16f),
                    Quaternionf(),
                )
            }
            world.spawnParticle(Particle.ELECTRIC_SPARK, arrival, 20, radius, 0.2, radius, 0.1)
            player.getNearbyEntities(radius + 0.5, 2.0, radius + 0.5)
                .asSequence()
                .filterIsInstance<LivingEntity>()
                .filter { it.uniqueId != player.uniqueId && it.uniqueId !in hit && canHit(player, it) && !it.isInvulnerable && it !is ArmorStand }
                .filter { Bukkit.isOwnedByCurrentRegion(it) }
                .filter { arrival.distance(it.location) in (radius - 1.0)..(radius + 1.0) }
                .forEach { target ->
                    hit += target.uniqueId
                    target.damage(spell.power * 2.5, player)
                    val push = target.location.toVector().subtract(arrival.toVector()).setY(0.0)
                    val dir = if (push.lengthSquared() < 1e-4) Vector(1.0, 0.0, 0.0) else push.normalize()
                    target.velocity = dir.multiply(1.1).setY(0.6)
                }
            dispatcher.sleep(1)
        }
        displays.forEach { it.remove() }
    }

    private suspend fun blizzard(player: Player, spell: Spell, dispatcher: RegionDispatcher) {
        val world = player.world
        val origin = player.eyeLocation
        val direction = origin.direction.normalize()
        world.playSound(origin, Sound.ENTITY_BREEZE_IDLE_AIR, 1.4f, 0.6f)
        world.playSound(origin, Sound.ITEM_BUCKET_FILL_POWDER_SNOW, 1.2f, 0.9f)

        for (tick in 0 until spell.duration) {
            val reach = spell.range * (tick % 20) / 20.0
            val cone = origin.clone().add(direction.clone().multiply(reach))
            val spread = reach * 0.4
            world.spawnParticle(Particle.SNOWFLAKE, cone, 20, spread, spread, spread, 0.03)
            world.spawnParticle(Particle.ITEM_SNOWBALL, cone, 6, spread, spread, spread, 0.05)
            if (tick % 6 == 0) {
                world.getNearbyLivingEntities(cone, spread + 1.0)
                    .filter { it.uniqueId != player.uniqueId && canHit(player, it) && !it.isInvulnerable && it !is ArmorStand }
                    .filter { Bukkit.isOwnedByCurrentRegion(it) }
                    .forEach { target ->
                        target.damage(spell.power * 0.7, player)
                        target.freezeTicks = max(target.freezeTicks, 100)
                    }
            }
            dispatcher.sleep(1)
        }
    }

    private suspend fun inferno(player: Player, spell: Spell, dispatcher: RegionDispatcher) {
        val world = player.world
        val center = player.location
        val ring = (0 until 16).map { i ->
            val angle = i * (2.0 * PI / 16)
            val loc = center.clone().add(cos(angle) * spell.radius, 0.1, sin(angle) * spell.radius)
            world.spawn(loc, BlockDisplay::class.java) { d ->
                d.block = Material.FIRE.createBlockData()
                d.brightness = Display.Brightness(15, 15)
                d.transformation = Transformation(Vector3f(-0.5f, 0f, -0.5f), Quaternionf(), Vector3f(0.01f), Quaternionf())
                d.isPersistent = false
                d.setGravity(false)
            }
        }
        ring.forEach {
            it.interpolationDelay = 0
            it.interpolationDuration = 10
            it.transformation = Transformation(Vector3f(-0.5f, 0f, -0.5f), Quaternionf(), Vector3f(1f, 0.8f, 1f), Quaternionf())
        }
        world.playSound(center, Sound.ITEM_FIRECHARGE_USE, 1.6f, 0.5f)

        for (tick in 0 until spell.duration) {
            if (tick % 4 == 0) {
                world.spawnParticle(Particle.FLAME, center, 40, spell.radius, 0.3, spell.radius, 0.04)
                world.spawnParticle(Particle.SMOKE, center, 15, spell.radius, 0.5, spell.radius, 0.02)
            }
            if (tick % 10 == 0) {
                world.getNearbyLivingEntities(center, spell.radius)
                    .filter { it.uniqueId != player.uniqueId && canHit(player, it) && !it.isInvulnerable && it !is ArmorStand }
                    .filter { Bukkit.isOwnedByCurrentRegion(it) }
                    .forEach { target ->
                        target.damage(spell.power * 1.2, player)
                        target.fireTicks = max(target.fireTicks, 80)
                    }
            }
            dispatcher.sleep(1)
        }
        ring.forEach {
            it.interpolationDelay = 0
            it.interpolationDuration = 8
            it.transformation = Transformation(Vector3f(-0.5f, 0f, -0.5f), Quaternionf(), Vector3f(0.01f), Quaternionf())
        }
        dispatcher.sleep(8)
        ring.forEach { it.remove() }
    }

    private suspend fun stormSurge(player: Player, spell: Spell, dispatcher: RegionDispatcher) {
        val world = player.world
        world.playSound(player.location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 1.2f)
        val struck = HashSet<java.util.UUID>()
        var source = player.location
        val chains = 5
        for (chain in 0 until chains) {
            val candidates = world.getNearbyLivingEntities(source, spell.radius)
                .filter { it.uniqueId != player.uniqueId && it.uniqueId !in struck && canHit(player, it) && !it.isInvulnerable && it !is ArmorStand }
                .filter { Bukkit.isOwnedByCurrentRegion(it) }
            val target = candidates.minByOrNull { it.location.distance(source) } ?: break
            struck += target.uniqueId
            drawBolt(world, source, target.location.clone().add(0.0, 1.0, 0.0), dispatcher)
            target.damage(spell.power * (2.5 - chain * 0.2), player)
            world.strikeLightningEffect(target.location)
            source = target.location
            dispatcher.sleep(3)
        }
    }

    private suspend fun drawBolt(world: World, from: Location, to: Location, dispatcher: RegionDispatcher) {
        val steps = 10
        val dx = (to.x - from.x) / steps
        val dy = (to.y - from.y) / steps
        val dz = (to.z - from.z) / steps
        for (i in 0..steps) {
            val point = Location(world, from.x + dx * i, from.y + dy * i, from.z + dz * i)
            world.spawnParticle(Particle.ELECTRIC_SPARK, point, 8, 0.1, 0.1, 0.1, 0.0)
            world.spawnParticle(Particle.FIREWORK, point, 2, 0.1, 0.1, 0.1, 0.0)
        }
        dispatcher.sleep(1)
    }

    private suspend fun stoneFist(player: Player, spell: Spell, dispatcher: RegionDispatcher) {
        val world = player.world
        val eye = player.eyeLocation
        val direction = eye.direction.normalize()
        val display = world.spawn(eye, BlockDisplay::class.java) { d ->
            d.block = Material.COBBLESTONE.createBlockData()
            d.brightness = Display.Brightness(15, 15)
            d.transformation = Transformation(Vector3f(-0.4f, -0.4f, -0.4f), Quaternionf(), Vector3f(0.8f), Quaternionf())
            d.isPersistent = false
            d.setGravity(false)
        }
        world.playSound(eye, Sound.BLOCK_STONE_PLACE, 1f, 0.5f)
        val steps = (spell.range * 2).toInt().coerceAtLeast(10)
        for (step in 0 until steps) {
            val point = eye.clone().add(direction.clone().multiply(step * 0.5))
            display.teleport(point)
            display.interpolationDelay = 0
            display.interpolationDuration = 2
            display.transformation = Transformation(
                Vector3f(-0.4f, -0.4f, -0.4f),
                Quaternionf().rotateY((step * 0.4).toFloat()).rotateX((step * 0.3).toFloat()),
                Vector3f(0.8f),
                Quaternionf(),
            )
            world.spawnParticle(Particle.CRIT, point, 5, 0.2, 0.2, 0.2, 0.0)
            val hit = world.getNearbyLivingEntities(point, 1.2)
                .firstOrNull { it.uniqueId != player.uniqueId && canHit(player, it) && !it.isInvulnerable && it !is ArmorStand && Bukkit.isOwnedByCurrentRegion(it) }
            if (hit != null) {
                hit.damage(spell.power * 3.0, player)
                hit.velocity = direction.clone().multiply(1.5).setY(0.5)
                world.playSound(point, Sound.BLOCK_STONE_BREAK, 1f, 0.8f)
                break
            }
            dispatcher.sleep(1)
        }
        display.remove()
    }

    private suspend fun steamBurst(player: Player, spell: Spell, dispatcher: RegionDispatcher) {
        val world = player.world
        val center = player.location
        world.playSound(center, Sound.BLOCK_LAVA_EXTINGUISH, 1.4f, 1.3f)
        for (tick in 0 until 20) {
            val radius = spell.radius * tick / 20.0
            world.spawnParticle(Particle.CLOUD, center, 30, radius, 0.4, radius, 0.02)
            world.spawnParticle(Particle.SMOKE, center.clone().add(0.0, 0.5, 0.0), 20, radius, 0.4, radius, 0.03)
            if (tick == 2) {
                world.getNearbyLivingEntities(center, spell.radius)
                    .filter { it.uniqueId != player.uniqueId && canHit(player, it) && !it.isInvulnerable && it !is ArmorStand }
                    .filter { Bukkit.isOwnedByCurrentRegion(it) }
                    .forEach { target ->
                        target.damage(spell.power * 1.5, player)
                        val push = target.location.toVector().subtract(center.toVector()).setY(0.0)
                        val dir = if (push.lengthSquared() < 1e-4) Vector(1.0, 0.0, 0.0) else push.normalize()
                        target.velocity = dir.multiply(1.6).setY(0.5)
                    }
            }
            dispatcher.sleep(1)
        }
    }

    private suspend fun dustDevil(player: Player, spell: Spell, dispatcher: RegionDispatcher) {
        val world = player.world
        val anchor = aimedBlock(player, spell.range)?.add(0.0, 1.0, 0.0) ?: player.location
        world.playSound(anchor, Sound.ENTITY_BREEZE_WIND_BURST, 1.3f, 0.8f)
        val shards = (0 until 12).map { i ->
            world.spawn(anchor, BlockDisplay::class.java) { d ->
                d.block = Material.BROWN_CONCRETE_POWDER.createBlockData()
                d.brightness = Display.Brightness(15, 15)
                d.transformation = Transformation(Vector3f(-0.15f, 0f, -0.15f), Quaternionf(), Vector3f(0.3f, 0.6f, 0.3f), Quaternionf())
                d.isPersistent = false
                d.setGravity(false)
            }
        }
        for (tick in 0 until spell.duration) {
            shards.forEachIndexed { i, display ->
                val angle = tick * 0.4 + i * (2.0 * PI / shards.size)
                val height = 0.2 + (i % 3) * 0.6
                val radius = 1.1 + sin(tick * 0.1 + i) * 0.2
                display.teleport(anchor.clone().add(cos(angle) * radius, height, sin(angle) * radius))
                display.interpolationDelay = 0
                display.interpolationDuration = 2
                display.transformation = Transformation(
                    Vector3f(-0.15f, 0f, -0.15f),
                    Quaternionf().rotateY(angle.toFloat()),
                    Vector3f(0.3f, 0.6f, 0.3f),
                    Quaternionf(),
                )
            }
            world.spawnParticle(Particle.DUST_PLUME, anchor.clone().add(0.0, 1.0, 0.0), 20, 1.2, 1.5, 1.2, 0.1)
            if (tick % 8 == 0) {
                world.getNearbyLivingEntities(anchor, spell.radius)
                    .filter { it.uniqueId != player.uniqueId && canHit(player, it) && !it.isInvulnerable && it !is ArmorStand }
                    .filter { Bukkit.isOwnedByCurrentRegion(it) }
                    .forEach { target ->
                        target.damage(spell.power * 0.8, player)
                        val pull = anchor.toVector().subtract(target.location.toVector()).setY(0.0)
                        if (pull.lengthSquared() > 0.1) target.velocity = pull.normalize().multiply(0.5).setY(0.25)
                    }
            }
            dispatcher.sleep(1)
        }
        shards.forEach { it.remove() }
    }

    private fun genericBurst(player: Player, spell: Spell) {
        val world = player.world
        val element = spell.elements.first()
        world.spawnParticle(element.particle, player.location.add(0.0, 1.0, 0.0), 50, 1.0, 1.0, 1.0, 0.1)
        world.playSound(player.location, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 1.5f)
    }

    private fun aimedBlock(player: Player, maxRange: Double) =
        player.getTargetBlockExact(maxRange.toInt().coerceIn(1, 60))?.location

    private fun canHit(player: Player, target: LivingEntity): Boolean {
        if (target is Player) return player.world.pvp && target.gameMode in setOf(GameMode.SURVIVAL, GameMode.ADVENTURE)
        return true
    }

    private fun lookAt(direction: Vector): Quaternionf {
        val length = direction.length()
        if (length < 1e-4) return Quaternionf()
        val d = Vector3f(direction.x.toFloat(), direction.y.toFloat(), direction.z.toFloat()).div(length.toFloat())
        val yaw = kotlin.math.atan2(d.x.toDouble(), d.z.toDouble()).toFloat()
        val pitch = -kotlin.math.asin(d.y.toDouble()).toFloat()
        return Quaternionf().rotateY(yaw).rotateX(pitch)
    }
}
