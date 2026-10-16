package com.cyberoperative.game

import com.cyberoperative.game.ui.common.formatAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Short numbers (owner, 0.36.3): 398k, 3.7m, 198m; always rounded down and short. */
class FormatAmountTest {
    @Test fun ownerExamples() {
        assertEquals("398k", formatAmount(398_765))
        assertEquals("3.7m", formatAmount(3_785_245))
        assertEquals("198m", formatAmount(198_465_245))
        assertEquals("999", formatAmount(999))
        assertEquals("12.3k", formatAmount(12_345))
        assertEquals("1m", formatAmount(1_000_000))
        assertEquals("3.4b", formatAmount(3_400_000_000))
    }

    @Test fun alwaysShort() {
        var v = 1L
        repeat(19) {
            for (n in listOf(v, v * 3 + 1, v * 7 - 1, Long.MAX_VALUE)) assertTrue("$n -> ${formatAmount(n)}", formatAmount(n).length <= 6)
            if (v <= Long.MAX_VALUE / 70) v *= 10
        }
    }
}
