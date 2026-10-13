package com.cyberoperative.game.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import com.cyberoperative.game.core.MathUtil
import com.cyberoperative.game.data.OperativeSkin
import com.cyberoperative.game.data.OperativeSkins
import com.cyberoperative.game.ui.common.HeroPalette
import com.cyberoperative.game.ui.common.OperativeMark
import com.cyberoperative.game.ui.common.drawHeroHelmet
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Class bodies traced from the owner's concept sheet (2026-10-11): FIELD AGENT, JUGGERNAUT,
 * PHANTOM and PACKET MAGE. Each is drawn in the hero's design grid (feet at y = 118, centre
 * x = 52, facing right) and placed/mirrored into the world like the TECHNICIAN's icon hero.
 *
 * Colours come from the skin: dark plates with neon trim (DEFAULT green, PURPLE violet), or
 * gunmetal-grey plates with steel trim (TITANIUM).
 */
internal object ClassFigures {

    /** Emblem units → world: same scale as the TECHNICIAN (118 units to the feet). */
    private const val SCALE = 0.66f

    private val path = Path()

    class Look(
        val plateLight: Color, val plateMid: Color, val plateDark: Color,
        val trim: Color, val face: Color, val visor: Color, val hero: HeroPalette
    )

    private fun mix(a: Color, b: Color, t: Float) =
        Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)

    /** [cobaltTitanium]: PHANTOM and PACKET MAGE wear TITANIUM as cobalt blue (owner, 2026-10-11). */
    fun look(skin: OperativeSkin, time: Float, cobaltTitanium: Boolean = false): Look {
        val c = OperativeMark.colors(skin, time)
        if (skin.id == OperativeSkins.TITANIUM.id && cobaltTitanium) {
            return Look(
                Color(0xFF3A6AD8), Color(0xFF22479F), Color(0xFF10235A),
                Color(0xFF7DB3FF), Color(0xFFE8F1FF), Color(0xFF05070C), HeroPalette.forSkin(skin)
            )
        }
        if (skin.id == OperativeSkins.TITANIUM.id) {
            // Owner, 2026-10-11: gray metal (gunmetal plates, steel edges), not white.
            return Look(
                Color(0xFF9CA5AF), Color(0xFF66707B), Color(0xFF353C45),
                Color(0xFFC3CCD6), Color(0xFFE8EEF4), Color(0xFF070A0E), HeroPalette.forSkin(skin)
            )
        }
        // Default skin: the concept's bright neon green trim on near-black plates.
        val trim = if (skin.id == OperativeSkins.DEFAULT.id) Color(0xFF1FF29A) else c.edge
        val base = Color(0xFF1A202B)
        return Look(
            mix(Color(0xFF2E3747), trim, 0.16f), mix(base, trim, 0.08f), Color(0xFF0B0E14),
            trim, if (skin.id == OperativeSkins.DEFAULT.id) Color(0xFFB8FFE0) else c.face, Color(0xFF05070A),
            HeroPalette.forSkin(skin)
        )
    }


    // --- Character creator parts (owner, 2026-10-13) --------------------------------

    /** Appearance of the figure being drawn (set by OperativeFigures.drawFigure; A = class default). */
    var part: com.cyberoperative.game.save.CharacterLook = com.cyberoperative.game.save.CharacterLook()

    /** Where a body's head, visor, chest, knees and feet are, in its design grid. */
    class Anchors(
        val head: Offset, val face: Offset, val faceW: Float, val faceH: Float, val chest: Offset,
        val kneeL: Offset, val kneeR: Offset, val footL: Offset, val footR: Offset
    )

    val AGENT_PARTS = Anchors(Offset(48.5f, 34f), Offset(52.7f, 57f), 19f, 12f, Offset(52f, 80f), Offset(35f, 104f), Offset(69f, 102f), Offset(36f, 117f), Offset(71f, 117f))
    val JUGGERNAUT_PARTS = Anchors(Offset(52f, 30f), Offset(52f, 45f), 22f, 12f, Offset(52f, 66f), Offset(38f, 100f), Offset(64f, 100f), Offset(38f, 117f), Offset(64f, 117f))
    val PHANTOM_PARTS = Anchors(Offset(54f, 24f), Offset(54f, 40f), 18f, 13f, Offset(52f, 66f), Offset(42f, 98f), Offset(68f, 94f), Offset(48f, 112f), Offset(65f, 110f))
    // Visor at (52, 49), 18 × 13 (outside the hood's 80% scale); the hood dome tops out near (54, 22).
    val MAGE_PARTS = Anchors(Offset(54f, 24f), Offset(52f, 49f), 18f, 13f, Offset(52f, 90f), Offset(40f, 110f), Offset(64f, 110f), Offset(40f, 117f), Offset(64f, 117f))
    /** The TECHNICIAN is the icon hero (emblem units). */
    val TECH_PARTS = Anchors(Offset(49f, 13f), Offset(55f, 46f), 26f, 17f, Offset(53f, 88f), Offset(43f, 108f), Offset(61f, 108f), Offset(43f, 117f), Offset(61f, 117f))

    /** Draws the B/C appearance pieces over a body ([bob]: the torso's lift, [legBob]: the legs'). */
    fun DrawScope.accessories(a: Anchors, l: Look, pulse: Float, bob: Float, legBob: Float, time: Float, look: com.cyberoperative.game.save.CharacterLook = part) {
        if (look.head == 0 && look.face == 0 && look.body == 0 && look.legs == 0) return
        withTransform({ translate(0f, -bob) }) {
            val h = a.head
            when (look.head) {
                1 -> { // Antenna with a blinking tip.
                    drawLine(l.plateDark, h + Offset(5f, 2f), h + Offset(9f, -11f), 2.2f, StrokeCap.Round)
                    drawLine(l.plateLight, h + Offset(5f, 2f), h + Offset(9f, -11f), 1.1f, StrokeCap.Round)
                    val blink = 0.6f + 0.4f * sin(time * 5f)
                    drawCircle(l.trim.copy(alpha = 0.3f * blink), 4f, h + Offset(9f, -12f))
                    drawCircle(l.trim, 1.9f, h + Offset(9f, -12f))
                }
                2 -> { // Crest fins.
                    fillStroke(poly(h.x - 7f, h.y + 4f, h.x - 12f, h.y - 9f, h.x - 2f, h.y + 1f), l.plateLight, l.trim, 0.8f)
                    fillStroke(poly(h.x + 7f, h.y + 4f, h.x + 12f, h.y - 9f, h.x + 2f, h.y + 1f), l.plateLight, l.trim, 0.8f)
                    neon(h + Offset(-9f, -4f), h + Offset(-4f, 1f), l.trim, 0.8f, pulse)
                    neon(h + Offset(9f, -4f), h + Offset(4f, 1f), l.trim, 0.8f, pulse)
                }
            }
            if (look.face > 0) {
                val c = a.face
                val w = a.faceW
                val hh = a.faceH
                drawRoundRect(l.visor, Offset(c.x - w / 2f, c.y - hh / 2f), Size(w, hh), CornerRadius(hh * 0.35f))
                drawRoundRect(l.trim.copy(alpha = 0.55f), Offset(c.x - w / 2f, c.y - hh / 2f), Size(w, hh), CornerRadius(hh * 0.35f), style = Stroke(0.8f))
                val s = hh * 0.22f
                if (look.face == 1) { // Digital: two upright bars.
                    for (sx in listOf(-w * 0.2f, w * 0.2f)) {
                        drawRect(l.face.copy(alpha = 0.25f * pulse), Offset(c.x + sx - s * 1.1f, c.y - hh * 0.32f), Size(s * 2.2f, hh * 0.64f))
                        drawRect(l.face, Offset(c.x + sx - s * 0.55f, c.y - hh * 0.26f), Size(s * 1.1f, hh * 0.52f))
                    }
                } else { // Hunter: narrowed, angled eyes.
                    for (side in listOf(-1f, 1f)) {
                        val ex = c.x + side * w * 0.21f
                        drawLine(l.face.copy(alpha = 0.25f * pulse), Offset(ex - side * s * 1.6f, c.y - s * 1.1f), Offset(ex + side * s * 1.4f, c.y + s * 0.4f), s * 2f, StrokeCap.Round)
                        drawLine(l.face, Offset(ex - side * s * 1.6f, c.y - s * 1.1f), Offset(ex + side * s * 1.4f, c.y + s * 0.4f), s * 0.8f, StrokeCap.Round)
                    }
                    drawLine(l.face, Offset(c.x - s, c.y + hh * 0.28f), Offset(c.x + s, c.y + hh * 0.28f), s * 0.6f, StrokeCap.Round)
                }
            }
            val ch = a.chest
            when (look.body) {
                1 -> { // Chest reactor.
                    drawCircle(l.trim.copy(alpha = 0.25f * pulse), 7f, ch)
                    drawCircle(l.plateDark, 4.4f, ch)
                    drawCircle(l.trim, 4.4f, ch, style = Stroke(1.1f))
                    drawCircle(Color.White.copy(alpha = 0.85f * pulse), 1.8f, ch)
                }
                2 -> { // Plating: twin chevrons.
                    for (k in 0..1) {
                        val y = ch.y - 3f + k * 4.5f
                        path.reset(); path.moveTo(ch.x - 8f, y); path.lineTo(ch.x, y + 4f); path.lineTo(ch.x + 8f, y)
                        neonPath(path, l.trim, 1.1f, pulse)
                    }
                }
            }
        }
        withTransform({ translate(0f, -legBob) }) {
            when (look.legs) {
                1 -> for (k in listOf(a.kneeL, a.kneeR)) { // Knee lights.
                    drawCircle(l.trim.copy(alpha = 0.3f * pulse), 4f, k)
                    drawCircle(l.trim, 1.9f, k)
                }
                2 -> for (f in listOf(a.footL, a.footR)) { // Jet boots.
                    val flick = 0.7f + 0.3f * sin(time * 30f + f.x)
                    drawOval(l.trim.copy(alpha = 0.35f * flick), Offset(f.x - 7f, f.y - 1f), Size(14f, 6f))
                    drawPath(poly(f.x - 3.5f, f.y + 1f, f.x + 3.5f, f.y + 1f, f.x, f.y + 6f * flick), l.trim.copy(alpha = 0.8f))
                    drawPath(poly(f.x - 1.6f, f.y + 1f, f.x + 1.6f, f.y + 1f, f.x, f.y + 3.5f * flick), Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }

    // --- helpers (all in design units) ---------------------------------------------

    private fun poly(vararg p: Float): Path {
        path.reset()
        var i = 0
        while (i < p.size) {
            if (i == 0) path.moveTo(p[i], p[i + 1]) else path.lineTo(p[i], p[i + 1])
            i += 2
        }
        path.close()
        return path
    }

    private fun DrawScope.fillStroke(p: Path, fill: Color, edge: Color, w: Float = 1f) {
        drawPath(p, fill)
        drawPath(p, edge, style = Stroke(w, join = StrokeJoin.Round))
    }

    private fun DrawScope.fillStroke(p: Path, fill: Brush, edge: Color, w: Float = 1f) {
        drawPath(p, fill)
        drawPath(p, edge, style = Stroke(w, join = StrokeJoin.Round))
    }

    /** A neon line with a soft halo. */
    private fun DrawScope.neon(a: Offset, b: Offset, c: Color, w: Float, glow: Float = 1f) {
        drawLine(c.copy(alpha = 0.18f * glow * c.alpha), a, b, w * 3.2f, StrokeCap.Round)
        drawLine(c, a, b, w, StrokeCap.Round)
    }

    private fun DrawScope.neonPath(p: Path, c: Color, w: Float, glow: Float = 1f) {
        drawPath(p, c.copy(alpha = 0.18f * glow * c.alpha), style = Stroke(w * 3.2f, join = StrokeJoin.Round, cap = StrokeCap.Round))
        drawPath(p, c, style = Stroke(w, join = StrokeJoin.Round, cap = StrokeCap.Round))
    }

    private fun DrawScope.limb(a: Offset, b: Offset, w: Float, c: Color, edge: Color) {
        drawLine(edge, a, b, w + 1.4f, StrokeCap.Round)
        drawLine(c, a, b, w, StrokeCap.Round)
    }

    /** Black octagon visor with the >_< face. */
    private fun DrawScope.visorFace(cx: Float, cy: Float, w: Float, h: Float, l: Look, pulse: Float) {
        val hw = w / 2f
        val hh = h / 2f
        val k = minOf(hw, hh) * 0.42f
        val v = poly(cx - hw + k, cy - hh, cx + hw - k, cy - hh, cx + hw, cy - hh + k, cx + hw, cy + hh - k,
            cx + hw - k, cy + hh, cx - hw + k, cy + hh, cx - hw, cy + hh - k, cx - hw, cy - hh + k)
        drawPath(v, l.visor)
        drawPath(v, l.trim.copy(alpha = 0.5f), style = Stroke(0.8f, join = StrokeJoin.Round))
        val s = minOf(w, h) * 0.17f
        val st = Stroke(s * 0.55f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val glow = Stroke(s * 1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val ex = w * 0.24f
        path.reset(); path.moveTo(cx - ex - s * 0.9f, cy - s); path.lineTo(cx - ex + s * 0.3f, cy); path.lineTo(cx - ex - s * 0.9f, cy + s)
        drawPath(path, l.face.copy(alpha = 0.22f * pulse), style = glow); drawPath(path, l.face, style = st)
        path.reset(); path.moveTo(cx + ex + s * 0.9f, cy - s); path.lineTo(cx + ex - s * 0.3f, cy); path.lineTo(cx + ex + s * 0.9f, cy + s)
        drawPath(path, l.face.copy(alpha = 0.22f * pulse), style = glow); drawPath(path, l.face, style = st)
        drawLine(l.face, Offset(cx - s * 0.7f, cy + s * 1.15f), Offset(cx + s * 0.7f, cy + s * 1.15f), s * 0.55f, StrokeCap.Round)
    }

    /** Glowing floor ring under the figure (the concept's stand). */
    private fun DrawScope.floorRing(l: Look, w: Float, pulse: Float) {
        drawOval(Color.Black.copy(alpha = 0.4f), Offset(52f - w / 2f, 113f), Size(w, 9f))
        drawOval(l.trim.copy(alpha = 0.12f * pulse), Offset(52f - w / 2f - 4f, 112f), Size(w + 8f, 11f))
        drawOval(l.trim.copy(alpha = 0.45f * pulse), Offset(52f - w / 2f, 113.5f), Size(w, 8f), style = Stroke(1f))
    }

    /** Places a design-grid drawing at ([x], [foot]) facing [dir] (−1/+1) with [alpha]. */
    private inline fun DrawScope.place(x: Float, foot: Float, u: Float, dir: Float, alpha: Float, crossinline body: DrawScope.() -> Unit) {
        val k = SCALE * u
        val layer = alpha < 0.99f
        if (layer) drawContext.canvas.saveLayer(
            Rect(x - 80f * u, foot - 100f * u, x + 80f * u, foot + 12f * u), Paint().apply { this.alpha = alpha }
        )
        withTransform({
            translate(x, foot)
            scale(dir * k, k, Offset.Zero)
            translate(-52f, -118f)
        }) { body() }
        if (layer) drawContext.canvas.restore()
    }

    /** Aim pitch for a weapon held forward: −0.55..0.55 rad (mirrored figures use |cos|). */
    private fun pitch(facing: Float): Float = atan2(sin(facing), abs(cos(facing))).coerceIn(-0.55f, 0.55f)

    // =================================================================================
    // FIELD AGENT: the icon hood on a crouched, armoured rifleman with a trailing scarf.
    // =================================================================================

    fun DrawScope.fieldAgent(skin: OperativeSkin, x: Float, foot: Float, u: Float, facing: Float, moving: Boolean, time: Float, alpha: Float) {
        val dir = if (cos(facing) < 0f) -1f else 1f
        val walk = if (moving) sin(time * 12f) else 0f
        val bob = if (moving) abs(sin(time * 12f)) * 1.6f else sin(time * 2f) * 0.6f
        val l = look(skin, time)
        val pulse = 0.8f + 0.2f * sin(time * 2.2f)
        place(x, foot, u, dir, alpha) {
            floorRing(l, 50f, pulse)
            withTransform({ translate(0f, -bob) }) {
                // Scarf tails streaming behind (drawn first).
                val sw = sin(time * 5f) * 3f + if (moving) 4f else 0f
                drawPath(poly(42f, 72f, 30f, 70f, 14f - sw, 66f + sw * 0.4f, 6f - sw, 74f, 18f - sw * 0.5f, 74f, 30f, 77f, 41f, 78f), l.trim.copy(alpha = 0.9f))
                drawPath(poly(40f, 76f, 26f, 80f, 12f - sw, 86f, 20f - sw * 0.6f, 81f, 30f, 80f), l.trim.copy(alpha = 0.65f))
                // Back leg, then front leg (crouched stance).
                val bk = walk * 4f
                limb(Offset(44f, 94f), Offset(34f - bk, 104f), 10f, l.plateDark, l.trim.copy(alpha = 0.45f))
                limb(Offset(34f - bk, 104f), Offset(37f - bk, 114f), 9f, l.plateMid, l.trim.copy(alpha = 0.45f))
                fillStroke(poly(30f - bk, 111f, 43f - bk, 111f, 44f - bk, 118f, 29f - bk, 118f), l.plateMid, l.trim, 0.9f)
                limb(Offset(60f, 94f), Offset(69f + bk, 102f), 10.5f, l.plateMid, l.trim.copy(alpha = 0.55f))
                limb(Offset(69f + bk, 102f), Offset(70f + bk, 114f), 9.5f, l.plateLight, l.trim.copy(alpha = 0.55f))
                drawRoundRect(l.plateDark, Offset(64f + bk, 98f), Size(9f, 7f), CornerRadius(2f))
                drawRoundRect(l.trim, Offset(64f + bk, 98f), Size(9f, 7f), CornerRadius(2f), style = Stroke(0.8f))
                drawCircle(l.trim, 2.2f, Offset(69f + bk, 102f))
                fillStroke(poly(63f + bk, 111f, 78f + bk, 111f, 80f + bk, 118f, 62f + bk, 118f), l.plateLight, l.trim, 0.9f)
                neon(Offset(64f + bk, 116.5f), Offset(78f + bk, 116.5f), l.trim, 0.9f)
                // Armoured vest, chest plate and belt.
                fillStroke(poly(32f, 68f, 72f, 68f, 76f, 88f, 66f, 97f, 38f, 97f, 28f, 88f),
                    Brush.verticalGradient(listOf(l.plateMid, l.plateDark), startY = 70f, endY = 97f), l.trim.copy(alpha = 0.8f), 1f)
                drawRoundRect(l.plateLight, Offset(40f, 74f), Size(24f, 12f), CornerRadius(3f))
                drawRoundRect(l.trim.copy(alpha = 0.8f), Offset(40f, 74f), Size(24f, 12f), CornerRadius(3f), style = Stroke(0.8f))
                neon(Offset(44f, 80f), Offset(60f, 80f), l.trim, 1f, pulse)
                drawRect(l.plateDark, Offset(38f, 90f), Size(28f, 5f))
                drawRect(l.trim, Offset(50f, 90.5f), Size(4f, 4f))
                drawRoundRect(l.plateMid, Offset(32f, 88f), Size(7f, 8f), CornerRadius(1.5f))
                drawRoundRect(l.plateMid, Offset(65f, 88f), Size(7f, 8f), CornerRadius(1.5f))
                // Shoulder pads.
                fillStroke(poly(24f, 70f, 38f, 64f, 42f, 76f, 28f, 82f), l.plateLight, l.trim, 1f)
                fillStroke(poly(64f, 64f, 80f, 70f, 76f, 82f, 62f, 76f), l.plateLight, l.trim, 1f)
            }
            // Helmet: the traced icon hood (scaled down a touch, sitting on the collar).
            withTransform({ translate(0f, -bob); translate(52f, 74f); scale(0.7f, 0.7f, Offset.Zero); translate(-54f, -70f) }) {
                drawHeroHelmet(l.hero, time)
            }
            // Rifle held across, pitched toward the aim.
            withTransform({ translate(0f, -bob); rotate((pitch(facing) * 57.29578f), Offset(58f, 86f)) }) {
                limb(Offset(40f, 78f), Offset(52f, 88f), 4.6f, l.plateDark, l.trim.copy(alpha = 0.7f))
                fillStroke(poly(36f, 86f, 46f, 83f, 48f, 92f, 38f, 95f), l.plateDark, l.trim.copy(alpha = 0.6f), 0.8f)
                fillStroke(poly(46f, 81f, 76f, 80f, 92f, 81f, 94f, 85f, 92f, 89f, 66f, 90f, 46f, 90f), Brush.verticalGradient(listOf(l.plateMid, l.plateDark), startY = 81f, endY = 90f), l.trim, 0.9f)
                fillStroke(poly(70f, 79f, 86f, 79f, 88f, 82f, 70f, 82f), l.plateLight, l.trim.copy(alpha = 0.7f), 0.6f)
                drawRect(l.plateDark, Offset(92f, 83.2f), Size(12f, 3.4f))
                drawRect(l.plateDark, Offset(60f, 89f), Size(6f, 8f))
                neon(Offset(50f, 83.5f), Offset(90f, 83.5f), l.trim, 1.1f, pulse)
                drawCircle(l.trim.copy(alpha = 0.5f * pulse), 2.4f, Offset(104f, 84.9f))
                drawRoundRect(l.plateDark, Offset(64f, 77.5f), Size(10f, 4f), CornerRadius(1f))
                drawCircle(l.trim, 1.1f, Offset(72f, 79.5f))
                limb(Offset(64f, 76f), Offset(74f, 86f), 4.6f, l.plateDark, l.trim.copy(alpha = 0.7f))
                drawCircle(l.plateMid, 2.8f, Offset(74f, 86f))
                drawCircle(l.trim, 2.8f, Offset(74f, 86f), style = Stroke(0.7f))
            }
            accessories(AGENT_PARTS, l, pulse, bob, bob, time)
        }
    }

    // =================================================================================
    // JUGGERNAUT: a hulking armoured frame with a tower shield and a heavy gauntlet.
    // =================================================================================

    fun DrawScope.juggernaut(skin: OperativeSkin, x: Float, foot: Float, u: Float, facing: Float, moving: Boolean, time: Float, alpha: Float) {
        val dir = if (cos(facing) < 0f) -1f else 1f
        val walk = if (moving) sin(time * 9f) else 0f
        val bob = if (moving) abs(sin(time * 9f)) * 1.2f else sin(time * 1.6f) * 0.4f
        val l = look(skin, time)
        val pulse = 0.8f + 0.2f * sin(time * 2.2f)
        place(x, foot, u, dir, alpha) {
            floorRing(l, 66f, pulse)
            // Legs: thick blocks with knee pads and wide feet.
            for (si in 0..1) {
                val sx = if (si == 0) 38f else 64f
                val lift = (if (si == 0) walk else -walk).coerceAtLeast(0f) * 3f
                val col = if (si == 0) l.plateDark else l.plateMid
                fillStroke(poly(sx - 8f, 92f, sx + 8f, 92f, sx + 7f, 110f - lift, sx - 7f, 110f - lift), col, l.trim.copy(alpha = 0.5f), 0.9f)
                fillStroke(poly(sx - 6f, 97f, sx + 6f, 97f, sx + 5f, 104f, sx - 5f, 104f), l.plateLight, l.trim, 0.8f)
                fillStroke(poly(sx - 11f, 110f - lift, sx + 11f, 110f - lift, sx + 12f, 118f - lift, sx - 12f, 118f - lift), l.plateMid, l.trim, 0.9f)
                neon(Offset(sx - 9f, 116f - lift), Offset(sx + 9f, 116f - lift), l.trim, 0.9f, pulse)
            }
            withTransform({ translate(0f, -bob) }) {
                // Waist and torso.
                fillStroke(poly(34f, 84f, 70f, 84f, 68f, 95f, 36f, 95f), l.plateDark, l.trim.copy(alpha = 0.6f), 0.9f)
                drawRoundRect(l.plateMid, Offset(46f, 85.5f), Size(12f, 7f), CornerRadius(1.5f))
                drawRect(l.trim, Offset(50f, 87.5f), Size(4f, 3f))
                fillStroke(poly(30f, 50f, 76f, 50f, 78f, 74f, 68f, 86f, 36f, 86f, 28f, 74f),
                    Brush.verticalGradient(listOf(l.plateLight, l.plateMid, l.plateDark), startY = 52f, endY = 86f), l.trim.copy(alpha = 0.8f), 1.1f)
                // Chest plates and the V of glowing seams.
                fillStroke(poly(36f, 56f, 50f, 56f, 50f, 72f, 40f, 76f), l.plateMid, l.plateDark, 0.8f)
                fillStroke(poly(54f, 56f, 68f, 56f, 64f, 76f, 54f, 72f), l.plateMid, l.plateDark, 0.8f)
                path.reset(); path.moveTo(34f, 60f); path.lineTo(52f, 80f); path.lineTo(70f, 60f)
                neonPath(path, l.trim, 1.3f, pulse)
                drawCircle(l.trim.copy(alpha = 0.3f * pulse), 5f, Offset(52f, 66f))
                drawCircle(l.trim, 2.4f, Offset(52f, 66f))
                // Head: armoured helmet with the visor.
                fillStroke(poly(41f, 34f, 63f, 34f, 66f, 40f, 66f, 54f, 38f, 54f, 38f, 40f),
                    Brush.verticalGradient(listOf(l.plateLight, l.plateMid), startY = 34f, endY = 54f), l.trim.copy(alpha = 0.8f), 1f)
                visorFace(52f, 45f, 22f, 12f, l, pulse)
                drawRect(l.plateDark, Offset(48f, 30f), Size(8f, 4f))
                // Back shoulder and the gauntlet arm (off side).
                limb(Offset(22f, 64f), Offset(20f, 80f), 13f, l.plateLight, l.trim)
                drawLine(l.plateDark, Offset(16f, 72f), Offset(27f, 72f), 2f)
                drawCircle(l.trim.copy(alpha = 0.7f), 14.6f, Offset(24f, 58f))
                drawCircle(com.cyberoperative.game.ui.common.SafeBrush.radialGradient(listOf(l.plateLight, l.plateMid, l.plateDark), Offset(20f, 52f), 18f), 14f, Offset(24f, 58f))
                drawArc(l.plateDark, 20f, 140f, false, Offset(12f, 46f), Size(24f, 24f), style = Stroke(2f))
                drawCircle(l.plateDark, 5.5f, Offset(24f, 58f))
                drawCircle(l.trim.copy(alpha = 0.35f * pulse), 6.5f, Offset(24f, 58f))
                drawCircle(l.trim, 3.4f, Offset(24f, 58f), style = Stroke(1.6f))
                fillStroke(poly(10f, 76f, 30f, 76f, 32f, 92f, 12f, 94f, 8f, 86f), Brush.verticalGradient(listOf(l.plateLight, l.plateMid), startY = 76f, endY = 94f), l.trim, 1f)
                for (i in 0..2) {
                    val gy = 80f + i * 4.5f
                    drawCircle(l.trim.copy(alpha = 0.25f * pulse), 2.4f, Offset(15f + i * 5f, gy + 1f))
                    drawCircle(l.trim, 1.3f, Offset(15f + i * 5f, gy + 1f))
                }
                // Tower shield on the facing side, in front of everything.
                val sh = poly(70f, 46f, 90f, 40f, 100f, 50f, 100f, 100f, 92f, 112f, 72f, 106f)
                fillStroke(sh, Brush.horizontalGradient(listOf(l.plateMid, l.plateDark), startX = 70f, endX = 100f), l.trim, 1.3f)
                val inner = poly(75f, 51f, 89f, 47f, 95f, 54f, 95f, 98f, 89f, 106f, 76f, 101f)
                drawPath(inner, l.plateDark)
                neonPath(inner, l.trim.copy(alpha = 0.9f), 1.1f, pulse)
                neon(Offset(86f, 58f), Offset(86f, 96f), l.trim, 1.6f, pulse)
                drawRoundRect(l.plateLight, Offset(78f, 70f), Size(5f, 14f), CornerRadius(1.5f))
            }
            accessories(JUGGERNAUT_PARTS, l, pulse, bob, 0f, time)
        }
    }

    // =================================================================================
    // PHANTOM: a hooded assassin in a tattered, dissolving cloak with twin daggers.
    // =================================================================================

    fun DrawScope.phantom(skin: OperativeSkin, x: Float, foot: Float, u: Float, facing: Float, moving: Boolean, time: Float, alpha: Float) {
        val dir = if (cos(facing) < 0f) -1f else 1f
        val l = look(skin, time, cobaltTitanium = true)
        val pulse = 0.8f + 0.2f * sin(time * 2.2f)
        val hover = 5f + sin(time * 2.6f) * 1.6f
        place(x, foot, u, dir, alpha) {
            drawOval(Color.Black.copy(alpha = 0.3f), Offset(36f, 114f), Size(32f, 7f))
            drawOval(l.trim.copy(alpha = 0.35f * pulse), Offset(34f, 114f), Size(36f, 7f), style = Stroke(1f))
            withTransform({ translate(0f, -hover) }) {
                val flow = sin(time * 3f) * 3f + if (moving) 5f else 0f
                // Back of the cloak: a big tattered sweep trailing behind.
                val back = poly(44f, 36f, 30f, 46f, 16f - flow, 66f, 6f - flow, 92f, 14f - flow, 88f, 12f - flow, 104f, 22f - flow * 0.6f, 96f,
                    26f - flow * 0.5f, 110f, 34f, 98f, 42f, 106f, 46f, 88f, 64f, 52f)
                fillStroke(back, Brush.verticalGradient(listOf(l.plateMid, l.plateDark), startY = 36f, endY = 110f), l.trim.copy(alpha = 0.85f), 1f)
                // Dissolving pixels off the trailing edge.
                for (i in 0 until 9) {
                    val ph = (time * 0.45f + i * 0.137f) % 1f
                    val px = 14f - flow - ph * 16f + (i % 3) * 4f
                    val py = 60f + i * 5.5f - ph * 6f
                    val sz = 2.6f * (1f - ph) + 0.6f
                    drawRect(l.trim.copy(alpha = (1f - ph) * 0.8f), Offset(px, py), Size(sz, sz))
                }
                // Legs: crouched, one knee forward, one tucked.
                limb(Offset(48f, 84f), Offset(42f, 98f), 5.5f, l.plateDark, l.trim.copy(alpha = 0.4f))
                limb(Offset(42f, 98f), Offset(48f, 110f), 5f, l.plateDark, l.trim.copy(alpha = 0.4f))
                fillStroke(poly(44f, 108f, 52f, 108f, 54f, 113f, 44f, 113f), l.plateMid, l.trim, 0.8f)
                limb(Offset(58f, 84f), Offset(68f, 94f), 6f, l.plateMid, l.trim.copy(alpha = 0.45f))
                limb(Offset(68f, 94f), Offset(64f, 108f), 5.5f, l.plateMid, l.trim.copy(alpha = 0.45f))
                drawCircle(l.trim, 1.8f, Offset(68f, 94f))
                fillStroke(poly(60f, 106f, 69f, 106f, 71f, 111f, 60f, 111f), l.plateLight, l.trim, 0.8f)
                // Torso: a fitted dark suit with a glowing clasp.
                fillStroke(poly(42f, 56f, 62f, 56f, 64f, 76f, 60f, 86f, 46f, 86f, 40f, 76f), l.plateDark, l.trim.copy(alpha = 0.7f), 0.9f)
                drawLine(l.plateMid, Offset(44f, 74f), Offset(62f, 74f), 3f)
                drawCircle(l.trim.copy(alpha = 0.35f * pulse), 3.4f, Offset(52f, 62f))
                drawCircle(l.trim, 1.7f, Offset(52f, 62f))
                // Front cloak panel over the shoulder.
                fillStroke(poly(62f, 50f, 72f, 66f, 78f, 88f, 70f, 84f, 66f, 92f, 62f, 74f), l.plateMid, l.trim.copy(alpha = 0.85f), 0.9f)
                // Daggers: one low behind, one forward.
                limb(Offset(44f, 62f), Offset(34f, 76f), 4.5f, l.plateDark, l.trim.copy(alpha = 0.4f))
                dagger(34f, 76f, 200f, l, pulse)
                limb(Offset(60f, 62f), Offset(74f, 70f), 4.5f, l.plateMid, l.trim.copy(alpha = 0.45f))
                dagger(74f, 70f, 120f, l, pulse)
                // Hood: a swept point, deep opening, the visor inside.
                path.reset()
                path.moveTo(38f, 56f)
                path.cubicTo(30f, 44f, 30f, 30f, 38f, 24f)
                path.cubicTo(30f, 22f - flow * 0.3f, 22f - flow, 24f, 14f - flow, 30f)
                path.cubicTo(26f, 14f, 50f, 10f, 62f, 20f)
                path.cubicTo(70f, 28f, 70f, 46f, 66f, 56f)
                path.cubicTo(58f, 60f, 46f, 60f, 38f, 56f)
                path.close()
                fillStroke(path, Brush.verticalGradient(listOf(l.plateLight, l.plateMid, l.plateDark), startY = 12f, endY = 58f), l.trim, 1.1f)
                drawOval(Color(0xFF020306), Offset(41f, 27f), Size(25f, 26f))
                visorFace(54f, 40f, 18f, 13f, l, pulse)
            }
            // Floating diamond shards.
            for (i in 0 until 3) {
                val a = time * 0.9f + i * 2.1f
                val sx = 52f + cos(a) * (40f + i * 4f)
                val sy = 58f + sin(a * 1.3f) * 22f - hover
                val s = 3.2f - i * 0.5f
                drawPath(poly(sx, sy - s * 1.6f, sx + s, sy, sx, sy + s * 1.6f, sx - s, sy), l.trim.copy(alpha = 0.25f))
                drawPath(poly(sx, sy - s * 1.6f, sx + s, sy, sx, sy + s * 1.6f, sx - s, sy), l.trim, style = Stroke(0.8f, join = StrokeJoin.Round))
            }
            accessories(PHANTOM_PARTS, l, pulse, hover, hover, time)
        }
    }

    private fun DrawScope.dagger(hx: Float, hy: Float, angleDeg: Float, l: Look, pulse: Float) {
        rotate(angleDeg, Offset(hx, hy)) {
            drawRect(l.plateDark, Offset(hx - 1.6f, hy - 2.8f), Size(3.2f, 5.6f))
            drawLine(l.plateLight, Offset(hx + 1.5f, hy - 3.5f), Offset(hx + 1.5f, hy + 3.5f), 1.6f)
            val blade = poly(hx + 2.5f, hy - 2f, hx + 15f, hy, hx + 2.5f, hy + 2f)
            drawPath(blade, l.trim.copy(alpha = 0.3f * pulse), style = Stroke(3f, join = StrokeJoin.Round))
            drawPath(blade, l.plateLight)
            drawPath(blade, l.trim, style = Stroke(0.7f, join = StrokeJoin.Round))
        }
    }

    // =================================================================================
    // PACKET MAGE: a tall peaked hood, a long rune-hemmed robe and an orb staff.
    // =================================================================================

    fun DrawScope.packetMage(skin: OperativeSkin, x: Float, foot: Float, u: Float, facing: Float, moving: Boolean, time: Float, alpha: Float) {
        val dir = if (cos(facing) < 0f) -1f else 1f
        val walk = if (moving) sin(time * 10f) else 0f
        val bob = if (moving) abs(sin(time * 10f)) * 1.2f else sin(time * 1.8f) * 0.5f
        val l = look(skin, time, cobaltTitanium = true)
        val pulse = 0.8f + 0.2f * sin(time * 2.2f)
        place(x, foot, u, dir, alpha) {
            floorRing(l, 52f, pulse)
            // Floating code cards on the off side.
            for (i in 0 until 3) {
                val fy = 46f + i * 16f + sin(time * 1.7f + i) * 2.5f
                val fx = 10f + (i % 2) * 6f
                drawRect(l.trim.copy(alpha = 0.14f), Offset(fx, fy), Size(9f, 11f))
                drawRect(l.trim.copy(alpha = 0.75f), Offset(fx, fy), Size(9f, 11f), style = Stroke(0.7f))
                drawLine(l.trim.copy(alpha = 0.8f), Offset(fx + 2f, fy + 4f), Offset(fx + 7f, fy + 4f), 0.7f)
                drawLine(l.trim.copy(alpha = 0.8f), Offset(fx + 2f, fy + 7f), Offset(fx + 5.5f, fy + 7f), 0.7f)
            }
            withTransform({ translate(0f, -bob) }) {
                val sway = walk * 2f
                // Robe: long, flared, with a lighter front panel.
                val robe = poly(38f, 58f, 66f, 58f, 74f, 108f, 78f, 117f, 26f + sway, 117f, 30f + sway, 108f)
                fillStroke(robe, Brush.verticalGradient(listOf(l.plateMid, l.plateDark), startY = 58f, endY = 117f), l.trim.copy(alpha = 0.85f), 1f)
                fillStroke(poly(47f, 70f, 57f, 70f, 61f, 116f, 43f + sway * 0.5f, 116f), l.plateLight.copy(alpha = 0.9f), l.trim.copy(alpha = 0.6f), 0.7f)
                // Hem band of glowing runes.
                neon(Offset(29f + sway, 110f), Offset(76f, 110f), l.trim, 0.9f, pulse)
                for (i in 0 until 8) {
                    val rx = 33f + i * 5.5f + sway * 0.6f
                    drawLine(l.trim.copy(alpha = 0.85f), Offset(rx, 112.5f), Offset(rx + 2f, 115f), 0.8f)
                    drawLine(l.trim.copy(alpha = 0.85f), Offset(rx + 2f, 112.5f), Offset(rx, 115f), 0.8f)
                }
                // Belt and the chest gem.
                drawRect(l.plateDark, Offset(40f, 78f), Size(24f, 4f))
                drawRect(l.trim.copy(alpha = 0.7f), Offset(40f, 78f), Size(24f, 4f), style = Stroke(0.6f))
                val gem = poly(52f, 62f, 56f, 67f, 52f, 73f, 48f, 67f)
                drawCircle(l.trim.copy(alpha = 0.3f * pulse), 7f, Offset(52f, 67.5f))
                drawPath(gem, l.trim)
                drawPath(gem, Color.White.copy(alpha = 0.6f), style = Stroke(0.6f, join = StrokeJoin.Round))
                // Off arm: a wide sleeve, palm open with a spark.
                fillStroke(poly(38f, 60f, 30f, 72f, 24f, 82f, 32f, 84f, 40f, 72f), l.plateMid, l.trim.copy(alpha = 0.8f), 0.8f)
                drawCircle(l.plateLight, 2.6f, Offset(26f, 84f))
                drawCircle(l.trim.copy(alpha = 0.3f * pulse), 5f, Offset(24f, 86f))
                // Staff on the facing side, crowned with the orb and a ring.
                val top = Offset(82f, 24f)
                limb(Offset(80f, 117f), Offset(82f, 30f), 2.2f, l.plateDark, l.trim.copy(alpha = 0.5f))
                fillStroke(poly(78f, 30f, 86f, 30f, 84f, 34f, 80f, 34f), l.plateLight, l.trim, 0.6f)
                // Orb with a tilted ring and three sparks orbiting along it: the back half passes
                // behind the orb, the front half in front of it.
                val tilt = -18f + sin(time * 0.9f) * 6f
                fun spark(k: Int, front: Boolean) {
                    for (tr in 0..2) {
                        val a = time * 4f + k * 2.0944f - tr * 0.22f
                        if ((sin(a) >= 0f) != front) continue
                        val alpha = (if (front) 1f else 0.45f) * (1f - tr * 0.32f)
                        drawCircle(if (tr == 0) l.face.copy(alpha = alpha) else l.trim.copy(alpha = alpha * 0.8f),
                            if (tr == 0) 1.5f else 1.1f - tr * 0.2f, Offset(top.x + cos(a) * 11f, top.y + sin(a) * 3.4f))
                    }
                }
                rotate(tilt, top) {
                    drawArc(l.trim.copy(alpha = 0.45f), 180f, 180f, false, Offset(top.x - 11f, top.y - 3.4f), Size(22f, 6.8f), style = Stroke(0.8f))
                    for (k in 0 until 3) spark(k, front = false)
                }
                drawCircle(l.trim.copy(alpha = 0.16f * pulse), 11f, top)
                drawCircle(l.trim.copy(alpha = 0.35f * pulse), 7f, top)
                drawCircle(l.trim, 4.8f, top)
                drawCircle(Color.White.copy(alpha = 0.85f), 2f, top + Offset(-1f, -1f))
                rotate(tilt, top) {
                    drawArc(l.trim.copy(alpha = 0.9f), 0f, 180f, false, Offset(top.x - 11f, top.y - 3.4f), Size(22f, 6.8f), style = Stroke(0.9f))
                    for (k in 0 until 3) spark(k, front = true)
                }
                // Staff arm.
                fillStroke(poly(64f, 60f, 74f, 66f, 82f, 70f, 80f, 76f, 70f, 74f, 62f, 70f), l.plateMid, l.trim.copy(alpha = 0.8f), 0.8f)
                drawCircle(l.plateLight, 2.8f, Offset(81f, 72f))
                // The tall peaked hood with the visor.
                // Smooth peaked hood whose whole top leans back (owner, 2026-10-11), shrunk to 80%
                // around the collar; the opening is an octagon that hugs the face visor.
                withTransform({ scale(0.8f, 0.8f, Offset(52f, 64f)) }) {
                    path.reset()
                    path.moveTo(34f, 62f)
                    path.cubicTo(32f, 46f, 33f, 32f, 38f, 22f)
                    path.cubicTo(37f, 14f, 30f, 7f, 20f, 3f)
                    // Right side bulges outward so it clears the face opening.
                    path.cubicTo(40f, 4f, 56f, 9f, 64f, 20f)
                    path.cubicTo(73f, 32f, 75f, 48f, 70f, 62f)
                    path.lineTo(52f, 66f)
                    path.close()
                    fillStroke(path, Brush.verticalGradient(listOf(l.plateLight, l.plateMid, l.plateDark), startY = 3f, endY = 66f), l.trim, 1.3f)
                    path.reset(); path.moveTo(54f, 30f); path.cubicTo(50f, 18f, 40f, 10f, 26f, 5f)
                    neonPath(path, l.trim.copy(alpha = 0.6f), 0.7f, pulse)
                }
                // Octagonal opening one step outside the visor.
                val ow = 11.5f; val oh = 8.5f; val ok = 4f; val oy = 49f
                drawPath(poly(52f - ow + ok, oy - oh, 52f + ow - ok, oy - oh, 52f + ow, oy - oh + ok, 52f + ow, oy + oh - ok,
                    52f + ow - ok, oy + oh, 52f - ow + ok, oy + oh, 52f - ow, oy + oh - ok, 52f - ow, oy - oh + ok), Color(0xFF020306))
                visorFace(52f, 49f, 18f, 13f, l, pulse)
            }
            accessories(MAGE_PARTS, l, pulse, bob, bob, time)
        }
    }
}
