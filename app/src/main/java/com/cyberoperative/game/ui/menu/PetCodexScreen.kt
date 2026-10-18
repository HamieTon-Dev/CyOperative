package com.cyberoperative.game.ui.menu

import com.cyberoperative.game.platform.PlatformBackHandler as BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberoperative.game.data.GearRarity
import com.cyberoperative.game.data.PetDexEntry
import com.cyberoperative.game.data.Pets
import com.cyberoperative.game.save.PlayerProfile
import com.cyberoperative.game.ui.common.CyberButton
import com.cyberoperative.game.ui.common.PetArt
import com.cyberoperative.game.ui.common.ScreenScaffold
import com.cyberoperative.game.ui.common.rarityCard
import com.cyberoperative.game.ui.common.rarityColor
import com.cyberoperative.game.ui.theme.Palette

/*
 * PET CODEX (owner, 0.50.0: "like boss codex, player can see pets they have unlocked, click on them
 * see their potential stats and their associated egg"). Every pet in the game, grouped by the rarity
 * it's designed for (the 1,005 originals hatch from COMMON … LEGENDARY eggs). Pets you've hatched
 * show (animated ones move); the rest are dark silhouettes. Tap a hatched pet for its page.
 */

/** A codex section: its pets and the rarities they can hatch at. */
private class DexSection(val title: String, val lo: GearRarity, val hi: GearRarity, val arts: List<Int>)

private val sections: List<DexSection> by lazy {
    val newArts = (0 until Pets.NEW_COUNT).map { Pets.NEW_BASE + it }
    val tiers = listOf(
        GearRarity.GLITCHED_ENCRYPTED, GearRarity.ENCRYPTED, GearRarity.ABYSSAL, GearRarity.CELESTIAL,
        GearRarity.VOLTAIC, GearRarity.FORGED, GearRarity.GLITCHED
    )
    tiers.map { t -> DexSection(t.label, t, t, newArts.filter { Pets.designedTier(it) == t }) } +
        DexSection("ORIGINALS", GearRarity.COMMON, GearRarity.LEGENDARY, (0 until Pets.PET_COUNT).toList())
}

@Composable
fun PetCodexScreen(profile: PlayerProfile, onBack: () -> Unit) {
    val time = com.cyberoperative.game.ui.common.rememberSlowClock(24)
    val dex = remember(profile.petCodex, profile.pets) { com.cyberoperative.game.meta.Incubators.codex(profile) }
    var open by remember { mutableStateOf<Int?>(null) }
    val total = sections.sumOf { it.arts.size }
    Box(Modifier.fillMaxSize()) {
        ScreenScaffold("PET CODEX", onBack, trailing = {
            Text("${dex.size}/$total", color = Palette.Gold, style = MaterialTheme.typography.labelLarge)
        }) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(68.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Hatch a pet to add it to the codex. Discovered: ${dex.size} of $total.",
                        color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
                for (s in sections) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        val found = s.arts.count { it in dex }
                        Column(Modifier.padding(top = 8.dp)) {
                            Text(s.title, color = rarityColor(s.hi), style = MaterialTheme.typography.titleSmall, letterSpacing = 2.sp)
                            Text("$found / ${s.arts.size} discovered" + if (s.lo != s.hi) " · hatch from ${s.lo.label} to ${s.hi.label} eggs" else "",
                                color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    items(s.arts, key = { it }) { art ->
                        DexTile(art, dex[art], s.hi, time) { if (art in dex) open = art }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(16.dp)) }
            }
        }
        open?.let { art ->
            BackHandler { open = null }
            PetDexPage(art, dex.getValue(art), time) { open = null }
        }
    }
}

@Composable
private fun DexTile(art: Int, entry: PetDexEntry?, tier: GearRarity, time: Float, onClick: () -> Unit) {
    val known = entry != null
    val c = if (known) rarityColor(entry!!.best) else Palette.Divider
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(Palette.Surface.copy(alpha = 0.9f), RoundedCornerShape(6.dp))
            .border(1.dp, c.copy(alpha = if (known) 0.8f else 0.5f), RoundedCornerShape(6.dp))
            .clickable(enabled = known, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            if (known) Modifier.fillMaxSize().padding(4.dp)
            else Modifier
                .fillMaxSize()
                .padding(4.dp)
                // Not hatched yet: a flat dark silhouette (like the BOSS CODEX).
                .drawWithContent {
                    drawIntoCanvas { cv ->
                        cv.saveLayer(
                            androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height),
                            Paint().apply { colorFilter = ColorFilter.tint(Color(0xFF1A2232), BlendMode.SrcIn) }
                        )
                        drawContent()
                        cv.restore()
                    }
                }
        ) {
            with(PetArt) { drawPet(art, size.width / 2f, size.height * 0.95f, size.minDimension * 0.9f, time = if (known) time + art * 0.17f else 0f) }
        }
        if (!known) Text("?", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
    }
}

