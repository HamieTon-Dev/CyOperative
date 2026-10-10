package com.cyberoperative.game

import com.cyberoperative.game.data.BossRole
import com.cyberoperative.game.data.Bosses
import com.cyberoperative.game.engine.BossBrain
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.LevelPlanner
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.engine.RunStats
import com.cyberoperative.game.engine.ScreenFx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Boss Expansion Vol. 1, Stage A: metadata, dossier data, screen effects, placement. */
class BossExpansionTest {

    @Test fun everyBossHasDossierData() {
        for (b in Bosses.roster) {
            assertTrue("${b.id} tier", b.tier in 1..4)
            assertTrue("${b.id} abilities", b.abilityNames().isNotEmpty())
            assertTrue("${b.id} abilities ≤ 4", b.abilityNames().size <= 4)
            assertTrue("${b.id} bounty", b.euros > 0)
            assertTrue(b.armor >= 0f)
        }
        assertEquals(BossRole.TANK, Bosses.byId("good_game")?.role ?: BossRole.TANK)
        assertEquals(Bosses.classics + Bosses.expansion, Bosses.roster)
    }

    @Test fun classicsKeepTheirLevels() {
        for ((i, b) in Bosses.classics.withIndex()) assertEquals(b, Bosses.forLevel(10 * (i + 1)))
    }

    @Test fun rareBossNeverBeforeItsLevel() {
        for (seed in 0 until 200) assertNull(LevelPlanner.rollRareBoss(LevelPlanner.RARE_BOSS_FROM - 10, Random(seed)))
    }

    @Test fun screenFxTiming() {
        val fx = ScreenFx()
        fx.hitStop(0.1f)
        assertEquals(0f, fx.tick(0.05f), 0f)
        assertEquals(0f, fx.tick(0.05f), 0f)
        assertEquals(0.05f, fx.tick(0.05f), 1e-6f)
        fx.slowMotion(1f, 0.3f)
        assertEquals(0.03f, fx.tick(0.1f), 1e-5f)
        fx.shake(10f, 1f)
        assertTrue(fx.shakeNow > 9f)
        fx.shake(2f, 1f) // weaker shake doesn't override a stronger one
        assertTrue(fx.shakeNow > 9f)
        fx.tick(1f)
        assertEquals(0f, fx.shakeNow, 0f)
    }

    @Test fun bossDossierAndKillBeat() {
        val s = RunStats().apply { maxHp = 1e7f; damage = 1e5f; fireRate = 4f; range = 2000f }
        val g = GameEngine(RunConfig(baseStats = s, seed = 5L, freeRevives = 0))
        g.debugStartPlan(LevelPlanner.bossPlan(10, Random(1)).copy(glitchedBoss = false))
        g.update(1f / 60f)
        assertTrue("bounty shown during the entrance", g.bossBounty() > 0)
        var t = 0f
        while (t < BossBrain.INTRO_SECONDS + 0.2f) { g.update(1f / 60f); t += 1f / 60f }
        val boss = g.boss!!
        g.killEnemy(boss)
        assertTrue("kill freezes", g.fx.hitStop > 0f)
        assertTrue("kill flashes", g.fx.flashNow > 0f)
        assertTrue("kill shakes", g.fx.shakeNow > 0f)
        assertTrue("kill slows time", g.fx.slowMo > 0f)
        assertEquals(1, g.bossesDefeated)
    }
}

/** Vault Sentinel's systems: barrier cubes, lockdown, sweeping laser, mortar, co-op sync. */
class VaultSentinelTest {

    private fun fight(): GameEngine {
        val s = RunStats().apply { maxHp = 1e7f; damage = 1f; fireRate = 0.01f; range = 10f }
        val g = GameEngine(RunConfig(baseStats = s, seed = 9L, freeRevives = 0))
        g.debugStartPlan(LevelPlanner.bossPlan(130, Random(1)).copy(boss = com.cyberoperative.game.data.BossExpansion.VAULT_SENTINEL, glitchedBoss = false))
        var t = 0f
        while (t < BossBrain.INTRO_SECONDS + 0.1f) { g.update(1f / 60f); t += 1f / 60f }
        return g
    }

    /** Runs [seconds] with the boss held on its forced pattern only. */
    private fun run(g: GameEngine, seconds: Float, input: Pair<Float, Float> = 0f to 0f) {
        var t = 0f
        while (t < seconds) {
            g.boss?.boss?.let { if (it.active == null) it.rest = 99f }
            g.setInput(input.first, input.second)
            g.update(1f / 60f); t += 1f / 60f
        }
    }

