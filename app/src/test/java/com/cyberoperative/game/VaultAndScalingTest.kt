package com.cyberoperative.game

import com.cyberoperative.game.core.Scaling
import com.cyberoperative.game.data.Enemies
import com.cyberoperative.game.data.Events
import com.cyberoperative.game.engine.Arena
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.LevelPlanner
import com.cyberoperative.game.engine.Phase
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.engine.RunStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class VaultAndScalingTest {

    @Test fun vaultPaysHundredPerLevelFromAThousand() {
        assertEquals(1000, GameEngine.vaultPayout(1))
        assertEquals(1000, GameEngine.vaultPayout(10))
        assertEquals(7000, GameEngine.vaultPayout(70))
    }

    @Test fun clearedVaultCacheOpensShakesAndPaysOut() {
        val g = GameEngine(RunConfig(baseStats = RunStats().apply { maxHp = 1e9f }, seed = 3L, freeRevives = 0))
        g.debugStartPlan(LevelPlanner.eventPlan(70, Random(1), Events.DATA_VAULT, null))
        assertTrue(g.vaultPresent)
        val obstaclesWithVault = g.arena.obstacles.size
        // Touching it during combat does nothing.
        var guard = 0
        while (g.phase != Phase.PORTAL && guard++ < 6000) {
            for (e in g.enemies.items) if (e.active) g.killEnemy(e)
            if (g.phase == Phase.UPGRADE) g.chooseUpgrade(0)
            g.update(1f / 60f)
        }
        assertTrue(g.vaultReady)
        val before = g.eurosEarned
        val r = Arena.vaultObstacle(g.plan.arena).rect
        guard = 0
        while (g.vaultOpening <= 0f && g.vaultPresent && guard++ < 1200) {
            g.setInput(r.centerX - g.px, r.bottom + 20f - g.py)
            g.update(1f / 60f)
        }
        g.setInput(0f, 0f)
        assertTrue("cache should start opening", g.vaultOpening > 0f)
        repeat(120) { g.update(1f / 60f) }
        assertFalse(g.vaultPresent)
        assertEquals(before + 7000, g.eurosEarned)
        assertEquals(obstaclesWithVault - 1, g.arena.obstacles.size)
        // Save & exit, then continue: the cache stays cracked (no second payout).
        val snap = g.snapshot()!!
        val g2 = GameEngine(g.config, com.cyberoperative.game.engine.RunSnapshot.decodeOrNull(snap.encode()))
        assertFalse("cracked cache stays cracked after loading", g2.vaultPresent)
        assertEquals(obstaclesWithVault - 1, g2.arena.obstacles.size)
    }

    @Test fun opLevelIsTheOnlyDifficultyExtra() {
        // Owner, 2026-10-09: base curve only (no late ramp, no build-size scaling)…
        assertEquals(Scaling.enemyHp(60), 1f + 0.11f * 59 + 0.0009f * 59 * 59, 0.001f)
        assertTrue(Scaling.enemyHp(5000).isFinite())
        fun hp(op: Int, level: Int, d: com.cyberoperative.game.engine.Difficulty = com.cyberoperative.game.engine.Difficulty.MEDIUM): Float {
            val g = GameEngine(RunConfig(seed = 2L, opLevel = op, difficulty = d))
            if (level > 1) g.debugJumpToLevel(level)
            return g.spawnEnemyAt(Enemies.MALWARE, null, 200f, 300f, telegraph = false)!!.maxHp
        }
        // …and it scales with OP level from level 1: +2% HP per OP level, half on EASY.
        assertEquals(1.5f, hp(26, 1) / hp(1, 1), 0.01f)
        assertEquals(1.5f, hp(26, 60) / hp(1, 60), 0.01f)
        val easy = com.cyberoperative.game.engine.Difficulty.EASY
        assertEquals(1.25f, hp(26, 1, easy) / hp(1, 1, easy), 0.01f)
        assertEquals(3f, RunConfig(opLevel = 101).opHpMul, 0.001f)
        assertEquals(5.89f, RunConfig(opLevel = 500).opHpMul, 0.01f)
    }

    @Test fun bigBuildsNoLongerToughenThreats() {
        fun hpWith(weapons: List<String>): Float {
            val g = GameEngine(RunConfig(seed = 2L))
            for (id in weapons) g.build.take(com.cyberoperative.game.data.Upgrades.byId(id))
            g.debugJumpToLevel(80)
            return g.spawnEnemyAt(Enemies.MALWARE, null, 200f, 300f, telegraph = false)!!.maxHp
        }
        assertEquals(hpWith(emptyList()), hpWith(com.cyberoperative.game.data.Weapons.all.take(7).map { it.id }), 0.01f)
        // Deep levels still get busier and more elite.
        assertEquals(70, Scaling.campaignThreats(200))
        assertTrue(Scaling.campaignThreats(80) > Scaling.campaignThreats(60))
        assertTrue(Scaling.eliteChance(80) > Scaling.eliteChance(50))
    }

    @Test fun hardStaysHarderThanNormalAndEarlyLevelsAreUntouched() {
        fun hp(d: com.cyberoperative.game.engine.Difficulty, level: Int): Float {
            val g = GameEngine(RunConfig(seed = 2L, opLevel = 60, difficulty = d))
            if (level > 1) g.debugJumpToLevel(level)
            return g.spawnEnemyAt(Enemies.MALWARE, null, 200f, 300f, telegraph = false)!!.maxHp
        }

        for (l in listOf(1, 10, 30, 45, 60, 90, 150)) {
            assertTrue("level $l", hp(com.cyberoperative.game.engine.Difficulty.HARD, l) > hp(com.cyberoperative.game.engine.Difficulty.MEDIUM, l))
            assertTrue("level $l", hp(com.cyberoperative.game.engine.Difficulty.MEDIUM, l) > hp(com.cyberoperative.game.engine.Difficulty.EASY, l))
        }
        // HARD vs NORMAL: ×1.45 before the pressure kicks in, and still ahead deep into a run
        // (Hard takes 70% of Normal's post-10 HP bump on top of its own ×1.45; owner, 2026-10-11).
        assertEquals(1.45f, hp(com.cyberoperative.game.engine.Difficulty.HARD, 4) / hp(com.cyberoperative.game.engine.Difficulty.MEDIUM, 4), 0.01f)
        for (l in listOf(200, 400, 999)) assertTrue("level $l", hp(com.cyberoperative.game.engine.Difficulty.HARD, l) > hp(com.cyberoperative.game.engine.Difficulty.MEDIUM, l))
    }

    @Test fun normalGetsTougherPastLevelTenAndEasyIsUntouched() {
        fun hp(d: com.cyberoperative.game.engine.Difficulty, level: Int, elite: com.cyberoperative.game.data.EliteModifier? = null): Float {
            val g = GameEngine(RunConfig(seed = 2L, opLevel = 1, difficulty = d))
            if (level > 1) g.debugJumpToLevel(level)
            return g.spawnEnemyAt(Enemies.MALWARE, elite, 200f, 300f, telegraph = false)!!.maxHp
        }
        val base52 = com.cyberoperative.game.core.Scaling.enemyHp(52) * Enemies.MALWARE.baseHp
        // Normal at L52: the old curve × hpPressure (~2.44).
        assertEquals(com.cyberoperative.game.core.Scaling.hpPressure(52), hp(com.cyberoperative.game.engine.Difficulty.MEDIUM, 52) / base52, 0.05f)
        assertTrue(com.cyberoperative.game.core.Scaling.hpPressure(52) > 2.3f)
        // Easy is exactly the old curve ×0.7.
        assertEquals(0.7f, hp(com.cyberoperative.game.engine.Difficulty.EASY, 52) / base52, 0.01f)
        // Double the threats around level 52.
        assertEquals(2f, com.cyberoperative.game.core.Scaling.countPressure(52), 0.001f)
        // Elites: double HP on Normal (vs Easy's elite multiplier), and armoured.
        val e = com.cyberoperative.game.data.EliteModifier.CORRUPTED
        val ratioNormal = hp(com.cyberoperative.game.engine.Difficulty.MEDIUM, 30, e) / hp(com.cyberoperative.game.engine.Difficulty.MEDIUM, 30)
        val ratioEasy = hp(com.cyberoperative.game.engine.Difficulty.EASY, 30, e) / hp(com.cyberoperative.game.engine.Difficulty.EASY, 30)
        assertEquals(2f, ratioNormal / ratioEasy, 0.01f)
    }

    @Test fun bossesHaveDoubleHpOnNormalAndHard() {
        fun bossHp(d: com.cyberoperative.game.engine.Difficulty): Float {
            val g = GameEngine(RunConfig(seed = 2L, opLevel = 1, difficulty = d))
            g.debugStartPlan(LevelPlanner.bossPlan(10, Random(1)).copy(boss = com.cyberoperative.game.data.Bosses.byId("breach"), glitchedBoss = false))
            g.update(1f / 60f)
            return g.boss!!.maxHp
        }
        val easy = bossHp(com.cyberoperative.game.engine.Difficulty.EASY) / 0.7f
        assertTrue("normal boss ≥ 2× the old HP", bossHp(com.cyberoperative.game.engine.Difficulty.MEDIUM) >= easy * 2f)
        assertTrue(bossHp(com.cyberoperative.game.engine.Difficulty.HARD) > bossHp(com.cyberoperative.game.engine.Difficulty.MEDIUM))
    }

    @Test fun level52RoomsHaveTwiceTheThreats() {
        val g = GameEngine(RunConfig(seed = 4L, difficulty = com.cyberoperative.game.engine.Difficulty.MEDIUM))
        val easy = GameEngine(RunConfig(seed = 4L, difficulty = com.cyberoperative.game.engine.Difficulty.EASY))
        val p = LevelPlanner.plan(52, Random(9), null, true, countMul = g.pressureCount(52))
        val pe = LevelPlanner.plan(52, Random(9), null, true, countMul = easy.pressureCount(52))
        assertEquals(p.waves.sumOf { it.size }.toFloat(), 2f * pe.waves.sumOf { it.size }, 2f)
    }

    @Test fun hardLocksFourWeaponSlotsWhateverIsUnlocked() {
        fun slots(d: com.cyberoperative.game.engine.Difficulty, unlocked: Int) =
            GameEngine(RunConfig(seed = 1L, difficulty = d, weaponSlots = unlocked)).build.weaponSlots
        // Owner's examples: 7 base → 3; 2 extra unlocked (9) → 5; all five unlocked (12) → 8.
        assertEquals(3, slots(com.cyberoperative.game.engine.Difficulty.HARD, 7))
        assertEquals(5, slots(com.cyberoperative.game.engine.Difficulty.HARD, 9))
        assertEquals(8, slots(com.cyberoperative.game.engine.Difficulty.HARD, 12))
        assertEquals(7, slots(com.cyberoperative.game.engine.Difficulty.MEDIUM, 7))
        assertEquals(7, slots(com.cyberoperative.game.engine.Difficulty.EASY, 7))
        // With three weapons on Hard, a fourth needs a swap.
        val g = GameEngine(RunConfig(seed = 1L, difficulty = com.cyberoperative.game.engine.Difficulty.HARD, weaponSlots = 7))
        val weapons = com.cyberoperative.game.data.Upgrades.all.filter { com.cyberoperative.game.data.Upgrades.isWeapon(it) && !it.instant && it.evolvesFrom == null }
        for (w in weapons) { if (g.build.weapons().size >= 3) break; if (!g.build.needsSlot(w)) g.build.take(w) }
        assertEquals(3, g.build.weapons().size)
        val next = weapons.first { g.build.level(it.id) == 0 }
        assertTrue("a 4th weapon needs a free slot on Hard", g.build.needsSlot(next))
    }
}
