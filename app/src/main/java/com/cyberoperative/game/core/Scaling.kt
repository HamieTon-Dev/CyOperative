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

    /** Enemy HP multiplier. L1=1.0, L10≈2.1, L30≈4.9, L60≈14.6, L100≈43 (late ramp from L31). */
    fun enemyHp(level: Int): Float {
        val n = (level - 1).coerceAtLeast(0).toFloat()
        return (1f + 0.11f * n + 0.0009f * n * n) * lateRamp(level, LATE_HP)
    }

    /**
     * 0 up to level 30, rising to 1 at level 80 (owner, 2026-10-09: "hard is
     * fine how it was; it is already quite hard at 30"). Every extra on top of
     * the base curve — OP level, build size, the late ramp — fades in with it,
     * so levels 1–30 play exactly as designed and the extras only bite deep.
     */
    fun extraRamp(level: Int): Float = ((level - LATE_RAMP_FROM) / 50f).coerceIn(0f, 1f)

    const val LATE_HP = 1.02
    const val LATE_DAMAGE = 1.015

    /**
     * Owner, 2026-10-08: "after level 60 it feels too easy" — builds snowball
     * (100+ picks, a dozen auto-weapons), so past level 30 threats compound
     * on top of the polynomial curve. Capped so very deep runs stay finite.
     */
    fun lateRamp(level: Int, perLevel: Double): Float {
        val over = (level - LATE_RAMP_FROM).coerceIn(0, 300)
        return Math.pow(perLevel, over.toDouble()).toFloat()
    }

    const val LATE_RAMP_FROM = 30

    /** Enemy damage multiplier. Grows slower than HP so deaths stay understandable. */
    fun enemyDamage(level: Int): Float {
        val n = (level - 1).coerceAtLeast(0).toFloat()
        return (1f + 0.045f * n + 0.00018f * n * n) * lateRamp(level, LATE_DAMAGE)
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

    /**
     * Campaign: how many threats a level contains (owner: "kill 8 on level 1,
     * 14 on level 2…"). Grows fast early, then flattens; capped so a level is
     * never a slog.
     */
    fun campaignThreats(level: Int): Int {
        if (level <= 1) return 8
        val base = min(45, (8.0 + 6.0 * Math.pow((level - 1).toDouble(), 0.6)).toInt())
        // Owner, 2026-10-08: past level 50 the rooms keep getting busier (to 70).
        return if (level > 50) min(70, base + (level - 50) / 2) else base
    }

    /** Campaign waves: one screen-full at a time. */
    fun campaignWaves(threats: Int): Int = when {
        threats <= 8 -> 1
        else -> min(5, (threats + 9) / 10)
    }

    /** Endless mode: seconds per difficulty stage. */
    const val ENDLESS_STAGE_SECONDS = 30f

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
    fun eliteChance(level: Int): Float = when {
        level < 6 -> 0f
        level <= 50 -> min(0.35f, 0.02f * (level - 5))
        // Deep runs: more elites, up to half of all threats.
        else -> min(0.5f, 0.35f + 0.005f * (level - 50))
    }

    /**
     * Adaptive threat (owner, 2026-10-08: "levels 50+ scale too slowly if you
     * have many weapons"). Build size — upgrade picks and the weapons fighting
     * for you (max 7 since 0.10.1) — raises threat HP and damage. Fades in with
     * [extraRamp] (nothing up to level 30, full by 80) and is fixed per level.
     */
    fun adaptiveHp(level: Int, picks: Int, weapons: Int): Float =
        1f + adaptiveRamp(level) * (0.01f * picks.coerceIn(0, 400) + 0.08f * weapons.coerceIn(0, 12))

    fun adaptiveDamage(level: Int, picks: Int, weapons: Int): Float =
        1f + adaptiveRamp(level) * (0.004f * picks.coerceIn(0, 400) + 0.03f * weapons.coerceIn(0, 12))

    fun adaptiveRamp(level: Int): Float = extraRamp(level)

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