/** A hatched pet's page: it moving, its name, the rarities it comes in, its stats there, and its egg. */
@Composable
internal fun PetDexPage(art: Int, entry: PetDexEntry, time: Float, onClose: () -> Unit) {
    val designed = Pets.designedTier(art)
    val lo = designed ?: GearRarity.COMMON
    val hi = designed ?: GearRarity.LEGENDARY
    val c = rarityColor(entry.best)
    Box(
        Modifier.fillMaxSize().background(Color(0xE605080F)).clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .background(Palette.Surface, RoundedCornerShape(12.dp))
                .border(2.dp, c, RoundedCornerShape(12.dp))
                .clickable(enabled = false) {}
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Canvas(Modifier.size(150.dp)) {
                with(PetArt) { drawPet(art, size.width / 2f, size.height * 0.95f, size.minDimension * 0.92f, time = time) }
            }
            Text(Pets.petName(art), color = c, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Box(Modifier.rarityCard(hi, corner = 4.dp).border(1.dp, rarityColor(hi), RoundedCornerShape(4.dp)).padding(horizontal = 10.dp, vertical = 3.dp)) {
                Text(if (lo == hi) hi.label else "${lo.label} – ${hi.label}", color = rarityColor(hi), style = MaterialTheme.typography.labelLarge)
            }
            if (designed == null) Text("Best you've hatched: ${entry.best.label}", color = rarityColor(entry.best), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(10.dp))
            Text("POTENTIAL STATS", color = Palette.Cyan, style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                DexStat("DAMAGE", range(lo, hi) { "${(Pets.damageMul(it) * 100).toInt()}%" }, "of your payload")
                DexStat("FIRE RATE", range(lo, hi) { "%.2fs".format(Pets.fireInterval(it)) }, "between shots")
                DexStat("ABILITIES", abilityRange(lo, hi), "random at hatch")
            }
            DexStat("HATCH TIME", range(lo, hi) { hours(Pets.hatchMs(it)) }, "in an incubator")
            Spacer(Modifier.height(10.dp))
            Text("ITS EGG", color = Palette.Cyan, style = MaterialTheme.typography.labelLarge)
            com.cyberoperative.game.ui.common.AnimatedEgg(entry.egg, entry.best, time, Modifier.size(84.dp))
            Text(
                if (designed != null) "${Pets.eggName(com.cyberoperative.game.data.PetEgg(0, hi, art, art))} · only this egg hatches it"
                else "Any ${lo.label} – ${hi.label} egg can hatch it (this is the one yours came from)",
                color = Palette.TextSecondary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            CyberButton("CLOSE", Modifier.fillMaxWidth(), accent = c) { onClose() }
        }
    }
}

@Composable
private fun DexStat(label: String, value: String, sub: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
        Text(label, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
        Text(value, color = Palette.TextPrimary, style = MaterialTheme.typography.titleSmall)
        Text(sub, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
    }
}

private fun range(lo: GearRarity, hi: GearRarity, f: (GearRarity) -> String): String = if (lo == hi) f(lo) else "${f(lo)} – ${f(hi)}"

/** How many abilities a pet hatches with (Pets.abilitiesFor): 1 below EPIC, up to 2 below GLITCHED, up to 4 from GLITCHED. */
private fun abilityRange(lo: GearRarity, hi: GearRarity): String {
    fun max(r: GearRarity) = when { r < GearRarity.EPIC -> 1; r < GearRarity.GLITCHED -> 2; else -> Pets.MAX_ABILITIES }
    val a = 1; val b = max(hi)
    return if (a == b) "$a" else "$a – $b"
}

private fun hours(ms: Long): String {
    val m = ms / 60_000L
    return if (m >= 60) "${m / 60}h" + (if (m % 60 > 0) " ${m % 60}m" else "") else "${m}m"
}
