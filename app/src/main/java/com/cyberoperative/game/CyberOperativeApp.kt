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
    /** Idle Runs (0.37.0): the saved run, its worker, the home-screen widget. */
    lateinit var idle: com.cyberoperative.game.widget.AndroidIdleHost
        private set
    /** Co-op server (Firebase), or an offline stand-in when this build has no Firebase project. */
    val coop: com.cyberoperative.game.net.CoopBackend by lazy { com.cyberoperative.game.net.FirebaseCoopBackend.create(this) }

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        // Companion incubator: boot-count clock and network time (0.38.0).
        com.cyberoperative.game.platform.PlatformClock.context = this
        com.cyberoperative.game.meta.TrustedClock.refresh()
        save = SaveRepository(this)
        audio = AudioManager(this)
        val s = save.current.settings
        audio.setVolumes(s.musicVolume, s.sfxVolume)
        audio.hapticsEnabled = s.haptics
        audio.initialize()
        idle = com.cyberoperative.game.widget.AndroidIdleHost(this)
        com.cyberoperative.game.idle.IdleRuns.host = idle
        com.cyberoperative.game.meta.PetReminders.schedule = { at, name -> com.cyberoperative.game.widget.PetHatchWorker.schedule(this, at, name) }
    }
}
