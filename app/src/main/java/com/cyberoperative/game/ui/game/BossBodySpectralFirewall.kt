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
 * Spectral Firewall (Boss Pack Gamma 03, owner's card in
 * docs/boss-concepts/spectral_firewall_card.webp): a dark armoured sphere
 * bristling with red-black spikes around a blazing red core eye, circled by a
 * rotating ring of burning firewall bricks with gaps. The ring fills in and
 * burns hotter each phase.
 */
internal object SpectralFirewallBody : BossBody {
    private val SHELL_LIT = Color(0xFF3A2228)
    private val SHELL = Color(0xFF0A0508)
    private val SPIKE_LIT = Color(0xFF7A1820)
    private val SPIKE_DARK = Color(0xFF1A0507)
    private val CORE = Color(0xFFFF2A2A)
    private val BRICK_HOT = Color(0xFFFFD45A)
    private val BRICK = Color(0xFFFF7A1A)
    private val BRICK_DEEP = Color(0xFFD9301A)

    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius
        val t = p.time
        val fire = p.color
        val hot = p.winding
        val pulse = 0.5f + 0.5f * sin(t * (3f + p.phase * 1.5f))
        val ground = p.cy + r * 0.9f
        val spin = t * (0.5f + 0.15f * p.phase)

        // Heat glow and a scorched ring on the floor.
        drawOval(
            Brush.radialGradient(listOf(fire.copy(alpha = 0.4f), fire.copy(alpha = 0f)), center = Offset(p.cx, ground), radius = r * 1.9f),
            Offset(p.cx - r * 1.9f, ground - r * 0.7f), Size(r * 3.8f, r * 1.4f)
        )
        drawOval(Color(0xFFFF3B1A).copy(alpha = 0.55f), Offset(p.cx - r * 1.45f, ground - r * 0.52f), Size(r * 2.9f, r * 1.04f), style = Stroke(2.2f))

        // Firewall ring: curved burning wall slabs orbiting with gaps; behind first, in front after the sphere.
        val slots = 9
        val filled = when (p.phase) { 0 -> 6; 1 -> 7; else -> 8 }
        val ringR = r * (1.6f + (if (hot) 0.12f else 0f))
        val squash = 0.36f
        fun brick(i: Int, front: Boolean) {
            if (i % slots >= filled) return
            val a0 = spin + i * (6.283f / slots)
            val a1 = a0 + (6.283f / slots) * 0.78f
            val mid = (a0 + a1) / 2f
            if ((sin(mid) >= 0f) != front) return
            val h = r * 0.5f * (0.85f + 0.15f * (sin(mid) + 1f) / 2f)
            val flick = 0.85f + 0.15f * sin(t * 11f + i * 2.3f)
            val n = 7
            val slab = Path()
            for (k in 0..n) {
                val a = a0 + (a1 - a0) * k / n
                val x = p.cx + cos(a) * ringR
                val y = ground - r * 0.1f + sin(a) * ringR * squash - h
                if (k == 0) slab.moveTo(x, y) else slab.lineTo(x, y)
            }
            for (k in n downTo 0) {
                val a = a0 + (a1 - a0) * k / n
                slab.lineTo(p.cx + cos(a) * ringR, ground - r * 0.1f + sin(a) * ringR * squash)
            }
            slab.close()
            val gy = ground - r * 0.1f + sin(mid) * ringR * squash
            drawPath(slab, Brush.verticalGradient(listOf(BRICK_HOT.copy(alpha = 0.95f * flick), BRICK.copy(alpha = 0.92f), BRICK_DEEP.copy(alpha = 0.88f)), startY = gy - h - r * 0.1f, endY = gy + r * 0.1f))
            drawPath(slab, Color(0xFFFFE7A0).copy(alpha = 0.5f * flick), style = Stroke(1.6f))
            // Brick seams and flames along the top edge.
            for (k in 1 until 3) {
                val a = a0 + (a1 - a0) * k / 3f
                val x = p.cx + cos(a) * ringR
                val yb = ground - r * 0.1f + sin(a) * ringR * squash
                drawLine(BRICK_DEEP.copy(alpha = 0.55f), Offset(x, yb - h), Offset(x, yb), 1.3f)
            }
            for (k in 0 until 3) {
                val a = a0 + (a1 - a0) * (k + 0.5f) / 3f
                val x = p.cx + cos(a) * ringR
                val yt = ground - r * 0.1f + sin(a) * ringR * squash - h
                val fh = h * (0.35f + 0.4f * sin(t * 9f + i * 1.7f + k * 2.1f).coerceAtLeast(0f)) * (if (hot) 1.5f else 1f)
                val fw = r * 0.12f
                val flame = Path().apply {
                    moveTo(x - fw, yt)
                    quadraticTo(x - fw * 0.6f, yt - fh * 0.6f, x + sin(t * 6f + i + k) * fw * 0.5f, yt - fh)
                    quadraticTo(x + fw * 0.6f, yt - fh * 0.6f, x + fw, yt)
                    close()
                }
                drawPath(flame, BRICK.copy(alpha = 0.75f))
                drawPath(flame, BRICK_HOT.copy(alpha = 0.4f), style = Stroke(1.2f))
            }
        }
        for (i in 0 until slots) brick(i, front = false)

