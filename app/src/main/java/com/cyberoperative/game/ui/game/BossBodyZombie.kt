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
 * ZOMBIE (classic, level 100): an undead process. A hulking, hunched robot
 * corpse that won't terminate — a cracked chest plate over exposed cable ribs,
 * a hanging broken jaw, one burning red eye and one dead X, long arms dragging
 * cable sinew. It lurches as it moves; sparks, leaks and missing plates grow
 * each phase as it gets angrier. Charging opens the jaw and flares the eye.
 */
internal object ZombieBody : BossBody {
    private val LIT = Color(0xFF6A5A56)
    private val MID = Color(0xFF3A302E)
    private val DARK = Color(0xFF0E0B0B)
    private val ROT = Color(0xFF6A8A3A)

    override val previewSpan: Float get() = 5f

    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius * 1.3f
        val t = p.time
        val red = p.color
        val hot = p.winding
        val lurch = sin(t * 2.6f)
        val cy = p.cy + kotlin.math.abs(lurch) * r * 0.06f
        val tilt = lurch * r * 0.08f
        val lit = if (p.hitFlash) Color.White else LIT

        drawOval(Color.Black.copy(alpha = 0.45f), Offset(p.cx - r * 1.0f, p.cy + r * 0.8f), Size(r * 2f, r * 0.45f))
        // Leaking code puddle.
        drawOval(ROT.copy(alpha = 0.25f + 0.1f * p.phase), Offset(p.cx - r * 0.5f, p.cy + r * 0.85f), Size(r * 1.0f, r * 0.25f))

        // Arms: long, dragging, with cable sinew.
        for (s in listOf(-1f, 1f)) {
            val sx = p.cx + s * r * 0.75f + tilt
            val sy = cy - r * 0.35f
            val swing = sin(t * 2.6f + s) * 0.2f
            val ex = sx + s * r * 0.3f + swing * r * 0.3f
            val ey = sy + r * 0.6f
            val hx = ex + s * r * 0.05f + swing * r * 0.25f
            val hy = ey + r * 0.55f
            drawLine(DARK, Offset(sx, sy), Offset(ex, ey), r * 0.26f)
            drawLine(lit, Offset(sx, sy), Offset(ex, ey), r * 0.18f)
            drawLine(DARK, Offset(ex, ey), Offset(hx, hy), r * 0.2f)
            drawLine(MID, Offset(ex, ey), Offset(hx, hy), r * 0.13f)
            // Cable sinew between joints.
            drawLine(red.copy(alpha = 0.6f), Offset(sx + s * r * 0.08f, sy), Offset(hx, hy), 1.5f)
            drawCircle(DARK, r * 0.1f, Offset(ex, ey))
            drawCircle(red.copy(alpha = 0.6f), r * 0.1f, Offset(ex, ey), style = Stroke(1.5f))
            // Claw hand.
            for (k in -1..1) crystal(hx, hy, 1.571f + k * 0.35f, r * 0.28f, r * 0.08f, lit, DARK, red.copy(alpha = 0.5f))
        }

        // Torso: hunched slab.
        val torso = floatArrayOf(-r * 0.75f, -r * 0.55f, r * 0.75f, -r * 0.5f, r * 0.6f, r * 0.45f, -r * 0.6f, r * 0.5f)
        slab(p.cx + tilt, cy, torso, r * 0.25f, if (p.hitFlash) Color.White else MID, side = DARK)
        // Exposed cable ribs down the chest.
        for (k in 0 until 4) {
            val y = cy - r * 0.15f + k * r * 0.14f
            val rib = Path().apply {
                moveTo(p.cx + tilt - r * 0.45f, y)
                quadraticBezierTo(p.cx + tilt, y + r * 0.1f, p.cx + tilt + r * 0.45f, y)
            }
            drawPath(rib, DARK, style = Stroke(r * 0.07f))
            drawPath(rib, red.copy(alpha = 0.5f + 0.3f * sin(t * 4f + k)), style = Stroke(1.5f))
        }
        // Chest plate, cracked; chunks missing each phase.
        val plate = Path().apply {
            moveTo(p.cx + tilt - r * 0.6f, cy - r * 0.5f)
            lineTo(p.cx + tilt + r * (if (p.phase >= 1) 0.35f else 0.6f), cy - r * 0.46f)
            lineTo(p.cx + tilt + r * (if (p.phase >= 2) 0.05f else 0.4f), cy - r * 0.2f)
            lineTo(p.cx + tilt - r * 0.5f, cy - r * 0.22f)
            close()
        }
        drawPath(plate, lit)
        drawPath(plate, DARK, style = Stroke(2f))
        drawLine(DARK, Offset(p.cx + tilt - r * 0.2f, cy - r * 0.48f), Offset(p.cx + tilt - r * 0.05f, cy - r * 0.25f), 2f)

