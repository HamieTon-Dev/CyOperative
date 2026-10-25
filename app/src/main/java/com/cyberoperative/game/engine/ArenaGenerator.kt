package com.cyberoperative.game.engine

import com.cyberoperative.game.i18n.tr
import com.cyberoperative.game.core.Rect
import com.cyberoperative.game.data.ArenaTemplate
import com.cyberoperative.game.data.ArenaShape
import com.cyberoperative.game.data.Arenas
import com.cyberoperative.game.data.DecorKind
import com.cyberoperative.game.data.DecorSpec
import com.cyberoperative.game.data.ObstacleKind
import com.cyberoperative.game.data.ObstacleSpec
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Procedural arenas (owner's vision, 2026-10-07): every level is a freshly
 * generated room — random barriers, server racks, small blinking servers and
 * floor decoration — not one flat pane.
 *
 * A layout is built from a random *style* (how obstacles are arranged) and a
 * random *kit* (which hardware it uses), then validated: the spawn point and
 * the exit gate must stay clear and a walking route between them must exist.
 * Invalid attempts are discarded; after too many, a hand-made template is used.
 */
object ArenaGenerator {

    enum class Style { SCATTER, ROWS, CLUSTERS, CORRIDOR, RING, MIRROR }

    /** Room width for this build (720 portrait on phones, 1440 landscape on PC). */
    private val W: Float get() = ArenaShape.width
    /** How many times wider than a phone room: scales piece counts so density stays the same. */
    private val K: Float get() = W / ArenaShape.PORTRAIT_WIDTH
    private val cols: Int get() = (W / 60f).toInt()
    /** Gap between obstacles: wide enough for the operative and any enemy. */
    private const val GAP = 74f
    private const val MARGIN = 12f

    private val sectorNames = listOf(
        tr("SUBNET"), tr("DATA CENTER"), tr("SERVER HALL"), tr("CORE NODE"), tr("BACKBONE"), tr("SECTOR"),
        tr("DMZ"), tr("VLAN"), tr("CLUSTER"), tr("RACK ROW"), tr("COLD AISLE"), tr("HOT AISLE")
    )

    /**
     * @param allDoors campaign rooms: keep a doorway clear (and walkable) on every wall, not just
     * the top gate and the bottom entry, since the world map can lead out of any side.
     */
    fun generate(level: Int, rng: Random, allDoors: Boolean = false): ArenaTemplate {
        repeat(24) { attempt ->
            val t = tryGenerate(level, rng, attempt, allDoors)
            if (t != null) return t
        }
        // Extremely unlikely; keeps the game playable no matter what.
        return Arenas.templates[rng.nextInt(Arenas.templates.size)]
    }

    private fun name(rng: Random): String {
        val base = sectorNames[rng.nextInt(sectorNames.size)]
        val suffix = when (rng.nextInt(3)) {
            0 -> "${('A' + rng.nextInt(6))}${rng.nextInt(1, 10)}"
            1 -> "10.${rng.nextInt(0, 255)}.${rng.nextInt(0, 255)}"
            else -> "${rng.nextInt(1, 99)}"
        }
        return "$base $suffix"
    }

    private fun tryGenerate(level: Int, rng: Random, attempt: Int, allDoors: Boolean = false): ArenaTemplate? {
        // Landscape rooms are shorter (16:9 view): about one screen plus a little scroll.
        val height = if (ArenaShape.wide) ArenaShape.WIDE_MIN_HEIGHT + rng.nextInt(0, 4) * 40f else 1080f + rng.nextInt(0, 6) * 50f
        val style = Style.entries[rng.nextInt(Style.entries.size)]
        // Denser rooms as the run goes on (more cover, more tactics).
        val density = min(1f, 0.55f + level * 0.02f) * (if (attempt > 12) 0.7f else 1f)
        val placed = ArrayList<ObstacleSpec>()
        val kit = kit(rng)

        fun clearZones(r: Rect): Boolean {
            val cx = W / 2f
            // Exit gate strip at the top and spawn pocket at the bottom stay open.
            if (r.intersectsRect(cx - 120f, 0f, cx + 120f, 190f)) return false
            if (r.intersectsRect(cx - 110f, height - 230f, cx + 110f, height)) return false
            // Side doorways (campaign): the middle of the left and right walls.
            if (allDoors && r.intersectsRect(0f, height * 0.5f - 120f, 230f, height * 0.5f + 120f)) return false
            if (allDoors && r.intersectsRect(W - 230f, height * 0.5f - 120f, W, height * 0.5f + 120f)) return false
            return true
        }

        fun fits(r: Rect): Boolean {
            if (r.left < MARGIN || r.right > W - MARGIN || r.top < 150f || r.bottom > height - 150f) return false
            if (!clearZones(r)) return false
            for (o in placed) {
                val q = o.rect
                if (r.intersectsRect(q.left - GAP, q.top - GAP, q.right + GAP, q.bottom + GAP)) return false
            }
            return true
        }

        fun add(r: Rect, k: ObstacleKind): Boolean {
            if (!fits(r)) return false
            placed += ObstacleSpec(r, k)
            return true
        }

        fun box(cx: Float, cy: Float, w: Float, h: Float) = Rect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)

        fun randomPiece(cx: Float, cy: Float): Boolean {
            val k = kit[rng.nextInt(kit.size)]
            val (w, h) = sizeOf(k, rng)
            return add(box(cx, cy, w, h), k)
        }

        val top = 230f
        val bottom = height - 260f
        when (style) {
            Style.SCATTER -> {
                val n = (6 + rng.nextInt(5)) * density * K
                var tries = 0
                while (placed.size < n && tries++ < 200) {
                    randomPiece(60f + rng.nextFloat() * (W - 120f), top + rng.nextFloat() * (bottom - top))
                }
            }
            Style.ROWS -> {
                val rows = 2 + rng.nextInt(2)
                for (i in 0 until rows) {
                    val y = top + (bottom - top) * (i + 0.5f) / rows
                    val segs = (2 + rng.nextInt(2)) * K.toInt()
                    for (s in 0 until segs) {
                        if (rng.nextFloat() > 0.85f) continue
                        val cx = W * (s + 0.5f) / segs
                        val rowKind = when (rng.nextInt(5)) {
                            0 -> ObstacleKind.COOLANT_PIPES
                            1 -> ObstacleKind.HOLO_WALL
                            2 -> kit[0]
                            else -> ObstacleKind.SERVER_RACK
                        }
                        add(box(cx, y, W / segs - 120f, 46f), rowKind)
                    }
                }
                repeat((3 * density * K).toInt()) { randomPiece(60f + rng.nextFloat() * (W - 120f), top + rng.nextFloat() * (bottom - top)) }
            }
            Style.CLUSTERS -> {
                val clusters = ((2 + rng.nextInt(3)) * K).toInt()
                repeat(clusters) {
                    val cx = 120f + rng.nextFloat() * (W - 240f)
                    val cy = top + rng.nextFloat() * (bottom - top)
                    add(box(cx, cy, 70f, 70f), kit[rng.nextInt(kit.size)])
                    repeat(3) { k ->
                        val dx = (if (k % 2 == 0) 1 else -1) * (90f + rng.nextFloat() * 40f)
                        val dy = (if (k < 2) -1 else 1) * (70f + rng.nextFloat() * 40f)
                        add(box(cx + dx, cy + dy, 40f, 40f), ObstacleKind.SMALL_SERVER)
                    }
                }
            }
            Style.CORRIDOR -> {
                val segments = 3 + rng.nextInt(3)
                for (i in 0 until segments) {
                    val y = top + (bottom - top) * i / max(1, segments - 1)
                    val leftSide = i % 2 == 0
                    val len = (220f + rng.nextFloat() * 160f) * (1f + (K - 1f) * 0.6f)
                    val r = if (leftSide) Rect(MARGIN + 1f, y - 22f, MARGIN + 1f + len, y + 22f) else Rect(W - MARGIN - 1f - len, y - 22f, W - MARGIN - 1f, y + 22f)
                    add(r, when (rng.nextInt(6)) {
                        0, 1 -> ObstacleKind.FIREWALL_NODE
                        2 -> ObstacleKind.BLAST_WALL
                        3 -> ObstacleKind.ENERGY_BARRIER
                        else -> ObstacleKind.SERVER_RACK
                    })
                }
                repeat((2 * density * K).toInt() + 1) { randomPiece(80f + rng.nextFloat() * (W - 160f), top + rng.nextFloat() * (bottom - top)) }
            }
            Style.RING -> {
                val cx = W / 2f
                val cy = (top + bottom) / 2f
                add(box(cx, cy, 120f, 100f), if (rng.nextBoolean()) ObstacleKind.POWER_UNIT else ObstacleKind.REACTOR)
                val n = 6 + rng.nextInt(3) + (if (ArenaShape.wide) 4 else 0)
                // An ellipse that fits the room: wide and flat on PC, tall on phones.
                val rx = if (ArenaShape.wide) W * 0.32f else 230f
                val ry = if (ArenaShape.wide) min(300f, (bottom - top) / 2f) else 260f
                for (i in 0 until n) {
                    val a = i * (Math.PI * 2 / n).toFloat() + rng.nextFloat() * 0.2f
                    add(box(cx + kotlin.math.cos(a) * rx, cy + kotlin.math.sin(a) * ry, 56f, 56f), kit[rng.nextInt(kit.size)])
                }
            }
            Style.MIRROR -> {
                var tries = 0
                val n = (4 + rng.nextInt(3)) * density * K
                while (placed.size < n * 2 && tries++ < 120) {
                    val k = kit[rng.nextInt(kit.size)]
                    val (w, h) = sizeOf(k, rng)
                    val cx = 60f + rng.nextFloat() * (W / 2f - 120f)
                    val cy = top + rng.nextFloat() * (bottom - top)
                    val a = box(cx, cy, w, h)
                    val b = box(W - cx, cy, w, h)
                    if (fits(a) && !a.intersectsRect(b.left - GAP, b.top - GAP, b.right + GAP, b.bottom + GAP)) {
                        placed += ObstacleSpec(a, k)
                        if (fits(b)) placed += ObstacleSpec(b, k) else placed.removeAt(placed.lastIndex)
                    }
                }
            }
        }
        // Sprinkle small blinking server units into leftover space.
        var extra = 0
        var guard = 0
        while (extra < (2 + rng.nextInt(3)) * K && guard++ < 60) {
            if (add(box(50f + rng.nextFloat() * (W - 100f), top + rng.nextFloat() * (bottom - top), 40f, 40f), ObstacleKind.SMALL_SERVER)) extra++
        }
        if (placed.size < 3) return null

        val template = ArenaTemplate("gen_${style.name.lowercase()}_$attempt", name(rng), height, placed.toList(), decor = decor(height, placed, rng, level))
        val arena = Arena(template)
        if (!arena.isFree(arena.spawnX, arena.spawnY, 40f) || !arena.isFree(arena.portalX, arena.portalY, 50f)) return null
        if (!reachable(arena)) return null
        if (allDoors) {
            val mid = height * 0.5f
            if (!arena.isFree(60f, mid, 40f) || !arena.isFree(W - 60f, mid, 40f)) return null
            if (!reachableTo(arena, 60f, mid) || !reachableTo(arena, W - 60f, mid)) return null
        }
        return template
    }

    /** Which hardware a room is built from (a kit gives each room a character). */
    private fun kit(rng: Random): List<ObstacleKind> = when (rng.nextInt(10)) {
        5 -> listOf(ObstacleKind.BLAST_WALL, ObstacleKind.REACTOR, ObstacleKind.ANTENNA_TOWER)
        6 -> listOf(ObstacleKind.COOLANT_PIPES, ObstacleKind.COOLING_UNIT, ObstacleKind.REACTOR, ObstacleKind.SMALL_SERVER)
        7 -> listOf(ObstacleKind.HOLO_WALL, ObstacleKind.TERMINAL, ObstacleKind.ANTENNA_TOWER, ObstacleKind.DATA_PILLAR)
        8 -> listOf(ObstacleKind.ENERGY_BARRIER, ObstacleKind.BLAST_WALL, ObstacleKind.POWER_UNIT, ObstacleKind.CRATES)
        9 -> listOf(ObstacleKind.SERVER_RACK, ObstacleKind.COOLANT_PIPES, ObstacleKind.HOLO_WALL, ObstacleKind.ENERGY_BARRIER)
        0 -> listOf(ObstacleKind.SERVER_RACK, ObstacleKind.SMALL_SERVER, ObstacleKind.COOLING_UNIT)
        1 -> listOf(ObstacleKind.FIREWALL_NODE, ObstacleKind.ROUTER, ObstacleKind.DATA_PILLAR)
        2 -> listOf(ObstacleKind.TERMINAL, ObstacleKind.CRATES, ObstacleKind.SMALL_SERVER)
        3 -> listOf(ObstacleKind.POWER_UNIT, ObstacleKind.FIBER_JUNCTION, ObstacleKind.DATA_PILLAR)
        else -> listOf(ObstacleKind.SERVER_RACK, ObstacleKind.ROUTER, ObstacleKind.CRATES, ObstacleKind.TERMINAL)
    }

    private fun sizeOf(k: ObstacleKind, rng: Random): Pair<Float, Float> = when (k) {
        ObstacleKind.SERVER_RACK -> if (rng.nextBoolean()) 180f to 50f else 56f to 150f
        ObstacleKind.FIREWALL_NODE -> 150f to 42f
        ObstacleKind.DATA_PILLAR -> 60f to 60f
        ObstacleKind.COOLING_UNIT -> 92f to 92f
        ObstacleKind.ROUTER -> 80f to 70f
        ObstacleKind.TERMINAL -> 86f to 56f
        ObstacleKind.POWER_UNIT -> 100f to 90f
        ObstacleKind.FIBER_JUNCTION -> if (rng.nextBoolean()) 40f to 180f else 180f to 40f
        ObstacleKind.SMALL_SERVER -> 40f to 40f
        ObstacleKind.CRATES -> 70f to 60f
        ObstacleKind.BLAST_WALL -> if (rng.nextBoolean()) 200f to 40f else 44f to 170f
        ObstacleKind.COOLANT_PIPES -> if (rng.nextBoolean()) 190f to 44f else 44f to 160f
        ObstacleKind.HOLO_WALL -> 160f to 30f
        ObstacleKind.REACTOR -> 86f to 86f
        ObstacleKind.ENERGY_BARRIER -> 170f to 26f
        ObstacleKind.ANTENNA_TOWER -> 36f to 36f
        ObstacleKind.SHOP_COUNTER -> 340f to 170f
        ObstacleKind.BARRIER_CUBE, ObstacleKind.LOCK_CUBE -> 52f to 52f
    }

    /**
     * World Kit decor (owner sheet, 2026-10-10): tile variants break up repetition,
     * conduits add colour and direction, beacons light the way; spark panels and
     * dark-zone emitters only in deeper, higher-drama rooms. Used by generated and
     * hand-made rooms alike.
     */
    fun worldKit(height: Float, rng: Random, level: Int, obstacles: List<ObstacleSpec> = emptyList()): List<DecorSpec> {
        val out = ArrayList<DecorSpec>()
        repeat(2 + rng.nextInt(4)) {
            out += DecorSpec(DecorKind.CRACKED_TILE, (rng.nextInt(cols) * 60).toFloat(), (rng.nextInt((height / 60).toInt()) * 60).toFloat(), 60f, 60f, rng.nextInt())
        }
        repeat(1 + rng.nextInt(2)) {
            out += DecorSpec(DecorKind.CONDUIT, (1 + rng.nextInt(cols - 2)) * 60f, (3 + rng.nextInt(((height - 360f) / 60f).toInt().coerceAtLeast(1))) * 60f, (2 + rng.nextInt(4)) * 60f, (1 + rng.nextInt(3)) * 60f, rng.nextInt())
        }
        repeat(1 + rng.nextInt(2)) {
            out += DecorSpec(DecorKind.BEACON, 60f + rng.nextFloat() * (W - 120f), 220f + rng.nextFloat() * (height - 440f), 26f, 26f, rng.nextInt())
        }
        if (level >= 15) repeat(rng.nextInt(2)) {
            out += DecorSpec(DecorKind.SPARK_PANEL, (1 + rng.nextInt(cols - 2)) * 60f, (3 + rng.nextInt(((height - 360f) / 60f).toInt().coerceAtLeast(1))) * 60f, 60f, 60f, rng.nextInt())
        }
        if (level >= 40 && rng.nextFloat() < 0.3f) {
            out += DecorSpec(DecorKind.DARK_EMITTER, 80f + rng.nextFloat() * (W - 160f), 260f + rng.nextFloat() * (height - 520f), 24f, 24f, rng.nextInt())
        }
        return out.filter { d -> obstacles.none { it.rect.contains(d.x + d.w / 2f, d.y + d.h / 2f) } }
    }

    private fun decor(height: Float, obstacles: List<ObstacleSpec>, rng: Random, level: Int = 1): List<DecorSpec> {
        val out = ArrayList<DecorSpec>()
        // World Kit uses its own seeded random so the room's layout and spawns stay as they were.
        out += worldKit(height, Random(level * 7919L + height.toInt() * 31L + obstacles.size), level)
        out += DecorSpec(DecorKind.WARNING_STRIPES, W / 2f - 120f, 120f, 240f, 22f)
        repeat(((3 + rng.nextInt(3)) * K).toInt()) {
            out += DecorSpec(DecorKind.CABLE, rng.nextFloat() * W, 160f + rng.nextFloat() * (height - 320f), 120f + rng.nextFloat() * 260f, 0f, rng.nextInt())
        }
        repeat(((2 + rng.nextInt(3)) * K).toInt()) {
            out += DecorSpec(DecorKind.VENT, 40f + rng.nextFloat() * (W - 120f), 160f + rng.nextFloat() * (height - 320f), 70f, 44f)
        }
        repeat(2 + rng.nextInt(2)) {
            val x = if (rng.nextBoolean()) 18f else W - 30f
            out += DecorSpec(DecorKind.FLOOR_LIGHT, x, 180f + rng.nextFloat() * (height - 400f), 12f, 120f + rng.nextFloat() * 120f, rng.nextInt())
        }
        repeat(rng.nextInt(3)) {
            out += DecorSpec(DecorKind.DATA_POOL, 60f + rng.nextFloat() * (W - 120f), 200f + rng.nextFloat() * (height - 400f), 90f + rng.nextFloat() * 60f, 50f, rng.nextInt())
        }
        repeat(1 + rng.nextInt(2)) {
            out += DecorSpec(DecorKind.HOLO_PANEL, 60f + rng.nextFloat() * (W - 180f), 200f + rng.nextFloat() * (height - 400f), 100f, 40f, rng.nextInt())
        }
        repeat(((4 + rng.nextInt(4)) * K).toInt()) {
            out += DecorSpec(DecorKind.FLOOR_TILE, (rng.nextInt(cols) * 60).toFloat(), (rng.nextInt((height / 60).toInt()) * 60).toFloat(), 60f, 60f, rng.nextInt())
        }
        // Decor never sits under an obstacle (it would be hidden anyway).
        return out.filter { d -> obstacles.none { it.rect.contains(d.x + d.w / 2f, d.y + d.h / 2f) } }
    }

    /**
     * The upgrade shop (owner, 2026-10-08): a small, roughly square room with
     * the shopkeeper's counter near the top and a few random fixtures along the
     * walls. Always leaves a clear walk to the exit gate.
     */
    fun shopRoom(rng: Random): ArenaTemplate {
        repeat(20) {
            val height = 820f + rng.nextInt(0, 3) * 40f
            val placed = ArrayList<ObstacleSpec>()
            placed += counter()
            val fixtures = listOf(ObstacleKind.CRATES, ObstacleKind.SMALL_SERVER, ObstacleKind.REACTOR, ObstacleKind.ANTENNA_TOWER, ObstacleKind.COOLANT_PIPES, ObstacleKind.POWER_UNIT)
            var tries = 0
            // 0.53.2 (owner): fewer fixtures (1-2), slim and flush against the side walls, never in
            // the clear zone round the counter ([shopKeepOut]).
            val keepOut = shopKeepOut()
            while (placed.size < 2 + rng.nextInt(2) && tries++ < 60) {
                val k = fixtures[rng.nextInt(fixtures.size)]
                val (w, h) = sizeOf(k, rng).let { (a, b) -> minOf(a, FIXTURE_MAX_W) to minOf(b, 110f) }
                val left = rng.nextBoolean()
                val cx = if (left) w / 2f else W - w / 2f
                val cy = 160f + rng.nextFloat() * (height - 380f)
                val r = Rect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
                if (r.intersectsRect(keepOut.left, keepOut.top, keepOut.right, keepOut.bottom)) continue
                if (placed.any { o -> r.intersectsRect(o.rect.left - GAP, o.rect.top - GAP, o.rect.right + GAP, o.rect.bottom + GAP) }) continue
                placed += ObstacleSpec(r, k)
            }
            val t = ArenaTemplate("shop", tr("UPGRADE SHOP"), height, placed.toList(), decor = decor(height, placed, rng))
            val a = Arena(t)
            if (a.isFree(a.spawnX, a.spawnY, 40f) && a.isFree(a.portalX, a.portalY, 50f) && reachable(a)) return t
        }
        return ArenaTemplate("shop", tr("UPGRADE SHOP"), 820f, listOf(counter()))
    }

    /**
     * The shopkeeper's counter (0.53.2, owner): a squarer table, [ : egg : ], two pieces of gear
     * (front and back) on each side of the egg, all at world-drop size without touching. Narrow
     * enough to leave a wide walk round both ends on every screen.
     */
    private fun counter(): ObstacleSpec {
        val w = minOf(COUNTER_WIDTH, W - 80f)
        return ObstacleSpec(Rect(W / 2f - w / 2f, COUNTER_TOP, W / 2f + w / 2f, COUNTER_BOTTOM), ObstacleKind.SHOP_COUNTER)
    }
    /**
     * The shop's clear zone (owner, 0.53.2): the counter plus 120 on each side, from the top wall
     * to 320 below it. No fixture may touch it.
     */
    fun shopKeepOut(): Rect {
        val c = counter().rect
        return Rect(c.left - 120f, 0f, c.right + 120f, c.bottom + 320f)
    }
    private const val FIXTURE_MAX_W = 60f
    private const val COUNTER_WIDTH = 340f
    private const val COUNTER_TOP = 236f
    private const val COUNTER_BOTTOM = 406f

    /** Grid flood fill: can the operative walk from spawn to the gate? */
    fun reachable(a: Arena): Boolean = reachableTo(a, a.portalX, a.portalY)

    /** Grid flood fill from the spawn point to ([tx], [ty]). */
    fun reachableTo(a: Arena, tx0: Float, ty0: Float): Boolean {
        val cell = 20f
        val cols = (a.width / cell).toInt()
        val rows = (a.height / cell).toInt()
        val seen = BooleanArray(rows * cols)
        val queue = IntArray(rows * cols)
        var head = 0
        var tail = 0
        val sc = (a.spawnX / cell).toInt()
        val sr = (a.spawnY / cell).toInt()
        val tc = (tx0 / cell).toInt().coerceIn(0, cols - 1)
        val tr = (ty0 / cell).toInt().coerceIn(0, rows - 1)
        queue[tail++] = sr * cols + sc
        seen[sr * cols + sc] = true
        while (head < tail) {
            val cur = queue[head++]
            val r = cur / cols
            val c = cur % cols
            if (r == tr && c == tc) return true
            for (k in 0 until 4) {
                val nr = r + (if (k == 0) 1 else if (k == 1) -1 else 0)
                val nc = c + (if (k == 2) 1 else if (k == 3) -1 else 0)
                if (nr < 0 || nc < 0 || nr >= rows || nc >= cols) continue
                val idx = nr * cols + nc
                if (seen[idx]) continue
                if (!a.isFree(nc * cell + cell / 2f, nr * cell + cell / 2f, 22f)) continue
                seen[idx] = true
                queue[tail++] = idx
            }
        }
        return false
    }
}
