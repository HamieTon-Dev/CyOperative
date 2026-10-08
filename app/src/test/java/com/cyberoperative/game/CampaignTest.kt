package com.cyberoperative.game

import com.cyberoperative.game.core.Scaling
import com.cyberoperative.game.engine.Arena
import com.cyberoperative.game.engine.ArenaGenerator
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.GameMode
import com.cyberoperative.game.engine.LevelPlanner
import com.cyberoperative.game.engine.Phase
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.engine.RunStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CampaignTest {

    @Test fun ownerThreatCounts() {
        assertEquals(8, Scaling.campaignThreats(1))
        assertEquals(14, Scaling.campaignThreats(2))
        for (l in 2..500) assertTrue(Scaling.campaignThreats(l) >= Scaling.campaignThreats(l - 1))
        assertTrue(Scaling.campaignThreats(10_000) <= 70)
        val plan = LevelPlanner.plan(2, Random(4), null, previousWasEvent = true)
        if (plan.event == null) assertEquals(14, plan.enemyCount)
    }

    @Test fun generatedRoomsAreAlwaysPlayable() {
        val rng = Random(99)
        val names = HashSet<String>()
        repeat(300) { i ->
            val t = ArenaGenerator.generate(1 + i % 60, rng)
            val a = Arena(t)
            assertTrue("spawn blocked in ${t.id}", a.isFree(a.spawnX, a.spawnY, 40f))
            assertTrue("gate blocked in ${t.id}", a.isFree(a.portalX, a.portalY, 50f))
            assertTrue("no route in ${t.id}", ArenaGenerator.reachable(a))
            assertTrue(t.obstacles.size >= 3)
            names += t.obstacles.joinToString { "${it.kind}${it.rect.left.toInt()}${it.rect.top.toInt()}" }
        }
        assertTrue("rooms should not repeat", names.size > 250)
    }

    @Test fun everyClearGivesAPowerUpThenTheGateOpens() {
        val s = RunStats().apply { maxHp = 1e7f; damage = 300f; range = 900f; fireRate = 4f }
        val g = GameEngine(RunConfig(baseStats = s, seed = 5L))
        val bot = Bot(g, dodge = false)
        var t = 0f
        var sawUpgrade = false
        while (t < 120f && g.level == 1) {
            if (g.phase == Phase.UPGRADE) sawUpgrade = true
            if (g.phase == Phase.PORTAL) assertTrue(g.portalOpen)
            bot.step(1f / 30f); t += 1f / 30f
        }
        assertTrue(sawUpgrade)
        assertEquals(2, g.level)
        assertEquals(0, g.levelKills)
    }

    @Test fun endlessKeepsSpawningAndRaisesStages() {
        val s = RunStats().apply { maxHp = 1e7f; damage = 400f; range = 900f; fireRate = 5f; orbDamage = 200f }
        val g = GameEngine(RunConfig(baseStats = s, seed = 6L, mode = GameMode.ENDLESS))
        val bot = Bot(g, dodge = false)
        var t = 0f
        var upgrades = 0
        while (t < 320f) {
            if (g.phase == Phase.UPGRADE) upgrades++
            bot.step(1f / 30f); t += 1f / 30f
        }
        assertTrue("stage ${g.level}", g.level >= 10)
        assertTrue(g.kills > 100)
        assertTrue(upgrades > 0)
        assertTrue("boss at stage 10 should have been fought", g.bossesDefeated >= 1 || g.boss != null)
        assertTrue(g.phase != Phase.CLEARED && g.phase != Phase.PORTAL)
    }
}
