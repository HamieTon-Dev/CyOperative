package com.cyberoperative.game

import androidx.test.core.app.ApplicationProvider
import com.cyberoperative.game.audio.AudioManager
import com.cyberoperative.game.data.Bosses
import com.cyberoperative.game.engine.BossBrain
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.LevelPlanner
import com.cyberoperative.game.engine.Phase
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.engine.RunStats
import com.cyberoperative.game.save.SaveRepository
import com.cyberoperative.game.ui.game.GameSession
import com.cyberoperative.game.ui.game.formatFightTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

/** Victory sequence (THREAT NEUTRALIZED) and BOSS CODEX records. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BossVictoryCodexTest {
    private fun run(g: GameEngine, seconds: Float) {
        var t = 0f
        while (t < seconds) { g.update(1f / 60f); t += 1f / 60f }
    }

    private fun bossFight(boss: String): GameEngine {
        val g = GameEngine(RunConfig(baseStats = RunStats().apply { maxHp = 1e7f }, seed = 4L, freeRevives = 0))
        g.debugStartPlan(LevelPlanner.bossPlan(10, Random(1)).copy(boss = Bosses.byId(boss), glitchedBoss = false))
        run(g, BossBrain.INTRO_SECONDS + 0.5f)
        return g
    }

    @Test fun victoryHoldsTheRoomUntilItHasPlayedAndCanBeSkipped() {
        val g = bossFight("breach")
        val before = g.eurosEarned
        g.killEnemy(g.boss!!)
        val v = assertNotNull(g.victory).let { g.victory!! }
        assertEquals("card shows the bounty actually paid", g.eurosEarned - before, v.bounty)
        assertTrue("fight time measured", v.seconds > 0.3f)
        run(g, 2f)
        assertEquals("room waits for the victory sequence", Phase.COMBAT, g.phase)
        g.skipVictory()
        run(g, 2.5f)
        assertTrue("room clears after the skip (${g.phase})", g.phase != Phase.COMBAT)
    }

    @Test fun victoryPlaysThroughOnItsOwn() {
        val g = bossFight("botmaster")
        g.killEnemy(g.boss!!)
        run(g, GameEngine.VICTORY_SECONDS + 2.5f)
        assertTrue(g.phase != Phase.COMBAT)
    }

    @Test fun codexRecordsMetDefeatedAndBestTime() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val save = SaveRepository(ctx)
        save.update { it.copy(tutorialDone = true, bossRecords = emptyMap()) }
        val audio = AudioManager(ctx)

        fun fight(holdSeconds: Float): GameSession {
            val s = GameSession(save, audio)
            s.engine.debugStartPlan(LevelPlanner.bossPlan(10, Random(1)).copy(boss = Bosses.byId("white_eye"), glitchedBoss = false))
            fun frames(sec: Float) { var t = 0f; while (t < sec) { s.engine.boss?.let { b -> b.boss?.let { st -> if (st.active == null) st.rest = 99f } }; s.onFrame(1f / 60f); t += 1f / 60f } }
            frames(BossBrain.INTRO_SECONDS + holdSeconds)
            s.engine.killEnemy(s.engine.boss!!)
            frames(1f)
            return s
        }

        val first = fight(2f)
        assertTrue("first kill is flagged", first.hud.victoryFirst)
        assertFalse(first.hud.victoryBest)
        assertTrue(first.hud.victoryT >= 0f)
        first.finish()
        val r1 = save.current.bossRecords.getValue("white_eye")
        assertEquals(1, r1.met)
        assertEquals(1, r1.defeated)
        assertTrue(r1.bestSeconds > 1.5f)

        val second = fight(0.5f)
        assertFalse(second.hud.victoryFirst)
        assertTrue("a faster kill is a new record", second.hud.victoryBest)
        second.finish()
        val r2 = save.current.bossRecords.getValue("white_eye")
        assertEquals(2, r2.met)
        assertEquals(2, r2.defeated)
        assertTrue(r2.bestSeconds < r1.bestSeconds)
    }

    @Test fun fightTimeFormat() {
        assertEquals("1:05.3", formatFightTime(65.34f))
        assertEquals("0:09.0", formatFightTime(9f))
        assertEquals("—", formatFightTime(0f))
    }
}
