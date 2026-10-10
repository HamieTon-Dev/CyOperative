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
 * Worm Queen (Boss Pack Alpha 04, SUMMONER): a huge dark armoured brood-sphere
 * bristling with long magenta spikes around a glowing pink core, sitting in a
 * spreading pool of corruption with veins and pixel debris, a few swarmlings
 * crawling over her. More spikes and a wider pool each phase.
 */
internal object WormQueenBody : BossBody {
    private val SHELL_LIT = Color(0xFF3A1A30)
    private val SHELL = Color(0xFF0E0610)

    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius
        val t = p.time
        val pink = p.color
        val hot = p.winding
        val pulse = 0.5f + 0.5f * sin(t * (2.2f + p.phase))
        val breathe = 1f + 0.03f * sin(t * 2f)
        val ground = p.cy + r * 0.85f

        // Corruption pool: magenta pixel floor with veins.
        val pool = r * (1.5f + 0.3f * p.phase)
        drawOval(Brush.radialGradient(listOf(pink.copy(alpha = 0.45f), pink.copy(alpha = 0.12f), pink.copy(alpha = 0f)), center = Offset(p.cx, ground), radius = pool), Offset(p.cx - pool, ground - pool * 0.4f), Size(pool * 2f, pool * 0.8f))
        for (i in 0 until 26 + p.phase * 10) {
            val a = i * 2.39f
            val d = pool * (0.3f + 0.65f * ((i * 0.618f) % 1f))
            val on = ((t * 3f).toInt() + i) % 4 != 0
            if (on) drawRect(pink.copy(alpha = 0.55f), Offset(p.cx + cos(a) * d, ground + sin(a) * d * 0.4f), Size(r * 0.07f, r * 0.05f))
        }
        for (i in 0 until 8) {
            val a = i * 0.785f + 0.3f
            drawLine(pink.copy(alpha = 0.5f), Offset(p.cx, ground), Offset(p.cx + cos(a) * pool * 0.95f, ground + sin(a) * pool * 0.38f), 1.6f)
        }

        // Spikes behind the sphere.
        val spikes = 11 + p.phase * 2
        val lit = if (p.hitFlash) Color.White else Color(0xFFB0204E)
        val dark = if (p.hitFlash) Color(0xFFDDDDDD) else Color(0xFF3A0818)
        val grow = (1f + 0.08f * p.phase) * (if (hot) 1.15f else 1f) * breathe
        fun spike(i: Int, back: Boolean) {
            val a = i * (6.283f / spikes) + 0.2f
            if ((sin(a) < -0.1f) != back) return
            val len = r * (if (i % 2 == 0) 0.62f else 0.45f) * grow
            crystal(p.cx + cos(a) * r * 0.85f, p.cy + sin(a) * r * 0.8f, a, len, r * 0.42f, lit, dark, pink)
        }
        for (i in 0 until spikes) spike(i, back = true)

        // Armoured sphere with plate seams.
        val shell = if (p.hitFlash) lighter(SHELL_LIT, 0.6f) else SHELL_LIT
        drawCircle(Brush.radialGradient(listOf(shell, SHELL), center = Offset(p.cx - r * 0.25f, p.cy - r * 0.3f), radius = r * 1.1f), r * 0.95f * breathe, Offset(p.cx, p.cy))
        for (k in 0 until 8) {
            val a = k * 0.785f
            drawLine(pink.copy(alpha = 0.3f), Offset(p.cx + cos(a) * r * 0.48f, p.cy + sin(a) * r * 0.48f), Offset(p.cx + cos(a) * r * 0.93f, p.cy + sin(a) * r * 0.93f), 1.8f)
        }
        drawCircle(pink.copy(alpha = 0.5f), r * 0.95f * breathe, Offset(p.cx, p.cy), style = Stroke(2f))
        // Glowing core.
        val coreR = r * (0.32f + 0.03f * p.phase) * (if (hot) 1.15f else 1f)
        val cc = Offset(p.cx + cos(p.aim) * r * 0.04f, p.cy + sin(p.aim) * r * 0.03f)
        drawCircle(Color(0xFF14040C), coreR * 1.25f, cc)
        drawCircle(Brush.radialGradient(listOf(Color.White, lighter(pink, 0.5f), pink, pink.copy(alpha = 0f)), center = cc, radius = coreR * 1.25f), coreR * 1.25f, cc)
        drawCircle(pink, coreR, cc, style = Stroke(3f))
        drawCircle(Color.White.copy(alpha = 0.7f + 0.3f * pulse), coreR * 0.35f, cc)

        // Front spikes.
        for (i in 0 until spikes) spike(i, back = false)

        // Swarmlings crawling over her.
        for (k in 0 until 2 + p.phase) {
            val a = t * (0.7f + 0.2f * k) + k * 2.1f
            val sx = p.cx + cos(a) * r * 0.95f
            val sy = p.cy + sin(a) * r * 0.75f
            for (j in 0 until 6) crystal(sx, sy, j * 1.047f + t, r * 0.12f, r * 0.08f, pink, darker(pink, 0.4f), Color.Transparent)
            drawCircle(darker(pink, 0.5f), r * 0.09f, Offset(sx, sy))
            drawCircle(Color(0xFFFFE6F0), r * 0.025f, Offset(sx - r * 0.03f, sy - r * 0.01f))
            drawCircle(Color(0xFFFFE6F0), r * 0.025f, Offset(sx + r * 0.03f, sy - r * 0.01f))
        }
    }
}
