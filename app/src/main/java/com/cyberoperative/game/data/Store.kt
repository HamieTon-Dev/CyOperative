package com.cyberoperative.game.data

import com.cyberoperative.game.i18n.tr

/**
 * Store catalogue (§33, §58) — owner's structure, 2026-10-07.
 *
 * ◇ packs are real-money Google Play products (prices are the owner's USD
 * prices; Play shows the localised price at runtime). Everything else is
 * bought with ◇ inside the game.
 */
data class DiamondPack(val productId: String, val diamonds: Int, val usdPrice: String)

data class RevivePack(val id: String, val revives: Int, val priceDiamonds: Int)

object StoreCatalog {

    val diamondPacks = listOf(
        DiamondPack("diamonds_150", 150, "$1.00"),
        DiamondPack("diamonds_500", 500, "$4.50"),
        DiamondPack("diamonds_1000", 1_000, "$8.50"),
        // Owner, 0.52.0: ◇5,000 for $29.99 replaces ◇10,000 for $69.99 (new product: create
        // "diamonds_5000" in Play Console; Steam itemdef 1005, steam/itemdefs.json).
        DiamondPack("diamonds_5000", 5_000, "$29.99")
    )

    val revivePacks = listOf(
        RevivePack("revive_1", 1, 100),
        RevivePack("revive_7", 7, 500),
        RevivePack("revive_20", 20, 800),
        RevivePack("revive_250", 250, 6_500)
    )

    const val SKIN_PRICE = 100

    /** Premium skins (owner, 2026-10-11): GOLD and NEON (pick your colour), ◇1000 each, not in the pack. */
    const val PREMIUM_SKIN_PRICE = 1_000

    /** NEON OPERATIVE (the app-icon operative): ◇100 alone, or included in the skin pack. */
    const val NEON_OPERATIVE_PRICE = 100
    /**
     * Every ◇100 colour skin (owner, 2026-10-11: ◇1400). Never includes premium skins (GOLD,
     * NEON, or any later skin priced above [SKIN_PRICE]) — see [OperativeSkins.paid].
     */
    const val ALL_SKINS_PRICE = 1_400

    /** Owner decision pending — provisional ◇ prices for living backgrounds. */
    const val BACKGROUND_PRICE = 150
    const val ALL_BACKGROUNDS_PRICE = 600

    /** Revive tokens usable per run, so revives can't make a run infinite (§34). */
    const val MAX_PAID_REVIVES_PER_RUN = 3
}

/** How a skin animates on top of its base colours. */
enum class SkinEffect {
    NONE, SPECTRUM, HOLOGRAM, PULSE,
    /** The app-icon look: blue-green-only colour drift with a breathing glow. */
    NEON,
    /** GLITCHED: slowly runs through every colour like a glitched boss, with the odd glitch jump. */
    GLITCH,
    /** GOLD: polished gold with a light sweeping across it. */
    GOLD,
    /** NEON (custom): the player's own colour, glowing and breathing. */
    GLOW
}

/**
 * Operative skins: recolours of the >_< shield. Named by how they look (owner, 2026-10-11);
 * the ids are the original CyOps TD palette names, kept so owned skins carry over.
 */
data class OperativeSkin(
    val id: String,
    val name: String,
    val origin: String,
    /** Shield edge / outer glow. */
    val edge: Long,
    /** The >_< face. */
    val face: Long,
    /** Shield body fill. */
    val body: Long,
    val effect: SkinEffect = SkinEffect.NONE,
    val free: Boolean = false,
    /** Earned, not bought: unlocked per class by reaching a level on a difficulty (0.17.0). */
    val unlock: SkinUnlock? = null,
    /** ◇ price when bought on its own (premium skins cost more and aren't in the skin pack). */
    val price: Int = StoreCatalog.SKIN_PRICE
) {
    val premium: Boolean get() = price > StoreCatalog.SKIN_PRICE
}

/**
 * "Reach level [level] on [difficulty] with this class" (difficulty = Difficulty enum name).
 * [campaign]: beating World 15's boss unlocks it for every class (owner, 2026-10-11); a
 * [level] above 0 still counts too, so a skin earned the old way stays unlocked.
 */
data class SkinUnlock(val difficulty: String, val level: Int, val campaign: Boolean = false) {
    val label: String get() = if (campaign) tr("Beat World 15's boss · every class")
        else tr("Reach level {0} on {1} with this class", level, when (difficulty) { "MEDIUM" -> tr("Normal"); "HARD" -> tr("Hard"); else -> difficulty.lowercase().replaceFirstChar { it.uppercase() } })
}

