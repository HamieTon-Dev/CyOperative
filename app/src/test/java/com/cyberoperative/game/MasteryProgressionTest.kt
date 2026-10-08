package com.cyberoperative.game

import com.cyberoperative.game.data.Mastery
import com.cyberoperative.game.data.Operatives
import com.cyberoperative.game.data.PermanentUpgradeDef
import com.cyberoperative.game.data.PermanentUpgrades
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.ui.menu.MAX_OPERATIVE_LEVEL
import com.cyberoperative.game.ui.menu.operativeLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Endless permanent-upgrade mastery and OP scaling (owner, 2026-10-08: "play never ends"). */
class MasteryProgressionTest {

    private fun def(id: String) = PermanentUpgrades.byId(id)!!

    @Test fun designedRangeIsUnchanged() {
        for (d in PermanentUpgrades.all) for (l in 0..d.maxLevel) assertEquals(l.toFloat(), d.effectiveLevel(l), 1e-4f)
        val cfg = Operatives.buildConfig("operative", mapOf("max_hp" to 5), 1L)
        assertEquals(140f, cfg.baseStats.maxHp, 1e-3f)
        assertEquals(1f, cfg.masteryDpsRatio, 1e-4f)
        assertEquals(1f, cfg.masterySurvivalRatio, 1e-4f)
    }

    @Test fun endlessKeepsGrowingWithDiminishingSteps() {
        val hp = def("max_hp")
        assertEquals(PermanentUpgradeDef.MAX_LEVEL, hp.levelCap)
        var prev = hp.effectiveLevel(hp.maxLevel)
        var prevStep = Float.MAX_VALUE
        for (l in listOf(21, 30, 100, 1000, 9999)) {
            val e = hp.effectiveLevel(l)
            assertTrue("level $l must add more", e > prev)
            prev = e
        }
        for (m in listOf(1, 10, 100, 1000)) {
            val step = hp.effectiveLevel(hp.maxLevel + m + 1) - hp.effectiveLevel(hp.maxLevel + m)
            assertTrue(step < prevStep); prevStep = step
        }
        assertEquals(20f + 2f * kotlin.math.sqrt(9979f), hp.effectiveLevel(9999), 1e-2f)
    }

    @Test fun softStatsStayBelowTheirCeiling() {
        for (d in PermanentUpgrades.all.filter { it.mastery == Mastery.SOFT }) {
            val top = d.effectiveLevel(PermanentUpgradeDef.MAX_LEVEL)
            assertTrue(d.id, top <= d.maxLevel + d.softExtra + 1e-3f)
            assertTrue(d.id, d.effectiveLevel(d.maxLevel + 1) > d.maxLevel)
        }
        val maxed = Operatives.buildConfig("operative", PermanentUpgrades.all.associate { it.id to it.levelCap }, 1L).baseStats
        assertTrue(maxed.armor <= 0.6f)
        assertTrue(maxed.critChance <= 0.85f)
        assertTrue(maxed.moveSpeed < 276f * 1.61f)
    }

    @Test fun hardCappedUpgradesNeverPassTheirMax() {
        for (id in listOf("starting_orbs", "starting_power", "rerolls")) {
            val d = def(id)
            assertEquals(d.maxLevel, d.levelCap)
            assertEquals(d.maxLevel, d.capAt(9999))
        }
        val cfg = Operatives.buildConfig("operative", mapOf("rerolls" to 50, "starting_orbs" to 50), 1L)
        assertEquals(3, cfg.rerolls)
        assertEquals(3, cfg.baseStats.orbCount)
    }

    @Test fun masteryLevelsNeedOpLevels() {
        val hp = def("max_hp")
        assertEquals(20, hp.capAt(0))
        assertEquals(70, hp.capAt(50))
        assertEquals(PermanentUpgradeDef.MAX_LEVEL, hp.capAt(MAX_OPERATIVE_LEVEL))
        for (l in listOf(20, 100, 1000, 9998)) assertTrue(hp.costFor(l + 1) > hp.costFor(l))
    }

    @Test fun threatsKeepScalingPastOp100() {
        var prevHp = 0f
        var prevDmg = 0f
        for (op in listOf(1, 50, 101, 200, 1000, 5000, 9999)) {
            val c = RunConfig(opLevel = op)
            assertTrue(c.opHpMul > prevHp || op == 1)
            assertTrue(c.opDamageMul > prevDmg || op == 1)
            prevHp = c.opHpMul; prevDmg = c.opDamageMul
        }
        assertEquals(3f, RunConfig(opLevel = 101).opHpMul, 1e-3f)
        assertEquals(7.1f, RunConfig(opLevel = 1000).opHpMul, 0.1f)
        assertEquals(11.3f, RunConfig(opLevel = 9999).opHpMul, 0.15f)
        assertEquals(6.2f, RunConfig(opLevel = 9999).opDamageMul, 0.1f)
    }

    @Test fun masteryIsAnsweredByTougherThreatsButStillPaysOff() {
        val core = PermanentUpgrades.all.associate { it.id to it.maxLevel }
        val deep = PermanentUpgrades.all.associate { it.id to it.maxLevel + 500 }
        val c0 = Operatives.buildConfig("operative", core, 1L).copy(opLevel = 600)
        val c1 = Operatives.buildConfig("operative", deep, 1L).copy(opLevel = 600)
        assertTrue(c1.masteryDpsRatio > 1.5f)
        assertTrue(c1.opHpMul > c0.opHpMul)
        // Player damage grew more than threat HP did.
        assertTrue(c1.masteryDpsRatio > c1.opHpMul / c0.opHpMul)
        assertTrue(c1.masterySurvivalRatio > c1.opDamageMul / c0.opDamageMul)
    }

    @Test fun operativeLevelReachesNineThousandNineHundredNinetyNine() {
        assertEquals(1, operativeLevel(0))
        assertEquals(MAX_OPERATIVE_LEVEL, operativeLevel(Long.MAX_VALUE / 4))
        assertTrue(operativeLevel(100_000_000L) in 1000 until MAX_OPERATIVE_LEVEL)
    }
}
