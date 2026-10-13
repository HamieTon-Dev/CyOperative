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
    /** The NEON skin's colour, picked on the colour wheel (owner, 2026-10-11). */
    val neonColor: Long = 0xFFFF2BD6,
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
    /** World-map campaign: furthest server world reached, and full campaigns finished. */
    val bestWorld: Int = 0,
    val campaignsCompleted: Int = 0,
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
    /** Data Vault update (2026-10-13): € and the € upgrades were wiped once. */
    val dataVaultWipe: Boolean = false,
    /** Highest INFERNO tier unlocked (0 = none; beating World 15 on Hard unlocks I). */
    val infernoUnlocked: Int = 0,
    /** BOSS CODEX: per boss id, what you've done against it. */
    val bossRecords: Map<String, BossRecord> = emptyMap(),
    val settings: GameSettings = GameSettings(),
    // --- Characters and gear (Data Vault update, owner 2026-10-13) -----------------------
    // The ACTIVE character lives in the top-level fields (operativeXp, permanentUpgrades,
    // selectedOperative, savedRun and the gear below); the others wait in [otherCharacters].
    // See meta/Characters.kt.
    /** Active character's id and name ("" until the first character is made). */
    val characterId: String = "",
    val characterName: String = "",
    val look: CharacterLook = CharacterLook(),
    /** Gear: equipped per slot, the backpack (60) and the Data Vault (1,500). */
    val equipped: Map<com.cyberoperative.game.data.GearSlot, com.cyberoperative.game.data.GearItem> = emptyMap(),
    val backpack: List<com.cyberoperative.game.data.GearItem> = emptyList(),
    val vault: List<com.cyberoperative.game.data.GearItem> = emptyList(),
    /** Next gear id (unique across every character). */
    val gearSeq: Long = 1,
    val otherCharacters: List<CharacterSave> = emptyList()
)

/** Appearance picks on the character creator: HEAD, FACE, BODY, LEGS (three options each). */
@Serializable
data class CharacterLook(val head: Int = 0, val face: Int = 0, val body: Int = 0, val legs: Int = 0)

/** A character that isn't the active one: everything that belongs to it alone. */
@Serializable
data class CharacterSave(
    val id: String,
    val name: String,
    val classId: String,
    val look: CharacterLook = CharacterLook(),
    val operativeXp: Long = 0,
    val permanentUpgrades: Map<String, Int> = emptyMap(),
    val equipped: Map<com.cyberoperative.game.data.GearSlot, com.cyberoperative.game.data.GearItem> = emptyMap(),
    val backpack: List<com.cyberoperative.game.data.GearItem> = emptyList(),
    val vault: List<com.cyberoperative.game.data.GearItem> = emptyList(),
    val savedRun: String? = null,
    val lastDifficulty: String = "MEDIUM"
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
    /** Android: play in LANDSCAPE (wide rooms, HUD strip, joystick left / buttons right) instead of PORTRAIT. */
    val landscape: Boolean = false,
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
        // Inferno tiers count as Hard for records and skins.
        else -> if (difficulty.startsWith("INFERNO")) bestHard else bestNormal
    }


    fun with(difficulty: String, level: Int): ClassRecord = when (difficulty) {
        "EASY" -> copy(bestEasy = maxOf(bestEasy, level))
        "HARD" -> copy(bestHard = maxOf(bestHard, level))
        else -> if (difficulty.startsWith("INFERNO")) copy(bestHard = maxOf(bestHard, level)) else copy(bestNormal = maxOf(bestNormal, level))
    }

}

/** BOSS CODEX entry: times met and defeated, and the fastest kill (seconds, 0 = none yet). */
@Serializable
data class BossRecord(
    val met: Int = 0,
    val defeated: Int = 0,
    val bestSeconds: Float = 0f
)