object OperativeSkins {
    val DEFAULT = OperativeSkin("default", "CYBER OPERATIVE", tr("The logo"), 0xFF23C55E, 0xFF6EF7A5, 0xFF07301E, free = true)

    /** Class skins (owner, 2026-10-11): every class earns these on its own. */
    val TITANIUM = OperativeSkin("titanium", tr("TITANIUM"), tr("Class skin: level 100 on Normal"), 0xFFAEB7C1, 0xFFE6ECF2, 0xFF2B3138,
        SkinEffect.PULSE, unlock = SkinUnlock("MEDIUM", 100))
    /** Owner, 2026-10-11: now earned by beating World 15's boss (L100 on Hard still counts for anyone who got it that way). */
    val PURPLE = OperativeSkin("purple", tr("PURPLE"), tr("Campaign skin: beat World 15's boss"), 0xFFA259FF, 0xFFE7D2FF, 0xFF1A0833,
        SkinEffect.HOLOGRAM, unlock = SkinUnlock("HARD", 100, campaign = true))
    val GLITCHED = OperativeSkin("glitched", tr("GLITCHED"), tr("Campaign skin: beat World 15's boss"), 0xFFFF2BD6, 0xFFFFFFFF, 0xFF120818,
        SkinEffect.GLITCH, unlock = SkinUnlock("HARD", 0, campaign = true))
    val classSkins: List<OperativeSkin> get() = listOf(TITANIUM, PURPLE, GLITCHED)

    val GOLD = OperativeSkin("gold", tr("GOLD"), tr("Premium skin"), 0xFFFFC832, 0xFFFFF1B8, 0xFF2E2104,
        SkinEffect.GOLD, price = StoreCatalog.PREMIUM_SKIN_PRICE)

    /** NEON: the player picks the colour ([neon]); the catalogue entry shows the default pink. */
    const val NEON_ID = "neon_custom"
    const val NEON_DEFAULT_COLOR = 0xFFFF2BD6
    val NEON = neon(NEON_DEFAULT_COLOR)

    /** The NEON skin in [color] (a bright, fully saturated neon hue). */
    fun neon(color: Long): OperativeSkin {
        val c = color or 0xFF000000
        fun ch(shift: Int) = ((c shr shift) and 0xFF).toInt()
        fun mix(t: Float, toward: Int) = { v: Int -> (v + (toward - v) * t).toInt().coerceIn(0, 255) }
        val light = mix(0.65f, 255); val dark = mix(0.86f, 0)
        fun argb(f: (Int) -> Int) = 0xFF000000 or (f(ch(16)).toLong() shl 16) or (f(ch(8)).toLong() shl 8) or f(ch(0)).toLong()
        return OperativeSkin(NEON_ID, tr("NEON"), tr("Premium skin · pick your colour"), c, argb(light), argb(dark),
            SkinEffect.GLOW, price = StoreCatalog.PREMIUM_SKIN_PRICE)
    }

    /** The colour carried in a co-op skin token ("neon_custom#RRGGBB"), or null. */
    private fun tokenColor(token: String): Long? =
        token.substringAfter('#', "").takeIf { it.length == 6 }?.toLongOrNull(16)?.let { it or 0xFF000000 }

    /** What a profile wears, as one string for co-op: the skin id, plus the colour for NEON. */
    fun token(skin: OperativeSkin): String =
        if (skin.id == NEON_ID) NEON_ID + "#" + (skin.edge and 0xFFFFFF).toString(16).padStart(6, '0').uppercase() else skin.id

