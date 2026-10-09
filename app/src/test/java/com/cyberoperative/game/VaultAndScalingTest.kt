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

    @Test fun opLevelIsTheOnlyDifficultyExtra() {
        // Owner, 2026-10-09: base curve only (no late ramp, no build-size scaling)…
        assertEquals(Scaling.enemyHp(60), 1f + 0.11f * 59 + 0.0009f * 59 * 59, 0.001f)
        assertTrue(Scaling.enemyHp(5000).isFinite())
        fun hp(op: Int, level: Int, d: com.cyberoperative.game.engine.Difficulty = com.cyberoperative.game.engine.Difficulty.MEDIUM): Float {
            val g = GameEngine(RunConfig(seed = 2L, opLevel = op, difficulty = d))
            if (level > 1) g.debugJumpToLevel(level)
            return g.spawnEnemyAt(Enemies.MALWARE, null, 200f, 300f, telegraph = false)!!.maxHp
        }
        // …and it scales with OP level from level 1: +2% HP per OP level, half on EASY.
        assertEquals(1.5f, hp(26, 1) / hp(1, 1), 0.01f)
        assertEquals(1.5f, hp(26, 60) / hp(1, 60), 0.01f)
        val easy = com.cyberoperative.game.engine.Difficulty.EASY
        assertEquals(1.25f, hp(26, 1, easy) / hp(1, 1, easy), 0.01f)
        assertEquals(3f, RunConfig(opLevel = 101).opHpMul, 0.001f)
        assertEquals(5.89f, RunConfig(opLevel = 500).opHpMul, 0.01f)
    }

    @Test fun bigBuildsNoLongerToughenThreats() {
        fun hpWith(weapons: List<String>): Float {
            val g = GameEngine(RunConfig(seed = 2L))
            for (id in weapons) g.build.take(com.cyberoperative.game.data.Upgrades.byId(id))
            g.debugJumpToLevel(80)
            return g.spawnEnemyAt(Enemies.MALWARE, null, 200f, 300f, telegraph = false)!!.maxHp
        }
        assertEquals(hpWith(emptyList()), hpWith(com.cyberoperative.game.data.Weapons.all.take(7).map { it.id }), 0.01f)
        // Deep levels still get busier and more elite.
        assertEquals(70, Scaling.campaignThreats(200))
        assertTrue(Scaling.campaignThreats(80) > Scaling.campaignThreats(60))
        assertTrue(Scaling.eliteChance(80) > Scaling.eliteChance(50))
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
        // HARD vs NORMAL at the same OP level: ×1.45 at every level.
        assertEquals(1.45f, hp(com.cyberoperative.game.engine.Difficulty.HARD, 30) / (hp(com.cyberoperative.game.engine.Difficulty.MEDIUM, 30)), 0.01f)
        assertEquals(1.45f, hp(com.cyberoperative.game.engine.Difficulty.HARD, 90) / (hp(com.cyberoperative.game.engine.Difficulty.MEDIUM, 90)), 0.01f)
    }
}
