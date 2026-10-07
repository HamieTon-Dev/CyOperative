package com.cyberoperative.game.engine

import com.cyberoperative.game.core.MathUtil
import com.cyberoperative.game.core.Rect
import com.cyberoperative.game.data.ArenaTemplate
import com.cyberoperative.game.data.ObstacleKind
import com.cyberoperative.game.data.ObstacleSpec
import kotlin.math.sqrt

/** A loaded arena: obstacle geometry plus collision queries (§13). */
class Arena(val template: ArenaTemplate, extra: List<ObstacleSpec> = emptyList()) {
    val width: Float = template.width
    val height: Float = template.height
    val obstacles: List<ObstacleSpec> = template.obstacles + extra
    private val rects: Array<Rect> = obstacles.map { it.rect }.toTypedArray()

    val spawnX: Float get() = width * 0.5f
    val spawnY: Float get() = height - 120f
    val portalX: Float get() = width * 0.5f
    val portalY: Float get() = 90f

    /** Scratch output for [pushOut]; avoids allocating a pair per call. */
    val out = FloatArray(2)

    /**
     * Moves a circle out of every obstacle and back inside the walls.
     * Result in [out]. Returns true if any correction was applied.
     */
    fun pushOut(x: Float, y: Float, r: Float): Boolean {
        var px = x
        var py = y
        var moved = false
        for (i in rects.indices) {
            val rc = rects[i]
            if (!rc.intersectsCircle(px, py, r)) continue
            moved = true
            val nx = MathUtil.clamp(px, rc.left, rc.right)
            val ny = MathUtil.clamp(py, rc.top, rc.bottom)
            val dx = px - nx
            val dy = py - ny
            val d2 = dx * dx + dy * dy
            if (d2 > 1e-6f) {
                val d = sqrt(d2)
                val push = r - d
                px += dx / d * push
                py += dy / d * push
            } else {
                // Centre inside the rect: leave along the shallowest axis.
                val toL = px - rc.left
                val toR = rc.right - px
                val toT = py - rc.top
                val toB = rc.bottom - py
                val m = minOf(minOf(toL, toR), minOf(toT, toB))
                when (m) {
                    toL -> px = rc.left - r
                    toR -> px = rc.right + r
                    toT -> py = rc.top - r
                    else -> py = rc.bottom + r
                }
            }
        }
        val cx = MathUtil.clamp(px, r, width - r)
        val cy = MathUtil.clamp(py, r, height - r)
        if (cx != px || cy != py) moved = true
        out[0] = cx
        out[1] = cy
        return moved
    }

    fun lineOfSight(x0: Float, y0: Float, x1: Float, y1: Float, pad: Float = 0f): Boolean {
        for (i in rects.indices) if (rects[i].intersectsSegment(x0, y0, x1, y1, pad)) return false
        return true
    }

    fun isFree(x: Float, y: Float, r: Float): Boolean {
        if (x < r || y < r || x > width - r || y > height - r) return false
        for (i in rects.indices) if (rects[i].intersectsCircle(x, y, r)) return false
        return true
    }

    /** Index of the obstacle containing the point, or -1. */
    fun obstacleAt(x: Float, y: Float, r: Float): Int {
        for (i in rects.indices) if (rects[i].intersectsCircle(x, y, r)) return i
        return -1
    }

    fun rect(i: Int): Rect = rects[i]

    companion object {
        fun vaultObstacle(t: ArenaTemplate): ObstacleSpec {
            val cx = t.width / 2f
            val cy = t.height / 2f
            return ObstacleSpec(Rect(cx - 50f, cy - 40f, cx + 50f, cy + 40f), ObstacleKind.FIBER_JUNCTION)
        }
    }
}
