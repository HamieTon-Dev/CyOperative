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
 * rarities above LEGENDARY have their own art and only drop on INFERNO.
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
 * Gear rarities, lowest first. [artTier] is the art pool in the atlases (0 grey … 4 gold, then the
 * four INFERNO sheets). [mul] scales base values and affixes.
 *
 * INFERNO rarities (owner, 0.33.0): FORGED (orange), VOLTAIC (yellow), CELESTIAL (platinum) and
 * ABYSSAL (inferno red, the top), each with its own art. They replace the recoloured ENCRYPTED /
 * INFERNAL / CRYPTO tiers; old saves keep those names on disk ([SerialName]) and load as the new ones.
 */
@Serializable
enum class GearRarity(
    val label: String,
    val color: Long,
    val artTier: Int,
    val mul: Float,
    val affixes: Int,
    /** Lowest INFERNO tier it can drop on (0 = any difficulty). */
    val minInferno: Int = 0,
    /** Boss drop chance per piece (percent) at INFERNO I; multiplied by the INFERNO level. */
    val infernoBase: Float = 0f
) {
    COMMON("COMMON", 0xFFB8C4D6, 0, 1.0f, 0),
    UNCOMMON("UNCOMMON", 0xFF3CFF6E, 1, 1.1f, 1),
    RARE("RARE", 0xFF2E9BFF, 2, 1.22f, 2),
    EPIC("EPIC", 0xFFB45CFF, 3, 1.36f, 3),
    LEGENDARY("LEGENDARY", 0xFFFFC21A, 4, 1.6f, 4),
    @SerialName("ENCRYPTED") FORGED("FORGED", 0xFFFF8A1F, 5, 1.85f, 4, minInferno = 1, infernoBase = 0.8f),
    @SerialName("INFERNAL") VOLTAIC("VOLTAIC", 0xFFFFF04D, 6, 2.1f, 5, minInferno = 3, infernoBase = 0.7f),
    @SerialName("CRYPTO") CELESTIAL("CELESTIAL", 0xFFDDE8FF, 7, 2.4f, 6, minInferno = 5, infernoBase = 0.5f),
    ABYSSAL("ABYSSAL", 0xFFFF2B3A, 8, 2.8f, 6, minInferno = 7, infernoBase = 0.4f);

    /** Above BLUE: the drop gets a light beam and the sheen sound. */
    val sheen: Boolean get() = ordinal > RARE.ordinal
    /** The four INFERNO rarities: animated cards (holographic sheen; flames for ABYSSAL). */
    val inferno: Boolean get() = minInferno > 0
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
        GearSlot.PRIMARY to intArrayOf(21, 19, 15, 17, 28, 5, 5, 5, 5),
        GearSlot.SECONDARY to intArrayOf(20, 20, 19, 21, 20, 5, 5, 5, 5),
        GearSlot.HELMET to intArrayOf(44, 44, 44, 42, 46, 5, 5, 5, 5),
        GearSlot.CHEST to intArrayOf(23, 17, 17, 17, 26, 5, 5, 5, 5),
        GearSlot.GLOVES to intArrayOf(20, 19, 17, 21, 23, 5, 5, 5, 5),
        GearSlot.BOOTS to intArrayOf(44, 44, 36, 38, 48, 5, 5, 5, 5),
        GearSlot.IMPLANT to intArrayOf(20, 20, 19, 20, 21, 5, 5, 5, 5),
        GearSlot.CORE to intArrayOf(20, 20, 20, 20, 20, 5, 5, 5, 5)
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
        GearRarity.FORGED to listOf("Forged", "Molten", "Ember", "Smelted", "Crucible", "Anvil", "Slag", "Kiln", "Tempered", "Foundry"),
        GearRarity.VOLTAIC to listOf("Voltaic", "Arc", "Surge", "Thunder", "Live-Wire", "Tesla", "Storm", "Overcharge", "Lightning", "Galvanic"),
        GearRarity.CELESTIAL to listOf("Celestial", "Platinum", "Starlit", "Halo", "Seraph", "Orbital", "Eclipse", "Nova", "Aether", "Zenith"),
        GearRarity.ABYSSAL to listOf("Abyssal", "Hellfire", "Brimstone", "Inferno", "Cinder", "Doom", "Pyre", "Demon", "Hellforged", "Infernal")
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
     * Rarity odds (percent) for one drop. [boss] drops are better; bosses after World 12 / Endless
     * level 120 get a little more.
     *
     * INFERNO (owner, 0.33.0): each of the four INFERNO rarities drops at its base chance
     * multiplied by the INFERNO level, P = base × I (bosses: FORGED 0.8%, VOLTAIC 0.7%, CELESTIAL
     * 0.5%, ABYSSAL 0.4%; elites a quarter of that), from its own INFERNO tier up. Luck adds on top
     * (P × (1 + luck)). Together they never take more than [INFERNO_CAP]; COMMON … LEGENDARY share
     * what is left in their usual proportions (tilted toward the rare end by INFERNO and luck).
     */
    fun odds(boss: Boolean, inferno: Int, luck: Float = 0f, lateBoost: Boolean = false): FloatArray {
        val base = if (boss) floatArrayOf(18f, 34f, 30f, 13f, 4.2f) else floatArrayOf(52f, 30f, 13f, 3.8f, 0.95f)
        val w = FloatArray(GearRarity.entries.size)
        val bonus = 1f + luck + (if (lateBoost) 0.15f else 0f)
        var high = 0f
        for (r in GearRarity.entries) if (r.inferno && inferno >= r.minInferno) {
            w[r.ordinal] = r.infernoBase * (if (boss) 1f else ELITE_SHARE) * inferno * bonus
            high += w[r.ordinal]
        }
        if (high > INFERNO_CAP) { for (i in w.indices) w[i] *= INFERNO_CAP / high; high = INFERNO_CAP }
        // Every step of luck tilts the usual five toward the rare end.
        val tilt = 1f + 0.08f * inferno.coerceAtMost(10) + luck + (if (lateBoost) 0.15f else 0f)
        val low = FloatArray(base.size) { i -> base[i] * tilt.pow(i.toFloat()) }
        val lowSum = low.sum()
        for (i in low.indices) w[i] = low[i] / lowSum * (100f - high)
        return w
    }

    /** Elites drop the INFERNO rarities at this share of the boss chance. */
    const val ELITE_SHARE = 0.25f
    /** The four INFERNO rarities together never take more than this (percent). */
    const val INFERNO_CAP = 60f

    fun rollRarity(rng: Random, boss: Boolean, inferno: Int, luck: Float = 0f, lateBoost: Boolean = false): GearRarity {
        val o = odds(boss, inferno, luck, lateBoost)
        var x = rng.nextFloat() * 100f
        for (i in o.indices) { x -= o[i]; if (x <= 0f) return GearRarity.entries[i] }
        return GearRarity.COMMON
    }

    /**
     * The in-run shop's equipment (owner, 0.32.0): PURPLE and up only, rolled on the boss table
     * (the rarest the least likely to be on the counter). ABYSSAL is pinned (owner, 0.34.0) so a
     * 4-piece counter shows one about 1 time in 100 ([SHOP_ABYSSAL] per piece) from INFERNO VII on.
     */
    fun rollShopRarity(rng: Random, inferno: Int): GearRarity = pick(rng, shopOdds(inferno))

    /** Per-piece shop odds (percent) by rarity: EPIC and up, ABYSSAL fixed at [SHOP_ABYSSAL]. */
    fun shopOdds(inferno: Int): FloatArray {
        val o = odds(boss = true, inferno = inferno)
        val w = FloatArray(o.size)
        val top = GearRarity.ABYSSAL.ordinal
        val abyss = if (inferno >= GearRarity.ABYSSAL.minInferno) SHOP_ABYSSAL else 0f
        var rest = 0f
        for (i in GearRarity.EPIC.ordinal until top) rest += o[i]
        for (i in GearRarity.EPIC.ordinal until top) w[i] = o[i] / rest * (100f - abyss)
        w[top] = abyss
        return w
    }

    /** Equipment pieces on a shop counter (GameEngine.SHOP_GEAR). */
    const val SHOP_GEAR_PIECES = 4
    /** ABYSSAL per shop piece (percent): 1 − 0.99^(1/4), so 1% per 4-piece counter. */
    const val SHOP_ABYSSAL = 0.2510f

    private fun pick(rng: Random, o: FloatArray): GearRarity {
        var x = rng.nextFloat() * o.sum()
        for (i in o.indices) { x -= o[i]; if (x <= 0f && o[i] > 0f) return GearRarity.entries[i] }
        return GearRarity.EPIC
    }

    /** Shop price: by rarity, a little more per item level. */
    fun shopPrice(item: GearItem): Int {
        val base = when (item.rarity) {
            GearRarity.COMMON -> 300
            GearRarity.UNCOMMON -> 600
            GearRarity.RARE -> 900
            GearRarity.EPIC -> 1500
            GearRarity.LEGENDARY -> 4000
            GearRarity.FORGED -> 9000
            GearRarity.VOLTAIC -> 18000
            GearRarity.CELESTIAL -> 35000
            GearRarity.ABYSSAL -> 70000
        }
        return (base * (1f + 0.03f * item.level)).toInt() / 10 * 10
    }

    /**
     * What a backpack piece sells for (owner, 0.35.0): item level × rarity tier (1 COMMON … 9
     * ABYSSAL) × 50, but never more than 85% of what the shop would charge for it.
     */
    fun sellPrice(item: GearItem): Int =
        minOf(item.level * (item.rarity.ordinal + 1) * 50, (shopPrice(item) * 0.85f).toInt() / 10 * 10).coerceAtLeast(10)

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
        val scale = (1f + 0.5f * level / OpProgress.MAX_LEVEL.toFloat()) * (1f + 0.18f * (rarity.ordinal - GearRarity.LEGENDARY.ordinal).coerceAtLeast(0))
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
            GearPerks.apply(s, g)
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

    val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    fun encode(items: Collection<GearItem>): String = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(GearItem.serializer()), items.toList())

    fun decode(text: String?): List<GearItem> = if (text.isNullOrBlank()) emptyList() else runCatching {
        json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(GearItem.serializer()), text)
    }.getOrDefault(emptyList())
}

