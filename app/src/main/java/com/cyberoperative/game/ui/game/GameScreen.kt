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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.layout.onSizeChanged
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
    onNewOperation: () -> Unit,
    screenShake: Boolean = true
) {
    val view = LocalView.current
    ImmersiveGameplay()
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    // Leaving the app mid-fight opens the pause menu, so coming back never
    // drops the player straight into combat.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE && session.engine.phase != Phase.DEAD) session.paused = true
            // Backgrounded: keep a save so a killed app can still CONTINUE.
            if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) session.autosave()
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
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
    // HUD folded to the small pill (pull handle); its measured height sets where the arena starts.
    var hudCollapsed by rememberSaveable { mutableStateOf(false) }
    var hudHeightPx by remember { mutableIntStateOf(0) }
    var shopHeld by remember { mutableStateOf<Int?>(null) }
    var shopSwap by remember { mutableStateOf<Int?>(null) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Palette.SurfaceSunken)
    ) {
        // The arena starts right under the HUD as measured (it grows with the buff row and
        // the boss bar, shrinks when folded); before the first measure, a safe estimate.
        val hudHeightDp = with(density) { if (hudHeightPx > 0) hudHeightPx.toDp() else 96.dp }
        val topInsetPx = with(density) { (hudHeightDp + 4.dp).toPx() }
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
            renderer.draw(
                this, session.engine, time, session.skin, session.body, session.background, showDamageNumbers, topInsetPx, bottomInsetPx,
                partnerSkin = session.partnerSkin, partnerBody = session.partnerBody, shake = screenShake
            )
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

        // Hints and banners stack under the HUD instead of sitting at fixed offsets.
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = hudHeightDp + HUD_HINT_GAP, start = 12.dp, end = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
        ) {
            if (hud.phase == Phase.PORTAL) {
                HintChip(
                    if (hud.topGateLocked) "▲ GATE LOCKED — WALK IN AGAIN TO SKIP SHOP" else "▲ GATE OPEN — WALK THROUGH TO CONTINUE",
                    if (hud.topGateLocked) Palette.Red else Palette.Green
                )
            }
            if (hud.shopGateOpen && hud.phase == Phase.PORTAL) {
                HintChip(if (hud.shopGateRight) "SHOP GATE OPEN — RIGHT WALL ►" else "◄ SHOP GATE OPEN — LEFT WALL", Palette.Gold)
            }
            if (hud.vaultReady) {
                HintChip("◆ WALK UP TO THE DATA CACHE TO CRACK IT", Palette.Gold)
            }
            // Co-op: down and waiting for the partner, or the partner is down.
            if (hud.coop && hud.downed) {
                HintChip("YOU'RE DOWN · ${hud.partnerName.ifEmpty { "PARTNER" }} CAN REVIVE YOU (${(hud.reviveProgress * 100).toInt()}%)", Palette.Red)
            } else if (hud.coop && hud.partnerDowned && !hud.partnerGone) {
                HintChip("${hud.partnerName.ifEmpty { "PARTNER" }} IS DOWN · STAND NEXT TO THEM TO REVIVE", Palette.Magenta)
            }
            session.coopNotice?.let { HintChip(it, Palette.Orange) }
            if (hud.bannerVisible && hud.banner.isNotEmpty() && hud.phase != Phase.UPGRADE) {
                Banner(hud, Modifier.padding(top = 6.dp))
            }
        }
        // Upgrade shop: buy panel at the counter, and the terminal message when a shop appears.
        if (hud.inShop && hud.atShopCounter) {
            ShopPanel(
                hud.shopItems, hud.euros,
                onBuy = { i -> if (session.shopNeedsSlot(i)) shopSwap = i else session.buyShopItem(i) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                onHold = { shopHeld = it },
                slotsFull = { session.shopNeedsSlot(it) }
            )
        }
        // Shop: hold for details; a new weapon with full slots goes through the swap grid.
        val heldItem = shopHeld?.let { hud.shopItems.getOrNull(it) }
        if (heldItem != null && hud.inShop) {
            val idx = shopHeld!!
            UpgradeDetailSheet(
                heldItem.def, heldItem.nextLevel, session.previewLines(heldItem.def),
                actionLabel = if (heldItem.sold) "SOLD" else "BUY €${heldItem.price}",
                actionEnabled = !heldItem.sold && hud.euros >= heldItem.price,
                onAction = {
                    shopHeld = null
                    if (session.shopNeedsSlot(idx)) shopSwap = idx else session.buyShopItem(idx)
                },
                onBack = { shopHeld = null }
            )
        }
        val swapItem = shopSwap?.let { hud.shopItems.getOrNull(it) }
        if (swapItem != null && hud.inShop) {
            val idx = shopSwap!!
            WeaponSwap(
                newDef = swapItem.def, newLevel = swapItem.nextLevel,
                equipped = session.equippedWeapons(),
                preview = { old -> session.previewLines(swapItem.def, old) },
                onReplace = { old -> session.buyShopItem(idx, old); shopSwap = null },
                onBack = { shopSwap = null }
            )
        }
        if (hud.skipShopPrompt) {
            SkipShopDialog(
                onNo = { session.answerSkipShop(false) },
                onYes = { session.answerSkipShop(true) },
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 28.dp)
            )
        }
        BossDossierCard(hud, Modifier.align(Alignment.Center).padding(horizontal = 12.dp))
        ShopMessage(hud.shopMessageSerial, hud.shopGateRight, Modifier.align(Alignment.Center).padding(horizontal = 20.dp), hidden = hud.skipShopPrompt)
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
                coop = session.isCoop,
                topPadding = hudHeightDp,
                audio = session.audio,
                saveBlocked = hud.saveBlockReason,
                onResume = { session.paused = false },
                onSave = { if (session.saveAndExit()) onExitToMenu() },
                onQuit = { session.finish(); onExitToMenu() }
            )
        }
        if (session.showTutorial) {
            TutorialOverlay(onDone = { session.dismissTutorial() })
        }
        // Drawn last so it stays readable over the pause menu; full-screen panels
        // (upgrade pick, game over, briefing) cover it as before.
        val coveredByPanel = hud.phase == Phase.UPGRADE || hud.phase == Phase.DEAD || session.showTutorial
        if (session.paused || !coveredByPanel) {
            GameHud(
                hud,
                paused = session.paused,
                collapsed = hudCollapsed,
                onToggleCollapsed = { hudCollapsed = !hudCollapsed },
                onPause = { if (hud.phase != Phase.DEAD) session.paused = !session.paused },
                modifier = Modifier.onSizeChanged { hudHeightPx = it.height }
            )
        }
    }
}

