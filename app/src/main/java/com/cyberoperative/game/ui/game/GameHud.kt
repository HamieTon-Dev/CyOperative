package com.cyberoperative.game.ui.game

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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

private fun mono(size: TextUnit, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = TerminalFont, fontSize = size, fontWeight = weight, letterSpacing = 0.4.sp)

/**
 * Hides the status bar while gameplay is on screen. A swipe from the top edge
 * shows it transiently (and a second swipe opens notifications / Quick
 * Settings as usual). Navigation bars and gestures are left alone. The bars
 * come back as soon as the game screen leaves the composition.
 */
@Composable
fun ImmersiveGameplay() {
    val view = LocalView.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(view, lifecycle) {
        val window = view.context.findActivity()?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            val previousBehavior = controller.systemBarsBehavior
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
            // The system can bring the bar back while the app is away; hide it again on return.
            val obs = LifecycleEventObserver { _, e ->
                if (e == Lifecycle.Event.ON_RESUME) controller.hide(WindowInsetsCompat.Type.statusBars())
            }
            lifecycle.addObserver(obs)
            onDispose {
                lifecycle.removeObserver(obs)
                controller.show(WindowInsetsCompat.Type.statusBars())
                controller.systemBarsBehavior = previousBehavior
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

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
                    if (h.mode == GameMode.ENDLESS) "STAGE ${h.level}" else "LVL ${h.level}",
                    color = levelColor(h), style = mono(big, FontWeight.Bold), maxLines = 1, softWrap = false
                )
                Divider()
                Text(
                    "SCORE ${"%,d".format(h.score)}", color = Palette.TextPrimary, style = mono(small, FontWeight.Medium),
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                )
                Divider()
                Text(
                    "€ ${"%,d".format(h.euros)}", color = Palette.Euro, style = mono(small, FontWeight.Medium),
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                )
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
    val ctx = LocalContext.current
    var text by remember { mutableStateOf(formatClock(ctx)) }
    LaunchedEffect(ctx) {
        while (true) {
            val now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L + 50L)
            text = formatClock(ctx)
        }
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, ctx) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) text = formatClock(ctx) }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    Text(
        text, color = Palette.TextSecondary, style = mono(12.sp), maxLines = 1, softWrap = false,
        modifier = modifier.semantics { contentDescription = "Time $text" }
    )
}

private fun formatClock(ctx: Context): String {
    val is24 = android.text.format.DateFormat.is24HourFormat(ctx)
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
private fun HpRow(h: HudSnapshot) {
    val f = h.hp / h.maxHp.coerceAtLeast(1).toFloat()
    StatRow(
        label = "CORE HP",
        labelColor = Palette.Green,
        fraction = f,
        color = Palette.healthColor(f),
        overlay = if (h.firewallMax > 0) h.firewall / h.firewallMax.toFloat() else 0f,
        value = "${h.hp}/${h.maxHp}",
        extra = if (h.firewallMax > 0) "FW ${h.firewall}" else null,
        description = "Core health ${h.hp} of ${h.maxHp}" + if (h.firewallMax > 0) ", firewall ${h.firewall} of ${h.firewallMax}" else ""
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
    if (h.mode == GameMode.ENDLESS) {
        StatRow(
            label = "DATA ${h.runLevel}", labelColor = Palette.Green, fraction = h.xpPercent / 100f,
            color = Palette.Green, overlay = 0f, value = "${h.xpPercent}%", extra = null,
            description = "Data level ${h.runLevel}, ${h.xpPercent} percent"
        )
    } else if (h.kind != LevelKind.BOSS && h.kind != LevelKind.SHOP && h.timedSeconds < 0) {
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
    value: String, extra: String?, description: String
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(16.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = labelColor, style = mono(10.sp, FontWeight.Bold), maxLines = 1, softWrap = false, modifier = Modifier.widthIn(min = 62.dp))
        ThinBar(fraction, color, overlay, Modifier.weight(1f).height(8.dp))
        Text(value, color = Palette.TextPrimary, style = mono(10.sp, FontWeight.Medium), maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 8.dp))
        if (extra != null) {
            Text(extra, color = Palette.Orange, style = mono(10.sp, FontWeight.Medium), maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 6.dp))
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
