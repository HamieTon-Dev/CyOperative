package com.cyberoperative.game.data

/**
 * Environment themes (owner, 2026-10-08: "different color levels every 3
 * levels, different light colors, new walls, different tile textures and
 * layouts, generating a little different each time").
 *
 * Every 3 levels the run moves to the next theme in a per-run shuffled order,
 * so two runs never walk the same sequence. Inside a theme each level still
 * rolls its own floor pattern, tile size, trench spacing and a small hue/
 * brightness shift ([FloorStyle]), so no two rooms look identical.
 */
enum class FloorPattern { PLATES, HEX, GRID, CIRCUIT, DIAMOND, BRICK, DOTS, GROOVES }

data class Environment(
    val id: String,
    val name: String,
    /** Floor under everything. */
    val base: Long,
    /** Two plate shades the floor alternates between. */
    val plateA: Long,
    val plateB: Long,
    /** Bevel highlight on plates. */
    val bevel: Long,
    /** Trenches, lit panels, wall edges: the room's main light. */
    val accent: Long,
    /** Second light colour (data pixels, some decor). */
    val accent2: Long,
    /** Light pools thrown by hardware. */
    val light: Long,
    /** Obstacle body tone blended into every wall/obstacle. */
    val wallTone: Long,
    /** Neon trim for racks, crates and blast walls in this theme. */
    val wallTrim: Long,
    val patterns: List<FloorPattern>
)

/** The per-level variation inside a theme. */
data class FloorStyle(
    val env: Environment,
    val pattern: FloorPattern,
    val tile: Float,
    val trenchEveryX: Int,
    val trenchEveryY: Int,
    /** −1..1: small brightness shift so neighbouring levels differ. */
    val shade: Float,
    /** Packets in trenches run up (true) or down. */
    val flowUp: Boolean,
    /** Diagonal light sweep across the floor. */
    val sweep: Boolean
)

object Environments {

