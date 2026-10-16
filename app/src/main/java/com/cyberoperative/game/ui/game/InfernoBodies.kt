package com.cyberoperative.game.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.cyberoperative.game.ui.common.SafeBrush
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * INFERNO boss bodies (owner, 0.36.x): the owner's Inferno Boss Pack paintings, animated with a
 * deformation rig so every boss has moving parts, not a moving picture.
 *
 * Art: resources/bosses/inferno_<pack>.webp, 3 columns (phase 1/2/3) × 5 rows (the pack's bosses in
 * InfernoBosses order), 320 px cells, bottom-aligned on a transparent ground.
 *
 * Each painting is laid on a 28 × 28 grid; every grid corner is displaced
 * by the boss's [Deform]s, so parts of one painting move on their own without tearing: legs step,
 * wings bend, jaws open, rings turn, cloaks ripple, pylons slide, crystals and lanterns bob, cores
 * breathe. PC and Android 10+ draw it as one textured triangle mesh; older Android (no
 * drawVertices on hardware canvases) draws it tile by tile over the same grid. Wind-ups drive every part
 * harder and cross-fade toward the next painting (jaw bosses snap to the open-maw one); cores go
 * white-hot; hits flash white. Audit and notes: docs/INFERNO_BOSS_REDESIGN.md.
 */

private const val CELL = 320
private const val GRID = 28

/** The three pack atlases, decoded once. */
private object InfernoArt {
    private val atlases = HashMap<String, ImageBitmap?>()
    val shaders = HashMap<ImageBitmap, androidx.compose.ui.graphics.Shader>()

    @Synchronized
    fun atlas(pack: String): ImageBitmap? = atlases.getOrPut(pack) {
        runCatching { com.cyberoperative.game.platform.loadImageResource("bosses/inferno_$pack.webp") }.getOrNull()
    }
}

private fun shaderFor(img: ImageBitmap): androidx.compose.ui.graphics.Shader =
    InfernoArt.shaders.getOrPut(img) { androidx.compose.ui.graphics.ImageShader(img) }

private fun smooth(e0: Float, e1: Float, x: Float): Float {
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

    /** Cloth / robe / tail below [from] rippling sideways, more toward the hem. */
    class Ripple(val from: Float, val amp: Float, val speed: Float, val waves: Float) : Deform() {
        override fun apply(u: Float, v: Float, s: RigState, out: FloatArray) {
            val w = smooth(from, 0.9f, v) * (1f - smooth(0.9f, 0.98f, v))
            if (w <= 0f) return
            out[0] += amp * w * (1f + s.wind) * sin(s.t * speed + v * waves + u * 3f)
        }
    }
}

/** How the whole boss moves. */
internal enum class Motion { HOVER, HEAVY, STOMP, DRIFT }

/**
 * One INFERNO boss's rig: its atlas cell ([pack], [row]), drawn [span] hit radii wide with the
 * reactor core ([coreU], [coreV]) on the hitbox centre, plus its moving [parts].
 */
internal class InfernoRig(
    val pack: String,
    val row: Int,
    val span: Float,
    val coreU: Float,
    val coreV: Float,
    val motion: Motion,
    val parts: List<Deform>,
    val hot: Long = 0xFFFF6A1A,
    val coreSize: Float = 0.08f,
    /** Jaw boss: the wind-up snaps to the third painting (open maw / breath). */
    val jaws: Boolean = false,
    /** Side-on beast: mirrored to face the operative. */
    val faces: Boolean = false
)

internal class InfernoSpriteBody(private val rig: InfernoRig) : BossBody {
    override val previewSpan: Float get() = rig.span * 1.08f
    override val previewDrop: Float get() = 0f

    // Displaced grid corners, reused every frame (cell units).
    private val gx = FloatArray((GRID + 1) * (GRID + 1))
    private val gy = FloatArray((GRID + 1) * (GRID + 1))
    private val tmp = FloatArray(2)

