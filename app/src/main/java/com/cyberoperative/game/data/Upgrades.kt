package com.cyberoperative.game.data

import com.cyberoperative.game.engine.RunStats
import com.cyberoperative.game.data.ArenaShape

/**
 * Mod tiers (owner, 2026-10-08): BLUE rares are the everyday "good" pull;
 * PURPLE, GOLDEN and TITANIUM are progressively rarer. Their odds rise with
 * level, difficulty and boss rewards (see RunBuild.rollOffer luck).
 */
enum class Rarity(val label: String, val color: Long, val weight: Float, /** Luck steps: 0 BLUE and below, 1 PURPLE, 2 GOLDEN, 3 above. */ val highTier: Int) {
    COMMON("COMMON", 0xFFB8C4D6, 100f, 0),
    UNCOMMON("UNCOMMON", 0xFF00FF9C, 55f, 0),
    RARE("BLUE RARE", 0xFF2E9BFF, 26f, 0),
    EPIC("PURPLE", 0xFFA259FF, 7f, 1),
    LEGENDARY("GOLDEN", 0xFFFFD426, 2.5f, 2),
    // The equipment tiers as chips (owner, 0.39.0): very strong, rarer the higher they go.
    GLITCHED("GLITCHED LEGENDARY", 0xFFFF3DF2, 1.5f, 3),
    TITANIUM("TITANIUM", 0xFFDCE8F2, 0.8f, 3),
    FORGED("FORGED", 0xFFFF8A1F, 0.9f, 3),
    VOLTAIC("VOLTAIC", 0xFFFFF04D, 0.75f, 3),
    CELESTIAL("CELESTIAL", 0xFFBFD4FF, 0.6f, 3),
    ABYSSAL("ABYSSAL", 0xFFFF2B3A, 0.45f, 3),
    /** Near game-breaking (owner, 0.39.0): very rare on every difficulty. */
    ENCRYPTED("ENCRYPTED", 0xFF00F0FF, 0.16f, 3);

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
        return "MASTERY ${level - maxLevel}: " + when (category) {
            UpgradeCategory.WEAPON -> "build damage up (shared mastery)"
            UpgradeCategory.ORBIT -> "node & blade damage up (shared mastery)"
            UpgradeCategory.DEFENSE -> "max HP & Firewall up (shared mastery)"
            UpgradeCategory.STAT -> "build damage & attack speed up (shared mastery)"
            UpgradeCategory.UTILITY -> "+${pct(Upgrades.MASTERY_UTILITY)}% € and data"
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
        "packet_nodes", "PACKET NODES", "(o)", Rarity.UNCOMMON, UpgradeCategory.ORBIT, 3,
        "Adds an orbiting Packet Node. Nodes never stop attacking.",
        { "+1 Orbiting Packet Node" },
        { s, l -> s.orbCount += l }
    )
    val ENHANCED_NODES = UpgradeDef(
        "enhanced_nodes", "ENHANCED NODES", "(O)", Rarity.RARE, UpgradeCategory.ORBIT, 1,
        "EVOLUTION. Packet Nodes grow larger, hit harder and orbit wider.",
        { "Nodes +50% damage, +25% size" },
        { s, _ -> s.orbDamage *= 1.5f; s.orbSize *= 1.25f; s.orbRadius += 14f },
        evolvesFrom = "packet_nodes"
    )
    val SENTINEL_NODES = UpgradeDef(
        "sentinel_nodes", "SENTINEL NODES", "{O}", Rarity.EPIC, UpgradeCategory.ORBIT, 1,
        "EVOLUTION. Two more nodes join the orbit and spin faster.",
        { "+2 Nodes, +30% orbit speed" },
        { s, _ -> s.orbCount += 2; s.orbAngularSpeed *= 1.3f },
        evolvesFrom = "enhanced_nodes", alsoRequires = "node_overclock"
    )
    val AUTONOMOUS_NODE = UpgradeDef(
        "autonomous_node", "AUTONOMOUS DEFENSE NODE", "<O>", Rarity.LEGENDARY, UpgradeCategory.ORBIT, 1,
        "FINAL EVOLUTION. Every node independently fires packets at nearby threats.",
        { "Nodes fire autonomous bolts" },
        { s, _ -> s.orbBoltInterval = 1.4f; s.orbDamage *= 1.2f },
        evolvesFrom = "sentinel_nodes"
    )
    val NODE_OVERCLOCK = UpgradeDef(
        "node_overclock", "NODE OVERCLOCK", "@>", Rarity.COMMON, UpgradeCategory.ORBIT, 3,
        "Packet Nodes spin faster and hit harder.",
        { "Nodes +25% speed, +20% damage" },
        { s, l -> repeat(l) { s.orbAngularSpeed *= 1.25f; s.orbDamage *= 1.2f } }
    )

