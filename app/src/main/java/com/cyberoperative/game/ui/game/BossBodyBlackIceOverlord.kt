package com.cyberoperative.game.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/**
 * Black Ice Overlord (Boss Pack Beta 03, STATUS): a dark navy armoured golem
 * encased in huge cyan ice crystals — a crown of shards, crystal pauldrons, a
 * chest crystal — with glowing ice-blue eyes, two shoulder ice cannons and
 * snowflakes drifting around it. In its Permafrost Shell it sits inside a
 * faceted ice dome. More crystals grow each phase.
 */
internal object BlackIceOverlordBody : BossBody {
    private val ARMOR_LIT = Color(0xFF1E3A66)
    private val ARMOR = Color(0xFF0E1C36)
    private val ARMOR_DARK = Color(0xFF060C18)
    private val ICE_LIT = Color(0xFFBFF3FF)
    private val ICE = Color(0xFF5FD0FF)
    private val ICE_DARK = Color(0xFF1A6EB0)

    private fun DrawScope.shard(x: Float, y: Float, a: Float, len: Float, w: Float) = crystal(x, y, a, len, w, ICE_LIT.copy(alpha = 0.92f), ICE_DARK.copy(alpha = 0.92f), Color.White.copy(alpha = 0.9f))

    private fun DrawScope.snowflake(c: Offset, s: Float, a: Float) {
        for (k in 0 until 3) {
            val ang = k * 1.047f
            drawLine(Color.White.copy(alpha = a), Offset(c.x - cos(ang) * s, c.y - sin(ang) * s), Offset(c.x + cos(ang) * s, c.y + sin(ang) * s), 1.5f)
        }
    }

    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius
        val t = p.time
        val hot = p.winding
        val pulse = 0.5f + 0.5f * sin(t * (2.5f + p.phase))
        val ground = p.cy + r * 0.85f
        val grow = 1f + 0.12f * p.phase

        // Frost on the floor with crystal clusters.
        drawOval(Brush.radialGradient(listOf(ICE.copy(alpha = 0.4f), ICE.copy(alpha = 0f)), center = Offset(p.cx, ground), radius = r * 1.8f), Offset(p.cx - r * 1.8f, ground - r * 0.6f), Size(r * 3.6f, r * 1.2f))
        for (k in 0 until 4 + p.phase * 2) {
            val a = k * 1.3f + 0.4f
            shard(p.cx + cos(a) * r * 1.2f, ground + sin(a) * r * 0.4f, -1.571f + 0.3f * cos(k * 2f), r * (0.3f + 0.1f * (k % 3)), r * 0.13f)
        }