/**
 * Signature perks (owner, 0.35.0: "not enough uniqueness to the perks higher rarity equipment
 * gives you"). LEGENDARY and every INFERNO rarity add one fixed perk per piece, by what the piece
 * is: the rifle, an armor piece, or the sidearm / implant / core.
 */
object GearPerks {
    private enum class Kind { RIFLE, ARMOR, SYSTEM }
    private fun kind(g: GearItem) = when {
        g.slot == GearSlot.PRIMARY -> Kind.RIFLE
        g.slot.armorPiece -> Kind.ARMOR
        else -> Kind.SYSTEM
    }

    /** The perk's line on the item card, or null below LEGENDARY. */
    fun text(g: GearItem): String? = when (g.rarity) {
        GearRarity.LEGENDARY -> when (kind(g)) { Kind.RIFLE -> "Shots pierce 1 more threat"; Kind.ARMOR -> "+2% dodge"; Kind.SYSTEM -> "+6% XP gain, +6% €" }
        GearRarity.FORGED -> when (kind(g)) { Kind.RIFLE -> "Shots bounce once more, +8% damage"; Kind.ARMOR -> "Molten plating: getting hit sprays 3 slag bolts"; Kind.SYSTEM -> "+1 Packet Node" }
        GearRarity.VOLTAIC -> when (kind(g)) { Kind.RIFLE -> "Shots arc to 1 more threat"; Kind.ARMOR -> "+7% move speed"; Kind.SYSTEM -> "Packet Nodes fire their own bolts" }
        GearRarity.CELESTIAL -> when (kind(g)) { Kind.RIFLE -> "+1 parallel shot"; Kind.ARMOR -> "+15% healing, +3 HP per kill"; Kind.SYSTEM -> "+12% critical damage, +3% critical chance" }
        GearRarity.ABYSSAL -> when (kind(g)) { Kind.RIFLE -> "3% chance to delete a threat outright, +25% critical damage"; Kind.ARMOR -> "Hellfire skin: hits spray 5 bolts, +3% dodge"; Kind.SYSTEM -> "+1 Packet Node, +25% node damage, +10% damage" }
        else -> null
    }

