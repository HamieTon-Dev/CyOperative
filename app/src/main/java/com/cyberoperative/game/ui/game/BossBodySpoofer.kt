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
 * SPOOFER (classic, level 110): an identity forger. A floating porcelain mask
 * with hollow pink-lit eyes and a fixed smile, hanging in a hood of TV static,
 * with ghost copies of itself drifting off to each side in chromatic offsets
 * (the forgeries). The mask cracks and more copies split off each phase;
 * charging pulls the copies apart and turns the smile into a grin of light.
 */
internal object SpooferBody : BossBody {
    private val MASK_LIT = Color(0xFFF2E6EE)
    private val MASK = Color(0xFFC8B4C2)
    private val MASK_DARK = Color(0xFF6A5866)

    override val previewSpan: Float get() = 5.4f

    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius * 1.35f
        val t = p.time
        val pink = p.color
        val hot = p.winding
        val cy = p.cy + sin(t * 1.5f) * r * 0.07f

        drawOval(Color.Black.copy(alpha = 0.35f), Offset(p.cx - r * 0.7f, p.cy + r * 0.85f), Size(r * 1.4f, r * 0.35f))

        // Static hood behind the mask.
        val hood = Path().apply {
            moveTo(p.cx, cy - r * 1.25f)
            cubicTo(p.cx + r * 1.0f, cy - r * 1.1f, p.cx + r * 0.95f, cy + r * 0.5f, p.cx + r * 0.55f, cy + r * 0.85f)
            lineTo(p.cx - r * 0.55f, cy + r * 0.85f)
            cubicTo(p.cx - r * 0.95f, cy + r * 0.5f, p.cx - r * 1.0f, cy - r * 1.1f, p.cx, cy - r * 1.25f)
            close()
        }
        drawPath(hood, Brush.verticalGradient(listOf(Color(0xFF2A1426), Color(0xFF0E060C)), cy - r * 1.2f, cy + r * 0.85f))
        // Static noise lines in the hood.
        for (k in 0 until 14) {
            val y = cy - r * 1.0f + ((k * 0.137f + t * 0.9f) % 1f) * r * 1.8f
            val w = r * (0.3f + 0.4f * ((k * 7 % 5) / 5f))
            val x = p.cx + sin(k * 3.1f + t * 5f) * r * 0.3f
            drawLine(pink.copy(alpha = 0.25f), Offset(x - w, y), Offset(x + w, y), 1.5f)
        }
        drawPath(hood, pink.copy(alpha = 0.5f), style = Stroke(1.8f))

