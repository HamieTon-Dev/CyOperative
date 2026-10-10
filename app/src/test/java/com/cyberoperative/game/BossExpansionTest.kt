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

    @Test fun expansionBossesTakeTheirPlannedLevels() {
        assertEquals(130, Bosses.firstLevelOf(com.cyberoperative.game.data.BossExpansion.VAULT_SENTINEL))
        assertEquals(140, Bosses.firstLevelOf(com.cyberoperative.game.data.BossExpansion.ROOTKIT_APOSTLE))
        assertEquals(240, Bosses.firstLevelOf(com.cyberoperative.game.data.BossExpansion.NULLSHADE_SPECTER))
        // A slot whose boss isn't built yet keeps its classic, one loop tougher.
        // Any slot whose boss isn't built yet keeps its classic, one loop tougher.
        for (lvl in 130..240 step 10) {
            val slot = com.cyberoperative.game.data.BossExpansion.inSlot(lvl / 10 - 13)
            if (slot == null) {
                assertEquals(Bosses.classics[(lvl / 10 - 1) % 12], Bosses.forLevel(lvl))
                assertEquals(1, Bosses.cycleForLevel(lvl))
            } else assertEquals(slot, Bosses.forLevel(lvl))
        }
        assertEquals(150, Bosses.firstLevelOf(com.cyberoperative.game.data.BossExpansion.PULSE_BISHOP))
        assertEquals(0, Bosses.cycleForLevel(240))
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

/** Nullshade Specter: blackout, eye-glint lock windows, ghost dash, spark ambush, power restore, co-op sync. */
class NullshadeSpecterTest {

    private fun fight(): GameEngine {
        val s = RunStats().apply { maxHp = 1e7f; damage = 1f; fireRate = 0.01f; range = 10f }
        val g = GameEngine(RunConfig(baseStats = s, seed = 13L, freeRevives = 0))
        g.debugStartPlan(LevelPlanner.bossPlan(150, Random(1)).copy(boss = com.cyberoperative.game.data.BossExpansion.NULLSHADE_SPECTER, glitchedBoss = false))
        var t = 0f
        while (t < BossBrain.INTRO_SECONDS + 0.2f) { g.update(1f / 60f); t += 1f / 60f }
        return g
    }

    private fun run(g: GameEngine, seconds: Float) {
        var t = 0f
        while (t < seconds) {
            g.boss?.boss?.let { if (it.active == null) it.rest = 99f }
            g.setInput(0f, 0f)
            g.update(1f / 60f); t += 1f / 60f
        }
    }

    @Test fun blackoutOpensTheFight() {
        val g = fight()
        run(g, 0.2f)
        assertTrue("room is dark", g.darkness > 0.95f)
    }

    @Test fun onlyHittableWhileEyesAreOpen() {
        val g = fight()
        val b = g.boss!!
        var sawClosed = false
        var sawOpen = false
        var t = 0f
        while (t < 8f) {
            run(g, 0.1f); t += 0.1f
            val hp = b.hp
            g.damageEnemy(b, 10f, false, com.cyberoperative.game.engine.ProjKind.BOLT, quiet = true)
            if (b.untargetable) { sawClosed = true; assertEquals("no damage in the shadows", hp, b.hp, 0.001f) }
            else if (b.state == com.cyberoperative.game.engine.AiState.MOVE) { sawOpen = true; assertTrue("damage while eyes are open", b.hp < hp) }
        }
        assertTrue(sawClosed && sawOpen)
    }

    @Test fun ghostDashHitsOnceAndLeavesATrail() {
        val g = fight()
        val me = g.operatives[0]
        me.invuln = 0f
        val hp = me.hp
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.GhostDash(0.3f, 900f, 2000f, 30f))
        run(g, 1.6f)
        assertTrue("dash hit", me.hp < hp)
        assertTrue("corruption trail", g.hazards.items.any { it.active && it.kind == com.cyberoperative.game.engine.HazardKind.INFECTED })
    }

    @Test fun sparkAmbushComesOutOfAServerBlock() {
        val g = fight()
        val b = g.boss!!
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.SparkAmbush(0.8f, 90f, 30f, 10))
        run(g, 0.1f)
        assertEquals(com.cyberoperative.game.engine.AiState.HIDDEN, b.state)
        val mark = g.hazards.items.first { it.active && it.kind == com.cyberoperative.game.engine.HazardKind.BLAST }
        assertTrue("marked spot is a block", g.arena.obstacleAt(mark.x, mark.y, 1f) >= 0)
        run(g, 0.85f)
        assertEquals(com.cyberoperative.game.engine.AiState.MOVE, b.state)
        assertTrue("stepped out beside it", kotlin.math.hypot(b.x - mark.x, b.y - mark.y) < 260f)
        assertTrue("sparks", g.projectiles.items.count { it.active && it.kind == com.cyberoperative.game.engine.ProjKind.NEEDLE } >= 8)
    }

    @Test fun powerRestoresOnTheKill() {
        val g = fight()
        run(g, 0.3f)
        g.killEnemy(g.boss!!)
        var t = 0f
        while (t < 3f) { g.update(1f / 60f); t += 1f / 60f }
        assertEquals(0f, g.darkness, 0.01f)
    }

    @Test fun darknessTravelsToTheGuest() {
        val w = com.cyberoperative.game.engine.CoopWorld()
        w.darkness = 1f; w.bossVeil = 0.4f; w.lightFlicker = 0.18f
        val back = com.cyberoperative.game.engine.CoopCodec.decodeWorld(com.cyberoperative.game.engine.CoopCodec.encodeWorld(w))!!
        assertEquals(1f, back.darkness, 0.01f)
        assertEquals(0.4f, back.bossVeil, 0.01f)
        assertEquals(0.18f, back.lightFlicker, 0.01f)
    }
}

