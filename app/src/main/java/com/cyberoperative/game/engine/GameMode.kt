package com.cyberoperative.game.engine

/**
 * CAMPAIGN is the main game (owner, 2026-10-07): discrete levels with a fixed
 * number of threats, a power-up after every clear, then the gate at the top of
 * the room opens and the next generated room slides in.
 *
 * ENDLESS keeps one room and never stops spawning: survive as long as you can,
 * with upgrades earned from data (XP) mid-fight and a boss every 10 stages.
 */
enum class GameMode(val label: String) { CAMPAIGN("CAMPAIGN"), ENDLESS("ENDLESS") }

/**
 * Chosen when a new run starts (owner, 2026-10-08). Scales how tough threats
 * are and, to keep HARD worth it, the € / score paid out and the odds of
 * rolling the rarer PURPLE / GOLDEN / TITANIUM mods.
 */
enum class Difficulty(
    val label: String,
    val blurb: String,
    val enemyHp: Float,
    val enemyDamage: Float,
    val rewardMul: Float,
    /** Added to the rarity luck of every upgrade offer. */
    val luck: Float,
    val color: Long,
    /** How much of the deep-run extras (OP level, build size) this difficulty gets. */
    val extraScaling: Float = 1f,
    /**
     * Owner, 2026-10-11 ("Normal mode is too easy… after level 10 nothing is hard"):
     * how much of the post-level-10 pressure (extra HP, more threats, extra damage,
     * Scaling.hpPressure/countPressure/damagePressure) this difficulty gets.
     */
    val pressure: Float = 0f,
    /** Share of the post-level-10 HP pressure (Hard takes a fraction on top of its own ×1.45). */
    val hpPressureShare: Float = pressure,
    /** Boss HP multiplier on top of everything else (owner: "at least double"). */
    val bossHpMul: Float = 1f,
    /** Elites: HP multiplier and tougher armour (owner: "double HP and more armor"). */
    val eliteHpMul: Float = 1f,
    val eliteArmor: Boolean = false
) {
    // Easy stays as it was (owner: "Easy mode is fine. Leave it for younger users").
    EASY("EASY", "Softer threats · ×0.8 rewards", 0.7f, 0.65f, 0.8f, 0f, 0xFF00FF9C, extraScaling = 0.5f),
    MEDIUM("MEDIUM", "The intended challenge", 1f, 1f, 1f, 0.1f, 0xFF00E5FF,
        pressure = 1f, bossHpMul = 2f, eliteHpMul = 2f, eliteArmor = true),
    // Hard: its own ×1.45 plus 70% of Normal's HP bump, so it stays harder than Normal at every level.
    HARD("HARD", "Tougher threats · ×1.5 rewards · better mods", 1.45f, 1.4f, 1.5f, 0.35f, 0xFFFF2D55,
        pressure = 1f, hpPressureShare = 0.7f, bossHpMul = 2f, eliteHpMul = 2f, eliteArmor = true);

    companion object {
        fun byName(name: String?): Difficulty = entries.firstOrNull { it.name == name } ?: MEDIUM
    }
}
