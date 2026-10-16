package com.cyberoperative.game.ui.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cyberoperative.game.engine.GameMode
import com.cyberoperative.game.engine.LevelKind
import com.cyberoperative.game.ui.theme.Palette
import com.cyberoperative.game.ui.theme.TerminalFont
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fullscreen gameplay HUD (owner, 2026-10-08 mockup): one compact header row
 * (level · score · € | clock · pause), two thin bars (CORE HP, THREATS), a
 * row of neon buff chips, and a pull handle that folds the bar down to a
 * small diamond + clock pill.
 */

/** Near-black navy behind the HUD; fades out toward the battlefield. */
private val HudNavy = Color(0xFF05080F)
private val HudHairline = Palette.Cyan.copy(alpha = 0.28f)
private val ThreatPink = Color(0xFFFF2D6F)

internal fun mono(size: TextUnit, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = TerminalFont, fontSize = size, fontWeight = weight, letterSpacing = 0.4.sp)

/**
 * True while the notification shade (or another system window) covers the
 * game. Only counts once the window has actually had focus, so previews and
 * first frames never hide the HUD.
 */
@Composable
private fun shadeOpen(): Boolean {
    val focused = LocalWindowInfo.current.isWindowFocused
    var hadFocus by remember { mutableStateOf(false) }
    if (focused) hadFocus = true
    return hadFocus && !focused
}

/** Insets the HUD keeps clear of: cutouts always, side system bars (landscape / 3-button nav). */
private val HudInsets: WindowInsets
    @Composable get() = WindowInsets.displayCutout
        .union(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

@Composable
fun GameHud(
    h: HudSnapshot,
    paused: Boolean,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    onPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hiddenByShade = shadeOpen()
    val alpha by animateFloatAsState(if (hiddenByShade) 0f else 1f, label = "hudShade")
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
.background(Brush.verticalGradient(listOf(HudNavy.copy(alpha = 0.94f), HudNavy.copy(alpha = 0.78f))))
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(HudHairline, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            .windowInsetsPadding(HudInsets)
            .padding(top = 4.dp)
    ) {
        if (collapsed) {
            CollapsedRow(h, paused, onPause)
        } else {
            HeaderRow(h, paused, onPause)
            Column(Modifier.padding(horizontal = 12.dp)) {
                HpRow(h)
                XpRow(h)
                if (h.coop && !h.partnerGone) PartnerRow(h)
                ProgressRow(h)
                if (h.owned.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    UpgradeBar(h, Modifier.fillMaxWidth())
                }
                val boss = h.bossName
                if (boss != null) {
                    Spacer(Modifier.height(4.dp))
                    BossHealthBar(h)
                } else if (h.eventName != null) {
                    Text(
                        h.eventName + if (h.timedSeconds >= 0) "   SURVIVE ${h.timedSeconds}s" else "",
                        color = Color(h.eventAccent), style = mono(11.sp, FontWeight.Bold),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
        PullHandle(collapsed, onToggleCollapsed, Modifier.align(Alignment.CenterHorizontally))
    }
}

private fun levelColor(h: HudSnapshot): Color = when (h.kind) {
    LevelKind.BOSS -> Palette.Red
    LevelKind.EVENT -> Color(h.eventAccent)
    LevelKind.NORMAL -> Palette.Cyan
    LevelKind.SHOP -> Palette.Gold
}

@Composable
private fun HeaderRow(h: HudSnapshot, paused: Boolean, onPause: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp)) {
        // Narrow phones get a slightly smaller type so the row never wraps.
        val tight = maxWidth < 360.dp
        val big = if (tight) 13.sp else 15.sp
        val small = if (tight) 11.sp else 12.sp
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    h.roomLabel,
                    color = levelColor(h), style = mono(big, FontWeight.Bold), maxLines = 1, softWrap = false
                )
                Divider()
                com.cyberoperative.game.ui.common.FitNumber(h.score.toLong(), Palette.TextPrimary, mono(small, FontWeight.Medium), Modifier.weight(1f, fill = false), prefix = "SCORE ")
                Divider()
                com.cyberoperative.game.ui.common.FitNumber(h.euros.toLong(), Palette.Euro, mono(small, FontWeight.Medium), Modifier.weight(1f, fill = false), prefix = "€ ")
            }
            HudClock(Modifier.padding(start = 6.dp))
            PauseButton(paused, onPause)
        }
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .padding(horizontal = 7.dp)
            .width(1.dp)
            .height(14.dp)
            .background(Palette.Divider)
    )
}

