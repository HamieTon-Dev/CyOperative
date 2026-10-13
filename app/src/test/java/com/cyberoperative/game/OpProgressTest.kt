package com.cyberoperative.game

import com.cyberoperative.game.data.OpProgress
import com.cyberoperative.game.engine.Armor
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.engine.RunStats
import com.cyberoperative.game.meta.StoreManager
import com.cyberoperative.game.save.PlayerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** OP Lv 1–60, OVERCLOCK 1–300, armor, the € wipe and kill XP (owner, 2026-10-13). */
class OpProgressTest {
    @Test fun levelsFollowDiabloFour() {
        // ×10 the Diablo IV numbers (owner, 2026-10-13).
        assertEquals(25_000L, OpProgress.xpToNext(1))                // ⌊(1+9)² × 25⌋ × 10
        assertEquals(841_000L, OpProgress.xpToNext(49))              // ⌊58² × 25⌋ × 10
        assertEquals(1_620_000L, OpProgress.xpToNext(59))
        assertTrue(OpProgress.xpToNext(50) > OpProgress.xpToNext(49))
        for (l in 1 until 59) assertTrue(OpProgress.xpToNext(l + 1) >= OpProgress.xpToNext(l))
        val to60 = OpProgress.totalFor(60)
        assertEquals(29_211_000L, to60)
        assertEquals(1, OpProgress.of(0).level)
        assertEquals(2, OpProgress.of(25_000).level)
        val s = OpProgress.of(to60)
        assertEquals(60, s.level)
        assertEquals(0, s.overclock)
        assertEquals("OP Lv 60 · OC 0", s.label)
        // The XP bar's label switches to Overclock at 60.
        assertEquals("OC Lv 0", s.barLabel)
        assertEquals("OP Lv 1", OpProgress.of(0).barLabel)
    }

    @Test fun overclockIsTheLongGrind() {
        val to60 = OpProgress.totalFor(60)
        // The last level costs about 40× the whole 1–60 climb, and the late levels ramp ~3% each.
        val last = OpProgress.overclockXp(300).toDouble() / to60
        assertTrue("last OC level ≈ 40× 1–60: $last", last in 30.0..60.0)
        assertEquals(1.03, OpProgress.overclockXp(250).toDouble() / OpProgress.overclockXp(249), 0.001)
        // OC 1–4 cost about as much as the whole 1–60 climb.
        val first4 = (1..4).sumOf { OpProgress.overclockXp(it) }
        assertTrue("OC 1–4 ≈ 1–60: ${first4.toDouble() / to60}", first4.toDouble() / to60 in 0.5..1.5)
        val oc12 = OpProgress.of(to60 + (1..12).sumOf { OpProgress.overclockXp(it) })
        assertEquals(12, oc12.overclock)
        assertEquals(63, oc12.effective)
        assertEquals(12, OpProgress.boardPoints(oc12, 0))
        assertEquals(2, OpProgress.boardPoints(oc12, 10))
    }

    @Test fun chipSlotsGrowFromFiveToFifteen() {
        assertEquals(5, OpProgress.chipSlots(1))
        assertEquals(15, OpProgress.chipSlots(60))
        for (l in 1 until 60) assertTrue(OpProgress.chipSlots(l + 1) >= OpProgress.chipSlots(l))
    }

    @Test fun armorFollowsDiabloThree() {
        assertEquals(0.5f, Armor.reduction(50f * 70, 70), 1e-4f)
        assertEquals(2f / 3f, Armor.reduction(2 * 50f * 70, 70), 1e-4f)
        assertEquals(0f, Armor.reduction(0f, 70), 0f)
        assertTrue("armor thins out as monsters out-level you", Armor.reduction(500f, 60) < Armor.reduction(500f, 10))
    }

    @Test fun euroAndBoughtUpgradesAreWipedOnce() {
        val old = PlayerProfile(euros = 123_456, permanentUpgrades = mapOf("max_hp" to 12, "base_damage" to 7))
        val m = StoreManager.migrate(old)
        assertEquals(0L, m.euros)
        assertTrue(m.permanentUpgrades.isEmpty())
        // Only once: € earned afterwards stays.
        val later = StoreManager.migrate(m.copy(euros = 500))
        assertEquals(500L, later.euros)
    }

    @Test fun killsEarnXpAndEnemiesScaleToTheOperative() {
        val g = GameEngine(RunConfig(baseStats = RunStats().apply { maxHp = 1e6f }, seed = 4L, consumables = true))
        var guard = 0
        while (g.aliveCount() == 0 && guard++ < 600) g.update(1f / 60f)
        val e = g.enemies.items.first { it.active }
        g.killEnemy(e)
        assertTrue(g.opXpEarned > 0)
        val low = GameEngine(RunConfig(seed = 4L, opLevel = 1)).monsterLevel
        val high = GameEngine(RunConfig(seed = 4L, opLevel = 40)).monsterLevel
        assertEquals(1, low)
        assertEquals(40, high)
        assertTrue(RunConfig(opLevel = 40).opHpMul > RunConfig(opLevel = 1).opHpMul * 3f)
    }
}

/** 0.30.2: the ×10 level XP keeps every existing character's level. */
class XpScaleMigrationTest {
    @Test fun oldSavesKeepTheirLevel() {
        val oldXp = 506_375L // OP Lv 31 under the old curve
        val p = com.cyberoperative.game.meta.StoreManager.migrate(
            com.cyberoperative.game.save.PlayerProfile(operativeXp = oldXp,
                otherCharacters = listOf(com.cyberoperative.game.save.CharacterSave("c", "Alt", "phantom", operativeXp = oldXp)))
        )
        assertEquals(31, OpProgress.of(p.operativeXp).level)
        assertEquals(31, OpProgress.of(p.otherCharacters[0].operativeXp).level)
        assertTrue(p.xpScaled)
        // Only once.
        assertEquals(p.operativeXp, com.cyberoperative.game.meta.StoreManager.migrate(p).operativeXp)
    }
}