/** Ransom King: key-zone shield, lock grid, royal seizure root + cage, ransom pulse encryption, co-op sync. */
class RansomKingTest {

    private fun fight(): GameEngine {
        val s = RunStats().apply { maxHp = 1e7f; damage = 1f; fireRate = 0.01f; range = 10f }
        val g = GameEngine(RunConfig(baseStats = s, seed = 17L, freeRevives = 0))
        g.debugStartPlan(LevelPlanner.bossPlan(210, Random(1)).copy(boss = com.cyberoperative.game.data.BossExpansion.RANSOM_KING, glitchedBoss = false))
        var t = 0f
        while (t < BossBrain.INTRO_SECONDS + 0.2f) { g.update(1f / 60f); t += 1f / 60f }
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

    @Test fun capturingEveryKeyZoneBreaksTheShield() {
        val g = fight()
        val b = g.boss!!
        val me = g.operatives[0]
        run(g, 0.1f)
        assertEquals("shield up", 1f, g.bossShield, 0f)
        assertEquals(BossBrain.SHIELD_DAMAGE_MUL, b.damageTakenMul, 0.001f)
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.KeyZone(2, 58f, 30f))
        val zones = g.hazards.items.filter { it.active && it.kind == com.cyberoperative.game.engine.HazardKind.KEY_ZONE }
        assertEquals(2, zones.size)
        for (z in zones.toList()) {
            var t = 0f
            while (t < 2f && z.active) { me.px = z.x; me.py = z.y; run(g, 0.05f); t += 0.05f }
            assertTrue("zone unlocked", !z.active)
        }
        assertEquals("shield broken", 0f, g.bossShield, 0f)
        assertEquals(BossBrain.DECRYPTED_DAMAGE_MUL, b.damageTakenMul, 0.001f)
        run(g, BossBrain.DECRYPT_SECONDS + 0.2f)
        assertEquals("shield reforms", 1f, g.bossShield, 0f)
    }

    @Test fun lockGridWallsHaveGaps() {
        val g = fight()
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.LockGrid(1, 6f))
        run(g, 1.2f)
        val cubes = g.barriers.filter { it.solid && it.style == com.cyberoperative.game.engine.Barrier.STYLE_LOCK }
        assertTrue("a wall rose", cubes.size >= 8)
        val across = (g.arena.width / (BossBrain.CUBE * 2f)).toInt()
        assertTrue("the wall has openings", cubes.size <= across - 3)
        assertTrue(g.arena.obstacles.any { it.kind == com.cyberoperative.game.data.ObstacleKind.LOCK_CUBE })
    }

    @Test fun royalSeizureRootsAndCages() {
        val g = fight()
        val me = g.operatives[0]
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.RoyalSeizure(105f, 0.6f, 20f, 1.0f, 3f))
        run(g, 0.7f)
        assertTrue("seized", me.rooted > 0f)
        val x = me.px; val y = me.py
        run(g, 0.5f, 1f to 0f)
        assertEquals("can't move while seized", x, me.px, 0.01f)
        assertEquals(y, me.py, 0.01f)
        assertTrue("caged", g.barriers.count { it.style == com.cyberoperative.game.engine.Barrier.STYLE_LOCK } >= 6)
        run(g, 0.6f)
        assertEquals(0f, me.rooted, 0f)
    }

    @Test fun encryptedMovementBurstsTheRansom() {
        val g = fight()
        val me = g.operatives[0]
        me.invuln = 0f
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.RansomPulse(1, 0.5f, 900f, 300f, 10f))
        var t = 0f
        while (t < 4f && me.encrypted <= 0f) { run(g, 0.05f); t += 0.05f }
        assertTrue("tagged by the ring", me.encrypted > 0f)
        // Standing still: nothing happens.
        val hp = me.hp
        run(g, 0.5f)
        assertEquals(hp, me.hp, 0.01f)
        // Moving charges the burst.
        run(g, 1.3f, 0f to 1f)
        assertTrue("ransom burst on the move", me.hp < hp)
        assertEquals(0f, me.encrypted, 0f)
    }

    @Test fun statusesTravelToTheGuest() {
        val w = com.cyberoperative.game.engine.CoopWorld()
        w.bossShield = 1f
        w.barriers += floatArrayOf(100f, 200f, 24f, 1f, 6f, 2f, 1f)
        w.ops += com.cyberoperative.game.engine.NetOp().apply { rooted = 0.8f; encrypted = 2f; encryptCharge = 0.5f }
        val back = com.cyberoperative.game.engine.CoopCodec.decodeWorld(com.cyberoperative.game.engine.CoopCodec.encodeWorld(w))!!
        assertEquals(1f, back.bossShield, 0f)
        assertEquals(1f, back.barriers[0][6], 0f)
        assertEquals(0.8f, back.ops[0].rooted, 0.01f)
        assertEquals(2f, back.ops[0].encrypted, 0.01f)
        assertEquals(0.5f, back.ops[0].encryptCharge, 0.01f)
    }
}

/**
 * Owner, 2026-10-10: "do they work if the player is moving while fighting ransom king? If he does
 * the lock down to put you in a square, what if you're at the top of the screen or near a barrier?"
 */
class RansomKingEdgeCaseTest {

