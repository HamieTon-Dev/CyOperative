package com.cyberoperative.game.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Nullshade Specter, revision 2, traced from the owner's reference
 * (docs/boss-concepts/nullshade_reference_owner.webp): "Think DARKNESS GHOST",
 * "There shouldn't be an outline really".
 *
 * A faceted dark hood over a pitch-black face, two sharp red eyes that blink and
 * throw a faint red light around them, and a body that is not a robe at all but
 * a cloud of black voxel blocks with magenta light bleeding between them. A
 * clawed hand reaches out of the dark; a magenta energy ring burns on the floor.
 * While [BossPose.veiled] it sinks into the dark and only its eye-glints show.
 */
internal object NullshadeSpecterBody : BossBody {
    private val EYE = Color(0xFFFF2A3A)
    private val HOOD_LIT = Color(0xFF2B1B36)
    private val HOOD_MID = Color(0xFF1B1024)
    private val HOOD_DARK = Color(0xFF0E0814)
    private val VOID = Color(0xFF020103)

    /** Stable pseudo-random 0..1 per index and salt. */
    private fun hash(i: Int, salt: Int): Float {
        var x = i * 374761393 + salt * 668265263
        x = (x xor (x ushr 13)) * 1274126177
        return ((x xor (x ushr 16)) and 0xFFFF) / 65535f
    }

    /** 0 = shut … 1 = open. Blinks every ~3.5 s (quick close and open). */
    private fun eyeOpen(t: Float, hot: Boolean): Float {
        if (hot) return 1f
        val c = (t + 1.8f) % 3.6f
        return if (c < 0.22f) kotlin.math.abs(c - 0.11f) / 0.11f else 1f
    }

    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius * 1.35f
        val t = p.time
        val mag = p.color
        val hot = p.winding
        val vis = 1f - 0.85f * p.veiled
        val bob = sin(t * 1.6f) * r * 0.04f
        val cy = p.cy + bob
        val ground = p.cy + p.radius * 0.95f
        val open = eyeOpen(t, hot) * (if (p.veiled > 0.5f && !hot) 0.7f else 1f)
        val swell = if (hot) 1.12f else 1f

        // Magenta light pooled on the floor, and the energy ring.
        drawOval(
            Brush.radialGradient(listOf(mag.copy(alpha = 0.32f * vis), mag.copy(alpha = 0f)), center = Offset(p.cx, ground), radius = r * 1.3f),
            Offset(p.cx - r * 1.3f, ground - r * 0.5f), Size(r * 2.6f, r * 1.0f)
        )
        val ringA = (0.55f + 0.25f * sin(t * 2.2f) + (if (hot) 0.3f else 0f)) * vis
        drawOval(mag.copy(alpha = ringA * 0.35f), Offset(p.cx - r * 1.12f, ground - r * 0.4f), Size(r * 2.24f, r * 0.8f), style = Stroke(9f))
        drawOval(lighter(mag, 0.4f).copy(alpha = ringA), Offset(p.cx - r * 1.1f, ground - r * 0.39f), Size(r * 2.2f, r * 0.78f), style = Stroke(2.2f))
        // Faint red light the eyes throw on the floor in front of it.
        drawOval(EYE.copy(alpha = 0.12f * open * vis), Offset(p.cx - r * 0.5f, ground - r * 0.15f), Size(r, r * 0.3f))

        // Magenta glow behind the voxel cloud.
        val cloudC = Offset(p.cx + r * 0.1f, cy + r * 0.32f)
        drawOval(
            Brush.radialGradient(listOf(lighter(mag, 0.15f).copy(alpha = 0.85f * vis), mag.copy(alpha = 0.4f * vis), mag.copy(alpha = 0f)), center = cloudC, radius = r * 1.3f * swell),
            Offset(cloudC.x - r * 1.45f * swell, cloudC.y - r * 0.95f * swell), Size(r * 2.9f * swell, r * 1.9f * swell)
        )

        // Shoulders: a soft dark mass, no outline, broken up by the blocks drawn over it.
        val shoulders = Path().apply {
            moveTo(p.cx - r * 0.95f, cy + r * 0.4f)
            cubicTo(p.cx - r * 0.9f, cy - r * 0.2f, p.cx - r * 0.5f, cy - r * 0.5f, p.cx, cy - r * 0.5f)
            cubicTo(p.cx + r * 0.5f, cy - r * 0.5f, p.cx + r * 0.95f, cy - r * 0.2f, p.cx + r * 1.0f, cy + r * 0.45f)
            lineTo(p.cx + r * 0.6f, cy + r * 0.85f)
            lineTo(p.cx - r * 0.55f, cy + r * 0.8f)
            close()
        }
        drawPath(shoulders, Brush.verticalGradient(listOf(HOOD_MID.copy(alpha = vis), VOID.copy(alpha = 0.9f * vis)), startY = cy - r * 0.45f, endY = cy + r * 0.75f))

