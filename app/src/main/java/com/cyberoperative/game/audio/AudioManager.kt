package com.cyberoperative.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.SystemClock
import android.util.Log
import com.cyberoperative.game.R
import com.cyberoperative.game.engine.GameSound
import java.io.File
import kotlin.random.Random

/** Music states (§46). */
enum class MusicState { NONE, MENU, COMBAT, BOSS, EVENT, GAME_OVER }

/**
 * Sound effects through a SoundPool (synthesized once into the cache dir)
 * and music through a single looping MediaPlayer. Every failure degrades to
 * silence; audio can never crash the game.
 */
class AudioManager(private val context: Context) {

    private var pool: SoundPool? = null
    private val ids = java.util.concurrent.ConcurrentHashMap<GameSound, Int>()
    private val lastPlayed = HashMap<GameSound, Long>()
    private var music: MediaPlayer? = null
    private var musicState = MusicState.NONE
    private var musicVolume = 0.7f
    private var sfxVolume = 0.8f
    var hapticsEnabled = true
    private var combatToggle = false
    private var paused = false

    fun initialize() {
        if (pool != null) return
        try {
            val p = SoundPool.Builder()
                .setMaxStreams(10)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                ).build()
            pool = p
            Thread {
                val dir = File(context.cacheDir, "sfx").apply { mkdirs() }
                for ((sound, recipe) in SoundBank.recipes) {
                    try {
                        val f = File(dir, "${sound.name.lowercase()}_v1.wav")
                        if (!f.exists()) f.writeBytes(ToneSynth.renderWav(recipe.duration, recipe.voices))
                        ids[sound] = p.load(f.absolutePath, 1)
                    } catch (t: Throwable) {
                        Log.w(TAG, "sfx $sound failed", t)
                    }
                }
            }.start()
        } catch (t: Throwable) {
            Log.w(TAG, "SoundPool unavailable; running silent", t)
        }
    }

    fun setVolumes(music: Float, sfx: Float) {
        musicVolume = music
        sfxVolume = sfx
        this.music?.setVolume(musicVolume, musicVolume)
    }

    fun play(sound: GameSound) {
        val p = pool ?: return
        val id = ids[sound] ?: return
        val recipe = SoundBank.recipes[sound] ?: return
        val now = SystemClock.uptimeMillis()
        val gap = SoundBank.minInterval[sound]
        if (gap != null) {
            val last = lastPlayed[sound] ?: 0L
            if (now - last < (gap * 1000).toLong()) return
        }
        lastPlayed[sound] = now
        val variants = SoundBank.pitchVariants[sound]
        val rate = variants?.get(Random.nextInt(variants.size)) ?: 1f
        val vol = (sfxVolume * recipe.gain).coerceIn(0f, 1f)
        if (vol <= 0f) return
        p.play(id, vol, vol, 1, 0, rate)
        when (sound) {
            GameSound.PLAYER_HURT -> vibrate(30)
            GameSound.BOSS_DEATH, GameSound.BOSS_SPAWN -> vibrate(120)
            GameSound.GAME_OVER -> vibrate(200)
            else -> {}
        }
    }

    fun playBootChime() {
        try {
            val mp = MediaPlayer.create(context, R.raw.boot_chime) ?: return
            mp.setVolume(sfxVolume, sfxVolume)
            mp.setOnCompletionListener { it.release() }
            mp.start()
        } catch (t: Throwable) {
            Log.w(TAG, "boot chime failed", t)
        }
    }

    fun setMusic(state: MusicState) {
        if (state == musicState) return
        musicState = state
        val res = when (state) {
            MusicState.NONE -> null
            MusicState.MENU -> R.raw.music_menu
            MusicState.COMBAT -> {
                combatToggle = !combatToggle
                if (combatToggle) R.raw.music_combat_a else R.raw.music_combat_b
            }
            MusicState.BOSS -> R.raw.music_boss
            MusicState.EVENT -> R.raw.music_event
            MusicState.GAME_OVER -> R.raw.music_gameover
        }
        stopMusic()
        if (res == null) return
        try {
            val mp = MediaPlayer.create(context, res) ?: return
            mp.isLooping = true
            mp.setVolume(musicVolume, musicVolume)
            if (!paused) mp.start()
            music = mp
        } catch (t: Throwable) {
            Log.w(TAG, "music failed", t)
        }
    }

    private fun stopMusic() {
        try {
            music?.stop()
            music?.release()
        } catch (_: Throwable) {
        }
        music = null
    }

    fun onPause() {
        paused = true
        try { music?.pause() } catch (_: Throwable) {}
    }

    fun onResume() {
        paused = false
        try { music?.start() } catch (_: Throwable) {}
    }

    private fun vibrate(ms: Long) {
        if (!hapticsEnabled) return
        try {
            val vib: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vib == null || !vib.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= 26) {
                vib.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(ms)
            }
        } catch (_: Throwable) {
        }
    }

    companion object {
        private const val TAG = "CyberOpAudio"
    }
}