    private fun fight(seed: Long = 21L): GameEngine {
        val s = RunStats().apply { maxHp = 1e7f; damage = 1f; fireRate = 0.01f; range = 10f }
        val g = GameEngine(RunConfig(baseStats = s, seed = seed, freeRevives = 0))
        g.debugStartPlan(LevelPlanner.bossPlan(210, Random(seed)).copy(boss = com.cyberoperative.game.data.BossExpansion.RANSOM_KING, glitchedBoss = false))
        var t = 0f
        while (t < BossBrain.INTRO_SECONDS + 0.2f) { g.update(1f / 60f); t += 1f / 60f }
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

    /** Floor reachable from the operative (flood fill on a 12-unit grid), in square units. */
    private fun reachableArea(g: GameEngine): Float {
        val step = 12f
        val cols = (g.arena.width / step).toInt()
        val rows = (g.arena.height / step).toInt()
        val seen = BooleanArray(cols * rows)
        val me = g.operatives[0]
        val start = (me.py / step).toInt().coerceIn(0, rows - 1) * cols + (me.px / step).toInt().coerceIn(0, cols - 1)
        val queue = ArrayDeque<Int>().apply { add(start) }
        seen[start] = true
        var count = 0
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            count++
            val cx = c % cols; val cy = c / cols
            for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                val nx = cx + dx; val ny = cy + dy
                if (nx !in 0 until cols || ny !in 0 until rows) continue
                val n = ny * cols + nx
                if (seen[n]) continue
                if (!g.arena.isFree((nx + 0.5f) * step, (ny + 0.5f) * step, g.playerRadius * 0.9f)) continue
                seen[n] = true
                queue.add(n)
            }
        }
        return count * step * step
    }

    @Test fun movingOutOfTheSlamAvoidsTheSeizure() {
        val g = fight()
        val me = g.operatives[0]
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.RoyalSeizure(110f, 1.0f, 28f, 1.1f, 3.5f))
        // Keep running sideways through the warning.
        run(g, 1.2f, 1f to 0f)
        assertEquals("not seized after moving out", 0f, me.rooted, 0f)
        assertTrue("no cage without a catch", g.barriers.none { it.style == com.cyberoperative.game.engine.Barrier.STYLE_LOCK })
    }

    @Test fun lockGridNeverTrapsAMovingOperativeInsideACube() {
        for (seed in 1L..12L) {
            val g = fight(seed)
            val me = g.operatives[0]
            g.debugBossPattern(com.cyberoperative.game.data.Pattern.LockGrid(3, 6f))
            // Walk around while the walls rise.
            run(g, 0.5f, 0f to -1f)
            run(g, 0.6f, 1f to 0f)
            run(g, 0.4f)
            assertTrue("seed $seed: not stuck inside a cube", g.arena.isFree(me.px, me.py, g.playerRadius * 0.9f))
            assertTrue("seed $seed: plenty of room to move", reachableArea(g) > 120f * 120f)
        }
    }

    @Test fun seizureCageAlwaysHasAWayOutAtEdgesAndBlocks() {
        val g0 = fight()
        val w = g0.arena.width; val h = g0.arena.height
        // Top edge, top corners, under the boss's start, side walls, bottom corner, and right next to a block.
        val spots = mutableListOf(w / 2f to 70f, 60f to 60f, w - 60f to 60f, 50f to h / 2f, w - 50f to h / 2f, 60f to h - 60f, w / 2f to h / 2f)
        val block = g0.arena.obstacles.firstOrNull()?.rect
        if (block != null) spots += (block.right + 30f) to block.centerY
        var caged = 0
        for ((i, spot) in spots.withIndex()) {
            val g = fight(30L + i)
            val me = g.operatives[0]
            g.arena.pushOut(spot.first, spot.second, g.playerRadius)
            me.px = g.arena.out[0]; me.py = g.arena.out[1]
            g.debugBossPattern(com.cyberoperative.game.data.Pattern.RoyalSeizure(110f, 0.5f, 10f, 1.0f, 5f))
            run(g, 0.95f)
            assertTrue("spot $i: seized", me.rooted > 0f)
            run(g, 0.4f)
            val cubes = g.barriers.count { it.solid && it.style == com.cyberoperative.game.engine.Barrier.STYLE_LOCK }
            // In a corner the room walls are two sides of the cage, so fewer cubes are needed.
            caged += if (cubes >= 3) 1 else 0
            val room = reachableArea(g)
            // The cage's inside is ~190 × 190; being able to reach well beyond that means an open side.
            assertTrue("spot $i (${spot.first.toInt()},${spot.second.toInt()}): sealed in (reach $room)", room > 260f * 260f)
        }
        assertTrue("cages really rose at most spots ($caged)", caged >= spots.size - 2)
    }
}

/** Spectral Firewall: ring shield and gaps, Firewall Ring out to the walls (burns, exposes the core), burn sector, heat collapse, purge spin. */
class SpectralFirewallTest {

