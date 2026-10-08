package com.cyberoperative.game.ui.game

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.cyberoperative.game.core.MathUtil
import com.cyberoperative.game.data.AccentKind
import com.cyberoperative.game.data.DecorKind
import com.cyberoperative.game.data.LivingBackground
import com.cyberoperative.game.data.ObstacleKind
import com.cyberoperative.game.data.ObstacleSpec
import com.cyberoperative.game.data.OperativeSkin
import com.cyberoperative.game.data.ShapeKind
import com.cyberoperative.game.engine.AiState
import com.cyberoperative.game.engine.Enemy
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.HazardKind
import com.cyberoperative.game.engine.LevelKind
import com.cyberoperative.game.engine.Phase
import com.cyberoperative.game.engine.ProjKind
import com.cyberoperative.game.engine.TextKind
import com.cyberoperative.game.ui.game.LivingBackgroundDrawer.drawLivingBackground
import com.cyberoperative.game.ui.game.OperativeFigures.drawFigure
import com.cyberoperative.game.ui.theme.Palette
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 2.5D arena renderer (owner, 2026-10-07). The simulation stays top-down;
 * the picture gives everything height:
 *  - obstacles are extruded boxes (a lit top face over a darker front face)
 *    with a cast shadow, and occlude whatever stands behind them;
 *  - the operative and enemies stand / hover above soft floor shadows;
 *  - everything that has height is depth-sorted by its floor position.
 * Readability still comes first: floor and decor are dim and cool, hostile
 * things are warm with dark outlines, friendly things are cyan/green.
 */
class ArenaRenderer {

    private val textPaint = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val tagPaint = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val shapePath = Path()

    // Depth-sort scratch (no per-frame allocation).
    private val sortKind = IntArray(MAX_ITEMS)
    private val sortIndex = IntArray(MAX_ITEMS)
    private val sortKey = FloatArray(MAX_ITEMS)
    private var sortCount = 0

    var scale = 1f
        private set
    var camY = 0f
        private set

    fun draw(
        scope: DrawScope, g: GameEngine, time: Float, skin: OperativeSkin, body: BodyStyle,
        background: LivingBackground, showNumbers: Boolean, topInset: Float, bottomInset: Float
    ) = with(scope) {
        val arena = g.arena
        scale = size.width / arena.width
        val visibleH = size.height / scale
        val top = -topInset / scale - WALL_HEIGHT
        val bottom = arena.height + bottomInset / scale
        val desired = g.py - visibleH * 0.58f
        camY = if (bottom - top <= visibleH) (top + bottom - visibleH) / 2f
        else MathUtil.clamp(desired, top, bottom - visibleH)

        // Room-to-room slide: the old room drops away, the new one slides down from above.
        val slide = when {
            g.phase == Phase.TRANSITION -> min(1f, g.phaseTimer / GameEngine.TRANSITION_TIME) * visibleH
            g.slideIn > 0f -> -(g.slideIn / GameEngine.TRANSITION_TIME) * visibleH
            else -> 0f
        }

        drawRect(Color(0xFF04070D))
        withTransform({
            scale(scale, scale, Offset.Zero)
            translate(shakeX(g, time), -camY + slide)
        }) {
            drawFloor(g, time)
            drawLivingBackground(background, arena.width, arena.height, time, (g.aliveCount() / 16f).coerceIn(0f, 1f), g.px, g.py)
            drawDecor(g, time)
            drawTopWall(g, time)
            drawHazardsUnder(g, time)
            drawPulses(g)
            drawShadows(g)
            drawSorted(g, time, skin, body)
            drawOrbitFront(g, time)
            drawProjectiles(g, time)
            drawZaps(g, time)
            drawHazardsOver(g)
            drawParticles(g)
            if (showNumbers) drawTexts(g)
        }
        if (g.plan.kind == LevelKind.EVENT) drawSpectrumBorder(time)
        if (g.hurtFlash > 0f) drawRect(Palette.Red.copy(alpha = 0.18f * (g.hurtFlash / 0.25f)))
    }

    /** Screen shake for the boss growl. */
    private fun shakeX(g: GameEngine, time: Float): Float {
        val t = g.bossIntroElapsed
        val start = com.cyberoperative.game.engine.BossBrain.INTRO_GROWL_AT
        return if (t >= start && t < start + 0.6f) sin(time * 95f) * 7f * (1f - (t - start) / 0.6f) else 0f
    }

    // --- Floor, decor and walls -------------------------------------------

    /**
     * Metal floor panels (owner's key art, 2026-10-08): bevelled plates with
     * dark seams, every fourth seam a glowing cyan trench with packets running
     * along it, the odd grate and lit service panel, and soft light pools.
     */
    private fun DrawScope.drawFloor(g: GameEngine, time: Float) {
        val w = g.arena.width
        val h = g.arena.height
        val boss = g.plan.kind == LevelKind.BOSS
        val event = g.plan.event
        val base = when {
            boss -> Color(0xFF0C0610)
            event != null -> mix(Color(event.accent), Color(0xFF070D18), 0.06f)
            else -> Color(0xFF070D18)
        }
        val plateA = if (boss) Color(0xFF1A0E18) else Color(0xFF111D33)
        val plateB = if (boss) Color(0xFF160B14) else Color(0xFF0F1A2E)
        val hi = if (boss) Color(0x22FF6080) else Color(0x2A7DD3FF)
        val lo = Color(0x66000000)
        val accent = if (boss) Palette.Red else Palette.Cyan
        drawRect(base, Offset.Zero, Size(w, h))
        val step = TILE
        val cols = (w / step).toInt()
        val rows = (h / step).toInt() + 1
        for (r in 0 until rows) {
            val y = r * step
            for (c in 0 until cols) {
                val x = c * step
                val hash = (c * 73856093) xor (r * 19349663) xor g.level * 83492791
                val k = (hash ushr 3) and 31
                drawRect(if (k % 3 == 0) plateB else plateA, Offset(x + 2f, y + 2f), Size(step - 4f, step - 4f))
                // Bevel: lit top-left edge, shaded bottom-right edge.
                drawLine(hi, Offset(x + 3f, y + 3f), Offset(x + step - 4f, y + 3f), 1.5f)
                drawLine(hi, Offset(x + 3f, y + 3f), Offset(x + 3f, y + step - 4f), 1.5f)
                drawLine(lo, Offset(x + 4f, y + step - 3f), Offset(x + step - 3f, y + step - 3f), 2f)
                drawLine(lo, Offset(x + step - 3f, y + 4f), Offset(x + step - 3f, y + step - 3f), 2f)
                when (k) {
                    1, 2 -> { // grate
                        var gy = y + 14f
                        while (gy < y + step - 12f) { drawLine(Color(0xFF060A12), Offset(x + 12f, gy), Offset(x + step - 12f, gy), 3f); gy += 7f }
                    }
                    5 -> { // lit service panel
                        val on = 0.5f + 0.5f * sin(time * 2f + c + r)
                        drawRect(accent.copy(alpha = 0.10f + 0.12f * on), Offset(x + 10f, y + 10f), Size(step - 20f, step - 20f))
                        drawRect(accent.copy(alpha = 0.35f), Offset(x + 10f, y + 10f), Size(step - 20f, step - 20f), style = Stroke(1.2f))
                    }
                    9 -> { // bolts
                        val bc = Color(0x33A0C8FF)
                        drawCircle(bc, 2f, Offset(x + 9f, y + 9f)); drawCircle(bc, 2f, Offset(x + step - 9f, y + 9f))
                        drawCircle(bc, 2f, Offset(x + 9f, y + step - 9f)); drawCircle(bc, 2f, Offset(x + step - 9f, y + step - 9f))
                    }
                }
            }
        }
        // Glowing trenches every 4th seam, with packets running along them.
        var tx = step * 2
        var lane = 0
        while (tx < w) {
            drawLine(accent.copy(alpha = 0.10f), Offset(tx, 0f), Offset(tx, h), 7f)
            drawLine(accent.copy(alpha = 0.32f), Offset(tx, 0f), Offset(tx, h), 1.8f)
            for (k in 0 until 2) {
                val py = ((time * (55f + lane * 13f) + k * h * 0.5f + lane * 211f) % (h + 120f)) - 60f
                drawCircle(accent.copy(alpha = 0.18f), 7f, Offset(tx, py))
                drawCircle(accent.copy(alpha = 0.85f), 2.6f, Offset(tx, py))
            }
            tx += step * 4
            lane++
        }
        var ty = step * 3
        while (ty < h) {
            drawLine(accent.copy(alpha = 0.07f), Offset(0f, ty), Offset(w, ty), 6f)
            drawLine(accent.copy(alpha = 0.22f), Offset(0f, ty), Offset(w, ty), 1.4f)
            ty += step * 5
        }
        // Light pools in front of tall hardware.
        for (o in g.arena.obstacles) {
            if (o.kind != ObstacleKind.SERVER_RACK && o.kind != ObstacleKind.SMALL_SERVER && o.kind != ObstacleKind.DATA_PILLAR) continue
            val r = o.rect
            val col = if (o.kind == ObstacleKind.DATA_PILLAR) Palette.Cyan else Palette.Green
            val cx = r.centerX
            val cy = r.bottom + 14f
            val rw = r.width * 0.8f + 40f
            drawOval(col.copy(alpha = 0.05f), Offset(cx - rw, cy - 26f), Size(rw * 2f, 52f))
            drawOval(col.copy(alpha = 0.06f), Offset(cx - rw * 0.6f, cy - 16f), Size(rw * 1.2f, 32f))
        }
        // Drifting data pixels (ambient, very faint).
        for (i in 0 until 26) {
            val px = ((i * 263) % 720).toFloat() + sin(time * 0.6f + i) * 12f
            val pyy = (((i * 157) % 1000) - time * (12f + i % 5 * 4f)).mod(h)
            val a = 0.15f + 0.15f * sin(time * 1.7f + i * 2.3f)
            val col = if (i % 4 == 0) Palette.Green else accent
            drawRect(col.copy(alpha = a), Offset(px, pyy), Size(5f, 5f))
        }
        // Side walls: a low raised kerb with a lit edge.
        val wall = if (boss) Palette.Red else Palette.CyanDim
        drawRect(Color(0xFF0E1830), Offset(-14f, 0f), Size(14f, h))
        drawRect(Color(0xFF0E1830), Offset(w, 0f), Size(14f, h))
        drawLine(wall.copy(alpha = 0.18f), Offset(0f, 0f), Offset(0f, h), 9f)
        drawLine(wall.copy(alpha = 0.18f), Offset(w, 0f), Offset(w, h), 9f)
        drawLine(wall.copy(alpha = 0.8f), Offset(0f, 0f), Offset(0f, h), 3f)
        drawLine(wall.copy(alpha = 0.8f), Offset(w, 0f), Offset(w, h), 3f)
        drawLine(wall.copy(alpha = 0.5f), Offset(0f, h), Offset(w, h), 3f)
    }

