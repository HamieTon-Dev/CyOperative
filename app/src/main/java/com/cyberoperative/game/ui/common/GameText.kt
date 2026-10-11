package com.cyberoperative.game.ui.common

/**
 * Platform-neutral text style for canvas text (damage numbers, tags, boss glyphs). Drawn by
 * [com.cyberoperative.game.platform.drawGameText], which Android implements with
 * android.graphics and the Steam/PC build with Skia. Mutable and reused, like a Paint.
 */
class GameTextPaint(
    var textSize: Float = 14f,
    /** ARGB. */
    var color: Int = 0xFFFFFFFF.toInt(),
    var align: Align = Align.CENTER,
    var bold: Boolean = false,
    /** 0..255, multiplied into [color]'s alpha. */
    var alpha: Int = 255,
    /** Drop shadow: radius 0 = none. */
    var shadowRadius: Float = 0f,
    var shadowDy: Float = 0f,
    var shadowColor: Int = 0xFF000000.toInt()
) {
    enum class Align { LEFT, CENTER, RIGHT }

    fun shadow(radius: Float, dy: Float, color: Int) { shadowRadius = radius; shadowDy = dy; shadowColor = color }
}

/** ARGB int from components (replaces android.graphics.Color.argb in shared code). */
fun argb(a: Int, r: Int, g: Int, b: Int): Int = (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

/** "Tap" on phones, "Click" on PC (Steam). */
val tapWord: String get() = if (com.cyberoperative.game.platform.AppInfo.desktop) "Click" else "Tap"
