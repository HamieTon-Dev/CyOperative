package com.cyberoperative.game.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.cyberoperative.game.ui.common.SafeBrush
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * INFERNO boss bodies, visual redesign (owner, 0.36.0): the owner's Inferno Boss Pack sheets ARE
 * the bodies. Each boss has three painted states (resources/bosses/inferno_<pack>.webp: 3 columns
 * = phase 1/2/3, 5 rows = the pack's bosses in InfernoBosses order, 320 px cells, art
 * bottom-aligned on a transparent ground). On top of the paintings a small rig per boss adds the
 * motion: hover / stomp, split halves that flap (wings), sway (legs, scythes, chains) or spread
 * (pylons), a pulsing reactor core, orbiting embers and rings, attack wind-ups that cross-fade to
 * the next painted state and blaze the core, and a white hit flash.
 * The audit and component notes: docs/INFERNO_BOSS_REDESIGN.md.
 */

private const val CELL = 320
private const val PI = Math.PI.toFloat()

/** The three pack atlases, decoded once. */
private object InfernoArt {
    private val atlases = HashMap<String, ImageBitmap?>()

    @Synchronized
    fun atlas(pack: String): ImageBitmap? = atlases.getOrPut(pack) {
        runCatching { com.cyberoperative.game.platform.loadImageResource("bosses/inferno_$pack.webp") }.getOrNull()
    }

    fun preload() {
        Thread({ for (p in listOf("alpha", "beta", "gamma")) atlas(p) }, "inferno-art").apply { isDaemon = true }.start()
    }
}

/** How a boss moves on top of its paintings. */
internal enum class Motion { HOVER, HEAVY, STOMP, DRIFT }

/** What its split halves do (left / right of the centre strip), or nothing. */
internal enum class Split { NONE, FLAP, SWAY, SPREAD }

/**
 * One INFERNO boss's rig. [row] is its row in the [pack] atlas. [span] is the painting's width in
 * hit radii; [coreU]/[coreV] (0..1 of the cell) is the reactor core, which sits on the hitbox
 * centre. [split] animates the halves outside the centre strip ([strip] of the width) by [amount]
 * (degrees for FLAP / SWAY, radii for SPREAD). [jaws]: the wind-up jumps straight to the third
 * painting (open maw, breath). [rings]: orbiting ember rings around the core. [hot] is the fire.
 */
internal class InfernoRig(
    val pack: String,
    val row: Int,
    val span: Float,
    val coreU: Float,
    val coreV: Float,
    val motion: Motion,
    val split: Split = Split.NONE,
    val amount: Float = 0f,
    val strip: Float = 0.22f,
    val jaws: Boolean = false,
    val rings: Int = 0,
    val hot: Long = 0xFFFF6A1A,
    val coreSize: Float = 0.08f,
    /** Side-on beast: mirrored to face the operative it's fighting. */
    val faces: Boolean = false
)

internal class InfernoSpriteBody(private val rig: InfernoRig) : BossBody {
    override val previewSpan: Float get() = rig.span * 1.08f
    override val previewDrop: Float get() = 0f

