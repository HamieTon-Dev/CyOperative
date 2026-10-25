package com.cyberoperative.game.data

import com.cyberoperative.game.i18n.tr
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.random.Random

/*
 * COMPANIONS (owner, 0.38.0): eggs drop in runs (and can be bought for ◇), hatch in the
 * INCUBATOR on a real-time timer, and a hatched pet follows your operative and fights.
 * Art: the owner's ten pet / egg sheets, every pet and egg cut out (resources/pets, webp atlases).
 */

/** An unhatched egg. [art] is its egg picture; [pet] is the pet inside (rolled when it dropped). */
@Serializable
data class PetEgg(
    @SerialName("u") val uid: Long,
    @SerialName("r") val rarity: GearRarity,
    @SerialName("a") val art: Int,
    @SerialName("p") val pet: Int
) {
    val name: String get() = Pets.eggName(this)
    /** Hatch time with no Inferno Stones. */
    val baseMs: Long get() = Pets.hatchMs(rarity)
}

/** A hatched companion. */
@Serializable
data class Pet(
    @SerialName("u") val uid: Long,
    @SerialName("a") val art: Int,
    @SerialName("r") val rarity: GearRarity,
    /** Not looked at yet (the reveal / "NEW" tag). */
    @SerialName("n") val fresh: Boolean = true,
    /** The egg picture it hatched from (the hatch animation, 0.45.0); −1 for older pets. */
    @SerialName("g") val eggArt: Int = -1
) {
    /** The egg it came out of, for the hatch animation. */
    val hatchedFrom: Int get() = if (eggArt >= 0) eggArt else Pets.eggPictureFor(rarity, uid)
    val name: String get() = Pets.petName(art)
    /** Rolled from the pet's id when it hatched (so older pets have theirs too). */
    val abilities: List<PetAbility> get() = Pets.abilitiesFor(uid, art, rarity)
    val loadout: PetLoadout get() = PetLoadout(art, rarity, abilities)
}

/**
 * Pet abilities (owner, 0.39.0): lower tiers have one; EPIC and up may roll a second; GLITCHED
 * LEGENDARY and up may roll a third and a fourth (four at most). Stronger and faster by rarity.
 */
enum class PetAbility(val label: String, val text: String, val cooldown: Float) {
    FIRE_BITE(tr("FIRE BITE"), tr("Breathes a burst of flame on the nearest threat and those around it."), 6f),
    CHAIN_SHOCK(tr("CHAIN SHOCK"), tr("A bolt that arcs on to three more threats."), 5f),
    HEAL_PULSE(tr("HEAL PULSE"), tr("Heals you for a share of your max HP."), 12f),
    SHIELD_BUBBLE(tr("SHIELD BUBBLE"), tr("A moment where nothing can hurt you."), 16f),
    SHOCK_NOVA(tr("SHOCK NOVA"), tr("A ring of energy hits every threat around the pet."), 8f),
    HOMING_MISSILES(tr("HOMING MISSILES"), tr("Two missiles that seek out threats."), 7f),
    PIERCING_LASER(tr("PIERCING LASER"), tr("A heavy shot that passes through threats."), 6f),
    RAPID_FIRE(tr("RAPID FIRE"), tr("Triple fire rate for a few seconds."), 14f);
}

/** What a run needs to know about an equipped pet. */
data class PetLoadout(val art: Int, val rarity: GearRarity, val abilities: List<PetAbility> = emptyList()) {
    fun encode(): String = "$art:${rarity.name}:" + abilities.joinToString(",") { it.name }

    companion object {
        fun decode(s: String?): PetLoadout? {
            if (s.isNullOrBlank()) return null
            val parts = s.split(':')
            val art = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val r = parts.getOrNull(1)?.let { n -> runCatching { GearRarity.valueOf(n) }.getOrNull() } ?: return null
            val ab = parts.getOrNull(2).orEmpty().split(',').mapNotNull { n -> runCatching { PetAbility.valueOf(n) }.getOrNull() }.take(Pets.MAX_ABILITIES)
            return PetLoadout(Pets.validPet(art), r, ab)
        }
    }
}

/**
 * Where the incubator's clock stands (anti-cheat). Hatch time only counts on the phone's
 * since-boot clock, which changing the date/time doesn't touch; across a reboot the gap is
 * checked against network time ([com.cyberoperative.game.data.TrustedClock]).
 */
