package com.cyberoperative.game.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cyberoperative.game.R
import com.cyberoperative.game.engine.GameSound
import java.io.File
import kotlin.random.Random
import android.media.AudioManager as SystemAudio

/** Music states (§46). */
enum class MusicState { NONE, MENU, COMBAT, BOSS, EVENT, GAME_OVER }

/**
 * Sound effects through a SoundPool (synthesized once into the cache dir) and
 * the CyOps TD soundtrack through one MediaPlayer with a shuffle playlist.
 *
 * Background rule (owner: "music still plays when minimized" must never
 * happen): nothing may START audio unless the app is in the foreground and
 * holds audio focus. Every start path — a state change, a track ending, the
 * player's buttons, a focus regain — goes through [canPlay]. Leaving the app
 * pauses music and every SFX stream; losing focus (a call, another app) or
 * unplugging headphones pauses too. Every failure degrades to silence.
 */
class AudioManager(private val context: Context) {

    private var pool: SoundPool? = null
    private val ids = java.util.concurrent.ConcurrentHashMap<GameSound, Int>()
    private val lastPlayed = HashMap<GameSound, Long>()
    private var musicVolume = 0.7f
    private var sfxVolume = 0.8f
    var hapticsEnabled = true

    // --- Music state, observable by the pause-screen player -------------
    private var player: MediaPlayer? = null
    private var state = MusicState.NONE
    private val bag = ShuffleBag(Random(System.nanoTime()))

    var currentTrack by mutableStateOf<MusicTrack?>(null)
        private set
    /** The player's own pause (the play/pause button), independent of the app lifecycle. */
    var userPaused by mutableStateOf(false)
        private set
    var shuffle by mutableStateOf(true)
        private set
    /** A track the player picked by hand; it keeps playing across music-state changes. */
    var pinned by mutableStateOf<MusicTrack?>(null)
        private set

    private var foreground = true
    private var hasFocus = false
    private val system = context.getSystemService(Context.AUDIO_SERVICE) as SystemAudio

