package com.cyberoperative.game.data

import com.cyberoperative.game.i18n.tr
import com.cyberoperative.game.core.Rect

/**
 * Arena layouts (§13–14). Every arena is 720 world units wide; heights vary.
 * The player always enters at the bottom centre and leaves through the access
 * port at the top centre, so those two strips are kept clear in every layout.
 *
 * Layouts are original. The runtime additionally mirrors any template
 * horizontally, so N templates give 2N distinct arenas.
 */
enum class ObstacleKind(val label: String) {
    SERVER_RACK(tr("SERVER RACK")),
    /** Barrier cube a boss raises out of the floor (never placed by arena layouts). */
    BARRIER_CUBE(tr("BARRIER CUBE")),
    /** Ransom King's padlocked cube (Lock Grid, Royal Seizure cage). */
    LOCK_CUBE(tr("LOCK CUBE")),
    FIREWALL_NODE(tr("FIREWALL NODE")),
    DATA_PILLAR(tr("DATA PILLAR")),
    COOLING_UNIT(tr("COOLING UNIT")),
    ROUTER(tr("ROUTER")),
    TERMINAL(tr("CORRUPTED TERMINAL")),
    POWER_UNIT(tr("POWER UNIT")),
    FIBER_JUNCTION(tr("FIBER JUNCTION")),
    /** Small blinking server unit (procedural arenas). */
    SMALL_SERVER(tr("SERVER UNIT")),
    /** Stack of hardware crates. */
    CRATES(tr("HARDWARE CRATES")),
    // --- New wall types (owner, 2026-10-08) ---
    /** Armoured bulkhead: long, tall, riveted. */
    BLAST_WALL(tr("BLAST WALL")),
    /** Bundle of coolant pipes with flowing liquid. */
    COOLANT_PIPES(tr("COOLANT PIPES")),
    /** Translucent hologram wall that flickers. */
    HOLO_WALL(tr("HOLO WALL")),
    /** Round reactor core with a pulsing heart. */
    REACTOR(tr("REACTOR CORE")),
    /** Low, glowing energy barrier. */
    ENERGY_BARRIER(tr("ENERGY BARRIER")),
    /** Thin, tall antenna mast with a blinking beacon. */
    ANTENNA_TOWER(tr("ANTENNA TOWER")),
    /** The shopkeeper's counter in the upgrade shop. */
    SHOP_COUNTER(tr("SHOP COUNTER"))
}

/** Non-colliding floor decoration (procedural arenas). */
enum class DecorKind {
    CABLE, VENT, FLOOR_LIGHT, HOLO_PANEL, DATA_POOL, WARNING_STRIPES, FLOOR_TILE,
    // World Kit (owner sheet docs/boss-concepts/world_kit.webp, 2026-10-10).
    /** 02 Broken grid tile: a cracked floor plate. */
    CRACKED_TILE,
    /** 04 Power conduit: red energy line running along the tile seams with an L bend. */
    CONDUIT,
    /** 09 Spark panel: a floor panel with animated electric sparks. */
    SPARK_PANEL,
    /** 12 Light beacon: a pylon that lights the floor around it. */
    BEACON,
    /** Overhead strip light (was the 11 EMP dark-zone emitter; redrawn 2026-10-11 so it can't pass for loot). */
    DARK_EMITTER
}

data class DecorSpec(val kind: DecorKind, val x: Float, val y: Float, val w: Float, val h: Float, val seed: Int = 0)

data class ObstacleSpec(val rect: Rect, val kind: ObstacleKind)

/**
 * Room shape for this build. Phones play tall 720-wide rooms; the PC (Steam) build switches
 * to [wide] landscape rooms at launch, before any room is made. Both co-op players are
 * always on the same platform, so they always share a shape.
 */
object ArenaShape {
    const val PORTRAIT_WIDTH = 720f
    /** PC rooms: three phone rooms wide, zoomed out so a whole room fits a 16:9 screen. */
    const val WIDE_WIDTH = 2160f
    var wide = false
    /** The PC gameplay rules for the run being played (set with [wide] when a run starts). */
    var pcRules = false
    val width: Float get() = if (wide) WIDE_WIDTH else PORTRAIT_WIDTH
    /** Wide rooms hold more threats (more floor to fill). */
    const val WIDE_CROWD = 1.6f
    /** Height range of PC rooms (about one 16:9 screen, plus a little scroll). */
    const val WIDE_MIN_HEIGHT = 1180f
    const val WIDE_MAX_HEIGHT = 1300f
    val crowd: Float get() = if (wide) WIDE_CROWD else 1f
}

