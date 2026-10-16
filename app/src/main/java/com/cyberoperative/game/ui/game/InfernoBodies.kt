package com.cyberoperative.game.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.cyberoperative.game.ui.common.SafeBrush
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * Bodies for the INFERNO Boss Packs Alpha / Beta / Gamma (owner sheets, 0.35.0): black volcanic
 * armour with molten seams, each built after its card. Shared look: dark plates, lava-orange
 * edges, a white-hot core, embers rising off the body and a burning pool on the floor.
 */

private val METAL = Color(0xFF1C0E0B)
private val METAL_LIT = Color(0xFF34190F)
private val METAL_DARK = Color(0xFF0B0504)
private val LAVA = Color(0xFFFF6A1A)
private val HOT = Color(0xFFFFC24A)
private val WHITE_HOT = Color(0xFFFFF1C8)
private const val PI = Math.PI.toFloat()

private fun metal(p: BossPose) = if (p.hitFlash) Color.White else METAL
private fun metalLit(p: BossPose) = if (p.hitFlash) Color.White else METAL_LIT

/** The molten pool under every INFERNO boss. */
private fun DrawScope.lavaPool(p: BossPose, ground: Float, w: Float = 1.3f) {
    val r = p.radius * w
    val k = 0.7f + 0.3f * sin(p.time * 3f)
    drawOval(SafeBrush.radialGradient(listOf(LAVA.copy(alpha = 0.45f * k), p.color.copy(alpha = 0.15f), Color.Transparent), Offset(p.cx, ground), r),
        Offset(p.cx - r, ground - r * 0.32f), Size(r * 2f, r * 0.64f))
}

/** Embers drifting up off the body. */
private fun DrawScope.embers(p: BossPose, n: Int = 10, spread: Float = 1.1f, height: Float = 2f) {
    for (k in 0 until n) {
        val ph = (p.time * 0.45f + k * 0.618f) % 1f
        val x = p.cx + sin(k * 2.3f + p.time * 0.9f) * p.radius * spread
        val y = p.cy + p.radius * 0.6f - ph * p.radius * height
        drawCircle((if (k % 3 == 0) HOT else LAVA).copy(alpha = (1f - ph) * 0.85f), 1.6f + (k % 3) * 0.6f, Offset(x, y))
    }
}

/** A dark plate with a molten edge. */
private fun DrawScope.plate(cx: Float, cy: Float, pts: FloatArray, fill: Color, edge: Color, w: Float = 2f) {
    val path = polyPath(cx, cy, pts)
    drawPath(path, fill)
    drawPath(path, edge, style = Stroke(w))
}

/** A glowing core orb: halo, colour, white-hot heart. */
private fun DrawScope.core(c: Offset, r: Float, color: Color, pulse: Float) {
    glow(c, r * 1.5f, color, 1.4f + pulse)
    drawCircle(SafeBrush.radialGradient(listOf(WHITE_HOT, HOT, color, color.copy(alpha = 0f)), c, r * 1.25f), r * 1.25f, c)
}

/** A thin tilted fire ring (an ellipse) around [c]. */
private fun DrawScope.fireRing(c: Offset, rx: Float, ry: Float, color: Color, width: Float = 2.5f) {
    drawOval(color.copy(alpha = color.alpha * 0.35f), Offset(c.x - rx, c.y - ry), Size(rx * 2f, ry * 2f), style = Stroke(width * 2.6f))
    drawOval(color, Offset(c.x - rx, c.y - ry), Size(rx * 2f, ry * 2f), style = Stroke(width))
}

private fun pulseOf(p: BossPose, rate: Float = 3f) = 0.5f + 0.5f * sin(p.time * (rate + p.phase)) + if (p.winding) 0.5f else 0f

// ======================================================================================
// Pack Alpha
// ======================================================================================

/** Hellcore Tyrant: an armoured forge core in a fire halo, molten shards orbiting, lava dripping. */
internal object HellcoreTyrantBody : BossBody {
    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius; val t = p.time; val pu = pulseOf(p)
        val cy = p.cy + sin(t * 1.5f) * r * 0.06f
        lavaPool(p, p.cy + r * 0.95f)
        // Lava dripping from the hull.
        for (k in 0 until 4) {
            val ph = (t * 0.6f + k * 0.27f) % 1f
            val x = p.cx + (k - 1.5f) * r * 0.28f
            drawLine(LAVA.copy(alpha = 0.8f * (1f - ph)), Offset(x, cy + r * 0.6f), Offset(x, cy + r * (0.6f + ph * 0.5f)), 3f)
        }
        // Orbiting shards (more each phase).
        val n = 6 + 2 * p.phase
        for (k in 0 until n) {
            val a = t * 0.7f + k * 2f * PI / n
            val sx = p.cx + cos(a) * r * 1.55f
            val sy = cy + sin(a) * r * 0.55f - r * 0.1f
            crystal(sx, sy + r * 0.25f, -PI / 2f, r * 0.55f, r * 0.2f, Color(0xFF5A2410), METAL_DARK, LAVA)
        }
        fireRing(Offset(p.cx, cy), r * 1.25f, r * 0.38f, LAVA.copy(alpha = 0.8f))
        // Hull: four heavy plates round the core.
        val m = metal(p); val ml = metalLit(p)
        plate(p.cx, cy, floatArrayOf(0f, -r * 1.05f, r * 0.55f, -r * 0.45f, 0f, -r * 0.25f, -r * 0.55f, -r * 0.45f), ml, LAVA)
        plate(p.cx, cy, floatArrayOf(-r * 0.95f, -r * 0.2f, -r * 0.35f, -r * 0.3f, -r * 0.4f, r * 0.35f, -r * 0.8f, r * 0.45f), m, LAVA)
        plate(p.cx, cy, floatArrayOf(r * 0.95f, -r * 0.2f, r * 0.35f, -r * 0.3f, r * 0.4f, r * 0.35f, r * 0.8f, r * 0.45f), m, LAVA)
        plate(p.cx, cy, floatArrayOf(-r * 0.35f, r * 0.4f, r * 0.35f, r * 0.4f, 0f, r * 0.85f), ml, LAVA)
        core(Offset(p.cx, cy + r * 0.02f), r * 0.28f, p.color, pu)
        embers(p)
    }
}