    // --- Defense: Firewall evolution chain ----------------------------------

    // --- Weapons -------------------------------------------------------------
    val PACKET_SCATTER = UpgradeDef(
        "packet_scatter", "PACKET SCATTER", "<:", Rarity.RARE, UpgradeCategory.WEAPON, 3,
        "Each volley also sprays a short-range cone of packets.",
        { l -> "Cone of ${1 + 2 * l} packets" },
        { s, l -> s.coneLevel = l }
    )
    val EXPLOIT_LANCE = UpgradeDef(
        "exploit_lance", "EXPLOIT LANCE", "==>", Rarity.RARE, UpgradeCategory.WEAPON, 3,
        "A slow, heavy, piercing payload fired while you stand your ground.",
        { l -> "Lance every ${"%.1f".format(2.8f - 0.4f * l)}s, ${(250 + 50 * l)}% dmg" },
        { s, l -> s.lanceLevel = l }
    )
    val PLASMA_BEAM = UpgradeDef(
        "plasma_beam", "PLASMA BEAM", "=O=", Rarity.TITANIUM, UpgradeCategory.WEAPON, 3,
        "An arm cannon that pours a continuous beam into your target while you stand still. Pierces every threat in its path. Overheats after 5s of hits, then cools for 3s.",
        { l -> "Beam ${(140 + 60 * l)}% damage/s" + if (l > 1) ", wider" else "" },
        { s, l -> s.beamLevel = l }
    )
    val ENCRYPTION_BLADES = UpgradeDef(
        "encryption_blades", "ENCRYPTION BLADES", "/|\\", Rarity.RARE, UpgradeCategory.ORBIT, 3,
        "Close-orbit shields that cut enemies and block incoming packets.",
        { "+1 Encryption Blade" },
        { s, l -> s.bladeCount += l }
    )
    val EMP_BURST = UpgradeDef(
        "emp_burst", "EMP BURST", "(*)", Rarity.UNCOMMON, UpgradeCategory.WEAPON, 3,
        "Periodically releases an electromagnetic pulse around you.",
        { l -> "Pulse every ${7 - l}s" },
        { s, l -> s.empLevel = l }
    )
    val BOTNET_CHAIN = UpgradeDef(
        "botnet_chain", "BOTNET CHAIN", "o-o", Rarity.RARE, UpgradeCategory.WEAPON, 3,
        "Hits jump to another nearby threat.",
        { "+1 chain jump" },
        { s, l -> s.chain += l }
    )
    val PACKET_SPLIT = UpgradeDef(
        "packet_split", "PACKET SPLIT", "||", Rarity.RARE, UpgradeCategory.WEAPON, 2,
        "Fire an additional parallel packet.",
        { "+1 parallel shot" },
        { s, l -> s.parallelShots += l; s.damage *= 1f - 0.05f * l }
    )
    val MULTISHOT = UpgradeDef(
        "multishot", "MULTISHOT", ">>", Rarity.EPIC, UpgradeCategory.WEAPON, 2,
        "Every volley fires again in quick succession.",
        { "+1 follow-up volley (-10% dmg)" },
        { s, l -> s.followUpShots += l; s.damage *= 1f - 0.1f * l }
    )
    val DIAGONAL_ROUTING = UpgradeDef(
        "diagonal_routing", "DIAGONAL ROUTING", "\\ /", Rarity.UNCOMMON, UpgradeCategory.WEAPON, 2,
        "Adds two packets angled off your main line.",
        { "+2 diagonal shots" },
        { s, l -> s.diagonalPairs += l }
    )
    val PENETRATION = UpgradeDef(
        "penetration", "PENETRATION", "-|>", Rarity.RARE, UpgradeCategory.WEAPON, 2,
        "Packets pass through threats.",
        { "+1 pierce" },
        { s, l -> s.pierce += l }
    )
    val PACKET_BOUNCE = UpgradeDef(
        "packet_bounce", "PACKET BOUNCE", "/\\/", Rarity.UNCOMMON, UpgradeCategory.WEAPON, 2,
        "Packets ricochet off walls and server racks.",
        { "+1 wall bounce" },
        { s, l -> s.bounce += l }
    )

