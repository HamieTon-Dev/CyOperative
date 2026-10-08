package com.cyberoperative.game.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import com.cyberoperative.game.data.OperativeSkin
import com.cyberoperative.game.data.OperativeSkins
import kotlin.math.cos
import kotlin.math.sin

/**
 * The app-icon emblem for the main menu: a hooded operative with a headset,
 * framed by a neon hexagon and circuit traces. The face shield is the player's
 * equipped skin, so the emblem changes with what they wear. Pure vectors on a
 * 100-unit grid; scales to any size.
 */
@Composable
fun OperativeEmblem(skin: OperativeSkin, modifier: Modifier = Modifier, animate: Boolean = true) {
    var time by remember { mutableFloatStateOf(0f) }
    if (animate) {
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { time = (it - start) / 1e9f }
        }
    }
    Canvas(modifier.aspectRatio(1f)) {
        val k = size.width / 100f
        scale(k, k, Offset.Zero) { drawEmblem(skin, time) }
    }
}

private val Cyan = Color(0xFF22E6FF)
private val NeonBlue = Color(0xFF3FA8FF)
private val Mint = Color(0xFF2BF5A0)

private fun path(vararg p: Float, closed: Boolean = true) = Path().apply {
    moveTo(p[0], p[1])
    var i = 2
    while (i < p.size) { lineTo(p[i], p[i + 1]); i += 2 }
    if (closed) close()
}

