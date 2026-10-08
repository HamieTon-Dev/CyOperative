package com.cyberoperative.game

import com.cyberoperative.game.data.AiKind
import com.cyberoperative.game.data.Enemies
import com.cyberoperative.game.engine.ArenaGenerator
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.LevelKind
import com.cyberoperative.game.engine.LevelPlan
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.engine.RunStats
import com.cyberoperative.game.core.MathUtil
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Owner, 2026-10-08: "enemies constantly run straight into walls and get
 * stuck". Chasers placed behind cover in many generated rooms must find
 * their way to a stationary operative.
 */
class PathingTest {

    @Test fun chasersReachThePlayerAroundCover() {
        val chaser = Enemies.all.first { it.ai == AiKind.CHASER }
        var total = 0
        var arrived = 0
        var seconds = 0f
        for (seed in 1L..24L) {
            val arena = ArenaGenerator.generate(25, Random(seed))
            val g = GameEngine(
                RunConfig(baseStats = RunStats().apply { maxHp = 1e9f; damage = 0f; orbCount = 0; range = 1f }, seed = seed, freeRevives = 0)
            )
            g.debugStartPlan(LevelPlan(25, LevelKind.NORMAL, arena, listOf(emptyList(), emptyList(), emptyList())))
            for (e in g.enemies.items) e.active = false
            // Spawn chasers whose straight line to the player is blocked by cover.
            val rng = Random(seed * 31)
            val placed = ArrayList<com.cyberoperative.game.engine.Enemy>()
            var tries = 0
            while (placed.size < 6 && tries++ < 400) {
                val x = 40f + rng.nextFloat() * (g.arena.width - 80f)
                val y = 200f + rng.nextFloat() * (g.arena.height * 0.5f)
                if (!g.arena.isFree(x, y, chaser.radius + 4f)) continue
                if (g.arena.lineOfSight(x, y, g.px, g.py, chaser.radius)) continue
                placed += g.spawnEnemyAt(chaser, null, x, y, telegraph = false) ?: continue
            }
            // Budget: 1.5x the straight-line travel time plus 1.5 s to get round cover.
            val budget = placed.associate { it.uid to 1.5f + 1.5f * MathUtil.dist(it.x, it.y, g.px, g.py) / it.speed }
            val reachedAt = HashMap<Int, Float>()
            var t = 0f
            repeat(60 * 20) {
                g.update(1f / 60f)
                t += 1f / 60f
                for (e in placed) if (e.uid !in reachedAt && MathUtil.dist(e.x, e.y, g.px, g.py) < 90f) reachedAt[e.uid] = t
            }
            total += placed.size
            arrived += placed.count { (reachedAt[it.uid] ?: 99f) <= budget.getValue(it.uid) }
            seconds += placed.sumOf { ((reachedAt[it.uid] ?: 20f) / budget.getValue(it.uid)).toDouble() }.toFloat()
        }
        val rate = arrived.toFloat() / total
        println("pathing: $arrived / $total blocked chasers reached the player in time (${(rate * 100).toInt()}%), avg ${"%.2f".format(seconds / total)} of budget")
        assertTrue("only ${(rate * 100).toInt()}% of blocked chasers reached the player in time", rate >= 0.95f)
    }
}