/** Ember Warden: a squat fortress between two rune pylons, one great burning eye. */
internal object EmberWardenBody : BossBody {
    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius; val t = p.time; val pu = pulseOf(p, 2f)
        val ground = p.cy + r * 0.95f
        lavaPool(p, ground, 1.6f)
        val m = metal(p); val ml = metalLit(p)
        // Pylons.
        for (s in listOf(-1f, 1f)) {
            val x = p.cx + s * r * 1.1f
            block(x, ground - r * 0.1f, r * 0.42f, r * 0.3f, r * 2.1f, ml, m, LAVA)
            for (k in 0 until 4) {
                val y = ground - r * (0.5f + k * 0.42f)
                drawLine(LAVA.copy(alpha = 0.5f + 0.4f * sin(t * 4f + k + s)), Offset(x - r * 0.08f, y), Offset(x + r * 0.08f, y - r * 0.12f), 2.5f)
            }
            crystal(x, ground - r * 2.25f, -PI / 2f, r * 0.45f, r * 0.42f, Color(0xFF4A1E10), METAL_DARK, LAVA)
        }
        // Fortress body and shoulders.
        block(p.cx, ground, r * 1.5f, r * 0.8f, r * 1.25f, ml, m, LAVA)
        block(p.cx, ground - r * 1.25f, r * 1.0f, r * 0.6f, r * 0.5f, ml, m, LAVA)
        // Lava legs.
        for (s in listOf(-1f, 1f)) drawRect(LAVA.copy(alpha = 0.55f + 0.3f * pu.coerceAtMost(1f)), Offset(p.cx + s * r * 0.45f - r * 0.07f, ground - r * 0.55f), Size(r * 0.14f, r * 0.55f))
        core(Offset(p.cx, ground - r * 0.85f), r * 0.24f, p.color, pu)
        embers(p, 8, 1.3f)
    }
}

/** Cinder Widow: a molten spider, burning egg-sac abdomen and eight lava-tipped legs. */
internal object CinderWidowBody : BossBody {
    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius; val t = p.time; val pu = pulseOf(p, 5f)
        val cy = p.cy
        lavaPool(p, cy + r * 0.9f, 1.5f)
        val m = metal(p); val ml = metalLit(p)
        // Legs: two joints each, stepping.
        for (s in listOf(-1f, 1f)) for (k in 0 until 4) {
            val step = sin(t * 6f + k * 1.6f + s) * r * 0.08f
            val hip = Offset(p.cx + s * r * 0.3f, cy + r * (-0.05f + k * 0.12f))
            val knee = Offset(p.cx + s * r * (1.0f + k * 0.1f), cy - r * (0.75f - k * 0.18f) + step)
            val foot = Offset(p.cx + s * r * (1.35f + k * 0.12f), cy + r * (0.75f + k * 0.04f) + step)
            drawLine(METAL_DARK, hip, knee, r * 0.13f); drawLine(m, hip, knee, r * 0.09f)
            drawLine(METAL_DARK, knee, foot, r * 0.11f); drawLine(ml, knee, foot, r * 0.07f)
            drawLine(LAVA, Offset(foot.x + (knee.x - foot.x) * 0.25f, foot.y + (knee.y - foot.y) * 0.25f), foot, r * 0.06f)
        }
        // Abdomen: a burning egg-sac sphere.
        val ab = Offset(p.cx, cy - r * 0.55f)
        drawCircle(SafeBrush.radialGradient(listOf(HOT, LAVA, p.color, METAL_DARK), ab, r * 0.75f), r * 0.75f, ab)
        for (k in 0 until 5) {
            val a = k * 1.25f + t * 0.2f
            drawLine(METAL_DARK.copy(alpha = 0.7f), Offset(ab.x + cos(a) * r * 0.2f, ab.y + sin(a) * r * 0.2f), Offset(ab.x + cos(a) * r * 0.72f, ab.y + sin(a) * r * 0.72f), 2.5f)
        }
        // Head and eyes.
        plate(p.cx, cy + r * 0.05f, ngon(6, r * 0.42f, PI / 6f, 0.8f), ml, LAVA)
        for (k in 0 until 6) drawCircle(WHITE_HOT.copy(alpha = 0.7f + 0.3f * (pu / 2f)), r * 0.05f, Offset(p.cx + (k % 3 - 1) * r * 0.13f, cy + r * (0.0f + (k / 3) * 0.12f)))
        // Fangs.
        for (s in listOf(-1f, 1f)) crystal(p.cx + s * r * 0.12f, cy + r * 0.3f, PI / 2f + s * 0.25f, r * 0.3f, r * 0.1f, HOT, LAVA, WHITE_HOT)
        embers(p, 8)
    }
}

