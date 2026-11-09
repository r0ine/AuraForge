package com.r0ine.auraforge.adventure

import com.r0ine.auraforge.AuraForgePlugin
import com.r0ine.auraforge.core.noItalic
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AdventureService(private val plugin: AuraForgePlugin) {
    private val profiles = ConcurrentHashMap<UUID, Adventurer>()
    private val traitKey = NamespacedKey(plugin, "weapon_traits")
    private val setKey = NamespacedKey(plugin, "armor_set")

    fun load(records: Map<String, Adventurer>) {
        records.forEach { (id, profile) -> runCatching { UUID.fromString(id) }.getOrNull()?.let { profiles[it] = profile } }
    }

    fun snapshot(): Map<String, Adventurer> = profiles.entries.associate { it.key.toString() to it.value }
    fun profile(player: Player): Adventurer = profiles[player.uniqueId] ?: Adventurer()

    fun change(player: Player, edit: (Adventurer) -> Adventurer): Adventurer {
        val updated = profiles.compute(player.uniqueId) { _, current -> edit(current ?: Adventurer()) }!!
        plugin.markDirty()
        return updated
    }

    fun traits(item: ItemStack?): Set<WeaponTrait> = item?.itemMeta?.let(::traits).orEmpty()
    fun traits(meta: ItemMeta): Set<WeaponTrait> {
        val stored = meta.persistentDataContainer.get(traitKey, PersistentDataType.STRING).orEmpty().split(',')
        return WeaponTrait.entries.filter { it.id in stored }.toSet()
    }

    fun armorSet(item: ItemStack?): ArmorSet? = item?.itemMeta?.let(::armorSet)
    private fun armorSet(meta: ItemMeta): ArmorSet? =
        meta.persistentDataContainer.get(setKey, PersistentDataType.STRING)?.let { id -> ArmorSet.entries.firstOrNull { it.id == id } }

    fun pieces(player: Player, set: ArmorSet): Int = player.inventory.armorContents.count {
        plugin.items.read(it)?.isArmor == true && armorSet(it) == set
    }

    fun lore(meta: ItemMeta): List<Component> = buildList {
        traits(meta).forEach { add(plugin.text.parse("<aqua>◆ ${it.title}").noItalic()) }
        armorSet(meta)?.let { add(plugin.text.parse("<gold>Set: ${it.title}").noItalic()) }
    }

    fun traitLock(player: Player, trait: WeaponTrait): String? {
        val (weapon, progress) = plugin.progress.weaponIn(player) ?: return "Ana elinde dövülmüş bir silah tut."
        if (weapon.amount != 1) return "Tek bir silahla işlem yap."
        val equipped = traits(weapon)
        if (trait in equipped) return null
        if (profile(player).rank < trait.rank) return "Maceracı rütbesi ${trait.rank} gerekli."
        if (equipped.size >= Adventurer.traitSlots(progress.level)) return "Yuvalar dolu. Ek yuvalar silah seviyesi 10 ve 20'de açılır."
        if (profile(player).essence < trait.cost) return "${trait.cost} öz gerekli."
        return null
    }

    fun inscribe(player: Player, trait: WeaponTrait): String {
        traitLock(player, trait)?.let { return it }
        val weapon = player.inventory.itemInMainHand
        val equipped = traits(weapon)
        val removing = trait in equipped
        val updated = if (removing) equipped - trait else equipped + trait
        if (!removing) change(player) { it.copy(essence = it.essence - trait.cost) }
        weapon.editMeta { it.persistentDataContainer.set(traitKey, PersistentDataType.STRING, updated.joinToString(",") { trait -> trait.id }) }
        plugin.items.update(weapon) { it }
        player.inventory.setItemInMainHand(weapon)
        return if (removing) "${trait.title} çıkarıldı; öz iade edilmez." else "${trait.title} silahına işlendi."
    }

    fun bindSet(player: Player, set: ArmorSet): String {
        val armor = player.inventory.itemInMainHand
        if (plugin.items.read(armor)?.isArmor != true || armor.amount != 1) return "Ana elinde tek bir dövülmüş zırh parçası tut."
        if (armorSet(armor) == set) return "Bu parça zaten ${set.title} setinden."
        if (profile(player).rank < 3) return "Set işlemek için maceracı rütbesi 3 gerekli."
        if (profile(player).essence < 40) return "Bir parçaya set işlemek 40 öz ister."
        change(player) { it.copy(essence = it.essence - 40) }
        armor.editMeta { it.persistentDataContainer.set(setKey, PersistentDataType.STRING, set.id) }
        plugin.items.update(armor) { it }
        player.inventory.setItemInMainHand(armor)
        return "${set.title} işlendi. Eski set mührü varsa değiştirildi."
    }

    fun research(player: Player, spell: LearnedSpell): String {
        val profile = profile(player)
        if (spell.id in profile.studied) {
            val selected = if (profile.selected == spell.id) null else spell.id
            change(player) { it.copy(selected = selected) }
            return if (selected == null) "Seçili büyü kaldırıldı." else "${spell.title} seçildi. Eğilerek silahla sağ tıkla."
        }
        profile.researchLock(spell)?.let { return it }
        change(player) { it.copy(essence = it.essence - spell.cost, studied = it.studied + spell.id, selected = spell.id) }
        return "${spell.title} öğrenildi ve seçildi."
    }

    fun contract(player: Player, quarry: Quarry): String {
        val profile = profile(player)
        if (profile.contract != null) return "Önce mevcut sözleşmeni bitir veya iptal et."
        change(player) { it.copy(contract = quarry.id, contractKills = 0) }
        return "${profile.contractGoal} ${quarry.title} avla; ödül ${profile.contractReward} öz."
    }

    fun claimContract(player: Player): String {
        val profile = profile(player)
        if (profile.contract == null || profile.contractKills < profile.contractGoal) return "Sözleşme henüz tamamlanmadı."
        change(player) {
            it.copy(contract = null, contractKills = 0, contracts = it.contracts + 1,
                essence = (it.essence + it.contractReward).coerceAtMost(1_000_000), xp = (it.xp + 30).coerceAtMost(1900))
        }
        return "Sözleşme teslim edildi: ${profile.contractReward} öz, 30 ustalık XP."
    }

    fun studyQuarry(player: Player, quarry: Quarry): String {
        val profile = profile(player)
        val kills = profile.kills[quarry.id] ?: 0
        val threshold = Adventurer.milestones.firstOrNull { kills >= it && "${quarry.id}:$it" !in profile.milestones }
            ?: return "Yeni araştırma ödülü yok. Eşikler: 10, 30, 75 av."
        change(player) {
            it.copy(milestones = it.milestones + "${quarry.id}:$threshold", essence = (it.essence + threshold).coerceAtMost(1_000_000))
        }
        return "${quarry.title} araştırıldı: +$threshold öz ve bu türe kalıcı +%2 hasar."
    }

    fun bestiaryBonus(player: Player, quarry: Quarry): Double =
        Adventurer.milestones.count { "${quarry.id}:$it" in profile(player).milestones } * 0.02

    fun salvageValue(item: ItemStack): Int? = plugin.items.read(item)?.let { forged ->
        4 + forged.quality.coerceIn(0, 100) / 10 + (plugin.items.progress(item)?.plus ?: 0).coerceIn(0, 10) * 2
    }

    fun salvage(player: Player, expected: ItemStack?): String {
        val held = player.inventory.itemInMainHand
        if (expected == null || held != expected || held.amount != 1) return "Elindeki eşya değişti. Söküm ekranını yeniden aç."
        val reward = salvageValue(held) ?: return "Yalnızca dövülmüş eşyalar sökülebilir."
        player.inventory.setItemInMainHand(ItemStack(Material.AIR))
        change(player) { it.copy(essence = (it.essence + reward).coerceAtMost(1_000_000)) }
        return "Eşya söküldü: +$reward öz."
    }

    fun manaMaximum(player: Player): Double = 100.0 + (profile(player).rank - 1) * 2 +
        if (pieces(player, ArmorSet.ASTRAL) >= 2) 20 else 0

    fun restoreMana(player: Player, amount: Double) {
        val now = System.currentTimeMillis()
        val maximum = manaMaximum(player)
        change(player) { it.copy(mana = (it.regenerated(now, maximum) + amount).coerceAtMost(maximum), manaAt = now) }
    }

    fun tell(player: Player, message: String) = player.sendMessage(plugin.text.parse("<gold>AuraForge <dark_gray>› <gray>$message"))
}
