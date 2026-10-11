package com.cyberoperative.game

import com.cyberoperative.game.core.MathUtil
import com.cyberoperative.game.engine.AllyConfig
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.LootKind
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.engine.RunStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Boss loot sprays out one piece at a time; co-op drops belong to one player (owner, 2026-10-11). */
class BossLootSpewTest {

    private fun stats() = RunStats().apply { maxHp = 1e6f }

    private fun step(g: GameEngine, seconds: Float) {
        var t = 0f
        while (t < seconds) { g.update(1f / 60f); t += 1f / 60f }
    }

    private fun killBoss(g: GameEngine): Pair<Float, Float> {
        g.debugJumpToLevel(10)
        step(g, 4f)
        val b = g.boss!!
        // Park everyone far from the boss so nothing is picked up mid-spray.
        for (o in g.operatives) { o.px = 60f; o.py = 60f }
        val at = b.x to b.y
        g.killEnemy(b)
        return at
    }

    @Test fun bossLootSpraysOutOneEveryTenthOfASecond() {
        val g = GameEngine(RunConfig(baseStats = stats(), seed = 7L, freeRevives = 0, consumables = true))
        val (bx, by) = killBoss(g)
        assertEquals("nothing lands in a stack on the kill", 0, g.loot.size)
        // One piece at a time: never two in the same frame.
        val landed = ArrayList<Int>()
        var last = 0
        for (frame in 0 until 60 * 8) {
            g.update(1f / 60f)
            val n = g.loot.size
            assertTrue("one at a time", n - last <= 1)
            if (n > last) landed += frame
            last = n
        }
        assertTrue("several pieces sprayed: $landed", landed.size >= 4)
        // Never faster than SPEW_INTERVAL (the kill's slow motion stretches it), all out within 2 s.
        val gaps = landed.zipWithNext { a, b -> b - a }
        assertTrue("a tenth of a second apart: $gaps", gaps.all { it >= 5 })
        assertTrue("sprayed quickly: $landed", landed.last() - landed.first() < 120)
        assertTrue("3+ chips sprayed", g.loot.count { it.kind == LootKind.UPGRADE } >= 3)
        // Each piece slid out from the boss, so they are spread out rather than piled up.
        val chips = g.loot.filter { it.kind == LootKind.UPGRADE }
        assertTrue(chips.all { !it.flying })
        assertTrue(chips.any { MathUtil.dist(it.x, it.y, bx, by) > GameEngine.SPEW_MIN * 0.5f })
        val spread = chips.flatMap { a -> chips.map { b -> MathUtil.dist(a.x, a.y, b.x, b.y) } }.max()
        assertTrue("spread $spread", spread > 60f)
    }

    @Test fun coopPlayersEachGetTheirOwnLootAndCantTakeTheOthers() {
        val g = GameEngine(RunConfig(baseStats = stats(), seed = 7L, freeRevives = 0, consumables = true, ally = AllyConfig(stats())))
        killBoss(g)
        step(g, 8f)
        val chips = g.loot.filter { it.kind == LootKind.UPGRADE }
        val mine = chips.filter { it.owner == 0 }
        val theirs = chips.filter { it.owner == 1 }
        assertTrue(mine.size >= 3)
        assertEquals(mine.size, theirs.size)
        assertTrue(chips.all { it.owner == 0 || it.owner == 1 })
        // The partner stands on the host's chip: it stays on the floor.
        val target = mine.first()
        val partner = g.operatives[1]
        val before = partner.build.level(target.upgradeId!!)
        repeat(30) { partner.px = target.x; partner.py = target.y; g.update(1f / 60f) }
        assertTrue("partner can't take the host's chip", target in g.loot)
        assertEquals(before, partner.build.level(target.upgradeId!!))
        // The host can.
        val host = g.operatives[0]
        repeat(30) { host.px = target.x; host.py = target.y; g.update(1f / 60f) }
        assertTrue("owner picks it up", target !in g.loot || target.declined)
    }

    @Test fun ownershipSurvivesTheCoopSnapshot() {
        val host = GameEngine(RunConfig(baseStats = stats(), seed = 7L, freeRevives = 0, consumables = true, ally = AllyConfig(stats())))
        killBoss(host)
        step(host, 3f)
        val w = host.captureWorld(1, emptyList())
        val bytes = com.cyberoperative.game.engine.CoopCodec.encodeWorld(w)
        val back = com.cyberoperative.game.engine.CoopCodec.decodeWorld(bytes)!!
        assertEquals(host.loot.map { it.owner }, back.loot.map { it[4].toInt() })
    }
}
