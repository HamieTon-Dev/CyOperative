package com.cyberoperative.game.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Nullshade Specter (dossier, BLACKOUT HUNTER): a floating hooded wraith whose
 * tattered cloak dissolves into magenta pixels, two angular red eyes burning
 * inside a pitch-black hood. Phase 2 trails phantom afterimages; phase 3
 * crackles with red static. While [BossPose.veiled] it melts into the dark and
 * only its eye-glints show.
 */
internal object NullshadeSpecterBody : BossBody {
    private val CLOAK_TOP = Color(0xFF1C0C26)
    private val CLOAK_BOTTOM = Color(0xFF060209)
    private val EYE = Color(0xFFFF2040)

    override fun DrawScope.draw(p: BossPose) {
        // Drawn larger than its hit radius: it looms over the room.
        val r = p.radius * 1.3f
        val t = p.time
        val purple = p.color
        val hot = p.winding
        // Visible amount: 1 in the open, ~0.18 deep in the shadows (eyes still glint).
        val vis = 1f - 0.82f * p.veiled
        val bob = sin(t * 1.8f) * r * 0.06f
        val cy = p.cy + bob
        val spread = if (hot) 1.15f else 1f

        // Shadow pool and drifting pixel debris on the floor.
        val ground = p.cy + r * 0.95f
        drawOval(Color.Black.copy(alpha = 0.5f * vis), Offset(p.cx - r, ground - r * 0.22f), Size(r * 2f, r * 0.44f))
        drawOval(purple.copy(alpha = 0.18f * vis), Offset(p.cx - r * 1.1f, ground - r * 0.26f), Size(r * 2.2f, r * 0.52f), style = Stroke(2f))

        // Phase 2+: phantom afterimages drifting behind it.
        if (p.phase >= 1) for (k in 1..2) {
            val off = sin(t * 1.3f + k * 2f) * r * 0.5f * k
            drawPath(cloakPath(p.cx + off, cy - r * 0.05f * k, r * (1f - 0.08f * k), t + k, spread), purple.copy(alpha = 0.08f * vis / k))
        }

        // Cloak with a dim magenta rim light.
        val cloak = cloakPath(p.cx, cy, r, t, spread)
        val top = if (p.hitFlash) Color(0xFF8A7090) else CLOAK_TOP
        drawPath(cloak, Brush.verticalGradient(listOf(top.copy(alpha = vis), CLOAK_BOTTOM.copy(alpha = vis)), startY = cy - r * 1.2f, endY = cy + r * 0.9f))
        drawPath(cloak, purple.copy(alpha = 0.45f * vis), style = Stroke(1.8f))
        // Folds down the robe.
        for (k in listOf(-0.35f, 0f, 0.35f)) {
            drawLine(purple.copy(alpha = 0.16f * vis), Offset(p.cx + k * r * 0.5f, cy - r * 0.1f), Offset(p.cx + k * r * 1.1f * spread, cy + r * 0.75f), 1.6f)
        }

        // Tattered sleeves reaching out.
        for (s in listOf(-1f, 1f)) {
            val sway = sin(t * 2.2f + s) * r * 0.08f
            val sleeve = Path().apply {
                moveTo(p.cx + s * r * 0.45f, cy - r * 0.2f)
                lineTo(p.cx + s * r * (1.15f * spread), cy + r * 0.2f + sway)
                lineTo(p.cx + s * r * 0.95f, cy + r * 0.28f + sway)
                lineTo(p.cx + s * r * (1.05f * spread), cy + r * 0.42f + sway)
                lineTo(p.cx + s * r * 0.55f, cy + r * 0.3f)
                close()
            }
            drawPath(sleeve, CLOAK_BOTTOM.copy(alpha = vis))
            drawPath(sleeve, purple.copy(alpha = 0.35f * vis), style = Stroke(1.4f))
        }

        // Pitch-black hood opening.
        val hoodC = Offset(p.cx, cy - r * 0.42f)
        drawOval(Color.Black.copy(alpha = 0.95f * vis.coerceAtLeast(0.6f)), Offset(hoodC.x - r * 0.42f, hoodC.y - r * 0.34f), Size(r * 0.84f, r * 0.7f))
        // Hood rim catching a little magenta light.
        drawArc(purple.copy(alpha = 0.5f * vis), 195f, 150f, false, Offset(hoodC.x - r * 0.45f, hoodC.y - r * 0.38f), Size(r * 0.9f, r * 0.78f), style = Stroke(2f))

        // Eyes: angular slits. Open = burning; veiled = faint blinking glints.
        val look = Offset(cos(p.aim) * r * 0.05f, sin(p.aim) * r * 0.03f)
        val blink = if (p.veiled > 0.5f) (if (((t * 1.4f).toInt() % 4) == 0) 0.25f else 1f) else 1f
        val eyeA = when {
            hot -> 1f
            p.veiled > 0.5f -> 0.55f * blink
            else -> 0.85f + 0.15f * sin(t * 3f)
        }
        val eyeW = r * (0.26f + 0.02f * p.phase) * (if (hot) 1.15f else 1f)
        val veiledEyes = p.veiled > 0.5f && !hot
        for (s in listOf(-1f, 1f)) {
            val ec = Offset(hoodC.x + s * r * 0.19f + look.x, hoodC.y + r * 0.03f + look.y)
            if (veiledEyes) {
                // In the shadows: just a pair of glints.
                drawCircle(EYE.copy(alpha = 0.25f * eyeA), eyeW * 0.35f, ec)
                drawCircle(EYE.copy(alpha = eyeA), eyeW * 0.12f, ec)
                continue
            }
            // Tight glow, then a sharp angled slit with a hot core.
            glow(ec, eyeW * 0.45f, EYE, (if (hot) 2.2f else 0.9f) * eyeA)
            drawCircle(EYE.copy(alpha = 0.22f * eyeA * (if (hot) 2f else 1f)), eyeW * 0.55f, ec)
            val slit = Path().apply {
                moveTo(ec.x - s * eyeW * 0.5f, ec.y - eyeW * 0.26f)
                lineTo(ec.x + s * eyeW * 0.55f, ec.y + eyeW * 0.02f)
                lineTo(ec.x + s * eyeW * 0.2f, ec.y + eyeW * 0.2f)
                lineTo(ec.x - s * eyeW * 0.42f, ec.y + eyeW * 0.04f)
                close()
            }
            drawPath(slit, EYE.copy(alpha = eyeA))
            val core = Path().apply {
                moveTo(ec.x - s * eyeW * 0.25f, ec.y - eyeW * 0.1f)
                lineTo(ec.x + s * eyeW * 0.3f, ec.y + eyeW * 0.03f)
                lineTo(ec.x - s * eyeW * 0.15f, ec.y + eyeW * 0.06f)
                close()
            }
            drawPath(core, lighter(EYE, if (hot) 0.85f else 0.55f).copy(alpha = eyeA))
        }

        // The cloak dissolving into pixels (more each phase).
        val bits = 26 + p.phase * 12
        for (i in 0 until bits) {
            val k = (t * (0.35f + (i % 5) * 0.06f) + i * 0.137f) % 1f
            val side = if (i % 2 == 0) -1f else 1f
            val bx = p.cx + side * r * (0.35f + ((i * 0.618f) % 1f) * 0.8f) + sin(t + i) * r * 0.08f
            val by = cy + r * (0.8f - ((i * 0.381f) % 1f) * 1.4f) - k * r * 0.9f
            val sz = r * (0.045f + 0.035f * (i % 4))
            val col = when (i % 4) { 0, 1 -> purple; 2 -> lighter(purple, 0.3f); else -> Color(0xFF3A1550) }
            drawRect(col.copy(alpha = (1f - k) * 0.85f * vis), Offset(bx, by), Size(sz, sz))
        }

        // Phase 3 / charging: red static arcs crackling around it.
        if ((p.phase >= 2 || hot) && vis > 0.3f) {
            val arcs = if (hot) 5 else 3
            for (i in 0 until arcs) {
                if (((t * 12f).toInt() + i * 3) % 4 == 0) continue
                val a = 0.3f + i * 0.6f + (t * 3f).toInt() * 0.45f
                var x = p.cx + cos(a) * r * 0.6f * (if (i % 2 == 0) 1f else -1f)
                var y = cy + sin(a) * r * 0.45f
                for (seg in 0 until 4) {
                    val nx = x + cos(a + (seg % 2 - 0.5f)) * r * 0.22f
                    val ny = y + sin(a + (seg % 2 - 0.5f)) * r * 0.22f
                    drawLine(EYE.copy(alpha = 0.8f * vis), Offset(x, y), Offset(nx, ny), 1.8f)
                    x = nx; y = ny
                }
            }
        }
    }

