package com.cyberoperative.game.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberoperative.game.engine.CampaignMap
import com.cyberoperative.game.engine.WorldMap
import com.cyberoperative.game.ui.common.GameTextPaint
import com.cyberoperative.game.ui.common.argb
import com.cyberoperative.game.platform.drawGameText
import com.cyberoperative.game.ui.theme.Palette
import kotlin.math.min
import kotlin.math.sin

/**
 * The world map ([M], controller BACK, or the MAP button): see-through so the fight stays
 * visible, and the game keeps running underneath. Shows this world's whole layout faintly,
 * the rooms you've cleared, where you are (pulsing), the next room to take, and the boss room.
 */
@Composable
fun WorldMapOverlay(hud: HudSnapshot, map: WorldMap, time: Float, onClose: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0x99020409)).clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
            Text("SERVER WORLD ${hud.world}", color = Palette.Cyan, style = MaterialTheme.typography.titleLarge)
            Text(
                if (hud.room >= CampaignMap.BOSS_INDEX) "BOSS ROOM" else "ROOM ${hud.room + 1} OF ${CampaignMap.ROOMS} · BOSS AFTER ROOM ${CampaignMap.ROOMS}",
                color = Palette.TextSecondary, style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.height(10.dp))
            Canvas(Modifier.size(width = 520.dp, height = 400.dp)) { drawMap(hud, map, time) }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Legend(Palette.Cyan, "YOU")
                Legend(Palette.Green, "CLEARED")
                Legend(Palette.Gold, "NEXT")
                Legend(Palette.Red, "BOSS")
            }
            Spacer(Modifier.height(6.dp))
            Text("[M] / BACK / TAP TO CLOSE", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun Legend(c: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(c.copy(alpha = 0.7f), RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(5.dp))
        Text(label, color = Palette.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

private val labelPaint = GameTextPaint(12f, argb(255, 230, 240, 255), bold = true)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMap(hud: HudSnapshot, map: WorldMap, time: Float) {
    val cols = map.maxX - map.minX + 1
    val rows = map.maxY - map.minY + 1
    val cell = min(size.width / cols, size.height / rows).coerceAtMost(90f)
    val ox = (size.width - cell * cols) / 2f
    val oy = (size.height - cell * rows) / 2f
    fun centre(i: Int): Offset {
        val r = map.rooms[i]
        return Offset(ox + (r.gx - map.minX + 0.5f) * cell, oy + (r.gy - map.minY + 0.5f) * cell)
    }
    // Corridors along the path.
    for (i in 1 until map.rooms.size) {
        val walked = (i - 1) in hud.clearedRooms && (i in hud.visitedRooms || i == hud.room)
        drawLine(
            (if (walked) Palette.Green else Palette.TextMuted).copy(alpha = if (walked) 0.8f else 0.35f),
            centre(i - 1), centre(i), cell * 0.12f
        )
    }
    val next = if (hud.room in hud.clearedRooms) hud.room + 1 else -1
    val pulse = 0.5f + 0.5f * sin(time * 4f)
    for (r in map.rooms) {
        val c = centre(r.index)
        val s = cell * 0.62f
        val tl = Offset(c.x - s / 2f, c.y - s / 2f)
        val base = when {
            r.index == hud.room -> Palette.Cyan
            r.boss -> Palette.Red
            r.index == next -> Palette.Gold
            r.index in hud.clearedRooms -> Palette.Green
            else -> Palette.TextMuted
        }
        val fill = when {
            r.index == hud.room -> 0.45f + 0.35f * pulse
            r.index in hud.clearedRooms -> 0.35f
            r.index == next || r.boss -> 0.18f
            else -> 0.06f
        }
        drawRoundRect(base.copy(alpha = fill), tl, Size(s, s), CornerRadius(s * 0.15f))
        drawRoundRect(base.copy(alpha = if (r.index in hud.visitedRooms || r.boss || r.index == next) 0.95f else 0.4f), tl, Size(s, s), CornerRadius(s * 0.15f), style = Stroke(2.5f))
        labelPaint.textSize = s * 0.34f
        labelPaint.color = argb(255, 235, 242, 255)
        drawGameText(if (r.boss) "☠" else "${r.index + 1}", c.x, c.y + s * 0.12f, labelPaint)
    }
}
