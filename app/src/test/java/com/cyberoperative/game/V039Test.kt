package com.cyberoperative.game

import com.cyberoperative.game.data.Gear
import com.cyberoperative.game.data.GearItem
import com.cyberoperative.game.data.GearRarity
import com.cyberoperative.game.data.GearSlot
import com.cyberoperative.game.data.Pets
import com.cyberoperative.game.data.Rarity
import com.cyberoperative.game.data.Upgrades
import com.cyberoperative.game.engine.Difficulty
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.RunBuild
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.engine.RunStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.39.0: GLITCHED LEGENDARY and ENCRYPTED, the new chip tiers, Hard balance, pet abilities. */
class V039Test {
    @Test fun newRaritiesSitWhereTheOwnerAskedAndOldSavesStillLoad() {
        assertEquals(GearRarity.LEGENDARY.ordinal + 1, GearRarity.GLITCHED.ordinal)
        assertEquals(GearRarity.entries.last(), GearRarity.ENCRYPTED)
        assertEquals(Rarity.LEGENDARY.ordinal + 1, Rarity.GLITCHED.ordinal)
        // "ENCRYPTED" on disk was FORGED's old name and must stay FORGED.
        val old = Gear.decode("""[{"u":1,"s":"CORE","r":"ENCRYPTED","a":0,"l":10,"b":5.0}]""").single()
        assertEquals(GearRarity.FORGED, old.rarity)
        val item = GearItem(2, GearSlot.CORE, GearRarity.ENCRYPTED, 0, 10, 5f)
        assertEquals(GearRarity.ENCRYPTED, Gear.decode(Gear.encode(listOf(item))).single().rarity)
        for (r in GearRarity.entries) GearItem(3, GearSlot.HELMET, r, 1, 10, 5f).name // every rarity has names
    }

    @Test fun glitchedAndEncryptedDropOnEveryDifficultyEncryptedVeryRare() {
        val o = Gear.odds(boss = true, inferno = 0)
        assertEquals(100f, o.sum(), 0.01f)
        assertTrue(o[GearRarity.GLITCHED.ordinal] in 1.5f..3f)
        assertTrue(o[GearRarity.ENCRYPTED.ordinal] in 0.1f..0.5f)
        assertEquals(0f, Gear.shopOdds(10)[GearRarity.ENCRYPTED.ordinal], 0f)
    }

    @Test fun goldenAndUpHit30PercentHarder() {
        val s1 = RunStats(); val s2 = RunStats()
        Gear.apply(s1, listOf(GearItem(1, GearSlot.CORE, GearRarity.EPIC, 0, 10, 10f)), "operative")
        Gear.apply(s2, listOf(GearItem(1, GearSlot.CORE, GearRarity.LEGENDARY, 0, 10, 10f)), "operative")
        assertEquals(110f, s1.maxHp, 0.01f)
        assertEquals(113f, s2.maxHp, 0.01f)
    }

    @Test fun everyNewChipTierHasChipsAndEncryptedStaysRare() {
        for (r in Rarity.entries.filter { it.special }) assertTrue("$r", Upgrades.all.any { it.rarity == r })
        val b = RunBuild(RunStats())
        val enc = Upgrades.all.filter { it.rarity == Rarity.ENCRYPTED }.sumOf { b.chanceOf(it, 0.3f).toDouble() }
        assertTrue("encrypted per card $enc", enc in 0.0005..0.01)
        b.take(Upgrades.DOUBLE_PAYLOAD); b.take(Upgrades.PHANTOM_CODE)
        assertEquals(RunStats().damage * 2f, b.stats.damage, 0.01f)
        assertEquals(0.1f, b.stats.dodge, 0.001f)
    }

    @Test fun failsafeSurvivesALethalHitAndFullRestoreHealsToFull() {
        val g = GameEngine(RunConfig(baseStats = RunStats(), seed = 5L, freeRevives = 0, consumables = true, powerGovernor = false))
        g.build.take(Upgrades.FAILSAFE); g.build.take(Upgrades.FULL_RESTORE)
        val charges = g.operatives[0].hpCharges
        g.operatives[0].hp = 5f // the next hit is lethal (no hit takes more than a share of max HP)
        g.damagePlayer(1e9f, g.px + 10f, g.py, ignoreInvuln = true)
        assertEquals(g.stats.maxHp, g.operatives[0].hp, 0.01f)
        assertEquals(charges - 1, g.operatives[0].hpCharges)
    }

    @Test fun hardEasesFromWorldThreeButStaysHarderThanNormal() {
        val early = GameEngine(RunConfig(seed = 2L, difficulty = Difficulty.HARD))
        assertEquals(1f, early.hardEase, 0f)
        val late = GameEngine(RunConfig(seed = 2L, difficulty = Difficulty.HARD)).also { it.debugJumpToLevel(90) }
        assertTrue(late.hardEase < 1f)
        assertEquals(1f, GameEngine(RunConfig(seed = 2L, difficulty = Difficulty.MEDIUM)).also { it.debugJumpToLevel(90) }.hardEase, 0f)
    }

    @Test fun petAbilitiesFollowTheOwnersRules() {
        for (r in GearRarity.entries) {
            val counts = (1L..400L).map { Pets.abilitiesFor(it, 5, r).size }
            assertTrue(counts.all { it in 1..Pets.MAX_ABILITIES })
            if (r < GearRarity.EPIC) assertTrue("$r", counts.all { it == 1 })
            if (r < GearRarity.GLITCHED) assertTrue("$r", counts.all { it <= 2 })
            if (r >= GearRarity.GLITCHED) assertTrue("$r", counts.any { it > 2 })
        }
    }
}
