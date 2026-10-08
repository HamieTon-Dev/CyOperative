package com.cyberoperative.game.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.em

/**
 * The CYBER OPERATIVE wordmark: angular neon letterforms built from vector
 * paths, inside a circuit-trace frame, with the tagline underneath. Pure
 * vector drawing, so it is crisp at any size. [glow] (0..1+) scales the halo
 * so callers can pulse it.
 */
@Composable
fun CyberTitleLogo(modifier: Modifier = Modifier, glow: Float = 1f, showTagline: Boolean = true) {
    val measurer = rememberTextMeasurer()
    val unitsHigh = if (showTagline) LOGO_H else LOGO_H_NO_TAGLINE
    val cache = remember { LogoCache() }
    Canvas(modifier.aspectRatio(LOGO_W / unitsHigh)) {
        val k = size.width / LOGO_W
        val words = cache.get(k)
        drawFrame(k, glow)
        drawWord(words.cyber, k, CYBER_TOP, CYBER_H, CYBER_STYLE, glow)
        drawWord(words.operative, k, OPERATIVE_TOP, OPERATIVE_H, OPERATIVE_STYLE, glow)
        if (showTagline) {
            val style = TextStyle(
                color = Color(0xFFA8F6FF),
                fontSize = (25f * k).toSp(),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.16.em,
                shadow = Shadow(Color(0xFF00E5FF).copy(alpha = (0.8f * glow).coerceIn(0f, 1f)), Offset.Zero, 14f * k)
            )
            val layout = measurer.measure(TAGLINE, style)
            val tx = (size.width - layout.size.width) / 2f
            val ty = TAGLINE_Y * k - layout.size.height / 2f
            drawText(layout, topLeft = Offset(tx, ty))
            // Dashes and dots either side of the tagline.
            val cy = TAGLINE_Y * k
            val line = Color(0xFF39E8FF)
            val gap = 22f * k
            val dash = 58f * k
            drawLine(line, Offset(tx - gap - dash, cy), Offset(tx - gap, cy), 3f * k, StrokeCap.Round)
            drawLine(line, Offset(tx + layout.size.width + gap, cy), Offset(tx + layout.size.width + gap + dash, cy), 3f * k, StrokeCap.Round)
            drawCircle(line, 3.5f * k, Offset(tx - gap - dash - 14f * k, cy))
            drawCircle(line, 3.5f * k, Offset(tx + layout.size.width + gap + dash + 14f * k, cy))
        }
    }
}

// ---- layout, in logo units (1000 wide) --------------------------------------

private const val LOGO_W = 1000f
private const val LOGO_H = 420f
private const val LOGO_H_NO_TAGLINE = 360f
private const val TAGLINE = "ENDLESS CYBER ROGUELIKE ACTION"
private const val TAGLINE_Y = 388f
private const val CYBER_TOP = 64f
private const val CYBER_H = 150f
private const val OPERATIVE_TOP = 240f
private const val OPERATIVE_H = 80f
private const val LETTER_GAP = 0.13f

private class WordStyle(
    val gradient: List<Color>,
    val glow: Color,
    val depth: Color,
    val stripe: Color,
    val stroke: Float
)

private val CYBER_STYLE = WordStyle(
    gradient = listOf(Color(0xFFF4FEFF), Color(0xFFD2F8FF), Color(0xFF8CEBFF), Color(0xFF3CD6F5)),
    glow = Color(0xFF00E5FF),
    depth = Color(0xFF0B4A66),
    stripe = Color(0xFF1AA6C8),
    stroke = 0.2f
)

private val OPERATIVE_STYLE = WordStyle(
    gradient = listOf(Color(0xFFD8FFEC), Color(0xFF7DFFC0), Color(0xFF22F08E), Color(0xFF10C474)),
    glow = Color(0xFF00FF9C),
    depth = Color(0xFF075C3A),
    stripe = Color(0xFF0FA866),
    stroke = 0.24f
)

private class Words(val cyber: Path, val operative: Path)

/** Glyph paths depend only on the scale, so they are built once per size. */
private class LogoCache {
    private var k = -1f
    private var words: Words? = null
    fun get(scale: Float): Words {
        words?.let { if (scale == k) return it }
        k = scale
        return Words(
            buildWord("CYBER", scale, CYBER_TOP, CYBER_H, CYBER_STYLE.stroke),
            buildWord("OPERATIVE", scale, OPERATIVE_TOP, OPERATIVE_H, OPERATIVE_STYLE.stroke)
        ).also { words = it }
    }
}

// ---- drawing -----------------------------------------------------------------