@Composable
private fun CollapsedRow(h: HudSnapshot, paused: Boolean, onPause: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The diamond doubles as a quiet core-health light while the bar is folded away.
        val f = h.hp / h.maxHp.coerceAtLeast(1).toFloat()
        val c = Palette.healthColor(f)
        Canvas(Modifier.size(12.dp).semantics { contentDescription = "Core health ${h.hp} of ${h.maxHp}" }) {
            val p = Path().apply {
                moveTo(size.width / 2f, 0f); lineTo(size.width, size.height / 2f)
                lineTo(size.width / 2f, size.height); lineTo(0f, size.height / 2f); close()
            }
            drawPath(p, c.copy(alpha = 0.25f))
            drawPath(p, c, style = Stroke(1.5.dp.toPx()))
        }
        HudClock(Modifier.padding(start = 8.dp))
        Spacer(Modifier.weight(1f))
        PauseButton(paused, onPause)
    }
}

/**
 * Device clock (12/24 h per the system setting). Re-formats once a minute,
 * on the minute, and when the app comes back, so the HUD never recomposes
 * per second for it.
 */
@Composable
private fun HudClock(modifier: Modifier = Modifier) {
    val is24 = com.cyberoperative.game.platform.rememberIs24Hour()
    var text by remember { mutableStateOf(formatClock(is24)) }
    LaunchedEffect(is24) {
        while (true) {
            val now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L + 50L)
            text = formatClock(is24)
        }
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, is24) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) text = formatClock(is24) }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    Text(
        text, color = Palette.TextSecondary, style = mono(12.sp), maxLines = 1, softWrap = false,
        modifier = modifier.semantics { contentDescription = "Time $text" }
    )
}

private fun formatClock(is24: Boolean): String {
    return SimpleDateFormat(if (is24) "HH:mm" else "h:mm", Locale.getDefault()).format(Date())
}

