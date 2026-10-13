package com.cyberoperative.game.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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

    private val filters = HashMap<GearRarity, ColorFilter?>()

    /** The INFERNO rarities reuse the golden art, hue-rotated (orange, red, neon blue). */
    fun filterFor(r: GearRarity): ColorFilter? = filters.getOrPut(r) {
        if (r.hue == 0f) null else ColorFilter.colorMatrix(hueMatrix(r.hue, if (r == GearRarity.CRYPTO) 1.25f else 1.1f))
    }

    /** Hue rotation by [degrees] (luminance kept), then a saturation boost. */
    fun hueMatrix(degrees: Float, saturation: Float = 1f): ColorMatrix {
        val a = Math.toRadians(degrees.toDouble())
        val c = cos(a).toFloat()
        val s = sin(a).toFloat()
        val lr = 0.213f; val lg = 0.715f; val lb = 0.072f
        val hue = floatArrayOf(
            lr + c * (1 - lr) + s * (-lr), lg + c * (-lg) + s * (-lg), lb + c * (-lb) + s * (1 - lb), 0f, 0f,
            lr + c * (-lr) + s * 0.143f, lg + c * (1 - lg) + s * 0.140f, lb + c * (-lb) + s * (-0.283f), 0f, 0f,
            lr + c * (-lr) + s * (-(1 - lr)), lg + c * (-lg) + s * lg, lb + c * (1 - lb) + s * lb, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        val m = ColorMatrix(hue)
        if (saturation != 1f) m.timesAssign(ColorMatrix().apply { setToSaturation(saturation) })
        return m
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
            alpha = alpha.coerceIn(0f, 1f), colorFilter = filterFor(rarity)
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
    }
}

/** A gear slot icon for menus: the art over a rarity-tinted tile ([frame] draws the border too). */
@Composable
fun GearIconView(item: GearItem?, modifier: Modifier = Modifier, frame: Boolean = true, dim: Boolean = false) {
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val c = item?.let { Color(it.rarity.color) } ?: Color(0xFF3A4A5C)
            val r = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.08f)
            drawRoundRect(SafeBrush.radialGradient(listOf(c.copy(alpha = 0.22f), Color(0xFF0B1220)), center, size.maxDimension * 0.7f), cornerRadius = r)
            if (frame) drawRoundRect(c.copy(alpha = if (item == null) 0.5f else 0.95f), cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(width = (size.minDimension * 0.035f).coerceAtLeast(1.5f)))
            if (item != null) with(GearIcons) { drawGear(item, center.x, center.y, size.minDimension * 0.86f, if (dim) 0.4f else 1f) }
        }
    }
}