/** Magma Pulse: a sphere of cracked plates over a molten heart, two orbit rings, plates breaking off. */
internal object MagmaPulseBody : BossBody {
    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius; val t = p.time; val pu = pulseOf(p, 4f)
        val c = Offset(p.cx, p.cy - r * 0.15f + sin(t * 1.3f) * r * 0.05f)
        lavaPool(p, p.cy + r * 0.95f, 1.4f)
        // Back half of the rings.
        fireRing(c, r * 1.45f, r * 0.42f, LAVA.copy(alpha = 0.55f))
        glow(c, r * 1.1f, p.color, 1.5f + pu)
        drawCircle(SafeBrush.radialGradient(listOf(WHITE_HOT, HOT, LAVA, p.color), c, r), r, c)
        // Armour plates: a grid of dark tiles with molten seams.
        val m = metal(p)
        val open = 0.04f * p.phase + (if (p.winding) 0.05f else 0f)
        for (row in -2..2) for (col in -2..2) {
            val x = col * 0.38f; val y = row * 0.38f
            if (x * x + y * y > 0.75f) continue
            val bulge = 1f + open * 3f
            val cx = c.x + x * r * bulge; val cy = c.y + y * r * bulge
            val w = r * 0.32f * (1f - (x * x + y * y) * 0.35f)
            drawRect(m, Offset(cx - w / 2f, cy - w / 2f), Size(w, w))
            drawRect(METAL_LIT.copy(alpha = 0.7f), Offset(cx - w / 2f, cy - w / 2f), Size(w, w * 0.25f))
        }
        // Floating fragments.
        for (k in 0 until 5 + p.phase * 2) {
            val a = -t * 0.8f + k * 1.3f
            val fx = c.x + cos(a) * r * (1.25f + 0.08f * (k % 2))
            val fy = c.y + sin(a) * r * 0.5f
            drawRect(METAL_LIT, Offset(fx - r * 0.09f, fy - r * 0.09f), Size(r * 0.18f, r * 0.18f))
            drawRect(LAVA, Offset(fx - r * 0.09f, fy - r * 0.09f), Size(r * 0.18f, r * 0.18f), style = Stroke(1.5f))
        }
        // Front ring and vent glow below.
        fireRing(c, r * 1.2f, r * 0.3f, HOT.copy(alpha = 0.8f), 2f)
        drawRect(Brush.verticalGradient(listOf(LAVA.copy(alpha = 0.6f), Color.Transparent), startY = c.y + r, endY = p.cy + r), Offset(c.x - r * 0.15f, c.y + r * 0.9f), Size(r * 0.3f, r * 0.6f))
        embers(p, 12, 1.2f)
    }
}

/** Ashen Reaver: a hooded reaper in a tattered burning cloak, two huge molten scythes. */
internal object AshenReaverBody : BossBody {
    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius; val t = p.time; val pu = pulseOf(p, 4f)
        val cy = p.cy + sin(t * 2f) * r * 0.08f
        lavaPool(p, p.cy + r, 1.2f)
        val m = metal(p)
        // Scythes behind: arcs of fire.
        val swing = sin(t * 1.6f) * 0.15f + (if (p.winding) 0.35f else 0f)
        for (s in listOf(-1f, 1f)) {
            val hx = p.cx + s * r * 0.75f; val hy = cy - r * 0.1f
            drawLine(METAL_DARK, Offset(hx, hy), Offset(hx + s * r * 0.35f, hy - r * 0.9f), r * 0.08f)
            val start = if (s < 0) 200f else -20f
            val sweep = if (s < 0) -100f else 100f
            val tl = Offset(hx + s * r * 0.35f - r * 1.1f, hy - r * 0.9f - r * 0.6f)
            drawArc(LAVA.copy(alpha = 0.35f), start + swing * 57f * s, sweep, false, tl, Size(r * 2.2f, r * 1.6f), style = Stroke(r * 0.22f))
            drawArc(HOT, start + swing * 57f * s, sweep, false, tl, Size(r * 2.2f, r * 1.6f), style = Stroke(r * 0.08f))
        }
        // Cloak: a tall tapering robe with a ragged hem.
        val hem = Path().apply {
            moveTo(p.cx, cy - r * 1.05f)
            lineTo(p.cx + r * 0.62f, cy + r * 0.2f)
            for (k in 0..6) {
                val x = p.cx + r * (0.62f - k * 0.2067f)
                val y = cy + r * (0.95f + (if (k % 2 == 0) 0f else -0.22f) + sin(t * 5f + k) * 0.05f)
                lineTo(x, y)
            }
            lineTo(p.cx - r * 0.62f, cy + r * 0.2f)
            close()
        }
        drawPath(hem, m)
        drawPath(hem, LAVA.copy(alpha = 0.8f), style = Stroke(2f))
        // Burning cracks down the cloak.
        for (k in 0 until 4) drawLine(LAVA.copy(alpha = 0.4f + 0.3f * sin(t * 3f + k)), Offset(p.cx + (k - 1.5f) * r * 0.18f, cy - r * 0.1f), Offset(p.cx + (k - 1.5f) * r * 0.26f, cy + r * 0.8f), 1.8f)
        // Hood opening and eyes.
        drawOval(METAL_DARK, Offset(p.cx - r * 0.24f, cy - r * 0.78f), Size(r * 0.48f, r * 0.5f))
        drawLine(LAVA, Offset(p.cx - r * 0.12f, cy - r * 0.55f), Offset(p.cx - r * 0.03f, cy - r * 0.5f), 3f)
        drawLine(LAVA, Offset(p.cx + r * 0.12f, cy - r * 0.55f), Offset(p.cx + r * 0.03f, cy - r * 0.5f), 3f)
        glow(Offset(p.cx, cy - r * 0.52f), r * 0.18f, LAVA, 1f + pu)
        embers(p, 12, 0.9f, 2.4f)
    }
}

