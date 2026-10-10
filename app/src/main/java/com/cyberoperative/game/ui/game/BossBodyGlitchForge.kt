package com.cyberoperative.game.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.cos
import kotlin.math.sin

/**
 * Glitch Forge (Boss Pack Beta 02, SUMMONER): a big dark server-core cube with
 * neon magenta edges and glitch glyphs on its faces, a glowing target ring and
 * core on top, horizontal glitch slices tearing across it, and small hologram
 * cubes orbiting. More tearing and more holo cubes each phase.
 */
internal object GlitchForgeBody : BossBody {
    private val TOP = Color(0xFF2A1830)
    private val FACE = Color(0xFF150A18)
    private val FACE_DARK = Color(0xFF0A050C)

    /** A small translucent hologram cube. */
    private fun DrawScope.holoCube(cx: Float, gy: Float, s: Float, col: Color, a: Float) {
        block(cx, gy, s, s, s, col.copy(alpha = 0.3f * a), col.copy(alpha = 0.18f * a), lighter(col, 0.5f), 0.9f * a)
        drawRect(lighter(col, 0.6f).copy(alpha = 0.7f * a), Offset(cx - s * 0.15f, gy - s * 0.65f), Size(s * 0.3f, s * 0.12f))
    }

    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius
        val t = p.time
        val mag = p.color
        val hot = p.winding
        val pulse = 0.5f + 0.5f * sin(t * (3f + p.phase))
        val ground = p.cy + r * 0.75f

        // Glitch-grid floor under it: a lit square with cross lines.
        val fs = r * (1.5f + 0.2f * p.phase)
        drawRect(mag.copy(alpha = 0.12f + 0.06f * pulse), Offset(p.cx - fs, ground - fs * 0.4f), Size(fs * 2f, fs * 0.8f))
        drawRect(mag.copy(alpha = 0.6f), Offset(p.cx - fs, ground - fs * 0.4f), Size(fs * 2f, fs * 0.8f), style = Stroke(2f))
        for (k in -2..2) drawLine(mag.copy(alpha = 0.3f), Offset(p.cx + k * fs * 0.4f, ground - fs * 0.4f), Offset(p.cx + k * fs * 0.4f, ground + fs * 0.4f), 1.4f)

        // Holo cubes orbiting (behind first).
        val holos = 3 + p.phase
        fun holo(i: Int, front: Boolean) {
            val a = t * 0.7f + i * (6.283f / holos)
            if ((sin(a) >= 0f) != front) return
            val hx = p.cx + cos(a) * r * 1.55f
            val hy = ground - r * 0.3f + sin(a) * r * 0.55f + sin(t * 2f + i) * r * 0.08f
            holoCube(hx, hy, r * 0.32f, mag, 0.8f)
        }
        for (i in 0 until holos) holo(i, false)

        // The core cube.
        val w = r * 1.5f
        val h = r * 1.15f
        val top = if (p.hitFlash) lighter(TOP, 0.3f) else TOP
        val face = if (p.hitFlash) lighter(FACE, 0.3f) else FACE
        val frontTop = ground - h
        // Neon glow hugging the cube's silhouette.
        val dd = w * 0.55f
        for (k in 3 downTo 1) drawRect(mag.copy(alpha = 0.08f * k), Offset(p.cx - w / 2f - k * 4f, frontTop - dd - k * 4f), Size(w + k * 8f, h + dd + k * 8f), style = Stroke(5f))
        block(p.cx, ground, w, w, h, top, face, mag)
        drawRect(lighter(mag, 0.3f), Offset(p.cx - w / 2f, frontTop - dd), Size(w, h + dd), style = Stroke(3f))
        // Inner panels and glitch glyphs on the face.
        drawRect(FACE_DARK, Offset(p.cx - w * 0.38f, frontTop + h * 0.15f), Size(w * 0.76f, h * 0.7f))
        drawRect(mag.copy(alpha = 0.6f), Offset(p.cx - w * 0.38f, frontTop + h * 0.15f), Size(w * 0.76f, h * 0.7f), style = Stroke(1.8f))
        val glyphs = listOf("}", "#", "¦", "≡", "∆", "/")
        val gp = android.graphics.Paint().apply {
            color = android.graphics.Color.argb((200 * (0.6f + 0.4f * pulse)).toInt(), 255, 60, 170)
            textSize = r * 0.32f; isAntiAlias = true; typeface = android.graphics.Typeface.MONOSPACE
        }
        for (k in 0 until 4) {
            val gch = glyphs[((t * 3f).toInt() + k * 2) % glyphs.size]
            drawContext.canvas.nativeCanvas.drawText(gch, p.cx - w * 0.3f + k * w * 0.18f, frontTop + h * (0.45f + 0.25f * (k % 2)), gp)
        }
        // Side vents.
        for (s in listOf(-1f, 1f)) for (k in 0 until 3) drawRect(mag.copy(alpha = 0.5f + 0.4f * pulse), Offset(p.cx + s * w * 0.45f - w * 0.02f, frontTop + h * (0.25f + 0.2f * k)), Size(w * 0.04f, h * 0.08f))

        // Top: target ring and glowing core.
        val tc = Offset(p.cx, frontTop - w * 0.55f * 0.27f)
        val ringR = w * 0.3f * (if (hot) 1.15f else 1f)
        drawOval(mag.copy(alpha = 0.35f + (if (hot) 0.4f else 0f)), Offset(tc.x - ringR * 1.3f, tc.y - ringR * 0.45f), Size(ringR * 2.6f, ringR * 0.9f))
        drawOval(lighter(mag, 0.3f), Offset(tc.x - ringR, tc.y - ringR * 0.36f), Size(ringR * 2f, ringR * 0.72f), style = Stroke(3f))
        drawOval(mag, Offset(tc.x - ringR * 0.6f, tc.y - ringR * 0.22f), Size(ringR * 1.2f, ringR * 0.44f), style = Stroke(2f))
        drawCircle(Brush.radialGradient(listOf(Color.White, lighter(mag, 0.4f), mag.copy(alpha = 0f)), center = tc, radius = ringR * 0.5f), ringR * 0.5f, tc)
        if (hot) drawLine(lighter(mag, 0.5f).copy(alpha = 0.8f), tc, Offset(tc.x, tc.y - r * 1.4f), r * 0.1f)

        // Glitch slices tearing across the cube (RGB-split strips).
        val tears = 2 + p.phase * 2 + (if (hot) 2 else 0)
        for (k in 0 until tears) {
            val seed = ((t * 9f).toInt() * 7 + k * 13)
            if (seed % 3 == 0) continue
            val sy = frontTop - w * 0.27f + ((seed * 37) % 100) / 100f * (h + w * 0.27f)
            val sh = r * 0.06f
            val off = (((seed * 53) % 21) - 10) * r * 0.012f
            clipRect(p.cx - w * 0.6f, sy, p.cx + w * 0.6f, sy + sh) {
                drawRect(Color(0xFF00FFFF).copy(alpha = 0.35f), Offset(p.cx - w * 0.5f + off, sy), Size(w, sh))
                drawRect(mag.copy(alpha = 0.55f), Offset(p.cx - w * 0.5f - off, sy), Size(w, sh))
            }
        }
        for (i in 0 until holos) holo(i, true)
    }
}
