package com.cyberoperative.game.ui.common

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * The PC cursor (owner art, 2026-10-11), traced as vectors: a beveled neon arrow with a navy
 * core plate, a cyan band, green inner accents and a green "eye" in the middle.
 *
 * Coordinates are in a 1000-unit box with the arrow tip (the hotspot) at (0, 0). The same
 * shapes draw the system cursor (desktop, Java2D) and the in-game aim marker (Compose).
 */
object CursorArt {
    /** Outer silhouette, clockwise from the tip. */
    val OUTER = floatArrayOf(
        0f, 0f, 350f, 268f, 410f, 284f, 765f, 588f, 520f, 622f,
        712f, 902f, 612f, 1050f, 402f, 722f, 280f, 868f
    )
    /** The cyan band's inner edge (the navy plate inside it). */
    val INNER = floatArrayOf(
        70f, 95f, 335f, 300f, 395f, 318f, 640f, 538f, 452f, 566f,
        640f, 860f, 612f, 900f, 415f, 650f, 300f, 760f
    )
    /** Green accent strokes: down the left inner edge, and along the right wing. */
    val ACCENT_LEFT = floatArrayOf(120f, 170f, 255f, 640f)
    val ACCENT_RIGHT = floatArrayOf(520f, 470f, 650f, 545f)
    /** The eye. */
    const val EYE_X = 410f
    const val EYE_Y = 520f
    const val EYE_R = 58f
    const val RING_R = 92f

    val NEON_BLUE = Color(0xFF1E6BFF)
    val CYAN = Color(0xFF39E6FF)
    val NAVY = Color(0xFF0D1830)
    val GREEN = Color(0xFF2DFFB0)

    private val path = Path()

    private fun Path.poly(pts: FloatArray, k: Float, x: Float, y: Float) {
        reset()
        moveTo(x + pts[0] * k, y + pts[1] * k)
        var i = 2
        while (i < pts.size) { lineTo(x + pts[i] * k, y + pts[i + 1] * k); i += 2 }
        close()
    }

    /** Draws the cursor with its tip at ([x], [y]), [size] px tall. */
    fun DrawScope.drawCursor(x: Float, y: Float, size: Float, alpha: Float = 1f, pressed: Boolean = false) {
        val k = size / 1050f
        path.poly(OUTER, k, x, y)
        drawPath(path, NEON_BLUE.copy(alpha = 0.35f * alpha), style = Stroke(size * 0.11f, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
        drawPath(path, NAVY.copy(alpha = alpha))
        drawPath(path, CYAN.copy(alpha = alpha), style = Stroke(size * 0.05f, join = StrokeJoin.Round))
        path.poly(INNER, k, x, y)
        drawPath(path, Color(0xFF14254A).copy(alpha = alpha))
        drawPath(path, CYAN.copy(alpha = 0.55f * alpha), style = Stroke(size * 0.018f, join = StrokeJoin.Round))
        val g = if (pressed) Color.White else GREEN
        drawLine(g.copy(alpha = alpha), Offset(x + ACCENT_LEFT[0] * k, y + ACCENT_LEFT[1] * k), Offset(x + ACCENT_LEFT[2] * k, y + ACCENT_LEFT[3] * k), size * 0.03f)
        drawLine(g.copy(alpha = alpha), Offset(x + ACCENT_RIGHT[0] * k, y + ACCENT_RIGHT[1] * k), Offset(x + ACCENT_RIGHT[2] * k, y + ACCENT_RIGHT[3] * k), size * 0.03f)
        val c = Offset(x + EYE_X * k, y + EYE_Y * k)
        drawCircle(CYAN.copy(alpha = alpha), RING_R * k, c, style = Stroke(size * 0.03f))
        drawCircle(g.copy(alpha = alpha), EYE_R * k, c)
        drawCircle(Color.White.copy(alpha = 0.5f * alpha), EYE_R * k * 0.35f, Offset(c.x - EYE_R * k * 0.3f, c.y - EYE_R * k * 0.3f))
    }
}
