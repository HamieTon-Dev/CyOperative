package com.cyberoperative.game.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.cyberoperative.game.data.GearRarity
import com.cyberoperative.game.data.PetEgg
import com.cyberoperative.game.data.Pets

/**
 * The owner's companion art (resources/pets): pet and egg atlases (16 × 16 cells a page), the
 * Inferno Stone icons and the incubator pictures. Decoded once, on first use.
 */
object PetArt {
    private val cache = HashMap<String, ImageBitmap?>()

    fun image(name: String): ImageBitmap? = synchronized(cache) {
        cache.getOrPut(name) { runCatching { com.cyberoperative.game.platform.loadImageResource("pets/$name.webp") }.getOrNull() }
    }

    /** Decodes the atlases on a background thread (app start). */
    fun preload() {
        Thread({ for (i in 0..9) { image("pets_$i"); image("eggs_$i") } }, "pet-atlas").apply { isDaemon = true }.start()
    }

    private fun DrawScope.cell(page: String, index: Int, px: Int, dx: Float, dy: Float, size: Float, alpha: Float) {
        val img = image("${page}_${index / Pets.PER_PAGE}") ?: return
        val i = index % Pets.PER_PAGE
        drawImage(
            img, IntOffset((i % Pets.COLS) * px, (i / Pets.COLS) * px), IntSize(px, px),
            IntOffset(dx.toInt(), dy.toInt()), IntSize(size.toInt().coerceAtLeast(1), size.toInt().coerceAtLeast(1)),
            alpha = alpha, filterQuality = FilterQuality.Medium
        )
    }

    /** Pet [art] standing at ([cx], [bottom]), [size] tall; animated pets move with [time] (seconds). */
    fun DrawScope.drawPet(art: Int, cx: Float, bottom: Float, size: Float, alpha: Float = 1f, time: Float = 0f) {
        val anim = Pets.anim(art)
        if (anim == null) {
            cell("pets", Pets.validPet(art), Pets.PET_PX, cx - size / 2f, bottom - size, size, alpha)
            return
        }
        val left = cx - size / 2f
        val bob = if (anim.floats) kotlin.math.sin(time * 2.2f) * 0.035f * size else 0f
        val top = bottom - size + bob
        with(PetFx) { draw(anim, left, top, size, time, front = false, alpha = alpha) }
        if (anim.glitches) glitchCell(Pets.validPet(art), left, top, size, alpha, time, anim.parts.first { it.kind == com.cyberoperative.game.data.PetAnim.Kind.GLITCH }.color)
        else cell("pets", Pets.validPet(art), Pets.PET_PX, left, top, size, alpha)
        with(PetFx) { draw(anim, left, top, size, time, front = true, alpha = alpha) }
    }

    /**
     * GLITCHED ENCRYPTED pets (0.49.0): now and then (like the gear's card) the sprite splits into
     * a red and a cyan ghost and its rows tear sideways; otherwise it's drawn as is.
     */
    private fun DrawScope.glitchCell(index: Int, dx: Float, dy: Float, size: Float, alpha: Float, time: Float, ghost: Long? = null) {
        val img = image("pets_${index / Pets.PER_PAGE}") ?: return
        val px = Pets.PET_PX
        val i = index % Pets.PER_PAGE
        val sx = (i % Pets.COLS) * px; val sy = (i / Pets.COLS) * px
        val seed = (time * 9f).toInt()
        val burst = seed % 7 < 2
        val d = size.toInt().coerceAtLeast(1)
        if (!burst) {
            drawImage(img, IntOffset(sx, sy), IntSize(px, px), IntOffset(dx.toInt(), dy.toInt()), IntSize(d, d), alpha = alpha, filterQuality = FilterQuality.Medium)
            return
        }
        val shift = (size * 0.04f).coerceAtLeast(1f)
        // Red / cyan ghosts (GLITCHED ENCRYPTED); GLITCHED pets name their own (magenta, 0.49.1).
        for ((c, s) in listOf(Color(ghost ?: 0xFFFF2B3A) to -1f, Color(0xFF00F0FF) to 1f))
            drawImage(img, IntOffset(sx, sy), IntSize(px, px), IntOffset((dx + shift * s).toInt(), dy.toInt()), IntSize(d, d),
                alpha = alpha * 0.55f, colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(c, androidx.compose.ui.graphics.BlendMode.SrcIn),
                filterQuality = FilterQuality.Medium)
        // Four bands, each pushed sideways by its own amount.
        val bands = 4
        for (b in 0 until bands) {
            val j = (((seed * 73 + b * 151) * 2654435761L) ushr 12 and 0xFF) / 255f
            val off = ((j - 0.5f) * size * 0.12f).toInt()
            val s0 = px * b / bands; val s1 = px * (b + 1) / bands
            val d0 = d * b / bands; val d1 = d * (b + 1) / bands
            drawImage(img, IntOffset(sx, sy + s0), IntSize(px, s1 - s0), IntOffset(dx.toInt() + off, dy.toInt() + d0), IntSize(d, (d1 - d0).coerceAtLeast(1)),
                alpha = alpha, filterQuality = FilterQuality.Medium)
        }
    }

    fun DrawScope.drawEgg(art: Int, cx: Float, bottom: Float, size: Float, alpha: Float = 1f) =
        cell("eggs", Pets.validEgg(art), Pets.EGG_PX, cx - size / 2f, bottom - size, size, alpha)
}

/** A pet portrait with a soft glow in its rarity colour (animated ENCRYPTED pets keep moving). */
@Composable
fun PetIconView(art: Int, rarity: GearRarity, modifier: Modifier = Modifier) {
    val t = if (Pets.anim(art) != null) {
        val v by rememberInfiniteTransition(label = "pet").animateFloat(
            0f, 600f, infiniteRepeatable(tween(600_000, easing = LinearEasing)), label = "t"
        )
        v
    } else 0f
    Canvas(modifier) {
        val c = Color(rarity.color)
        drawCircle(c.copy(alpha = 0.18f), size.minDimension * 0.46f, Offset(size.width / 2f, size.height * 0.55f))
        with(PetArt) { drawPet(art, size.width / 2f, size.height * 0.98f, size.minDimension * 0.96f, time = t) }
    }
}

@Composable
fun EggIconView(egg: PetEgg, modifier: Modifier = Modifier, alpha: Float = 1f) {
    Canvas(modifier) {
        drawCircle(Color(egg.rarity.color).copy(alpha = 0.16f * alpha), size.minDimension * 0.42f, Offset(size.width / 2f, size.height * 0.55f))
        with(PetArt) { drawEgg(egg.art, size.width / 2f, size.height * 0.98f, size.minDimension * 0.92f, alpha) }
    }
}

/** One of the stone / incubator pictures (stone_icon, stone_slot, stone_inv, stone_big, incubator_1..6). */
@Composable
fun PetPicture(name: String, modifier: Modifier = Modifier, alpha: Float = 1f) {
    val img = PetArt.image(name) ?: run { Box(modifier); return }
    Image(img, null, modifier, contentScale = ContentScale.Fit, alpha = alpha)
}
