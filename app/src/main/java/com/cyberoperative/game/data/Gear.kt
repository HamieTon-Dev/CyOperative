package com.cyberoperative.game.data

import com.cyberoperative.game.engine.RunStats
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.random.Random

/*
 * Data Vault update, phase 2 (owner, 2026-10-13): gear loot.
 *
 * Elites and bosses drop gear: two weapons (PRIMARY rifles, SECONDARY pistols), four armor
 * pieces (HELMET, CHEST, GLOVES, BOOTS), an IMPLANT and a CORE. Every piece has a rarity, an item
 * level (the finder's OP level ±2, never above 60), a base value and 0–6 rolled affixes.
 * The art is the owner's sprite sheets (resources/gear/<slot>.webp, see [GearArt]); the four
 * rarities above LEGENDARY reuse the golden art recoloured, with their own names and better stats,
 * and only drop on INFERNO.
 */

enum class GearSlot(val label: String, val sheet: String) {
    PRIMARY("PRIMARY", "primary"),
    SECONDARY("SECONDARY", "secondary"),
    HELMET("HELMET", "helmet"),
    CHEST("CHEST", "chest"),
    GLOVES("GLOVES", "gloves"),
    BOOTS("BOOTS", "boots"),
    IMPLANT("IMPLANT", "implant"),
    CORE("CORE", "core");

    val weapon: Boolean get() = this == PRIMARY || this == SECONDARY
    val armorPiece: Boolean get() = this == HELMET || this == CHEST || this == GLOVES || this == BOOTS
}

/**
 * Gear rarities, lowest first. [artTier] is the art pool (0 grey … 4 gold); [hue] recolours the
 * golden art for the INFERNO-only rarities. [mul] scales base values and affixes.
 */
enum class GearRarity(
    val label: String,
    val color: Long,
    val artTier: Int,
    val hue: Float,
    val mul: Float,
    val affixes: Int,
    /** Lowest INFERNO tier it can drop on (0 = any difficulty). */
    val minInferno: Int = 0
) {
    COMMON("COMMON", 0xFFB8C4D6, 0, 0f, 1.0f, 0),
    UNCOMMON("UNCOMMON", 0xFF3CFF6E, 1, 0f, 1.1f, 1),
    RARE("RARE", 0xFF2E9BFF, 2, 0f, 1.22f, 2),
    EPIC("EPIC", 0xFFB45CFF, 3, 0f, 1.36f, 3),
    LEGENDARY("LEGENDARY", 0xFFFFC21A, 4, 0f, 1.52f, 4),
    /** Orange. */
    ENCRYPTED("ENCRYPTED", 0xFFFF8A1F, 4, -18f, 1.7f, 4, minInferno = 1),
    /** Hell red. */
    INFERNAL("INFERNAL", 0xFFFF2B3A, 4, -46f, 1.9f, 5, minInferno = 4),
    /** Neon blue: the top of the ladder, close to 0%. */
    CRYPTO("CRYPTO", 0xFF00F0FF, 4, 148f, 2.15f, 6, minInferno = 7);

    /** Above BLUE: the drop gets a light beam and the sheen sound. */
    val sheen: Boolean get() = ordinal > RARE.ordinal
}

/** Stats an affix can roll. Values are stored in display units (percent as 0–100). */
enum class GearStat(val label: String, val percent: Boolean, val lo: Float, val hi: Float) {
    MAX_HP("Max HP", true, 2f, 6f),
    DAMAGE("Damage", true, 2f, 6f),
    CRIT_CHANCE("Critical chance", true, 1f, 3f),
    CRIT_DAMAGE("Critical damage", true, 5f, 15f),
    ATTACK_SPEED("Attack speed", true, 2f, 5f),
    MOVE_SPEED("Move speed", true, 1f, 4f),
    ARMOR("Armor", false, 3f, 8f),
    DODGE("Dodge", true, 0.5f, 2f),
    HEAL_ON_KILL("HP per kill", false, 1f, 3f),
    ELITE_DAMAGE("Damage to elites & bosses", true, 4f, 10f),
    XP_GAIN("XP gain", true, 2f, 6f),
    NODE_DAMAGE("Packet Node damage", true, 4f, 10f);

    fun format(v: Float): String {
        val n = if (v >= 10f || this == ARMOR) "%.0f".format(v) else "%.1f".format(v).removeSuffix(".0")
        return if (percent) "+$n% $label" else "+$n $label"
    }
}

@Serializable
data class GearAffix(@SerialName("t") val stat: GearStat, @SerialName("v") val value: Float)

