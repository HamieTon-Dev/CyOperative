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

    @Test
    fun upgradesDropAsChipsInsteadOfTheCardScreen() {
        val g = engine()
        val owned0 = g.build.owned().values.sum()
        var sawUpgradeScreen = false
        repeat(60 * 40) {
            for (x in g.enemies.items) if (x.active && x.boss == null) g.killEnemy(x)
            g.update(1f / 60f)
            if (g.phase == Phase.UPGRADE) sawUpgradeScreen = true
        }
        assertFalse("no card screen with chips", sawUpgradeScreen)
        val chips = g.loot.filter { it.kind == LootKind.UPGRADE }
        assertTrue("the clear's pick is on the floor as a chip", chips.isNotEmpty())
        assertEquals("chips aren't vacuumed at the clear", chips.size, g.loot.count { it.kind == LootKind.UPGRADE })
        // Walk over one: it's applied straight away and named on the HUD.
        val chip = chips.first()
        val o = g.operatives[0]
        val needsSwap = com.cyberoperative.game.data.Upgrades.byIdOrNull(chip.upgradeId!!)?.let { g.build.needsSlot(it) } == true
        o.px = chip.x; o.py = chip.y
        repeat(3) { g.update(1f / 60f) }
        if (!needsSwap) {
            assertTrue("picked up", chip !in g.loot)
            assertTrue(g.pickupName.isNotEmpty())
            assertTrue("build grew (or an instant effect)", g.build.owned().values.sum() >= owned0)
        }
    }

    @Test
    fun aWeaponChipWithFullSlotsAsksToSwapAfterTheWave() {
        val g = engine()
        // Fill every weapon slot.
        val weapons = com.cyberoperative.game.data.Upgrades.all.filter { com.cyberoperative.game.data.Upgrades.isWeapon(it) && it.evolvesFrom == null && g.build.isEligible(it) }
        for (w in weapons) { if (g.build.weapons().size >= g.build.weaponSlots) break; g.build.take(w) }
        val spare = weapons.first { g.build.level(it.id) == 0 }
        repeat(60 * 40) {
            for (x in g.enemies.items) if (x.active && x.boss == null) g.killEnemy(x)
            g.update(1f / 60f)
        }
        assertTrue(g.phase != Phase.COMBAT)
        val o = g.operatives[0]
        val chip = Loot(LootKind.UPGRADE, o.px + 200f, o.py, upgradeId = spare.id)
        g.loot += chip
        o.px = chip.x; o.py = chip.y
        repeat(2) { g.update(1f / 60f) }
        assertTrue("swap prompt", g.swapChip === chip)
        g.answerChipSwap(null)
        assertTrue("kept: thrown to the middle", chip in g.loot && kotlin.math.abs(chip.x - g.arena.width / 2f) < 700f)
        assertTrue("never on top of another pickup", g.loot.none { it !== chip && com.cyberoperative.game.core.MathUtil.dist(it.x, it.y, chip.x, chip.y) < 40f })
        // Walk back to it and swap this time.
        o.px = chip.x; o.py = chip.y
        repeat(2) { g.update(1f / 60f) }
        val old = g.build.weapons().first()
        g.answerChipSwap(old)
        assertTrue(chip !in g.loot)
        assertTrue(spare.id in g.build.weapons())
        assertFalse(old in g.build.weapons())
    }
}
