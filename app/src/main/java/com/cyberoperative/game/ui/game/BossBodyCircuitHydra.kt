package com.cyberoperative.game.ui.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.cyberoperative.game.engine.HydraRig
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Circuit Hydra (Boss Pack Alpha 03, AREA CONTROL) — owner redesign, step 1:
 * an armoured gunmetal core with toxic-green circuit traces, and long necks of
 * chained metal vertebrae ending in sleek robot serpent heads. Necks fan out
 * toward the operative and sway; more heads appear as the fight goes on
 * (3 / 4 / 5 by phase in previews).
 */
internal object CircuitHydraBody : BossBody {
    private val METAL_LIT = Color(0xFF6A747C)
    private val METAL = Color(0xFF242A2F)
    private val METAL_DARK = Color(0xFF0C0F11)
    private val TOXIC = Color(0xFF5CFF6A)
    private val TOXIC_HOT = Color(0xFFD8FFB0)

    /** One vertebra plate centred at (x, y), long axis along [a]. */
    private fun DrawScope.vertebra(x: Float, y: Float, a: Float, s: Float, glow: Float, flash: Boolean) {
        drawOval(Color.Black.copy(alpha = 0.3f), Offset(x - s, y + s * 0.5f), Size(s * 2f, s * 0.7f))
        rotate(a * 180f / PI.toFloat(), Offset(x, y)) {
            val w = s * 1.25f
            val h = s * 1.9f
            // Dorsal ridge plate.
            val ridge = Path().apply {
                moveTo(x - w * 0.45f, y - h * 0.3f)
                lineTo(x - w * 0.95f, y)
                lineTo(x - w * 0.45f, y + h * 0.3f)
                close()
            }
            drawPath(ridge, METAL_DARK)
            drawPath(ridge, TOXIC.copy(alpha = 0.25f + 0.3f * glow), style = Stroke(1.2f))
            drawRoundRect(
                Brush.linearGradient(listOf(if (flash) Color.White else METAL_LIT, METAL, METAL_DARK), Offset(x, y - h / 2), Offset(x, y + h / 2)),
                Offset(x - w / 2, y - h / 2), Size(w, h), CornerRadius(s * 0.35f)
            )
            drawRoundRect(METAL_DARK, Offset(x - w / 2, y - h / 2), Size(w, h), CornerRadius(s * 0.35f), style = Stroke(1.6f))
            // Green seam light across the plate.
            drawLine(TOXIC.copy(alpha = 0.35f + 0.65f * glow), Offset(x, y - h * 0.38f), Offset(x, y + h * 0.38f), s * 0.16f)
            drawCircle(TOXIC_HOT.copy(alpha = 0.3f + 0.7f * glow), s * 0.12f, Offset(x, y))
        }
    }

