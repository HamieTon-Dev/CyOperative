package com.cyberoperative.game

import com.cyberoperative.game.data.EliteModifier
import com.cyberoperative.game.data.Enemies
import com.cyberoperative.game.engine.CoopCodec
import com.cyberoperative.game.engine.CoopInput
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.Loot
import com.cyberoperative.game.engine.LootKind
import com.cyberoperative.game.engine.Phase
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.engine.RunStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PC consumables (HP charges, Packet Bombs) and floor loot (cash bags). */
class ConsumablesTest {
    private fun engine() = GameEngine(RunConfig(baseStats = RunStats(), seed = 21L, powerGovernor = false, consumables = true))

    @Test
    fun phonesKeepTheOldRules() {
        val g = GameEngine(RunConfig(baseStats = RunStats(), seed = 21L))
        assertFalse(g.config.consumables)
        assertEquals(0, g.operatives[0].hpCharges)
        val e = g.spawnEnemyAt(Enemies.MALWARE, null, g.px + 200f, g.py, telegraph = false)!!
        val before = g.eurosEarned
        g.killEnemy(e)
        assertTrue("kills pay straight away", g.eurosEarned > before)
        assertTrue(g.loot.isEmpty())
    }

    @Test
    fun hpChargeHealsAThirdAndCountsDown() {
        val g = engine()
        val o = g.operatives[0]
        assertEquals(3, o.hpCharges)
        assertFalse("no use at full HP", g.useHpCharge())
        o.hp = g.stats.maxHp * 0.2f
        assertTrue(g.useHpCharge())
        assertEquals(2, o.hpCharges)
        assertEquals(g.stats.maxHp * 0.55f, o.hp, 1f)
        assertFalse("short cooldown between uses", g.useHpCharge())
    }

    @Test
    fun packetBombBlastsAndClearsShots() {
        val g = engine()
        assertFalse("starts with none", g.usePacketBomb())
        g.operatives[0].packetBombs = 1
        val e = g.spawnEnemyAt(Enemies.MALWARE, null, g.px + 150f, g.py, telegraph = false)!!
        val hp0 = e.hp
        assertTrue(g.usePacketBomb())
        assertTrue("threat inside the blast is hurt", !e.active || e.hp < hp0)
        assertEquals(0, g.operatives[0].packetBombs)
    }

    @Test
    fun killsDropCashBagsThatArePulledInWhenTheRoomIsClear() {
        val g = engine()
        val e = g.spawnEnemyAt(Enemies.MALWARE, EliteModifier.entries.first(), g.px + 400f, g.py, telegraph = false)!!
        val before = g.eurosEarned
        g.killEnemy(e)
        assertEquals("no money until the bag is picked up", before, g.eurosEarned)
        val bag = g.loot.first { it.kind == LootKind.CASH }
        assertTrue(bag.amount > 0)
        // Clear the rest of the room; the bag (and any other pickups) stream to the operative.
        repeat(60 * 30) {
            for (x in g.enemies.items) if (x.active) g.killEnemy(x)
            g.update(1f / 60f)
            if (g.phase == Phase.UPGRADE && g.offer.isNotEmpty()) g.chooseUpgrade(0)
        }
        assertTrue("every bag collected", g.loot.none { it.kind == LootKind.CASH })
        assertTrue(g.eurosEarned >= before + bag.amount)
    }

    @Test
    fun lootAndUsesTravelOverCoop() {
        val g = engine()
        g.loot += Loot(LootKind.PACKET_BOMB, 100f, 200f)
        g.operatives[0].hpCharges = 7
        val w = CoopCodec.decodeWorld(CoopCodec.encodeWorld(g.captureWorld(1, emptyList())))!!
        assertEquals(1, w.loot.size)
        assertEquals(LootKind.PACKET_BOMB.ordinal.toFloat(), w.loot[0][0])
        assertEquals(7, w.ops[0].hpCharges)
        val c = CoopCodec.decodeInput(CoopCodec.encodeInput(CoopInput(seq = 3, hpSerial = 2, bombSerial = 5)))!!
        assertEquals(2, c.hpSerial)
        assertEquals(5, c.bombSerial)
    }
}