private fun DrawScope.drawWord(path: Path, k: Float, top: Float, h: Float, style: WordStyle, glow: Float) {
    val px = h * k
    val y0 = top * k
    // Extruded depth under the face.
    translate(top = px * 0.055f) { drawPath(path, style.depth) }
    // Soft neon halo: stacked wide, faint strokes.
    for (i in 16 downTo 1) {
        drawPath(
            path, style.glow.copy(alpha = (0.028f * glow).coerceIn(0f, 1f)),
            style = Stroke(width = px * 0.021f * i, join = StrokeJoin.Round)
        )
    }
    // Face.
    drawPath(path, Brush.verticalGradient(style.gradient, startY = y0, endY = y0 + px))
    // Scan cuts across the lower half, as in the key art.
    clipPath(path) {
        for ((at, thick) in listOf(0.6f to 0.022f, 0.7f to 0.03f, 0.82f to 0.04f)) {
            drawRect(style.stripe.copy(alpha = 0.55f), Offset(0f, y0 + px * at), androidx.compose.ui.geometry.Size(size.width, px * thick))
        }
        drawRect(Color.White.copy(alpha = 0.22f), Offset(0f, y0), androidx.compose.ui.geometry.Size(size.width, px * 0.18f))
    }
    // Bright rim.
    drawPath(path, Color.White.copy(alpha = 0.75f), style = Stroke(width = px * 0.016f, join = StrokeJoin.Miter))
}

