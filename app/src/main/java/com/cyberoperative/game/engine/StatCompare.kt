package com.cyberoperative.game.engine

import com.cyberoperative.game.data.Weapons
import kotlin.math.abs

/** One row of a before → after comparison. [better] colours it (green up, red down). */
data class StatLine(val label: String, val before: String, val after: String, val change: String, val better: Boolean)

/**
 * Owner, 2026-10-09: "tap and hold an upgrade … shows what stats are going to
 * increase and by how much compared to the current stat". Compares two
 * [RunStats] and lists only what changes, in player terms.
 */
object StatCompare {

    private enum class Kind { NUMBER, PERCENT, MULTIPLIER, COUNT }

    private class Stat(val label: String, val kind: Kind, val higherIsBetter: Boolean = true, val get: (RunStats) -> Float)

    private val stats = listOf(
        Stat("Main gun DPS", Kind.NUMBER) { mainGunDps(it) },
        Stat("All weapons DPS", Kind.NUMBER) { totalWeaponDps(it) },
        Stat("Damage", Kind.NUMBER) { it.damage },
        Stat("Attack speed (/s)", Kind.NUMBER) { it.fireRate },
        Stat("Crit chance", Kind.PERCENT) { it.critChance },
        Stat("Crit damage", Kind.MULTIPLIER) { it.critMul },
        Stat("Shots per volley", Kind.COUNT) { it.parallelShots.toFloat() },
        Stat("Follow-up shots", Kind.COUNT) { it.followUpShots.toFloat() },
        Stat("Diagonal pairs", Kind.COUNT) { it.diagonalPairs.toFloat() },
        Stat("Rear shot", Kind.COUNT) { if (it.rearShot) 1f else 0f },
        Stat("Pierce", Kind.COUNT) { it.pierce.toFloat() },
        Stat("Bounce", Kind.COUNT) { it.bounce.toFloat() },
        Stat("Chain", Kind.COUNT) { it.chain.toFloat() },
        Stat("Instant delete", Kind.PERCENT) { it.instantDeleteChance },
        Stat("Boss & elite damage", Kind.MULTIPLIER) { it.eliteDamageMul },
        Stat("Range", Kind.NUMBER) { it.range },
        Stat("Packet speed", Kind.NUMBER) { it.projectileSpeed },
        Stat("Max HP", Kind.NUMBER) { it.maxHp },
        Stat("Damage reduction", Kind.PERCENT) { it.armor },
        Stat("Dodge", Kind.PERCENT) { it.dodge },
        Stat("Regen (% HP/s)", Kind.PERCENT) { it.regenPerSec },
        Stat("Heal per kill", Kind.NUMBER) { it.healOnKill },
        Stat("Healing", Kind.MULTIPLIER) { it.healMul },
        Stat("Firewall", Kind.NUMBER) { it.firewallMax },
        Stat("Firewall recharge delay (s)", Kind.NUMBER, higherIsBetter = false) { it.firewallDelay },
        Stat("Counter shots when hit", Kind.COUNT) { it.counterRing.toFloat() },
        Stat("Move speed", Kind.NUMBER) { it.moveSpeed },
        Stat("Packet Nodes", Kind.COUNT) { it.orbCount.toFloat() },
        Stat("Node damage", Kind.NUMBER) { it.orbDamage },
        Stat("Blade damage", Kind.NUMBER) { it.bladeDamage },
        Stat("Data gain", Kind.MULTIPLIER) { it.xpMul },
        Stat("€ gain", Kind.MULTIPLIER) { it.euroMul }
    )

    /** Primary gun: damage × volleys/s × shots per volley, with average crits. */
    fun mainGunDps(s: RunStats): Float {
        val shots = s.parallelShots + 0.8f * (2 * s.diagonalPairs + if (s.rearShot) 1 else 0)
        val volleys = s.fireRate * (1 + s.followUpShots)
        return s.damage * volleys * shots * (1f + s.critChance.coerceIn(0f, 1f) * (s.critMul - 1f))
    }

    /** Built-in attack weapons: label, level in these stats, rough damage per second (from the engine's formulas). */
    private class BuiltIn(val name: String, val level: (RunStats) -> Int, val dps: (RunStats, Int) -> Float)

