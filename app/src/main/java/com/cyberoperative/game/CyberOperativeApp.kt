package com.cyberoperative.game

import android.app.Application
import com.cyberoperative.game.audio.AudioManager
import com.cyberoperative.game.save.SaveRepository

/** Process-wide singletons: the save and the audio engine. */
class CyberOperativeApp : Application() {
    lateinit var save: SaveRepository
        private set
    lateinit var audio: AudioManager
        private set

    override fun onCreate() {
        super.onCreate()
        save = SaveRepository(this)
        audio = AudioManager(this)
        val s = save.current.settings
        audio.setVolumes(s.musicVolume, s.sfxVolume)
        audio.hapticsEnabled = s.haptics
        audio.initialize()
    }
}