@Serializable
data class ClockAnchor(
    @SerialName("b") val boot: String = "",
    @SerialName("m") val mono: Long = 0,
    /** Real time at [mono] (network-checked when [verified]). */
    @SerialName("t") val trusted: Long = 0,
    @SerialName("v") val verified: Boolean = false
)

/** One incubator: the egg in it and its hatching progress, in no-stone milliseconds. */
@Serializable
data class IncubatorSlot(@SerialName("e") val egg: PetEgg, @SerialName("p") val progress: Long = 0) {
    val fraction: Float get() = (progress.toFloat() / egg.baseMs).coerceIn(0f, 1f)
}

/**
 * The incubators (owner, 0.45.0: three individual incubators, each hatching its own egg at the
 * same time) and up to six Inferno Stones, which speed up all three.
 */
@Serializable
data class Incubator(
    /** Pre-0.45 (one hatching + a queue): kept only so old saves load; see Incubators.migrate. */
    @SerialName("c") val current: PetEgg? = null,
    @SerialName("p") val progress: Long = 0,
    @SerialName("q") val queue: List<PetEgg> = emptyList(),
    /** The three incubators ([Pets.SLOTS]); null = empty. */
    @SerialName("h") val slots: List<IncubatorSlot?> = List(Pets.SLOTS) { null },
    /** Inferno Stones socketed (0..[Pets.MAX_STONES]). */
    @SerialName("s") val stones: Int = 0,
    @SerialName("k") val clock: ClockAnchor = ClockAnchor(),
    /** After a reboot the clock is paused until network time confirms the gap. */
    @SerialName("w") val waitingForTime: Boolean = false
) {
    fun slot(i: Int): IncubatorSlot? = slots.getOrNull(i)

    /** Milliseconds of real time left in incubator [i] at the current stone count (0 when empty). */
    fun remainingMs(i: Int): Long {
        val s = slot(i) ?: return 0
        return ((s.egg.baseMs - s.progress).coerceAtLeast(0) * Pets.timeFactor(stones)).toLong()
    }

    val eggsIn: List<PetEgg> get() = slots.mapNotNull { it?.egg }
    val busy: Boolean get() = slots.any { it != null }
    val full: Boolean get() = slots.size >= Pets.SLOTS && slots.none { it == null }
}

object Pets {
    /** Cut from the owner's ten sheets (resources/pets). */
    const val PET_COUNT = 1005
    const val EGG_COUNT = 872

    /*
     * The 0.40.0 set (owner: "500 more pets ranging from glitched to encrypted"): generated by
     * tools/pets/gen_new_pets.py into pages 4–5 (pets_4/5, eggs_4/5). New pet k is art
     * [NEW_BASE] + k and its own egg is egg art [NEW_BASE] + k; new_pets.txt has each one's
     * tier, name and egg name. GLITCHED and better eggs hatch these.
     */
    const val NEW_BASE = 1024
    /**
     * 500 from 0.40.0 (GLITCHED … ENCRYPTED) + 500 animated ENCRYPTED from 0.41.0 + 50 animated
     * GLITCHED ENCRYPTED from 0.49.0 (tools/pets/gen_gx_pets.py; pages 7–8) + 0.49.1's 100
     * GLITCHED, 100 GLITCHED ENCRYPTED and 100 ENCRYPTED (tools/pets/gen_more_pets.py; pages 8–9).
     */
    const val NEW_COUNT = 1350

    private class NewPet(val tier: GearRarity, val name: String, val egg: String, val anim: PetAnim?)
    private val newPets: List<NewPet> by lazy {
        runCatching {
            Pets::class.java.classLoader?.getResourceAsStream("pets/new_pets.txt")?.use { it.readBytes().decodeToString() }
        }.getOrNull().orEmpty().lines().filter { it.isNotBlank() }.mapNotNull { l ->
            val p = l.split('|')
            val tier = runCatching { GearRarity.valueOf(p[0]) }.getOrNull() ?: return@mapNotNull null
            NewPet(tier, p.getOrElse(1) { "Pet" }, p.getOrElse(2) { "Egg" }, PetAnim.parse(p.getOrNull(3)))
        }
    }
    private val newByTier: Map<GearRarity, List<Int>> by lazy { newPets.indices.groupBy { newPets[it].tier } }

