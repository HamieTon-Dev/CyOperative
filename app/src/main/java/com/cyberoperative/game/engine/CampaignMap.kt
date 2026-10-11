package com.cyberoperative.game.engine

import kotlin.random.Random

/**
 * The campaign's server worlds (owner, 2026-10-11).
 *
 * The campaign is 15 SERVER WORLDS. Each world is a map of 10 rooms laid out on a grid as a
 * winding path, then a boss room (room 11), always directly north of room 10. Doors sit on the
 * wall facing the neighbouring room, so moving through a world feels like crossing a map.
 * Every world's map comes from the run's seed, so a run's whole campaign is decided when it
 * starts (and co-op partners and CONTINUE rebuild exactly the same maps).
 */
enum class Dir(val dx: Int, val dy: Int, /** Door wall: 0 top, 1 left, 2 right, 3 bottom. */ val side: Int) {
    N(0, -1, 0), W(-1, 0, 1), E(1, 0, 2), S(0, 1, 3);

    val opposite: Dir get() = when (this) { N -> S; S -> N; E -> W; W -> E }

    companion object {
        fun between(ax: Int, ay: Int, bx: Int, by: Int): Dir = entries.first { it.dx == bx - ax && it.dy == by - ay }
    }
}

/** One room on a world map: its grid cell, and whether it is the boss room. */
data class MapRoom(val index: Int, val gx: Int, val gy: Int, val boss: Boolean)

class WorldMap(val world: Int, val rooms: List<MapRoom>) {
    val minX = rooms.minOf { it.gx }
    val maxX = rooms.maxOf { it.gx }
    val minY = rooms.minOf { it.gy }
    val maxY = rooms.maxOf { it.gy }

    /** Direction from room [from] to its neighbour [to] (they are always next to each other). */
    fun dir(from: Int, to: Int): Dir {
        val a = rooms[from]
        val b = rooms[to]
        return Dir.between(a.gx, a.gy, b.gx, b.gy)
    }

    /** The rooms a room's doors lead to: back (previous room) and forward (next room / boss). */
    fun neighbours(i: Int): List<Int> = listOfNotNull(if (i > 0) i - 1 else null, if (i < rooms.lastIndex) i + 1 else null)
}

object CampaignMap {
    const val WORLDS = 15
    /** Normal rooms per world (room 11 is the boss). */
    const val ROOMS = 10
    const val BOSS_INDEX = ROOMS

    fun generate(runSeed: Long, world: Int): WorldMap {
        val rng = Random(runSeed xor (world * 0x5DEECE66DL) xor 0x3A9C0FFEEL)
        repeat(200) {
            tryWalk(rng)?.let { return WorldMap(world, it) }
        }
        // Fallback (never expected): a straight climb north.
        return WorldMap(world, (0..BOSS_INDEX).map { MapRoom(it, 0, -it, it == BOSS_INDEX) })
    }

    /** A self-avoiding walk that prefers to keep heading somewhere (north / sideways) over doubling back. */
    private fun tryWalk(rng: Random): List<MapRoom>? {
        val cells = ArrayList<Pair<Int, Int>>()
        val used = HashSet<Pair<Int, Int>>()
        cells += 0 to 0
        used += 0 to 0
        var last: Dir? = null
        while (cells.size < ROOMS) {
            val (x, y) = cells.last()
            val options = Dir.entries.filter { d ->
                val c = (x + d.dx) to (y + d.dy)
                c !in used && d != last?.opposite &&
                    // Keep a free cell beside each new room so the path never seals itself in.
                    Dir.entries.any { e -> ((c.first + e.dx) to (c.second + e.dy)) !in used }
            }
            if (options.isEmpty()) return null
            val weights = options.map { d ->
                when (d) {
                    Dir.N -> 3f
                    Dir.E, Dir.W -> if (d == last) 1.6f else 2.4f
                    Dir.S -> 0.8f
                }
            }
            var roll = rng.nextFloat() * weights.sum()
            var pick = options.last()
            for ((i, d) in options.withIndex()) {
                roll -= weights[i]
                if (roll <= 0f) { pick = d; break }
            }
            val c = (x + pick.dx) to (y + pick.dy)
            cells += c
            used += c
            last = pick
        }
        // The boss room sits north of room 10 (you walk up into its arena from the bottom).
        val (lx, ly) = cells.last()
        val boss = lx to (ly - 1)
        if (boss in used) return null
        cells += boss
        return cells.mapIndexed { i, (x, y) -> MapRoom(i, x, y, i == BOSS_INDEX) }
    }

    /** The difficulty level a campaign room plays at (the old level curve: world 15's boss = old level 150). */
    fun levelFor(world: Int, room: Int): Int =
        if (room >= BOSS_INDEX) world * 10 else (world - 1) * 10 + minOf(room + 1, 9)
}
