package com.cyberoperative.game.idle

import com.cyberoperative.game.data.GearItem
import com.cyberoperative.game.save.CharacterLook
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.floor

/*
 * IDLE RUNS (owner, 0.37.0): the operator fights on its own while the game is closed, one threat
 * at a time, a boss every 10 levels, for half the € and XP a normal run would pay.
 * The whole run is this one serializable document (see IdleRunSimulator for the rules).
 */

@Serializable
enum class IdleStatus { ACTIVE, DEFEATED, COMPLETED }

/** The threat being fought right now (stats fixed when it spawned). */
@Serializable
data class IdleEnemy(
    /** EnemyDef id, or the BossDef id when [boss]. */
    val id: String,
    val boss: Boolean = false,
    /** EliteModifier name, or null. */
    val elite: String? = null,
    val hp: Float,
    val maxHp: Float,
    /** Damage per second it deals the operator while it lives (after armour, dodging and the crowd). */
    val dps: Float,
    /** Normal-run rewards for the kill (before the idle share). */
    val euros: Float,
    val xp: Float
)

@Serializable
data class IdleRunState(
    val version: Int = 1,
    val runId: Long = 0,
    val status: IdleStatus = IdleStatus.ACTIVE,
    /** Why a COMPLETED run stopped (shown on the summary). */
    val endReason: String = "",

    // --- The operator, as equipped when the run started ------------------------------------
    val characterId: String = "",
    val characterName: String = "",
    val classId: String = "operative",
    val skinId: String = "default",
    val neonColor: Long = 0xFFFF2BD6,
    val look: CharacterLook = CharacterLook(),
    val opXp: Long = 0,
    val permanent: Map<String, Int> = emptyMap(),
    val gear: List<GearItem> = emptyList(),

    // --- Run ---------------------------------------------------------------------------------
    /** Difficulty save name: MEDIUM (STANDARD) or INFERNO_n. */
    val difficulty: String = "MEDIUM",
    val seed: Long = 0,
    /** Wall-clock times (ms). */
    val startedAt: Long = 0,
    val simulatedTo: Long = 0,
    val endedAt: Long = 0,
    val level: Int = 1,
    /** This level's threats still to come ("enemyId" or "enemyId|ELITE"), front first. */
    val queue: List<String> = emptyList(),
    /** Threats this level started with (progress inside the level). */
    val roomSize: Int = 0,
    val enemy: IdleEnemy? = null,
    /** Seconds still walking to the next room / waiting on a boss intro. */
    val pause: Float = 0f,
    val hp: Float = 100f,
    val maxHp: Float = 100f,
    /** HP charge packets carried (the operator drinks one when low, like a player on Q). */
    val charges: Int = 3,
    val kills: Int = 0,
    val elites: Int = 0,
    val bosses: Int = 0,
    /** What a normal run would have paid so far; the operator keeps [EURO_SHARE] of the € and [REWARD_SHARE] of the XP. */
    val normalEuros: Double = 0.0,
    val normalXp: Double = 0.0,
    /** Upgrade chips picked this run (id → level), and their names in pick order. */
    val chips: Map<String, Int> = emptyMap(),
    val chipLog: List<String> = emptyList(),
    /** Gear found (uids are reassigned when collected). */
    val drops: List<GearItem> = emptyList(),
    val highestLevel: Int = 1,
    /** Rewards already moved into the save. */
    val collected: Boolean = false,
    /** The "operator terminated" notification has been posted. */
    val notified: Boolean = false
) {
    val active: Boolean get() = status == IdleStatus.ACTIVE
    val ended: Boolean get() = status != IdleStatus.ACTIVE

    /** € and XP the operator keeps (rounded down): 40% of the normal run's €, 50% of its XP. */
    val eurosEarned: Long get() = floor(normalEuros * EURO_SHARE).toLong()
    val xpEarned: Long get() = floor(normalXp * REWARD_SHARE).toLong()

    /** The next boss level (10, 20, …); the current one while a boss is up. */
    val nextBoss: Int get() = ((level + 9) / 10) * 10
    val bossLevel: Boolean get() = level % 10 == 0

    /** Seconds the run has been going (to now while active). */
    fun durationSeconds(now: Long): Long = (((if (ended) endedAt else now) - startedAt) / 1000L).coerceAtLeast(0)

    /** 0..1 through the current level. */
    val levelProgress: Float get() = if (roomSize <= 0) 0f else (1f - (queue.size + (if (enemy != null) 1 else 0)).toFloat() / roomSize).coerceIn(0f, 1f)

    companion object {
        /** Idle Runs pay this share of a normal run's XP (owner: exactly 50%). */
        const val REWARD_SHARE = 0.5
        /**
         * …and this share of its € (owner, 0.37.4: 20% less than the 50% at launch, "this doesn't
         * need to be a better option than actually playing the game").
         */
        const val EURO_SHARE = 0.4

        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun encode(s: IdleRunState): String = json.encodeToString(serializer(), s)

        /** Unreadable data (an older or broken file) never crashes the game or the widget. */
        fun decodeOrNull(raw: String?): IdleRunState? = if (raw.isNullOrBlank()) null else try {
            json.decodeFromString(serializer(), raw)
        } catch (_: Exception) {
            null
        }
    }
}
