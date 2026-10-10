package com.cyberoperative.game.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/**
 * Circuit Hydra (Boss Pack Alpha 03, AREA CONTROL): a serpent of dark crimson
 * armoured spheres, each with a glowing orange core ring, trailing behind a big
 * beam head with a blazing core and armoured jaw plates. The body is the
 * engine's trail (so it really snakes across the arena); previews draw an S-curve.
 */
internal object CircuitHydraBody : BossBody {
    private val SHELL_LIT = Color(0xFF5A1A24)
    private val SHELL = Color(0xFF14050A)
    private val CORE = Color(0xFFFF7A2A)

    /** One armoured sphere with a glowing core ring; [glow] 0..1. */
    fun DrawScope.segment(x: Float, y: Float, s: Float, red: Color, glow: Float, flash: Boolean) {
        drawOval(Color.Black.copy(alpha = 0.35f), Offset(x - s * 0.9f, y + s * 0.65f), Size(s * 1.8f, s * 0.5f))
        drawCircle(Brush.radialGradient(listOf(if (flash) Color.White else SHELL_LIT, SHELL), center = Offset(x - s * 0.3f, y - s * 0.35f), radius = s * 1.2f), s, Offset(x, y))
        drawCircle(red.copy(alpha = 0.6f), s, Offset(x, y), style = Stroke(2f))
        // Plate bands.
        drawArc(red.copy(alpha = 0.4f), 200f, 140f, false, Offset(x - s * 0.8f, y - s * 0.8f), Size(s * 1.6f, s * 1.6f), style = Stroke(1.6f))
        // Core ring on top.
        val c = Offset(x, y - s * 0.15f)
        drawCircle(CORE.copy(alpha = 0.25f * glow), s * 0.55f, c)
        drawCircle(CORE.copy(alpha = 0.6f + 0.4f * glow), s * 0.36f, c, style = Stroke(s * 0.12f))
        drawCircle(Color(0xFFFFE0B0).copy(alpha = 0.5f + 0.5f * glow), s * 0.14f, c)
    }

    /** The big head: segment sphere plus jaw plates and a blazing core aimed at you. */
    fun DrawScope.head(x: Float, y: Float, r: Float, red: Color, aim: Float, hot: Boolean, flash: Boolean, t: Float) {
        for (s in listOf(-1f, 1f)) {
            val a = aim + s * 0.55f
            crystal(x + cos(a) * r * 0.7f, y + sin(a) * r * 0.6f, a, r * 0.55f, r * 0.35f, Color(0xFF7A1A24), Color(0xFF2A0A10), red)
        }
        segment(x, y, r, red, if (hot) 1f else 0.6f + 0.4f * sin(t * 4f), flash)
        val cc = Offset(x + cos(aim) * r * 0.3f, y + sin(aim) * r * 0.25f)
        val cr = r * 0.32f * (if (hot) 1.2f else 1f)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFFFB070), CORE, CORE.copy(alpha = 0f)), center = cc, radius = cr * 1.6f), cr * 1.6f, cc)
        if (hot) drawLine(CORE.copy(alpha = 0.6f), cc, Offset(cc.x + cos(aim) * r * 1.6f, cc.y + sin(aim) * r * 1.6f), r * 0.12f)
    }

    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius
        val t = p.time
        val red = p.color
        // Body: engine trail (lifted) or a preview S-curve.
        val pts = if (p.trail.size >= 4) p.trail else FloatArray(20) { k ->
            val i = k / 2
            if (k % 2 == 0) p.cx + sin(i * 0.7f + t) * r * 0.9f - i * r * 0.08f else p.cy + i * r * 0.42f - r * 0.1f
        }
        val n = pts.size / 2
        for (i in n - 1 downTo 1) {
            val s = r * (0.74f - 0.3f * i / n) * (1f + 0.08f * p.phase)
            val glow = 0.5f + 0.5f * sin(t * 5f - i * 0.6f)
            segment(pts[i * 2], pts[i * 2 + 1], s, red, glow, p.hitFlash)
        }
        head(p.cx, p.cy, r * 0.95f, red, p.aim, p.winding, p.hitFlash, t)
    }
}