    // --- More weapons (owner, 2026-10-08) -----------------------------------
    val LOGIC_BOMBS = UpgradeDef(
        "logic_bombs", "LOGIC BOMBS", "[o]", Rarity.UNCOMMON, UpgradeCategory.WEAPON, 3,
        "Drop proximity mines while you move. They arm, wait, and detonate on the first threat that steps close.",
        { l -> "Up to ${2 + 2 * l} mines, ${(160 + 40 * l)}% blast" },
        { s, l -> s.mineLevel = l }
    )
    val MALWARE_MISSILES = UpgradeDef(
        "malware_missiles", "MALWARE MISSILES", "->*", Rarity.RARE, UpgradeCategory.WEAPON, 3,
        "Launches homing missiles at nearby threats, moving or not. Each bursts on impact.",
        { l -> "${1 + l} missiles every ${"%.1f".format(2.8f - 0.4f * l)}s" },
        { s, l -> s.missileLevel = l }
    )
    val ARC_DISCHARGE = UpgradeDef(
        "arc_discharge", "ARC DISCHARGE", "~z~", Rarity.EPIC, UpgradeCategory.WEAPON, 3,
        "Lightning leaps from you to several nearby threats at once. Ignores cover.",
        { l -> "Hits ${2 + l} threats every ${"%.1f".format(2.4f - 0.4f * l)}s" },
        { s, l -> s.arcLevel = l }
    )
    val QUANTUM_RAILGUN = UpgradeDef(
        "quantum_railgun", "QUANTUM RAILGUN", "=|==>", Rarity.LEGENDARY, UpgradeCategory.WEAPON, 2,
        "While you stand still, a rail slug tears through every threat AND every wall in its line.",
        { l -> "${600 + 200 * l}% damage, every ${"%.1f".format(3.6f - 0.8f * l)}s" },
        { s, l -> s.railLevel = l }
    )
    val ORBITAL_STRIKE = UpgradeDef(
        "orbital_strike", "ORBITAL STRIKE", "\\V/", Rarity.TITANIUM, UpgradeCategory.WEAPON, 1,
        "A satellite marks three threats and burns them from orbit every few seconds.",
        { "3 strikes of 800% every 5s" },
        { s, _ -> s.strikeLevel = 1 }
    )
    val TITANIUM_CHASSIS = UpgradeDef(
        "titanium_chassis", "TITANIUM CHASSIS", "[TI]", Rarity.TITANIUM, UpgradeCategory.DEFENSE, 1,
        "A titanium-plated frame. Shrugs off what would shred anyone else.",
        { "+60% max HP, +150 armor" },
        { s, _ -> s.maxHp *= 1.6f; s.armor += 150f }
    )
    val OMEGA_OVERCLOCK = UpgradeDef(
        "omega_overclock", "OMEGA OVERCLOCK", "<<!>>", Rarity.TITANIUM, UpgradeCategory.STAT, 1,
        "Every limiter removed. Everything you fire hits harder and faster.",
        { "+40% damage, +30% attack speed, +50% crit dmg" },
        { s, _ -> s.damage *= 1.4f; s.fireRate *= 1.3f; s.critMul += 0.5f }
    )
    val GOLDEN_PROTOCOL = UpgradeDef(
        "golden_protocol", "GOLDEN PROTOCOL", "[$$]", Rarity.LEGENDARY, UpgradeCategory.UTILITY, 1,
        "A gilded exploit chain: richer payouts and sharper hits.",
        { "+50% €, +20% damage, +10% crit chance" },
        { s, _ -> s.euroMul *= 1.5f; s.damage *= 1.2f; s.critChance += 0.1f }
    )

