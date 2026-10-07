package com.cyberoperative.game.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The shared CyOps universe palette (ported from CyOps TD) plus Cyber
 * Operative additions. Colour carries meaning (§49): cool tones are friendly
 * or environment, warm tones are always hostile.
 */
object Palette {
    // Surfaces
    val Background = Color(0xFF070B14)
    val Surface = Color(0xFF0C1322)
    val SurfaceRaised = Color(0xFF121C30)
    val SurfaceSunken = Color(0xFF060A11)
    val Divider = Color(0xFF1E2D47)
    val GridLine = Color(0xFF12203A)

    // Friendly / defence
    val Cyan = Color(0xFF00E5FF)
    val CyanDim = Color(0xFF0A8FA3)
    val Green = Color(0xFF00FF9C)
    val GreenDim = Color(0xFF0C9A60)
    val Blue = Color(0xFF2E7BFF)
    val Purple = Color(0xFFA259FF)

    // Hostile
    val Red = Color(0xFFFF2D55)
    val RedDeep = Color(0xFFC8102E)
    val Orange = Color(0xFFFF7A1A)
    val Magenta = Color(0xFFFF2EC4)

    // Information
    val TextPrimary = Color(0xFFE6EEFA)
    val TextSecondary = Color(0xFF93A6C4)
    val TextMuted = Color(0xFF5C6E8C)
    val Gold = Color(0xFFFFD426)

    /** € (earned) and ◇ (premium) currency colours. */
    val Euro = Color(0xFFFFD426)
    val Diamond = Color(0xFF7DF9FF)

    // Logo (the Cyber Operative shield)
    val LogoMint = Color(0xFF6EF7A5)
    val LogoEdge = Color(0xFF23C55E)
    val LogoBody = Color(0xFF07301E)

    val ServerLedGreen = Color(0xFF22F060)
    val ServerLedAmber = Color(0xFFFF9A1A)

    fun healthColor(fraction: Float): Color = when {
        fraction > 0.6f -> Green
        fraction > 0.3f -> Orange
        else -> Red
    }
}
