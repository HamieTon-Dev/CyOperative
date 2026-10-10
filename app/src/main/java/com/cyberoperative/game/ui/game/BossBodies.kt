package com.cyberoperative.game.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.cyberoperative.game.data.BossDef
import kotlin.math.cos
import kotlin.math.sin

/**
 * Everything a boss body needs to draw one frame (Boss Expansion S14).
 * Positions are arena units; [cx]/[cy] is the body centre already lifted off
 * the floor, [radius] the hit radius.
 */
class BossPose(
    val cx: Float,
    val cy: Float,
    val radius: Float,
    /** Boss colour (cycles hue while *GLITCHED*). */
    val color: Color,
    val time: Float,
    /** 0, 1, 2 — the HP phase. Bodies change as the fight goes on. */
    val phase: Int,
    /** Charging an attack: bodies light up / open. */
    val winding: Boolean,
    /** White for a frame when hit. */
    val hitFlash: Boolean,
    /** Radians toward the operative it is fighting. */
    val aim: Float,
    /** 0..1 HP left. */
    val hp: Float,
    val glitched: Boolean,
    /** 0 = fully visible … 1 = in the shadows, eyes closed (Nullshade's lock windows). */
    val veiled: Float = 0f
) {
    /** Body fill: white on a hit, otherwise the boss colour. */
    val fill: Color get() = if (hitFlash) Color.White else color
}

/** One boss's look. Each new boss gets its own; see [BossBodies]. */
fun interface BossBody {
    fun DrawScope.draw(p: BossPose)

    /** Drawn while the boss is underground / hidden; return false to use the default faint outline. */
    fun DrawScope.drawHidden(p: BossPose): Boolean = false

    /** Drawn above the blackout overlay (what still cuts through the dark, e.g. eyes). */
    fun DrawScope.drawOverDark(p: BossPose) {}
}

/**
 * Registry of per-boss bodies by [BossDef.id]. A boss without an entry falls
 * back to the classic extruded hexagon in [ArenaRenderer]. Owner rule
 * (2026-10-10): every body is shown to the owner for approval before it is
 * final — see docs/BOSS_EXPANSION_LOG.md §6.
 */
object BossBodies {
    private val bodies: Map<String, BossBody> = mapOf(
        "vault_sentinel" to VaultSentinelBody,
        "rootkit_apostle" to RootkitApostleBody,
        "nullshade" to NullshadeSpecterBody
    )

    fun forId(id: String): BossBody? = bodies[id]

    fun has(id: String) = id in bodies
}

// --- Shared drawing helpers for boss bodies ---------------------------------

internal fun darker(c: Color, k: Float) = Color(c.red * k, c.green * k, c.blue * k, c.alpha)

internal fun lighter(c: Color, k: Float) =
    Color(c.red + (1f - c.red) * k, c.green + (1f - c.green) * k, c.blue + (1f - c.blue) * k, c.alpha)

/** Closed polygon through [pts] (x0, y0, x1, y1, …) offset by [cx]/[cy]. */
internal fun polyPath(cx: Float, cy: Float, pts: FloatArray): Path = Path().apply {
    moveTo(cx + pts[0], cy + pts[1])
    var i = 2
    while (i < pts.size) { lineTo(cx + pts[i], cy + pts[i + 1]); i += 2 }
    close()
}

/** Regular polygon points (radius [r], [n] sides, rotated [rot] radians). */
internal fun ngon(n: Int, r: Float, rot: Float = 0f, squashY: Float = 1f): FloatArray = FloatArray(n * 2).also {
    for (i in 0 until n) {
        val a = rot + i * (2f * Math.PI.toFloat() / n)
        it[i * 2] = cos(a) * r
        it[i * 2 + 1] = sin(a) * r * squashY
    }
}

/**
 * 2.5D slab: the shape drawn dark [depth] lower (its side), then the lit top,
 * then a dark outline — the same extrusion the arena uses everywhere.
 */
internal fun DrawScope.slab(cx: Float, cy: Float, pts: FloatArray, depth: Float, top: Color, outline: Color = Color(0xFF12040A), side: Color = darker(top, 0.42f)) {
    drawPath(polyPath(cx, cy + depth, pts), side)
    drawPath(polyPath(cx, cy, pts), top)
    drawPath(polyPath(cx, cy, pts), outline, style = Stroke(2.5f))
}

