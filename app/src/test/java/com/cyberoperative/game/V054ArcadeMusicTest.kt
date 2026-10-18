package com.cyberoperative.game

import com.cyberoperative.game.audio.MusicLibrary
import com.cyberoperative.game.audio.MusicState
import com.cyberoperative.game.audio.TrackGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner, 0.54.0: 10 ARCADE tracks, top of the playlist, everywhere, any difficulty, reverb only. */
class V054ArcadeMusicTest {
    @Test fun arcadeTracksTopThePlaylistAndPlayEverywhere() {
        val arcade = MusicLibrary.arcade
        assertEquals(10, arcade.size)
        assertEquals(arcade, MusicLibrary.listed.take(10))
        assertEquals(TrackGroup.MENU, MusicLibrary.listed[10].group)
        try {
            for (inf in listOf(false, true)) {
                MusicLibrary.inferno = inf
                for (s in listOf(MusicState.MENU, MusicState.COMBAT, MusicState.EVENT, MusicState.BOSS))
                    assertTrue("$s inferno=$inf", MusicLibrary.poolFor(s).containsAll(arcade))
            }
        } finally { MusicLibrary.inferno = false }
        for (t in arcade) {
            assertTrue(t.fixedTempo)
            assertTrue(t.noEq)
            assertFalse("reverb allowed and bosses allowed", t.plain)
        }
    }
}