    override fun DrawScope.draw(p: BossPose) {
        val img = InfernoArt.atlas(rig.pack)
        if (img == null) { InfernoArt.preload(); fallback(p); return }
        val r = p.radius
        val t = p.time
        val s = r * rig.span
        val hot = Color(rig.hot)
        // Motion: hover bob, heavy settle, stomp, drift sway.
        var dx = 0f; var dy = 0f; var squash = 1f
        when (rig.motion) {
            Motion.HOVER -> dy = sin(t * 1.6f) * r * 0.07f
            Motion.DRIFT -> { dy = sin(t * 1.2f) * r * 0.05f; dx = sin(t * 0.7f) * r * 0.04f }
            Motion.HEAVY -> squash = 1f + sin(t * 1.1f) * 0.008f
            Motion.STOMP -> { val st = abs(sin(t * 2.2f)); dy = -st * r * 0.04f; squash = 1f - (1f - st) * 0.012f }
        }
        // Wind-up: heavy bodies compress before the blow.
        if (p.winding && (rig.motion == Motion.HEAVY || rig.motion == Motion.STOMP)) squash *= 0.965f
        val left = p.cx - rig.coreU * s + dx
        val top = p.cy - rig.coreV * s + dy
        val bottom = top + s
        val core = Offset(left + rig.coreU * s, top + rig.coreV * s)

        // Which painting: the phase, cross-fading to the next one while winding up.
        val base = p.phase.coerceIn(0, 2)
        val next = if (rig.jaws) 2 else (base + 1).coerceAtMost(2)
        val blend = if (p.winding) (if (rig.jaws) 1f else 0.55f + 0.25f * sin(t * 12f)) else 0f

        val flip = if (rig.faces && cos(p.aim) < 0f) -1f else 1f
        scale(flip, squash, Offset(core.x, bottom)) {
            rings(core, s, t, hot, behind = true)
            // Jaws: the open-maw painting replaces the body outright while winding up.
            if (rig.jaws && p.winding) frame(img, 2, left, top, s, 1f, p, t)
            else {
                frame(img, base, left, top, s, 1f, p, t)
                if (blend > 0f && next != base) frame(img, next, left, top, s, blend, p, t)
            }
            rings(core, s, t, hot, behind = false)
        }
        // Reactor core: breathing heat, white-hot while charging.
        val pulse = 0.5f + 0.5f * sin(t * (3f + p.phase))
        if (rig.coreSize > 0f) drawCore(core, s, pulse, p.winding, hot)
        // Drifting embers.
        embers(core, s, bottom, t, r, hot)
    }

    private fun DrawScope.drawCore(core: Offset, s: Float, pulse: Float, winding: Boolean, hot: Color) {
        val cr = s * rig.coreSize * (1f + 0.15f * pulse + (if (winding) 0.25f else 0f))
        drawCircle(SafeBrush.radialGradient(listOf(Color.White.copy(alpha = if (winding) 0.7f else 0.3f + 0.2f * pulse), hot.copy(alpha = 0.45f), Color.Transparent), core, cr * 2.2f),
            cr * 2.2f, core, blendMode = BlendMode.Plus)
    }

    private fun DrawScope.embers(core: Offset, s: Float, bottom: Float, t: Float, r: Float, hot: Color) {
        for (k in 0 until 8) {
            val ph = (t * 0.35f + k * 0.618f) % 1f
            val x = core.x + sin(k * 2.3f + t * 0.8f) * s * 0.3f
            val y = bottom - s * 0.1f - ph * s * 0.85f
            drawCircle((if (k % 3 == 0) Color(0xFFFFD27A) else hot).copy(alpha = (1f - ph) * 0.7f), r * 0.03f + (k % 3) * 0.6f, Offset(x, y))
        }
    }

