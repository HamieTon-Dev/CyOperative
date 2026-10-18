package com.cyberoperative.game.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import com.cyberoperative.game.data.Spell
import com.cyberoperative.game.save.GameSettings
import com.cyberoperative.game.ui.theme.Palette
import kotlin.math.roundToInt

/** The on-screen controls of Android's PC mode (owner, 2026-10-11). */
enum class TouchControl(val key: String, val label: String, val sizeDp: Int) {
    STICK("stick", "JOYSTICK", 112),
    DASH("dash", "DASH", 66),
    SPELL1("spell1", "SPELL 1", 58),
    SPELL2("spell2", "SPELL 2", 58),
    HP("hp", "HP CHARGE", 48),
    BOMB("bomb", "PACKET BOMB", 48)
}

/** Where each control sits, as fractions of the screen (customisable per orientation in Settings). */
object TouchLayout {
    /** Landscape: joystick bottom-left; dash, spells and pouch bottom-right. */
    private val LANDSCAPE = mapOf(
        TouchControl.STICK to Offset(0.13f, 0.74f),
        TouchControl.DASH to Offset(0.91f, 0.80f),
        TouchControl.SPELL1 to Offset(0.81f, 0.86f),
        TouchControl.SPELL2 to Offset(0.88f, 0.57f),
        TouchControl.HP to Offset(0.76f, 0.64f),
        TouchControl.BOMB to Offset(0.94f, 0.36f)
    )
    /** Portrait: the three abilities bottom-right, the pouch just above them. */
    private val PORTRAIT = mapOf(
        TouchControl.STICK to Offset(0.32f, 0.86f),
        TouchControl.DASH to Offset(0.86f, 0.86f),
        TouchControl.SPELL1 to Offset(0.68f, 0.90f),
        TouchControl.SPELL2 to Offset(0.86f, 0.765f),
        TouchControl.HP to Offset(0.70f, 0.79f),
        TouchControl.BOMB to Offset(0.88f, 0.675f)
    )

    private fun key(landscape: Boolean, c: TouchControl, axis: String) = "${if (landscape) "L" else "P"}.${c.key}.$axis"

    fun pos(s: GameSettings, c: TouchControl, landscape: Boolean): Offset {
        val d = (if (landscape) LANDSCAPE else PORTRAIT).getValue(c)
        return Offset(s.touchLayout[key(landscape, c, "x")] ?: d.x, s.touchLayout[key(landscape, c, "y")] ?: d.y)
    }

    fun with(s: GameSettings, c: TouchControl, landscape: Boolean, p: Offset): Map<String, Float> =
        s.touchLayout + mapOf(key(landscape, c, "x") to p.x.coerceIn(0.03f, 0.97f), key(landscape, c, "y") to p.y.coerceIn(0.05f, 0.97f))

    fun reset(s: GameSettings, landscape: Boolean): Map<String, Float> =
        s.touchLayout.filterKeys { !it.startsWith(if (landscape) "L." else "P.") }

    /**
     * The MAP button's corner (top right, under the HUD) is off limits to every control
     * (owner, 2026-10-11: "buttons should not be able to block the map button"). Sizes in dp.
     */
    const val MAP_ZONE_W = 120f
    fun mapZoneH(landscape: Boolean): Float = if (landscape) 120f else 210f

    /** [p] (screen fractions) moved out of the MAP corner if control [c] would overlap it. */
    fun clearOfMap(p: Offset, c: TouchControl, landscape: Boolean, wDp: Float, hDp: Float): Offset {
        if (wDp <= 0f || hDp <= 0f) return p
        val half = c.sizeDp / 2f
        val zoneLeft = wDp - MAP_ZONE_W
        val zoneBottom = mapZoneH(landscape)
        val cx = p.x * wDp
        val cy = p.y * hDp
        if (cx + half <= zoneLeft || cy - half >= zoneBottom) return p
        // Out the shorter way: left of the corner, or below it.
        val left = (cx + half) - zoneLeft
        val down = zoneBottom - (cy - half)
        return if (left < down) Offset((zoneLeft - half) / wDp, p.y) else Offset(p.x, (zoneBottom + half) / hDp)
    }