        val lit = if (p.hitFlash) lighter(ARMOR_LIT, 0.6f) else ARMOR_LIT
        val mid = if (p.hitFlash) lighter(ARMOR, 0.6f) else ARMOR
        val edge = ICE.copy(alpha = 0.7f)
        fun plate(pts: FloatArray, c: Color) {
            val path = polyPath(p.cx, p.cy, pts)
            drawPath(path, c)
            drawPath(path, edge, style = Stroke(1.8f))
        }
        // Legs and torso.
        plate(floatArrayOf(-r * 0.5f, r * 0.3f, -r * 0.12f, r * 0.3f, -r * 0.18f, r * 0.85f, -r * 0.55f, r * 0.85f), ARMOR_DARK)
        plate(floatArrayOf(r * 0.12f, r * 0.3f, r * 0.5f, r * 0.3f, r * 0.55f, r * 0.85f, r * 0.18f, r * 0.85f), ARMOR_DARK)
        plate(floatArrayOf(-r * 0.7f, -r * 0.4f, r * 0.7f, -r * 0.4f, r * 0.55f, r * 0.4f, -r * 0.55f, r * 0.4f), mid)
        plate(floatArrayOf(-r * 0.5f, -r * 0.35f, r * 0.5f, -r * 0.35f, r * 0.25f, r * 0.05f, -r * 0.25f, r * 0.05f), lit)
        // Shoulder cannons with ice muzzles.
        for (s in listOf(-1f, 1f)) {
            val sx = p.cx + s * r * 0.82f
            val sy = p.cy - r * 0.32f
            drawCircle(mid, r * 0.3f, Offset(sx, sy))
            drawCircle(edge, r * 0.3f, Offset(sx, sy), style = Stroke(1.8f))
            val ma = p.aim
            drawLine(ARMOR_DARK, Offset(sx, sy), Offset(sx + cos(ma) * r * 0.45f, sy + sin(ma) * r * 0.35f), r * 0.16f)
            val mc = Offset(sx + cos(ma) * r * 0.45f, sy + sin(ma) * r * 0.35f)
            drawCircle(ICE.copy(alpha = 0.4f + 0.4f * pulse + (if (hot) 0.3f else 0f)), r * 0.1f * (if (hot) 1.6f else 1f), mc)
            // Crystal pauldron.
            shard(sx, sy - r * 0.15f, -1.571f + s * 0.5f, r * 0.55f * grow, r * 0.28f)
            shard(sx + s * r * 0.1f, sy, -1.571f + s * 1.0f, r * 0.4f * grow, r * 0.2f)
        }
        // Chest crystal.
        shard(p.cx, p.cy + r * 0.25f, -1.571f, r * 0.55f, r * 0.3f)
        glow(Offset(p.cx, p.cy), r * 0.25f, ICE, 0.6f + pulse)
        // Head: helm with glowing eyes.
        val hy = p.cy - r * 0.62f
        plate(floatArrayOf(-r * 0.32f, -r * 0.8f, r * 0.32f, -r * 0.8f, r * 0.28f, -r * 0.42f, 0f, -r * 0.32f, -r * 0.28f, -r * 0.42f), ARMOR_DARK)
        val eyeA = if (hot) 1f else 0.7f + 0.3f * pulse
        for (s in listOf(-1f, 1f)) {
            val ec = Offset(p.cx + s * r * 0.13f + cos(p.aim) * r * 0.02f, hy)
            drawCircle(ICE.copy(alpha = 0.3f * eyeA), r * 0.1f, ec)
            drawLine((if (hot) Color.White else ICE_LIT).copy(alpha = eyeA), Offset(ec.x - s * r * 0.07f, ec.y - r * 0.02f), Offset(ec.x + s * r * 0.07f, ec.y + r * 0.02f), r * 0.05f)
        }
        // Crown of ice shards.
        val crown = 5 + p.phase * 2
        for (k in 0 until crown) {
            val f = k / (crown - 1f) - 0.5f
            shard(p.cx + f * r * 0.5f, p.cy - r * 0.8f, -1.571f + f * 1.4f, r * (0.75f - kotlin.math.abs(f) * 0.5f) * grow, r * 0.22f)
        }
        // Snowflakes drifting around.
        for (k in 0 until 6 + p.phase * 2) {
            val q = (t * 0.25f + k * 0.17f) % 1f
            val a = k * 1.9f
            snowflake(Offset(p.cx + cos(a) * r * (1.1f + 0.5f * q), p.cy - r * 0.3f + sin(a) * r * 0.6f - q * r * 0.6f), r * 0.07f, 1f - q)
        }

        // Permafrost Shell: a faceted ice dome.
        if (p.shield > 0f) {
            val dc = Offset(p.cx, p.cy - r * 0.1f)
            val dr = r * 1.45f
            drawCircle(ICE.copy(alpha = 0.18f), dr, dc)
            drawPath(polyPath(dc.x, dc.y, ngon(8, dr, 0.39f)), ICE_LIT.copy(alpha = 0.25f))
            drawPath(polyPath(dc.x, dc.y, ngon(8, dr, 0.39f)), ICE_LIT.copy(alpha = 0.85f), style = Stroke(2.5f))
            for (k in 0 until 8) {
                val a = 0.39f + k * 0.785f
                drawLine(ICE_LIT.copy(alpha = 0.4f), dc, Offset(dc.x + cos(a) * dr, dc.y + sin(a) * dr), 1.5f)
            }
            // Cracks spread as the shell weakens (shield = fraction left).
            val cracks = ((1f - p.shield) * 8).toInt()
            for (k in 0 until cracks) {
                val a = k * 2.4f
                drawLine(Color.White, Offset(dc.x + cos(a) * dr * 0.3f, dc.y + sin(a) * dr * 0.3f), Offset(dc.x + cos(a + 0.3f) * dr * 0.95f, dc.y + sin(a + 0.3f) * dr * 0.95f), 2f)
            }
        }
    }
}
