package com.r0ine.auraforge.adventure

import kotlinx.serialization.Serializable

@Serializable
data class Adventurer(
    val xp: Int = 0,
    val essence: Int = 0,
    val kills: Map<String, Int> = emptyMap(),
    val studied: Set<String> = emptySet(),
    val selected: String? = null,
    val contract: String? = null,
    val contractKills: Int = 0,
    val contracts: Int = 0,
    val milestones: Set<String> = emptySet(),
    val mana: Double = 100.0,
    val manaAt: Long = 0,
    val cooldowns: Map<String, Long> = emptyMap(),
) {
    val rank: Int get() = (1 + xp / 100).coerceAtMost(20)
    val contractGoal: Int get() = 5 + contracts.coerceAtMost(10)
    val contractReward: Int get() = 15 + contracts.coerceAtMost(10) * 3

    fun credited(quarry: Quarry): Adventurer = copy(
        xp = (xp + 10).coerceAtMost(1900),
        essence = (essence + 2).coerceAtMost(1_000_000),
        kills = kills + (quarry.id to ((kills[quarry.id] ?: 0) + 1).coerceAtMost(1_000_000)),
        contractKills = if (contract == quarry.id) (contractKills + 1).coerceAtMost(contractGoal) else contractKills,
    )

    fun researchLock(spell: LearnedSpell): String? = when {
        spell.id in studied -> "Öğrenildi"
        rank < spell.rank -> "Maceracı rütbesi ${spell.rank} gerekli"
        spell.parent != null && spell.parent.lowercase() !in studied -> "Önce ${LearnedSpell.valueOf(spell.parent).title} öğren"
        essence < spell.cost -> "${spell.cost} öz gerekli"
        else -> null
    }

    fun regenerated(now: Long, maximum: Double): Double =
        (mana + ((now - manaAt).coerceAtLeast(0) / 1000.0) * 4).coerceIn(0.0, maximum)

    companion object {
        val milestones = listOf(10, 30, 75)
        fun traitSlots(weaponLevel: Int): Int = when {
            weaponLevel >= 20 -> 3
            weaponLevel >= 10 -> 2
            else -> 1
        }
    }
}
