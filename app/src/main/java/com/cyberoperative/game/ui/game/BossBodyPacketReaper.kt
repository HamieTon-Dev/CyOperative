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
 * Packet Reaper (Boss Pack Alpha 02, ASSASSIN): a hunched crimson assassin mech
 * with a red visor, back thrusters venting magenta flame and two huge glowing
 * packet scythes for arms. The blades sweep wider each phase; phase 3 adds a
 * third blade on its back.
 */
internal object PacketReaperBody : BossBody {
    private val ARMOR_LIT = Color(0xFF6A1428)
    private val ARMOR = Color(0xFF3A0A18)
    private val ARMOR_DARK = Color(0xFF14040A)
    private val VISOR = Color(0xFFFF3050)

    /** A glowing crescent blade from its pivot, sweeping [sweep] radians around [aim]. */
    private fun DrawScope.scythe(px: Float, py: Float, aim: Float, len: Float, sweep: Float, col: Color, intensity: Float) {
        val outer = Path()
        val inner = Path()
        val n = 14
        for (k in 0..n) {
            val q = k / n.toFloat()
            val a = aim - sweep / 2f + sweep * q
            val w = sin(q * Math.PI.toFloat())
            val ro = len * (0.55f + 0.45f * q)
            val ri = ro - len * 0.22f * w
            val ox = px + cos(a) * ro; val oy = py + sin(a) * ro * 0.8f
            val ix = px + cos(a) * ri; val iy = py + sin(a) * ri * 0.8f
            if (k == 0) { outer.moveTo(ox, oy); inner.moveTo(ix, iy) } else { outer.lineTo(ox, oy); inner.lineTo(ix, iy) }
        }
        val blade = Path().apply { addPath(outer) }
        val pts = ArrayList<Offset>()
        for (k in n downTo 0) {
            val q = k / n.toFloat()
            val a = aim - sweep / 2f + sweep * q
            val w = sin(q * Math.PI.toFloat())
            val ro = len * (0.55f + 0.45f * q)
            val ri = ro - len * 0.22f * w
            pts += Offset(px + cos(a) * ri, py + sin(a) * ri * 0.8f)
        }
        for (pt in pts) blade.lineTo(pt.x, pt.y)
        blade.close()
        drawPath(blade, col.copy(alpha = 0.25f * intensity), style = Stroke(10f))
        drawPath(blade, Brush.linearGradient(listOf(lighter(col, 0.6f), col, darker(col, 0.6f)), start = Offset(px, py), end = Offset(px + cos(aim) * len, py + sin(aim) * len)))
        drawPath(outer, Color.White.copy(alpha = 0.85f * intensity), style = Stroke(2.2f))
    }

    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius
        val t = p.time
        val pink = p.color
        val hot = p.winding
        val pulse = 0.5f + 0.5f * sin(t * (4f + p.phase))
        val bob = sin(t * 3f) * r * 0.05f
        val cy = p.cy + bob
        val ground = p.cy + r * 0.95f
        val lean = cos(p.aim) * 0.18f

        // Speed shadow and thruster glow on the floor.
        drawOval(Color.Black.copy(alpha = 0.4f), Offset(p.cx - r * 0.8f, ground - r * 0.2f), Size(r * 1.6f, r * 0.4f))
        drawOval(Brush.radialGradient(listOf(pink.copy(alpha = 0.35f), pink.copy(alpha = 0f)), center = Offset(p.cx, ground), radius = r * 1.4f), Offset(p.cx - r * 1.4f, ground - r * 0.5f), Size(r * 2.8f, r * 1f))

