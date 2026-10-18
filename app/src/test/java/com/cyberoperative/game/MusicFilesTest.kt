package com.cyberoperative.game

import com.cyberoperative.game.audio.MusicLibrary
import com.cyberoperative.game.platform.musicResource
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 0.45.3: every soundtrack piece has an Android raw resource (the NEON tracks once played silently). */
class MusicFilesTest {
    @Test fun everyTrackResolvesToARawResource() {
        for (t in MusicLibrary.all) assertTrue(t.file, musicResource(t.file) != 0)
    }

    @Test fun everyTrackFileShipsForPc() {
        val raw = listOf(File("src/main/res/raw"), File("app/src/main/res/raw")).first { it.isDirectory }
        for (t in MusicLibrary.all) assertTrue(t.file, File(raw, "${t.file}.ogg").isFile)
    }
}
