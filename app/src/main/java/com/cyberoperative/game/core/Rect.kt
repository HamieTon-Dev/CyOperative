package com.cyberoperative.game.core

/** Axis-aligned rectangle in world units. Immutable; obstacles never move. */
data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) * 0.5f
    val centerY: Float get() = (top + bottom) * 0.5f

    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom

    fun intersectsRect(l: Float, t: Float, r: Float, b: Float): Boolean =
        left < r && right > l && top < b && bottom > t

    /** True if a circle at ([cx],[cy]) with radius [r] overlaps this rect. */
    fun intersectsCircle(cx: Float, cy: Float, r: Float): Boolean {
        val nx = MathUtil.clamp(cx, left, right)
        val ny = MathUtil.clamp(cy, top, bottom)
        val dx = cx - nx
        val dy = cy - ny
        return dx * dx + dy * dy < r * r
    }

    /**
     * Liang–Barsky segment test: does the segment (x0,y0)->(x1,y1) cross this
     * rect (optionally grown by [pad])? Used for line-of-sight.
     */
    fun intersectsSegment(x0: Float, y0: Float, x1: Float, y1: Float, pad: Float = 0f): Boolean {
        val l = left - pad
        val t = top - pad
        val r = right + pad
        val b = bottom + pad
        val dx = x1 - x0
        val dy = y1 - y0
        var u0 = 0f
        var u1 = 1f
        // Four clip edges, unrolled to stay allocation-free (called every frame).
        for (i in 0 until 4) {
            val p: Float
            val q: Float
            when (i) {
                0 -> { p = -dx; q = x0 - l }
                1 -> { p = dx; q = r - x0 }
                2 -> { p = -dy; q = y0 - t }
                else -> { p = dy; q = b - y0 }
            }
            if (p == 0f) {
                if (q < 0f) return false
            } else {
                val u = q / p
                if (p < 0f) {
                    if (u > u1) return false
                    if (u > u0) u0 = u
                } else {
                    if (u < u0) return false
                    if (u < u1) u1 = u
                }
            }
        }
        return true
    }
}
