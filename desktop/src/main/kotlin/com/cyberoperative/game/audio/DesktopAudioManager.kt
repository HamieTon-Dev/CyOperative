package com.cyberoperative.game.audio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cyberoperative.game.engine.GameSound
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine
import javax.swing.SwingUtilities
import kotlin.math.log10
import kotlin.random.Random

/** Music states (§46). */
enum class MusicState { NONE, MENU, COMBAT, BOSS, EVENT, GAME_OVER }

/**
 * PC audio with the same API as the Android AudioManager.
 *
 * Sound effects are the same synthesized recipes ([SoundBank] / [ToneSynth]), preloaded into
 * small pools of Java Sound clips. The soundtrack streams the same Ogg Vorbis files (vorbisspi)
 * on a background thread, with the same rotations (menu / combat / boss signature tracks),
 * shuffle bag and pause-screen player controls. Per-boss EQ, reverb and tempo stretch are
 * Android-only effects; on PC each boss still opens on its signature track.
 * Every failure degrades to silence.
 */
class AudioManager {

    private val clips = java.util.concurrent.ConcurrentHashMap<GameSound, List<Clip>>()
    private val lastPlayed = HashMap<GameSound, Long>()
    private var musicVolume = 0.7f
    private var sfxVolume = 0.8f
    var hapticsEnabled = true

    private var player: MusicStream? = null
    private var state = MusicState.NONE
    private val bag = ShuffleBag(Random(System.nanoTime()))
    private var foreground = true
    private var bossMusic: BossMusic? = null
    private var bossPhase = 0
    private var bossGlitched = false

    var currentTrack by mutableStateOf<MusicTrack?>(null)
        private set
    var userPaused by mutableStateOf(false)
        private set
    var shuffle by mutableStateOf(true)
        private set
    var pinned by mutableStateOf<MusicTrack?>(null)
        private set

    fun initialize() {
        if (clips.isNotEmpty()) return
        Thread {
            for ((sound, recipe) in SoundBank.recipes) {
                try {
                    val wav = ToneSynth.renderWav(recipe.duration, recipe.voices)
                    val pool = (0 until CLIPS_PER_SOUND).map {
                        AudioSystem.getClip().apply { open(AudioSystem.getAudioInputStream(ByteArrayInputStream(wav))) }
                    }
                    clips[sound] = pool
                } catch (_: Throwable) {
                }
            }
            // Recorded effects (owner's drop sounds) straight from the classpath.
            for (sound in SoundBank.files.keys) {
                try {
                    val wav = SoundBank.fileBytes(sound) ?: continue
                    clips[sound] = (0 until CLIPS_PER_SOUND).map {
                        AudioSystem.getClip().apply { open(AudioSystem.getAudioInputStream(ByteArrayInputStream(wav))) }
                    }
                } catch (_: Throwable) {
                }
            }
        }.apply { isDaemon = true; name = "sfx-load" }.start()
    }

    fun setVolumes(music: Float, sfx: Float) {
        musicVolume = music
        sfxVolume = sfx
        player?.setVolume(musicGain())
    }

    // --- Sound effects -----------------------------------------------------------------

    fun play(sound: GameSound) {
        if (!foreground) return
        val pool = clips[sound] ?: return
        val now = System.nanoTime() / 1_000_000L
        val gap = SoundBank.minInterval[sound]
        if (gap != null) {
            val last = lastPlayed[sound] ?: 0L
            if (now - last < (gap * 1000).toLong()) return
        }
        lastPlayed[sound] = now
        val vol = (sfxVolume * SoundBank.gain(sound)).coerceIn(0f, 1f)
        if (vol <= 0f) return
        val clip = pool.firstOrNull { !it.isRunning } ?: pool[Random.nextInt(pool.size)]
        try {
            clip.stop()
            setGain(clip, vol)
            clip.framePosition = 0
            clip.start()
        } catch (_: Throwable) {
        }
    }

