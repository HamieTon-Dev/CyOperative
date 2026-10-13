package com.cyberoperative.game.ui.menu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.cyberoperative.game.data.Operatives
import com.cyberoperative.game.engine.RunSnapshot
import com.cyberoperative.game.meta.StoreManager
import com.cyberoperative.game.save.PlayerProfile
import com.cyberoperative.game.ui.common.CurrencyChip
import com.cyberoperative.game.ui.common.CyberButton
import com.cyberoperative.game.ui.common.CyberTitleLogo
import com.cyberoperative.game.ui.common.LivingBackground
import com.cyberoperative.game.ui.game.BodyStyle
import com.cyberoperative.game.ui.game.FigurePreview
import com.cyberoperative.game.ui.theme.Palette
import kotlin.math.sin

/**
 * The PC (Steam) main menu, laid out for a 16:9 screen with no scrolling: a small badge and
 * wordmark at the top, every destination right underneath, your operative on a lit stage
 * on the left and your records and controls on the right.
 */
@Composable
fun LandscapeMainMenu(profile: PlayerProfile, t: Float, onSelect: (MenuTarget) -> Unit) {
    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        LivingBackground(Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                com.cyberoperative.game.ui.common.SafeBrush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), radius = 1600f)
            )
        )
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 16.dp)) {
            // Everything below the logo needs ~390dp; the logo gets what is left (never huge).
            // Short screens (phones held sideways): no badge, a small logo, and two button rows.
            val compact = maxHeight < 560.dp
            val logoW = if (compact) ((maxHeight - 300.dp) * (1000f / 420f)).coerceIn(170.dp, 320.dp)
            else ((maxHeight - 450.dp) * (1000f / 420f)).coerceIn(260.dp, 470.dp)
            val centreW = if (compact) (maxWidth * 0.68f).coerceIn(560.dp, 720.dp) else (maxWidth * 0.56f).coerceIn(600.dp, 780.dp)
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(operativeLabel(profile.operativeXp), color = Palette.Green, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.weight(1f))
                    CurrencyChip("€", profile.euros, Palette.Euro)
                    Spacer(Modifier.width(8.dp))
                    CurrencyChip("◇", profile.diamonds, Palette.Diamond)
                }
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    HeroStage(profile, t, Modifier.weight(1f).fillMaxHeight())
                    Column(Modifier.width(centreW).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (!compact) com.cyberoperative.game.ui.common.OperativeEmblem(
                            StoreManager.effectiveSkin(profile),
                            Modifier.size(64.dp).scale(1f + 0.03f * sin(t * 2f))
                        )
                        CyberTitleLogo(Modifier.width(logoW), glow = 0.75f + 0.25f * sin(t * 2.4f))
                        Spacer(Modifier.height(if (compact) 4.dp else 10.dp))
                        MenuButtons(profile, onSelect, compact)
                    }
                    InfoSide(profile, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun MenuButtons(profile: PlayerProfile, onSelect: (MenuTarget) -> Unit, compact: Boolean = false) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
        val saved = remember(profile.savedRun) { RunSnapshot.decodeOrNull(profile.savedRun) }
        val rest = MenuTarget.entries.filter { it != MenuTarget.PLAY && it != MenuTarget.ENDLESS && it != MenuTarget.CONTINUE && it != MenuTarget.CO_OP }
        if (compact) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (saved != null) CyberButton("CONTINUE", primary = true, accent = Palette.Green, modifier = Modifier.weight(1.3f)) { onSelect(MenuTarget.CONTINUE) }
                CyberButton(if (saved != null) "NEW" else "PLAY", primary = saved == null, accent = Palette.Green, modifier = Modifier.weight(1.3f)) { onSelect(MenuTarget.PLAY) }
                CyberButton("ENDLESS", accent = Palette.Purple, modifier = Modifier.weight(1f)) { onSelect(MenuTarget.ENDLESS) }
                CyberButton("CO-OP", accent = Palette.Magenta, modifier = Modifier.weight(1f)) { onSelect(MenuTarget.CO_OP) }
            }
            for (row in rest.chunked(4)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (item in row) CyberButton(shortLabel(item), modifier = Modifier.weight(1f)) { onSelect(item) }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            return@Column
        }
        if (saved != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CyberButton("CONTINUE", primary = true, accent = Palette.Green, subtitle = saved.label, modifier = Modifier.weight(1.4f)) { onSelect(MenuTarget.CONTINUE) }
                CyberButton("NEW OPERATION", accent = Palette.Cyan, subtitle = "START OVER", modifier = Modifier.weight(1f)) { onSelect(MenuTarget.PLAY) }
            }
        } else {
            CyberButton("PLAY", primary = true, accent = Palette.Green, subtitle = if (profile.bestWorld > 0) "CAMPAIGN · BEST: WORLD ${profile.bestWorld}" else "CAMPAIGN · 15 SERVER WORLDS", modifier = Modifier.fillMaxWidth()) { onSelect(MenuTarget.PLAY) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CyberButton(
                "ENDLESS MODE", accent = Palette.Purple,
                subtitle = if (profile.endlessBestStage > 0) "BEST LEVEL ${profile.endlessBestStage}" else "NO MAP · NO END",
                modifier = Modifier.weight(1f)
            ) { onSelect(MenuTarget.ENDLESS) }
            CyberButton("CO-OP", accent = Palette.Magenta, subtitle = "PLAY WITH A FRIEND", modifier = Modifier.weight(1f)) { onSelect(MenuTarget.CO_OP) }
        }
        for (row in rest.chunked(4)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (item in row) CyberButton(item.label, modifier = Modifier.weight(1f)) { onSelect(item) }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun HeroStage(profile: PlayerProfile, t: Float, modifier: Modifier) {
    val op = Operatives.byId(profile.selectedOperative)
    val skin = StoreManager.effectiveSkin(profile, op.id)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height * 0.9f
                val r = (size.width * 0.36f).coerceAtMost(size.height * 0.3f)
                val pulse = 0.5f + 0.5f * sin(t * 2f)
                drawOval(
                    com.cyberoperative.game.ui.common.SafeBrush.radialGradient(listOf(Palette.Cyan.copy(alpha = 0.32f), Color.Transparent), Offset(cx, cy), r * 1.2f),
                    Offset(cx - r * 1.2f, cy - r * 0.35f), Size(r * 2.4f, r * 0.7f)
                )
                for (i in 0 until 3) {
                    val k = 1f - i * 0.22f
                    drawOval(
                        Palette.Cyan.copy(alpha = (0.55f - i * 0.15f) * (0.7f + 0.3f * pulse)),
                        Offset(cx - r * k, cy - r * 0.2f * k), Size(r * 2f * k, r * 0.4f * k), style = Stroke(2.5f)
                    )
                }
                for (i in 0 until 14) {
                    val ph = (t * 0.35f + i * 0.13f) % 1f
                    val x = cx + sin(i * 2.3f + t * 0.4f) * r * 0.8f
                    drawCircle(Palette.Cyan.copy(alpha = (1f - ph) * 0.6f), 2.5f, Offset(x, cy - ph * size.height * 0.7f))
                }
            }
            FigurePreview(
                BodyStyle.byId(op.bodyId), skin,
                Modifier.widthIn(max = 260.dp).fillMaxWidth().fillMaxHeight(0.5f).padding(bottom = 14.dp)
            )
        }
        Text(op.name, color = Palette.TextPrimary, style = MaterialTheme.typography.titleLarge)
        Text(op.passive, color = Palette.Cyan, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(4.dp))
        Text("HamieTon.dev", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun InfoSide(profile: PlayerProfile, modifier: Modifier) {
    Column(modifier.padding(start = 24.dp), verticalArrangement = Arrangement.Bottom) {
        if (profile.highestLevel > 0) {
            InfoLine("BEST LEVEL", "${profile.highestLevel}")
            InfoLine("BEST SCORE", "%,d".format(profile.bestScore))
            if (profile.bestWorld > 0) InfoLine("CAMPAIGN", "WORLD ${profile.bestWorld}" + if (profile.campaignsCompleted > 0) " · ${profile.campaignsCompleted}× DONE" else "")
            if (profile.endlessBestStage > 0) InfoLine("ENDLESS", "LEVEL ${profile.endlessBestStage}")
            Spacer(Modifier.height(14.dp))
        }
        if (!com.cyberoperative.game.platform.AppInfo.desktop) return@Column
        Text("CONTROLS", color = Palette.Cyan, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        for (l in listOf("WASD · MOVE", "MOUSE · AIM & FIRE", "LEFT STICK · MOVE", "RIGHT STICK · AIM & FIRE", "ESC / START · PAUSE", "F11 · FULLSCREEN")) {
            Text(l, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        Text(value, color = Palette.TextPrimary, style = MaterialTheme.typography.labelMedium)
    }
}

/** Labels that fit a phone held sideways. */
private fun shortLabel(t: MenuTarget): String = when (t) {
    MenuTarget.LEADERBOARD -> "SCORES"
    MenuTarget.ACHIEVEMENTS -> "TROPHIES"
    else -> t.label
}
