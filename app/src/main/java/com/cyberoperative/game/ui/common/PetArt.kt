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
        Thread({ for (i in 0..7) { image("pets_$i"); image("eggs_$i") } }, "pet-atlas").apply { isDaemon = true }.start()
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
        cell("pets", Pets.validPet(art), Pets.PET_PX, left, top, size, alpha)
        with(PetFx) { draw(anim, left, top, size, time, front = true, alpha = alpha) }
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