        // Back spikes (behind the sphere), spinning slowly with it.
        val spikes = 12
        val grow = (1f + 0.08f * p.phase) * (if (hot) 1.1f else 1f)
        val lit = if (p.hitFlash) Color.White else SPIKE_LIT
        val dark = if (p.hitFlash) Color(0xFFDDDDDD) else SPIKE_DARK
        val edge = fire.copy(alpha = 0.85f)
        fun spike(i: Int, back: Boolean) {
            val a = spin * 0.6f + i * (6.283f / spikes)
            val isBack = sin(a) < -0.2f
            if (isBack != back) return
            val bx = p.cx + cos(a) * r * 0.7f
            val by = p.cy + sin(a) * r * 0.62f
            val len = r * (if (i % 3 == 0) 0.75f else 0.55f) * grow
            crystal(bx, by, a, len, r * 0.34f, lit, dark, edge)
        }
        for (i in 0 until spikes) spike(i, back = true)

        // Armoured sphere with glowing panel seams.
        val shellLit = if (p.hitFlash) lighter(SHELL_LIT, 0.6f) else SHELL_LIT
        drawCircle(
            Brush.radialGradient(listOf(shellLit, SHELL), center = Offset(p.cx - r * 0.25f, p.cy - r * 0.3f), radius = r * 1.1f),
            r * 0.8f, Offset(p.cx, p.cy)
        )
        val seam = fire.copy(alpha = 0.45f + 0.35f * pulse)
        drawArc(seam, 200f, 140f, false, Offset(p.cx - r * 0.62f, p.cy - r * 0.62f), Size(r * 1.24f, r * 1.24f), style = Stroke(2f))
        drawArc(seam, 20f, 140f, false, Offset(p.cx - r * 0.62f, p.cy - r * 0.62f), Size(r * 1.24f, r * 1.24f), style = Stroke(2f))
        for (k in 0 until 6) {
            val a = spin * 0.6f + k * 1.047f
            drawLine(seam.copy(alpha = seam.alpha * 0.7f), Offset(p.cx + cos(a) * r * 0.45f, p.cy + sin(a) * r * 0.45f), Offset(p.cx + cos(a) * r * 0.78f, p.cy + sin(a) * r * 0.78f), 1.6f)
        }
        // Small side lights.
        for (s in listOf(-1f, 1f)) {
            val lc = Offset(p.cx + s * r * 0.5f, p.cy - r * 0.05f)
            drawCircle(Color(0xFF1A0507), r * 0.12f, lc)
            drawCircle(CORE.copy(alpha = 0.7f + 0.3f * pulse), r * 0.08f, lc)
        }
        // Core eye: dark socket, glowing ring, white-hot centre.
        val coreR = r * (0.3f + 0.04f * p.phase) * (if (hot) 1.12f else 1f)
        val cc = Offset(p.cx + cos(p.aim) * r * 0.04f, p.cy + sin(p.aim) * r * 0.03f)
        drawCircle(Color(0xFF120306), coreR * 1.25f, cc)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFFF8A80), CORE, CORE.copy(alpha = 0f)), center = cc, radius = coreR * 1.25f), coreR * 1.25f, cc)
        drawCircle(CORE.copy(alpha = 0.9f), coreR, cc, style = Stroke(2.5f))
        drawCircle(Color.White.copy(alpha = if (hot) 1f else 0.85f), coreR * 0.38f, cc)

        // Front spikes.
        for (i in 0 until spikes) spike(i, back = false)
        for (i in 0 until slots) brick(i, front = true)

        // Embers rising off it (more each phase).
        val embers = 10 + p.phase * 8
        for (i in 0 until embers) {
            val k = (t * (0.4f + (i % 4) * 0.08f) + i * 0.173f) % 1f
            val ex = p.cx + sin(i * 2.7f + t * 0.5f) * r * 1.3f
            val ey = ground - k * r * 2.2f
            drawCircle((if (i % 3 == 0) BRICK_HOT else BRICK).copy(alpha = (1f - k) * 0.85f), r * 0.025f * (1f + (i % 3)), Offset(ex, ey))
        }
    }
}
