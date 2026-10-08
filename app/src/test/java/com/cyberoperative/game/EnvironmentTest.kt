package com.cyberoperative.game

import com.cyberoperative.game.data.Environments
import com.cyberoperative.game.data.FloorPattern
import com.cyberoperative.game.data.ObstacleKind
import com.cyberoperative.game.engine.ArenaGenerator
import com.cyberoperative.game.engine.Arena
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Owner, 2026-10-08: new themes every 3 levels, new walls, varied tiles and layouts. */
class EnvironmentTest {

    @Test fun themeChangesEveryThreeLevelsInAPerRunOrder() {
        assertTrue(Environments.all.size >= 12)
        val seed = 42L
        assertEquals(Environments.forLevel(1, seed), Environments.forLevel(3, seed))
        assertNotEquals(Environments.forLevel(3, seed), Environments.forLevel(4, seed))
        // All themes show up within one full cycle.
        val seen = (1..Environments.all.size * 3).map { Environments.forLevel(it, seed).id }.toSet()
        assertEquals(Environments.all.size, seen.size)
        // Different runs walk different orders.
        val orders = (1L..6L).map { s -> (1..12 step 3).map { Environments.forLevel(it, s).id } }.toSet()
        assertTrue(orders.size > 1)
        assertTrue(Environments.isNewTheme(1) && Environments.isNewTheme(4) && !Environments.isNewTheme(5))
    }

    @Test fun levelsVaryInsideATheme() {
        val styles = (1..300).map { Environments.styleFor(it, 7L) }
        assertEquals(FloorPattern.entries.toSet(), styles.map { it.pattern }.toSet())
        assertTrue(styles.map { it.tile }.toSet().size >= 3)
        assertTrue(styles.map { it.trenchEveryX }.toSet().size >= 3)
    }

    @Test fun newWallTypesAppearInPlayableRooms() {
        val kinds = HashSet<ObstacleKind>()
        for (seed in 1L..200L) {
            val t = ArenaGenerator.generate(20, Random(seed))
            assertTrue(ArenaGenerator.reachable(Arena(t)))
            t.obstacles.forEach { kinds += it.kind }
        }
        for (k in listOf(ObstacleKind.BLAST_WALL, ObstacleKind.COOLANT_PIPES, ObstacleKind.HOLO_WALL, ObstacleKind.REACTOR, ObstacleKind.ENERGY_BARRIER, ObstacleKind.ANTENNA_TOWER))
            assertTrue("$k never generated", k in kinds)
    }
}