        // Ghost copies (forgeries), chromatic offset.
        val copies = 1 + p.phase
        val spread = r * (0.75f + (if (hot) 0.45f else 0f))
        for (k in 1..copies) for (s in listOf(-1f, 1f)) {
            val off = s * spread * k * (0.75f + 0.1f * sin(t * 2f + k))
            val tint = if (s < 0) Color(0xFF30E0FF) else Color(0xFFFF30C0)
            val a = (0.28f - 0.06f * k) * (if (sin(t * 11f + k * 3f + s) > -0.6f) 1f else 0.3f)
            mask(p.cx + off, cy + sin(t * 1.7f + k) * r * 0.06f, r * (1f - 0.1f * k), tint.copy(alpha = a), tint.copy(alpha = a), tint.copy(alpha = a), pink, false, 0, t, ghost = true)
        }
        // The real mask.
        mask(p.cx, cy, r, if (p.hitFlash) Color.White else MASK_LIT, MASK, MASK_DARK, pink, hot, p.phase, t, ghost = false)
    }

    /** One mask; [ghost] masks are flat tinted silhouettes. */
    private fun DrawScope.mask(cx: Float, cy: Float, r: Float, lit: Color, mid: Color, dark: Color, pink: Color, hot: Boolean, cracks: Int, t: Float, ghost: Boolean) {
        val face = Path().apply {
            moveTo(cx, cy - r * 0.85f)
            cubicTo(cx + r * 0.62f, cy - r * 0.85f, cx + r * 0.62f, cy + r * 0.1f, cx + r * 0.38f, cy + r * 0.48f)
            quadraticBezierTo(cx, cy + r * 0.75f, cx - r * 0.38f, cy + r * 0.48f)
            cubicTo(cx - r * 0.62f, cy + r * 0.1f, cx - r * 0.62f, cy - r * 0.85f, cx, cy - r * 0.85f)
            close()
        }
        if (ghost) { drawPath(face, lit); return }
        drawPath(face, Brush.linearGradient(listOf(lit, mid, dark), Offset(cx - r * 0.5f, cy - r * 0.8f), Offset(cx + r * 0.5f, cy + r * 0.6f)))
        drawPath(face, dark, style = Stroke(2f))
        // Brow ridge and painted marks.
        for (s in listOf(-1f, 1f)) {
            drawLine(pink.copy(alpha = 0.8f), Offset(cx + s * r * 0.12f, cy - r * 0.45f), Offset(cx + s * r * 0.4f, cy - r * 0.38f), 2.5f)
            // Tear streak under each eye.
            drawLine(pink.copy(alpha = 0.6f), Offset(cx + s * r * 0.24f, cy - r * 0.12f), Offset(cx + s * r * 0.22f, cy + r * 0.18f), 2f)
        }
        // Hollow eyes lit from inside.
        val eye = if (hot) 1f else 0.6f + 0.4f * sin(t * 2.2f)
        for (s in listOf(-1f, 1f)) {
            val e = Path().apply {
                moveTo(cx + s * r * 0.08f, cy - r * 0.25f)
                quadraticBezierTo(cx + s * r * 0.25f, cy - r * 0.38f, cx + s * r * 0.38f, cy - r * 0.24f)
                quadraticBezierTo(cx + s * r * 0.24f, cy - r * 0.14f, cx + s * r * 0.08f, cy - r * 0.25f)
                close()
            }
            drawPath(e, Color.Black)
            glow(Offset(cx + s * r * 0.23f, cy - r * 0.24f), r * 0.08f, pink, eye * 1.4f)
            drawCircle((if (hot) Color.White else pink).copy(alpha = eye), r * 0.035f, Offset(cx + s * r * 0.23f, cy - r * 0.24f))
        }
        // Fixed smile; a grin of light when charging.
        val smile = Path().apply {
            moveTo(cx - r * 0.28f, cy + r * 0.2f)
            quadraticBezierTo(cx, cy + r * (if (hot) 0.5f else 0.38f), cx + r * 0.28f, cy + r * 0.2f)
            quadraticBezierTo(cx, cy + r * (if (hot) 0.38f else 0.3f), cx - r * 0.28f, cy + r * 0.2f)
            close()
        }
        drawPath(smile, if (hot) pink else Color.Black)
        if (hot) glow(Offset(cx, cy + r * 0.3f), r * 0.15f, pink, 1.2f)
        // Cracks with each phase.
        val cr = listOf(
            floatArrayOf(0.1f, -0.85f, 0.18f, -0.6f, 0.08f, -0.42f),
            floatArrayOf(-0.45f, 0.1f, -0.3f, 0.0f, -0.32f, 0.2f),
            floatArrayOf(0.4f, 0.25f, 0.25f, 0.15f, 0.3f, 0.4f),
            floatArrayOf(-0.1f, 0.6f, 0.0f, 0.45f)
        )
        for (k in 0 until (cracks * 2).coerceAtMost(cr.size)) {
            val c = cr[k]
            val path = Path().apply {
                moveTo(cx + c[0] * r, cy + c[1] * r)
                var i = 2
                while (i < c.size) { lineTo(cx + c[i] * r, cy + c[i + 1] * r); i += 2 }
            }
            drawPath(path, Color(0xFF2A1426), style = Stroke(2f))
            drawPath(path, pink.copy(alpha = 0.6f), style = Stroke(0.8f))
        }
    }
}
