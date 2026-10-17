package com.cyberoperative.game.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cyberoperative.game.data.GearRarity
import kotlin.math.sin

/*
 * Animated cards for the four INFERNO rarities (owner, 0.33.0): FORGED, VOLTAIC and CELESTIAL get
 * a subtle holographic sheen in their own colour, like a foil trading card; ABYSSAL, the top tier,
 * gets low flames, rising embers and a flickering border instead.
 */

/**
 * Adds the rarity's card animation (nothing below FORGED). [over] draws it over the content (gear
 * tiles: the foil sits on the art); otherwise behind it, over the card's background (item cards,
 * so the text stays crisp).
 */
fun Modifier.rarityCard(rarity: GearRarity?, corner: Dp = 6.dp, over: Boolean = true): Modifier =
    if (rarity == null || !rarity.animated) this else composed {
        val t by rememberInfiniteTransition(label = "rarity").animateFloat(
            0f, 600f, infiniteRepeatable(tween(600_000, easing = LinearEasing), RepeatMode.Restart), label = "t"
        )
        val draw: DrawScope.() -> Unit = { RarityFx.draw(this, rarity, t, corner.toPx()) }
        if (over) drawWithContent { drawContent(); draw() } else drawBehind(draw)
    }

object RarityFx {
    fun draw(d: DrawScope, rarity: GearRarity, t: Float, corner: Float) = with(d) {
        val clip = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, CornerRadius(corner)))
        }
        clipPath(clip) {
            when (rarity) {
                GearRarity.ABYSSAL -> flames(t)
                GearRarity.GLITCHED -> glitch(t)
                GearRarity.ENCRYPTED -> codeRain(t)
                else -> sheen(Color(rarity.color), t)
            }
        }
        if (rarity == GearRarity.ENCRYPTED) {
            val pulse = 0.55f + 0.3f * sin(t * 3f)
            drawRoundRect(Color(0xFF00F0FF).copy(alpha = pulse), cornerRadius = CornerRadius(corner),
                style = Stroke(width = (size.minDimension * 0.03f).coerceIn(1.5f, 3f)))
        }
        if (rarity == GearRarity.ABYSSAL) {
            val flick = 0.55f + 0.25f * sin(t * 11f) * sin(t * 4.3f)
            drawRoundRect(Color(0xFFFF3A1A).copy(alpha = flick), cornerRadius = CornerRadius(corner),
                style = Stroke(width = (size.minDimension * 0.03f).coerceIn(1.5f, 3f)))
        }
    }

    /** A soft diagonal band of the tier colour sweeping across every few seconds, over faint foil lines. */
    private fun DrawScope.sheen(c: Color, t: Float) {
        val w = size.width; val h = size.height
        // Faint foil texture: fine diagonal lines that shimmer slowly.
        val step = (size.minDimension / 9f).coerceAtLeast(6f)
        val la = 0.05f + 0.03f * sin(t * 1.3f)
        var x = -h
        while (x < w) {
            drawLine(c.copy(alpha = la), Offset(x, h), Offset(x + h, 0f), strokeWidth = 1f)
            x += step
        }
        // The sweep: 1.4 s across, then 2.2 s rest.
        val period = 3.6f
        val p = (t % period) / 1.4f
        if (p <= 1f) {
            val span = w + h
            val cx = -h * 0.5f + p * (span + h * 0.5f)
            val band = size.minDimension * 0.55f
            val brush = Brush.linearGradient(
                0f to Color.Transparent, 0.35f to c.copy(alpha = 0.22f), 0.5f to Color.White.copy(alpha = 0.18f),
                0.65f to c.copy(alpha = 0.22f), 1f to Color.Transparent,
                start = Offset(cx - band, h * 0.5f + band), end = Offset(cx + band, h * 0.5f - band)
            )
            drawRect(brush)
        }
    }

    /** GLITCHED (owner, 0.39.0): a gold card that tears into magenta / cyan slices now and then. */
    private fun DrawScope.glitch(t: Float) {
        val w = size.width; val h = size.height
        sheen(Color(0xFFFFC21A), t)
        val seed = (t * 9f).toInt()
        val burst = seed % 7 < 2
        val n = if (burst) 5 else 2
        for (k in 0 until n) {
            val j = (((seed * 73 + k * 151) * 2654435761L) ushr 12 and 0xFFFF) / 65535f
            val y = h * j
            val bh = h * (0.03f + 0.06f * ((k * 37 + seed) % 5) / 5f)
            val dx = (j - 0.5f) * w * (if (burst) 0.12f else 0.04f)
            val a = if (burst) 0.32f else 0.12f
            drawRect(Color(0xFFFF3DF2).copy(alpha = a), Offset(dx, y), androidx.compose.ui.geometry.Size(w, bh))
            drawRect(Color(0xFF3DF2FF).copy(alpha = a * 0.8f), Offset(-dx, y + bh * 0.5f), androidx.compose.ui.geometry.Size(w, bh * 0.5f))
        }
    }

    /** ENCRYPTED (owner, 0.39.0): falling green code over a dark sheen. */
    private fun DrawScope.codeRain(t: Float) {
        val w = size.width; val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0x2200F0FF), Color.Transparent, Color(0x3300F0FF))))
        val cols = 7
        val cell = (size.minDimension / 12f).coerceAtLeast(3f)
        for (c in 0 until cols) {
            val x = w * (c + 0.5f) / cols
            val speed = 0.18f + 0.07f * ((c * 5) % 4)
            val head = ((t * speed + c * 0.29f) % 1.3f) * h
            for (k in 0 until 8) {
                val y = head - k * cell * 1.4f
                if (y < 0f || y > h) continue
                val a = (if (k == 0) 0.85f else 0.5f * (1f - k / 8f))
                drawRect(if (k == 0) Color.White.copy(alpha = a) else Color(0xFF00F0FF).copy(alpha = a),
                    Offset(x - cell * 0.35f, y), androidx.compose.ui.geometry.Size(cell * 0.7f, cell))
            }
        }
    }

    /** Low flames along the bottom edge, a warm glow, and embers drifting up. */
    private fun DrawScope.flames(t: Float) {
        val w = size.width; val h = size.height
        // Big cards keep low flames: never sized past a 150 dp tile.
        val flameH = minOf(h, 150.dp.toPx())
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0x33FF2B1A), Color(0x66FF3A1A)), startY = h - flameH * 0.55f, endY = h))
        val n = 9
        for (k in 0 until n) {
            val fx = w * (k + 0.5f) / n
            val fw = w / n * 0.9f
            val fh = flameH * (0.16f + 0.07f * sin(t * 6.5f + k * 1.7f) + 0.04f * sin(t * 11f + k))
            val tip = fx + sin(t * 5f + k * 2.3f) * fw * 0.25f
            val p = Path().apply {
                moveTo(fx - fw * 0.6f, h)
                quadraticTo(fx - fw * 0.5f, h - fh * 0.5f, tip, h - fh)
                quadraticTo(fx + fw * 0.5f, h - fh * 0.5f, fx + fw * 0.6f, h)
                close()
            }
            drawPath(p, Brush.verticalGradient(listOf(Color(0x00FF3A1A), Color(0x99FF3A1A), Color(0xBBFFA040)), startY = h - fh, endY = h))
        }
        for (k in 0 until 7) {
            val ph = (t * 0.28f + k * 0.143f) % 1f
            val ex = w * ((k * 0.37f + 0.1f) % 1f) + sin(t * 1.6f + k) * w * 0.04f
            val ey = h - ph * flameH * 0.8f
            drawCircle(Color(0xFFFF9A40).copy(alpha = (1f - ph) * 0.8f), (size.minDimension * 0.012f).coerceAtLeast(1.2f), Offset(ex, ey))
        }
    }
}