/** Two-tone crystal spike from ([bx], [by]) toward angle [a], [len] long, [w] wide at the base. */
internal fun DrawScope.crystal(bx: Float, by: Float, a: Float, len: Float, w: Float, lit: Color, dark: Color, edge: Color, squash: Float = 1f) {
    val tx = bx + cos(a) * len
    val ty = by + sin(a) * len * squash
    val px = -sin(a) * w / 2f
    val py = cos(a) * w / 2f * squash
    val left = Path().apply { moveTo(bx + px, by + py); lineTo(tx, ty); lineTo(bx, by); close() }
    val right = Path().apply { moveTo(bx - px, by - py); lineTo(tx, ty); lineTo(bx, by); close() }
    drawPath(left, lit)
    drawPath(right, dark)
    drawLine(edge, Offset(bx + px, by + py), Offset(tx, ty), 1.4f)
    drawLine(edge.copy(alpha = edge.alpha * 0.6f), Offset(bx - px, by - py), Offset(tx, ty), 1.2f)
}

/** Soft neon glow: a few widening translucent rings. */
internal fun DrawScope.glow(c: Offset, r: Float, color: Color, strength: Float = 1f) {
    for (i in 3 downTo 1) drawCircle(color.copy(alpha = 0.07f * strength * i), r * (1f + i * 0.22f), c)
}

/** Previews a boss body on its own (codex, approval renders). */
@Composable
fun BossBodyPreview(def: BossDef, phase: Int, time: Float, modifier: Modifier = Modifier, winding: Boolean = false, veiled: Float = 0f) {
    Canvas(modifier) {
        val scale = size.minDimension / (def.radius * 4.2f)
        val cxs = size.width / 2f / scale
        val cys = size.height / 2f / scale
        drawContext.transform.scale(scale, scale, Offset.Zero)
        val pose = BossPose(cxs, cys - 6f, def.radius, Color(def.color), time, phase, winding, false, Math.PI.toFloat() / 2f, 1f - phase * 0.35f, false, veiled)
        // Floor shadow.
        drawOval(Color.Black.copy(alpha = 0.45f), Offset(cxs - def.radius * 1.1f, cys + def.radius * 0.55f), androidx.compose.ui.geometry.Size(def.radius * 2.2f, def.radius * 0.7f))
        val body = BossBodies.forId(def.id)
        if (body != null) with(body) { draw(pose) }
        else slab(pose.cx, pose.cy, ngon(6, def.radius, time * 0.4f, 0.85f), def.radius * 0.35f, pose.fill)
    }
}

/**
 * 2.5D block standing on the floor at ([cx], [groundY]): front face [w] wide
 * and [h] tall, top face [d] deep (drawn squashed), neon [edge] trim.
 */
internal fun DrawScope.block(
    cx: Float, groundY: Float, w: Float, d: Float, h: Float,
    top: Color, face: Color, edge: Color, edgeAlpha: Float = 0.9f
) {
    val dd = d * 0.55f
    val left = cx - w / 2f
    val frontTop = groundY - h
    // Front face, then the top face set back by its depth.
    drawRect(face, Offset(left, frontTop), androidx.compose.ui.geometry.Size(w, h))
    drawRect(top, Offset(left, frontTop - dd), androidx.compose.ui.geometry.Size(w, dd))
    val e = edge.copy(alpha = edgeAlpha)
    drawLine(e, Offset(left, frontTop), Offset(left + w, frontTop), 2.2f)
    drawLine(e, Offset(left, frontTop - dd), Offset(left + w, frontTop - dd), 1.6f)
    drawLine(e.copy(alpha = edgeAlpha * 0.7f), Offset(left, frontTop - dd), Offset(left, groundY), 1.6f)
    drawLine(e.copy(alpha = edgeAlpha * 0.7f), Offset(left + w, frontTop - dd), Offset(left + w, groundY), 1.6f)
    drawLine(Color(0xFF05070D), Offset(left, groundY), Offset(left + w, groundY), 2f)
}