    // --- GLITCHED LEGENDARY (owner, 0.39.0: "makes hard mode runs possible") ----------
    val GHOST_TRIGGER = UpgradeDef(
        "ghost_trigger", "GHOST TRIGGER", "~>*", Rarity.GLITCHED, UpgradeCategory.STAT, 1,
        "A glitch in the fire control: your rifle keeps shooting while you move.",
        { "Fire on the move at 50% rate, +15% damage" },
        { s, _ -> s.moveFireRate = maxOf(s.moveFireRate, 0.5f); s.damage *= 1.15f }
    )
    val BUFFER_OVERFLOW = UpgradeDef(
        "buffer_overflow", "BUFFER OVERFLOW", "[>>]", Rarity.GLITCHED, UpgradeCategory.STAT, 2,
        "Writes past the limit: more damage, faster shots.", { "+35% damage, +15% attack speed" },
        { s, l -> s.damage *= 1f + 0.35f * l; s.fireRate *= 1f + 0.15f * l }
    )
    val STACK_SMASH = UpgradeDef(
        "stack_smash", "STACK SMASH", "<+>", Rarity.GLITCHED, UpgradeCategory.DEFENSE, 2,
        "Every hit leaks a little HP back to you.", { "Heal 3 HP per hit, +20% max HP" },
        { s, l -> s.healOnHit += 3f * l; s.maxHp *= 1f + 0.2f * l }
    )
    val PHASE_SKIP = UpgradeDef(
        "phase_skip", "PHASE SKIP", ">|>", Rarity.GLITCHED, UpgradeCategory.DEFENSE, 1,
        "Skips frames: dash sooner and slip more hits.", { "Dash cooldown −30%, +6% dodge" },
        { s, _ -> s.dashCooldownMul *= 0.7f; s.dodge += 0.06f }
    )
    val ROLLBACK = UpgradeDef(
        "rollback", "ROLLBACK", "<<3", Rarity.GLITCHED, UpgradeCategory.DEFENSE, 1,
        "Restores from a cleaner snapshot: more HP, better healing.", { "+40% max HP, +40% healing, HP potions +50%" },
        { s, _ -> s.maxHp *= 1.4f; s.healMul *= 1.4f; s.potionMul *= 1.5f }
    )

