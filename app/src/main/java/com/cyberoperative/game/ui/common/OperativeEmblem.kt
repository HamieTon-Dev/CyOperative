package com.cyberoperative.game.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import com.cyberoperative.game.data.OperativeSkin
import com.cyberoperative.game.data.OperativeSkins
import kotlin.math.cos
import kotlin.math.sin

/**
 * The app-icon emblem for the main menu: a hooded operative with a headset,
 * framed by a neon hexagon and circuit traces. The face shield is the player's
 * equipped skin, so the emblem changes with what they wear. Pure vectors on a
 * 100-unit grid; scales to any size.
 */
@Composable
fun OperativeEmblem(skin: OperativeSkin, modifier: Modifier = Modifier, animate: Boolean = true) {
    var time by remember { mutableFloatStateOf(0f) }
    if (animate) {
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { time = (it - start) / 1e9f }
        }
    }
    Canvas(modifier.aspectRatio(1f)) {
        val k = size.width / 100f
        scale(k, k, Offset.Zero) { drawEmblem(skin, time) }
    }
}

private val Cyan = Color(0xFF22E6FF)
private val NeonBlue = Color(0xFF3FA8FF)
private val Mint = Color(0xFF2BF5A0)

private fun path(vararg p: Float, closed: Boolean = true) = Path().apply {
    moveTo(p[0], p[1])
    var i = 2
    while (i < p.size) { lineTo(p[i], p[i + 1]); i += 2 }
    if (closed) close()
}

