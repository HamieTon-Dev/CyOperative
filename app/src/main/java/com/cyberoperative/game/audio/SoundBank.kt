package com.cyberoperative.game.audio

import com.cyberoperative.game.engine.GameSound

/**
 * Synthesis recipes for every effect (§47), in the same family as CyOps TD's
 * bank: UI is a clean blip, combat is short and soft, bosses are deep.
 * Owner's CyOps TD guidance carried over: no constant loud ticking — rapid
 * effects are quiet and rate-limited.
 */
object SoundBank {

    data class Recipe(val duration: Float, val voices: List<ToneSynth.Voice>, val gain: Float = 1f)

    private fun v(
        wave: ToneSynth.Wave, f0: Float, f1: Float = f0, amp: Float = 0.5f,
        decay: Float = 8f, delay: Float = 0f, attack: Float = 0.004f
    ) = ToneSynth.Voice(wave, f0, f1, amp, decay, delay, attack)

    private val S = ToneSynth.Wave.SINE
    private val Q = ToneSynth.Wave.SQUARE
    private val T = ToneSynth.Wave.TRIANGLE
    private val N = ToneSynth.Wave.NOISE

    /** Minimum seconds between two plays of the same effect. */
    val minInterval: Map<GameSound, Float> = mapOf(
        GameSound.PLAYER_SHOT to 0.07f,
        GameSound.ORB_HIT to 0.09f,
        GameSound.ENEMY_HIT to 0.06f,
        GameSound.ENEMY_DEATH to 0.08f,
        GameSound.CRIT to 0.1f,
        GameSound.SHIELD_BLOCK to 0.08f,
        GameSound.BOOT_TICK to 0.05f
    )

    /** Pentatonic pitch steps so rapid kills sound like a loose melody. */
    val pitchVariants: Map<GameSound, List<Float>> = mapOf(
        GameSound.ENEMY_DEATH to listOf(1f, 9f / 8f, 5f / 4f, 3f / 2f, 5f / 3f),
        GameSound.ORB_HIT to listOf(1f, 9f / 8f, 5f / 4f)
    )