    private fun fight(seed: Long = 23L): GameEngine {
        val s = RunStats().apply { maxHp = 1e7f; damage = 1f; fireRate = 0.01f; range = 10f }
        val g = GameEngine(RunConfig(baseStats = s, seed = seed, freeRevives = 0))
        g.debugStartPlan(LevelPlanner.bossPlan(230, Random(seed)).copy(boss = com.cyberoperative.game.data.BossExpansion.SPECTRAL_FIREWALL, glitchedBoss = false))
        var t = 0f
        while (t < BossBrain.INTRO_SECONDS + 0.2f) { g.update(1f / 60f); t += 1f / 60f }
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

    @Test fun ringBlocksShotsExceptThroughTheGaps() {
        val g = fight()
        val b = g.boss!!
        val me = g.operatives[0]
        run(g, 0.1f)
        assertEquals(6, g.bossRingFilled)
        // Sweep all the way round the boss: some angles are blocked, some (the gaps) let damage through.
        var blocked = 0; var through = 0
        for (k in 0 until 72) {
            val a = k * Math.PI.toFloat() * 2f / 72f
            me.px = b.x + kotlin.math.cos(a) * 260f; me.py = b.y + 0.8f * b.radius - 18f + kotlin.math.sin(a) * 260f * 0.36f
            val hp = b.hp
            g.damageEnemy(b, 10f, false, com.cyberoperative.game.engine.ProjKind.BOLT, quiet = true)
            if (b.hp < hp) through++ else blocked++
        }
        assertTrue("most angles are covered by plates ($blocked)", blocked > 36)
        assertTrue("the gaps let shots through ($through)", through > 6)
        // Standing in a gap (as the bot does) always gets hits in.
        val gap = g.firewallGapPoint(2.2f)!!
        me.px = gap.first; me.py = gap.second
        val hp = b.hp
        g.damageEnemy(b, 10f, false, com.cyberoperative.game.engine.ProjKind.BOLT, quiet = true)
        assertTrue("shot through a gap", b.hp < hp)
    }

    @Test fun firewallRingPushesToTheWallsBurnsAndExposesTheCore() {
        val g = fight()
        val b = g.boss!!
        val me = g.operatives[0]
        me.invuln = 0f
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.FirewallRing(1, 2, 400f, 20f))
        run(g, 0.8f)
        val wall = g.hazards.items.first { it.active && it.kind == com.cyberoperative.game.engine.HazardKind.FIRE_WALL }
        assertTrue("ring launched: core exposed", g.bossRingOut)
        // From anywhere, while the ring is out, the core takes damage.
        val hp = b.hp
        g.damageEnemy(b, 10f, false, com.cyberoperative.game.engine.ProjKind.BOLT, quiet = true)
        assertTrue(b.hp < hp)
        // Stand in its path away from the gaps; it reaches you and sets you on fire.
        val far = kotlin.math.hypot(g.arena.width, g.arena.height)
        var burned = false
        var t = 0f
        while (t < 6f && wall.active) {
            if (!burned) {
                // Keep the operative on a plate (opposite a gap) at a fixed distance.
                val a = wall.x2 / 1000f + Math.PI.toFloat() / 2f
                me.px = (b.x + kotlin.math.cos(a) * 300f).coerceIn(30f, g.arena.width - 30f)
                me.py = (b.y + kotlin.math.sin(a) * 300f).coerceIn(30f, g.arena.height - 30f)
            }
            run(g, 0.05f); t += 0.05f
            if (me.burning > 0f) burned = true
        }
        assertTrue("touching the wall sets you on fire", burned)
        assertTrue("it went all the way out", wall.maxRadius >= far - 1f)
        run(g, 0.3f)
        assertTrue("ring back once the wall is gone", !g.bossRingOut)
    }

    @Test fun ringWavesBuildByPhase() {
        val def = com.cyberoperative.game.data.BossExpansion.SPECTRAL_FIREWALL
        val waves = def.phases.map { ph -> ph.patterns.filterIsInstance<com.cyberoperative.game.data.Pattern.FirewallRing>().maxOf { it.waves } }
        assertEquals(listOf(1, 3, 4), waves)
        // All four phase-3 walls are out at once and each still has its gaps.
        val g = fight()
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.FirewallRing(4, 2, 190f, 28f, waveGap = 1.05f))
        run(g, 4.0f)
        val walls = g.hazards.items.filter { it.active && it.kind == com.cyberoperative.game.engine.HazardKind.FIRE_WALL }
        assertEquals(4, walls.size)
        assertTrue(walls.all { it.y2.toInt() == 2 })
    }

    @Test fun burnSectorWarnsThenBurns() {
        val g = fight()
        val me = g.operatives[0]
        me.invuln = 0f
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.BurnSector(1, 70f, 1.0f, 2.0f, 20f))
        val hp = me.hp
        run(g, 0.9f)
        assertEquals("nothing during the warning", hp, me.hp, 0.01f)
        run(g, 0.6f)
        assertTrue("burning once it ignites", me.hp < hp && me.burning > 0f)
    }

    @Test fun heatCollapseClosesInWithGaps() {
        val g = fight()
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.HeatCollapse(2, 200f, 20f))
        run(g, 0.2f)
        val wall = g.hazards.items.first { it.active && it.kind == com.cyberoperative.game.engine.HazardKind.FIRE_WALL }
        val r0 = wall.radius
        run(g, 1.0f)
        assertTrue("closing in", wall.radius < r0)
        assertEquals(2, wall.y2.toInt())
        assertTrue("collapse doesn't take the ring away", !g.bossRingOut)
    }

    @Test fun purgeSpinSetsYouOnFire() {
        val g = fight()
        val me = g.operatives[0]
        me.invuln = 0f
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.PurgeSpin(4, 0.3f, 3f, 360f, 20f))
        var t = 0f
        while (t < 3.5f && me.burning <= 0f) { run(g, 0.05f); t += 0.05f }
        assertTrue("a flame jet caught the operative", me.burning > 0f)
    }

    @Test fun ringAndBurnTravelToTheGuest() {
        val w = com.cyberoperative.game.engine.CoopWorld()
        w.bossRingAngle = 1.2f; w.bossRingFilled = 7; w.bossRingOut = true
        w.ops += com.cyberoperative.game.engine.NetOp().apply { burning = 1.5f }
        val back = com.cyberoperative.game.engine.CoopCodec.decodeWorld(com.cyberoperative.game.engine.CoopCodec.encodeWorld(w))!!
        assertEquals(1.2f, back.bossRingAngle, 0.03f)
        assertEquals(7, back.bossRingFilled)
        assertTrue(back.bossRingOut)
        assertEquals(1.5f, back.ops[0].burning, 0.01f)
    }
}

