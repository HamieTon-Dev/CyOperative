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

/**
 * Ransom King (Boss Pack Gamma 02): a hulking block golem of dark armour with
 * red neon seams, a cube head with angry red eyes and a jagged grin, a spiked
 * gold crown licked by flames, and padlocked red cubes orbiting him — more of
 * them each phase.
 */
internal object RansomKingBody : BossBody {
    private val TOP = Color(0xFF2A3047)
    private val FACE = Color(0xFF141826)
    private val DARK = Color(0xFF0A0C14)
    private val GOLD = Color(0xFFFFC233)
    private val GOLD_DARK = Color(0xFFB8740F)
    private val FLAME = Color(0xFFFF7A1A)

    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius
        val t = p.time
        val red = p.color
        val hot = p.winding
        val pulse = 0.5f + 0.5f * sin(t * (3f + p.phase))
        val ground = p.cy + r * 0.7f
        val top = if (p.hitFlash) lighter(TOP, 0.7f) else TOP
        val face = if (p.hitFlash) lighter(FACE, 0.7f) else FACE
        val edge = red

        // Red light pooled under him.
        drawOval(
            Brush.radialGradient(listOf(red.copy(alpha = 0.35f), red.copy(alpha = 0f)), center = Offset(p.cx, ground), radius = r * 1.4f),
            Offset(p.cx - r * 1.4f, ground - r * 0.5f), Size(r * 2.8f, r * 1.0f)
        )
        drawOval(red.copy(alpha = 0.6f), Offset(p.cx - r * 1.05f, ground - r * 0.34f), Size(r * 2.1f, r * 0.68f), style = Stroke(2f))

        // Orbiting padlock cubes: the ones behind him first.
        val locks = 2 + p.phase
        val orbit = t * 0.6f
        fun lockCube(i: Int, front: Boolean) {
            val a = orbit + i * (6.283f / locks)
            if ((sin(a) >= 0f) != front) return
            val lx = p.cx + cos(a) * r * 1.45f
            val ly = ground - r * 0.35f + sin(a) * r * 0.55f + sin(t * 2f + i) * r * 0.06f
            val s = r * 0.5f * (0.85f + 0.15f * (sin(a) + 1f) / 2f)
            val glowA = if (hot) 0.95f else 0.55f + 0.3f * pulse
            glow(Offset(lx, ly - s * 0.6f), s * 0.6f, red, 0.5f * glowA)
            block(lx, ly, s, s, s, red.copy(alpha = 0.42f), Color(0xFF3A0A10).copy(alpha = 0.85f), lighter(red, 0.4f), glowA)
            padlock(Offset(lx, ly - s * 0.45f), s * 0.6f, lighter(red, 0.55f).copy(alpha = glowA))
        }
        for (i in 0 until locks) lockCube(i, front = false)

        // Legs.
        for (s in listOf(-1f, 1f)) block(p.cx + s * r * 0.3f, ground, r * 0.3f, r * 0.3f, r * 0.32f, top, face, edge, 0.7f)
        // Arms hanging beside the torso, fists at the bottom.
        for (s in listOf(-1f, 1f)) {
            val sway = sin(t * 1.5f + s) * r * 0.03f
            block(p.cx + s * r * 0.82f, ground - r * 0.12f + sway, r * 0.3f, r * 0.3f, r * 0.62f, top, face, edge, 0.7f)
        }
        // Torso.
        val torsoBase = ground - r * 0.28f
        block(p.cx, torsoBase, r * 1.15f, r * 0.8f, r * 0.72f, top, face, edge)
        // Neon seams on the chest.
        drawLine(red.copy(alpha = 0.5f + 0.4f * pulse), Offset(p.cx - r * 0.5f, torsoBase - r * 0.4f), Offset(p.cx + r * 0.5f, torsoBase - r * 0.4f), 2f)
        drawLine(red.copy(alpha = 0.5f + 0.4f * pulse), Offset(p.cx, torsoBase - r * 0.4f), Offset(p.cx, torsoBase - r * 0.05f), 2f)
        // Shoulder blocks.
        for (s in listOf(-1f, 1f)) block(p.cx + s * r * 0.72f, torsoBase - r * 0.38f, r * 0.42f, r * 0.42f, r * 0.42f, top, face, edge)