    // --- FORGED … ABYSSAL ------------------------------------------------------
    val MOLTEN_CORE = UpgradeDef(
        "molten_core", "MOLTEN CORE", "{^}", Rarity.FORGED, UpgradeCategory.STAT, 1,
        "Shots come out of the crucible white-hot.", { "+45% damage, shots bounce once more" },
        { s, _ -> s.damage *= 1.45f; s.bounce += 1 }
    )
    val SLAG_PLATING = UpgradeDef(
        "slag_plating", "SLAG PLATING", "[#]", Rarity.FORGED, UpgradeCategory.DEFENSE, 1,
        "Molten armor: hits spray slag back at them.", { "+200 armor, +40% max HP, +6 counter bolts" },
        { s, _ -> s.armor += 200f; s.maxHp *= 1.4f; s.counterRing += 6 }
    )
    val STORM_CIRCUIT = UpgradeDef(
        "storm_circuit", "STORM CIRCUIT", "z>z", Rarity.VOLTAIC, UpgradeCategory.STAT, 1,
        "Live current in every bolt.", { "Shots arc to 2 more threats, +35% attack speed" },
        { s, _ -> s.chain += 2; s.fireRate *= 1.35f }
    )
    val OVERVOLT_NODES = UpgradeDef(
        "overvolt_nodes", "OVERVOLT NODES", "(z)", Rarity.VOLTAIC, UpgradeCategory.ORBIT, 1,
        "Supercharged Packet Nodes that fire their own bolts.", { "+2 Packet Nodes, nodes fire bolts, +60% node damage" },
        { s, _ -> s.orbCount += 2; s.orbDamage *= 1.6f; s.orbBoltInterval = if (s.orbBoltInterval <= 0f) 1.2f else s.orbBoltInterval * 0.75f }
    )
    val HALO_PROTOCOL = UpgradeDef(
        "halo_protocol", "HALO PROTOCOL", "(+)", Rarity.CELESTIAL, UpgradeCategory.DEFENSE, 1,
        "A guardian halo: tougher, and every kill patches you up.", { "+60% max HP, +50% healing, +8 HP per kill" },
        { s, _ -> s.maxHp *= 1.6f; s.healMul *= 1.5f; s.healOnKill += 8f }
    )
    val STARFALL_OPTICS = UpgradeDef(
        "starfall_optics", "STARFALL OPTICS", "*|*", Rarity.CELESTIAL, UpgradeCategory.STAT, 1,
        "Star-tracked targeting.", { "+1 parallel shot, +15% critical chance, +60% critical damage" },
        { s, _ -> s.parallelShots += 1; s.critChance += 0.15f; s.critMul += 0.6f }
    )
    val INFERNO_PROTOCOL = UpgradeDef(
        "inferno_protocol", "INFERNO PROTOCOL", "<#>", Rarity.ABYSSAL, UpgradeCategory.DEFENSE, 1,
        "Every bolt that lands burns HP back into you.", { "Heal 10 HP per hit" },
        { s, _ -> s.healOnHit += 10f }
    )
    val HELLGATE_ROUNDS = UpgradeDef(
        "hellgate_rounds", "HELLGATE ROUNDS", "!#!", Rarity.ABYSSAL, UpgradeCategory.STAT, 1,
        "Rounds from the abyss.", { "+70% damage, +5% instant delete, +40% elite & boss damage" },
        { s, _ -> s.damage *= 1.7f; s.instantDeleteChance += 0.05f; s.eliteDamageMul += 0.4f }
    )

