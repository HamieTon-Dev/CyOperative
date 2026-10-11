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
    @Before fun wide() { ArenaShape.wide = true }
    @After fun portrait() { ArenaShape.wide = false }

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
}
