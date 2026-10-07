package com.cyberoperative.game.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cyberoperative.game.engine.GameMode
import com.cyberoperative.game.engine.LevelKind
import com.cyberoperative.game.engine.Phase
import com.cyberoperative.game.ui.theme.Palette
import kotlin.math.sqrt

/**
 * The gameplay screen (§7, §51–54). Layout, top to bottom:
 * compact HUD → arena (fills the screen) → one movement zone at the bottom.
 * The whole arena accepts the drag, so the thumb never has to find a tiny
 * stick; the joystick appears where the thumb lands and rests at bottom
 * centre when idle.
 */
@Composable
fun GameScreen(
    session: GameSession,
    showDamageNumbers: Boolean,
    onExitToMenu: () -> Unit,
    onNewOperation: () -> Unit
) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Frame loop: lives with the screen; leaving the composition stops it.
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(session) {
        var last = withFrameNanos { it }
        val start = last
        while (true) {
            withFrameNanos { now ->
                val dt = (now - last) / 1_000_000_000f
                last = now
                time = (now - start) / 1_000_000_000f
                session.onFrame(dt)
            }
        }
    }

    BackHandler {
        if (session.hud.phase != Phase.DEAD) session.paused = !session.paused
    }

    val renderer = remember { ArenaRenderer() }
    val density = LocalDensity.current
    val hud = session.hud

    // Joystick state (screen pixels)
    var stickActive by remember { mutableStateOf(false) }
    var originX by remember { mutableFloatStateOf(0f) }
    var originY by remember { mutableFloatStateOf(0f) }
    var knobX by remember { mutableFloatStateOf(0f) }
    var knobY by remember { mutableFloatStateOf(0f) }
    val stickRadiusPx = with(density) { 56.dp.toPx() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Palette.SurfaceSunken)
    ) {
        val topInsetPx = with(density) { 118.dp.toPx() }
        val bottomInsetPx = with(density) { 150.dp.toPx() }
        val restX = constraints.maxWidth / 2f
        val restY = constraints.maxHeight - with(density) { 110.dp.toPx() }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(session) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (session.paused || session.engine.phase == Phase.UPGRADE || session.engine.phase == Phase.DEAD) return@awaitEachGesture
                        stickActive = true
                        originX = down.position.x
                        originY = down.position.y
                        knobX = originX
                        knobY = originY
                        session.engine.setInput(0f, 0f)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            var dx = change.position.x - originX
                            var dy = change.position.y - originY
                            val d = sqrt(dx * dx + dy * dy)
                            if (d > stickRadiusPx) {
                                // Let the base trail the thumb so direction changes stay instant.
                                val excess = d - stickRadiusPx
                                originX += dx / d * excess
                                originY += dy / d * excess
                                dx = change.position.x - originX
                                dy = change.position.y - originY
                            }
                            knobX = originX + dx
                            knobY = originY + dy
                            session.engine.setInput(dx / stickRadiusPx, dy / stickRadiusPx)
                            change.consume()
                        }
                        stickActive = false
                        session.engine.setInput(0f, 0f)
                    }
                }
        ) {
            @Suppress("UNUSED_VARIABLE") val tick = session.frameTick
            renderer.draw(this, session.engine, time, session.skin, session.body, session.background, showDamageNumbers, topInsetPx, bottomInsetPx)
            // Joystick
            val bx = if (stickActive) originX else restX
            val by = if (stickActive) originY else restY
            drawCircle(Palette.Cyan.copy(alpha = if (stickActive) 0.16f else 0.07f), stickRadiusPx, Offset(bx, by))
            drawCircle(Palette.Cyan.copy(alpha = if (stickActive) 0.6f else 0.25f), stickRadiusPx, Offset(bx, by), style = Stroke(3f))
            val kx = if (stickActive) knobX else restX
            val ky = if (stickActive) knobY else restY
            drawCircle(Palette.Cyan.copy(alpha = if (stickActive) 0.85f else 0.3f), stickRadiusPx * 0.42f, Offset(kx, ky))
        }

        if (!stickActive && hud.phase == Phase.COMBAT && hud.level == 1 && time < 12f) {
            Text(
                "DRAG TO MOVE · RELEASE TO FIRE",
                color = Palette.TextSecondary, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 18.dp)
            )
        }

        GameHud(hud, onPause = { session.paused = true })

        if (hud.bannerVisible && hud.banner.isNotEmpty() && hud.phase != Phase.UPGRADE) {
            Banner(hud, Modifier.align(Alignment.TopCenter).padding(top = 170.dp))
        }
        if (hud.phase == Phase.PORTAL) {
            Text(
                "▲ GATE OPEN — WALK THROUGH TO CONTINUE",
                color = Palette.Green, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 128.dp)
                    .background(Palette.Background.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        if (hud.phase == Phase.UPGRADE) {
            UpgradeOverlay(session)
        }
        val result = session.result
        if (hud.phase == Phase.DEAD && result != null) {
            GameOverOverlay(
                result = result,
                hud = hud,
                onRevive = { session.revive() },
                onMenu = { session.finish(); onExitToMenu() },
                onNew = { session.finish(); onNewOperation() }
            )
        }
        if (session.paused && hud.phase != Phase.DEAD) {
            PauseOverlay(
                onResume = { session.paused = false },
                onQuit = { session.finish(); onExitToMenu() }
            )
        }
        if (session.showTutorial) {
            TutorialOverlay(onDone = { session.dismissTutorial() })
        }
    }
}