    /** Hooded robe: pointed hood, shoulders, flared body, ragged swaying hem. */
    private fun cloakPath(cx: Float, cy: Float, r: Float, t: Float, spread: Float): Path = Path().apply {
        moveTo(cx, cy - r * 1.18f)
        cubicTo(cx + r * 0.42f, cy - r * 1.05f, cx + r * 0.62f, cy - r * 0.55f, cx + r * 0.6f * spread, cy - r * 0.18f)
        cubicTo(cx + r * 0.85f * spread, cy + r * 0.2f, cx + r * 0.95f * spread, cy + r * 0.55f, cx + r * 0.9f * spread, cy + r * 0.78f)
        // Ragged hem, right to left.
        val teeth = 9
        for (i in 0..teeth) {
            val f = 1f - i / teeth.toFloat()
            val x = cx + (f * 2f - 1f) * r * 0.9f * spread
            val sway = sin(t * 3f + i * 1.3f) * r * 0.06f
            val y = cy + r * (if (i % 2 == 0) 0.78f else 0.98f + 0.08f * abs(sin(i * 2.7f))) + sway
            lineTo(x, y)
        }
        cubicTo(cx - r * 0.95f * spread, cy + r * 0.55f, cx - r * 0.85f * spread, cy + r * 0.2f, cx - r * 0.6f * spread, cy - r * 0.18f)
        cubicTo(cx - r * 0.62f, cy - r * 0.55f, cx - r * 0.42f, cy - r * 1.05f, cx, cy - r * 1.18f)
        close()
    }
}