    private val focusListener = SystemAudio.OnAudioFocusChangeListener { change ->
        when (change) {
            SystemAudio.AUDIOFOCUS_GAIN -> { hasFocus = true; player?.setVolume(musicVolume, musicVolume); resumeIfAllowed() }
            SystemAudio.AUDIOFOCUS_LOSS -> { hasFocus = false; pauseMusicOnly() }
            SystemAudio.AUDIOFOCUS_LOSS_TRANSIENT -> pauseMusicOnly()
            SystemAudio.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.setVolume(musicVolume * 0.25f, musicVolume * 0.25f)
        }
    }
    private val focusRequest: AudioFocusRequest? = if (Build.VERSION.SDK_INT >= 26) {
        AudioFocusRequest.Builder(SystemAudio.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener(focusListener)
            .build()
    } else null

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            // Headphones unplugged: never blast music out of the speaker.
            if (i?.action == SystemAudio.ACTION_AUDIO_BECOMING_NOISY) pauseByUser(true)
        }
    }

    fun initialize() {
        if (pool != null) return
        try {
            context.registerReceiver(noisyReceiver, IntentFilter(SystemAudio.ACTION_AUDIO_BECOMING_NOISY))
        } catch (_: Throwable) {
        }
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
        player?.setVolume(musicVolume, musicVolume)
    }

    // ======================================================================
    // Sound effects
    // ======================================================================

    fun play(sound: GameSound) {
        if (!foreground) return
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
            GameSound.BOSS_GROWL -> vibrate(260)
            GameSound.GAME_OVER -> vibrate(200)
            else -> {}
        }
    }

    fun playBootChime() {
        if (!foreground) return
        try {
            val mp = MediaPlayer.create(context, R.raw.boot_chime) ?: return
            mp.setVolume(sfxVolume, sfxVolume)
            mp.setOnCompletionListener { it.release() }
            mp.start()
        } catch (t: Throwable) {
            Log.w(TAG, "boot chime failed", t)
        }
    }

    // ======================================================================
    // Music
    // ======================================================================

    private fun canPlay(): Boolean = foreground && !userPaused && state != MusicState.NONE

    private fun requestFocus(): Boolean {
        if (hasFocus) return true
        val r = try {
            if (Build.VERSION.SDK_INT >= 26 && focusRequest != null) system.requestAudioFocus(focusRequest)
            else @Suppress("DEPRECATION") system.requestAudioFocus(focusListener, SystemAudio.STREAM_MUSIC, SystemAudio.AUDIOFOCUS_GAIN)
        } catch (_: Throwable) {
            SystemAudio.AUDIOFOCUS_REQUEST_FAILED
        }
        hasFocus = r == SystemAudio.AUDIOFOCUS_REQUEST_GRANTED
        return hasFocus
    }

    private fun abandonFocus() {
        if (!hasFocus) return
        try {
            if (Build.VERSION.SDK_INT >= 26 && focusRequest != null) system.abandonAudioFocusRequest(focusRequest)
            else @Suppress("DEPRECATION") system.abandonAudioFocus(focusListener)
        } catch (_: Throwable) {
        }
        hasFocus = false
    }

    /** The game says which kind of music fits now (menu, combat, boss…). */
    fun setMusic(next: MusicState) {
        if (next == state) return
        state = next
        // Same track carrying on into a new state still changes tempo (e.g. a boss
        // arrives, or falls). Re-checked every frame by [ensureSpeed] until the
        // player reports the new rate: one call was not always honoured, which
        // left the 1.5x boss tempo running until the song ended.
        speedDirty = true
        ensureSpeed()
        if (next == MusicState.NONE) { stopPlayer(); return }
        // A hand-picked track keeps playing through state changes.
        val keep = pinned
        if (keep != null && currentTrack == keep && player != null) return
        val cur = currentTrack
        if (cur != null && player != null && cur in MusicLibrary.poolFor(next)) return
        playTrack(if (keep != null) keep else bag.next(MusicLibrary.poolFor(next), cur))
    }

    private fun playTrack(track: MusicTrack?) {
        stopPlayer()
        currentTrack = track
        if (track == null) return
        try {
            val mp = MediaPlayer.create(context, track.res) ?: return
            mp.setVolume(musicVolume, musicVolume)
            mp.isLooping = !shuffle && pinned != null
            mp.setOnCompletionListener { onTrackFinished() }
            player = mp
            if (canPlay() && requestFocus()) startAtSpeed(mp)
        } catch (t: Throwable) {
            Log.w(TAG, "music failed", t)
        }
    }

    /**
     * Owner, 2026-10-07: the soundtrack runs faster in a fight — 1.25x in
     * levels, 1.5x on boss levels, normal on menus. Time-stretched at playback
     * (pitch unchanged), so no extra audio files ship.
     */

    private fun startAtSpeed(mp: MediaPlayer) {
        mp.start()
        // Applied after start(): setting a non-zero speed on a paused player
        // would itself start playback, which must only happen via canPlay().
        applySpeed(mp)
        speedDirty = true
    }

    private var speedDirty = false
    private var lastSpeedCheck = 0L

    /** Called every frame by the game; cheap unless a tempo change is pending. */
    fun ensureSpeed() {
        if (!speedDirty) return
        val now = System.nanoTime()
        if (now - lastSpeedCheck < 150_000_000L) return
        lastSpeedCheck = now
        val mp = player ?: run { speedDirty = false; return }
        try {
            // Only while playing: setting a speed on a paused player starts it.
            if (!mp.isPlaying) return
            val want = speedFor(state)
            if (kotlin.math.abs(mp.playbackParams.speed - want) < 0.01f) { speedDirty = false; return }
            applySpeed(mp)
        } catch (t: Throwable) {
            speedDirty = false
        }
    }

    private fun applySpeed(mp: MediaPlayer) {
        try {
            mp.playbackParams = mp.playbackParams.setSpeed(speedFor(state)).setPitch(1f)
        } catch (t: Throwable) {
            Log.w(TAG, "playback speed unsupported", t)
        }
    }

    private fun onTrackFinished() {
        // Next in the shuffle (or the next track in list order when shuffle is off).
        val nextTrack = if (shuffle) bag.next(poolNow(), currentTrack) else nextInOrder(currentTrack)
        if (pinned != null) pinned = nextTrack
        playTrack(nextTrack)
    }

    private fun poolNow(): List<MusicTrack> = if (pinned != null) MusicLibrary.all else MusicLibrary.poolFor(state)

    private fun nextInOrder(t: MusicTrack?): MusicTrack {
        val list = MusicLibrary.all
        val i = list.indexOf(t)
        return list[(i + 1).mod(list.size)]
    }

    private fun stopPlayer() {
        try {
            player?.setOnCompletionListener(null)
            player?.stop()
            player?.release()
        } catch (_: Throwable) {
        }
        player = null
    }

    private fun pauseMusicOnly() {
        try { if (player?.isPlaying == true) player?.pause() } catch (_: Throwable) {}
    }

    private fun resumeIfAllowed() {
        val mp = player
        if (mp == null) {
            if (canPlay() && state != MusicState.NONE) playTrack(pinned ?: bag.next(MusicLibrary.poolFor(state), null))
            return
        }
        if (canPlay() && requestFocus()) {
            try { if (!mp.isPlaying) startAtSpeed(mp) } catch (_: Throwable) {}
        }
    }

    // --- Player controls (pause-screen music player) -------------------

    fun selectTrack(track: MusicTrack) {
        pinned = track
        userPaused = false
        playTrack(track)
    }

    fun nextTrack() {
        val n = if (shuffle) bag.next(poolNow(), currentTrack) else nextInOrder(currentTrack)
        if (pinned != null) pinned = n
        userPaused = false
        playTrack(n)
    }

    fun previousTrack() {
        val p = bag.previous(currentTrack) ?: MusicLibrary.all.let { l -> l[(l.indexOf(currentTrack) - 1).mod(l.size)] }
        if (pinned != null) pinned = p
        userPaused = false
        playTrack(p)
    }

    fun pauseByUser(value: Boolean) {
        userPaused = value
        if (value) pauseMusicOnly() else resumeIfAllowed()
    }

    fun toggleShuffle() {
        shuffle = !shuffle
        player?.isLooping = !shuffle && pinned != null
    }

    /** Back to automatic music (menu / combat / boss rotations). */
    fun clearPin() {
        pinned = null
        val cur = currentTrack
        if (cur == null || cur !in MusicLibrary.poolFor(state)) playTrack(bag.next(MusicLibrary.poolFor(state), cur))
    }

    // --- Lifecycle -------------------------------------------------------

    /** Activity stopped/paused: silence everything, music and effects. */
    fun onBackground() {
        foreground = false
        pauseMusicOnly()
        try { pool?.autoPause() } catch (_: Throwable) {}
        abandonFocus()
    }

    fun onForeground() {
        foreground = true
        try { pool?.autoResume() } catch (_: Throwable) {}
        resumeIfAllowed()
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

        fun speedFor(s: MusicState): Float = when (s) {
            MusicState.COMBAT, MusicState.EVENT -> 1.25f
            MusicState.BOSS -> 1.5f
            else -> 1f
        }

    }
}
