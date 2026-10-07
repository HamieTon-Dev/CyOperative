package com.cyberoperative.game.ui.game

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.cyberoperative.game.core.MathUtil
import com.cyberoperative.game.data.LivingBackground
import com.cyberoperative.game.data.ObstacleKind
import com.cyberoperative.game.data.OperativeSkin
import com.cyberoperative.game.ui.common.OperativeMark
import com.cyberoperative.game.ui.game.LivingBackgroundDrawer.drawLivingBackground
import com.cyberoperative.game.data.ShapeKind
import com.cyberoperative.game.engine.AiState
import com.cyberoperative.game.engine.Enemy
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.HazardKind
import com.cyberoperative.game.engine.LevelKind
import com.cyberoperative.game.engine.Phase
import com.cyberoperative.game.engine.ProjKind
import com.cyberoperative.game.engine.TextKind
import com.cyberoperative.game.ui.theme.Palette
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws the arena (§12, §49). Readability first: the background is dim and
 * cool, obstacles are solid and outlined, hostile things are warm with dark
 * outlines, friendly things are cyan/green, and the operative is the logo.
 *
 * The world is 720 units wide; it is scaled to the canvas width and the
 * camera follows the operative vertically.
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

    var scale = 1f
        private set
    var camY = 0f
        private set

    fun draw(scope: DrawScope, g: GameEngine, time: Float, skin: OperativeSkin, background: LivingBackground, showNumbers: Boolean, topInset: Float, bottomInset: Float) = with(scope) {
        val arena = g.arena
        scale = size.width / arena.width
        val visibleH = size.height / scale
        // Camera: follow the player, keep a little more space above (enemies
        // come from the top) and clamp to the arena (with HUD/joystick insets).
        val top = -topInset / scale
        val bottom = arena.height + bottomInset / scale
        val desired = g.py - visibleH * 0.58f
        camY = if (bottom - top <= visibleH) (top + bottom - visibleH) / 2f
        else MathUtil.clamp(desired, top, bottom - visibleH)

        drawRect(Palette.SurfaceSunken)
        withTransform({
            scale(scale, scale, Offset.Zero)
            translate(0f, -camY)
        }) {
            drawFloor(g, time)
            drawLivingBackground(background, g.arena.width, g.arena.height, time, (g.aliveCount() / 16f).coerceIn(0f, 1f), g.px, g.py)
            drawHazardsUnder(g, time)
            drawObstacles(g, time)
            drawPortal(g, time)
            drawPulses(g)
            drawEnemies(g, time)
            drawOrbit(g, time)
            drawPlayer(g, time, skin)
            drawProjectiles(g)
            drawHazardsOver(g, time)
            drawParticles(g)
            if (showNumbers) drawTexts(g)
        }
        if (g.plan.kind == LevelKind.EVENT) drawSpectrumBorder(time)
        if (g.hurtFlash > 0f) drawRect(Palette.Red.copy(alpha = 0.18f * (g.hurtFlash / 0.25f)))
        if (g.phase == Phase.TRANSITION) drawRect(Color.Black.copy(alpha = min(1f, g.phaseTimer / GameEngine.TRANSITION_TIME)))
    }

    // --- Floor --------------------------------------------------------------

    private fun DrawScope.drawFloor(g: GameEngine, time: Float) {
        val w = g.arena.width
        val h = g.arena.height
        val event = g.plan.event
        val floor = when {
            g.plan.kind == LevelKind.BOSS -> Color(0xFF0D0710)
            event != null -> Color(event.accent).copy(alpha = 0.06f).compositeOver(Palette.Background)
            else -> Palette.Background
        }
        drawRect(floor, Offset.Zero, Size(w, h))
        // Circuit grid
        val step = 60f
        val grid = (if (g.plan.kind == LevelKind.BOSS) Palette.RedDeep.copy(alpha = 0.18f) else Palette.GridLine)
        var x = 0f
        while (x <= w) { drawLine(grid, Offset(x, 0f), Offset(x, h), 1.5f); x += step }
        var y = 0f
        while (y <= h) { drawLine(grid, Offset(0f, y), Offset(w, y), 1.5f); y += step }
        // Packets travelling along grid lines (ambient, very faint).
        val pc = Palette.Cyan.copy(alpha = 0.22f)
        for (i in 0 until 10) {
            val lane = (i * 7 % 12) * step
            val p = ((time * (40f + i * 9f) + i * 137f) % (h + 200f)) - 100f
            if (i % 2 == 0) drawCircle(pc, 2.5f, Offset(lane, p)) else drawCircle(pc, 2.5f, Offset((p * 0.6f) % w, (i * 5 % 18) * step))
        }
        // Arena wall
        val wall = if (g.plan.kind == LevelKind.BOSS) Palette.Red else Palette.CyanDim
        drawRect(wall.copy(alpha = 0.7f), Offset.Zero, Size(w, h), style = Stroke(4f))
        drawRect(wall.copy(alpha = 0.15f), Offset(-6f, -6f), Size(w + 12f, h + 12f), style = Stroke(10f))
    }

    private fun Color.compositeOver(bg: Color): Color {
        val a = alpha
        return Color(red * a + bg.red * (1 - a), green * a + bg.green * (1 - a), blue * a + bg.blue * (1 - a), 1f)
    }

    // --- Obstacles ----------------------------------------------------------

    private fun DrawScope.drawObstacles(g: GameEngine, time: Float) {
        val obstacles = g.arena.obstacles
        for (index in obstacles.indices) {
            val o = obstacles[index]
            val r = o.rect
            val tl = Offset(r.left, r.top)
            val sz = Size(r.width, r.height)
            val cr = CornerRadius(6f, 6f)
            drawRoundRect(Color(0xFF000000).copy(alpha = 0.45f), tl + Offset(5f, 7f), sz, cr)
            when (o.kind) {
                ObstacleKind.SERVER_RACK -> {
                    drawRoundRect(Color(0xFF162238), tl, sz, cr)
                    drawRoundRect(Palette.Cyan.copy(alpha = 0.55f), tl, sz, cr, style = Stroke(2f))
                    // Blinking LED rows (CyOps TD rack animation idea).
                    val horizontal = r.width >= r.height
                    val count = if (horizontal) (r.width / 22f).toInt() else (r.height / 22f).toInt()
                    for (k in 0 until count) {
                        val on = sin(time * (2f + (k % 3)) + k * 1.3f + index) > 0.1f
                        val col = if ((k + index) % 4 == 0) Palette.ServerLedAmber else Palette.ServerLedGreen
                        val c = col.copy(alpha = if (on) 0.95f else 0.18f)
                        if (horizontal) drawCircle(c, 3f, Offset(r.left + 12f + k * 22f, r.top + r.height * 0.35f))
                        else drawCircle(c, 3f, Offset(r.left + r.width * 0.3f, r.top + 12f + k * 22f))
                        if (horizontal) drawLine(Palette.Divider, Offset(r.left + 6f + k * 22f, r.top + r.height * 0.7f), Offset(r.left + 18f + k * 22f, r.top + r.height * 0.7f), 2f)
                        else drawLine(Palette.Divider, Offset(r.left + r.width * 0.5f, r.top + 12f + k * 22f), Offset(r.left + r.width * 0.8f, r.top + 12f + k * 22f), 2f)
                    }
                }
                ObstacleKind.FIREWALL_NODE -> {
                    drawRoundRect(Color(0xFF2A1408), tl, sz, cr)
                    val pulse = 0.5f + 0.5f * sin(time * 3f + index)
                    drawRoundRect(Palette.Orange.copy(alpha = 0.5f + 0.4f * pulse), tl, sz, cr, style = Stroke(2.5f))
                    // Brick pattern = firewall.
                    var by = r.top + 12f
                    var row = 0
                    while (by < r.bottom) {
                        drawLine(Palette.Orange.copy(alpha = 0.3f), Offset(r.left, by), Offset(r.right, by), 1.5f)
                        var bx = r.left + (if (row % 2 == 0) 18f else 0f)
                        while (bx < r.right) {
                            drawLine(Palette.Orange.copy(alpha = 0.3f), Offset(bx, by - 12f), Offset(bx, by), 1.5f); bx += 36f
                        }
                        by += 12f; row++
                    }
                }
                ObstacleKind.DATA_PILLAR -> {
                    drawRoundRect(Color(0xFF0E2A33), tl, sz, cr)
                    drawRoundRect(Palette.Cyan.copy(alpha = 0.7f), tl, sz, cr, style = Stroke(2.5f))
                    val f = (time * 0.6f + index * 0.3f) % 1f
                    drawLine(Palette.Cyan.copy(alpha = 0.6f * (1f - f)), Offset(r.left + 4f, r.bottom - f * r.height), Offset(r.right - 4f, r.bottom - f * r.height), 3f)
                    drawCircle(Palette.Cyan.copy(alpha = 0.25f), min(r.width, r.height) * 0.22f, Offset(r.centerX, r.centerY))
                }
                ObstacleKind.COOLING_UNIT -> {
                    drawRoundRect(Color(0xFF13202C), tl, sz, cr)
                    drawRoundRect(Palette.Blue.copy(alpha = 0.6f), tl, sz, cr, style = Stroke(2f))
                    val c = Offset(r.centerX, r.centerY)
                    val rad = min(r.width, r.height) * 0.36f
                    drawCircle(Palette.Divider, rad, c, style = Stroke(2f))
                    rotate(time * 220f + index * 40f, c) {
                        for (b in 0 until 4) {
                            val a = b * MathUtil.PI / 2f
                            drawLine(Palette.Blue.copy(alpha = 0.7f), c, Offset(c.x + cos(a) * rad, c.y + sin(a) * rad), 4f)
                        }
                    }
                }
                ObstacleKind.ROUTER -> {
                    drawRoundRect(Color(0xFF101C2E), tl, sz, cr)
                    drawRoundRect(Palette.Green.copy(alpha = 0.55f), tl, sz, cr, style = Stroke(2f))
                    for (k in 0 until 4) {
                        val on = ((time * 4f).toInt() + k + index) % 4 != 0
                        drawCircle(Palette.Green.copy(alpha = if (on) 0.9f else 0.15f), 3f, Offset(r.left + r.width * (0.2f + 0.2f * k), r.bottom - 10f))
                    }
                    drawLine(Palette.Green.copy(alpha = 0.6f), Offset(r.left + 10f, r.top), Offset(r.left + 4f, r.top - 14f), 2f)
                    drawLine(Palette.Green.copy(alpha = 0.6f), Offset(r.right - 10f, r.top), Offset(r.right - 4f, r.top - 14f), 2f)
                }
                ObstacleKind.TERMINAL -> {
                    drawRoundRect(Color(0xFF1B1020), tl, sz, cr)
                    drawRoundRect(Palette.Purple.copy(alpha = 0.6f), tl, sz, cr, style = Stroke(2f))
                    val glitch = ((time * 9f).toInt() + index) % 13 == 0
                    drawRect(Palette.Purple.copy(alpha = if (glitch) 0.5f else 0.18f), Offset(r.left + 8f, r.top + 8f), Size(r.width - 16f, r.height - 22f))
                    if (!glitch) drawLine(Palette.Green.copy(alpha = 0.6f), Offset(r.left + 12f, r.top + 16f), Offset(r.left + 12f + ((time * 30f) % (r.width - 30f)), r.top + 16f), 2f)
                }
                ObstacleKind.POWER_UNIT -> {
                    drawRoundRect(Color(0xFF1A1A0C), tl, sz, cr)
                    drawRoundRect(Palette.Gold.copy(alpha = 0.55f), tl, sz, cr, style = Stroke(2f))
                    val c = Offset(r.centerX, r.centerY)
                    val p = 0.5f + 0.5f * sin(time * 2.4f + index)
                    drawCircle(Palette.Gold.copy(alpha = 0.15f + 0.25f * p), min(r.width, r.height) * 0.3f, c)
                    // lightning glyph
                    val s = min(r.width, r.height) * 0.18f
                    shapePath.reset()
                    shapePath.moveTo(c.x + s * 0.2f, c.y - s * 1.2f); shapePath.lineTo(c.x - s * 0.6f, c.y + s * 0.1f)
                    shapePath.lineTo(c.x, c.y + s * 0.1f); shapePath.lineTo(c.x - s * 0.2f, c.y + s * 1.2f)
                    shapePath.lineTo(c.x + s * 0.6f, c.y - s * 0.1f); shapePath.lineTo(c.x, c.y - s * 0.1f); shapePath.close()
                    drawPath(shapePath, Palette.Gold.copy(alpha = 0.8f))
                }
                ObstacleKind.FIBER_JUNCTION -> {
                    val vault = g.plan.rules.vault && index == g.arena.obstacles.lastIndex
                    val col = if (vault) Palette.Gold else Palette.Purple
                    drawRoundRect(Color(0xFF140E22), tl, sz, cr)
                    drawRoundRect(col.copy(alpha = 0.7f), tl, sz, cr, style = Stroke(2.5f))
                    val f = (time * 0.8f + index * 0.17f) % 1f
                    val vertical = r.height > r.width
                    if (vertical) drawCircle(col, 4f, Offset(r.centerX, r.top + f * r.height))
                    else drawCircle(col, 4f, Offset(r.left + f * r.width, r.centerY))
                    if (vault) {
                        tagPaint.textSize = 18f
                        tagPaint.color = Palette.Gold.toArgb()
                        drawContext.canvas.nativeCanvas.drawText("[\$\$\$]", r.centerX, r.centerY + 6f, tagPaint)
                    }
                }
            }
        }
    }

    // --- Portal -------------------------------------------------------------

    private fun DrawScope.drawPortal(g: GameEngine, time: Float) {
        val x = g.arena.portalX
        val y = g.arena.portalY
        if (!g.portalOpen) {
            drawRoundRect(Palette.Divider, Offset(x - 50f, y - 16f), Size(100f, 32f), CornerRadius(6f, 6f), style = Stroke(2f))
            return
        }
        val p = 0.5f + 0.5f * sin(time * 4f)
        drawCircle(Palette.Green.copy(alpha = 0.18f + 0.1f * p), GameEngine.PORTAL_RADIUS + 14f, Offset(x, y))
        drawCircle(Palette.Green.copy(alpha = 0.9f), GameEngine.PORTAL_RADIUS, Offset(x, y), style = Stroke(4f))
        rotate(time * 90f, Offset(x, y)) {
            for (i in 0 until 6) {
                val a = i * MathUtil.TWO_PI / 6f
                drawCircle(Palette.Green, 4f, Offset(x + cos(a) * (GameEngine.PORTAL_RADIUS - 10f), y + sin(a) * (GameEngine.PORTAL_RADIUS - 10f)))
            }
        }
        tagPaint.textSize = 16f
        tagPaint.color = Palette.Green.toArgb()
        drawContext.canvas.nativeCanvas.drawText("ACCESS PORT", x, y + GameEngine.PORTAL_RADIUS + 26f, tagPaint)
    }

    // --- Enemies ------------------------------------------------------------

    private fun DrawScope.drawEnemies(g: GameEngine, time: Float) {
        for (e in g.enemies.items) {
            if (!e.active) continue
            val c = Offset(e.x, e.y)
            val base = Color(e.def.color)
            if (e.state == AiState.SPAWNING) {
                val t = e.stateTimer
                val p = 0.5f + 0.5f * sin(time * 18f)
                drawCircle(Palette.Red.copy(alpha = 0.25f + 0.25f * p), e.radius * (1.2f + t), c, style = Stroke(3f))
                drawCircle(Palette.Red.copy(alpha = 0.12f), e.radius, c)
                continue
            }
            if (e.state == AiState.HIDDEN) {
                drawCircle(base.copy(alpha = 0.12f), e.radius, c, style = Stroke(2f))
                continue
            }
            val elite = e.elite
            if (elite != null) {
                val p = 0.5f + 0.5f * sin(time * 5f + e.uid)
                drawCircle(Color(elite.color).copy(alpha = 0.18f + 0.15f * p), e.radius * 1.55f, c)
                drawCircle(Color(elite.color).copy(alpha = 0.8f), e.radius * 1.3f, c, style = Stroke(2.5f))
            }
            val boss = e.boss
            if (boss != null) {
                val p = 0.5f + 0.5f * sin(time * 3f)
                drawCircle(base.copy(alpha = 0.12f + 0.08f * p), e.radius * 1.7f, c)
            }
            // Windup flash so every attack is readable.
            val winding = e.state == AiState.WINDUP
            val fill = when {
                e.hitFlash > 0f -> Color.White
                winding && ((time * 16f).toInt() % 2 == 0) -> Color.White.copy(alpha = 0.85f)
                else -> base
            }
            drawShape(e.def.shape, c, e.radius, fill, if (boss != null) time * 25f else 0f)
            drawShape(e.def.shape, c, e.radius, Color(0xFF1A0006), if (boss != null) time * 25f else 0f, stroke = 3f)
            // Tag (CyOps TD ASCII identity)
            tagPaint.textSize = if (boss != null) 22f else max(11f, e.radius * 0.75f)
            tagPaint.color = 0xFF14040A.toInt()
            drawContext.canvas.nativeCanvas.drawText(e.def.tag, e.x, e.y + tagPaint.textSize * 0.35f, tagPaint)
            // Health bar for non-boss enemies that have been hurt.
            if (boss == null && e.hp < e.maxHp) {
                val bw = e.radius * 2f
                val by = e.y - e.radius - 10f
                drawRect(Color(0xAA000000), Offset(e.x - bw / 2f, by), Size(bw, 4f))
                drawRect(Palette.Red, Offset(e.x - bw / 2f, by), Size(bw * (e.hp / e.maxHp).coerceIn(0f, 1f), 4f))
            }
            if (e.uid == g.targetUid) {
                drawCircle(Palette.Cyan.copy(alpha = 0.8f), e.radius + 8f, c, style = Stroke(2f))
            }
        }
    }

    private fun DrawScope.drawShape(shape: ShapeKind, c: Offset, r: Float, color: Color, rotationDeg: Float, stroke: Float = 0f) {
        val style = if (stroke > 0f) Stroke(stroke) else androidx.compose.ui.graphics.drawscope.Fill
        when (shape) {
            ShapeKind.CIRCLE -> drawCircle(color, r, c, style = style)
            ShapeKind.SQUARE -> rotate(rotationDeg, c) {
                drawRoundRect(color, Offset(c.x - r * 0.88f, c.y - r * 0.88f), Size(r * 1.76f, r * 1.76f), CornerRadius(4f, 4f), style = style)
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

    // --- Player and orbit ---------------------------------------------------

    private fun DrawScope.drawOrbit(g: GameEngine, time: Float) {
        val s = g.stats
        for (i in 0 until s.orbCount) {
            val a = g.orbAngle + MathUtil.TWO_PI * i / s.orbCount
            val x = g.px + cos(a) * s.orbRadius
            val y = g.py + sin(a) * s.orbRadius
            // short trail
            for (k in 1..3) {
                val ta = a - k * 0.12f
                drawCircle(Palette.Cyan.copy(alpha = 0.12f * (4 - k)), s.orbSize * (1f - k * 0.15f), Offset(g.px + cos(ta) * s.orbRadius, g.py + sin(ta) * s.orbRadius))
            }
            drawCircle(Palette.Cyan.copy(alpha = 0.25f), s.orbSize * 1.7f, Offset(x, y))
            drawCircle(Palette.Cyan, s.orbSize, Offset(x, y))
            drawCircle(Color.White.copy(alpha = 0.85f), s.orbSize * 0.45f, Offset(x, y))
        }
        for (i in 0 until s.bladeCount) {
            val a = g.bladeAngle + MathUtil.TWO_PI * i / s.bladeCount
            val x = g.px + cos(a) * s.bladeRadius
            val y = g.py + sin(a) * s.bladeRadius
            rotate(Math.toDegrees(a.toDouble()).toFloat() + 90f, Offset(x, y)) {
                drawRoundRect(Palette.Green.copy(alpha = 0.3f), Offset(x - 16f, y - 6f), Size(32f, 12f), CornerRadius(6f, 6f))
                drawRoundRect(Palette.Green, Offset(x - 13f, y - 3.5f), Size(26f, 7f), CornerRadius(4f, 4f))
            }
        }
    }

    private fun DrawScope.drawPlayer(g: GameEngine, time: Float, skin: OperativeSkin) {
        if (g.phase == Phase.DEAD) return
        val c = Offset(g.px, g.py)
        val r = g.playerRadius
        val blink = g.invuln > 0f && ((time * 20f).toInt() % 2 == 0)
        // Firewall bubble
        if (g.firewall > 0f) {
            val f = g.firewall / max(1f, g.stats.firewallMax)
            drawCircle(Palette.Orange.copy(alpha = 0.10f + 0.12f * f), r * 1.75f, c)
            drawCircle(Palette.Orange.copy(alpha = 0.35f + 0.4f * f), r * 1.75f, c, style = Stroke(2f))
        }
        drawCircle(Palette.Cyan.copy(alpha = 0.16f), r * 1.5f, c)
        // Facing indicator (aim chevron)
        val fx = cos(g.facing)
        val fy = sin(g.facing)
        val tip = Offset(g.px + fx * (r + 14f), g.py + fy * (r + 14f))
        drawLine(if (g.moving) Palette.TextMuted else Palette.Green, Offset(g.px + fx * (r + 2f), g.py + fy * (r + 2f)), tip, 4f)
        if (!blink) {
            with(OperativeMark) { drawOperative(skin, time, c, r * 2.7f) }
        }
        // "FIRING" ring when stationary with a target.
        if (!g.moving && g.targetUid >= 0) {
            drawCircle(Palette.Green.copy(alpha = 0.5f), r * 1.35f, c, style = Stroke(1.5f))
        }
    }

    // --- Projectiles / hazards / effects -----------------------------------

    private fun DrawScope.drawProjectiles(g: GameEngine) {
        for (p in g.projectiles.items) {
            if (!p.active) continue
            val c = Offset(p.x, p.y)
            if (p.friendly) {
                val col = when (p.kind) {
                    ProjKind.LANCE -> Palette.Green
                    ProjKind.CONE -> Palette.Blue
                    ProjKind.NODE_BOLT -> Palette.Cyan
                    ProjKind.COUNTER -> Palette.Green
                    else -> if (p.crit) Palette.Gold else Palette.Cyan
                }
                val len = if (p.kind == ProjKind.LANCE) 0.06f else 0.025f
                drawLine(col.copy(alpha = 0.45f), c, Offset(p.x - p.vx * len, p.y - p.vy * len), p.radius * 1.4f)
                drawCircle(col, p.radius, c)
                drawCircle(Color.White.copy(alpha = 0.8f), p.radius * 0.45f, c)
            } else {
                val col = if (p.kind == ProjKind.BOSS) Palette.Magenta else Palette.Orange
                // Dark outline keeps hostile packets readable on any floor.
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
                    val armed = h.timer >= h.windup
                    if (!armed) {
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
                else -> {}
            }
        }
    }

    private fun DrawScope.drawHazardsOver(g: GameEngine, time: Float) {
        for (h in g.hazards.items) {
            if (!h.active) continue
            val col = Color(h.color)
            when (h.kind) {
                HazardKind.LINE -> {
                    val f = (h.timer / h.duration).coerceIn(0f, 1f)
                    drawLine(col.copy(alpha = 0.25f + 0.5f * f), Offset(h.x, h.y), Offset(h.x2, h.y2), 2f + 4f * f)
                }
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
            drawRect(Color(p.color).copy(alpha = a), Offset(p.x - p.size / 2f, p.y - p.size / 2f), Size(p.size, p.size))
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
            val alpha = (min(1f, t.life * 3f) * 255).toInt()
            textPaint.textSize = size
            textPaint.color = color
            textPaint.alpha = alpha
            textPaint.setShadowLayer(3f, 0f, 1f, 0xFF000000.toInt())
            canvas.drawText(t.text, t.x, t.y, textPaint)
        }
    }

    /** Spectrum / rainbow energy border for event levels (§26). */
    private fun DrawScope.drawSpectrumBorder(time: Float) {
        val w = size.width
        val h = size.height
        val segs = 36
        val thick = 7f
        val per = (2 * (w + h)) / segs
        for (i in 0 until segs) {
            val hue = ((i * 360f / segs) + time * 120f) % 360f
            val col = Color.hsv(hue, 0.85f, 1f, 0.85f)
            var d = i * per
            // Walk the perimeter: top, right, bottom, left.
            val start: Offset
            val end: Offset
            when {
                d < w -> { start = Offset(d, 0f); end = Offset(min(w, d + per), 0f) }
                d < w + h -> { d -= w; start = Offset(w, d); end = Offset(w, min(h, d + per)) }
                d < 2 * w + h -> { d -= w + h; start = Offset(w - d, h); end = Offset(max(0f, w - d - per), h) }
                else -> { d -= 2 * w + h; start = Offset(0f, h - d); end = Offset(0f, max(0f, h - d - per)) }
            }
            drawLine(col, start, end, thick)
        }
    }
}