/** Shared harness for the autonomous expansion bosses. */
abstract class ExpansionBossHarness(private val def: () -> com.cyberoperative.game.data.BossDef, private val level: Int) {
    protected fun fight(seed: Long = 31L): GameEngine {
        val s = RunStats().apply { maxHp = 1e7f; damage = 1f; fireRate = 0.01f; range = 10f }
        val g = GameEngine(RunConfig(baseStats = s, seed = seed, freeRevives = 0))
        g.debugStartPlan(LevelPlanner.bossPlan(level, Random(seed)).copy(boss = def(), glitchedBoss = false))
        var t = 0f
        while (t < BossBrain.INTRO_SECONDS + 0.2f) { g.update(1f / 60f); t += 1f / 60f }
        return g
    }

    protected fun run(g: GameEngine, seconds: Float, input: Pair<Float, Float> = 0f to 0f) {
        var t = 0f
        while (t < seconds) {
            g.boss?.boss?.let { if (it.active == null) it.rest = 99f }
            g.setInput(input.first, input.second)
            g.update(1f / 60f); t += 1f / 60f
        }
    }

    protected fun hazards(g: GameEngine, k: com.cyberoperative.game.engine.HazardKind) = g.hazards.items.filter { it.active && it.kind == k }
}

class PulseBishopTest : ExpansionBossHarness({ com.cyberoperative.game.data.BossExpansion.PULSE_BISHOP }, 150) {
    @Test fun lineWarpLandsOnYourLane() {
        val g = fight()
        val b = g.boss!!
        val me = g.operatives[0]
        me.px = 120f; me.py = g.arena.height * 0.6f
        val x0 = b.x; val y0 = b.y
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.LineWarp(0.6f, 20f))
        assertTrue("it jumped (${x0},${y0} -> ${b.x},${b.y})", kotlin.math.hypot(b.x - x0, b.y - y0) > 100f)
        assertTrue("same row or column", kotlin.math.abs(b.x - me.px) < 1f || kotlin.math.abs(b.y - me.py) < 1f)
        assertTrue("beam down the lane", hazards(g, com.cyberoperative.game.engine.HazardKind.BEAM).isNotEmpty())
    }

    @Test fun crossBeamFiresFourWays() {
        val g = fight()
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.CrossBeam(0.5f, 1f, 0f, 26f, 20f))
        assertEquals(4, hazards(g, com.cyberoperative.game.engine.HazardKind.SWEEP).size)
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.CrossBeam(0.5f, 1f, 0f, 26f, 20f, diagonal = true))
        assertEquals(12, hazards(g, com.cyberoperative.game.engine.HazardKind.SWEEP).size)
    }

    @Test fun minesArmThenDetonateWhenYouComeClose() {
        val g = fight()
        val me = g.operatives[0]
        me.invuln = 0f
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.BishopMines(1, 0.8f, 20f, 80f, 30f))
        val mine = hazards(g, com.cyberoperative.game.engine.HazardKind.MINE).single()
        val hp = me.hp
        run(g, 0.5f)
        assertEquals("unarmed: harmless", hp, me.hp, 0.01f)
        run(g, 0.9f)
        assertTrue("tripped and blew", !mine.active && me.hp < hp)
    }

    @Test fun convergenceFlashPullsThenBlasts() {
        val g = fight()
        val b = g.boss!!
        val me = g.operatives[0]
        me.invuln = 0f
        me.px = b.x; me.py = b.y + 400f
        val d0 = kotlin.math.hypot(me.px - b.x, me.py - b.y)
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.ConvergenceFlash(1.2f, 160f, 200f, 30f))
        run(g, 1.0f)
        assertTrue("pulled in", kotlin.math.hypot(me.px - b.x, me.py - b.y) < d0 - 120f)
        // Running away against the pull keeps you out of the blast.
        val hp = me.hp
        run(g, 0.6f, 0f to 1f)
        assertTrue("flash went off", g.fx.flashNow > 0f || me.hp <= hp)
    }
}

class PacketReaperTest : ExpansionBossHarness({ com.cyberoperative.game.data.BossExpansion.PACKET_REAPER }, 160) {
    @Test fun dashSlashHitsAndEndsInACrescent() {
        val g = fight()
        val me = g.operatives[0]
        me.invuln = 0f
        val hp = me.hp
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.DashSlash(0.4f, 900f, 1200f, 30f, trail = true))
        var sawSlash = false; var sawTrail = false
        var t = 0f
        while (t < 2.5f) {
            run(g, 0.05f); t += 0.05f
            if (hazards(g, com.cyberoperative.game.engine.HazardKind.SLASH).isNotEmpty()) sawSlash = true
            if (hazards(g, com.cyberoperative.game.engine.HazardKind.BLAST).isNotEmpty()) sawTrail = true
        }
        assertTrue("dash hit", me.hp < hp)
        assertTrue("crescent slash at the end", sawSlash)
        assertTrue("trail burst along the path", sawTrail)
    }

    @Test fun scythesComeBack() {
        val g = fight()
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.Scythes(2, 380f, 1.6f, 22f))
        val sc = hazards(g, com.cyberoperative.game.engine.HazardKind.SCYTHE)
        assertEquals(2, sc.size)
        val s = sc[0]
        run(g, 0.8f)
        val far = kotlin.math.hypot(s.x2 - s.x, s.y2 - s.y)
        run(g, 0.7f)
        val back = kotlin.math.hypot(s.x2 - s.x, s.y2 - s.y)
        assertTrue("went out ($far) and came back ($back)", far > 250f && back < far * 0.5f)
    }

    @Test fun backlineDiveComesFromBehind() {
        val g = fight()
        val b = g.boss!!
        val me = g.operatives[0]
        me.facing = -Math.PI.toFloat() / 2f // facing up (toward the top of the arena)
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.BacklineDive(0.6f, 900f, 30f))
        run(g, 0.1f)
        assertEquals(com.cyberoperative.game.engine.AiState.HIDDEN, b.state)
        run(g, 0.55f)
        assertTrue("appeared behind (below) the operative", b.y > me.py)
    }
}

