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
 * Rootkit Apostle (Boss Pack Gamma 01): a black armoured orb crowned with huge
 * magenta crystal spikes, a pointed mask with a single red eye, standing in a
 * pool of corrupted floor with crystal shards breaking through it. The crown
 * grows and the floor bloom spreads with each phase.
 */
internal object RootkitApostleBody : BossBody {
    // Owner, 2026-10-10: "Darker body".
    private val SHELL = Color(0xFF07030A)
    private val SHELL_LIT = Color(0xFF1C0A20)
    private val EYE = Color(0xFFFF2D3C)

    /** Two-tone crystal spike from [base] toward angle [a], [len] long, [w] wide at the base. */
    private fun DrawScope.crystal(bx: Float, by: Float, a: Float, len: Float, w: Float, lit: Color, dark: Color, edge: Color, squash: Float = 1f) {
        val tx = bx + cos(a) * len
        val ty = by + sin(a) * len * squash
        val px = -sin(a) * w / 2f
        val py = cos(a) * w / 2f * squash
        val left = Path().apply { moveTo(bx + px, by + py); lineTo(tx, ty); lineTo(bx, by); close() }
        val right = Path().apply { moveTo(bx - px, by - py); lineTo(tx, ty); lineTo(bx, by); close() }
        drawPath(left, lit)
        drawPath(right, dark)
        drawLine(edge, Offset(bx + px, by + py), Offset(tx, ty), 1.4f)
        drawLine(edge.copy(alpha = edge.alpha * 0.6f), Offset(bx - px, by - py), Offset(tx, ty), 1.2f)
    }

    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius
        val t = p.time
        val pink = p.color
        val hot = p.winding
        val pulse = 0.5f + 0.5f * sin(t * (2.5f + p.phase * 1.5f))
        val ground = p.cy + r * 0.62f
        // Charging: the crystals go white-hot instead of a glow that hides the body.
        // Dark crystals with bright magenta edges; white-hot while charging.
        val lit = if (p.hitFlash) Color.White else if (hot) lighter(pink, 0.55f) else darker(pink, 0.52f)
        val dark = if (p.hitFlash) Color(0xFFDDDDDD) else darker(pink, 0.18f)
        val edge = pink.copy(alpha = 0.95f)
        // The eye breathes slowly from dim to bright (~3 s) and blazes when it attacks (owner, 2026-10-10).
        val breath = if (hot) 1f else 0.5f + 0.5f * sin(t * 2.1f)

        // Corrupted floor pool with veins, wider each phase.
        val pool = r * (1.35f + 0.25f * p.phase)
        drawOval(pink.copy(alpha = 0.16f + 0.08f * pulse), Offset(p.cx - pool, ground - pool * 0.38f), Size(pool * 2f, pool * 0.76f))
        drawOval(pink.copy(alpha = 0.55f), Offset(p.cx - pool, ground - pool * 0.38f), Size(pool * 2f, pool * 0.76f), style = Stroke(2f))
        for (i in 0 until 9) {
            val a = i * 0.698f + t * 0.08f
            val l = pool * (0.75f + 0.25f * sin(t * 1.3f + i))
            drawLine(pink.copy(alpha = 0.45f), Offset(p.cx, ground), Offset(p.cx + cos(a) * l, ground + sin(a) * l * 0.38f), 1.6f)
        }
        // Shards breaking through the floor (back half first).
        val shards = 5 + p.phase * 3
        fun shard(i: Int) {
            val a = i * (6.283f / shards) + 0.3f
            val sx = p.cx + cos(a) * pool * 0.82f
            val sy = ground + sin(a) * pool * 0.82f * 0.38f
            val h = r * (0.42f + 0.32f * ((i * 37) % 5) / 4f) * (0.88f + 0.12f * sin(t * 2f + i))
            crystal(sx, sy, -1.571f + 0.3f * cos(i * 1.7f), h, r * 0.2f, lit, dark, edge)
            // A smaller twin leaning off each shard.
            crystal(sx + r * 0.08f, sy + 1f, -1.571f + 0.55f, h * 0.55f, r * 0.12f, lit, dark, edge)
        }
        for (i in 0 until shards) if (sin(i * (6.283f / shards) + 0.3f) < 0f) shard(i)