        // Head, slumped forward.
        val hx = p.cx + tilt * 1.4f; val hy = cy - r * 0.75f
        val head = floatArrayOf(-r * 0.4f, -r * 0.36f, r * 0.4f, -r * 0.38f, r * 0.37f, r * 0.12f, -r * 0.35f, r * 0.14f)
        slab(hx, hy, head, r * 0.12f, lit, side = DARK)
        // Hanging broken jaw.
        val jawOpen = if (hot) r * 0.2f else r * 0.07f + kotlin.math.abs(lurch) * r * 0.04f
        val jaw = floatArrayOf(-r * 0.24f, 0f, r * 0.2f, 0f, r * 0.16f, r * 0.12f, -r * 0.2f, r * 0.14f)
        drawRect(Color.Black, Offset(hx - r * 0.22f, hy + r * 0.12f), Size(r * 0.42f, jawOpen))
        if (hot) glow(Offset(hx, hy + r * 0.12f + jawOpen * 0.5f), r * 0.12f, red, 1.4f)
        slab(hx, hy + r * 0.13f + jawOpen, jaw, r * 0.06f, MID, side = DARK)
        for (k in 0 until 4) drawLine(Color(0xFFD8D0C0), Offset(hx - r * 0.18f + k * r * 0.11f, hy + r * 0.13f + jawOpen), Offset(hx - r * 0.18f + k * r * 0.11f, hy + r * 0.07f + jawOpen), 2f)
        // Eyes: one burning, one dead X.
        val eye = if (hot) 1f else 0.6f + 0.4f * sin(t * 5f) * (0.5f + 0.25f * p.phase)
        val e1 = Offset(hx - r * 0.13f, hy - r * 0.08f)
        glow(e1, r * 0.12f, red, eye * 1.5f)
        drawCircle(if (hot) Color.White else red, r * 0.06f, e1)
        val e2 = Offset(hx + r * 0.14f, hy - r * 0.08f)
        drawLine(DARK, Offset(e2.x - r * 0.06f, e2.y - r * 0.06f), Offset(e2.x + r * 0.06f, e2.y + r * 0.06f), 3f)
        drawLine(DARK, Offset(e2.x + r * 0.06f, e2.y - r * 0.06f), Offset(e2.x - r * 0.06f, e2.y + r * 0.06f), 3f)

        // Sparks and code drips, more each phase.
        for (k in 0 until 1 + p.phase * 2) {
            val f = ((t * 1.2f + k * 0.37f) % 1f)
            val sx = p.cx + tilt + (k % 3 - 1) * r * 0.4f
            drawCircle(ROT.copy(alpha = 0.9f * (1f - f)), r * 0.035f, Offset(sx, cy + r * 0.45f + f * r * 0.4f))
            val a = t * 13f + k * 1.9f
            val spx = p.cx + tilt + cos(k * 2.1f) * r * 0.55f
            val spy = cy - r * 0.3f + sin(k * 1.3f) * r * 0.25f
            drawLine(Color(0xFFFFE0A0).copy(alpha = 1f - f), Offset(spx, spy), Offset(spx + cos(a) * r * 0.14f, spy + sin(a) * r * 0.14f), 1.6f)
        }
    }
}