    private fun mix(a: Color, b: Color, t: Float) =
        Color(a.red * t + b.red * (1 - t), a.green * t + b.green * (1 - t), a.blue * t + b.blue * (1 - t), 1f)

    private fun DrawScope.drawDecor(g: GameEngine, time: Float) {
        for (d in g.arena.template.decor) {
            when (d.kind) {
                DecorKind.FLOOR_TILE -> drawRect(Color(0x0A7DF9FF), Offset(d.x + 3f, d.y + 3f), Size(d.w - 6f, d.h - 6f))
                DecorKind.CABLE -> {
                    shapePath.reset()
                    val wob = (d.seed % 50) + 20f
                    shapePath.moveTo(d.x, d.y)
                    shapePath.cubicTo(d.x + d.w * 0.3f, d.y - wob, d.x + d.w * 0.6f, d.y + wob, d.x + d.w, d.y + wob * 0.3f)
                    drawPath(shapePath, Color(0xFF050910), style = Stroke(7f))
                    drawPath(shapePath, Color(0xFF1A2B44), style = Stroke(4f))
                    val f = ((time * 0.4f + (d.seed and 0xFF) / 255f) % 1f)
                    drawCircle(Palette.Cyan.copy(alpha = 0.45f), 2.5f, Offset(d.x + d.w * f, d.y + wob * (0.3f * f)))
                }
                DecorKind.VENT -> {
                    drawRoundRect(Color(0xFF070C16), Offset(d.x, d.y), Size(d.w, d.h), CornerRadius(4f))
                    var vx = d.x + 6f
                    while (vx < d.x + d.w - 4f) { drawLine(Color(0xFF1C2B42), Offset(vx, d.y + 5f), Offset(vx, d.y + d.h - 5f), 2f); vx += 8f }
                    drawRoundRect(Color(0xFF22344F), Offset(d.x, d.y), Size(d.w, d.h), CornerRadius(4f), style = Stroke(1.5f))
                }
                DecorKind.FLOOR_LIGHT -> {
                    val n = (d.h / 20f).toInt()
                    for (i in 0 until n) {
                        val on = ((time * 6f).toInt() + i + d.seed) % n < 3
                        drawRect(Palette.Cyan.copy(alpha = if (on) 0.6f else 0.12f), Offset(d.x, d.y + i * 20f), Size(d.w, 12f))
                    }
                }
                DecorKind.HOLO_PANEL -> {
                    val col = if (d.seed % 2 == 0) Palette.Cyan else Palette.Green
                    drawRect(col.copy(alpha = 0.06f), Offset(d.x, d.y), Size(d.w, d.h))
                    drawRect(col.copy(alpha = 0.3f), Offset(d.x, d.y), Size(d.w, d.h), style = Stroke(1f))
                    for (i in 0 until 5) {
                        val bh = (0.3f + 0.7f * (0.5f + 0.5f * sin(time * 2f + i + d.seed))) * (d.h - 10f)
                        drawRect(col.copy(alpha = 0.35f), Offset(d.x + 8f + i * 18f, d.y + d.h - 5f - bh), Size(10f, bh))
                    }
                }
                DecorKind.DATA_POOL -> {
                    val p = 0.5f + 0.5f * sin(time * 1.6f + d.seed)
                    val col = if (d.seed % 3 == 0) Palette.Purple else Palette.Blue
                    drawOval(col.copy(alpha = 0.08f + 0.06f * p), Offset(d.x, d.y), Size(d.w, d.h))
                    drawOval(col.copy(alpha = 0.25f), Offset(d.x, d.y), Size(d.w, d.h), style = Stroke(1.5f))
                }
                DecorKind.WARNING_STRIPES -> {
                    drawRect(Color(0xFF14110A), Offset(d.x, d.y), Size(d.w, d.h))
                    var sx = d.x
                    while (sx < d.x + d.w) {
                        shapePath.reset()
                        shapePath.moveTo(sx, d.y + d.h); shapePath.lineTo(sx + 10f, d.y)
                        shapePath.lineTo(sx + 20f, d.y); shapePath.lineTo(sx + 10f, d.y + d.h); shapePath.close()
                        drawPath(shapePath, Palette.Gold.copy(alpha = 0.35f))
                        sx += 22f
                    }
                }
            }
        }
    }

    /** The far wall: racks along the top edge and the exit gate in the middle. */
    private fun DrawScope.drawTopWall(g: GameEngine, time: Float) {
        val w = g.arena.width
        val gateL = g.arena.portalX - GameEngine.GATE_HALF_WIDTH
        val gateR = g.arena.portalX + GameEngine.GATE_HALF_WIDTH
        val boss = g.plan.kind == LevelKind.BOSS
        // Wall front face, from y = -WALL_HEIGHT to y = 0.
        drawRect(Color(0xFF0C1526), Offset(-14f, -WALL_HEIGHT), Size(w + 28f, WALL_HEIGHT))
        drawRect(Color(0xFF16233A), Offset(-14f, -WALL_HEIGHT - 12f), Size(w + 28f, 12f))
        // Rack bays with blinking LEDs.
        var bx = 10f
        var i = 0
        while (bx < w - 50f) {
            if (bx + 60f > gateL && bx < gateR) { bx = gateR + 14f; continue }
            drawRoundRect(Color(0xFF111D33), Offset(bx, -WALL_HEIGHT + 10f), Size(60f, WALL_HEIGHT - 16f), CornerRadius(3f))
            for (k in 0 until 4) {
                val on = sin(time * (2f + k * 0.7f) + i * 1.3f + k) > 0.1f
                val col = if ((k + i) % 4 == 0) Palette.ServerLedAmber else Palette.ServerLedGreen
                drawCircle(col.copy(alpha = if (on) 0.95f else 0.15f), 2.6f, Offset(bx + 10f + k * 13f, -WALL_HEIGHT + 22f))
                drawLine(Color(0xFF22344F), Offset(bx + 6f, -WALL_HEIGHT + 34f + k * 7f), Offset(bx + 54f, -WALL_HEIGHT + 34f + k * 7f), 2f)
            }
            bx += 72f
            i++
        }
        drawLine((if (boss) Palette.Red else Palette.CyanDim).copy(alpha = 0.8f), Offset(-14f, 0f), Offset(w + 14f, 0f), 3f)

        // Gate.
        val open = g.portalOpen
        val gw = gateR - gateL
        drawRect(Color(0xFF02050A), Offset(gateL, -WALL_HEIGHT), Size(gw, WALL_HEIGHT))
        val frame = if (open) Palette.Green else Palette.Red
        drawRect(frame.copy(alpha = 0.85f), Offset(gateL - 6f, -WALL_HEIGHT - 12f), Size(6f, WALL_HEIGHT + 12f))
        drawRect(frame.copy(alpha = 0.85f), Offset(gateR, -WALL_HEIGHT - 12f), Size(6f, WALL_HEIGHT + 12f))
        if (!open) {
            // Laser bars.
            for (k in 0 until 5) {
                val yy = -WALL_HEIGHT + 10f + k * 13f
                val flick = 0.6f + 0.4f * sin(time * 20f + k)
                drawLine(Palette.Red.copy(alpha = 0.75f * flick), Offset(gateL, yy), Offset(gateR, yy), 3f)
            }
        } else {
            val p = 0.5f + 0.5f * sin(time * 4f)
            drawRect(Palette.Green.copy(alpha = 0.18f + 0.12f * p), Offset(gateL, -WALL_HEIGHT), Size(gw, WALL_HEIGHT))
            // Chevrons pulling the player in.
            for (k in 0 until 3) {
                val yy = 40f - ((time * 60f + k * 30f) % 90f)
                shapePath.reset()
                shapePath.moveTo(g.arena.portalX - 22f, yy + 12f); shapePath.lineTo(g.arena.portalX, yy); shapePath.lineTo(g.arena.portalX + 22f, yy + 12f)
                drawPath(shapePath, Palette.Green.copy(alpha = 0.7f), style = Stroke(4f))
            }
        }
    }