    val recipes: Map<GameSound, Recipe> = mapOf(
        GameSound.PLAYER_SHOT to Recipe(0.08f, listOf(v(T, 1320f, 760f, 0.4f, 30f)), 0.22f),
        GameSound.LANCE to Recipe(0.32f, listOf(v(Q, 420f, 120f, 0.4f, 9f), v(N, 1f, 1f, 0.15f, 18f)), 0.5f),
        GameSound.ORB_HIT to Recipe(0.12f, listOf(v(S, 880f, 1320f, 0.45f, 22f)), 0.22f),
        GameSound.ENEMY_HIT to Recipe(0.06f, listOf(v(S, 360f, 300f, 0.3f, 40f, attack = 0.008f)), 0.12f),
        GameSound.ENEMY_DEATH to Recipe(0.38f, listOf(
            v(S, 392f, amp = 0.55f, decay = 9f, attack = 0.012f),
            v(S, 784f, amp = 0.08f, decay = 16f, attack = 0.012f)
        ), 0.3f),
        GameSound.ELITE_DEATH to Recipe(0.6f, listOf(
            v(T, 520f, 1040f, 0.45f, 6f), v(N, 1f, 1f, 0.2f, 10f), v(S, 130f, 60f, 0.4f, 5f)
        ), 0.5f),
        GameSound.PLAYER_HURT to Recipe(0.26f, listOf(v(Q, 300f, 90f, 0.45f, 10f), v(N, 1f, 1f, 0.22f, 16f)), 0.55f),
        GameSound.CRIT to Recipe(0.14f, listOf(v(Q, 1500f, 2200f, 0.25f, 26f), v(S, 750f, 1100f, 0.25f, 20f)), 0.22f),
        GameSound.FIREWALL_UP to Recipe(0.4f, listOf(v(T, 330f, 990f, 0.4f, 5f), v(S, 660f, 1320f, 0.2f, 6f)), 0.4f),
        GameSound.FIREWALL_BREAK to Recipe(0.45f, listOf(v(Q, 900f, 120f, 0.35f, 6f), v(N, 1f, 1f, 0.3f, 8f)), 0.5f),
        GameSound.SHIELD_BLOCK to Recipe(0.1f, listOf(v(T, 1800f, 1200f, 0.35f, 30f)), 0.25f),
        GameSound.EMP to Recipe(0.5f, listOf(v(S, 120f, 600f, 0.5f, 5f), v(N, 1f, 1f, 0.15f, 6f)), 0.45f),
        GameSound.UPGRADE_SELECTED to Recipe(0.5f, listOf(
            v(T, 523f, amp = 0.4f, decay = 6f), v(T, 659f, amp = 0.35f, decay = 6f, delay = 0.08f),
            v(T, 784f, amp = 0.35f, decay = 5f, delay = 0.16f)
        ), 0.5f),
        GameSound.LEVEL_COMPLETE to Recipe(0.6f, listOf(
            v(T, 520f, 1040f, 0.42f, 5f), v(S, 1040f, 1560f, 0.24f, 6f, delay = 0.1f)
        ), 0.5f),
        GameSound.PORTAL_OPEN to Recipe(0.7f, listOf(v(S, 220f, 880f, 0.45f, 3f), v(T, 440f, 1320f, 0.2f, 4f)), 0.4f),
        GameSound.BOSS_SPAWN to Recipe(1.2f, listOf(
            v(Q, 240f, 500f, 0.4f, 2.4f), v(S, 120f, 250f, 0.4f, 2.0f),
            v(Q, 240f, 500f, 0.35f, 2.4f, delay = 0.55f)
        ), 0.6f),
        GameSound.BOSS_PHASE to Recipe(0.7f, listOf(v(Q, 180f, 90f, 0.4f, 3f), v(N, 1f, 1f, 0.2f, 5f)), 0.55f),
        GameSound.BOSS_DEATH to Recipe(2.4f, listOf(
            v(N, 1f, 1f, 0.55f, 7f), v(Q, 900f, 220f, 0.22f, 14f),
            v(S, 95f, 28f, 0.6f, 1.4f), v(Q, 640f, 70f, 0.2f, 1.6f),
            v(N, 1f, 1f, 0.42f, 4.5f, delay = 0.26f), v(Q, 210f, 45f, 0.3f, 3.2f, delay = 0.26f),
            v(S, 48f, 30f, 0.4f, 0.9f, delay = 0.45f)
        ), 0.8f),
        GameSound.CURRENCY to Recipe(0.3f, listOf(v(S, 1320f, amp = 0.3f, decay = 12f), v(S, 1760f, amp = 0.3f, decay = 12f, delay = 0.07f)), 0.3f),
        GameSound.GAME_OVER to Recipe(1.3f, listOf(
            v(Q, 380f, 60f, 0.45f, 2.0f), v(S, 190f, 40f, 0.45f, 1.7f), v(N, 1f, 1f, 0.16f, 3.0f)
        ), 0.6f),
        GameSound.EVENT_START to Recipe(0.9f, listOf(
            v(T, 440f, amp = 0.35f, decay = 4f), v(T, 554f, amp = 0.35f, decay = 4f, delay = 0.12f),
            v(T, 659f, amp = 0.35f, decay = 4f, delay = 0.24f), v(T, 880f, amp = 0.35f, decay = 3f, delay = 0.36f)
        ), 0.5f),
        GameSound.REVIVE to Recipe(0.9f, listOf(v(S, 220f, 1320f, 0.45f, 3f), v(T, 330f, 990f, 0.3f, 3f)), 0.55f),
        GameSound.UI_CLICK to Recipe(0.07f, listOf(v(Q, 880f, 1180f, 0.35f, 34f)), 0.45f),
        GameSound.UI_BACK to Recipe(0.08f, listOf(v(Q, 900f, 600f, 0.35f, 30f)), 0.4f),
        GameSound.BOOT_TICK to Recipe(0.03f, listOf(v(Q, 2400f, amp = 0.2f, decay = 80f)), 0.12f),
        GameSound.ACCESS_GRANTED to Recipe(0.8f, listOf(
            v(T, 523f, amp = 0.4f, decay = 4f), v(T, 784f, amp = 0.4f, decay = 4f, delay = 0.12f),
            v(T, 1046f, amp = 0.4f, decay = 3f, delay = 0.24f)
        ), 0.5f)
    )
}