/** One piece of gear (saved in the backpack, the Data Vault or a gear slot). */
@Serializable
data class GearItem(
    @SerialName("u") val uid: Long,
    @SerialName("s") val slot: GearSlot,
    @SerialName("r") val rarity: GearRarity,
    /** Index into the slot's art for [GearRarity.artTier]. */
    @SerialName("a") val art: Int,
    @SerialName("l") val level: Int,
    /** Weapon damage, armor, or the CORE / IMPLANT main stat; SECONDARY: the weapon's level. */
    @SerialName("b") val base: Float,
    @SerialName("x") val affixes: List<GearAffix> = emptyList(),
    /** Locked items are never sold, deposited in bulk or replaced by DEPOSIT ALL. */
    @SerialName("k") val locked: Boolean = false,
    /** Not looked at yet ("NEW" tag). */
    @SerialName("n") val fresh: Boolean = true
) {
    val name: String get() = GearNames.name(this)
    val style: GunStyle? get() = if (slot == GearSlot.PRIMARY) GunStyle.forArt(art) else null
    /** Class this weapon is built for (only that class can equip it), or null. */
    val classOnly: String? get() = style?.classId
    /** SECONDARY: the arsenal weapon it carries. */
    val sidearm: WeaponSpec? get() = if (slot == GearSlot.SECONDARY) Gear.sidearmFor(rarity, art) else null
    fun usableBy(classId: String): Boolean = classOnly == null || classOnly == classId
    /** A rough power score for sorting and "vs equipped" (higher is better). */
    val power: Float get() = base * (if (slot == GearSlot.SECONDARY) 40f else 1f) + affixes.sumOf { it.value.toDouble() }.toFloat() * 2f

    /** The headline number on the item card: DAMAGE, ARMOR, etc. */
    val mainLabel: String get() = when (slot) {
        GearSlot.PRIMARY -> "DAMAGE"
        GearSlot.SECONDARY -> "WEAPON LEVEL"
        GearSlot.CORE -> "MAX HP"
        GearSlot.IMPLANT -> "CRITICAL CHANCE"
        else -> "ARMOR"
    }
    val mainValue: String get() = when (slot) {
        GearSlot.CORE, GearSlot.IMPLANT -> "+" + "%.1f".format(base).removeSuffix(".0") + "%"
        else -> "%.0f".format(base)
    }
    val description: String get() = when (slot) {
        GearSlot.PRIMARY -> style!!.text + (classOnly?.let { " " + Operatives.byId(it).name + " only." } ?: "")
        GearSlot.SECONDARY -> sidearm?.let { "Auto-weapon: ${it.name}. ${it.description}" } ?: ""
        GearSlot.CORE -> "Power core: raises maximum HP."
        GearSlot.IMPLANT -> "Neural implant: sharper targeting, more critical hits."
        else -> "Armor plating: cuts the damage you take."
    }
}

/**
 * PRIMARY rifle attack styles. Every rifle's art has a fixed style (so the same-looking gun always
 * fires the same way); one in five are class weapons that only that class can equip.
 */
enum class GunStyle(
    val noun: String,
    val text: String,
    val damageMul: Float,
    val rateMul: Float = 1f,
    val classId: String? = null,
    val apply: (RunStats) -> Unit = {}
) {
    PULSE("Pulse Rifle", "Steady pulse bolts.", 1f),
    BURST("Burst Rifle", "Three-round bursts.", 0.55f, apply = { it.followUpShots += 2 }),
    SCATTER("Scattergun", "A fan of five pellets, short range.", 0.42f, 0.85f, apply = { it.diagonalPairs += 2; it.range *= 0.7f }),
    RAIL("Railgun", "Slow, heavy rails that pierce three threats.", 2.3f, 0.5f, apply = { it.pierce += 3; it.projectileSpeed *= 1.6f }),
    SMG("SMG", "Very fast, light bolts.", 0.58f, 1.8f),
    ARC("Arc Rifle", "Bolts that jump to two more threats.", 0.8f, apply = { it.chain += 2 }),
    RICOCHET("Ricochet Carbine", "Bolts bounce off walls twice.", 0.85f, apply = { it.bounce += 2 }),
    TWIN("Twin Rifle", "Two parallel bolts per shot.", 0.62f, apply = { it.parallelShots += 1 }),
    // Class weapons: a little stronger than the shared styles.
    SIGNAL("Signal Rifle", "Signal bolts that pierce two threats and arc to one more.", 0.95f, classId = "technician",
        apply = { it.pierce += 2; it.chain += 1 }),
    SIEGE("Siege Cannon", "A huge, slow shell that punches through a threat.", 2.9f, 0.42f, classId = "juggernaut",
        apply = { it.pierce += 1; it.projectileSpeed *= 0.85f }),
    GHOST("Ghost Rifle", "Silent shots: +15% critical chance, +60% critical damage.", 1.05f, classId = "phantom",
        apply = { it.critChance += 0.15f; it.critMul += 0.6f }),
    HEX("Hex Caster", "Hexed bolts that bounce twice and arc once.", 0.9f, classId = "packet_mage",
        apply = { it.bounce += 2; it.chain += 1 }),
    TACTICAL("Tactical Carbine", "Two-round bursts, +5% critical chance.", 0.75f, classId = "operative",
        apply = { it.followUpShots += 1; it.critChance += 0.05f });

    companion object {
        private val shared = listOf(PULSE, BURST, SCATTER, RAIL, SMG, ARC, RICOCHET, TWIN)
        private val classed = listOf(TACTICAL, SIGNAL, SIEGE, GHOST, HEX)
        fun forArt(art: Int): GunStyle = if (art % 5 == 4) classed[(art / 5) % classed.size] else shared[(art - art / 5) % shared.size]
    }
}