    /** Where [c] goes on a [wDp] x [hDp] screen: the saved spot, kept out of the MAP corner. */
    fun place(s: GameSettings, c: TouchControl, landscape: Boolean, wDp: Float, hDp: Float): Offset =
        clearOfMap(pos(s, c, landscape), c, landscape, wDp, hDp)

    /** AUTO: locked in landscape, floating in portrait. */
    fun stickLocked(s: GameSettings, landscape: Boolean): Boolean = when (s.joystickMode) {
        "FLOAT" -> false
        "LOCKED" -> true
        else -> landscape
    }

    /**
     * Opaque by default (owner, 0.48.1); [seeThrough] (TOUCH CONTROLS → SEE-THROUGH BUTTONS)
     * brings back the old 55%. Set from the settings by the game screen and the editor.
     */
    @Volatile var seeThrough = false
    val ALPHA: Float get() = if (seeThrough) 0.55f else 1f
}

/** What one button shows right now. */
/** [abyssal]: FULL RESTORE is on, so the potion glows abyssal red (owner, 0.39.0). */
data class TouchButtonState(val ready: Boolean, val cooldown: Float = 0f, val maxCooldown: Float = 1f, val count: Int = -1, val abyssal: Boolean = false)

/**
 * The action buttons (dash, spells, HP charge, Packet Bomb) at their layout spots. Each fires
 * the moment it's touched, so it works with a thumb already on the joystick.
 */
@Composable
fun TouchActionButtons(
    settings: GameSettings, landscape: Boolean, hud: HudSnapshot,
    onPress: (TouchControl) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        for (c in TouchControl.entries) {
            if (c == TouchControl.STICK) continue
            val p = TouchLayout.place(settings, c, landscape, w.value, h.value)
            val st = stateOf(c, hud)
            TouchButton(
                c, spellOf(c, hud), st,
                Modifier
                    .offset(w * p.x - (c.sizeDp / 2).dp, h * p.y - (c.sizeDp / 2).dp)
                    .size(c.sizeDp.dp)
                    .pointerInput(c) { detectTapGestures(onPress = { onPress(c) }) }
            )
        }
    }
}

private fun spellOf(c: TouchControl, h: HudSnapshot): Spell? = when (c) {
    TouchControl.SPELL1 -> h.spell1
    TouchControl.SPELL2 -> h.spell2
    else -> null
}

private fun stateOf(c: TouchControl, h: HudSnapshot): TouchButtonState = when (c) {
    TouchControl.DASH -> TouchButtonState(h.dashCd <= 0f, h.dashCd, h.dashMax)
    TouchControl.SPELL1 -> TouchButtonState(h.spell1Cd <= 0f, h.spell1Cd, h.spell1?.cooldown ?: 1f)
    TouchControl.SPELL2 -> TouchButtonState(h.spell2Cd <= 0f, h.spell2Cd, h.spell2?.cooldown ?: 1f)
    TouchControl.HP -> TouchButtonState(h.hpCharges > 0, count = h.hpCharges, abyssal = h.potionFull)
    TouchControl.BOMB -> TouchButtonState(h.packetBombs > 0, count = h.packetBombs)
    TouchControl.STICK -> TouchButtonState(true)
}

