package com.cyberoperative.game.data

import com.cyberoperative.game.i18n.tr
import com.cyberoperative.game.engine.RunStats
import com.cyberoperative.game.data.ArenaShape

/**
 * Mod tiers (owner, 2026-10-08): BLUE rares are the everyday "good" pull;
 * PURPLE, GOLDEN and TITANIUM are progressively rarer. Their odds rise with
 * level, difficulty and boss rewards (see RunBuild.rollOffer luck).
 */
enum class Rarity(val label: String, val color: Long, val weight: Float, /** Luck steps: 0 BLUE and below, 1 PURPLE, 2 GOLDEN, 3 above. */ val highTier: Int) {
    COMMON(tr("COMMON"), 0xFFB8C4D6, 100f, 0),
    UNCOMMON(tr("UNCOMMON"), 0xFF00FF9C, 55f, 0),
    RARE(tr("BLUE RARE"), 0xFF2E9BFF, 26f, 0),
    EPIC(tr("PURPLE"), 0xFFA259FF, 7f, 1),
    LEGENDARY(tr("GOLDEN"), 0xFFFFD426, 2.5f, 2),
    // The equipment tiers as chips (owner, 0.39.0): very strong, rarer the higher they go.
    GLITCHED(tr("GLITCHED LEGENDARY"), 0xFFFF3DF2, 1.5f, 3),
    TITANIUM(tr("TITANIUM"), 0xFFDCE8F2, 0.8f, 3),
    FORGED(tr("FORGED"), 0xFFFF8A1F, 0.9f, 3),
    VOLTAIC(tr("VOLTAIC"), 0xFFFFF04D, 0.75f, 3),
    CELESTIAL(tr("CELESTIAL"), 0xFFBFD4FF, 0.6f, 3),
    ABYSSAL(tr("ABYSSAL"), 0xFFFF2B3A, 0.45f, 3),
    /** Near game-breaking (owner, 0.39.0): very rare on every difficulty. */
    ENCRYPTED(tr("ENCRYPTED"), 0xFF00F0FF, 0.16f, 3);

    /** GLITCHED and the five tiers above TITANIUM: animated chips. */
    val special: Boolean get() = this == GLITCHED || ordinal > TITANIUM.ordinal
}

enum class UpgradeCategory { WEAPON, ORBIT, DEFENSE, STAT, UTILITY }

/**
 * One run-scoped upgrade. Data only: [apply] is told the owned level and adds
 * its effect to a [RunStats] being rebuilt from scratch.
 *
 * Evolution (§21) is generic: an upgrade with [evolvesFrom] only becomes
 * offerable once that upgrade is at its max level (and [alsoRequires], if
 * set, is owned). Chains of any length are just more rows.
 */
data class UpgradeDef(
    val id: String,
    val name: String,
    /** ASCII icon in the CyOps TD glyph style, drawn inside the card frame. */
    val glyph: String,
    val rarity: Rarity,
    val category: UpgradeCategory,
    val maxLevel: Int,
    val description: String,
    /** One-line effect for the card, given the level being offered. */
    val effect: (level: Int) -> String,
    val apply: (stats: RunStats, level: Int) -> Unit,
    val evolvesFrom: String? = null,
    val alsoRequires: String? = null,
    /** Repeatable filler (e.g. a heal) that never counts toward the build. */
    val instant: Boolean = false,
    /** Arsenal weapons scale every level themselves (no mastery formula needed). */
    val scalesForever: Boolean = false
) {
    /**
     * Owner, 2026-10-08: every upgrade can keep levelling to [MAX_LEVEL].
     * [maxLevel] is the designed ("core") range: it still gates evolutions.
     * Levels past it are MASTERY levels that add a steady category bonus.
     */
    val levelCap: Int get() = if (instant) maxLevel else MAX_LEVEL

    /**
     * The designed effect of [level]. Mastery levels past [maxLevel] are NOT applied
     * here: they are pooled across the whole build by [Upgrades.applyMastery].
     */
    fun applyLevel(stats: RunStats, level: Int) {
        if (scalesForever) { apply(stats, level); return }
        apply(stats, minOf(level, maxLevel))
    }

    /** Mastery levels [level] holds past the designed range (0 for arsenal weapons and fillers). */
    fun masteryLevels(level: Int): Int = if (scalesForever || instant) 0 else (level - maxLevel).coerceAtLeast(0)

    /** Card text for [level], including mastery levels. */
    fun effectAt(level: Int): String {
        if (scalesForever || instant || level <= maxLevel) return effect(level)
        return tr("MASTERY {0}: ", level - maxLevel) + when (category) {
            UpgradeCategory.WEAPON -> tr("build damage up (shared mastery)")
            UpgradeCategory.ORBIT -> tr("node & blade damage up (shared mastery)")
            UpgradeCategory.DEFENSE -> tr("max HP & Firewall up (shared mastery)")
            UpgradeCategory.STAT -> tr("build damage & attack speed up (shared mastery)")
            UpgradeCategory.UTILITY -> tr("+{0}% € and data", pct(Upgrades.MASTERY_UTILITY))
        }
    }

    private fun pct(v: Float) = "%.1f".format(v * 100f).removeSuffix(".0")

    companion object {
        const val MAX_LEVEL = 10000
    }
}