        // Head: a big cube with a sloped brow.
        val headBase = torsoBase - r * 0.66f
        val hw = r * 1.05f
        val hh = r * 0.8f
        block(p.cx, headBase, hw, hw, hh, top, face, edge)
        val fy = headBase - hh
        // Angry eyes: slanted red slits under the brow.
        val eyeA = if (hot) 1f else 0.75f + 0.25f * pulse
        val eyeCol = if (hot) Color(0xFFFFE0D0) else red
        for (s in listOf(-1f, 1f)) {
            val ex = p.cx + s * hw * 0.22f + cos(p.aim) * r * 0.03f
            val ey = fy + hh * 0.36f
            drawCircle(red.copy(alpha = 0.18f * eyeA * (if (hot) 2f else 1f)), r * 0.1f, Offset(ex, ey))
            val eye = Path().apply {
                moveTo(ex - s * hw * 0.15f, ey - hh * 0.08f)
                lineTo(ex + s * hw * 0.14f, ey + hh * 0.06f)
                lineTo(ex - s * hw * 0.08f, ey + hh * 0.12f)
                close()
            }
            drawPath(eye, eyeCol.copy(alpha = eyeA))
        }
        // Jagged grin.
        val mouth = Path().apply {
            val my = fy + hh * 0.72f
            moveTo(p.cx - hw * 0.28f, my)
            for (k in 1..6) lineTo(p.cx - hw * 0.28f + k * hw * 0.56f / 6f, my + (if (k % 2 == 1) hh * 0.08f else 0f))
        }
        drawPath(mouth, red.copy(alpha = 0.85f * eyeA), style = Stroke(2.2f))
        // Phase 3: red light cracking through the armour.
        if (p.phase >= 2) {
            val c = red.copy(alpha = 0.85f)
            drawLine(c, Offset(p.cx - hw * 0.4f, fy + hh * 0.1f), Offset(p.cx - hw * 0.2f, fy + hh * 0.25f), 1.8f)
            drawLine(c, Offset(p.cx + r * 0.35f, torsoBase - r * 0.6f), Offset(p.cx + r * 0.15f, torsoBase - r * 0.3f), 1.8f)
            drawLine(c, Offset(p.cx - r * 0.45f, torsoBase - r * 0.2f), Offset(p.cx - r * 0.25f, torsoBase - r * 0.5f), 1.8f)
        }

        // Cursed crown (owner, 2026-10-10: "not the orientation… it looks like a cursed crown…
        // try a different design that looks like an evil crown"): level on his head, blackened
        // iron-gold, bent thorn spikes with red-hot cracks, a slit-pupil eye gem, smoky crimson
        // fire and glowing drips.
        val crownBase = fy - hw * 0.55f * 0.5f + r * 0.04f
        val cw = hw * 1.15f
        val cx = p.cx
        val grow = (1f + 0.12f * p.phase) * (if (hot) 1.1f else 1f)
        val ember = if (hot) Color(0xFFFFB080) else Color(0xFFFF2D2D)
        val metalTop = if (p.hitFlash) Color.White else Color(0xFF6B5420)
        val metalLow = if (p.hitFlash) Color(0xFFCCCCCC) else Color(0xFF1C140A)
        // Smoky crimson fire behind the thorns.
        val flameH = r * (0.3f + 0.1f * p.phase) * (if (hot) 1.5f else 1f)
        for (k in 0 until 6) {
            val fx = cx - cw / 2f + cw * (k + 1f) / 7f
            val h = flameH * (0.7f + 0.3f * sin(t * 8f + k * 1.9f))
            val by = crownBase - r * 0.3f
            val flame = Path().apply {
                moveTo(fx - r * 0.1f, by)
                quadraticTo(fx - r * 0.1f, by - h * 0.55f, fx + sin(t * 6f + k) * r * 0.06f, by - h)
                quadraticTo(fx + r * 0.1f, by - h * 0.55f, fx + r * 0.1f, by)
                close()
            }
            drawPath(flame, Color(0xFF8A0F14).copy(alpha = 0.75f))
            drawPath(flame, Color(0xFFFF3B2B).copy(alpha = 0.35f), style = Stroke(1.4f))
            // Smoke curling off the top.
            val k2 = (t * 0.7f + k * 0.37f) % 1f
            drawCircle(Color(0xFF2A2026).copy(alpha = 0.5f * (1f - k2)), r * (0.05f + 0.06f * k2), Offset(fx + sin(t + k) * r * 0.08f, by - h - k2 * r * 0.4f))
        }
        if (hot) glow(Offset(cx, crownBase - r * 0.4f), cw * 0.4f, ember, 0.9f)
        // Thorns: crooked, bent spikes of uneven height; every edge facing out glows like a crack.
        val heights = floatArrayOf(0.55f, 0.82f, 0.48f, 1.1f, 0.6f, 0.9f, 0.5f)
        val bend = floatArrayOf(-0.16f, 0.12f, -0.1f, 0.06f, 0.14f, -0.12f, 0.18f)
        val lean = floatArrayOf(-0.32f, -0.2f, -0.1f, 0f, 0.1f, 0.2f, 0.32f)
        for (k in heights.indices) {
            val bx = cx - cw / 2f + cw * (k + 0.5f) / heights.size
            val by = crownBase - r * 0.12f
            val h = r * heights[k] * grow
            val w = r * 0.21f
            val tipX = bx + lean[k] * h
            val tipY = by - h
            // The thorn kinks halfway up, then hooks at the tip.
            val midX = bx + lean[k] * h * 0.5f + bend[k] * r
            val midY = by - h * 0.5f
            val thorn = Path().apply {
                moveTo(bx - w / 2f, by)
                lineTo(midX - w * 0.22f, midY)
                lineTo(tipX, tipY)
                lineTo(tipX + bend[k] * r * 0.5f, tipY + h * 0.12f)
                lineTo(midX + w * 0.2f, midY + h * 0.04f)
                lineTo(bx + w / 2f, by)
                close()
            }
            drawPath(thorn, Brush.verticalGradient(listOf(metalTop, metalLow), startY = tipY, endY = by))
            drawPath(thorn, Color(0xFF0A0604), style = Stroke(1.6f))
            // Red-hot crack running up the thorn.
            drawLine(ember.copy(alpha = 0.55f + 0.35f * pulse), Offset(bx, by - h * 0.05f), Offset(midX, midY), 1.6f)
            drawLine(ember.copy(alpha = 0.4f + 0.3f * pulse), Offset(midX, midY), Offset(tipX + bend[k] * r * 0.15f, tipY + h * 0.18f), 1.2f)
        }
        // Band: blackened metal with rivets and dripping embers.
        val bandTop = crownBase - r * 0.18f
        drawRect(Brush.verticalGradient(listOf(metalTop, metalLow), startY = bandTop, endY = crownBase + r * 0.04f), Offset(cx - cw / 2f - r * 0.04f, bandTop), Size(cw + r * 0.08f, r * 0.22f))
        drawRect(Color(0xFF0A0604), Offset(cx - cw / 2f - r * 0.04f, bandTop), Size(cw + r * 0.08f, r * 0.22f), style = Stroke(1.6f))
        drawLine(ember.copy(alpha = 0.5f), Offset(cx - cw / 2f, bandTop + r * 0.02f), Offset(cx + cw / 2f, bandTop + r * 0.02f), 1.2f)
        for (k in listOf(-3, -2, 2, 3)) drawCircle(Color(0xFF3A2C12), r * 0.025f, Offset(cx + k * cw * 0.14f, bandTop + r * 0.11f))
        for (k in 0 until 5) {
            val dx = cx - cw * 0.4f + k * cw * 0.2f + sin(k * 3.1f) * r * 0.03f
            val drip = ((t * 0.5f + k * 0.29f) % 1f)
            val len = r * (0.06f + 0.12f * drip)
            drawLine(ember.copy(alpha = 0.7f * (1f - drip * 0.6f)), Offset(dx, crownBase + r * 0.04f), Offset(dx, crownBase + r * 0.04f + len), 2f)
            drawCircle(ember.copy(alpha = 0.8f * (1f - drip)), r * 0.018f, Offset(dx, crownBase + r * 0.04f + len))
        }
        // Slit-pupil eye gem in the middle; side gems like dull blood drops.
        val gem = Offset(cx, bandTop + r * 0.1f)
        glow(gem, r * 0.12f, ember, 0.6f + 0.6f * pulse + (if (hot) 1f else 0f))
        drawOval(Color(0xFF2A0306), Offset(gem.x - r * 0.11f, gem.y - r * 0.075f), Size(r * 0.22f, r * 0.15f))
        drawOval(ember, Offset(gem.x - r * 0.09f, gem.y - r * 0.06f), Size(r * 0.18f, r * 0.12f))
        drawOval(Color(0xFF120002), Offset(gem.x - r * 0.016f, gem.y - r * 0.055f), Size(r * 0.032f, r * 0.11f))
        for (s in listOf(-1f, 1f)) drawCircle(Color(0xFF7A0A12), r * 0.035f, Offset(cx + s * cw * 0.3f, bandTop + r * 0.1f))