/** One round, see-through button with its icon, a cooldown sweep, and a count badge for the pouch items. */
@Composable
fun TouchButton(c: TouchControl, spell: Spell?, st: TouchButtonState, modifier: Modifier) {
    val accent = when (c) {
        TouchControl.DASH -> Palette.Cyan
        TouchControl.SPELL1 -> Palette.Purple
        TouchControl.SPELL2 -> Palette.Magenta
        TouchControl.HP -> if (st.abyssal) Color(0xFFFF2B3A) else Palette.Green
        TouchControl.BOMB -> Color(0xFF7DF9FF)
        TouchControl.STICK -> Palette.Cyan
    }
    val see = TouchLayout.seeThrough
    Box(modifier.graphicsLayer { alpha = if (st.ready || !see) TouchLayout.ALPHA else TouchLayout.ALPHA * 0.6f }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val o = Offset(size.width / 2f, size.height / 2f)
            drawCircle(if (see) Color(0xFF05080F).copy(alpha = 0.65f) else Color(0xFF0B1424), r, o)
            drawCircle(accent, r - 1.5f, o, style = Stroke(2.5f))
            if (!st.ready && st.cooldown > 0f) {
                val f = (st.cooldown / st.maxCooldown).coerceIn(0f, 1f)
                drawArc(Color.Black.copy(alpha = 0.55f), -90f, 360f * f, true, Offset(o.x - r, o.y - r), Size(r * 2f, r * 2f))
            }
            when (c) {
                TouchControl.DASH -> dashIcon(o, r, accent)
                TouchControl.HP -> potionIcon(o, r, st.abyssal)
                TouchControl.BOMB -> bombIcon(o, r)
                else -> {}
            }
        }
        if (spell != null) Text(spell.icon, color = accent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        if (!st.ready && st.cooldown > 0f) Text("${kotlin.math.ceil(st.cooldown).toInt()}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (st.count >= 0) Text(
            "${st.count}", color = if (st.count > 0) Color.White else Palette.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}

private val iconPath = Path()

private fun DrawScope.dashIcon(o: Offset, r: Float, c: Color) {
    // Double chevron with speed lines.
    for (k in 0..1) {
        val x = o.x - r * 0.18f + k * r * 0.32f
        iconPath.reset()
        iconPath.moveTo(x - r * 0.2f, o.y - r * 0.35f); iconPath.lineTo(x + r * 0.12f, o.y); iconPath.lineTo(x - r * 0.2f, o.y + r * 0.35f)
        drawPath(iconPath, c, style = Stroke(r * 0.14f, cap = StrokeCap.Round))
    }
    for (k in -1..1) drawLine(c.copy(alpha = 0.6f), Offset(o.x - r * 0.75f, o.y + k * r * 0.22f), Offset(o.x - r * 0.45f, o.y + k * r * 0.22f), r * 0.07f, cap = StrokeCap.Round)
}

private fun DrawScope.potionIcon(o: Offset, r: Float, abyssal: Boolean = false) {
    val k = r / 22f
    if (abyssal) drawCircle(com.cyberoperative.game.ui.common.SafeBrush.radialGradient(listOf(Color(0x99FF2B3A), Color.Transparent), Offset(o.x, o.y + 2f * k), 18f * k), 18f * k, Offset(o.x, o.y + 2f * k))
    drawRect(Color(0xCCBFEFFF), Offset(o.x - 3f * k, o.y - 15f * k), Size(6f * k, 7f * k))
    drawCircle(if (abyssal) Color(0xFFD01020) else Color(0xFF00C878), 9f * k, Offset(o.x, o.y + 2f * k))
    drawCircle(Color(0xFFBFEFFF), 9.5f * k, Offset(o.x, o.y + 2f * k), style = Stroke(1.4f * k))
    drawLine(Color.White, Offset(o.x - 4f * k, o.y + 2f * k), Offset(o.x + 4f * k, o.y + 2f * k), 2f * k)
    drawLine(Color.White, Offset(o.x, o.y - 2f * k), Offset(o.x, o.y + 6f * k), 2f * k)
}

private fun DrawScope.bombIcon(o: Offset, r: Float) {
    val k = r / 22f
    drawCircle(Color(0xFF14181F), 9.5f * k, Offset(o.x - 1f * k, o.y + 2f * k))
    drawCircle(Color(0xFF2E9BFF), 9.5f * k, Offset(o.x - 1f * k, o.y + 2f * k), style = Stroke(1.6f * k))
    drawLine(Color(0xFFB0905A), Offset(o.x + 5f * k, o.y - 6f * k), Offset(o.x + 9f * k, o.y - 11f * k), 1.8f * k)
    drawCircle(Color(0xFFFFC233), 2.6f * k, Offset(o.x + 9f * k, o.y - 11f * k))
}

/** The joystick graphic (also used by the layout editor). */
fun DrawScope.drawStick(base: Offset, knob: Offset, radius: Float, active: Boolean) {
    if (TouchLayout.seeThrough) {
        drawCircle(Palette.Cyan.copy(alpha = if (active) 0.16f else 0.07f), radius, base)
        drawCircle(Palette.Cyan.copy(alpha = if (active) 0.6f else 0.25f), radius, base, style = Stroke(3f))
        drawCircle(Palette.Cyan.copy(alpha = if (active) 0.85f else 0.3f), radius * 0.42f, knob)
        return
    }
    // Opaque (0.48.1): a solid pad with a bright rim and a solid knob.
    drawCircle(Color(0xFF0B1424), radius, base)
    drawCircle(Palette.Cyan.copy(alpha = if (active) 1f else 0.75f), radius, base, style = Stroke(3f))
    drawCircle(Color(0xFF0E3A4A), radius * 0.42f, knob)
    drawCircle(Palette.Cyan, radius * 0.42f, knob, style = Stroke(3f))
}

/**
 * Settings → TOUCH CONTROLS: drag the joystick and every button where you want them, for the
 * orientation you're in. RESET puts this orientation back to the defaults.
 */
@Composable
fun TouchLayoutEditor(settings: GameSettings, landscape: Boolean, onChange: (Map<String, Float>) -> Unit) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val wDp = maxWidth.value
        val hDp = maxHeight.value
        // The MAP button's corner: shown, and no control can be dragged into it.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(TouchLayout.MAP_ZONE_W.dp, TouchLayout.mapZoneH(landscape).dp)
                .border(1.dp, Palette.Cyan.copy(alpha = 0.5f), androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                .background(Palette.Cyan.copy(alpha = 0.06f), androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) { Text("MAP\n(KEEP CLEAR)", color = Palette.Cyan.copy(alpha = 0.8f), fontSize = 9.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        for (c in TouchControl.entries) {
            val p = TouchLayout.place(settings, c, landscape, wDp, hDp)
            val half = with(density) { (c.sizeDp / 2).dp.toPx() }
            val latest = androidx.compose.runtime.rememberUpdatedState(settings)
            val change0 = androidx.compose.runtime.rememberUpdatedState(onChange)
            Box(
                Modifier
                    .offset { IntOffset((p.x * wPx - half).roundToInt(), (p.y * hPx - half).roundToInt()) }
                    .size(c.sizeDp.dp)
                    .pointerInput(c, landscape) {
                        var cur = p
                        detectDragGestures(onDragStart = { cur = TouchLayout.place(latest.value, c, landscape, wDp, hDp) }) { change, drag ->
                            change.consume()
                            cur = Offset(cur.x + drag.x / wPx, cur.y + drag.y / hPx)
                            change0.value(TouchLayout.with(latest.value, c, landscape, TouchLayout.clearOfMap(cur, c, landscape, wDp, hDp)))
                        }
                    }
            ) {
                if (c == TouchControl.STICK) Canvas(Modifier.fillMaxSize()) {
                    val o = Offset(size.width / 2f, size.height / 2f)
                    drawStick(o, o, size.minDimension / 2f, active = true)
                } else TouchButton(c, null, TouchButtonState(true, count = if (c == TouchControl.HP || c == TouchControl.BOMB) 3 else -1), Modifier.fillMaxSize())
                Text(c.label, color = Palette.TextSecondary, fontSize = 9.sp, modifier = Modifier.align(Alignment.BottomCenter).offset(y = 14.dp))
            }
        }
    }
}
