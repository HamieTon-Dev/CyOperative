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
        walk(g, 0f, g.shopGateY) { g.inShop }
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
}
