package com.cyberoperative.game.audio

import com.cyberoperative.game.data.BossDef
import com.cyberoperative.game.data.Bosses

/**
 * Boss music per tier (Stage F3; owner, 2026-10-10: "We need some of the boss music
 * to sound different… a boss specific audio modification to each one").
 *
 * Every boss plays from its tier's pool of the heavy CyOps tracks, opens on its own
 * signature track, and gets a signature sound applied live on the player: pitch
 * (independent of tempo), a bass/treble EQ tilt and a reverb space. The tempo
 * climbs a little with each boss phase. No extra audio files ship.
 */
enum class MusicTier(val label: String, val tracks: List<String>, val speed: Float) {
    /** Low-threat classics. */
    I("TIER I", listOf("level10_1", "level10_2"), 1.35f),
    /** High-threat classics. */
    II("TIER II", listOf("boss2_1", "boss2_2"), 1.45f),
    /** Expansion bosses. */
    III("TIER III", listOf("boss2_1", "boss2_2", "boss2_remix_1", "boss2_remix_2"), 1.5f),
    /** Pack Gamma, the hardest four. */
    IV("TIER IV", listOf("boss2_remix_1", "boss2_remix_2"), 1.55f)
}

/** Reverb space, mapped onto Android's PresetReverb presets. */
enum class Reverb(val preset: Short) {
    NONE(0), SMALL_ROOM(1), LARGE_ROOM(3), MEDIUM_HALL(4), LARGE_HALL(5), PLATE(6)
}

data class BossMusic(
    val tier: MusicTier,
    /** The boss's signature track (played first, then its tier's pool). */
    val track: String,
    /** 1 = original key; <1 darker/heavier, >1 brighter/frantic. Tempo is separate. */
    val pitch: Float = 1f,
    /** EQ tilt in dB: low shelf (< 250 Hz) and high shelf (> 4 kHz). */
    val bassDb: Float = 0f,
    val trebleDb: Float = 0f,
    val reverb: Reverb = Reverb.NONE,
    /** Added to the tier's tempo for this boss. */
    val speedAdj: Float = 0f
) {
    /** Tempo for HP phase [phase] (0..2): each phase pushes it a little harder. */
    fun speed(phase: Int): Float = tier.speed + speedAdj + PHASE_STEP * phase.coerceIn(0, 2)

    /** Pitch, nudged up for a *GLITCHED* boss. */
    fun pitch(glitched: Boolean): Float = if (glitched) pitch * GLITCH_PITCH else pitch

    companion object {
        const val PHASE_STEP = 0.05f
        const val GLITCH_PITCH = 1.06f
    }
}

object BossMusicProfiles {
    private val profiles: Map<String, BossMusic> = mapOf(
        // --- Tier I/II: the classics -------------------------------------------------
        "breach" to BossMusic(MusicTier.I, "level10_1", pitch = 1.0f, bassDb = 3f),
        "botmaster" to BossMusic(MusicTier.I, "level10_2", pitch = 1.04f, trebleDb = 4f, reverb = Reverb.SMALL_ROOM),
        "worm_prime" to BossMusic(MusicTier.I, "level10_2", pitch = 0.93f, bassDb = 6f, reverb = Reverb.SMALL_ROOM),
        "ransom" to BossMusic(MusicTier.I, "level10_1", pitch = 0.97f, trebleDb = 2f, reverb = Reverb.MEDIUM_HALL),
        "rootkit_king" to BossMusic(MusicTier.I, "level10_2", pitch = 0.9f, trebleDb = -6f, reverb = Reverb.LARGE_ROOM),
        "syn_storm" to BossMusic(MusicTier.I, "level10_1", pitch = 1.06f, speedAdj = 0.1f, trebleDb = 3f),
        "kernel_panic" to BossMusic(MusicTier.II, "boss2_1", pitch = 1.08f, trebleDb = 5f, bassDb = -2f),
        "exfil" to BossMusic(MusicTier.II, "boss2_2", pitch = 1.05f, speedAdj = 0.15f),
        "white_eye" to BossMusic(MusicTier.II, "boss2_1", pitch = 1.0f, trebleDb = 3f, reverb = Reverb.LARGE_HALL),
        "zombie" to BossMusic(MusicTier.II, "boss2_2", pitch = 0.86f, speedAdj = -0.12f, bassDb = 5f, reverb = Reverb.SMALL_ROOM),
        "spoofer" to BossMusic(MusicTier.II, "boss2_1", pitch = 1.1f, trebleDb = 2f, reverb = Reverb.PLATE),
        "good_game" to BossMusic(MusicTier.II, "boss2_2", pitch = 0.88f, speedAdj = -0.08f, bassDb = 6f),
        // --- Tier III: the expansion -------------------------------------------------
        "vault_sentinel" to BossMusic(MusicTier.III, "boss2_1", pitch = 0.92f, bassDb = 6f, reverb = Reverb.LARGE_HALL),
        "pulse_bishop" to BossMusic(MusicTier.III, "boss2_remix_1", pitch = 1.0f, reverb = Reverb.LARGE_HALL, trebleDb = 2f),
        "packet_reaper" to BossMusic(MusicTier.III, "boss2_remix_2", pitch = 0.95f, speedAdj = 0.12f, bassDb = 3f),
        "worm_queen" to BossMusic(MusicTier.III, "boss2_2", pitch = 0.9f, bassDb = 6f, reverb = Reverb.LARGE_ROOM),
        "glitch_forge" to BossMusic(MusicTier.III, "boss2_remix_1", pitch = 1.12f, trebleDb = 5f, reverb = Reverb.PLATE),
        "botnet_monarch" to BossMusic(MusicTier.III, "boss2_remix_2", pitch = 1.03f, reverb = Reverb.MEDIUM_HALL, bassDb = 2f),
        "circuit_hydra" to BossMusic(MusicTier.III, "boss2_1", pitch = 0.94f, bassDb = 4f, reverb = Reverb.LARGE_ROOM),
        "black_ice_overlord" to BossMusic(MusicTier.III, "boss2_remix_2", pitch = 0.85f, trebleDb = 4f, reverb = Reverb.PLATE),
        // --- Tier IV: Pack Gamma -----------------------------------------------------
        "rootkit_apostle" to BossMusic(MusicTier.IV, "boss2_remix_1", pitch = 0.88f, trebleDb = -4f, reverb = Reverb.PLATE),
        "ransom_king" to BossMusic(MusicTier.IV, "boss2_remix_2", pitch = 0.92f, bassDb = 4f, reverb = Reverb.LARGE_HALL),
        "spectral_firewall" to BossMusic(MusicTier.IV, "boss2_remix_1", pitch = 1.05f, bassDb = 5f, reverb = Reverb.MEDIUM_HALL),
        "nullshade" to BossMusic(MusicTier.IV, "boss2_remix_2", pitch = 0.8f, speedAdj = -0.3f, trebleDb = -8f, reverb = Reverb.LARGE_HALL)
    )

    /** The music for [def] (a tier default if a boss has no entry yet). */
    fun forBoss(def: BossDef): BossMusic = profiles[def.id] ?: BossMusic(
        when {
            def.id in Bosses.GAMMA_IDS -> MusicTier.IV
            def in Bosses.classics -> if (def.tier <= 2) MusicTier.I else MusicTier.II
            else -> MusicTier.III
        },
        track = "boss2_1"
    )

    fun has(id: String) = id in profiles
}