// ======================================================================================
// Pack Beta
// ======================================================================================

/** Infernal Router: a wide fortress saucer bristling with spires, fire ports turning round its rim. */
internal object InfernalRouterBody : BossBody {
    override val previewSpan: Float get() = 4.6f
    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius; val t = p.time; val pu = pulseOf(p, 3f)
        val cy = p.cy - r * 0.1f + sin(t * 1.2f) * r * 0.04f
        lavaPool(p, p.cy + r, 1.4f)
        // Downward fire jet.
        drawRect(Brush.verticalGradient(listOf(HOT.copy(alpha = 0.7f), LAVA.copy(alpha = 0.3f), Color.Transparent), startY = cy + r * 0.3f, endY = p.cy + r), Offset(p.cx - r * 0.18f, cy + r * 0.3f), Size(r * 0.36f, r * 0.75f))
        val m = metal(p); val ml = metalLit(p)
        // Spires on the rim (behind).
        for (k in 0 until 6) {
            val x = p.cx + (k - 2.5f) * r * 0.42f
            val h = r * (0.9f + 0.35f * (1f - abs(k - 2.5f) / 2.5f))
            drawLine(METAL_DARK, Offset(x, cy - r * 0.1f), Offset(x, cy - h), r * 0.12f)
            drawLine(LAVA.copy(alpha = 0.7f), Offset(x, cy - r * 0.2f), Offset(x, cy - h * 0.9f), 1.6f)
            drawCircle(HOT, r * 0.04f, Offset(x, cy - h))
        }
        // Saucer: lower hull, rim, dome.
        drawOval(m, Offset(p.cx - r * 1.5f, cy - r * 0.15f), Size(r * 3f, r * 0.6f))
        drawOval(LAVA.copy(alpha = 0.8f), Offset(p.cx - r * 1.5f, cy - r * 0.15f), Size(r * 3f, r * 0.6f), style = Stroke(2f))
        drawOval(ml, Offset(p.cx - r * 1.1f, cy - r * 0.42f), Size(r * 2.2f, r * 0.6f))
        // Fire ports turning round the rim.
        for (k in 0 until 8) {
            val a = t * 0.9f + k * PI / 4f
            if (sin(a) < -0.2f) continue
            val x = p.cx + cos(a) * r * 1.35f
            val y = cy + r * 0.15f + sin(a) * r * 0.2f
            drawCircle(LAVA.copy(alpha = 0.5f + 0.5f * sin(a)), r * 0.09f, Offset(x, y))
        }
        // Dome with the triangle emblem.
        drawArc(m, 180f, 180f, true, Offset(p.cx - r * 0.6f, cy - r * 0.85f), Size(r * 1.2f, r * 0.9f))
        drawArc(LAVA, 180f, 180f, false, Offset(p.cx - r * 0.6f, cy - r * 0.85f), Size(r * 1.2f, r * 0.9f), style = Stroke(2f))
        val e = Offset(p.cx, cy - r * 0.42f)
        core(e, r * 0.2f, p.color, pu)
        drawPath(polyPath(e.x, e.y, floatArrayOf(-r * 0.12f, -r * 0.07f, r * 0.12f, -r * 0.07f, 0f, r * 0.12f)), METAL_DARK)
        embers(p, 8, 1.4f)
    }
}

/** Pyroclast Oracle: a pointed hood round a pale mask with one red eye, crystals and fire rings round it. */
internal object PyroclastOracleBody : BossBody {
    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius; val t = p.time; val pu = pulseOf(p, 2.5f)
        val cy = p.cy - r * 0.1f + sin(t * 1.4f) * r * 0.07f
        lavaPool(p, p.cy + r, 1.2f)
        val c = Offset(p.cx, cy - r * 0.2f)
        fireRing(c, r * 1.2f, r * 0.4f, LAVA.copy(alpha = 0.6f))
        // Floating crystals.
        for (k in 0 until 8 + 2 * p.phase) {
            val a = t * 0.4f + k * 0.785f
            val x = p.cx + cos(a) * r * 1.6f
            val y = cy + sin(a * 1.3f) * r * 0.7f - r * 0.3f
            crystal(x, y + r * 0.2f, -PI / 2f, r * 0.42f, r * 0.18f, Color(0xFFFF4A2A), Color(0xFF5A0E08), HOT)
        }
        val m = metal(p); val ml = metalLit(p)
        // Robe and pointed hood.
        plate(p.cx, cy, floatArrayOf(0f, -r * 1.25f, r * 0.55f, -r * 0.1f, r * 0.45f, r * 0.95f, 0f, r * 1.1f, -r * 0.45f, r * 0.95f, -r * 0.55f, -r * 0.1f), m, LAVA)
        plate(p.cx, cy, floatArrayOf(0f, -r * 1.0f, r * 0.32f, -r * 0.2f, 0f, r * 0.3f, -r * 0.32f, -r * 0.2f), ml, LAVA, 1.5f)
        // Mask and eye.
        val mask = if (p.hitFlash) Color.White else Color(0xFFD8CFC8)
        plate(p.cx, cy, floatArrayOf(0f, -r * 0.7f, r * 0.2f, -r * 0.35f, r * 0.12f, -r * 0.05f, 0f, r * 0.1f, -r * 0.12f, -r * 0.05f, -r * 0.2f, -r * 0.35f), mask, Color(0xFF6A5048), 1.5f)
        core(Offset(p.cx, cy - r * 0.45f), r * 0.08f, p.color, pu)
        fireRing(c, r * 0.95f, r * 0.22f, HOT.copy(alpha = 0.7f), 1.8f)
        embers(p, 10)
    }
}