object Upgrades {

    /*
     * Mastery (balance pass, owner 2026-10-11: "at level 180 I'm doing 26,000+ damage").
     * Every mastery level used to multiply on its own, so forty upgrades at mastery 10
     * compounded past ×10,000. Now a category's mastery levels are added into one pool P
     * and applied once through [masteryCurve], a gentle quadratic that keeps pace with
     * the threat HP curve: P 35 ≈ ×3 (≈ level 50), P 100 ≈ ×8, P 210 ≈ ×23 (≈ level 180).
     */
    const val MASTERY_LINEAR = 0.047f
    const val MASTERY_QUAD = 0.00027f
    /** STAT mastery also adds this much attack speed per level (additive). */
    const val MASTERY_STAT_RATE = 0.005f
    /** UTILITY mastery: +€ and data per level (additive). */
    const val MASTERY_UTILITY = 0.02f

    /** The multiplier a mastery pool of [p] levels gives. */
    fun masteryCurve(p: Float): Float = 1f + MASTERY_LINEAR * p + MASTERY_QUAD * p * p

    /** What one more mastery level adds on top of a pool of [p], in % of base (card text). */
    fun masteryStepPct(p: Float): Float = (masteryCurve(p + 1f) - masteryCurve(p)) * 100f

    fun applyMastery(s: RunStats, levels: Map<String, Int>) {
        val pool = FloatArray(UpgradeCategory.entries.size)
        for ((id, l) in levels) {
            val def = byIdOrNull(id) ?: continue
            pool[def.category.ordinal] += def.masteryLevels(l).toFloat()
        }
        val weapon = pool[UpgradeCategory.WEAPON.ordinal]
        val stat = pool[UpgradeCategory.STAT.ordinal]
        val orbit = pool[UpgradeCategory.ORBIT.ordinal]
        val defense = pool[UpgradeCategory.DEFENSE.ordinal]
        val utility = pool[UpgradeCategory.UTILITY.ordinal]
        s.damage *= masteryCurve(weapon + stat)
        s.fireRate *= 1f + MASTERY_STAT_RATE * stat
        s.orbDamage *= masteryCurve(orbit)
        s.bladeDamage *= masteryCurve(orbit)
        s.maxHp *= masteryCurve(defense)
        s.euroMul += MASTERY_UTILITY * utility
        s.xpMul += MASTERY_UTILITY * utility
    }


    // --- Orbit: Packet Node evolution chain ---------------------------------
    val PACKET_NODES = UpgradeDef(
        "packet_nodes", tr("PACKET NODES"), "(o)", Rarity.UNCOMMON, UpgradeCategory.ORBIT, 3,
        tr("Adds an orbiting Packet Node. Nodes never stop attacking."),
        { tr("+1 Orbiting Packet Node") },
        { s, l -> s.orbCount += l }
    )
    val ENHANCED_NODES = UpgradeDef(
        "enhanced_nodes", tr("ENHANCED NODES"), "(O)", Rarity.RARE, UpgradeCategory.ORBIT, 1,
        tr("EVOLUTION. Packet Nodes grow larger, hit harder and orbit wider."),
        { tr("Nodes +50% damage, +25% size") },
        { s, _ -> s.orbDamage *= 1.5f; s.orbSize *= 1.25f; s.orbRadius += 14f },
        evolvesFrom = "packet_nodes"
    )
    val SENTINEL_NODES = UpgradeDef(
        "sentinel_nodes", tr("SENTINEL NODES"), "{O}", Rarity.EPIC, UpgradeCategory.ORBIT, 1,
        tr("EVOLUTION. Two more nodes join the orbit and spin faster."),
        { tr("+2 Nodes, +30% orbit speed") },
        { s, _ -> s.orbCount += 2; s.orbAngularSpeed *= 1.3f },
        evolvesFrom = "enhanced_nodes", alsoRequires = "node_overclock"
    )
    val AUTONOMOUS_NODE = UpgradeDef(
        "autonomous_node", tr("AUTONOMOUS DEFENSE NODE"), "<O>", Rarity.LEGENDARY, UpgradeCategory.ORBIT, 1,
        tr("FINAL EVOLUTION. Every node independently fires packets at nearby threats."),
        { tr("Nodes fire autonomous bolts") },
        { s, _ -> s.orbBoltInterval = 1.4f; s.orbDamage *= 1.2f },
        evolvesFrom = "sentinel_nodes"
    )
    val NODE_OVERCLOCK = UpgradeDef(
        "node_overclock", tr("NODE OVERCLOCK"), "@>", Rarity.COMMON, UpgradeCategory.ORBIT, 3,
        tr("Packet Nodes spin faster and hit harder."),
        { tr("Nodes +25% speed, +20% damage") },
        { s, l -> repeat(l) { s.orbAngularSpeed *= 1.25f; s.orbDamage *= 1.2f } }
    )

