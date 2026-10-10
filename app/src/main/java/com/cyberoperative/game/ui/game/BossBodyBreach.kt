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
 * BREACH (classic, level 10): a hovering breaching pod. A heavy hexagonal gunmetal
 * hull on two downward thrusters, a red visor slit, and a big rotating drill ram
 * that swings to point at you. Armour cracks open (glowing seams) as it weakens;
 * charging heats the drill white.
 */
internal object BreachBody : BossBody {
    private val LIT = Color(0xFF4A4F5A)
    private val MID = Color(0xFF262A32)
    private val DARK = Color(0xFF0E1015)

    override val previewSpan: Float get() = 5f

    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius * 1.4f
        val t = p.time
        val red = p.color
        val hot = p.winding
        val bob = sin(t * 2.2f) * r * 0.05f
        val cy = p.cy + bob
        val ground = p.cy + r * 0.9f

        // Warning chevrons on the floor under it.
        drawOval(Brush.radialGradient(listOf(red.copy(alpha = 0.3f), red.copy(alpha = 0f)), center = Offset(p.cx, ground), radius = r * 1.3f), Offset(p.cx - r * 1.3f, ground - r * 0.4f), Size(r * 2.6f, r * 0.8f))
        for (k in -2..2) {
            val x = p.cx + k * r * 0.38f
            val ch = Path().apply { moveTo(x - r * 0.12f, ground - r * 0.08f); lineTo(x, ground + r * 0.04f); lineTo(x + r * 0.12f, ground - r * 0.08f) }
            drawPath(ch, red.copy(alpha = 0.35f + 0.25f * sin(t * 4f + k)), style = Stroke(3f))
        }

        // Drill ram: behind the hull when aimed up, in front when aimed down.
        val front = sin(p.aim) > -0.2f
        if (!front) drill(p.cx, cy, p.aim, r, red, hot, t)

        // Thrusters with flames.
        for (s in listOf(-1f, 1f)) {
            val tx = p.cx + s * r * 0.78f
            val ty = cy + r * 0.35f
            val flame = r * (0.35f + 0.12f * sin(t * 30f + s)) * (if (hot) 1.5f else 1f)
            drawOval(Brush.verticalGradient(listOf(Color.White, red, red.copy(alpha = 0f)), ty, ty + flame), Offset(tx - r * 0.1f, ty), Size(r * 0.2f, flame))
            slab(tx, ty - r * 0.12f, floatArrayOf(-r * 0.2f, -r * 0.18f, r * 0.2f, -r * 0.18f, r * 0.16f, r * 0.14f, -r * 0.16f, r * 0.14f), r * 0.12f, if (p.hitFlash) Color.White else MID, side = DARK)
        }

        // Hull: wide hexagonal slab.
        val hull = floatArrayOf(-r * 0.95f, -r * 0.05f, -r * 0.6f, -r * 0.55f, r * 0.6f, -r * 0.55f, r * 0.95f, -r * 0.05f, r * 0.6f, r * 0.4f, -r * 0.6f, r * 0.4f)
        slab(p.cx, cy, hull, r * 0.28f, if (p.hitFlash) Color.White else MID, side = DARK)
        // Top plate highlight.
        drawPath(polyPath(p.cx, cy, floatArrayOf(-r * 0.6f, -r * 0.5f, r * 0.6f, -r * 0.5f, r * 0.75f, -r * 0.2f, -r * 0.75f, -r * 0.2f)), if (p.hitFlash) Color.White else LIT)
        // Red trim and rivets.
        drawPath(polyPath(p.cx, cy, hull), red.copy(alpha = 0.75f), style = Stroke(2.2f))
        for (k in -3..3) drawCircle(DARK, r * 0.035f, Offset(p.cx + k * r * 0.22f, cy + r * 0.28f))