/** Searfang Colossus: a hunched furnace beast, open lava jaws, four armoured legs, burning stacks. */
internal object SearfangColossusBody : BossBody {
    override val previewSpan: Float get() = 4.6f
    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius; val t = p.time; val pu = pulseOf(p, 3f)
        val ground = p.cy + r * 0.95f
        lavaPool(p, ground, 1.6f)
        val m = metal(p); val ml = metalLit(p)
        val stomp = abs(sin(t * 3f)) * r * 0.05f
        // Legs.
        for ((i, s) in listOf(-1.1f, -0.45f, 0.45f, 1.1f).withIndex()) {
            val lift = if (i % 2 == 0) stomp else 0f
            plate(p.cx + s * r, ground - lift, floatArrayOf(-r * 0.18f, -r * 0.8f, r * 0.18f, -r * 0.8f, r * 0.22f, 0f, -r * 0.22f, 0f), m, LAVA, 1.5f)
            drawLine(LAVA, Offset(p.cx + s * r - r * 0.22f, ground - lift), Offset(p.cx + s * r + r * 0.22f, ground - lift), 3f)
        }
        // Back with exhaust stacks.
        val by = ground - r * 0.95f
        plate(p.cx, by, floatArrayOf(-r * 1.35f, r * 0.2f, -r * 1.0f, -r * 0.55f, r * 0.8f, -r * 0.6f, r * 1.35f, r * 0.15f, r * 0.9f, r * 0.35f, -r * 0.9f, r * 0.35f), ml, LAVA)
        for (k in 0 until 4) {
            val x = p.cx + (k - 1.2f) * r * 0.42f
            drawRect(METAL_DARK, Offset(x - r * 0.1f, by - r * 0.95f), Size(r * 0.2f, r * 0.45f))
            drawRect(LAVA, Offset(x - r * 0.1f, by - r * 0.95f), Size(r * 0.2f, r * 0.45f), style = Stroke(1.5f))
            val fh = r * (0.35f + 0.15f * sin(t * 8f + k * 2f))
            drawPath(Path().apply { moveTo(x - r * 0.09f, by - r * 0.95f); quadraticTo(x, by - r * 0.95f - fh * 1.3f, x + r * 0.09f, by - r * 0.95f); close() },
                Brush.verticalGradient(listOf(Color.Transparent, LAVA, HOT), startY = by - r * 0.95f - fh, endY = by - r * 0.95f))
        }
        // Head with open jaws (the jaws gape wider while winding up).
        val gape = r * (0.22f + (if (p.winding) 0.15f else 0f) + 0.04f * sin(t * 4f))
        val hx = p.cx; val hy = by + r * 0.4f
        drawOval(SafeBrush.radialGradient(listOf(WHITE_HOT, HOT, LAVA), Offset(hx, hy), r * 0.5f), Offset(hx - r * 0.42f, hy - gape * 0.6f), Size(r * 0.84f, gape * 1.6f))
        plate(hx, hy - gape * 0.5f, floatArrayOf(-r * 0.55f, 0f, r * 0.55f, 0f, r * 0.45f, -r * 0.35f, -r * 0.45f, -r * 0.35f), ml, LAVA)
        plate(hx, hy + gape * 0.6f, floatArrayOf(-r * 0.45f, 0f, r * 0.45f, 0f, r * 0.3f, r * 0.25f, -r * 0.3f, r * 0.25f), m, LAVA)
        for (k in 0 until 6) {
            val x = hx + (k - 2.5f) * r * 0.15f
            crystal(x, hy - gape * 0.5f, PI / 2f, gape * 0.8f, r * 0.08f, WHITE_HOT, HOT, WHITE_HOT)
            crystal(x, hy + gape * 0.6f, -PI / 2f, gape * 0.6f, r * 0.07f, WHITE_HOT, HOT, WHITE_HOT)
        }
        for (s in listOf(-1f, 1f)) drawCircle(HOT.copy(alpha = 0.6f + 0.4f * (pu / 2f)), r * 0.06f, Offset(hx + s * r * 0.3f, hy - gape * 0.5f - r * 0.2f))
        embers(p, 10, 1.4f)
    }
}