    // --- Defense: Firewall evolution chain ----------------------------------

    // --- Weapons -------------------------------------------------------------
    val PACKET_SCATTER = UpgradeDef(
        "packet_scatter", tr("PACKET SCATTER"), "<:", Rarity.RARE, UpgradeCategory.WEAPON, 3,
        tr("Each volley also sprays a short-range cone of packets."),
        { l -> tr("Cone of {0} packets", 1 + 2 * l) },
        { s, l -> s.coneLevel = l }
    )
    val EXPLOIT_LANCE = UpgradeDef(
        "exploit_lance", tr("EXPLOIT LANCE"), "==>", Rarity.RARE, UpgradeCategory.WEAPON, 3,
        tr("A slow, heavy, piercing payload fired while you stand your ground."),
        { l -> tr("Lance every {0}s, {1}% dmg", "%.1f".format(2.8f - 0.4f * l), (250 + 50 * l)) },
        { s, l -> s.lanceLevel = l }
    )
    val PLASMA_BEAM = UpgradeDef(
        "plasma_beam", tr("PLASMA BEAM"), "=O=", Rarity.TITANIUM, UpgradeCategory.WEAPON, 3,
        tr("An arm cannon that pours a continuous beam into your target while you stand still. Pierces every threat in its path. Overheats after 5s of hits, then cools for 3s."),
        { l -> tr("Beam {0}% damage/s", (140 + 60 * l)) + if (l > 1) tr(", wider") else "" },
        { s, l -> s.beamLevel = l }
    )
    val ENCRYPTION_BLADES = UpgradeDef(
        "encryption_blades", tr("ENCRYPTION BLADES"), "/|\\", Rarity.RARE, UpgradeCategory.ORBIT, 3,
        tr("Close-orbit shields that cut enemies and block incoming packets."),
        { tr("+1 Encryption Blade") },
        { s, l -> s.bladeCount += l }
    )
    val EMP_BURST = UpgradeDef(
        "emp_burst", tr("EMP BURST"), "(*)", Rarity.UNCOMMON, UpgradeCategory.WEAPON, 3,
        tr("Periodically releases an electromagnetic pulse around you."),
        { l -> tr("Pulse every {0}s", 7 - l) },
        { s, l -> s.empLevel = l }
    )
    val BOTNET_CHAIN = UpgradeDef(
        "botnet_chain", tr("BOTNET CHAIN"), "o-o", Rarity.RARE, UpgradeCategory.WEAPON, 3,
        tr("Hits jump to another nearby threat."),
        { tr("+1 chain jump") },
        { s, l -> s.chain += l }
    )
    val PACKET_SPLIT = UpgradeDef(
        "packet_split", tr("PACKET SPLIT"), "||", Rarity.RARE, UpgradeCategory.WEAPON, 2,
        tr("Fire an additional parallel packet."),
        { tr("+1 parallel shot") },
        { s, l -> s.parallelShots += l; s.damage *= 1f - 0.05f * l }
    )
    val MULTISHOT = UpgradeDef(
        "multishot", tr("MULTISHOT"), ">>", Rarity.EPIC, UpgradeCategory.WEAPON, 2,
        tr("Every volley fires again in quick succession."),
        { tr("+1 follow-up volley (-10% dmg)") },
        { s, l -> s.followUpShots += l; s.damage *= 1f - 0.1f * l }
    )
    val DIAGONAL_ROUTING = UpgradeDef(
        "diagonal_routing", tr("DIAGONAL ROUTING"), "\\ /", Rarity.UNCOMMON, UpgradeCategory.WEAPON, 2,
        tr("Adds two packets angled off your main line."),
        { tr("+2 diagonal shots") },
        { s, l -> s.diagonalPairs += l }
    )
    val PENETRATION = UpgradeDef(
        "penetration", tr("PENETRATION"), "-|>", Rarity.RARE, UpgradeCategory.WEAPON, 2,
        tr("Packets pass through threats."),
        { tr("+1 pierce") },
        { s, l -> s.pierce += l }
    )
    val PACKET_BOUNCE = UpgradeDef(
        "packet_bounce", tr("PACKET BOUNCE"), "/\\/", Rarity.UNCOMMON, UpgradeCategory.WEAPON, 2,
        tr("Packets ricochet off walls and server racks."),
        { tr("+1 wall bounce") },
        { s, l -> s.bounce += l }
    )