private fun DrawScope.drawFrame(k: Float, glow: Float) {
    val line = Color(0xFF2FE4FF)
    fun trace(vararg p: Float, node: Boolean = true) {
        val path = Path().apply {
            moveTo(p[0] * k, p[1] * k)
            var i = 2
            while (i < p.size) { lineTo(p[i] * k, p[i + 1] * k); i += 2 }
        }
        drawPath(path, line.copy(alpha = (0.18f * glow).coerceIn(0f, 1f)), style = Stroke(10f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, line.copy(alpha = 0.9f), style = Stroke(2.6f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (node) {
            val end = Offset(p[p.size - 2] * k, p[p.size - 1] * k)
            drawCircle(line.copy(alpha = (0.3f * glow).coerceIn(0f, 1f)), 11f * k, end)
            drawCircle(Color(0xFFBFF8FF), 5.5f * k, end)
        }
    }
    // Main frame, broken into runs like a circuit board.
    trace(105f, 180f, 105f, 60f, 135f, 30f, 520f, 30f)
    trace(700f, 30f, 865f, 30f, 895f, 60f, 895f, 150f)
    trace(895f, 222f, 895f, 312f, 865f, 342f, 560f, 342f)
    trace(440f, 342f, 135f, 342f, 105f, 312f, 105f, 240f)
    // Dashed run along the top.
    for (i in 0 until 5) {
        val x = 560f + i * 26f
        drawLine(line.copy(alpha = 0.85f), Offset(x * k, 30f * k), Offset((x + 13f) * k, 30f * k), 2.6f * k, StrokeCap.Round)
    }
    // Traces running out to the sides.
    trace(105f, 180f, 62f, 180f, 40f, 158f, 40f, 70f)
    trace(105f, 240f, 18f, 240f)
    trace(895f, 150f, 938f, 150f, 960f, 172f, 960f, 320f)
    trace(895f, 222f, 936f, 222f)
    // Small chips on the frame.
    drawRect(line.copy(alpha = 0.9f), Offset(97f * k, 120f * k), androidx.compose.ui.geometry.Size(16f * k, 26f * k))
    drawRect(line.copy(alpha = 0.9f), Offset(887f * k, 270f * k), androidx.compose.ui.geometry.Size(16f * k, 26f * k))
}

// ---- letterforms -------------------------------------------------------------

private fun glyphWidth(ch: Char, s: Float): Float = when (ch) {
    'C' -> 0.8f; 'Y' -> 0.86f; 'B' -> 0.82f; 'E' -> 0.74f; 'R' -> 0.82f
    'O' -> 0.86f; 'P' -> 0.8f; 'A' -> 0.86f; 'T' -> 0.8f; 'I' -> s; 'V' -> 0.86f
    else -> 0.6f
}

/** Builds a centred word as one path. Glyphs are designed on a unit-high grid. */
private fun buildWord(text: String, k: Float, top: Float, h: Float, s: Float): Path {
    val total = text.sumOf { glyphWidth(it, s).toDouble() }.toFloat() + LETTER_GAP * (text.length - 1)
    var x = (LOGO_W - total * h) / 2f
    val word = Path()
    for (ch in text) {
        val pen = Pen(x * k, top * k, h * k)
        word.addPath(glyph(ch, pen, s))
        x += (glyphWidth(ch, s) + LETTER_GAP) * h
    }
    return word
}

private class Pen(val ox: Float, val oy: Float, val h: Float) {
    fun poly(vararg p: Float): Path = Path().apply {
        moveTo(ox + p[0] * h, oy + p[1] * h)
        var i = 2
        while (i < p.size) { lineTo(ox + p[i] * h, oy + p[i + 1] * h); i += 2 }
        close()
    }

    fun rect(x0: Float, y0: Float, x1: Float, y1: Float) = poly(x0, y0, x1, y0, x1, y1, x0, y1)

    /** Rectangle with 45-degree chamfers (top-left, top-right, bottom-right, bottom-left). */
    fun cham(x0: Float, y0: Float, x1: Float, y1: Float, tl: Float, tr: Float, br: Float, bl: Float): Path {
        // Corners never overlap, even on a short counter (hole).
        val m = minOf(x1 - x0, y1 - y0) / 2f
        val a = minOf(tl, m); val b = minOf(tr, m); val c = minOf(br, m); val d = minOf(bl, m)
        return poly(
            x0 + a, y0, x1 - b, y0, x1, y0 + b, x1, y1 - c,
            x1 - c, y1, x0 + d, y1, x0, y1 - d, x0, y0 + a
        )
    }
}

private infix fun Path.or(other: Path) = Path().also { it.op(this, other, PathOperation.Union) }
private infix fun Path.minus(other: Path) = Path().also { it.op(this, other, PathOperation.Difference) }

private fun glyph(ch: Char, p: Pen, s: Float): Path {
    val w = glyphWidth(ch, s)
    val c = 0.26f
    // Inner chamfer that keeps the stroke an even width around a 45-degree corner.
    val ci = (c - 0.414f * s).coerceAtLeast(0f)
    return when (ch) {
        'C' -> p.cham(0f, 0f, w, 1f, c, 0f, 0f, c) minus p.cham(s, s, w + 1f, 1f - s, ci, 0f, 0f, ci)
        'Y' -> {
            val cup = p.cham(0f, 0f, w, 0.6f, 0f, 0f, c, c) minus p.cham(s, -1f, w - s, 0.6f - s, 0f, 0f, ci, ci)
            cup or p.rect(w / 2f - s / 2f, 0.5f, w / 2f + s / 2f, 1f)
        }
        'B' -> {
            val m = 0.5f
            val r = c * 0.8f
            val ri = (r - 0.414f * s).coerceAtLeast(0f)
            p.cham(0f, 0f, w, 1f, 0f, r, r, 0f) minus
                p.cham(s, s, w - s, m - s / 2f, 0f, ri, ri, 0f) minus
                p.cham(s, m + s / 2f, w - s, 1f - s, 0f, ri, ri, 0f) minus
                p.poly(w + 0.02f, m - 0.09f, w - s * 0.55f, m, w + 0.02f, m + 0.09f)
        }
        'E' -> p.cham(0f, 0f, w, 1f, c, 0f, 0f, c) minus
            p.rect(s, s, w + 1f, 0.5f - s / 2f) minus
            p.rect(s, 0.5f + s / 2f, w + 1f, 1f - s) minus
            p.rect(w * 0.8f, 0.5f - s / 2f - 0.01f, w + 1f, 0.5f + s / 2f + 0.01f)
        'R', 'P' -> {
            val bowl = 0.6f
            val r = c * 0.8f
            val ri = (r - 0.414f * s).coerceAtLeast(0f)
            var g = (p.cham(0f, 0f, w, bowl, 0f, r, r, 0f) minus p.cham(s, s, w - s, bowl - s, 0f, ri, ri, 0f)) or
                p.rect(0f, 0f, s, 1f)
            if (ch == 'R') g = g or p.poly(w * 0.4f, bowl - s, w * 0.4f + s * 1.3f, bowl - s, w, 1f, w - s * 1.3f, 1f)
            g
        }
        'O' -> p.cham(0f, 0f, w, 1f, c, c, c, c) minus p.cham(s, s, w - s, 1f - s, ci, ci, ci, ci)
        'A' -> p.cham(0f, 0f, w, 1f, c, c, 0f, 0f) minus
            p.cham(s, s, w - s, 0.55f - s / 2f, ci, ci, 0f, 0f) minus
            p.rect(s, 0.55f + s / 2f, w - s, 2f)
        'T' -> p.rect(0f, 0f, w, s) or p.rect(w / 2f - s / 2f, 0f, w / 2f + s / 2f, 1f)
        'I' -> p.rect(0f, 0f, s, 1f)
        'V' -> p.poly(0f, 0f, s * 1.15f, 0f, w / 2f, 1f - s * 1.5f, w - s * 1.15f, 0f, w, 0f, w / 2f + s * 0.62f, 1f, w / 2f - s * 0.62f, 1f)
        else -> p.rect(0f, 0f, w, 1f)
    }
}