/** The atlas layout written by the slicer (cells per rarity art tier, in order, 16 per row). */
object GearArt {
    const val COLS = 16
    const val CELL = 96
    private val counts: Map<GearSlot, IntArray> = mapOf(
        GearSlot.PRIMARY to intArrayOf(21, 19, 15, 17, 28),
        GearSlot.SECONDARY to intArrayOf(20, 20, 19, 21, 20),
        GearSlot.HELMET to intArrayOf(44, 44, 44, 42, 46),
        GearSlot.CHEST to intArrayOf(23, 17, 17, 17, 26),
        GearSlot.GLOVES to intArrayOf(20, 19, 17, 21, 23),
        GearSlot.BOOTS to intArrayOf(44, 44, 36, 38, 48),
        GearSlot.IMPLANT to intArrayOf(20, 20, 19, 20, 21),
        GearSlot.CORE to intArrayOf(20, 20, 20, 20, 20)
    )

    fun count(slot: GearSlot, tier: Int): Int = counts.getValue(slot)[tier]

    /** Cell index in the slot's atlas. */
    fun cell(slot: GearSlot, tier: Int, art: Int): Int {
        val c = counts.getValue(slot)
        var off = 0
        for (t in 0 until tier) off += c[t]
        return off + art.mod(c[tier])
    }
}

object GearNames {
    private val prefixes: Map<GearRarity, List<String>> = mapOf(
        GearRarity.COMMON to listOf("Scrap", "Worn", "Standard", "Patched", "Basic", "Surplus", "Salvaged", "Field", "Issue", "Rusted"),
        GearRarity.UNCOMMON to listOf("Tuned", "Reinforced", "Hardened", "Calibrated", "Shielded", "Mesh", "Boosted", "Sealed", "Refined", "Ported"),
        GearRarity.RARE to listOf("Cobalt", "Vector", "Sapphire", "Ion", "Cipher", "Glacier", "Tidal", "Pulse", "Proxy", "Static"),
        GearRarity.EPIC to listOf("Void", "Phantom", "Nebula", "Hex", "Wraith", "Astral", "Umbra", "Rift", "Spectral", "Nightfall"),
        GearRarity.LEGENDARY to listOf("Solar", "Aegis", "Zenith", "Helios", "Sovereign", "Gilded", "Radiant", "Aurum", "Paragon", "Midas"),
        GearRarity.ENCRYPTED to listOf("Encrypted", "Keystone", "Hashfire", "Lockbreaker", "Amber", "Blackbox", "Backdoor", "Zero-Day", "Cipherforged", "Ember"),
        GearRarity.INFERNAL to listOf("Infernal", "Hellfire", "Brimstone", "Cinder", "Doom", "Scorched", "Demon", "Molten", "Abyssal", "Pyre"),
        GearRarity.CRYPTO to listOf("Crypto", "Quantum", "Ledger", "Genesis", "Singularity", "Prism", "Mainnet", "Hashrate", "Neon", "Overclocked")
    )
    private val nouns: Map<GearSlot, List<String>> = mapOf(
        GearSlot.HELMET to listOf("Helmet", "Visor", "Hood", "Cowl", "Faceguard", "Helm", "Mask"),
        GearSlot.CHEST to listOf("Vest", "Plate", "Harness", "Carapace", "Jacket", "Cuirass", "Rig"),
        GearSlot.GLOVES to listOf("Gloves", "Grips", "Gauntlets", "Handwraps", "Knuckles", "Claws"),
        GearSlot.BOOTS to listOf("Boots", "Treads", "Striders", "Greaves", "Kicks", "Stompers"),
        GearSlot.IMPLANT to listOf("Implant", "Neurochip", "Wafer", "Coprocessor", "Module", "Lattice"),
        GearSlot.CORE to listOf("Core", "Reactor", "Cell", "Heart", "Orb", "Dynamo")
    )

