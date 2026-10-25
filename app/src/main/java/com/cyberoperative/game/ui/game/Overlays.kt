package com.cyberoperative.game.ui.game

import com.cyberoperative.game.i18n.tr
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import com.cyberoperative.game.data.Rarity
import kotlin.math.sin
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cyberoperative.game.data.StoreCatalog
import com.cyberoperative.game.engine.UpgradeOffer
import com.cyberoperative.game.data.UpgradeDef
import com.cyberoperative.game.data.Upgrades
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.mutableStateOf
import com.cyberoperative.game.ui.common.CyberButton
import com.cyberoperative.game.ui.theme.Palette

/** Game over (§54). */
@Composable
fun GameOverOverlay(
    result: RunResult,
    hud: HudSnapshot,
    onRevive: () -> Unit,
    onMenu: () -> Unit,
    onNew: () -> Unit
) {
    val title = remember { listOf(tr("OPERATIVE TERMINATED"), tr("CONNECTION LOST"), tr("SYSTEM COMPROMISED")).random() }
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
                StatRow(tr("LEVEL REACHED"), result.level.toString(), if (result.newBestLevel) tr("NEW RECORD") else null)
                StatRow(tr("SCORE"), "%,d".format(result.score), if (result.newBestScore) tr("NEW RECORD") else null)
                StatRow(tr("THREATS ELIMINATED"), result.kills.toString(), null)
                StatRow(tr("BOSSES ELIMINATED"), result.bosses.toString(), null)
                StatRow(tr("€ EARNED"), "€ ${result.euros}", null, Palette.Euro)
            }
            if (result.newAchievements.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(tr("ACHIEVEMENTS UNLOCKED"), color = Palette.Gold, style = MaterialTheme.typography.labelLarge)
                for (a in result.newAchievements) Text("★ $a", color = Palette.TextPrimary, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(20.dp))
            val reviveLabel: String? = when {
                hud.canRevive -> tr("FREE · {0} LEFT THIS RUN", hud.revivesLeft)
                hud.paidRevivesLeft <= 0 -> null
                hud.reviveTokens > 0 -> tr("USE 1 OF {0} REVIVES", hud.reviveTokens)
                hud.diamonds >= StoreCatalog.revivePacks.first().priceDiamonds -> "◇${StoreCatalog.revivePacks.first().priceDiamonds}"
                else -> null
            }
            if (reviveLabel != null) {
                CyberButton(tr("REVIVE"), Modifier.fillMaxWidth(), accent = Palette.Green, primary = true, subtitle = reviveLabel) { onRevive() }
                Spacer(Modifier.height(10.dp))
            } else if (!hud.canRevive && hud.paidRevivesLeft > 0) {
                Text(tr("No revives left — revive packs are in the STORE."), color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(10.dp))
            }
            CyberButton(tr("START NEW OPERATION"), Modifier.fillMaxWidth(), accent = Palette.Cyan) { onNew() }
            Spacer(Modifier.height(10.dp))
            CyberButton(tr("RETURN TO MAIN MENU"), Modifier.fillMaxWidth(), accent = Palette.TextSecondary) { onMenu() }
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


/**
 * Weapon slots full (owner, 2026-10-09): the equipped weapons in a grid. Tap
 * one to swap it for [newDef] (asks YES / NO first, with both full
 * descriptions and every stat that changes); hold one to see what it does and
 * what the swap would change. NO comes back to the grid.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun WeaponSwap(
    newDef: UpgradeDef,
    newLevel: Int,
    equipped: List<Pair<UpgradeDef, Int>>,
    preview: (String) -> List<com.cyberoperative.game.engine.StatLine>,
    onReplace: (String) -> Unit,
    onBack: () -> Unit,
    confirmVerb: String = "replace"
) {
    var target by remember { mutableStateOf<Pair<UpgradeDef, Int>?>(null) }
    var held by remember { mutableStateOf<Pair<UpgradeDef, Int>?>(null) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF0040810))
            .clickable(enabled = true, onClick = {})
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))
            val pick = target
            if (pick == null) {
                Text(tr("WEAPON SLOTS FULL"), color = Palette.Orange, style = MaterialTheme.typography.headlineMedium)
                Text(
                    tr("Choose a weapon to replace with {0}", newDef.name),
                    color = Palette.TextSecondary, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center
                )
                Text(tr("{0} to choose · hold for details", com.cyberoperative.game.ui.common.tapWord), color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(16.dp))
                for (row in equipped.chunked(4)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (w in row) {
                            WeaponTile(
                                w.first, w.second,
                                Modifier
                                    .weight(1f)
                                    .combinedClickable(
                                        onClickLabel = tr("Replace"), onLongClickLabel = tr("Details"),
                                        onLongClick = { held = w },
                                        onClick = { held = null; target = w }
                                    ),
                                selected = held?.first?.id == w.first.id
                            )
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(8.dp))
                val info = held
                if (info != null) {
                    UpgradeDetails(info.first, info.second, emptyList(), title = tr("EQUIPPED"), accent = Palette.Orange, showChanges = false)
                    Spacer(Modifier.height(8.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Palette.Surface, RoundedCornerShape(10.dp))
                            .border(1.dp, Palette.Divider, RoundedCornerShape(10.dp))
                            .padding(14.dp)
                    ) {
                        StatChanges(preview(info.first.id), heading = tr("IF {0} BECOMES {1}", info.first.name, newDef.name))
                    }
                } else {
                    UpgradeDetails(newDef, newLevel, emptyList(), title = tr("NEW WEAPON"), accent = Palette.Green, showChanges = false)
                }
                Spacer(Modifier.height(16.dp))
                CyberButton(tr("KEEP MY WEAPONS · BACK"), Modifier.fillMaxWidth(), accent = Palette.TextSecondary, onClick = onBack)
            } else {
                Text(tr("REPLACE WEAPON?"), color = Palette.Orange, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    WeaponTile(pick.first, pick.second, Modifier.width(110.dp), dim = true)
                    Text("  →  ", color = Palette.Cyan, style = MaterialTheme.typography.headlineMedium)
                    WeaponTile(newDef, newLevel, Modifier.width(110.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    if (confirmVerb == "replace") tr("You want to replace {0} with {1}?", pick.first.name, newDef.name) else "$confirmVerb ${pick.first.name} → ${newDef.name}?",
                    color = Palette.TextPrimary, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CyberButton(tr("YES"), Modifier.weight(1f), accent = Palette.Green, primary = true) { onReplace(pick.first.id) }
                    CyberButton(tr("NO"), Modifier.weight(1f), accent = Palette.Red, primary = true) { target = null }
                }
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Palette.Surface, RoundedCornerShape(10.dp))
                        .border(1.dp, Palette.Cyan.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    StatChanges(preview(pick.first.id), heading = tr("WHAT CHANGES"))
                }
                Spacer(Modifier.height(10.dp))
                UpgradeDetails(pick.first, pick.second, emptyList(), title = tr("BEFORE · REMOVED"), accent = Palette.Red, showChanges = false)
                Spacer(Modifier.height(10.dp))
                UpgradeDetails(newDef, newLevel, emptyList(), title = tr("AFTER · NEW"), accent = Palette.Green, showChanges = false)
            }
        }
    }
}

/** One weapon square: glyph in its rarity colour, name and level. */
@Composable
private fun WeaponTile(def: UpgradeDef, level: Int, modifier: Modifier, dim: Boolean = false, selected: Boolean = false) {
    val c = Color(def.rarity.color)
    Column(
        modifier
            .aspectRatio(0.8f)
            .background(Palette.Surface.copy(alpha = if (dim) 0.6f else 1f), RoundedCornerShape(8.dp))
            .border(if (selected) 3.dp else 2.dp, if (selected) Palette.Orange else c.copy(alpha = if (dim) 0.5f else 0.9f), RoundedCornerShape(8.dp))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(def.glyph, color = c, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(def.name, color = Palette.TextPrimary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
        Text(tr("LV {0}", level), color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
    }
}