        for (i in 0 until locks) lockCube(i, front = true)

        // Ransom shield: a red hex-lattice dome while he's ENCRYPTED.
        if (p.shield > 0f) {
            val dc = Offset(p.cx, p.cy - r * 0.15f)
            val dr = r * 1.55f
            drawCircle(red.copy(alpha = 0.08f * p.shield), dr, dc)
            drawCircle(red.copy(alpha = (0.5f + 0.2f * pulse) * p.shield), dr, dc, style = Stroke(2.5f))
            for (k in 0 until 14) {
                val a = k * 0.449f + t * 0.3f
                val hx = dc.x + cos(a) * dr * 0.78f
                val hy = dc.y + sin(a) * dr * 0.78f
                drawPath(polyPath(hx, hy, ngon(6, r * 0.16f, 0.52f)), red.copy(alpha = 0.25f * p.shield), style = Stroke(1.4f))
            }
            padlock(Offset(dc.x, dc.y - dr - r * 0.05f), r * 0.32f, Color(0xFFFFC233).copy(alpha = 0.85f * p.shield))
        }
    }

    /** Padlock glyph: shackle arc over a body with a keyhole. */
    private fun DrawScope.padlock(c: Offset, s: Float, col: Color) {
        drawArc(col, 180f, 180f, false, Offset(c.x - s * 0.28f, c.y - s * 0.62f), Size(s * 0.56f, s * 0.56f), style = Stroke(s * 0.1f))
        drawRect(col, Offset(c.x - s * 0.38f, c.y - s * 0.34f), Size(s * 0.76f, s * 0.6f))
        drawCircle(DARK, s * 0.08f, Offset(c.x, c.y - s * 0.1f))
        drawRect(DARK, Offset(c.x - s * 0.03f, c.y - s * 0.08f), Size(s * 0.06f, s * 0.16f))
    }
}
