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

/** Level-up screen (§53): gameplay paused, three stacked cards, tap one. */
@Composable
fun UpgradeOverlay(session: GameSession) {
    @Suppress("UNUSED_VARIABLE") val tick = session.hud
    val offer = session.engine.offer
    // Weapon swap (owner, 2026-10-09): picking a new weapon with all slots full
    // opens the grid of equipped weapons, then a YES / NO confirmation.
    var swapCard by remember(session.hud.offerSerial) { mutableStateOf<Int?>(null) }
    val swapIndex = swapCard
    val swapDef = swapIndex?.let { offer.getOrNull(it)?.def }
    if (swapIndex != null && swapDef != null) {
        WeaponSwap(
            newDef = swapDef,
            equipped = session.equippedWeapons(),
            onReplace = { old -> session.chooseUpgrade(swapIndex, old); swapCard = null },
            onBack = { swapCard = null }
        )
        return
    }
    // Drives the shimmer on GOLDEN and TITANIUM cards.
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time = (it - start) / 1e9f }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE6040810))
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
            Spacer(Modifier.height(24.dp))
            Text("SYSTEM UPGRADE", color = Palette.Green, style = MaterialTheme.typography.headlineMedium)
            Text("SELECT ONE MODULE", color = Palette.TextSecondary, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(18.dp))
            val hud = session.hud
            if (hud.waitingForPartner || (hud.coop && offer.isEmpty())) {
                Text(
                    "WAITING FOR ${hud.partnerName.ifEmpty { "YOUR PARTNER" }.uppercase()} TO PICK…",
                    color = Palette.Magenta, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
            }
            val weaponsNow = session.equippedWeapons().size
            Text(
                "WEAPON SLOTS $weaponsNow / ${Upgrades.MAX_WEAPONS}",
                color = if (weaponsNow >= Upgrades.MAX_WEAPONS) Palette.Orange else Palette.TextMuted,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.height(8.dp))
            offer.forEachIndexed { i, o ->
                val needsSlot = session.needsWeaponSlot(i)
                UpgradeCard(o, time, weapon = Upgrades.isWeapon(o.def), swap = needsSlot) {
                    if (needsSlot) swapCard = i else session.chooseUpgrade(i)
                }
                Spacer(Modifier.height(12.dp))
            }
            // Reward counter (owner, 2026-10-08): how many were earned, and how many are left.
            if (hud.rewardBatchTotal > 1) {
                RewardCounter(hud.rewardBatchTaken, hud.rewardBatchTotal)
                Spacer(Modifier.height(10.dp))
            }
            if (session.hud.rerolls > 0) {
                Spacer(Modifier.height(4.dp))
                CyberButton("REROLL (${session.hud.rerolls})", accent = Palette.Purple) { session.reroll() }
            }
        }
    }
}

@Composable
private fun RewardCounter(taken: Int, total: Int) {
    val left = total - taken
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until total) {
                val done = i < taken
                val current = i == taken
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (current) 16.dp else 12.dp)
                        .background(
                            when {
                                done -> Palette.Green
                                current -> Palette.Gold
                                else -> Palette.Surface
                            },
                            RoundedCornerShape(3.dp)
                        )
                        .border(1.dp, if (done) Palette.Green else Palette.Gold, RoundedCornerShape(3.dp))
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "REWARD ${taken + 1} OF $total",
            color = Palette.Gold, style = MaterialTheme.typography.titleSmall
        )
        Text(
            if (left <= 1) "Last pick of this drop" else "${left - 1} more to pick after this one",
            color = Palette.TextSecondary, style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun UpgradeCard(o: UpgradeOffer, time: Float, weapon: Boolean = false, swap: Boolean = false, onClick: () -> Unit) {
    val tier = o.def.rarity
    val high = tier.highTier
    // GOLDEN and TITANIUM shimmer so a lucky roll is impossible to miss.
    val base = Color(tier.color)
    val rarity = when (tier) {
        Rarity.TITANIUM -> lerp(Color(0xFFB9C7D4), Color(0xFFFFFFFF), 0.5f + 0.5f * sin(time * 3f))
        Rarity.LEGENDARY -> lerp(base, Color(0xFFFFF3B0), 0.5f + 0.5f * sin(time * 2.4f))
        else -> base
    }
    val shape = RoundedCornerShape(10.dp)
    val fill = when (tier) {
        Rarity.TITANIUM -> Brush.linearGradient(
            listOf(Color(0xFF1E2833), Color(0xFF3A4856), Color(0xFF1E2833)),
            start = Offset(600f * ((time * 0.35f) % 1f) - 300f, 0f),
            end = Offset(600f * ((time * 0.35f) % 1f) + 300f, 300f)
        )
        Rarity.LEGENDARY -> Brush.verticalGradient(listOf(Color(0xFF2A2208), Palette.Surface))
        Rarity.EPIC -> Brush.verticalGradient(listOf(Color(0xFF1F1233), Palette.Surface))
        else -> Brush.verticalGradient(listOf(Palette.Surface, Palette.Surface))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(fill, shape)
            .border(if (o.isEvolution || high >= 2) 3.dp else 2.dp, rarity, shape)
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
                val tag = when {
                    o.isEvolution -> "EVOLUTION · ${tier.label}"
                    high >= 2 -> "★ ${tier.label} ★"
                    else -> tier.label
                }
                Text(tag, color = rarity, style = MaterialTheme.typography.labelSmall)
                if (weapon) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (swap) "WEAPON · SWAP" else "WEAPON",
                        color = if (swap) Palette.Orange else Palette.Cyan, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .border(1.dp, (if (swap) Palette.Orange else Palette.Cyan).copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp)
                    )
                }
                if (!o.def.instant) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "LV ${o.nextLevel - 1} → ${o.nextLevel}" + if (o.mastery) " · MASTERY" else " / ${o.def.maxLevel}",
                        color = if (o.mastery) Palette.Gold else Palette.TextMuted, style = MaterialTheme.typography.labelSmall
                    )
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


