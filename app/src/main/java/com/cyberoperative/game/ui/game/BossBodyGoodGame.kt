package com.cyberoperative.game.ui.game

import com.cyberoperative.game.platform.drawGameText
import com.cyberoperative.game.ui.common.GameTextPaint
import com.cyberoperative.game.ui.common.argb
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * GOOD GAME (classic, level 120): an enormous siege platform. Two heavy tracks
 * with rolling treads, a wide armoured hull with crimson hazard trim and a
 * stencilled "GG", smokestacks venting, and a big turret whose twin cannons
 * swing to follow you. Armour plates fall away and the smoke thickens each
 * phase; charging glows the muzzles and the exhaust.
 */
internal object GoodGameBody : BossBody {
    private val LIT = Color(0xFF4E4C50)
    private val MID = Color(0xFF2A282C)
    private val DARK = Color(0xFF0E0D10)

    override val previewSpan: Float get() = 4.6f

    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius * 1.25f
        val t = p.time
        val red = p.color
        val hot = p.winding
        val cy = p.cy
        val lit = if (p.hitFlash) Color.White else LIT

        drawOval(Color.Black.copy(alpha = 0.5f), Offset(p.cx - r * 1.2f, cy + r * 0.55f), Size(r * 2.4f, r * 0.5f))

        // Tracks with moving treads.
        for (s in listOf(-1f, 1f)) {
            val tx = p.cx + s * r * 0.95f
            val tw = r * 0.38f; val th = r * 1.45f
            drawRoundRect(DARK, Offset(tx - tw / 2, cy - th / 2 + r * 0.1f), Size(tw, th), CornerRadius(tw * 0.45f))
            drawRoundRect(MID, Offset(tx - tw / 2, cy - th / 2), Size(tw, th), CornerRadius(tw * 0.45f))
            val roll = (t * 0.9f) % 1f
            for (k in 0 until 10) {
                val y = cy - th / 2 + ((k + roll) / 10f) * th
                if (y < cy - th / 2 + tw * 0.2f || y > cy + th / 2 - tw * 0.2f) continue
                drawLine(DARK, Offset(tx - tw / 2 + 2f, y), Offset(tx + tw / 2 - 2f, y), 2.5f)
            }
            drawRoundRect(red.copy(alpha = 0.5f), Offset(tx - tw / 2, cy - th / 2), Size(tw, th), CornerRadius(tw * 0.45f), style = Stroke(1.6f))
            for (k in 0 until 4) drawCircle(LIT, tw * 0.16f, Offset(tx, cy - th * 0.35f + k * th * 0.23f))
        }

        // Hull.
        val hull = floatArrayOf(-r * 0.8f, -r * 0.65f, r * 0.8f, -r * 0.65f, r * 0.85f, r * 0.55f, -r * 0.85f, r * 0.55f)
        slab(p.cx, cy, hull, r * 0.3f, if (p.hitFlash) Color.White else MID, side = DARK)
        // Hazard trim along the front.
        for (k in 0 until 8) {
            val x = p.cx - r * 0.8f + k * r * 0.2f
            drawLine(if (k % 2 == 0) red else DARK, Offset(x, cy + r * 0.48f), Offset(x + r * 0.12f, cy + r * 0.48f), r * 0.08f)
        }
        // Armour plates; corners fall off with each phase.
        val plates = listOf(Pair(-0.5f, -0.4f), Pair(0.5f, -0.4f), Pair(-0.5f, 0.2f), Pair(0.5f, 0.2f))
        for ((k, pl) in plates.withIndex()) {
            if (k >= 4 - p.phase) {
                // Exposed machinery where the plate was.
                drawRect(Color(0xFF1A0A0C), Offset(p.cx + pl.first * r - r * 0.2f, cy + pl.second * r - r * 0.14f), Size(r * 0.4f, r * 0.3f))
                drawLine(red.copy(alpha = 0.7f + 0.3f * sin(t * 6f + k)), Offset(p.cx + pl.first * r - r * 0.15f, cy + pl.second * r), Offset(p.cx + pl.first * r + r * 0.15f, cy + pl.second * r + r * 0.05f), 2f)
                continue
            }
            drawRect(lit, Offset(p.cx + pl.first * r - r * 0.2f, cy + pl.second * r - r * 0.14f), Size(r * 0.4f, r * 0.3f))
            drawRect(DARK, Offset(p.cx + pl.first * r - r * 0.2f, cy + pl.second * r - r * 0.14f), Size(r * 0.4f, r * 0.3f), style = Stroke(1.6f))
            for (bx in listOf(-0.15f, 0.15f)) drawCircle(DARK, r * 0.025f, Offset(p.cx + (pl.first + bx) * r, cy + pl.second * r - r * 0.09f))
        }
        // GG stencil.
        drawGameText("GG", p.cx, cy + r * 0.38f, GameTextPaint(align = GameTextPaint.Align.LEFT).apply {
            color = argb(200, (red.red * 255).toInt(), (red.green * 255).toInt(), (red.blue * 255).toInt())
            textSize = r * 0.3f; bold = true; align = GameTextPaint.Align.CENTER
        })

