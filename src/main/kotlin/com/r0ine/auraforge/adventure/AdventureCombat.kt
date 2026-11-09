package com.r0ine.auraforge.adventure

import com.r0ine.auraforge.AuraForgePlugin
import com.r0ine.auraforge.core.runFor
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Tameable
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AdventureCombat(private val plugin: AuraForgePlugin) : Listener {
    private data class Combo(val target: UUID, val count: Int, val at: Long)
    private data class Impact(val attacker: UUID, val target: UUID, var accepted: Boolean = false)
    private data class Swing(val target: UUID, val strength: Float, val tick: Int)
    private val combos = ConcurrentHashMap<UUID, Combo>()
    private val swings = ConcurrentHashMap<UUID, Swing>()
    private val secondary = ThreadLocal<Impact?>()
    private val farmed = NamespacedKey(plugin, "spawned_without_reward")
    private val adventure get() = plugin.adventure

    fun canHit(player: Player, target: LivingEntity): Boolean {
        if (!Bukkit.isOwnedByCurrentRegion(target) || target.isDead || !target.isValid || target == player) return false
        if (!plugin.progress.canHit(player, target)) return false
        if (target is Tameable && target.owner?.uniqueId == player.uniqueId) return false
        if (target is Player) {
            val team = player.scoreboard.getEntryTeam(player.name)
            if (team != null && team.hasEntry(target.name) && !team.allowFriendlyFire()) return false
        }
        return true
    }

    fun hit(player: Player, target: LivingEntity, damage: Double, effect: () -> Unit = {}): Boolean {
        if (!canHit(player, target)) return false
        val previous = secondary.get()
        val impact = Impact(player.uniqueId, target.uniqueId)
        secondary.set(impact)
        try {
            target.damage(damage, player)
        } finally {
            secondary.set(previous)
        }
        if (impact.accepted && !target.isDead) effect()
        return impact.accepted
    }

    fun isSecondary(): Boolean = secondary.get() != null

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun beforeAttack(event: PrePlayerAttackEntityEvent) {
        if (event.willAttack()) swings[event.player.uniqueId] = Swing(event.attacked.uniqueId, event.player.attackCooldown, Bukkit.getCurrentTick())
    }

    private fun attackStrength(player: Player, target: LivingEntity): Float {
        // Bazı Paper sürümleri hasar olayı gelmeden vuruş sayacını sıfırlar.
        val swing = swings[player.uniqueId]
        return if (swing?.target == target.uniqueId && swing.tick == Bukkit.getCurrentTick()) swing.strength else player.attackCooldown
    }

    fun combo(player: Player, target: LivingEntity): Int {
        val previous = combos[player.uniqueId] ?: return 1
        val window = if (adventure.pieces(player, ArmorSet.TEMPEST) >= 2) 6000 else 4000
        return if (previous.target == target.uniqueId && System.currentTimeMillis() - previous.at <= window) {
            (previous.count + 1).coerceAtMost(8)
        } else 1
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onAttack(event: EntityDamageByEntityEvent) {
        if (isSecondary() || event.cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return
        val player = event.damager as? Player ?: return
        val target = event.entity as? LivingEntity ?: return
        if (!canHit(player, target) || plugin.progress.weaponIn(player) == null || attackStrength(player, target) < 0.85f) return
        val traits = adventure.traits(player.inventory.itemInMainHand)
        val chain = combo(player, target)
        var bonus = (chain - 1) * 0.02
        val healthRatio = target.health / (target.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0)
        if (WeaponTrait.EXECUTION in traits && healthRatio < 0.3) bonus += 0.20
        if (WeaponTrait.AMBUSH in traits && healthRatio > 0.9) bonus += 0.20
        if (WeaponTrait.GIANTSLAYER in traits && (target.getAttribute(Attribute.MAX_HEALTH)?.value ?: 0.0) >= 40) bonus += 0.20
        if (WeaponTrait.SUNDER in traits) bonus += ((target.getAttribute(Attribute.ARMOR)?.value ?: 0.0) * 0.01).coerceAtMost(0.20)
        if (WeaponTrait.MOMENTUM in traits) bonus += chain * 0.02
        if (WeaponTrait.BACKSTAB in traits) {
            val towardPlayer = player.location.toVector().subtract(target.location.toVector()).setY(0)
            if (towardPlayer.lengthSquared() > 0.01 && target.location.direction.dot(towardPlayer.normalize()) < -0.5) bonus += 0.25
        }
        if (WeaponTrait.AERIAL in traits && !target.isOnGround) bonus += 0.15
        if (WeaponTrait.FROSTBITE in traits && (target.freezeTicks > 0 || target.hasPotionEffect(PotionEffectType.SLOWNESS))) bonus += 0.18
        if (WeaponTrait.KINDLING in traits && target.fireTicks > 0) bonus += 0.18
        Quarry.of(target.type)?.let {
            bonus += adventure.bestiaryBonus(player, it)
            if (WeaponTrait.HUNTER in traits) bonus += 0.12
        }
        if (WeaponTrait.FOCUS in traits && adventure.profile(player).regenerated(System.currentTimeMillis(), adventure.manaMaximum(player)) >= adventure.manaMaximum(player) * 0.8) bonus += 0.12
        if (adventure.pieces(player, ArmorSet.ASH) >= 2 && target.fireTicks > 0) bonus += 0.10
        event.damage *= 1 + bonus.coerceAtMost(1.2)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun afterHit(event: EntityDamageByEntityEvent) {
        val player = event.damager as? Player ?: return
        val target = event.entity as? LivingEntity ?: return
        if (event.finalDamage <= 0 || !canHit(player, target)) return
        secondary.get()?.let {
            if (it.attacker == player.uniqueId && it.target == target.uniqueId) it.accepted = true
            return
        }
        if (event.cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK || attackStrength(player, target) < 0.85f || plugin.progress.weaponIn(player) == null) return
        val chain = combo(player, target)
        combos[player.uniqueId] = Combo(target.uniqueId, chain, System.currentTimeMillis())
        val traits = adventure.traits(player.inventory.itemInMainHand)
        if (WeaponTrait.EMBER in traits || adventure.pieces(player, ArmorSet.ASH) >= 4) target.fireTicks = maxOf(target.fireTicks, 60)
        if (WeaponTrait.FROST in traits) target.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 40, 0))
        if (WeaponTrait.VENOM in traits) target.addPotionEffect(PotionEffect(PotionEffectType.POISON, 60, 0))
        if (WeaponTrait.RUPTURE in traits) target.addPotionEffect(PotionEffect(PotionEffectType.WITHER, 40, 0))
        if (WeaponTrait.VAMPIRIC in traits) heal(player, (event.finalDamage * 0.06).coerceAtMost(1.0))
        if (WeaponTrait.ARCANE_LEECH in traits) adventure.restoreMana(player, 3.0)
        if (WeaponTrait.REPEL in traits) {
            val push = target.location.toVector().subtract(player.location.toVector()).setY(0)
            if (push.lengthSquared() > 0.01) target.velocity = push.normalize().multiply(0.5).setY(0.3)
        }
        if (WeaponTrait.GRAVITY in traits) target.velocity = target.velocity.multiply(0.2).setY(-0.3)
        if (adventure.pieces(player, ArmorSet.TEMPEST) >= 4) player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 40, 0))
        if (adventure.pieces(player, ArmorSet.EARTHEN) >= 4 && chain % 5 == 0) heal(player, 1.0)
        player.sendActionBar(plugin.text.parse("<aqua>Kombo $chain/8 <dark_gray>· <light_purple>Mana ${adventure.profile(player).regenerated(System.currentTimeMillis(), adventure.manaMaximum(player)).toInt()}"))
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onHurt(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        var reduction = 0.0
        val combat = event.cause in setOf(EntityDamageEvent.DamageCause.ENTITY_ATTACK, EntityDamageEvent.DamageCause.PROJECTILE, EntityDamageEvent.DamageCause.MAGIC)
        if (combat && adventure.pieces(player, ArmorSet.EARTHEN) >= 2) reduction += 0.08
        if (event.cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK && adventure.pieces(player, ArmorSet.GLACIER) >= 2) reduction += 0.06
        if (event.cause in setOf(EntityDamageEvent.DamageCause.FIRE, EntityDamageEvent.DamageCause.FIRE_TICK, EntityDamageEvent.DamageCause.LAVA) && adventure.pieces(player, ArmorSet.ASH) >= 4) reduction += 0.35
        event.damage *= 1 - reduction
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun frostArmor(event: EntityDamageByEntityEvent) {
        val player = event.entity as? Player ?: return
        val attacker = event.damager as? LivingEntity ?: return
        if (event.finalDamage > 0 && canHit(player, attacker) && adventure.pieces(player, ArmorSet.GLACIER) >= 4) {
            attacker.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 40, 1))
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSpawn(event: CreatureSpawnEvent) {
        if (event.spawnReason in setOf(CreatureSpawnEvent.SpawnReason.SPAWNER, CreatureSpawnEvent.SpawnReason.SPAWNER_EGG, CreatureSpawnEvent.SpawnReason.DISPENSE_EGG)) {
            event.entity.persistentDataContainer.set(farmed, PersistentDataType.BYTE, 1)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onKill(event: EntityDeathEvent) {
        val player = event.entity.killer ?: return
        val quarry = Quarry.of(event.entity.type) ?: return
        if (event.entity.persistentDataContainer.has(farmed)) return
        plugin.runFor(player) { creditKill(player, quarry) }
    }

    private fun creditKill(player: Player, quarry: Quarry) {
        if (!player.isOnline || plugin.progress.weaponIn(player) == null) return
        val old = adventure.profile(player)
        val updated = adventure.change(player) {
            val credited = it.credited(quarry)
            if (WeaponTrait.REAPER in adventure.traits(player.inventory.itemInMainHand)) credited.copy(essence = (credited.essence + 2).coerceAtMost(1_000_000)) else credited
        }
        if (updated.rank > old.rank) adventure.tell(player, "Maceracı rütben ${updated.rank} oldu. Yeni mühür ve büyüleri masadan incele.")
        if (updated.contract != null && updated.contractKills >= updated.contractGoal && old.contractKills < old.contractGoal) adventure.tell(player, "Av sözleşmen tamamlandı. Ödülünü + Macera menüsünden al.")
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        combos.remove(event.player.uniqueId)
        swings.remove(event.player.uniqueId)
    }

    fun heal(player: Player, amount: Double) {
        if (!player.isDead) player.health = (player.health + amount).coerceAtMost(player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0)
    }
}
