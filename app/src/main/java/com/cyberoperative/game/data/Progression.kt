package com.cyberoperative.game.data

import com.cyberoperative.game.i18n.tr
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
    /**
     * Retired in 0.24.1 (owner: the card screen is gone, so rerolls did nothing). Kept only to
     * work out the refund for players who bought it ([StoreManager.migrate]).
     */
    val RETIRED_REROLLS = PermanentUpgradeDef("rerolls", tr("LEVEL-UP REROLLS"), tr("Redraw the upgrade cards."), 3, 400, 4,
        { l -> if (l >= 2f) tr("{0} rerolls per run", l.toInt()) else tr("{0} reroll per run", l.toInt()) }, mastery = Mastery.NONE)

    /** € spent on [def] up to [level] (refunds). */
    fun spentOn(def: PermanentUpgradeDef, level: Int): Long = (0 until level).sumOf { def.costFor(it) }


    private fun p(v: Float) = "+${"%.1f".format(v).removeSuffix(".0")}%"
    private fun n(v: Float) = "%.1f".format(v).removeSuffix(".0")

    val all: List<PermanentUpgradeDef> = listOf(
        PermanentUpgradeDef("max_hp", tr("MAXIMUM HP"), tr("More memory to absorb punishment."), 20, 60, 1,
            { l -> p(8f * l) + tr(" max HP") }, { s, l -> s.maxHp *= 1f + 0.08f * l }),
        PermanentUpgradeDef("base_damage", tr("BASE DAMAGE"), tr("Stronger default payload."), 20, 70, 1,
            { l -> p(6f * l) + tr(" damage") }, { s, l -> s.damage *= 1f + 0.06f * l }),
        // Owner, 2026-10-09: extra weapon slots unlock at OP 10, 20, 30, 50 and 80.
        PermanentUpgradeDef("weapon_slots", tr("WEAPON SLOTS"), tr("Carry one more weapon into every operation."), 5, 5000, 10,
            { l -> tr("{0} weapon slots", com.cyberoperative.game.data.Upgrades.MAX_WEAPONS + l.toInt()) },
            applyConfig = { c, l -> c.copy(weaponSlots = c.weaponSlots + l.toInt()) },
            mastery = Mastery.NONE,
            levelUnlocks = listOf(10, 20, 30, 50, 80),
            levelCosts = listOf(5_000L, 15_000L, 40_000L, 100_000L, 250_000L)),
        PermanentUpgradeDef("attack_speed", tr("ATTACK SPEED"), tr("Faster packet cycling."), 15, 80, 1,
            { l -> p(4f * l) + tr(" attack speed") }, { s, l -> s.fireRate *= 1f + 0.04f * l }, mastery = Mastery.SOFT, softExtra = 15),
        PermanentUpgradeDef("move_speed", tr("MOVEMENT SPEED"), tr("Lower-latency movement."), 10, 70, 2,
            { l -> p(3f * l) + tr(" move speed") }, { s, l -> s.moveSpeed *= 1f + 0.03f * l }, mastery = Mastery.SOFT, softExtra = 10),
        PermanentUpgradeDef("attack_range", tr("ATTACK RANGE"), tr("Longer targeting range."), 10, 60, 2,
            { l -> p(4f * l) + tr(" range") }, { s, l -> s.range *= 1f + 0.04f * l }, mastery = Mastery.SOFT, softExtra = 10),
        PermanentUpgradeDef("crit_chance", tr("CRITICAL CHANCE"), tr("Find weaknesses more often."), 10, 90, 3,
            { l -> p(1.5f * l) + tr(" crit chance") }, { s, l -> s.critChance += 0.015f * l }, mastery = Mastery.SOFT, softExtra = 20),
        PermanentUpgradeDef("crit_damage", tr("CRITICAL DAMAGE"), tr("Exploit weaknesses harder."), 10, 90, 3,
            { l -> p(8f * l) + tr(" crit damage") }, { s, l -> s.critMul += 0.08f * l }),
        PermanentUpgradeDef("armor", tr("ARMOR"), tr("Hardened chassis: armor cuts the damage you take."), 10, 100, 3,
            { l -> tr("+{0} armor", n(25f * l)) }, { s, l -> s.armor += 25f * l }, mastery = Mastery.SOFT, softExtra = 10),
        PermanentUpgradeDef("orb_damage", tr("ORB DAMAGE"), tr("Packet Nodes hit harder."), 15, 70, 2,
            { l -> p(8f * l) + tr(" node damage") }, { s, l -> s.orbDamage *= 1f + 0.08f * l }),
        PermanentUpgradeDef("starting_orbs", tr("STARTING ORB COUNT"), tr("Begin each run with extra Packet Nodes."), 2, 900, 6,
            { l -> if (l >= 2f) tr("+{0} starting nodes", l.toInt()) else tr("+{0} starting node", l.toInt()) }, { s, l -> s.orbCount += l.toInt() }, mastery = Mastery.NONE),
        PermanentUpgradeDef("starting_power", tr("STARTING WEAPON POWER"), tr("Free upgrade chips at the start of every run."), 2, 1200, 8,
            { l -> if (l >= 2f) tr("+{0} starting upgrades", l.toInt()) else tr("+{0} starting upgrade", l.toInt()) },
            applyConfig = { c, l -> c.copy(startingUpgrades = c.startingUpgrades + l.toInt()) }, mastery = Mastery.NONE),
        PermanentUpgradeDef("projectile_speed", tr("PROJECTILE SPEED"), tr("Packets reach targets sooner."), 10, 50, 2,
            { l -> p(5f * l) + tr(" packet speed") }, { s, l -> s.projectileSpeed *= 1f + 0.05f * l }, mastery = Mastery.SOFT, softExtra = 10),
        PermanentUpgradeDef("healing", tr("HEALING EFFECTIVENESS"), tr("Repairs restore more."), 10, 70, 4,
            { l -> p(8f * l) + tr(" healing") }, { s, l -> s.healMul *= 1f + 0.08f * l }),
        PermanentUpgradeDef("boss_damage", tr("BOSS DAMAGE"), tr("Extra damage to bosses and elites."), 10, 90, 5,
            { l -> p(5f * l) + tr(" boss & elite damage") }, { s, l -> s.eliteDamageMul += 0.05f * l }),
        PermanentUpgradeDef("upgrade_quality", tr("LEVEL-UP UPGRADE QUALITY"), tr("Rare, Epic and Legendary upgrade drops appear more often."), 5, 250, 5,
            { l -> p(15f * l) + tr(" rare+ chance") },
            applyConfig = { c, l -> c.copy(upgradeQuality = c.upgradeQuality + 0.15f * l) }, mastery = Mastery.SOFT, softExtra = 5),
        PermanentUpgradeDef("xp_gain", tr("EXPERIENCE GAIN"), tr("Collect run data faster."), 10, 60, 3,
            { l -> p(5f * l) + tr(" data gain") }, { s, l -> s.xpMul += 0.05f * l }),
        PermanentUpgradeDef("euro_gain", tr("€ GAIN"), tr("Earn more € from every operation."), 10, 120, 3,
            { l -> p(5f * l) + " €" }, { s, l -> s.euroMul += 0.05f * l })
    )

    fun byId(id: String): PermanentUpgradeDef? = all.firstOrNull { it.id == id }
}