    // --- Shadows & depth-sorted solids ------------------------------------

    private fun DrawScope.drawShadows(g: GameEngine) {
        for (o in g.arena.obstacles) {
            val r = o.rect
            val hgt = heightOf(o.kind)
            drawRoundRect(Color.Black.copy(alpha = 0.32f), Offset(r.left + hgt * 0.35f, r.top + 6f), Size(r.width, r.height), CornerRadius(6f))
        }
        for (e in g.enemies.items) {
            if (!e.active || e.state == AiState.SPAWNING || e.state == AiState.HIDDEN) continue
            drawOval(Color.Black.copy(alpha = 0.35f), Offset(e.x - e.radius, e.y + e.radius * 0.35f), Size(e.radius * 2f, e.radius * 0.8f))
        }
    }

    private fun DrawScope.drawSorted(g: GameEngine, time: Float, skin: OperativeSkin, body: BodyStyle) {
        sortCount = 0
        val obs = g.arena.obstacles
        for (i in obs.indices) push(0, i, obs[i].rect.bottom)
        val items = g.enemies.items
        for (i in items.indices) if (items[i].active) push(1, i, items[i].y + items[i].radius * 0.5f)
        if (g.phase != Phase.DEAD) push(2, 0, g.py + g.playerRadius * 0.5f)
        // Insertion sort: tiny n, already nearly sorted frame to frame.
        for (i in 1 until sortCount) {
            val k = sortKey[i]; val kd = sortKind[i]; val ix = sortIndex[i]
            var j = i - 1
            while (j >= 0 && sortKey[j] > k) {
                sortKey[j + 1] = sortKey[j]; sortKind[j + 1] = sortKind[j]; sortIndex[j + 1] = sortIndex[j]; j--
            }
            sortKey[j + 1] = k; sortKind[j + 1] = kd; sortIndex[j + 1] = ix
        }
        // Orbit elements behind the operative are drawn before it.
        drawOrbitBack(g, time)
        for (i in 0 until sortCount) {
            when (sortKind[i]) {
                0 -> drawObstacle(g, obs[sortIndex[i]], sortIndex[i], time)
                1 -> drawEnemy(g, items[sortIndex[i]], time)
                else -> drawPlayer(g, time, skin, body)
            }
        }
    }

    private fun push(kind: Int, index: Int, key: Float) {
        if (sortCount >= MAX_ITEMS) return
        sortKind[sortCount] = kind; sortIndex[sortCount] = index; sortKey[sortCount] = key; sortCount++
    }

    private fun heightOf(k: ObstacleKind): Float = when (k) {
        ObstacleKind.SERVER_RACK -> 104f
        ObstacleKind.SMALL_SERVER -> 64f
        ObstacleKind.DATA_PILLAR -> 92f
        ObstacleKind.COOLING_UNIT -> 36f
        ObstacleKind.ROUTER -> 40f
        ObstacleKind.TERMINAL -> 48f
        ObstacleKind.POWER_UNIT -> 58f
        ObstacleKind.FIREWALL_NODE -> 52f
        ObstacleKind.FIBER_JUNCTION -> 28f
        ObstacleKind.CRATES -> 50f
    }

    private data class Look(val top: Color, val front: Color, val trim: Color)

    private fun lookOf(k: ObstacleKind, vault: Boolean): Look = when {
        vault -> Look(Color(0xFF2A2410), Color(0xFF17130A), Palette.Gold)
        else -> when (k) {
            ObstacleKind.SERVER_RACK, ObstacleKind.SMALL_SERVER -> Look(Color(0xFF243453), Color(0xFF17233A), Palette.Cyan)
            ObstacleKind.FIREWALL_NODE -> Look(Color(0xFF45230E), Color(0xFF2D160A), Palette.Orange)
            ObstacleKind.DATA_PILLAR -> Look(Color(0xFF15435A), Color(0xFF0E2D3F), Palette.Cyan)
            ObstacleKind.COOLING_UNIT -> Look(Color(0xFF203350), Color(0xFF152238), Palette.Blue)
            ObstacleKind.ROUTER -> Look(Color(0xFF183148), Color(0xFF102234), Palette.Green)
            ObstacleKind.TERMINAL -> Look(Color(0xFF2C1B40), Color(0xFF1C112C), Palette.Purple)
            ObstacleKind.POWER_UNIT -> Look(Color(0xFF332E14), Color(0xFF221E0C), Palette.Gold)
            ObstacleKind.FIBER_JUNCTION -> Look(Color(0xFF261B3C), Color(0xFF181128), Palette.Purple)
            ObstacleKind.CRATES -> Look(Color(0xFF1C2D4F), Color(0xFF121E36), Color(0xFF6E9BE0))
        }
    }

