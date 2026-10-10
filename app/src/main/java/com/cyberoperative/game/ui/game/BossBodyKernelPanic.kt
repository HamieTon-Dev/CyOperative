package com.cyberoperative.game.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.cos
import kotlin.math.sin

/**
 * KERNEL PANIC (classic, level 70): a crashing processor. A big floating CPU
 * package — gold pins along every edge, a dark die with an amber warning
 * triangle — that's cracking apart: the die splits along glowing fault lines,
 * sparks jump between pins and error hex scrolls across it. Each phase cracks
 * it further and lifts the die halves apart; charging overheats it white.
 */
internal object KernelPanicBody : BossBody {
    private val PKG_LIT = Color(0xFF3A3A30)
    private val PKG = Color(0xFF1C1C16)
    private val PKG_DARK = Color(0xFF0A0A08)
    private val PIN = Color(0xFFC9A64A)

    override val previewSpan: Float get() = 5f

    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius * 1.3f
        val t = p.time
        val amb = p.color
        val hot = p.winding
        val cy = p.cy + sin(t * 1.8f) * r * 0.05f
        val shake = if (hot) sin(t * 60f) * r * 0.02f else 0f
        val cx = p.cx + shake
        val half = r * 0.85f

        // Heat shimmer on the floor.
        drawOval(Brush.radialGradient(listOf(amb.copy(alpha = 0.3f), amb.copy(alpha = 0f)), center = Offset(cx, p.cy + r * 0.95f), radius = r * 1.3f), Offset(cx - r * 1.3f, p.cy + r * 0.6f), Size(r * 2.6f, r * 0.7f))

        // Package: square slab seen at a slight tilt, with pins on all sides.
        val pkg = floatArrayOf(-half, -half * 0.8f, half, -half * 0.8f, half, half * 0.8f, -half, half * 0.8f)
        for (k in 0 until 9) {
            val f = -half + (k + 0.5f) * (2 * half / 9)
            val pl = r * 0.18f
            drawLine(PIN, Offset(cx + f, cy - half * 0.8f), Offset(cx + f, cy - half * 0.8f - pl * 0.7f), r * 0.06f)
            drawLine(PIN, Offset(cx + f, cy + half * 0.8f), Offset(cx + f, cy + half * 0.8f + pl), r * 0.06f)
            val fy = (-half + (k + 0.5f) * (2 * half / 9)) * 0.8f
            drawLine(PIN, Offset(cx - half, cy + fy), Offset(cx - half - pl, cy + fy), r * 0.06f)
            drawLine(PIN, Offset(cx + half, cy + fy), Offset(cx + half + pl, cy + fy), r * 0.06f)
        }
        slab(cx, cy, pkg, r * 0.22f, if (p.hitFlash) Color.White else PKG, side = PKG_DARK)
        drawPath(polyPath(cx, cy, pkg), amb.copy(alpha = 0.6f), style = Stroke(2f))
        // Board traces on the package.
        for (k in 0 until 6) {
            val y = cy - half * 0.65f + k * half * 0.26f
            drawLine(PKG_LIT, Offset(cx - half * 0.92f, y), Offset(cx - half * 0.62f, y + half * 0.1f), 1.5f)
            drawLine(PKG_LIT, Offset(cx + half * 0.92f, y), Offset(cx + half * 0.62f, y - half * 0.1f), 1.5f)
        }

