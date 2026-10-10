package com.cyberoperative.game.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * EXFIL (classic, level 80): a data-thief stealth dart. A sleek black arrowhead
 * with swept wings always pointing at you, a pink cockpit slit, twin
 * afterburners and two cargo pods of stolen data glowing under the wings, with
 * a trail of leaked bits behind it. Fuller pods and longer flames each phase;
 * charging (a dash coming) flares the burners and tucks the wings back.
 */
internal object ExfilBody : BossBody {
    private val LIT = Color(0xFF3A3040)
    private val MID = Color(0xFF1C1622)
    private val DARK = Color(0xFF08060B)

    override val previewSpan: Float get() = 5.2f

    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius * 1.45f
        val t = p.time
        val col = p.color
        val hot = p.winding
        val cy = p.cy + sin(t * 2.4f) * r * 0.05f
        val a = p.aim

        // Shadow and leaked-bit trail behind.
        drawOval(Color.Black.copy(alpha = 0.35f), Offset(p.cx - r * 0.8f, p.cy + r * 0.6f), Size(r * 1.6f, r * 0.45f))
        for (k in 0 until 10) {
            val f = ((t * 1.4f + k * 0.1f) % 1f)
            val d = r * (0.7f + f * 1.6f)
            val off = sin(k * 2.3f) * r * 0.3f
            val bx = p.cx - cos(a) * d - sin(a) * off
            val by = cy - sin(a) * d * 0.75f + cos(a) * off * 0.75f
            drawRect(col.copy(alpha = 0.7f * (1f - f)), Offset(bx - 2.5f, by - 2.5f), Size(5f, 5f))
        }

        rotate(a * 180f / PI.toFloat(), Offset(p.cx, cy)) {
            val x = p.cx; val y = cy
            val sweep = if (hot) 0.75f else 1f
            // Afterburners.
            for (s in listOf(-1f, 1f)) {
                val fl = r * (0.55f + 0.15f * p.phase + 0.15f * sin(t * 40f + s)) * (if (hot) 1.8f else 1f)
                val ny = y + s * r * 0.22f
                drawOval(Brush.horizontalGradient(listOf(col.copy(alpha = 0f), col, Color.White), x - r * 0.75f - fl, x - r * 0.7f), Offset(x - r * 0.72f - fl, ny - r * 0.09f), Size(fl, r * 0.18f))
            }
            // Wings, swept back.
            for (s in listOf(-1f, 1f)) {
                val wing = Path().apply {
                    moveTo(x + r * 0.25f, y + s * r * 0.15f)
                    lineTo(x - r * 0.55f, y + s * r * 1.0f * sweep)
                    lineTo(x - r * 0.75f, y + s * r * 0.9f * sweep)
                    lineTo(x - r * 0.45f, y + s * r * 0.2f)
                    close()
                }
                drawPath(wing, if (s < 0) LIT else MID)
                drawPath(wing, col.copy(alpha = 0.8f), style = Stroke(1.8f))
                // Data cargo pod under the wing.
                val px = x - r * 0.3f; val py = y + s * r * 0.55f * sweep
                val fill = 0.45f + 0.2f * p.phase
                drawOval(DARK, Offset(px - r * 0.28f, py - r * 0.11f), Size(r * 0.56f, r * 0.22f))
                drawOval(col.copy(alpha = fill + 0.2f * sin(t * 6f + s)), Offset(px - r * 0.24f, py - r * 0.07f), Size(r * 0.48f * fill + r * 0.1f, r * 0.14f))
                drawOval(col.copy(alpha = 0.8f), Offset(px - r * 0.28f, py - r * 0.11f), Size(r * 0.56f, r * 0.22f), style = Stroke(1.5f))
            }
            // Fuselage: long arrowhead.
            val hull = Path().apply {
                moveTo(x + r * 1.05f, y)
                lineTo(x + r * 0.2f, y - r * 0.28f)
                lineTo(x - r * 0.75f, y - r * 0.3f)
                lineTo(x - r * 0.62f, y)
                lineTo(x - r * 0.75f, y + r * 0.3f)
                lineTo(x + r * 0.2f, y + r * 0.28f)
                close()
            }
            drawPath(hull, Brush.verticalGradient(listOf(if (p.hitFlash) Color.White else LIT, MID, DARK), y - r * 0.3f, y + r * 0.3f))
            drawPath(hull, DARK, style = Stroke(2f))
            drawLine(col.copy(alpha = 0.7f), Offset(x + r * 1.0f, y), Offset(x - r * 0.6f, y), 1.6f)
            // Cockpit slit.
            val glowA = if (hot) 1f else 0.6f + 0.4f * sin(t * 3f)
            val slit = Path().apply { moveTo(x + r * 0.55f, y); lineTo(x + r * 0.15f, y - r * 0.1f); lineTo(x - r * 0.05f, y); lineTo(x + r * 0.15f, y + r * 0.1f); close() }
            drawPath(slit, (if (hot) Color.White else col).copy(alpha = glowA))
            glow(Offset(x + r * 0.2f, y), r * 0.15f, col, glowA)
        }
    }
}