@Composable
private fun GameHud(h: HudSnapshot, onPause: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.Background.copy(alpha = 0.82f))
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val levelColor = when (h.kind) {
                LevelKind.BOSS -> Palette.Red
                LevelKind.EVENT -> Color(h.eventAccent)
                LevelKind.NORMAL -> Palette.Cyan
            }
            Text(if (h.mode == GameMode.ENDLESS) "STAGE ${h.level}" else "LVL ${h.level}", color = levelColor, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("SCORE ${"%,d".format(h.score)}", color = Palette.TextPrimary, style = MaterialTheme.typography.labelMedium)
                Text("€ ${h.euros}", color = Palette.Euro, style = MaterialTheme.typography.labelMedium)
            }
            Box(
                Modifier
                    .size(40.dp)
                    .border(1.dp, Palette.Divider, RoundedCornerShape(6.dp))
                    .clickable(onClick = onPause),
                contentAlignment = Alignment.Center
            ) { Text("II", color = Palette.TextPrimary, style = MaterialTheme.typography.titleMedium) }
        }
        Spacer(Modifier.height(4.dp))
        // HP bar with firewall overlay
        Bar(
            fraction = h.hp / h.maxHp.coerceAtLeast(1).toFloat(),
            color = Palette.healthColor(h.hp / h.maxHp.coerceAtLeast(1).toFloat()),
            label = "HP ${h.hp}/${h.maxHp}" + if (h.firewallMax > 0) "   FW ${h.firewall}/${h.firewallMax}" else "",
            overlay = if (h.firewallMax > 0) h.firewall / h.firewallMax.toFloat() else 0f,
            height = 14
        )
        Spacer(Modifier.height(3.dp))
        if (h.mode == GameMode.ENDLESS) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("DATA ${h.runLevel}", color = Palette.Green, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f)) { Bar(h.xpPercent / 100f, Palette.Green, null, 0f, 5) }
            }
        } else if (h.kind != LevelKind.BOSS && h.timedSeconds < 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("THREATS ${h.levelKills}/${h.levelThreats}", color = Palette.Red, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f)) { Bar(h.levelKills / h.levelThreats.coerceAtLeast(1).toFloat(), Palette.Red, null, 0f, 5) }
            }
        }
        val boss = h.bossName
        if (boss != null) {
            Spacer(Modifier.height(4.dp))
            Bar(h.bossPercent / 100f, Palette.Red, "$boss · ${h.bossPhase}", 0f, 12)
        } else if (h.eventName != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                h.eventName + if (h.timedSeconds >= 0) "   SURVIVE ${h.timedSeconds}s" else "",
                color = Color(h.eventAccent), style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun Bar(fraction: Float, color: Color, label: String?, overlay: Float, height: Int) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .background(Palette.SurfaceRaised, RoundedCornerShape(3.dp))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height.dp)
                .background(color, RoundedCornerShape(3.dp))
        )
        if (overlay > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(overlay.coerceIn(0f, 1f))
                    .height((height / 3).coerceAtLeast(2).dp)
                    .align(Alignment.BottomStart)
                    .background(Palette.Orange)
            )
        }
        if (label != null) {
            Text(
                label, color = Color.Black, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun Banner(h: HudSnapshot, modifier: Modifier) {
    val color = when (h.kind) {
        LevelKind.BOSS -> Palette.Red
        LevelKind.EVENT -> Color(h.eventAccent)
        LevelKind.NORMAL -> Palette.Cyan
    }
    Column(
        modifier
            .background(Palette.Background.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(h.banner, color = color, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        if (h.bannerSub.isNotEmpty()) {
            Text(h.bannerSub, color = Palette.TextSecondary, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun TutorialOverlay(onDone: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xD9000000))
            .clickable(onClick = onDone),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .background(Palette.Surface, RoundedCornerShape(10.dp))
                .border(1.dp, Palette.Cyan, RoundedCornerShape(10.dp))
                .padding(20.dp)
        ) {
            Text("OPERATIVE BRIEFING", color = Palette.Cyan, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            for (line in TUTORIAL_LINES) {
                Text("> $line", color = Palette.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text("TAP TO BEGIN", color = Palette.Green, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

val TUTORIAL_LINES = listOf(
    "Drag anywhere to move.",
    "Stop moving to fire automatically.",
    "Orbiting Packet Nodes attack all the time.",
    "Avoid red/orange enemy attacks.",
    "Defeat all threats, then enter the ACCESS PORT.",
    "Choose an upgrade when you gain enough data.",
    "A boss appears every 10 levels."
)

@Composable
private fun PauseOverlay(onResume: () -> Unit, onQuit: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(enabled = true, onClick = {}),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .padding(32.dp)
                .background(Palette.Surface, RoundedCornerShape(10.dp))
                .border(1.dp, Palette.Cyan, RoundedCornerShape(10.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("OPERATION PAUSED", color = Palette.Cyan, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            com.cyberoperative.game.ui.common.CyberButton("RESUME", Modifier.fillMaxWidth(), accent = Palette.Green, primary = true) { onResume() }
            Spacer(Modifier.height(10.dp))
            com.cyberoperative.game.ui.common.CyberButton("ABORT OPERATION", Modifier.fillMaxWidth(), accent = Palette.Red) { onQuit() }
            Spacer(Modifier.height(6.dp))
            Text("Progress and € earned so far are kept.", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}