    override fun DrawScope.draw(p: BossPose) {
        val img = InfernoArt.atlas(rig.pack) ?: run { fallback(p); return }
        val r = p.radius
        val t = p.time
        val s = r * rig.span
        val hot = Color(rig.hot)
        var dx = 0f; var dy = 0f; var squash = 1f
        when (rig.motion) {
            Motion.HOVER -> dy = sin(t * 1.6f) * r * 0.07f
            Motion.DRIFT -> { dy = sin(t * 1.2f) * r * 0.05f; dx = sin(t * 0.7f) * r * 0.04f }
            Motion.HEAVY -> squash = 1f + sin(t * 1.1f) * 0.01f
            Motion.STOMP -> { val st = abs(sin(t * 2.2f)); dy = -st * r * 0.03f; squash = 1f - (1f - st) * 0.012f }
        }
        if (p.winding && (rig.motion == Motion.HEAVY || rig.motion == Motion.STOMP)) squash *= 0.965f
        val flip = rig.faces && cos(p.aim) < 0f
        val left = p.cx - rig.coreU * s + dx
        val top = p.cy - rig.coreV * s + dy
        val core = Offset(left + rig.coreU * s, top + rig.coreV * s)

        // Pose the grid for this frame.
        val st = RigState(t, if (p.winding) 1f else 0f, p.phase)
        for (j in 0..GRID) for (i in 0..GRID) {
            val u = i / GRID.toFloat(); val v = j / GRID.toFloat()
            tmp[0] = 0f; tmp[1] = 0f
            for (d in rig.parts) d.apply(u, v, st, tmp)
            gx[j * (GRID + 1) + i] = u + tmp[0]
            // Squash about the ground line.
            gy[j * (GRID + 1) + i] = 1f - (1f - (v + tmp[1])) * squash
        }

        val base = p.phase.coerceIn(0, 2)
        val next = if (rig.jaws) 2 else (base + 1).coerceAtMost(2)
        if (rig.jaws && p.winding) mesh(img, 2, left, top, s, 1f, p, flip)
        else {
            mesh(img, base, left, top, s, 1f, p, flip)
            val blend = if (p.winding) 0.55f + 0.25f * sin(t * 12f) else 0f
            if (blend > 0f && next != base) mesh(img, next, left, top, s, blend, p, flip)
        }
        val pulse = 0.5f + 0.5f * sin(t * (3f + p.phase))
        if (rig.coreSize > 0f) drawCore(core, s, pulse, p.winding, hot)
        embers(core, s, top + s, t, r, hot)
    }

    // Mesh drawing (Android 10+ and PC): one textured triangle mesh per painting.
    private val paint = androidx.compose.ui.graphics.Paint()
    private val indices: List<Int> by lazy {
        val n = GRID + 1
        ArrayList<Int>(GRID * GRID * 6).apply {
            for (j in 0 until GRID) for (i in 0 until GRID) {
                val a = j * n + i
                add(a); add(a + 1); add(a + n)
                add(a + 1); add(a + n + 1); add(a + n)
            }
        }
    }
    private val white: List<Color> by lazy { List((GRID + 1) * (GRID + 1)) { Color.White } }

    private fun DrawScope.mesh(img: ImageBitmap, col: Int, left: Float, top: Float, s: Float, alpha: Float, p: BossPose, flip: Boolean) {
        if (!com.cyberoperative.game.platform.AppInfo.meshDrawing) return tiles(img, col, left, top, s, alpha, p, flip)
        val n = GRID + 1
        val mirror = left + rig.coreU * s
        val pos = ArrayList<Offset>(n * n); val tex = ArrayList<Offset>(n * n)
        for (j in 0..GRID) for (i in 0..GRID) {
            val k = j * n + i
            val x = left + gx[k] * s
            pos += Offset(if (flip) 2f * mirror - x else x, top + gy[k] * s)
            tex += Offset(col * CELL + i * CELL / GRID.toFloat(), rig.row * CELL + j * CELL / GRID.toFloat())
        }
        paint.shader = shaderFor(img)
        paint.alpha = alpha
        paint.colorFilter = if (p.hitFlash) ColorFilter.tint(Color.White.copy(alpha = 0.55f), BlendMode.SrcAtop) else null
        val verts = androidx.compose.ui.graphics.Vertices(androidx.compose.ui.graphics.VertexMode.Triangles, pos, tex, white, indices)
        drawContext.canvas.drawVertices(verts, BlendMode.Modulate, paint)
    }