    private fun DrawScope.drawObstacle(g: GameEngine, o: ObstacleSpec, index: Int, time: Float) {
        val r = o.rect
        val h = heightOf(o.kind)
        val vault = g.plan.rules.vault && index == g.arena.obstacles.lastIndex
        val look = lookOf(o.kind, vault)
        val cr = CornerRadius(5f)
        val topY = r.top - h
        // Front face (from the top face's bottom edge down to the floor).
        drawRoundRect(look.front, Offset(r.left, r.bottom - h), Size(r.width, h), cr)
        // Lit vertical edges on the front corners (neon trim).
        drawLine(look.trim.copy(alpha = 0.15f), Offset(r.left + 1f, r.bottom - h), Offset(r.left + 1f, r.bottom), 6f)
        drawLine(look.trim.copy(alpha = 0.15f), Offset(r.right - 1f, r.bottom - h), Offset(r.right - 1f, r.bottom), 6f)
        drawLine(look.trim.copy(alpha = 0.7f), Offset(r.left + 1f, r.bottom - h), Offset(r.left + 1f, r.bottom - 2f), 1.6f)
        drawLine(look.trim.copy(alpha = 0.7f), Offset(r.right - 1f, r.bottom - h), Offset(r.right - 1f, r.bottom - 2f), 1.6f)
        // Top face with a glowing rim.
        drawRoundRect(look.top, Offset(r.left, topY), Size(r.width, r.height), cr)
        drawRoundRect(Color.White.copy(alpha = 0.05f), Offset(r.left + 3f, topY + 3f), Size(r.width - 6f, r.height * 0.45f), cr)
        drawRoundRect(look.trim.copy(alpha = 0.14f), Offset(r.left - 2f, topY - 2f), Size(r.width + 4f, r.height + 4f), cr, style = Stroke(6f))
        drawRoundRect(look.trim.copy(alpha = 0.85f), Offset(r.left, topY), Size(r.width, r.height), cr, style = Stroke(2f))
        drawLine(look.trim.copy(alpha = 0.45f), Offset(r.left + 3f, r.bottom - h), Offset(r.right - 3f, r.bottom - h), 2f)

        val frontTop = r.bottom - h
        when (o.kind) {
            ObstacleKind.SERVER_RACK, ObstacleKind.SMALL_SERVER -> {
                // Rack units: dark bays, each with a row of blinking LEDs that glow.
                val cols = max(1, ((r.width - 10f) / 11f).toInt())
                val rows = max(1, ((h - 10f) / 11f).toInt())
                for (row in 0 until rows) {
                    val ry = frontTop + 6f + row * 11f
                    drawRect(Color(0xFF0A1222), Offset(r.left + 4f, ry - 4f), Size(r.width - 8f, 8f))
                    for (c in 0 until cols) {
                        val on = sin(time * (1.8f + (c % 3) * 0.9f) + c * 1.3f + row * 2.1f + index) > 0.15f
                        val col = when ((c * 7 + row * 3 + index) % 6) {
                            0 -> Palette.ServerLedAmber
                            1, 2 -> Palette.Cyan
                            else -> Palette.ServerLedGreen
                        }
                        val cx = r.left + 9f + c * 11f
                        if (on) drawCircle(col.copy(alpha = 0.22f), 5f, Offset(cx, ry))
                        drawCircle(col.copy(alpha = if (on) 1f else 0.12f), 1.9f, Offset(cx, ry))
                    }
                }
            }
            ObstacleKind.FIREWALL_NODE -> {
                val p = 0.5f + 0.5f * sin(time * 3f + index)
                var by = frontTop + 10f
                var row = 0
                while (by < r.bottom - 2f) {
                    drawLine(Palette.Orange.copy(alpha = 0.3f + 0.2f * p), Offset(r.left + 2f, by), Offset(r.right - 2f, by), 1.5f)
                    var bx = r.left + (if (row % 2 == 0) 18f else 0f)
                    while (bx < r.right) { drawLine(Palette.Orange.copy(alpha = 0.3f), Offset(bx, by - 10f), Offset(bx, by), 1.5f); bx += 36f }
                    by += 10f; row++
                }
            }
            ObstacleKind.DATA_PILLAR -> {
                val f = (time * 0.6f + index * 0.3f) % 1f
                drawLine(Palette.Cyan.copy(alpha = 0.7f * (1f - f)), Offset(r.left + 4f, r.bottom - f * h), Offset(r.right - 4f, r.bottom - f * h), 3f)
                drawCircle(Palette.Cyan.copy(alpha = 0.35f), min(r.width, r.height) * 0.25f, Offset(r.centerX, topY + r.height / 2f))
            }
            ObstacleKind.COOLING_UNIT -> {
                val c = Offset(r.centerX, topY + r.height / 2f)
                val rad = min(r.width, r.height) * 0.38f
                drawCircle(Color(0xFF0A111C), rad, c)
                rotate(time * 240f + index * 40f, c) {
                    for (b in 0 until 4) {
                        val a = b * MathUtil.PI / 2f
                        drawLine(Palette.Blue.copy(alpha = 0.75f), c, Offset(c.x + cos(a) * rad, c.y + sin(a) * rad), 5f)
                    }
                }
            }
            ObstacleKind.ROUTER -> {
                for (k in 0 until 4) {
                    val on = ((time * 4f).toInt() + k + index) % 4 != 0
                    drawCircle(Palette.Green.copy(alpha = if (on) 0.95f else 0.15f), 3f, Offset(r.left + r.width * (0.2f + 0.2f * k), frontTop + h * 0.5f))
                }
                drawLine(Palette.Green.copy(alpha = 0.7f), Offset(r.left + 10f, topY + 4f), Offset(r.left + 4f, topY - 18f), 2.5f)
                drawLine(Palette.Green.copy(alpha = 0.7f), Offset(r.right - 10f, topY + 4f), Offset(r.right - 4f, topY - 18f), 2.5f)
            }
            ObstacleKind.TERMINAL -> {
                val glitch = ((time * 9f).toInt() + index) % 13 == 0
                drawRect(Palette.Purple.copy(alpha = if (glitch) 0.6f else 0.25f), Offset(r.left + 8f, frontTop + 6f), Size(r.width - 16f, h - 14f))
                if (!glitch) drawLine(Palette.Green.copy(alpha = 0.7f), Offset(r.left + 12f, frontTop + 14f), Offset(r.left + 12f + ((time * 30f) % (r.width - 30f)), frontTop + 14f), 2f)
            }
            ObstacleKind.POWER_UNIT -> {
                val p = 0.5f + 0.5f * sin(time * 2.4f + index)
                val c = Offset(r.centerX, frontTop + h / 2f)
                drawCircle(Palette.Gold.copy(alpha = 0.2f + 0.3f * p), min(r.width, h) * 0.3f, c)
                val s = min(r.width, h) * 0.18f
                shapePath.reset()
                shapePath.moveTo(c.x + s * 0.2f, c.y - s * 1.2f); shapePath.lineTo(c.x - s * 0.6f, c.y + s * 0.1f)
                shapePath.lineTo(c.x, c.y + s * 0.1f); shapePath.lineTo(c.x - s * 0.2f, c.y + s * 1.2f)
                shapePath.lineTo(c.x + s * 0.6f, c.y - s * 0.1f); shapePath.lineTo(c.x, c.y - s * 0.1f); shapePath.close()
                drawPath(shapePath, Palette.Gold.copy(alpha = 0.85f))
            }
            ObstacleKind.FIBER_JUNCTION -> {
                val f = (time * 0.8f + index * 0.17f) % 1f
                if (r.height > r.width) drawCircle(look.trim, 4f, Offset(r.centerX, topY + f * r.height))
                else drawCircle(look.trim, 4f, Offset(r.left + f * r.width, topY + r.height / 2f))
                if (vault) {
                    tagPaint.textSize = 18f
                    tagPaint.color = Palette.Gold.toArgb()
                    drawContext.canvas.nativeCanvas.drawText("[\$\$\$]", r.centerX, topY + r.height / 2f + 6f, tagPaint)
                }
            }
            ObstacleKind.CRATES -> {
                // Reinforced hardware crate: X braces on top and front.
                val xc = Color(0xFF4B6FA8)
                drawRect(Color(0xFF0E1830), Offset(r.left + 6f, topY + 6f), Size(r.width - 12f, r.height - 12f))
                drawLine(xc, Offset(r.left + 6f, topY + 6f), Offset(r.right - 6f, topY + r.height - 6f), 4f)
                drawLine(xc, Offset(r.right - 6f, topY + 6f), Offset(r.left + 6f, topY + r.height - 6f), 4f)
                drawRect(Color(0xFF6E9BE0).copy(alpha = 0.6f), Offset(r.left + 6f, topY + 6f), Size(r.width - 12f, r.height - 12f), style = Stroke(1.5f))
                drawLine(xc.copy(alpha = 0.7f), Offset(r.left + 5f, frontTop + 5f), Offset(r.right - 5f, r.bottom - 5f), 3f)
                drawLine(xc.copy(alpha = 0.7f), Offset(r.right - 5f, frontTop + 5f), Offset(r.left + 5f, r.bottom - 5f), 3f)
            }
        }
    }

    // --- Enemies -------------------------------------------------------------

