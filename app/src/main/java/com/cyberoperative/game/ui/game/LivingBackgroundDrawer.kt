package com.cyberoperative.game.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import com.cyberoperative.game.data.LivingBackground
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * CyOps TD's living backgrounds, ported to the arena floor. Same effects,
 * tints, intensities and speeds; geometry adapted to a portrait arena.
 * Drawn before obstacles and entities so everything paints over it.
 * [load] is 0..1 (how busy the arena is) for LATTICE / HEATMAP.
 */
object LivingBackgroundDrawer {

    private val binary = arrayOf("0", "1")

    fun DrawScope.drawLivingBackground(bg: LivingBackground, w: Float, h: Float, time: Float, load: Float, focusX: Float, focusY: Float) {
        if (bg == LivingBackground.NONE) return
        val tint = Color(bg.tint)
        val peak = bg.intensity / 255f
        when (bg) {
            LivingBackground.NONE -> Unit
            LivingBackground.DRIFT -> {
                for (i in 0 until 22) {
                    val span = w + 420f
                    val x = ((i * 137f) + time * bg.speed * span) % span - 210f
                    val y = (i * 311f) % h
                    val a = peak * (0.4f + 0.6f * abs(sin(i * 1.7f)))
                    drawLine(tint.copy(alpha = a), Offset(x, y), Offset(x + 150f, y + 84f), 2f)
                }
            }
            LivingBackground.LATTICE -> {
                var gx = 60f
                var index = 0
                while (gx < w) {
                    var gy = 60f
                    while (gy < h) {
                        val phase = sin(time * bg.speed * 6.28f + index * 0.7f)
                        val radius = 2.2f + 2.4f * (0.5f + 0.5f * phase) * (0.6f + load)
                        drawCircle(tint.copy(alpha = peak * (0.45f + 0.55f * (0.5f + 0.5f * phase))), radius, Offset(gx, gy))
                        gy += 100f
                        index++
                    }
                    gx += 100f
                }
                var ly = 60f
                while (ly < h) {
                    drawLine(tint.copy(alpha = peak * 0.35f), Offset(60f, ly), Offset(w - 60f, ly), 1f)
                    ly += 100f
                }
            }
            LivingBackground.AURORA -> {
                for (i in 0 until 6) {
                    val drift = sin(time * bg.speed * 6.28f + i * 1.3f)
                    val y = h * (0.1f + i * 0.16f) + drift * 46f
                    drawLine(tint.copy(alpha = peak * (0.35f + 0.4f * (0.5f + 0.5f * drift))), Offset(0f, y), Offset(w, y + drift * 30f), 86f)
                }
            }
            LivingBackground.RAINFALL -> {
                val canvas = drawContext.canvas.nativeCanvas
                val paint = rainPaint
                paint.color = android.graphics.Color.argb(255, ((bg.tint shr 16) and 0xFF).toInt(), ((bg.tint shr 8) and 0xFF).toInt(), (bg.tint and 0xFF).toInt())
                for (col in 0 until 16) {
                    val x = 24f + col * 46f
                    val speed = 90f + (col % 5) * 46f
                    val head = (time * speed * bg.speed * 4f + col * 73f) % (h + 190f)
                    for (n in 0 until 6) {
                        val y = head - n * 26f
                        if (y < 0f || y > h) continue
                        paint.alpha = (bg.intensity * (1f - n / 6f)).toInt().coerceAtLeast(0)
                        canvas.drawText(binary[(col + n + (time * 3).toInt()) % 2], x, y, paint)
                    }
                }
            }
            LivingBackground.PULSE -> {
                for (i in 0 until 5) {
                    val phase = (time * bg.speed + i / 5f) % 1f
                    drawCircle(tint.copy(alpha = peak * (1f - phase)), 60f + phase * 1100f, Offset(focusX, focusY), style = Stroke(2f))
                }
            }
            LivingBackground.ORBIT -> {
                val cx = w / 2f
                val cy = h / 2f
                for (i in 0 until 4) {
                    val rx = 120f + i * 90f
                    val ry = 200f + i * 140f
                    drawOval(tint.copy(alpha = peak * 0.35f), Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2), style = Stroke(1f))
                    val direction = if (i % 2 == 0) 1f else -1f
                    val angle = direction * time * bg.speed * 6.28f * (1.6f - i * 0.2f) + i * 1.9f
                    for (t in 0 until 6) {
                        val a = angle - direction * t * 0.05f
                        drawCircle(tint.copy(alpha = peak * (1f - t / 6f)), 4f - t * 0.5f, Offset(cx + cos(a) * rx, cy + sin(a) * ry))
                    }
                }
            }
            LivingBackground.HEATMAP -> {
                val cell = 80f
                var row = 0
                var y = 0f
                while (y < h) {
                    var col = 0
                    var x = 0f
                    while (x < w) {
                        val wave = sin(time * bg.speed * 6.28f + col * 0.55f) * sin(time * bg.speed * 3.9f + row * 0.8f + col * 0.2f)
                        val warmth = (0.5f + 0.5f * wave) * (0.7f + 0.3f * load)
                        drawRect(tint.copy(alpha = peak * 0.8f * warmth), Offset(x + 2f, y + 2f), Size(cell - 4f, cell - 4f))
                        x += cell
                        col++
                    }
                    y += cell
                    row++
                }
            }
        }
    }

    private val rainPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = 16f
        typeface = android.graphics.Typeface.MONOSPACE
    }
}