    // --- ENCRYPTED (owner, 0.39.0: "near game breaking buffs") ----------------------
    val ZERO_LATENCY = UpgradeDef(
        "enc_zero_latency", "ZERO LATENCY", ">>|", Rarity.ENCRYPTED, UpgradeCategory.DEFENSE, 1,
        "Dash recharges twice as fast.", { "Dash cooldown −50%" },
        { s, _ -> s.dashCooldownMul *= 0.5f }
    )
    val RUN_AND_GUN = UpgradeDef(
        "enc_run_and_gun", "RUN & GUN", "~>>", Rarity.ENCRYPTED, UpgradeCategory.STAT, 1,
        "No more standing still: your rifle fires at full rate while you move.", { "Shoot while moving, full rate" },
        { s, _ -> s.moveFireRate = 1f }
    )
    val DOUBLE_PAYLOAD = UpgradeDef(
        "enc_double_payload", "DOUBLE PAYLOAD", "x2", Rarity.ENCRYPTED, UpgradeCategory.STAT, 1,
        "Everything you fire does double damage.", { "Damage ×2" },
        { s, _ -> s.damage *= 2f; s.orbDamage *= 2f; s.bladeDamage *= 2f }
    )
    val REGEN_MATRIX = UpgradeDef(
        "enc_regen_matrix", "REGEN MATRIX", "+x2", Rarity.ENCRYPTED, UpgradeCategory.DEFENSE, 1,
        "Doubles healing from every source, HP potions included.", { "Healing ×2 (all sources)" },
        { s, _ -> s.healMul *= 2f; s.potionMul *= 2f }
    )
    val FULL_RESTORE = UpgradeDef(
        "enc_full_restore", "FULL RESTORE", "[+]!", Rarity.ENCRYPTED, UpgradeCategory.DEFENSE, 1,
        "HP potions become abyssal draughts that heal you to full.", { "HP potions heal to full" },
        { s, _ -> s.potionFullHeal = true }
    )
    val FAILSAFE = UpgradeDef(
        "enc_failsafe", "FAILSAFE", "!+!", Rarity.ENCRYPTED, UpgradeCategory.DEFENSE, 1,
        "HP potions drink themselves at 20% HP, or on a hit that would kill you (you survive it).",
        { "Auto-potion at 20% HP or a lethal hit" },
        { s, _ -> s.autoPotion = true }
    )
    val PHANTOM_CODE = UpgradeDef(
        "enc_phantom_code", "PHANTOM CODE", "~~", Rarity.ENCRYPTED, UpgradeCategory.DEFENSE, 1,
        "Doubles your dodge chance (up to 60%).", { "Dodge ×2, +5% dodge" },
        { s, _ -> s.dodge += 0.05f; s.dodgeMul *= 2f }
    )
    val OVERCLOCK_SYNC = UpgradeDef(
        "enc_overclock_sync", "OVERCLOCK SYNC", ">>>>", Rarity.ENCRYPTED, UpgradeCategory.STAT, 1,
        "You and your companion fire twice as fast.", { "Fire rate ×2 (you and your pet)" },
        { s, _ -> s.fireRate *= 2f; s.petRateMul *= 2f }
    )
    val VAMPIRIC_ROOT = UpgradeDef(
        "enc_vampiric_root", "VAMPIRIC ROOT", "<3<", Rarity.ENCRYPTED, UpgradeCategory.DEFENSE, 1,
        "Root access to their lifeblood: heal 20 HP on every hit.", { "Heal 20 HP per hit" },
        { s, _ -> s.healOnHit += 20f }
    )

