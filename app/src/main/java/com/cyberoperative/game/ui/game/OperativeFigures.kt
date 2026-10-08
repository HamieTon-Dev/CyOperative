package com.cyberoperative.game.ui.game

import com.cyberoperative.game.core.MathUtil

import androidx.compose.ui.graphics.toArgb

import androidx.compose.ui.graphics.nativeCanvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.cyberoperative.game.data.OperativeSkin
import com.cyberoperative.game.data.SkinEffect
import com.cyberoperative.game.ui.common.OperativeMark
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Full-body 2.5D operative designs. All three keep the >_< face and take
 * their colours from the equipped [OperativeSkin]:
 *  edge = trim / glow, face = >_< and lights, body = armour.
 *
 * Every figure is drawn standing on its feet at ([x], [footY]) and sized by
 * [u] (one design unit; the in-game operative uses u = 1 → ~66 units tall).
 */
enum class BodyStyle(val id: String, val label: String, val blurb: String) {
    AGENT("agent", "FIELD AGENT", "Shield-head specialist in light armour with a sidearm blaster."),
    MECH("mech", "SENTINEL MECH", "Heavy frame, monitor head showing >_<, shoulder cannon."),
    RUNNER("runner", "SHADOW RUNNER", "Hooded coat over the shield face, hovers on thrusters."),
    /** The app icon as a whole operative (owner, 2026-10-08). Sold in the store. */
    NEON("neon_operative", "NEON OPERATIVE", "The app icon come to life: faceted neon hood, antenna, glowing >_< shield face.");

    /** Bodies that must be bought before they can be selected. */
    val premium: Boolean get() = this == NEON

    companion object {
        fun byId(id: String) = entries.firstOrNull { it.id == id } ?: AGENT
    }
}

object OperativeFigures {

    private val path = Path()

    /** Draw the Plasma Beam arm cannon instead of the sidearm (set per figure draw). */
    private var heavy = false

    private fun shade(c: Color, k: Float) = Color(c.red * k, c.green * k, c.blue * k, c.alpha)

    private fun lighten(c: Color, k: Float) =
        Color(c.red + (1f - c.red) * k, c.green + (1f - c.green) * k, c.blue + (1f - c.blue) * k, c.alpha)

    /**
     * @param facing aim angle in radians (also decides which way the body faces)
     * @param moving walking animation on/off
     */
    fun DrawScope.drawFigure(
        style: BodyStyle, skin: OperativeSkin, x: Float, footY: Float, u: Float,
        facing: Float, moving: Boolean, time: Float, alpha: Float = 1f, bigGun: Boolean = false
    ) {
        heavy = bigGun
        if (style == BodyStyle.NEON) {
            // Its own fixed look (the icon), independent of colour skins.
            neonOperative(x, footY, u, facing, moving, time, alpha)
            return
        }
        val c = OperativeMark.colors(skin, time)
        val edge = c.edge.copy(alpha = alpha)
        val face = c.face.copy(alpha = alpha)
        // Armour is a lifted version of the skin's dark body colour so it reads on the floor.
        val armor = (if (skin.effect == com.cyberoperative.game.data.SkinEffect.NEON)
            // Neon: deep blue armour like the icon's hood.
            Color(0xFF173A86) else lighten(c.body, 0.18f)).copy(alpha = alpha)
        val armorDark = shade(armor, 0.6f)
        val dir = if (cos(facing) < 0f) -1f else 1f
        val walk = if (moving) sin(time * 12f) else 0f
        val bob = if (moving) abs(sin(time * 12f)) * 1.5f * u else sin(time * 2f) * 0.6f * u

        // Floor shadow.
        drawOval(Color.Black.copy(alpha = 0.35f * alpha), Offset(x - 16f * u, footY - 4f * u), Size(32f * u, 9f * u))
        if (skin.effect == com.cyberoperative.game.data.SkinEffect.NEON) {
            // Neon skin: the whole operative stands in a soft cyan aura and lights the floor.
            val breathe = 0.7f + 0.3f * sin(time * 2.2f)
            drawOval(edge.copy(alpha = 0.22f * breathe * alpha), Offset(x - 22f * u, footY - 7f * u), Size(44f * u, 14f * u))
            drawCircle(edge.copy(alpha = 0.10f * breathe * alpha), 30f * u, Offset(x, footY - 30f * u))
            drawCircle(edge.copy(alpha = 0.07f * breathe * alpha), 40f * u, Offset(x, footY - 30f * u))
        }

        when (style) {
            BodyStyle.AGENT -> agent(x, footY - bob, u, dir, walk, facing, armor, armorDark, edge, face, skin, time, alpha)
            BodyStyle.MECH -> mech(x, footY - bob * 0.5f, u, dir, walk, facing, armor, armorDark, edge, face, alpha)
            BodyStyle.RUNNER -> runner(x, footY, u, dir, moving, facing, armor, armorDark, edge, face, skin, time, alpha)
            BodyStyle.NEON -> Unit
        }
    }