/**
 * Weapon slots full (owner, 2026-10-09): the equipped weapons in a grid. Tap
 * one to swap it for [newDef] (asks YES / NO first); press and hold to see what
 * it does. NO comes back to the grid.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun WeaponSwap(
    newDef: UpgradeDef,
    equipped: List<Pair<UpgradeDef, Int>>,
    onReplace: (String) -> Unit,
    onBack: () -> Unit
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
                Text("WEAPON SLOTS FULL", color = Palette.Orange, style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Choose a weapon to replace with ${newDef.name}",
                    color = Palette.TextSecondary, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center
                )
                Text("Tap to choose · hold for details", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(16.dp))
                for (row in equipped.chunked(4)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (w in row) {
                            WeaponTile(
                                w.first, w.second,
                                Modifier
                                    .weight(1f)
                                    .combinedClickable(
                                        onClickLabel = "Replace", onLongClickLabel = "Details",
                                        onLongClick = { held = w },
                                        onClick = { held = null; target = w }
                                    )
                            )
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(8.dp))
                val info = held
                if (info != null) {
                    UpgradeTooltip(info.first, info.second)
                } else {
                    Text("NEW WEAPON", color = Palette.Green, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    UpgradeTooltip(newDef, 1)
                }
                Spacer(Modifier.height(16.dp))
                CyberButton("KEEP MY WEAPONS · BACK TO CARDS", Modifier.fillMaxWidth(), accent = Palette.TextSecondary, onClick = onBack)
            } else {
                Text("REPLACE WEAPON?", color = Palette.Orange, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    WeaponTile(pick.first, pick.second, Modifier.width(110.dp), dim = true)
                    Text("  →  ", color = Palette.Cyan, style = MaterialTheme.typography.headlineMedium)
                    WeaponTile(newDef, 1, Modifier.width(110.dp))
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "You want to replace ${pick.first.name} with ${newDef.name}?",
                    color = Palette.TextPrimary, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center
                )
                Text(
                    "${pick.first.name} (LV ${pick.second}) is removed for the rest of this run.",
                    color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CyberButton("YES", Modifier.weight(1f), accent = Palette.Green, primary = true) { onReplace(pick.first.id) }
                    CyberButton("NO", Modifier.weight(1f), accent = Palette.Red, primary = true) { target = null }
                }
            }
        }
    }
}

/** One weapon square: glyph in its rarity colour, name and level. */
@Composable
private fun WeaponTile(def: UpgradeDef, level: Int, modifier: Modifier, dim: Boolean = false) {
    val c = Color(def.rarity.color)
    Column(
        modifier
            .aspectRatio(0.8f)
            .background(Palette.Surface.copy(alpha = if (dim) 0.6f else 1f), RoundedCornerShape(8.dp))
            .border(2.dp, c.copy(alpha = if (dim) 0.5f else 0.9f), RoundedCornerShape(8.dp))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(def.glyph, color = c, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(def.name, color = Palette.TextPrimary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
        Text("LV $level", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
    }
}
