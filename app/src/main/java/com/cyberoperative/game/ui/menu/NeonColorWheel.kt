package com.cyberoperative.game.ui.menu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import com.cyberoperative.game.ui.common.hueOf
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * NEON skin colour wheel (owner, 2026-10-11): a ring of bright, fully saturated neon hues.
 * Tap or drag round the ring to pick; the centre glows in the chosen colour.
 */
object NeonColors {
    /** The neon colour at [hue] degrees: full saturation and brightness, so it always glows. */
    fun at(hue: Float): Long = Color.hsv(((hue % 360f) + 360f) % 360f, 1f, 1f).toArgb().toLong() and 0xFFFFFFFFL
}

@Composable
fun NeonColorWheel(color: Long, onPick: (Long) -> Unit, modifier: Modifier = Modifier) {
    val hues = List(13) { Color.hsv((it * 30f) % 360f, 1f, 1f) }
    fun pick(size: androidx.compose.ui.unit.IntSize, p: Offset) {
        val a = Math.toDegrees(atan2((p.y - size.height / 2f).toDouble(), (p.x - size.width / 2f).toDouble())).toFloat()
        onPick(NeonColors.at(a))
    }
    Canvas(
        modifier
            .pointerInput(Unit) { detectTapGestures { pick(size, it) } }
            .pointerInput(Unit) { detectDragGestures { change, _ -> pick(size, change.position) } }
    ) {
        val r = min(size.width, size.height) / 2f
        val ring = r * 0.22f
        val mid = r - ring / 2f - 2f
        drawCircle(Brush.sweepGradient(hues, center), mid, center, style = Stroke(ring))
        val c = Color(color)
        // The current pick: a marker on the ring and a glowing centre.
        val a = Math.toRadians(hueOf(color).toDouble())
        val m = Offset(center.x + cos(a).toFloat() * mid, center.y + sin(a).toFloat() * mid)
        drawCircle(Color.White, ring * 0.42f, m, style = Stroke(3f))
        drawCircle(com.cyberoperative.game.ui.common.SafeBrush.radialGradient(listOf(c.copy(alpha = 0.55f), Color.Transparent), center, mid * 0.75f), mid * 0.75f, center)
        drawCircle(c, mid * 0.32f, center)
        drawCircle(Color.White.copy(alpha = 0.6f), mid * 0.32f, center, style = Stroke(2f))
    }
}