        // The voxel cloud: dark blocks (and a few lit magenta ones) spreading out of the body.
        val blocks = 95 + p.phase * 20
        for (i in 0 until blocks) {
            // Denser near the body, thinning outward; drifts and flickers.
            val ang = hash(i, 1) * 6.283f
            val dist = sqrt(hash(i, 2)) * r * 1.25f * swell
            val drift = sin(t * (0.6f + hash(i, 3)) + i) * r * 0.05f
            val bx = cloudC.x + cos(ang) * dist * 1.15f + drift
            val by = cloudC.y + sin(ang) * dist * 0.62f - ((t * 0.12f + hash(i, 4)) % 1f) * r * 0.25f
            val life = (t * (0.3f + hash(i, 5) * 0.4f) + hash(i, 6)) % 1f
            if (life > 0.92f) continue
            val sz = r * (0.11f + 0.15f * hash(i, 7)) * (1f - dist / (r * 1.8f)).coerceIn(0.35f, 1f)
            val lit = hash(i, 8) < 0.2f + 0.05f * p.phase
            val a = vis * (if (life < 0.08f) life / 0.08f else 1f)
            if (lit) {
                drawRect(mag.copy(alpha = 0.85f * a), Offset(bx, by), Size(sz, sz))
                drawRect(lighter(mag, 0.5f).copy(alpha = 0.7f * a), Offset(bx, by), Size(sz, sz * 0.28f))
            } else {
                // Black cube: a slightly lit top face catching the magenta glow.
                drawRect(VOID.copy(alpha = a), Offset(bx, by), Size(sz, sz))
                drawRect(Color(0xFF2A1238).copy(alpha = a), Offset(bx, by), Size(sz, sz * 0.25f))
            }
        }

        // Clawed hand reaching out of the dark (toward the operative's side).
        val side = if (cos(p.aim) < 0f) -1f else 1f
        val reach = 1f + 0.06f * sin(t * 1.4f) + (if (hot) 0.12f else 0f)
        val sx = p.cx + side * r * 0.45f
        val sy = cy + r * 0.05f
        val hx = p.cx + side * r * 0.98f * reach
        val hy = cy + r * 0.22f
        val arm = Path().apply {
            moveTo(sx, sy - r * 0.2f)
            quadraticTo(p.cx + side * r * 0.8f, sy - r * 0.12f, hx, hy - r * 0.1f)
            lineTo(hx, hy + r * 0.1f)
            quadraticTo(p.cx + side * r * 0.72f, sy + r * 0.22f, sx, sy + r * 0.22f)
            close()
        }
        drawPath(arm, HOOD_DARK.copy(alpha = vis))
        // Rim of magenta light along the underside only (light from below, not an outline).
        drawLine(mag.copy(alpha = 0.35f * vis), Offset(sx, sy + r * 0.13f), Offset(hx, hy + r * 0.07f), 1.4f)
        for (k in 0 until 4) {
            val fa = (if (side > 0f) 0f else Math.PI.toFloat()) + (k - 1.5f) * 0.32f * side + 0.25f * side
            val curl = sin(t * 2f + k) * 0.08f
            crystal(hx, hy, fa + curl, r * (0.26f + 0.05f * (k % 2)), r * 0.07f, Color(0xFF1C1024).copy(alpha = vis), VOID.copy(alpha = vis), Color.Transparent)
        }

        // Faceted hood (low-poly planes, no outline).
        val hc = Offset(p.cx, cy - r * 0.55f)
        val tip = Offset(p.cx + r * 0.06f, cy - r * 1.15f)
        val l = Offset(p.cx - r * 0.66f, cy - r * 0.1f)
        val rr = Offset(p.cx + r * 0.68f, cy - r * 0.08f)
        val chinL = Offset(p.cx - r * 0.3f, cy + r * 0.12f)
        val chinR = Offset(p.cx + r * 0.32f, cy + r * 0.12f)
        val brow = Offset(p.cx, cy - r * 0.82f)
        fun facet(c: Color, pts: List<Offset>) {
            drawPath(Path().apply { moveTo(pts[0].x, pts[0].y); for (q in 1 until pts.size) lineTo(pts[q].x, pts[q].y); close() }, c.copy(alpha = vis))
        }
        val flash = if (p.hitFlash) 0.5f else 0f
        val browL = Offset(p.cx - r * 0.5f, cy - r * 0.62f)
        facet(lighter(HOOD_LIT, flash), listOf(tip, brow, browL))
        facet(lighter(HOOD_MID, flash), listOf(browL, brow, chinL, l))
        facet(lighter(HOOD_DARK, flash), listOf(tip, rr, brow))
        facet(lighter(HOOD_MID, flash * 0.6f), listOf(brow, rr, chinR))
        facet(lighter(HOOD_LIT, flash * 0.6f), listOf(tip, browL, Offset(p.cx - r * 0.12f, cy - r * 0.95f)))