        // Thrusters venting magenta flame behind and below.
        for (s in listOf(-1f, 1f)) {
            val tx = p.cx + s * r * 0.35f
            val ty = cy + r * 0.35f
            val fl = r * (0.55f + 0.2f * sin(t * 20f + s)) * (if (hot) 1.5f else 1f)
            val flame = Path().apply { moveTo(tx - r * 0.12f, ty); lineTo(tx + s * r * 0.1f, ty + fl); lineTo(tx + r * 0.12f, ty); close() }
            drawPath(flame, Brush.verticalGradient(listOf(Color.White, pink, pink.copy(alpha = 0f)), startY = ty, endY = ty + fl))
            drawRect(ARMOR_DARK, Offset(tx - r * 0.13f, ty - r * 0.12f), Size(r * 0.26f, r * 0.16f))
        }

        // Back blade (phase 3).
        val sweep = 2.2f + 0.25f * p.phase + (if (hot) 0.5f else 0f)
        if (p.phase >= 2) scythe(p.cx, cy - r * 0.3f, -1.571f, r * 1.4f, 2.0f, pink, 0.8f)

        val lit = if (p.hitFlash) Color.White else ARMOR_LIT
        val mid = if (p.hitFlash) Color(0xFFDDDDDD) else ARMOR
        fun plate(pts: FloatArray, c: Color) {
            val path = polyPath(p.cx, cy, pts)
            drawPath(path, c)
            drawPath(path, pink.copy(alpha = 0.7f), style = Stroke(1.8f))
        }
        // Legs folded under (it hovers on its thrusters).
        plate(floatArrayOf(-r * 0.4f, r * 0.25f, -r * 0.15f, r * 0.25f, -r * 0.25f, r * 0.7f, -r * 0.5f, r * 0.6f), ARMOR_DARK)
        plate(floatArrayOf(r * 0.15f, r * 0.25f, r * 0.4f, r * 0.25f, r * 0.5f, r * 0.6f, r * 0.25f, r * 0.7f), ARMOR_DARK)
        // Hunched torso.
        plate(floatArrayOf(-r * 0.55f, -r * 0.35f, r * 0.55f, -r * 0.35f, r * 0.48f, r * 0.3f, -r * 0.48f, r * 0.3f), mid)
        plate(floatArrayOf(-r * 0.45f, -r * 0.3f, r * 0.45f, -r * 0.3f, r * 0.2f, r * 0.05f, -r * 0.2f, r * 0.05f), lit)
        // Core light on the chest.
        drawCircle(pink.copy(alpha = 0.6f + 0.4f * pulse), r * 0.09f, Offset(p.cx, cy - r * 0.08f))
        // Shoulders.
        for (s in listOf(-1f, 1f)) drawCircle(lit, r * 0.24f, Offset(p.cx + s * r * 0.58f, cy - r * 0.3f))
        // Head with a slanted visor, leaning toward its target.
        val hx = p.cx + lean * r
        val hy = cy - r * 0.62f
        drawPath(polyPath(hx, hy, floatArrayOf(-r * 0.3f, -r * 0.18f, r * 0.3f, -r * 0.18f, r * 0.24f, r * 0.2f, -r * 0.24f, r * 0.2f)), ARMOR_DARK)
        drawPath(polyPath(hx, hy, floatArrayOf(-r * 0.3f, -r * 0.18f, r * 0.3f, -r * 0.18f, r * 0.24f, r * 0.2f, -r * 0.24f, r * 0.2f)), pink.copy(alpha = 0.7f), style = Stroke(1.8f))
        val visorA = if (hot) 1f else 0.7f + 0.3f * pulse
        drawCircle(VISOR.copy(alpha = 0.25f * visorA), r * 0.22f, Offset(hx, hy))
        drawLine((if (hot) Color.White else VISOR).copy(alpha = visorA), Offset(hx - r * 0.2f, hy - r * 0.02f), Offset(hx + r * 0.2f, hy + r * 0.03f), r * 0.07f)

        // The two arm scythes, curling forward to either side.
        scythe(p.cx - r * 0.62f, cy - r * 0.25f, 3.6f + lean, r * 1.55f, sweep, pink, if (hot) 1f else 0.75f)
        scythe(p.cx + r * 0.62f, cy - r * 0.25f, -0.46f + lean, r * 1.55f, sweep, pink, if (hot) 1f else 0.75f)
    }
}
