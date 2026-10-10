package com.cyberoperative.game

import com.cyberoperative.game.data.BossRole
import com.cyberoperative.game.data.Bosses
import com.cyberoperative.game.engine.BossBrain
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.LevelPlanner
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.engine.RunStats
import com.cyberoperative.game.engine.ScreenFx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Boss Expansion Vol. 1, Stage A: metadata, dossier data, screen effects, placement. */
class BossExpansionTest {

    @Test fun everyBossHasDossierData() {
        for (b in Bosses.roster) {
            assertTrue("${b.id} tier", b.tier in 1..4)
            assertTrue("${b.id} abilities", b.abilityNames().isNotEmpty())
            assertTrue("${b.id} abilities ≤ 4", b.abilityNames().size <= 4)
            assertTrue("${b.id} bounty", b.euros > 0)
            assertTrue(b.armor >= 0f)
        }
        assertEquals(BossRole.TANK, Bosses.byId("good_game")?.role ?: BossRole.TANK)
        assertEquals(Bosses.classics + Bosses.expansion, Bosses.roster)
    }

    @Test fun classicsKeepTheirLevels() {
        for ((i, b) in Bosses.classics.withIndex()) assertEquals(b, Bosses.forLevel(10 * (i + 1)))
    }

    @Test fun rareBossNeverBeforeItsLevel() {
        for (seed in 0 until 200) assertNull(LevelPlanner.rollRareBoss(LevelPlanner.RARE_BOSS_FROM - 10, Random(seed)))
    }

    @Test fun screenFxTiming() {
        val fx = ScreenFx()
        fx.hitStop(0.1f)
        assertEquals(0f, fx.tick(0.05f), 0f)
        assertEquals(0f, fx.tick(0.05f), 0f)
        assertEquals(0.05f, fx.tick(0.05f), 1e-6f)
        fx.slowMotion(1f, 0.3f)
        assertEquals(0.03f, fx.tick(0.1f), 1e-5f)
        fx.shake(10f, 1f)
        assertTrue(fx.shakeNow > 9f)
        fx.shake(2f, 1f) // weaker shake doesn't override a stronger one
        assertTrue(fx.shakeNow > 9f)
        fx.tick(1f)
        assertEquals(0f, fx.shakeNow, 0f)
    }

    @Test fun bossDossierAndKillBeat() {
        val s = RunStats().apply { maxHp = 1e7f; damage = 1e5f; fireRate = 4f; range = 2000f }
        val g = GameEngine(RunConfig(baseStats = s, seed = 5L, freeRevives = 0))
        g.debugStartPlan(LevelPlanner.bossPlan(10, Random(1)).copy(glitchedBoss = false))
        g.update(1f / 60f)
        assertTrue("bounty shown during the entrance", g.bossBounty() > 0)
        var t = 0f
        while (t < BossBrain.INTRO_SECONDS + 0.2f) { g.update(1f / 60f); t += 1f / 60f }
        val boss = g.boss!!
        g.killEnemy(boss)
        assertTrue("kill freezes", g.fx.hitStop > 0f)
        assertTrue("kill flashes", g.fx.flashNow > 0f)
        assertTrue("kill shakes", g.fx.shakeNow > 0f)
        assertTrue("kill slows time", g.fx.slowMo > 0f)
        assertEquals(1, g.bossesDefeated)
    }
}
