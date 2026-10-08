package com.cyberoperative.game

import com.cyberoperative.game.data.Rarity
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.LevelKind
import com.cyberoperative.game.engine.Phase
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.engine.RunStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner, 2026-10-08: 1-in-20 upgrade shop behind a side gate, GOLDEN+ mods from €1,000. */
class ShopTest {

    private fun walk(g: GameEngine, tx: Float, ty: Float, until: () -> Boolean) {
        var guard = 0
        while (!until() && guard++ < 60 * 30) {
            val dx = tx - g.px
            val dy = ty - g.py
            g.setInput(dx, dy)
            g.update(1f / 60f)
        }
        g.setInput(0f, 0f)
    }

    @Test fun sideGateShopSellsGoldenModsForRunEuros() {
        assertEquals(0.05f, GameEngine.SHOP_CHANCE, 1e-6f)
        val g = GameEngine(RunConfig(baseStats = RunStats().apply { maxHp = 1e9f; damage = 1e5f; range = 3000f }, seed = 5L, freeRevives = 0))
        // Clear level 1 with the bot, pick the upgrades, then force the shop roll.
        val bot = Bot(g, dodge = false)
        var t = 0f
        while (g.phase != Phase.PORTAL && t < 120f) { bot.step(1f / 30f); t += 1f / 30f }
        assertEquals(Phase.PORTAL, g.phase)
        val serial = g.shopMessageSerial
        g.offerShop()
        assertTrue(g.shopGateOpen)
        assertEquals(serial + 1, g.shopMessageSerial)

        // Through the left side gate.
        walk(g, g.shopGateX, g.shopGateY) { g.inShop }
        repeat(60) { g.update(1f / 60f) }
        assertTrue(g.inShop)
        assertEquals(LevelKind.SHOP, g.plan.kind)
        assertEquals(1, g.level)
        assertTrue(g.shopItems.isNotEmpty())
        assertTrue(g.shopItems.all { it.def.rarity.ordinal >= Rarity.LEGENDARY.ordinal && it.price >= 1000 })
        assertTrue(g.aliveCount() == 0)
        assertNotNull(g.saveBlockReason)

        // Too poor, then rich enough.
        val item = g.shopItems.first()
        g.debugGrantEuros(-g.eurosEarned)
        assertFalse(g.buyShopItem(0))
        g.debugGrantEuros(item.price + 5)
        assertTrue(g.buyShopItem(0))
        assertEquals(5, g.eurosEarned)
        assertTrue(g.shopItems[0].sold)
        assertEquals(1, g.build.level(item.def.id).coerceAtMost(1))
        assertFalse(g.buyShopItem(0))

        // Counter detection, then out through the top gate to level 2.
        walk(g, g.arena.width / 2f, 330f) { g.atShopCounter }
        assertTrue(g.atShopCounter)
        walk(g, 60f, 200f) { g.py < 220f }
        walk(g, g.arena.portalX, 0f) { g.level == 2 }
        assertEquals(2, g.level)
        assertFalse(g.inShop)
        assertFalse(g.shopGateOpen)
    }

    @Test fun shopPricesRiseWithRarityAndDepth() {
        assertTrue(GameEngine.shopPrice(Rarity.LEGENDARY, 1) >= 1000)
        assertTrue(GameEngine.shopPrice(Rarity.TITANIUM, 1) > GameEngine.shopPrice(Rarity.LEGENDARY, 1))
        assertTrue(GameEngine.shopPrice(Rarity.LEGENDARY, 40) > GameEngine.shopPrice(Rarity.LEGENDARY, 1))
    }

    /** Owner, 2026-10-08: the side gate must never be blocked by a wall or block. */
    @Test fun shopGateIsNeverBlocked() {
        var offered = 0
        for (seed in 1L..150L) {
            val g = GameEngine(RunConfig(baseStats = RunStats().apply { maxHp = 1e9f }, seed = seed, freeRevives = 0))
            g.debugJumpToLevel(1 + (seed % 60).toInt())
            var guard = 0
            while (g.phase != Phase.PORTAL && guard++ < 6000) {
                for (e in g.enemies.items) if (e.active) g.killEnemy(e)
                if (g.phase == Phase.UPGRADE) g.chooseUpgrade(0)
                g.update(1f / 60f)
            }
            if (g.phase != Phase.PORTAL || g.inShop) continue
            g.offerShop()
            if (!g.shopGateOpen) continue
            offered++
            val a = g.arena
            // Nothing stands in the doorway or right in front of it.
            var d = -GameEngine.SHOP_GATE_HALF
            while (d <= GameEngine.SHOP_GATE_HALF) {
                for (depth in listOf(20f, 40f, 60f, 80f)) {
                    val x = if (g.shopGateRight) a.width - depth else depth
                    assertTrue("seed $seed: gate blocked at depth $depth", a.obstacleAt(x, g.shopGateY + d, 2f) < 0)
                }
                d += 10f
            }
            // And it can actually be walked into: steer with the flow field until through.
            var steps = 0
            while (!g.inShop && steps++ < 60 * 25) {
                val tx = g.shopGateX
                val ty = g.shopGateY
                if (a.lineOfSight(g.px, g.py, tx, ty, g.playerRadius)) g.setInput(tx - g.px, ty - g.py)
                else {
                    g.path.update(a, tx.coerceIn(30f, a.width - 30f), ty, 1f)
                    if (g.path.steer(g.px, g.py, g.playerRadius)) g.setInput(g.path.dir[0], g.path.dir[1]) else g.setInput(tx - g.px, ty - g.py)
                }
                g.update(1f / 60f)
            }
            assertTrue("seed $seed: could not walk into the shop gate", g.inShop)
        }
        assertTrue("only $offered rooms offered a shop", offered >= 120)
    }
}
