package com.cyberoperative.game.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.cyberoperative.game.ui.theme.Palette
import kotlin.math.sin
import kotlin.random.Random

/**
 * Animated "living" network backdrop (§48), in the spirit of CyOps TD's
 * living backgrounds: a faint circuit grid, packets flowing along traces and
 * server racks with blinking LEDs at the edges. Cool, low-alpha, slow — it is
 * atmosphere, never something that competes with what's on top of it.
 */
@Composable
fun LivingBackground(modifier: Modifier = Modifier, intensity: Float = 1f, accent: Color = Palette.Cyan) {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time = (it - start) / 1_000_000_000f }
    }
    val traces = remember { buildTraces(Random(7)) }
    Canvas(modifier) {
        drawLiving(time, traces, intensity, accent)
    }
}

/** A trace is a polyline of points in 0..1 unit space. */
class Trace(val xs: FloatArray, val ys: FloatArray, val speed: Float, val offset: Float) {
    val segLen: FloatArray
    val total: Float

    init {
        segLen = FloatArray(xs.size - 1) { i ->
            kotlin.math.abs(xs[i + 1] - xs[i]) + kotlin.math.abs(ys[i + 1] - ys[i])
        }
        total = segLen.sum()
    }
}

fun buildTraces(rng: Random, count: Int = 14): List<Trace> = List(count) {
    val n = 4 + rng.nextInt(4)
    val xs = FloatArray(n)
    val ys = FloatArray(n)
    var x = rng.nextFloat()
    var y = rng.nextFloat()
    for (i in 0 until n) {
        xs[i] = x; ys[i] = y
        if (i % 2 == 0) x = (x + (rng.nextFloat() - 0.5f) * 0.6f).coerceIn(0.02f, 0.98f)
        else y = (y + (rng.nextFloat() - 0.5f) * 0.5f).coerceIn(0.02f, 0.98f)
    }
    Trace(xs, ys, 0.05f + rng.nextFloat() * 0.08f, rng.nextFloat())
}

fun DrawScope.drawLiving(time: Float, traces: List<Trace>, intensity: Float, accent: Color) {
    val w = size.width
    val h = size.height
    // Grid
    val step = w / 9f
    val gridColor = Palette.GridLine.copy(alpha = 0.55f * intensity)
    var gx = 0f
    while (gx <= w) { drawLine(gridColor, Offset(gx, 0f), Offset(gx, h), 1f); gx += step }
    var gy = (time * 6f) % step
    while (gy <= h) { drawLine(gridColor, Offset(0f, gy), Offset(w, gy), 1f); gy += step }

    // Traces + flowing packets
    val traceColor = accent.copy(alpha = 0.10f * intensity)
    val packetColor = accent.copy(alpha = 0.55f * intensity)
    for (t in traces) {
        for (i in 0 until t.xs.size - 1) {
            drawLine(traceColor, Offset(t.xs[i] * w, t.ys[i] * h), Offset(t.xs[i + 1] * w, t.ys[i + 1] * h), 2f)
        }
        for (k in 0 until 2) {
            var d = ((time * t.speed + t.offset + k * 0.5f) % 1f) * t.total
            var i = 0
            while (i < t.segLen.size && d > t.segLen[i]) { d -= t.segLen[i]; i++ }
            if (i >= t.segLen.size) continue
            val f = if (t.segLen[i] > 0f) d / t.segLen[i] else 0f
            val px = (t.xs[i] + (t.xs[i + 1] - t.xs[i]) * f) * w
            val py = (t.ys[i] + (t.ys[i + 1] - t.ys[i]) * f) * h
            drawCircle(packetColor, 3.2f, Offset(px, py))
            drawCircle(accent.copy(alpha = 0.12f * intensity), 9f, Offset(px, py))
        }
    }

    // Server racks along the bottom edge with blinking LEDs.
    val rackW = w / 6f
    val rackH = h * 0.12f
    for (r in 0 until 6) {
        val left = r * rackW + rackW * 0.12f
        val top = h - rackH - 8f
        drawRoundRect(
            Palette.SurfaceRaised.copy(alpha = 0.6f * intensity),
            Offset(left, top), Size(rackW * 0.76f, rackH), CornerRadius(4f, 4f)
        )
        for (row in 0 until 4) {
            val ly = top + rackH * (0.18f + row * 0.2f)
            for (c in 0 until 3) {
                val phase = sin(time * (1.3f + r * 0.37f + c * 0.71f) + row * 1.7f + r)
                val on = phase > 0.2f
                val col = if ((r + row + c) % 5 == 0) Palette.ServerLedAmber else Palette.ServerLedGreen
                drawCircle(col.copy(alpha = (if (on) 0.75f else 0.12f) * intensity), 2.6f, Offset(left + rackW * (0.18f + c * 0.12f), ly))
            }
            drawLine(Palette.Divider.copy(alpha = 0.6f * intensity), Offset(left + rackW * 0.5f, ly), Offset(left + rackW * 0.68f, ly), 2f)
        }
    }

    // Slow scanline
    val scan = (time * 0.12f % 1f) * h
    drawRect(accent.copy(alpha = 0.035f * intensity), Offset(0f, scan), Size(w, 40f))
}
