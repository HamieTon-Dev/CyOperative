package com.cyberoperative.game.ui.game

import androidx.compose.ui.graphics.drawscope.withTransform

import com.cyberoperative.game.ui.common.drawHeroFigure

import com.cyberoperative.game.ui.common.HeroFace

import com.cyberoperative.game.ui.common.HeroPalette

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
    /** The app icon as a whole operative (owner, 2026-10-08). Since 0.17.0 the TECHNICIAN's body, no longer sold. */
    NEON("neon_operative", "NEON OPERATIVE", "The app icon come to life: faceted neon hood, antenna, glowing >_< shield face."),
    /** PACKET MAGE (0.17.0): robed code-caster with a fire-orb staff. */
    MAGE("mage", "PACKET MAGE", "Robed code-caster under a deep hood, a staff crowned with packet fire.");

    /** Bodies that must be bought before they can be selected (none since classes, 0.17.0). */
    val premium: Boolean get() = false

    companion object {
        fun byId(id: String) = entries.firstOrNull { it.id == id } ?: AGENT
    }
}

object OperativeFigures {

    private val path = Path()

    /** Draw the Plasma Beam arm cannon instead of the sidearm (set per figure draw). */
    private var heavy = false

    /**
     * @param facing aim angle in radians (also decides which way the body faces)
     * @param moving walking animation on/off
     */
    fun DrawScope.drawFigure(
        style: BodyStyle, skin: OperativeSkin, x: Float, footY: Float, u: Float,
        facing: Float, moving: Boolean, time: Float, alpha: Float = 1f, bigGun: Boolean = false,
        look: com.cyberoperative.game.save.CharacterLook? = null
    ) {
        heavy = bigGun
        ClassFigures.part = look ?: com.cyberoperative.game.save.CharacterLook()
        // Class bodies traced from the owner's concept sheet (0.17.1); the TECHNICIAN keeps the icon hero.
        when (style) {
            BodyStyle.AGENT -> with(ClassFigures) { fieldAgent(skin, x, footY, u, facing, moving, time, alpha) }
            BodyStyle.MECH -> with(ClassFigures) { juggernaut(skin, x, footY, u, facing, moving, time, alpha) }
            BodyStyle.RUNNER -> with(ClassFigures) { phantom(skin, x, footY, u, facing, moving, time, alpha) }
            BodyStyle.MAGE -> with(ClassFigures) { packetMage(skin, x, footY, u, facing, moving, time, alpha) }
            // The icon look; a colour skin re-tints the hood, trim and face.
            BodyStyle.NEON -> neonOperative(x, footY, u, facing, moving, time, alpha, palette = HeroPalette.forSkin(skin)) {
                with(ClassFigures) { accessories(TECH_PARTS, look(skin, time), 0.8f + 0.2f * sin(time * 2.2f), 0f, 0f, time) }
            }
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

    /**
     * The upgrade-shop keeper (owner, 2026-10-08): the main-menu hooded operative,
     * but with a gold-rimmed shield and the face X,.,.X instead of >_<.
     */
    fun DrawScope.drawShopkeeper(x: Float, footY: Float, u: Float, time: Float, palette: HeroPalette = HeroPalette.KEEPER_GOLD) {
        neonOperative(x, footY, u, MathUtil.PI / 2f, false, time, 1f, keeper = true, palette = palette)
    }

    private val neonCyan = Color(0xFF22D3FF)
    private val neonHood = Color(0xFF1B3F9A)
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

    /** Emblem units → world: the traced hero is 118 units to the feet; 0.66u per unit. */
    private const val HERO_SCALE = 0.66f

    /**
     * NEON OPERATIVE (and the shopkeeper): the app-icon hero traced to the
     * icon's exact proportions — the same geometry as the main-menu emblem —
     * standing on a cloak and legs. Mirrors to face left.
     */
    private fun DrawScope.neonOperative(
        x: Float, foot: Float, u: Float, facing: Float, moving: Boolean, time: Float, alpha: Float,
        keeper: Boolean = false, palette: HeroPalette = HeroPalette.ICON,
        extra: (DrawScope.() -> Unit)? = null
    ) {
        val breathe = 0.7f + 0.3f * sin(time * 2.2f)
        val dir = if (keeper) 1f else if (cos(facing) < 0f) -1f else 1f
        val walk = if (moving) sin(time * 12f) else 0f
        val bob = if (moving) abs(sin(time * 12f)) * 1.4f * u else sin(time * 2f) * 0.5f * u
        val f = foot - bob
        val p = palette.at(time)
        // Floor shadow and aura in the hero's glow colour.
        drawOval(Color.Black.copy(alpha = 0.35f * alpha), Offset(x - 18f * u, foot - 4f * u), Size(36f * u, 9f * u))
        drawOval(p.glow.copy(alpha = 0.22f * breathe * alpha), Offset(x - 24f * u, foot - 7f * u), Size(48f * u, 14f * u))
        drawCircle(p.glow.copy(alpha = 0.07f * breathe * alpha), 38f * u, Offset(x, f - 38f * u))
        val k = HERO_SCALE * u
        val layer = alpha < 0.99f
        if (layer) drawContext.canvas.saveLayer(
            androidx.compose.ui.geometry.Rect(x - 60f * u, f - 90f * u, x + 60f * u, foot + 10f * u),
            androidx.compose.ui.graphics.Paint().apply { this.alpha = alpha }
        )
        withTransform({
            translate(x, f)
            scale(dir * k, k, Offset.Zero)
            translate(-52f, -118f)
        }) {
            drawHeroFigure(palette, if (keeper) HeroFace.KEEPER else HeroFace.ICON, time, walk)
            extra?.invoke(this)
        }
        if (layer) drawContext.canvas.restore()
        // Blaster on the facing side, at the icon's right shoulder.
        // No handheld blaster on the TECHNICIAN (owner, 0.43.1); the Plasma Beam arm cannon still shows.
        if (!keeper && heavy) gun(x + dir * 21f * k, f - 44f * k, u, facing, neonCyan.copy(alpha = alpha), neonGreen.copy(alpha = alpha), neonHood.copy(alpha = alpha))
    }
}

/** Animated preview of a body design (operative screen, design board). */
@androidx.compose.runtime.Composable
fun FigurePreview(
    style: BodyStyle, skin: OperativeSkin, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier, moving: Boolean = false,
    look: com.cyberoperative.game.save.CharacterLook? = null, turn: Float = 0f
) {
    var time by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    androidx.compose.runtime.LaunchedEffect(style, skin.id) {
        val start = androidx.compose.runtime.withFrameNanos { it }
        while (true) androidx.compose.runtime.withFrameNanos { time = (it - start) / 1e9f }
    }
    androidx.compose.foundation.Canvas(modifier) {
        val u = size.height / 80f
        // Fits the taller of width / height (wide boxes no longer clip the figure).
        val uu = minOf(u, size.width / 60f)
        with(OperativeFigures) { drawFigure(style, skin, size.width / 2f, size.height * 0.9f, uu, -0.4f + turn, moving, time, look = look) }
    }
}
