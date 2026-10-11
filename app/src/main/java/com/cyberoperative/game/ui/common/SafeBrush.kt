package com.cyberoperative.game.ui.common

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode

/**
 * Radial gradients that never crash. Android throws "ending radius must be > 0" for a radius
 * of 0 (or less, or NaN), which a glow sized by a pop-in or a pulse can hit for one frame
 * (0.25.2 crash on run start: a freshly dropped chip's glow at size 0). Every glow in the game
 * goes through here, with the radius clamped to a tiny positive size.
 */
object SafeBrush {
    private const val MIN_RADIUS = 0.01f

    private fun safe(radius: Float): Float =
        if (radius.isNaN() || radius < MIN_RADIUS) MIN_RADIUS else radius

    fun radialGradient(
        colors: List<Color>,
        center: Offset = Offset.Unspecified,
        radius: Float = Float.POSITIVE_INFINITY,
        tileMode: TileMode = TileMode.Clamp
    ): Brush = Brush.radialGradient(colors, center, safe(radius), tileMode)

    fun radialGradient(
        vararg colorStops: Pair<Float, Color>,
        center: Offset = Offset.Unspecified,
        radius: Float = Float.POSITIVE_INFINITY,
        tileMode: TileMode = TileMode.Clamp
    ): Brush = Brush.radialGradient(*colorStops, center = center, radius = safe(radius), tileMode = tileMode)
}
