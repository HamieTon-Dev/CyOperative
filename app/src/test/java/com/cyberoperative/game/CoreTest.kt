package com.cyberoperative.game

import com.cyberoperative.game.core.Rect
import com.cyberoperative.game.core.Scaling
import com.cyberoperative.game.engine.Scoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreTest {

    @Test fun scalingStartsAtOneAndIsMonotonic() {
        assertEquals(1f, Scaling.enemyHp(1), 1e-6f)
        assertEquals(1f, Scaling.enemyDamage(1), 1e-6f)
        assertEquals(1f, Scaling.enemySpeed(1), 1e-6f)
        var prevHp = 0f
        var prevDmg = 0f
        for (l in 1..2000) {
            val hp = Scaling.enemyHp(l)
            val dmg = Scaling.enemyDamage(l)
            assertTrue(hp >= prevHp && dmg >= prevDmg)
            prevHp = hp; prevDmg = dmg
        }
    }

    @Test fun scalingStaysFiniteAndManageableForEndlessLevels() {
        for (l in listOf(100, 1_000, 10_000, 100_000)) {
            assertTrue(Scaling.enemyHp(l).isFinite())
            assertTrue(Scaling.bossHp(l).isFinite())
            assertTrue(Scaling.enemySpeed(l) < 1.31f)      // speed is capped
            assertTrue(Scaling.projectileSpeed(l) < 1.36f) // never undodgeable
            assertTrue(Scaling.enemyBudget(l) <= 46)
        }
        // Early levels approachable
        assertTrue(Scaling.enemyHp(10) < 2.2f)
    }

    @Test fun bossEveryTenLevels() {
        assertFalse(Scaling.isBossLevel(1))
        assertFalse(Scaling.isBossLevel(9))
        assertTrue(Scaling.isBossLevel(10))
        assertTrue(Scaling.isBossLevel(20))
        assertTrue(Scaling.isBossLevel(1000))
        assertFalse(Scaling.isBossLevel(1001))
    }

    @Test fun rectSegmentAndCircle() {
        val r = Rect(100f, 100f, 200f, 200f)
        assertTrue(r.intersectsSegment(0f, 150f, 300f, 150f))
        assertFalse(r.intersectsSegment(0f, 50f, 300f, 50f))
        assertFalse(r.intersectsSegment(0f, 0f, 90f, 300f))
        assertTrue(r.intersectsCircle(95f, 150f, 10f))
        assertFalse(r.intersectsCircle(80f, 150f, 10f))
    }

    @Test fun scoringSpeedBonusIsCappedAndChildrenPayLess() {
        val instant = Scoring.levelClear(10, 0f, 20, 10f, 1f)
        val slow = Scoring.levelClear(10, 9999f, 20, 10f, 1f)
        assertTrue(instant <= slow * 2 + 1)
        assertTrue(Scoring.kill(10, 5, false, true) < Scoring.kill(10, 5, false, false))
        assertTrue(Scoring.kill(10, 5, true, false) > Scoring.kill(10, 5, false, false))
    }
}
