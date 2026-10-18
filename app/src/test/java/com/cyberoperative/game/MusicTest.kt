package com.cyberoperative.game

import com.cyberoperative.game.audio.MusicLibrary
import com.cyberoperative.game.audio.MusicState
import com.cyberoperative.game.audio.ShuffleBag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MusicTest {

    @Test fun wholeCyOpsSoundtrackIsIncluded() {
        // 28 CyOps tracks + 10 INFERNO tracks (0.32.0).
        // 38 CyOps tracks + the owner's 5 NEON tracks (0.37.5) + 10 ARCADE tracks (0.54.0).
        assertEquals(53, MusicLibrary.all.size)
        assertEquals(53, MusicLibrary.all.map { it.id }.toSet().size)
        assertEquals(43, MusicLibrary.poolFor(MusicState.COMBAT).size)
        assertTrue(MusicLibrary.poolFor(MusicState.MENU).isNotEmpty())
        assertTrue(MusicLibrary.poolFor(MusicState.BOSS).isNotEmpty())
    }

    @Test fun infernoRunsPlayOnlyTheInfernoTracksAndBossesStayAtSpeed() {
        try {
            MusicLibrary.inferno = true
            // The 8 INFERNO tracks plus the 5 NEON (0.37.5) and 10 ARCADE (0.54.0) tracks, which play on any level.
            assertEquals(23, MusicLibrary.poolFor(MusicState.COMBAT).size)
            assertTrue(MusicLibrary.poolFor(MusicState.COMBAT).all { it.id.startsWith("inferno") || it.fixedTempo })
            assertEquals(setOf("inferno_boss_1", "inferno_boss_2"), MusicLibrary.poolFor(MusicState.BOSS).filter { !it.id.startsWith("arcade_") }.map { it.id }.toSet())
        } finally { MusicLibrary.inferno = false }
        val m = com.cyberoperative.game.audio.BossMusicProfiles.inferno(com.cyberoperative.game.data.Bosses.byId("exfil")!!)
        for (phase in 0..2) assertEquals(1f, m.speed(phase), 0f)
        assertTrue(MusicLibrary.poolFor(MusicState.COMBAT).none { it.id.startsWith("inferno") })
        MusicLibrary.infernoUnlocked = false
        assertEquals(43, MusicLibrary.listed.size)
    }

    @Test fun bossPoolHasLevel10AndBoss2Variants() {
        val ids = MusicLibrary.poolFor(MusicState.BOSS).map { it.id }.filter { !it.startsWith("arcade_") }.toSet()
        assertEquals(setOf("level10_1", "level10_2", "boss2_1", "boss2_2", "boss2_remix_1", "boss2_remix_2"), ids)
    }

    @Test fun shufflePlaysEveryTrackBeforeRepeating() {
        val bag = ShuffleBag(Random(3))
        val pool = MusicLibrary.all
        var last = bag.next(pool, null)
        repeat(20) { round ->
            val seen = HashSet<String>()
            seen += last!!.id
            repeat(pool.size - 1) {
                val n = bag.next(pool, last)!!
                assertTrue("repeat within a cycle (round $round)", seen.add(n.id))
                last = n
            }
            val first = bag.next(pool, last)!!
            assertNotEquals("same track twice in a row across reshuffle", last!!.id, first.id)
            last = first
        }
    }

    @Test fun musicTempoByState() {
        val am = com.cyberoperative.game.audio.AudioManager
        assertEquals(1f, am.speedFor(MusicState.MENU), 0f)
        assertEquals(1.25f, am.speedFor(MusicState.COMBAT), 0f)
        assertEquals(1.5f, am.speedFor(MusicState.BOSS), 0f)
        // Slow rounds only change normal combat music.
        assertEquals(0.75f, am.speedFor(MusicState.COMBAT, slowRound = true), 0f)
        assertEquals(1.5f, am.speedFor(MusicState.BOSS, slowRound = true), 0f)
        assertEquals(1.25f, am.speedFor(MusicState.EVENT, slowRound = true), 0f)
    }
}
