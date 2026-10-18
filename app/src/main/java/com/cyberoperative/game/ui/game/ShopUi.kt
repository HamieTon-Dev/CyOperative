package com.cyberoperative.game.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberoperative.game.engine.ShopItem
import com.cyberoperative.game.ui.theme.Palette
import kotlinx.coroutines.delay

private val TERMINAL_LINES = listOf(
    "> RECEIVING MESSAGE ...",
    "> SOURCE: UNKNOWN // ENCRYPTION: VALID",
    "> DECRYPTING ......... OK",
    "> UPGRADE SHOP AVAILABLE",
    "> SIDE GATE UNLOCKED"
)

/**
 * The shopkeeper's message (owner, 2026-10-08): typed out like the boot
 * terminal whenever a shop appears ([serial] changes), then fades away.
 * Never blocks input.
 */
@Composable
fun ShopMessage(serial: Int, right: Boolean, modifier: Modifier = Modifier, hidden: Boolean = false) {
    val lines = TERMINAL_LINES.dropLast(1) + (TERMINAL_LINES.last() + if (right) "  [ RIGHT WALL >> ]" else "  [ << LEFT WALL ]")
    var shown by remember { mutableIntStateOf(0) }
    var chars by remember { mutableIntStateOf(0) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(serial) {
        if (serial == 0) return@LaunchedEffect
        visible = true
        shown = 0
        chars = 0
        val total = lines.sumOf { it.length }
        while (chars < total) {
            chars += 2
            delay(18)
        }
        delay(3200)
        visible = false
    }
    if (!visible || hidden) return
    var left = chars
    Column(
        modifier
            .background(Color(0xEE020806), RoundedCornerShape(6.dp))
            .border(1.dp, Palette.Green.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        for ((i, line) in lines.withIndex()) {
            if (left <= 0) break
            val part = line.take(left)
            left -= line.length
            val highlight = i == 3
            Text(
                part + if (left < 0) "█" else "",
                color = if (highlight) Palette.Gold else Palette.Green,
                fontFamily = FontFamily.Monospace,
                fontSize = if (highlight) 15.sp else 12.sp
            )
        }
    }
}

/**
 * The shop window (owner, 2026-10-11: "so large, players cannot close it"): a compact square in
 * the middle of the screen, at most 90% of the width and 80% of the height (capped at 380dp), so it
 * fits phones in portrait and landscape and the PC window alike. The list scrolls inside it and
 * the X at the top right closes it.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ShopPanel(
    items: List<ShopItem>, euros: Int, onBuy: (Int) -> Unit, onClose: () -> Unit,
    modifier: Modifier = Modifier, onHold: (Int) -> Unit = {}, slotsFull: (Int) -> Boolean = { false },
    gear: List<com.cyberoperative.game.engine.GearShopItem> = emptyList(), onBuyGear: (Int) -> Unit = {},
    egg: com.cyberoperative.game.engine.EggShopItem? = null, onBuyEgg: () -> Unit = {}
) {
    val eggTime = com.cyberoperative.game.ui.common.rememberSlowClock(24)
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // 8 items now (4 buffs, 4 equipment): a little taller than wide where there's room.
        val side = minOf(maxWidth * 0.9f, maxHeight * 0.8f, 380.dp)
        val tall = minOf(maxHeight * 0.86f, side * 1.35f)
        Column(
            Modifier
                .size(side, tall)
                .background(Color(0xF0080A10), RoundedCornerShape(10.dp))
                .border(1.5.dp, Palette.Gold, RoundedCornerShape(10.dp))
                // Swallow taps so they don't reach the arena underneath.
                .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("X,.,.X  SHOP", color = Palette.Gold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1)
                com.cyberoperative.game.ui.common.FitNumber(euros.toLong(), Palette.Euro, MaterialTheme.typography.titleSmall, prefix = "€ ", maxFull = 9)
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(30.dp)
                        .background(Palette.Surface, RoundedCornerShape(6.dp))
                        .border(1.dp, Palette.TextSecondary, RoundedCornerShape(6.dp))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) { Text("✕", color = Palette.TextPrimary, style = MaterialTheme.typography.titleSmall) }
            }
            Text("${com.cyberoperative.game.ui.common.tapWord} to buy · hold for details", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if (items.isEmpty() && gear.isEmpty()) Text("Sold out. Come back another time.", color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall)
                if (items.isNotEmpty()) Text("BUFFS", color = Palette.Gold, style = MaterialTheme.typography.labelSmall)
                for ((i, it) in items.withIndex()) {
                    val col = Color(it.def.rarity.color)
                    val affordable = euros >= it.price
                    val shape = RoundedCornerShape(8.dp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(Palette.Surface, shape)
                            .border(if (it.sold) 1.dp else 2.dp, if (it.sold) Palette.Divider else col, shape)
                            .combinedClickable(enabled = !it.sold, onLongClick = { onHold(i) }, onLongClickLabel = "Details") { if (affordable) onBuy(i) }
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(32.dp)
                                .background(col.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
                                .border(1.dp, col.copy(alpha = 0.7f), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) { Text(it.def.glyph, color = col, fontSize = 8.sp, textAlign = TextAlign.Center, maxLines = 1) }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                it.def.rarity.label + (if (it.nextLevel > 1) " · LV ${it.nextLevel}" else "") + if (!it.sold && slotsFull(i)) " · SWAP" else "",
                                color = col, style = MaterialTheme.typography.labelSmall, maxLines = 1
                            )
                            Text(it.def.name, color = if (it.sold) Palette.TextMuted else Palette.TextPrimary, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                            Text(it.def.effectAt(it.nextLevel), color = Palette.Green, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when {
                                it.sold -> "SOLD"
                                affordable -> "€${it.price}"
                                else -> "€${com.cyberoperative.game.ui.common.formatAmount(it.price.toLong())}\nNEED €${com.cyberoperative.game.ui.common.formatAmount((it.price - euros).toLong())}"
                            },
                            color = when {
                                it.sold -> Palette.TextMuted
                                affordable -> Palette.Euro
                                else -> Palette.Red
                            },
                            style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End
                        )
                    }
                }
                // Equipment (owner, 0.32.0): PURPLE and up, bought into this run's finds (backpack, then vault).
                if (gear.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("EQUIPMENT", color = Palette.Gold, style = MaterialTheme.typography.labelSmall)
                }
                for ((gi, g) in gear.withIndex()) {
                    val col = Color(g.item.rarity.color)
                    val affordable = euros >= g.price
                    val shape = RoundedCornerShape(8.dp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(Palette.Surface, shape)
                            .border(if (g.sold) 1.dp else 2.dp, if (g.sold) Palette.Divider else col, shape)
                            .clickable(enabled = !g.sold) { if (affordable) onBuyGear(gi) }
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        com.cyberoperative.game.ui.common.GearIconView(g.item, Modifier.size(40.dp), dim = g.sold)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${g.item.rarity.label} · ${g.item.slot.label} · LV ${g.item.level}", color = col, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            Text(g.item.name, color = if (g.sold) Palette.TextMuted else Palette.TextPrimary, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                            Text("${g.item.mainLabel} ${g.item.mainValue}" + if (g.item.affixes.isNotEmpty()) " · ${g.item.affixes.size} bonuses" else "",
                                color = Palette.Green, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when {
                                g.sold -> "SOLD"
                                affordable -> "€${"%,d".format(g.price)}"
                                else -> "€${com.cyberoperative.game.ui.common.formatAmount(g.price.toLong())}\nNEED €${com.cyberoperative.game.ui.common.formatAmount((g.price - euros).toLong())}"
                            },
                            color = when {
                                g.sold -> Palette.TextMuted
                                affordable -> Palette.Euro
                                else -> Palette.Red
                            },
                            style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End
                        )
                    }
                }
                // The shop's egg (owner, 0.53.0): twice its inventory sell value, into this run's eggs.
                if (egg != null) {
                    val col = com.cyberoperative.game.ui.common.rarityColor(egg.egg.rarity)
                    val affordable = euros >= egg.price
                    val shape = RoundedCornerShape(8.dp)
                    Spacer(Modifier.height(6.dp))
                    Text("PET EGG", color = Palette.Gold, style = MaterialTheme.typography.labelSmall)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(Palette.Surface, shape)
                            .border(if (egg.sold) 1.dp else 2.dp, if (egg.sold) Palette.Divider else col, shape)
                            .clickable(enabled = !egg.sold) { if (affordable) onBuyEgg() }
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        com.cyberoperative.game.ui.common.AnimatedEgg(egg.egg.art, egg.egg.rarity, if (egg.sold) 0f else eggTime, Modifier.size(40.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(egg.egg.rarity.label + " EGG", color = col, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            Text(egg.egg.name, color = if (egg.sold) Palette.TextMuted else Palette.TextPrimary, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                            Text("Hatch it in PETS", color = Palette.TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when {
                                egg.sold -> "SOLD"
                                affordable -> "€${"%,d".format(egg.price)}"
                                else -> "€${com.cyberoperative.game.ui.common.formatAmount(egg.price.toLong())}\nNEED €${com.cyberoperative.game.ui.common.formatAmount((egg.price - euros).toLong())}"
                            },
                            color = when { egg.sold -> Palette.TextMuted; affordable -> Palette.Euro; else -> Palette.Red },
                            style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

/** At the counter with the shop closed: the button that opens it. */
@Composable
fun OpenShopButton(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        "X,.,.X  OPEN SHOP",
        color = Palette.Gold, style = MaterialTheme.typography.titleSmall,
        modifier = modifier
            .background(Color(0xE6080A10), RoundedCornerShape(8.dp))
            .border(1.5.dp, Palette.Gold, RoundedCornerShape(8.dp))
            .clickable { onOpen() }
            .padding(horizontal = 18.dp, vertical = 10.dp)
    )
}

/** Shown when the player walks into the top gate while a shop is on offer. */
@Composable
fun SkipShopDialog(onNo: () -> Unit, onYes: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Color(0xF0080A10), RoundedCornerShape(10.dp))
            .border(1.5.dp, Palette.Gold, RoundedCornerShape(10.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("SKIP THE UPGRADE SHOP?", color = Palette.Gold, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text("The shop gate closes once you move on.", color = Palette.TextSecondary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Row {
            DialogBox("NO", "TAKE ME TO THE SHOP", Palette.Green, onNo, Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            DialogBox("YES", "SKIP IT", Palette.Red, onYes, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DialogBox(label: String, sub: String, color: Color, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier
            .background(color.copy(alpha = 0.16f), shape)
            .border(2.dp, color, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("[ $label ]", color = color, style = MaterialTheme.typography.titleLarge)
        Text(sub, color = color.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
    }
}
