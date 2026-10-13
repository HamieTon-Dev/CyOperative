package com.cyberoperative.game

import com.cyberoperative.game.data.Environments
import com.cyberoperative.game.engine.Difficulty
import com.cyberoperative.game.save.ClassRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** INFERNO I–X (owner, 2026-10-13). */
class InfernoTest {
    @Test fun tenTiersEachHarderThanTheLast() {
        val tiers = (1..10).map { Difficulty.inferno(it)!! }
        assertEquals("INFERNO I", tiers.first().label)
        assertEquals("INFERNO X", tiers.last().label)
        var prev = Difficulty.HARD
        for (t in tiers) {
            assertTrue(t.enemyHp > prev.enemyHp && t.enemyDamage > prev.enemyDamage && t.rewardMul > prev.rewardMul && t.luck > prev.luck)
            prev = t
        }
        assertNull(Difficulty.inferno(11))
    }

    @Test fun eachTierIsUnlockedByBeatingWorldFifteenOnTheOneBefore() {
        assertEquals(1, Difficulty.HARD.unlocksInferno)
        assertEquals(0, Difficulty.MEDIUM.unlocksInferno)
        for (k in 1..9) assertEquals(k + 1, Difficulty.inferno(k)!!.unlocksInferno)
        assertEquals(0, Difficulty.INFERNO_10.unlocksInferno)
        assertEquals(listOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD), Difficulty.available(0))
        assertEquals(Difficulty.INFERNO_3, Difficulty.available(3).last())
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