    /** A mechanical dragon head seen from above at (x, y), facing [a]; [hot] splits the jaws open. */
    private fun DrawScope.head(x: Float, y: Float, a: Float, s: Float, hot: Boolean, flash: Boolean, t: Float) {
        drawOval(Color.Black.copy(alpha = 0.35f), Offset(x - s * 1.3f, y + s * 0.55f), Size(s * 2.6f, s * 1.0f))
        rotate(a * 180f / PI.toFloat(), Offset(x, y)) {
            fun P(px: Float, py: Float) = Offset(x + px * s, y + py * s)
            fun poly(vararg v: Float) = Path().apply {
                moveTo(x + v[0] * s, y + v[1] * s)
                var k = 2
                while (k < v.size) { lineTo(x + v[k] * s, y + v[k + 1] * s); k += 2 }
                close()
            }
            val lit = if (flash) Color.White else METAL_LIT
            val open = if (hot) 0.32f else 0.04f + 0.03f * sin(t * 3f)
            // Horns: a big swept pair off the brow, a smaller pair behind, cheek spikes.
            for (sd in listOf(-1f, 1f)) {
                val big = poly(-0.2f, 0.3f * sd, -1.15f, 0.95f * sd, -1.75f, 1.05f * sd, -0.95f, 0.62f * sd, -0.55f, 0.2f * sd)
                drawPath(big, Brush.linearGradient(listOf(METAL, METAL_DARK), P(-0.2f, 0.3f * sd), P(-1.7f, 1.0f * sd)))
                drawPath(big, TOXIC.copy(alpha = 0.55f), style = Stroke(1.5f))
                val small = poly(-0.75f, 0.18f * sd, -1.6f, 0.5f * sd, -1.05f, 0.12f * sd)
                drawPath(small, METAL_DARK)
                drawPath(small, METAL_LIT.copy(alpha = 0.6f), style = Stroke(1.2f))
                val cheek = poly(0.05f, 0.62f * sd + open * 0.4f, -0.35f, 0.98f * sd + open * 0.4f, -0.3f, 0.58f * sd + open * 0.4f)
                drawPath(cheek, METAL_DARK)
                drawPath(cheek, TOXIC.copy(alpha = 0.4f), style = Stroke(1.2f))
            }
            if (hot) {
                val mouth = P(1.05f, 0f)
                drawCircle(Brush.radialGradient(listOf(TOXIC_HOT, TOXIC, TOXIC.copy(alpha = 0f)), center = mouth, radius = s * 1.15f), s * 1.15f, mouth)
            }
            // Two heavy jaw halves; they splay apart when firing.
            for (sd in listOf(-1f, 1f)) {
                val o = open * sd
                val jaw = poly(
                    -0.9f, 0f, -0.7f, 0.68f * sd, 0.1f, 0.74f * sd + o * 0.4f,
                    0.95f, 0.5f * sd + o, 1.55f, 0.14f * sd + o, 1.6f, o * 0.5f, -0.2f, 0f
                )
                drawPath(jaw, Brush.linearGradient(listOf(lit, METAL, METAL_DARK), P(0f, 0f), P(0f, 0.75f * sd)))
                drawPath(jaw, METAL_DARK, style = Stroke(2.2f))
                // Row of fangs along the jaw line.
                for (k in 0..3) {
                    val fx = 0.55f + k * 0.26f
                    val fy = (0.52f - k * 0.11f) * sd + o * (0.6f + k * 0.12f)
                    drawPath(poly(fx - 0.08f, fy, fx + 0.06f, fy - 0.17f * sd, fx + 0.1f, fy), TOXIC_HOT.copy(alpha = 0.85f))
                }
                // Armour seam plates on the jaw.
                drawLine(METAL_DARK, P(-0.35f, 0.35f * sd + o * 0.2f), P(0.35f, 0.62f * sd + o * 0.4f), s * 0.05f)
                drawLine(TOXIC.copy(alpha = 0.6f), P(-0.6f, 0.5f * sd), P(1.0f, 0.38f * sd + o), s * 0.06f)
                // Deep-set eye under the brow.
                val e = P(0.3f, 0.36f * sd + o * 0.4f)
                drawCircle(TOXIC.copy(alpha = if (hot) 0.65f else 0.35f), s * 0.28f, e)
                drawPath(poly(0.08f, 0.27f * sd + o * 0.4f, 0.6f, 0.4f * sd + o * 0.4f, 0.22f, 0.43f * sd + o * 0.4f), if (hot) TOXIC_HOT else TOXIC)
                // Nostril vent.
                drawLine(TOXIC.copy(alpha = 0.7f), P(1.15f, 0.12f * sd + o * 0.7f), P(1.35f, 0.1f * sd + o * 0.8f), s * 0.07f)
            }
            // Heavy brow ridge: wide armoured plate overhanging the eyes, with a central spine.
            val brow = poly(-1.0f, 0f, -0.75f, -0.34f, 0.1f, -0.42f, 0.62f, -0.18f, 0.8f, 0f, 0.62f, 0.18f, 0.1f, 0.42f, -0.75f, 0.34f)
            drawPath(brow, Brush.linearGradient(listOf(lit, METAL, METAL_DARK), P(-0.6f, -0.35f), P(0.6f, 0.35f)))
            drawPath(brow, METAL_DARK, style = Stroke(2f))
            for (k in 0..2) {
                val bx = -0.75f + k * 0.42f
                drawPath(poly(bx + 0.18f, -0.06f, bx - 0.12f, 0f, bx + 0.18f, 0.06f, bx + 0.28f, 0f), METAL_DARK)
            }
            drawLine(TOXIC.copy(alpha = 0.9f), P(-0.9f, 0f), P(0.7f, 0f), s * 0.07f)
            drawCircle(TOXIC_HOT, s * 0.08f, P(-0.15f, 0f))
        }
    }

    /** A loose vertebra (coil hazard, split-head necks). */
    @Suppress("UNUSED_PARAMETER")
    fun DrawScope.segment(x: Float, y: Float, s: Float, col: Color, glow: Float, flash: Boolean) =
        vertebra(x, y, 0f, s * 0.8f, glow, flash)

    /** A free-standing head (split heads) aimed at [aim]. */
    @Suppress("UNUSED_PARAMETER")
    fun DrawScope.head(x: Float, y: Float, r: Float, col: Color, aim: Float, hot: Boolean, flash: Boolean, t: Float) =
        head(x, y, aim, r * 0.8f, hot, flash, t)