private fun hexagon(cx: Float, cy: Float, r: Float) = Path().apply {
    for (i in 0 until 6) {
        val a = Math.toRadians(-90.0 + 60.0 * i)
        val x = cx + r * cos(a).toFloat()
        val y = cy + r * sin(a).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private val outerHex = hexagon(50f, 47.5f, 44f)
private val innerHex = hexagon(50f, 47.5f, 39.5f)

private val hood = path(49.4f, 11.8f, 61f, 19f, 76f, 29f, 80.5f, 45f, 80.5f, 57f, 74f, 66f, 56f, 72f, 30f, 64f, 24f, 50f, 27f, 33f, 36f, 20f)
private val hoodFacet = path(49.4f, 11.8f, 61f, 19f, 58.6f, 23f, 40f, 28f, 36f, 20f)
private val hoodOpening = path(58.6f, 23f, 75f, 30f, 79f, 50f, 72f, 62f, 56f, 69f, 41f, 62f, 31.5f, 48f, 33f, 34f)
private val shoulders = path(8f, 82f, 19f, 66f, 26f, 59f, 47f, 67f, 51f, 78f, 62f, 69f, 76f, 62f, 92f, 76f, 92f, 100f, 8f, 100f)
private val strap = path(19f, 68f, 27f, 62f, 47f, 69f, 46f, 77f, 27f, 72f)
private val collarTab = path(63f, 68f, 75f, 63f, 77f, 66.5f, 65.4f, 77f)

private val traces = listOf(
    path(10f, 37f, 4f, 43f, 4f, 55f, closed = false),
    path(16f, 37f, 16f, 48f, 21f, 54f, 21f, 61f, closed = false),
    path(14f, 66f, 7f, 73f, 15f, 79f, 15f, 92f, closed = false),
    path(79f, 12f, 79f, 15f, 92f, 23f, 92f, 34f, closed = false),
    path(91f, 44f, 95f, 48f, 95f, 58f, closed = false),
    path(86f, 51f, 83f, 54f, 83f, 62f, closed = false)
)
private val traceNodes = listOf(
    Offset(4f, 55f), Offset(21f, 61f), Offset(15f, 92f), Offset(92f, 34f), Offset(95f, 58f), Offset(83f, 62f)
)
private val bits = listOf(
    Triple(14f, 12f, NeonBlue), Triple(33f, 4f, NeonBlue), Triple(72f, 8f, NeonBlue), Triple(87f, 11f, Mint),
    Triple(8f, 19f, Mint), Triple(6f, 48f, NeonBlue), Triple(3.5f, 53f, NeonBlue), Triple(81f, 32f, NeonBlue),
    Triple(84f, 37f, NeonBlue), Triple(93f, 68f, NeonBlue), Triple(88f, 77f, NeonBlue), Triple(18f, 83f, Mint),
    Triple(23f, 87f, NeonBlue), Triple(73f, 88f, NeonBlue)
)

// Face shield traced from the app icon: peaked top, straight sides, angular point.
private val shield = path(58.6f, 27.5f, 73f, 33.9f, 70.2f, 53f, 55.4f, 65.8f, 38.3f, 56.2f, 37.1f, 34.3f)
private val shieldFacet = path(37.1f, 34.3f, 58.6f, 27.5f, 58.6f, 38f, 37.4f, 44f)
private val eyeLeft = path(42.7f, 41.5f, 47.4f, 45.9f, 42.5f, 50.3f, closed = false)
private val mouthLine = path(50.6f, 50.2f, 58.3f, 50.2f, closed = false)
private val eyeRight = path(67.3f, 41.5f, 62f, 46.1f, 66.9f, 50.5f, closed = false)
private val IconGreen = Color(0xFF2BEA8C)
private val IconFace = Color(0xFF55F5A8)

/**
 * Colours of the traced hero (app-icon operative). The menu emblem, the NEON
 * OPERATIVE body in game and the shopkeeper all draw the same traced geometry
 * with one of these palettes.
 */
data class HeroPalette(
    val hoodTop: Color, val hoodMid: Color, val hoodBottom: Color, val facet: Color,
    val outline: Color, val opening: Color, val openingEdge: Color,
    /** Antenna tip, collar tab, glow. */
    val glow: Color,
    /** Ear-cup ring and collar button. */
    val accent: Color,
    val shieldEdge: Color, val shieldTop: Color, val shieldBottom: Color, val face: Color,
    /** Hue cycles over time (SPECTRUM keeper). */
    val spectrum: Boolean = false
) {
    fun at(time: Float): HeroPalette {
        if (!spectrum) return this
        val h = (time * 70f) % 360f
        val c = Color.hsv(h, 0.85f, 1f)
        val c2 = Color.hsv((h + 120f) % 360f, 0.8f, 1f)
        return copy(outline = c, glow = c2, accent = Color.hsv((h + 240f) % 360f, 0.8f, 1f), shieldEdge = c, face = c2,
            hoodTop = Color.hsv(h, 0.6f, 0.45f), hoodMid = Color.hsv(h, 0.6f, 0.28f), hoodBottom = Color.hsv(h, 0.6f, 0.16f),
            facet = Color.hsv(h, 0.5f, 0.6f).copy(alpha = 0.5f), openingEdge = Color.hsv(h, 0.6f, 0.4f))
    }

    companion object {
        /** The app icon: deep blue hood, cyan glow, neon-green face. */
        val ICON = HeroPalette(
            Color(0xFF2048B0), Color(0xFF122A70), Color(0xFF0A1A4A), Color(0xFF3A6BE0).copy(alpha = 0.55f),
            NeonBlue, Color(0xFF040A16), Color(0xFF1B3C9A), Cyan, Mint,
            IconGreen, Color(0xFF07231A), Color(0xFF020A07), IconFace
        )
        val KEEPER_GOLD = HeroPalette(
            Color(0xFFB8860B), Color(0xFF6B4A08), Color(0xFF2E1F04), Color(0xFFFFD86A).copy(alpha = 0.45f),
            Color(0xFFFFD426), Color(0xFF0E0A02), Color(0xFF6B4A08), Color(0xFFFFE680), Color(0xFFFFC94D),
            Color(0xFFFFD426), Color(0xFF2A2006), Color(0xFF0E0A02), Color(0xFFFFE680)
        )
        val KEEPER_TITANIUM = HeroPalette(
            Color(0xFFC8D4E0), Color(0xFF6B7A8A), Color(0xFF2A3340), Color(0xFFFFFFFF).copy(alpha = 0.4f),
            Color(0xFFEAF2FA), Color(0xFF0B0F14), Color(0xFF6B7A8A), Color(0xFFFFFFFF), Color(0xFFBFD0E0),
            Color(0xFFDCE8F2), Color(0xFF1C2430), Color(0xFF080B10), Color(0xFFF0F6FC)
        )
        /** Menacing: pitch-black hood, blood-red trim and eyes. */
        val KEEPER_BLACK = HeroPalette(
            Color(0xFF1C1C22), Color(0xFF0C0C10), Color(0xFF020203), Color(0xFF3A0A10).copy(alpha = 0.6f),
            Color(0xFF9B0F1F), Color(0xFF000000), Color(0xFF3A0A10), Color(0xFFFF2D3D), Color(0xFFFF2D3D),
            Color(0xFFB0101F), Color(0xFF140306), Color(0xFF000000), Color(0xFFFF2D3D)
        )
        val KEEPER_SPECTRUM = ICON.copy(spectrum = true)
    }
}

/** What the face shield shows. */
enum class HeroFace { ICON, KEEPER }

private val keeperFacePaint = android.graphics.Paint().apply {
    isAntiAlias = true
    textAlign = android.graphics.Paint.Align.CENTER
    typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
}

private fun DrawScope.drawFaceShield(skin: OperativeSkin, time: Float, pulse: Float) {
    // The default skin wears the icon's own neon green; any other skin tints it.
    val c = OperativeMark.colors(skin, time)
    val isDefault = skin.id == OperativeSkins.DEFAULT.id
    val p = if (isDefault) HeroPalette.ICON else HeroPalette.ICON.copy(shieldEdge = c.edge, face = c.face)
    drawHeroShield(p, HeroFace.ICON, time, pulse)
}

/** The traced face shield with either the >_< face or the shopkeeper's X,.,.X. */
private fun DrawScope.drawHeroShield(p: HeroPalette, face: HeroFace, time: Float, pulse: Float) {
    glowStroke(shield, p.shieldEdge, 2.8f, pulse * 0.8f, layers = 3)
    drawPath(shield, Brush.verticalGradient(listOf(p.shieldTop, p.shieldBottom), startY = 27f, endY = 66f))
    drawPath(shieldFacet, Color.White.copy(alpha = 0.035f))
    drawPath(shield, p.shieldEdge, style = Stroke(2.8f, join = StrokeJoin.Round))
    when (face) {
        HeroFace.ICON -> for (path in listOf(eyeLeft, mouthLine, eyeRight)) {
            drawPath(path, p.face.copy(alpha = 0.16f * pulse), style = Stroke(4.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, p.face, style = Stroke(2.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        HeroFace.KEEPER -> {
            val blink = ((time * 0.7f) % 1f) < 0.06f
            keeperFacePaint.textSize = 7.4f
            keeperFacePaint.color = p.face.toArgb()
            drawContext.canvas.nativeCanvas.drawText(if (blink) "-,.,.-" else "X,.,.X", 54.4f, 49.5f, keeperFacePaint)
        }
    }
}

/** Hood, opening, face and headset of the traced hero, in emblem units (100-unit grid). */
private fun DrawScope.drawHeroHead(p: HeroPalette, face: HeroFace, time: Float, pulse: Float) {
    glowStroke(hood, p.outline, 0.9f, pulse * 0.7f, layers = 3)
    drawPath(hood, Brush.verticalGradient(listOf(p.hoodTop, p.hoodMid, p.hoodBottom), startY = 11f, endY = 74f))
    drawPath(hoodFacet, p.facet)
    drawPath(hood, p.outline, style = Stroke(1.4f, join = StrokeJoin.Round))
    drawPath(hoodOpening, p.opening)
    drawPath(hoodOpening, p.openingEdge, style = Stroke(0.8f, join = StrokeJoin.Round))
    drawHeroShield(p, face, time, pulse)
}

private fun DrawScope.drawHeroHeadset(p: HeroPalette, time: Float, pulse: Float) {
    drawLine(p.outline, Offset(26.3f, 34f), Offset(26f, 19f), 1.6f, StrokeCap.Round)
    drawLine(p.hoodBottom, Offset(26.3f, 34f), Offset(26f, 19f), 0.7f, StrokeCap.Round)
    drawOval(p.hoodBottom, Offset(19f, 35f), Size(9f, 14f))
    drawOval(p.outline, Offset(19f, 35f), Size(9f, 14f), style = Stroke(0.9f))
    drawOval(p.accent.copy(alpha = 0.2f * pulse), Offset(19.8f, 37f), Size(6f, 10f), style = Stroke(2.6f))
    drawOval(p.accent, Offset(20.6f, 37.6f), Size(4.4f, 8.8f), style = Stroke(1.2f))
    val blink = 0.75f + 0.25f * sin(time * 4f)
    drawCircle(p.glow.copy(alpha = 0.18f * blink), 7f, Offset(26f, 17f))
    drawCircle(p.glow.copy(alpha = 0.3f * blink), 5f, Offset(26f, 17f))
    drawCircle(p.glow, 3.6f, Offset(26f, 17f), style = Stroke(1.1f))
    drawCircle(Color(0xFFE6FDFF).copy(alpha = 0.6f + 0.4f * blink), 2.4f, Offset(26f, 17f))
}

// Full-body extension of the traced hero (shoulders from the icon; cloak and legs below).
private val heroShoulders = path(16f, 82f, 22f, 66f, 28f, 60f, 47f, 67f, 51f, 76f, 62f, 68f, 74f, 62f, 84f, 70f, 88f, 82f)
private val heroCloak = path(28f, 78f, 78f, 78f, 74f, 98f, 62f, 106f, 44f, 106f, 32f, 98f)

/**
 * The traced hero standing, in emblem units: foot line at y = 118, centre x = 52.
 * [walk] −1..1 swings the legs. Callers scale/translate it into the world.
 */
fun DrawScope.drawHeroFigure(palette: HeroPalette, face: HeroFace, time: Float, walk: Float) {
    val p = palette.at(time)
    val pulse = 0.8f + 0.2f * sin(time * 2.2f)
    // Legs and boots.
    drawLine(p.hoodBottom, Offset(43f, 102f), Offset(43f - walk * 5f, 116f), 8f, StrokeCap.Round)
    drawLine(p.hoodMid, Offset(61f, 102f), Offset(61f + walk * 5f, 116f), 8f, StrokeCap.Round)
    drawCircle(p.glow, 3.6f, Offset(43f - walk * 5f, 117f))
    drawCircle(p.glow, 3.6f, Offset(61f + walk * 5f, 117f))
    // Sleeves, then the robe-like cloak.
    drawLine(p.outline, Offset(27f, 76f), Offset(22f, 96f), 9f, StrokeCap.Round)
    drawLine(p.hoodBottom, Offset(27f, 76f), Offset(22f, 96f), 6.5f, StrokeCap.Round)
    drawLine(p.outline, Offset(79f, 76f), Offset(84f, 96f), 9f, StrokeCap.Round)
    drawLine(p.hoodMid, Offset(79f, 76f), Offset(84f, 96f), 6.5f, StrokeCap.Round)
    drawPath(heroCloak, Brush.verticalGradient(listOf(p.hoodMid, p.hoodBottom), startY = 78f, endY = 106f))
    drawPath(heroCloak, p.outline, style = Stroke(1.2f, join = StrokeJoin.Round))
    drawLine(p.outline.copy(alpha = 0.6f), Offset(53f, 84f), Offset(53f, 104f), 1f)
    // Shoulders, strap, collar tab and button (from the icon).
    drawPath(heroShoulders, Brush.verticalGradient(listOf(p.hoodTop, p.hoodBottom), startY = 60f, endY = 84f))
    drawPath(heroShoulders, p.outline, style = Stroke(1.1f, join = StrokeJoin.Round))
    drawPath(strap, p.hoodBottom)
    drawPath(strap, p.outline.copy(alpha = 0.7f), style = Stroke(0.7f, join = StrokeJoin.Round))
    drawPath(collarTab, p.hoodBottom)
    drawPath(collarTab, p.glow, style = Stroke(1f, join = StrokeJoin.Round))
    // Head on top.
    drawHeroHead(p, face, time, pulse)
    drawCircle(p.accent.copy(alpha = 0.25f * pulse), 6f, Offset(42.7f, 73f))
    drawCircle(p.hoodBottom, 4.4f, Offset(42.7f, 73f))
    drawCircle(p.accent, 3.4f, Offset(42.7f, 73f))
    drawHeroHeadset(p, time, pulse)
}

private fun DrawScope.glowStroke(p: Path, color: Color, width: Float, glow: Float, layers: Int = 5) {
    for (i in layers downTo 1) {
        drawPath(p, color.copy(alpha = 0.07f * glow), style = Stroke(width + width * 0.9f * i, join = StrokeJoin.Round, cap = StrokeCap.Round))
    }
    drawPath(p, color, style = Stroke(width, join = StrokeJoin.Round, cap = StrokeCap.Round))
}

private fun DrawScope.drawEmblem(skin: OperativeSkin, time: Float) {
    val pulse = 0.8f + 0.2f * sin(time * 2.2f)

    // Data bits and circuit traces behind the frame.
    for ((x, y, c) in bits) drawRect(c.copy(alpha = 0.7f), Offset(x, y), Size(3f, 3f))
    for (t in traces) glowStroke(t, NeonBlue.copy(alpha = 0.85f), 0.7f, pulse * 0.6f, layers = 2)
    for (n in traceNodes) {
        drawCircle(Cyan.copy(alpha = 0.25f * pulse), 2.6f, n)
        drawCircle(Color(0xFF9DF4FF), 1.5f, n)
    }

    // Hexagon frame.
    drawPath(outerHex, Color(0xFF061024))
    glowStroke(outerHex, Cyan, 3f, pulse * 0.8f, layers = 4)
    drawPath(innerHex, Color(0xFF0A1A3A))
    drawPath(innerHex, NeonBlue.copy(alpha = 0.6f), style = Stroke(0.6f))

    // Hood (traced).
    val p = HeroPalette.ICON
    glowStroke(hood, p.outline, 0.9f, pulse * 0.7f, layers = 3)
    drawPath(hood, Brush.verticalGradient(listOf(p.hoodTop, p.hoodMid, p.hoodBottom), startY = 11f, endY = 74f))
    drawPath(hoodFacet, p.facet)
    drawPath(hood, p.outline, style = Stroke(1.4f, join = StrokeJoin.Round))
    drawPath(hoodOpening, p.opening)
    drawPath(hoodOpening, p.openingEdge, style = Stroke(0.8f, join = StrokeJoin.Round))

    clipPath(innerHex) {
        // Shoulders and collar.
        drawPath(shoulders, Brush.verticalGradient(listOf(Color(0xFF1C3A8F), Color(0xFF0C1C4C)), startY = 62f, endY = 100f))
        drawPath(shoulders, NeonBlue, style = Stroke(0.9f, join = StrokeJoin.Round))
        drawPath(strap, Color(0xFF0E1E52))
        drawPath(strap, NeonBlue.copy(alpha = 0.7f), style = Stroke(0.6f, join = StrokeJoin.Round))
        drawPath(collarTab, Color(0xFF0E1E52))
        drawPath(collarTab, Cyan, style = Stroke(0.9f, join = StrokeJoin.Round))
    }

    // The player's skin as the face shield.
    drawFaceShield(skin, time, pulse)

    // Collar button.
    drawCircle(Mint.copy(alpha = 0.25f * pulse), 6f, Offset(42.7f, 73f))
    drawCircle(Color(0xFF0B2A2A), 4.4f, Offset(42.7f, 73f))
    drawCircle(Brush.radialGradient(listOf(Color(0xFFB8FFE2), Mint), Offset(41.9f, 72.2f), 4f), 3.4f, Offset(42.7f, 73f))

    // Headset: ear cup, then the antenna with its glowing tip.
    drawHeroHeadset(HeroPalette.ICON, time, pulse)
}
