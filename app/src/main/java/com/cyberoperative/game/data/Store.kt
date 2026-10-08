package com.cyberoperative.game.data

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
        DiamondPack("diamonds_10000", 10_000, "$69.99")
    )

    val revivePacks = listOf(
        RevivePack("revive_1", 1, 100),
        RevivePack("revive_7", 7, 500),
        RevivePack("revive_20", 20, 800),
        RevivePack("revive_250", 250, 6_500)
    )

    const val SKIN_PRICE = 100

    /** NEON OPERATIVE (the app-icon operative): ◇100 alone, or included in the skin pack. */
    const val NEON_OPERATIVE_PRICE = 100
    const val ALL_SKINS_PRICE = 300

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
    NEON
}

/**
 * Operative skins: recolours of the >_< shield — the same mark as the CYBER
 * OPERATIVE agent in CyOps TD. One per CyOps TD agent colour, plus the CORE
 * skin palettes and the SPECTRUM agent skin, all from CyOps TD's palette.
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
    val free: Boolean = false
)

object OperativeSkins {
    val DEFAULT = OperativeSkin("default", "CYBER OPERATIVE", "The logo", 0xFF23C55E, 0xFF6EF7A5, 0xFF07301E, free = true)

    val all: List<OperativeSkin> = listOf(
        DEFAULT,
        // CyOps TD agent colours
        OperativeSkin("firewall", "FIREWALL", "Agent: FIREWALL", 0xFF00FF9C, 0xFFB8FFE0, 0xFF033322),
        OperativeSkin("ids", "IDS", "Agent: IDS / AI SENTINEL", 0xFF00E5FF, 0xFFB5F6FF, 0xFF032A33),
        OperativeSkin("blue_hat", "BLUE HAT", "Agent: BLUE HAT / TARPIT / IPS", 0xFF2E7BFF, 0xFFA9C8FF, 0xFF081A3D),
        OperativeSkin("analyst", "ANALYST", "Agent: ANALYST", 0xFFFFD426, 0xFFFFF0A8, 0xFF2E2603),
        OperativeSkin("cryptographer", "CRYPTOGRAPHER", "Agent: CRYPTOGRAPHER / QUANTUM", 0xFFA259FF, 0xFFDCC2FF, 0xFF1E0B36),
        OperativeSkin("zero_day_hunter", "ZERO-DAY HUNTER", "Agent: ZERO-DAY HUNTER", 0xFFFF7A1A, 0xFFFFCFA8, 0xFF331604),
        OperativeSkin("red_hat", "RED HAT", "Agent: RED HAT", 0xFFFF2D55, 0xFFFFB3C2, 0xFF33070F),
        OperativeSkin("ace", "ACE", "Agent: ACE", 0xFFE6EEFA, 0xFFFFFFFF, 0xFF1A2130),
        OperativeSkin("anti_duck", "ANTI DUCK HOLOGRAM", "Agent: ANTI DUCK USB", 0xFF5CFF7A, 0xFFF2F25A, 0xFF0E2A12, SkinEffect.HOLOGRAM),
        OperativeSkin("spectrum", "SPECTRUM", "Skin: SPECTRUM AGENTS", 0xFF00E5FF, 0xFFFFFFFF, 0xFF0C1322, SkinEffect.SPECTRUM),
        // CyOps TD CORE-SERVER skin palettes
        OperativeSkin("reactor", "REACTOR", "Core skin: REACTOR", 0xFFFF9A03, 0xFFFFD08A, 0xFF17100A, SkinEffect.PULSE),
        OperativeSkin("meridian", "MERIDIAN", "Core skin: MERIDIAN", 0xFFE8BE55, 0xFF7F73C0, 0xFF0B0A1E),
        OperativeSkin("glacier", "GLACIER", "Core skin: GLACIER", 0xFF96E0FF, 0xFFE6F8FF, 0xFF071620),
        OperativeSkin("mainframe", "MAINFRAME", "Core skin: MAINFRAME", 0xFF00FF52, 0xFFB3FFC9, 0xFF031008),
        OperativeSkin("cascade", "CASCADE", "Core skin: CASCADE", 0xFF00F578, 0xFF167F4C, 0xFF020C06),
        OperativeSkin("neongrid", "NEONGRID", "Core skin: NEONGRID", 0xFF0062FF, 0xFF00FF9C, 0xFF030E24, SkinEffect.PULSE),
        OperativeSkin("void", "VOID", "Core skin: VOID", 0xFFB653FF, 0xFFE2C4FF, 0xFF0D0716)
    )

    val paid: List<OperativeSkin> get() = all.filter { !it.free }

    fun byId(id: String): OperativeSkin = all.firstOrNull { it.id == id } ?: DEFAULT
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
    NONE("none", "STATIC", "The plain circuit grid.", 0xFF12203A, 0, 0f),
    DRIFT("drift", "DRIFT", "Slow diagonal data currents crossing the arena.", 0xFF0D658C, 34, 0.055f),
    LATTICE("lattice", "LATTICE", "A circuit lattice that breathes with the threat count.", 0xFF167D5B, 40, 0.18f),
    AURORA("aurora", "AURORA", "Broad bands of cold light moving behind everything.", 0xFF1C59A8, 30, 0.04f),
    RAINFALL("rainfall", "RAINFALL", "Sparse columns of falling binary.", 0xFF177F6E, 36, 0.5f),
    PULSE("pulse", "PULSE", "Rings travelling outward from the operative.", 0xFF2E49A8, 32, 0.22f),
    ORBIT("orbit", "ORBIT", "Slow elliptical traces, like a scheduler at work.", 0xFF3D53A6, 34, 0.035f),
    HEATMAP("heatmap", "HEATMAP", "A coarse grid whose cells warm with the traffic.", 0xFF217B8C, 30, 0.12f);

    companion object {
        fun byId(id: String): LivingBackground = entries.firstOrNull { it.id == id } ?: NONE
        val purchasable: List<LivingBackground> get() = entries.filter { it != NONE }
    }
}
