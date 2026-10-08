package com.cyberoperative.game.data

import com.cyberoperative.game.engine.RunStats

enum class Rarity(val label: String, val color: Long, val weight: Float) {
    COMMON("COMMON", 0xFFB8C4D6, 100f),
    UNCOMMON("UNCOMMON", 0xFF00FF9C, 55f),
    RARE("RARE", 0xFF2E9BFF, 26f),
    EPIC("EPIC", 0xFFA259FF, 10f),
    LEGENDARY("LEGENDARY", 0xFFFFD426, 4f)
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
    val instant: Boolean = false
)

object Upgrades {


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
    val FIREWALL = UpgradeDef(
        "firewall", "FIREWALL", "[#]", Rarity.UNCOMMON, UpgradeCategory.DEFENSE, 3,
        "A recharging barrier that absorbs damage before your HP.",
        { "+25 Firewall (recharges)" },
        { s, l -> s.firewallMax += 25f * l }
    )
    val REINFORCED_FIREWALL = UpgradeDef(
        "reinforced_firewall", "REINFORCED FIREWALL", "[##]", Rarity.RARE, UpgradeCategory.DEFENSE, 1,
        "EVOLUTION. Thicker rules, faster recovery.",
        { "Firewall +50%, recharge 1s sooner" },
        { s, _ -> s.firewallMax *= 1.5f; s.firewallDelay -= 1f },
        evolvesFrom = "firewall"
    )
    val ADAPTIVE_FIREWALL = UpgradeDef(
        "adaptive_firewall", "ADAPTIVE FIREWALL", "[#~]", Rarity.EPIC, UpgradeCategory.DEFENSE, 1,
        "EVOLUTION. Learns from every threat: kills restore Firewall.",
        { "Each kill restores 8% Firewall" },
        { s, _ -> s.firewallOnKill += 0.08f; s.firewallRate *= 1.4f },
        evolvesFrom = "reinforced_firewall"
    )
    val ZERO_TRUST = UpgradeDef(
        "zero_trust", "ZERO TRUST FORTRESS", "[ZT]", Rarity.LEGENDARY, UpgradeCategory.DEFENSE, 1,
        "FINAL EVOLUTION. Never trust, always verify: a broken Firewall detonates outward.",
        { "Firewall x2, breaking it releases a blast" },
        { s, _ -> s.firewallMax *= 2f; s.firewallBreakPulse = 3.5f },
        evolvesFrom = "adaptive_firewall"
    )

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
        "plasma_beam", "PLASMA BEAM", "=O=", Rarity.RARE, UpgradeCategory.WEAPON, 3,
        "An arm cannon that pours a continuous beam into your target while you stand still. Pierces every threat in its path.",
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
    val PROXY_SHOT = UpgradeDef(
        "proxy_shot", "PROXY SHOT", "<->", Rarity.UNCOMMON, UpgradeCategory.WEAPON, 1,
        "A proxy relays a copy of each volley behind you.",
        { "Adds a rear shot" },
        { s, _ -> s.rearShot = true }
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
        "Hits have a chance to delete a non-boss threat outright.", { "+3% instant delete" },
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
    val SELF_REPAIR = UpgradeDef(
        "self_repair", "SELF-REPAIR", "+~", Rarity.UNCOMMON, UpgradeCategory.DEFENSE, 3,
        "Slowly regenerate HP.", { "Regenerate 0.6% HP/s" },
        { s, l -> s.regenPerSec += 0.006f * l }
    )
    val DATA_LEECH = UpgradeDef(
        "data_leech", "DATA LEECH", "<+", Rarity.UNCOMMON, UpgradeCategory.DEFENSE, 3,
        "Siphon data from destroyed threats to repair yourself.", { "+2 HP per kill" },
        { s, l -> s.healOnKill += 2f * l }
    )
    val ENCRYPTION_ARMOR = UpgradeDef(
        "encryption_armor", "ENCRYPTION ARMOR", "[+]", Rarity.UNCOMMON, UpgradeCategory.DEFENSE, 4,
        "Reduces all incoming damage.", { "-8% damage taken" },
        { s, l -> s.armor += 0.08f * l }
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
        FIREWALL, REINFORCED_FIREWALL, ADAPTIVE_FIREWALL, ZERO_TRUST,
        PACKET_SCATTER, EXPLOIT_LANCE, PLASMA_BEAM, ENCRYPTION_BLADES, EMP_BURST, BOTNET_CHAIN,
        PACKET_SPLIT, MULTISHOT, DIAGONAL_ROUTING, PROXY_SHOT, PENETRATION, PACKET_BOUNCE,
        KERNEL_OVERCLOCK, PAYLOAD_BOOST, CRITICAL_INJECTION, ZERO_DAY_STRIKE, EXPLOIT_CHANCE,
        THREAT_DETECTION, LOW_LATENCY, MEMORY_EXPANSION, SELF_REPAIR, DATA_LEECH,
        ENCRYPTION_ARMOR, PACKET_EVASION, INTRUSION_COUNTER, MALWARE_PURGE, DATA_COMPRESSION,
        SYSTEM_RESTORE, CRYPTO_CACHE, DATA_DUMP
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String): UpgradeDef = byId[id] ?: error("Unknown upgrade $id")

    /** Roman numerals for card titles ("PACKET NODES II"). */
    fun roman(n: Int): String = when (n) {
        1 -> "I"; 2 -> "II"; 3 -> "III"; 4 -> "IV"; 5 -> "V"
        6 -> "VI"; 7 -> "VII"; 8 -> "VIII"; 9 -> "IX"; 10 -> "X"
        else -> n.toString()
    }
}