        // Visor slit.
        val vis = if (hot) 1f else 0.7f + 0.3f * sin(t * 3f)
        drawRect(DARK, Offset(p.cx - r * 0.5f, cy - r * 0.15f), Size(r, r * 0.16f))
        drawRect(red.copy(alpha = vis), Offset(p.cx - r * 0.45f, cy - r * 0.11f), Size(r * 0.9f, r * 0.08f))
        val scan = p.cx + sin(t * 2.5f) * r * 0.38f
        drawCircle(Color.White.copy(alpha = 0.8f * vis), r * 0.05f, Offset(scan, cy - r * 0.07f))
        glow(Offset(scan, cy - r * 0.07f), r * 0.12f, red, vis)

        // Damage: glowing cracks, more per phase.
        val cracks = listOf(
            floatArrayOf(-0.7f, -0.3f, -0.45f, -0.1f, -0.5f, 0.15f),
            floatArrayOf(0.55f, -0.45f, 0.4f, -0.25f, 0.6f, 0.05f),
            floatArrayOf(-0.1f, 0.25f, 0.1f, 0.05f, 0.25f, 0.3f),
            floatArrayOf(-0.85f, 0.0f, -0.65f, 0.2f)
        )
        for (k in 0 until (p.phase * 2).coerceAtMost(cracks.size)) {
            val c = cracks[k]
            val path = Path().apply {
                moveTo(p.cx + c[0] * r, cy + c[1] * r)
                var i = 2
                while (i < c.size) { lineTo(p.cx + c[i] * r, cy + c[i + 1] * r); i += 2 }
            }
            drawPath(path, red.copy(alpha = 0.5f + 0.4f * sin(t * 6f + k)), style = Stroke(2.4f))
        }
        if (p.phase >= 2) for (k in 0 until 3) {
            val a = t * 7f + k * 2.1f
            drawLine(Color(0xFFFFD0A0), Offset(p.cx + cos(a) * r * 0.5f, cy - r * 0.3f), Offset(p.cx + cos(a) * r * 0.62f, cy - r * 0.45f), 1.6f)
        }

        if (front) drill(p.cx, cy, p.aim, r, red, hot, t)
    }

    /** The rotating drill ram, from the hull toward [aim]. */
    private fun DrawScope.drill(cx: Float, cy: Float, aim: Float, r: Float, red: Color, hot: Boolean, t: Float) {
        val bx = cx + cos(aim) * r * 0.55f
        val by = cy + sin(aim) * r * 0.35f
        val len = r * (1.25f + if (hot) 0.2f else 0f)
        val w = r * 0.55f
        val tx = bx + cos(aim) * len
        val ty = by + sin(aim) * len * 0.9f
        val px = -sin(aim) * w / 2f
        val py = cos(aim) * w / 2f
        val cone = Path().apply { moveTo(bx + px, by + py); lineTo(tx, ty); lineTo(bx - px, by - py); close() }
        drawPath(cone, Brush.linearGradient(listOf(if (hot) Color.White else LIT, MID, DARK), Offset(bx + px, by + py), Offset(bx - px, by - py)))
        // Spiral flutes sliding toward the tip.
        for (k in 0 until 5) {
            val f = ((k / 5f + t * (if (hot) 3f else 1.2f)) % 1f)
            val ax = bx + (tx - bx) * f; val ay = by + (ty - by) * f
            val ww = 1f - f
            drawLine(red.copy(alpha = 0.6f + (if (hot) 0.4f else 0f)), Offset(ax + px * ww, ay + py * ww), Offset(ax - px * ww * 0.3f + cos(aim) * r * 0.1f, ay - py * ww * 0.3f + sin(aim) * r * 0.1f), 2f)
        }
        drawPath(cone, Color(0xFF05070D), style = Stroke(2f))
        // Collar.
        drawCircle(DARK, w * 0.55f, Offset(bx, by))
        drawCircle(red.copy(alpha = 0.8f), w * 0.55f, Offset(bx, by), style = Stroke(2.5f))
        if (hot) glow(Offset(tx, ty), r * 0.2f, red, 0.8f)
    }
}