    fun apply(s: RunStats, g: GearItem) {
        when (g.rarity) {
            GearRarity.LEGENDARY -> when (kind(g)) {
                Kind.RIFLE -> s.pierce += 1
                Kind.ARMOR -> s.dodge += 0.02f
                Kind.SYSTEM -> { s.xpMul *= 1.06f; s.euroMul *= 1.06f }
            }
            GearRarity.FORGED -> when (kind(g)) {
                Kind.RIFLE -> { s.bounce += 1; s.damage *= 1.08f }
                Kind.ARMOR -> s.counterRing += 3
                Kind.SYSTEM -> s.orbCount += 1
            }
            GearRarity.VOLTAIC -> when (kind(g)) {
                Kind.RIFLE -> s.chain += 1
                Kind.ARMOR -> s.moveSpeed *= 1.07f
                Kind.SYSTEM -> if (s.orbBoltInterval <= 0f) s.orbBoltInterval = 1.6f else s.orbBoltInterval *= 0.85f
            }
            GearRarity.CELESTIAL -> when (kind(g)) {
                Kind.RIFLE -> s.parallelShots += 1
                Kind.ARMOR -> { s.healMul *= 1.15f; s.healOnKill += 3f }
                Kind.SYSTEM -> { s.critMul += 0.12f; s.critChance += 0.03f }
            }
            GearRarity.ABYSSAL -> when (kind(g)) {
                Kind.RIFLE -> { s.instantDeleteChance += 0.03f; s.critMul += 0.25f }
                Kind.ARMOR -> { s.counterRing += 5; s.dodge += 0.03f }
                Kind.SYSTEM -> { s.orbCount += 1; s.orbDamage *= 1.25f; s.damage *= 1.1f }
            }
            else -> {}
        }
    }
}