        // Die: two halves split by a fault line, drifting apart each phase.
        val gap = r * (0.02f + 0.05f * p.phase + if (hot) 0.04f else 0f)
        val d = half * 0.6f
        val hotc = if (hot) Color.White else lighter(amb, 0.4f)
        val left = Path().apply { moveTo(cx - d - gap, cy - d * 0.8f); lineTo(cx - d * 0.05f - gap, cy - d * 0.8f); lineTo(cx + d * 0.15f - gap, cy - d * 0.1f); lineTo(cx - d * 0.2f - gap, cy + d * 0.3f); lineTo(cx + d * 0.05f - gap, cy + d * 0.8f); lineTo(cx - d - gap, cy + d * 0.8f); close() }
        val right = Path().apply { moveTo(cx - d * 0.05f + gap, cy - d * 0.8f); lineTo(cx + d + gap, cy - d * 0.8f); lineTo(cx + d + gap, cy + d * 0.8f); lineTo(cx + d * 0.05f + gap, cy + d * 0.8f); lineTo(cx - d * 0.2f + gap, cy + d * 0.3f); lineTo(cx + d * 0.15f + gap, cy - d * 0.1f); close() }
        // Glow through the crack.
        drawLine(amb.copy(alpha = 0.5f), Offset(cx, cy - d * 0.8f), Offset(cx, cy + d * 0.8f), gap * 2f + r * 0.12f)
        for (path in listOf(left, right)) {
            drawPath(path, Brush.linearGradient(listOf(if (p.hitFlash) Color.White else PKG_LIT, PKG_DARK), Offset(cx - d, cy - d), Offset(cx + d, cy + d)))
            drawPath(path, amb.copy(alpha = 0.85f), style = Stroke(2f))
        }
        // Extra fault lines per phase.
        val faults = listOf(
            floatArrayOf(-0.9f, -0.4f, -0.5f, -0.2f, -0.6f, 0.2f),
            floatArrayOf(0.85f, 0.5f, 0.45f, 0.25f, 0.6f, -0.3f),
            floatArrayOf(-0.7f, 0.7f, -0.35f, 0.45f),
            floatArrayOf(0.4f, -0.75f, 0.7f, -0.45f)
        )
        for (k in 0 until (p.phase * 2).coerceAtMost(faults.size)) {
            val f = faults[k]
            val path = Path().apply {
                moveTo(cx + f[0] * d, cy + f[1] * d)
                var i = 2
                while (i < f.size) { lineTo(cx + f[i] * d, cy + f[i + 1] * d); i += 2 }
            }
            drawPath(path, hotc.copy(alpha = 0.7f + 0.3f * sin(t * 8f + k)), style = Stroke(2f))
        }
        // Warning triangle on the die.
        val wy = cy - d * 0.15f
        val ws = d * 0.55f
        val warn = if (hot) 1f else 0.55f + 0.45f * (if (sin(t * 5f) > 0f) 1f else 0f)
        val tri = Path().apply { moveTo(cx, wy - ws * 0.6f); lineTo(cx + ws * 0.6f, wy + ws * 0.45f); lineTo(cx - ws * 0.6f, wy + ws * 0.45f); close() }
        glow(Offset(cx, wy), ws * 0.5f, amb, warn)
        drawPath(tri, amb.copy(alpha = 0.25f * warn))
        drawPath(tri, amb.copy(alpha = warn), style = Stroke(3f))
        drawLine(amb.copy(alpha = warn), Offset(cx, wy - ws * 0.25f), Offset(cx, wy + ws * 0.15f), 3f)
        drawCircle(amb.copy(alpha = warn), 2.2f, Offset(cx, wy + ws * 0.3f))

        // Error hex scrolling across the bottom of the die.
        val codes = listOf("0x0D", "PANIC", "0xDEAD", "BSOD", "0xC0")
        val code = codes[((t * 1.5f).toInt()) % codes.size]
        drawContext.canvas.nativeCanvas.drawText(code, cx, cy + d * 0.62f, android.graphics.Paint().apply {
            color = android.graphics.Color.argb(220, (amb.red * 255).toInt(), (amb.green * 255).toInt(), (amb.blue * 255).toInt())
            textSize = r * 0.16f; isAntiAlias = true; typeface = android.graphics.Typeface.MONOSPACE; textAlign = android.graphics.Paint.Align.CENTER; isFakeBoldText = true
        })

        // Sparks arcing between pins.
        for (k in 0 until 2 + p.phase * 2) {
            val seed = ((t * 3f).toInt() * 7 + k * 13) % 36
            val side = seed % 4
            val f = ((seed / 4) / 9f - 0.5f) * 2f * half
            val (sx, sy) = when (side) {
                0 -> Pair(cx + f, cy - half * 0.8f - r * 0.12f)
                1 -> Pair(cx + f, cy + half * 0.8f + r * 0.15f)
                2 -> Pair(cx - half - r * 0.15f, cy + f * 0.8f)
                else -> Pair(cx + half + r * 0.15f, cy + f * 0.8f)
            }
            val a = t * 17f + k
            drawLine(Color(0xFFFFF0C0), Offset(sx, sy), Offset(sx + cos(a) * r * 0.15f, sy + sin(a) * r * 0.15f), 1.8f)
            drawLine(Color(0xFFFFF0C0), Offset(sx + cos(a) * r * 0.15f, sy + sin(a) * r * 0.15f), Offset(sx + cos(a + 1f) * r * 0.25f, sy + sin(a + 1f) * r * 0.22f), 1.4f)
        }
    }
}
