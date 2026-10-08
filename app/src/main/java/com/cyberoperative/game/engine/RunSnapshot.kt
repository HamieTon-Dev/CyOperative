package com.cyberoperative.game.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A saved operation (owner, 2026-10-08: "return to menu and save where I am,
 * like CyOps TD"). Captures the level, the room (rebuilt from [levelSeed]),
 * every living threat with its position and HP, the player and the build.
 * Projectiles, hazards and effects are transient and not kept: the operative
 * comes back with a short grace period instead.
 *
 * Never taken while a boss is alive (see [GameEngine.saveBlockReason]).
 */
@Serializable
data class RunSnapshot(
    val version: Int = 1,
    val mode: String,
    val difficulty: String,
    val seed: Long,
    val level: Int,
    val levelSeed: Long,
    val previousArenaId: String?,
    val previousEvent: Boolean,
    val phase: String,
    val phaseTimer: Float,
    val px: Float,
    val py: Float,
    val hp: Float,
    val firewall: Float,
    val facing: Float,
    val upgrades: Map<String, Int>,
    val runLevel: Int,
    val xp: Float,
    val pendingUpgrades: Int,
    val rewardBatchTotal: Int,
    val rewardBatchTaken: Int,
    val offerLuck: Float,
    val offer: List<String>,
    val upgradeReturnsToCombat: Boolean,
    val rerollsLeft: Int,
    val revivesLeft: Int,
    val revivesUsed: Int,
    val score: Long,
    val kills: Int,
    val bosses: Int,
    val elites: Int,
    val events: Int,
    val completedEventIds: Set<String>,
    val euros: Int,
    val diamonds: Int,
    val runSeconds: Float,
    val levelSeconds: Float,
    val levelDamageTaken: Float,
    val levelEnemyTotal: Int,
    val levelKills: Int,
    val levelSpawned: Int,
    val stageTimer: Float,
    val timedRemaining: Float,
    val portalOpen: Boolean,
    val waveIndex: Int,
    val waveTimer: Float,
    val spawnTimer: Float,
    val hazardTimer: Float,
    val enemies: List<SavedEnemy>
) {
    /** One line for the CONTINUE button. */
    val label: String
        get() = (if (mode == GameMode.ENDLESS.name) "ENDLESS · STAGE $level" else "LEVEL $level") +
            " · ${Difficulty.byName(difficulty).label}"

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /** A save from an incompatible build is dropped rather than crashing the menu. */
        fun decodeOrNull(raw: String?): RunSnapshot? = try {
            if (raw.isNullOrEmpty()) null else json.decodeFromString(serializer(), raw)
        } catch (_: Exception) {
            null
        }
    }
}

@Serializable
data class SavedEnemy(
    val def: String,
    val elite: String?,
    val x: Float,
    val y: Float,
    val hp: Float,
    val maxHp: Float,
    val radius: Float,
    val speed: Float,
    val damageMul: Float,
    val attackRateMul: Float,
    val damageTakenMul: Float,
    val rewardMul: Float,
    val isChild: Boolean
)