/**
 * Playable classes (owner, 2026-10-11): the base Field Agent plus four specialists. Each class
 * has its own body ([bodyId], a BodyStyle id), stat changes and, for most, a built-in weapon
 * that takes no slot. Every class has the same unlockable skins (TITANIUM, PURPLE).
 */
data class OperativeDef(
    val id: String,
    val name: String,
    val description: String,
    val passive: String,
    val bodyId: String,
    /** Short stat lines for the class card. */
    val traits: List<String> = emptyList(),
    val modify: (RunStats) -> Unit = {}
)

object Operatives {
    /** The base operative (id kept as "operative" so older saves keep their selection). */
    val CYBER_OPERATIVE = OperativeDef(
        "operative", "FIELD AGENT", tr("Field specialist of the CyOps universe. Well rounded, adaptable."),
        tr("Boosted sidearm: +20% damage."), bodyId = "agent",
        traits = listOf(tr("+20% damage"), tr("Speed: standard"), tr("Starts with one Packet Node")),
        modify = { s -> s.damage *= 1.2f }
    )
    val TECHNICIAN = OperativeDef(
        "technician", "TECHNICIAN", tr("Signal engineer. Fast hands, faster feet, and a beam no wall can stop."),
        tr("SIGNAL BEAM: a beam through walls every 2s (grows with the level)."), bodyId = "neon_operative",
        traits = listOf(tr("+10% attack speed"), tr("+10% move speed"), tr("Built-in: SIGNAL BEAM (through walls, 2s)")),
        modify = { s ->
            s.fireRate *= 1.1f; s.moveSpeed *= 1.1f
            s.weapons[com.cyberoperative.game.data.Weapons.TECH_BEAM.id] = 1
        }
    )
    val JUGGERNAUT = OperativeDef(
        "juggernaut", "JUGGERNAUT", tr("Armoured breacher. Soaks the hits nobody else can and answers with a slam."),
        tr("BRUTE FORCE: a slow, crushing slam. ×2 HP, +100 armor, −10% speed."), bodyId = "mech",
        traits = listOf(tr("×2 max HP"), tr("+100 armor"), tr("−10% move speed"), tr("Built-in: BRUTE FORCE (slow, heavy slam)")),
        modify = { s ->
            s.maxHp *= 2f; s.armor += 100f; s.moveSpeed *= 0.9f
            s.weapons[com.cyberoperative.game.data.Weapons.BRUTE_FORCE.id] = 1
        }
    )
    val PHANTOM = OperativeDef(
        "phantom", "PHANTOM", tr("Stealth operative. Slips out of sight on a timer; only the Nullshade Specter can still see it."),
        tr("CLOAK: every 10s, turns invisible for a few seconds."), bodyId = "runner",
        traits = listOf(tr("Cloaks every 10s (3.5s)"), tr("Threats lose track of you while cloaked"), tr("The Nullshade Specter still sees you"))
    ) .let { it.copy(modify = { s -> s.cloak = true }) }
    val PACKET_MAGE = OperativeDef(
        "packet_mage", "PACKET MAGE", tr("Code-caster. Hurls tracking fire that bursts across crowds and cover."),
        tr("PACKET FIREBALL: a tracking fire shot every 2s, even on the move, with splash."), bodyId = "mage",
        traits = listOf(tr("Built-in: PACKET FIREBALL (tracking, 2s, moving or not)"), tr("Splash on threats and walls")),
        modify = { s -> s.weapons[com.cyberoperative.game.data.Weapons.MAGE_FIREBALL.id] = 1 }
    )
    /** In picker order: the Field Agent first. */
    val all = listOf(CYBER_OPERATIVE, TECHNICIAN, JUGGERNAUT, PHANTOM, PACKET_MAGE)
    fun byId(id: String) = all.firstOrNull { it.id == id } ?: CYBER_OPERATIVE
    /** The class a body belongs to (co-op partners send their body). */
    fun byBody(bodyId: String) = all.firstOrNull { it.bodyId == bodyId } ?: CYBER_OPERATIVE