    /** One painting, with the rig's moving halves. */
    private fun DrawScope.frame(img: ImageBitmap, col: Int, left: Float, top: Float, s: Float, alpha: Float, p: BossPose, t: Float) {
        if (alpha <= 0.01f) return
        val filter = if (p.hitFlash) ColorFilter.tint(Color.White.copy(alpha = 0.55f), BlendMode.SrcAtop) else null
        val src = IntOffset(col * CELL, rig.row * CELL)
        fun part(u0: Float, u1: Float) {
            val su0 = (u0 * CELL).toInt(); val su1 = (u1 * CELL).toInt()
            drawImage(img, IntOffset(src.x + su0, src.y), IntSize(su1 - su0, CELL),
                IntOffset((left + u0 * s).toInt(), top.toInt()), IntSize(((u1 - u0) * s).toInt().coerceAtLeast(1), s.toInt()),
                alpha = alpha, colorFilter = filter)
        }
        if (rig.split == Split.NONE) { part(0f, 1f); return }
        val half = rig.strip / 2f
        val pivot = Offset(left + rig.coreU * s, top + rig.coreV * s)
        val wave = sin(t * (if (rig.split == Split.FLAP) 2.4f else 1.7f))
        val windup = if (p.winding) 1f else 0f
        for (side in listOf(-1f, 1f)) {
            val u0 = if (side < 0) 0f else 0.5f - half * 0.2f
            val u1 = if (side < 0) 0.5f + half * 0.2f else 1f
            when (rig.split) {
                Split.FLAP -> {
                    // Wings: a slow beat; wide open while winding up.
                    val deg = side * (rig.amount * wave - windup * rig.amount * 1.6f)
                    rotate(deg, pivot) { part(u0, u1) }
                }
                Split.SWAY -> {
                    // Legs / scythes / chains: the sides counter-sway.
                    val deg = side * rig.amount * wave * (if (p.winding) 2f else 1f)
                    rotate(deg, pivot) { part(u0, u1) }
                }
                Split.SPREAD -> {
                    // Pylons step out to the sides, further while winding up.
                    // The ground (bottom fifth: the lava pool) stays put; only the pylons above step out.
                    val off = side * p.radius * rig.amount * (0.35f + 0.25f * wave + windup)
                    val mx = (left + u0 * s); val mx1 = (left + u1 * s)
                    val cut = 0.8f
                    val sw = ((u1 - u0) * CELL).toInt(); val dw = (mx1 - mx).toInt()
                    drawImage(img, IntOffset(src.x + (u0 * CELL).toInt(), src.y), IntSize(sw, (CELL * cut).toInt()),
                        IntOffset((mx + off).toInt(), top.toInt()), IntSize(dw, (s * cut).toInt()), alpha = alpha, colorFilter = filter)
                    drawImage(img, IntOffset(src.x + (u0 * CELL).toInt(), src.y + (CELL * cut).toInt()), IntSize(sw, CELL - (CELL * cut).toInt()),
                        IntOffset(mx.toInt(), (top + s * cut).toInt()), IntSize(dw, s.toInt() - (s * cut).toInt()), alpha = alpha, colorFilter = filter)
                }
                Split.NONE -> {}
            }
        }
        // The body itself stays put over the moving halves.
        part(0.5f - half, 0.5f + half)
    }

    /** Orbiting ember rings round the core (the rotating machinery), back half or front half. */
    private fun DrawScope.rings(core: Offset, s: Float, t: Float, hot: Color, behind: Boolean) {
        for (k in 0 until rig.rings) {
            val rx = s * (0.3f + 0.08f * k); val ry = rx * 0.28f
            val dir = if (k % 2 == 0) 1f else -1f
            for (d in 0 until 6) {
                val a = dir * t * (0.9f - 0.2f * k) + d * PI / 3f
                val front = sin(a) > 0f
                if (front == behind) continue
                drawCircle(hot.copy(alpha = if (front) 0.9f else 0.4f), s * 0.012f, Offset(core.x + cos(a) * rx, core.y + sin(a) * ry))
            }
            if (behind) drawOval(hot.copy(alpha = 0.12f), Offset(core.x - rx, core.y - ry), Size(rx * 2f, ry * 2f), style = Stroke(1.5f))
        }
    }

    /** Before the atlas has decoded: a dark core so the boss is never invisible. */
    private fun DrawScope.fallback(p: BossPose) {
        drawCircle(Color(0xFF1A0A08), p.radius, Offset(p.cx, p.cy))
        drawCircle(Color(rig.hot), p.radius * 0.35f, Offset(p.cx, p.cy))
    }
}