    fun isNewArt(art: Int): Boolean = art >= NEW_BASE && art - NEW_BASE < newPets.size
    /** A pet picture index that exists (old 0 until [PET_COUNT], or a new one). */
    fun validPet(art: Int): Int = if (isNewArt(art)) art else art.coerceIn(0, PET_COUNT - 1)
    fun validEgg(art: Int): Int = if (isNewArt(art)) art else art.coerceIn(0, EGG_COUNT - 1)
    /** The animated ENCRYPTED pets' moving parts (0.41.0), or null. */
    fun anim(art: Int): PetAnim? = if (isNewArt(art)) newPets[art - NEW_BASE].anim else null

    /** The rarity a new pet was designed for, or null for the owner's original pets. */
    fun designedTier(art: Int): GearRarity? = if (isNewArt(art)) newPets[art - NEW_BASE].tier else null
    /** Atlas layout: 16 × 16 cells per page; pets 64 px, eggs 48 px. */
    const val PER_PAGE = 256
    const val COLS = 16
    const val PET_PX = 64
    const val EGG_PX = 48

    const val MAX_STONES = 6
    /** Each Inferno Stone takes 15% off the hatch time; six take 90% (owner). */
    const val STONE_CUT = 0.15f
    /** Incubators hatching at the same time (owner, 0.45.0; was one plus a queue of three). */
    const val SLOTS = 3
    /** Eggs held (owner, 0.52.0: 1,000). */
    const val MAX_EGGS = 1000
    /** Pets and eggs stored in the DATA VAULT (0.52.0), together. */
    const val VAULT_SIZE = 1500

    /**
     * What a pet or an egg sells for (owner, 0.52.0: "10× value of similar rarity weapon"):
     * ten times the sell price of a same-rarity weapon at the account's OP level [level].
     */
    fun value(rarity: GearRarity, level: Int): Int =
        10 * Gear.sellPrice(GearItem(0L, GearSlot.PRIMARY, rarity, 0, level.coerceIn(1, OpProgress.MAX_LEVEL), 0f))

    /**
     * INSTANT HATCH price in ◇ (owner, 0.42.0; ◇ only, never €): ×4 of
     * ⌈rarity × minutes remaining ÷ 6⌉, rarity 1 (COMMON) … 11 (ENCRYPTED). A fresh ENCRYPTED egg
     * (10 h) is ◇4,400; it falls as the egg hatches and with Inferno Stones.
     */
    fun instantHatchPrice(r: GearRarity, remainingMs: Long): Int {
        if (remainingMs <= 0L) return 0
        val minutes = (remainingMs + 59_999L) / 60_000L
        return ((r.ordinal + 1) * minutes + 5).toInt() / 6 * HATCH_PRICE_MUL
    }
    const val HATCH_PRICE_MUL = 4

    /**
     * The € way (owner, 0.43.0): the account's highest operator level × the ◇ price × 100.
     * A fresh ENCRYPTED egg at OP 60 is €26.4M; it falls with the ◇ price as the egg hatches.
     */
    fun instantHatchEuros(diamondPrice: Int, accountOpLevel: Int): Long =
        accountOpLevel.coerceAtLeast(1).toLong() * diamondPrice * HATCH_EURO_MUL
    const val HATCH_EURO_MUL = 100L

    fun timeFactor(stones: Int): Float = (1f - STONE_CUT * stones.coerceIn(0, MAX_STONES)).coerceAtLeast(0.1f)

    /** Hatch time by rarity (owner, 0.38.0: 5 min … 8 h). */
    fun hatchMs(r: GearRarity): Long = 60_000L * when (r) {
        GearRarity.COMMON -> 5
        GearRarity.UNCOMMON -> 10
        GearRarity.RARE -> 20
        GearRarity.EPIC -> 60
        GearRarity.LEGENDARY -> 120
        GearRarity.GLITCHED -> 150
        GearRarity.FORGED -> 180
        GearRarity.VOLTAIC -> 240
        GearRarity.CELESTIAL -> 360
        GearRarity.ABYSSAL -> 480
        // Pets top out at ENCRYPTED; GLITCHED ENCRYPTED (gear only, 0.47.0) reads as it.
        GearRarity.ENCRYPTED -> 600
        // GLITCHED ENCRYPTED pets (owner, 0.49.0): 12 h, above ENCRYPTED's 10 h.
        GearRarity.GLITCHED_ENCRYPTED -> 720
    }

