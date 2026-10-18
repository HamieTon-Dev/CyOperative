package com.cyberoperative.game

import com.cyberoperative.game.data.GearRarity
import com.cyberoperative.game.data.Pets
import com.cyberoperative.game.idle.IdleRunState
import com.cyberoperative.game.idle.IdleRuns
import com.cyberoperative.game.idle.IdleStatus
import com.cyberoperative.game.save.PlayerProfile
import com.cyberoperative.game.save.GameSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/** Owner, 0.53.4: AUTO-SELL never touches eggs, of any rarity, even set to sell everything. */
class V053AutoSellEggsTest {
    @Test fun autoSellKeepsEveryEggFromAnIdleRun() {
        val rng = Random(3)
        val eggs = GearRarity.entries.map { Pets.rollEgg(rng, it.ordinal.toLong(), it) }
        val p = PlayerProfile(settings = GameSettings(autoSellBelow = GearRarity.entries.last()))
        val out = IdleRuns.collect(p, IdleRunState(status = IdleStatus.COMPLETED, eggs = eggs))
        assertEquals(eggs.size, out.eggs.size)
        assertEquals(eggs.map { it.rarity }, out.eggs.map { it.rarity })
    }
}