/** The rigs, after each painting (cores measured on the sheets). */
internal val infernoBodies: Map<String, BossBody> = mapOf(
    // Pack Alpha
    "hellcore_tyrant" to InfernoSpriteBody(InfernoRig("alpha", 0, 3.9f, 0.5f, 0.42f, Motion.HOVER, Split.SPREAD, 0.18f, strip = 0.62f, rings = 2, hot = 0xFFFF6A1A)),
    "ember_warden" to InfernoSpriteBody(InfernoRig("alpha", 1, 3.6f, 0.5f, 0.55f, Motion.HEAVY, Split.SPREAD, 0.22f, strip = 0.5f, hot = 0xFFFF9A1A, coreSize = 0.1f)),
    "cinder_widow" to InfernoSpriteBody(InfernoRig("alpha", 2, 3.9f, 0.5f, 0.56f, Motion.STOMP, Split.SWAY, 2.5f, strip = 0.34f, hot = 0xFFFF2A3A, coreSize = 0.05f)),
    "magma_pulse" to InfernoSpriteBody(InfernoRig("alpha", 3, 3.7f, 0.5f, 0.5f, Motion.DRIFT, rings = 2, hot = 0xFFFF8A10, coreSize = 0.09f)),
    "ashen_reaver" to InfernoSpriteBody(InfernoRig("alpha", 4, 3.8f, 0.5f, 0.38f, Motion.HOVER, Split.SWAY, 4f, strip = 0.4f, hot = 0xFFFF4A2A, coreSize = 0.04f)),
    // Pack Beta
    "infernal_router" to InfernoSpriteBody(InfernoRig("beta", 0, 4.2f, 0.5f, 0.48f, Motion.HOVER, rings = 2, hot = 0xFFFF3B1F, coreSize = 0.07f)),
    "pyroclast_oracle" to InfernoSpriteBody(InfernoRig("beta", 1, 3.9f, 0.5f, 0.3f, Motion.HOVER, Split.SWAY, 3f, strip = 0.36f, rings = 1, hot = 0xFFFF4A3A, coreSize = 0.04f)),
    "searfang_colossus" to InfernoSpriteBody(InfernoRig("beta", 2, 4.3f, 0.5f, 0.55f, Motion.STOMP, jaws = true, hot = 0xFFFF6A1A, coreSize = 0.0f, faces = true)),
    "burnwire_bishop" to InfernoSpriteBody(InfernoRig("beta", 3, 4.0f, 0.5f, 0.42f, Motion.HOVER, Split.SWAY, 3f, strip = 0.42f, hot = 0xFFFF3A3A, coreSize = 0.05f)),
    "furnace_maw" to InfernoSpriteBody(InfernoRig("beta", 4, 4.2f, 0.5f, 0.55f, Motion.HEAVY, jaws = true, hot = 0xFFFF7A1A, coreSize = 0.11f)),
    // Pack Gamma
    "redline_sovereign" to InfernoSpriteBody(InfernoRig("gamma", 0, 4.1f, 0.5f, 0.5f, Motion.HEAVY, Split.SPREAD, 0.16f, strip = 0.6f, hot = 0xFFFF1F2E, coreSize = 0.07f)),
    "scorch_halo" to InfernoSpriteBody(InfernoRig("gamma", 1, 3.9f, 0.5f, 0.5f, Motion.DRIFT, rings = 2, hot = 0xFFFFA020, coreSize = 0.08f)),
    "volcanic_null" to InfernoSpriteBody(InfernoRig("gamma", 2, 4.1f, 0.5f, 0.42f, Motion.HOVER, Split.SPREAD, 0.15f, strip = 0.5f, hot = 0xFFE01E50, coreSize = 0.06f)),
    "smolder_hex" to InfernoSpriteBody(InfernoRig("gamma", 3, 3.9f, 0.5f, 0.5f, Motion.DRIFT, rings = 1, hot = 0xFFFF6A2A, coreSize = 0.08f)),
    "combustion_monarch" to InfernoSpriteBody(InfernoRig("gamma", 4, 4.6f, 0.5f, 0.48f, Motion.HOVER, Split.FLAP, 4f, strip = 0.24f, hot = 0xFFFF4A2A, coreSize = 0.06f))
)
