package com.cyberoperative.game.data

import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.engine.RunStats
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.roundToLong

/**
 * How a permanent upgrade grows past its designed range ([PermanentUpgradeDef.maxLevel]).
 * Owner, 2026-10-08: "scale it to where play never ends".
 * - [NONE]: a hard cap (extra nodes, starting upgrades, rerolls would break runs).
 * - [SOFT]: mastery levels creep toward a ceiling (speed, range, crit chance, armor…),
 *   so these never reach game-breaking values.
 * - [ENDLESS]: mastery keeps adding forever with diminishing returns (√ of mastery).
 */
enum class Mastery { NONE, SOFT, ENDLESS }

/**
 * Permanent € upgrades (§31). They persist across runs and feed the base
 * [RunStats]/[RunConfig] of every new operation. Costs grow polynomially so
 * long-term progression keeps meaning without becoming a wall.
 *
 * Levels 1..[maxLevel] work exactly as before. Past that, MASTERY levels run up
 * to [MAX_LEVEL]; each one needs an OP level (mastery m needs OP LVL m), and its
 * value follows [effectiveLevel].
 */
data class PermanentUpgradeDef(
    val id: String,
    val name: String,
    val description: String,
    val maxLevel: Int,
    val baseCost: Int,
    /** Operative level required to buy the first level. */
    val unlockAt: Int,
    /** Effect text for an effective level. */
    val effect: (level: Float) -> String,
    val applyStats: (RunStats, Float) -> Unit = { _, _ -> },
    val applyConfig: (RunConfig, Float) -> RunConfig = { c, _ -> c },
    val mastery: Mastery = Mastery.ENDLESS,
    /** SOFT only: how many designed levels' worth mastery can add at most. */
    val softExtra: Int = 0,
    /** Per-level OP requirement (level n+1 needs levelUnlocks[n]); null = [unlockAt] for all. */
    val levelUnlocks: List<Int>? = null,
    /** Per-level € price; null = the usual growing formula. */
    val levelCosts: List<Long>? = null
) {
    fun costFor(currentLevel: Int): Long =
        levelCosts?.getOrNull(currentLevel) ?: (baseCost * (1.0 + currentLevel).pow(1.55)).roundToLong()

    /** OP level needed to buy the level after [currentLevel]. */
    fun unlockFor(currentLevel: Int): Int = levelUnlocks?.getOrNull(currentLevel) ?: unlockAt

    /** Highest level this upgrade can ever reach. */
    val levelCap: Int get() = if (mastery == Mastery.NONE) maxLevel else MAX_LEVEL

    /** Highest level buyable at [opLevel]: the designed range, plus one mastery level per OP level. */
    fun capAt(opLevel: Int): Int = when {
        levelUnlocks != null -> levelUnlocks.count { it <= opLevel }.coerceAtMost(maxLevel)
        mastery == Mastery.NONE -> maxLevel
        else -> minOf(MAX_LEVEL, maxLevel + opLevel.coerceAtLeast(0))
    }

    /** The level the effect is computed from: linear up to [maxLevel], then diminishing. */
    fun effectiveLevel(level: Int): Float {
        val core = minOf(level, maxLevel).coerceAtLeast(0)
        val m = (minOf(level, levelCap) - maxLevel).coerceAtLeast(0)
        if (m == 0) return core.toFloat()
        return core + when (mastery) {
            Mastery.NONE -> 0f
            Mastery.SOFT -> softExtra * (1f - exp(-m / SOFT_TAU))
            Mastery.ENDLESS -> ENDLESS_RATE * sqrt(m.toFloat())
        }
    }

    fun effectAt(level: Int): String = effect(effectiveLevel(level))

    companion object {
        const val MAX_LEVEL = 9999
        /** SOFT mastery reaches ~63% of its ceiling after this many mastery levels. */
        const val SOFT_TAU = 60f
        /** ENDLESS: 100 mastery levels ≈ 20 designed levels, 10,000 ≈ 200. */
        const val ENDLESS_RATE = 2f
    }
}

object PermanentUpgrades {

    private fun p(v: Float) = "+${"%.1f".format(v).removeSuffix(".0")}%"
    private fun n(v: Float) = "%.1f".format(v).removeSuffix(".0")

