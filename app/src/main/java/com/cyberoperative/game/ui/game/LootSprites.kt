package com.cyberoperative.game.ui.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.Loot
import com.cyberoperative.game.engine.LootKind
import com.cyberoperative.game.ui.common.GameTextPaint
import com.cyberoperative.game.ui.common.argb
import com.cyberoperative.game.platform.drawGameText
import kotlin.math.ln
import kotlin.math.sin

/**
 * PC floor pickups (owner, 2026-10-11): the HP charge is a green potion flask, the Packet
 * Bomb a small bomb with a lit fuse and a pulsing cyan ring (so it reads as a pickup), and
 * kill money a gold € cash bag. Everything bobs gently and pops in when dropped.
 */
object LootSprites {
    private val path = Path()
    private const val SCALE = 1.8f
    private val euroPaint = GameTextPaint(10f, argb(255, 90, 60, 0), bold = true)

    fun DrawScope.drawLoot(g: GameEngine, time: Float) {
        for (l in g.loot) drawOne(l, time)
    }

    private fun DrawScope.drawOne(l: Loot, time: Float) {
        val pop = (l.age * 5f).coerceAtMost(1f)
        val bob = sin(time * 3.2f + l.x * 0.05f) * 2.5f
        val x = l.x
        val y = l.y + bob
        // Drawn larger than life so they read at the zoomed-out PC view.
        scale(SCALE, Offset(x, l.y)) {
            drawOval(Color.Black.copy(alpha = 0.35f), Offset(x - 10f, l.y + 9f), Size(20f, 6f))
            when (l.kind) {
                LootKind.HP_CHARGE -> potion(x, y, pop, time)
                LootKind.PACKET_BOMB -> bomb(x, y, pop, time)
                LootKind.CASH -> cash(x, y, pop * (0.85f + 0.12f * ln(1f + l.amount.toFloat()).coerceAtMost(4f) / 4f))
            }
        }
    }

    private fun DrawScope.potion(x: Float, y: Float, k: Float, time: Float) {
        val glow = 0.25f + 0.12f * sin(time * 4f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF00FF9C).copy(alpha = glow), Color.Transparent), Offset(x, y), 26f * k), 26f * k, Offset(x, y))
        val r = 8f * k
        // Neck and cork.
        drawRect(Color(0xCCBFEFFF), Offset(x - 3f * k, y - r - 9f * k), Size(6f * k, 8f * k))
        drawRoundRect(Color(0xFF8A5A2B), Offset(x - 4f * k, y - r - 12f * k), Size(8f * k, 4f * k), CornerRadius(1.5f * k))
        // Bulb: glass, red-green charge liquid, highlight.
        drawCircle(Color(0x66BFEFFF), r + 1.5f * k, Offset(x, y))
        drawCircle(Color(0xFF00C878), r, Offset(x, y + 1f * k))
        drawCircle(Color(0xFF00FF9C), r * 0.62f, Offset(x - 1f * k, y + 2f * k))
        drawCircle(Color.White.copy(alpha = 0.7f), r * 0.22f, Offset(x - r * 0.4f, y - r * 0.3f))
        // A little "+".
        drawLine(Color.White, Offset(x - 3f * k, y + 1.5f * k), Offset(x + 3f * k, y + 1.5f * k), 1.6f * k)
        drawLine(Color.White, Offset(x, y - 1.5f * k), Offset(x, y + 4.5f * k), 1.6f * k)
        drawCircle(Color(0xFFBFEFFF), r + 1.5f * k, Offset(x, y), style = Stroke(1.2f * k))
    }

    private fun DrawScope.bomb(x: Float, y: Float, k: Float, time: Float) {
        // Pulsing pickup ring.
        val p = (time * 1.4f) % 1f
        drawCircle(Color(0xFF7DF9FF).copy(alpha = (1f - p) * 0.7f), (12f + p * 14f) * k, Offset(x, y), style = Stroke(2f))
        drawCircle(Brush.radialGradient(listOf(Color(0xFF7DF9FF).copy(alpha = 0.22f), Color.Transparent), Offset(x, y), 24f * k), 24f * k, Offset(x, y))
        val r = 9f * k
        drawCircle(Color(0xFF14181F), r, Offset(x, y))
        drawCircle(Color(0xFF2E9BFF).copy(alpha = 0.55f), r, Offset(x, y), style = Stroke(1.6f * k))
        drawCircle(Color.White.copy(alpha = 0.45f), r * 0.25f, Offset(x - r * 0.4f, y - r * 0.4f))
        // Cap and fuse with a flickering spark.
        drawRect(Color(0xFF3A4252), Offset(x + r * 0.35f, y - r - 2f * k), Size(5f * k, 4f * k))
        path.reset()
        path.moveTo(x + r * 0.6f, y - r - 1f * k)
        path.quadraticTo(x + r * 1.2f, y - r - 8f * k, x + r * 1.5f, y - r - 5f * k)
        drawPath(path, Color(0xFFB0905A), style = Stroke(1.6f * k, cap = StrokeCap.Round))
        val flick = 0.6f + 0.4f * sin(time * 31f)
        drawCircle(Color(0xFFFFC233).copy(alpha = flick), 3f * k, Offset(x + r * 1.5f, y - r - 5f * k))
        drawCircle(Color.White.copy(alpha = flick), 1.4f * k, Offset(x + r * 1.5f, y - r - 5f * k))
    }

    private fun DrawScope.cash(x: Float, y: Float, k: Float) {
        val w = 18f * k
        val h = 16f * k
        // Sack body.
        path.reset()
        path.moveTo(x - w * 0.28f, y - h * 0.45f)
        path.cubicTo(x - w * 0.75f, y - h * 0.1f, x - w * 0.62f, y + h * 0.55f, x, y + h * 0.55f)
        path.cubicTo(x + w * 0.62f, y + h * 0.55f, x + w * 0.75f, y - h * 0.1f, x + w * 0.28f, y - h * 0.45f)
        path.close()
        drawPath(path, Brush.verticalGradient(listOf(Color(0xFFFFD86B), Color(0xFFD99A1E)), y - h * 0.5f, y + h * 0.6f))
        drawPath(path, Color(0xFF7A5200), style = Stroke(1.3f * k))
        // Tied neck.
        drawOval(Color(0xFFE8B23A), Offset(x - w * 0.3f, y - h * 0.68f), Size(w * 0.6f, h * 0.26f))
        drawLine(Color(0xFF7A5200), Offset(x - w * 0.24f, y - h * 0.45f), Offset(x + w * 0.24f, y - h * 0.45f), 1.6f * k)
        euroPaint.textSize = 10f * k
        drawGameText("€", x, y + h * 0.32f, euroPaint)
    }
}
