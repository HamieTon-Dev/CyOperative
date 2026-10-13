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
    val eliteArmor: Boolean = false,
    /** Weapon slots locked for the whole run, whatever slots are unlocked (owner, 2026-10-11: Hard −4). */
    val lockedWeaponSlots: Int = 0,
    /** INFERNO tier 1..10 past Hard (owner, 2026-10-13); 0 for the base three. */
    val inferno: Int = 0
) {
    // Easy stays as it was (owner: "Easy mode is fine. Leave it for younger users").
    EASY("EASY", "Softer threats · ×0.8 rewards", 0.7f, 0.65f, 0.8f, 0f, 0xFF00FF9C, extraScaling = 0.5f),
    MEDIUM("MEDIUM", "The intended challenge", 1f, 1f, 1f, 0.1f, 0xFF00E5FF,
        pressure = 1f, bossHpMul = 2f, eliteHpMul = 2f, eliteArmor = true),
    // Hard: its own ×1.45 plus 70% of Normal's HP bump, so it stays harder than Normal at every level.
    HARD("HARD", "Tougher threats · −4 weapon slots · ×1.5 rewards · better mods", 1.45f, 1.4f, 1.5f, 0.35f, 0xFFFF2D55,
        pressure = 1f, hpPressureShare = 0.7f, bossHpMul = 2f, eliteHpMul = 2f, eliteArmor = true, lockedWeaponSlots = 4),
    // INFERNO I–X (owner, 2026-10-13): unlocked one by one by beating World 15's boss on the tier
    // before (Hard for Inferno I). Each tier: ×1.5 threat HP, ×1.25 damage, more rewards and loot luck.
    INFERNO_1("INFERNO I", "×2.175 threat HP · ×1.75 damage · ×1.9 rewards · better loot", 2.175f, 1.75f, 1.9f, 0.5f, 0xFFFF2A2A,
        pressure = 1f, hpPressureShare = 0.7f, bossHpMul = 2f, eliteHpMul = 2f, eliteArmor = true, lockedWeaponSlots = 4, inferno = 1),
    INFERNO_2("INFERNO II", "×3.262 threat HP · ×2.188 damage · ×2.3 rewards · better loot", 3.262f, 2.188f, 2.3f, 0.65f, 0xFFE0182F,
        pressure = 1f, hpPressureShare = 0.7f, bossHpMul = 2f, eliteHpMul = 2f, eliteArmor = true, lockedWeaponSlots = 4, inferno = 2),
    INFERNO_3("INFERNO III", "×4.894 threat HP · ×2.734 damage · ×2.7 rewards · better loot", 4.894f, 2.734f, 2.7f, 0.8f, 0xFFFF4A1A,
        pressure = 1f, hpPressureShare = 0.7f, bossHpMul = 2f, eliteHpMul = 2f, eliteArmor = true, lockedWeaponSlots = 4, inferno = 3),
    INFERNO_4("INFERNO IV", "×7.341 threat HP · ×3.418 damage · ×3.1 rewards · better loot", 7.341f, 3.418f, 3.1f, 0.95f, 0xFFFF6A00,
        pressure = 1f, hpPressureShare = 0.7f, bossHpMul = 2f, eliteHpMul = 2f, eliteArmor = true, lockedWeaponSlots = 4, inferno = 4),
    INFERNO_5("INFERNO V", "×11.011 threat HP · ×4.272 damage · ×3.5 rewards · better loot", 11.011f, 4.272f, 3.5f, 1.1f, 0xFFD0103A,
        pressure = 1f, hpPressureShare = 0.7f, bossHpMul = 2f, eliteHpMul = 2f, eliteArmor = true, lockedWeaponSlots = 4, inferno = 5),
    INFERNO_6("INFERNO VI", "×16.516 threat HP · ×5.341 damage · ×3.9 rewards · better loot", 16.516f, 5.341f, 3.9f, 1.25f, 0xFFB01870,
        pressure = 1f, hpPressureShare = 0.7f, bossHpMul = 2f, eliteHpMul = 2f, eliteArmor = true, lockedWeaponSlots = 4, inferno = 6),
    INFERNO_7("INFERNO VII", "×24.775 threat HP · ×6.676 damage · ×4.3 rewards · better loot", 24.775f, 6.676f, 4.3f, 1.4f, 0xFF9B30FF,
        pressure = 1f, hpPressureShare = 0.7f, bossHpMul = 2f, eliteHpMul = 2f, eliteArmor = true, lockedWeaponSlots = 4, inferno = 7),
    INFERNO_8("INFERNO VIII", "×37.162 threat HP · ×8.345 damage · ×4.7 rewards · better loot", 37.162f, 8.345f, 4.7f, 1.55f, 0xFF6A22FF,
        pressure = 1f, hpPressureShare = 0.7f, bossHpMul = 2f, eliteHpMul = 2f, eliteArmor = true, lockedWeaponSlots = 4, inferno = 8),
    INFERNO_9("INFERNO IX", "×55.743 threat HP · ×10.431 damage · ×5.1 rewards · better loot", 55.743f, 10.431f, 5.1f, 1.7f, 0xFF00C8FF,
        pressure = 1f, hpPressureShare = 0.7f, bossHpMul = 2f, eliteHpMul = 2f, eliteArmor = true, lockedWeaponSlots = 4, inferno = 9),
    INFERNO_10("INFERNO X", "×83.614 threat HP · ×13.039 damage · ×5.5 rewards · better loot", 83.614f, 13.039f, 5.5f, 1.85f, 0xFFFFE0E0,
        pressure = 1f, hpPressureShare = 0.7f, bossHpMul = 2f, eliteHpMul = 2f, eliteArmor = true, lockedWeaponSlots = 4, inferno = 10);

    /** Weapon slots usable this run from [unlocked] (never below one). */
    fun weaponSlots(unlocked: Int): Int = (unlocked - lockedWeaponSlots).coerceAtLeast(1)

    /** The tier beating World 15's boss on this difficulty unlocks (Hard → Inferno I …), or 0. */
    val unlocksInferno: Int get() = when {
        this == HARD -> 1
        inferno in 1 until MAX_INFERNO -> inferno + 1
        else -> 0
    }

    companion object {
        const val MAX_INFERNO = 10
        fun byName(name: String?): Difficulty = entries.firstOrNull { it.name == name } ?: MEDIUM
        fun inferno(tier: Int): Difficulty? = entries.firstOrNull { it.inferno == tier && tier > 0 }
        /** What a player can pick: the base three plus Inferno tiers up to [unlocked]. */
        fun available(unlocked: Int): List<Difficulty> = entries.filter { it.inferno <= unlocked }
    }
}
