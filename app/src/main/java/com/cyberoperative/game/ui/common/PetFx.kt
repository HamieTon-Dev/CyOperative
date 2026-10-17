package com.cyberoperative.game.ui.common

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import com.cyberoperative.game.data.PetAnim
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The animated ENCRYPTED pets' moving parts (owner, 0.41.0): orbiting orbs, spinning rings,
 * gears and propellers, a visor scan, floating shards, a helix of motes and a pulsing core.
 * Drawn around the pet's sprite in its [size] box: the [front] = false pass goes behind it,
 * then true in front. Mirrors `render_anim` in tools/pets/gen_new_pets.py.
 */
object PetFx {
    private val rainbow = longArrayOf(0xFFFF3DF2, 0xFFFF2B3A, 0xFFFF8A1F, 0xFFFFF04D, 0xFF3CE65A, 0xFF00F0FF, 0xFF326EFF, 0xFF9650F0)
    private val path = Path()

    private fun color(p: PetAnim.Part, t: Float, i: Int): Color {
        p.color?.let { return Color(it) }
        val f = ((t * 0.6f + i * 0.13f) % 1f) * rainbow.size
        val a = rainbow[f.toInt() % rainbow.size]; val b = rainbow[(f.toInt() + 1) % rainbow.size]
        return lerp(Color(a), Color(b), f - f.toInt())
    }

    fun DrawScope.draw(anim: PetAnim, left: Float, top: Float, size: Float, t: Float, front: Boolean, alpha: Float = 1f) {
        fun x(u: Float) = left + u * size
        fun y(v: Float) = top + v * size
        for (p in anim.parts) {
            val n = p.count.coerceIn(1, 8)
            when (p.kind) {
                PetAnim.Kind.ORBS, PetAnim.Kind.HELIX, PetAnim.Kind.SHARDS -> for (i in 0 until n) {
                    val a = t * p.speed * 2.4f + i * 2f * PI.toFloat() / n
                    val inFront = sin(a) >= 0f || p.kind == PetAnim.Kind.SHARDS
                    if (inFront != front) continue
                    val (u, v, r) = when (p.kind) {
                        PetAnim.Kind.ORBS -> Triple(0.5f + cos(a) * 0.44f, 0.56f + sin(a) * 0.13f, 0.045f)
                        PetAnim.Kind.HELIX -> Triple(0.5f + cos(a) * 0.40f, 0.2f + ((i.toFloat() / n + t * 0.12f * abs(p.speed)) % 1f) * 0.7f, 0.03f)
                        else -> Triple(0.5f + cos(a * 0.5f) * 0.46f, 0.35f + sin(t * 2f + i) * 0.06f, 0.04f)
                    }
                    val c = color(p, t, i)
                    val cx = x(u); val cy = y(v); val rr = r * size
                    drawCircle(c.copy(alpha = 0.25f * alpha), rr * 2f, Offset(cx, cy))
                    if (p.kind == PetAnim.Kind.SHARDS) {
                        path.reset()
                        path.moveTo(cx, cy - rr * 1.6f); path.lineTo(cx + rr, cy); path.lineTo(cx, cy + rr * 1.6f); path.lineTo(cx - rr, cy); path.close()
                        drawPath(path, c.copy(alpha = alpha))
                    } else {
                        drawCircle(c.copy(alpha = alpha), rr, Offset(cx, cy))
                        drawCircle(Color.White.copy(alpha = 0.85f * alpha), rr * 0.3f, Offset(cx - rr * 0.25f, cy - rr * 0.4f))
                    }
                }
                PetAnim.Kind.RING -> for (i in 0 until 14) {
                    val a = t * p.speed * 1.6f + i * 2f * PI.toFloat() / 14f
                    if ((sin(a) >= 0f) != front) continue
                    drawCircle(color(p, t, i).copy(alpha = 0.9f * alpha), 0.016f * size, Offset(x(0.5f + cos(a) * 0.4f), y(0.58f + sin(a) * 0.11f)))
                }
                PetAnim.Kind.GEARS -> if (front) for (g in 0..1) {
                    val cx = x(if (g == 0) 0.1f else 0.9f); val cy = y(0.66f)
                    val r = 0.085f * size
                    val rot = t * p.speed * 2f * (if (g == 0) 1f else -1f)
                    val c = color(p, t, g)
                    path.reset()
                    for (j in 0 until 16) {
                        val a = rot + j * PI.toFloat() / 8f
                        val rr = if (j % 2 == 0) r else r * 0.72f
                        if (j == 0) path.moveTo(cx + cos(a) * rr, cy + sin(a) * rr) else path.lineTo(cx + cos(a) * rr, cy + sin(a) * rr)
                    }
                    path.close()
                    drawPath(path, lerp(c, Color(0xFF282832), 0.35f).copy(alpha = alpha))
                    drawCircle(c.copy(alpha = alpha), r * 0.35f, Offset(cx, cy))
                }
                PetAnim.Kind.PROP -> if (front) {
                    val cx = x(0.5f); val cy = y(0.06f)
                    val w = abs(cos(t * p.speed * 9f)) * 0.26f * size + 0.03f * size
                    drawOval(color(p, t, 0).copy(alpha = 0.8f * alpha), Offset(cx - w, cy - 0.02f * size), Size(w * 2f, 0.04f * size))
                    drawCircle(Color(0xFFE6E6F0).copy(alpha = alpha), 0.02f * size, Offset(cx, cy))
                }
                PetAnim.Kind.PULSE -> if (!front) {
                    val a = (60f + 50f * sin(t * 3f * abs(p.speed))) / 255f
                    val c = color(p, t, 0)
                    drawCircle(c.copy(alpha = a / 3f * alpha), 0.48f * size, Offset(x(0.5f), y(0.55f)))
                    drawCircle(c.copy(alpha = a / 2f * alpha), 0.38f * size, Offset(x(0.5f), y(0.55f)))
                }
                PetAnim.Kind.SCAN -> if (front) {
                    val v = 0.42f + (sin(t * 2.5f * abs(p.speed)) * 0.5f + 0.5f) * 0.2f
                    drawLine(color(p, t, 0).copy(alpha = 0.86f * alpha), Offset(x(0.28f), y(v)), Offset(x(0.72f), y(v)), 0.012f * size)
                }
                PetAnim.Kind.FLOAT -> {}
            }
        }
    }
}