    // --- Art --------------------------------------------------------------------------------

    /** Colour class per pet / egg picture (R O Y V G C B P M W K), worked out when the art was cut. */
    private val hues: Pair<String, String> by lazy {
        val text = runCatching {
            Pets::class.java.classLoader?.getResourceAsStream("pets/hues.txt")?.use { it.readBytes().decodeToString() }
        }.getOrNull().orEmpty().lines()
        (text.getOrNull(0)?.trim().orEmpty()) to (text.getOrNull(1)?.trim().orEmpty())
    }
    private fun petHue(i: Int): Char = hues.first.getOrElse(i) { 'B' }
    private fun eggHue(i: Int): Char = hues.second.getOrElse(i) { 'B' }

    /** An egg picture's rarity, by its colour (matching the gear rarity colours). */
    fun eggArtRarity(i: Int): GearRarity = designedTier(i) ?: when (eggHue(i)) {
        'W', 'K' -> GearRarity.COMMON
        'G' -> GearRarity.UNCOMMON
        'B' -> GearRarity.RARE
        'P', 'M' -> GearRarity.EPIC
        'Y' -> if (i % 2 == 0) GearRarity.LEGENDARY else GearRarity.VOLTAIC
        'V' -> GearRarity.VOLTAIC
        'O' -> GearRarity.FORGED
        'C' -> GearRarity.CELESTIAL
        'R' -> GearRarity.ABYSSAL
        else -> GearRarity.RARE
    }

    private val eggsByRarity: Map<GearRarity, List<Int>> by lazy {
        val m = (0 until EGG_COUNT).groupBy { eggArtRarity(it) }
        GearRarity.entries.associateWith { r -> m[r].orEmpty().ifEmpty { (0 until EGG_COUNT).toList() } }
    }

    private val hueWord = mapOf(
        'R' to "Crimson", 'O' to "Ember", 'Y' to "Amber", 'V' to "Volt", 'G' to "Venom", 'C' to "Frost",
        'B' to "Azure", 'P' to "Violet", 'M' to "Neon", 'W' to "Ghost", 'K' to "Shadow"
    )
    private val species = listOf(
        "Byteling", "Glitchpup", "Sparkmite", "Codecub", "Packetling", "Nanokit", "Ratchet", "Cogling",
        "Voltpaw", "Pixelfang", "Bitbug", "Circuitkin", "Fluxling", "Chipling", "Datamite", "Hexpup",
        "Ionfox", "Proxypaw", "Rootling", "Scriptling", "Shardling", "Synthcub", "Torqling", "Vectorkin",
        "Wirefox", "Zapling", "Boltling", "Crawlbot", "Dronelet", "Sentrykin", "Mechapup", "Widget",
        "Gizmo", "Rivet", "Sprocket", "Servo", "Quark", "Tensor", "Kernel", "Cipher"
    )
    private val eggWords = listOf("Core", "Shell", "Nova", "Pulse", "Prism", "Vault", "Rift", "Signal", "Cipher", "Flux", "Nexus", "Ember")

    fun petName(art: Int): String = if (isNewArt(art)) newPets[art - NEW_BASE].name else "${hueWord[petHue(art)] ?: "Azure"} ${species[(art * 7919 + 13) % species.size]}"
    fun eggName(e: PetEgg): String = if (isNewArt(e.art)) newPets[e.art - NEW_BASE].egg else "${hueWord[eggHue(e.art)] ?: "Azure"} ${eggWords[(e.art * 31 + 7) % eggWords.size]} Egg"

    /** An egg picture of [rarity] (a pet hatched before eggs were remembered). */
    fun eggPictureFor(rarity: GearRarity, seed: Long): Int {
        val pics = eggsByRarity.getValue(rarity)
        return pics[(seed.mod(pics.size.toLong())).toInt()]
    }

    /** Pets and eggs go all the way to GLITCHED ENCRYPTED (owner, 0.49.0: 50 of them). */
    val TOP = GearRarity.GLITCHED_ENCRYPTED
    /** The rarities pets come in. */
    val RARITIES: List<GearRarity> get() = GearRarity.entries.filter { it <= TOP }