    private fun DrawScope.drawEnemy(g: GameEngine, e: Enemy, time: Float) {
        // *GLITCHED*: colour cycles and the body snaps between shapes.
        val glitch = e.def.glitched || e.boss?.glitched == true
        val base = if (glitch) Color.hsv((time * 220f + e.uid * 47f) % 360f, 0.85f, 1f) else Color(e.def.color)
        val shape = if (glitch) GLITCH_SHAPES[((time * 5f).toInt() + e.uid) % GLITCH_SHAPES.size] else e.def.shape
        if (e.state == AiState.SPAWNING) {
            val p = 0.5f + 0.5f * sin(time * 18f)
            drawOval(Palette.Red.copy(alpha = 0.25f + 0.25f * p), Offset(e.x - e.radius * (1.2f + e.stateTimer), e.y - e.radius * 0.5f * (1.2f + e.stateTimer)),
                Size(e.radius * 2f * (1.2f + e.stateTimer), e.radius * (1.2f + e.stateTimer)), style = Stroke(3f))
            // A beam of light descending onto the spawn point.
            drawLine(Palette.Red.copy(alpha = 0.18f * p), Offset(e.x, e.y - 120f), Offset(e.x, e.y), e.radius)
            return
        }
        if (e.state == AiState.HIDDEN) {
            drawOval(base.copy(alpha = 0.15f), Offset(e.x - e.radius, e.y - e.radius * 0.4f), Size(e.radius * 2f, e.radius * 0.8f), style = Stroke(2f))
            return
        }
        val boss = e.boss
        val lift = (if (boss != null) 18f else 12f) + sin(time * 4f + e.uid) * 2.5f
        val cx = e.x
        val cy = e.y - lift
        val depth = e.radius * 0.35f
        val elite = e.elite
        if (elite != null) {
            val p = 0.5f + 0.5f * sin(time * 5f + e.uid)
            drawCircle(Color(elite.color).copy(alpha = 0.15f + 0.15f * p), e.radius * 1.55f, Offset(cx, cy))
            drawCircle(Color(elite.color).copy(alpha = 0.85f), e.radius * 1.3f, Offset(cx, cy), style = Stroke(2.5f))
        }
        if (boss != null) drawCircle(base.copy(alpha = 0.14f + 0.08f * sin(time * 3f)), e.radius * 1.7f, Offset(cx, cy))
        if (glitch) {
            // RGB-split ghosts that jump a few pixels every few frames.
            val j = ((time * 14f).toInt() + e.uid) % 5 - 2
            drawShape(shape, Offset(cx - 4f + j, cy), e.radius, Color(0xFF00FFFF).copy(alpha = 0.35f), 0f)
            drawShape(shape, Offset(cx + 4f - j, cy), e.radius, Color(0xFFFF00FF).copy(alpha = 0.35f), 0f)
        }
        val winding = e.state == AiState.WINDUP
        val rot = if (boss != null) time * 25f else 0f
        // Extruded body: dark side first, then lit top, then a rim highlight.
        drawShape(shape, Offset(cx, cy + depth), e.radius, darken(base, 0.45f), rot)
        val fill = when {
            e.hitFlash > 0f -> Color.White
            winding && ((time * 16f).toInt() % 2 == 0) -> Color.White.copy(alpha = 0.9f)
            else -> base
        }
        drawShape(shape, Offset(cx, cy), e.radius, fill, rot)
        drawShape(shape, Offset(cx, cy), e.radius, Color(0xFF1A0006), rot, stroke = 2.5f)
        if (glitch && ((time * 9f).toInt() + e.uid) % 4 == 0) {
            // Scanline tear across the body.
            drawRect(Color.White.copy(alpha = 0.5f), Offset(cx - e.radius * 1.2f, cy - e.radius * 0.1f), Size(e.radius * 2.4f, 3f))
        }
        if (e.def.accent != AccentKind.NONE) drawAccent(e.def.accent, Offset(cx, cy), e.radius, base, time, e.uid)
        drawCircle(Color.White.copy(alpha = 0.22f), e.radius * 0.35f, Offset(cx - e.radius * 0.3f, cy - e.radius * 0.35f))
        // Eyes track the operative: the threats feel alive and you can read who is watching you.
        val ang = kotlin.math.atan2(g.py - e.y, g.px - e.x)
        val ex = cos(ang) * e.radius * 0.25f
        val ey = sin(ang) * e.radius * 0.18f
        val eyeR = max(2f, e.radius * 0.13f)
        val eyeCol = if (winding) Color.White else Color(0xFFFFF2B0)
        drawCircle(Color(0xFF14040A), eyeR * 1.7f, Offset(cx - e.radius * 0.28f + ex, cy - e.radius * 0.05f + ey))
        drawCircle(Color(0xFF14040A), eyeR * 1.7f, Offset(cx + e.radius * 0.28f + ex, cy - e.radius * 0.05f + ey))
        drawCircle(eyeCol, eyeR, Offset(cx - e.radius * 0.28f + ex, cy - e.radius * 0.05f + ey))
        drawCircle(eyeCol, eyeR, Offset(cx + e.radius * 0.28f + ex, cy - e.radius * 0.05f + ey))
        // Tag (CyOps TD ASCII identity) under the eyes.
        tagPaint.textSize = if (boss != null) 18f else max(9f, e.radius * 0.55f)
        tagPaint.color = 0xFF14040A.toInt()
        drawContext.canvas.nativeCanvas.drawText(e.def.tag, cx, cy + e.radius * 0.62f, tagPaint)
        if (boss == null && e.hp < e.maxHp) {
            val bw = e.radius * 2f
            val by = cy - e.radius - 12f
            drawRect(Color(0xAA000000), Offset(e.x - bw / 2f, by), Size(bw, 4f))
            drawRect(Palette.Red, Offset(e.x - bw / 2f, by), Size(bw * (e.hp / e.maxHp).coerceIn(0f, 1f), 4f))
        }
        if (e.uid == g.targetUid) {
            drawOval(Palette.Cyan.copy(alpha = 0.85f), Offset(e.x - e.radius - 6f, e.y + e.radius * 0.1f), Size(e.radius * 2f + 12f, e.radius + 6f), style = Stroke(2.5f))
        }
    }

    /** Family accent on a variant's body, so 300+ types stay readable at a glance. */
    private fun DrawScope.drawAccent(kind: AccentKind, c: Offset, r: Float, base: Color, time: Float, uid: Int) {
        val dark = Color(0xFF1A0006)
        val light = Color(0xFFFFF2B0)
        when (kind) {
            AccentKind.NONE -> {}
            AccentKind.RING -> drawCircle(dark, r * 1.18f, c, style = Stroke(3f))
            AccentKind.SPIKES -> for (i in 0 until 6) {
                val a = i * MathUtil.TWO_PI / 6 + time * 0.8f
                drawLine(dark, Offset(c.x + cos(a) * r * 0.9f, c.y + sin(a) * r * 0.9f), Offset(c.x + cos(a) * r * 1.35f, c.y + sin(a) * r * 1.35f), 3f)
            }
            AccentKind.ORBITERS -> for (i in 0 until 3) {
                val a = time * 3f + i * MathUtil.TWO_PI / 3 + uid
                drawCircle(base, r * 0.2f, Offset(c.x + cos(a) * r * 1.45f, c.y + sin(a) * r * 0.75f))
                drawCircle(dark, r * 0.2f, Offset(c.x + cos(a) * r * 1.45f, c.y + sin(a) * r * 0.75f), style = Stroke(1.5f))
            }
            AccentKind.CORE -> {
                val p = 0.6f + 0.4f * sin(time * 5f + uid)
                drawCircle(light.copy(alpha = 0.35f * p), r * 0.5f, Offset(c.x, c.y + r * 0.2f))
                drawCircle(Palette.Gold.copy(alpha = p), r * 0.18f, Offset(c.x, c.y + r * 0.35f))
            }
            AccentKind.STRIPES -> for (k in -1..1) {
                val y = c.y + r * 0.42f + k * r * 0.16f
                drawLine(dark.copy(alpha = 0.6f), Offset(c.x - r * 0.5f, y), Offset(c.x + r * 0.5f, y), 2f)
            }
            AccentKind.HORNS -> {
                drawLine(dark, Offset(c.x - r * 0.45f, c.y - r * 0.7f), Offset(c.x - r * 0.75f, c.y - r * 1.3f), 4f)
                drawLine(dark, Offset(c.x + r * 0.45f, c.y - r * 0.7f), Offset(c.x + r * 0.75f, c.y - r * 1.3f), 4f)
            }
            AccentKind.ANTENNA -> {
                drawLine(dark, Offset(c.x, c.y - r * 0.85f), Offset(c.x, c.y - r * 1.5f), 2.5f)
                val blink = if ((time * 3f + uid).toInt() % 2 == 0) Palette.Red else light
                drawCircle(blink, r * 0.15f, Offset(c.x, c.y - r * 1.55f))
            }
            AccentKind.PLATES -> {
                drawRect(dark.copy(alpha = 0.55f), Offset(c.x - r * 0.95f, c.y - r * 0.2f), Size(r * 0.32f, r * 0.7f))
                drawRect(dark.copy(alpha = 0.55f), Offset(c.x + r * 0.63f, c.y - r * 0.2f), Size(r * 0.32f, r * 0.7f))
            }
            AccentKind.HALO -> {
                val p = 0.5f + 0.5f * sin(time * 4f + uid)
                drawOval(base.copy(alpha = 0.35f + 0.3f * p), Offset(c.x - r * 0.8f, c.y - r * 1.45f), Size(r * 1.6f, r * 0.45f), style = Stroke(2.5f))
            }
            AccentKind.CRACKS -> {
                drawLine(dark, Offset(c.x - r * 0.1f, c.y - r * 0.9f), Offset(c.x + r * 0.15f, c.y - r * 0.45f), 2f)
                drawLine(dark, Offset(c.x + r * 0.15f, c.y - r * 0.45f), Offset(c.x - r * 0.05f, c.y - r * 0.2f), 2f)
                drawLine(dark, Offset(c.x + r * 0.6f, c.y + r * 0.1f), Offset(c.x + r * 0.85f, c.y + r * 0.45f), 2f)
            }
            AccentKind.VISOR -> drawRoundRect(dark.copy(alpha = 0.75f), Offset(c.x - r * 0.62f, c.y - r * 0.28f), Size(r * 1.24f, r * 0.42f), CornerRadius(r * 0.2f), style = Stroke(2.5f))
            AccentKind.BITS -> for (i in 0 until 4) {
                val t = ((time * 0.9f + i * 0.25f + uid * 0.13f) % 1f)
                val x = c.x + (i - 1.5f) * r * 0.5f
                drawRect(base.copy(alpha = 1f - t), Offset(x, c.y - r - t * r * 1.2f), Size(r * 0.18f, r * 0.18f))
            }
        }
    }

