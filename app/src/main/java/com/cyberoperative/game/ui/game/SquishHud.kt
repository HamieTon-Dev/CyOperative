package com.cyberoperative.game.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberoperative.game.engine.LevelKind
import com.cyberoperative.game.ui.theme.Palette

/*
 * UI SQUISH (owner, 0.48.0: "make our bars and icons super compact … so the screen isn't covered
 * with the top bar"). Phones, portrait and landscape: a small card in the top-left corner, with
 * the clock and pause in the top-right, and the rest of the screen left to the fight.
 *
 *   W3·R7  ♥ ▬▬▬▬ 3374
 *   ☠ ▬▬▬ 74/75   OP 37 ▬▬
 *   [+ 34]                      ← tap: €, score, the boss's name and every buff (tap a buff to read it)
 */

private val CardBg = Color(0xCC05080F)
private val ThreatPink = Color(0xFFFF2D6F)

/**
 * [onBaseHeight] reports only the folded card's height: the expanded panel floats over the arena,
 * so opening it never moves anything.
 */
@Composable
fun SquishHud(h: HudSnapshot, paused: Boolean, onPause: () -> Unit, onBaseHeight: (Int) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.displayCutout.union(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                    .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            )
            .padding(6.dp)
    ) {
        Column(
            Modifier
                .widthIn(max = 280.dp)
                .background(CardBg, RoundedCornerShape(6.dp))
                .border(1.dp, Palette.Cyan.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            // Bottom of the folded card on screen (+ its 4dp padding): what sits under the HUD starts there.
            val pad = with(androidx.compose.ui.platform.LocalDensity.current) { 4.dp.roundToPx() }
            Column(Modifier.onGloballyPositioned { onBaseHeight(it.positionInRoot().y.toInt() + it.size.height + pad) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(shortRoom(h.roomLabel), color = levelColor(h), style = mono(10.sp, FontWeight.Bold), maxLines = 1, softWrap = false)
                    Spacer(Modifier.width(6.dp))
                    val f = h.hp / h.maxHp.coerceAtLeast(1).toFloat()
                    MiniStat(
                        { HeartIcon(Palette.healthColor(f)) }, f, Palette.healthColor(f), 64.dp,
                        com.cyberoperative.game.ui.common.formatAmount(h.hp.toLong()), "Core health ${h.hp} of ${h.maxHp}"
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Bosses get their own thin bar between ■INV and MAP ([SquishBossBar]).
                    if (h.bossName == null && h.kind != LevelKind.SHOP && h.timedSeconds < 0 && h.levelThreats > 0) {
                        MiniStat(
                            { ThreatIcon() }, h.levelKills / h.levelThreats.toFloat(), ThreatPink, 40.dp,
                            "${h.levelKills}/${h.levelThreats}", "Threats cleared ${h.levelKills} of ${h.levelThreats}"
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(h.opLabel.replace("OP Lv ", "OP"), color = Color.White, style = mono(9.sp, FontWeight.Bold), maxLines = 1, softWrap = false)
                    Spacer(Modifier.width(3.dp))
                    MiniBar(h.opFraction, Color.White, 32.dp)
                }
                if (h.coop && !h.partnerGone) {
                    Spacer(Modifier.height(2.dp))
                    val pf = h.partnerHp / h.partnerMaxHp.coerceAtLeast(1).toFloat()
                    MiniStat(
                        { HeartIcon(Palette.Magenta) }, if (h.partnerDowned) 0f else pf, if (h.partnerDowned) Palette.Red else Palette.Magenta, 48.dp,
                        if (h.partnerDowned) "DOWN" else h.partnerName.ifEmpty { "P2" }.uppercase().take(8), "Partner health"
                    )
                }
                if (h.bossName == null && h.eventName != null) {
                    Text(
                        h.eventName + if (h.timedSeconds >= 0) " · ${h.timedSeconds}s" else "",
                        color = Color(h.eventAccent), style = mono(9.sp, FontWeight.Bold), maxLines = 1, softWrap = false,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Spacer(Modifier.height(3.dp))
                ExpandChip(open, h.owned.size) { open = !open }
            }
            if (open) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.cyberoperative.game.ui.common.FitNumber(h.euros.toLong(), Palette.Euro, mono(10.sp, FontWeight.Medium), prefix = "€ ")
                    Spacer(Modifier.width(8.dp))
                    com.cyberoperative.game.ui.common.FitNumber(h.score, Palette.TextPrimary, mono(10.sp, FontWeight.Medium), prefix = "SCORE ")
                }
                if (h.owned.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    UpgradeBar(h, Modifier.fillMaxWidth(), compact = true)
                }
            }
        }
        Row(Modifier.align(Alignment.TopEnd), verticalAlignment = Alignment.CenterVertically) {
            HudClock(Modifier.background(CardBg, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp))
            PauseButton(paused, onPause, compact = true)
        }
    }
}

/**
 * The boss bar (owner, 0.48.0: "thin boss bar, thin name above, color is based on boss, clean,
 * letters have dark background"): sits between ■INV and MAP, under the corner card.
 */
@Composable
fun SquishBossBar(h: HudSnapshot, modifier: Modifier = Modifier) {
    val name = h.bossName ?: return
    val bc = Color(h.bossColor).takeIf { h.bossColor != 0L } ?: Palette.Red
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = "$name ${h.bossPercent} percent" }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                name.uppercase(), color = bc, style = mono(10.sp, FontWeight.Bold), maxLines = 1, softWrap = false,
                modifier = Modifier.weight(1f, fill = false).background(CardBg, RoundedCornerShape(3.dp)).padding(horizontal = 4.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "${h.bossPercent}%", color = Palette.TextPrimary, style = mono(9.sp, FontWeight.Medium), maxLines = 1,
                modifier = Modifier.background(CardBg, RoundedCornerShape(3.dp)).padding(horizontal = 3.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
        Canvas(Modifier.fillMaxWidth().height(4.dp)) {
            val r = CornerRadius(2.dp.toPx())
            drawRoundRect(Color(0xCC05080F), cornerRadius = r)
            val fw = size.width * (h.bossPercent / 100f).coerceIn(0f, 1f)
            if (fw > 0f) {
                drawRoundRect(bc.copy(alpha = 0.3f), Offset(0f, -1.5.dp.toPx()), Size(fw, size.height + 3.dp.toPx()), r)
                drawRoundRect(bc, size = Size(fw, size.height), cornerRadius = r)
            }
        }
    }
}

/** "WORLD 3 · ROOM 7" → "W3·R7" (and "WORLD 1 · BOSS" → "W1·BOSS"). */
internal fun shortRoom(label: String): String =
    label.replace("WORLD ", "W").replace("ROOM ", "R").replace("LEVEL ", "L").replace(" · ", "·")

@Composable
private fun MiniStat(icon: @Composable () -> Unit, fraction: Float, color: Color, barWidth: Dp, value: String, description: String) {
    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = description }, verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(3.dp))
        MiniBar(fraction, color, barWidth)
        Spacer(Modifier.width(3.dp))
        Text(value, color = Palette.TextPrimary, style = mono(9.sp, FontWeight.Medium), maxLines = 1, softWrap = false)
    }
}

