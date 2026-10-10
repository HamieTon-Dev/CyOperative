package com.cyberoperative.game.engine

import com.cyberoperative.game.core.Scaling
import com.cyberoperative.game.data.Enemies

/**
 * Hidden power governor (owner, 2026-10-11): "make a system that can scale global damage
 * potential output of bosses, enemies, elites, and scale their HP based upon this potential
 * damage output… a boss fight lasts about the same amount of time at level 120 as at 1200…
 * Show this scale nowhere to the player… Easy mode doesn't need this system."
 *
 * It measures the team's *potential* damage per second (every weapon connecting, average
 * crits, both operatives in co-op) and guarantees floors on how long threats survive it:
 *  - a basic threat (Malware Crawler) takes at least [enemySeconds] of that output; every
 *    regular threat and elite is raised by the same factor;
 *  - a boss takes at least [bossSeconds] of it (boss & elite damage bonus included).
 * When a floor lifts HP, damage rises with its square root, so an overpowered build is also
 * hit harder. Boss damage additionally climbs past level [LATE_BOSS_LEVEL].
 * Nothing here is shown in the UI; under the floors (a fair build) it changes nothing.
 */
object PowerGovernor {
    const val LATE_BOSS_LEVEL = 120
    /** Extra boss damage per level past [LATE_BOSS_LEVEL] (+2.5%: ×3 at 200, ×28 at 1200). */
    const val LATE_BOSS_DAMAGE = 0.025f
    /** Threat damage = HP lift ^ this. */
    const val DAMAGE_EXP = 0.5f
    /** Staying power a build may have before threats hit harder: this × base HP × the damage curve. */
    const val SURVIVAL_HEADROOM = 6f

    fun active(d: Difficulty): Boolean = d != Difficulty.EASY
    fun enemySeconds(d: Difficulty): Float = if (d == Difficulty.HARD) 0.4f else 0.3f
    fun bossSeconds(d: Difficulty): Float = if (d == Difficulty.HARD) 80f else 60f

    /** Potential damage per second of one operative: main gun, every weapon, the nodes. */
    fun potentialDps(s: RunStats): Float =
        StatCompare.mainGunDps(s) + StatCompare.totalWeaponDps(s) + s.orbDamage * s.orbCount * 1.5f

    /** Potential damage per second against a boss or elite. */
    fun potentialBossDps(s: RunStats): Float = potentialDps(s) * s.eliteDamageMul

    /** Rough staying power: HP and firewall through armor, boosted by healing. */
    fun survival(s: RunStats): Float =
        (s.maxHp + s.firewallMax) / (1f - s.armor).coerceAtLeast(0.1f) * (0.75f + 0.25f * s.healMul) / (1f - s.dodge).coerceAtLeast(0.5f)

    /**
     * HP lift for regular threats: how far [teamDps] outpaces a basic threat whose HP
     * multiplier (without the governor) is [hpMul].
     */
    fun enemyHpLift(d: Difficulty, teamDps: Float, hpMul: Float): Float {
        if (!active(d)) return 1f
        val floor = teamDps * enemySeconds(d)
        return (floor / (Enemies.byId("malware").baseHp * hpMul)).coerceAtLeast(1f)
    }

    /** HP lift for a boss whose HP (without the governor) is [bossHp]. */
    fun bossHpLift(d: Difficulty, teamBossDps: Float, bossHp: Float): Float {
        if (!active(d) || bossHp <= 0f) return 1f
        return (teamBossDps * bossSeconds(d) / bossHp).coerceAtLeast(1f)
    }

    /** Damage lift from an HP lift and the toughest operative's staying power against [damageMul]. */
    fun damageLift(d: Difficulty, hpLift: Float, bestSurvival: Float, damageMul: Float): Float {
        if (!active(d)) return 1f
        val byPower = Math.pow(hpLift.toDouble(), DAMAGE_EXP.toDouble()).toFloat()
        val bySurvival = Math.pow((bestSurvival / (SURVIVAL_HEADROOM * 100f * damageMul)).toDouble(), DAMAGE_EXP.toDouble()).toFloat()
        return maxOf(1f, byPower, bySurvival)
    }

    /** Boss damage past level 120 (Normal and Hard). */
    fun lateBossDamage(d: Difficulty, level: Int): Float =
        if (!active(d) || level <= LATE_BOSS_LEVEL) 1f else 1f + LATE_BOSS_DAMAGE * (level - LATE_BOSS_LEVEL)

    /** The base enemy-damage curve at [level] (for the survival check). */
    fun damageCurve(level: Int): Float = Scaling.enemyDamage(level)
}
