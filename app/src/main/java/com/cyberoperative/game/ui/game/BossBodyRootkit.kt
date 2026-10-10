package com.cyberoperative.game.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.cos
import kotlin.math.sin

/**
 * ROOTKIT (classic, level 50): a kernel-level phantom. A tall hooded cloak of
 * tattered dark shards, a hollow hood with a terminal face — a blinking ">_"
 * prompt and two crimson eye slits — and code ribbons trailing from its sleeves.
 * While hidden it's only a faint scanline silhouette flickering in and out.
 * The tatters lengthen and the code ribbons multiply each phase.
 */
internal object RootkitBody : BossBody {
    private val CLOAK_LIT = Color(0xFF2A1218)
    private val CLOAK = Color(0xFF14080C)
    private val CLOAK_DARK = Color(0xFF060204)

    override val previewSpan: Float get() = 5.2f
    override val previewDrop: Float get() = 0.3f

    override fun DrawScope.drawHidden(p: BossPose): Boolean {
        // Hidden between attacks: only a faint flickering scanline silhouette.
        val r = p.radius * 1.35f
        val f = if (sin(p.time * 23f) > 0.6f) 0.35f else 0.1f
        for (k in 0 until 10) {
            val y = p.cy - r * 1.1f + k * r * 0.22f
            val w = r * (0.25f + 0.08f * k)
            drawLine(p.color.copy(alpha = f * (0.5f + 0.5f * sin(p.time * 9f + k))), Offset(p.cx - w, y), Offset(p.cx + w, y), 1.5f)
        }
        return true
    }

    override fun DrawScope.draw(p: BossPose) {
        val r = p.radius * 1.35f
        val t = p.time
        val red = p.color
        val hot = p.winding
        val cy = p.cy + sin(t * 1.3f) * r * 0.06f
        val lit = if (p.hitFlash) Color.White else CLOAK_LIT

        // Floor: a faint glitch square under it.
        drawOval(Brush.radialGradient(listOf(red.copy(alpha = 0.3f), red.copy(alpha = 0f)), center = Offset(p.cx, p.cy + r * 0.95f), radius = r), Offset(p.cx - r, p.cy + r * 0.65f), Size(r * 2, r * 0.6f))

        // Code ribbons trailing from the sleeves.
        val ribbons = 2 + p.phase
        for (s in listOf(-1f, 1f)) for (k in 0 until ribbons) {
            val path = Path()
            for (i in 0..10) {
                val u = i / 10f
                val x = p.cx + s * (r * 0.75f + u * r * (0.6f + 0.15f * k))
                val y = cy + r * 0.05f + k * r * 0.12f + sin(t * 3f + u * 5f + k) * r * 0.12f * u
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, red.copy(alpha = 0.35f), style = Stroke(2f))
            for (i in 0 until 4) {
                val u = ((t * 0.7f + i * 0.25f + k * 0.1f) % 1f)
                val x = p.cx + s * (r * 0.75f + u * r * (0.6f + 0.15f * k))
                val y = cy + r * 0.05f + k * r * 0.12f + sin(t * 3f + u * 5f + k) * r * 0.12f * u
                drawRect(red.copy(alpha = 0.8f * (1f - u)), Offset(x - 2f, y - 2f), Size(4f, 4f))
            }
        }

        // Cloak: tattered shards, longer each phase.
        val tatter = r * (0.25f + 0.12f * p.phase)
        val cloak = Path().apply {
            moveTo(p.cx - r * 0.45f, cy - r * 0.55f)
            lineTo(p.cx - r * 0.85f, cy + r * 0.2f)
            for (k in 0..6) {
                val x = p.cx - r * 0.85f + k * r * 0.283f
                val drop = tatter * (0.6f + 0.4f * sin(t * 4f + k * 1.7f))
                lineTo(x, cy + r * 0.6f + (if (k % 2 == 0) drop else 0f))
            }
            lineTo(p.cx + r * 0.85f, cy + r * 0.2f)
            lineTo(p.cx + r * 0.45f, cy - r * 0.55f)
            close()
        }
        drawPath(cloak, Brush.verticalGradient(listOf(lit, CLOAK, CLOAK_DARK), cy - r * 0.6f, cy + r * 0.9f))
        drawPath(cloak, red.copy(alpha = 0.55f), style = Stroke(1.8f))
        // Shard seams down the cloak.
        for (k in -2..2) drawLine(CLOAK_DARK, Offset(p.cx + k * r * 0.12f, cy - r * 0.3f), Offset(p.cx + k * r * 0.3f, cy + r * 0.55f), 2f)
        // Shoulder plates.
        for (s in listOf(-1f, 1f)) crystal(p.cx + s * r * 0.45f, cy - r * 0.45f, if (s < 0) 3.5f else -0.36f, r * 0.55f, r * 0.3f, lit, CLOAK_DARK, red.copy(alpha = 0.7f))

        // Hood.
        val hood = Path().apply {
            moveTo(p.cx, cy - r * 1.45f)
            cubicTo(p.cx + r * 0.6f, cy - r * 1.25f, p.cx + r * 0.62f, cy - r * 0.55f, p.cx + r * 0.42f, cy - r * 0.35f)
            lineTo(p.cx - r * 0.42f, cy - r * 0.35f)
            cubicTo(p.cx - r * 0.62f, cy - r * 0.55f, p.cx - r * 0.6f, cy - r * 1.25f, p.cx, cy - r * 1.45f)
            close()
        }
        drawPath(hood, Brush.verticalGradient(listOf(lit, CLOAK, CLOAK_DARK), cy - r * 1.45f, cy - r * 0.35f))
        drawPath(hood, red.copy(alpha = 0.6f), style = Stroke(2f))
        // Hollow face opening.
        drawOval(Color.Black, Offset(p.cx - r * 0.34f, cy - r * 1.05f), Size(r * 0.68f, r * 0.62f))
        // Terminal face: eye slits and a blinking prompt.
        val eye = if (hot) 1f else 0.7f + 0.3f * sin(t * 2.7f)
        for (s in listOf(-1f, 1f)) {
            val ex = p.cx + s * r * 0.13f + cos(p.aim) * r * 0.03f
            val ey = cy - r * 0.82f
            drawCircle(red.copy(alpha = 0.25f * eye), r * 0.1f, Offset(ex, ey))
            drawRect((if (hot) Color.White else red).copy(alpha = eye), Offset(ex - r * 0.08f, ey - r * 0.025f), Size(r * 0.16f, r * 0.05f))
        }
        val prompt = if (((t * 2f).toInt() % 2) == 0) ">_" else ">"
        drawContext.canvas.nativeCanvas.drawText(prompt, p.cx, cy - r * 0.55f, android.graphics.Paint().apply {
            color = android.graphics.Color.argb((255 * eye).toInt(), (red.red * 255).toInt(), (red.green * 255).toInt(), (red.blue * 255).toInt())
            textSize = r * 0.2f; isAntiAlias = true; typeface = android.graphics.Typeface.MONOSPACE; textAlign = android.graphics.Paint.Align.CENTER
        })
        // Charging: glitch slices offset across the figure.
        if (hot) for (k in 0 until 4) {
            val y = cy - r * 1.2f + ((t * 3f + k * 0.27f) % 1f) * r * 1.8f
            drawRect(red.copy(alpha = 0.35f), Offset(p.cx - r * 0.9f + sin(t * 40f + k) * r * 0.1f, y), Size(r * 1.8f, r * 0.05f))
        }
    }
}
