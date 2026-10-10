package com.cyberoperative.game

import com.cyberoperative.game.ui.common.formatAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Currency chips always fit (owner, 2026-10-11): never more than 6 characters, never rounded up. */
class FormatAmountTest {
    @Test fun compactAndRoundedDown() {
        assertEquals("0", formatAmount(0))
        assertEquals("9,999", formatAmount(9_999))
        assertEquals("74.7K", formatAmount(74_710))
        assertEquals("99.9K", formatAmount(99_999))
        assertEquals("100K", formatAmount(100_000))
        assertEquals("747K", formatAmount(747_100))
        assertEquals("1M", formatAmount(1_000_000))
        assertEquals("1.23M", formatAmount(1_234_567))
        assertEquals("12.3M", formatAmount(12_345_678))
        assertEquals("999M", formatAmount(999_999_999))
        assertEquals("3.4B", formatAmount(3_400_000_000))
        assertEquals("1.5T", formatAmount(1_500_000_000_000))
    }

    @Test fun alwaysShort() {
        var v = 1L
        repeat(19) {
            for (n in listOf(v, v * 3 + 1, v * 7 - 1, Long.MAX_VALUE)) assertTrue("$n -> ${formatAmount(n)}", formatAmount(n).length <= 6)
            if (v <= Long.MAX_VALUE / 70) v *= 10
        }
    }
}
