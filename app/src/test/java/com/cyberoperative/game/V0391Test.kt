package com.cyberoperative.game

import com.cyberoperative.game.data.Enemies
import com.cyberoperative.game.engine.Difficulty
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.idle.IdleRunSimulator
import com.cyberoperative.game.save.PlayerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.39.1: NORMAL past level 100, the HARD boss GLITCHED curve, HARD idle runs. */
class V0391Test {
    @Test fun normalGetsHarderPastLevelHundredOnly() {
        assertEquals(1f, GameEngine.normalLate(Difficulty.MEDIUM, 100, damage = false), 0f)
        assertEquals(2.2f, GameEngine.normalLate(Difficulty.MEDIUM, 200, damage = false), 0.001f)
        assertEquals(1f, GameEngine.normalLate(Difficulty.HARD, 200, damage = false), 0f)
        fun hp(d: Difficulty, l: Int) = GameEngine(RunConfig(seed = 2L, difficulty = d)).also { it.debugJumpToLevel(l) }
            .spawnEnemyAt(Enemies.MALWARE, null, 200f, 300f, telegraph = false)!!.maxHp
        assertTrue(hp(Difficulty.HARD, 150) > hp(Difficulty.MEDIUM, 150))
    }

    @Test fun hardBossGlitchedCurve() {
        val want = listOf(1f, 1f, 1f, 0.8f, 0.6f, 0.4f, 0.2f, 0f, 0f)
        for (w in 1..9) assertEquals("world $w", want[w - 1], GameEngine.hardBossGlitched(w), 0.001f)
    }

    @Test fun idleOffersHard() {
        assertTrue(Difficulty.HARD in IdleRunSimulator.choices(PlayerProfile(characterId = "c")))
    }
}
