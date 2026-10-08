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
            drawProjectiles(g)
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

    private fun DrawScope.drawFloor(g: GameEngine, time: Float) {
        val w = g.arena.width
        val h = g.arena.height
        val event = g.plan.event
        val floor = when {
            g.plan.kind == LevelKind.BOSS -> Color(0xFF100810)
            event != null -> mix(Color(event.accent), Palette.Background, 0.07f)
            else -> Color(0xFF0A1220)
        }
        drawRect(floor, Offset.Zero, Size(w, h))
        // Floor plates (subtle bevel gives the floor a tiled, physical feel).
        val step = 60f
        val plate = if (g.plan.kind == LevelKind.BOSS) Palette.RedDeep.copy(alpha = 0.16f) else Color(0xFF16233A)
        var x = 0f
        while (x <= w) { drawLine(plate, Offset(x, 0f), Offset(x, h), 2f); x += step }
        var y = 0f
        while (y <= h) {
            drawLine(plate, Offset(0f, y), Offset(w, y), 2f)
            drawLine(Color(0x0DFFFFFF), Offset(0f, y + 2f), Offset(w, y + 2f), 1f)
            y += step
        }
        // Packets running along the plate seams.
        val pc = Palette.Cyan.copy(alpha = 0.25f)
        for (i in 0 until 8) {
            val lane = ((i * 5 + 1) % 12) * step
            val p = ((time * (45f + i * 11f) + i * 137f) % (h + 200f)) - 100f
            drawCircle(pc, 2.5f, Offset(lane, p))
        }
        // Side walls: a low raised kerb.
        val wall = if (g.plan.kind == LevelKind.BOSS) Palette.Red else Palette.CyanDim
        drawRect(Color(0xFF0E1830), Offset(-14f, 0f), Size(14f, h))
        drawRect(Color(0xFF0E1830), Offset(w, 0f), Size(14f, h))
        drawLine(wall.copy(alpha = 0.7f), Offset(0f, 0f), Offset(0f, h), 3f)
        drawLine(wall.copy(alpha = 0.7f), Offset(w, 0f), Offset(w, h), 3f)
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
        ObstacleKind.SERVER_RACK -> 72f
        ObstacleKind.SMALL_SERVER -> 46f
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
            ObstacleKind.CRATES -> Look(Color(0xFF353B48), Color(0xFF222731), Color(0xFF93A6C4))
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
        // Top face.
        drawRoundRect(look.top, Offset(r.left, topY), Size(r.width, r.height), cr)
        drawRoundRect(look.trim.copy(alpha = 0.55f), Offset(r.left, topY), Size(r.width, r.height), cr, style = Stroke(2f))
        drawLine(look.trim.copy(alpha = 0.35f), Offset(r.left + 3f, r.bottom - h), Offset(r.right - 3f, r.bottom - h), 2f)

        val frontTop = r.bottom - h
        when (o.kind) {
            ObstacleKind.SERVER_RACK, ObstacleKind.SMALL_SERVER -> {
                // Blinking LED rows on the front face.
                val cols = max(1, ((r.width - 10f) / 16f).toInt())
                val rows = max(1, ((h - 12f) / 14f).toInt())
                for (row in 0 until rows) for (c in 0 until cols) {
                    val on = sin(time * (1.8f + (c % 3) * 0.9f) + c * 1.3f + row * 2.1f + index) > 0.25f
                    val col = if ((c + row + index) % 5 == 0) Palette.ServerLedAmber else Palette.ServerLedGreen
                    drawCircle(col.copy(alpha = if (on) 0.95f else 0.14f), 2.4f, Offset(r.left + 9f + c * 16f, frontTop + 9f + row * 14f))
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
                drawLine(Color(0xFF3A4252), Offset(r.left, frontTop + h / 2f), Offset(r.right, frontTop + h / 2f), 2f)
                drawLine(Color(0xFF3A4252), Offset(r.centerX, frontTop), Offset(r.centerX, r.bottom), 2f)
                drawLine(Palette.Gold.copy(alpha = 0.4f), Offset(r.left + 6f, topY + 6f), Offset(r.right - 6f, topY + r.height - 6f), 2f)
            }
        }
    }

    // --- Enemies -------------------------------------------------------------

    private fun DrawScope.drawEnemy(g: GameEngine, e: Enemy, time: Float) {
        val base = Color(e.def.color)
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
        val winding = e.state == AiState.WINDUP
        val rot = if (boss != null) time * 25f else 0f
        // Extruded body: dark side first, then lit top, then a rim highlight.
        drawShape(e.def.shape, Offset(cx, cy + depth), e.radius, darken(base, 0.45f), rot)
        val fill = when {
            e.hitFlash > 0f -> Color.White
            winding && ((time * 16f).toInt() % 2 == 0) -> Color.White.copy(alpha = 0.9f)
            else -> base
        }
        drawShape(e.def.shape, Offset(cx, cy), e.radius, fill, rot)
        drawShape(e.def.shape, Offset(cx, cy), e.radius, Color(0xFF1A0006), rot, stroke = 2.5f)
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
                    ShapeKind.HEXAGON -> 6
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
                        val x = c.x + cos(a) * rr
                        val y = c.y + sin(a) * rr
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
        drawFigure(body, skin, g.px, foot, FIGURE_SCALE, g.facing, g.moving, time, alpha = if (blink) 0.35f else 1f)
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

    private fun DrawScope.drawProjectiles(g: GameEngine) {
        val lift = 22f
        for (p in g.projectiles.items) {
            if (!p.active) continue
            drawCircle(Color.Black.copy(alpha = 0.25f), p.radius * 0.8f, Offset(p.x, p.y))
            val c = Offset(p.x, p.y - lift)
            if (p.friendly) {
                val col = when (p.kind) {
                    ProjKind.LANCE -> Palette.Green
                    ProjKind.CONE -> Palette.Blue
                    ProjKind.NODE_BOLT, ProjKind.COUNTER -> Palette.Cyan
                    else -> if (p.crit) Palette.Gold else Palette.Cyan
                }
                val len = if (p.kind == ProjKind.LANCE) 0.06f else 0.025f
                drawLine(col.copy(alpha = 0.45f), c, Offset(p.x - p.vx * len, p.y - lift - p.vy * len), p.radius * 1.4f)
                drawCircle(col, p.radius, c)
                drawCircle(Color.White.copy(alpha = 0.8f), p.radius * 0.45f, c)
            } else {
                val col = if (p.kind == ProjKind.BOSS) Palette.Magenta else Palette.Orange
                drawCircle(Color(0xFF1A0006), p.radius + 2.5f, c)
                drawCircle(col, p.radius, c)
                drawCircle(Color(0xFFFFE6D0), p.radius * 0.4f, c)
                if (p.homing > 0f) drawCircle(col.copy(alpha = 0.4f), p.radius + 6f, c, style = Stroke(1.5f))
            }
        }
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
        const val FIGURE_SCALE = 1.3f
        private const val MAX_ITEMS = 160
    }
}
