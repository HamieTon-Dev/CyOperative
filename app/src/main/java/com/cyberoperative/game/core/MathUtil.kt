package com.cyberoperative.game.core

import kotlin.math.sqrt

/** Small allocation-free math helpers used by the simulation. */
object MathUtil {
    const val PI = Math.PI.toFloat()
    const val TWO_PI = (Math.PI * 2.0).toFloat()

    fun dist2(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        return dx * dx + dy * dy
    }

    fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float = sqrt(dist2(ax, ay, bx, by))

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun clamp(v: Float, lo: Float, hi: Float): Float = if (v < lo) lo else if (v > hi) hi else v

    /** Wraps an angle into -PI..PI. */
    fun wrapAngle(a: Float): Float {
        var r = a % TWO_PI
        if (r > PI) r -= TWO_PI
        if (r < -PI) r += TWO_PI
        return r
    }
}