    fun playBootChime() {
        if (!foreground) return
        Thread {
            try {
                val stream = javaClass.classLoader.getResourceAsStream("music/boot_chime.wav") ?: return@Thread
                val clip = AudioSystem.getClip()
                clip.open(AudioSystem.getAudioInputStream(BufferedInputStream(stream)))
                setGain(clip, sfxVolume)
                clip.start()
            } catch (_: Throwable) {
            }
        }.apply { isDaemon = true }.start()
    }

    // --- Music -------------------------------------------------------------------------

    private fun canPlay(): Boolean = foreground && !userPaused && state != MusicState.NONE

    fun setBossMusic(music: BossMusic?, phase: Int, glitched: Boolean) {
        val changed = music != bossMusic || glitched != bossGlitched
        bossMusic = music
        bossPhase = phase
        bossGlitched = glitched
        if (changed && state == MusicState.BOSS && music != null && pinned == null) {
            val sig = MusicLibrary.byId(music.track)
            if (sig != null && sig != currentTrack) playTrack(sig)
        }
        player?.setVolume(musicGain())
    }

    private fun poolFor(s: MusicState): List<MusicTrack> {
        val m = bossMusic
        // ARCADE tracks (0.54.0) shuffle into every boss's rotation too.
        return if (s == MusicState.BOSS && m != null) m.tier.tracks.mapNotNull { MusicLibrary.byId(it) } + MusicLibrary.arcade else MusicLibrary.poolFor(s)
    }

    fun setMusic(next: MusicState) {
        if (next == state) return
        state = next
        if (next == MusicState.NONE) { stopPlayer(); return }
        // A NEON track never plays on a boss round, even hand-picked (owner, 0.37.5).
        if (next == MusicState.BOSS && pinned?.plain == true) pinned = null
        val keep = pinned
        if (keep != null && currentTrack == keep && player != null) return
        val cur = currentTrack
        val boss = bossMusic
        if (next == MusicState.BOSS && boss != null && keep == null) {
            val sig = MusicLibrary.byId(boss.track)
            if (sig != null && sig != cur) { playTrack(sig); return }
        }
        if (cur != null && player != null && cur in poolFor(next)) { player?.setVolume(musicGain()); return }
        playTrack(keep ?: bag.next(poolFor(next), cur))
    }

    /** Tempo changes are Android-only; kept for the shared API. */
    var slowRound = false

    /** Called every frame by the game (tempo is fixed on PC). */
    fun ensureSpeed() {}

    private fun playTrack(track: MusicTrack?) {
        stopPlayer()
        currentTrack = track
        if (track == null) return
        val s = MusicStream(track, musicGain()) { finished -> SwingUtilities.invokeLater { if (player === finished) onTrackFinished() } }
        player = s
        if (canPlay()) s.start() else s.prepare()
    }

    private fun onTrackFinished() {
        var nextTrack = if (shuffle) bag.next(poolNow(), currentTrack) else nextInOrder(currentTrack)
        while (state == MusicState.BOSS && nextTrack?.plain == true) nextTrack = nextInOrder(nextTrack)
        if (pinned != null) pinned = nextTrack
        if (!shuffle && pinned != null) { playTrack(currentTrack); return }
        playTrack(nextTrack)
    }

    private fun poolNow(): List<MusicTrack> =
        if (pinned != null) MusicLibrary.all.filter { !(state == MusicState.BOSS && it.plain) } else poolFor(state)

    private fun nextInOrder(t: MusicTrack?): MusicTrack {
        val list = MusicLibrary.all
        val i = list.indexOf(t)
        return list[(i + 1).mod(list.size)]
    }

    /** Music volume with headroom for a boss's (Android) EQ boost, kept so levels match. */
    private fun musicGain(): Float {
        val m = bossMusic.takeIf { state == MusicState.BOSS && currentTrack?.noEq != true } ?: return musicVolume
        val boost = maxOf(0f, m.bassDb, m.trebleDb)
        return musicVolume * Math.pow(10.0, (-boost * 0.5 / 20.0)).toFloat()
    }

