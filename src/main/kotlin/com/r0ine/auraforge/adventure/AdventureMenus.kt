package com.r0ine.auraforge.adventure

import com.r0ine.auraforge.AuraForgePlugin
import com.r0ine.auraforge.core.noItalic
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import java.util.UUID

enum class AdventurePage(val title: String) {
    HUB("+ Macera · Gelişim rehberi"), TRAITS("Silah mühürleri · 20 özellik"),
    SPELLS("Büyü araştırma ağacı"), SETS("Set atölyesi"), CONTRACTS("Av sözleşmeleri"),
    BESTIARY("Yaratık günlüğü"), SALVAGE("Eşya sökümü"), GUIDE("Maceracı rehberi");
}

class AdventureView(val owner: UUID, val page: AdventurePage, val held: ItemStack) : InventoryHolder {
    lateinit var view: Inventory
    val actions = mutableMapOf<Int, () -> Unit>()
    override fun getInventory(): Inventory = view
}

class AdventureMenus(private val plugin: AuraForgePlugin) : Listener {
    private val adventure get() = plugin.adventure

    fun open(player: Player, page: AdventurePage = AdventurePage.HUB) {
        if (!player.hasPermission("auraforge.use")) return
        val holder = AdventureView(player.uniqueId, page, player.inventory.itemInMainHand.clone())
        holder.view = Bukkit.createInventory(holder, 54, plugin.text.parse("<dark_purple>${page.title}"))
        for (slot in 0..53) holder.view.setItem(slot, icon(Material.BLACK_STAINED_GLASS_PANE, " "))
        val profile = adventure.profile(player)
        fun button(slot: Int, material: Material, title: String, lines: List<String>, action: () -> Unit) {
            holder.view.setItem(slot, icon(material, title, lines))
            holder.actions[slot] = action
        }
        fun act(operation: () -> String) {
            adventure.tell(player, operation())
            open(player, page)
        }
        when (page) {
            AdventurePage.HUB -> {
                button(4, Material.EXPERIENCE_BOTTLE, "Rütbe ${profile.rank}/20 · ${profile.essence} öz", listOf(
                    "Ustalık: ${profile.xp}/1900 XP", "Sonraki rütbe: ${if (profile.rank == 20) "Sınırda" else "${100 - profile.xp % 100} XP"}",
                    "Mana: ${profile.regenerated(System.currentTimeMillis(), adventure.manaMaximum(player)).toInt()}/${adventure.manaMaximum(player).toInt()}",
                    "Tıkla: baştan sona oyun rehberi",
                )) { open(player, AdventurePage.GUIDE) }
                button(10, Material.OAK_SAPLING, "Silah ustalık ağacı", listOf("Mevcut üç dal ve yetenek puanların.", "Elindeki dövülmüş silahla aç.")) { plugin.progressMenus.openTree(player, null) }
                button(12, Material.DIAMOND_SWORD, "20 saldırı mührü", listOf("Seçtiğin özellikleri silahına işle.", "Silah seviyesi 1 → 10 → 20", "Açık yuva sayısı 1 → 2 → 3")) { open(player, AdventurePage.TRAITS) }
                button(14, Material.ENCHANTED_BOOK, "10 büyü · Araştırma ağacı", listOf("Rütbe + öncül büyü + öz ile öğren.", "Eğil + silahla sağ tık: seçili büyü.")) { open(player, AdventurePage.SPELLS) }
                button(16, Material.DIAMOND_CHESTPLATE, "Beş ekipman seti", listOf("Dövülmüş zırha set mührü işle.", "2 ve 4 parçada farklı bonuslar.")) { open(player, AdventurePage.SETS) }
                button(28, Material.WRITABLE_BOOK, "Av sözleşmeleri", listOf("Hedef seç, avla, ödülü teslim al.", "Her teslim sonraki görevi büyütür.")) { open(player, AdventurePage.CONTRACTS) }
                button(30, Material.SPYGLASS, "Yaratık günlüğü", listOf("8 yaratık türünü araştır.", "10 / 30 / 75 avda ödül ve uzmanlık.")) { open(player, AdventurePage.BESTIARY) }
                button(32, Material.GRINDSTONE, "Eşyayı öze dönüştür", listOf("Kullanmadığın dövülmüş eşyayı sök.", "Sökümden önce onay ekranı açılır.")) { open(player, AdventurePage.SALVAGE) }
                button(34, Material.KNOWLEDGE_BOOK, "Element kodeksi", listOf("Eski tarifler, elementler ve arıtma.")) { plugin.progressMenus.openCodex(player, null) }
            }
            AdventurePage.TRAITS -> {
                val equipped = adventure.traits(player.inventory.itemInMainHand)
                WeaponTrait.entries.forEachIndexed { index, trait ->
                    val lock = adventure.traitLock(player, trait)
                    button(index, trait.icon, "${if (trait in equipped) "✓ " else ""}${trait.title}", listOf(
                        trait.description, "Rütbe ${trait.rank} · ${trait.cost} öz",
                        if (trait in equipped) "Tıkla: çıkar (öz iade edilmez)" else lock ?: "Tıkla: silaha işle",
                    )) { act { adventure.inscribe(player, trait) } }
                }
                holder.view.setItem(40, icon(Material.OAK_SAPLING, "Silah seviyesi → mühür yuvası", listOf(
                    "1. seviye: 1 yuva", "10. seviye: 2 yuva", "20. seviye: 3 yuva",
                    "Tam dolmuş yakın dövüş vuruşları tetikler.", "Bir mühür aynı silaha iki kez işlenmez.",
                )))
            }
            AdventurePage.SPELLS -> {
                val slots = listOf(1, 3, 5, 7, 8, 19, 20, 21, 23, 37)
                LearnedSpell.entries.forEachIndexed { index, spell ->
                    val known = spell.id in profile.studied
                    val selected = spell.id == profile.selected
                    button(slots[index], spell.icon, "${if (selected) "◆ " else if (known) "✓ " else ""}${spell.title}", listOf(
                        spell.description, "Rütbe ${spell.rank} · ${spell.cost} öz",
                        "Öncül: ${spell.parent?.let { LearnedSpell.valueOf(it).title } ?: "Kök büyü"}",
                        "${spell.mana} mana · ${spell.cooldown} saniye bekleme",
                        if (selected) "Tıkla: seçimi kaldır" else if (known) "Tıkla: kullanmak için seç" else profile.researchLock(spell) ?: "Tıkla: öğren ve seç",
                    )) { act { adventure.research(player, spell) } }
                }
                for (slot in listOf(10, 11, 12, 14, 28)) holder.view.setItem(slot, icon(Material.PURPLE_STAINED_GLASS_PANE, "↓ Önce üst büyüyü öğren"))
                holder.view.setItem(45, icon(Material.BOOK, "Büyü kullanımı", listOf("Dövülmüş silah + eğil + sağ tık.", "Saniyede 4 mana yenilenir.", "Beklemeler çıkışta sıfırlanmaz.")))
            }
            AdventurePage.SETS -> ArmorSet.entries.forEachIndexed { index, set ->
                button(11 + index, set.icon, set.title, listOf(
                    "Giyili parça: ${adventure.pieces(player, set)}/4", "2 parça: ${set.two}", "4 parça: ${set.four}",
                    "Rütbe 3 · Her parça 40 öz", "Ana elinde dövülmüş zırh tut.", "Tıkla: set işle; eski mühür değişir.",
                )) { act { adventure.bindSet(player, set) } }
            }
            AdventurePage.CONTRACTS -> {
                Quarry.entries.forEachIndexed { index, quarry ->
                    button(10 + index, quarry.icon, quarry.title, listOf(
                        "Hedef: ${profile.contractGoal} av", "Ödül: ${profile.contractReward} öz + 30 XP", "Tıkla: sözleşmeyi kabul et",
                    )) { act { adventure.contract(player, quarry) } }
                }
                val active = Quarry.entries.firstOrNull { it.id == profile.contract }
                button(31, Material.EMERALD, "${active?.title ?: "Aktif sözleşme yok"} · ${profile.contractKills}/${profile.contractGoal}", listOf("Tıkla: tamamlanan sözleşmenin ödülünü al.")) { act { adventure.claimContract(player) } }
                button(33, Material.BARRIER, "Sözleşmeyi iptal et", listOf("Bu sözleşmedeki av ilerlemesi silinir.")) {
                    act { adventure.change(player) { it.copy(contract = null, contractKills = 0) }; "Sözleşme iptal edildi." }
                }
            }
            AdventurePage.BESTIARY -> Quarry.entries.forEachIndexed { index, quarry ->
                button(10 + index, quarry.icon, quarry.title, buildList {
                    add("Avlanan: ${profile.kills[quarry.id] ?: 0}")
                    Adventurer.milestones.forEach { threshold -> add("${if ("${quarry.id}:$threshold" in profile.milestones) "✓" else "◇"} $threshold av → $threshold öz + %2 hasar") }
                    add("Tıkla: sıradaki araştırma ödülünü al.")
                }) { act { adventure.studyQuarry(player, quarry) } }
            }
            AdventurePage.SALVAGE -> {
                holder.view.setItem(13, holder.held)
                val reward = adventure.salvageValue(holder.held)
                button(31, Material.RED_CONCRETE, "Eşyayı kalıcı olarak sök", listOf(
                    "Kazanç: ${reward ?: 0} öz", "Elindeki eşya ve bütün gelişimi silinir.", "Bu işlem geri alınamaz.", "Tıkla: gösterilen eşyayı sökmeyi onayla.",
                )) { act { adventure.salvage(player, holder.held) } }
            }
            AdventurePage.GUIDE -> {
                val chapters = listOf(
                    "1 · Döv ve geliştir" to listOf("Tarif kitabından bir silah döv.", "Kullanarak seviye atlat, ustalık puanı harca.", "Büyü masasından arıtma ve element işle."),
                    "2 · Maceracı ustalığı" to listOf("Günlükteki yaratıkları dövülmüş silahla avla.", "Her av 10 XP + 2 öz; 100 XP bir rütbe.", "Azami rütbe 20. XP ve öz oyuncuya aittir."),
                    "3 · Silah mühürleri" to listOf("Öz ve rütbe ile 20 özellikten seçim yap.", "Silahın 10 ve 20. seviyesinde yeni yuva açılır.", "Mühürler silahla birlikte devredilir."),
                    "4 · Büyü araştırması" to listOf("Kök büyü → öncül → ileri büyü yolunu izle.", "Öğrenilmiş büyü tüketilmez; mana harcar.", "Eski 12 parşömen büyüsü ayrı çalışır."),
                    "5 · Setler" to listOf("Miğfer, göğüslük, pantolon ve bot döv.", "Eline aldığın parçaya aynı seti işle.", "Giyilen 2 ve 4 parçada bonus etkinleşir."),
                    "6 · Av ve günlük" to listOf("Sözleşme yalnızca kabulden sonraki avları sayar.", "Günlük 10/30/75 av eşiklerinde ödül verir.", "Spawner ve yumurta yaratıkları ödül vermez."),
                    "7 · Kombo" to listOf("Aynı hedefe 4 saniye içinde tam vuruş yap.", "8 zincire kadar her adım %2 hasar ekler.", "Hedef değişince veya süre bitince başa döner."),
                    "8 · Söküm" to listOf("Yedek dövülmüş eşyaları öze çevir.", "Kalite ve arıtma söküm getirisini artırır.", "Onayladığın eşya tamamen tüketilir."),
                )
                chapters.forEachIndexed { index, (title, lines) -> holder.view.setItem(10 + index, icon(Material.BOOK, title, lines)) }
            }
        }
        button(49, Material.ARROW, if (page == AdventurePage.HUB) "Kapat" else "+ Macera", emptyList()) {
            if (page == AdventurePage.HUB) player.closeInventory() else open(player)
        }
        holder.view.setItem(53, icon(Material.ECHO_SHARD, "${profile.essence} öz · Rütbe ${profile.rank}"))
        player.openInventory(holder.view)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.getHolder(false) as? AdventureView ?: return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        if (player.uniqueId != holder.owner || !player.hasPermission("auraforge.use") || event.clickedInventory !== holder.view) return
        val action = holder.actions[event.rawSlot] ?: return
        player.scheduler.run(plugin, {
            if (player.openInventory.topInventory === holder.view) action()
        }, null)
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.getHolder(false) is AdventureView) event.isCancelled = true
    }

    private fun icon(material: Material, title: String, lines: List<String> = emptyList()): ItemStack = ItemStack(material).apply {
        editMeta { meta ->
            meta.displayName(plugin.text.parse("<gold>$title").noItalic())
            meta.lore(lines.map { plugin.text.parse("<gray>$it").noItalic() })
        }
    }
}
