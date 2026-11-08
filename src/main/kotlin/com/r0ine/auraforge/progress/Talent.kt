package com.r0ine.auraforge.progress

import org.bukkit.Material

enum class Branch(val display: String, val hex: String, val pane: Material, val column: Int) {
    POWER("Güç Yolu", "FF6B5B", Material.RED_STAINED_GLASS_PANE, 0),
    SWIFT("Rüzgâr Yolu", "7EE8C8", Material.LIME_STAINED_GLASS_PANE, 3),
    ARCANE("Element Yolu", "B98CFF", Material.PURPLE_STAINED_GLASS_PANE, 6),
}

enum class Talent(
    val branch: Branch,
    val row: Int,
    val side: Int,
    val maxRank: Int,
    val perRank: Double,
    val icon: Material,
    val display: String,
    val describe: (Double) -> String,
) {
    KEEN_EDGE(Branch.POWER, 0, 0, 5, 0.04, Material.IRON_SWORD, "Keskin Ağız",
        { "Vuruş hasarı +%${pct(it)}" }),
    HEAVY_BLOW(Branch.POWER, 1, -1, 3, 0.05, Material.ANVIL, "Ağır Darbe",
        { "%${pct(it)} ihtimalle hedefi yavaşlatıp zayıflatır" }),
    BLEED(Branch.POWER, 1, 1, 3, 0.06, Material.REDSTONE, "Kanatma",
        { "%${pct(it)} ihtimalle 3 sn solma etkisi" }),
    EXECUTE(Branch.POWER, 2, 0, 3, 0.10, Material.WITHER_SKELETON_SKULL, "İnfaz",
        { "Canı %30 altındaki hedefe +%${pct(it)} hasar" }),
    CRIT(Branch.POWER, 3, -1, 5, 0.04, Material.GOLDEN_SWORD, "Kritik Vuruş",
        { "%${pct(it)} ihtimalle 1,5 kat hasar" }),
    LIFESTEAL(Branch.POWER, 3, 1, 5, 0.03, Material.GHAST_TEAR, "Can Emme",
        { "Verilen hasarın %${pct(it)} kadarı can olarak döner" }),
    BERSERK(Branch.POWER, 4, 0, 1, 0.25, Material.BLAZE_POWDER, "Öfke",
        { "Canın %40 altındayken +%${pct(it)} hasar" }),

    TEMPO(Branch.SWIFT, 0, 0, 5, 0.04, Material.CLOCK, "Tempo",
        { "Yetenek bekleme süresi -%${pct(it)}" }),
    HUNT(Branch.SWIFT, 1, -1, 2, 1.0, Material.RABBIT_FOOT, "Av Coşkusu",
        { "Öldürünce 3 sn Hız ${roman(it)}" }),
    SURGE(Branch.SWIFT, 1, 1, 3, 0.05, Material.FEATHER, "Dalga Gücü",
        { "Yetenek gücü +%${pct(it)}" }),
    ECHO(Branch.SWIFT, 2, 0, 3, 0.08, Material.ECHO_SHARD, "Yankı",
        { "Yetenek kullanınca %${pct(it)} ihtimalle bekleme sıfırlanır" }),
    EVASION(Branch.SWIFT, 3, -1, 5, 0.04, Material.PHANTOM_MEMBRANE, "Sıyrılma",
        { "%${pct(it)} ihtimalle gelen vuruşu savuşturur" }),
    MOMENTUM(Branch.SWIFT, 3, 1, 3, 1.0, Material.SUGAR, "Kinetik",
        { "Her öldürmede yetenek beklemesi -${it.toInt()} sn" }),
    STORM_STEP(Branch.SWIFT, 4, 0, 1, 1.0, Material.WIND_CHARGE, "Fırtına Adımı",
        { "Yetenek kullanınca 3 sn Hız II ve Zıplama I" }),

    RESONANCE(Branch.ARCANE, 0, 0, 5, 0.06, Material.AMETHYST_SHARD, "Rezonans",
        { "Element tetiklenme şansı +%${pct(it)}" }),
    AMPLIFY(Branch.ARCANE, 1, -1, 3, 0.15, Material.GLOWSTONE_DUST, "Yoğunluk",
        { "Element etkisinin gücü +%${pct(it)}" }),
    ELEMENT_WARD(Branch.ARCANE, 1, 1, 1, 1.0, Material.SHIELD, "Element Zırhı",
        { "Element tetiklenince 3 sn Direnç I" }),
    CHAIN(Branch.ARCANE, 2, 0, 3, 0.10, Material.CHAIN, "Seken Element",
        { "Tetiklenen element %${pct(it)} ihtimalle yakındaki ikinci hedefe sıçrar" }),
    OVERLOAD(Branch.ARCANE, 3, -1, 1, 1.0, Material.TNT, "Aşırı Yük",
        { "Her 5. tetiklemede çevreye element patlaması" }),
    SIPHON(Branch.ARCANE, 3, 1, 2, 2.0, Material.HONEY_BOTTLE, "Element Emme",
        { "Her tetiklemede ${it.toInt() / 2} kalp yenilenir" }),
    AVATAR(Branch.ARCANE, 4, 0, 1, 1.0, Material.NETHER_STAR, "Element Avatarı",
        { "Her 12. tetiklemede elementin çift büyüsü kendiliğinden patlar" });

    val key: String get() = name.lowercase()
    val slot: Int get() = row * 9 + branch.column + 1 + side

    fun value(rank: Int): Double = perRank * rank

    fun rival(): Talent? = if (side == 0) null else entries.firstOrNull { it.branch == branch && it.row == row && it.side == -side }

    companion object {
        fun byKey(key: String): Talent? = entries.firstOrNull { it.key == key }
        fun at(slot: Int): Talent? = entries.firstOrNull { it.slot == slot }
        fun inRow(branch: Branch, row: Int): List<Talent> = entries.filter { it.branch == branch && it.row == row }
    }
}

private fun pct(value: Double): String {
    val percent = value * 100
    return if (percent % 1.0 == 0.0) percent.toInt().toString() else String.format(java.util.Locale.of("tr"), "%.1f", percent)
}

private fun roman(value: Double): String = when (value.toInt()) {
    1 -> "I"
    2 -> "II"
    3 -> "III"
    else -> value.toInt().toString()
}