    // --- More weapons (owner, 2026-10-08) -----------------------------------
    val LOGIC_BOMBS = UpgradeDef(
        "logic_bombs", tr("LOGIC BOMBS"), "[o]", Rarity.UNCOMMON, UpgradeCategory.WEAPON, 3,
        tr("Drop proximity mines while you move. They arm, wait, and detonate on the first threat that steps close."),
        { l -> tr("Up to {0} mines, {1}% blast", 2 + 2 * l, (160 + 40 * l)) },
        { s, l -> s.mineLevel = l }
    )
    val MALWARE_MISSILES = UpgradeDef(
        "malware_missiles", tr("MALWARE MISSILES"), "->*", Rarity.RARE, UpgradeCategory.WEAPON, 3,
        tr("Launches homing missiles at nearby threats, moving or not. Each bursts on impact."),
        { l -> tr("{0} missiles every {1}s", 1 + l, "%.1f".format(2.8f - 0.4f * l)) },
        { s, l -> s.missileLevel = l }
    )
    val ARC_DISCHARGE = UpgradeDef(
        "arc_discharge", tr("ARC DISCHARGE"), "~z~", Rarity.EPIC, UpgradeCategory.WEAPON, 3,
        tr("Lightning leaps from you to several nearby threats at once. Ignores cover."),
        { l -> tr("Hits {0} threats every {1}s", 2 + l, "%.1f".format(2.4f - 0.4f * l)) },
        { s, l -> s.arcLevel = l }
    )
    val QUANTUM_RAILGUN = UpgradeDef(
        "quantum_railgun", tr("QUANTUM RAILGUN"), "=|==>", Rarity.LEGENDARY, UpgradeCategory.WEAPON, 2,
        tr("While you stand still, a rail slug tears through every threat AND every wall in its line."),
        { l -> tr("{0}% damage, every {1}s", 600 + 200 * l, "%.1f".format(3.6f - 0.8f * l)) },
        { s, l -> s.railLevel = l }
    )
    val ORBITAL_STRIKE = UpgradeDef(
        "orbital_strike", tr("ORBITAL STRIKE"), "\\V/", Rarity.TITANIUM, UpgradeCategory.WEAPON, 1,
        tr("A satellite marks three threats and burns them from orbit every few seconds."),
        { tr("3 strikes of 800% every 5s") },
        { s, _ -> s.strikeLevel = 1 }
    )
    val TITANIUM_CHASSIS = UpgradeDef(
        "titanium_chassis", tr("TITANIUM CHASSIS"), "[TI]", Rarity.TITANIUM, UpgradeCategory.DEFENSE, 1,
        tr("A titanium-plated frame. Shrugs off what would shred anyone else."),
        { tr("+60% max HP, +150 armor") },
        { s, _ -> s.maxHp *= 1.6f; s.armor += 150f }
    )
    val OMEGA_OVERCLOCK = UpgradeDef(
        "omega_overclock", tr("OMEGA OVERCLOCK"), "<<!>>", Rarity.TITANIUM, UpgradeCategory.STAT, 1,
        tr("Every limiter removed. Everything you fire hits harder and faster."),
        { tr("+40% damage, +30% attack speed, +50% crit dmg") },
        { s, _ -> s.damage *= 1.4f; s.fireRate *= 1.3f; s.critMul += 0.5f }
    )
    val GOLDEN_PROTOCOL = UpgradeDef(
        "golden_protocol", tr("GOLDEN PROTOCOL"), "[$$]", Rarity.LEGENDARY, UpgradeCategory.UTILITY, 1,
        tr("A gilded exploit chain: richer payouts and sharper hits."),
        { tr("+50% €, +20% damage, +10% crit chance") },
        { s, _ -> s.euroMul *= 1.5f; s.damage *= 1.2f; s.critChance += 0.1f }
    )

