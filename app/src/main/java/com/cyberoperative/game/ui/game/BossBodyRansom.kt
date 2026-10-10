package com.cyberoperative.game.ui.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.cos
import kotlin.math.sin

/**
 * RANSOM (classic, level 40): a giant floating padlock. A dark steel body with
 * gold trim, a thick gold shackle, a spinning combination dial around a burning
 * keyhole, hanging chains and encrypted hex glyphs drifting around it. The
 * shackle lifts open a little more each phase; charging spins the dial and
 * blazes the keyhole.
 */
internal object RansomBody : BossBody {
    private val STEEL_LIT = Color(0xFF4A4636)
    private val STEEL = Color(0xFF26231A)
    private val STEEL_DARK = Color(0xFF0E0C08)

    override val previewSpan: Float get() = 5.2f
    override val previewDrop: Float get() = 0.4f

    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius * 1.35f
        val t = p.time
        val gold = p.color
        val hot = p.winding
        val cy = p.cy + sin(t * 1.5f) * r * 0.05f
        val goldLit = lighter(gold, 0.45f)
        val goldDark = darker(gold, 0.45f)

        // Encrypted glyphs drifting around.
        val glyphs = listOf("0x", "FF", "A3", "7E", "#", "$")
        for (k in 0 until 6) {
            val a = t * 0.4f + k * 1.047f
            val gx = p.cx + cos(a) * r * 1.45f
            val gy = cy - r * 0.2f + sin(a) * r * 0.55f
            drawContext.canvas.nativeCanvas.drawText(glyphs[k], gx, gy, android.graphics.Paint().apply {
                color = android.graphics.Color.argb((120 + 80 * sin(t * 2f + k)).toInt().coerceIn(0, 255), (gold.red * 255).toInt(), (gold.green * 255).toInt(), (gold.blue * 255).toInt())
                textSize = r * 0.22f; isAntiAlias = true; typeface = android.graphics.Typeface.MONOSPACE; textAlign = android.graphics.Paint.Align.CENTER
            })
        }

        // Shackle: thick gold U, lifting open with each phase (left leg slides out).
        val lift = r * (0.08f * p.phase + if (hot) 0.1f else 0f)
        val sw = r * 0.2f
        val sl = p.cx - r * 0.42f; val sr = p.cx + r * 0.42f
        val sTop = cy - r * 1.25f - lift
        val shackle = Path().apply {
            moveTo(sl, cy - r * 0.35f - lift)
            lineTo(sl, sTop + r * 0.42f)
            cubicTo(sl, sTop - r * 0.05f, sr, sTop - r * 0.05f, sr, sTop + r * 0.42f)
            lineTo(sr, cy - r * 0.35f)
        }
        drawPath(shackle, goldDark, style = Stroke(sw + 4f))
        drawPath(shackle, gold, style = Stroke(sw))
        drawPath(shackle, goldLit.copy(alpha = 0.8f), style = Stroke(sw * 0.25f))

        // Chains hanging from the lower corners.
        for (s in listOf(-1f, 1f)) {
            for (k in 0 until 5) {
                val sway = sin(t * 2f + s) * r * 0.06f * k
                val lx = p.cx + s * (r * 0.72f + k * r * 0.06f) + sway
                val ly = cy + r * 0.35f + k * r * 0.17f
                val vertical = k % 2 == 0
                val w = if (vertical) r * 0.1f else r * 0.16f
                val h = if (vertical) r * 0.18f else r * 0.1f
                drawOval(STEEL_DARK, Offset(lx - w / 2 - 1.5f, ly - h / 2 - 1.5f), Size(w + 3f, h + 3f), style = Stroke(4f))
                drawOval(STEEL_LIT, Offset(lx - w / 2, ly - h / 2), Size(w, h), style = Stroke(2.5f))
            }
        }

        // Lock body: rounded slab with depth.
        val bw = r * 1.5f; val bh = r * 1.15f
        val left = p.cx - bw / 2; val top = cy - r * 0.45f
        drawRoundRect(STEEL_DARK, Offset(left, top + r * 0.18f), Size(bw, bh), CornerRadius(r * 0.22f))
        drawRoundRect(Brush.verticalGradient(listOf(if (p.hitFlash) Color.White else STEEL_LIT, STEEL, STEEL_DARK), top, top + bh), Offset(left, top), Size(bw, bh), CornerRadius(r * 0.22f))
        drawRoundRect(gold, Offset(left, top), Size(bw, bh), CornerRadius(r * 0.22f), style = Stroke(3f))
        drawRoundRect(goldDark, Offset(left + r * 0.08f, top + r * 0.08f), Size(bw - r * 0.16f, bh - r * 0.16f), CornerRadius(r * 0.16f), style = Stroke(1.5f))
        // Corner bolts.
        for (bx in listOf(left + r * 0.16f, left + bw - r * 0.16f)) for (by in listOf(top + r * 0.16f, top + bh - r * 0.16f)) {
            drawCircle(goldDark, r * 0.06f, Offset(bx, by))
            drawCircle(goldLit, r * 0.035f, Offset(bx - 1f, by - 1f))
        }

        // Combination dial around the keyhole.
        val dc = Offset(p.cx, top + bh * 0.48f)
        val dr = r * 0.42f
        drawCircle(STEEL_DARK, dr, dc)
        drawCircle(Brush.radialGradient(listOf(STEEL_LIT, STEEL), center = Offset(dc.x - dr * 0.3f, dc.y - dr * 0.3f), radius = dr * 1.2f), dr * 0.92f, dc)
        val spin = t * (if (hot) 6f else 0.7f) + p.phase * 0.6f
        for (k in 0 until 24) {
            val a = spin + k * 0.2618f
            val major = k % 3 == 0
            drawLine(if (major) gold else goldDark, Offset(dc.x + cos(a) * dr * 0.92f, dc.y + sin(a) * dr * 0.92f), Offset(dc.x + cos(a) * dr * (if (major) 0.72f else 0.8f), dc.y + sin(a) * dr * (if (major) 0.72f else 0.8f)), if (major) 2.5f else 1.4f)
        }
        drawCircle(gold, dr * 0.92f, dc, style = Stroke(2.5f))
        // Index marker on top.
        drawPath(Path().apply { moveTo(dc.x - r * 0.06f, dc.y - dr - r * 0.1f); lineTo(dc.x + r * 0.06f, dc.y - dr - r * 0.1f); lineTo(dc.x, dc.y - dr + r * 0.02f); close() }, goldLit)
        // Keyhole, burning.
        val kh = if (hot) 1f else 0.6f + 0.4f * sin(t * 2.5f)
        glow(dc, dr * 0.45f, gold, 0.8f + kh)
        val key = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(dc.x - r * 0.1f, dc.y - r * 0.17f, dc.x + r * 0.1f, dc.y + r * 0.03f))
            moveTo(dc.x - r * 0.06f, dc.y - r * 0.02f); lineTo(dc.x - r * 0.1f, dc.y + r * 0.2f); lineTo(dc.x + r * 0.1f, dc.y + r * 0.2f); lineTo(dc.x + r * 0.06f, dc.y - r * 0.02f); close()
        }
        drawPath(key, if (hot) Color.White else Color(0xFFFFF0B0).copy(alpha = 0.5f + 0.5f * kh))
    }
}