    private val GLITCH_SHAPES = ShapeKind.entries.toTypedArray()

    private fun darken(c: Color, k: Float) = Color(c.red * k, c.green * k, c.blue * k, c.alpha)

    private fun DrawScope.drawShape(shape: ShapeKind, c: Offset, r: Float, color: Color, rotationDeg: Float, stroke: Float = 0f) {
        val style = if (stroke > 0f) Stroke(stroke) else Fill
        when (shape) {
            ShapeKind.CIRCLE -> drawCircle(color, r, c, style = style)
            ShapeKind.SQUARE -> rotate(rotationDeg, c) {
                drawRoundRect(color, Offset(c.x - r * 0.88f, c.y - r * 0.88f), Size(r * 1.76f, r * 1.76f), CornerRadius(5f), style = style)
            }
            else -> {
                val sides = when (shape) {
                    ShapeKind.TRIANGLE -> 3
                    ShapeKind.DIAMOND -> 4
                    ShapeKind.PENTAGON -> 5
                    ShapeKind.HEXAGON -> 6
                    ShapeKind.STAR -> 10
                    else -> 8
                }
                shapePath.reset()
                val rot = Math.toRadians(rotationDeg.toDouble()).toFloat() - MathUtil.PI / 2f
                val rr = if (shape == ShapeKind.TRIANGLE) r * 1.2f else r
                if (shape == ShapeKind.CROSS) {
                    val a = r * 0.38f
                    shapePath.moveTo(c.x - a, c.y - r); shapePath.lineTo(c.x + a, c.y - r); shapePath.lineTo(c.x + a, c.y - a)
                    shapePath.lineTo(c.x + r, c.y - a); shapePath.lineTo(c.x + r, c.y + a); shapePath.lineTo(c.x + a, c.y + a)
                    shapePath.lineTo(c.x + a, c.y + r); shapePath.lineTo(c.x - a, c.y + r); shapePath.lineTo(c.x - a, c.y + a)
                    shapePath.lineTo(c.x - r, c.y + a); shapePath.lineTo(c.x - r, c.y - a); shapePath.lineTo(c.x - a, c.y - a)
                } else {
                    for (i in 0 until sides) {
                        val a = rot + i * MathUtil.TWO_PI / sides
                        // Star: alternate outer and inner points.
                        val pr = if (shape == ShapeKind.STAR && i % 2 == 1) rr * 0.55f else rr
                        val x = c.x + cos(a) * pr
                        val y = c.y + sin(a) * pr
                        if (i == 0) shapePath.moveTo(x, y) else shapePath.lineTo(x, y)
                    }
                }
                shapePath.close()
                drawPath(shapePath, color, style = style)
            }
        }
    }

    // --- Operative, orbit -------------------------------------------------

    private fun DrawScope.drawPlayer(g: GameEngine, time: Float, skin: OperativeSkin, body: BodyStyle) {
        val r = g.playerRadius
        val foot = g.py + r * 0.55f
        val blink = g.invuln > 0f && ((time * 20f).toInt() % 2 == 0)
        // Firewall dome.
        if (g.firewall > 0f) {
            val f = g.firewall / max(1f, g.stats.firewallMax)
            drawOval(Palette.Orange.copy(alpha = 0.10f + 0.12f * f), Offset(g.px - r * 1.8f, g.py - r * 2.6f), Size(r * 3.6f, r * 3.4f))
            drawOval(Palette.Orange.copy(alpha = 0.35f + 0.4f * f), Offset(g.px - r * 1.8f, g.py - r * 2.6f), Size(r * 3.6f, r * 3.4f), style = Stroke(2f))
        }
        // Firing ring on the floor.
        if (!g.moving && g.targetUid >= 0) {
            drawOval(Palette.Green.copy(alpha = 0.55f), Offset(g.px - r * 1.4f, g.py), Size(r * 2.8f, r * 1.1f), style = Stroke(2f))
        }
        drawFigure(body, skin, g.px, foot, FIGURE_SCALE, g.facing, g.moving, time, alpha = if (blink) 0.35f else 1f, bigGun = g.stats.beamLevel > 0)
    }

    private fun orbPos(g: GameEngine, i: Int, out: FloatArray) {
        val s = g.stats
        val a = g.orbAngle + MathUtil.TWO_PI * i / s.orbCount
        out[0] = g.px + cos(a) * s.orbRadius
        out[1] = g.py + sin(a) * s.orbRadius
    }

    private val tmp = FloatArray(2)

    /** Orbs/blades behind the operative (smaller y) draw before it. */
    private fun DrawScope.drawOrbitBack(g: GameEngine, time: Float) = drawOrbit(g, time, back = true)

    private fun DrawScope.drawOrbitFront(g: GameEngine, time: Float) = drawOrbit(g, time, back = false)

    private fun DrawScope.drawOrbit(g: GameEngine, time: Float, back: Boolean) {
        val s = g.stats
        val lift = 26f
        for (i in 0 until s.orbCount) {
            orbPos(g, i, tmp)
            if ((tmp[1] < g.py) != back) continue
            val x = tmp[0]
            val y = tmp[1]
            drawOval(Color.Black.copy(alpha = 0.3f), Offset(x - s.orbSize, y - 3f), Size(s.orbSize * 2f, s.orbSize * 0.7f))
            drawCircle(Palette.Cyan.copy(alpha = 0.25f), s.orbSize * 1.7f, Offset(x, y - lift))
            drawCircle(Palette.Cyan, s.orbSize, Offset(x, y - lift))
            drawCircle(Color.White.copy(alpha = 0.85f), s.orbSize * 0.45f, Offset(x - 2f, y - lift - 2f))
        }
        for (i in 0 until s.bladeCount) {
            val a = g.bladeAngle + MathUtil.TWO_PI * i / s.bladeCount
            val x = g.px + cos(a) * s.bladeRadius
            val y = g.py + sin(a) * s.bladeRadius
            if ((y < g.py) != back) continue
            rotate(Math.toDegrees(a.toDouble()).toFloat() + 90f, Offset(x, y - lift)) {
                drawRoundRect(Palette.Green.copy(alpha = 0.3f), Offset(x - 16f, y - lift - 6f), Size(32f, 12f), CornerRadius(6f))
                drawRoundRect(Palette.Green, Offset(x - 13f, y - lift - 3.5f), Size(26f, 7f), CornerRadius(4f))
            }
        }
    }

    // --- Projectiles / hazards / effects -----------------------------------