    // --- GLITCHED LEGENDARY (owner, 0.39.0: "makes hard mode runs possible") ----------
    val GHOST_TRIGGER = UpgradeDef(
        "ghost_trigger", tr("GHOST TRIGGER"), "~>*", Rarity.GLITCHED, UpgradeCategory.STAT, 1,
        tr("A glitch in the fire control: your rifle keeps shooting while you move."),
        { tr("Fire on the move at 50% rate, +15% damage") },
        { s, _ -> s.moveFireRate = maxOf(s.moveFireRate, 0.5f); s.damage *= 1.15f }
    )
    val BUFFER_OVERFLOW = UpgradeDef(
        "buffer_overflow", tr("BUFFER OVERFLOW"), "[>>]", Rarity.GLITCHED, UpgradeCategory.STAT, 2,
        tr("Writes past the limit: more damage, faster shots."), { tr("+35% damage, +15% attack speed") },
        { s, l -> s.damage *= 1f + 0.35f * l; s.fireRate *= 1f + 0.15f * l }
    )
    val STACK_SMASH = UpgradeDef(
        "stack_smash", tr("STACK SMASH"), "<+>", Rarity.GLITCHED, UpgradeCategory.DEFENSE, 2,
        tr("Every hit leaks a little HP back to you."), { tr("Heal 3 HP per hit, +20% max HP") },
        { s, l -> s.healOnHit += 3f * l; s.maxHp *= 1f + 0.2f * l }
    )
    val PHASE_SKIP = UpgradeDef(
        "phase_skip", tr("PHASE SKIP"), ">|>", Rarity.GLITCHED, UpgradeCategory.DEFENSE, 1,
        tr("Skips frames: dash sooner and slip more hits."), { tr("Dash cooldown −30%, +6% dodge") },
        { s, _ -> s.dashCooldownMul *= 0.7f; s.dodge += 0.06f }
    )
    val ROLLBACK = UpgradeDef(
        "rollback", tr("ROLLBACK"), "<<3", Rarity.GLITCHED, UpgradeCategory.DEFENSE, 1,
        tr("Restores from a cleaner snapshot: more HP, better healing."), { tr("+40% max HP, +40% healing, HP potions +50%") },
        { s, _ -> s.maxHp *= 1.4f; s.healMul *= 1.4f; s.potionMul *= 1.5f }
    )

    // --- FORGED … ABYSSAL ------------------------------------------------------
    val MOLTEN_CORE = UpgradeDef(
        "molten_core", tr("MOLTEN CORE"), "{^}", Rarity.FORGED, UpgradeCategory.STAT, 1,
        tr("Shots come out of the crucible white-hot."), { tr("+45% damage, shots bounce once more") },
        { s, _ -> s.damage *= 1.45f; s.bounce += 1 }
    )
    val SLAG_PLATING = UpgradeDef(
        "slag_plating", tr("SLAG PLATING"), "[#]", Rarity.FORGED, UpgradeCategory.DEFENSE, 1,
        tr("Molten armor: hits spray slag back at them."), { tr("+200 armor, +40% max HP, +6 counter bolts") },
        { s, _ -> s.armor += 200f; s.maxHp *= 1.4f; s.counterRing += 6 }
    )
    val STORM_CIRCUIT = UpgradeDef(
        "storm_circuit", tr("STORM CIRCUIT"), "z>z", Rarity.VOLTAIC, UpgradeCategory.STAT, 1,
        tr("Live current in every bolt."), { tr("Shots arc to 2 more threats, +35% attack speed") },
        { s, _ -> s.chain += 2; s.fireRate *= 1.35f }
    )
    val OVERVOLT_NODES = UpgradeDef(
        "overvolt_nodes", tr("OVERVOLT NODES"), "(z)", Rarity.VOLTAIC, UpgradeCategory.ORBIT, 1,
        tr("Supercharged Packet Nodes that fire their own bolts."), { tr("+2 Packet Nodes, nodes fire bolts, +60% node damage") },
        { s, _ -> s.orbCount += 2; s.orbDamage *= 1.6f; s.orbBoltInterval = if (s.orbBoltInterval <= 0f) 1.2f else s.orbBoltInterval * 0.75f }
    )
    val HALO_PROTOCOL = UpgradeDef(
        "halo_protocol", tr("HALO PROTOCOL"), "(+)", Rarity.CELESTIAL, UpgradeCategory.DEFENSE, 1,
        tr("A guardian halo: tougher, and every kill patches you up."), { tr("+60% max HP, +50% healing, +8 HP per kill") },
        { s, _ -> s.maxHp *= 1.6f; s.healMul *= 1.5f; s.healOnKill += 8f }
    )
    val STARFALL_OPTICS = UpgradeDef(
        "starfall_optics", tr("STARFALL OPTICS"), "*|*", Rarity.CELESTIAL, UpgradeCategory.STAT, 1,
        tr("Star-tracked targeting."), { tr("+1 parallel shot, +15% critical chance, +60% critical damage") },
        { s, _ -> s.parallelShots += 1; s.critChance += 0.15f; s.critMul += 0.6f }
    )
    val INFERNO_PROTOCOL = UpgradeDef(
        "inferno_protocol", tr("INFERNO PROTOCOL"), "<#>", Rarity.ABYSSAL, UpgradeCategory.DEFENSE, 1,
        tr("Every bolt that lands burns HP back into you."), { tr("Heal 10 HP per hit") },
        { s, _ -> s.healOnHit += 10f }
    )
    val HELLGATE_ROUNDS = UpgradeDef(
        "hellgate_rounds", tr("HELLGATE ROUNDS"), "!#!", Rarity.ABYSSAL, UpgradeCategory.STAT, 1,
        tr("Rounds from the abyss."), { tr("+70% damage, +5% instant delete, +40% elite & boss damage") },
        { s, _ -> s.damage *= 1.7f; s.instantDeleteChance += 0.05f; s.eliteDamageMul += 0.4f }
    )

