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
        assertTrue(Scaling.enemyHp(60) > 20f)
        assertTrue(Scaling.enemyDamage(60) > 7f)
        assertTrue(Scaling.enemyHp(5000).isFinite())
        fun hp(op: Int): Float {
            val g = GameEngine(RunConfig(seed = 2L, opLevel = op))
            return g.spawnEnemyAt(Enemies.MALWARE, null, 200f, 300f, telegraph = false)!!.maxHp
        }
        assertEquals(1.5f, hp(26) / hp(1), 0.01f)
        assertEquals(3f, RunConfig(opLevel = 500).opHpMul, 0.001f)
    }
}