@Composable
private fun PauseButton(paused: Boolean, onPause: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clickable(role = Role.Button, onClickLabel = if (paused) "Resume" else "Pause", onClick = onPause)
            .semantics { contentDescription = if (paused) "Resume" else "Pause" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(30.dp)) {
            val r = CornerRadius(5.dp.toPx())
            drawRoundRect(Palette.Cyan.copy(alpha = 0.10f), cornerRadius = r)
            drawRoundRect(Palette.Cyan.copy(alpha = 0.75f), cornerRadius = r, style = Stroke(1.dp.toPx()))
            val cx = size.width / 2f
            val cy = size.height / 2f
            val s = size.minDimension
            if (paused) {
                val p = Path().apply {
                    moveTo(cx - s * 0.14f, cy - s * 0.2f); lineTo(cx + s * 0.2f, cy); lineTo(cx - s * 0.14f, cy + s * 0.2f); close()
                }
                drawPath(p, Palette.Cyan)
            } else {
                val w = 2.5.dp.toPx()
                for (dx in floatArrayOf(-s * 0.1f, s * 0.1f)) {
                    drawLine(Palette.Cyan, Offset(cx + dx, cy - s * 0.18f), Offset(cx + dx, cy + s * 0.18f), w, StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
private fun HpRow(h: HudSnapshot, label: String = "CORE HP", labelWidth: androidx.compose.ui.unit.Dp = 62.dp) {
    val f = h.hp / h.maxHp.coerceAtLeast(1).toFloat()
    StatRow(
        labelWidth = labelWidth,
        label = label,
        labelColor = Palette.Green,
        fraction = f,
        color = Palette.healthColor(f),
        overlay = 0f,
        value = "${h.hp}/${h.maxHp}",
        extra = null,
        description = "Core health ${h.hp} of ${h.maxHp}"
    )
}

/**
 * OP level and a white XP bar filling toward the next level (owner, 2026-10-13:
 * "OP Lv XX ====" with the bar to the right of the label). Past OP Lv 60 it fills toward the next Overclock level.
 */
@Composable
private fun XpRow(h: HudSnapshot, labelWidth: androidx.compose.ui.unit.Dp = 62.dp) {
    StatRow(
        labelWidth = labelWidth,
        label = h.opLabel,
        labelColor = Color.White,
        fraction = h.opFraction,
        color = Color.White,
        overlay = 0f,
        value = "${(h.opFraction * 100f).toInt()}%",
        extra = null,
        description = "${h.opLabel}, ${(h.opFraction * 100f).toInt()} percent to the next level"
    )
}

/** Co-op partner's HP in magenta, matching their ring on the field. */
@Composable
private fun PartnerRow(h: HudSnapshot) {
    val name = h.partnerName.ifEmpty { "PARTNER" }.uppercase().take(10)
    StatRow(
        label = name, labelColor = Palette.Magenta,
        fraction = h.partnerHp / h.partnerMaxHp.coerceAtLeast(1).toFloat(),
        color = if (h.partnerDowned) Palette.Red else Palette.Magenta, overlay = 0f,
        value = if (h.partnerDowned) "DOWN" else "${h.partnerHp}/${h.partnerMaxHp}", extra = null,
        description = "Partner $name " + if (h.partnerDowned) "is down" else "health ${h.partnerHp} of ${h.partnerMaxHp}"
    )
}

@Composable
private fun ProgressRow(h: HudSnapshot) {
    if (h.kind != LevelKind.BOSS && h.kind != LevelKind.SHOP && h.timedSeconds < 0) {
        StatRow(
            label = "THREATS", labelColor = ThreatPink,
            fraction = h.levelKills / h.levelThreats.coerceAtLeast(1).toFloat(),
            color = ThreatPink, overlay = 0f, value = "${h.levelKills}/${h.levelThreats}", extra = null,
            description = "Threats cleared ${h.levelKills} of ${h.levelThreats}"
        )
    }
}

/** Label · thin glowing bar · value, all on one 16dp line. */
@Composable
private fun StatRow(
    label: String, labelColor: Color, fraction: Float, color: Color, overlay: Float,
    value: String, extra: String?, description: String, labelWidth: androidx.compose.ui.unit.Dp = 62.dp
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(16.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = labelColor, style = mono(10.sp, FontWeight.Bold), maxLines = 1, softWrap = false, modifier = Modifier.widthIn(min = labelWidth))
        ThinBar(fraction, color, overlay, Modifier.weight(1f).height(8.dp))
        Text(value, color = Palette.TextPrimary, style = mono(10.sp, FontWeight.Medium), maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 8.dp))
        if (extra != null) {
            Text(extra, color = Palette.Cyan, style = mono(10.sp, FontWeight.Bold), maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/** A 4dp track with a soft glow under the fill; the firewall rides on top as an orange hairline. */
@Composable
private fun ThinBar(fraction: Float, color: Color, overlay: Float, modifier: Modifier) {
    Canvas(modifier) {
        val th = 4.dp.toPx()
        val top = (size.height - th) / 2f
        val r = CornerRadius(th / 2f)
        val fw = size.width * fraction.coerceIn(0f, 1f)
        drawRoundRect(Palette.SurfaceRaised, Offset(0f, top), Size(size.width, th), r)
        if (fw > 0f) {
            drawRoundRect(color.copy(alpha = 0.22f), Offset(0f, top - 2.dp.toPx()), Size(fw, th + 4.dp.toPx()), CornerRadius(th))
            drawRoundRect(color, Offset(0f, top), Size(fw, th), r)
        }
        if (overlay > 0f) {
            drawRect(Palette.Orange, Offset(0f, top - 1.5.dp.toPx()), Size(size.width * overlay.coerceIn(0f, 1f), 1.5.dp.toPx()))
        }
    }
}

/** Chevron that folds / unfolds the HUD. */
@Composable
private fun PullHandle(collapsed: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    Box(
        modifier
            .size(width = 72.dp, height = 22.dp)
            .clickable(role = Role.Button, onClickLabel = if (collapsed) "Show HUD" else "Hide HUD", onClick = onToggle)
            .semantics { contentDescription = if (collapsed) "Show HUD" else "Hide HUD" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(width = 22.dp, height = 8.dp)) {
            val up = !collapsed
            val y0 = if (up) size.height else 0f
            val y1 = if (up) 0f else size.height
            val stroke = 1.5.dp.toPx()
            drawLine(Palette.Cyan.copy(alpha = 0.7f), Offset(0f, y0), Offset(size.width / 2f, y1), stroke, StrokeCap.Round)
            drawLine(Palette.Cyan.copy(alpha = 0.7f), Offset(size.width / 2f, y1), Offset(size.width, y0), stroke, StrokeCap.Round)
        }
    }
}

/** Extra space under the HUD before hints and banners start. */
val HUD_HINT_GAP: Dp = 8.dp

/**
 * PC (landscape) HUD: a strip *above* the arena, never over it (owner: "this is not a phone
 * game"). Left: level, score, € and HP; middle: the boss bar, or the threat count / event;
 * right: clock and pause. Buffs run along a second line.
 */
@Composable
fun PcHudStrip(h: HudSnapshot, paused: Boolean, onPause: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(HudNavy, Color(0xFF070C16))))
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(Palette.Cyan.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        h.roomLabel,
                        color = levelColor(h), style = mono(15.sp, FontWeight.Bold), maxLines = 1
                    )
                    Divider()
                    com.cyberoperative.game.ui.common.FitNumber(h.score.toLong(), Palette.TextPrimary, mono(12.sp, FontWeight.Medium), Modifier.weight(1f, fill = false), prefix = "SCORE ")
                    Divider()
                    com.cyberoperative.game.ui.common.FitNumber(h.euros.toLong(), Palette.Euro, mono(12.sp, FontWeight.Medium), Modifier.weight(1f, fill = false), prefix = "€ ")
                }
                Spacer(Modifier.height(4.dp))
                HpRow(h)
                XpRow(h)
                if (h.coop && !h.partnerGone) PartnerRow(h)
            }
            Spacer(Modifier.width(28.dp))
            Column(Modifier.weight(1.35f)) {
                if (h.bossName != null) BossHealthBar(h)
                else {
                    ProgressRow(h)
                    if (h.eventName != null) Text(
                        h.eventName + if (h.timedSeconds >= 0) "   SURVIVE ${h.timedSeconds}s" else "",
                        color = Color(h.eventAccent), style = mono(12.sp, FontWeight.Bold),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Spacer(Modifier.width(20.dp))
            // PC keys only, and not on Steam (its big ability bar sits along the bottom instead);
            // on Android the on-screen buttons show all of this.
            if (h.consumables && com.cyberoperative.game.platform.GameKeys.active && !com.cyberoperative.game.platform.AppInfo.desktop) {
                AbilityChip(com.cyberoperative.game.ui.common.KeyAction.DASH, "➠", "DASH", h.dashCd, Palette.Cyan)
                Spacer(Modifier.width(6.dp))
                h.spell1?.let { AbilityChip(com.cyberoperative.game.ui.common.KeyAction.SPELL_1, it.icon, it.label, h.spell1Cd, Palette.Purple) }
                Spacer(Modifier.width(6.dp))
                h.spell2?.let { AbilityChip(com.cyberoperative.game.ui.common.KeyAction.SPELL_2, it.icon, it.label, h.spell2Cd, Palette.Magenta) }
                Spacer(Modifier.width(14.dp))
                ConsumableChip(com.cyberoperative.game.ui.common.KeyAction.HP_CHARGE, "✚", h.hpCharges, Palette.Green)
                Spacer(Modifier.width(8.dp))
                ConsumableChip(com.cyberoperative.game.ui.common.KeyAction.PACKET_BOMB, "✹", h.packetBombs, Palette.Cyan)
                Spacer(Modifier.width(14.dp))
            }
            HudClock()
            PauseButton(paused, onPause)
        }
        if (h.owned.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            UpgradeBar(h, Modifier.fillMaxWidth())
        }
    }
}

/**
 * Android landscape HUD (owner's mock-up, 2026-10-12): a slim strip with no big boxes.
 * Left: "WORLD 1 · BOSS | 8,135 | € 919", the HP bar, then small upgrade chips and the build.
 * Right: the boss's name, phase and % over a thin bar (or the threats bar). Clock and pause last.
 */
@Composable
fun CompactHudStrip(h: HudSnapshot, paused: Boolean, onPause: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(HudNavy.copy(alpha = 0.92f), Color(0xE6070C16))))
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(Palette.Cyan.copy(alpha = 0.3f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            .padding(start = 14.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(h.roomLabel, color = levelColor(h), style = mono(13.sp, FontWeight.Bold), maxLines = 1)
                Divider()
                com.cyberoperative.game.ui.common.FitNumber(h.score.toLong(), Palette.TextPrimary, mono(12.sp, FontWeight.Medium), Modifier.weight(1f, fill = false))
                Divider()
                com.cyberoperative.game.ui.common.FitNumber(h.euros.toLong(), Palette.Euro, mono(12.sp, FontWeight.Medium), Modifier.weight(1f, fill = false), prefix = "€ ")
            }
            Spacer(Modifier.height(2.dp))
            HpRow(h, label = "HP", labelWidth = 26.dp)
            XpRow(h, labelWidth = 26.dp)
            if (h.coop && !h.partnerGone) PartnerRow(h)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (h.owned.isNotEmpty()) {
                    // The chips at two-thirds size.
                    val d = androidx.compose.ui.platform.LocalDensity.current
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density * 0.66f, d.fontScale)
                    ) { UpgradeBar(h, Modifier.weight(1f, fill = false)) }
                    Spacer(Modifier.width(8.dp))
                }
                Text(com.cyberoperative.game.ui.common.buildStampText(), color = Color.White.copy(alpha = 0.45f), style = mono(9.sp), maxLines = 1)
            }
        }
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1.3f).align(Alignment.Top)) {
            if (h.bossName != null) BossHealthBar(h, compact = true)
            else {
                Spacer(Modifier.height(4.dp))
                ProgressRow(h)
                if (h.eventName != null) Text(
                    h.eventName + if (h.timedSeconds >= 0) "   SURVIVE ${h.timedSeconds}s" else "",
                    color = Color(h.eventAccent), style = mono(11.sp, FontWeight.Bold),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        HudClock()
        PauseButton(paused, onPause)
    }
}

/** "[Q] ✚ 3": a consumable, its key and how many are carried (dimmed at zero). */
@Composable
private fun ConsumableChip(action: com.cyberoperative.game.ui.common.KeyAction, icon: String, count: Int, color: Color) {
    val key = com.cyberoperative.game.platform.GameKeys.keyName(com.cyberoperative.game.ui.common.KeyBinds.code(action)).uppercase().take(5)
    val c = if (count > 0) color else Palette.TextMuted
    Row(
        Modifier
            .border(1.dp, c.copy(alpha = 0.6f), androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("[$key]", color = Palette.TextSecondary, style = mono(11.sp, FontWeight.Bold))
        Spacer(Modifier.width(6.dp))
        Text("$icon $count", color = c, style = mono(14.sp, FontWeight.Bold))
    }
}

/**
 * Steam ability bar (owner, 0.31.0: "larger icons for abilities… readable… see the cooldown"):
 * big tiles for DASH, both class spells, HP charges and Packet Bombs, each with its key, a dark
 * sweep that shrinks as it recharges, and the seconds left in large digits.
 */
@Composable
fun PcAbilityBar(h: HudSnapshot, scale: Float, modifier: Modifier = Modifier) {
    val tile = 76.dp * scale
    Row(modifier, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp * scale), verticalAlignment = Alignment.Bottom) {
        AbilityTile(com.cyberoperative.game.ui.common.KeyAction.DASH, "➠", "DASH", h.dashCd, com.cyberoperative.game.engine.GameEngine.DASH_COOLDOWN, Palette.Cyan, tile)
        h.spell1?.let { AbilityTile(com.cyberoperative.game.ui.common.KeyAction.SPELL_1, it.icon, it.label, h.spell1Cd, it.cooldown, Palette.Purple, tile) }
        h.spell2?.let { AbilityTile(com.cyberoperative.game.ui.common.KeyAction.SPELL_2, it.icon, it.label, h.spell2Cd, it.cooldown, Palette.Magenta, tile) }
        Spacer(Modifier.width(10.dp * scale))
        AbilityTile(com.cyberoperative.game.ui.common.KeyAction.HP_CHARGE, "✚", "HP", 0f, 1f, Palette.Green, tile, count = h.hpCharges)
        AbilityTile(com.cyberoperative.game.ui.common.KeyAction.PACKET_BOMB, "✹", "BOMB", 0f, 1f, Palette.Cyan, tile, count = h.packetBombs)
    }
}

@Composable
private fun AbilityTile(
    action: com.cyberoperative.game.ui.common.KeyAction, icon: String, label: String,
    cooldown: Float, maxCooldown: Float, color: Color, size: androidx.compose.ui.unit.Dp, count: Int = -1
) {
    val key = com.cyberoperative.game.platform.GameKeys.keyName(com.cyberoperative.game.ui.common.KeyBinds.code(action)).uppercase().take(5)
    val ready = cooldown <= 0f && count != 0
    val c = if (ready) color else Palette.TextMuted
    val k = size / 76.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val r = CornerRadius(size.toPx() * 0.16f)
                drawRoundRect(Color(0xE6070C16), cornerRadius = r)
                drawRoundRect(c.copy(alpha = if (ready) 0.22f else 0.08f), cornerRadius = r)
                if (cooldown > 0f && maxCooldown > 0f) {
                    // Dark sweep over the part still recharging (clockwise from the top).
                    val f = (cooldown / maxCooldown).coerceIn(0f, 1f)
                    drawArc(Color(0xCC000000), -90f, 360f * f, true, Offset(-this.size.width * 0.25f, -this.size.height * 0.25f),
                        Size(this.size.width * 1.5f, this.size.height * 1.5f))
                }
                drawRoundRect(c.copy(alpha = if (ready) 0.95f else 0.5f), cornerRadius = r, style = Stroke((if (ready) 3f else 2f) * k))
            }
            Text(icon, color = c, style = mono(30.sp * k, FontWeight.Bold))
            if (cooldown > 0f) Text(
                if (cooldown >= 10f) "${cooldown.toInt()}" else "%.1f".format(cooldown),
                color = Color.White, style = mono(24.sp * k, FontWeight.Bold).copy(shadow = androidx.compose.ui.graphics.Shadow(Color.Black, Offset(0f, 2f), 6f))
            )
            Text("[$key]", color = Palette.TextSecondary, style = mono(11.sp * k, FontWeight.Bold), modifier = Modifier.align(Alignment.TopStart).padding(5.dp * k))
            if (count >= 0) Text("$count", color = if (count > 0) Color.White else Palette.Red, style = mono(16.sp * k, FontWeight.Bold), modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp * k))
        }
        Spacer(Modifier.height(3.dp))
        Text(label, color = c, style = mono(11.sp * k, FontWeight.Bold), maxLines = 1)
    }
}

/** "[R] ☄ FIRESTORM" with a countdown while it recharges. */
@Composable
private fun AbilityChip(action: com.cyberoperative.game.ui.common.KeyAction, icon: String, label: String, cooldown: Float, color: Color) {
    val key = com.cyberoperative.game.platform.GameKeys.keyName(com.cyberoperative.game.ui.common.KeyBinds.code(action)).uppercase().take(5)
    val ready = cooldown <= 0f
    val c = if (ready) color else Palette.TextMuted
    Row(
        Modifier
            .border(1.dp, c.copy(alpha = if (ready) 0.8f else 0.4f), androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("[$key]", color = Palette.TextSecondary, style = mono(11.sp, FontWeight.Bold))
        Spacer(Modifier.width(5.dp))
        Text(icon, color = c, style = mono(14.sp, FontWeight.Bold))
        Spacer(Modifier.width(4.dp))
        Text(
            if (ready) label else "${kotlin.math.ceil(cooldown).toInt()}s",
            color = c, style = mono(11.sp, FontWeight.Bold), maxLines = 1
        )
    }
}
