package com.cyberoperative.game

import com.cyberoperative.game.data.GearRarity
import com.cyberoperative.game.data.Pets
import com.cyberoperative.game.engine.Difficulty
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.RunConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.53.0: the in-run shop's egg. */
class V053ShopEggTest {
    @Test fun normalShopsSometimesHaveAnEggAtTwiceItsSellValue() {
        val g = GameEngine(RunConfig(seed = 4L))
        val rolls = (0 until 1000).map { g.debugRollShopEgg() }
        val eggs = rolls.filterNotNull()
        assertTrue("${eggs.size}", eggs.size in 230..370)          // ~30 %
        assertTrue(eggs.map { it.egg.rarity }.toSet().size >= 4)  // random rarities
        val lvl = g.operatives[0].gearLevel
        assertTrue(eggs.all { it.price == 2 * Pets.value(it.egg.rarity, lvl) && !it.sold })
    }

    @Test fun infernoShopsAlwaysHaveAGlitchedOrBetterEggHigherOnesRarer() {
        val g = GameEngine(RunConfig(seed = 4L, difficulty = Difficulty.inferno(3)!!))
        val eggs = (0 until 2000).map { g.debugRollShopEgg()!! }
        assertTrue(eggs.all { it.egg.rarity >= GearRarity.GLITCHED })
        val n = eggs.groupingBy { it.egg.rarity }.eachCount()
        assertTrue(n.getValue(GearRarity.GLITCHED) > n.getValue(GearRarity.ABYSSAL))
        assertTrue((n[GearRarity.ABYSSAL] ?: 0) > (n[GearRarity.GLITCHED_ENCRYPTED] ?: 0))
        assertEquals(GearRarity.GLITCHED, n.maxBy { it.value }.key)
    }
}
