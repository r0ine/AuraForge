package com.r0ine.auraforge.adventure

import com.r0ine.auraforge.core.SaveFile
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdventurerTest {
    @Test
    fun `av kredisi sadece aktif hedefin sozlesmesini ilerletir`() {
        val started = Adventurer(contract = Quarry.SKELETON.id)
        val wrongTarget = started.credited(Quarry.ZOMBIE)
        assertEquals(0, wrongTarget.contractKills)
        assertEquals(1, wrongTarget.kills[Quarry.ZOMBIE.id])
        val completed = (1..20).fold(wrongTarget) { profile, _ -> profile.credited(Quarry.SKELETON) }
        assertEquals(5, completed.contractKills)
        assertEquals(20, completed.kills[Quarry.SKELETON.id])
        assertEquals(42, completed.essence)
        assertEquals(3, completed.rank)
    }

    @Test
    fun `rutbe ve sozlesme zorlugu sinirli kalir`() {
        assertEquals(1, Adventurer(xp = 99).rank)
        assertEquals(2, Adventurer(xp = 100).rank)
        assertEquals(20, Adventurer(xp = 1900).credited(Quarry.BLAZE).rank)
        assertEquals(1900, Adventurer(xp = 1900).credited(Quarry.BLAZE).xp)
        assertEquals(15, Adventurer(contracts = 100).contractGoal)
        assertEquals(45, Adventurer(contracts = 100).contractReward)
    }

    @Test
    fun `arastirma rutbe oncul ve oz gerektirir`() {
        val spell = LearnedSpell.ECLIPSE
        assertNotNull(Adventurer(essence = 1000).researchLock(spell))
        assertNotNull(Adventurer(xp = 1900, essence = 1000).researchLock(spell))
        assertNotNull(Adventurer(xp = 1900, essence = 0, studied = setOf("void_hook")).researchLock(spell))
        assertNull(Adventurer(xp = 1900, essence = spell.cost, studied = setOf("void_hook")).researchLock(spell))
        assertNotNull(Adventurer(xp = 1900, essence = 1000, studied = setOf(spell.id)).researchLock(spell))
    }

    @Test
    fun `mana yeniden giriste siniri asmaz ve saat geri giderse eksilmez`() {
        val profile = Adventurer(mana = 20.0, manaAt = 10_000)
        assertEquals(40.0, profile.regenerated(15_000, 100.0))
        assertEquals(20.0, profile.regenerated(5_000, 100.0))
        assertEquals(100.0, profile.regenerated(1_000_000, 100.0))
        assertEquals(80.0, Adventurer(mana = 140.0, manaAt = 10_000).regenerated(10_000, 80.0))
    }

    @Test
    fun `muhur yuvalari yalnizca on ve yirminci seviyede acilir`() {
        assertEquals(listOf(1, 1, 2, 2, 3, 3), listOf(1, 9, 10, 19, 20, 30).map(Adventurer::traitSlots))
    }

    @Test
    fun `eski kayit dosyalari yeni profil olmadan yuklenir`() {
        val saved = Json.decodeFromString<SaveFile>("""{"version":1,"forges":[],"smiths":{},"altars":[]}""")
        assertTrue(saved.adventurers.isEmpty())
    }

    @Test
    fun `buyuler sozlesme mana ve beklemeler kayit dongusunde korunur`() {
        val profile = Adventurer(xp = 600, essence = 72, studied = setOf("cinder_lance"), selected = "cinder_lance",
            contract = "blaze", contractKills = 3, contracts = 2, kills = mapOf("blaze" to 30),
            milestones = setOf("blaze:10"), mana = 42.5, manaAt = 17000, cooldowns = mapOf("cinder_lance" to 23000))
        val saved = SaveFile(adventurers = mapOf("player" to profile))
        assertEquals(saved, Json.decodeFromString<SaveFile>(Json.encodeToString(saved)))
    }

    @Test
    fun `icerik kimlikleri benzersiz ve buyu agaci dongusuzdur`() {
        assertEquals(20, WeaponTrait.entries.size)
        assertEquals(10, LearnedSpell.entries.size)
        assertEquals(5, ArmorSet.entries.size)
        assertEquals(20, WeaponTrait.entries.map { it.id }.toSet().size)
        LearnedSpell.entries.forEach { spell ->
            val visited = mutableSetOf<String>()
            var node: LearnedSpell? = spell
            while (node != null) {
                assertTrue(visited.add(node.id))
                val parent = node.parent?.let(LearnedSpell::valueOf)
                if (parent != null) assertTrue(parent.rank <= node.rank)
                node = parent
            }
            assertFalse(spell.description.isBlank())
        }
    }
}
