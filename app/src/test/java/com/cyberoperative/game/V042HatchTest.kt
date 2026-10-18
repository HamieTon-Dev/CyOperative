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
        val want = listOf(4, 16, 40, 160, 400, 600, 840, 1280, 2160, 3200, 4400, 5760) // + GLITCHED ENCRYPTED (0.49.0, 12 h)
        for ((i, r) in Pets.RARITIES.withIndex()) assertEquals("$r", want[i], Pets.instantHatchPrice(r, Pets.hatchMs(r)))
        assertEquals(2200, Pets.instantHatchPrice(GearRarity.ENCRYPTED, 300 * min))
        assertEquals(0, Pets.instantHatchPrice(GearRarity.ENCRYPTED, 0))
    }

    @Test fun payingHatchesNowAndCostsDiamondsOnly() {
        val now = ClockNow("b", 0, 1_000_000L, null)
        val egg = Pets.rollEgg(Random(1), 7, GearRarity.EPIC)
        val p = Incubators.send(PlayerProfile(eggs = listOf(egg), petSeq = 10, diamonds = 200, euros = 0), 7, now)!!
        val out = Incubators.instantHatch(p, 0, now)!!
        assertEquals(40, out.diamonds)
        assertEquals(1, out.pets.size)
        assertNull(out.incubator.slot(0))
        assertNull(Incubators.instantHatch(Incubators.send(PlayerProfile(eggs = listOf(egg), petSeq = 10, diamonds = 159, euros = 999_999), 7, now)!!, 0, now))
    }
}

/** 0.43.0: the € instant hatch = highest operator level on the account × ◇ price × 100. */
class V043EuroHatchTest {
    @Test fun eurosUseTheHighestOperatorOnTheAccount() {
        val now = ClockNow("b", 0, 1_000_000L, null)
        val egg = Pets.rollEgg(Random(1), 7, GearRarity.EPIC)
        val xp60 = (1L..2_000_000L).first { com.cyberoperative.game.data.OpProgress.of(it * 1_000_000L).level >= 60 } * 1_000_000L
        val base = PlayerProfile(eggs = listOf(egg), petSeq = 10, operativeXp = 0,
            otherCharacters = listOf(com.cyberoperative.game.save.CharacterSave("x", "X", "operative", operativeXp = xp60)))
        assertEquals(60, Incubators.accountOpLevel(base))
        assertEquals(960_000L, Pets.instantHatchEuros(160, 60))
        val p = Incubators.send(base.copy(euros = 1_000_000L), 7, now)!!
        val out = Incubators.instantHatchWithEuros(p, 0, now)!!
        assertEquals(40_000L, out.euros)
        assertEquals(1, out.pets.size)
        assertNull(Incubators.instantHatchWithEuros(Incubators.send(base.copy(euros = 959_999L, diamonds = 9999), 7, now)!!, 0, now))
    }
}
