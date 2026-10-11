package com.cyberoperative.game

import com.cyberoperative.game.engine.AllyConfig
import com.cyberoperative.game.engine.CoopCodec
import com.cyberoperative.game.engine.CoopInput
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.GameMode
import com.cyberoperative.game.engine.Operative
import com.cyberoperative.game.engine.Phase
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.engine.RunStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Co-op shop (owner, 2026-10-11): a shop each, in together, out together. */
class CoopShopTest {
    private fun stats() = RunStats().apply { maxHp = 1e7f }

    private fun put(o: Operative, x: Float, y: Float) { o.px = x; o.py = y; o.netX = x; o.netY = y; o.netMoving = false }

    @Test fun eachPlayerShopsAloneAndBothLeaveTogether() {
        val host = GameEngine(RunConfig(baseStats = stats(), seed = 8L, mode = GameMode.ENDLESS, consumables = true, ally = AllyConfig(stats())))
        val guest = GameEngine(RunConfig(baseStats = stats(), seed = 8L, mode = GameMode.ENDLESS, consumables = true, ally = AllyConfig(stats()))).also { it.enterMirror(1) }
        var seq = 0
        fun sync() { guest.applyWorld(CoopCodec.decodeWorld(CoopCodec.encodeWorld(host.captureWorld(++seq, emptyList())))!!) }
        val (a, b) = host.operatives

        // Clear the room, then a shop is on offer (co-op gets shops now).
        var guard = 0
        while (host.phase != Phase.PORTAL && guard++ < 60 * 90) { for (e in host.enemies.items) if (e.active) host.killEnemy(e); host.update(1f / 60f) }
        assertEquals(Phase.PORTAL, host.phase)
        host.offerShop()
        assertTrue(host.shopGateOpen)
        sync()
        assertTrue("the guest sees the shop gate", guest.shopGateOpen)

        // The partner alone walks into the shop gate: both go in.
        guard = 0
        while (!host.inShop && guard++ < 400) { put(b, host.shopGateX, host.shopGateY); host.update(1f / 60f) }
        assertTrue(host.inShop)
        repeat(30) { host.update(1f / 60f) }
        assertTrue("a shop each", a.shopItems.isNotEmpty() && b.shopItems.isNotEmpty())
        sync()
        assertTrue("the guest is in the same shop room", guest.inShop)
        assertEquals(b.shopItems.map { it.def.id }, guest.shopItems.map { it.def.id })

        // The guest buys from their own shop: only theirs is sold.
        host.debugGrantEuros(100_000)
        val bought = b.shopItems[0].def.id
        val before = b.build.level(bought)
        host.applyInput(1, CoopInput(seq = 1, x = b.px, y = b.py, level = host.level, shopSerial = 1, shopIndex = 0))
        assertTrue(b.shopItems[0].sold)
        assertFalse(a.shopItems[0].sold)
        assertEquals(before + 1, b.build.level(bought))

        // One player at the exit: wait (1/2). Both: out together.
        put(b, host.arena.width / 2f, host.arena.height - 200f)
        repeat(30) { put(a, host.arena.portalX, host.arena.portalY); put(b, host.arena.width / 2f, host.arena.height - 200f); host.update(1f / 60f) }
        assertTrue("still in the shop", host.inShop && host.phase == Phase.PORTAL)
        assertEquals(1, host.shopReadyCount)
        assertEquals(2, host.shopReadyTotal)
        sync()
        assertEquals(1, guest.shopReadyCount)
        assertFalse(guest.meShopReady)
        guard = 0
        while (host.phase != Phase.TRANSITION && guard++ < 60) { put(a, host.arena.portalX, host.arena.portalY); put(b, host.arena.portalX + 30f, host.arena.portalY); host.update(1f / 60f) }
        assertEquals(Phase.TRANSITION, host.phase)
        repeat(120) { host.update(1f / 60f) }
        assertFalse("left the shop together", host.inShop)
        sync()
        assertFalse(guest.inShop)
    }
}