/** A short, segmented bar like the reference's (a dark track, notches every 20%). */
@Composable
private fun MiniBar(fraction: Float, color: Color, width: Dp) {
    Canvas(Modifier.size(width, 6.dp)) {
        val r = CornerRadius(2.dp.toPx())
        drawRoundRect(Color(0xFF1A2333), cornerRadius = r)
        val fw = size.width * fraction.coerceIn(0f, 1f)
        if (fw > 0f) drawRoundRect(color, size = Size(fw, size.height), cornerRadius = r)
        for (k in 1 until 5) {
            val x = size.width * k / 5f
            drawLine(Color(0xFF05080F).copy(alpha = 0.7f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
        }
    }
}

@Composable
private fun HeartIcon(c: Color) {
    Canvas(Modifier.size(10.dp)) {
        val w = size.width; val hgt = size.height
        val p = Path().apply {
            moveTo(w / 2f, hgt * 0.92f)
            cubicTo(w * -0.1f, hgt * 0.5f, w * 0.1f, hgt * -0.05f, w / 2f, hgt * 0.25f)
            cubicTo(w * 0.9f, hgt * -0.05f, w * 1.1f, hgt * 0.5f, w / 2f, hgt * 0.92f)
            close()
        }
        drawPath(p, c)
    }
}

/** The pink threat: a small triangle with two eyes, like the field's basic threats. */
@Composable
private fun ThreatIcon() {
    Canvas(Modifier.size(10.dp)) {
        val w = size.width; val hgt = size.height
        val p = Path().apply { moveTo(w / 2f, 0f); lineTo(w, hgt); lineTo(0f, hgt); close() }
        drawPath(p, ThreatPink)
        drawCircle(Color(0xFF05080F), w * 0.09f, Offset(w * 0.38f, hgt * 0.68f))
        drawCircle(Color(0xFF05080F), w * 0.09f, Offset(w * 0.62f, hgt * 0.68f))
    }
}

@Suppress("unused") // kept for a compact boss marker
@Composable
private fun SkullIcon(c: Color) {
    Canvas(Modifier.size(10.dp)) {
        val w = size.width; val hgt = size.height
        drawCircle(c, w * 0.42f, Offset(w / 2f, hgt * 0.42f))
        drawRect(c, Offset(w * 0.28f, hgt * 0.6f), Size(w * 0.44f, hgt * 0.36f))
        drawCircle(Color(0xFF05080F), w * 0.12f, Offset(w * 0.34f, hgt * 0.42f))
        drawCircle(Color(0xFF05080F), w * 0.12f, Offset(w * 0.66f, hgt * 0.42f))
    }
}

/** "+ 34": opens the panel with €, score and the buffs; "−" folds it again. */
@Composable
private fun ExpandChip(open: Boolean, buffs: Int, onToggle: () -> Unit) {
    Box(
        Modifier
            .height(18.dp)
            .widthIn(min = 30.dp)
            .border(1.dp, Palette.Cyan.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            .clickable(role = Role.Button, onClickLabel = if (open) "Hide details" else "Show buffs, euros and score", onClick = onToggle)
            .semantics { contentDescription = if (open) "Hide HUD details" else "Show HUD details" }
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            (if (open) "−" else "+") + if (buffs > 0) " $buffs" else "",
            color = Palette.Cyan, style = mono(10.sp, FontWeight.Bold), maxLines = 1
        )
    }
}