        // Face: a pitch-black hollow, its inner edge lit faintly red by the eyes.
        val face = Path().apply {
            moveTo(p.cx - r * 0.36f, cy - r * 0.62f)
            quadraticTo(p.cx, cy - r * 0.84f, p.cx + r * 0.38f, cy - r * 0.6f)
            quadraticTo(p.cx + r * 0.38f, cy - r * 0.1f, p.cx + r * 0.02f, cy + r * 0.08f)
            quadraticTo(p.cx - r * 0.38f, cy - r * 0.1f, p.cx - r * 0.36f, cy - r * 0.62f)
            close()
        }
        drawPath(face, Brush.radialGradient(
            listOf(EYE.copy(alpha = 0.14f * open * vis), VOID.copy(alpha = 0.98f), VOID),
            center = Offset(hc.x, hc.y - r * 0.02f), radius = r * 0.42f
        ))

        // Eyes: sharp almond slits angled up and out; white-hot cores.
        val look = Offset(cos(p.aim) * r * 0.03f, sin(p.aim) * r * 0.02f)
        val eyeW = r * 0.25f * (if (hot) 1.12f else 1f)
        val glint = p.veiled > 0.5f && !hot
        for (s in listOf(-1f, 1f)) {
            val ec = Offset(hc.x + s * r * 0.185f + look.x, hc.y - r * 0.02f + look.y)
            if (open <= 0.05f) continue
            // Faint red light spilling around the eye.
            // Owner, 2026-10-10: "Fainter glow around the eyes. They should be sharper", then "bigger eyes please".
            drawOval(
                Brush.radialGradient(listOf(EYE.copy(alpha = (if (glint) 0.08f else 0.14f) * open * (if (hot) 1.5f else 1f)), EYE.copy(alpha = 0f)), center = ec, radius = eyeW * 0.6f),
                Offset(ec.x - eyeW * 0.6f, ec.y - eyeW * 0.32f), Size(eyeW * 1.2f, eyeW * 0.64f)
            )
            val h = eyeW * 0.3f * open
            // Pointed blade-like slit: sharp inner and outer corners, angled up and out.
            val eye = Path().apply {
                moveTo(ec.x - s * eyeW * 0.55f, ec.y + h * 0.35f)
                lineTo(ec.x - s * eyeW * 0.1f, ec.y - h * 0.55f)
                lineTo(ec.x + s * eyeW * 0.62f, ec.y - h * 1.0f)
                lineTo(ec.x + s * eyeW * 0.2f, ec.y + h * 0.55f)
                close()
            }
            drawPath(eye, EYE.copy(alpha = if (glint) 0.7f else 1f))
            if (!glint) {
                val core = Path().apply {
                    moveTo(ec.x - s * eyeW * 0.28f, ec.y + h * 0.1f)
                    lineTo(ec.x + s * eyeW * 0.42f, ec.y - h * 0.62f)
                    lineTo(ec.x + s * eyeW * 0.1f, ec.y + h * 0.25f)
                    close()
                }
                drawPath(core, Color(0xFFFFE6E6).copy(alpha = if (hot) 1f else 0.9f))
            }
        }

        // Phase 3: red static flickering through the cloud.
        if (p.phase >= 2 && vis > 0.3f) for (i in 0 until 3) {
            if (((t * 10f).toInt() + i * 3) % 3 == 0) continue
            val a0 = hash(i + (t * 4f).toInt() * 7, 9) * 6.283f
            var x = cloudC.x + cos(a0) * r * 0.5f
            var y = cloudC.y + sin(a0) * r * 0.35f
            for (seg in 0 until 4) {
                val nx = x + (hash(i * 5 + seg, 10) - 0.5f) * r * 0.35f
                val ny = y + (hash(i * 5 + seg, 11) - 0.5f) * r * 0.3f
                drawLine(EYE.copy(alpha = 0.75f * vis), Offset(x, y), Offset(nx, ny), 1.6f)
                x = nx; y = ny
            }
        }
    }
}