    @Test fun coverRisesThenSinks() {
        val g = fight()
        val before = g.arena.obstacles.size
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.CoverDeploy(4, 2f))
        assertTrue("cubes queued", g.barriers.isNotEmpty())
        assertEquals("not solid while telegraphed", before, g.arena.obstacles.size)
        run(g, 1.0f)
        assertTrue("cubes are walls once risen", g.arena.obstacles.size > before)
        assertTrue(g.arena.obstacles.any { it.kind == com.cyberoperative.game.data.ObstacleKind.BARRIER_CUBE })
        run(g, 2.5f)
        assertEquals("cubes gone after their life", before, g.arena.obstacles.size)
        assertTrue(g.barriers.isEmpty())
    }

    @Test fun lockdownLeavesAWayOut() {
        val g = fight()
        val me = g.operatives[0]
        for (spot in listOf(g.arena.width / 2f to g.arena.height * 0.6f, g.arena.width / 2f to g.arena.height - 90f, 90f to g.arena.height - 90f)) {
            g.sinkBarriers(); run(g, 0.5f)
            me.px = spot.first; me.py = spot.second
            g.debugBossPattern(com.cyberoperative.game.data.Pattern.Lockdown(110f, 6f))
            run(g, 1.3f)
            // Against room walls fewer cubes are needed; the walls close those sides.
            assertTrue("a box rose at $spot", g.barriers.count { it.solid } >= 3)
            // Some direction leads more than 200 units out.
            val escaped = (0 until 4).any { side ->
                val sx = me.px; val sy = me.py
                val dir = when (side) { 0 -> 1f to 0f; 1 -> 0f to 1f; 2 -> -1f to 0f; else -> 0f to -1f }
                run(g, 2.2f, dir)
                val far = kotlin.math.hypot(me.px - sx, me.py - sy) > 200f
                me.px = sx; me.py = sy
                far
            }
            assertTrue("no way out of the lockdown at $spot", escaped)
        }
    }

    @Test fun cubesStopTheLaser() {
        val g = fight()
        val b = g.boss!!
        val me = g.operatives[0]
        // A cube right between the boss and the operative.
        val mx = (b.x + me.px) / 2f; val my = (b.y + me.py) / 2f
        assertTrue(g.addBarrier(mx, my, 40f, 0.01f, 10f))
        run(g, 0.1f)
        val ang = kotlin.math.atan2(me.py - b.y, me.px - b.x)
        g.addSweep(b.x, b.y, ang, 0.0001f, 26f, 0.05f, 0.5f, 50f, 0xFFFF2D55, b.uid)
        val hp = me.hp
        run(g, 0.6f)
        assertEquals("cover blocked the beam", hp, me.hp, 0.01f)
    }

    @Test fun sweepHitsInTheOpen() {
        val g = fight()
        val b = g.boss!!
        val me = g.operatives[0]
        me.invuln = 0f
        val ang = kotlin.math.atan2(me.py - b.y, me.px - b.x)
        g.addSweep(b.x, b.y, ang - 0.3f, 0.6f, 26f, 0.05f, 0.5f, 50f, 0xFFFF2D55, b.uid)
        val hp = me.hp
        run(g, 0.7f)
        assertTrue("the sweep crossed the operative", me.hp < hp)
    }

    @Test fun mortarLandsOnTheMark() {
        val g = fight()
        val me = g.operatives[0]
        me.invuln = 0f
        val hp = me.hp
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.Mortar(1, 80f, 1.0f, 30f))
        run(g, 0.2f)
        assertTrue(g.hazards.items.any { it.active && it.kind == com.cyberoperative.game.engine.HazardKind.MORTAR })
        assertEquals("no damage mid-flight", hp, me.hp, 0.01f)
        run(g, 1.0f)
        assertTrue("shell hit the spot you stood on", me.hp < hp)
    }

    @Test fun barriersTravelToTheGuest() {
        val w = com.cyberoperative.game.engine.CoopWorld()
        w.barriers += floatArrayOf(300f, 400f, 24f, 0.9f, 8f, 1.5f)
        val back = com.cyberoperative.game.engine.CoopCodec.decodeWorld(com.cyberoperative.game.engine.CoopCodec.encodeWorld(w))!!
        assertEquals(1, back.barriers.size)
        assertEquals(300f, back.barriers[0][0], 0.5f)
        assertEquals(1.5f, back.barriers[0][5], 0.01f)
    }
}

/** Rootkit Apostle: burrow and erupt, spike lines, infected zones on your favourite spots, bloom lanes. */
class RootkitApostleTest {

