package com.cyberoperative.game.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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

/** Shown while the operative stands at the counter: buy GOLDEN / TITANIUM mods with run €. */
@Composable
fun ShopPanel(items: List<ShopItem>, euros: Int, onBuy: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Color(0xF0080A10), RoundedCornerShape(10.dp))
            .border(1.5.dp, Palette.Gold, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("X,.,.X  UPGRADE SHOP", color = Palette.Gold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("€ $euros", color = Palette.Euro, style = MaterialTheme.typography.titleMedium)
        }
        Text("Tap a mod to buy it with this run's €.", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(8.dp))
        if (items.isEmpty()) Text("Sold out. Come back another time.", color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall)
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
                    .clickable(enabled = !it.sold && affordable) { onBuy(i) }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .background(col.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
                        .border(1.dp, col.copy(alpha = 0.7f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) { Text(it.def.glyph, color = col, fontSize = 9.sp, textAlign = TextAlign.Center, maxLines = 1) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(it.def.rarity.label + (if (it.nextLevel > 1) " · LV ${it.nextLevel}" else ""), color = col, style = MaterialTheme.typography.labelSmall)
                    Text(it.def.name, color = if (it.sold) Palette.TextMuted else Palette.TextPrimary, style = MaterialTheme.typography.titleSmall)
                    Text(it.def.effectAt(it.nextLevel), color = Palette.Green, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        it.sold -> "SOLD"
                        affordable -> "€${it.price}"
                        else -> "€${it.price}\nNEED €${it.price - euros}"
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
        Spacer(Modifier.height(4.dp))
        Text("Exit through the gate at the top when you're done.", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
    }
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
