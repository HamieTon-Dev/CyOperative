package com.cyberoperative.game.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Boss mesh rigs (owner, 0.36.x: "every boss is animated and has moving parts"): a boss image is
 * laid on a grid whose corners [Deform] parts displace (legs step, wings bend, jaws open, rings
 * turn, cloaks ripple, pylons slide, pieces bob, cores breathe), then drawn through that grid.
 * PC and Android 10+ draw one textured triangle mesh; older Android (no drawVertices on hardware
 * canvases) draws the same grid tile by tile. Used by the INFERNO paintings (InfernoBodies.kt) and
 * the code-drawn bosses (BossMeshBodies.kt).
 */

private val shaders = HashMap<ImageBitmap, androidx.compose.ui.graphics.Shader>()

internal fun shaderFor(img: ImageBitmap): androidx.compose.ui.graphics.Shader =
    shaders.getOrPut(img) { androidx.compose.ui.graphics.ImageShader(img) }

internal fun smooth(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** The animation state a deform reads: time, wind-up 0..1, phase. */
internal class RigState(val t: Float, val wind: Float, val phase: Int)

/**
 * One moving part of a painting. Coordinates are 0..1 of the cell; [apply] adds this part's
 * displacement of point (u, v) into [out] (cell units).
 */
internal sealed class Deform {
    abstract fun apply(u: Float, v: Float, s: RigState, out: FloatArray)

    /** A wing / scythe / limb hinged at (pu, pv): the [side] (-1 left, 1 right) bends up and down, more toward the tip. */
    class Bend(val pu: Float, val pv: Float, val side: Int, val reach: Float, val deg: Float, val speed: Float,
               val phase: Float = 0f, val windDeg: Float = 0f) : Deform() {
        override fun apply(u: Float, v: Float, s: RigState, out: FloatArray) {
            val along = (u - pu) * side
            if (along <= 0f) return
            val w = smooth(0f, reach, along)
            val a = Math.toRadians(((deg * sin(s.t * speed + phase) + windDeg * s.wind) * w).toDouble()).toFloat() * -side
            val dx = u - pu; val dy = v - pv
            out[0] += dx * cos(a) - dy * sin(a) - dx
            out[1] += dx * sin(a) + dy * cos(a) - dy
        }
    }

    /** A leg: the foot near (u0, v0) lifts and swings forward on its own beat ([phase]). */
    class Step(val u0: Float, val v0: Float, val radius: Float, val lift: Float, val speed: Float, val phase: Float, val swing: Float = 0.4f) : Deform() {
        override fun apply(u: Float, v: Float, s: RigState, out: FloatArray) {
            val d2 = ((u - u0) * (u - u0) + (v - v0) * (v - v0)) / (radius * radius)
            if (d2 > 4f) return
            val w = exp(-d2)
            val c = s.t * speed * (1f + s.wind) + phase
            out[0] += cos(c) * lift * swing * w
            out[1] -= sin(c).coerceAtLeast(0f) * lift * w
        }
    }

    /** A loose piece (crystal, lantern, pylon tip, chimney) bobbing on its own beat. */
    class Bob(val u0: Float, val v0: Float, val radius: Float, val amp: Float, val speed: Float, val phase: Float, val sideways: Float = 0f) : Deform() {
        override fun apply(u: Float, v: Float, s: RigState, out: FloatArray) {
            val d2 = ((u - u0) * (u - u0) + (v - v0) * (v - v0)) / (radius * radius)
            if (d2 > 4f) return
            val w = exp(-d2)
            val c = s.t * speed + phase
            out[0] += cos(c) * amp * sideways * w
            out[1] += sin(c) * amp * w * (1f + s.wind)
        }
    }

    /** A mouth: below [mouth] (between [u0] and [u1]) drops, above lifts a little; wide open on wind-up. */
    class Jaw(val u0: Float, val u1: Float, val mouth: Float, val depth: Float, val idle: Float, val open: Float, val speed: Float = 2.2f) : Deform() {
        override fun apply(u: Float, v: Float, s: RigState, out: FloatArray) {
            val inX = smooth(u0, u0 + 0.08f, u) * (1f - smooth(u1 - 0.08f, u1, u))
            if (inX <= 0f) return
            val amt = idle * (0.5f + 0.5f * sin(s.t * speed)) + open * s.wind
            if (v >= mouth) out[1] += amt * inX * smooth(mouth, mouth + depth * 0.4f, v) * (1f - smooth(mouth + depth, mouth + depth * 1.4f, v))
            else out[1] -= amt * 0.45f * inX * smooth(mouth, mouth - depth * 0.4f, v) * (1f - smooth(mouth - depth, mouth - depth * 1.4f, v))
        }
    }

    /** A ring (ellipse at (cu, cv), radii rx / ry, band r0..r1 in ring units) turning back and forth. */
    class Spin(val cu: Float, val cv: Float, val rx: Float, val ry: Float, val r0: Float, val r1: Float, val deg: Float, val speed: Float, val phase: Float = 0f) : Deform() {
        override fun apply(u: Float, v: Float, s: RigState, out: FloatArray) {
            val ex = (u - cu) / rx; val ey = (v - cv) / ry
            val rr = sqrt(ex * ex + ey * ey)
            val w = smooth(r0 - 0.15f, r0, rr) * (1f - smooth(r1, r1 + 0.15f, rr))
            if (w <= 0f) return
            val a = Math.toRadians((deg * sin(s.t * speed + phase) * (1f + 0.5f * s.wind) * w).toDouble()).toFloat()
            out[0] += (ex * cos(a) - ey * sin(a) - ex) * rx
            out[1] += (ex * sin(a) + ey * cos(a) - ey) * ry
        }
    }

    /** A core / abdomen / throat swelling and settling. */
    class Breathe(val cu: Float, val cv: Float, val radius: Float, val amp: Float, val speed: Float) : Deform() {
        override fun apply(u: Float, v: Float, s: RigState, out: FloatArray) {
            val dx = u - cu; val dy = v - cv
            val d = sqrt(dx * dx + dy * dy)
            if (d > radius) return
            val k = (amp * sin(s.t * speed) + amp * 1.5f * s.wind) * (1f - smooth(radius * 0.4f, radius, d))
            out[0] += dx * k; out[1] += dy * k
        }
    }

    /** Pylons / shields on one [side] sliding out past [edge]; wide on wind-up. The ground stays put. */
    class Slide(val side: Int, val edge: Float, val idle: Float, val wind: Float, val speed: Float = 1.3f) : Deform() {
        override fun apply(u: Float, v: Float, s: RigState, out: FloatArray) {
            val w = if (side < 0) smooth(edge, edge - 0.1f, u) else smooth(edge, edge + 0.1f, u)
            if (w <= 0f) return
            out[0] += side * (idle * (0.5f + 0.5f * sin(s.t * speed)) + wind * s.wind) * w * (1f - smooth(0.82f, 0.94f, v))
        }
    }

    /** [parts] placed on a square [m] times smaller than the mesh's, centred in it. */
    class Zoom(val m: Float, val parts: List<Deform>) : Deform() {
        private val inner = FloatArray(2)
        override fun apply(u: Float, v: Float, s: RigState, out: FloatArray) {
            inner[0] = 0f; inner[1] = 0f
            val iu = (u - 0.5f) * m + 0.5f; val iv = (v - 0.5f) * m + 0.5f
            for (d in parts) d.apply(iu, iv, s, inner)
            out[0] += inner[0] / m; out[1] += inner[1] / m
        }
    }

    /** Cloth / robe / tail below [from] rippling sideways, more toward the hem. */
    class Ripple(val from: Float, val amp: Float, val speed: Float, val waves: Float) : Deform() {
        override fun apply(u: Float, v: Float, s: RigState, out: FloatArray) {
            val w = smooth(from, 0.9f, v) * (1f - smooth(0.9f, 0.98f, v))
            if (w <= 0f) return
            out[0] += amp * w * (1f + s.wind) * sin(s.t * speed + v * waves + u * 3f)
        }
    }
}

/** A posed grid over a square image region, and the two ways of drawing through it. */
internal class MeshGrid(private val grid: Int) {
    private val n = grid + 1
    private val gx = FloatArray(n * n)
    private val gy = FloatArray(n * n)
    private val tmp = FloatArray(2)
    private val paint = androidx.compose.ui.graphics.Paint()
    private val indices: List<Int> = ArrayList<Int>(grid * grid * 6).apply {
        for (j in 0 until grid) for (i in 0 until grid) {
            val a = j * n + i
            add(a); add(a + 1); add(a + n)
            add(a + 1); add(a + n + 1); add(a + n)
        }
    }
    private val white: List<Color> = List(n * n) { Color.White }

    /** Moves every corner by [parts]; [squash] scales about the ground line (v = 1). */
    fun pose(parts: List<Deform>, st: RigState, squash: Float = 1f) {
        for (j in 0..grid) for (i in 0..grid) {
            val u = i / grid.toFloat(); val v = j / grid.toFloat()
            tmp[0] = 0f; tmp[1] = 0f
            for (d in parts) d.apply(u, v, st, tmp)
            gx[j * n + i] = u + tmp[0]
            gy[j * n + i] = 1f - (1f - (v + tmp[1])) * squash
        }
    }

    /**
     * Draws the square [src] region of [img] (top-left [srcX], [srcY], side [srcSize] px) through
     * the posed grid onto the [s]-sided square at ([left], [top]); [mirrorX] flips about that x.
     */
    fun DrawScope.draw(img: ImageBitmap, srcX: Float, srcY: Float, srcSize: Float, left: Float, top: Float, s: Float,
                       alpha: Float, filter: ColorFilter?, mirrorX: Float? = null) {
        if (!com.cyberoperative.game.platform.AppInfo.meshDrawing) return tiles(img, srcX, srcY, srcSize, left, top, s, alpha, filter, mirrorX)
        val pos = ArrayList<Offset>(n * n); val tex = ArrayList<Offset>(n * n)
        for (j in 0..grid) for (i in 0..grid) {
            val k = j * n + i
            val x = left + gx[k] * s
            pos += Offset(if (mirrorX != null) 2f * mirrorX - x else x, top + gy[k] * s)
            tex += Offset(srcX + i * srcSize / grid, srcY + j * srcSize / grid)
        }
        paint.shader = shaderFor(img)
        paint.alpha = alpha
        paint.colorFilter = filter
        val verts = androidx.compose.ui.graphics.Vertices(androidx.compose.ui.graphics.VertexMode.Triangles, pos, tex, white, indices)
        drawContext.canvas.drawVertices(verts, BlendMode.Modulate, paint)
    }

    private fun DrawScope.tiles(img: ImageBitmap, srcX: Float, srcY: Float, srcSize: Float, left: Float, top: Float, s: Float,
                                alpha: Float, filter: ColorFilter?, mirrorX: Float?) {
        val tile = srcSize / grid
        for (j in 0 until grid) for (i in 0 until grid) {
            val a = j * n + i
            var x0 = left + (gx[a] + gx[a + n]) * 0.5f * s
            var x1 = left + (gx[a + 1] + gx[a + n + 1]) * 0.5f * s
            if (mirrorX != null) { val m0 = 2f * mirrorX - x1; x1 = 2f * mirrorX - x0; x0 = m0 }
            val y0 = top + (gy[a] + gy[a + 1]) * 0.5f * s
            val y1 = top + (gy[a + n] + gy[a + n + 1]) * 0.5f * s
            val ix0 = floor(x0).toInt(); val iy0 = floor(y0).toInt()
            val w = (ceil(x1).toInt() - ix0).coerceAtLeast(1)
            val h = (ceil(y1).toInt() - iy0).coerceAtLeast(1)
            val si = if (mirrorX != null) grid - 1 - i else i
            val sx = (srcX + si * tile).toInt(); val sy = (srcY + j * tile).toInt()
            val sw = ((srcX + (si + 1) * tile).toInt() - sx).coerceAtLeast(1)
            val sh = ((srcY + (j + 1) * tile).toInt() - sy).coerceAtLeast(1)
            drawImage(img, IntOffset(sx, sy), IntSize(sw, sh), IntOffset(ix0, iy0), IntSize(w, h), alpha = alpha, colorFilter = filter)
        }
    }
}