    private fun DrawScope.drawProjectiles(g: GameEngine, time: Float) {
        val lift = 22f
        drawBeam(g, time, lift)
        for (p in g.projectiles.items) {
            if (!p.active) continue
            if (p.kind == ProjKind.MINE) { drawMine(p.x, p.y, p.armTimer > 0f, time, p.tint); continue }
            drawCircle(Color.Black.copy(alpha = 0.25f), p.radius * 0.8f, Offset(p.x, p.y))
            val c = Offset(p.x, p.y - lift)
            if (p.kind == ProjKind.MISSILE) { drawMissile(c, p.vx, p.vy, time); continue }
            if (p.kind == ProjKind.BOOMERANG) {
                val col = if (p.tint != 0L) Color(p.tint) else Palette.Green
                rotate(time * 900f % 360f, c) {
                    drawCircle(col.copy(alpha = 0.25f), p.radius * 1.8f, c)
                    drawArc(col, 20f, 140f, false, Offset(c.x - p.radius, c.y - p.radius), Size(p.radius * 2f, p.radius * 2f), style = Stroke(4f))
                    drawArc(col, 200f, 140f, false, Offset(c.x - p.radius, c.y - p.radius), Size(p.radius * 2f, p.radius * 2f), style = Stroke(4f))
                    drawCircle(Color.White, p.radius * 0.3f, c)
                }
                continue
            }
            if (p.kind == ProjKind.RAIL) {
                val tail = Offset(p.x - p.vx * 0.06f, p.y - lift - p.vy * 0.06f)
                drawLine(Color(0xFFB98CFF).copy(alpha = 0.25f), c, tail, p.radius * 3.2f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(Color(0xFFE7D4FF).copy(alpha = 0.8f), c, tail, p.radius * 1.3f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(Color.White, c, tail, p.radius * 0.45f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                continue
            }
            if (p.friendly) {
                val col = if (p.tint != 0L) Color(p.tint) else when (p.kind) {
                    ProjKind.LANCE -> Palette.Green
                    ProjKind.CONE -> Palette.Blue
                    ProjKind.NODE_BOLT, ProjKind.COUNTER -> Palette.Cyan
                    else -> if (p.crit) Palette.Gold else Palette.Cyan
                }
                val len = if (p.kind == ProjKind.LANCE) 0.07f else 0.035f
                val tail = Offset(p.x - p.vx * len, p.y - lift - p.vy * len)
                drawLine(col.copy(alpha = 0.18f), c, tail, p.radius * 3f)
                drawLine(col.copy(alpha = 0.6f), c, tail, p.radius * 1.3f)
                drawCircle(col.copy(alpha = 0.25f), p.radius * 2.2f, c)
                drawCircle(col, p.radius, c)
                drawCircle(Color.White.copy(alpha = 0.9f), p.radius * 0.5f, c)
            } else {
                // Hostile packets: hot red-orange streaks with a glow (key art).
                val col = if (p.kind == ProjKind.BOSS) Palette.Magenta else Color(0xFFFF4A2A)
                val tail = Offset(p.x - p.vx * 0.09f, p.y - lift - p.vy * 0.09f)
                drawLine(col.copy(alpha = 0.16f), c, tail, p.radius * 3f)
                drawLine(col.copy(alpha = 0.55f), c, tail, p.radius * 1.2f)
                drawCircle(col.copy(alpha = 0.25f), p.radius * 2.3f, c)
                drawCircle(Color(0xFF1A0006), p.radius + 2f, c)
                drawCircle(col, p.radius, c)
                drawCircle(Color(0xFFFFE6D0), p.radius * 0.45f, c)
                if (p.homing > 0f) drawCircle(col.copy(alpha = 0.4f), p.radius + 6f, c, style = Stroke(1.5f))
            }
        }
    }

    /** Logic Bomb on the floor: a dark puck whose light blinks faster once armed. */
    private fun DrawScope.drawMine(x: Float, y: Float, arming: Boolean, time: Float, tint: Long = 0L) {
        val c = Offset(x, y)
        val blink = if (arming) 0.35f else 0.5f + 0.5f * sin(time * 12f + x)
        val col = if (tint != 0L) Color(tint) else Color(0xFFFF9A1A)
        drawCircle(Color.Black.copy(alpha = 0.35f), 13f, c)
        drawCircle(Color(0xFF1B1F2A), 10f, c)
        drawCircle(col.copy(alpha = 0.7f), 10f, c, style = Stroke(2f))
        drawCircle(col.copy(alpha = 0.25f * blink), 22f, c)
        drawCircle(col.copy(alpha = 0.6f + 0.4f * blink), 3.5f, c)
    }

    /** Malware Missile: a small warhead with an orange exhaust plume. */
    private fun DrawScope.drawMissile(c: Offset, vx: Float, vy: Float, time: Float) {
        val sp = max(1f, kotlin.math.hypot(vx, vy))
        val dx = vx / sp
        val dy = vy / sp
        val tail = Offset(c.x - dx * 26f, c.y - dy * 26f)
        val flick = 0.8f + 0.2f * sin(time * 50f)
        drawLine(Color(0xFFFF7A1A).copy(alpha = 0.25f), c, tail, 12f * flick, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(Color(0xFFFFC14D).copy(alpha = 0.7f), c, Offset(c.x - dx * 16f, c.y - dy * 16f), 5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(Color(0xFFE6EEF6), Offset(c.x - dx * 6f, c.y - dy * 6f), Offset(c.x + dx * 7f, c.y + dy * 7f), 7f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawCircle(Palette.Red, 3f, Offset(c.x + dx * 6f, c.y + dy * 6f))
    }

    /** Arc Discharge lightning and Orbital Strike target marks / beams. */
    private fun DrawScope.drawZaps(g: GameEngine, time: Float) {
        val lift = 22f
        for (z in g.zaps.items) {
            if (!z.active) continue
            when (z.kind) {
                com.cyberoperative.game.engine.ZapKind.ARC -> {
                    val fade = (1f - z.timer / z.duration).coerceIn(0f, 1f)
                    val path = Path()
                    val segs = 7
                    val r = kotlin.random.Random(z.seed + (time * 30f).toInt())
                    val x0 = z.x; val y0 = z.y - lift; val x1 = z.x2; val y1 = z.y2 - lift
                    val nx = -(y1 - y0); val ny = x1 - x0
                    val nl = max(1f, kotlin.math.hypot(nx, ny))
                    path.moveTo(x0, y0)
                    for (i in 1 until segs) {
                        val t = i / segs.toFloat()
                        val j = (r.nextFloat() - 0.5f) * 26f
                        path.lineTo(x0 + (x1 - x0) * t + nx / nl * j, y0 + (y1 - y0) * t + ny / nl * j)
                    }
                    path.lineTo(x1, y1)
                    val arcCol = if (z.color != 0L) Color(z.color) else Color(0xFFA259FF)
                    drawPath(path, arcCol.copy(alpha = 0.35f * fade), style = Stroke(9f))
                    drawPath(path, Color(0xFFD9BFFF).copy(alpha = 0.9f * fade), style = Stroke(3f))
                    drawPath(path, Color.White.copy(alpha = fade), style = Stroke(1.2f))
                    drawCircle(Color(0xFFD9BFFF).copy(alpha = 0.5f * fade), 14f, Offset(x1, y1))
                }
                com.cyberoperative.game.engine.ZapKind.LASER -> {
                    val fade = (1f - z.timer / z.duration).coerceIn(0f, 1f)
                    val col = Color(z.color)
                    val a = Offset(z.x, z.y - lift)
                    val b = Offset(z.x2, z.y2 - lift)
                    drawLine(col.copy(alpha = 0.25f * fade), a, b, z.radius * 3f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(col.copy(alpha = 0.85f * fade), a, b, z.radius, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = fade), a, b, z.radius * 0.35f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                }
                com.cyberoperative.game.engine.ZapKind.FIELD -> {
                    val col = Color(z.color)
                    val life = (1f - z.timer / z.duration).coerceIn(0f, 1f)
                    val fadeIn = (z.timer / 0.2f).coerceIn(0f, 1f)
                    val a = life * fadeIn
                    val c = Offset(z.x, z.y)
                    drawCircle(col.copy(alpha = 0.14f * a), z.radius, c)
                    drawCircle(col.copy(alpha = 0.6f * a), z.radius, c, style = Stroke(2.5f))
                    // Bubbling cells inside the zone.
                    for (k in 0 until 7) {
                        val ang = z.seed * 0.37f + k * 0.9f + time * 0.6f
                        val rr = z.radius * (0.25f + 0.1f * k)
                        val pulse = 0.5f + 0.5f * sin(time * 6f + k)
                        drawCircle(col.copy(alpha = 0.35f * a * pulse), 4f + 2f * pulse, Offset(z.x + cos(ang) * rr * 0.9f, z.y + sin(ang) * rr * 0.5f))
                    }
                }
                com.cyberoperative.game.engine.ZapKind.STRIKE -> {
                    val c = Offset(z.x, z.y)
                    val ti = if (z.color != 0L) Color(z.color) else Color(0xFFDCE8F2)
                    if (!z.landed) {
                        val f = (z.timer / z.duration).coerceIn(0f, 1f)
                        drawCircle(ti.copy(alpha = 0.12f), z.radius, c)
                        drawCircle(ti.copy(alpha = 0.8f), z.radius * (1f - 0.6f * f), c, style = Stroke(2.5f))
                        drawLine(ti.copy(alpha = 0.7f), Offset(z.x - 14f, z.y), Offset(z.x + 14f, z.y), 2f)
                        drawLine(ti.copy(alpha = 0.7f), Offset(z.x, z.y - 14f), Offset(z.x, z.y + 14f), 2f)
                    } else {
                        val f = ((z.timer - z.duration) / 0.3f).coerceIn(0f, 1f)
                        val a = 1f - f
                        drawLine(ti.copy(alpha = 0.3f * a), Offset(z.x, z.y - 900f), c, 60f * a + 10f)
                        drawLine(Color.White.copy(alpha = 0.95f * a), Offset(z.x, z.y - 900f), c, 16f * a + 2f)
                        drawCircle(Color.White.copy(alpha = 0.6f * a), z.radius * 0.6f, c)
                    }
                }
            }
        }
    }

    /** Plasma Beam: layered cyan glow with a white-hot core, muzzle flare and impact sparks. */
    private fun DrawScope.drawBeam(g: GameEngine, time: Float, lift: Float) {
        if (!g.beamActive) return
        val ang = g.facing
        val sx = g.px + cos(ang) * 30f
        val sy = g.py - lift - 6f + sin(ang) * 30f
        val ex = g.beamX2
        val ey = g.beamY2 - lift
        val w = g.beamWidth
        val flick = 0.85f + 0.15f * sin(time * 60f)
        val a = Offset(sx, sy)
        val b = Offset(ex, ey)
        drawLine(Palette.Cyan.copy(alpha = 0.10f), a, b, w * 2.6f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(Palette.Cyan.copy(alpha = 0.30f * flick), a, b, w * 1.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(Color(0xFF7DF9FF).copy(alpha = 0.85f), a, b, w * 0.75f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.95f * flick), a, b, w * 0.3f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        // Energy pulses travelling down the beam.
        val len = kotlin.math.hypot(ex - sx, ey - sy)
        if (len > 1f) for (k in 0 until 4) {
            val t = ((time * 2.2f + k * 0.25f) % 1f)
            drawCircle(Color.White.copy(alpha = 0.6f), w * 0.35f, Offset(sx + (ex - sx) * t, sy + (ey - sy) * t))
        }
        // Muzzle flare and impact.
        drawCircle(Palette.Cyan.copy(alpha = 0.25f), w * 1.6f, a)
        drawCircle(Color.White.copy(alpha = 0.9f), w * 0.6f, a)
        drawCircle(Palette.Cyan.copy(alpha = 0.3f * flick), w * 1.4f, b)
        drawCircle(Color.White.copy(alpha = 0.8f), w * 0.45f, b)
    }

    private fun DrawScope.drawHazardsUnder(g: GameEngine, time: Float) {
        for (h in g.hazards.items) {
            if (!h.active) continue
            val col = Color(h.color)
            when (h.kind) {
                HazardKind.ZONE -> {
                    if (h.timer < h.windup) {
                        drawCircle(col.copy(alpha = 0.6f), h.radius, Offset(h.x, h.y), style = Stroke(2f))
                        drawCircle(col.copy(alpha = 0.15f), h.radius * (h.timer / max(0.01f, h.windup)), Offset(h.x, h.y))
                    } else {
                        val p = 0.5f + 0.5f * sin(time * 6f + h.x)
                        drawCircle(col.copy(alpha = 0.22f + 0.1f * p), h.radius, Offset(h.x, h.y))
                        drawCircle(col.copy(alpha = 0.7f), h.radius, Offset(h.x, h.y), style = Stroke(2.5f))
                    }
                }
                HazardKind.BLAST -> {
                    val f = (h.timer / h.duration).coerceIn(0f, 1f)
                    drawCircle(col.copy(alpha = 0.12f), h.radius, Offset(h.x, h.y))
                    drawCircle(col.copy(alpha = 0.35f), h.radius * f, Offset(h.x, h.y))
                    drawCircle(col.copy(alpha = 0.85f), h.radius, Offset(h.x, h.y), style = Stroke(2.5f))
                }
                HazardKind.LINE -> {
                    val f = (h.timer / h.duration).coerceIn(0f, 1f)
                    drawLine(col.copy(alpha = 0.25f + 0.5f * f), Offset(h.x, h.y), Offset(h.x2, h.y2), 2f + 4f * f)
                }
                else -> {}
            }
        }
    }

    private fun DrawScope.drawHazardsOver(g: GameEngine) {
        for (h in g.hazards.items) {
            if (!h.active) continue
            val col = Color(h.color)
            when (h.kind) {
                HazardKind.SHOCK_RING -> {
                    drawCircle(col.copy(alpha = 0.8f), h.radius, Offset(h.x, h.y), style = Stroke(GameEngine.RING_THICKNESS * 1.4f))
                    drawCircle(Color.White.copy(alpha = 0.5f), h.radius, Offset(h.x, h.y), style = Stroke(3f))
                }
                HazardKind.BEAM -> {
                    if (h.timer < h.windup) {
                        val f = h.timer / h.windup
                        drawLine(col.copy(alpha = 0.2f + 0.35f * f), Offset(h.x, h.y), Offset(h.x2, h.y2), h.radius * f)
                        drawLine(col.copy(alpha = 0.7f), Offset(h.x, h.y), Offset(h.x2, h.y2), 2f)
                    } else {
                        drawLine(col.copy(alpha = 0.55f), Offset(h.x, h.y), Offset(h.x2, h.y2), h.radius * 1.3f)
                        drawLine(Color.White.copy(alpha = 0.9f), Offset(h.x, h.y), Offset(h.x2, h.y2), h.radius * 0.45f)
                    }
                }
                else -> {}
            }
        }
    }

    private fun DrawScope.drawPulses(g: GameEngine) {
        for (p in g.pulses.items) {
            if (!p.active) continue
            val a = (p.life / p.maxLife).coerceIn(0f, 1f)
            drawCircle(Color(p.color).copy(alpha = 0.6f * a), p.radius, Offset(p.x, p.y), style = Stroke(4f + 6f * a))
        }
    }

    private fun DrawScope.drawParticles(g: GameEngine) {
        for (p in g.particles.items) {
            if (!p.active) continue
            val a = (p.life / p.maxLife).coerceIn(0f, 1f)
            drawRect(Color(p.color).copy(alpha = a), Offset(p.x - p.size / 2f, p.y - 14f - p.size / 2f), Size(p.size, p.size))
        }
    }

    private fun DrawScope.drawTexts(g: GameEngine) {
        val canvas = drawContext.canvas.nativeCanvas
        for (t in g.texts.items) {
            if (!t.active) continue
            val color = when (t.kind) {
                TextKind.NORMAL -> 0xFFE6EEFA.toInt()
                TextKind.CRIT -> 0xFFFFD426.toInt()
                TextKind.BOSS -> 0xFFFFB0C0.toInt()
                TextKind.HEAL -> 0xFF00FF9C.toInt()
                TextKind.SHIELD -> 0xFFFF9A1A.toInt()
                TextKind.PLAYER_HURT -> 0xFFFF2D55.toInt()
                TextKind.INFO -> 0xFF7DF9FF.toInt()
            }
            val size = when (t.kind) {
                TextKind.CRIT -> 24f
                TextKind.PLAYER_HURT -> 22f
                TextKind.NORMAL, TextKind.SHIELD -> 18f
                else -> 20f
            }
            textPaint.textSize = size
            textPaint.color = color
            textPaint.alpha = (min(1f, t.life * 3f) * 255).toInt()
            textPaint.setShadowLayer(3f, 0f, 1f, 0xFF000000.toInt())
            canvas.drawText(t.text, t.x, t.y - 30f, textPaint)
        }
    }

    /** Spectrum / rainbow energy border for event levels (§26). */
    private fun DrawScope.drawSpectrumBorder(time: Float) {
        val w = size.width
        val h = size.height
        val segs = 36
        val per = (2 * (w + h)) / segs
        for (i in 0 until segs) {
            val hue = ((i * 360f / segs) + time * 120f) % 360f
            val col = Color.hsv(hue, 0.85f, 1f, 0.85f)
            var d = i * per
            val start: Offset
            val end: Offset
            when {
                d < w -> { start = Offset(d, 0f); end = Offset(min(w, d + per), 0f) }
                d < w + h -> { d -= w; start = Offset(w, d); end = Offset(w, min(h, d + per)) }
                d < 2 * w + h -> { d -= w + h; start = Offset(w - d, h); end = Offset(max(0f, w - d - per), h) }
                else -> { d -= 2 * w + h; start = Offset(0f, h - d); end = Offset(0f, max(0f, h - d - per)) }
            }
            drawLine(col, start, end, 7f)
        }
    }

    companion object {
        const val WALL_HEIGHT = 80f
        const val TILE = 60f
        const val FIGURE_SCALE = 1.3f
        private const val MAX_ITEMS = 160
    }
}
