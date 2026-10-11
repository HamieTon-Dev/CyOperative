package com.cyberoperative.game

import com.cyberoperative.game.data.Bosses
import com.cyberoperative.game.engine.BossBrain
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.LevelPlanner
import com.cyberoperative.game.engine.ProjKind
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.engine.RunStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** GOOD GAME's siege cannon (owner, 2026-10-11): charge, then a tracking splash shell every 5 s. */
class SiegeCannonTest {
    private fun shells(g: GameEngine) = g.projectiles.items.count { it.active && !it.friendly && it.kind == ProjKind.BOSS && it.homing > 0f && it.splash > 0f }

    private fun fight(): GameEngine {
        val g = GameEngine(RunConfig(baseStats = RunStats().apply { maxHp = 1e7f; damage = 0.01f; orbCount = 0 }, seed = 3L, freeRevives = 0, powerGovernor = false))
        g.debugStartPlan(LevelPlanner.bossPlan(10, Random(1)).copy(boss = Bosses.byId("good_game"), glitchedBoss = false))
        var t = 0f
        while (t < BossBrain.INTRO_SECONDS + 0.1f) { g.update(1f / 60f); t += 1f / 60f }
        return g
    }

    @Test fun chargesThenFiresATrackingSplashShellEveryFiveSeconds() {
        assertTrue(Bosses.byId("good_game")!!.siegeCannon)
        val g = fight()
        var fired = 0
        var sawCharge = false
        var lastShells = 0
        var t = 0f
        val times = ArrayList<Float>()
        while (t < 16f) {
            g.update(1f / 60f); t += 1f / 60f
            if (g.bossCharge > 0.3f) sawCharge = true
            val n = shells(g)
            if (n > lastShells) { fired++; times += t }
            lastShells = n
        }
        assertTrue("charge animation state was shown", sawCharge)
        assertTrue("fired $fired shells in 16 s", fired in 3..4)
        for (i in 1 until times.size) assertEquals("one every ${BossBrain.CANNON_EVERY}s", BossBrain.CANNON_EVERY, times[i] - times[i - 1], 0.1f)
    }

    @Test fun theShellTracksAndItsBlastHurts() {
        val g = fight()
        var t = 0f
        while (shells(g) == 0 && t < 8f) { g.update(1f / 60f); t += 1f / 60f }
        val shell = g.projectiles.items.first { it.active && !it.friendly && it.splash > 0f }
        assertEquals(BossBrain.CANNON_SPLASH, shell.splash, 0f)
        val hp = g.hp
        // Let it fly (it homes on the standing operative) and burst.
        var s = 0f
        while (shell.active && s < 6f) { g.update(1f / 60f); s += 1f / 60f }
        assertTrue("the shell burst on the operative (${g.hp} < $hp)", g.hp < hp)
    }
}
