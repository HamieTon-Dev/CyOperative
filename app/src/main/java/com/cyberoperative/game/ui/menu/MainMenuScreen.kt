package com.cyberoperative.game.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cyberoperative.game.save.PlayerProfile
import com.cyberoperative.game.ui.common.CurrencyChip
import com.cyberoperative.game.ui.common.CyberButton
import com.cyberoperative.game.ui.common.LivingBackground
import com.cyberoperative.game.ui.common.OperativeMarkIcon
import com.cyberoperative.game.ui.theme.Palette
import kotlin.math.sin

enum class MenuTarget(val label: String) {
    PLAY("PLAY"), OPERATIVE("OPERATIVE"), UPGRADES("UPGRADES"), ARMORY("ARMORY"),
    SKINS("SKINS"), STORE("STORE"), LEADERBOARD("LEADERBOARD"), ACHIEVEMENTS("ACHIEVEMENTS"),
    SETTINGS("SETTINGS"), ABOUT("ABOUT"), ENDLESS("ENDLESS MODE")
}

/** Main menu (§6). One big PLAY, the rest in a compact two-column grid. */
@Composable
fun MainMenuScreen(profile: PlayerProfile, onSelect: (MenuTarget) -> Unit) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val s = withFrameNanos { it }
        while (true) withFrameNanos { t = (it - s) / 1e9f }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
    ) {
        LivingBackground(Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("OP LVL ${operativeLevel(profile.operativeXp)}", color = Palette.Green, style = MaterialTheme.typography.labelLarge)
                Row {
                    CurrencyChip("€", profile.euros, Palette.Euro)
                    Spacer(Modifier.width(8.dp))
                    CurrencyChip("◇", profile.diamonds, Palette.Diamond)
                }
            }
            Spacer(Modifier.height(22.dp))
            OperativeMarkIcon(
                com.cyberoperative.game.data.OperativeSkins.byId(profile.selectedSkin),
                Modifier
                    .size(110.dp)
                    .scale(1f + 0.03f * sin(t * 2f))
            )
            Spacer(Modifier.height(10.dp))
            // Neon title, as in the key art.
            val glow = 0.7f + 0.3f * sin(t * 2.4f)
            Text(
                "CYBER", color = Color(0xFFE6FBFF),
                style = MaterialTheme.typography.displayLarge.copy(
                    shadow = Shadow(Palette.Cyan.copy(alpha = glow), Offset(0f, 0f), 28f)
                )
            )
            Text(
                "OPERATIVE", color = Palette.Green,
                style = MaterialTheme.typography.displayMedium.copy(
                    shadow = Shadow(Palette.Green.copy(alpha = glow), Offset(0f, 0f), 22f)
                )
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "—  ENDLESS CYBER ROGUELIKE ACTION  —",
                color = Palette.TextSecondary, style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            if (profile.highestLevel > 0) {
                Text(
                    "BEST LEVEL ${profile.highestLevel}   ·   BEST SCORE ${"%,d".format(profile.bestScore)}",
                    color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(Modifier.height(20.dp))
            CyberButton(
                "PLAY", primary = true, accent = Palette.Green,
                subtitle = "START OPERATION",
                modifier = Modifier.fillMaxWidth()
            ) { onSelect(MenuTarget.PLAY) }
            Spacer(Modifier.height(10.dp))
            CyberButton(
                "ENDLESS MODE", accent = Palette.Purple,
                subtitle = if (profile.endlessBestStage > 0) "BEST STAGE ${profile.endlessBestStage}" else "ONE ROOM · NEVER-ENDING THREATS",
                modifier = Modifier.fillMaxWidth()
            ) { onSelect(MenuTarget.ENDLESS) }
            Spacer(Modifier.height(14.dp))
            val rest = MenuTarget.entries.filter { it != MenuTarget.PLAY && it != MenuTarget.ENDLESS }
            for (row in rest.chunked(2)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (item in row) {
                        CyberButton(item.label, modifier = Modifier.weight(1f)) { onSelect(item) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("HamieTon.dev", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Account level from Operative XP (§39): each level needs a little more. */
fun operativeLevel(xp: Long): Int {
    var level = 1
    var need = 200L
    var rest = xp
    while (rest >= need && level < 999) {
        rest -= need
        level++
        need = 200L + (level - 1) * 60L
    }
    return level
}
