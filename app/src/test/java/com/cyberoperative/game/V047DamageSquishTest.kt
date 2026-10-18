package com.cyberoperative.game

import com.cyberoperative.game.ui.common.squishDamage
import com.cyberoperative.game.ui.common.squishedIsScientific
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.47.3: Android damage numbers, the owner's examples. */
class V047DamageSquishTest {
    @Test fun ownersExamples() {
        assertEquals("999", squishDamage(999))
        assertEquals("11k", squishDamage(11_867))
        assertEquals("100k", squishDamage(100_826))
        assertEquals("7m", squishDamage(7_543_247))
        assertEquals("2b", squishDamage(2_900_000_000))
        assertEquals("9t", squishDamage(9_999_999_999_999))
        assertEquals("1.2e13", squishDamage(12_345_678_901_234))
        assertTrue(squishedIsScientific(squishDamage(Long.MAX_VALUE)))
    }
}