data class ArenaTemplate(
    val id: String,
    val name: String,
    val height: Float,
    val obstacles: List<ObstacleSpec>,
    /** Minimum level before this layout can appear (complex layouts later). */
    val minLevel: Int = 1,
    val bossArena: Boolean = false,
    val decor: List<DecorSpec> = emptyList(),
    val width: Float = ArenaShape.width
) {
    companion object {
        const val ARENA_WIDTH = ArenaShape.PORTRAIT_WIDTH
    }
}

object Arenas {

    private fun o(x: Float, y: Float, w: Float, h: Float, k: ObstacleKind) =
        ObstacleSpec(Rect(x, y, x + w, y + h), k)

    private val R = ObstacleKind.SERVER_RACK
    private val F = ObstacleKind.FIREWALL_NODE
    private val P = ObstacleKind.DATA_PILLAR
    private val C = ObstacleKind.COOLING_UNIT
    private val RT = ObstacleKind.ROUTER
    private val T = ObstacleKind.TERMINAL
    private val PW = ObstacleKind.POWER_UNIT
    private val FJ = ObstacleKind.FIBER_JUNCTION

    /** Hand-made layouts for this build's room shape. */
    val templates: List<ArenaTemplate> get() = if (ArenaShape.wide) wideTemplates else portraitTemplates

    private val portraitTemplates: List<ArenaTemplate> = listOf(
        ArenaTemplate("open_grid", tr("OPEN GRID"), 1040f, listOf(
            o(150f, 330f, 70f, 70f, P), o(500f, 330f, 70f, 70f, P),
            o(150f, 640f, 70f, 70f, P), o(500f, 640f, 70f, 70f, P)
        )),
        ArenaTemplate("rack_rows", tr("RACK ROWS"), 1120f, listOf(
            o(70f, 360f, 220f, 54f, R), o(430f, 360f, 220f, 54f, R),
            o(70f, 700f, 220f, 54f, R), o(430f, 700f, 220f, 54f, R)
        )),
        ArenaTemplate("crossroads", tr("CROSSROADS"), 1100f, listOf(
            o(90f, 260f, 150f, 150f, R), o(480f, 260f, 150f, 150f, R),
            o(90f, 640f, 150f, 150f, PW), o(480f, 640f, 150f, 150f, PW)
        )),
        ArenaTemplate("firewall_gate", tr("FIREWALL GATE"), 1160f, listOf(
            o(0f, 520f, 170f, 46f, F), o(280f, 520f, 160f, 46f, F), o(550f, 520f, 170f, 46f, F),
            o(130f, 260f, 60f, 60f, RT), o(530f, 260f, 60f, 60f, RT),
            o(330f, 800f, 60f, 60f, P)
        ), minLevel = 2),
        ArenaTemplate("server_farm", tr("SERVER FARM"), 1200f, listOf(
            o(110f, 250f, 60f, 190f, R), o(330f, 250f, 60f, 190f, R), o(550f, 250f, 60f, 190f, R),
            o(110f, 640f, 60f, 190f, R), o(330f, 640f, 60f, 190f, R), o(550f, 640f, 60f, 190f, R)
        ), minLevel = 3),
        ArenaTemplate("cooling_hall", tr("COOLING HALL"), 1080f, listOf(
            o(90f, 220f, 96f, 96f, C), o(420f, 300f, 96f, 96f, C),
            o(210f, 520f, 96f, 96f, C), o(540f, 600f, 96f, 96f, C),
            o(100f, 760f, 96f, 96f, C)
        ), minLevel = 2),
        ArenaTemplate("core_ring", tr("CORE RING"), 1140f, listOf(
            o(290f, 470f, 140f, 140f, PW),
            o(150f, 290f, 56f, 56f, P), o(514f, 290f, 56f, 56f, P),
            o(150f, 734f, 56f, 56f, P), o(514f, 734f, 56f, 56f, P)
        ), minLevel = 4),
        ArenaTemplate("zigzag", tr("ZIGZAG ROUTE"), 1260f, listOf(
            o(0f, 300f, 400f, 44f, R), o(320f, 560f, 400f, 44f, R),
            o(0f, 820f, 400f, 44f, R)
        ), minLevel = 5),
        ArenaTemplate("split_lanes", tr("SPLIT LANES"), 1220f, listOf(
            o(220f, 230f, 40f, 260f, FJ), o(460f, 230f, 40f, 260f, FJ),
            o(220f, 640f, 40f, 260f, FJ), o(460f, 640f, 40f, 260f, FJ)
        ), minLevel = 6),
        ArenaTemplate("terminal_scatter", tr("TERMINAL SCATTER"), 1120f, listOf(
            o(80f, 240f, 80f, 56f, T), o(400f, 210f, 80f, 56f, T), o(560f, 420f, 80f, 56f, T),
            o(230f, 450f, 80f, 56f, T), o(90f, 680f, 80f, 56f, T), o(470f, 720f, 80f, 56f, T)
        ), minLevel = 3),
        ArenaTemplate("router_hub", tr("ROUTER HUB"), 1180f, listOf(
            o(320f, 300f, 80f, 80f, RT), o(140f, 520f, 80f, 80f, RT), o(500f, 520f, 80f, 80f, RT),
            o(320f, 740f, 80f, 80f, RT),
            o(0f, 520f, 60f, 80f, PW), o(660f, 520f, 60f, 80f, PW)
        ), minLevel = 7),
        ArenaTemplate("backbone", tr("BACKBONE"), 1300f, listOf(
            o(160f, 260f, 400f, 40f, F),
            o(80f, 480f, 50f, 220f, R), o(590f, 480f, 50f, 220f, R),
            o(300f, 560f, 120f, 50f, P),
            o(160f, 900f, 400f, 40f, F)
        ), minLevel = 10)
    )

