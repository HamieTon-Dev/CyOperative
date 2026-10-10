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
 * WORM PRIME (classic, level 30): a self-replicating armoured worm. A round
 * head with a circular maw of inward teeth and a burning gullet turns toward
 * you; a tail of ring segments with molten seams writhes behind it, dripping
 * corrupted orange code. More drips and hotter seams each phase.
 */
internal object WormPrimeBody : BossBody {
    private val LIT = Color(0xFF4A3028)
    private val MID = Color(0xFF2A1812)
    private val DARK = Color(0xFF100806)

    override val previewSpan: Float get() = 6.6f

    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius * 1.35f
        val t = p.time
        val hot = p.winding
        val col = p.color
        val back = p.aim + Math.PI.toFloat()
        val seam = 0.5f + 0.15f * p.phase

        // Tail: segments trailing away from the aim, writhing.
        val n = 6
        // The tail curls off to one side in an S, so it never hides behind the head.
        val pts = Array(n) { k ->
            val d = r * (0.8f + k * 0.48f)
            val curl = back + 0.55f + k * 0.32f + sin(t * 1.6f - k * 0.7f) * 0.18f
            val x = p.cx + cos(curl) * d
            val y = p.cy + sin(curl) * d * 0.62f + r * 0.15f
            Offset(x, y)
        }
        for (k in n - 1 downTo 0) {
            val s = r * (0.62f - k * 0.07f)
            val c = pts[k]
            drawOval(Color.Black.copy(alpha = 0.3f), Offset(c.x - s, c.y + s * 0.5f), Size(s * 2, s * 0.7f))
            drawCircle(Brush.radialGradient(listOf(if (p.hitFlash) Color.White else LIT, MID, DARK), center = Offset(c.x - s * 0.3f, c.y - s * 0.4f), radius = s * 1.3f), s, c)
            // Molten seam ring.
            drawCircle(col.copy(alpha = seam + 0.3f * sin(t * 5f - k)), s * 0.78f, c, style = Stroke(s * 0.12f))
            drawCircle(DARK, s, c, style = Stroke(2f))
            // Dorsal spike.
            crystal(c.x, c.y - s * 0.6f, -Math.PI.toFloat() / 2f + sin(t * 2f + k) * 0.2f, s * 0.55f, s * 0.35f, LIT, DARK, col.copy(alpha = 0.6f))
        }
        // Corrupted drips falling from the tail.
        for (k in 0 until 2 + p.phase * 2) {
            val f = ((t * 0.8f + k * 0.31f) % 1f)
            val c = pts[(k * 2) % n]
            drawCircle(col.copy(alpha = 0.8f * (1f - f)), r * 0.05f * (1f - f * 0.5f), Offset(c.x + (k % 3 - 1) * r * 0.1f, c.y + r * 0.3f + f * r * 0.5f))
        }

        // Head.
        val hr = r * 0.85f
        drawOval(Color.Black.copy(alpha = 0.35f), Offset(p.cx - hr, p.cy + hr * 0.55f), Size(hr * 2, hr * 0.7f))
        drawCircle(Brush.radialGradient(listOf(if (p.hitFlash) Color.White else LIT, MID, DARK), center = Offset(p.cx - hr * 0.3f, p.cy - hr * 0.4f), radius = hr * 1.3f), hr, Offset(p.cx, p.cy))
        // Armour plates around the head.
        for (k in 0 until 6) {
            val a = k * 1.047f + 0.5f
            drawArc(col.copy(alpha = 0.55f), a * 57.3f, 40f, false, Offset(p.cx - hr * 0.92f, p.cy - hr * 0.92f), Size(hr * 1.84f, hr * 1.84f), style = Stroke(3f))
        }
        drawCircle(DARK, hr, Offset(p.cx, p.cy), style = Stroke(2.5f))
        // Maw, toward the aim.
        val mx = p.cx + cos(p.aim) * hr * 0.32f
        val my = p.cy + sin(p.aim) * hr * 0.26f
        val open = if (hot) 0.62f else 0.5f + 0.04f * sin(t * 4f)
        val mr = hr * open
        drawCircle(DARK, mr, Offset(mx, my))
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE0A0), col, col.copy(alpha = 0f)), center = Offset(mx, my), radius = mr * 0.8f), mr * 0.8f, Offset(mx, my))
        // Two rings of inward teeth, the inner one counter-rotating.
        for (ring in 0..1) {
            val rr = mr * (if (ring == 0) 1f else 0.65f)
            val rot = t * (if (ring == 0) 0.8f else -1.3f)
            val teeth = if (ring == 0) 12 else 9
            for (k in 0 until teeth) {
                val a = rot + k * 6.283f / teeth
                val bx = mx + cos(a) * rr; val by = my + sin(a) * rr
                val tip = Offset(mx + cos(a) * rr * 0.62f, my + sin(a) * rr * 0.62f)
                val px = -sin(a) * rr * 0.16f; val py = cos(a) * rr * 0.16f
                val tooth = Path().apply { moveTo(bx + px, by + py); lineTo(tip.x, tip.y); lineTo(bx - px, by - py); close() }
                drawPath(tooth, Color(0xFFEDE0D0))
                drawPath(tooth, DARK, style = Stroke(1f))
            }
        }
        drawCircle(col.copy(alpha = 0.9f), mr, Offset(mx, my), style = Stroke(2.5f))
        // Eye clusters above the maw.
        for (s in listOf(-1f, 1f)) for (j in 0..1) {
            val e = Offset(p.cx + s * hr * (0.45f + j * 0.18f), p.cy - hr * (0.55f - j * 0.12f))
            drawCircle(col.copy(alpha = 0.3f), hr * 0.09f, e)
            drawCircle(if (hot) Color.White else lighter(col, 0.4f), hr * 0.045f, e)
        }
    }
}
