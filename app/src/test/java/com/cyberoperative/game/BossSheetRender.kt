package com.cyberoperative.game

import android.graphics.Bitmap
import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.cyberoperative.game.data.Bosses
import com.cyberoperative.game.ui.game.BossBodies
import com.cyberoperative.game.ui.game.BossPose
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Reference sheet: every boss with its body and dossier facts (docs/screenshots/boss_sheet.png). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class BossSheetRender {
    private val enabled = System.getProperty("cyberop.renderPreviews") == "true"
    private val outDir = File(System.getProperty("cyberop.previewDir") ?: "build/screens")

    @Test fun bossSheet() {
        assumeTrue(enabled)
        val bosses = Bosses.roster
        val cols = 4
        val cardW = 600f
        val cardH = 560f
        val margin = 40f
        val top = margin + 120f
        val rows = (bosses.size + cols - 1) / cols
        val width = (margin * 2 + cols * cardW).toInt()
        val height = (top + rows * cardH + margin).toInt()
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(android.graphics.Canvas(bmp))

        val mono = Typeface.MONOSPACE
        val bold = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        val title = android.graphics.Paint().apply { isAntiAlias = true; color = 0xFF00E5FF.toInt(); textSize = 48f; typeface = bold }
        val sub = android.graphics.Paint(title).apply { textSize = 21f; color = 0xFF8FA0B8.toInt(); typeface = mono }
        val name = android.graphics.Paint(title).apply { textSize = 30f }
        val text = android.graphics.Paint(title).apply { textSize = 18.5f; color = 0xFFC9D4E3.toInt(); typeface = mono }
        val dim = android.graphics.Paint(text).apply { color = 0xFF7F8EA3.toInt() }
        val chip = android.graphics.Paint(text).apply { textSize = 17f; typeface = bold }
        val tierNames = mapOf(1 to "LOW", 2 to "MEDIUM", 3 to "HIGH", 4 to "EXTREME")

        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(width.toFloat(), height.toFloat())) {
            drawRect(Color(0xFF070B12))
            val nc = drawContext.canvas.nativeCanvas
            nc.drawText("CYBER OPERATIVE · BOSS ROSTER", margin, margin + 44f, title)
            nc.drawText("${bosses.size} bosses · ${Bosses.classics.size} classics · ${bosses.size - Bosses.classics.size} expansion (Pack Gamma marked) · every 10th level draws one at random from the pool",
                margin, margin + 84f, sub)
            for ((i, b) in bosses.withIndex()) {
                val x0 = margin + (i % cols) * cardW
                val y0 = top + (i / cols) * cardH
                val col = Color(b.color)
                val gamma = b.id in Bosses.GAMMA_IDS
                val classic = b in Bosses.classics
                // Card.
                drawRoundRect(Color(0xFF0E1522), Offset(x0 + 8f, y0 + 8f), Size(cardW - 16f, cardH - 16f), CornerRadius(16f))
                drawRoundRect(col.copy(alpha = 0.55f), Offset(x0 + 8f, y0 + 8f), Size(cardW - 16f, cardH - 16f), CornerRadius(16f), style = Stroke(2f))
                // Body, framed like the codex preview.
                val boxX = x0 + 16f; val boxY = y0 + 16f; val boxW = cardW - 32f; val boxH = 300f
                drawRoundRect(Color(0xFF070C16), Offset(boxX, boxY), Size(boxW, boxH), CornerRadius(12f))
                clipRect(boxX, boxY, boxX + boxW, boxY + boxH) {
                    val body = BossBodies.forId(b.id)
                    val scale = minOf(boxW, boxH) / (b.radius * (body?.previewSpan ?: 4.2f))
                    val cxs = (boxX + boxW / 2f) / scale
                    val cys = (boxY + boxH / 2f) / scale + b.radius * (body?.previewDrop ?: 0f)
                    withTransform({ scale(scale, scale, Offset.Zero) }) {
                        drawOval(Color.Black.copy(alpha = 0.45f), Offset(cxs - b.radius * 1.1f, cys + b.radius * 0.55f), Size(b.radius * 2.2f, b.radius * 0.7f))
                        val pose = BossPose(cxs, cys - 6f, b.radius, col, 1.3f + i * 0.21f, 0, false, false, Math.PI.toFloat() / 2f, 1f, false)
                        if (body != null) with(body) { draw(pose) }
                    }
                }
                // Facts.
                var ty = boxY + boxH + 40f
                val np = android.graphics.Paint(name).apply { color = b.color.toInt() }
                nc.drawText(b.name, x0 + 24f, ty, np)
                ty += 26f
                nc.drawText(b.title, x0 + 24f, ty, dim)
                ty += 32f
                val pack = when { gamma -> "PACK GAMMA"; classic -> "CLASSIC"; else -> "EXPANSION" }
                val appears = when { gamma -> "L${Bosses.GAMMA_FROM}+"; classic -> "L10+"; else -> "L20+" }
                val chips = listOf(
                    pack to (if (gamma) 0xFFFF2D55 else if (classic) 0xFF00E5FF else 0xFFA259FF),
                    "TIER ${tierNames[b.tier] ?: b.tier}" to 0xFFFFD426,
                    b.role.label to b.role.color,
                    appears to 0xFF00FF9C
                )
                var cx = x0 + 24f
                for ((label, c) in chips) {
                    val w = chip.measureText(label) + 18f
                    drawRoundRect(Color(c).copy(alpha = 0.16f), Offset(cx, ty - 20f), Size(w, 28f), CornerRadius(8f))
                    drawRoundRect(Color(c).copy(alpha = 0.8f), Offset(cx, ty - 20f), Size(w, 28f), CornerRadius(8f), style = Stroke(1.2f))
                    nc.drawText(label, cx + 9f, ty, android.graphics.Paint(chip).apply { color = c.toInt() })
                    cx += w + 8f
                }
                ty += 36f
                nc.drawText("Base HP ${"%,d".format(b.baseHp.toInt())} · contact ${b.contactDamage.toInt()} · ${b.phases.size} phases · bounty €${b.euros}", x0 + 24f, ty, text)
                ty += 28f
                val abil = b.abilityNames(4)
                nc.drawText("ATTACKS:", x0 + 24f, ty, dim)
                var line = ""
                var ly = ty
                for (a in abil) {
                    val next = if (line.isEmpty()) a else "$line · $a"
                    if (text.measureText(next) > cardW - 150f) {
                        nc.drawText(line, x0 + 120f, ly, text); ly += 24f; line = a
                    } else line = next
                }
                if (line.isNotEmpty()) nc.drawText(line, x0 + 120f, ly, text)
            }
        }
        outDir.mkdirs()
        File(outDir, "boss_sheet.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