/** Burnwire Bishop: a hovering bishop with a spired mitre, a burning halo and looping chains. */
internal object BurnwireBishopBody : BossBody {
    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius; val t = p.time; val pu = pulseOf(p, 2.5f)
        val cy = p.cy - r * 0.1f + sin(t * 1.6f) * r * 0.07f
        lavaPool(p, p.cy + r, 1.2f)
        // Halo above, chain loop around.
        fireRing(Offset(p.cx, cy - r * 1.15f), r * 0.75f, r * 0.2f, HOT)
        val links = 22
        for (k in 0 until links) {
            val a = t * 0.5f + k * 2f * PI / links
            val x = p.cx + cos(a) * r * 1.45f
            val y = cy + r * 0.2f + sin(a) * r * 0.45f
            drawOval(LAVA.copy(alpha = if (sin(a) > 0f) 0.95f else 0.45f), Offset(x - r * 0.07f, y - r * 0.04f), Size(r * 0.14f, r * 0.08f), style = Stroke(2f))
        }
        val m = metal(p); val ml = metalLit(p)
        // Hanging lanterns.
        for (s in listOf(-1f, 1f)) {
            val lx = p.cx + s * r * 1.05f
            drawLine(METAL_DARK, Offset(lx, cy - r * 0.6f), Offset(lx, cy + r * 0.1f), 2f)
            plate(lx, cy + r * 0.3f, floatArrayOf(0f, -r * 0.25f, r * 0.1f, 0f, 0f, r * 0.25f, -r * 0.1f, 0f), ml, LAVA, 1.5f)
            drawCircle(HOT.copy(alpha = 0.8f), r * 0.05f, Offset(lx, cy + r * 0.3f))
        }
        // Robe and mitre.
        plate(p.cx, cy, floatArrayOf(-r * 0.5f, -r * 0.3f, r * 0.5f, -r * 0.3f, r * 0.3f, r * 0.7f, 0f, r * 1.05f, -r * 0.3f, r * 0.7f), m, LAVA)
        plate(p.cx, cy, floatArrayOf(-r * 0.32f, -r * 0.3f, 0f, -r * 1.3f, r * 0.32f, -r * 0.3f), ml, LAVA)
        // Glowing cross.
        drawLine(HOT, Offset(p.cx, cy - r * 1.0f), Offset(p.cx, cy + r * 0.6f), 3f)
        drawLine(HOT, Offset(p.cx - r * 0.25f, cy - r * 0.15f), Offset(p.cx + r * 0.25f, cy - r * 0.15f), 3f)
        core(Offset(p.cx, cy - r * 0.15f), r * 0.12f, p.color, pu)
        embers(p, 8)
    }
}

/** Furnace Maw: a titanic mouth of black fangs round a white-hot throat, burning stacks at its sides. */
internal object FurnaceMawBody : BossBody {
    override val previewSpan: Float get() = 4.4f
    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius; val t = p.time; val pu = pulseOf(p, 2f)
        val cy = p.cy - r * 0.1f
        lavaPool(p, p.cy + r, 1.6f)
        val m = metal(p); val ml = metalLit(p)
        // Side stacks breathing fire.
        for (s in listOf(-1f, 1f)) {
            val x = p.cx + s * r * 1.35f
            plate(x, cy, floatArrayOf(-r * 0.22f, -r * 0.5f, r * 0.22f, -r * 0.5f, r * 0.28f, r * 0.5f, -r * 0.28f, r * 0.5f), ml, LAVA)
            val fh = r * (0.5f + 0.2f * sin(t * 7f + s))
            drawPath(Path().apply { moveTo(x - r * 0.2f, cy - r * 0.5f); quadraticTo(x + s * r * 0.2f, cy - r * 0.5f - fh * 1.5f, x + r * 0.2f, cy - r * 0.5f); close() },
                Brush.verticalGradient(listOf(Color.Transparent, LAVA, HOT), startY = cy - r * 0.5f - fh, endY = cy - r * 0.5f))
            drawLine(METAL_DARK, Offset(x, cy + r * 0.5f), Offset(p.cx + s * r * 0.8f, cy + r * 0.85f), r * 0.08f)
        }
        // The maw: shell, throat, fangs.
        val open = 1f + (if (p.winding) 0.12f else 0f) + 0.03f * sin(t * 2.5f)
        drawOval(m, Offset(p.cx - r * 1.15f, cy - r * 0.95f * open), Size(r * 2.3f, r * 1.9f * open))
        drawOval(LAVA, Offset(p.cx - r * 1.15f, cy - r * 0.95f * open), Size(r * 2.3f, r * 1.9f * open), style = Stroke(2.5f))
        val throat = Offset(p.cx, cy)
        drawOval(SafeBrush.radialGradient(listOf(WHITE_HOT, HOT, LAVA, p.color), throat, r * 0.85f), Offset(p.cx - r * 0.8f, cy - r * 0.7f * open), Size(r * 1.6f, r * 1.4f * open))
        glow(throat, r * 0.5f, HOT, 1f + pu)
        val n = 14
        for (k in 0 until n) {
            val a = k * 2f * PI / n
            val bx = p.cx + cos(a) * r * 0.85f
            val by = cy + sin(a) * r * 0.75f * open
            crystal(bx, by, a + PI, r * (0.32f + 0.08f * (k % 2)), r * 0.13f, Color(0xFF2A140E), METAL_DARK, LAVA)
        }
        embers(p, 12, 1.5f)
    }
}

// ======================================================================================
// Pack Gamma
// ======================================================================================

