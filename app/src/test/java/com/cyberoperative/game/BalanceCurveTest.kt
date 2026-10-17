package com.cyberoperative.game

import com.cyberoperative.game.core.Scaling
import com.cyberoperative.game.data.Enemies
import com.cyberoperative.game.data.Events
import com.cyberoperative.game.data.UpgradeCategory
import com.cyberoperative.game.data.Upgrades
import com.cyberoperative.game.data.Weapons
import com.cyberoperative.game.engine.Difficulty
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.LevelPlanner
import com.cyberoperative.game.engine.Phase
import com.cyberoperative.game.engine.RunBuild
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.engine.RunStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Balance pass (owner, 2026-10-11: "at level 180 I'm doing 26,000+ damage… maybe we are
 * upgrading too often?"). Mastery used to compound per upgrade into ×10^6; it is now
 * pooled, and picks are a little rarer.
 */
class BalanceCurveTest {
    private fun hit(s: RunStats) = s.damage * (1f + s.critChance * (s.critMul - 1f))

    /** Runs the campaign's pick schedule with a damage-greedy player; returns hits to kill a Malware Crawler per decade. */
    private fun greedyRun(seed: Int): Map<Int, Float> {
        val rng = Random(seed)
        val b = RunBuild(RunStats())
        var xp = 0f
        var runLevel = 1
        fun pick() {
            val offer = b.rollOffer(rng, 3, 0.3f)
            val best = offer.filter { !it.def.instant }.maxByOrNull { hit(b.preview(it.def)) } ?: return
            b.take(best.def)
        }
        val malware = Enemies.byId("malware").baseHp
        val out = HashMap<Int, Float>()
        for (l in 1..180) {
            if (Scaling.isBossLevel(l)) repeat(GameEngine.bossPicks(l)) { pick() } else {
                xp += Scaling.campaignThreats(l) * Scaling.countPressure(l) * 3f * b.stats.xpMul
                while (xp >= Scaling.xpToNext(runLevel)) { xp -= Scaling.xpToNext(runLevel); runLevel++; pick() }
                pick()
                if (rng.nextFloat() < minOf(GameEngine.BONUS_PICK_CAP, (l - 1) * 0.01f)) pick()
                if (l >= 25 && rng.nextFloat() < minOf(GameEngine.DEEP_PICK_CAP, (l - 24) * 0.005f)) pick()
                if (rng.nextFloat() < Events.EVENT_CHANCE) pick()
            }
            if (l % 10 == 0) {
                val hp = malware * Scaling.enemyHp(l) * (1f + (Scaling.hpPressure(l) - 1f) * Difficulty.MEDIUM.hpPressureShare)
                out[l] = hp / hit(b.stats)
            }
        }
        return out
    }

    @Test fun damageKeepsPaceWithThreatsInsteadOfRunningAway() {
        for (seed in 1..3) {
            val h = greedyRun(seed)
            // Before the pass: 0.10 hits at level 100 and 0.004 at level 180 (one-shotting everything).
            // 0.39.0's GLITCHED … ENCRYPTED chips are meant to be near game-breaking (owner), so a
            // damage-greedy NORMAL build may one-shot basics late; it must not run away past that.
            for (l in listOf(30, 50, 100, 150, 180)) {
                assertTrue("seed $seed L$l: ${h[l]} hits to kill a basic threat", h.getValue(l) in 0.4f..8f)
            }
        }
    }

    @Test fun masteryIsPooledNotCompounded() {
        val b = RunBuild(RunStats())
        val stat = Upgrades.all.filter { it.category == UpgradeCategory.STAT && !it.instant && it.evolvesFrom == null }
        var pool = 0
        for (u in stat) {
            repeat(u.maxLevel + 10) { b.take(u) }
            pool += 10
        }
        val core = RunBuild(RunStats())
        for (u in stat) repeat(u.maxLevel) { core.take(u) }
        val ratio = b.stats.damage / core.stats.damage
        assertEquals(Upgrades.masteryCurve(pool.toFloat()), ratio, 0.01f)
        // The old per-upgrade compounding was ×1.5 per upgrade, multiplied together.
        val old = Math.pow(1.5, stat.size.toDouble()).toFloat()
        assertTrue("ratio $ratio vs old $old", ratio < old / 3f)
    }

    @Test fun fewerPicksAndTaperedWeaponLevels() {
        assertEquals(2, GameEngine.bossPicks(10))
        assertEquals(6, GameEngine.bossPicks(180))
        val w = Weapons.upgrades.first().let { u -> Weapons.all.first { it.id == u.id } }
        val per10 = w.damageAt(10) - w.damageAt(9)
        val per20 = w.damageAt(20) - w.damageAt(19)
        assertTrue(per20 < per10)
        assertEquals(w.damage * (1f + 0.35f * 9 + 0.15f * 10), w.damageAt(20), 0.001f)
    }

    @Test fun packetFloodMoreThanDoublesAndNothingIsDropped() {
        // Old flood: ×2.6 budget, 32 on screen and anything over the cap was thrown away.
        val oldL10 = (Scaling.campaignThreats(10) * 2.6f).toInt()
        val plan = LevelPlanner.eventPlan(10, Random(1), Events.PACKET_FLOOD, null)
        assertTrue("flood L10 ${plan.enemyCount} vs old $oldL10", plan.enemyCount >= oldL10 * 2)

        val g = GameEngine(RunConfig(baseStats = RunStats().apply { maxHp = 1e9f; damage = 1e6f; fireRate = 8f }, seed = 3L, freeRevives = 0, difficulty = Difficulty.MEDIUM))
        val p52 = LevelPlanner.eventPlan(52, Random(2), Events.PACKET_FLOOD, null, g.pressureCount(52))
        g.debugStartPlan(p52)
        val before = g.levelSpawned
        var peak = 0
        var t = 0f
        while (t < 600f && g.phase == Phase.COMBAT) {
            for (e in g.enemies.items) if (e.active && e.boss == null && g.rng.nextFloat() < 0.03f) g.killEnemy(e)
            g.update(1f / 60f); t += 1f / 60f
            peak = maxOf(peak, g.aliveCount())
        }
        assertTrue("flood cleared (${g.phase})", g.phase != Phase.COMBAT)
        assertTrue("every planned threat spawned: ${g.levelSpawned - before} of ${p52.enemyCount}", g.levelSpawned - before >= p52.enemyCount)
        assertTrue("room fills past the normal cap ($peak)", peak > Scaling.MAX_ALIVE)
        assertTrue(peak <= Scaling.MAX_ALIVE_SWARM + 2)
    }

    private fun bossAt(level: Int, stats: RunStats, d: Difficulty = Difficulty.MEDIUM): Pair<GameEngine, Float> {
        val g = GameEngine(RunConfig(baseStats = stats, seed = 9L, freeRevives = 0, difficulty = d))
        g.debugStartPlan(LevelPlanner.bossPlan(level, Random(1)).copy(boss = com.cyberoperative.game.data.Bosses.byId("breach"), glitchedBoss = false))
        var t = 0f
        while (g.boss == null && t < 5f) { g.update(1f / 60f); t += 1f / 60f }
        return g to g.boss!!.maxHp
    }

    private fun godStats() = RunStats().apply { maxHp = 1e9f; damage = 1_000_000f; fireRate = 8f; critChance = 0.8f; critMul = 4f }

    @Test fun governorHoldsBossFightLengthFromLevel120To1200() {
        val s = godStats()
        val dps = com.cyberoperative.game.engine.PowerGovernor.potentialBossDps(RunBuild(s).stats)
        val (_, hp120) = bossAt(120, s)
        val (_, hp1200) = bossAt(1200, s)
        val sec120 = hp120 / dps
        val sec1200 = hp1200 / dps
        assertTrue("L120 boss lasts $sec120 s of potential output", sec120 >= 59f)
        assertTrue("L1200 boss lasts $sec1200 s", sec1200 >= 59f && sec1200 < sec120 * 3f)
        // Hard asks for longer.
        val (_, hpHard) = bossAt(120, s, Difficulty.HARD)
        assertTrue(hpHard / dps >= 79f)
    }

    @Test fun governorLiftsThreatsAndTheirDamageButNotOnEasy() {
        val s = godStats()
        val g = GameEngine(RunConfig(baseStats = s, seed = 9L, freeRevives = 0, difficulty = Difficulty.MEDIUM))
        g.debugStartPlan(LevelPlanner.plan(151, Random(1), null, true))
        val dps = com.cyberoperative.game.engine.PowerGovernor.potentialDps(RunBuild(s).stats)
        val malware = g.spawnEnemyAt(Enemies.byId("malware"), null, 400f, 400f, telegraph = false)!!
        assertTrue("malware ${malware.maxHp} vs ${dps * 0.3f}", malware.maxHp >= dps * 0.29f)
        assertTrue("governed threats hit harder (${g.adaptiveDamage})", g.adaptiveDamage > 10f)

        val easy = GameEngine(RunConfig(baseStats = s, seed = 9L, freeRevives = 0, difficulty = Difficulty.EASY))
        easy.debugStartPlan(LevelPlanner.plan(151, Random(1), null, true))
        assertEquals(1f, easy.adaptiveHp, 0f)
        assertEquals(1f, easy.adaptiveDamage, 0f)
    }

    @Test fun aFairBuildIsNotGoverned() {
        val g = GameEngine(RunConfig(baseStats = RunStats(), seed = 9L, freeRevives = 0, difficulty = Difficulty.MEDIUM))
        g.debugStartPlan(LevelPlanner.plan(11, Random(1), null, true))
        assertEquals(1f, g.adaptiveHp, 0f)
        assertEquals(1f, g.adaptiveDamage, 0f)
    }

    @Test fun nothingOneShotsAndThreatDamageIsHalved() {
        for ((d, cap) in listOf(Difficulty.MEDIUM to 0.25f, Difficulty.HARD to 0.34f, Difficulty.EASY to 0.25f)) {
            val s = RunStats().apply { maxHp = 1000f }
            val g = GameEngine(RunConfig(baseStats = s, seed = 9L, freeRevives = 0, difficulty = d, powerGovernor = true))
            g.debugStartPlan(LevelPlanner.plan(211, Random(1), null, true))
            val before = g.hp
            g.damagePlayer(1e9f, g.px + 10f, g.py, ignoreInvuln = true)
            assertEquals("$d: one huge hit takes at most ${cap * 100}%", before - cap * 1000f, g.hp, 1f)
            assertTrue(g.phase != Phase.DEAD)
        }
        val g = GameEngine(RunConfig(baseStats = RunStats().apply { maxHp = 1000f }, seed = 9L, freeRevives = 0))
        g.debugStartPlan(LevelPlanner.plan(11, Random(1), null, true))
        val before = g.hp
        g.damagePlayer(100f, g.px + 10f, g.py, ignoreInvuln = true)
        assertEquals("Normal halves threat damage", before - 50f, g.hp, 0.5f)
    }
}
