package com.cyberoperative.game

import com.cyberoperative.game.data.GearRarity
import com.cyberoperative.game.data.Pets
import com.cyberoperative.game.meta.ClockNow
import com.cyberoperative.game.meta.Incubators
import com.cyberoperative.game.save.PlayerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

/** 0.42.0: INSTANT HATCH for ◇ = 4 × ⌈rarity × minutes left ÷ 6⌉ (owner approved ×4). */
class V042HatchTest {
    private val min = 60_000L

    @Test fun pricesMatchTheApprovedChart() {
        val want = listOf(4, 16, 40, 160, 400, 600, 840, 1280, 2160, 3200, 4400)
        for ((i, r) in GearRarity.entries.withIndex()) assertEquals("$r", want[i], Pets.instantHatchPrice(r, Pets.hatchMs(r)))
        assertEquals(2200, Pets.instantHatchPrice(GearRarity.ENCRYPTED, 300 * min))
        assertEquals(0, Pets.instantHatchPrice(GearRarity.ENCRYPTED, 0))
    }

    @Test fun payingHatchesNowAndCostsDiamondsOnly() {
        val now = ClockNow("b", 0, 1_000_000L, null)
        val egg = Pets.rollEgg(Random(1), 7, GearRarity.EPIC)
        val p = Incubators.send(PlayerProfile(eggs = listOf(egg), petSeq = 10, diamonds = 200, euros = 0), 7, now)!!
        val out = Incubators.instantHatch(p, now)!!
        assertEquals(40, out.diamonds)
        assertEquals(1, out.pets.size)
        assertNull(out.incubator.current)
        assertNull(Incubators.instantHatch(Incubators.send(PlayerProfile(eggs = listOf(egg), petSeq = 10, diamonds = 159, euros = 999_999), 7, now)!!, now))
    }
}
