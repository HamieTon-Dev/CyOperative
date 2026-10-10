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
 * SYN-STORM (classic, level 60): a handshake-flood vortex. A squat armoured
 * turbine with a spinning fan core sits in the eye of a storm: curved spiral
 * arms of SYN packets swirl around it, more arms each phase, and loose packets
 * whip around the edge. Charging spins everything up and lights the arms.
 */
internal object SynStormBody : BossBody {
    private val LIT = Color(0xFF4A3426)
    private val MID = Color(0xFF261A12)
    private val DARK = Color(0xFF0E0906)

    override val previewSpan: Float get() = 5.4f

    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius * 1.35f
        val t = p.time
        val col = p.color
        val hot = p.winding
        val spin = t * (if (hot) 3.2f else 1.1f)
        val cy = p.cy
        val arms = 3 + p.phase

        // Storm disc on the floor.
        drawOval(Brush.radialGradient(listOf(col.copy(alpha = 0.32f), col.copy(alpha = 0f)), center = Offset(p.cx, cy + r * 0.3f), radius = r * 1.7f), Offset(p.cx - r * 1.7f, cy + r * 0.3f - r * 0.65f), Size(r * 3.4f, r * 1.3f))

        // Spiral arms of packets.
        for (a in 0 until arms) {
            val base = spin + a * 2f * PI.toFloat() / arms
            val path = Path()
            for (i in 0..24) {
                val u = i / 24f
                val ang = base + u * 2.6f
                val rr = r * (0.5f + u * 1.15f)
                val x = p.cx + cos(ang) * rr
                val y = cy + r * 0.2f + sin(ang) * rr * 0.42f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, col.copy(alpha = if (hot) 0.5f else 0.22f), style = Stroke(r * 0.12f))
            drawPath(path, lighter(col, 0.3f).copy(alpha = if (hot) 0.9f else 0.55f), style = Stroke(2f))
            // Packets riding the arm.
            for (k in 0 until 5) {
                val u = ((k / 5f + t * 0.6f) % 1f)
                val ang = base + u * 2.6f
                val rr = r * (0.5f + u * 1.15f)
                val x = p.cx + cos(ang) * rr
                val y = cy + r * 0.2f + sin(ang) * rr * 0.42f
                val s = r * 0.07f * (1.2f - u * 0.5f)
                drawRect(DARK, Offset(x - s - 1, y - s * 0.7f - 1), Size(s * 2 + 2, s * 1.4f + 2))
                drawRect(if (hot) Color.White else col, Offset(x - s, y - s * 0.7f), Size(s * 2, s * 1.4f))
            }
        }

        // Turbine housing: octagonal slab.
        val body = ngon(8, r * 0.72f, PI.toFloat() / 8f, 0.62f)
        slab(p.cx, cy, body, r * 0.32f, if (p.hitFlash) Color.White else MID, side = DARK)
        drawPath(polyPath(p.cx, cy, body), col.copy(alpha = 0.75f), style = Stroke(2.2f))
        // Intake ring and fan.
        val fr = r * 0.48f
        drawOval(DARK, Offset(p.cx - fr, cy - fr * 0.62f), Size(fr * 2, fr * 1.24f))
        for (k in 0 until 7) {
            val a = spin * 3f + k * 0.8976f
            val blade = Path().apply {
                moveTo(p.cx, cy)
                lineTo(p.cx + cos(a) * fr * 0.92f, cy + sin(a) * fr * 0.57f)
                lineTo(p.cx + cos(a + 0.45f) * fr * 0.85f, cy + sin(a + 0.45f) * fr * 0.52f)
                close()
            }
            drawPath(blade, if (k % 2 == 0) LIT else MID)
        }
        val hub = if (hot) 1f else 0.6f + 0.4f * sin(t * 4f)
        glow(Offset(p.cx, cy), fr * 0.35f, col, 1f + hub)
        drawCircle(lighter(col, 0.5f * hub), fr * 0.18f, Offset(p.cx, cy))
        drawOval(col.copy(alpha = 0.9f), Offset(p.cx - fr, cy - fr * 0.62f), Size(fr * 2, fr * 1.24f), style = Stroke(2.5f))
        // Exhaust vents on the rim, blinking in sequence.
        for (k in 0 until 8) {
            val a = k * PI.toFloat() / 4f
            val on = hot || ((t * 4f).toInt() % 8 == k)
            drawCircle(if (on) Color.White else col.copy(alpha = 0.6f), r * 0.04f, Offset(p.cx + cos(a) * r * 0.62f, cy + sin(a) * r * 0.38f))
        }
    }
}
