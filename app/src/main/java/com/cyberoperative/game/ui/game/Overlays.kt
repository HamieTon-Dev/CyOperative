package com.cyberoperative.game.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cyberoperative.game.data.StoreCatalog
import com.cyberoperative.game.engine.UpgradeOffer
import com.cyberoperative.game.ui.common.CyberButton
import com.cyberoperative.game.ui.theme.Palette

/** Level-up screen (§53): gameplay paused, three stacked cards, tap one. */
@Composable
fun UpgradeOverlay(session: GameSession) {
    @Suppress("UNUSED_VARIABLE") val tick = session.hud
    val offer = session.engine.offer
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE6040810))
            .clickable(enabled = true, onClick = {})
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Text("SYSTEM UPGRADE", color = Palette.Green, style = MaterialTheme.typography.headlineMedium)
            Text("SELECT ONE MODULE", color = Palette.TextSecondary, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(18.dp))
            offer.forEachIndexed { i, o ->
                UpgradeCard(o) { session.chooseUpgrade(i) }
                Spacer(Modifier.height(12.dp))
            }
            if (session.hud.rerolls > 0) {
                Spacer(Modifier.height(4.dp))
                CyberButton("REROLL (${session.hud.rerolls})", accent = Palette.Purple) { session.reroll() }
            }
        }
    }
}

@Composable
private fun UpgradeCard(o: UpgradeOffer, onClick: () -> Unit) {
    val rarity = Color(o.def.rarity.color)
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Surface, shape)
            .border(if (o.isEvolution) 3.dp else 2.dp, rarity, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(62.dp)
                .background(rarity.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                .border(1.dp, rarity.copy(alpha = 0.7f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(o.def.glyph, color = rarity, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (o.isEvolution) "EVOLUTION · ${o.def.rarity.label}" else o.def.rarity.label,
                    color = rarity, style = MaterialTheme.typography.labelSmall
                )
                if (!o.def.instant && o.def.maxLevel > 1) {
                    Spacer(Modifier.width(8.dp))
                    Text("LV ${o.nextLevel - 1} → ${o.nextLevel}/${o.def.maxLevel}", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(o.title, color = Palette.TextPrimary, style = MaterialTheme.typography.titleMedium)
            Text(o.effect, color = Palette.Green, style = MaterialTheme.typography.bodyMedium)
            Text(o.def.description, color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Game over (§54). */
@Composable
fun GameOverOverlay(
    result: RunResult,
    hud: HudSnapshot,
    onRevive: () -> Unit,
    onMenu: () -> Unit,
    onNew: () -> Unit
) {
    val title = remember { listOf("OPERATIVE TERMINATED", "CONNECTION LOST", "SYSTEM COMPROMISED").random() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xEE07020A))
            .clickable(enabled = true, onClick = {}),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = Palette.Red, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Palette.Surface, RoundedCornerShape(8.dp))
                    .border(1.dp, Palette.Divider, RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                StatRow("LEVEL REACHED", result.level.toString(), if (result.newBestLevel) "NEW RECORD" else null)
                StatRow("SCORE", "%,d".format(result.score), if (result.newBestScore) "NEW RECORD" else null)
                StatRow("THREATS ELIMINATED", result.kills.toString(), null)
                StatRow("BOSSES ELIMINATED", result.bosses.toString(), null)
                StatRow("€ EARNED", "€ ${result.euros}", null, Palette.Euro)
            }
            if (result.newAchievements.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("ACHIEVEMENTS UNLOCKED", color = Palette.Gold, style = MaterialTheme.typography.labelLarge)
                for (a in result.newAchievements) Text("★ $a", color = Palette.TextPrimary, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(20.dp))
            val reviveLabel: String? = when {
                hud.canRevive -> "FREE · ${hud.revivesLeft} LEFT THIS RUN"
                hud.paidRevivesLeft <= 0 -> null
                hud.reviveTokens > 0 -> "USE 1 OF ${hud.reviveTokens} REVIVES"
                hud.diamonds >= StoreCatalog.revivePacks.first().priceDiamonds -> "◇${StoreCatalog.revivePacks.first().priceDiamonds}"
                else -> null
            }
            if (reviveLabel != null) {
                CyberButton("REVIVE", Modifier.fillMaxWidth(), accent = Palette.Green, primary = true, subtitle = reviveLabel) { onRevive() }
                Spacer(Modifier.height(10.dp))
            } else if (!hud.canRevive && hud.paidRevivesLeft > 0) {
                Text("No revives left — revive packs are in the STORE.", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(10.dp))
            }
            CyberButton("START NEW OPERATION", Modifier.fillMaxWidth(), accent = Palette.Cyan) { onNew() }
            Spacer(Modifier.height(10.dp))
            CyberButton("RETURN TO MAIN MENU", Modifier.fillMaxWidth(), accent = Palette.TextSecondary) { onMenu() }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, badge: String?, color: Color = Palette.TextPrimary) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Palette.TextSecondary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
        if (badge != null) {
            Text(badge, color = Palette.Gold, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(8.dp))
        }
        Text(value, color = color, style = MaterialTheme.typography.titleMedium)
    }
}
