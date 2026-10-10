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

        // Crown (owner, 2026-10-10: "bigger, sharper, more like jutting out / triangular and
        // crooked"): a tilted gold band with sharp faceted spikes fanning out at uneven angles,
        // flames licking up between them.
        val crownBase = fy - hw * 0.55f * 0.5f + r * 0.04f
        val cw = hw * 1.2f
        val tilt = -0.16f + sin(t * 1.2f) * 0.02f
        val ox = p.cx + r * 0.06f
        fun rot(dx: Float, dy: Float) = Offset(ox + dx * cos(tilt) - dy * sin(tilt), crownBase + dx * sin(tilt) + dy * cos(tilt))
        val grow = (1f + 0.12f * p.phase) * (if (hot) 1.1f else 1f)
        val heights = floatArrayOf(0.5f, 0.78f, 0.6f, 1.05f, 0.55f, 0.85f, 0.45f)
        val lean = floatArrayOf(-0.62f, -0.4f, -0.2f, 0.04f, 0.22f, 0.44f, 0.7f)
        val goldLit = if (p.hitFlash) Color.White else Color(0xFFFFE27A)
        val goldShade = if (p.hitFlash) Color(0xFFDDDDDD) else Color(0xFFC07A0E)
        // Flames behind the spikes.
        val flameH = r * (0.32f + 0.1f * p.phase) * (if (hot) 1.5f else 1f)
        for (k in 0 until 6) {
            val base = rot(-cw / 2f + cw * (k + 1f) / 7f, -r * 0.3f)
            val h = flameH * (0.75f + 0.25f * sin(t * 9f + k * 1.7f))
            val flame = Path().apply {
                moveTo(base.x - r * 0.1f, base.y)
                quadraticTo(base.x - r * 0.08f, base.y - h * 0.6f, base.x + sin(t * 7f + k) * r * 0.05f, base.y - h)
                quadraticTo(base.x + r * 0.08f, base.y - h * 0.6f, base.x + r * 0.1f, base.y)
                close()
            }
            drawPath(flame, FLAME.copy(alpha = 0.7f))
            drawCircle(Color(0xFFFFE08A).copy(alpha = 0.55f), r * 0.04f, Offset(base.x, base.y - h * 0.3f))
        }
        if (hot) glow(rot(0f, -r * 0.4f), cw * 0.4f, GOLD, 0.9f)
        // Spikes: sharp two-tone gold triangles jutting out from the band's top edge.
        for (k in heights.indices) {
            val bx = -cw / 2f + cw * (k + 0.5f) / heights.size
            val b0 = rot(bx, -r * 0.12f)
            val ang = -1.571f + tilt + lean[k]
            crystal(b0.x, b0.y, ang, r * heights[k] * grow, r * 0.2f, goldLit, goldShade, Color(0xFF7A4A08))
        }
        // Band: a skewed gold strip with gems.
        val band = Path().apply {
            val a0 = rot(-cw / 2f - r * 0.04f, -r * 0.16f); val a1 = rot(cw / 2f + r * 0.04f, -r * 0.16f)
            val a2 = rot(cw / 2f, r * 0.02f); val a3 = rot(-cw / 2f, r * 0.02f)
            moveTo(a0.x, a0.y); lineTo(a1.x, a1.y); lineTo(a2.x, a2.y); lineTo(a3.x, a3.y); close()
        }
        drawPath(band, Brush.verticalGradient(listOf(goldLit, GOLD, GOLD_DARK), startY = crownBase - r * 0.2f, endY = crownBase + r * 0.05f))
        drawPath(band, Color(0xFF7A4A08), style = Stroke(1.6f))
        for (k in listOf(-2, -1, 0, 1, 2)) {
            val gc = rot(k * cw * 0.2f, -r * 0.07f)
            drawCircle(if (k == 0) red else if (k % 2 == 0) Color(0xFFFF8A3A) else Color(0xFFB0102A), r * (if (k == 0) 0.065f else 0.045f), gc)
            drawCircle(Color.White.copy(alpha = 0.6f), r * 0.015f, Offset(gc.x - r * 0.015f, gc.y - r * 0.02f))
        }

        for (i in 0 until locks) lockCube(i, front = true)
    }

    /** Padlock glyph: shackle arc over a body with a keyhole. */
    private fun DrawScope.padlock(c: Offset, s: Float, col: Color) {
        drawArc(col, 180f, 180f, false, Offset(c.x - s * 0.28f, c.y - s * 0.62f), Size(s * 0.56f, s * 0.56f), style = Stroke(s * 0.1f))
        drawRect(col, Offset(c.x - s * 0.38f, c.y - s * 0.34f), Size(s * 0.76f, s * 0.6f))
        drawCircle(DARK, s * 0.08f, Offset(c.x, c.y - s * 0.1f))
        drawRect(DARK, Offset(c.x - s * 0.03f, c.y - s * 0.08f), Size(s * 0.06f, s * 0.16f))
    }
}
