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
    val tutorialDone: Boolean = false,
    val settings: GameSettings = GameSettings()
)

@Serializable
data class GameSettings(
    val musicVolume: Float = 0.7f,
    val sfxVolume: Float = 0.8f,
    val haptics: Boolean = true,
    val damageNumbers: Boolean = true,
    val screenShake: Boolean = true,
    val showFps: Boolean = false
)
