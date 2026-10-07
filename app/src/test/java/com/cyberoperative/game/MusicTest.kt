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
        assertEquals(24, MusicLibrary.all.size)
        assertEquals(24, MusicLibrary.all.map { it.id }.toSet().size)
        assertEquals(24, MusicLibrary.poolFor(MusicState.COMBAT).size)
        assertTrue(MusicLibrary.poolFor(MusicState.MENU).isNotEmpty())
        assertTrue(MusicLibrary.poolFor(MusicState.BOSS).isNotEmpty())
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
}
