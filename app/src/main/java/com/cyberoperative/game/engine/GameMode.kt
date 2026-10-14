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
class Difficulty private constructor(
    val name: String,
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
    val eliteArmor: Boolean = false,
    /** Weapon slots locked for the whole run, whatever slots are unlocked (owner, 2026-10-11: Hard −4). */
    val lockedWeaponSlots: Int = 0,
    /** INFERNO level 1..[MAX_INFERNO] past Hard (owner: 1–500, 0.34.0); 0 for the base three. */
    val inferno: Int = 0
) {
    /** Weapon slots usable this run from [unlocked] (never below one). */
    fun weaponSlots(unlocked: Int): Int = (unlocked - lockedWeaponSlots).coerceAtLeast(1)

    /** The tier beating World 15's boss on this difficulty unlocks (Hard → Inferno I …), or 0. */
    val unlocksInferno: Int get() = when {
        this == HARD -> 1
        inferno in 1 until MAX_INFERNO -> inferno + 1
        else -> 0
    }

    override fun equals(other: Any?): Boolean = other is Difficulty && other.name == name
    override fun hashCode(): Int = name.hashCode()
    override fun toString(): String = name

    companion object {
        /** INFERNO levels (owner, 0.34.0): 1–500, each a little harder than the last. */
        const val MAX_INFERNO = 500

        // Easy stays as it was (owner: "Easy mode is fine. Leave it for younger users").
        val EASY = Difficulty("EASY", "EASY", "Softer threats · ×0.8 rewards", 0.7f, 0.65f, 0.8f, 0f, 0xFF00FF9C, extraScaling = 0.5f)
        val MEDIUM = Difficulty("MEDIUM", "MEDIUM", "The intended challenge", 1f, 1f, 1f, 0.1f, 0xFF00E5FF,
            pressure = 1f, bossHpMul = 2f, eliteHpMul = 2f, eliteArmor = true)
        // Hard: its own ×1.45 plus 70% of Normal's HP bump, so it stays harder than Normal at every level.
        val HARD = Difficulty("HARD", "HARD", "Tougher threats · −4 weapon slots · ×1.5 rewards · better mods", 1.45f, 1.4f, 1.5f, 0.35f, 0xFFFF2D55,
            pressure = 1f, hpPressureShare = 0.7f, bossHpMul = 2f, eliteHpMul = 2f, eliteArmor = true, lockedWeaponSlots = 4)

        /** The base three (INFERNO levels come from [inferno]). */
        val entries: List<Difficulty> = listOf(EASY, MEDIUM, HARD)

        /** One colour per 50 levels, hotter to colder as you go deeper. */
        private val infernoColors = longArrayOf(0xFFFF2A2A, 0xFFE0182F, 0xFFFF4A1A, 0xFFFF6A00, 0xFFD0103A,
            0xFFB01870, 0xFF9B30FF, 0xFF6A22FF, 0xFF00C8FF, 0xFFFFE0E0)

        private val cache = HashMap<Int, Difficulty>()

        /** Threat HP on INFERNO [level] (owner's curve A): Hard ×1.5, then +1.2% per level, compounding. */
        fun infernoHp(level: Int): Float = (2.175 * Math.pow(1.012, (level - 1).toDouble())).toFloat()
        /** Threat damage: the same curve at half the rate (+0.6% per level). */
        fun infernoDamage(level: Int): Float = (1.75 * Math.pow(1.006, (level - 1).toDouble())).toFloat()
        fun infernoRewards(level: Int): Float = 1.9f + 0.02f * (level - 1)
        fun infernoLuck(level: Int): Float = 0.5f + 0.003f * (level - 1)

        /** INFERNO [level] (1..[MAX_INFERNO]), or null. */
        fun inferno(level: Int): Difficulty? {
            if (level !in 1..MAX_INFERNO) return null
            return synchronized(cache) {
                cache.getOrPut(level) {
                    val hp = infernoHp(level); val dmg = infernoDamage(level); val rw = infernoRewards(level)
                    Difficulty(
                        "INFERNO_$level", "INFERNO $level",
                        "×%.2f threat HP · ×%.2f damage · ×%.2f rewards · better loot".format(hp, dmg, rw),
                        hp, dmg, rw, infernoLuck(level), infernoColors[((level - 1) / 50).coerceAtMost(infernoColors.size - 1)],
                        extraScaling = (0.5f + 0.003f * (level - 1)).coerceAtMost(2f),
                        pressure = 1f, hpPressureShare = 0.7f, bossHpMul = 2f, eliteHpMul = 2f, eliteArmor = true, lockedWeaponSlots = 4,
                        inferno = level
                    )
                }
            }
        }

        /** By save name: EASY / MEDIUM / HARD / INFERNO_n (older saves' INFERNO_1..10 still load). */
        fun byName(name: String?): Difficulty = when {
            name == null -> MEDIUM
            name.startsWith("INFERNO_") -> name.removePrefix("INFERNO_").toIntOrNull()?.let { inferno(it) } ?: HARD
            else -> entries.firstOrNull { it.name == name } ?: MEDIUM
        }

        /** What a player can pick: the base three plus the highest unlocked INFERNO level (pick lower ones with a stepper). */
        fun available(unlocked: Int): List<Difficulty> = entries + listOfNotNull(inferno(unlocked.coerceAtMost(MAX_INFERNO)))
    }
}