    private val builtIns = listOf(
        BuiltIn("PACKET SCATTER", { it.coneLevel }) { s, l -> s.damage * 0.45f * (1 + 2 * l) * s.fireRate * 0.5f },
        BuiltIn("EXPLOIT LANCE", { it.lanceLevel }) { s, l -> s.damage * (2.5f + 0.5f * l) / (2.8f - 0.4f * l) },
        BuiltIn("PLASMA BEAM", { it.beamLevel }) { s, l -> s.damage * (1.4f + 0.6f * l) * 5f / 8f },
        BuiltIn("EMP BURST", { it.empLevel }) { s, l -> s.damage * (0.9f + 0.3f * l) / (7f - l) },
        BuiltIn("LOGIC BOMBS", { it.mineLevel }) { s, l -> s.damage * (1.6f + 0.4f * l) / 0.9f * 0.5f },
        BuiltIn("MALWARE MISSILES", { it.missileLevel }) { s, l -> s.damage * 1.1f * (1 + l) / (2.8f - 0.4f * l) },
        BuiltIn("ARC DISCHARGE", { it.arcLevel }) { s, l -> s.damage * (1.3f + 0.3f * l) * (2 + l) / (2.4f - 0.4f * l) },
        BuiltIn("QUANTUM RAILGUN", { it.railLevel }) { s, l -> s.damage * (6f + 2f * l) / (3.6f - 0.8f * l) },
        BuiltIn("ORBITAL STRIKE", { it.strikeLevel }) { s, _ -> s.damage * 8f * 3f / 5f },
        BuiltIn("ENCRYPTION BLADES", { it.bladeCount }) { s, n -> s.bladeDamage * n * 2f }
    )

    /** Every weapon together (arsenal + built-in), rough damage per second. */
    fun totalWeaponDps(s: RunStats): Float =
        s.weapons.entries.sumOf { (id, l) -> weaponDps(id, l, s).toDouble() }.toFloat() +
            builtIns.sumOf { b -> b.level(s).let { if (it > 0) b.dps(s, it).toDouble() else 0.0 } }.toFloat()

    /** Rough damage per second of an arsenal weapon at [level] for these stats. */
    fun weaponDps(id: String, level: Int, s: RunStats): Float {
        val w = Weapons.byId(id) ?: return 0f
        return s.damage * w.damageAt(level) * w.countAt(level) / w.cooldownAt(level)
    }

    private fun fmt(v: Float, kind: Kind): String = when (kind) {
        Kind.PERCENT -> "${trim(v * 100f)}%"
        Kind.MULTIPLIER -> "×${"%.2f".format(v)}"
        Kind.COUNT -> v.toInt().toString()
        Kind.NUMBER -> trim(v)
    }

    private fun trim(v: Float): String = when {
        abs(v) >= 1000f -> "%,d".format(v.toLong())
        abs(v) >= 100f -> "%.0f".format(v)
        else -> "%.1f".format(v).removeSuffix(".0")
    }

    private fun change(a: Float, b: Float, kind: Kind): String {
        val d = b - a
        val sign = if (d >= 0) "+" else "−"
        return when (kind) {
            Kind.PERCENT -> "$sign${trim(abs(d) * 100f)}%"
            Kind.COUNT -> "$sign${abs(d).toInt()}"
            Kind.MULTIPLIER -> "$sign${"%.2f".format(abs(d))}"
            Kind.NUMBER -> if (abs(a) > 1e-3f) "$sign${trim(abs(d) / abs(a) * 100f)}%" else "$sign${trim(abs(d))}"
        }
    }

    /** Every stat that differs between [before] and [after], plus arsenal weapons gained, lost or levelled. */
    fun lines(before: RunStats, after: RunStats): List<StatLine> {
        val out = ArrayList<StatLine>()
        for (st in stats) {
            val a = st.get(before)
            val b = st.get(after)
            if (abs(b - a) <= 1e-4f * maxOf(1f, abs(a))) continue
            out += StatLine(st.label, fmt(a, st.kind), fmt(b, st.kind), change(a, b, st.kind), (b > a) == st.higherIsBetter)
        }
        for (id in (before.weapons.keys + after.weapons.keys).distinct()) {
            val la = before.weapons[id] ?: 0
            val lb = after.weapons[id] ?: 0
            if (la == lb) continue
            out += weaponLine(Weapons.byId(id)?.name ?: id, la, lb, if (la > 0) weaponDps(id, la, before) else 0f, if (lb > 0) weaponDps(id, lb, after) else 0f)
        }
        for (b in builtIns) {
            val la = b.level(before)
            val lb = b.level(after)
            if (la == lb) continue
            out += weaponLine(b.name, la, lb, if (la > 0) b.dps(before, la) else 0f, if (lb > 0) b.dps(after, lb) else 0f)
        }
        return out
    }

    /** A weapon gained, lost or levelled: level and rough damage per second on each side. */
    private fun weaponLine(name: String, la: Int, lb: Int, da: Float, db: Float) = StatLine(
        name,
        if (la > 0) "LV $la · ${trim(da)}/s" else "—",
        if (lb > 0) "LV $lb · ${trim(db)}/s" else "REMOVED",
        when {
            la == 0 -> "NEW"
            lb == 0 -> "−${trim(da)}/s"
            else -> change(da, db, Kind.NUMBER)
        },
        lb > la
    )
}