    val all: List<Environment> = listOf(
        Environment("cyan_grid", "CYAN GRID", 0xFF070D18, 0xFF111D33, 0xFF0F1A2E, 0x2A7DD3FF, 0xFF00E5FF, 0xFF00FF9C, 0xFF00FF9C,
            0xFF17233A, 0xFF00E5FF, listOf(FloorPattern.PLATES, FloorPattern.GRID, FloorPattern.CIRCUIT)),
        Environment("emerald_works", "EMERALD DATAWORKS", 0xFF04110C, 0xFF0C2A1D, 0xFF0A2318, 0x2A6CFFB4, 0xFF00FF9C, 0xFF7DFFB2, 0xFF2BFF88,
            0xFF12301F, 0xFF3CFF9A, listOf(FloorPattern.HEX, FloorPattern.CIRCUIT, FloorPattern.DOTS)),
        Environment("amethyst_core", "AMETHYST CORE", 0xFF0B0716, 0xFF1D1235, 0xFF180F2D, 0x2AB98CFF, 0xFFA259FF, 0xFFFF6AE6, 0xFFB070FF,
            0xFF231640, 0xFFC08CFF, listOf(FloorPattern.DIAMOND, FloorPattern.HEX, FloorPattern.PLATES)),
        Environment("magma_forge", "MAGMA FORGE", 0xFF140804, 0xFF2A140C, 0xFF22100A, 0x2AFF9A5A, 0xFFFF7A1A, 0xFFFFD426, 0xFFFF5A2A,
            0xFF2E1810, 0xFFFF8A3D, listOf(FloorPattern.BRICK, FloorPattern.GROOVES, FloorPattern.PLATES)),
        Environment("arctic_server", "ARCTIC SERVER FARM", 0xFF0A1218, 0xFF1C2B38, 0xFF18252F, 0x33DDEEFF, 0xFF9FE7FF, 0xFFFFFFFF, 0xFFBFEFFF,
            0xFF223444, 0xFFCFF4FF, listOf(FloorPattern.GRID, FloorPattern.PLATES, FloorPattern.DOTS)),
        Environment("toxic_lab", "TOXIC LAB", 0xFF0C1004, 0xFF1D260A, 0xFF182008, 0x2AD6FF4D, 0xFFB8FF2E, 0xFFFFE14D, 0xFFA8FF3A,
            0xFF222C0E, 0xFFC8FF4A, listOf(FloorPattern.GROOVES, FloorPattern.HEX, FloorPattern.CIRCUIT)),
        Environment("rose_neon", "ROSE NEON DISTRICT", 0xFF14060E, 0xFF2A0F20, 0xFF220C1A, 0x2AFF8AC8, 0xFFFF4DB8, 0xFF00E5FF, 0xFFFF6AC8,
            0xFF2E1226, 0xFFFF7AD0, listOf(FloorPattern.DIAMOND, FloorPattern.GRID, FloorPattern.BRICK)),
        Environment("golden_vault", "GOLDEN VAULT", 0xFF120E04, 0xFF2A220C, 0xFF221B0A, 0x2AFFE38A, 0xFFFFD426, 0xFFFFF3B0, 0xFFFFC94D,
            0xFF2E2610, 0xFFFFD95A, listOf(FloorPattern.PLATES, FloorPattern.DIAMOND, FloorPattern.BRICK)),
        Environment("deep_ocean", "DEEP OCEAN RELAY", 0xFF031014, 0xFF0A2630, 0xFF081F28, 0x2A5AE8FF, 0xFF00C8E0, 0xFF2E9BFF, 0xFF00D4B4,
            0xFF0E2A34, 0xFF2EE6E6, listOf(FloorPattern.HEX, FloorPattern.GROOVES, FloorPattern.DOTS)),
        Environment("solar_flare", "SOLAR FLARE ARRAY", 0xFF140B02, 0xFF2C1A06, 0xFF241505, 0x2AFFC060, 0xFFFFA820, 0xFFFF4A2A, 0xFFFFB84D,
            0xFF301E08, 0xFFFFB02E, listOf(FloorPattern.CIRCUIT, FloorPattern.GRID, FloorPattern.GROOVES)),
        Environment("ghost_mono", "GHOST PROTOCOL", 0xFF0B0B0D, 0xFF1C1C21, 0xFF17171B, 0x33FFFFFF, 0xFFE6E8EE, 0xFF9AA4B4, 0xFFD8DEE8,
            0xFF232328, 0xFFF0F2F6, listOf(FloorPattern.DOTS, FloorPattern.GRID, FloorPattern.PLATES)),
        Environment("ultraviolet", "ULTRAVIOLET MAINFRAME", 0xFF07051A, 0xFF130F38, 0xFF100C2E, 0x2A8A7DFF, 0xFF6A5CFF, 0xFF00FFC8, 0xFF7A6CFF,
            0xFF19143E, 0xFF8C80FF, listOf(FloorPattern.CIRCUIT, FloorPattern.DIAMOND, FloorPattern.HEX))
    )

    /** Theme for a level: changes every 3 levels, in an order shuffled per run by [runSeed]. */
    fun forLevel(level: Int, runSeed: Long): Environment {
        val block = (level - 1).coerceAtLeast(0) / 3
        val order = all.indices.shuffled(kotlin.random.Random(runSeed))
        return all[order[block % all.size]]
    }

    /** True on the first level of a new theme block (for the "ENTERING …" banner). */
    fun isNewTheme(level: Int): Boolean = (level - 1) % 3 == 0

    /** The level's own variation inside its theme. */
    fun styleFor(level: Int, runSeed: Long): FloorStyle {
        val env = forLevel(level, runSeed)
        val r = kotlin.random.Random(runSeed * 31 + level * 7919L)
        return FloorStyle(
            env = env,
            pattern = env.patterns[r.nextInt(env.patterns.size)],
            tile = listOf(48f, 60f, 60f, 72f)[r.nextInt(4)],
            trenchEveryX = 3 + r.nextInt(4),
            trenchEveryY = 4 + r.nextInt(4),
            shade = r.nextFloat() * 2f - 1f,
            flowUp = r.nextBoolean(),
            sweep = r.nextFloat() < 0.35f
        )
    }
}
