package com.r0ine.auraforge.adventure

import org.bukkit.Material
import org.bukkit.entity.EntityType

enum class WeaponTrait(val title: String, val description: String, val icon: Material, val rank: Int) {
    EMBER("Kor İzi", "Her tam vuruş hedefi 3 saniye yakar.", Material.BLAZE_POWDER, 1),
    FROST("Kırağı", "Hedefe 2 saniye Yavaşlık I verir.", Material.SNOWBALL, 1),
    VENOM("Zehir Dişi", "Zehre bağışık olmayan hedefe 3 saniye Zehir I.", Material.SPIDER_EYE, 2),
    RUPTURE("Yırtılma", "Hedefe 2 saniye Wither I verir.", Material.REDSTONE, 3),
    EXECUTION("Son Hüküm", "%30 candan düşük hedefe %20 ek hasar.", Material.IRON_AXE, 4),
    AMBUSH("İlk Yara", "%90 candan yüksek hedefe %20 ek hasar.", Material.FLINT, 2),
    GIANTSLAYER("Dev Avcısı", "Azami canı 40 ve üstü hedefe %20 ek hasar.", Material.DIAMOND_AXE, 5),
    SUNDER("Zırh Çatlağı", "Hedefin zırh puanı başına %1 hasar; sınır %20.", Material.IRON_CHESTPLATE, 4),
    VAMPIRIC("Kan Bağı", "Verilen son hasarın %6'sı kadar iyileştirir; en çok 1 can.", Material.FERMENTED_SPIDER_EYE, 5),
    ARCANE_LEECH("Mana Sızıntısı", "Tam vuruşta 3 mana yeniler.", Material.LAPIS_LAZULI, 2),
    MOMENTUM("İvme", "Kombo başına %2 ek hasar; sınır %16.", Material.FEATHER, 3),
    BACKSTAB("Sırt Darbesi", "Hedefin arkasından vurunca %25 ek hasar.", Material.SHEARS, 5),
    AERIAL("Hava Avcısı", "Yerden kesilmiş hedefe %15 ek hasar.", Material.PHANTOM_MEMBRANE, 4),
    FROSTBITE("Buz Kıran", "Donmuş veya yavaş hedefe %18 ek hasar.", Material.BLUE_ICE, 6),
    KINDLING("Köz Besleyen", "Yanan hedefe %18 ek hasar.", Material.FIRE_CHARGE, 6),
    REPEL("Savuruş", "Hedefi senden uzağa iter.", Material.WIND_CHARGE, 2),
    GRAVITY("Ağırlık", "Hedefin yatay hızını keser ve yere bastırır.", Material.ANVIL, 7),
    HUNTER("Avcı Mührü", "Yaratık günlüğündeki türlere %12 ek hasar.", Material.BONE, 3),
    FOCUS("Odak", "En az %80 manayla %12 ek hasar.", Material.AMETHYST_SHARD, 7),
    REAPER("Ruh Hasadı", "Bu silahla av öldürünce fazladan 2 öz kazanırsın.", Material.ECHO_SHARD, 8);

    val id: String get() = name.lowercase()
    val cost: Int get() = 12 + rank * 4
}

enum class ArmorSet(val title: String, val icon: Material, val two: String, val four: String) {
    ASH("Kül Muhafızı", Material.BLAZE_POWDER, "Yanan hedeflere %10 hasar.", "Ateş hasarı %35 azalır; vuruşlar yakar."),
    GLACIER("Buzul Nöbetçisi", Material.BLUE_ICE, "Yakın dövüş hasarı %6 azalır.", "Vuran düşmana 2 saniye Yavaşlık II."),
    TEMPEST("Fırtına Gezgini", Material.FEATHER, "Kombo zaman aşımı 4 yerine 6 saniye.", "Tam vuruşta 2 saniye Hız I."),
    EARTHEN("Kök Bekçisi", Material.MOSS_BLOCK, "Gelen savaş hasarı %8 azalır.", "Kombo 5'e ulaşınca 1 can yeniler."),
    ASTRAL("Yıldız Dokumacı", Material.AMETHYST_CLUSTER, "Mana sınırına +20.", "Büyüler %20 daha az mana harcar.");

    val id: String get() = name.lowercase()
}

enum class LearnedSpell(
    val title: String, val description: String, val icon: Material,
    val rank: Int, val mana: Int, val cooldown: Int, val parent: String? = null,
) {
    CINDER_LANCE("Kor Mızrağı", "18 bloktaki hedefe 6 hasar ve 4 saniye yanma.", Material.BLAZE_ROD, 1, 18, 6),
    GLACIAL_PRISON("Buz Hapishanesi", "16 bloktaki hedefe 4 hasar ve 3 saniye Yavaşlık IV.", Material.PACKED_ICE, 2, 22, 9),
    STORM_LINK("Şimşek Örgüsü", "Yakındaki en çok 4 görünür hedefe 5 hasar.", Material.LIGHTNING_ROD, 3, 30, 12),
    STONE_SKIN("Taş Deri", "6 saniye Direnç II.", Material.COBBLED_DEEPSLATE, 2, 22, 14),
    VERDANT_PULSE("Yaşam Filizi", "Kendine 6 can ve 4 saniye Yenilenme I.", Material.FLOWERING_AZALEA, 3, 30, 16),
    VOID_HOOK("Boşluk Kancası", "16 bloktaki hedefe 4 hasar; hedefi kendine çeker.", Material.ENDER_PEARL, 4, 26, 10, "CINDER_LANCE"),
    SOLAR_NOVA("Güneş Tacı", "6 blokta en çok 8 hedefe 7 hasar; seni ateşten temizler.", Material.SUNFLOWER, 6, 40, 18, "CINDER_LANCE"),
    FROST_SHATTER("Kristal Kırımı", "7 blokta 5 hasar; yavaş veya donmuş hedefte 9 hasar.", Material.AMETHYST_CLUSTER, 5, 32, 12, "GLACIAL_PRISON"),
    WINDSTEP("Rüzgâr İzleri", "İleri atılma, 5 saniye Yavaş Düşüş ve Hız II.", Material.WIND_CHARGE, 4, 20, 8, "STORM_LINK"),
    ECLIPSE("Tutulma", "18 bloktaki hedefe 8 hasar; canı %30 altındaysa 12.", Material.ECHO_SHARD, 8, 45, 20, "VOID_HOOK");

    val id: String get() = name.lowercase()
    val cost: Int get() = 15 + rank * 8
}

enum class Quarry(val type: EntityType, val title: String, val icon: Material) {
    ZOMBIE(EntityType.ZOMBIE, "Zombi", Material.ROTTEN_FLESH),
    SKELETON(EntityType.SKELETON, "İskelet", Material.BONE),
    SPIDER(EntityType.SPIDER, "Örümcek", Material.SPIDER_EYE),
    CREEPER(EntityType.CREEPER, "Creeper", Material.GUNPOWDER),
    ENDERMAN(EntityType.ENDERMAN, "Enderman", Material.ENDER_PEARL),
    BLAZE(EntityType.BLAZE, "Blaze", Material.BLAZE_ROD),
    PILLAGER(EntityType.PILLAGER, "Yağmacı", Material.CROSSBOW),
    WITHER_SKELETON(EntityType.WITHER_SKELETON, "Wither İskeleti", Material.COAL);

    val id: String get() = name.lowercase()
    companion object {
        fun of(type: EntityType): Quarry? = entries.firstOrNull { it.type == type }
    }
}
