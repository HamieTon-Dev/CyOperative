package com.cyberoperative.game.ui.game

import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.widthIn
import com.cyberoperative.game.platform.PlatformBackHandler as BackHandler
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
import androidx.compose.foundation.verticalScroll
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
import com.cyberoperative.game.platform.GameKeys
import com.cyberoperative.game.platform.platformGestureExclusion
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
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
    com.cyberoperative.game.platform.ImmersiveGameplay()
    com.cyberoperative.game.platform.KeepScreenOn()
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

    // PC: mouse position (screen px) and whether a button is held (fires at the cursor).
    val mouse = remember { FloatArray(2) }
    var mouseDown by remember { mutableStateOf(false) }
    var keysMoving by remember { mutableStateOf(false) }
    /** Campaign world map overlay ([M], controller BACK, MAP button). */
    var showMap by remember { mutableStateOf(false) }
    val renderer = remember { ArenaRenderer() }

    // Frame loop: lives with the screen; leaving the composition stops it.
    var time by remember { mutableFloatStateOf(0f) }
    DisposableEffect(Unit) {
        GameKeys.inGame = true
        onDispose { GameKeys.inGame = false }
    }
    LaunchedEffect(session) {
        var last = withFrameNanos { it }
        val start = last
        while (true) {
            withFrameNanos { now ->
                val dt = (now - last) / 1_000_000_000f
                last = now
                time = (now - start) / 1_000_000_000f
                if (!GameKeys.active && session.engine.config.consumables) {
                    // Touch: spells land where the operative is facing (its gun turns to the nearest threat).
                    val e = session.engine
                    e.setAim(false, e.localX + kotlin.math.cos(e.localFacing) * 300f, e.localY + kotlin.math.sin(e.localFacing) * 300f)
                }
                if (GameKeys.active) {
                    val e = session.engine
                    val live = !session.paused && e.phase != Phase.UPGRADE && e.phase != Phase.DEAD
                    val kx = GameKeys.x
                    val ky = GameKeys.y
                    val keys = live && (kx != 0f || ky != 0f)
                    if (keys) e.setInput(kx, ky) else if (keysMoving) e.setInput(0f, 0f)
                    keysMoving = keys
                    if (GameKeys.stickAim) {
                        // Twin-stick: the right stick aims and fires around the operative.
                        e.setAim(live, e.localX + GameKeys.aimX * 300f, e.localY + GameKeys.aimY * 300f)
                    } else {
                        val sc = renderer.scale.coerceAtLeast(0.0001f)
                        e.setAim(live && mouseDown, mouse[0] / sc + renderer.camX, mouse[1] / sc + renderer.camY)
                    }
                    if (session.showTutorial && GameKeys.takeAnyPress()) session.dismissTutorial()
                    // Q / LB: HP charge, E / RB: Packet Bomb (presses are consumed even when unusable).
                    val hpKey = GameKeys.takeHpCharge()
                    val bombKey = GameKeys.takePacketBomb()
                    if (live && hpKey) session.useHpCharge()
                    if (live && bombKey) session.usePacketBomb()
                    if (GameKeys.takeMap() && e.campaign) showMap = !showMap
                    val dashKey = GameKeys.takeDash()
                    val r = GameKeys.takeSpell(0)
                    val f = GameKeys.takeSpell(1)
                    if (live && dashKey) session.dash()
                    if (live && r) session.castSpell(0)
                    if (live && f) session.castSpell(1)
                }
                session.onFrame(dt)
            }
        }
    }

    BackHandler {
        if (session.hud.phase != Phase.DEAD) session.paused = !session.paused
    }

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
    /** The shop window is open (opened with OPEN SHOP at the counter, closed with X / back / walking off). */
    var shopOpen by remember { mutableStateOf(false) }
    // Back / Esc closes the shop first (registered after the pause handler, so it wins).
    BackHandler(enabled = shopOpen) { shopOpen = false }
    var shopSwap by remember { mutableStateOf<Int?>(null) }


    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Palette.SurfaceSunken)
    ) {
        // The arena starts right under the HUD as measured (it grows with the buff row and
        // the boss bar, shrinks when folded); before the first measure, a safe estimate.
        val hudHeightDp = with(density) { if (hudHeightPx > 0) hudHeightPx.toDp() else 96.dp }
        // PC: the canvas itself starts below the HUD strip, so no inset inside it.
        val landscape = com.cyberoperative.game.ui.common.PlayMode.landscape
        val topInsetPx = with(density) { if (landscape) 0f else (hudHeightDp + 4.dp).toPx() }
        val bottomInsetPx = with(density) { (if (landscape) 24.dp else 150.dp).toPx() }
        val restX = constraints.maxWidth / 2f
        val restY = constraints.maxHeight - with(density) { 110.dp.toPx() }
        // Touch layout (Settings → TOUCH CONTROLS): locked joystick spot, and the PC-mode buttons.
        val touchSettings = session.settings
        val stickLocked = !GameKeys.active && TouchLayout.stickLocked(touchSettings, landscape)
        val lockPos = TouchLayout.place(touchSettings, TouchControl.STICK, landscape, maxWidth.value, maxHeight.value)
        val lockX = lockPos.x * constraints.maxWidth
        // The canvas starts under the HUD strip in landscape: its touches are relative to that.
        val lockY = lockPos.y * constraints.maxHeight - (if (landscape) hudHeightPx.toFloat() else 0f)

        Canvas(
            Modifier
                .fillMaxSize()
                // PC: the arena lives below the HUD strip and never draws under it.
                .then(if (landscape) Modifier.padding(top = hudHeightDp).clipToBounds() else Modifier)
                // Steering thumbs near the side edges shouldn't trigger the system Back swipe.
                // Android honours up to 200dp per edge, bottom-most first: where the stick lives.
                .platformGestureExclusion()
                .pointerInput(session, "mouse") {
                    if (!GameKeys.active) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val c = awaitPointerEvent().changes.firstOrNull() ?: continue
                            mouse[0] = c.position.x
                            mouse[1] = c.position.y
                            mouseDown = c.pressed
                        }
                    }
                }
                .pointerInput(session, stickLocked, lockX, lockY) {
                    if (GameKeys.active) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (session.paused || session.engine.phase == Phase.UPGRADE || session.engine.phase == Phase.DEAD) return@awaitEachGesture
                        // Locked joystick: only touches on (or near) it steer, and it never moves.
                        if (stickLocked && kotlin.math.hypot(down.position.x - lockX, down.position.y - lockY) > stickRadiusPx * 2.2f) return@awaitEachGesture
                        stickActive = true
                        originX = if (stickLocked) lockX else down.position.x
                        originY = if (stickLocked) lockY else down.position.y
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
                            if (d > stickRadiusPx && stickLocked) {
                                dx = dx / d * stickRadiusPx
                                dy = dy / d * stickRadiusPx
                            } else if (d > stickRadiusPx) {
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
            if (GameKeys.active) {
                // PC: the mouse is the neon arrow cursor itself (system cursor). With a controller,
                // the same arrow marks where the right stick aims.
                if (GameKeys.stickAim) {
                    val e = session.engine
                    with(com.cyberoperative.game.ui.common.CursorArt) {
                        drawCursor(
                            (e.localX + GameKeys.aimX * 160f - renderer.camX) * renderer.scale,
                            (e.localY + GameKeys.aimY * 160f - renderer.camY) * renderer.scale,
                            34f * density.density, pressed = true
                        )
                    }
                }
                return@Canvas
            }
            // Joystick
            // At rest: its locked spot, or (PC-mode buttons on screen) its layout spot, clear of them.
            val atLayout = stickLocked || (!GameKeys.active && session.engine.config.consumables)
            val bx = if (stickActive) originX else if (atLayout) lockX else restX
            val by = if (stickActive) originY else if (atLayout) lockY else restY
            drawCircle(Palette.Cyan.copy(alpha = if (stickActive) 0.16f else 0.07f), stickRadiusPx, Offset(bx, by))
            drawCircle(Palette.Cyan.copy(alpha = if (stickActive) 0.6f else 0.25f), stickRadiusPx, Offset(bx, by), style = Stroke(3f))
            val kx = if (stickActive) knobX else bx
            val ky = if (stickActive) knobY else by
            drawCircle(Palette.Cyan.copy(alpha = if (stickActive) 0.85f else 0.3f), stickRadiusPx * 0.42f, Offset(kx, ky))
        }

        if (!GameKeys.active && session.engine.config.consumables && !session.paused && hud.phase != Phase.DEAD && hud.phase != Phase.UPGRADE) {
            TouchActionButtons(touchSettings, landscape, hud) { c ->
                when (c) {
                    TouchControl.DASH -> session.dash()
                    TouchControl.SPELL1 -> session.castSpell(0)
                    TouchControl.SPELL2 -> session.castSpell(1)
                    TouchControl.HP -> session.useHpCharge()
                    TouchControl.BOMB -> session.usePacketBomb()
                    TouchControl.STICK -> {}
                }
            }
        }
        if (!stickActive && hud.phase == Phase.COMBAT && hud.level == 1 && time < 12f) {
            Text(
                if (GameKeys.padConnected) "LEFT STICK TO MOVE · RIGHT STICK TO AIM & FIRE" else if (GameKeys.active) "WASD TO MOVE · HOLD MOUSE TO FIRE · STAND STILL TO AUTO-AIM" else "DRAG TO MOVE · RELEASE TO FIRE",
                color = Palette.TextSecondary, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 18.dp)
            )
        }

        // Hints and banners stack under the HUD instead of sitting at fixed offsets.
        // Measured so the pickup line can sit below them (owner: they overlapped).
        var hintsHeightPx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
        val mapButtonShown = hud.campaign && !GameKeys.active && !session.paused && hud.phase != Phase.DEAD
        Column(
            Modifier
                .align(Alignment.TopCenter)
                // Clear of the MAP button on both sides (stays centred) while it shows.
                .padding(
                    top = hudHeightDp + HUD_HINT_GAP,
                    start = if (mapButtonShown) 84.dp else 12.dp, end = if (mapButtonShown) 84.dp else 12.dp
                )
                .onSizeChanged { hintsHeightPx = it.height },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
        ) {
            if (hud.phase == Phase.PORTAL) {
                HintChip(
                    if (hud.topGateLocked) "▲ GATE LOCKED — WALK IN AGAIN TO SKIP SHOP" else hud.doorHint.ifEmpty { "▲ GATE OPEN — WALK THROUGH TO CONTINUE" },
                    if (hud.topGateLocked) Palette.Red else Palette.Green
                )
            }
            // Co-op shop: both leave together through the exit gate.
            if (hud.inShop && hud.coop && hud.shopReadyCount > 0) {
                HintChip(
                    if (hud.meShopReady) "WAITING FOR ${hud.partnerName.ifEmpty { "PARTNER" }} · READY TO LEAVE ${hud.shopReadyCount}/${hud.shopReadyTotal}"
                    else "${hud.partnerName.ifEmpty { "PARTNER" }} FINISHED SHOPPING · READY TO LEAVE ${hud.shopReadyCount}/${hud.shopReadyTotal} · WALK TO THE GATE",
                    Palette.Gold
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
        // Upgrade shop: at the counter an OPEN SHOP button; the shop is a small window in the
        // middle of the screen with an X to close it (also back / Esc, or walking away).
        val atCounter = hud.inShop && hud.atShopCounter
        LaunchedEffect(atCounter) { if (!atCounter) shopOpen = false }
        if (atCounter && !shopOpen) {
            OpenShopButton(
                onOpen = { shopOpen = true },
                modifier = Modifier.align(Alignment.Center).padding(top = 160.dp)
            )
        }
        if (atCounter && shopOpen) {
            ShopPanel(
                hud.shopItems, hud.euros,
                onBuy = { i -> if (session.shopNeedsSlot(i)) shopSwap = i else session.buyShopItem(i) },
                onClose = { shopOpen = false },
                // Above every other overlay (terminal message, pickup line, hints).
                modifier = Modifier.zIndex(10f).padding(top = hudHeightDp),
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
        // Android: a see-through MAP button under the HUD (campaign only).
        if (mapButtonShown) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = hudHeightDp + 8.dp, end = 10.dp)
                    .graphicsLayer { alpha = 0.6f }
                    .background(Color(0xCC05080F), RoundedCornerShape(8.dp))
                    .border(1.5.dp, Palette.Cyan, RoundedCornerShape(8.dp))
                    .clickable { showMap = !showMap }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) { Text("MAP", color = Palette.Cyan, style = MaterialTheme.typography.labelLarge) }
        }
        if (showMap && hud.campaign) {
            WorldMapOverlay(hud, session.engine.worldMap, time) { showMap = false }
        }
        // World 15's boss is down: carry on in Endless, or end the run as a win.
        if (hud.campaignComplete) {
            CampaignCompleteDialog(
                onEndless = { session.answerCampaignComplete(true) },
                onFinish = { session.answerCampaignComplete(false); session.finish(); onExitToMenu() }
            )
        }
        // Upgrade chips: the line naming what was just picked up, under the HUD.
        PickupLine(
            hud.pickupName, hud.pickupRarity, hud.pickupSerial,
            // Just under the HUD, below any hint chips ("NEXT ROOM ▶ …"), and under the level
            // banner while one is up.
            Modifier.align(Alignment.TopCenter).padding(
                top = hudHeightDp + maxOf(
                    if (hud.bannerVisible && hud.banner.isNotEmpty()) 120.dp else 8.dp,
                    if (hintsHeightPx > 0) HUD_HINT_GAP + with(density) { hintsHeightPx.toDp() } + 10.dp else 0.dp
                )
            )
        )
        // A weapon chip with every slot taken, once the room is clear: swap, or keep yours (it's
        // thrown to the middle of the arena).
        val chipDef = hud.swapChipId?.let { com.cyberoperative.game.data.Upgrades.byIdOrNull(it) }
        if (chipDef != null) {
            WeaponSwap(
                newDef = chipDef, newLevel = 1,
                equipped = session.equippedWeapons(),
                preview = { old -> session.previewLines(chipDef, old) },
                onReplace = { old -> session.answerChipSwap(old) },
                onBack = { session.answerChipSwap(null) }
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
        VictoryCard(hud, onSkip = { session.skipVictory() })
        ShopMessage(hud.shopMessageSerial, hud.shopGateRight, Modifier.align(Alignment.Center).padding(horizontal = 20.dp), hidden = hud.skipShopPrompt || shopOpen)
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
                session = session,
                owned = hud.owned,
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
            TutorialOverlay(pcRun = session.engine.config.consumables, onDone = { session.dismissTutorial() })
        }
        // Drawn last so it stays readable over the pause menu; full-screen panels
        // (upgrade pick, game over, briefing) cover it as before.
        val coveredByPanel = hud.phase == Phase.UPGRADE || hud.phase == Phase.DEAD || session.showTutorial
        if (session.paused || !coveredByPanel) {
          // PC: a compact strip above the arena (never over it), everything at 75%.
          if (com.cyberoperative.game.ui.common.PlayMode.landscape) androidx.compose.runtime.CompositionLocalProvider(
              LocalDensity provides androidx.compose.ui.unit.Density(density.density * HUD_PC_SCALE, density.fontScale)
          ) {
            // Android landscape gets the slim strip (owner, 2026-10-12); the PC keeps its key chips.
            if (com.cyberoperative.game.platform.AppInfo.desktop) PcHudStrip(
                hud, paused = session.paused,
                onPause = { if (hud.phase != Phase.DEAD) session.paused = !session.paused },
                modifier = Modifier.onSizeChanged { hudHeightPx = it.height }
            ) else CompactHudStrip(
                hud, paused = session.paused,
                onPause = { if (hud.phase != Phase.DEAD) session.paused = !session.paused },
                modifier = Modifier.onSizeChanged { hudHeightPx = it.height }
            )
          } else run {
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
}

/** HUD scale on PC (compact bars over a zoomed-out arena). */
private const val HUD_PC_SCALE = 0.75f

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
private fun TutorialOverlay(pcRun: Boolean, onDone: () -> Unit) {
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
            for (line in if (GameKeys.active) TUTORIAL_LINES_PC else if (pcRun) TUTORIAL_LINES_TOUCH_PC else TUTORIAL_LINES) {
                Text("> $line", color = Palette.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    GameKeys.padConnected -> "PRESS ANY BUTTON TO BEGIN"
                    GameKeys.active -> "CLICK TO BEGIN"
                    else -> "TAP TO BEGIN"
                }, color = Palette.Green, style = MaterialTheme.typography.titleMedium,
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

/** Android PC mode briefing: the joystick plus the on-screen buttons. */
val TUTORIAL_LINES_TOUCH_PC = listOf(
    "Move with the joystick; your gun fires on its own.",
    "Buttons: DASH, your two class SPELLS, HP CHARGE and PACKET BOMB.",
    "Upgrades drop from threats as glowing chips: walk over them.",
    "Threats drop € as cash bags; a cleared room pulls them in.",
    "Defeat all threats, then pick an exit door.",
    "A boss appears every 10 levels."
)

/** The PC briefing: keyboard + mouse, or a controller. */
val TUTORIAL_LINES_PC = listOf(
    "Move with WASD (or the left stick).",
    "Hold the mouse button (or the right stick) to fire while moving.",
    "Stand still and your gun picks targets on its own.",
    "Orbiting Packet Nodes attack all the time.",
    "Avoid red/orange enemy attacks.",
    "Defeat all threats, then enter the ACCESS PORT.",
    "Choose an upgrade when you gain enough data.",
    "A boss appears every 10 levels.  ESC / START pauses."
)

/** Pause menu tabs (CO-065: current build and a settings shortcut). */
private enum class PauseTab(val label: String) { MENU("MENU"), BUILD("BUILD"), SETTINGS("SETTINGS") }

@Composable
private fun PauseOverlay(
    session: GameSession,
    owned: List<Pair<String, Int>>,
    coop: Boolean,
    topPadding: androidx.compose.ui.unit.Dp,
    audio: com.cyberoperative.game.audio.AudioManager,
    saveBlocked: String?,
    onResume: () -> Unit,
    onSave: () -> Unit,
    onQuit: () -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(PauseTab.MENU) }
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
            Spacer(Modifier.height(10.dp))
            // Tabs.
            Row(Modifier.fillMaxWidth()) {
                for (t in PauseTab.entries) {
                    val on = t == tab
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable { tab = t; session.audio.play(com.cyberoperative.game.engine.GameSound.UI_CLICK) }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(t.label, color = if (on) Palette.Cyan else Palette.TextMuted, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth().height(2.dp).background(if (on) Palette.Cyan else Palette.Divider))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            when (tab) {
                PauseTab.MENU -> PauseMenuTab(audio, saveBlocked, onResume, onSave, onQuit)
                PauseTab.BUILD -> PauseBuildTab(session.engine.stats, owned, onResume)
                PauseTab.SETTINGS -> PauseSettingsTab(session, onResume)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.PauseMenuTab(
    audio: com.cyberoperative.game.audio.AudioManager,
    saveBlocked: String?,
    onResume: () -> Unit,
    onSave: () -> Unit,
    onQuit: () -> Unit
) {
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

/** BUILD: the operative's key stats and every module owned this run. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.PauseBuildTab(
    s: com.cyberoperative.game.engine.RunStats,
    owned: List<Pair<String, Int>>,
    onResume: () -> Unit
) {
    val stats = listOf(
        "DAMAGE" to "%.0f".format(s.damage),
        "FIRE RATE" to "%.1f/s".format(s.fireRate),
        "CRIT" to "${(s.critChance * 100).toInt()}%",
        "RANGE" to "%.0f".format(s.range),
        "MAX HP" to "%.0f".format(s.maxHp),
        "ARMOR" to "${(s.armor * 100).toInt()}%",
        "FIREWALL" to "%.0f".format(s.firewallMax),
        "DODGE" to "${(s.dodge * 100).toInt()}%",
        "MOVE" to "%.0f".format(s.moveSpeed)
    )
    Column(
        Modifier
            .fillMaxWidth()
            .weight(1f, fill = false)
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
    ) {
        for (row in stats.chunked(3)) {
            Row(Modifier.fillMaxWidth()) {
                for ((k, v) in row) {
                    Column(Modifier.weight(1f).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(v, color = Palette.TextPrimary, style = MaterialTheme.typography.titleMedium)
                        Text(k, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("MODULES (${owned.size})", color = Palette.Cyan, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        if (owned.isEmpty()) Text("No modules yet — clear a room to pick one.", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
        for ((id, lvl) in owned) {
            val u = com.cyberoperative.game.data.Upgrades.byId(id)
            val c = Color(u.rarity.color)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .background(Palette.Background.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .border(1.dp, c.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(u.glyph, color = c, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(44.dp))
                Column(Modifier.weight(1f)) {
                    Text(u.name, color = c, style = MaterialTheme.typography.labelLarge)
                    Text(u.description, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                }
                if (lvl > 0) Text("LV $lvl", color = Palette.TextSecondary, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    com.cyberoperative.game.ui.common.CyberButton("RESUME", Modifier.fillMaxWidth(), accent = Palette.Green, primary = true) { onResume() }
}

/** SETTINGS shortcut: volumes and the in-game toggles, without leaving the run. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.PauseSettingsTab(session: GameSession, onResume: () -> Unit) {
    var s by remember { mutableStateOf(session.settings) }
    fun set(next: com.cyberoperative.game.save.GameSettings) { s = next; session.updateSettings(next) }
    val sliderColors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Palette.Cyan, activeTrackColor = Palette.Cyan, inactiveTrackColor = Palette.Divider)
    Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
        Text("MUSIC VOLUME ${(s.musicVolume * 100).toInt()}%", color = Palette.TextPrimary, style = MaterialTheme.typography.labelLarge)
        androidx.compose.material3.Slider(s.musicVolume, { set(s.copy(musicVolume = it)) }, colors = sliderColors)
        Text("EFFECTS VOLUME ${(s.sfxVolume * 100).toInt()}%", color = Palette.TextPrimary, style = MaterialTheme.typography.labelLarge)
        androidx.compose.material3.Slider(
            s.sfxVolume, { set(s.copy(sfxVolume = it)) },
            onValueChangeFinished = { session.audio.play(com.cyberoperative.game.engine.GameSound.UI_CLICK) }, colors = sliderColors
        )
        Spacer(Modifier.height(6.dp))
        PauseToggle("HAPTICS", s.haptics) { set(s.copy(haptics = it)) }
        PauseToggle("DAMAGE NUMBERS", s.damageNumbers) { set(s.copy(damageNumbers = it)) }
        PauseToggle("SCREEN SHAKE", s.screenShake) { set(s.copy(screenShake = it)) }
    }
    Spacer(Modifier.height(12.dp))
    com.cyberoperative.game.ui.common.CyberButton("RESUME", Modifier.fillMaxWidth(), accent = Palette.Green, primary = true) { onResume() }
}

@Composable
private fun PauseToggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Palette.TextPrimary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(
            value, onChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedThumbColor = Palette.Background, checkedTrackColor = Palette.Green)
        )
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
internal fun BossHealthBar(h: HudSnapshot, compact: Boolean = false) {
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
            .then(
                // Compact (Android landscape, owner 2026-10-12): no box, just name line and a thin bar.
                if (compact) Modifier.padding(top = 2.dp)
                else Modifier
                    .background(Color(0xE6120208), RoundedCornerShape(8.dp))
                    .border(2.dp, color.copy(alpha = 0.85f * flicker), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
    ) {
        if (compact) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (shownName.isEmpty()) "!! WARNING !!" else shownName,
                color = if (shownName.isEmpty()) color.copy(alpha = if ((intro * 6f).toInt() % 2 == 0) 1f else 0.4f) else Palette.TextPrimary.copy(alpha = flicker),
                style = mono(14.sp, FontWeight.Bold), maxLines = 1, modifier = Modifier.weight(1f)
            )
            Text(if (introRunning) (if (intro >= growl) "ENGAGING…" else "") else h.bossPhase, color = color, style = mono(11.sp, FontWeight.Bold), maxLines = 1, modifier = Modifier.weight(1f))
            if (!introRunning) Text("${h.bossPercent}%", color = color, style = mono(14.sp, FontWeight.Bold))
        } else Row(verticalAlignment = Alignment.Bottom) {
            Text(if (shownName.isEmpty()) "" else h.bossTag, color = color.copy(alpha = flicker), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Text(
                if (shownName.isEmpty()) "!! WARNING !!" else shownName,
                color = if (shownName.isEmpty()) color.copy(alpha = if ((intro * 6f).toInt() % 2 == 0) 1f else 0.4f) else Palette.TextPrimary.copy(alpha = flicker),
                style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f), maxLines = 1
            )
            if (!introRunning) Text("${h.bossPercent}%", color = color, style = MaterialTheme.typography.titleLarge)
        }
        if (!compact) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (introRunning && intro < nameEnd) "" else h.bossTitle.uppercase(),
                color = Palette.TextSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), maxLines = 1
            )
            Text(if (introRunning) (if (intro >= growl) "ENGAGING…" else "") else h.bossPhase, color = color, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(if (compact) 3.dp else 6.dp))
        androidx.compose.foundation.Canvas(
            Modifier
                .fillMaxWidth()
                .height(if (compact) 11.dp else 26.dp)
        ) {
            val r = androidx.compose.ui.geometry.CornerRadius((if (compact) 3.dp else 6.dp).toPx())
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
                style = if (compact) mono(10.sp, FontWeight.Bold) else MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = if (compact) 1.dp else 4.dp)
            )
        }
        if (h.bossRing >= 0) {
            // Spectral Firewall: its plates soak up your fire unless you shoot through a gap.
            val up = h.bossRing == 1
            Text(
                if (up) "🔥 RING UP — FIRE THROUGH THE GAPS" else "💥 RING LAUNCHED — CORE EXPOSED",
                color = if (up) Color(0xFFFF7A1A) else Color(0xFFFFD45A),
                style = if (compact) mono(10.sp, FontWeight.Bold) else MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = if (compact) 1.dp else 4.dp)
            )
        }
        if (h.bossShield >= 0) {
            // Ransom King: shielded until the key zones are captured.
            val up = h.bossShield == 1
            Text(
                if (up) "🔒 ENCRYPTED — CAPTURE THE KEY ZONES" else "🔓 DECRYPTED — HIT HIM NOW",
                color = if (up) Color(0xFFFFC233) else Color(0xFFFF3B3B),
                style = if (compact) mono(10.sp, FontWeight.Bold) else MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = if (compact) 1.dp else 4.dp)
            )
        }
        if (h.bossLock >= 0) {
            // Nullshade: you can only lock on while its eyes are open.
            val open = h.bossLock == 1
            Text(
                if (open) "◉ EYES OPEN — LOCK ON" else "◌ IN THE SHADOWS — NO LOCK",
                color = if (open) Color(0xFFFF2A3A) else Palette.TextMuted,
                style = if (compact) mono(10.sp, FontWeight.Bold) else MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = if (compact) 1.dp else 4.dp)
            )
        }
    }
}

/**
 * "MEMORY EXPANSION" in grey, "GOLDEN PROTOCOL" in shimmering gold, "MALWARE PURGE" in glowing
 * purple: the upgrade chip just picked up, in its rarity colour, with no dark panel behind it
 * (a tight dark outline keeps it legible over any floor).
 */
@Composable
private fun PickupLine(name: String, rarity: com.cyberoperative.game.data.Rarity, serial: Int, modifier: Modifier) {
    if (serial == 0 || name.isEmpty()) return
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(serial) {
        val start = withFrameNanos { it }
        t = 0f
        while (t < PICKUP_SECONDS) withFrameNanos { t = (it - start) / 1e9f }
    }
    if (t >= PICKUP_SECONDS) return
    val alpha = (if (t < 0.15f) t / 0.15f else ((PICKUP_SECONDS - t) / 0.6f)).coerceIn(0f, 1f)
    val c = Color(rarity.color)
    val tier = rarity.highTier
    val base = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 1.5.sp)
    Box(modifier.graphicsLayer { this.alpha = alpha }, contentAlignment = Alignment.Center) {
        // Glow (PURPLE and up), then the readable line with a dark outline shadow on top.
        if (tier >= 1) Text(name.uppercase(), color = c, style = base.copy(shadow = androidx.compose.ui.graphics.Shadow(c, Offset.Zero, 22f)))
        val sheen = tier >= 2
        val style = if (sheen) base.copy(
            brush = Brush.linearGradient(
                listOf(c, Color.White, c),
                start = Offset(-300f + t * 520f, 0f), end = Offset(-100f + t * 520f, 40f),
                tileMode = androidx.compose.ui.graphics.TileMode.Clamp
            ),
            shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.9f), Offset(0f, 1.5f), 4f)
        ) else base.copy(color = c, shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.9f), Offset(0f, 1.5f), 4f))
        Text(name.uppercase(), style = style)
    }
}

private const val PICKUP_SECONDS = 2.8f

/** After World 15: the campaign is done. Carry on in Endless (no map), or end the run here. */
@Composable
private fun CampaignCompleteDialog(onEndless: () -> Unit, onFinish: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp).background(Palette.Surface, RoundedCornerShape(12.dp))
                .border(2.dp, Palette.Gold, RoundedCornerShape(12.dp)).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("CAMPAIGN COMPLETE", color = Palette.Gold, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text("All ${com.cyberoperative.game.engine.CampaignMap.WORLDS} server worlds secured.", color = Palette.TextPrimary, style = MaterialTheme.typography.bodyMedium)
            Text("Continue in ENDLESS? No map: WORLD ??? · ROOM ???, doors anywhere.", color = Palette.TextSecondary, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(14.dp))
            com.cyberoperative.game.ui.common.CyberButton("CONTINUE IN ENDLESS", Modifier.fillMaxWidth(), accent = Palette.Purple, primary = true) { onEndless() }
            Spacer(Modifier.height(8.dp))
            com.cyberoperative.game.ui.common.CyberButton("END THE RUN", Modifier.fillMaxWidth(), accent = Palette.Gold) { onFinish() }
        }
    }
}