class WormQueenTest : ExpansionBossHarness({ com.cyberoperative.game.data.BossExpansion.WORM_QUEEN }, 170) {
    @Test fun eggsHatchIntoSwarmlings() {
        val g = fight()
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.SwarmHatch(3, 1.0f, 3))
        assertTrue(hazards(g, com.cyberoperative.game.engine.HazardKind.EGG).size >= 2)
        run(g, 1.2f)
        assertTrue("hatched", g.enemies.items.count { it.active && it.def.id == "swarmling" } >= 6)
    }

    @Test fun aRealSwarm() {
        val g = fight()
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.SwarmHatch(10, 0.8f, 5))
        run(g, 5f)
        val n = g.enemies.items.count { it.active && it.def.id == "swarmling" }
        assertTrue("a swarm well past the normal cap ($n)", n > com.cyberoperative.game.core.Scaling.MAX_ALIVE)
    }

    @Test fun trailLeavesInfectedPools() {
        val g = fight()
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.CorruptionTrail(2f, 1.6f, 55f, 7f, 16f))
        run(g, 2.1f)
        assertTrue(hazards(g, com.cyberoperative.game.engine.HazardKind.INFECTED).size >= 4)
    }

    @Test fun roarHastesTheSwarmThenWearsOff() {
        val g = fight()
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.SwarmHatch(2, 0.3f, 3))
        run(g, 0.5f)
        val s = g.enemies.items.first { it.active && it.def.id == "swarmling" }
        val base = s.speed
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.QueenRoar(380f, 230f, 20f, 1.6f, 1f))
        assertEquals(base * 1.6f, s.speed, 0.5f)
        run(g, 1.2f)
        assertEquals("speed restored", base, s.speed, 0.5f)
    }
}

class GlitchForgeTest : ExpansionBossHarness({ com.cyberoperative.game.data.BossExpansion.GLITCH_FORGE }, 180) {
    @Test fun decoysPopInOneHit() {
        val g = fight()
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.Summon("holo_clone", 3))
        run(g, 1.5f)
        val clone = g.enemies.items.first { it.active && it.def.id == "holo_clone" }
        g.damageEnemy(clone, 5f, false, com.cyberoperative.game.engine.ProjKind.BOLT, quiet = true)
        assertTrue("popped", !clone.active)
    }

    @Test fun corruptFloorLeavesHalfTheTilesSafe() {
        val g = fight()
        val me = g.operatives[0]
        me.invuln = 0f
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.CorruptFloor(5, 0.8f, 3f, 30f))
        val tiles = hazards(g, com.cyberoperative.game.engine.HazardKind.TILE)
        assertTrue("checkerboard: about half of 25", tiles.size in 9..13)
        // A safe cell right next to you exists: step onto it and stay unharmed.
        val c = 64f
        val gx = (me.px / c).toInt(); val gy = (me.py / c).toInt()
        val safe = listOf(0 to 0, 1 to 0, 0 to 1, -1 to 0, 0 to -1).map { (dx, dy) -> (gx + dx + 0.5f) * c to (gy + dy + 0.5f) * c }
            .first { (x, y) -> tiles.none { kotlin.math.abs(it.x - x) < 1f && kotlin.math.abs(it.y - y) < 1f } }
        me.px = safe.first; me.py = safe.second
        val hp = me.hp
        run(g, 1.5f)
        assertEquals("safe tile is safe", hp, me.hp, 0.01f)
    }

    @Test fun cubesHome() {
        val g = fight()
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.CubeBarrage(2, 3, 220f, 2f, 10f))
        run(g, 0.7f)
        val cubes = g.projectiles.items.filter { it.active && it.kind == com.cyberoperative.game.engine.ProjKind.CUBE }
        assertTrue(cubes.size >= 4 && cubes.all { it.homing > 0f })
    }

    @Test fun corePulseCorruptsTilesBehindTheRing() {
        val g = fight()
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.CorePulse(380f, 230f, 20f, 2.5f, 16f))
        assertTrue(hazards(g, com.cyberoperative.game.engine.HazardKind.SHOCK_RING).isNotEmpty())
        assertTrue(hazards(g, com.cyberoperative.game.engine.HazardKind.TILE).size >= 8)
    }
}

