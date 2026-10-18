package com.cyberoperative.game.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberoperative.game.data.UpgradeDef
import com.cyberoperative.game.data.Upgrades
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.ui.theme.Palette
import com.cyberoperative.game.ui.theme.TerminalFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Buff chips under the HP bars (owner, 2026-10-08 HUD mockup): one compact
 * neon-outlined chip per owned mod, in its rarity colour, in a single
 * scrolling row. A "+N" chip at the end counts the chips scrolled out of view
 * (tap it to jump there). Tap a chip to see its name, effect and duration;
 * tap again (or wait) to close. The Plasma Beam chip doubles as its heat /
 * overheat-cooldown meter.
 */
@Composable
fun UpgradeBar(h: HudSnapshot, modifier: Modifier = Modifier, compact: Boolean = false) {
    if (h.owned.isEmpty()) return
    if (compact) { CompactUpgradeGrid(h, modifier); return }
    var selected by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val hiddenCount by remember(h.owned.size) {
        derivedStateOf {
            val info = list.layoutInfo
            val lastFull = info.visibleItemsInfo.lastOrNull { it.offset + it.size <= info.viewportEndOffset }?.index ?: -1
            (h.owned.size - 1 - lastFull).coerceAtLeast(0)
        }
    }
    // Details close by themselves so they never sit over the fight.
    LaunchedEffect(selected) { if (selected != null) { delay(4000); selected = null } }
    Box(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LazyRow(
                state = list,
                modifier = Modifier.weight(1f).height(CHIP_TOUCH),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(h.owned, key = { it.first }) { (id, level) ->
                    val def = Upgrades.all.firstOrNull { it.id == id }
                    if (def != null) {
                        BuffChip(
                            def, level,
                            selected = selected == id,
                            beamHeat = if (id == Upgrades.PLASMA_BEAM.id) h.beamHeat else 0f,
                            beamCooldown = if (id == Upgrades.PLASMA_BEAM.id) h.beamCooldown else 0f,
                            onTap = { selected = if (selected == id) null else id }
                        )
                    }
                }
            }
            if (hiddenCount > 0) {
                Box(
                    Modifier
                        .padding(start = 4.dp)
                        .height(CHIP_TOUCH)
                        .clickable(role = Role.Button, onClickLabel = "Show more buffs") {
                            scope.launch { list.animateScrollToItem(h.owned.size - 1) }
                        }
                        .semantics { contentDescription = "$hiddenCount more buffs" },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "+$hiddenCount", color = Palette.Cyan, fontSize = 11.sp, fontFamily = TerminalFont, fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .border(1.dp, Palette.Cyan.copy(alpha = 0.6f), CHIP_SHAPE)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }
        // Details float below the row, so nothing in the HUD moves.
        val selDef = selected?.let { id -> Upgrades.all.firstOrNull { it.id == id } }
        if (selDef != null) {
            Popup(offset = IntOffset(0, with(LocalDensity.current) { (CHIP_TOUCH + 4.dp).roundToPx() })) {
                Box(Modifier.clickable { selected = null }) {
                    UpgradeTooltip(selDef, h.owned.firstOrNull { it.first == selDef.id }?.second ?: 1, h.beamHeat, h.beamCooldown)
                }
            }
        }
    }
}

/**
 * Phone portrait HUD (owner, 0.47.2): chips at half size, half the gap, stacked in three rows that
 * scroll sideways; "+N" counts the ones out of view (tap it to jump there).
 */
@Composable
private fun CompactUpgradeGrid(h: HudSnapshot, modifier: Modifier) {
    var selected by remember { mutableStateOf<String?>(null) }
    val grid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val hiddenCount by remember(h.owned.size) {
        derivedStateOf {
            val info = grid.layoutInfo
            val lastFull = info.visibleItemsInfo.filter { it.offset.x + it.size.width <= info.viewportEndOffset }.maxOfOrNull { it.index } ?: -1
            (h.owned.size - 1 - lastFull).coerceAtLeast(0)
        }
    }
    LaunchedEffect(selected) { if (selected != null) { delay(4000); selected = null } }
    val gridH = SMALL_CHIP * 3 + SMALL_GAP * 2
    Box(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.lazy.grid.LazyHorizontalGrid(
                rows = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                state = grid,
                modifier = Modifier.weight(1f).height(gridH),
                horizontalArrangement = Arrangement.spacedBy(SMALL_GAP),
                verticalArrangement = Arrangement.spacedBy(SMALL_GAP)
            ) {
                items(h.owned.size, key = { h.owned[it].first }) { i ->
                    val (id, level) = h.owned[i]
                    val def = Upgrades.all.firstOrNull { it.id == id }
                    if (def != null) {
                        BuffChip(
                            def, level,
                            selected = selected == id,
                            beamHeat = if (id == Upgrades.PLASMA_BEAM.id) h.beamHeat else 0f,
                            beamCooldown = if (id == Upgrades.PLASMA_BEAM.id) h.beamCooldown else 0f,
                            onTap = { selected = if (selected == id) null else id },
                            small = true
                        )
                    }
                }
            }
            if (hiddenCount > 0) {
                Text(
                    "+$hiddenCount", color = Palette.Cyan, fontSize = 9.sp, fontFamily = TerminalFont, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(start = 3.dp)
                        .clickable(role = Role.Button, onClickLabel = "Show more buffs") {
                            scope.launch { grid.animateScrollToItem(h.owned.size - 1) }
                        }
                        .semantics { contentDescription = "$hiddenCount more buffs" }
                        .background(Palette.Background.copy(alpha = 0.85f), RoundedCornerShape(3.dp))
                        .border(1.dp, Palette.Cyan.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
        val selDef = selected?.let { id -> Upgrades.all.firstOrNull { it.id == id } }
        if (selDef != null) {
            Popup(offset = IntOffset(0, with(LocalDensity.current) { (gridH + 4.dp).roundToPx() })) {
                Box(Modifier.clickable { selected = null }) {
                    UpgradeTooltip(selDef, h.owned.firstOrNull { it.first == selDef.id }?.second ?: 1, h.beamHeat, h.beamCooldown)
                }
            }
        }
    }
}

/** Compact chips: half the 24dp chip, half the 4dp gap. */
private val SMALL_CHIP = 12.dp
private val SMALL_GAP = 2.dp

/** Visible chip height inside a taller touch row. */
private val CHIP_TOUCH = 36.dp
private val CHIP_SHAPE = RoundedCornerShape(5.dp)

@Composable
private fun BuffChip(
    def: UpgradeDef, level: Int, selected: Boolean,
    beamHeat: Float, beamCooldown: Float, onTap: () -> Unit, small: Boolean = false
) {
    val c = Color(def.rarity.color)
    // [small]: the compact HUD's half-size chip (no taller touch row: the three rows are the target).
    val k = if (small) 0.5f else 1f
    val shape = if (small) RoundedCornerShape(3.dp) else CHIP_SHAPE
    Box(
        Modifier
            .height(if (small) SMALL_CHIP else CHIP_TOUCH)
            .widthIn(min = 36.dp * k)
            .clickable(role = Role.Button, onClickLabel = "Details", onClick = onTap)
            .semantics { contentDescription = "${def.name}, level $level" },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .height(24.dp * k)
                .drawBehind {
                    // Soft neon glow around the outline.
                    val g = 2.dp.toPx() * k
                    drawRoundRect(
                        c.copy(alpha = if (selected) 0.35f else 0.16f),
                        topLeft = androidx.compose.ui.geometry.Offset(-g, -g),
                        size = androidx.compose.ui.geometry.Size(size.width + 2 * g, size.height + 2 * g),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx() * k)
                    )
                }
                .background(Palette.Background.copy(alpha = 0.9f), shape)
                .drawBehind {
                    // Beam: heat fills from the bottom; during overheat the chip shows a countdown.
                    val f = when {
                        beamCooldown > 0f -> (beamCooldown / GameEngine.BEAM_COOLDOWN).coerceIn(0f, 1f)
                        else -> beamHeat.coerceIn(0f, 1f)
                    }
                    if (f > 0f) {
                        val fill = if (beamCooldown > 0f) Palette.Red.copy(alpha = 0.4f) else Palette.Gold.copy(alpha = 0.28f)
                        drawRoundRect(
                            fill,
                            topLeft = androidx.compose.ui.geometry.Offset(0f, size.height * (1f - f)),
                            size = androidx.compose.ui.geometry.Size(size.width, size.height * f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx())
                        )
                    }
                }
                .border(if (small) 0.75.dp else 1.dp, c.copy(alpha = if (selected) 1f else 0.85f), shape),
            contentAlignment = Alignment.Center
        ) {
            Row(Modifier.padding(horizontal = 6.dp * k), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (beamCooldown > 0f) "%.0f".format(kotlin.math.ceil(beamCooldown)) else def.glyph,
                    color = if (beamCooldown > 0f) Palette.Red else c,
                    fontSize = if (small) 6.5.sp else 10.sp, fontFamily = TerminalFont, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Clip, textAlign = TextAlign.Center,
                    lineHeight = if (small) 7.sp else androidx.compose.ui.unit.TextUnit.Unspecified
                )
                if (level > 1) {
                    Text(
                        if (level >= 1000) "${level / 1000}k" else "$level",
                        color = Palette.TextPrimary, fontSize = if (small) 5.5.sp else 9.sp, fontFamily = TerminalFont,
                        lineHeight = if (small) 6.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
                        modifier = Modifier.padding(start = 3.dp * k)
                    )
                }
            }
        }
    }
}

@Composable
internal fun UpgradeTooltip(def: UpgradeDef, level: Int, beamHeat: Float = 0f, beamCooldown: Float = 0f) {
    val c = Color(def.rarity.color)
    Column(
        Modifier
            .widthIn(max = 300.dp)
            .padding(horizontal = 16.dp)
            .background(Palette.Surface, RoundedCornerShape(8.dp))
            .border(1.dp, c, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(def.name, color = Palette.TextPrimary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(8.dp))
            Text(def.rarity.label, color = c, style = MaterialTheme.typography.labelSmall)
        }
        Text(
            if (level > def.maxLevel) "LEVEL $level · MASTERY ${level - def.maxLevel}" else "LEVEL $level / ${def.maxLevel} (then mastery to ${UpgradeDef.MAX_LEVEL})",
            color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall
        )
        Text(def.effectAt(level), color = Palette.Green, style = MaterialTheme.typography.bodySmall)
        Text(def.description, color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall)
        // Mods last the whole run; only the beam has a timer of its own.
        val duration = when {
            def.id == Upgrades.PLASMA_BEAM.id && beamCooldown > 0f -> "DURATION: OVERHEATED · COOLING ${"%.0f".format(kotlin.math.ceil(beamCooldown))}s"
            def.id == Upgrades.PLASMA_BEAM.id -> "DURATION: ACTIVE ALL RUN · HEAT ${(beamHeat * 100).toInt()}%"
            else -> "DURATION: ACTIVE ALL RUN"
        }
        Text(duration, color = Palette.Cyan, style = MaterialTheme.typography.labelSmall)
    }
}
