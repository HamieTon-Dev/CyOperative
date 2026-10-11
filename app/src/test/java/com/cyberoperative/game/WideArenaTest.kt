package com.cyberoperative.game

import com.cyberoperative.game.data.ArenaShape
import com.cyberoperative.game.data.ArenaTemplate
import com.cyberoperative.game.data.Arenas
import com.cyberoperative.game.engine.Arena
import com.cyberoperative.game.engine.ArenaGenerator
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.engine.RunStats
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/** The PC (Steam) build's landscape rooms: wide, zoomed out, always playable. */
class WideArenaTest {
    @Before fun wide() { ArenaShape.wide = true; ArenaShape.pcRules = true }
    @After fun portrait() { ArenaShape.wide = false; ArenaShape.pcRules = false }

    private fun assertPlayable(t: ArenaTemplate) {
        assertEquals(t.id, ArenaShape.WIDE_WIDTH, t.width)
        val a = Arena(t)
        for (o in a.obstacles) {
            val r = o.rect
            assertTrue("${t.id}: ${o.kind} inside the floor", r.left >= 0f && r.right <= t.width && r.top >= 0f && r.bottom <= t.height)
        }
        assertTrue("${t.id}: spawn clear", a.isFree(a.spawnX, a.spawnY, 30f))
        assertTrue("${t.id}: gate clear", a.isFree(a.portalX, a.portalY, 40f))
        assertTrue("${t.id}: gate reachable", ArenaGenerator.reachable(a))
    }

    @Test
    fun generatedRoomsAreWideAndPlayable() {
        val rng = Random(42)
        for (level in listOf(1, 5, 12, 30, 80, 150)) repeat(8) {
            val t = ArenaGenerator.generate(level, rng)
            assertTrue("height ${t.height}", t.height in ArenaShape.WIDE_MIN_HEIGHT..ArenaShape.WIDE_MAX_HEIGHT)
            assertTrue("a wide room has more cover (${t.obstacles.size})", t.obstacles.size >= 3)
            assertPlayable(t)
        }
    }

    @Test
    fun handMadeAndBossRoomsWiden() {
        for (t in Arenas.templates) {
            assertPlayable(t)
            assertPlayable(Arenas.mirrored(t))
        }
        assertPlayable(Arenas.bossArena)
        assertPlayable(Arenas.mirrored(Arenas.bossArena))
        assertPlayable(ArenaGenerator.shopRoom(Random(3)))
    }

    @Test
    fun aWideBossFightRuns() {
        val g = GameEngine(RunConfig(baseStats = RunStats(), seed = 11L))
        g.debugJumpToLevel(10)
        repeat(60 * 12) { g.update(1f / 60f) }
        assertEquals(ArenaShape.WIDE_WIDTH, g.arena.width)
        val b = g.boss
        assertNotNull("boss spawned", b)
        assertTrue("boss inside the room", b!!.x in 0f..g.arena.width && b.y in 0f..g.arena.height)
    }

    /** Clears level 2 (no boss, no event forced) and returns the engine at the open exits. */
    private fun clearedRoom(seed: Long): GameEngine {
        val g = GameEngine(RunConfig(baseStats = RunStats(), seed = seed, powerGovernor = false))
        repeat(60 * 40) {
            for (x in g.enemies.items) if (x.active && x.boss == null) g.killEnemy(x)
            g.update(1f / 60f)
            if (g.phase == com.cyberoperative.game.engine.Phase.UPGRADE && g.offer.isNotEmpty()) g.chooseUpgrade(0)
            if (g.phase == com.cyberoperative.game.engine.Phase.PORTAL) return g
        }
        return g
    }

    @Test
    fun clearedRoomsOpenOneToFourExits() {
        val counts = IntArray(4)
        for (seed in 1L..24L) {
            val g = clearedRoom(seed)
            assertEquals("seed $seed reaches the exits", com.cyberoperative.game.engine.Phase.PORTAL, g.phase)
            assertTrue(g.exitDoors.size <= 3)
            counts[g.exitDoors.size]++
            for (d in g.exitDoors) {
                when (d.side) {
                    0 -> assertTrue("far-wall door clear", g.arena.isFree(d.x, g.arena.portalY + 40f, 20f))
                    1 -> assertTrue("left door clear", g.arena.isFree(30f, d.y, 20f))
                    else -> assertTrue("right door clear", g.arena.isFree(g.arena.width - 30f, d.y, 20f))
                }
                if (g.shopGateOpen) assertTrue("never on the shop's wall", d.side == 0 || (d.side == 2) != g.shopGateRight)
            }
        }
        assertTrue("several rooms get extra exits (${counts.toList()})", counts[1] + counts[2] + counts[3] >= 8)
    }

    @Test
    fun anExtraExitLeadsOn() {
        val g = (1L..40L).asSequence().map { clearedRoom(it) }.first { it.exitDoors.isNotEmpty() && !it.shopGateOpen }
        val d = g.exitDoors.first()
        val o = g.operatives[0]
        when (d.side) {
            0 -> { o.px = d.x; o.py = g.arena.portalY + 20f }
            1 -> { o.px = 16f; o.py = d.y }
            else -> { o.px = g.arena.width - 16f; o.py = d.y }
        }
        val level = g.level
        repeat(120) { g.update(1f / 60f) }
        assertEquals("through the door to the next level", level + 1, g.level)
    }
}
