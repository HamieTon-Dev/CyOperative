package com.cyberoperative.game

import android.graphics.Bitmap
import android.graphics.Typeface
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.cyberoperative.game.data.EliteModifier
import com.cyberoperative.game.data.Enemies
import com.cyberoperative.game.engine.AiState
import com.cyberoperative.game.engine.Enemy
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.ui.game.ArenaRenderer
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Reference sheet: every regular threat, then every elite modifier (docs/screenshots/enemy_sheet.png). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class EnemySheetRender {
    private val enabled = System.getProperty("cyberop.renderPreviews") == "true"
    private val outDir = File(System.getProperty("cyberop.previewDir") ?: "build/screens")

    private val cols = 18
    private val cellW = 136f
    private val cellH = 150f
    private val margin = 40f

    @Test fun enemySheet() {
        assumeTrue(enabled)
        val defs = Enemies.all
        val eliteSamples = listOf("malware", "sql_injector", "trojan", "sniffer", "ddos_node", "poly_golem", "enc_marksman", "botnet_bull")
            .mapNotNull { id -> defs.firstOrNull { it.id == id } }
        val threatRows = (defs.size + cols - 1) / cols
        val width = (margin * 2 + cols * cellW).toInt()
        val eliteTop = margin + 110f + threatRows * cellH + 70f
        val height = (eliteTop + 90f + EliteModifier.entries.size * cellH + margin).toInt()

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(android.graphics.Canvas(bmp))
        val g = GameEngine(RunConfig(seed = 1L))
        val renderer = ArenaRenderer()
        val title = android.graphics.Paint().apply { isAntiAlias = true; color = 0xFF00E5FF.toInt(); textSize = 46f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
        val head = android.graphics.Paint(title).apply { textSize = 32f; color = 0xFFE6EEFA.toInt() }
        val sub = android.graphics.Paint(title).apply { textSize = 20f; color = 0xFF8FA0B8.toInt(); typeface = Typeface.MONOSPACE }
        val label = android.graphics.Paint(title).apply { textSize = 14.5f; color = 0xFFC9D4E3.toInt(); textAlign = android.graphics.Paint.Align.CENTER; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
        val small = android.graphics.Paint(label).apply { textSize = 12f; color = 0xFF7F8EA3.toInt(); typeface = Typeface.MONOSPACE }

        fun enemyAt(def: com.cyberoperative.game.data.EnemyDef, uid: Int, x: Float, y: Float, elite: EliteModifier?): Enemy = Enemy().apply {
            active = true; this.uid = uid; this.def = def; this.elite = elite; boss = null
            this.x = x; this.y = y
            radius = (def.radius * (if (elite != null) EliteModifier.SIZE_MUL else 1f) * 1.25f).coerceAtMost(40f)
            hp = 1f; maxHp = 1f; state = AiState.MOVE; hitFlash = 0f
        }

        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(width.toFloat(), height.toFloat())) {
            drawRect(androidx.compose.ui.graphics.Color(0xFF070B12))
            val nc = drawContext.canvas.nativeCanvas
            nc.drawText("CYBER OPERATIVE · THREAT ROSTER", margin, margin + 40f, title)
            nc.drawText("${defs.size} threats · level each first appears in brackets (999 = only spawned by bosses / other threats)", margin, margin + 78f, sub)
            val top = margin + 110f
            for ((i, d) in defs.withIndex()) {
                val cx = margin + (i % cols) * cellW + cellW / 2f
                val cy = top + (i / cols) * cellH
                drawRoundRect(androidx.compose.ui.graphics.Color(0xFF0E1522), androidx.compose.ui.geometry.Offset(cx - cellW / 2f + 4f, cy + 4f),
                    Size(cellW - 8f, cellH - 8f), androidx.compose.ui.geometry.CornerRadius(10f))
                val e = enemyAt(d, i, cx, cy + 82f, null)
                with(renderer) { drawEnemy(g, e, 1.1f) }
                val words = d.name.split(" ")
                val (l1, l2) = if (words.size > 1 && d.name.length > 14) words.first() to words.drop(1).joinToString(" ") else d.name to ""
                nc.drawText(l1, cx, cy + cellH - 36f, label)
                if (l2.isNotEmpty()) nc.drawText(l2, cx, cy + cellH - 21f, label)
                nc.drawText("[${d.minLevel}]", cx, cy + cellH - 8f, small)
            }

            nc.drawText("ELITES · ${EliteModifier.entries.size} modifiers (any threat can roll one; shown on sample threats)", margin, eliteTop, head)
            nc.drawText("×${EliteModifier.BASE_HP_MUL} base HP · ×${EliteModifier.SIZE_MUL} size · ×${EliteModifier.REWARD_MUL} rewards · Normal/Hard: ×2 HP, armor, −20% damage taken", margin, eliteTop + 34f, sub)
            val etop = eliteTop + 70f
            for ((r, m) in EliteModifier.entries.withIndex()) {
                val ry = etop + r * cellH
                val lp = android.graphics.Paint(head).apply { color = m.color.toInt(); textSize = 26f }
                nc.drawText(m.label, margin, ry + 62f, lp)
                val stats = buildList {
                    if (m.hpMul != 1f) add("HP ×${m.hpMul}")
                    if (m.speedMul != 1f) add("speed ×${m.speedMul}")
                    if (m.attackRateMul != 1f) add("attack ×${m.attackRateMul}")
                    if (m.damageTakenMul != 1f) add("takes ×${m.damageTakenMul}")
                }.joinToString(" · ")
                nc.drawText(stats, margin, ry + 92f, sub)
                for ((k, d) in eliteSamples.withIndex()) {
                    val cx = margin + 540f + k * 236f
                    val e = enemyAt(d, 1000 + r * 10 + k, cx, ry + 84f, m)
                    with(renderer) { drawEnemy(g, e, 1.1f + k * 0.13f) }
                    nc.drawText(d.name, cx, ry + cellH - 14f, small)
                }
            }
        }
        outDir.mkdirs()
        File(outDir, "enemy_sheet.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
