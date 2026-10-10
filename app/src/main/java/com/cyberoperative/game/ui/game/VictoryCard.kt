package com.cyberoperative.game.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.ui.theme.Palette

/** When the card slides in (game seconds after the kill), after the body has shattered. */
private const val CARD_IN = 0.55f

/**
 * Victory sequence card (owner, 2026-10-10): THREAT NEUTRALIZED, the boss's name
 * in its colour, fight time (NEW RECORD when beaten), the bounty counting up and
 * a FIRST DEFEAT · DOSSIER UNLOCKED badge the first time. Tap anywhere to skip.
 */
@Composable
fun VictoryCard(h: HudSnapshot, onSkip: () -> Unit) {
    val t = h.victoryT
    if (t < CARD_IN) return
    val interaction = remember { MutableInteractionSource() }
    // Campaign: tap anywhere to skip (the room is over). Endless keeps fighting, so only the card is tappable.
    val tapAnywhere = h.mode != com.cyberoperative.game.engine.GameMode.ENDLESS
    Box(
        if (tapAnywhere) Modifier.fillMaxSize().clickable(interaction, indication = null) { onSkip() } else Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val local = t - CARD_IN
        val left = GameEngine.VICTORY_SECONDS - t
        val alpha = minOf(1f, local / 0.25f, left / 0.35f).coerceIn(0f, 1f)
        val pop = 0.92f + 0.08f * minOf(1f, local / 0.25f)
        val col = if (h.victoryColor != 0L) Color(h.victoryColor) else Palette.Red
        // Dim band behind the card so it reads over the arena.
        Box(
            Modifier
                .fillMaxWidth()
                .height(320.dp)
                .alpha(alpha * 0.85f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000), Color(0xE6000000), Color.Transparent)))
        )
        Column(
            Modifier
                .padding(horizontal = 28.dp)
                .alpha(alpha)
                .scale(pop)
                .fillMaxWidth()
                .background(Palette.Surface.copy(alpha = 0.92f), RoundedCornerShape(10.dp))
                .border(1.5.dp, col, RoundedCornerShape(10.dp))
                .clickable(interaction, indication = null) { onSkip() }
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header resolves out of glitch characters.
            val header = "THREAT NEUTRALIZED"
            val resolved = (local / 0.45f * header.length).toInt().coerceIn(0, header.length)
            val glitch = "#%&@!?01<>/=+*"
            val shown = header.mapIndexed { i, c -> if (i < resolved || c == ' ') c else glitch[(i * 7 + (local * 40).toInt()) % glitch.length] }.joinToString("")
            Text(shown, color = Palette.Green, style = MaterialTheme.typography.titleMedium, letterSpacing = 3.sp)
            Spacer(Modifier.height(8.dp))
            // Scan line under the header.
            Canvas(Modifier.fillMaxWidth().height(2.dp)) {
                val f = (local / 0.6f).coerceIn(0f, 1f)
                drawLine(col, Offset(size.width * (0.5f - f / 2f), 0f), Offset(size.width * (0.5f + f / 2f), 0f), 2.dp.toPx())
            }
            Spacer(Modifier.height(10.dp))
            Text("${h.victoryTag} ${h.victoryName}", color = col, fontWeight = FontWeight.Bold, fontSize = 26.sp, textAlign = TextAlign.Center)
            Text(h.victoryTitle.uppercase(), color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp)
            Spacer(Modifier.height(14.dp))
            StatRow("FIGHT TIME", formatFightTime(h.victorySeconds), Palette.TextPrimary, badge = if (h.victoryBest) "NEW RECORD" else null)
            Spacer(Modifier.height(6.dp))
            val count = (local / 0.8f).coerceIn(0f, 1f)
            val eased = 1f - (1f - count) * (1f - count)
            StatRow("BOUNTY", "+€" + "%,d".format((h.victoryBounty * eased).toInt()), Palette.Euro)
            if (h.victoryFirst) {
                Spacer(Modifier.height(12.dp))
                val blink = if (((local * 3f).toInt() % 2) == 0 || local > 1.6f) 1f else 0.55f
                Text(
                    "◆ FIRST DEFEAT · DOSSIER UNLOCKED",
                    color = Palette.Gold.copy(alpha = blink), style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .border(1.dp, Palette.Gold.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("TAP TO CONTINUE", color = Palette.TextMuted.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, color: Color, badge: String? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Palette.TextSecondary, style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (badge != null) {
                Text(
                    badge, color = Palette.Background, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.background(Palette.Gold, RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(value, color = color, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** m:ss.t */
fun formatFightTime(seconds: Float): String {
    if (seconds <= 0f) return "—"
    val tenths = (seconds * 10f).toInt()
    return "%d:%02d.%d".format(tenths / 600, (tenths / 10) % 60, tenths % 10)
}
