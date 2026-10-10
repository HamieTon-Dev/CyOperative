package com.cyberoperative.game

import com.cyberoperative.game.data.AiKind
import com.cyberoperative.game.data.AttackKind
import com.cyberoperative.game.data.Bosses
import com.cyberoperative.game.data.Enemies
import com.cyberoperative.game.data.EnemyVariants
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.LevelKind
import com.cyberoperative.game.engine.LevelPlan
import com.cyberoperative.game.engine.LevelPlanner
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.engine.RunStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Owner, 2026-10-08: 300+ more enemy types, plus *GLITCHED* enemies and bosses. */
class RosterTest {

    @Test fun atLeastThreeHundredNewTypesAllDistinct() {
        val added = Enemies.all.size - Enemies.originals.size
        assertTrue("only $added new types", added >= 300)
        assertEquals(Enemies.all.size, Enemies.all.map { it.id }.toSet().size)
        assertEquals(Enemies.all.size, Enemies.all.map { it.name }.toSet().size)
        for (d in EnemyVariants.all) {
            assertTrue(d.id, d.baseHp > 0f && d.radius > 0f && d.minLevel >= 1)
            if (d.attack != AttackKind.NONE) assertTrue(d.id, d.attackCooldown > 0f && d.projectileSpeed > 0f && d.projectileCount >= 1)
            if (d.ai == AiKind.CHARGER) assertTrue(d.id, d.projectileSpeed > 0f)
            d.splitInto?.let { Enemies.byId(it) }
        }
    }

    @Test fun rosterWidensWithDepth() {
        val l1 = Enemies.pool(1).size
        val l20 = Enemies.pool(20).size
        val l60 = Enemies.pool(60).size
        assertTrue("$l1 < $l20 < $l60", l1 < l20 && l20 < l60)
        assertTrue(l60 >= 300)
        assertTrue(Enemies.pool(10).none { it.glitched })
        assertTrue(Enemies.pool(45).count { it.glitched } == 3)
    }

    /** Every type spawns and fights for a few seconds without breaking the engine. */
    @Test fun everyTypeRunsInTheEngine() {
        val g = GameEngine(RunConfig(baseStats = RunStats().apply { maxHp = 1e9f; damage = 0f; orbCount = 0 }, seed = 3L, freeRevives = 0))
        for (batch in Enemies.all.chunked(12)) {
            for (e in g.enemies.items) e.active = false
            for ((i, d) in batch.withIndex()) g.spawnEnemyAt(d, null, 80f + (i % 6) * 100f, 200f + (i / 6) * 160f, telegraph = false)
            repeat(180) { g.update(1f / 60f) }
        }
        assertTrue(g.hp > 0f)
    }

    @Test fun glitchedEnemiesMixAttackPatterns() {
        val g = GameEngine(RunConfig(baseStats = RunStats().apply { maxHp = 1e9f; damage = 0f; orbCount = 0 }, seed = 8L, freeRevives = 0))
        g.debugJumpToLevel(40)
        for (e in g.enemies.items) e.active = false
        val daemon = g.spawnEnemyAt(Enemies.GLITCH_DAEMON, null, g.px, g.py - 250f, telegraph = false)!!
        daemon.hp = 1e9f; daemon.maxHp = 1e9f
        val volleySizes = HashSet<Int>()
        var before = 0
        repeat(60 * 40) {
            g.update(1f / 60f)
            val now = g.projectiles.items.count { it.active && !it.friendly }
            if (now > before) volleySizes += now - before
            before = now
        }
        assertTrue("glitched daemon used patterns of sizes $volleySizes", volleySizes.size >= 3)
    }

    @Test fun glitchedBossIsRareTougherAndNamed() {
        val rolls = (1..4000).count { LevelPlanner.rollGlitchedBoss(30, Random(it.toLong())) }
        assertTrue("glitched boss rate ${rolls / 40f}%", rolls in 300..700)
        assertTrue((1..500).none { LevelPlanner.rollGlitchedBoss(10, Random(it.toLong())) })

        fun bossHp(glitched: Boolean): Pair<Float, String> {
            val g = GameEngine(RunConfig(baseStats = RunStats().apply { maxHp = 1e9f }, seed = 4L, freeRevives = 0))
            val base = LevelPlanner.bossPlan(30, Random(1)).copy(boss = Bosses.forLevel(30))
            g.debugStartPlan(base.copy(glitchedBoss = glitched))
            return g.boss!!.maxHp to g.boss!!.boss!!.displayName
        }
        val (normalHp, normalName) = bossHp(false)
        val (glitchHp, glitchName) = bossHp(true)
        assertTrue(glitchHp > normalHp * 1.3f)
        assertEquals(Bosses.forLevel(30).name, normalName)
        assertTrue(glitchName.startsWith("*GLITCHED* "))
    }
}
