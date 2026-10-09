package com.cyberoperative.game.ui.game

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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cyberoperative.game.data.UpgradeDef
import com.cyberoperative.game.data.Upgrades
import com.cyberoperative.game.engine.StatLine
import com.cyberoperative.game.ui.common.CyberButton
import com.cyberoperative.game.ui.theme.Palette

/**
 * Everything about one upgrade (owner, 2026-10-09: tap and hold to see what it
 * does and which stats change, by how much): rarity, level, effect, the full
 * description, then each stat that changes, current → after, green when it
 * goes up and red when it goes down.
 */
@Composable
fun UpgradeDetails(
    def: UpgradeDef, level: Int, lines: List<StatLine>, modifier: Modifier = Modifier,
    title: String? = null, accent: Color? = null, showChanges: Boolean = true
) {
    val c = Color(def.rarity.color)
    Column(
        modifier
            .fillMaxWidth()
            .background(Palette.Surface, RoundedCornerShape(10.dp))
            .border(2.dp, accent ?: c, RoundedCornerShape(10.dp))
            .padding(14.dp)
    ) {
        if (title != null) Text(title, color = accent ?: c, style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(def.glyph, color = c, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(def.name, color = Palette.TextPrimary, style = MaterialTheme.typography.titleMedium)
                Text(
                    def.rarity.label + (if (Upgrades.isWeapon(def)) " · WEAPON" else " · POWER-UP") +
                        if (def.instant) "" else " · LV $level" + if (level > def.maxLevel) " (MASTERY)" else " / ${def.maxLevel}",
                    color = c, style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(def.effectAt(level), color = Palette.Green, style = MaterialTheme.typography.bodyMedium)
        Text(def.description, color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall)
        if (showChanges) {
            Spacer(Modifier.height(10.dp))
            StatChanges(lines)
        }
    }
}

/** The before → after table. */
@Composable
fun StatChanges(lines: List<StatLine>, heading: String = "WHAT CHANGES") {
    Text(heading, color = Palette.Cyan, style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(4.dp))
    if (lines.isEmpty()) {
        Text("No lasting stat change (one-time effect).", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
        return
    }
    for (l in lines) {
        val col = if (l.better) Palette.Green else Palette.Red
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(l.label, color = Palette.TextSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text("${l.before} → ", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
            Text(l.after, color = col, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text(
                l.change, color = col, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End, modifier = Modifier.width(64.dp)
            )
        }
    }
}

/** Full-screen sheet for a held card / shop item with its main action. */
@Composable
fun UpgradeDetailSheet(
    def: UpgradeDef,
    level: Int,
    lines: List<StatLine>,
    actionLabel: String,
    actionEnabled: Boolean = true,
    onAction: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF2040810))
            .clickable(enabled = true, onClick = onBack)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            UpgradeDetails(def, level, lines, Modifier.clickable(enabled = true, onClick = {}))
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CyberButton(actionLabel, Modifier.weight(1f), accent = Palette.Green, primary = true, enabled = actionEnabled, onClick = onAction)
                CyberButton("BACK", Modifier.weight(1f), accent = Palette.TextSecondary, primary = true, onClick = onBack)
            }
        }
    }
}