    /** Build a run's starting configuration from the operative + permanent upgrades. */
    fun buildConfig(operativeId: String, permanent: Map<String, Int>, seed: Long, opLevel: Int = 1, gear: Collection<GearItem> = emptyList()): RunConfig {
        val (stats, config) = build(operativeId, permanent, seed, coreOnly = false, gear = gear)
        // OP level (owner, 2026-10-13): each level adds a little base HP and damage. Gear is
        // what really makes an operative stronger.
        OpProgress.applyLevel(stats, opLevel)
        // Mastery (levels past the designed range) is answered by tougher threats:
        // the same stats with mastery stripped give how much stronger mastery made the operative.
        val (core, _) = build(operativeId, permanent, seed, coreOnly = true, gear = gear)
        return config.copy(
            classId = byId(operativeId).id,
            gearLevel = opLevel,
            masteryDpsRatio = (dps(stats) / dps(core)).coerceAtLeast(1f),
            masterySurvivalRatio = (survival(stats) / survival(core)).coerceAtLeast(1f)
        )
    }

    private fun build(operativeId: String, permanent: Map<String, Int>, seed: Long, coreOnly: Boolean, gear: Collection<GearItem> = emptyList()): Pair<RunStats, RunConfig> {
        val stats = RunStats()
        byId(operativeId).modify(stats)
        // Equipped gear (Data Vault update): weapons, armor, implant, core and their affixes.
        Gear.apply(stats, gear, byId(operativeId).id)
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
        s.maxHp / (1f - com.cyberoperative.game.engine.Armor.reduction(s.armor, com.cyberoperative.game.engine.Armor.NOMINAL_ML)).coerceAtLeast(0.1f) * (0.75f + 0.25f * s.healMul)
}