    // --- ENCRYPTED (owner, 0.39.0: "near game breaking buffs") ----------------------
    val ZERO_LATENCY = UpgradeDef(
        "enc_zero_latency", tr("ZERO LATENCY"), ">>|", Rarity.ENCRYPTED, UpgradeCategory.DEFENSE, 1,
        tr("Dash recharges twice as fast."), { tr("Dash cooldown −50%") },
        { s, _ -> s.dashCooldownMul *= 0.5f }
    )
    val RUN_AND_GUN = UpgradeDef(
        "enc_run_and_gun", tr("RUN & GUN"), "~>>", Rarity.ENCRYPTED, UpgradeCategory.STAT, 1,
        tr("No more standing still: your rifle fires at full rate while you move."), { tr("Shoot while moving, full rate") },
        { s, _ -> s.moveFireRate = 1f }
    )
    val DOUBLE_PAYLOAD = UpgradeDef(
        "enc_double_payload", tr("DOUBLE PAYLOAD"), "x2", Rarity.ENCRYPTED, UpgradeCategory.STAT, 1,
        tr("Everything you fire does double damage."), { tr("Damage ×2") },
        { s, _ -> s.damage *= 2f; s.orbDamage *= 2f; s.bladeDamage *= 2f }
    )
    val REGEN_MATRIX = UpgradeDef(
        "enc_regen_matrix", tr("REGEN MATRIX"), "+x2", Rarity.ENCRYPTED, UpgradeCategory.DEFENSE, 1,
        tr("Doubles healing from every source, HP potions included."), { tr("Healing ×2 (all sources)") },
        { s, _ -> s.healMul *= 2f; s.potionMul *= 2f }
    )
    val FULL_RESTORE = UpgradeDef(
        "enc_full_restore", tr("FULL RESTORE"), "[+]!", Rarity.ENCRYPTED, UpgradeCategory.DEFENSE, 1,
        tr("HP potions become abyssal draughts that heal you to full."), { tr("HP potions heal to full") },
        { s, _ -> s.potionFullHeal = true }
    )
    val FAILSAFE = UpgradeDef(
        "enc_failsafe", tr("FAILSAFE"), "!+!", Rarity.ENCRYPTED, UpgradeCategory.DEFENSE, 1,
        tr("HP potions drink themselves at 20% HP, or on a hit that would kill you (you survive it)."),
        { tr("Auto-potion at 20% HP or a lethal hit") },
        { s, _ -> s.autoPotion = true }
    )
    val PHANTOM_CODE = UpgradeDef(
        "enc_phantom_code", tr("PHANTOM CODE"), "~~", Rarity.ENCRYPTED, UpgradeCategory.DEFENSE, 1,
        tr("Doubles your dodge chance (up to 60%)."), { tr("Dodge ×2, +5% dodge") },
        { s, _ -> s.dodge += 0.05f; s.dodgeMul *= 2f }
    )
    val OVERCLOCK_SYNC = UpgradeDef(
        "enc_overclock_sync", tr("OVERCLOCK SYNC"), ">>>>", Rarity.ENCRYPTED, UpgradeCategory.STAT, 1,
        tr("You and your companion fire twice as fast."), { tr("Fire rate ×2 (you and your pet)") },
        { s, _ -> s.fireRate *= 2f; s.petRateMul *= 2f }
    )
    val VAMPIRIC_ROOT = UpgradeDef(
        "enc_vampiric_root", tr("VAMPIRIC ROOT"), "<3<", Rarity.ENCRYPTED, UpgradeCategory.DEFENSE, 1,
        tr("Root access to their lifeblood: heal 20 HP on every hit."), { tr("Heal 20 HP per hit") },
        { s, _ -> s.healOnHit += 20f }
    )

