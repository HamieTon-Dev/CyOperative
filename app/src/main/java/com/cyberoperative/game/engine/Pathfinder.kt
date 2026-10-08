package com.cyberoperative.game.engine

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Enemy navigation (owner, 2026-10-08: "enemies run straight into walls and
 * get stuck"). A flow field toward the player over a coarse grid:
 *
 * - Once per room, every cell gets its clearance (distance to the nearest
 *   obstacle or wall).
 * - Whenever the player changes cell (at most every [REBUILD_SECONDS]), a
 *   Dijkstra pass from the player's cell gives every walkable cell its path
 *   cost. Cells close to walls cost extra, so routes keep to the middle of
 *   aisles instead of scraping along racks; diagonals never cut corners.
 * - An enemy steers toward the farthest point down its path it can still see
 *   with its own width, which gives smooth, short routes around cover.
 */
class Pathfinder {

    private var arena: Arena? = null
    private var cols = 0
    private var rows = 0
    private var clearance = FloatArray(0)
    private var cost = FloatArray(0)
    private var heap = IntArray(0)
    private var heapKey = FloatArray(0)
    private var heapSize = 0
    private var goalCell = -1
    private var sinceBuild = 0f
    /** Steering result of [steer]: unit direction. */
    val dir = FloatArray(2)

    fun reset() {
        arena = null
        goalCell = -1
    }

    /** Called once per frame step; rebuilds the field when the room or the player cell changes. */
    fun update(a: Arena, px: Float, py: Float, dt: Float) {
        if (a !== arena) buildClearance(a)
        sinceBuild += dt
        val cell = cellOf(px, py)
        if (cell != goalCell && (sinceBuild >= REBUILD_SECONDS || goalCell < 0)) {
            buildField(cell)
            sinceBuild = 0f
        }
    }

    private fun cellOf(x: Float, y: Float): Int {
        val c = (x / CELL).toInt().coerceIn(0, cols - 1)
        val r = (y / CELL).toInt().coerceIn(0, rows - 1)
        return r * cols + c
    }

    private fun cx(i: Int) = (i % cols + 0.5f) * CELL
    private fun cy(i: Int) = (i / cols + 0.5f) * CELL

    private fun buildClearance(a: Arena) {
        arena = a
        cols = max(1, (a.width / CELL).toInt() + 1)
        rows = max(1, (a.height / CELL).toInt() + 1)
        val n = cols * rows
        clearance = FloatArray(n)
        cost = FloatArray(n) { INF }
        heap = IntArray(n * 8)
        heapKey = FloatArray(n * 8)
        for (i in 0 until n) {
            val x = cx(i)
            val y = cy(i)
            var d = min(min(x, a.width - x), min(y, a.height - y))
            for (k in a.obstacles.indices) {
                val r = a.rect(k)
                val dx = max(max(r.left - x, 0f), x - r.right)
                val dy = max(max(r.top - y, 0f), y - r.bottom)
                val inside = x >= r.left && x <= r.right && y >= r.top && y <= r.bottom
                val dd = if (inside) 0f else sqrt(dx * dx + dy * dy)
                if (dd < d) d = dd
            }
            clearance[i] = d
        }
        goalCell = -1
    }

    private fun walkable(i: Int) = clearance[i] >= MIN_CLEARANCE

    /** Dijkstra from the player's cell; walls nearby make a cell pricier. */
    private fun buildField(goal: Int) {
        goalCell = goal
        java.util.Arrays.fill(cost, INF)
        heapSize = 0
        var start = goal
        if (!walkable(start)) {
            // Player hugging cover: start from the nearest walkable cell.
            var best = -1
            var bd = Float.MAX_VALUE
            val gc = goal % cols
            val gr = goal / cols
            for (r in max(0, gr - 3)..min(rows - 1, gr + 3)) for (c in max(0, gc - 3)..min(cols - 1, gc + 3)) {
                val i = r * cols + c
                if (!walkable(i)) continue
                val d = ((c - gc) * (c - gc) + (r - gr) * (r - gr)).toFloat()
                if (d < bd) { bd = d; best = i }
            }
            if (best < 0) return
            start = best
        }
        cost[start] = 0f
        push(start, 0f)
        while (heapSize > 0) {
            val k = heapKey[0]
            val i = pop()
            if (k > cost[i]) continue
            val c = i % cols
            val r = i / cols
            for (dr in -1..1) for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val nc = c + dc
                val nr = r + dr
                if (nc < 0 || nr < 0 || nc >= cols || nr >= rows) continue
                val j = nr * cols + nc
                if (!walkable(j)) continue
                val diag = dr != 0 && dc != 0
                // No corner cutting: both orthogonal neighbours must be open.
                if (diag && (!walkable(r * cols + nc) || !walkable(nr * cols + c))) continue
                val step = if (diag) 1.414f else 1f
                // Keep routes off the walls: tight cells cost up to 3x.
                val tight = (WALL_COMFORT - clearance[j]).coerceAtLeast(0f) / WALL_COMFORT
                val nk = cost[i] + step * (1f + 2f * tight)
                if (nk < cost[j]) {
                    cost[j] = nk
                    push(j, nk)
                }
            }
        }
    }

    /**
     * Direction for an enemy at (x, y) with [radius] to head toward the player.
     * Returns false when the field has no route (caller falls back to direct).
     */
    fun steer(x: Float, y: Float, radius: Float): Boolean {
        val a = arena ?: return false
        var cur = cellOf(x, y)
        if (cost[cur] >= INF) {
            // Pressed against cover: pick the cheapest reachable neighbour cell.
            var best = -1
            var bk = INF
            val c0 = cur % cols
            val r0 = cur / cols
            for (r in max(0, r0 - 2)..min(rows - 1, r0 + 2)) for (c in max(0, c0 - 2)..min(cols - 1, c0 + 2)) {
                val i = r * cols + c
                if (cost[i] < bk) { bk = cost[i]; best = i }
            }
            if (best < 0) return false
            return aim(x, y, cx(best), cy(best))
        }
        // Walk down the field and aim at the farthest node still in clear view.
        var target = cur
        for (step in 0 until LOOKAHEAD) {
            val next = downhill(cur)
            if (next < 0 || next == cur) break
            if (!a.lineOfSight(x, y, cx(next), cy(next), radius * 0.9f)) break
            target = next
            cur = next
            if (cost[cur] <= 0f) break
        }
        if (target == cellOf(x, y)) {
            val next = downhill(target)
            if (next < 0) return false
            target = next
        }
        return aim(x, y, cx(target), cy(target))
    }

    /** Cost-to-player of the cell at (x, y); INF when unreachable. */
    fun costAt(x: Float, y: Float): Float = if (arena == null) INF else cost[cellOf(x, y)]

    private fun downhill(i: Int): Int {
        val c = i % cols
        val r = i / cols
        var best = -1
        var bk = cost[i]
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val nc = c + dc
            val nr = r + dr
            if (nc < 0 || nr < 0 || nc >= cols || nr >= rows) continue
            val j = nr * cols + nc
            if (cost[j] < bk) { bk = cost[j]; best = j }
        }
        return best
    }

    private fun aim(x: Float, y: Float, tx: Float, ty: Float): Boolean {
        val dx = tx - x
        val dy = ty - y
        val d = sqrt(dx * dx + dy * dy)
        if (d < 0.5f) return false
        dir[0] = dx / d
        dir[1] = dy / d
        return true
    }

    // --- Binary heap of cell indices keyed by cost -----------------------------

    private fun push(i: Int, k: Float) {
        if (heapSize >= heap.size) return
        var p = heapSize++
        heap[p] = i
        heapKey[p] = k
        while (p > 0) {
            val parent = (p - 1) / 2
            if (heapKey[parent] <= heapKey[p]) break
            swap(p, parent)
            p = parent
        }
    }

    private fun pop(): Int {
        val top = heap[0]
        heapSize--
        if (heapSize > 0) {
            heap[0] = heap[heapSize]
            heapKey[0] = heapKey[heapSize]
            var p = 0
            while (true) {
                val l = 2 * p + 1
                val r = l + 1
                var m = p
                if (l < heapSize && heapKey[l] < heapKey[m]) m = l
                if (r < heapSize && heapKey[r] < heapKey[m]) m = r
                if (m == p) break
                swap(p, m)
                p = m
            }
        }
        return top
    }

    private fun swap(a: Int, b: Int) {
        val t = heap[a]; heap[a] = heap[b]; heap[b] = t
        val k = heapKey[a]; heapKey[a] = heapKey[b]; heapKey[b] = k
    }

    companion object {
        const val CELL = 20f
        /** Narrowest gap the field routes through (the generator keeps gaps ≥ 74). */
        const val MIN_CLEARANCE = 17f
        /** Below this clearance a cell costs extra (stay mid-aisle). */
        const val WALL_COMFORT = 46f
        const val LOOKAHEAD = 10
        const val REBUILD_SECONDS = 0.2f
        const val INF = Float.MAX_VALUE
    }
}
