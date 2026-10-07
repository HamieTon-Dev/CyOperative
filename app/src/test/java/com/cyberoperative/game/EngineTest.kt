package com.cyberoperative.game

import com.cyberoperative.game.data.Bosses
import com.cyberoperative.game.data.Enemies
import com.cyberoperative.game.data.Events
import com.cyberoperative.game.data.Arenas
import com.cyberoperative.game.engine.Arena
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.LevelPlanner
import com.cyberoperative.game.engine.Phase
import com.cyberoperative.game.engine.ProjKind
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.engine.RunStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class EngineTest {

    private fun strongConfig(seed: Long = 7L, hp: Float = 1e7f, level: Int = 1): RunConfig {
        val power = com.cyberoperative.game.core.Scaling.enemyHp(level)
        val s = RunStats().apply {
            maxHp = hp; damage = 120f * power; fireRate = 4f; range = 900f; orbDamage = 60f * power
        }
        return RunConfig(baseStats = s, seed = seed, freeRevives = 0)
    }

    private fun friendlyBolts(g: GameEngine) =
        g.projectiles.items.count { it.active && it.friendly && it.kind == ProjKind.BOLT }

    @Test fun movingHoldsFireAndStoppingFires() {
        val s = RunStats().apply { range = 3000f; maxHp = 1e6f }
        val g = GameEngine(RunConfig(baseStats = s, seed = 3L))
        // Let spawns finish their telegraph.
        repeat(120) { g.update(1f / 60f) }
        // Hold the stick: no primary fire at all.
        g.setInput(0.0f, -1f)
        var fired = 0
        repeat(60) { g.update(1f / 60f); fired = maxOf(fired, friendlyBolts(g)) }
        assertEquals("primary weapon must not fire while moving", 0, fired)
        assertEquals(-1, g.targetUid)
        // Release: auto-target and fire.
        g.setInput(0f, 0f)
        repeat(40) { g.update(1f / 60f); fired = maxOf(fired, friendlyBolts(g)) }
        assertTrue("stopping must fire", fired > 0)
        assertTrue(g.targetUid >= 0)
    }

    @Test fun orbsKeepAttackingWhileMoving() {
        val g = GameEngine(RunConfig(baseStats = RunStats().apply { maxHp = 1e6f }, seed = 5L))
        repeat(60) { g.update(1f / 60f) }
        val e = g.spawnEnemyAt(Enemies.TROJAN, null, g.px + g.stats.orbRadius, g.py, telegraph = false)
        assertNotNull(e)
        e!!.speed = 0f
        val before = e.hp
        // Jitter left/right: always "moving", but staying in place.
        repeat(180) { i ->
            g.setInput(if ((i / 6) % 2 == 0) 1f else -1f, 0f)
            g.update(1f / 60f)
            assertTrue(g.moving)
        }
        assertTrue("orb should damage while the operative moves", e.hp < before || !e.active)
    }

    @Test fun obstaclesBlockMovement() {
        val a = Arena(Arenas.templates.first { it.id == "rack_rows" })
        val rack = a.obstacles.first().rect
        val moved = a.pushOut(rack.centerX, rack.centerY, 20f)
        assertTrue(moved)
        assertFalse(rack.intersectsCircle(a.out[0], a.out[1], 19.9f))
        assertFalse(a.lineOfSight(rack.left - 50f, rack.centerY, rack.right + 50f, rack.centerY))
    }

    @Test fun botClearsLevelsAndBeatsFirstBoss() {
        val g = GameEngine(strongConfig())
        val bot = Bot(g, dodge = false)
        var t = 0f
        while (t < 60f * 30f && g.level < 12 && g.phase != Phase.DEAD) { bot.step(1f / 30f); t += 1f / 30f }
        assertTrue("reached level ${g.level}", g.level >= 12)
        assertTrue(g.bossesDefeated >= 1)
        assertTrue(g.kills > 20)
        assertTrue(g.score > 0)
        assertTrue(g.eurosEarned > 0)
    }

    @Test fun everyBossRunsAllPhasesAndCanBeDefeated() {
        for ((i, boss) in Bosses.roster.withIndex()) {
            val level = 10 * (i + 1)
            val g = GameEngine(strongConfig(seed = 100L + i, level = level))
            g.debugStartPlan(LevelPlanner.bossPlan(level, Random(i)))
            assertEquals(boss.id, g.plan.boss!!.id)
            val bot = Bot(g, dodge = false)
            var t = 0f
            var sawPhase3 = false
            while (t < 240f && g.bossesDefeated == 0) {
                bot.step(1f / 30f); t += 1f / 30f
                val b = g.boss
                if (b != null && b.hp / b.maxHp < 0.25f) sawPhase3 = true
            }
            assertEquals("${boss.name} not defeated", 1, g.bossesDefeated)
            assertTrue("${boss.name} never reached phase 3", sawPhase3)
        }
    }

    @Test fun everyEventCompletes() {
        for ((i, ev) in Events.all.withIndex()) {
            val g = GameEngine(strongConfig(seed = 200L + i))
            g.debugStartPlan(LevelPlanner.eventPlan(15, Random(i), ev, null))
            val bot = Bot(g, dodge = false)
            var t = 0f
            while (t < 240f && g.phase == Phase.COMBAT) { bot.step(1f / 30f); t += 1f / 30f }
            assertTrue("${ev.name} did not complete (phase ${g.phase})", g.phase != Phase.COMBAT && g.phase != Phase.DEAD)
            assertEquals(1, g.eventsCompleted)
            assertTrue(ev.id in g.completedEventIds)
        }
    }

    @Test fun deathOffersLimitedRevive() {
        val s = RunStats().apply { maxHp = 1f }
        val g = GameEngine(RunConfig(baseStats = s, seed = 9L, freeRevives = 1))
        var t = 0f
        // Walk toward enemies until dead.
        while (g.phase != Phase.DEAD && t < 120f) {
            val e = g.nearestEnemy(g.px, g.py, 5000f)
            if (e != null) g.setInput(e.x - g.px, e.y - g.py) else g.setInput(0f, 0f)
            g.update(1f / 30f); t += 1f / 30f
        }
        assertEquals(Phase.DEAD, g.phase)
        assertTrue(g.canRevive)
        assertTrue(g.revive())
        assertTrue(g.hp > 0f)
        assertFalse(g.revive()) // not dead any more
        while (g.phase != Phase.DEAD && t < 240f) {
            val e = g.nearestEnemy(g.px, g.py, 5000f)
            if (e != null) g.setInput(e.x - g.px, e.y - g.py) else g.setInput(0f, 0f)
            g.update(1f / 30f); t += 1f / 30f
        }
        assertEquals(Phase.DEAD, g.phase)
        assertFalse("revives must be limited", g.canRevive)
    }

    @Test fun upgradeChoiceAppliesAndResumes() {
        val g = GameEngine(strongConfig().copy(startingUpgrades = 2))
        assertEquals(Phase.UPGRADE, g.phase)
        val first = g.offer.first()
        g.chooseUpgrade(0)
        assertTrue(first.def.instant || g.build.level(first.def.id) == 1)
        assertEquals(Phase.UPGRADE, g.phase)
        g.chooseUpgrade(1)
        assertEquals(Phase.COMBAT, g.phase)
    }

    @Test fun defaultOperativeSurvivesEarlyLevels() {
        // Balance sanity (§82): a simple dodging bot with no upgrades bought
        // should get through the opening levels on average.
        var total = 0
        val seeds = 6
        for (seed in 0 until seeds) {
            val g = GameEngine(RunConfig(seed = 1000L + seed, freeRevives = 0))
            val bot = Bot(g, dodge = true)
            var t = 0f
            while (g.phase != Phase.DEAD && t < 600f && g.level < 25) { bot.step(1f / 30f); t += 1f / 30f }
            total += g.level
            println("seed $seed reached level ${g.level} kills=${g.kills} t=${t.toInt()}s")
        }
        val avg = total.toFloat() / seeds
        println("average level reached: $avg")
        assertTrue("early game too hard: avg $avg", avg >= 4f)
    }
}