    private fun DrawScope.limb(a: Offset, b: Offset, w: Float, color: Color) {
        drawLine(color, a, b, w, cap = StrokeCap.Round)
    }

    private fun DrawScope.gun(sx: Float, sy: Float, u: Float, facing: Float, edge: Color, face: Color, armor: Color, length: Float = 15f) {
        if (heavy) { cannon(sx, sy, u, facing, edge, armor); return }
        val ex = sx + cos(facing) * length * u
        val ey = sy + sin(facing) * length * u
        limb(Offset(sx, sy), Offset(ex, ey), 4.5f * u, armor)
        limb(Offset(sx + cos(facing) * 8f * u, sy + sin(facing) * 8f * u), Offset(ex + cos(facing) * 5f * u, ey + sin(facing) * 5f * u), 4f * u, edge)
        drawCircle(face.copy(alpha = face.alpha * 0.8f), 2.2f * u, Offset(ex + cos(facing) * 6f * u, ey + sin(facing) * 6f * u))
    }

    /** The Plasma Beam arm cannon: a thick barrel with glowing coils and a hot muzzle. */
    private fun DrawScope.cannon(sx: Float, sy: Float, u: Float, facing: Float, edge: Color, armor: Color) {
        val dx = cos(facing)
        val dy = sin(facing)
        val ex = sx + dx * 20f * u
        val ey = sy + dy * 20f * u
        limb(Offset(sx, sy), Offset(ex, ey), 9f * u, Color(0xFF0D1A3A).copy(alpha = armor.alpha))
        limb(Offset(sx + dx * 2f * u, sy + dy * 2f * u), Offset(ex, ey), 6.5f * u, armor)
        for (k in 1..3) {
            val t = k / 4f
            drawCircle(edge, 2.2f * u, Offset(sx + (ex - sx) * t, sy + (ey - sy) * t))
        }
        drawCircle(Color(0xFF22D3FF).copy(alpha = 0.3f * armor.alpha), 6f * u, Offset(ex, ey))
        drawCircle(Color(0xFFE6FBFF).copy(alpha = armor.alpha), 3.2f * u, Offset(ex, ey))
    }

    // --- NEON OPERATIVE (the app icon) ----------------------------------------

