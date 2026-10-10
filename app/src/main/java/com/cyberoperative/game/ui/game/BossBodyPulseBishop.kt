package com.cyberoperative.game.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pulse Bishop (Boss Pack Beta 01, CONTROLLER): a floating dark prelate of
 * faceted armour plates with crimson edges, a tall split mitre with a glowing
 * cross, angry red eyes, swept shoulder fins and a burning + sigil on the floor
 * under it. The sigil's arms grow and the plates spread apart each phase.
 */
internal object PulseBishopBody : BossBody {
    private val PLATE_LIT = Color(0xFF3A2030)
    private val PLATE = Color(0xFF1E0F1A)
    private val PLATE_DARK = Color(0xFF0C060B)

    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius
        val t = p.time
        val red = p.color
        val hot = p.winding
        val pulse = 0.5f + 0.5f * sin(t * (2.5f + p.phase))
        val bob = sin(t * 1.7f) * r * 0.06f
        val cy = p.cy + bob
        val ground = p.cy + r * 0.95f
        val spread = 1f + 0.06f * p.phase + (if (hot) 0.06f else 0f)

        // + sigil burning on the floor.
        val arm = r * (1.3f + 0.35f * p.phase) * (if (hot) 1.25f else 1f)
        val sig = red.copy(alpha = 0.35f + 0.25f * pulse + (if (hot) 0.3f else 0f))
        drawOval(Brush.radialGradient(listOf(red.copy(alpha = 0.35f), red.copy(alpha = 0f)), center = Offset(p.cx, ground), radius = r * 1.3f), Offset(p.cx - r * 1.3f, ground - r * 0.45f), Size(r * 2.6f, r * 0.9f))
        drawLine(sig.copy(alpha = sig.alpha * 0.4f), Offset(p.cx - arm, ground), Offset(p.cx + arm, ground), r * 0.16f)
        drawLine(sig.copy(alpha = sig.alpha * 0.4f), Offset(p.cx, ground - arm * 0.36f), Offset(p.cx, ground + arm * 0.36f), r * 0.16f)
        drawLine(sig, Offset(p.cx - arm, ground), Offset(p.cx + arm, ground), 3f)
        drawLine(sig, Offset(p.cx, ground - arm * 0.36f), Offset(p.cx, ground + arm * 0.36f), 3f)

        val lit = if (p.hitFlash) Color.White else PLATE_LIT
        val mid = if (p.hitFlash) Color(0xFFE0E0E0) else PLATE
        val edge = red.copy(alpha = 0.85f)
        fun plate(pts: FloatArray, c: Color) {
            val path = polyPath(p.cx, cy, pts)
            drawPath(path, c)
            drawPath(path, edge, style = Stroke(2f))
        }

        // Shoulder fins swept back.
        for (s in listOf(-1f, 1f)) {
            plate(floatArrayOf(s * r * 0.45f, -r * 0.15f, s * r * 1.25f * spread, -r * 0.55f, s * r * 1.05f * spread, r * 0.05f, s * r * 0.55f, r * 0.2f), PLATE_DARK)
            plate(floatArrayOf(s * r * 0.5f, r * 0.15f, s * r * 1.0f * spread, r * 0.3f, s * r * 0.75f * spread, r * 0.7f, s * r * 0.4f, r * 0.55f), mid)
        }
        // Robe: tapering stack of plates down to a point.
        plate(floatArrayOf(-r * 0.55f, r * 0.1f, r * 0.55f, r * 0.1f, r * 0.3f, r * 0.75f, 0f, r * 1.0f, -r * 0.3f, r * 0.75f), mid)
        drawLine(red.copy(alpha = 0.5f + 0.4f * pulse), Offset(p.cx, cy + r * 0.15f), Offset(p.cx, cy + r * 0.9f), 2f)
        // Chest.
        plate(floatArrayOf(-r * 0.6f, -r * 0.35f, r * 0.6f, -r * 0.35f, r * 0.55f, r * 0.2f, 0f, r * 0.35f, -r * 0.55f, r * 0.2f), lit)
        // Face plate with angry eyes.
        plate(floatArrayOf(-r * 0.42f, -r * 0.62f, r * 0.42f, -r * 0.62f, r * 0.36f, -r * 0.18f, 0f, -r * 0.02f, -r * 0.36f, -r * 0.18f), PLATE_DARK)
        val eyeA = if (hot) 1f else 0.75f + 0.25f * pulse
        for (s in listOf(-1f, 1f)) {
            val ex = p.cx + s * r * 0.17f + cos(p.aim) * r * 0.02f
            val ey = cy - r * 0.38f
            drawCircle(red.copy(alpha = 0.2f * eyeA), r * 0.12f, Offset(ex, ey))
            val eye = Path().apply {
                moveTo(ex - s * r * 0.13f, ey - r * 0.06f)
                lineTo(ex + s * r * 0.12f, ey + r * 0.03f)
                lineTo(ex - s * r * 0.06f, ey + r * 0.06f)
                close()
            }
            drawPath(eye, (if (hot) Color(0xFFFFE0E8) else red).copy(alpha = eyeA))
        }
        // Mitre: two tall split peaks with a glowing cross.
        val mh = r * (0.95f + 0.05f * p.phase)
        plate(floatArrayOf(-r * 0.42f, -r * 0.62f, -r * 0.05f, -r * 0.62f, -r * 0.08f, -r * 0.62f - mh, -r * 0.3f, -r * 0.62f - mh * 0.6f), mid)
        plate(floatArrayOf(r * 0.05f, -r * 0.62f, r * 0.42f, -r * 0.62f, r * 0.3f, -r * 0.62f - mh * 0.6f, r * 0.08f, -r * 0.62f - mh), lit)
        val cc = Offset(p.cx, cy - r * 0.62f - mh * 0.5f)
        val cs = r * 0.16f
        glow(cc, cs * 1.4f, red, 0.6f + pulse * 0.6f + (if (hot) 1.4f else 0f))
        drawLine((if (hot) Color.White else lighter(red, 0.5f)), Offset(cc.x - cs, cc.y), Offset(cc.x + cs, cc.y), 3f)
        drawLine((if (hot) Color.White else lighter(red, 0.5f)), Offset(cc.x, cc.y - cs * 1.3f), Offset(cc.x, cc.y + cs * 1.3f), 3f)
        if (hot) {
            // Charging: the cross throws thin rays out along its arms.
            for (a in listOf(0f, 1.571f, 3.142f, 4.712f)) drawLine(red.copy(alpha = 0.7f), cc, Offset(cc.x + cos(a) * r * 1.4f, cc.y + sin(a) * r * 1.4f), 2f)
        }
        // Phase 3: floating plate shards orbiting.
        if (p.phase >= 2) for (k in 0 until 5) {
            val a = t * 1.3f + k * 1.257f
            val sx = p.cx + cos(a) * r * 1.25f
            val sy = cy - r * 0.2f + sin(a) * r * 0.45f
            plate(floatArrayOf(sx - p.cx, sy - cy - r * 0.08f, sx - p.cx + r * 0.08f, sy - cy, sx - p.cx, sy - cy + r * 0.1f, sx - p.cx - r * 0.07f, sy - cy), PLATE_DARK)
        }
    }
}