    fun name(item: GearItem): String {
        val p = prefixes.getValue(item.rarity)
        val prefix = p[item.art.mod(p.size)]
        val noun = when (item.slot) {
            GearSlot.PRIMARY -> GunStyle.forArt(item.art).noun
            GearSlot.SECONDARY -> item.sidearm?.name?.lowercase()?.split(' ')?.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } } ?: "Pistol"
            else -> nouns.getValue(item.slot).let { it[(item.art / p.size + item.art).mod(it.size)] }
        }
        return "$prefix $noun"
    }
}

/** Gear rolls, drop tables and stats (owner, 2026-10-13). */
object Gear {
    const val BACKPACK_SIZE = 60
    const val VAULT_SIZE = 1500

    private val armorPerLevel = mapOf(GearSlot.CHEST to 10f, GearSlot.HELMET to 7f, GearSlot.BOOTS to 6f, GearSlot.GLOVES to 5f)

    /** Item level for a drop found at OP level [opLevel]: within ±2, never above the max. */
    fun itemLevel(opLevel: Int, rng: Random): Int =
        (opLevel + rng.nextInt(-2, 3)).coerceIn(1, OpProgress.MAX_LEVEL)

    /** SECONDARY art → one of the arsenal weapons of the same rarity (Titanium ones above GOLDEN). */
    fun sidearmFor(rarity: GearRarity, art: Int): WeaponSpec {
        val r = when (rarity) {
            GearRarity.COMMON -> Rarity.COMMON
            GearRarity.UNCOMMON -> Rarity.UNCOMMON
            GearRarity.RARE -> Rarity.RARE
            GearRarity.EPIC -> Rarity.EPIC
            GearRarity.LEGENDARY -> Rarity.LEGENDARY
            else -> Rarity.TITANIUM
        }
        val pool = Weapons.all.filter { it.rarity == r }
        return pool[art.mod(pool.size)]
    }

    /**
     * Rarity odds (percent) for one drop. [boss] drops are better; INFERNO adds the four high
     * rarities and pushes the odds up; bosses after World 12 / Endless level 120 get a little more.
     */
    fun odds(boss: Boolean, inferno: Int, luck: Float = 0f, lateBoost: Boolean = false): FloatArray {
        val base = if (boss) floatArrayOf(18f, 34f, 30f, 13f, 5f, 0.8f, 0.2f, 0.035f)
        else floatArrayOf(52f, 30f, 13f, 3.8f, 1.1f, 0.25f, 0.06f, 0.01f)
        // Every step of luck tilts the table toward the rare end.
        val tilt = 1f + 0.08f * inferno + luck + (if (lateBoost) 0.15f else 0f)
        val w = FloatArray(base.size) { i ->
            val r = GearRarity.entries[i]
            if (r.minInferno > inferno || (r.minInferno > 0 && inferno == 0)) 0f else base[i] * tilt.pow(i.toFloat())
        }
        val sum = w.sum()
        for (i in w.indices) w[i] = w[i] / sum * 100f
        return w
    }

    fun rollRarity(rng: Random, boss: Boolean, inferno: Int, luck: Float = 0f, lateBoost: Boolean = false): GearRarity {
        val o = odds(boss, inferno, luck, lateBoost)
        var x = rng.nextFloat() * 100f
        for (i in o.indices) { x -= o[i]; if (x <= 0f) return GearRarity.entries[i] }
        return GearRarity.COMMON
    }

    /** Slot odds: weapons a little less often than armor. */
    private val slotWeights = floatArrayOf(10f, 10f, 14f, 14f, 13f, 13f, 12f, 12f)

    fun rollSlot(rng: Random): GearSlot {
        var x = rng.nextFloat() * slotWeights.sum()
        for (i in slotWeights.indices) { x -= slotWeights[i]; if (x <= 0f) return GearSlot.entries[i] }
        return GearSlot.CORE
    }