    /** The boss room for this build's room shape. */
    val bossArena: ArenaTemplate get() = if (ArenaShape.wide) wideBossArena else portraitBossArena

    /** Open arena with only corner cover, so boss patterns stay readable. */
    private val portraitBossArena = ArenaTemplate("boss_core", tr("COMPROMISED CORE"), 1160f, listOf(
        o(110f, 300f, 64f, 64f, P), o(546f, 300f, 64f, 64f, P),
        o(110f, 800f, 64f, 64f, P), o(546f, 800f, 64f, 64f, P)
    ), bossArena = true, decor = listOf(
        // World Kit for boss rooms: conduits feeding the centre, beacons by the pillars,
        // spark panels and EMP dark-zone emitters in the corners.
        DecorSpec(DecorKind.CONDUIT, 60f, 540f, 240f, 120f, 1),
        DecorSpec(DecorKind.CONDUIT, 420f, 540f, 240f, 120f, 2),
        DecorSpec(DecorKind.BEACON, 142f, 420f, 26f, 26f, 3),
        DecorSpec(DecorKind.BEACON, 578f, 420f, 26f, 26f, 4),
        DecorSpec(DecorKind.SPARK_PANEL, 300f, 960f, 60f, 60f, 5),
        DecorSpec(DecorKind.SPARK_PANEL, 360f, 180f, 60f, 60f, 6),
        DecorSpec(DecorKind.DARK_EMITTER, 60f, 1080f, 24f, 24f, 7),
        DecorSpec(DecorKind.DARK_EMITTER, 660f, 1080f, 24f, 24f, 8),
        DecorSpec(DecorKind.CRACKED_TILE, 240f, 660f, 60f, 60f, 9),
        DecorSpec(DecorKind.CRACKED_TILE, 480f, 360f, 60f, 60f, 10),
        DecorSpec(DecorKind.CRACKED_TILE, 180f, 960f, 60f, 60f, 11)
    ))

    /**
     * The landscape boss room (PC): a wide, open stage. Cover sits in the four corners so the
     * whole middle is the boss's floor, with conduits feeding the centre from both sides.
     */
    private val wideBossArena: ArenaTemplate by lazy { scaleWide(wideBossDesign, 1240f) }