    val all: List<PermanentUpgradeDef> = listOf(
        PermanentUpgradeDef("max_hp", "MAXIMUM HP", "More memory to absorb punishment.", 20, 60, 1,
            { l -> p(8f * l) + " max HP" }, { s, l -> s.maxHp *= 1f + 0.08f * l }),
        PermanentUpgradeDef("base_damage", "BASE DAMAGE", "Stronger default payload.", 20, 70, 1,
            { l -> p(6f * l) + " damage" }, { s, l -> s.damage *= 1f + 0.06f * l }),
        // Owner, 2026-10-09: extra weapon slots unlock at OP 10, 20, 30, 50 and 80.
        PermanentUpgradeDef("weapon_slots", "WEAPON SLOTS", "Carry one more weapon into every operation.", 5, 5000, 10,
            { l -> "${com.cyberoperative.game.data.Upgrades.MAX_WEAPONS + l.toInt()} weapon slots" },
            applyConfig = { c, l -> c.copy(weaponSlots = c.weaponSlots + l.toInt()) },
            mastery = Mastery.NONE,
            levelUnlocks = listOf(10, 20, 30, 50, 80),
            levelCosts = listOf(5_000L, 15_000L, 40_000L, 100_000L, 250_000L)),
        PermanentUpgradeDef("attack_speed", "ATTACK SPEED", "Faster packet cycling.", 15, 80, 1,
            { l -> p(4f * l) + " attack speed" }, { s, l -> s.fireRate *= 1f + 0.04f * l }, mastery = Mastery.SOFT, softExtra = 15),
        PermanentUpgradeDef("move_speed", "MOVEMENT SPEED", "Lower-latency movement.", 10, 70, 2,
            { l -> p(3f * l) + " move speed" }, { s, l -> s.moveSpeed *= 1f + 0.03f * l }, mastery = Mastery.SOFT, softExtra = 10),
        PermanentUpgradeDef("attack_range", "ATTACK RANGE", "Longer targeting range.", 10, 60, 2,
            { l -> p(4f * l) + " range" }, { s, l -> s.range *= 1f + 0.04f * l }, mastery = Mastery.SOFT, softExtra = 10),
        PermanentUpgradeDef("crit_chance", "CRITICAL CHANCE", "Find weaknesses more often.", 10, 90, 3,
            { l -> p(1.5f * l) + " crit chance" }, { s, l -> s.critChance += 0.015f * l }, mastery = Mastery.SOFT, softExtra = 20),
        PermanentUpgradeDef("crit_damage", "CRITICAL DAMAGE", "Exploit weaknesses harder.", 10, 90, 3,
            { l -> p(8f * l) + " crit damage" }, { s, l -> s.critMul += 0.08f * l }),
        PermanentUpgradeDef("armor", "ENEMY DAMAGE REDUCTION", "Hardened chassis.", 10, 100, 3,
            { l -> "-${n(2f * l)}% damage taken" }, { s, l -> s.armor += 0.02f * l }, mastery = Mastery.SOFT, softExtra = 10),
        PermanentUpgradeDef("firewall", "FIREWALL STRENGTH", "Start every run with a recharging firewall.", 10, 110, 4,
            { l -> "+${n(10f * l)} firewall" }, { s, l -> s.firewallMax += 10f * l }),
        PermanentUpgradeDef("orb_damage", "ORB DAMAGE", "Packet Nodes hit harder.", 15, 70, 2,
            { l -> p(8f * l) + " node damage" }, { s, l -> s.orbDamage *= 1f + 0.08f * l }),
        PermanentUpgradeDef("starting_orbs", "STARTING ORB COUNT", "Begin each run with extra Packet Nodes.", 2, 900, 6,
            { l -> "+${l.toInt()} starting node" + if (l >= 2f) "s" else "" }, { s, l -> s.orbCount += l.toInt() }, mastery = Mastery.NONE),
        PermanentUpgradeDef("starting_power", "STARTING WEAPON POWER", "Choose free upgrades at the start of every run.", 2, 1200, 8,
            { l -> "+${l.toInt()} starting upgrade" + if (l >= 2f) "s" else "" },
            applyConfig = { c, l -> c.copy(startingUpgrades = c.startingUpgrades + l.toInt()) }, mastery = Mastery.NONE),
        PermanentUpgradeDef("projectile_speed", "PROJECTILE SPEED", "Packets reach targets sooner.", 10, 50, 2,
            { l -> p(5f * l) + " packet speed" }, { s, l -> s.projectileSpeed *= 1f + 0.05f * l }, mastery = Mastery.SOFT, softExtra = 10),
        PermanentUpgradeDef("healing", "HEALING EFFECTIVENESS", "Repairs restore more.", 10, 70, 4,
            { l -> p(8f * l) + " healing" }, { s, l -> s.healMul *= 1f + 0.08f * l }),
        PermanentUpgradeDef("boss_damage", "BOSS DAMAGE", "Extra damage to bosses and elites.", 10, 90, 5,
            { l -> p(5f * l) + " boss & elite damage" }, { s, l -> s.eliteDamageMul += 0.05f * l }),
        PermanentUpgradeDef("upgrade_quality", "LEVEL-UP UPGRADE QUALITY", "Rare, Epic and Legendary cards appear more often.", 5, 250, 5,
            { l -> p(15f * l) + " rare+ chance" },
            applyConfig = { c, l -> c.copy(upgradeQuality = c.upgradeQuality + 0.15f * l) }, mastery = Mastery.SOFT, softExtra = 5),
        PermanentUpgradeDef("rerolls", "LEVEL-UP REROLLS", "Redraw the upgrade cards.", 3, 400, 4,
            { l -> "${l.toInt()} reroll" + (if (l >= 2f) "s" else "") + " per run" },
            applyConfig = { c, l -> c.copy(rerolls = c.rerolls + l.toInt()) }, mastery = Mastery.NONE),
        PermanentUpgradeDef("xp_gain", "EXPERIENCE GAIN", "Collect run data faster.", 10, 60, 3,
            { l -> p(5f * l) + " data gain" }, { s, l -> s.xpMul += 0.05f * l }),
        PermanentUpgradeDef("euro_gain", "€ GAIN", "Earn more € from every operation.", 10, 120, 3,
            { l -> p(5f * l) + " €" }, { s, l -> s.euroMul += 0.05f * l })
    )