    /** A fresh item. */
    fun roll(rng: Random, uid: Long, level: Int, rarity: GearRarity, slot: GearSlot = rollSlot(rng)): GearItem {
        val art = rng.nextInt(GearArt.count(slot, rarity.artTier))
        val l = level.coerceIn(1, OpProgress.MAX_LEVEL)
        val variance = 0.92f + rng.nextFloat() * 0.16f
        val base = when (slot) {
            GearSlot.PRIMARY -> 20f * (1f + 0.025f * (l - 1)) * rarity.mul * variance
            GearSlot.SECONDARY -> (1 + (l - 1) / 10 + (rarity.ordinal + 1) / 2).toFloat()
            GearSlot.CORE -> (6f + 0.15f * l) * rarity.mul * variance
            GearSlot.IMPLANT -> (2f + 0.05f * l) * rarity.mul * variance
            else -> armorPerLevel.getValue(slot) * l * rarity.mul * variance
        }
        val pool = GearStat.entries.toMutableList()
        if (slot.armorPiece) pool.remove(GearStat.ARMOR).also { pool.add(GearStat.ARMOR) }
        val affixes = (0 until rarity.affixes).map {
            val stat = pool.removeAt(rng.nextInt(pool.size))
            GearAffix(stat, affixValue(stat, l, rarity, rng.nextFloat()))
        }
        return GearItem(uid, slot, rarity, art, l, round1(base), affixes)
    }

    fun affixValue(stat: GearStat, level: Int, rarity: GearRarity, roll: Float): Float {
        val scale = (1f + 0.5f * level / OpProgress.MAX_LEVEL.toFloat()) * (1f + 0.12f * (rarity.ordinal - GearRarity.LEGENDARY.ordinal).coerceAtLeast(0))
        // Higher rarities roll toward the top of the range.
        val r = roll.pow(1f / (1f + 0.25f * rarity.ordinal))
        val v = (stat.lo + (stat.hi - stat.lo) * r) * scale
        return round1(if (stat == GearStat.ARMOR) v * level else v)
    }

    private fun round1(v: Float) = kotlin.math.round(v * 10f) / 10f

    /** Adds [gear] (what is equipped) to [s]; class weapons only count for [classId]. */
    fun apply(s: RunStats, gear: Collection<GearItem>, classId: String) {
        for (g in gear) {
            if (!g.usableBy(classId)) continue
            when (g.slot) {
                GearSlot.PRIMARY -> {
                    val style = g.style!!
                    s.damage *= g.base / 20f * style.damageMul
                    s.fireRate *= style.rateMul
                    style.apply(s)
                }
                GearSlot.SECONDARY -> g.sidearm?.let { w -> s.weapons[w.id] = maxOf(s.weapons[w.id] ?: 0, g.base.toInt()) }
                GearSlot.CORE -> s.maxHp *= 1f + g.base / 100f
                GearSlot.IMPLANT -> s.critChance += g.base / 100f
                else -> s.armor += g.base
            }
            for (a in g.affixes) applyAffix(s, a)
        }
    }

    private fun applyAffix(s: RunStats, a: GearAffix) {
        val p = a.value / 100f
        when (a.stat) {
            GearStat.MAX_HP -> s.maxHp *= 1f + p
            GearStat.DAMAGE -> s.damage *= 1f + p
            GearStat.CRIT_CHANCE -> s.critChance += p
            GearStat.CRIT_DAMAGE -> s.critMul += p
            GearStat.ATTACK_SPEED -> s.fireRate *= 1f + p
            GearStat.MOVE_SPEED -> s.moveSpeed *= 1f + p
            GearStat.ARMOR -> s.armor += a.value
            GearStat.DODGE -> s.dodge += p
            GearStat.HEAL_ON_KILL -> s.healOnKill += a.value
            GearStat.ELITE_DAMAGE -> s.eliteDamageMul *= 1f + p
            GearStat.XP_GAIN -> s.xpMul *= 1f + p
            GearStat.NODE_DAMAGE -> s.orbDamage *= 1f + p
        }
    }

    // --- Compact text form (co-op lobby, run saves) -------------------------------

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    fun encode(items: Collection<GearItem>): String = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(GearItem.serializer()), items.toList())

    fun decode(text: String?): List<GearItem> = if (text.isNullOrBlank()) emptyList() else runCatching {
        json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(GearItem.serializer()), text)
    }.getOrDefault(emptyList())
}
