package com.cyberoperative.game.data

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
    SERVER_RACK("SERVER RACK"),
    FIREWALL_NODE("FIREWALL NODE"),
    DATA_PILLAR("DATA PILLAR"),
    COOLING_UNIT("COOLING UNIT"),
    ROUTER("ROUTER"),
    TERMINAL("CORRUPTED TERMINAL"),
    POWER_UNIT("POWER UNIT"),
    FIBER_JUNCTION("FIBER JUNCTION")
}

data class ObstacleSpec(val rect: Rect, val kind: ObstacleKind)

data class ArenaTemplate(
    val id: String,
    val name: String,
    val height: Float,
    val obstacles: List<ObstacleSpec>,
    /** Minimum level before this layout can appear (complex layouts later). */
    val minLevel: Int = 1,
    val bossArena: Boolean = false
) {
    val width: Float get() = ARENA_WIDTH

    companion object {
        const val ARENA_WIDTH = 720f
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

    val templates: List<ArenaTemplate> = listOf(
        ArenaTemplate("open_grid", "OPEN GRID", 1040f, listOf(
            o(150f, 330f, 70f, 70f, P), o(500f, 330f, 70f, 70f, P),
            o(150f, 640f, 70f, 70f, P), o(500f, 640f, 70f, 70f, P)
        )),
        ArenaTemplate("rack_rows", "RACK ROWS", 1120f, listOf(
            o(70f, 360f, 220f, 54f, R), o(430f, 360f, 220f, 54f, R),
            o(70f, 700f, 220f, 54f, R), o(430f, 700f, 220f, 54f, R)
        )),
        ArenaTemplate("crossroads", "CROSSROADS", 1100f, listOf(
            o(90f, 260f, 150f, 150f, R), o(480f, 260f, 150f, 150f, R),
            o(90f, 640f, 150f, 150f, PW), o(480f, 640f, 150f, 150f, PW)
        )),
        ArenaTemplate("firewall_gate", "FIREWALL GATE", 1160f, listOf(
            o(0f, 520f, 170f, 46f, F), o(280f, 520f, 160f, 46f, F), o(550f, 520f, 170f, 46f, F),
            o(130f, 260f, 60f, 60f, RT), o(530f, 260f, 60f, 60f, RT),
            o(330f, 800f, 60f, 60f, P)
        ), minLevel = 2),
        ArenaTemplate("server_farm", "SERVER FARM", 1200f, listOf(
            o(110f, 250f, 60f, 190f, R), o(330f, 250f, 60f, 190f, R), o(550f, 250f, 60f, 190f, R),
            o(110f, 640f, 60f, 190f, R), o(330f, 640f, 60f, 190f, R), o(550f, 640f, 60f, 190f, R)
        ), minLevel = 3),
        ArenaTemplate("cooling_hall", "COOLING HALL", 1080f, listOf(
            o(90f, 220f, 96f, 96f, C), o(420f, 300f, 96f, 96f, C),
            o(210f, 520f, 96f, 96f, C), o(540f, 600f, 96f, 96f, C),
            o(100f, 760f, 96f, 96f, C)
        ), minLevel = 2),
        ArenaTemplate("core_ring", "CORE RING", 1140f, listOf(
            o(290f, 470f, 140f, 140f, PW),
            o(150f, 290f, 56f, 56f, P), o(514f, 290f, 56f, 56f, P),
            o(150f, 734f, 56f, 56f, P), o(514f, 734f, 56f, 56f, P)
        ), minLevel = 4),
        ArenaTemplate("zigzag", "ZIGZAG ROUTE", 1260f, listOf(
            o(0f, 300f, 400f, 44f, R), o(320f, 560f, 400f, 44f, R),
            o(0f, 820f, 400f, 44f, R)
        ), minLevel = 5),
        ArenaTemplate("split_lanes", "SPLIT LANES", 1220f, listOf(
            o(220f, 230f, 40f, 260f, FJ), o(460f, 230f, 40f, 260f, FJ),
            o(220f, 640f, 40f, 260f, FJ), o(460f, 640f, 40f, 260f, FJ)
        ), minLevel = 6),
        ArenaTemplate("terminal_scatter", "TERMINAL SCATTER", 1120f, listOf(
            o(80f, 240f, 80f, 56f, T), o(400f, 210f, 80f, 56f, T), o(560f, 420f, 80f, 56f, T),
            o(230f, 450f, 80f, 56f, T), o(90f, 680f, 80f, 56f, T), o(470f, 720f, 80f, 56f, T)
        ), minLevel = 3),
        ArenaTemplate("router_hub", "ROUTER HUB", 1180f, listOf(
            o(320f, 300f, 80f, 80f, RT), o(140f, 520f, 80f, 80f, RT), o(500f, 520f, 80f, 80f, RT),
            o(320f, 740f, 80f, 80f, RT),
            o(0f, 520f, 60f, 80f, PW), o(660f, 520f, 60f, 80f, PW)
        ), minLevel = 7),
        ArenaTemplate("backbone", "BACKBONE", 1300f, listOf(
            o(160f, 260f, 400f, 40f, F),
            o(80f, 480f, 50f, 220f, R), o(590f, 480f, 50f, 220f, R),
            o(300f, 560f, 120f, 50f, P),
            o(160f, 900f, 400f, 40f, F)
        ), minLevel = 10)
    )

    /** Open arena with only corner cover, so boss patterns stay readable. */
    val bossArena = ArenaTemplate("boss_core", "COMPROMISED CORE", 1160f, listOf(
        o(110f, 300f, 64f, 64f, P), o(546f, 300f, 64f, 64f, P),
        o(110f, 800f, 64f, 64f, P), o(546f, 800f, 64f, 64f, P)
    ), bossArena = true)

    fun eligible(level: Int): List<ArenaTemplate> = templates.filter { level >= it.minLevel }

    /** Mirror a template horizontally. */
    fun mirrored(t: ArenaTemplate): ArenaTemplate = t.copy(
        id = t.id + "_m",
        obstacles = t.obstacles.map { s ->
            val r = s.rect
            s.copy(rect = Rect(ArenaTemplate.ARENA_WIDTH - r.right, r.top, ArenaTemplate.ARENA_WIDTH - r.left, r.bottom))
        }
    )
}
