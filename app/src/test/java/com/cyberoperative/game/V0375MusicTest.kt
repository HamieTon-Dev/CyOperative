package com.cyberoperative.game

import com.cyberoperative.game.audio.AudioManager
import com.cyberoperative.game.audio.MusicLibrary
import com.cyberoperative.game.audio.MusicState
import com.cyberoperative.game.audio.TrackGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The owner's NEON tracks (0.37.5): any level, never a boss round, never altered, small files. */
class V0375MusicTest {
    private val neon = MusicLibrary.all.filter { it.group == TrackGroup.NEON }

    @Test fun neonPlaysOnEveryLevelButNeverOnABoss() {
        assertEquals(5, neon.size)
        assertTrue(neon.all { it.plain })
        try {
            for (hell in listOf(false, true)) {
                MusicLibrary.inferno = hell
                assertTrue(MusicLibrary.poolFor(MusicState.COMBAT).containsAll(neon))
                assertTrue(MusicLibrary.poolFor(MusicState.EVENT).containsAll(neon))
                assertFalse(MusicLibrary.poolFor(MusicState.BOSS).any { it.plain })
                assertFalse(MusicLibrary.poolFor(MusicState.MENU).any { it.plain })
            }
        } finally { MusicLibrary.inferno = false }
        val ids = neon.map { it.id }.toSet()
        assertFalse(com.cyberoperative.game.audio.MusicTier.entries.any { t -> t.tracks.any { it in ids } })
        for (b in com.cyberoperative.game.data.Bosses.roster + com.cyberoperative.game.data.InfernoBosses.all) {
            assertFalse(com.cyberoperative.game.audio.BossMusicProfiles.forBoss(b).track in ids)
        }
    }

    @Test fun neonFilesAreSmallOggs() {
        for (t in neon) {
            val f = File("src/main/res/raw/${t.file}.ogg")
            assertTrue("${t.file} exists", f.isFile)
            assertTrue("${t.file} is ${f.length() / 1024} KB", f.length() < 3_000_000)
            assertEquals("OggS", f.inputStream().use { String(it.readNBytes(4)) })
        }
        // Normal tracks are still sped up in a fight (the NEON ones are exempt in AudioManager.speedNow).
        assertEquals(1.25f, AudioManager.speedFor(MusicState.COMBAT))
    }
}
