package com.cyberoperative.game.ui.common

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.max
import kotlin.math.min

/**
 * The operator redesign (owner, 0.44.0: "more closely trace the original operator design then
 * add in the nice shading and color"; approved from docs/operators_concept/v2_*.png).
 *
 * The figures keep their exact shapes; on top:
 *  - [lift]: every plate colour becomes a richer, slightly muted tone of itself (dark armour a
 *    deep tint, coloured panels a mid-tone), like tools/operators/shade_operators.py;
 *  - [overlay]: each panel is lit as a rounded volume from the top left, with a soft highlight;
 *  - [matte] (the Packet Mage's robe): cloth, so softer light, no highlight, folds.
 * Neon trim, faces and visors are untouched.
 */
object FigureShade {
    /** Off restores the old flat look (tests / comparisons). */
    @Volatile var enabled = true

    fun lift(c: Color, matte: Boolean = false): Color {
        if (!enabled) return c
        val v = max(c.red, max(c.green, c.blue))
        if (v < 0.005f) return c
        val target = if (v < 0.12f) (if (matte) 0.3f else 0.22f) else (if (matte) 0.56f else 0.48f)
        val k = target / v
        val r = c.red * k; val g = c.green * k; val b = c.blue * k
        val grey = (r + g + b) / 3f
        val s = if (matte) 1.15f else 0.85f
        fun ch(x: Float) = (grey + (x - grey) * s).coerceIn(0f, 1f)
        return Color(ch(r), ch(g), ch(b), c.alpha)
    }

    /** Rounded light and shade over a panel already filled along [p] (in the panel's own space). */
    fun DrawScope.overlay(p: Path, matte: Boolean = false) {
        if (!enabled) return
        val b = p.getBounds()
        if (b.width <= 0.5f || b.height <= 0.5f) return
        val c = Offset(b.left + b.width * 0.3f, b.top + b.height * 0.25f)
        val r = max(b.width, b.height) * 1.05f
        drawPath(p, SafeBrush.radialGradient(
            listOf(Color.White.copy(alpha = if (matte) 0.1f else 0.2f), Color.Transparent, Color.Black.copy(alpha = if (matte) 0.32f else 0.48f)), c, r))
        if (!matte) clipPath(p) {
            val hc = Offset(b.left + b.width * 0.32f, b.top + b.height * 0.22f)
            val hr = min(b.width, b.height) * 0.32f
            drawCircle(SafeBrush.radialGradient(listOf(Color.White.copy(alpha = 0.34f), Color.Transparent), hc, hr), hr, hc)
        }
    }

    /** A soft highlight along a round limb from [a] to [b] of width [w]. */
    fun DrawScope.tube(a: Offset, b: Offset, w: Float, matte: Boolean = false) {
        if (!enabled) return
        val o = Offset(-w * 0.18f, -w * 0.2f)
        drawLine(Color.White.copy(alpha = if (matte) 0.08f else 0.2f), a + o, b + o, w * 0.32f, StrokeCap.Round)
        drawLine(Color.Black.copy(alpha = 0.22f), a - o, b - o, w * 0.3f, StrokeCap.Round)
    }

    /** Soft vertical cloth folds inside [p], deepening toward its bottom ([top]..[bottom] in y). */
    fun DrawScope.folds(p: Path, top: Float, bottom: Float, count: Int = 5) {
        if (!enabled) return
        val b = p.getBounds()
        clipPath(p) {
            for (i in 0 until count) {
                val t = (i + 0.5f) / count
                val x0 = b.left + b.width * (0.2f + 0.6f * t)
                val x1 = b.left + b.width * (0.05f + 0.9f * t)
                drawLine(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f)), startY = top, endY = bottom),
                    Offset(x0, top), Offset(x1, bottom), b.width * 0.07f, StrokeCap.Round)
                val xl0 = x0 + b.width * 0.06f; val xl1 = x1 + b.width * 0.07f
                drawLine(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.07f)), startY = top, endY = bottom),
                    Offset(xl0, top), Offset(xl1, bottom), b.width * 0.04f, StrokeCap.Round)
            }
        }
    }
}
