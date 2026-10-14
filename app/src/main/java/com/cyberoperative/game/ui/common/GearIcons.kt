package com.cyberoperative.game.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.cyberoperative.game.data.GearArt
import com.cyberoperative.game.data.GearItem
import com.cyberoperative.game.data.GearRarity
import com.cyberoperative.game.data.GearSlot
import kotlin.math.cos
import kotlin.math.sin

/**
 * The owner's gear art (resources/gear/<slot>.webp atlases, 96 px cells). Decoded once, off the
 * UI thread where possible ([preload]); drawing before an atlas is ready just skips the icon.
 */
object GearIcons {
    private val atlases = arrayOfNulls<ImageBitmap>(GearSlot.entries.size)
    private val tried = BooleanArray(GearSlot.entries.size)
    @Volatile private var preloading = false

    @Synchronized
    fun atlas(slot: GearSlot): ImageBitmap? {
        val i = slot.ordinal
        if (!tried[i]) {
            tried[i] = true
            atlases[i] = runCatching { com.cyberoperative.game.platform.loadImageResource("gear/${slot.sheet}.webp") }.getOrNull()
        }
        return atlases[i]
    }

    /** Decodes every atlas on a background thread (app start / run start). */
    fun preload() {
        if (preloading) return
        preloading = true
        Thread({ for (s in GearSlot.entries) atlas(s) }, "gear-atlas").apply { isDaemon = true }.start()
    }

    /** Draws a piece's art centred on ([cx], [cy]) in a [size] square. */
    fun DrawScope.drawGear(slot: GearSlot, rarity: GearRarity, art: Int, cx: Float, cy: Float, size: Float, alpha: Float = 1f) {
        val img = atlas(slot) ?: return
        val cell = GearArt.cell(slot, rarity.artTier, art)
        val src = IntOffset((cell % GearArt.COLS) * GearArt.CELL, (cell / GearArt.COLS) * GearArt.CELL)
        val d = size.toInt().coerceAtLeast(1)
        drawImage(
            img, srcOffset = src, srcSize = IntSize(GearArt.CELL, GearArt.CELL),
            dstOffset = IntOffset((cx - d / 2f).toInt(), (cy - d / 2f).toInt()), dstSize = IntSize(d, d),
            alpha = alpha.coerceIn(0f, 1f)
        )
    }

    fun DrawScope.drawGear(item: GearItem, cx: Float, cy: Float, size: Float, alpha: Float = 1f) =
        drawGear(item.slot, item.rarity, item.art, cx, cy, size, alpha)