    // --- Stats ---------------------------------------------------------------
    val KERNEL_OVERCLOCK = UpgradeDef(
        "kernel_overclock", tr("KERNEL OVERCLOCK"), ">>>", Rarity.COMMON, UpgradeCategory.STAT, 5,
        tr("Raises attack speed."), { tr("+15% attack speed") },
        { s, l -> s.fireRate *= 1f + 0.15f * l }
    )
    val PAYLOAD_BOOST = UpgradeDef(
        "payload_boost", tr("PAYLOAD BOOST"), "+DMG", Rarity.COMMON, UpgradeCategory.STAT, 5,
        tr("Raises packet damage."), { tr("+15% damage") },
        { s, l -> s.damage *= 1f + 0.15f * l }
    )
    val CRITICAL_INJECTION = UpgradeDef(
        "critical_injection", tr("CRITICAL INJECTION"), "!*", Rarity.UNCOMMON, UpgradeCategory.STAT, 4,
        tr("More hits land as critical injections."), { tr("+8% critical chance") },
        { s, l -> s.critChance += 0.08f * l }
    )
    val ZERO_DAY_STRIKE = UpgradeDef(
        "zero_day_strike", tr("ZERO-DAY STRIKE"), "!!", Rarity.UNCOMMON, UpgradeCategory.STAT, 3,
        tr("Critical hits exploit unpatched flaws."), { tr("+40% critical damage") },
        { s, l -> s.critMul += 0.4f * l }
    )
    val EXPLOIT_CHANCE = UpgradeDef(
        "exploit_chance", tr("EXPLOIT CHANCE"), "0d", Rarity.EPIC, UpgradeCategory.STAT, 2,
        tr("Hits have a chance to delete a normal threat outright (never elites or bosses)."), { tr("+3% instant delete (not elites or bosses)") },
        { s, l -> s.instantDeleteChance += 0.03f * l }
    )
    val THREAT_DETECTION = UpgradeDef(
        "threat_detection", tr("THREAT DETECTION"), "(?)", Rarity.COMMON, UpgradeCategory.STAT, 3,
        tr("Longer range and faster packets."), { tr("+15% range & packet speed") },
        { s, l -> s.range *= 1f + 0.15f * l; s.projectileSpeed *= 1f + 0.15f * l }
    )
    val LOW_LATENCY = UpgradeDef(
        "low_latency", tr("LOW LATENCY"), "~>", Rarity.COMMON, UpgradeCategory.STAT, 3,
        tr("Move faster."), { tr("+10% movement speed") },
        { s, l -> s.moveSpeed *= 1f + 0.10f * l }
    )
    val MEMORY_EXPANSION = UpgradeDef(
        "memory_expansion", tr("MEMORY EXPANSION"), "+HP", Rarity.COMMON, UpgradeCategory.DEFENSE, 5,
        tr("Raises max HP."), { tr("+20% max HP") },
        { s, l -> s.maxHp *= 1f + 0.2f * l }
    )
    val DATA_LEECH = UpgradeDef(
        "data_leech", tr("DATA LEECH"), "<+", Rarity.UNCOMMON, UpgradeCategory.DEFENSE, 3,
        tr("Siphon data from destroyed threats to repair yourself."), { tr("+2 HP per kill") },
        { s, l -> s.healOnKill += 2f * l }
    )
    val ENCRYPTION_ARMOR = UpgradeDef(
        "encryption_armor", tr("ENCRYPTION ARMOR"), "[+]", Rarity.UNCOMMON, UpgradeCategory.DEFENSE, 4,
        tr("Hardened plating: armor cuts the damage you take."), { tr("+60 armor") },
        { s, l -> s.armor += 60f * l }
    )
    val PACKET_EVASION = UpgradeDef(
        "packet_evasion", tr("PACKET EVASION"), "~", Rarity.UNCOMMON, UpgradeCategory.DEFENSE, 3,
        tr("Chance for an attack to miss entirely."), { tr("+6% dodge") },
        { s, l -> s.dodge += 0.06f * l }
    )
    val INTRUSION_COUNTER = UpgradeDef(
        "intrusion_counter", tr("INTRUSION COUNTERMEASURE"), "<*>", Rarity.RARE, UpgradeCategory.DEFENSE, 2,
        tr("When you are hit, release a ring of retaliatory packets."), { tr("+6 counter packets on hit") },
        { s, l -> s.counterRing += 6 * l }
    )
    val MALWARE_PURGE = UpgradeDef(
        "malware_purge", tr("MALWARE PURGE"), "x!", Rarity.RARE, UpgradeCategory.STAT, 3,
        tr("Extra damage against elites and bosses."), { tr("+25% elite & boss damage") },
        { s, l -> s.eliteDamageMul += 0.25f * l }
    )
    val DATA_COMPRESSION = UpgradeDef(
        "data_compression", tr("DATA COMPRESSION"), "zip", Rarity.COMMON, UpgradeCategory.UTILITY, 3,
        tr("Gain upgrades faster."), { tr("+20% data (XP) gain") },
        { s, l -> s.xpMul += 0.2f * l }
    )
    val SYSTEM_RESTORE = UpgradeDef(
        "system_restore", tr("SYSTEM RESTORE"), "<3", Rarity.COMMON, UpgradeCategory.UTILITY, 999,
        tr("Restore a large part of your HP."), { tr("Heal 40% max HP") },
        { _, _ -> }, instant = true
    )

