package com.cyberoperative.game.ui.menu

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberoperative.game.data.BossDef
import com.cyberoperative.game.data.BossExpansion
import com.cyberoperative.game.data.Bosses
import com.cyberoperative.game.data.ThreatTier
import com.cyberoperative.game.save.BossRecord
import com.cyberoperative.game.save.PlayerProfile
import com.cyberoperative.game.ui.common.ScreenScaffold
import com.cyberoperative.game.ui.game.BossBodyPreview
import com.cyberoperative.game.ui.game.formatFightTime
import com.cyberoperative.game.ui.theme.Palette

/**
 * BOSS CODEX (Stage F2): every boss in the game. Bosses you've met show their
 * animated body; the rest are black silhouettes until you meet them. Tap a known
 * boss for its dossier: phases, role, threat tier, abilities, bounty, when it
 * can appear and your record against it (met, defeated, fastest kill).
 */
@Composable
fun BossCodexScreen(profile: PlayerProfile, onBack: () -> Unit) {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val s = withFrameNanos { it }
        while (true) withFrameNanos { time = (it - s) / 1e9f }
    }
    var open by remember { mutableStateOf<BossDef?>(null) }
    val records = profile.bossRecords
    val defeated = Bosses.roster.count { (records[it.id]?.defeated ?: 0) > 0 }
    val sections = listOf(
        Triple("CLASSIC THREATS", "Any boss level · always the first boss", Bosses.classics),
        Triple("EXPANSION BOSSES", "From level 20", BossExpansion.all.filter { it.id !in Bosses.GAMMA_IDS }),
        Triple("PACK GAMMA", "Level ${Bosses.GAMMA_FROM}+ · the hardest", BossExpansion.all.filter { it.id in Bosses.GAMMA_IDS })
    )
    Box(Modifier.fillMaxSize()) {
        ScreenScaffold("BOSS CODEX", onBack, trailing = {
            Text("$defeated/${Bosses.roster.size}", color = Palette.Gold, style = MaterialTheme.typography.labelLarge)
        }) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(span = { GridItemSpan(2) }) {
                    Text(
                        "Meet a boss to unlock its dossier. Defeated: $defeated of ${Bosses.roster.size}.",
                        color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall
                    )
                }
                for ((title, sub, list) in sections) {
                    item(span = { GridItemSpan(2) }) {
                        Column(Modifier.padding(top = 8.dp)) {
                            Text(title, color = Palette.Cyan, style = MaterialTheme.typography.titleSmall, letterSpacing = 2.sp)
                            Text(sub, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    items(list, key = { it.id }) { b ->
                        BossCard(b, records[b.id], time) { if ((records[b.id]?.met ?: 0) > 0) open = b }
                    }
                }
                item(span = { GridItemSpan(2) }) { Spacer(Modifier.height(16.dp)) }
            }
        }
        open?.let { b ->
            BackHandler { open = null }
            BossDossierPage(b, records[b.id] ?: BossRecord(), time) { open = null }
        }
    }
}

@Composable
private fun BossCard(b: BossDef, rec: BossRecord?, time: Float, onClick: () -> Unit) {
    val known = (rec?.met ?: 0) > 0
    val beaten = (rec?.defeated ?: 0) > 0
    val col = Color(b.color)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.Surface.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
            .border(1.dp, if (known) col.copy(alpha = 0.7f) else Palette.Divider, RoundedCornerShape(8.dp))
            .clickable(enabled = known, onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.15f), contentAlignment = Alignment.Center) {
            BossBodyPreview(
                b, 0, if (known) time else 0f,
                if (known) Modifier.fillMaxSize()
                else Modifier
                    .fillMaxSize()
                    // Unmet: the body drawn as a flat dark silhouette (tinted layer keeps only its shape).
                    .drawWithContent {
                        drawIntoCanvas { c ->
                            c.saveLayer(
                                androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height),
                                Paint().apply { colorFilter = ColorFilter.tint(Color(0xFF1A2232), BlendMode.SrcIn) }
                            )
                            drawContent()
                            c.restore()
                        }
                    }
            )
            if (!known) Text("?", color = Palette.TextMuted.copy(alpha = 0.6f), fontSize = 34.sp, fontWeight = FontWeight.Bold)
            if (beaten) Text(
                "✓ ${rec?.defeated}", color = Palette.Green, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.TopEnd).background(Palette.Background.copy(alpha = 0.7f), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp)
            )
        }
        Text(
            if (known) b.name else "???", color = if (known) col else Palette.TextMuted,
            style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 1
        )
        Text(
            if (known) ThreatTier.label(b.tier) + " THREAT" else "NOT ENCOUNTERED",
            color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun BossDossierPage(b: BossDef, rec: BossRecord, time: Float, onClose: () -> Unit) {
    val col = Color(b.color)
    var phase by remember { mutableIntStateOf(0) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF0050810))
            .clickable(enabled = true, onClick = {})
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(40.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("< CODEX", color = Palette.Cyan, style = MaterialTheme.typography.titleMedium, modifier = Modifier.clickable(onClick = onClose).padding(8.dp))
                Text(b.tag, color = col, style = MaterialTheme.typography.titleMedium)
            }
            Text(b.name, color = col, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(b.title.uppercase(), color = Palette.TextSecondary, style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(b.role.label, Color(b.role.color))
                Chip(ThreatTier.label(b.tier) + " THREAT", if (b.tier >= 3) Palette.Red else Palette.Orange)
                Chip(if (b.id in Bosses.GAMMA_IDS) "LEVEL ${Bosses.GAMMA_FROM}+" else if (b in Bosses.classics) "ANY LEVEL" else "LEVEL 20+", Palette.Cyan)
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.2f)
                    .background(Palette.Surface.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                    .border(1.dp, col.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            ) {
                BossBodyPreview(b, phase, time, Modifier.fillMaxSize())
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (k in 0..2) {
                        val on = phase == k
                        Text(
                            "PHASE ${k + 1}", color = if (on) Palette.Background else col, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .background(if (on) col else Palette.Background.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .border(1.dp, col, RoundedCornerShape(4.dp))
                                .clickable { phase = k }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(b.codex, color = Palette.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Section("ABILITIES")
            for (a in b.abilityNames(6)) Text("▸ $a", color = Palette.TextPrimary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Section("YOUR RECORD")
            Kv("ENCOUNTERED", "${rec.met}×")
            Kv("DEFEATED", "${rec.defeated}×", if (rec.defeated > 0) Palette.Green else Palette.TextMuted)
            Kv("FASTEST KILL", formatFightTime(rec.bestSeconds), Palette.Gold)
            Kv("BASE BOUNTY", "€" + "%,d".format(b.euros), Palette.Euro)
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Text(
        text, color = color, style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
private fun Section(t: String) {
    Text(t, color = Palette.Cyan, style = MaterialTheme.typography.titleSmall, letterSpacing = 2.sp)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Kv(k: String, v: String, color: Color = Palette.TextPrimary) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, color = Palette.TextSecondary, style = MaterialTheme.typography.labelLarge)
        Text(v, color = color, style = MaterialTheme.typography.labelLarge)
    }
}