    /**
     * The light beam a drop stands in (owner, 2026-10-13: "all of the items will need the aura beam
     * behind them"): a soft column of the rarity colour, taller and brighter for better rarities.
     */
    fun DrawScope.drawLootBeam(rarity: GearRarity, x: Float, y: Float, t: Float, scale: Float = 1f) {
        val c = Color(rarity.color)
        val tier = rarity.ordinal
        val h = (90f + 38f * tier) * scale
        val w = (16f + 3f * tier) * scale
        val pulse = 0.75f + 0.25f * sin(t * 4f)
        val a = (0.32f + 0.07f * tier).coerceAtMost(0.85f) * pulse
        // Column: bright at the floor, fading upward.
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, c.copy(alpha = a * 0.55f), c.copy(alpha = a)), startY = y - h, endY = y),
            topLeft = Offset(x - w / 2f, y - h), size = androidx.compose.ui.geometry.Size(w, h)
        )
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = a * 0.6f)), startY = y - h * 0.8f, endY = y),
            topLeft = Offset(x - w * 0.14f, y - h * 0.8f), size = androidx.compose.ui.geometry.Size(w * 0.28f, h * 0.8f)
        )
        // Pool of light on the floor.
        drawOval(
            SafeBrush.radialGradient(listOf(c.copy(alpha = a), Color.Transparent), Offset(x, y), 30f * scale),
            topLeft = Offset(x - 30f * scale, y - 12f * scale), size = androidx.compose.ui.geometry.Size(60f * scale, 24f * scale)
        )
        // Rising motes for EPIC and up.
        if (tier >= GearRarity.EPIC.ordinal) for (k in 0 until 3 + tier) {
            val ph = ((t * 0.55f + k * 0.37f) % 1f)
            val mx = x + sin(k * 2.1f + t * 1.7f) * w * 0.45f
            drawCircle(c.copy(alpha = (1f - ph) * 0.8f), (2.2f + tier * 0.2f) * scale, Offset(mx, y - ph * h))
        }
        when (rarity) {
            GearRarity.FORGED -> forgeSparks(c, x, y, h, t, scale)
            GearRarity.VOLTAIC -> voltArcs(c, x, y, h, w, t, scale)
            GearRarity.CELESTIAL -> celestialHalo(c, x, y, h, t, scale)
            GearRarity.ABYSSAL -> abyssFlames(x, y, w, t, scale)
            else -> {}
        }
    }

    /** FORGED: anvil sparks spraying out of the floor pool and falling back. */
    private fun DrawScope.forgeSparks(c: Color, x: Float, y: Float, h: Float, t: Float, scale: Float) {
        for (k in 0 until 9) {
            val ph = (t * 0.9f + k * 0.111f) % 1f
            val dir = if (k % 2 == 0) 1f else -1f
            val vx = (18f + (k * 13) % 23) * dir * scale
            val sx = x + vx * ph * 2.2f
            val sy = y - (h * 0.45f) * (ph * 2f - ph * ph * 1.6f)
            drawLine(Color(0xFFFFD08A).copy(alpha = (1f - ph) * 0.9f), Offset(sx, sy), Offset(sx - vx * 0.08f, sy + 4f * scale), strokeWidth = 1.6f * scale)
        }
        drawCircle(c.copy(alpha = 0.25f + 0.1f * sin(t * 9f)), 14f * scale, Offset(x, y - 4f * scale))
    }

    /** VOLTAIC: jagged lightning crawling up the beam, re-shaped a few times a second. */
    private fun DrawScope.voltArcs(c: Color, x: Float, y: Float, h: Float, w: Float, t: Float, scale: Float) {
        val seed = (t * 7f).toInt()
        for (a in 0 until 2) {
            var px = x; var py = y
            val p = androidx.compose.ui.graphics.Path().apply { moveTo(px, py) }
            for (s in 1..7) {
                val j = (((seed * 31 + a * 17 + s * 7) * 2654435761L) ushr 16 and 0xFF) / 255f - 0.5f
                px = x + j * w * 1.4f
                py = y - h * 0.9f * s / 7f
                p.lineTo(px, py)
            }
            val flick = if ((seed + a) % 3 == 0) 0.25f else 0.85f
            drawPath(p, c.copy(alpha = flick), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8f * scale))
            drawPath(p, Color.White.copy(alpha = flick * 0.7f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.8f * scale))
        }
    }

    /** CELESTIAL: a slow halo ring and four-point star glints drifting up. */
    private fun DrawScope.celestialHalo(c: Color, x: Float, y: Float, h: Float, t: Float, scale: Float) {
        val hy = y - h * 0.55f
        drawOval(c.copy(alpha = 0.55f + 0.2f * sin(t * 2f)), topLeft = Offset(x - 26f * scale, hy - 7f * scale),
            size = androidx.compose.ui.geometry.Size(52f * scale, 14f * scale), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f * scale))
        for (k in 0 until 5) {
            val ph = (t * 0.35f + k * 0.2f) % 1f
            val sx = x + sin(k * 2.4f + t) * 26f * scale
            val sy = y - ph * h
            val r = (5f + 3f * sin(t * 5f + k)) * scale * (1f - ph)
            val a = (1f - ph) * 0.9f
            drawLine(Color.White.copy(alpha = a), Offset(sx - r, sy), Offset(sx + r, sy), strokeWidth = 1.2f * scale)
            drawLine(Color.White.copy(alpha = a), Offset(sx, sy - r), Offset(sx, sy + r), strokeWidth = 1.2f * scale)
        }
    }

    /** ABYSSAL: flames licking up round the item and embers rising. */
    private fun DrawScope.abyssFlames(x: Float, y: Float, w: Float, t: Float, scale: Float) {
        for (k in 0 until 7) {
            val fx = x + (k - 3) * w * 0.32f
            val fh = (26f + 14f * sin(t * 7f + k * 1.9f) + (k % 3) * 6f) * scale * (1f - kotlin.math.abs(k - 3) * 0.13f)
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(fx - 7f * scale, y)
                quadraticTo(fx - 6f * scale + sin(t * 9f + k) * 3f * scale, y - fh * 0.55f, fx + sin(t * 6f + k * 2f) * 4f * scale, y - fh)
                quadraticTo(fx + 6f * scale, y - fh * 0.5f, fx + 7f * scale, y)
                close()
            }
            drawPath(p, Brush.verticalGradient(listOf(Color(0x00FF2B00), Color(0xCCFF3A1A), Color(0xEEFFB040)), startY = y - fh, endY = y))
        }
        for (k in 0 until 8) {
            val ph = (t * 0.5f + k * 0.125f) % 1f
            val ex = x + sin(k * 1.7f + t * 2f) * w * 0.8f
            drawCircle(Color(0xFFFF8A3A).copy(alpha = (1f - ph) * 0.9f), 1.8f * scale, Offset(ex, y - 20f * scale - ph * 120f * scale))
        }
    }
}

/** A gear slot icon for menus: the art over a rarity-tinted tile ([frame] draws the border too). */
@Composable
fun GearIconView(item: GearItem?, modifier: Modifier = Modifier, frame: Boolean = true, dim: Boolean = false) {
    Box(modifier.rarityCard(item?.rarity?.takeIf { !dim }, corner = 4.dp, over = true)) {
        Canvas(Modifier.matchParentSize()) {
            val c = item?.let { Color(it.rarity.color) } ?: Color(0xFF3A4A5C)
            val r = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.08f)
            drawRoundRect(SafeBrush.radialGradient(listOf(c.copy(alpha = 0.22f), Color(0xFF0B1220)), center, size.maxDimension * 0.7f), cornerRadius = r)
            if (frame) drawRoundRect(c.copy(alpha = if (item == null) 0.5f else 0.95f), cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(width = (size.minDimension * 0.035f).coerceAtLeast(1.5f)))
            if (item != null) with(GearIcons) { drawGear(item, center.x, center.y, size.minDimension * 0.86f, if (dim) 0.4f else 1f) }
        }
    }
}
