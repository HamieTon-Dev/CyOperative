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
 * WHITE EYE (classic, level 90): an offensive watcher. A huge floating eyeball
 * in an armoured housing ringed with mechanical shutter blades; the pupil
 * follows you everywhere. Red circuit veins creep across the white each phase
 * and the shutter opens wider; charging pinches the pupil and burns the iris.
 */
internal object WhiteEyeBody : BossBody {
    private val LIT = Color(0xFF3A3E48)
    private val MID = Color(0xFF1C1F26)
    private val DARK = Color(0xFF0A0B0F)
    private val VEIN = Color(0xFFE0304A)

    override val previewSpan: Float get() = 5f

    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius * 1.4f
        val t = p.time
        val white = p.color
        val hot = p.winding
        val cy = p.cy + sin(t * 1.4f) * r * 0.06f
        val c = Offset(p.cx, cy)

        // Floor shadow and a searchlight pool toward the aim.
        drawOval(Color.Black.copy(alpha = 0.4f), Offset(p.cx - r * 0.8f, p.cy + r * 0.85f), Size(r * 1.6f, r * 0.4f))
        val lx = p.cx + cos(p.aim) * r * 1.6f; val ly = p.cy + r * 0.9f + sin(p.aim) * r * 0.5f
        drawOval(Brush.radialGradient(listOf(white.copy(alpha = 0.16f), white.copy(alpha = 0f)), center = Offset(lx, ly), radius = r * 0.9f), Offset(lx - r * 0.9f, ly - r * 0.4f), Size(r * 1.8f, r * 0.8f))

        // Shutter blades around the housing, opening with each phase.
        val open = 0.15f + 0.1f * p.phase + (if (hot) 0.08f else 0f)
        val blades = 10
        for (k in 0 until blades) {
            val a = k * 2f * PI.toFloat() / blades + t * 0.15f
            val bx = p.cx + cos(a) * r * (0.95f + open)
            val by = cy + sin(a) * r * (0.95f + open)
            crystal(bx, by, a, r * 0.45f, r * 0.4f, LIT, DARK, white.copy(alpha = 0.5f))
        }
        // Housing ring.
        drawCircle(DARK, r * 1.0f, c)
        drawCircle(Brush.sweepGradient(listOf(LIT, MID, LIT, MID, LIT), c), r * 0.98f, c, style = Stroke(r * 0.14f))
        for (k in 0 until 12) {
            val a = k * PI.toFloat() / 6f
            drawCircle(DARK, r * 0.035f, Offset(p.cx + cos(a) * r * 0.98f, cy + sin(a) * r * 0.98f))
        }

        // The eyeball.
        val er = r * 0.86f
        drawCircle(Brush.radialGradient(listOf(Color.White, if (p.hitFlash) Color.White else Color(0xFFD8DCE6), Color(0xFF8A90A0)), center = Offset(p.cx - er * 0.3f, cy - er * 0.35f), radius = er * 1.4f), er, c)
        // Circuit veins creeping in from the edge, more each phase.
        val veins = 4 + p.phase * 4
        for (k in 0 until veins) {
            val a = k * 2.4f + 0.3f
            val path = Path()
            var x = p.cx + cos(a) * er * 0.97f
            var y = cy + sin(a) * er * 0.97f
            path.moveTo(x, y)
            for (j in 1..3) {
                val rr = er * (0.97f - j * 0.14f)
                val aa = a + (if (j % 2 == 0) 0.12f else -0.1f)
                val nx = p.cx + cos(aa) * rr; val ny = cy + sin(aa) * rr
                path.lineTo(nx, y); path.lineTo(nx, ny)
                x = nx; y = ny
            }
            drawPath(path, VEIN.copy(alpha = 0.55f + 0.3f * sin(t * 3f + k)), style = Stroke(1.6f))
            drawCircle(VEIN, 2f, Offset(x, y))
        }
        // Iris + pupil, tracking the aim.
        val look = er * 0.38f
        val ix = p.cx + cos(p.aim) * look
        val iy = cy + sin(p.aim) * look
        val ir = er * 0.42f
        drawCircle(Brush.radialGradient(listOf(Color(0xFF50202A), Color(0xFFB02040), Color(0xFF200810)), center = Offset(ix, iy), radius = ir), ir, Offset(ix, iy))
        for (k in 0 until 16) {
            val a = k * PI.toFloat() / 8f + t * 0.3f
            drawLine(Color(0xFFFF6070).copy(alpha = 0.5f), Offset(ix + cos(a) * ir * 0.45f, iy + sin(a) * ir * 0.45f), Offset(ix + cos(a) * ir * 0.92f, iy + sin(a) * ir * 0.92f), 1.4f)
        }
        val burn = if (hot) 1f else 0.4f + 0.2f * sin(t * 2f)
        glow(Offset(ix, iy), ir * 0.7f, VEIN, burn)
        val pr = ir * (if (hot) 0.22f else 0.45f + 0.05f * sin(t * 1.3f))
        drawCircle(Color.Black, pr, Offset(ix, iy))
        drawCircle(VEIN.copy(alpha = 0.9f), ir, Offset(ix, iy), style = Stroke(2f))
        // Lens glint.
        drawCircle(Color.White.copy(alpha = 0.85f), er * 0.1f, Offset(p.cx - er * 0.38f, cy - er * 0.42f))
        drawCircle(Color.White.copy(alpha = 0.5f), er * 0.05f, Offset(p.cx - er * 0.22f, cy - er * 0.52f))
        drawCircle(DARK, er, c, style = Stroke(2.5f))
    }
}