/** Redline Sovereign: a stepped black monolith-king, crown spires in a burning halo, a molten seam down its heart. */
internal object RedlineSovereignBody : BossBody {
    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius; val t = p.time; val pu = pulseOf(p, 2f)
        val ground = p.cy + r * 0.95f
        lavaPool(p, ground, 1.6f)
        val m = metal(p); val ml = metalLit(p)
        // Side spires.
        for (s in listOf(-1f, 1f)) {
            crystal(p.cx + s * r * 1.35f, ground - r * 0.3f, -PI / 2f, r * 1.6f, r * 0.3f, Color(0xFF3A1810), METAL_DARK, LAVA)
        }
        // Stepped body.
        block(p.cx, ground, r * 1.9f, r * 0.8f, r * 0.55f, ml, m, LAVA)
        block(p.cx, ground - r * 0.55f, r * 1.45f, r * 0.7f, r * 0.6f, ml, m, LAVA)
        block(p.cx, ground - r * 1.15f, r * 1.0f, r * 0.6f, r * 0.55f, ml, m, LAVA)
        // Crown spires and halo.
        val top = ground - r * 1.7f
        fireRing(Offset(p.cx, top - r * 0.35f), r * 0.85f, r * 0.22f, HOT)
        for (k in 0 until 5) {
            val x = p.cx + (k - 2f) * r * 0.22f
            val h = r * (0.55f + 0.3f * (1f - abs(k - 2f) / 2f))
            crystal(x, top, -PI / 2f, h, r * 0.14f, Color(0xFF4A1E10), METAL_DARK, HOT)
        }
        // The redline seam.
        val seam = 0.6f + 0.4f * sin(t * 5f)
        drawLine(LAVA.copy(alpha = seam), Offset(p.cx, top), Offset(p.cx, ground), r * 0.06f)
        drawLine(WHITE_HOT.copy(alpha = seam), Offset(p.cx, top), Offset(p.cx, ground), r * 0.02f)
        core(Offset(p.cx, ground - r * 0.9f), r * 0.2f, p.color, pu)
        embers(p, 12, 1.3f, 2.6f)
    }
}

/** Scorch Halo: a floating wheel of blades round a single slit eye; it spins faster each phase. */
internal object ScorchHaloBody : BossBody {
    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius; val t = p.time; val pu = pulseOf(p, 3f)
        val c = Offset(p.cx, p.cy - r * 0.15f + sin(t * 1.4f) * r * 0.06f)
        lavaPool(p, p.cy + r, 1.3f)
        val spin = t * (0.8f + 0.4f * p.phase + (if (p.winding) 1.2f else 0f))
        val m = metal(p)
        // Blade arrows round the rim.
        for (k in 0 until 8) {
            val a = spin + k * PI / 4f
            crystal(c.x + cos(a) * r * 1.05f, c.y + sin(a) * r * 1.05f, a, r * 0.55f, r * 0.26f, Color(0xFF4A1E10), METAL_DARK, LAVA)
        }
        // Rings.
        drawCircle(m, r * 1.05f, c)
        drawCircle(LAVA, r * 1.05f, c, style = Stroke(r * 0.06f))
        drawCircle(METAL_LIT, r * 0.8f, c, style = Stroke(r * 0.18f))
        for (k in 0 until 12) {
            val a = -spin * 1.5f + k * PI / 6f
            drawLine(HOT.copy(alpha = 0.8f), Offset(c.x + cos(a) * r * 0.7f, c.y + sin(a) * r * 0.7f), Offset(c.x + cos(a) * r * 0.9f, c.y + sin(a) * r * 0.9f), 2.5f)
        }
        drawCircle(LAVA.copy(alpha = 0.7f), r * 0.6f, c, style = Stroke(2f))
        // The eye.
        core(c, r * 0.4f, p.color, pu)
        drawOval(METAL_DARK, Offset(c.x - r * 0.07f, c.y - r * 0.3f), Size(r * 0.14f, r * 0.6f))
        embers(p, 10, 1.3f)
    }
}

/** Volcanic Null: a towering obsidian monolith with a molten eye, suppression pylons and an ash halo. */
internal object VolcanicNullBody : BossBody {
    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius; val t = p.time; val pu = pulseOf(p, 1.6f)
        val ground = p.cy + r * 0.95f
        lavaPool(p, ground, 1.7f)
        val m = metal(p); val ml = metalLit(p)
        fireRing(Offset(p.cx, ground - r * 1.3f), r * 1.55f, r * 0.3f, LAVA.copy(alpha = 0.6f))
        // Pylons with orbiting shards.
        for (s in listOf(-1f, 1f)) {
            val x = p.cx + s * r * 1.25f
            block(x, ground, r * 0.36f, r * 0.3f, r * 1.4f, ml, m, LAVA)
            val fy = ground - r * (1.8f + 0.1f * sin(t * 2f + s))
            crystal(x, fy + r * 0.2f, -PI / 2f, r * 0.45f, r * 0.2f, Color(0xFF3A1810), METAL_DARK, LAVA)
        }
        // The monolith with ribbed sides.
        block(p.cx, ground, r * 1.2f, r * 0.7f, r * 2.2f, ml, m, LAVA)
        for (k in 0 until 6) {
            val y = ground - r * (0.3f + k * 0.32f)
            drawLine(LAVA.copy(alpha = 0.3f + 0.25f * sin(t * 2f + k)), Offset(p.cx - r * 0.5f, y), Offset(p.cx + r * 0.5f, y), 1.6f)
        }
        drawLine(LAVA, Offset(p.cx, ground - r * 2.1f), Offset(p.cx, ground), r * 0.05f)
        core(Offset(p.cx, ground - r * 1.3f), r * 0.24f, p.color, pu)
        embers(p, 10, 1.6f, 2.6f)
    }
}

