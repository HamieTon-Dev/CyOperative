package com.cyberoperative.game.core

import kotlin.math.exp
import kotlin.math.min

/**
 * Endless difficulty curves. Every function is a pure function of the level
 * so it can be tested and tuned in one place (see GAME_DESIGN.md §Difficulty).
 *
 * The curves are deliberately polynomial rather than exponential: they keep
 * growing forever (there is no final level) but stay finite and readable at
 * level 1000+, and early levels stay approachable.
 */
object Scaling {

    /** Enemy HP multiplier. L1=1.0, L10≈2.1, L50≈8.6, L100≈20.7, L1000≈1008. */
    fun enemyHp(level: Int): Float {
        val n = (level - 1).coerceAtLeast(0).toFloat()
        return 1f + 0.11f * n + 0.0009f * n * n
    }

    /** Enemy damage multiplier. Grows slower than HP so deaths stay understandable. */
    fun enemyDamage(level: Int): Float {
        val n = (level - 1).coerceAtLeast(0).toFloat()
        return 1f + 0.045f * n + 0.00018f * n * n
    }

    /** Enemy movement multiplier: approaches +30% asymptotically (never impossible). */
    fun enemySpeed(level: Int): Float {
        val n = (level - 1).coerceAtLeast(0).toFloat()
        return 1f + 0.30f * (1f - exp(-n / 70f))
    }

    /** Enemy projectile speed multiplier: approaches +35%. */
    fun projectileSpeed(level: Int): Float {
        val n = (level - 1).coerceAtLeast(0).toFloat()
        return 1f + 0.35f * (1f - exp(-n / 80f))
    }

    /** Enemy attack-rate multiplier (cooldown divisor): approaches +40%. */
    fun attackRate(level: Int): Float {
        val n = (level - 1).coerceAtLeast(0).toFloat()
        return 1f + 0.40f * (1f - exp(-n / 90f))
    }

    /** Total enemies across all waves in a normal level. */
    fun enemyBudget(level: Int): Int {
        val n = (level - 1).coerceAtLeast(0)
        return min(5 + (n * 0.55f).toInt(), 46)
    }

    /** Number of waves in a normal level. */
    fun waveCount(level: Int): Int = when {
        level < 4 -> 1
        level < 15 -> 2
        level < 40 -> 3
        else -> 4
    }

    /** Max simultaneous enemies on screen (performance + readability cap). */
    const val MAX_ALIVE = 32

    /** Chance that a spawned enemy is an elite variant. */
    fun eliteChance(level: Int): Float =
        if (level < 6) 0f else min(0.35f, 0.02f * (level - 5))

    /** Boss HP multiplier: tracks enemy HP but with a gentle extra per boss cycle. */
    fun bossHp(level: Int): Float {
        val cycle = (level / 10).coerceAtLeast(1)
        return enemyHp(level) * (1f + 0.08f * (cycle - 1))
    }

    /** Is this a boss level? Every 10 levels. */
    fun isBossLevel(level: Int): Boolean = level > 0 && level % 10 == 0

    /** XP needed to go from run-level [runLevel] to the next. */
    fun xpToNext(runLevel: Int): Float = 10f + 9f * (runLevel - 1) + 0.6f * (runLevel - 1) * (runLevel - 1)
}
