package com.cyberoperative.game.save

import kotlinx.serialization.Serializable

/** Persistent player data (§55). Versioned so later fields can migrate. */
@Serializable
data class PlayerProfile(
    val version: Int = 1,
    val euros: Long = 0,
    val diamonds: Long = 0,
    val operativeXp: Long = 0,
    val permanentUpgrades: Map<String, Int> = emptyMap(),
    val highestLevel: Int = 0,
    val bestScore: Long = 0,
    val totalKills: Long = 0,
    val totalBosses: Long = 0,
    val totalRuns: Int = 0,
    val totalEvents: Int = 0,
    val longestRunSeconds: Float = 0f,
    val eventTypesCompleted: Set<String> = emptySet(),
    val achievements: Set<String> = emptySet(),
    val ownedSkins: Set<String> = setOf("default"),
    val selectedSkin: String = "default",
    val ownedOperatives: Set<String> = setOf("operative"),
    val selectedOperative: String = "operative",
    val ownedThemes: Set<String> = setOf("default"),
    val selectedTheme: String = "default",
    val ownedBackgrounds: Set<String> = setOf("none"),
    val selectedBackground: String = "none",
    /** Full-body design for the operative (BodyStyle id). */
    val operativeBody: String = "agent",
    val endlessBestStage: Int = 0,
    val endlessBestScore: Long = 0,
    /** Revive tokens bought with ◇ (revive packs). */
    val reviveTokens: Int = 0,
    val tutorialDone: Boolean = false,
    /**
     * A saved operation (RunSnapshot JSON), or null. Kept as a string so a save
     * from an older build can be dropped without breaking the whole profile.
     */
    val savedRun: String? = null,
    /** Difficulty picked last time, preselected on the next new run. */
    val lastDifficulty: String = "MEDIUM",
    /** Best campaign level per class id and difficulty (unlocks the class skins, 0.17.0). */
    val classRecords: Map<String, ClassRecord> = emptyMap(),
    /** NEON OPERATIVE became the free TECHNICIAN class: its ◇ price was refunded once. */
    val neonRefunded: Boolean = false,
    /** BOSS CODEX: per boss id, what you've done against it. */
    val bossRecords: Map<String, BossRecord> = emptyMap(),
    val settings: GameSettings = GameSettings()
)

@Serializable
data class GameSettings(
    val musicVolume: Float = 0.7f,
    val sfxVolume: Float = 0.8f,
    val haptics: Boolean = true,
    val damageNumbers: Boolean = true,
    val screenShake: Boolean = true,
    val showFps: Boolean = false,
    /** PC key bindings: action name → key code (missing = default). */
    val keyBinds: Map<String, Long> = emptyMap(),
    /** Android: PC MODE (the PC gameplay set) and, within it, LANDSCAPE or PORTRAIT. */
    val pcMode: Boolean = false,
    val pcLandscape: Boolean = true,
    /** Android touch controls: joystick AUTO (locked in landscape, floating in portrait), FLOAT or LOCKED. */
    val joystickMode: String = "AUTO",
    /** Android touch layout: "<P|L>.<control>.<x|y>" → fraction of the screen. */
    val touchLayout: Map<String, Float> = emptyMap()
)

/** Best campaign level reached with one class, per difficulty. */
@Serializable
data class ClassRecord(
    val bestEasy: Int = 0,
    val bestNormal: Int = 0,
    val bestHard: Int = 0
) {
    fun best(difficulty: String): Int = when (difficulty) {
        "EASY" -> bestEasy
        "HARD" -> bestHard
        else -> bestNormal
    }

    fun with(difficulty: String, level: Int): ClassRecord = when (difficulty) {
        "EASY" -> copy(bestEasy = maxOf(bestEasy, level))
        "HARD" -> copy(bestHard = maxOf(bestHard, level))
        else -> copy(bestNormal = maxOf(bestNormal, level))
    }
}

/** BOSS CODEX entry: times met and defeated, and the fastest kill (seconds, 0 = none yet). */
@Serializable
data class BossRecord(
    val met: Int = 0,
    val defeated: Int = 0,
    val bestSeconds: Float = 0f
)
