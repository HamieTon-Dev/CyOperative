package com.cyberoperative.game.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/**
 * Botnet Monarch (Boss Pack Beta 04, COMMANDER): a crimson command sphere with
 * heavy spikes, a blazing white-red core and uplink antennas, hovering over a
 * glowing dashed orbit track that its drones ride. The track and antennas
 * light up more each phase.
 */
internal object BotnetMonarchBody : BossBody {
    private val SHELL_LIT = Color(0xFF7A1A22)
    private val SHELL = Color(0xFF1A0508)

    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius
        val t = p.time
        val red = p.color
        val hot = p.winding
        val pulse = 0.5f + 0.5f * sin(t * (3f + p.phase))
        val bob = sin(t * 1.5f) * r * 0.05f
        val cy = p.cy + bob
        val ground = p.cy + r * 0.9f

        // Orbit track on the floor: a glowing dashed ellipse with chevrons.
        val tr = r * 2.1f
        drawOval(red.copy(alpha = 0.15f), Offset(p.cx - tr, p.cy - tr * 0.75f), Size(tr * 2f, tr * 1.5f), style = Stroke(14f))
        drawOval(red.copy(alpha = 0.75f), Offset(p.cx - tr, p.cy - tr * 0.75f), Size(tr * 2f, tr * 1.5f), style = Stroke(3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(26f, 14f), -t * 60f)))
        drawOval(Brush.radialGradient(listOf(red.copy(alpha = 0.35f), red.copy(alpha = 0f)), center = Offset(p.cx, ground), radius = r * 1.4f), Offset(p.cx - r * 1.4f, ground - r * 0.5f), Size(r * 2.8f, r * 1f))

        val lit = if (p.hitFlash) Color.White else Color(0xFFD03040)
        val dark = if (p.hitFlash) Color(0xFFDDDDDD) else Color(0xFF4A0A12)
        val spikes = 10 + p.phase * 2
        val grow = (1f + 0.07f * p.phase) * (if (hot) 1.12f else 1f)
        fun spike(i: Int, back: Boolean) {
            val a = i * (6.283f / spikes) + 0.31f
            if ((sin(a) < -0.1f) != back) return
            crystal(p.cx + cos(a) * r * 0.82f, cy + sin(a) * r * 0.76f, a, r * (if (i % 2 == 0) 0.55f else 0.4f) * grow, r * 0.3f, lit, dark, lighter(red, 0.4f))
        }
        for (i in 0 until spikes) spike(i, true)
        // Uplink antennas on top with blinking tips.
        for (s in listOf(-1f, 0f, 1f)) {
            val ax = p.cx + s * r * 0.4f
            val top = cy - r * (1.25f + (if (s == 0f) 0.25f else 0f) + 0.08f * p.phase)
            drawLine(Color(0xFF2A0A10), Offset(ax, cy - r * 0.7f), Offset(ax, top), r * 0.08f)
            val on = ((t * 4f).toInt() + (s * 2).toInt()) % 2 == 0 || hot
            drawCircle(red.copy(alpha = if (on) 0.9f else 0.3f), r * 0.08f, Offset(ax, top))
            if (on) drawCircle(red.copy(alpha = 0.25f), r * 0.2f, Offset(ax, top))
        }
        // Armoured sphere.
        val shell = if (p.hitFlash) lighter(SHELL_LIT, 0.5f) else SHELL_LIT
        drawCircle(Brush.radialGradient(listOf(shell, SHELL), center = Offset(p.cx - r * 0.3f, cy - r * 0.35f), radius = r * 1.15f), r * 0.9f, Offset(p.cx, cy))
        drawArc(red.copy(alpha = 0.55f + 0.3f * pulse), 200f, 140f, false, Offset(p.cx - r * 0.7f, cy - r * 0.7f), Size(r * 1.4f, r * 1.4f), style = Stroke(2.2f))
        drawArc(red.copy(alpha = 0.55f + 0.3f * pulse), 20f, 140f, false, Offset(p.cx - r * 0.7f, cy - r * 0.7f), Size(r * 1.4f, r * 1.4f), style = Stroke(2.2f))
        // Blazing core.
        val coreR = r * (0.36f + 0.03f * p.phase) * (if (hot) 1.15f else 1f)
        val cc = Offset(p.cx + cos(p.aim) * r * 0.04f, cy + sin(p.aim) * r * 0.03f)
        drawCircle(Color(0xFF14030A), coreR * 1.2f, cc)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFFF9090), red, red.copy(alpha = 0f)), center = cc, radius = coreR * 1.3f), coreR * 1.3f, cc)
        drawCircle(Color.White.copy(alpha = 0.75f + 0.25f * pulse), coreR * 0.45f, cc)
        for (i in 0 until spikes) spike(i, false)
    }
}
