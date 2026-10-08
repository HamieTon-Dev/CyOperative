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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import com.cyberoperative.game.data.OperativeSkin
import com.cyberoperative.game.data.SkinEffect
import kotlin.math.sin

/**
 * The >_< shield (the CyOps TD CYBER OPERATIVE mark, identical geometry to
 * ic_cyber_operative.xml) drawn in any [OperativeSkin]'s colours.
 */
object OperativeMark {
    /** Logo viewport size (the vector is cropped to 64 x 74). */
    const val W = 64f
    const val H = 74f

    private fun p(d: String): Path = PathParser().parsePathString(d).toPath()

    // Coordinates are in the original 108-unit logo space; offset by (-22, -18).
    private val glow = p("M54,20 L84,31 L84,56 C84,73 70,84 54,90 C38,84 24,73 24,56 L24,31 Z")
    private val body = p("M54,25 L79,34 L79,56 C79,70 68,79 54,85 C40,79 29,70 29,56 L29,34 Z")
    private val left = p("M37,50.5 L43.5,57 L37,63.5")
    private val mouth = p("M49,63 L59,63")
    private val right = p("M71,50.5 L64.5,57 L71,63.5")

    private val faceStroke = Stroke(4.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val edgeStroke = Stroke(3.2f, join = StrokeJoin.Round)
    private val faceGlowStroke = Stroke(8f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    data class Colors(val edge: Color, val face: Color, val body: Color)

    fun colors(skin: OperativeSkin, time: Float): Colors {
        val edge = Color(skin.edge)
        val face = Color(skin.face)
        val body = Color(skin.body)
        return when (skin.effect) {
            SkinEffect.NONE -> Colors(edge, face, body)
            SkinEffect.SPECTRUM -> {
                val hue = (time * 40f) % 360f
                Colors(Color.hsv(hue, 0.85f, 1f), Color.hsv((hue + 40f) % 360f, 0.35f, 1f), body)
            }
            SkinEffect.NEON -> {
                // Glow drifts a little around the skin's own edge hue (the NEON
                // OPERATIVE face stays green, like the icon), never far from it.
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(skin.edge.toInt(), hsv)
                val hue = (hsv[0] + 14f * sin(time * 0.9f) + 360f) % 360f
                val faceGlow = 0.85f + 0.15f * sin(time * 2.6f)
                Colors(Color.hsv(hue, 0.85f, 1f), Color.hsv(152f, 0.8f, faceGlow), body)
            }
            SkinEffect.HOLOGRAM -> {
                val t = 0.5f + 0.5f * sin(time * 2.2f)
                Colors(lerp(edge, face, t), lerp(face, edge, t), body)
            }
            SkinEffect.PULSE -> {
                val t = 0.5f + 0.5f * sin(time * 3f)
                Colors(lerp(edge.copy(alpha = 0.7f), edge, t), face, body)
            }
        }
    }

    /** Draw the mark centred on [center], [width] pixels wide. */
    fun DrawScope.drawOperative(skin: OperativeSkin, time: Float, center: Offset, width: Float, alpha: Float = 1f) {
        val c = colors(skin, time)
        val s = width / W
        val h = H * s
        translate(center.x - width / 2f, center.y - h / 2f) {
            scale(s, s, Offset.Zero) {
                translate(-22f, -18f) {
                    if (skin.effect == SkinEffect.NEON) {
                        // Breathing neon bloom: layered soft strokes outside the shield.
                        val breathe = 0.75f + 0.25f * sin(time * 2.2f)
                        drawPath(body, c.edge.copy(alpha = 0.10f * alpha * breathe), style = Stroke(14f, join = StrokeJoin.Round))
                        drawPath(body, c.edge.copy(alpha = 0.18f * alpha * breathe), style = Stroke(9f, join = StrokeJoin.Round))
                        drawPath(body, c.edge.copy(alpha = 0.30f * alpha * breathe), style = Stroke(5.5f, join = StrokeJoin.Round))
                    }
                    drawPath(glow, c.edge.copy(alpha = 0.16f * alpha))
                    drawPath(body, c.body.copy(alpha = alpha))
                    drawPath(body, c.edge.copy(alpha = alpha), style = edgeStroke)
                    drawPath(left, c.face.copy(alpha = alpha), style = faceStroke)
                    drawPath(mouth, c.face.copy(alpha = alpha), style = faceStroke)
                    drawPath(right, c.face.copy(alpha = alpha), style = faceStroke)
                    if (skin.effect == SkinEffect.NEON) {
                        // Glowing face, like the icon's lit >_<.
                        val fg = c.face.copy(alpha = 0.25f * alpha)
                        drawPath(left, fg, style = faceGlowStroke)
                        drawPath(mouth, fg, style = faceGlowStroke)
                        drawPath(right, fg, style = faceGlowStroke)
                    }
                }
            }
        }
    }
}

/** A standalone, animated preview of a skin (menu, skins screen). */
@Composable
fun OperativeMarkIcon(skin: OperativeSkin, modifier: Modifier = Modifier, animate: Boolean = true) {
    var time by remember { mutableFloatStateOf(0f) }
    if (animate && skin.effect != SkinEffect.NONE) {
        LaunchedEffect(skin.id) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { time = (it - start) / 1e9f }
        }
    }
    Canvas(modifier) {
        val w = minOf(size.width, size.height * OperativeMark.W / OperativeMark.H)
        with(OperativeMark) { drawOperative(skin, time, center, w) }
    }
}