@Composable
private fun HintChip(text: String, color: Color) {
    Text(
        text, color = color, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
        modifier = Modifier
            .background(Palette.Background.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun Banner(h: HudSnapshot, modifier: Modifier) {
    val color = when (h.kind) {
        LevelKind.BOSS -> Palette.Red
        LevelKind.EVENT -> Color(h.eventAccent)
        LevelKind.NORMAL -> Palette.Cyan
        LevelKind.SHOP -> Palette.Gold
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
private fun PauseOverlay(
    coop: Boolean,
    topPadding: androidx.compose.ui.unit.Dp,
    audio: com.cyberoperative.game.audio.AudioManager,
    saveBlocked: String?,
    onResume: () -> Unit,
    onSave: () -> Unit,
    onQuit: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(enabled = true, onClick = {})
            .padding(top = topPadding),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .background(Palette.Surface, RoundedCornerShape(10.dp))
                .border(1.dp, Palette.Cyan, RoundedCornerShape(10.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(if (coop) "CO-OP MENU" else "OPERATION PAUSED", color = Palette.Cyan, style = MaterialTheme.typography.titleLarge)
            if (coop) Text("Co-op can't be paused · the fight continues", color = Palette.Orange, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(12.dp))
            com.cyberoperative.game.ui.common.CyberButton("RESUME", Modifier.fillMaxWidth(), accent = Palette.Green, primary = true) { onResume() }
            Spacer(Modifier.height(12.dp))
            MusicPlayerPanel(audio, Modifier.weight(1f, fill = false))
            Spacer(Modifier.height(12.dp))
            if (saveBlocked == null) {
                com.cyberoperative.game.ui.common.CyberButton(
                    "SAVE & EXIT TO MENU", Modifier.fillMaxWidth(), accent = Palette.Cyan,
                    subtitle = "CONTINUE FROM HERE LATER"
                ) { onSave() }
            } else {
                // Boss fights can't be saved; the button is shown locked so the rule is clear.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Palette.Background, RoundedCornerShape(8.dp))
                        .border(1.dp, Palette.Red.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(saveBlocked, color = Palette.Red, style = MaterialTheme.typography.titleSmall)
                    Text(
                        when {
                            saveBlocked.contains("BOSS") -> "Defeat the boss to unlock saving"
                            saveBlocked.contains("CO-OP") -> "Rewards are banked when the run ends"
                            else -> "Try again in a moment"
                        },
                        color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            com.cyberoperative.game.ui.common.CyberButton("ABORT OPERATION", Modifier.fillMaxWidth(), accent = Palette.Red) { onQuit() }
            Spacer(Modifier.height(4.dp))
            Text("Abort ends the run. Progress and € earned so far are kept.", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}


/** Characters a corrupted boss name flickers through before it resolves. */
private const val GLITCH_CHARS = "#%&@!?01<>/\\=+*$"

/**
 * The big boss bar (owner: "SHOW BIG BOSS health bar at top"), with the
 * entrance from [com.cyberoperative.game.engine.BossBrain]: the bar grows out
 * from the centre while it fills, the name glitches in, the bar shakes on the
 * growl, then the fight starts. Phase thresholds are marked at 60% and 25% and
 * a white trail drains behind each hit.
 */
@Composable
internal fun BossHealthBar(h: HudSnapshot) {
    val color = if (h.bossColor != 0L) Color(h.bossColor) else Palette.Red
    val intro = h.bossIntro
    val barEnd = com.cyberoperative.game.engine.BossBrain.INTRO_BAR_END
    val nameEnd = com.cyberoperative.game.engine.BossBrain.INTRO_NAME_END
    val growl = com.cyberoperative.game.engine.BossBrain.INTRO_GROWL_AT
    val introRunning = intro >= 0f
    // 0..1: how far the bar has grown/filled during the entrance.
    val grow = if (introRunning) (intro / barEnd).coerceIn(0f, 1f) else 1f
    val eased = grow * grow * (3f - 2f * grow)
    val fraction = if (introRunning) eased else h.bossPercent / 100f
    val trail by androidx.compose.animation.core.animateFloatAsState(
        targetValue = fraction,
        animationSpec = if (introRunning) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(durationMillis = 650, delayMillis = 250),
        label = "bossTrail"
    )
    val name = h.bossName ?: ""
    val shownName = when {
        !introRunning || intro >= nameEnd -> name
        intro < barEnd -> ""
        else -> {
            // Characters resolve left to right; the rest flicker through glitch symbols.
            val p = (intro - barEnd) / (nameEnd - barEnd)
            val revealed = (p * name.length).toInt()
            val seed = (intro * 30f).toInt()
            buildString {
                name.forEachIndexed { i, c ->
                    append(if (i < revealed || c == ' ') c else GLITCH_CHARS[(seed * 7 + i * 13) % GLITCH_CHARS.length])
                }
            }
        }
    }
    val shaking = introRunning && intro >= growl && intro < growl + 0.6f
    val shakeX = if (shaking) (if ((intro * 60f).toInt() % 2 == 0) 5f else -5f) else 0f
    val flicker = if (shaking && (intro * 40f).toInt() % 3 == 0) 0.45f else 1f
    Column(
        Modifier
            .fillMaxWidth()
            .offset(x = shakeX.dp)
            .background(Color(0xE6120208), RoundedCornerShape(8.dp))
            .border(2.dp, color.copy(alpha = 0.85f * flicker), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (shownName.isEmpty()) "" else h.bossTag, color = color.copy(alpha = flicker), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Text(
                if (shownName.isEmpty()) "!! WARNING !!" else shownName,
                color = if (shownName.isEmpty()) color.copy(alpha = if ((intro * 6f).toInt() % 2 == 0) 1f else 0.4f) else Palette.TextPrimary.copy(alpha = flicker),
                style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f), maxLines = 1
            )
            if (!introRunning) Text("${h.bossPercent}%", color = color, style = MaterialTheme.typography.titleLarge)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (introRunning && intro < nameEnd) "" else h.bossTitle.uppercase(),
                color = Palette.TextSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), maxLines = 1
            )
            Text(if (introRunning) (if (intro >= growl) "ENGAGING…" else "") else h.bossPhase, color = color, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(6.dp))
        androidx.compose.foundation.Canvas(
            Modifier
                .fillMaxWidth()
                .height(26.dp)
        ) {
            val r = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
            // During the entrance the frame itself grows out from the centre.
            val frameW = size.width * (if (introRunning) eased.coerceAtLeast(0.02f) else 1f)
            val left = (size.width - frameW) / 2f
            val o = androidx.compose.ui.geometry.Offset(left, 0f)
            drawRoundRect(Color(0xFF26060C), topLeft = o, size = androidx.compose.ui.geometry.Size(frameW, size.height), cornerRadius = r)
            if (introRunning) {
                drawRoundRect(
                    androidx.compose.ui.graphics.Brush.verticalGradient(listOf(color, color.copy(red = color.red * 0.55f, green = color.green * 0.55f, blue = color.blue * 0.55f))),
                    topLeft = o, size = androidx.compose.ui.geometry.Size(frameW, size.height), cornerRadius = r, alpha = flicker
                )
            } else {
                if (trail > fraction) {
                    drawRoundRect(Color.White.copy(alpha = 0.75f), size = androidx.compose.ui.geometry.Size(size.width * trail, size.height), cornerRadius = r)
                }
                drawRoundRect(
                    androidx.compose.ui.graphics.Brush.verticalGradient(listOf(color, color.copy(red = color.red * 0.55f, green = color.green * 0.55f, blue = color.blue * 0.55f))),
                    size = androidx.compose.ui.geometry.Size(size.width * fraction, size.height), cornerRadius = r
                )
                for (t in floatArrayOf(0.6f, 0.25f)) {
                    val x = size.width * t
                    drawLine(Color.Black.copy(alpha = 0.8f), androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 3.dp.toPx())
                    drawLine(Color.White.copy(alpha = 0.5f), androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 1.dp.toPx())
                }
            }
            drawRoundRect(color.copy(alpha = 0.9f * flicker), topLeft = o, size = androidx.compose.ui.geometry.Size(frameW, size.height), cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        }
        if (h.bossIceShell >= 0) {
            Text(
                "❄ PERMAFROST SHELL ${h.bossIceShell}% — KEEP FIRING",
                color = Color(0xFF9AE6FF),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (h.bossRing >= 0) {
            // Spectral Firewall: its plates soak up your fire unless you shoot through a gap.
            val up = h.bossRing == 1
            Text(
                if (up) "🔥 RING UP — FIRE THROUGH THE GAPS" else "💥 RING LAUNCHED — CORE EXPOSED",
                color = if (up) Color(0xFFFF7A1A) else Color(0xFFFFD45A),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (h.bossShield >= 0) {
            // Ransom King: shielded until the key zones are captured.
            val up = h.bossShield == 1
            Text(
                if (up) "🔒 ENCRYPTED — CAPTURE THE KEY ZONES" else "🔓 DECRYPTED — HIT HIM NOW",
                color = if (up) Color(0xFFFFC233) else Color(0xFFFF3B3B),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (h.bossLock >= 0) {
            // Nullshade: you can only lock on while its eyes are open.
            val open = h.bossLock == 1
            Text(
                if (open) "◉ EYES OPEN — LOCK ON" else "◌ IN THE SHADOWS — NO LOCK",
                color = if (open) Color(0xFFFF2A3A) else Palette.TextMuted,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