        // Smokestacks venting.
        for (s in listOf(-1f, 1f)) {
            val sx = p.cx + s * r * 0.55f; val sy = cy - r * 0.62f
            drawRect(DARK, Offset(sx - r * 0.08f, sy - r * 0.25f), Size(r * 0.16f, r * 0.25f))
            drawRect(lit, Offset(sx - r * 0.06f, sy - r * 0.25f), Size(r * 0.05f, r * 0.25f))
            if (hot) glow(Offset(sx, sy - r * 0.25f), r * 0.1f, red, 1.4f)
            for (k in 0 until 3 + p.phase * 2) {
                val f = ((t * 0.7f + k * 0.21f + (if (s > 0) 0.5f else 0f)) % 1f)
                drawCircle(Color(0xFF3A3436).copy(alpha = 0.55f * (1f - f)), r * (0.08f + 0.15f * f), Offset(sx + sin(f * 5f + k) * r * 0.1f, sy - r * 0.3f - f * r * 0.8f))
            }
        }

        // Turret with twin cannons, following the aim.
        val tc = Offset(p.cx, cy - r * 0.1f)
        val tr = r * 0.42f
        rotate(p.aim * 180f / PI.toFloat(), tc) {
            // Mantlet, then two thick barrels with muzzle brakes.
            drawRect(DARK, Offset(tc.x + tr * 0.6f, tc.y - tr * 0.62f), Size(r * 0.22f, tr * 1.24f))
            for (s in listOf(-1f, 1f)) {
                val by = tc.y + s * tr * 0.34f
                drawRect(DARK, Offset(tc.x, by - r * 0.12f), Size(r * 1.0f, r * 0.24f))
                drawRect(Brush.verticalGradient(listOf(lit, MID, DARK), by - r * 0.1f, by + r * 0.1f), Offset(tc.x, by - r * 0.1f), Size(r * 0.95f, r * 0.2f))
                drawRect(DARK, Offset(tc.x + r * 0.85f, by - r * 0.16f), Size(r * 0.2f, r * 0.32f))
                drawRect(MID, Offset(tc.x + r * 0.87f, by - r * 0.14f), Size(r * 0.16f, r * 0.28f))
                drawLine(DARK, Offset(tc.x + r * 0.95f, by - r * 0.14f), Offset(tc.x + r * 0.95f, by + r * 0.14f), 2f)
                if (hot) glow(Offset(tc.x + r * 1.05f, by), r * 0.12f, red, 1.6f)
            }
        }
        // SIEGE CANNON charge: heat climbs the barrels, the muzzles swell with light and
        // sparks are sucked in; a white-hot flash just before it fires.
        val ch = p.charge
        if (ch > 0f) {
            rotate(p.aim * 180f / PI.toFloat(), tc) {
                for (s in listOf(-1f, 1f)) {
                    val by = tc.y + s * tr * 0.34f
                    drawRect(Color(0xFFFF5A1A).copy(alpha = 0.55f * ch), Offset(tc.x + r * 0.1f, by - r * 0.06f), Size(r * 0.85f * ch, r * 0.12f))
                    val m = Offset(tc.x + r * 1.08f, by)
                    glow(m, r * (0.1f + 0.22f * ch), Color(0xFFFF7A1A), 1f + 2f * ch)
                    drawCircle(Color(0xFFFFC14D).copy(alpha = 0.5f + 0.5f * ch), r * (0.05f + 0.1f * ch), m)
                    if (ch > 0.85f) drawCircle(Color.White.copy(alpha = (ch - 0.85f) / 0.15f), r * 0.12f, m)
                    for (k in 0 until 6) {
                        val a = k * (PI.toFloat() / 3f) + t * 3f
                        val d = r * (0.55f - 0.45f * ((t * 2.2f + k * 0.17f) % 1f)) * (1.2f - ch * 0.4f)
                        drawCircle(Color(0xFFFFC14D).copy(alpha = 0.8f * ch), r * 0.025f, Offset(m.x + cos(a) * d, m.y + sin(a) * d))
                    }
                }
            }
            drawCircle(Color(0xFFFF7A1A).copy(alpha = 0.6f * ch), tr * (1.1f + 0.15f * sin(t * 30f)), tc, style = Stroke(3f))
        }
        drawCircle(DARK, tr * 1.05f, Offset(tc.x, tc.y + r * 0.06f))
        drawCircle(Brush.radialGradient(listOf(lit, MID, DARK), center = Offset(tc.x - tr * 0.3f, tc.y - tr * 0.35f), radius = tr * 1.4f), tr, tc)
        drawCircle(red.copy(alpha = 0.8f), tr, tc, style = Stroke(2.5f))
        // Commander hatch with a sensor eye.
        val eye = if (hot) 1f else 0.6f + 0.4f * sin(t * 2f)
        drawCircle(DARK, tr * 0.35f, Offset(tc.x - cos(p.aim) * tr * 0.3f, tc.y - sin(p.aim) * tr * 0.3f))
        drawCircle(red.copy(alpha = eye), tr * 0.14f, Offset(tc.x - cos(p.aim) * tr * 0.3f, tc.y - sin(p.aim) * tr * 0.3f))
    }
}
