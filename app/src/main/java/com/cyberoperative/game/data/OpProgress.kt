package com.cyberoperative.game.data

import kotlin.math.floor
import kotlin.math.pow

/**
 * Operative level and OVERCLOCK (owner, 2026-10-13; modelled on Diablo IV's levels and Paragon).
 *
 * - **OP Lv 1–60.** Levels 1–49 need ⌊(L+9)² × 25⌋ × 10 XP each (Diablo IV's formula ×10, owner
 *   2026-10-13); 50–59 rise in flat steps (900,000 + 80,000 per level). About 29.2M XP in all.
 * - **OVERCLOCK 1–300.** After OP Lv 60, all XP goes here. Each level costs 1.2% more than the
 *   last up to OC 200, then 3% more per level; the last one costs about 40× the whole 1–60 climb.
 *   Each Overclock level is one point for the Overclock board.
 * - Monster level and enemy scaling use the [effective] level: OP level plus a quarter of Overclock.
 */
object OpProgress {
    const val MAX_LEVEL = 60
    const val MAX_OVERCLOCK = 300

    /** XP to go from OP level [level] to the next (1..59). ×10 the Diablo IV curve (owner, 2026-10-13). */
    fun xpToNext(level: Int): Long = LEVEL_XP_MUL * when {
        level < 50 -> floor((level + 9.0).pow(2) * 25.0).toLong()
        else -> 90_000L + (level - 50) * 8_000L
    }

    /** Owner, 2026-10-13: every level needs 10× the XP (59 → 60 is 1,620,000); Overclock scales with it. */
    const val LEVEL_XP_MUL = 10L

    /** XP for Overclock level [n] (1..300). */
    fun overclockXp(n: Int): Long {
        // ×10 with the level curve, so Overclock stays the longer grind past level 60.
        val early = 600_000.0 * LEVEL_XP_MUL * 1.012.pow((minOf(n, 200) - 1).toDouble())
        return if (n <= 200) early.toLong() else (early * 1.03.pow((n - 200).toDouble())).toLong()
    }

    /** Where [totalXp] puts an operative. */
    data class State(
        val level: Int,
        val overclock: Int,
        /** XP into the current level / Overclock level, and what that level needs. */
        val into: Long,
        val need: Long
    ) {
        val fraction: Float get() = if (need <= 0) 1f else (into.toFloat() / need).coerceIn(0f, 1f)
        val maxed: Boolean get() = level >= MAX_LEVEL && overclock >= MAX_OVERCLOCK
        /** "OP Lv 24" or "OP Lv 60 · OC 12". */
        val label: String get() = if (level < MAX_LEVEL) "OP Lv $level" else "OP Lv $level · OC $overclock"
        /** Monster level and scaling: the OP level plus a quarter of Overclock. */
        val effective: Int get() = level + overclock / 4
    }

    fun of(totalXp: Long): State {
        var rest = totalXp.coerceAtLeast(0)
        var level = 1
        while (level < MAX_LEVEL) {
            val need = xpToNext(level)
            if (rest < need) return State(level, 0, rest, need)
            rest -= need
            level++
        }
        var oc = 0
        while (oc < MAX_OVERCLOCK) {
            val need = overclockXp(oc + 1)
            if (rest < need) return State(MAX_LEVEL, oc, rest, need)
            rest -= need
            oc++
        }
        return State(MAX_LEVEL, MAX_OVERCLOCK, 0, 0)
    }

    /** Total XP needed to reach OP level [level] from scratch. */
    fun totalFor(level: Int): Long = (1 until level.coerceIn(1, MAX_LEVEL)).sumOf { xpToNext(it) }

    /**
     * XP for a kill at monster level [ml]: elites ×5, bosses ×60 (a world boss is a big step).
     * [mul] is the operative's XP gain (Overclock board).
     */
    fun killXp(ml: Int, elite: Boolean, boss: Boolean, mul: Float = 1f): Long {
        val base = 25.0 + 6.0 * ml.coerceAtLeast(1)
        val m = when {
            boss -> 60.0
            elite -> 5.0
            else -> 1.0
        }
        return (base * m * mul).toLong()
    }

    /** XP for clearing a room at monster level [ml]. */
    fun roomXp(ml: Int, mul: Float = 1f): Long = ((60.0 + 15.0 * ml.coerceAtLeast(1)) * mul).toLong()

    /** Base stats from OP [level] (1..60): +4% HP and +3% damage per level. */
    fun applyLevel(s: com.cyberoperative.game.engine.RunStats, level: Int) {
        val l = (level.coerceIn(1, MAX_LEVEL) - 1).toFloat()
        s.maxHp *= 1f + 0.04f * l
        s.damage *= 1f + 0.03f * l
    }

    /** Equipped chip slots: 5 at OP Lv 1, 15 at OP Lv 60. */
    fun chipSlots(level: Int): Int = 5 + ((level.coerceIn(1, MAX_LEVEL) - 1) * 10) / (MAX_LEVEL - 1)

    /** Overclock board points available: one per Overclock level, minus what's been spent. */
    fun boardPoints(state: State, spent: Int): Int = (state.overclock - spent).coerceAtLeast(0)
}