    val all: List<OperativeSkin> = listOf(
        DEFAULT, TITANIUM, PURPLE, GLITCHED, GOLD, NEON,
        // Colour skins (owner, 2026-10-11: named by how they look; ids kept so owned skins carry over)
        OperativeSkin("firewall", tr("MINT"), tr("Colour skin"), 0xFF00FF9C, 0xFFB8FFE0, 0xFF033322),
        OperativeSkin("ids", tr("CYAN"), tr("Colour skin"), 0xFF00E5FF, 0xFFB5F6FF, 0xFF032A33),
        OperativeSkin("blue_hat", tr("ROYAL BLUE"), tr("Colour skin"), 0xFF2E7BFF, 0xFFA9C8FF, 0xFF081A3D),
        OperativeSkin("analyst", tr("YELLOW"), tr("Colour skin"), 0xFFFFD426, 0xFFFFF0A8, 0xFF2E2603),
        OperativeSkin("cryptographer", tr("DEEP PURPLE"), tr("Colour skin"), 0xFFA259FF, 0xFFDCC2FF, 0xFF1E0B36),
        OperativeSkin("zero_day_hunter", tr("ORANGE"), tr("Colour skin"), 0xFFFF7A1A, 0xFFFFCFA8, 0xFF331604),
        OperativeSkin("red_hat", tr("RED"), tr("Colour skin"), 0xFFFF2D55, 0xFFFFB3C2, 0xFF33070F),
        OperativeSkin("ace", tr("WHITE"), tr("Colour skin"), 0xFFE6EEFA, 0xFFFFFFFF, 0xFF1A2130),
        OperativeSkin("anti_duck", tr("LIME HOLOGRAM"), tr("Shimmers between lime and yellow"), 0xFF5CFF7A, 0xFFF2F25A, 0xFF0E2A12, SkinEffect.HOLOGRAM),
        OperativeSkin("spectrum", tr("RAINBOW"), tr("Cycles through every colour"), 0xFF00E5FF, 0xFFFFFFFF, 0xFF0C1322, SkinEffect.SPECTRUM),
        // Animated and two-tone skins
        OperativeSkin("reactor", tr("AMBER"), tr("Pulsing glow"), 0xFFFF9A03, 0xFFFFD08A, 0xFF17100A, SkinEffect.PULSE),
        OperativeSkin("meridian", tr("SAND & VIOLET"), tr("Two-tone"), 0xFFE8BE55, 0xFF7F73C0, 0xFF0B0A1E),
        OperativeSkin("glacier", tr("ICE BLUE"), tr("Colour skin"), 0xFF96E0FF, 0xFFE6F8FF, 0xFF071620),
        OperativeSkin("mainframe", tr("GREEN"), tr("Colour skin"), 0xFF00FF52, 0xFFB3FFC9, 0xFF031008),
        OperativeSkin("cascade", tr("EMERALD"), tr("Two-tone"), 0xFF00F578, 0xFF167F4C, 0xFF020C06),
        OperativeSkin("neongrid", tr("ELECTRIC BLUE"), tr("Pulsing glow, mint face"), 0xFF0062FF, 0xFF00FF9C, 0xFF030E24, SkinEffect.PULSE),
        OperativeSkin("void", tr("VIOLET"), tr("Colour skin"), 0xFFB653FF, 0xFFE2C4FF, 0xFF0D0716)
    )

    /** The ◇100 colour skins (what the skin pack covers). */
    val paid: List<OperativeSkin> get() = all.filter { !it.free && it.unlock == null && !it.premium }

    /** By id, or by co-op token ("neon_custom#RRGGBB" is NEON in that colour). */
    fun byId(id: String): OperativeSkin {
        if (id.startsWith("$NEON_ID#")) return neon(tokenColor(id) ?: NEON_DEFAULT_COLOR)
        return all.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * Living backgrounds ported from CyOps TD (§48). Drawn on the arena floor
 * under everything, low alpha, cool tints only — never mistakable for a threat.
 */
enum class LivingBackground(
    val id: String,
    val displayName: String,
    val description: String,
    val tint: Long,
    /** Peak alpha 0..255 — deliberately small. */
    val intensity: Int,
    val speed: Float
) {
    NONE("none", tr("STATIC"), tr("The plain circuit grid."), 0xFF12203A, 0, 0f),
    DRIFT("drift", tr("DRIFT"), tr("Slow diagonal data currents crossing the arena."), 0xFF0D658C, 34, 0.055f),
    LATTICE("lattice", tr("LATTICE"), tr("A circuit lattice that breathes with the threat count."), 0xFF167D5B, 40, 0.18f),
    AURORA("aurora", tr("AURORA"), tr("Broad bands of cold light moving behind everything."), 0xFF1C59A8, 30, 0.04f),
    RAINFALL("rainfall", tr("RAINFALL"), tr("Sparse columns of falling binary."), 0xFF177F6E, 36, 0.5f),
    PULSE("pulse", tr("PULSE"), tr("Rings travelling outward from the operative."), 0xFF2E49A8, 32, 0.22f),
    ORBIT("orbit", tr("ORBIT"), tr("Slow elliptical traces, like a scheduler at work."), 0xFF3D53A6, 34, 0.035f),
    HEATMAP("heatmap", tr("HEATMAP"), tr("A coarse grid whose cells warm with the traffic."), 0xFF217B8C, 30, 0.12f);

    companion object {
        fun byId(id: String): LivingBackground = entries.firstOrNull { it.id == id } ?: NONE
        val purchasable: List<LivingBackground> get() = entries.filter { it != NONE }
    }
}
