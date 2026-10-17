package com.cyberoperative.game

import com.cyberoperative.game.engine.AllyConfig
import com.cyberoperative.game.engine.CoopCodec
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.GameMode
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.engine.RunStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Co-op loot ownership (owner, 0.39.0): each player's own drops are solid on their screen. */
class CoopLootOwnerTest {
    private fun stats() = RunStats().apply { maxHp = 1e7f }

    @Test fun guestSeesOwnDropsAsItsOwnAndHostDropsAsTheHosts() {
        val cfg = RunConfig(baseStats = stats(), seed = 5L, mode = GameMode.CAMPAIGN, consumables = true, powerGovernor = false, ally = AllyConfig(stats()))
        val host = GameEngine(cfg)
        val guest = GameEngine(cfg).also { it.enterMirror(1) }
        host.debugEnterRoom(com.cyberoperative.game.engine.CampaignMap.BOSS_INDEX)
        host.killEnemy(host.boss!!)
        repeat(60 * 4) { host.update(1f / 60f) }
        guest.applyWorld(CoopCodec.decodeWorld(CoopCodec.encodeWorld(host.captureWorld(1, emptyList())))!!)
        assertEquals(1, guest.primary)
        assertTrue(guest.loot.any { it.owner == 1 })
        assertTrue(guest.loot.any { it.owner == 0 })
        // Drawn full size (not the 5% pop-in) and partners' ghosts actually visible: the age travels.
        assertTrue(guest.loot.all { it.age > 0.2f })

        // The guest standing on its own potion collects it (the host runs pickups).
        val b = host.operatives[1]
        val potion = host.loot.first { it.kind == com.cyberoperative.game.engine.LootKind.HP_CHARGE && it.owner == 1 }
        val before = b.hpCharges
        b.px = potion.x; b.py = potion.y; b.netX = potion.x; b.netY = potion.y; b.netMoving = false
        repeat(30) { host.update(1f / 60f) }
        assertTrue(b.hpCharges > before)
    }
}
