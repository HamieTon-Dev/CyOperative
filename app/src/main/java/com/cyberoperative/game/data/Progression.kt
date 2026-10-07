package com.cyberoperative.game.data

import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.engine.RunStats
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Permanent € upgrades (§31). They persist across runs and feed the base
 * [RunStats]/[RunConfig] of every new operation. Costs grow polynomially so
 * long-term progression keeps meaning without becoming a wall.
 */
data class PermanentUpgradeDef(
    val id: String,
    val name: String,
    val description: String,
    val maxLevel: Int,
    val baseCost: Int,
    /** Operative level required to buy the first level. */
    val unlockAt: Int,
    val effect: (level: Int) -> String,
    val applyStats: (RunStats, Int) -> Unit = { _, _ -> },
    val applyConfig: (RunConfig, Int) -> RunConfig = { c, _ -> c }
) {
    fun costFor(currentLevel: Int): Long = (baseCost * (1.0 + currentLevel).pow(1.55)).roundToLong()
}

object PermanentUpgrades {

    private fun p(v: Float) = "+${"%.1f".format(v).removeSuffix(".0")}%"

    val all: List<PermanentUpgradeDef> = listOf(
        PermanentUpgradeDef("max_hp", "MAXIMUM HP", "More memory to absorb punishment.", 20, 60, 1,
            { l -> p(8f * l) + " max HP" }, { s, l -> s.maxHp *= 1f + 0.08f * l }),
        PermanentUpgradeDef("base_damage", "BASE DAMAGE", "Stronger default payload.", 20, 70, 1,
            { l -> p(6f * l) + " damage" }, { s, l -> s.damage *= 1f + 0.06f * l }),
        PermanentUpgradeDef("attack_speed", "ATTACK SPEED", "Faster packet cycling.", 15, 80, 1,
            { l -> p(4f * l) + " attack speed" }, { s, l -> s.fireRate *= 1f + 0.04f * l }),
        PermanentUpgradeDef("move_speed", "MOVEMENT SPEED", "Lower-latency movement.", 10, 70, 2,
            { l -> p(3f * l) + " move speed" }, { s, l -> s.moveSpeed *= 1f + 0.03f * l }),
        PermanentUpgradeDef("attack_range", "ATTACK RANGE", "Longer targeting range.", 10, 60, 2,
            { l -> p(4f * l) + " range" }, { s, l -> s.range *= 1f + 0.04f * l }),
        PermanentUpgradeDef("crit_chance", "CRITICAL CHANCE", "Find weaknesses more often.", 10, 90, 3,
            { l -> p(1.5f * l) + " crit chance" }, { s, l -> s.critChance += 0.015f * l }),
        PermanentUpgradeDef("crit_damage", "CRITICAL DAMAGE", "Exploit weaknesses harder.", 10, 90, 3,
            { l -> p(8f * l) + " crit damage" }, { s, l -> s.critMul += 0.08f * l }),
        PermanentUpgradeDef("armor", "ENEMY DAMAGE REDUCTION", "Hardened chassis.", 10, 100, 3,
            { l -> "-${2 * l}% damage taken" }, { s, l -> s.armor += 0.02f * l }),
        PermanentUpgradeDef("firewall", "FIREWALL STRENGTH", "Start every run with a recharging firewall.", 10, 110, 4,
            { l -> "+${10 * l} firewall" }, { s, l -> s.firewallMax += 10f * l }),
        PermanentUpgradeDef("orb_damage", "ORB DAMAGE", "Packet Nodes hit harder.", 15, 70, 2,
            { l -> p(8f * l) + " node damage" }, { s, l -> s.orbDamage *= 1f + 0.08f * l }),
        PermanentUpgradeDef("starting_orbs", "STARTING ORB COUNT", "Begin each run with extra Packet Nodes.", 2, 900, 6,
            { l -> "+$l starting node" + if (l > 1) "s" else "" }, { s, l -> s.orbCount += l }),
        PermanentUpgradeDef("starting_power", "STARTING WEAPON POWER", "Choose free upgrades at the start of every run.", 2, 1200, 8,
            { l -> "+$l starting upgrade" + if (l > 1) "s" else "" },
            applyConfig = { c, l -> c.copy(startingUpgrades = c.startingUpgrades + l) }),
        PermanentUpgradeDef("projectile_speed", "PROJECTILE SPEED", "Packets reach targets sooner.", 10, 50, 2,
            { l -> p(5f * l) + " packet speed" }, { s, l -> s.projectileSpeed *= 1f + 0.05f * l }),
        PermanentUpgradeDef("healing", "HEALING EFFECTIVENESS", "Repairs restore more.", 10, 70, 4,
            { l -> p(8f * l) + " healing" }, { s, l -> s.healMul *= 1f + 0.08f * l }),
        PermanentUpgradeDef("boss_damage", "BOSS DAMAGE", "Extra damage to bosses and elites.", 10, 90, 5,
            { l -> p(5f * l) + " boss & elite damage" }, { s, l -> s.eliteDamageMul += 0.05f * l }),
        PermanentUpgradeDef("upgrade_quality", "LEVEL-UP UPGRADE QUALITY", "Rare, Epic and Legendary cards appear more often.", 5, 250, 5,
            { l -> p(15f * l) + " rare+ chance" },
            applyConfig = { c, l -> c.copy(upgradeQuality = c.upgradeQuality + 0.15f * l) }),
        PermanentUpgradeDef("rerolls", "LEVEL-UP REROLLS", "Redraw the upgrade cards.", 3, 400, 4,
            { l -> "$l reroll" + (if (l > 1) "s" else "") + " per run" },
            applyConfig = { c, l -> c.copy(rerolls = c.rerolls + l) }),
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
        val stats = RunStats()
        byId(operativeId).modify(stats)
        var config = RunConfig(baseStats = stats, seed = seed)
        for (def in PermanentUpgrades.all) {
            val l = (permanent[def.id] ?: 0).coerceAtMost(def.maxLevel)
            if (l <= 0) continue
            def.applyStats(stats, l)
            config = def.applyConfig(config, l)
        }
        stats.clampLimits()
        return config
    }
}