/** Smolder Hex: a hexagonal curse engine, a burning sigil in its face and six ember turrets at its corners. */
internal object SmolderHexBody : BossBody {
    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius; val t = p.time; val pu = pulseOf(p, 3f)
        val c = Offset(p.cx, p.cy - r * 0.1f)
        lavaPool(p, p.cy + r, 1.5f)
        val m = metal(p); val ml = metalLit(p)
        val rot = t * 0.25f
        // Outer sigil circle.
        drawCircle(LAVA.copy(alpha = 0.5f), r * 1.45f, c, style = Stroke(2f))
        // Turrets at the six corners.
        for (k in 0 until 6) {
            val a = rot + k * PI / 3f
            val tx = c.x + cos(a) * r * 1.1f; val ty = c.y + sin(a) * r * 1.1f
            val aim = p.aim
            drawLine(METAL_DARK, Offset(tx, ty), Offset(tx + cos(aim) * r * 0.32f, ty + sin(aim) * r * 0.32f), r * 0.09f)
            drawCircle(ml, r * 0.2f, Offset(tx, ty))
            drawCircle(LAVA, r * 0.2f, Offset(tx, ty), style = Stroke(2f))
            drawCircle(HOT.copy(alpha = 0.6f + 0.4f * sin(t * 6f + k)), r * 0.07f, Offset(tx, ty))
        }
        // Hex body and inner sigil.
        plate(c.x, c.y, ngon(6, r * 0.95f, rot), m, LAVA, 2.5f)
        plate(c.x, c.y, ngon(6, r * 0.65f, rot), ml, LAVA, 1.5f)
        val sig = HOT.copy(alpha = 0.6f + 0.3f * sin(t * 4f))
        val tri1 = ngon(3, r * 0.5f, -rot * 2f - PI / 2f)
        val tri2 = ngon(3, r * 0.5f, -rot * 2f + PI / 2f)
        drawPath(polyPath(c.x, c.y, tri1), sig, style = Stroke(2f))
        drawPath(polyPath(c.x, c.y, tri2), sig, style = Stroke(2f))
        core(c, r * 0.18f, p.color, pu)
        embers(p, 10, 1.4f)
    }
}

/** Combustion Monarch: a moth-queen with four great burning wings round a reactor heart, crowned with a halo. */
internal object CombustionMonarchBody : BossBody {
    override val previewSpan: Float get() = 5f
    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius; val t = p.time; val pu = pulseOf(p, 3f)
        val cy = p.cy - r * 0.2f + sin(t * 2f) * r * 0.08f
        lavaPool(p, p.cy + r, 1.7f)
        val flap = sin(t * 3f) * 0.12f + (if (p.winding) 0.15f else 0f)
        // Wings: upper and lower on each side, each a fan of flame feathers.
        for (s in listOf(-1f, 1f)) {
            for (w in 0..1) {
                val base = if (w == 0) -0.35f - flap else 0.45f + flap * 0.6f
                val len = if (w == 0) r * 2.0f else r * 1.4f
                for (f in 0 until 5) {
                    val a = (if (s < 0) PI else 0f) + s * (base + (f - 2) * 0.16f)
                    val l = len * (1f - abs(f - 2) * 0.1f)
                    crystal(p.cx + s * r * 0.2f, cy, a, l, r * 0.3f, Color(0xFFFF5A1A).copy(alpha = 0.85f), Color(0xFF7A1A08), HOT, 1f)
                }
            }
        }
        // Thorax and crown.
        val m = metal(p); val ml = metalLit(p)
        plate(p.cx, cy, floatArrayOf(0f, -r * 0.9f, r * 0.3f, -r * 0.3f, r * 0.22f, r * 0.6f, 0f, r * 1.0f, -r * 0.22f, r * 0.6f, -r * 0.3f, -r * 0.3f), m, LAVA)
        for (k in 0 until 3) crystal(p.cx + (k - 1) * r * 0.16f, cy - r * 0.75f, -PI / 2f + (k - 1) * 0.35f, r * (0.45f - abs(k - 1) * 0.12f), r * 0.1f, ml, METAL_DARK, HOT)
        fireRing(Offset(p.cx, cy - r * 1.35f), r * 0.55f, r * 0.14f, HOT)
        core(Offset(p.cx, cy), r * 0.26f, p.color, pu)
        embers(p, 16, 2f, 2.6f)
    }
}

/** Registry used by [BossBodies]. */
internal val infernoBodies: Map<String, BossBody> = mapOf(
    "hellcore_tyrant" to HellcoreTyrantBody,
    "ember_warden" to EmberWardenBody,
    "cinder_widow" to CinderWidowBody,
    "magma_pulse" to MagmaPulseBody,
    "ashen_reaver" to AshenReaverBody,
    "infernal_router" to InfernalRouterBody,
    "pyroclast_oracle" to PyroclastOracleBody,
    "searfang_colossus" to SearfangColossusBody,
    "burnwire_bishop" to BurnwireBishopBody,
    "furnace_maw" to FurnaceMawBody,
    "redline_sovereign" to RedlineSovereignBody,
    "scorch_halo" to ScorchHaloBody,
    "volcanic_null" to VolcanicNullBody,
    "smolder_hex" to SmolderHexBody,
    "combustion_monarch" to CombustionMonarchBody
)
