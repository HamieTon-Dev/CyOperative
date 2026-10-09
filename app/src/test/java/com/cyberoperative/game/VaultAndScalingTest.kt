package com.cyberoperative.game

import com.cyberoperative.game.core.Scaling
import com.cyberoperative.game.data.Enemies
import com.cyberoperative.game.data.Events
import com.cyberoperative.game.engine.Arena
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.LevelPlanner
import com.cyberoperative.game.engine.Phase
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.engine.RunStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class VaultAndScalingTest {

    @Test fun vaultPaysHundredPerLevelFromAThousand() {
        assertEquals(1000, GameEngine.vaultPayout(1))
        assertEquals(1000, GameEngine.vaultPayout(10))
        assertEquals(7000, GameEngine.vaultPayout(70))
    }

    @Test fun clearedVaultCacheOpensShakesAndPaysOut() {
        val g = GameEngine(RunConfig(baseStats = RunStats().apply { maxHp = 1e9f }, seed = 3L, freeRevives = 0))
        g.debugStartPlan(LevelPlanner.eventPlan(70, Random(1), Events.DATA_VAULT, null))
        assertTrue(g.vaultPresent)
        val obstaclesWithVault = g.arena.obstacles.size
        // Touching it during combat does nothing.
        var guard = 0
        while (g.phase != Phase.PORTAL && guard++ < 6000) {
            for (e in g.enemies.items) if (e.active) g.killEnemy(e)
            if (g.phase == Phase.UPGRADE) g.chooseUpgrade(0)
            g.update(1f / 60f)
        }
        assertTrue(g.vaultReady)
        val before = g.eurosEarned
        val r = Arena.vaultObstacle(g.plan.arena).rect
        guard = 0
        while (g.vaultOpening <= 0f && g.vaultPresent && guard++ < 1200) {
            g.setInput(r.centerX - g.px, r.bottom + 20f - g.py)
            g.update(1f / 60f)
        }
        g.setInput(0f, 0f)
        assertTrue("cache should start opening", g.vaultOpening > 0f)
        repeat(120) { g.update(1f / 60f) }
        assertFalse(g.vaultPresent)
        assertEquals(before + 7000, g.eurosEarned)
        assertEquals(obstaclesWithVault - 1, g.arena.obstacles.size)
    }

    @Test fun lateLevelsCompoundAndOpLevelToughensThreats() {
        assertEquals(Scaling.enemyHp(30), 1f + 0.11f * 29 + 0.0009f * 29 * 29, 0.001f)
        assertTrue(Scaling.enemyHp(60) > 14f)
        assertTrue(Scaling.enemyDamage(60) > 5f)
        assertTrue(Scaling.enemyHp(5000).isFinite())
        fun hp(op: Int, level: Int): Float {
            val g = GameEngine(RunConfig(seed = 2L, opLevel = op))
            if (level > 1) g.debugJumpToLevel(level)
            return g.spawnEnemyAt(Enemies.MALWARE, null, 200f, 300f, telegraph = false)!!.maxHp
        }
        // Owner, 2026-10-09: OP level changes nothing up to level 30, then fades in (full at 80).
        assertEquals(1f, hp(26, 1) / hp(1, 1), 0.001f)
        assertEquals(1f, hp(26, 30) / hp(1, 30), 0.001f)
        assertEquals(1.5f, hp(26, 80) / hp(1, 80), 0.01f)
        assertEquals(1.25f, hp(26, 55) / hp(1, 55), 0.01f)
        assertEquals(3f, RunConfig(opLevel = 101).opHpMul, 0.001f)
        // Past OP 101 threats keep toughening on a log curve (endless mastery, 0.9.7).
        assertEquals(5.89f, RunConfig(opLevel = 500).opHpMul, 0.01f)
    }

    @Test fun threatsAdaptToBigBuildsOnlyLater() {
        assertEquals(1f, Scaling.adaptiveHp(30, 200, 30), 0.0001f)
        assertTrue(Scaling.adaptiveHp(80, 100, 7) > 1.5f)
        assertTrue(Scaling.adaptiveHp(60, 100, 7) > Scaling.adaptiveHp(60, 20, 2))
        assertEquals(70, Scaling.campaignThreats(200))
        assertTrue(Scaling.eliteChance(90) > Scaling.eliteChance(50))

        fun hpWith(weapons: List<String>): Float {
            val g = GameEngine(RunConfig(seed = 2L))
            for (id in weapons) g.build.take(com.cyberoperative.game.data.Upgrades.byId(id))
            g.debugJumpToLevel(80)
            return g.spawnEnemyAt(Enemies.MALWARE, null, 200f, 300f, telegraph = false)!!.maxHp
        }
        val lean = hpWith(emptyList())
        val stacked = hpWith(com.cyberoperative.game.data.Weapons.all.take(7).map { it.id })
        assertTrue("stacked build should face tougher threats ($stacked vs $lean)", stacked > lean * 1.4f)
    }

    @Test fun hardStaysHarderThanNormalAndEarlyLevelsAreUntouched() {
        fun hp(d: com.cyberoperative.game.engine.Difficulty, level: Int): Float {
            val g = GameEngine(RunConfig(seed = 2L, opLevel = 60, difficulty = d))
            if (level > 1) g.debugJumpToLevel(level)
            return g.spawnEnemyAt(Enemies.MALWARE, null, 200f, 300f, telegraph = false)!!.maxHp
        }

        for (l in listOf(1, 10, 30, 45, 60, 90, 150)) {
            assertTrue("level $l", hp(com.cyberoperative.game.engine.Difficulty.HARD, l) > hp(com.cyberoperative.game.engine.Difficulty.MEDIUM, l))
            assertTrue("level $l", hp(com.cyberoperative.game.engine.Difficulty.MEDIUM, l) > hp(com.cyberoperative.game.engine.Difficulty.EASY, l))
        }
        // Levels 1-30 on HARD: just the base curve × 1.45, as before the deep-run extras.
        assertEquals(1.45f, hp(com.cyberoperative.game.engine.Difficulty.HARD, 30) / (hp(com.cyberoperative.game.engine.Difficulty.MEDIUM, 30)), 0.01f)
    }
}