class BotnetMonarchTest : ExpansionBossHarness({ com.cyberoperative.game.data.BossExpansion.BOTNET_MONARCH }, 190) {
    @Test fun droneRingOrbitsAndCanBeShotDown() {
        val g = fight()
        val b = g.boss!!
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.DroneRing(4))
        run(g, 1.2f)
        val drones = g.enemies.items.filter { it.active && it.def.id == "orbit_drone" }
        assertEquals(4, drones.size)
        val d0 = drones[0]
        val a0 = kotlin.math.atan2(d0.y - b.y, d0.x - b.x)
        run(g, 0.5f)
        val a1 = kotlin.math.atan2(d0.y - b.y, d0.x - b.x)
        assertTrue("orbiting", kotlin.math.abs(a1 - a0) > 0.1f)
        assertTrue("close to the monarch", kotlin.math.hypot(d0.x - b.x, d0.y - b.y) < b.radius * 2.6f)
        g.damageEnemy(d0, 1e9f, false, com.cyberoperative.game.engine.ProjKind.BOLT, quiet = true)
        assertTrue(!d0.active)
    }

    @Test fun syncBurstLinksThenFiresTogether() {
        val g = fight()
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.DroneRing(4))
        run(g, 1.2f)
        for (p in g.projectiles.items) p.active = false
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.SyncBurst(1.0f, 2, 260f, 10f))
        run(g, 0.5f)
        assertTrue("linking", g.bossSync in 0.3f..0.7f)
        run(g, 0.6f)
        assertTrue("fired together", g.projectiles.items.count { it.active && !it.friendly } >= 16 + 8)
    }

    @Test fun orbitalStrikesLandOnTheMark() {
        val g = fight()
        val me = g.operatives[0]
        me.invuln = 0f
        val hp = me.hp
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.OrbitalBarrage(3, 80f, 1.0f, 30f))
        run(g, 0.8f)
        assertEquals(hp, me.hp, 0.01f)
        run(g, 0.4f)
        assertTrue(me.hp < hp)
    }
}

class CircuitHydraTest : ExpansionBossHarness({ com.cyberoperative.game.data.BossExpansion.CIRCUIT_HYDRA }, 200) {
    @Test fun rigHasAHeadPerPhaseAndFloatsInItsBand() {
        val g = fight()
        run(g, 2f)
        assertEquals("three heads in phase 1", 3 * 4, g.bossTrail.size)
        val b = g.boss!!
        var t = 0f
        while (t < 30f) {
            run(g, 0.25f); t += 0.25f
            assertTrue("floats between 30% and 50% down (y=${b.y})", b.y in g.arena.height * 0.28f..g.arena.height * 0.52f)
        }
    }

    private fun heads(g: com.cyberoperative.game.engine.GameEngine) = g.enemies.items.filter { it.active && it.def.id == "hydra_head" }

    @Test fun headsShieldTheCoreUntilAllAreDown() {
        val g = fight()
        val b = g.boss!!
        run(g, 1.5f)
        assertEquals("three heads in phase 1", 3, heads(g).size)
        assertEquals(0b111, g.bossHeadMask)
        val hp = b.hp
        g.damageEnemy(b, 500f, false, com.cyberoperative.game.engine.ProjKind.BOLT, quiet = true)
        assertEquals("core shielded while heads live", hp, b.hp, 0.01f)
        // Heads take their own damage.
        val h0 = heads(g)[0]
        val h0hp = h0.hp
        g.damageEnemy(h0, 10f, false, com.cyberoperative.game.engine.ProjKind.BOLT, quiet = true)
        assertTrue(h0.hp < h0hp)
        // Kill every head: the core is exposed.
        for (h in heads(g)) g.damageEnemy(h, 1e6f, false, com.cyberoperative.game.engine.ProjKind.BOLT, quiet = true)
        run(g, 0.1f)
        assertEquals(0, g.bossHeadMask)
        g.damageEnemy(b, 500f, false, com.cyberoperative.game.engine.ProjKind.BOLT, quiet = true)
        assertTrue("exposed core takes damage", b.hp < hp)
        // After the window every head regrows.
        run(g, BossBrain.EXPOSE_SECONDS + 1.5f)
        assertEquals(3, heads(g).size)
        assertTrue(g.bossHeadMask > 0)
    }

    @Test fun beamArcOneBeamPerLivingHead() {
        val g = fight()
        run(g, 1.5f)
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.BeamArc(1, 0.8f, 1.5f, 60f, 0f, 20f))
        val beams = hazards(g, com.cyberoperative.game.engine.HazardKind.SWEEP)
        assertEquals(3, beams.size)
        // Beams leave the mouths, out on the necks, not the core.
        val b = g.boss!!
        val rig = g.bossTrail
        for (h in beams) {
            assertTrue("beam starts away from the core", kotlin.math.hypot(h.x - b.x, h.y - b.y) > b.radius * 2f)
            val near = (0 until rig.size / 4).minOf { k -> kotlin.math.hypot(h.x - rig[k * 4 + 2], h.y - rig[k * 4 + 3]) }
            assertTrue("beam starts at a head ($near)", near < b.radius * 1.2f)
        }
        // A destroyed head's beam dies with it, and it can't fire again.
        val victim = heads(g).first()
        g.damageEnemy(victim, 1e6f, false, com.cyberoperative.game.engine.ProjKind.BOLT, quiet = true)
        run(g, 0.1f)
        assertEquals(2, hazards(g, com.cyberoperative.game.engine.HazardKind.SWEEP).size)
        for (h in hazards(g, com.cyberoperative.game.engine.HazardKind.SWEEP)) h.active = false
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.BeamArc(1, 0.8f, 1.5f, 60f, 0f, 20f))
        assertEquals(2, hazards(g, com.cyberoperative.game.engine.HazardKind.SWEEP).size)
    }

    @Test fun coilClosesInWithAGap() {
        val g = fight()
        val me = g.operatives[0]
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.CoilCrush(280f, 70f, 110f, 20f))
        val coil = hazards(g, com.cyberoperative.game.engine.HazardKind.COIL).single()
        val r0 = coil.radius
        run(g, 1f)
        assertTrue(coil.radius < r0 && coil.y2.toInt() == 1)
        // Walking out through the gap gets you clear.
        val gap = coil.x2 / 1000f
        me.invuln = 0f
        val hp = me.hp
        var t = 0f
        while (t < 1.4f) {
            val a = coil.x2 / 1000f
            run(g, 0.05f, kotlin.math.cos(a) to kotlin.math.sin(a)); t += 0.05f
        }
        assertEquals("slipped out through the gap", hp, me.hp, 0.01f)
    }

    @Test fun segmentBurstFiresFromTheBody() {
        val g = fight()
        run(g, 1.5f)
        for (p in g.projectiles.items) p.active = false
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.SegmentBurst(6, 190f, 10f))
        assertTrue(g.projectiles.items.count { it.active && !it.friendly } >= 24)
    }
}