        // Crown: big spikes fanning up behind the orb.
        val crown = 5 + p.phase * 2
        val grow = (1f + 0.15f * p.phase) * (if (hot) 1.12f else 1f)
        for (i in 0 until crown) {
            val f = i / (crown - 1f) - 0.5f
            val a = -1.571f + f * 2.3f
            val len = r * (1.35f - kotlin.math.abs(f) * 0.9f) * grow * (0.94f + 0.06f * sin(t * 3f + i))
            crystal(p.cx + cos(a) * r * 0.5f, p.cy + sin(a) * r * 0.45f, a, len, r * 0.34f, lit, dark, edge)
        }
        if (hot) glow(Offset(p.cx, p.cy - r * 0.5f), r * 0.8f, pink, 0.9f)

        // Armoured orb.
        drawCircle(
            Brush.radialGradient(listOf(SHELL_LIT, SHELL), center = Offset(p.cx - r * 0.25f, p.cy - r * 0.3f), radius = r * 1.1f),
            r * 0.78f, Offset(p.cx, p.cy)
        )
        // Plate seams.
        for (i in 0 until 6) {
            val a = i * 1.047f + 0.5f
            drawLine(pink.copy(alpha = 0.18f + 0.22f * breath), Offset(p.cx + cos(a) * r * 0.32f, p.cy + sin(a) * r * 0.3f), Offset(p.cx + cos(a) * r * 0.76f, p.cy + sin(a) * r * 0.72f), 1.8f)
        }
        drawCircle(pink.copy(alpha = 0.55f), r * 0.78f, Offset(p.cx, p.cy), style = Stroke(2f))
        // Side spikes off the orb.
        for (s in listOf(-1f, 1f)) {
            crystal(p.cx + s * r * 0.7f, p.cy + r * 0.05f, if (s < 0f) 3.3f else -0.16f, r * 0.55f * grow, r * 0.22f, lit, dark, edge)
            crystal(p.cx + s * r * 0.55f, p.cy + r * 0.45f, if (s < 0f) 2.5f else 0.64f, r * 0.42f * grow, r * 0.2f, lit, dark, edge)
        }

        // Pointed mask with the eye, which tracks the operative.
        val mask = Path().apply {
            moveTo(p.cx, p.cy - r * 0.62f)
            lineTo(p.cx + r * 0.36f, p.cy - r * 0.05f)
            lineTo(p.cx + r * 0.16f, p.cy + r * 0.5f)
            lineTo(p.cx, p.cy + r * 0.72f)
            lineTo(p.cx - r * 0.16f, p.cy + r * 0.5f)
            lineTo(p.cx - r * 0.36f, p.cy - r * 0.05f)
            close()
        }
        drawPath(mask, Color(0xFF0B040D))
        drawPath(mask, pink.copy(alpha = 0.9f), style = Stroke(2f))
        val eyeC = Offset(p.cx + cos(p.aim) * r * 0.06f, p.cy + r * 0.02f + sin(p.aim) * r * 0.05f)
        val eyeR = r * (0.17f + 0.03f * p.phase)
        glow(eyeC, eyeR * (1.15f + 0.35f * breath), EYE, 0.25f + 1.4f * breath + (if (hot) 1.6f else 0f))
        drawCircle(darker(EYE, 0.3f + 0.7f * breath), eyeR, eyeC)
        drawCircle(lighter(darker(EYE, 0.25f + 0.75f * breath), 0.6f * breath), eyeR * 0.55f, eyeC)
        drawCircle(Color.White.copy(alpha = if (hot) 1f else 0.15f + 0.7f * breath), eyeR * 0.25f, eyeC)
        // Red code-slit running down the mask from the eye.
        drawLine(EYE.copy(alpha = 0.2f + 0.7f * breath), Offset(p.cx, eyeC.y + eyeR), Offset(p.cx, p.cy + r * 0.6f), 2.5f)

        // Front shards over the orb's base.
        for (i in 0 until shards) if (sin(i * (6.283f / shards) + 0.3f) >= 0f) shard(i)

        // Phase 3: code motes rising off the crown.
        if (p.phase >= 2) for (i in 0 until 8) {
            val k = (t * 0.9f + i * 0.125f) % 1f
            val mx = p.cx + sin(i * 2.4f) * r * 0.9f
            drawRect(pink.copy(alpha = 1f - k), Offset(mx, p.cy - r * 0.4f - k * r * 1.6f), Size(3.5f, 3.5f))
        }
    }
}