    /** A new egg of [wanted] rarity (capped at [TOP]): its picture matches the rarity's colour; the pet inside is any of them. */
    fun rollEgg(rng: Random, uid: Long, wanted: GearRarity): PetEgg {
        val rarity = wanted.coerceAtMost(TOP)
        val pics = eggsByRarity.getValue(rarity)
        // GLITCHED and up: one of the 0.40.0 pets designed for that rarity, in its own egg.
        newByTier[rarity]?.takeIf { it.isNotEmpty() }?.let { ks ->
            val k = ks[rng.nextInt(ks.size)]
            return PetEgg(uid, rarity, NEW_BASE + k, NEW_BASE + k)
        }
        return PetEgg(uid, rarity, pics[rng.nextInt(pics.size)], rng.nextInt(PET_COUNT))
    }

    // --- Combat -------------------------------------------------------------------------------

    const val MAX_ABILITIES = 4

    /** How many abilities, and which, a pet has (from its id, so it never changes). */
    fun abilitiesFor(uid: Long, art: Int, rarity: GearRarity): List<PetAbility> {
        val rng = Random(uid * 1_000_003L + art * 7919L + 17)
        var n = 1
        if (rarity >= GearRarity.EPIC && rng.nextFloat() < (if (rarity >= GearRarity.GLITCHED) 0.7f else 0.45f)) n++
        if (n == 2 && rarity >= GearRarity.GLITCHED && rng.nextFloat() < 0.4f) n++
        if (n == 3 && rng.nextFloat() < 0.25f) n++
        return PetAbility.entries.shuffled(rng).take(n.coerceAtMost(MAX_ABILITIES))
    }

    /** Ability power (share of the operative's payload, before the pet's rarity multiplier) and cooldown. */
    fun abilityCooldown(a: PetAbility, r: GearRarity): Float = a.cooldown * (1f - 0.04f * r.ordinal).coerceAtLeast(0.5f)

    /** Pet shot damage as a share of its operative's payload. */
    fun damageMul(r: GearRarity): Float = when (r) {
        GearRarity.COMMON -> 0.30f
        GearRarity.UNCOMMON -> 0.36f
        GearRarity.RARE -> 0.44f
        GearRarity.EPIC -> 0.54f
        GearRarity.LEGENDARY -> 0.66f
        GearRarity.GLITCHED -> 0.74f
        GearRarity.FORGED -> 0.80f
        GearRarity.VOLTAIC -> 0.95f
        GearRarity.CELESTIAL -> 1.10f
        GearRarity.ABYSSAL -> 1.30f
        GearRarity.ENCRYPTED -> 1.55f
        GearRarity.GLITCHED_ENCRYPTED -> 1.8f
    }

    /** Seconds between pet shots: 1.1 s for COMMON down to about 0.5 s for ENCRYPTED. */
    fun fireInterval(r: GearRarity): Float = 1.1f - 0.0625f * r.ordinal
    const val RANGE = 460f

    // --- Drops ----------------------------------------------------------------------------------

    /** Egg chance from a boss and an elite (each operative rolls its own). */
    const val BOSS_EGG = 0.35f
    const val ELITE_EGG = 0.02f

    /**
     * Inferno Stone chance from a boss on INFERNO [level]: an ABYSSAL-tier find, the same
     * 0.4% × INFERNO level as ABYSSAL gear, at most 20%.
     */
    fun stoneChance(level: Int): Float = if (level <= 0) 0f else (0.004f * level).coerceAtMost(0.2f)

    /** Store eggs (◇). Defaults: change prices / odds here. */
    enum class StoreEgg(val label: String, val price: Long, val odds: List<Pair<GearRarity, Float>>) {
        MYSTERY(tr("MYSTERY EGG"), 150, listOf(GearRarity.COMMON to 40f, GearRarity.UNCOMMON to 30f, GearRarity.RARE to 20f, GearRarity.EPIC to 8f, GearRarity.LEGENDARY to 2f)),
        EPIC(tr("EPIC EGG"), 600, listOf(GearRarity.EPIC to 85f, GearRarity.LEGENDARY to 15f)),
        LEGENDARY(tr("LEGENDARY EGG"), 1500, listOf(GearRarity.LEGENDARY to 100f));