    val CRYPTO_CACHE = UpgradeDef(
        "crypto_cache", tr("CRYPTO CACHE"), "[€]", Rarity.COMMON, UpgradeCategory.UTILITY, 999,
        tr("Decrypt a cache of funds. Banked when the operation ends."), { tr("+€ now (scales with level)") },
        { _, _ -> }, instant = true
    )
    val DATA_DUMP = UpgradeDef(
        "data_dump", tr("DATA DUMP"), "{..}", Rarity.COMMON, UpgradeCategory.UTILITY, 999,
        tr("Absorb a dump of threat data toward your next upgrade."), { tr("+60% of an upgrade's data") },
        { _, _ -> }, instant = true
    )

    val all: List<UpgradeDef> = listOf(
        PACKET_NODES, ENHANCED_NODES, SENTINEL_NODES, AUTONOMOUS_NODE, NODE_OVERCLOCK,
        PACKET_SCATTER, EXPLOIT_LANCE, PLASMA_BEAM, ENCRYPTION_BLADES, EMP_BURST, BOTNET_CHAIN,
        PACKET_SPLIT, MULTISHOT, DIAGONAL_ROUTING, PENETRATION, PACKET_BOUNCE,
        KERNEL_OVERCLOCK, PAYLOAD_BOOST, CRITICAL_INJECTION, ZERO_DAY_STRIKE, EXPLOIT_CHANCE,
        THREAT_DETECTION, LOW_LATENCY, MEMORY_EXPANSION, DATA_LEECH,
        ENCRYPTION_ARMOR, PACKET_EVASION, INTRUSION_COUNTER, MALWARE_PURGE, DATA_COMPRESSION,
        SYSTEM_RESTORE, CRYPTO_CACHE, DATA_DUMP,
        LOGIC_BOMBS, MALWARE_MISSILES, ARC_DISCHARGE, QUANTUM_RAILGUN, ORBITAL_STRIKE,
        TITANIUM_CHASSIS, OMEGA_OVERCLOCK, GOLDEN_PROTOCOL,
        GHOST_TRIGGER, BUFFER_OVERFLOW, STACK_SMASH, PHASE_SKIP, ROLLBACK,
        MOLTEN_CORE, SLAG_PLATING, STORM_CIRCUIT, OVERVOLT_NODES, HALO_PROTOCOL, STARFALL_OPTICS,
        INFERNO_PROTOCOL, HELLGATE_ROUNDS,
        ZERO_LATENCY, RUN_AND_GUN, DOUBLE_PAYLOAD, REGEN_MATRIX, FULL_RESTORE, FAILSAFE,
        PHANTOM_CODE, OVERCLOCK_SYNC, VAMPIRIC_ROOT
    ) + Weapons.upgrades

    private val byId = all.associateBy { it.id }

    fun byId(id: String): UpgradeDef = byId[id] ?: error("Unknown upgrade $id")
    fun byIdOrNull(id: String): UpgradeDef? = byId[id]

    /**
     * Weapons take a weapon slot (owner, 2026-10-09: at most [MAX_WEAPONS]).
     * Every arsenal weapon plus the built-in attacks; gun modifiers (Multishot,
     * Penetration…) and the starting Packet Nodes are power-ups, not weapons.
     */
    private val builtInWeapons = setOf(
        "packet_scatter", "exploit_lance", "plasma_beam", "encryption_blades", "emp_burst",
        "logic_bombs", "malware_missiles", "arc_discharge", "quantum_railgun", "orbital_strike"
    )
    private val weaponIds: Set<String> by lazy { builtInWeapons + Weapons.upgrades.map { it.id } }

    fun isWeapon(def: UpgradeDef): Boolean = def.id in weaponIds
    fun isWeapon(id: String): Boolean = id in weaponIds

    /** Weapon slots per operative. */
    const val MAX_WEAPONS = 7

    /** Roman numerals for card titles ("PACKET NODES II"). */
    fun roman(n: Int): String = when (n) {
        1 -> "I"; 2 -> "II"; 3 -> "III"; 4 -> "IV"; 5 -> "V"
        6 -> "VI"; 7 -> "VII"; 8 -> "VIII"; 9 -> "IX"; 10 -> "X"
        else -> n.toString()
    }
}
