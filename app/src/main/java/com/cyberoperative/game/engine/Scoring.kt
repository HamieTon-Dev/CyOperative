package com.cyberoperative.game.engine

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Score formula (§41). Score only comes from things that are hard to farm:
 * kills (split children and summons pay less), level clears, a capped speed
 * bonus, a no-damage bonus and bosses. Nothing pays for merely staying alive,
 * so stalling a level never raises the score.
 */
object Scoring {

    fun levelScale(level: Int): Float = 1f + 0.06f * (level - 1)

    fun kill(baseScore: Int, level: Int, elite: Boolean, child: Boolean): Int {
        var s = baseScore * levelScale(level)
        if (elite) s *= 2.5f
        if (child) s *= 0.35f
        return s.toInt()
    }

    /** Par time for a level: generous early, scales with the enemy budget. */
    fun parSeconds(enemyCount: Int): Float = 18f + 1.6f * enemyCount

    fun levelClear(level: Int, seconds: Float, enemyCount: Int, damageTaken: Float, eventMul: Float): Int {
        val base = 100f * levelScale(level)
        val par = parSeconds(enemyCount)
        // Speed bonus: up to +100% of base, linear to zero at par. Capped so a
        // fast clear can never be worth more than the clear itself.
        val speed = base * min(1f, max(0f, (par - seconds) / par))
        val flawless = if (damageTaken <= 0f) 60f * sqrt(level.toFloat()) else 0f
        return ((base + speed + flawless) * eventMul).toInt()
    }

    fun boss(bossScore: Int, level: Int, cycle: Int): Int =
        (bossScore * levelScale(level) * (1f + 0.5f * cycle)).toInt()

    /** Operative XP (account progression) earned by a finished run. */
    fun operativeXp(levelReached: Int, kills: Int, bosses: Int): Int =
        levelReached * 12 + kills + bosses * 80
}