    /** The painting drawn tile by tile over the posed grid (older Android; neighbours share edges, so no seams). */
    private fun DrawScope.tiles(img: ImageBitmap, col: Int, left: Float, top: Float, s: Float, alpha: Float, p: BossPose, flip: Boolean) {
        val filter = if (p.hitFlash) ColorFilter.tint(Color.White.copy(alpha = 0.55f), BlendMode.SrcAtop) else null
        val sx0 = col * CELL; val sy0 = rig.row * CELL
        val tile = CELL / GRID
        val n = GRID + 1
        val mirror = left + rig.coreU * s
        for (j in 0 until GRID) for (i in 0 until GRID) {
            val a = j * n + i
            var x0 = left + (gx[a] + gx[a + n]) * 0.5f * s
            var x1 = left + (gx[a + 1] + gx[a + n + 1]) * 0.5f * s
            if (flip) { val m0 = 2f * mirror - x1; x1 = 2f * mirror - x0; x0 = m0 }
            val y0 = top + (gy[a] + gy[a + 1]) * 0.5f * s
            val y1 = top + (gy[a + n] + gy[a + n + 1]) * 0.5f * s
            val ix0 = floor(x0).toInt(); val iy0 = floor(y0).toInt()
            val w = (ceil(x1).toInt() - ix0).coerceAtLeast(1)
            val h = (ceil(y1).toInt() - iy0).coerceAtLeast(1)
            val srcI = if (flip) GRID - 1 - i else i
            drawImage(img, IntOffset(sx0 + srcI * tile, sy0 + j * tile), IntSize(tile, tile),
                IntOffset(ix0, iy0), IntSize(w, h), alpha = alpha, colorFilter = filter)
        }
    }

    private fun DrawScope.drawCore(core: Offset, s: Float, pulse: Float, winding: Boolean, hot: Color) {
        val cr = s * rig.coreSize * (1f + 0.15f * pulse + (if (winding) 0.25f else 0f))
        drawCircle(SafeBrush.radialGradient(listOf(Color.White.copy(alpha = if (winding) 0.7f else 0.3f + 0.2f * pulse), hot.copy(alpha = 0.45f), Color.Transparent), core, cr * 2.2f),
            cr * 2.2f, core, blendMode = BlendMode.Plus)
    }

    private fun DrawScope.embers(core: Offset, s: Float, bottom: Float, t: Float, r: Float, hot: Color) {
        for (k in 0 until 8) {
            val ph = (t * 0.35f + k * 0.618f) % 1f
            val x = core.x + sin(k * 2.3f + t * 0.8f) * s * 0.3f
            val y = bottom - s * 0.1f - ph * s * 0.85f
            drawCircle((if (k % 3 == 0) Color(0xFFFFD27A) else hot).copy(alpha = (1f - ph) * 0.7f), r * 0.03f + (k % 3) * 0.6f, Offset(x, y))
        }
    }

    /** Before the atlas has decoded: a dark core so the boss is never invisible. */
    private fun DrawScope.fallback(p: BossPose) {
        drawCircle(Color(0xFF1A0A08), p.radius, Offset(p.cx, p.cy))
        drawCircle(Color(rig.hot), p.radius * 0.35f, Offset(p.cx, p.cy))
    }
}