    fun byId(id: String): PermanentUpgradeDef? = all.firstOrNull { it.id == id }
}

/** Playable operatives (§37). One today; the architecture supports more. */
data class OperativeDef(
    val id: String,
    val name: String,
    val description: String,
    val passive: String,
    val modify: (RunStats) -> Unit = {}
)

object Operatives {
    val CYBER_OPERATIVE = OperativeDef(
        "operative", "CYBER OPERATIVE", "Field specialist of the CyOps universe. Balanced, adaptable.",
        "Adaptive Kit: starts with one Packet Node."
    )
    val all = listOf(CYBER_OPERATIVE)
    fun byId(id: String) = all.firstOrNull { it.id == id } ?: CYBER_OPERATIVE

    /** Build a run's starting configuration from the operative + permanent upgrades. */
    fun buildConfig(operativeId: String, permanent: Map<String, Int>, seed: Long): RunConfig {
        val (stats, config) = build(operativeId, permanent, seed, coreOnly = false)
        // Mastery (levels past the designed range) is answered by tougher threats:
        // the same stats with mastery stripped give how much stronger mastery made the operative.
        val (core, _) = build(operativeId, permanent, seed, coreOnly = true)
        return config.copy(
            masteryDpsRatio = (dps(stats) / dps(core)).coerceAtLeast(1f),
            masterySurvivalRatio = (survival(stats) / survival(core)).coerceAtLeast(1f)
        )
    }

    private fun build(operativeId: String, permanent: Map<String, Int>, seed: Long, coreOnly: Boolean): Pair<RunStats, RunConfig> {
        val stats = RunStats()
        byId(operativeId).modify(stats)
        var config = RunConfig(baseStats = stats, seed = seed)
        for (def in PermanentUpgrades.all) {
            val raw = (permanent[def.id] ?: 0).coerceIn(0, def.levelCap)
            val l = if (coreOnly) minOf(raw, def.maxLevel) else raw
            if (l <= 0) continue
            val eff = def.effectiveLevel(l)
            def.applyStats(stats, eff)
            config = def.applyConfig(config, eff)
        }
        stats.clampLimits()
        return stats to config
    }

    /** Rough damage output: payload × fire rate × average crit, plus the nodes. */
    private fun dps(s: RunStats): Float =
        s.damage * s.fireRate * (1f + s.critChance * (s.critMul - 1f)) * s.eliteDamageMul + s.orbDamage * s.orbCount

    /** Rough staying power: HP and firewall through armor, boosted by healing. */
    private fun survival(s: RunStats): Float =
        (s.maxHp + s.firewallMax) / (1f - s.armor).coerceAtLeast(0.1f) * (0.75f + 0.25f * s.healMul)
}