private fun hexagon(cx: Float, cy: Float, r: Float) = Path().apply {
    for (i in 0 until 6) {
        val a = Math.toRadians(-90.0 + 60.0 * i)
        val x = cx + r * cos(a).toFloat()
        val y = cy + r * sin(a).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private val outerHex = hexagon(50f, 50f, 44f)
private val innerHex = hexagon(50f, 50f, 39.5f)

private val hood = path(50f, 12f, 67f, 21f, 75f, 33f, 79f, 54f, 75f, 68f, 50f, 74f, 27f, 68f, 24f, 52f, 28f, 33f)
private val hoodFacet = path(50f, 12f, 61f, 18f, 52f, 25f, 40f, 26f)
private val hoodOpening = path(51f, 25f, 66f, 31f, 71f, 47f, 69f, 63f, 59f, 71f, 44f, 71f, 34f, 63f, 32f, 47f, 37f, 31f)
private val shoulders = path(8f, 80f, 24f, 66f, 40f, 69f, 51f, 79f, 61f, 69f, 77f, 66f, 92f, 80f, 92f, 100f, 8f, 100f)
private val strap = path(20f, 72f, 28f, 67f, 46f, 73f, 45f, 80f, 27f, 75f)
private val collarTab = path(63f, 71f, 75f, 68f, 77.5f, 76f, 65.5f, 79f)

private val traces = listOf(
    path(10f, 37f, 4f, 43f, 4f, 55f, closed = false),
    path(16f, 37f, 16f, 48f, 21f, 54f, 21f, 61f, closed = false),
    path(14f, 66f, 7f, 73f, 15f, 79f, 15f, 92f, closed = false),
    path(79f, 12f, 79f, 15f, 92f, 23f, 92f, 34f, closed = false),
    path(91f, 44f, 95f, 48f, 95f, 58f, closed = false),
    path(86f, 51f, 83f, 54f, 83f, 62f, closed = false)
)
private val traceNodes = listOf(
    Offset(4f, 55f), Offset(21f, 61f), Offset(15f, 92f), Offset(92f, 34f), Offset(95f, 58f), Offset(83f, 62f)
)
private val bits = listOf(
    Triple(14f, 12f, NeonBlue), Triple(33f, 4f, NeonBlue), Triple(72f, 8f, NeonBlue), Triple(87f, 11f, Mint),
    Triple(8f, 19f, Mint), Triple(6f, 48f, NeonBlue), Triple(3.5f, 53f, NeonBlue), Triple(81f, 32f, NeonBlue),
    Triple(84f, 37f, NeonBlue), Triple(93f, 68f, NeonBlue), Triple(88f, 77f, NeonBlue), Triple(18f, 83f, Mint),
    Triple(23f, 87f, NeonBlue), Triple(73f, 88f, NeonBlue)
)

// Face shield traced from the app icon: peaked top, straight sides, angular point.
private val shield = path(52f, 25.5f, 69f, 32.5f, 69f, 51f, 52f, 67f, 35f, 51f, 35f, 32.5f)
private val shieldFacet = path(35f, 32.5f, 52f, 25.5f, 52f, 38f, 35f, 43f)
private val eyeLeft = path(40f, 40.5f, 45.8f, 45.8f, 40f, 51.1f, closed = false)
private val mouthLine = path(48.6f, 50.6f, 55.4f, 50.6f, closed = false)
private val eyeRight = path(64f, 40.5f, 58.2f, 45.8f, 64f, 51.1f, closed = false)
private val IconGreen = Color(0xFF2BEA8C)
private val IconFace = Color(0xFF55F5A8)

private fun DrawScope.drawFaceShield(skin: OperativeSkin, time: Float, pulse: Float) {
    // The default skin wears the icon's own neon green; any other skin tints it.
    val c = OperativeMark.colors(skin, time)
    val isDefault = skin.id == OperativeSkins.DEFAULT.id
    val edge = if (isDefault) IconGreen else c.edge
    val face = if (isDefault) IconFace else c.face
    glowStroke(shield, edge, 2.2f, pulse * 0.8f, layers = 3)
    drawPath(shield, Brush.verticalGradient(listOf(Color(0xFF07231A), Color(0xFF020A07)), startY = 25f, endY = 67f))
    drawPath(shieldFacet, Color.White.copy(alpha = 0.035f))
    drawPath(shield, edge, style = Stroke(2.2f, join = StrokeJoin.Round))
    for (p in listOf(eyeLeft, mouthLine, eyeRight)) {
        drawPath(p, face.copy(alpha = 0.16f * pulse), style = Stroke(4.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(p, face, style = Stroke(2.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun DrawScope.glowStroke(p: Path, color: Color, width: Float, glow: Float, layers: Int = 5) {
    for (i in layers downTo 1) {
        drawPath(p, color.copy(alpha = 0.07f * glow), style = Stroke(width + width * 0.9f * i, join = StrokeJoin.Round, cap = StrokeCap.Round))
    }
    drawPath(p, color, style = Stroke(width, join = StrokeJoin.Round, cap = StrokeCap.Round))
}

private fun DrawScope.drawEmblem(skin: OperativeSkin, time: Float) {
    val pulse = 0.8f + 0.2f * sin(time * 2.2f)

    // Data bits and circuit traces behind the frame.
    for ((x, y, c) in bits) drawRect(c.copy(alpha = 0.7f), Offset(x, y), Size(3f, 3f))
    for (t in traces) glowStroke(t, NeonBlue.copy(alpha = 0.85f), 0.7f, pulse * 0.6f, layers = 2)
    for (n in traceNodes) {
        drawCircle(Cyan.copy(alpha = 0.25f * pulse), 2.6f, n)
        drawCircle(Color(0xFF9DF4FF), 1.5f, n)
    }

    // Hexagon frame.
    drawPath(outerHex, Color(0xFF061024))
    glowStroke(outerHex, Cyan, 3f, pulse * 0.8f, layers = 4)
    drawPath(innerHex, Color(0xFF0A1A3A))
    drawPath(innerHex, NeonBlue.copy(alpha = 0.6f), style = Stroke(0.6f))

    // Hood.
    glowStroke(hood, NeonBlue, 0.9f, pulse * 0.7f, layers = 3)
    drawPath(hood, Brush.verticalGradient(listOf(Color(0xFF2A55C8), Color(0xFF15307F), Color(0xFF0D1F5A)), startY = 11f, endY = 74f))
    drawPath(hoodFacet, Color(0xFF3A6BE0).copy(alpha = 0.55f))
    drawPath(hood, NeonBlue, style = Stroke(0.9f, join = StrokeJoin.Round))
    drawPath(hoodOpening, Color(0xFF040A16))
    drawPath(hoodOpening, Color(0xFF1B3C9A), style = Stroke(0.8f, join = StrokeJoin.Round))

    clipPath(innerHex) {
        // Shoulders and collar.
        drawPath(shoulders, Brush.verticalGradient(listOf(Color(0xFF1C3A8F), Color(0xFF0C1C4C)), startY = 62f, endY = 100f))
        drawPath(shoulders, NeonBlue, style = Stroke(0.9f, join = StrokeJoin.Round))
        drawPath(strap, Color(0xFF0E1E52))
        drawPath(strap, NeonBlue.copy(alpha = 0.7f), style = Stroke(0.6f, join = StrokeJoin.Round))
        drawPath(collarTab, Color(0xFF0E1E52))
        drawPath(collarTab, Cyan, style = Stroke(0.9f, join = StrokeJoin.Round))
    }

    // The player's skin as the face shield.
    drawFaceShield(skin, time, pulse)

    // Collar button.
    drawCircle(Mint.copy(alpha = 0.25f * pulse), 6f, Offset(41f, 76f))
    drawCircle(Color(0xFF0B2A2A), 4.4f, Offset(41f, 76f))
    drawCircle(Brush.radialGradient(listOf(Color(0xFFB8FFE2), Mint), Offset(40.2f, 75.2f), 4f), 3.4f, Offset(41f, 76f))

    // Headset: ear cup, then the antenna with its glowing tip.
    drawLine(NeonBlue, Offset(25.5f, 40f), Offset(25.8f, 19f), 1.6f, StrokeCap.Round)
    drawLine(Color(0xFF0D1F5A), Offset(25.5f, 40f), Offset(25.8f, 19f), 0.7f, StrokeCap.Round)
    drawOval(Color(0xFF0C1C4C), Offset(19f, 38f), Size(9f, 15f))
    drawOval(NeonBlue, Offset(19f, 38f), Size(9f, 15f), style = Stroke(0.9f))
    drawOval(Mint.copy(alpha = 0.2f * pulse), Offset(19.6f, 40f), Size(6f, 11f), style = Stroke(2.6f))
    drawOval(Mint, Offset(20.4f, 41f), Size(4.6f, 9f), style = Stroke(1.2f))
    val blink = 0.75f + 0.25f * sin(time * 4f)
    drawCircle(Cyan.copy(alpha = 0.18f * blink), 7f, Offset(26f, 17f))
    drawCircle(Cyan.copy(alpha = 0.3f * blink), 5f, Offset(26f, 17f))
    drawCircle(Cyan, 3.6f, Offset(26f, 17f), style = Stroke(1.1f))
    drawCircle(Color(0xFFE6FDFF).copy(alpha = 0.6f + 0.4f * blink), 2.4f, Offset(26f, 17f))
}