    override val previewSpan: Float get() = 8f
    override val previewDrop: Float get() = 1.3f

    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius * HydraRig.SCALE
        val t = p.time
        val heads = HydraRig.heads(p.phase)
        val ccy = HydraRig.coreY(p.cy, p.radius)
        // In a fight the engine's rig (lifted) is passed in, so beams leave the mouths you see.
        val rig = if (p.trail.size == heads * 4) p.trail else HydraRig.layout(p.cx, p.cy, p.radius, p.phase, t)
        // Aim point: the operative in a fight, else far along the aim angle.
        val ax = if (p.aimX.isNaN()) p.cx + cos(p.aim) * 2000f else p.aimX
        val ay = if (p.aimY.isNaN()) p.cy + sin(p.aim) * 2000f else p.aimY
        val by = HydraRig.baseY(p.cy, p.radius)
        // Necks rise from behind the core in a wide fan; heads are drawn last.
        // A severed neck (head destroyed) slumps to the floor beside the core and sparks.
        fun alive(h: Int) = p.heads < 0 || (p.heads shr h) and 1 == 1
        // A neck lunging down in front of the core (Head Bite) is drawn over it.
        fun lunging(h: Int) = rig[h * 4 + 3] > ccy + r * 0.3f
        fun neck(h: Int) {
            val bx = HydraRig.baseX(p.cx, p.radius, h, heads, p.phase)
            val live = alive(h)
            var cxp = rig[h * 4]; var cyp = rig[h * 4 + 1]; var hx = rig[h * 4 + 2]; var hy = rig[h * 4 + 3]
            if (!live) {
                val side = if (hx < p.cx) -1f else 1f
                hx = bx + side * r * (0.9f + 0.15f * (h % 2))
                hy = ccy + r * 0.45f + sin(t * 2f + h) * r * 0.02f
                cxp = bx + side * r * 0.55f
                cyp = by - r * 0.55f
            }
            // Enough vertebrae to stay solid when a neck stretches out to bite.
            val len = kotlin.math.hypot(hx - bx, hy - by) + kotlin.math.hypot(cxp - bx, cyp - by) * 0.5f
            val n = if (live) maxOf(12, (len / (r * 0.2f)).toInt()) else 7
            val reach = if (live) 0.9f else 1f
            for (i in 0 until n) {
                val u = i / (n - 1f) * reach
                val x = (1 - u) * (1 - u) * bx + 2 * (1 - u) * u * cxp + u * u * hx
                val y = (1 - u) * (1 - u) * by + 2 * (1 - u) * u * cyp + u * u * hy
                val dx = 2 * (1 - u) * (cxp - bx) + 2 * u * (hx - cxp)
                val dy = 2 * (1 - u) * (cyp - by) + 2 * u * (hy - cyp)
                val glow = if (live) 0.5f + 0.5f * sin(t * 6f - i * 0.8f - h) else 0.1f
                vertebra(x, y, atan2(dy, dx), r * (0.27f - 0.08f * u), glow, p.hitFlash)
            }
            if (!live) {
                // Severed end: a torn collar with flickering sparks.
                drawCircle(METAL_DARK, r * 0.2f, Offset(hx, hy))
                drawCircle(TOXIC.copy(alpha = 0.5f + 0.5f * sin(t * 23f + h)), r * 0.2f, Offset(hx, hy), style = Stroke(2f))
                for (k in 0 until 4) {
                    val a = t * 9f + k * 1.7f + h
                    val l = r * (0.12f + 0.12f * ((sin(t * 31f + k * 2.3f) + 1f) / 2f))
                    drawLine(TOXIC_HOT, Offset(hx, hy), Offset(hx + cos(a) * l, hy + sin(a) * l), 1.8f)
                }
            }
        }
        for (h in 0 until heads) if (!alive(h) || !lunging(h)) neck(h)
        // Core: armoured octagon hull with PCB traces and a toxic reactor.
        drawOval(Color.Black.copy(alpha = 0.4f), Offset(p.cx - r * 0.95f, ccy + r * 0.55f), Size(r * 1.9f, r * 0.6f))
        val hull = Path().apply {
            for (k in 0 until 8) {
                val a = k * PI.toFloat() / 4f + PI.toFloat() / 8f
                val x = p.cx + cos(a) * r * 0.85f
                val y = ccy + sin(a) * r * 0.72f
                if (k == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(hull, Brush.radialGradient(listOf(if (p.hitFlash) Color.White else METAL_LIT, METAL, METAL_DARK), center = Offset(p.cx - r * 0.25f, ccy - r * 0.3f), radius = r * 1.1f))
        drawPath(hull, METAL_DARK, style = Stroke(3f))
        // Circuit traces running out from the reactor.
        for (k in 0 until 8) {
            val a = k * PI.toFloat() / 4f
            val pulse = 0.4f + 0.6f * ((sin(t * 3f - k) + 1f) / 2f)
            val m = Offset(p.cx + cos(a) * r * 0.42f, ccy + sin(a) * r * 0.36f)
            val e = Offset(p.cx + cos(a + 0.25f) * r * 0.72f, ccy + sin(a + 0.25f) * r * 0.6f)
            drawLine(TOXIC.copy(alpha = 0.6f * pulse), Offset(p.cx + cos(a) * r * 0.28f, ccy + sin(a) * r * 0.24f), m, 2.2f)
            drawLine(TOXIC.copy(alpha = 0.6f * pulse), m, e, 2.2f)
            drawCircle(TOXIC_HOT.copy(alpha = pulse), 2.6f, e)
        }
        val rc = Offset(p.cx, ccy - r * 0.05f)
        val beat = if (p.winding) 1f else 0.6f + 0.4f * sin(t * 2.5f)
        drawCircle(TOXIC.copy(alpha = 0.25f * beat), r * 0.42f, rc)
        drawCircle(METAL_DARK, r * 0.27f, rc)
        drawCircle(Brush.radialGradient(listOf(TOXIC_HOT, TOXIC, TOXIC.copy(alpha = 0f)), center = rc, radius = r * 0.24f * (0.85f + 0.3f * beat)), r * 0.24f, rc)
        drawCircle(TOXIC.copy(alpha = 0.8f), r * 0.27f, rc, style = Stroke(2.5f))
        if (p.heads == 0) {
            // Core exposed: the reactor blazes and the hull seams glow white-hot.
            val f = 0.5f + 0.5f * sin(t * 10f)
            drawCircle(Brush.radialGradient(listOf(Color.White, TOXIC_HOT, TOXIC.copy(alpha = 0f)), center = rc, radius = r * 0.7f), r * 0.7f * (0.85f + 0.15f * f), rc)
            drawPath(hull, TOXIC_HOT.copy(alpha = 0.5f + 0.5f * f), style = Stroke(3f))
        } else {
            // Head shield: a translucent hex dome over the core, fed by power links from each living head.
            val dome = r * 0.98f
            for (h in 0 until heads) if (alive(h)) {
                val bx = HydraRig.baseX(p.cx, p.radius, h, heads, p.phase)
                drawLine(TOXIC.copy(alpha = 0.35f + 0.25f * sin(t * 5f + h)), Offset(bx, by), rc, 2f)
            }
            drawOval(TOXIC.copy(alpha = 0.10f + 0.04f * sin(t * 3f)), Offset(p.cx - dome, ccy - dome * 0.9f), Size(dome * 2f, dome * 1.7f))
            drawOval(TOXIC.copy(alpha = 0.55f), Offset(p.cx - dome, ccy - dome * 0.9f), Size(dome * 2f, dome * 1.7f), style = Stroke(2f))
            for (k in 0 until 6) {
                val a = k * PI.toFloat() / 3f + t * 0.4f
                drawLine(TOXIC.copy(alpha = 0.18f), rc, Offset(p.cx + cos(a) * dome, ccy - dome * 0.05f + sin(a) * dome * 0.85f), 1.2f)
            }
        }
        for (h in 0 until heads) if (alive(h) && lunging(h)) neck(h)
        // Roar after regrowing: every head stares straight at you, jaws wide, sending out shockwaves.
        val roar = p.heads > 0 && (p.heads and HydraRig.LEAN_BIT) != 0
        val waves = p.heads > 0 && (p.heads and HydraRig.ROAR_BIT) != 0
        for (h in 0 until heads) if (alive(h)) {
            val hx = rig[h * 4 + 2]; val hy = rig[h * 4 + 3]
            val face = if (roar) atan2(ay - hy, ax - hx) else HydraRig.facing(rig, h, ax, ay)
            head(hx, hy, face, r * HydraRig.HEAD, p.winding || roar, p.hitFlash, t)
            if (waves) {
                val s = r * HydraRig.HEAD
                val mx = hx + cos(face) * s * 1.4f; val my = hy + sin(face) * s * 1.4f
                for (k in 0 until 3) {
                    val q = ((t * 1.8f + k / 3f + h * 0.17f) % 1f)
                    val rr = s * (0.6f + 3.2f * q)
                    drawArc(
                        TOXIC_HOT.copy(alpha = 0.95f * (1f - q)), face * 180f / PI.toFloat() - 45f, 90f, false,
                        Offset(mx - rr, my - rr), Size(rr * 2f, rr * 2f), style = Stroke(6f * (1f - q) + 1.5f)
                    )
                }
            }
        }
    }
}
