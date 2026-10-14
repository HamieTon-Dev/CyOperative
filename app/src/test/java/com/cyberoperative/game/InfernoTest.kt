package com.cyberoperative.game

import com.cyberoperative.game.data.Environments
import com.cyberoperative.game.engine.Difficulty
import com.cyberoperative.game.save.ClassRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** INFERNO 1–500 (owner, 0.34.0: curve A). */
class InfernoTest {
    @Test fun fiveHundredLevelsOnCurveA() {
        val one = Difficulty.inferno(1)!!; val top = Difficulty.inferno(500)!!
        assertEquals(2.175f, one.enemyHp, 0.001f)
        assertEquals(836.6f, top.enemyHp, 1f)
        assertTrue(Difficulty.inferno(2)!!.enemyHp > one.enemyHp && top.enemyDamage > one.enemyDamage)
        assertNull(Difficulty.inferno(501))
        assertEquals(one, Difficulty.byName("INFERNO_1"))
        assertEquals(Difficulty.inferno(37), Difficulty.byName("INFERNO_37"))
        assertEquals(2, Difficulty.inferno(1)!!.unlocksInferno)
        assertEquals(0, top.unlocksInferno)
        assertEquals(Difficulty.inferno(3), Difficulty.available(3).last())
    }

    @Test fun infernoRoomsAreMostlyHellAndSometimesNormal() {
        var hell = 0
        for (level in 1..200) if (Environments.isInferno(Environments.styleFor(level, 42L, infernoTier = 4).env)) hell++
        assertTrue("mostly hell rooms: $hell / 200", hell in 160..195)
        for (level in 1..50) assertTrue(!Environments.isInferno(Environments.styleFor(level, 42L).env))
    }

    @Test fun infernoRunsCountAsHardForRecords() {
        val r = ClassRecord().with("INFERNO_5", 120)
        assertEquals(120, r.bestHard)
        assertEquals(120, r.best("INFERNO_2"))
    }
}
