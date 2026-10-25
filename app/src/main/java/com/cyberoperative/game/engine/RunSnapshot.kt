package com.cyberoperative.game.engine

import com.cyberoperative.game.i18n.tr
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
    /** Firewall is gone (2026-10-13); kept so older saves still decode. */
    val firewall: Float = 0f,
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
    /** PC consumables (-1 = saved before they existed: start amounts). */
    val hpCharges: Int = -1,
    val packetBombs: Int = -1,
    /** The room shape and rules this run started with (Android PC mode can change between runs). */
    val wide: Boolean = false,
    val pcRules: Boolean = false,
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
    val enemies: List<SavedEnemy>,
    /** Owner, 2026-10-10: an offered upgrade shop survives save & exit (side gate and where it is). */
    val shopGateOpen: Boolean = false,
    val shopGateRight: Boolean = false,
    val shopGateY: Float = 0f,
    /** A Data Vault cache already cracked stays cracked (no second payout). */
    val vaultCracked: Boolean = false,
    /**
     * Save format: 1 = before the world-map campaign (CAMPAIGN then meant today's ENDLESS, and
     * ENDLESS was the retired one-room mode); 2 = world-map campaign.
     */
    val format: Int = 1,
    /** Campaign position (format 2). */
    val world: Int = 1,
    val room: Int = 0,
    val clearedRooms: List<Int> = emptyList(),
    val visitedRooms: List<Int> = emptyList()
) {
    /** One line for the CONTINUE button. */
    val label: String
        get() = (if (mode == GameMode.CAMPAIGN.name) {
            if (room >= CampaignMap.BOSS_INDEX) tr("WORLD {0} · BOSS", world) else tr("WORLD {0} · ROOM {1}", world, room + 1)
        } else tr("ENDLESS · LEVEL {0}", level)) + " · ${Difficulty.byName(difficulty).label}"

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /** A save from an incompatible build is dropped rather than crashing the menu. */
        fun decodeOrNull(raw: String?): RunSnapshot? = try {
            if (raw.isNullOrEmpty()) null else migrate(json.decodeFromString(serializer(), raw))
        } catch (_: Exception) {
            null
        }

        /** Older saves: the old level-by-level campaign continues as ENDLESS; old one-room ENDLESS runs are gone. */
        private fun migrate(r: RunSnapshot): RunSnapshot? = when {
            r.format >= 2 -> r
            r.mode == GameMode.ENDLESS.name -> null
            else -> r.copy(mode = GameMode.ENDLESS.name, format = 2)
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