    /** The icon's shield face: green edge, mint >_<, near-black plate. */
    private val neonFace = OperativeSkin("neon_face", "", "", 0xFF19E07A, 0xFF46FFB4, 0xFF02140C, SkinEffect.NEON)
    /** Blank shield (face drawn as text) for the shopkeeper. */
    private val keeperFace = OperativeSkin("keeper_face", "", "", 0xFFFFD426, 0xFF140F02, 0xFF140F02, SkinEffect.NONE)
    private val keeperPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
    }

    /**
     * The upgrade-shop keeper (owner, 2026-10-08): the main-menu hooded operative,
     * but with a gold-rimmed shield and the face X,.,.X instead of >_<.
     */
    fun DrawScope.drawShopkeeper(x: Float, footY: Float, u: Float, time: Float) {
        neonOperative(x, footY, u, MathUtil.PI / 2f, false, time, 1f, keeper = true)
    }

    private val neonCyan = Color(0xFF22D3FF)
    private val neonHood = Color(0xFF1B3F9A)
    private val neonHoodDark = Color(0xFF0E2560)
    private val neonHoodShade = Color(0xFF0A1A46)
    private val neonGreen = Color(0xFF1FF29A)

    /** Polygon helper in design units relative to ([ox], [oy]). */
    private fun poly(ox: Float, oy: Float, u: Float, vararg p: Float) {
        path.reset()
        var i = 0
        while (i < p.size) {
            val px = ox + p[i] * u
            val py = oy + p[i + 1] * u
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            i += 2
        }
        path.close()
    }

    private fun DrawScope.neonOperative(x: Float, foot: Float, u: Float, facing: Float, moving: Boolean, time: Float, alpha: Float, keeper: Boolean = false) {
        val breathe = 0.7f + 0.3f * sin(time * 2.2f)
        val cyan = neonCyan.copy(alpha = alpha)
        val hood = neonHood.copy(alpha = alpha)
        val hoodDark = neonHoodDark.copy(alpha = alpha)
        val green = neonGreen.copy(alpha = alpha)
        val dir = if (cos(facing) < 0f) -1f else 1f
        val walk = if (moving) sin(time * 12f) else 0f
        val bob = if (moving) abs(sin(time * 12f)) * 1.4f * u else sin(time * 2f) * 0.5f * u
        val f = foot - bob

        // Floor shadow and neon aura.
        drawOval(Color.Black.copy(alpha = 0.35f * alpha), Offset(x - 16f * u, foot - 4f * u), Size(32f * u, 9f * u))
        drawOval(cyan.copy(alpha = 0.22f * breathe * alpha), Offset(x - 22f * u, foot - 7f * u), Size(44f * u, 14f * u))
        drawCircle(cyan.copy(alpha = 0.08f * breathe * alpha), 36f * u, Offset(x, f - 36f * u))

        // Legs (dark armour, cyan boots).
        val hip = f - 13f * u
        limb(Offset(x - 5f * u, hip), Offset(x - 5f * u - walk * 3f * u, f - (if (walk > 0) walk * 2f * u else 0f)), 6.5f * u, hoodDark)
        limb(Offset(x + 5f * u, hip), Offset(x + 5f * u + walk * 3f * u, f - (if (walk < 0) -walk * 2f * u else 0f)), 6.5f * u, hood)
        drawCircle(cyan, 2.8f * u, Offset(x - 5f * u - walk * 3f * u, f))
        drawCircle(cyan, 2.8f * u, Offset(x + 5f * u + walk * 3f * u, f))

        // Cloak / torso: tapered, faceted like the hood.
        val sh = f - 36f * u // shoulder line
        poly(x, sh, u, -15f, 0f, 15f, 0f, 12f, 23f, -12f, 23f)
        drawPath(path, hoodDark)
        poly(x, sh, u, -15f, 0f, 0f, 2f, 0f, 23f, -12f, 23f)
        drawPath(path, neonHoodShade.copy(alpha = alpha))
        poly(x, sh, u, -15f, 0f, 15f, 0f, 12f, 23f, -12f, 23f)
        drawPath(path, cyan, style = Stroke(1.6f * u, join = StrokeJoin.Round))
        drawLine(cyan.copy(alpha = 0.6f * alpha), Offset(x, sh + 3f * u), Offset(x, sh + 21f * u), 1.3f * u)

        // Off arm, then the blaster arm on the facing side.
        limb(Offset(x - dir * 13f * u, sh + 3f * u), Offset(x - dir * 15f * u, sh + 15f * u), 5f * u, hoodDark)

        // Shoulder plate (left) with the green power button, raised collar (right).
        poly(x, sh, u, -19f, -2f, -5f, -5f, -3f, 5f, -17f, 7f)
        drawPath(path, hood)
        drawPath(path, cyan, style = Stroke(1.6f * u, join = StrokeJoin.Round))
        drawCircle(Color(0xFF041A10).copy(alpha = alpha), 3.6f * u, Offset(x - 11f * u, sh + 1.5f * u))
        drawCircle(green, 2.7f * u, Offset(x - 11f * u, sh + 1.5f * u))
        drawCircle(Color.White.copy(alpha = 0.5f * alpha), 0.9f * u, Offset(x - 11.8f * u, sh + 0.6f * u))
        poly(x, sh, u, 5f, -8f, 16f, -5f, 18f, 6f, 6f, 4f)
        drawPath(path, hood)
        drawPath(path, cyan, style = Stroke(1.6f * u, join = StrokeJoin.Round))

        // Antenna (behind the hood, left), with a glowing tip.
        val hc = Offset(x, sh - 15f * u) // hood centre
        drawLine(cyan, Offset(hc.x - 11f * u, hc.y - 10f * u), Offset(hc.x - 15f * u, hc.y - 26f * u), 1.8f * u, cap = StrokeCap.Round)
        drawCircle(cyan.copy(alpha = 0.25f * breathe * alpha), 5.5f * u, Offset(hc.x - 15.5f * u, hc.y - 28f * u))
        drawCircle(cyan, 3.4f * u, Offset(hc.x - 15.5f * u, hc.y - 28f * u), style = Stroke(1.4f * u))
        drawCircle(Color(0xFFE6FBFF).copy(alpha = alpha), 2f * u, Offset(hc.x - 15.5f * u, hc.y - 28f * u))

        // Hood: faceted, pointed top, broad base.
        poly(hc.x, hc.y, u, 0f, -20f, 11f, -15f, 15f, -3f, 13f, 10f, 7f, 15f, -7f, 15f, -13f, 10f, -15f, -3f, -11f, -15f)
        drawPath(path, cyan.copy(alpha = 0.18f * breathe * alpha), style = Stroke(6f * u, join = StrokeJoin.Round))
        drawPath(path, hood)
        // Facets: a darker right flank and a lit left crown.
        poly(hc.x, hc.y, u, 0f, -20f, 11f, -15f, 15f, -3f, 13f, 10f, 7f, 15f, 3f, -6f)
        drawPath(path, hoodDark)
        poly(hc.x, hc.y, u, 0f, -20f, -11f, -15f, -6f, -9f)
        drawPath(path, Color(0xFF2A5BC4).copy(alpha = alpha))
        poly(hc.x, hc.y, u, 0f, -20f, 11f, -15f, 15f, -3f, 13f, 10f, 7f, 15f, -7f, 15f, -13f, 10f, -15f, -3f, -11f, -15f)
        drawPath(path, cyan, style = Stroke(1.8f * u, join = StrokeJoin.Round))
        // Hood opening (dark) holding the shield face.
        poly(hc.x, hc.y, u, 0f, -12f, 9f, -8f, 10.5f, 2f, 7f, 12f, -7f, 12f, -10.5f, 2f, -9f, -8f)
        drawPath(path, Color(0xFF040818).copy(alpha = alpha))
        if (keeper) {
            with(OperativeMark) { drawOperative(keeperFace, time, Offset(hc.x, hc.y + 1f * u), 19f * u, alpha) }
            val blink = ((time * 0.7f) % 1f) < 0.06f
            keeperPaint.textSize = 6.2f * u
            keeperPaint.color = (if (blink) Color(0xFFFFF3B0) else Color(0xFFFFD426)).toArgb()
            drawContext.canvas.nativeCanvas.drawText(if (blink) "-,.,.-" else "X,.,.X", hc.x, hc.y + 3.5f * u, keeperPaint)
        } else {
            with(OperativeMark) { drawOperative(neonFace, time, Offset(hc.x, hc.y + 1f * u), 19f * u, alpha) }
        }

        // Earpiece ring on the left of the hood.
        drawOval(hood, Offset(hc.x - 18f * u, hc.y - 5f * u), Size(6.5f * u, 11f * u))
        drawOval(cyan, Offset(hc.x - 18f * u, hc.y - 5f * u), Size(6.5f * u, 11f * u), style = Stroke(1.5f * u))
        drawOval(Color(0xFF040818).copy(alpha = alpha), Offset(hc.x - 16.4f * u, hc.y - 2.5f * u), Size(3.3f * u, 6f * u))

        if (!keeper) gun(x + dir * 12f * u, sh + 6f * u, u, facing, cyan, green, hood)
        else limb(Offset(x + 13f * u, sh + 3f * u), Offset(x + 15f * u, sh + 15f * u), 5f * u, hoodDark)
    }

    // --- FIELD AGENT -------------------------------------------------------

    private fun DrawScope.agent(
        x: Float, foot: Float, u: Float, dir: Float, walk: Float, facing: Float,
        armor: Color, armorDark: Color, edge: Color, face: Color, skin: OperativeSkin, time: Float, alpha: Float
    ) {
        val hip = foot - 14f * u
        // Legs (back leg darker for depth).
        limb(Offset(x - 4.5f * u, hip), Offset(x - 4.5f * u - walk * 3f * u, foot - (if (walk > 0) walk * 2f * u else 0f)), 6f * u, armorDark)
        limb(Offset(x + 4.5f * u, hip), Offset(x + 4.5f * u + walk * 3f * u, foot - (if (walk < 0) -walk * 2f * u else 0f)), 6f * u, armor)
        // Boots
        drawCircle(edge, 2.6f * u, Offset(x - 4.5f * u - walk * 3f * u, foot))
        drawCircle(edge, 2.6f * u, Offset(x + 4.5f * u + walk * 3f * u, foot))
        // Backpack antenna (behind the body).
        limb(Offset(x - dir * 8f * u, hip - 18f * u), Offset(x - dir * 13f * u, hip - 32f * u), 1.6f * u, edge)
        drawCircle(face, 1.8f * u, Offset(x - dir * 13f * u, hip - 32f * u))
        // Torso: front face + top highlight for a solid look.
        val tl = Offset(x - 11f * u, hip - 21f * u)
        drawRoundRect(armorDark, tl + Offset(0f, 2f * u), Size(22f * u, 21f * u), CornerRadius(6f * u))
        drawRoundRect(armor, tl, Size(22f * u, 19f * u), CornerRadius(6f * u))
        drawRoundRect(edge, tl, Size(22f * u, 19f * u), CornerRadius(6f * u), style = Stroke(1.6f * u))
        // Belt + chest light.
        drawLine(edge, Offset(x - 10f * u, hip - 4f * u), Offset(x + 10f * u, hip - 4f * u), 2f * u)
        val pulse = 0.6f + 0.4f * sin(time * 4f)
        drawCircle(face.copy(alpha = face.alpha * pulse), 2.6f * u, Offset(x, hip - 12f * u))
        // Off arm.
        limb(Offset(x - dir * 10f * u, hip - 17f * u), Offset(x - dir * 12f * u, hip - 6f * u), 4.5f * u, armorDark)
        // Head: the >_< shield.
        with(OperativeMark) { drawOperative(skin, time, Offset(x, hip - 33f * u), 26f * u, alpha) }
        // Gun arm on top.
        gun(x + dir * 9f * u, hip - 16f * u, u, facing, edge, face, armor)
    }

    // --- SENTINEL MECH -----------------------------------------------------

    private fun DrawScope.mech(
        x: Float, foot: Float, u: Float, dir: Float, walk: Float, facing: Float,
        armor: Color, armorDark: Color, edge: Color, face: Color, alpha: Float
    ) {
        val hip = foot - 11f * u
        // Stubby legs with foot pads.
        for (si in 0..1) {
            val side = if (si == 0) -1f else 1f
            val lift = if (walk * side > 0) walk * side * 2.5f * u else 0f
            val col = if (side < 0) armorDark else armor
            drawRoundRect(col, Offset(x + side * 8f * u - 4f * u, hip), Size(8f * u, 9f * u - lift), CornerRadius(2f * u))
            drawRoundRect(edge, Offset(x + side * 8f * u - 6f * u, foot - 3f * u - lift), Size(12f * u, 4f * u), CornerRadius(2f * u))
        }
        // Body block.
        val tl = Offset(x - 15f * u, hip - 22f * u)
        drawRoundRect(armorDark, tl + Offset(0f, 3f * u), Size(30f * u, 22f * u), CornerRadius(5f * u))
        drawRoundRect(armor, tl, Size(30f * u, 20f * u), CornerRadius(5f * u))
        drawRoundRect(edge, tl, Size(30f * u, 20f * u), CornerRadius(5f * u), style = Stroke(1.8f * u))
        // Chest vents.
        for (i in 0 until 3) drawLine(edge.copy(alpha = edge.alpha * 0.6f), Offset(x - 8f * u, hip - 16f * u + i * 4f * u), Offset(x + 8f * u, hip - 16f * u + i * 4f * u), 1.2f * u)
        // Neck + monitor head.
        drawRect(armorDark, Offset(x - 3f * u, hip - 26f * u), Size(6f * u, 5f * u))
        val head = Offset(x - 15f * u, hip - 48f * u)
        drawRoundRect(armorDark, head + Offset(0f, 2f * u), Size(30f * u, 24f * u), CornerRadius(5f * u))
        drawRoundRect(armor, head, Size(30f * u, 22f * u), CornerRadius(5f * u))
        drawRoundRect(edge, head, Size(30f * u, 22f * u), CornerRadius(5f * u), style = Stroke(1.8f * u))
        // Screen with the >_< face.
        drawRoundRect(Color(0xFF020806).copy(alpha = alpha), head + Offset(3.5f * u, 3.5f * u), Size(23f * u, 15f * u), CornerRadius(3f * u))
        val cx = x
        val cy = head.y + 11f * u
        val st = Stroke(2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        path.reset(); path.moveTo(cx - 9f * u, cy - 3.5f * u); path.lineTo(cx - 5.5f * u, cy); path.lineTo(cx - 9f * u, cy + 3.5f * u)
        drawPath(path, face, style = st)
        path.reset(); path.moveTo(cx + 9f * u, cy - 3.5f * u); path.lineTo(cx + 5.5f * u, cy); path.lineTo(cx + 9f * u, cy + 3.5f * u)
        drawPath(path, face, style = st)
        drawLine(face, Offset(cx - 2.5f * u, cy + 3.5f * u), Offset(cx + 2.5f * u, cy + 3.5f * u), 2.2f * u, cap = StrokeCap.Round)
        // Antenna.
        limb(Offset(x + dir * 9f * u, head.y), Offset(x + dir * 12f * u, head.y - 7f * u), 1.5f * u, edge)
        drawCircle(face, 1.6f * u, Offset(x + dir * 12f * u, head.y - 7f * u))
        // Shoulder cannon.
        gun(x + dir * 14f * u, hip - 18f * u, u, facing, edge, face, armor, length = 13f)
        drawCircle(armor, 4.5f * u, Offset(x + dir * 14f * u, hip - 18f * u))
        drawCircle(edge, 4.5f * u, Offset(x + dir * 14f * u, hip - 18f * u), style = Stroke(1.4f * u))
    }

    // --- SHADOW RUNNER -----------------------------------------------------

    private fun DrawScope.runner(
        x: Float, foot: Float, u: Float, dir: Float, moving: Boolean, facing: Float,
        armor: Color, armorDark: Color, edge: Color, face: Color, skin: OperativeSkin, time: Float, alpha: Float
    ) {
        val hover = 5f * u + sin(time * 3f) * 1.2f * u
        val hem = foot - hover
        // Thruster glow instead of legs.
        val flick = 0.7f + 0.3f * sin(time * 30f)
        drawOval(face.copy(alpha = 0.35f * flick * alpha), Offset(x - 9f * u, hem - 1f * u), Size(18f * u, 7f * u + hover * 0.6f))
        drawOval(edge.copy(alpha = 0.6f * flick * alpha), Offset(x - 5f * u, hem), Size(10f * u, 4f * u + hover * 0.4f))
        // Coat: trapezoid that flares and trails when moving.
        val sway = if (moving) -dir * 4f * u else sin(time * 1.5f) * 1f * u
        val shoulderY = hem - 26f * u
        path.reset()
        path.moveTo(x - 9f * u, shoulderY)
        path.lineTo(x + 9f * u, shoulderY)
        path.lineTo(x + 14f * u + sway, hem)
        path.lineTo(x + 2f * u + sway * 0.5f, hem - 3f * u)
        path.lineTo(x - 14f * u + sway, hem)
        path.close()
        drawPath(path, armorDark)
        path.reset()
        path.moveTo(x - 9f * u, shoulderY)
        path.lineTo(x + 9f * u, shoulderY)
        path.lineTo(x + 12f * u + sway, hem - 2f * u)
        path.lineTo(x - 12f * u + sway, hem - 2f * u)
        path.close()
        drawPath(path, armor)
        drawPath(path, edge, style = Stroke(1.5f * u, join = StrokeJoin.Round))
        // Coat seam light.
        drawLine(face.copy(alpha = face.alpha * 0.7f), Offset(x, shoulderY + 3f * u), Offset(x + sway * 0.6f, hem - 3f * u), 1.4f * u)
        // Hood framing the shield face.
        drawOval(armorDark, Offset(x - 15f * u, shoulderY - 27f * u), Size(30f * u, 30f * u))
        drawOval(edge, Offset(x - 15f * u, shoulderY - 27f * u), Size(30f * u, 30f * u), style = Stroke(1.4f * u))
        with(OperativeMark) { drawOperative(skin, time, Offset(x, shoulderY - 11f * u), 21f * u, alpha) }
        // Arm blaster.
        gun(x + dir * 8f * u, shoulderY + 5f * u, u, facing, edge, face, armor, length = 13f)
    }
}

/** Animated preview of a body design (operative screen, design board). */
@androidx.compose.runtime.Composable
fun FigurePreview(style: BodyStyle, skin: OperativeSkin, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier, moving: Boolean = false) {
    var time by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    androidx.compose.runtime.LaunchedEffect(style, skin.id) {
        val start = androidx.compose.runtime.withFrameNanos { it }
        while (true) androidx.compose.runtime.withFrameNanos { time = (it - start) / 1e9f }
    }
    androidx.compose.foundation.Canvas(modifier) {
        val u = size.height / 80f
        with(OperativeFigures) { drawFigure(style, skin, size.width / 2f, size.height * 0.9f, u, -0.4f, moving, time) }
    }
}