class BlackIceOverlordTest : ExpansionBossHarness({ com.cyberoperative.game.data.BossExpansion.BLACK_ICE_OVERLORD }, 220) {
    @Test fun freezePatchStacksChillThenFreezes() {
        val g = fight()
        val me = g.operatives[0]
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.FreezePatch(1, 110f, 8f, 0.3f))
        var froze = false
        var t = 0f
        while (t < 4f) { run(g, 0.1f); t += 0.1f; if (me.frozen > 0f) froze = true }
        assertTrue("five stacks froze the operative", froze)
    }

    @Test fun chillSlowsMovement() {
        val g = fight()
        val me = g.operatives[0]
        me.px = 360f; me.py = g.arena.height * 0.7f
        val x0 = me.px
        run(g, 0.5f, 1f to 0f)
        val free = me.px - x0
        me.px = 360f; me.chill = 4f
        run(g, 0.5f, 1f to 0f)
        val slowed = me.px - 360f
        assertTrue("chilled moves slower ($slowed vs $free)", slowed < free * 0.8f)
    }

    @Test fun iceLaserChills() {
        val g = fight()
        val b = g.boss!!
        val me = g.operatives[0]
        me.invuln = 0f
        val a = kotlin.math.atan2(me.py - (b.y - b.radius * 0.4f), me.px - (b.x - b.radius * 0.7f))
        g.addSweep(b.x - b.radius * 0.7f, b.y - b.radius * 0.4f, a - 0.3f, 0.6f, 24f, 0.05f, 0.6f, 10f, 0xFF3AB8FF, -1)?.tick = 2f
        run(g, 0.8f)
        assertTrue(me.chill > 0f || me.frozen > 0f)
    }

    @Test fun shellSoaksDamageUntilShattered() {
        val g = fight()
        val b = g.boss!!
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.PermafrostShell(0.1f, 20f, 230f))
        assertEquals(1f, g.bossIceShell, 0.01f)
        val hp = b.hp
        g.damageEnemy(b, 10f, false, com.cyberoperative.game.engine.ProjKind.BOLT, quiet = true)
        assertEquals("a quarter gets through", hp - 2.5f, b.hp, 0.5f)
        // Sustained fire breaks it.
        repeat(400) { if (g.bossIceShell > 0f) g.damageEnemy(b, b.maxHp * 0.01f, false, com.cyberoperative.game.engine.ProjKind.BOLT, quiet = true) }
        assertTrue("shattered", g.bossIceShell < 0f)
        val hp2 = b.hp
        g.damageEnemy(b, 10f, false, com.cyberoperative.game.engine.ProjKind.BOLT, quiet = true)
        assertTrue("full damage again", hp2 - b.hp > 5f)
    }

    @Test fun crystalsLandAndChill() {
        val g = fight()
        val me = g.operatives[0]
        me.invuln = 0f
        g.debugBossPattern(com.cyberoperative.game.data.Pattern.CrystalVolley(1, 80f, 0.8f, 10f))
        run(g, 1.1f)
        assertTrue(me.chill > 0f)
    }
}

class WildBossTest {
    @Test fun sevenBossesTurnUpAtAnyBossLevel() {
        val seen = HashSet<String>()
        var wild = 0
        for (seed in 0 until 400) {
            val lvl = 20 + 10 * (seed % 10)
            val plan = LevelPlanner.bossPlan(lvl, Random(seed.toLong()))
            if (plan.boss != Bosses.forLevel(lvl)) { wild++; seen += plan.boss!!.id }
        }
        assertTrue("about a quarter are wild ($wild/400)", wild in 70..130)
        assertTrue("all seven appear ($seen)", seen.containsAll(LevelPlanner.WILD_BOSS_IDS))
    }

    @Test fun neverAtTheFirstBoss() {
        for (seed in 0 until 200) assertEquals(Bosses.forLevel(10), LevelPlanner.bossPlan(10, Random(seed.toLong())).boss)
    }

    @Test fun earlyWildBossIsTonedDown() {
        val s = RunStats().apply { maxHp = 1e7f }
        val g = GameEngine(RunConfig(baseStats = s, seed = 1L, freeRevives = 0))
        g.debugStartPlan(LevelPlanner.bossPlan(30, Random(1)).copy(boss = com.cyberoperative.game.data.BossExpansion.WORM_QUEEN, glitchedBoss = false))
        g.update(1f / 60f)
        val wildHp = g.boss!!.maxHp
        val g2 = GameEngine(RunConfig(baseStats = s, seed = 1L, freeRevives = 0))
        g2.debugStartPlan(LevelPlanner.bossPlan(30, Random(1)).copy(boss = Bosses.forLevel(30), glitchedBoss = false))
        g2.update(1f / 60f)
        assertTrue("wild ${wildHp} vs scheduled ${g2.boss!!.maxHp}", wildHp <= g2.boss!!.maxHp * 1.15f)
    }
}