        fun roll(rng: Random): GearRarity {
            var x = rng.nextFloat() * odds.sumOf { it.second.toDouble() }.toFloat()
            for ((r, w) in odds) { x -= w; if (x <= 0f) return r }
            return odds.last().first
        }
    }
}

/**
 * Moving parts of an animated ENCRYPTED pet (owner, 0.41.0: "special orbs rotating them or
 * different moving mechanical parts"). Parsed from new_pets.txt ("KIND:count:RRGGBB|RAINBOW:speed;…"),
 * drawn live by `ui/common/PetFx`.
 */
class PetAnim(val parts: List<Part>) {
    /** GLITCH (0.49.0): the sprite itself tears into red / cyan slices now and then. */
    enum class Kind { ORBS, RING, GEARS, PROP, SHARDS, HELIX, SCAN, PULSE, FLOAT, GLITCH }
    /** [color] is ARGB, or null for RAINBOW (cycles every rarity colour); [gx]: cyan ↔ red (GLITCHED ENCRYPTED). */
    class Part(val kind: Kind, val count: Int, val color: Long?, val speed: Float, val gx: Boolean = false)

    val floats: Boolean get() = parts.any { it.kind == Kind.FLOAT }
    val glitches: Boolean get() = parts.any { it.kind == Kind.GLITCH }

    companion object {
        fun parse(s: String?): PetAnim? {
            if (s.isNullOrBlank()) return null
            val parts = s.split(';').mapNotNull { p ->
                val f = p.split(':')
                val kind = runCatching { Kind.valueOf(f[0]) }.getOrNull() ?: return@mapNotNull null
                val gx = f.getOrNull(2) == "GX"
                val color = f.getOrNull(2)?.let { if (it == "RAINBOW" || it == "GX") null else (0xFF000000L or (it.toLongOrNull(16) ?: 0xFFFFFFL)) }
                Part(kind, f.getOrNull(1)?.toIntOrNull() ?: 1, color, f.getOrNull(3)?.toFloatOrNull() ?: 1f, gx)
            }
            return if (parts.isEmpty()) null else PetAnim(parts)
        }
    }
}

/** PET CODEX (0.50.0): a pet you've hatched — the egg it came from and the best rarity you've had it in. */
@Serializable
data class PetDexEntry(
    @SerialName("e") val egg: Int,
    @SerialName("r") val best: GearRarity
)

/**
 * DAILY EGGS (owner, 0.50.0): three single eggs on sale, of random rarities and kinds. All three
 * roll again every [ROTATE_MS]; buying the last one rolls three new ones at once.
 * 0.50.1 (owner): the store sells nothing below GLITCHED LEGENDARY, priced by rarity.
 * 0.51.0 (owner): ◇100 for GLITCHED, +◇50 per rarity above it (… ◇400 GLITCHED ENCRYPTED).
 */
object EggOffers {
    const val COUNT = 3
    const val BASE_PRICE = 100L
    const val STEP_PRICE = 50L

    fun price(r: GearRarity): Long = BASE_PRICE + STEP_PRICE * (r.ordinal - GearRarity.GLITCHED.ordinal).coerceAtLeast(0)
    const val ROTATE_MS = 24L * 60L * 60L * 1000L

    /** Rarity weights (sum 100), GLITCHED LEGENDARY and up only; the rarest seldom. Defaults: tune here. */
    val weights: List<Pair<GearRarity, Float>> = listOf(
        GearRarity.GLITCHED to 35f, GearRarity.FORGED to 22f, GearRarity.VOLTAIC to 16f, GearRarity.CELESTIAL to 12f,
        GearRarity.ABYSSAL to 8f, GearRarity.ENCRYPTED to 5f, GearRarity.GLITCHED_ENCRYPTED to 2f
    )

    fun rollRarity(rng: Random): GearRarity {
        var x = rng.nextFloat() * weights.sumOf { it.second.toDouble() }.toFloat()
        for ((r, w) in weights) { x -= w; if (x <= 0f) return r }
        return weights.last().first
    }

    /** Three fresh offers (uids are set when bought). */
    fun roll(rng: Random): List<PetEgg> = List(COUNT) { Pets.rollEgg(rng, 0L, rollRarity(rng)) }
}