    private fun stopPlayer() {
        player?.stop()
        player = null
    }

    private fun resumeIfAllowed() {
        val p = player
        if (p == null) {
            if (canPlay()) playTrack(pinned ?: bag.next(MusicLibrary.poolFor(state), null))
            return
        }
        if (canPlay()) p.start()
    }

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
        if (value) player?.pause() else resumeIfAllowed()
    }

    fun toggleShuffle() { shuffle = !shuffle }

    fun clearPin() {
        pinned = null
        val cur = currentTrack
        if (cur == null || cur !in MusicLibrary.poolFor(state)) playTrack(bag.next(MusicLibrary.poolFor(state), cur))
    }

    /** Window minimised: silence music and effects. */
    fun onBackground() {
        foreground = false
        player?.pause()
        for (pool in clips.values) for (c in pool) try { c.stop() } catch (_: Throwable) {}
    }

    fun onForeground() {
        foreground = true
        resumeIfAllowed()
    }

    private fun setGain(line: javax.sound.sampled.Line, volume: Float) {
        try {
            val g = line.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
            val db = if (volume <= 0.0001f) g.minimum else (20f * log10(volume)).coerceIn(g.minimum, g.maximum)
            g.value = db
        } catch (_: Throwable) {
        }
    }

    /**
     * One soundtrack track decoding from Ogg Vorbis to 16-bit PCM on its own thread.
     * [onEnd] fires once when the track plays out (not when stopped).
     */
    private inner class MusicStream(val track: MusicTrack, initialGain: Float, val onEnd: (MusicStream) -> Unit) {
        @Volatile private var paused = true
        @Volatile private var stopped = false
        @Volatile private var gain = initialGain
        private var thread: Thread? = null
        private var line: SourceDataLine? = null
        private val lock = Object()

        fun prepare() = ensureThread()

        fun start() {
            ensureThread()
            synchronized(lock) { paused = false; lock.notifyAll() }
            try { line?.start() } catch (_: Throwable) {}
        }

        fun pause() {
            paused = true
            try { line?.stop() } catch (_: Throwable) {}
        }

        fun stop() {
            stopped = true
            synchronized(lock) { lock.notifyAll() }
            try { line?.stop(); line?.flush(); line?.close() } catch (_: Throwable) {}
        }

        fun setVolume(v: Float) {
            gain = v
            line?.let { setGain(it, v) }
        }

        private fun ensureThread() {
            if (thread != null) return
            thread = Thread({ run() }, "music-${track.id}").apply { isDaemon = true; start() }
        }

        private fun run() {
            try {
                val res = javaClass.classLoader.getResourceAsStream("music/${track.file}.ogg") ?: return
                val ogg = AudioSystem.getAudioInputStream(BufferedInputStream(res))
                val base = ogg.format
                val pcmFormat = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, base.sampleRate, 16, base.channels, base.channels * 2, base.sampleRate, false)
                val pcm: AudioInputStream = AudioSystem.getAudioInputStream(pcmFormat, ogg)
                val l = AudioSystem.getSourceDataLine(pcmFormat)
                l.open(pcmFormat, 1 shl 16)
                line = l
                setGain(l, gain)
                val buf = ByteArray(8192)
                while (!stopped) {
                    synchronized(lock) { while (paused && !stopped) lock.wait(250) }
                    if (stopped) break
                    if (!l.isRunning) l.start()
                    val n = pcm.read(buf)
                    if (n < 0) {
                        l.drain()
                        if (!stopped) onEnd(this)
                        break
                    }
                    l.write(buf, 0, n)
                }
                try { l.close(); pcm.close() } catch (_: Throwable) {}
            } catch (_: Throwable) {
            }
        }
    }

    companion object {
        private const val CLIPS_PER_SOUND = 4
        /** Shared with the game session's round roll (tempo itself is Android-only). */
        const val SLOW_ROUND_CHANCE = 0.2f
    }
}