    /** Drawn on a 1440 × 1000 grid; [scaleWide] spreads it to the PC room size. */
    private val wideBossDesign = ArenaTemplate("boss_core", tr("COMPROMISED CORE"), 1000f, listOf(
        o(250f, 270f, 70f, 70f, P), o(1120f, 270f, 70f, 70f, P),
        o(250f, 690f, 70f, 70f, P), o(1120f, 690f, 70f, 70f, P),
        o(40f, 470f, 54f, 90f, PW), o(1346f, 470f, 54f, 90f, PW)
    ), bossArena = true, width = 1440f, decor = listOf(
        DecorSpec(DecorKind.CONDUIT, 100f, 480f, 420f, 120f, 1),
        DecorSpec(DecorKind.CONDUIT, 920f, 480f, 420f, 120f, 2),
        DecorSpec(DecorKind.BEACON, 272f, 390f, 26f, 26f, 3),
        DecorSpec(DecorKind.BEACON, 1142f, 390f, 26f, 26f, 4),
        DecorSpec(DecorKind.BEACON, 272f, 610f, 26f, 26f, 12),
        DecorSpec(DecorKind.BEACON, 1142f, 610f, 26f, 26f, 13),
        DecorSpec(DecorKind.SPARK_PANEL, 540f, 840f, 60f, 60f, 5),
        DecorSpec(DecorKind.SPARK_PANEL, 840f, 160f, 60f, 60f, 6),
        DecorSpec(DecorKind.DARK_EMITTER, 70f, 920f, 24f, 24f, 7),
        DecorSpec(DecorKind.DARK_EMITTER, 1346f, 920f, 24f, 24f, 8),
        DecorSpec(DecorKind.DARK_EMITTER, 70f, 120f, 24f, 24f, 14),
        DecorSpec(DecorKind.DARK_EMITTER, 1346f, 120f, 24f, 24f, 15),
        DecorSpec(DecorKind.CRACKED_TILE, 480f, 600f, 60f, 60f, 9),
        DecorSpec(DecorKind.CRACKED_TILE, 900f, 300f, 60f, 60f, 10),
        DecorSpec(DecorKind.CRACKED_TILE, 360f, 840f, 60f, 60f, 11),
        DecorSpec(DecorKind.CRACKED_TILE, 1020f, 780f, 60f, 60f, 16),
        DecorSpec(DecorKind.WARNING_STRIPES, 600f, 120f, 240f, 22f, 17)
    ))

    /**
     * Landscape versions of the hand-made layouts: each piece keeps its size, its centre
     * spreads across the double-width floor, and the room is shortened to suit a 16:9 view.
     */
    private val wideTemplates: List<ArenaTemplate> by lazy { portraitTemplates.map(::widen) }

    /** Moves each piece's centre onto a bigger floor; pieces keep their size. */
    private fun scaleWide(t: ArenaTemplate, height: Float): ArenaTemplate {
        val kx = ArenaShape.WIDE_WIDTH / t.width
        val ky = height / t.height
        return t.copy(
            height = height, width = ArenaShape.WIDE_WIDTH,
            obstacles = t.obstacles.map { s ->
                val r = s.rect
                val cx = (r.left + r.right) / 2f * kx
                val cy = (r.top + r.bottom) / 2f * ky
                val hw = (r.right - r.left) / 2f
                val hh = (r.bottom - r.top) / 2f
                s.copy(rect = Rect(cx - hw, cy - hh, cx + hw, cy + hh))
            },
            decor = t.decor.map { d -> d.copy(x = (d.x + d.w / 2f) * kx - d.w / 2f, y = (d.y + d.h / 2f) * ky - d.h / 2f) }
        )
    }

    private fun widen(t: ArenaTemplate): ArenaTemplate {
        val k = ArenaShape.WIDE_WIDTH / ArenaShape.PORTRAIT_WIDTH
        val h = (ArenaShape.WIDE_MIN_HEIGHT + (t.height - 1040f) * 0.4f).coerceIn(ArenaShape.WIDE_MIN_HEIGHT, ArenaShape.WIDE_MAX_HEIGHT)
        fun y(v: Float) = 150f + (v - 150f) * (h - 300f) / (t.height - 300f)
        return t.copy(
            height = h, width = ArenaShape.WIDE_WIDTH,
            obstacles = t.obstacles.map { s ->
                val r = s.rect
                val w = r.right - r.left
                val hh = r.bottom - r.top
                // Full-width walls (touching a side) stretch with the floor; the rest keep their size.
                val cx = (r.left + r.right) / 2f * k
                val cy = y((r.top + r.bottom) / 2f)
                // Long walls grow with the floor (to about twice their length); small pieces keep their size.
                val ww = if (w >= 140f) w * minOf(2f, k * 0.75f) else w
                s.copy(rect = Rect(cx - ww / 2f, cy - hh / 2f, cx + ww / 2f, cy + hh / 2f))
            },
            decor = t.decor.map { d -> d.copy(x = d.x * k, y = y(d.y)) }
        )
    }

    fun eligible(level: Int): List<ArenaTemplate> = templates.filter { level >= it.minLevel }

    /** Mirror a template horizontally. */
    fun mirrored(t: ArenaTemplate): ArenaTemplate = t.copy(
        id = t.id + "_m",
        obstacles = t.obstacles.map { s ->
            val r = s.rect
            s.copy(rect = Rect(t.width - r.right, r.top, t.width - r.left, r.bottom))
        },
        decor = t.decor.map { d -> d.copy(x = t.width - d.x - d.w) }
    )
}