    private fun fight(): GameEngine {
        val s = RunStats().apply { maxHp = 1e7f; damage = 1f; fireRate = 0.01f; range = 10f }
        val g = GameEngine(RunConfig(baseStats = s, seed = 11L, freeRevives = 0))
        g.debugStartPlan(LevelPlanner.bossPlan(140, Random(1)).copy(boss = com.cyberoperative.game.data.BossExpansion.ROOTKIT_APOSTLE, glitchedBoss = false))
        var t = 0f
        while (t < BossBrain.INTRO_SECONDS + 0.1f) { g.update(1f / 60f); t += 1f / 60f }
        return g
    }

    private fun run(g: GameEngine, seconds: Float, input: Pair<Float, Float> = 0f to 0f) {
        var t = 0f
        while (t < seconds) {
            g.boss?.boss?.let { if (it.active == null) it.rest = 99f }
            g.setInput(input.first, input.second)
            g.update(1f / 60f); t += 1f / 60f
        }
    }

    @Test fun burrowsTunnelsThenErupts() {
        val g = fight()
        val b = g.boss!!
        val me = g.operatives[0]
        val start = kotlin.math.hypot(b.x - me.px, b.y - me.py)
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.Burrow(2.0f, 260f, 0.8f, 95f, 30f))
        run(g, 0.6f)
        assertTrue("underground", b.state == com.cyberoperative.game.engine.AiState.HIDDEN)
        assertTrue("can't be hit underground", !b.targetable)
        run(g, 1.2f)
        assertTrue("tunnelled toward the operative", kotlin.math.hypot(b.x - me.px, b.y - me.py) < start - 200f)
        run(g, 1.0f)
        assertTrue("exit was marked", g.hazards.items.any { it.active && it.kind == com.cyberoperative.game.engine.HazardKind.BLAST } || b.state != com.cyberoperative.game.engine.AiState.HIDDEN)
        run(g, 1.5f)
        assertTrue("surfaced", b.state == com.cyberoperative.game.engine.AiState.MOVE && b.targetable)
    }

    @Test fun spikesHitOnceAfterTheirWarning() {
        val g = fight()
        val me = g.operatives[0]
        me.invuln = 0f
        val hp = me.hp
        g.addSpike(me.px, me.py, 32f, 0.5f, 40f, 0xFFFF2E9A)
        run(g, 0.4f)
        assertEquals("no damage during the warning", hp, me.hp, 0.01f)
        run(g, 0.2f)
        val after = me.hp
        assertTrue("burst hit", after < hp)
        me.invuln = 0f
        run(g, 0.5f)
        assertEquals("lingering crystals don't hit again", after, me.hp, 0.01f)
    }

    @Test fun infectionFollowsWhereYouStood() {
        val g = fight()
        val me = g.operatives[0]
        val camp = g.arena.width * 0.25f to g.arena.height * 0.7f
        // Camp in one spot, then step away.
        var t = 0f
        while (t < 5f) { me.px = camp.first; me.py = camp.second; run(g, 0.1f); t += 0.1f }
        me.px = g.arena.width * 0.75f; me.py = g.arena.height * 0.7f
        run(g, 0.2f)
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.Infect(2, 100f, 6f, 18f))
        val zones = g.hazards.items.filter { it.active && it.kind == com.cyberoperative.game.engine.HazardKind.INFECTED }
        assertTrue("zone where you stand", zones.any { kotlin.math.hypot(it.x - me.px, it.y - me.py) < 10f })
        assertTrue("zone on the spot you camped", zones.any { kotlin.math.hypot(it.x - camp.first, it.y - camp.second) < 90f })
    }

    @Test fun bloomLeavesSafeLanes() {
        val g = fight()
        val b = g.boss!!
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.Bloom(3, 26f, lanes = 3))
        val spikes = g.hazards.items.filter { it.active && it.kind == com.cyberoperative.game.engine.HazardKind.SPIKE }
        assertTrue(spikes.size > 10)
        // Walk each ring's circumference: there must be gaps wider than an operative.
        for (ring in 1..3) {
            val rr = 60f + ring * 88f
            val angles = spikes.filter { kotlin.math.abs(kotlin.math.hypot(it.x - b.x, it.y - b.y) - rr) < 2f }
                .map { kotlin.math.atan2(it.y - b.y, it.x - b.x) }.sorted()
            if (angles.isEmpty()) continue
            val gaps = angles.zipWithNext { a, c -> c - a } + (angles.first() + 2 * Math.PI.toFloat() - angles.last())
            assertTrue("ring $ring has a lane", gaps.max() * rr > 32f * 2f + g.playerRadius * 2f)
        }
    }
}
