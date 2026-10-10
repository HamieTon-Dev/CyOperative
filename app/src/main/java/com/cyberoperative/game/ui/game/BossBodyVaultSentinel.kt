package com.cyberoperative.game.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/**
 * Vault Sentinel (Boss Pack Alpha 01): a stepped fortress of navy server
 * blocks with red neon seams around a glowing hex core, a laser emitter on
 * the crown. Four cover modules ring the base; it deploys them as the fight
 * goes on (two left in phase 2, none in phase 3, core cracked open and blazing).
 */
internal object VaultSentinelBody : BossBody {
    private val NAVY_TOP = Color(0xFF26324F)
    private val NAVY_FACE = Color(0xFF111A2E)
    private val NAVY_DARK = Color(0xFF0A1020)

    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius
        val t = p.time
        val red = p.color
        val ground = p.cy + r * 0.55f
        val pulse = 0.5f + 0.5f * sin(t * (if (p.phase >= 2) 7f else 3.5f))
        val hot = p.winding
        val top = if (p.hitFlash) lighter(NAVY_TOP, 0.75f) else NAVY_TOP
        val face = if (p.hitFlash) lighter(NAVY_FACE, 0.7f) else NAVY_FACE

        // Plinth: wide octagon slab with a red rim and lit vents.
        val plinth = ngon(8, r * 1.12f, Math.PI.toFloat() / 8f, 0.5f)
        slab(p.cx, ground - r * 0.06f, plinth, r * 0.16f, NAVY_DARK, outline = red.copy(alpha = 0.85f), side = Color(0xFF060A14))
        for (i in 0 until 8) {
            val a = Math.PI.toFloat() / 8f + i * Math.PI.toFloat() / 4f
            val vx = p.cx + cos(a) * r * 0.9f
            val vy = ground - r * 0.06f + sin(a) * r * 0.45f
            drawCircle(red.copy(alpha = 0.35f + 0.45f * pulse), r * 0.035f, Offset(vx, vy))
        }

        // Cover modules: back pair behind the tower, front pair in front of it.
        val modules = when (p.phase) { 0 -> 4; 1 -> 2; else -> 0 }
        val mw = r * 0.42f
        fun module(dx: Float, dy: Float) {
            val gx = p.cx + dx * r
            val gy = ground + dy * r
            block(gx, gy, mw, mw, r * 0.4f, top, face, red)
            // Status light on each module, flashing while it winds up.
            val lit = if (hot) ((t * 14f).toInt() % 2 == 0) else true
            drawRect(
                (if (lit) red else NAVY_DARK).copy(alpha = 0.9f),
                Offset(gx - mw * 0.3f, gy - r * 0.26f), Size(mw * 0.6f, r * 0.06f)
            )
        }
        if (modules >= 4) { module(-0.72f, -0.32f); module(0.72f, -0.32f) }

        // Main tower.
        val tw = r * 0.95f
        val th = r * 1.0f
        block(p.cx, ground, tw, tw, th, top, face, red)
        // Seams down the front face.
        for (k in listOf(-0.32f, 0.32f)) {
            drawLine(red.copy(alpha = 0.55f + 0.35f * pulse), Offset(p.cx + k * tw, ground - th * 0.92f), Offset(p.cx + k * tw, ground - th * 0.08f), 2f)
        }
        drawLine(red.copy(alpha = 0.5f), Offset(p.cx - tw * 0.5f, ground - th * 0.3f), Offset(p.cx + tw * 0.5f, ground - th * 0.3f), 1.5f)

        // Hex core in the upper front face; tracks the operative a little.
        val coreR = r * (if (p.phase >= 2) 0.3f else 0.24f)
        val coreC = Offset(p.cx, ground - th * 0.62f)
        glow(coreC, coreR * 1.5f, red, 1.4f + pulse + (if (hot) 1.5f else 0f))
        drawPath(polyPath(coreC.x, coreC.y, ngon(6, coreR * 1.18f, Math.PI.toFloat() / 6f)), NAVY_DARK)
        drawPath(polyPath(coreC.x, coreC.y, ngon(6, coreR, Math.PI.toFloat() / 6f)), red)
        drawPath(polyPath(coreC.x, coreC.y, ngon(6, coreR * 0.62f, Math.PI.toFloat() / 6f)), lighter(red, 0.5f + 0.3f * pulse))
        val look = Offset(coreC.x + cos(p.aim) * coreR * 0.25f, coreC.y + sin(p.aim) * coreR * 0.2f)
        drawCircle(if (hot) Color.White else lighter(red, 0.85f), coreR * 0.3f, look)
        if (p.phase >= 2) {
            // Cracked open: jagged fractures leaking light.
            val crack = red.copy(alpha = 0.9f)
            drawLine(crack, Offset(coreC.x - coreR * 1.2f, coreC.y - coreR * 0.9f), Offset(coreC.x - coreR * 2.0f, coreC.y - coreR * 1.6f), 2f)
            drawLine(crack, Offset(coreC.x + coreR * 1.1f, coreC.y + coreR * 0.6f), Offset(coreC.x + coreR * 1.9f, coreC.y + coreR * 1.5f), 2f)
            drawLine(crack, Offset(coreC.x + coreR * 1.2f, coreC.y - coreR * 0.8f), Offset(coreC.x + coreR * 1.6f, coreC.y - coreR * 1.7f), 2f)
        }

        // Crown with the laser emitter.
        val crownGround = ground - th - tw * 0.55f * 0.5f + r * 0.06f
        block(p.cx, crownGround, tw * 0.55f, tw * 0.55f, r * 0.3f, top, face, red)
        val lens = Offset(p.cx, crownGround - r * 0.3f - tw * 0.55f * 0.55f * 0.5f)
        drawCircle(NAVY_DARK, r * 0.14f, lens)
        drawCircle(red.copy(alpha = 0.7f + 0.3f * pulse), r * 0.1f, lens)
        drawCircle(Color.White.copy(alpha = 0.6f + 0.4f * pulse), r * 0.04f, lens)
        if (hot) {
            // Charging: a beam of light climbs out of the emitter.
            drawLine(red.copy(alpha = 0.35f), lens, Offset(lens.x, lens.y - r * 1.6f), r * 0.22f)
            drawLine(Color.White.copy(alpha = 0.9f), lens, Offset(lens.x, lens.y - r * 1.6f), r * 0.06f)
        }

        if (modules >= 2) { module(-0.78f, 0.12f); module(0.78f, 0.12f) }

        // Phase 3: sparks spitting off the broken shell.
        if (p.phase >= 2) {
            for (i in 0 until 6) {
                val k = ((t * 1.7f + i * 0.37f) % 1f)
                val a = i * 1.05f + t * 0.5f
                val sx = coreC.x + cos(a) * r * (0.3f + 0.9f * k)
                val sy = coreC.y + sin(a) * r * 0.5f * (0.3f + 0.9f * k) - k * r * 0.4f
                drawCircle(lighter(red, 0.6f).copy(alpha = 1f - k), r * 0.035f, Offset(sx, sy))
            }
        }
        // Rim ring on the floor showing the hit area.
        drawOval(red.copy(alpha = 0.25f), Offset(p.cx - r, ground - r * 0.32f), Size(r * 2f, r * 0.64f), style = Stroke(1.5f))
    }
}