private fun legs(vararg feet: Pair<Float, Float>, lift: Float, speed: Float, radius: Float = 0.09f): List<Deform> =
    feet.mapIndexed { k, (u, v) -> Deform.Step(u, v, radius, lift, speed, k * 1.9f + (if (u < 0.5f) 0f else Math.PI.toFloat())) }

/** The rigs, part by part, after each painting. */
internal val infernoBodies: Map<String, BossBody> = mapOf(
    // ---- Pack Alpha ----
    // Floating command engine: rune pylons bob on their own beats and slide out on wind-up, the
    // reactor rings turn, the crown spikes sway, the core breathes.
    "hellcore_tyrant" to InfernoSpriteBody(InfernoRig("alpha", 0, 3.9f, 0.5f, 0.42f, Motion.HOVER, listOf(
        Deform.Bob(0.12f, 0.45f, 0.09f, 0.025f, 1.7f, 0f), Deform.Bob(0.88f, 0.45f, 0.09f, 0.025f, 1.7f, 2.2f),
        Deform.Bob(0.28f, 0.28f, 0.07f, 0.02f, 2.1f, 1f), Deform.Bob(0.72f, 0.28f, 0.07f, 0.02f, 2.1f, 3f),
        Deform.Slide(-1, 0.22f, 0.01f, 0.05f), Deform.Slide(1, 0.78f, 0.01f, 0.05f),
        Deform.Spin(0.5f, 0.43f, 0.36f, 0.08f, 0.8f, 1.15f, 7f, 1.4f),
        Deform.Bend(0.5f, 0.3f, -1, 0.2f, 3f, 2.3f), Deform.Bend(0.5f, 0.3f, 1, 0.2f, 3f, 2.3f, 1f),
        Deform.Breathe(0.5f, 0.42f, 0.2f, 0.04f, 3f)
    ), hot = 0xFFFF6A1A)),
    // Fortress: shield pylons deploy outward, the bunker breathes heat, the furnace eye swells.
    "ember_warden" to InfernoSpriteBody(InfernoRig("alpha", 1, 3.6f, 0.5f, 0.55f, Motion.HEAVY, listOf(
        Deform.Slide(-1, 0.3f, 0.012f, 0.07f, 1f), Deform.Slide(1, 0.7f, 0.012f, 0.07f, 1f),
        Deform.Bob(0.18f, 0.12f, 0.06f, 0.012f, 2.6f, 0f), Deform.Bob(0.82f, 0.12f, 0.06f, 0.012f, 2.6f, 1.6f),
        Deform.Breathe(0.5f, 0.57f, 0.2f, 0.05f, 2.2f)
    ), hot = 0xFFFF9A1A, coreSize = 0.1f)),
    // Spider: eight legs step in a wave (faster on wind-up), the abdomen breathes, the mandibles snap.
    "cinder_widow" to InfernoSpriteBody(InfernoRig("alpha", 2, 3.9f, 0.5f, 0.56f, Motion.STOMP, legs(
        0.07f to 0.86f, 0.2f to 0.9f, 0.16f to 0.5f, 0.3f to 0.66f, 0.93f to 0.86f, 0.8f to 0.9f, 0.84f to 0.5f, 0.7f to 0.66f,
        lift = 0.035f, speed = 4.5f
    ) + listOf(
        Deform.Breathe(0.5f, 0.42f, 0.2f, 0.05f, 2.5f),
        Deform.Jaw(0.4f, 0.6f, 0.74f, 0.07f, 0.008f, 0.03f, 6f)
    ), hot = 0xFFFF2A3A, coreSize = 0.05f)),
    // Reactor: the armour bands turn against each other, the debris belt orbits, the core swells.
    "magma_pulse" to InfernoSpriteBody(InfernoRig("alpha", 3, 3.7f, 0.5f, 0.5f, Motion.DRIFT, listOf(
        Deform.Spin(0.5f, 0.5f, 0.44f, 0.12f, 0.75f, 1.15f, 8f, 1.2f),
        Deform.Spin(0.5f, 0.5f, 0.46f, 0.46f, 0.85f, 1.15f, 5f, 0.9f, 1.5f),
        Deform.Breathe(0.5f, 0.5f, 0.32f, 0.05f, 2.4f)
    ), hot = 0xFFFF8A10, coreSize = 0.09f)),
    // Reaper: the scythes swing from the shoulders (wide on wind-up), the ash cloak ripples, the hood nods.
    "ashen_reaver" to InfernoSpriteBody(InfernoRig("alpha", 4, 3.8f, 0.5f, 0.38f, Motion.HOVER, listOf(
        Deform.Bend(0.36f, 0.4f, -1, 0.3f, 6f, 1.6f, 0f, 9f), Deform.Bend(0.64f, 0.4f, 1, 0.3f, 6f, 1.6f, 0.6f, 9f),
        Deform.Ripple(0.5f, 0.03f, 3f, 9f),
        Deform.Bob(0.5f, 0.27f, 0.08f, 0.01f, 1.4f, 0f)
    ), hot = 0xFFFF4A2A, coreSize = 0.04f)),
    // ---- Pack Beta ----
    // Router fortress: the port ring turns, antennas sway on their own beats, the eye core pulses.
    "infernal_router" to InfernoSpriteBody(InfernoRig("beta", 0, 4.2f, 0.5f, 0.48f, Motion.HOVER, listOf(
        Deform.Spin(0.5f, 0.66f, 0.46f, 0.09f, 0.75f, 1.2f, 6f, 1.5f),
        Deform.Bend(0.5f, 0.45f, -1, 0.35f, 2.5f, 2.2f), Deform.Bend(0.5f, 0.45f, 1, 0.35f, 2.5f, 2.2f, 1.2f),
        Deform.Bob(0.15f, 0.2f, 0.06f, 0.012f, 3f, 0f), Deform.Bob(0.85f, 0.2f, 0.06f, 0.012f, 3f, 2f),
        Deform.Breathe(0.5f, 0.58f, 0.14f, 0.06f, 2.6f)
    ), hot = 0xFFFF3B1F, coreSize = 0.07f)),
    // Prophet: crystals drift on their own beats (rising on wind-up), the halo rings turn, the robe ripples.
    "pyroclast_oracle" to InfernoSpriteBody(InfernoRig("beta", 1, 3.9f, 0.5f, 0.3f, Motion.HOVER, listOf(
        Deform.Bob(0.12f, 0.32f, 0.07f, 0.025f, 1.8f, 0f), Deform.Bob(0.88f, 0.32f, 0.07f, 0.025f, 1.8f, 1.7f),
        Deform.Bob(0.1f, 0.52f, 0.07f, 0.025f, 1.5f, 3f), Deform.Bob(0.9f, 0.52f, 0.07f, 0.025f, 1.5f, 4.4f),
        Deform.Bob(0.25f, 0.62f, 0.06f, 0.02f, 2f, 5f), Deform.Bob(0.75f, 0.62f, 0.06f, 0.02f, 2f, 0.8f),
        Deform.Spin(0.5f, 0.42f, 0.4f, 0.08f, 0.75f, 1.2f, 7f, 1.3f),
        Deform.Ripple(0.55f, 0.025f, 2.6f, 8f),
        Deform.Breathe(0.5f, 0.23f, 0.08f, 0.08f, 3f)
    ), hot = 0xFFFF4A3A, coreSize = 0.04f)),
    // Beast: four legs stomp in a gait, the jaw works (gapes and breathes fire on wind-up), stacks shudder.
    "searfang_colossus" to InfernoSpriteBody(InfernoRig("beta", 2, 4.3f, 0.5f, 0.55f, Motion.STOMP, legs(
        0.22f to 0.84f, 0.38f to 0.87f, 0.62f to 0.87f, 0.8f to 0.84f, lift = 0.03f, speed = 3.2f, radius = 0.08f
    ) + listOf(
        Deform.Jaw(0.72f, 0.98f, 0.58f, 0.1f, 0.012f, 0.045f, 2.4f),
        Deform.Bob(0.4f, 0.14f, 0.1f, 0.01f, 9f, 0f), Deform.Bob(0.55f, 0.14f, 0.08f, 0.01f, 8f, 1.5f),
        Deform.Breathe(0.45f, 0.48f, 0.25f, 0.03f, 2f)
    ), hot = 0xFFFF6A1A, coreSize = 0f, jaws = true, faces = true)),
    // Bishop: lanterns swing on their chains, the halo turns, the mitre sways, the robe spikes ripple.
    "burnwire_bishop" to InfernoSpriteBody(InfernoRig("beta", 3, 4.0f, 0.5f, 0.42f, Motion.HOVER, listOf(
        Deform.Bob(0.1f, 0.62f, 0.08f, 0.025f, 1.7f, 0f, 0.8f), Deform.Bob(0.9f, 0.62f, 0.08f, 0.025f, 1.7f, 2.5f, 0.8f),
        Deform.Bob(0.2f, 0.36f, 0.07f, 0.02f, 2.1f, 1.2f, 0.8f), Deform.Bob(0.8f, 0.36f, 0.07f, 0.02f, 2.1f, 3.6f, 0.8f),
        Deform.Spin(0.5f, 0.2f, 0.2f, 0.05f, 0.7f, 1.3f, 9f, 1.6f),
        Deform.Bend(0.5f, 0.42f, -1, 0.2f, 2f, 1.5f), Deform.Bend(0.5f, 0.42f, 1, 0.2f, 2f, 1.5f, 0.8f),
        Deform.Ripple(0.68f, 0.015f, 3f, 12f)
    ), hot = 0xFFFF3A3A, coreSize = 0.05f)),
    // Furnace mouth: the jaws chew (gape wide on wind-up), chimneys shudder, side armour heaves.
    "furnace_maw" to InfernoSpriteBody(InfernoRig("beta", 4, 4.2f, 0.5f, 0.55f, Motion.HEAVY, listOf(
        Deform.Jaw(0.2f, 0.8f, 0.53f, 0.18f, 0.015f, 0.05f, 1.8f),
        Deform.Slide(-1, 0.2f, 0.012f, 0.03f, 1.8f), Deform.Slide(1, 0.8f, 0.012f, 0.03f, 1.8f),
        Deform.Bob(0.25f, 0.12f, 0.06f, 0.012f, 7f, 0f), Deform.Bob(0.5f, 0.1f, 0.06f, 0.012f, 7f, 2f), Deform.Bob(0.75f, 0.12f, 0.06f, 0.012f, 7f, 4f),
        Deform.Breathe(0.5f, 0.55f, 0.15f, 0.06f, 2.5f)
    ), hot = 0xFFFF7A1A, coreSize = 0.11f, jaws = true)),
    // ---- Pack Gamma ----
    // Machine king: the crown halo turns, royal pylons slide out and bob, the core swells, the crown sways.
    "redline_sovereign" to InfernoSpriteBody(InfernoRig("gamma", 0, 4.1f, 0.5f, 0.5f, Motion.HEAVY, listOf(
        Deform.Spin(0.5f, 0.22f, 0.26f, 0.06f, 0.7f, 1.3f, 8f, 1.2f),
        Deform.Slide(-1, 0.22f, 0.012f, 0.05f), Deform.Slide(1, 0.78f, 0.012f, 0.05f),
        Deform.Bob(0.08f, 0.45f, 0.07f, 0.02f, 1.6f, 0f), Deform.Bob(0.92f, 0.45f, 0.07f, 0.02f, 1.6f, 2f),
        Deform.Bend(0.5f, 0.15f, -1, 0.15f, 3f, 2.5f), Deform.Bend(0.5f, 0.15f, 1, 0.15f, 3f, 2.5f, 1f),
        Deform.Breathe(0.5f, 0.58f, 0.18f, 0.05f, 2.4f)
    ), hot = 0xFFFF1F2E, coreSize = 0.07f)),
    // Halo wheel: outer blade ring and inner ring turn opposite ways, the eye dilates.
    "scorch_halo" to InfernoSpriteBody(InfernoRig("gamma", 1, 3.9f, 0.5f, 0.5f, Motion.DRIFT, listOf(
        Deform.Spin(0.5f, 0.5f, 0.48f, 0.48f, 0.72f, 1.2f, 11f, 1.1f),
        Deform.Spin(0.5f, 0.5f, 0.48f, 0.48f, 0.45f, 0.68f, -7f, 1.1f),
        Deform.Breathe(0.5f, 0.5f, 0.18f, 0.08f, 2.2f)
    ), hot = 0xFFFFA020, coreSize = 0.08f)),
    // Nullfield monolith: suppression pylons slide and bob, the halo turns, fragments drift, the core throbs.
    "volcanic_null" to InfernoSpriteBody(InfernoRig("gamma", 2, 4.1f, 0.5f, 0.42f, Motion.HOVER, listOf(
        Deform.Slide(-1, 0.26f, 0.012f, 0.05f), Deform.Slide(1, 0.74f, 0.012f, 0.05f),
        Deform.Bob(0.12f, 0.42f, 0.08f, 0.02f, 1.5f, 0f), Deform.Bob(0.88f, 0.42f, 0.08f, 0.02f, 1.5f, 1.9f),
        Deform.Spin(0.5f, 0.5f, 0.46f, 0.07f, 0.75f, 1.2f, 6f, 1f),
        Deform.Bob(0.3f, 0.82f, 0.05f, 0.012f, 2.4f, 1f), Deform.Bob(0.7f, 0.82f, 0.05f, 0.012f, 2.4f, 3f),
        Deform.Breathe(0.5f, 0.5f, 0.16f, 0.07f, 1.8f)
    ), hot = 0xFFE01E50, coreSize = 0.06f)),
    // Hex engine: the turret ring turns (each turret kicks on its own beat), the inner hex counter-turns.
    "smolder_hex" to InfernoSpriteBody(InfernoRig("gamma", 3, 3.9f, 0.5f, 0.5f, Motion.DRIFT, listOf<Deform>(
        Deform.Spin(0.5f, 0.48f, 0.46f, 0.46f, 0.6f, 1.15f, 7.5f, 1f),
        Deform.Spin(0.5f, 0.48f, 0.46f, 0.46f, 0.0f, 0.42f, -5f, 1.4f)
    ) + (0 until 6).map { k ->
        val a = k * Math.PI.toFloat() / 3f - Math.PI.toFloat() / 2f
        Deform.Bob(0.5f + cos(a) * 0.37f, 0.48f + sin(a) * 0.37f, 0.06f, 0.012f, 6f, k * 1.05f, 0.6f)
    }, hot = 0xFFFF6A2A, coreSize = 0.08f)),
    // Moth-queen: both great wings beat (open wide on wind-up), the tail sways, the halo turns, the core swells.
    "combustion_monarch" to InfernoSpriteBody(InfernoRig("gamma", 4, 4.6f, 0.5f, 0.48f, Motion.HOVER, listOf(
        Deform.Bend(0.46f, 0.47f, -1, 0.42f, 6f, 2.4f, 0f, 9f), Deform.Bend(0.54f, 0.47f, 1, 0.42f, 6f, 2.4f, 0f, 9f),
        Deform.Ripple(0.68f, 0.02f, 2.4f, 6f),
        Deform.Spin(0.5f, 0.22f, 0.2f, 0.05f, 0.7f, 1.3f, 8f, 1.4f),
        Deform.Breathe(0.5f, 0.48f, 0.14f, 0.07f, 2.8f)
    ), hot = 0xFFFF4A2A, coreSize = 0.06f))
)
