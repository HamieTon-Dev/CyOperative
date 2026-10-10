package com.cyberoperative.game.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Circuit Hydra's neck and head layout, shared by the engine (beams leave the
 * mouths, bursts leave the necks) and the renderer, so what you see is where the
 * attacks come from. Necks rise from behind the core in a wide fan and curve
 * out to heads that turn toward the operative.
 *
 * Layout per head, 4 floats: neck control point (x, y), head (x, y).
 */
object HydraRig {
    /** Visual size of the body relative to the boss's hit radius (the hitbox is the core). */
    const val SCALE = 1.6f
    /** Head size relative to the scaled radius. */
    const val HEAD = 0.42f

    fun heads(phase: Int): Int = 3 + phase.coerceIn(0, 2)

    /** Core centre y for a boss at [cy] with hit radius [radius]. */
    fun coreY(cy: Float, radius: Float): Float = cy + radius * SCALE * 0.35f

    fun layout(cx: Float, cy: Float, radius: Float, phase: Int, t: Float, out: FloatArray? = null): FloatArray {
        val r = radius * SCALE
        val n = heads(phase)
        val o = if (out != null && out.size == n * 4) out else FloatArray(n * 4)
        val fan = 1.15f + 0.2f * phase.coerceIn(0, 2)
        val up = -PI.toFloat() / 2f
        val ccy = coreY(cy, radius)
        for (h in 0 until n) {
            val off = (h / (n - 1f) - 0.5f) * 2f * fan
            val sway = sin(t * 1.3f + h * 1.9f)
            val ha = up + off + sway * 0.08f
            val dist = r * (2.3f - 0.3f * abs(off) / fan)
            // Control point bows the neck outward, so each arc reads on its own.
            val ca = up + off * 1.45f + sway * 0.12f
            o[h * 4] = cx + cos(ca) * dist * 0.85f
            o[h * 4 + 1] = ccy + sin(ca) * dist * 0.85f
            o[h * 4 + 2] = cx + cos(ha) * dist
            o[h * 4 + 3] = ccy + sin(ha) * dist * 0.92f
        }
        return o
    }

    /** Neck base for head [h] of [n]. */
    fun baseX(cx: Float, radius: Float, h: Int, n: Int, phase: Int): Float {
        val fan = 1.15f + 0.2f * phase.coerceIn(0, 2)
        val off = (h / (n - 1f) - 0.5f) * 2f * fan
        return cx + off * radius * SCALE * 0.3f
    }

    fun baseY(cy: Float, radius: Float): Float = coreY(cy, radius) - radius * SCALE * 0.15f

    /** Facing of head [h]: along its neck, turned most of the way toward the operative. */
    fun facing(rig: FloatArray, h: Int, aimX: Float, aimY: Float): Float {
        val hx = rig[h * 4 + 2]; val hy = rig[h * 4 + 3]
        val neck = atan2(hy - rig[h * 4 + 1], hx - rig[h * 4])
        return angleMix(neck, atan2(aimY - hy, aimX - hx), 0.8f)
    }

    /** Where the beam leaves head [h]'s mouth. */
    fun mouth(rig: FloatArray, h: Int, radius: Float, facing: Float): Pair<Float, Float> {
        val s = radius * SCALE * HEAD
        return Pair(rig[h * 4 + 2] + cos(facing) * s * 1.35f, rig[h * 4 + 3] + sin(facing) * s * 1.35f)
    }

    /** A point [u] (0 = base, 1 = head) along head [h]'s neck. */
    fun neckPoint(rig: FloatArray, h: Int, bx: Float, by: Float, u: Float): Pair<Float, Float> {
        val cxp = rig[h * 4]; val cyp = rig[h * 4 + 1]; val hx = rig[h * 4 + 2]; val hy = rig[h * 4 + 3]
        return Pair(
            (1 - u) * (1 - u) * bx + 2 * (1 - u) * u * cxp + u * u * hx,
            (1 - u) * (1 - u) * by + 2 * (1 - u) * u * cyp + u * u * hy
        )
    }

    /** Blend angle [a] toward [b] by [f] the short way round. */
    fun angleMix(a: Float, b: Float, f: Float): Float {
        var d = b - a
        while (d > PI) d -= 2 * PI.toFloat()
        while (d < -PI) d += 2 * PI.toFloat()
        return a + d * f
    }
}
