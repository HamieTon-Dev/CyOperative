package com.cyberoperative.game.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * BOTMASTER (classic, level 20): a floating command spire. A tall hexagonal
 * console with a magenta screen showing a pulsing ∞, an antenna crown with
 * blinking tips, and its botnet — small drone nodes orbiting on a holo ring,
 * each linked to the spire by a data line. More nodes join every phase;
 * charging lights every link at once.
 */
internal object BotmasterBody : BossBody {
    private val LIT = Color(0xFF3C2A44)
    private val MID = Color(0xFF221828)
    private val DARK = Color(0xFF0C0810)

    override val previewSpan: Float get() = 5.6f

    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius * 1.35f
        val t = p.time
        val mag = p.color
        val hot = p.winding
        val cy = p.cy + sin(t * 1.6f) * r * 0.05f
        val nodes = 6 + p.phase * 2
        val ringRx = r * 1.55f
        val ringRy = r * 0.55f
        val ringCy = p.cy + r * 0.35f

        // Holo ring on the floor.
        drawOval(mag.copy(alpha = 0.18f), Offset(p.cx - ringRx, ringCy - ringRy), Size(ringRx * 2, ringRy * 2), style = Stroke(r * 0.08f))
        drawOval(mag.copy(alpha = 0.6f), Offset(p.cx - ringRx, ringCy - ringRy), Size(ringRx * 2, ringRy * 2), style = Stroke(1.5f))

        // Nodes behind the spire first (upper half of the ellipse), then the spire, then front nodes.
        val pos = Array(nodes) { k ->
            val a = t * 0.6f + k * 2f * PI.toFloat() / nodes
            Offset(p.cx + cos(a) * ringRx, ringCy + sin(a) * ringRy - r * 0.25f)
        }
        val core = Offset(p.cx, cy - r * 0.35f)
        for (k in 0 until nodes) if (pos[k].y < ringCy - r * 0.25f) node(pos[k], core, r, mag, hot, t, k)

        // Spire: hexagonal console.
        val body = floatArrayOf(-r * 0.55f, -r * 0.9f, r * 0.55f, -r * 0.9f, r * 0.7f, -r * 0.2f, r * 0.45f, r * 0.45f, -r * 0.45f, r * 0.45f, -r * 0.7f, -r * 0.2f)
        slab(p.cx, cy, body, r * 0.3f, if (p.hitFlash) Color.White else MID, side = DARK)
        drawPath(polyPath(p.cx, cy, floatArrayOf(-r * 0.55f, -r * 0.9f, r * 0.55f, -r * 0.9f, r * 0.65f, -r * 0.55f, -r * 0.65f, -r * 0.55f)), if (p.hitFlash) Color.White else LIT)
        drawPath(polyPath(p.cx, cy, body), mag.copy(alpha = 0.7f), style = Stroke(2f))
        // Screen with the ∞ glyph.
        val sx = p.cx; val sy = cy - r * 0.3f
        drawRect(DARK, Offset(sx - r * 0.42f, sy - r * 0.25f), Size(r * 0.84f, r * 0.5f))
        drawRect(Brush.verticalGradient(listOf(mag.copy(alpha = 0.35f), mag.copy(alpha = 0.1f)), sy - r * 0.22f, sy + r * 0.22f), Offset(sx - r * 0.38f, sy - r * 0.22f), Size(r * 0.76f, r * 0.44f))
        for (k in 0 until 5) drawLine(mag.copy(alpha = 0.15f), Offset(sx - r * 0.38f, sy - r * 0.18f + k * r * 0.09f), Offset(sx + r * 0.38f, sy - r * 0.18f + k * r * 0.09f), 1f)
        val beat = if (hot) 1f else 0.6f + 0.4f * sin(t * 3f)
        val inf = Path().apply {
            val w = r * 0.26f; val h = r * 0.13f
            for (i in 0..40) {
                val u = i / 40f * 2f * PI.toFloat()
                val x = sx + w * sin(u)
                val y = sy + h * sin(u) * cos(u) * 2f
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        drawPath(inf, mag.copy(alpha = 0.4f * beat), style = Stroke(r * 0.12f))
        drawPath(inf, lighter(mag, 0.6f).copy(alpha = beat), style = Stroke(3f))
        // Control keys row.
        for (k in -2..2) drawRect(mag.copy(alpha = if (((t * 4).toInt() + k) % 3 == 0) 0.9f else 0.3f), Offset(p.cx + k * r * 0.16f - r * 0.05f, cy + r * 0.15f), Size(r * 0.1f, r * 0.06f))

        // Antenna crown: three masts with blinking tips.
        for (k in -1..1) {
            val bx = p.cx + k * r * 0.35f
            val top = cy - r * 0.9f - r * (if (k == 0) 0.75f else 0.5f)
            drawLine(DARK, Offset(bx, cy - r * 0.88f), Offset(bx, top), r * 0.08f)
            drawLine(LIT, Offset(bx - r * 0.015f, cy - r * 0.88f), Offset(bx - r * 0.015f, top), 1.5f)
            for (j in 1..2) drawLine(LIT, Offset(bx - r * 0.08f, top + j * r * 0.15f), Offset(bx + r * 0.08f, top + j * r * 0.15f), 2f)
            val on = hot || ((t * 2f + k * 0.33f) % 1f) < 0.5f
            drawCircle(if (on) Color.White else mag, r * 0.06f, Offset(bx, top))
            if (on) glow(Offset(bx, top), r * 0.1f, mag, 1.2f)
        }

        for (k in 0 until nodes) if (pos[k].y >= ringCy - r * 0.25f) node(pos[k], core, r, mag, hot, t, k)
    }

    /** One botnet drone node with its data link back to the spire. */
    private fun DrawScope.node(c: Offset, core: Offset, r: Float, mag: Color, hot: Boolean, t: Float, k: Int) {
        val pulse = ((t * 1.5f + k * 0.37f) % 1f)
        drawLine(mag.copy(alpha = if (hot) 0.8f else 0.25f), core, c, if (hot) 2.5f else 1.2f)
        // A data packet running along the link.
        val q = Offset(core.x + (c.x - core.x) * pulse, core.y + (c.y - core.y) * pulse)
        drawCircle(lighter(mag, 0.5f), 2.5f, q)
        val s = r * 0.16f
        drawCircle(Color.Black.copy(alpha = 0.3f), s, Offset(c.x, c.y + s * 1.6f))
        drawPath(polyPath(c.x, c.y, ngon(6, s, 0.52f)), MID)
        drawPath(polyPath(c.x, c.y, ngon(6, s, 0.52f)), mag.copy(alpha = 0.9f), style = Stroke(1.8f))
        drawCircle(if (hot) Color.White else mag, s * 0.35f, c)
    }
}