    // --- Stats ---------------------------------------------------------------
    val KERNEL_OVERCLOCK = UpgradeDef(
        "kernel_overclock", "KERNEL OVERCLOCK", ">>>", Rarity.COMMON, UpgradeCategory.STAT, 5,
        "Raises attack speed.", { "+15% attack speed" },
        { s, l -> s.fireRate *= 1f + 0.15f * l }
    )
    val PAYLOAD_BOOST = UpgradeDef(
        "payload_boost", "PAYLOAD BOOST", "+DMG", Rarity.COMMON, UpgradeCategory.STAT, 5,
        "Raises packet damage.", { "+15% damage" },
        { s, l -> s.damage *= 1f + 0.15f * l }
    )
    val CRITICAL_INJECTION = UpgradeDef(
        "critical_injection", "CRITICAL INJECTION", "!*", Rarity.UNCOMMON, UpgradeCategory.STAT, 4,
        "More hits land as critical injections.", { "+8% critical chance" },
        { s, l -> s.critChance += 0.08f * l }
    )
    val ZERO_DAY_STRIKE = UpgradeDef(
        "zero_day_strike", "ZERO-DAY STRIKE", "!!", Rarity.UNCOMMON, UpgradeCategory.STAT, 3,
        "Critical hits exploit unpatched flaws.", { "+40% critical damage" },
        { s, l -> s.critMul += 0.4f * l }
    )
    val EXPLOIT_CHANCE = UpgradeDef(
        "exploit_chance", "EXPLOIT CHANCE", "0d", Rarity.EPIC, UpgradeCategory.STAT, 2,
        "Hits have a chance to delete a normal threat outright (never elites or bosses).", { "+3% instant delete (not elites or bosses)" },
        { s, l -> s.instantDeleteChance += 0.03f * l }
    )
    val THREAT_DETECTION = UpgradeDef(
        "threat_detection", "THREAT DETECTION", "(?)", Rarity.COMMON, UpgradeCategory.STAT, 3,
        "Longer range and faster packets.", { "+15% range & packet speed" },
        { s, l -> s.range *= 1f + 0.15f * l; s.projectileSpeed *= 1f + 0.15f * l }
    )
    val LOW_LATENCY = UpgradeDef(
        "low_latency", "LOW LATENCY", "~>", Rarity.COMMON, UpgradeCategory.STAT, 3,
        "Move faster.", { "+10% movement speed" },
        { s, l -> s.moveSpeed *= 1f + 0.10f * l }
    )
    val MEMORY_EXPANSION = UpgradeDef(
        "memory_expansion", "MEMORY EXPANSION", "+HP", Rarity.COMMON, UpgradeCategory.DEFENSE, 5,
        "Raises max HP.", { "+20% max HP" },
        { s, l -> s.maxHp *= 1f + 0.2f * l }
    )
    val DATA_LEECH = UpgradeDef(
        "data_leech", "DATA LEECH", "<+", Rarity.UNCOMMON, UpgradeCategory.DEFENSE, 3,
        "Siphon data from destroyed threats to repair yourself.", { "+2 HP per kill" },
        { s, l -> s.healOnKill += 2f * l }
    )
    val ENCRYPTION_ARMOR = UpgradeDef(
        "encryption_armor", "ENCRYPTION ARMOR", "[+]", Rarity.UNCOMMON, UpgradeCategory.DEFENSE, 4,
        "Hardened plating: armor cuts the damage you take.", { "+60 armor" },
        { s, l -> s.armor += 60f * l }
    )
    val PACKET_EVASION = UpgradeDef(
        "packet_evasion", "PACKET EVASION", "~", Rarity.UNCOMMON, UpgradeCategory.DEFENSE, 3,
        "Chance for an attack to miss entirely.", { "+6% dodge" },
        { s, l -> s.dodge += 0.06f * l }
    )
    val INTRUSION_COUNTER = UpgradeDef(
        "intrusion_counter", "INTRUSION COUNTERMEASURE", "<*>", Rarity.RARE, UpgradeCategory.DEFENSE, 2,
        "When you are hit, release a ring of retaliatory packets.", { "+6 counter packets on hit" },
        { s, l -> s.counterRing += 6 * l }
    )
    val MALWARE_PURGE = UpgradeDef(
        "malware_purge", "MALWARE PURGE", "x!", Rarity.RARE, UpgradeCategory.STAT, 3,
        "Extra damage against elites and bosses.", { "+25% elite & boss damage" },
        { s, l -> s.eliteDamageMul += 0.25f * l }
    )
    val DATA_COMPRESSION = UpgradeDef(
        "data_compression", "DATA COMPRESSION", "zip", Rarity.COMMON, UpgradeCategory.UTILITY, 3,
        "Gain upgrades faster.", { "+20% data (XP) gain" },
        { s, l -> s.xpMul += 0.2f * l }
    )
    val SYSTEM_RESTORE = UpgradeDef(
        "system_restore", "SYSTEM RESTORE", "<3", Rarity.COMMON, UpgradeCategory.UTILITY, 999,
        "Restore a large part of your HP.", { "Heal 40% max HP" },
        { _, _ -> }, instant = true
    )

    val CRYPTO_CACHE = UpgradeDef(
        "crypto_cache", "CRYPTO CACHE", "[€]", Rarity.COMMON, UpgradeCategory.UTILITY, 999,
        "Decrypt a cache of funds. Banked when the operation ends.", { "+€ now (scales with level)" },
        { _, _ -> }, instant = true
    )
    val DATA_DUMP = UpgradeDef(
        "data_dump", "DATA DUMP", "{..}", Rarity.COMMON, UpgradeCategory.UTILITY, 999,
        "Absorb a dump of threat data toward your next upgrade.", { "+60% of an upgrade's data" },
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
