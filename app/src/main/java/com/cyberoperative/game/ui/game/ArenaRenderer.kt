package com.cyberoperative.game.ui.game

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.cyberoperative.game.core.MathUtil
import com.cyberoperative.game.data.AccentKind
import com.cyberoperative.game.data.DecorKind
import com.cyberoperative.game.data.Environments
import com.cyberoperative.game.data.FloorPattern
import com.cyberoperative.game.data.FloorStyle
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
        background: LivingBackground, showNumbers: Boolean, topInset: Float, bottomInset: Float,
        /** Co-op partner's look (their own skin and body). */
        partnerSkin: OperativeSkin = skin, partnerBody: BodyStyle = body,
        /** Settings → SCREEN SHAKE. */
        shake: Boolean = true
    ) = with(scope) {
        this@ArenaRenderer.partnerSkin = partnerSkin
        this@ArenaRenderer.partnerBody = partnerBody
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
            val fxShake = if (shake) g.fx.shakeNow else 0f
            val sx = (if (shake) shakeX(g, time) else 0f) + sin(time * 83f) * fxShake
            val sy = cos(time * 71f) * fxShake * 0.7f
            translate(sx, -camY + slide + sy)
        }) {
            drawFloor(g, time)
            drawLivingBackground(background, arena.width, arena.height, time, (g.aliveCount() / 16f).coerceIn(0f, 1f), g.px, g.py)
            drawDecor(g, time)
            drawTopWall(g, time)
            drawHazardsUnder(g, time)
            drawBarrierGhosts(g, time)
            drawPulses(g)
            drawShadows(g)
            drawEnemyMarkers(g, time)
            drawSorted(g, time, skin, body)
            drawXray(g, time)
            drawOrbitFront(g, time)
            if (g.darknessNow > 0.01f) {
                drawDarkness(g, time)
                // Telegraphs must still be readable in the dark.
                drawHazardsUnder(g, time)
                drawBarrierGhosts(g, time)
            }
            drawProjectiles(g, time)
            drawZaps(g, time)
            drawHazardsOver(g)
            drawParticles(g)
            if (showNumbers) drawTexts(g)
        }
        if (g.plan.kind == LevelKind.EVENT) drawSpectrumBorder(time)
        if (g.hurtFlash > 0f) drawRect(Palette.Red.copy(alpha = 0.18f * (g.hurtFlash / 0.25f)))
        val flash = g.fx.flashNow
        if (flash > 0f) drawRect(Color(g.fx.flashColor).copy(alpha = flash.coerceIn(0f, 1f)))
    }

    private var partnerSkin: OperativeSkin? = null
    private var partnerBody: BodyStyle? = null

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
    private var styleKey = Long.MIN_VALUE
    private var cachedStyle: FloorStyle? = null

    /** This level's theme and floor variation (cached; rebuilt when the level changes). */
    private fun styleOf(g: GameEngine): FloorStyle {
        val key = g.level.toLong() * 1_000_003L + g.config.seed
        val c = cachedStyle
        if (c != null && key == styleKey) return c
        styleKey = key
        return Environments.styleFor(g.level, g.config.seed).also { cachedStyle = it }
    }

    private fun shadeOf(c: Long, shade: Float): Color {
        val k = 1f + 0.12f * shade
        val col = Color(c)
        return Color((col.red * k).coerceIn(0f, 1f), (col.green * k).coerceIn(0f, 1f), (col.blue * k).coerceIn(0f, 1f), col.alpha)
    }

    /**
     * Floors (owner, 2026-10-08): a theme every 3 levels, and inside it each
     * level rolls its own tile pattern, tile size, trench spacing and shade.
     * Boss arenas keep their red crimson look.
     */
    private fun DrawScope.drawFloor(g: GameEngine, time: Float) {
        val w = g.arena.width
        val h = g.arena.height
        val boss = g.plan.kind == LevelKind.BOSS
        val event = g.plan.event
        val st = styleOf(g)
        val env = st.env
        val base = when {
            boss -> Color(0xFF0C0610)
            event != null -> mix(Color(event.accent), Color(env.base), 0.06f)
            else -> shadeOf(env.base, st.shade)
        }
        val plateA = if (boss) Color(0xFF1A0E18) else shadeOf(env.plateA, st.shade)
        val plateB = if (boss) Color(0xFF160B14) else shadeOf(env.plateB, st.shade)
        val hi = if (boss) Color(0x22FF6080) else Color(env.bevel)
        val lo = Color(0x66000000)
        val accent = if (boss) Palette.Red else Color(env.accent)
        val accent2 = if (boss) Palette.Magenta else Color(env.accent2)
        val pattern = if (boss) FloorPattern.PLATES else st.pattern
        drawRect(base, Offset.Zero, Size(w, h))
        val step = if (boss) TILE else st.tile
        val cols = (w / step).toInt() + 1
        val rows = (h / step).toInt() + 1
        for (r in 0 until rows) {
            val y = r * step
            for (c in 0 until cols) {
                val x = c * step
                val hash = (c * 73856093) xor (r * 19349663) xor g.level * 83492791
                val k = (hash ushr 3) and 31
                val plate = if (k % 3 == 0) plateB else plateA
                when (pattern) {
                    FloorPattern.PLATES -> {
                        drawRect(plate, Offset(x + 2f, y + 2f), Size(step - 4f, step - 4f))
                        drawLine(hi, Offset(x + 3f, y + 3f), Offset(x + step - 4f, y + 3f), 1.5f)
                        drawLine(hi, Offset(x + 3f, y + 3f), Offset(x + 3f, y + step - 4f), 1.5f)
                        drawLine(lo, Offset(x + 4f, y + step - 3f), Offset(x + step - 3f, y + step - 3f), 2f)
                        drawLine(lo, Offset(x + step - 3f, y + 4f), Offset(x + step - 3f, y + step - 3f), 2f)
                    }
                    FloorPattern.GRID -> {
                        drawRect(plate, Offset(x, y), Size(step, step))
                        drawLine(accent.copy(alpha = 0.16f), Offset(x, y), Offset(x + step, y), 1f)
                        drawLine(accent.copy(alpha = 0.16f), Offset(x, y), Offset(x, y + step), 1f)
                        drawCircle(accent.copy(alpha = 0.3f), 1.6f, Offset(x, y))
                    }
                    FloorPattern.HEX -> {
                        drawRect(plate, Offset(x, y), Size(step, step))
                        val cx = x + step / 2f + if (r % 2 == 0) 0f else step / 2f
                        val cy = y + step / 2f
                        val rr = step * 0.48f
                        shapePath.reset()
                        for (i in 0 until 6) {
                            val a = MathUtil.PI / 6f + i * MathUtil.PI / 3f
                            val px = cx + cos(a) * rr
                            val py = cy + sin(a) * rr
                            if (i == 0) shapePath.moveTo(px, py) else shapePath.lineTo(px, py)
                        }
                        shapePath.close()
                        drawPath(shapePath, hi, style = Stroke(1.5f))
                        if (k == 7) drawPath(shapePath, accent.copy(alpha = 0.10f))
                    }
                    FloorPattern.CIRCUIT -> {
                        drawRect(plate, Offset(x + 1f, y + 1f), Size(step - 2f, step - 2f))
                        val tc = accent.copy(alpha = 0.14f)
                        when (k % 4) {
                            0 -> { drawLine(tc, Offset(x, y + step * 0.3f), Offset(x + step * 0.6f, y + step * 0.3f), 2f); drawLine(tc, Offset(x + step * 0.6f, y + step * 0.3f), Offset(x + step * 0.6f, y + step), 2f); drawCircle(tc, 3f, Offset(x + step * 0.6f, y + step * 0.3f)) }
                            1 -> { drawLine(tc, Offset(x + step * 0.25f, y), Offset(x + step * 0.25f, y + step * 0.7f), 2f); drawCircle(tc, 3.5f, Offset(x + step * 0.25f, y + step * 0.7f)) }
                            2 -> { drawLine(tc, Offset(x, y + step * 0.75f), Offset(x + step, y + step * 0.75f), 2f) }
                            else -> drawRect(tc, Offset(x + step * 0.35f, y + step * 0.35f), Size(step * 0.3f, step * 0.3f), style = Stroke(1.5f))
                        }
                    }
                    FloorPattern.DIAMOND -> {
                        drawRect(if ((r + c) % 2 == 0) plateA else plateB, Offset(x, y), Size(step, step))
                        shapePath.reset()
                        shapePath.moveTo(x + step / 2f, y + 4f); shapePath.lineTo(x + step - 4f, y + step / 2f)
                        shapePath.lineTo(x + step / 2f, y + step - 4f); shapePath.lineTo(x + 4f, y + step / 2f); shapePath.close()
                        drawPath(shapePath, hi, style = Stroke(1.4f))
                    }
                    FloorPattern.BRICK -> {
                        val off = if (r % 2 == 0) 0f else step / 2f
                        drawRect(plate, Offset(x - off + 2f, y + 2f), Size(step - 4f, step / 2f - 3f))
                        drawRect(if (k % 2 == 0) plateA else plateB, Offset(x - off + 2f, y + step / 2f + 1f), Size(step - 4f, step / 2f - 3f))
                        drawLine(hi, Offset(x - off + 3f, y + 3f), Offset(x - off + step - 4f, y + 3f), 1.2f)
                    }
                    FloorPattern.DOTS -> {
                        drawRect(plate, Offset(x + 1f, y + 1f), Size(step - 2f, step - 2f))
                        val n = 4
                        for (dy in 0 until n) for (dx in 0 until n) {
                            drawCircle(Color(0x55000000), 2.2f, Offset(x + step * (dx + 0.5f) / n, y + step * (dy + 0.5f) / n))
                        }
                    }
                    FloorPattern.GROOVES -> {
                        drawRect(plate, Offset(x + 1f, y + 1f), Size(step - 2f, step - 2f))
                        val vertical = (r + c) % 2 == 0
                        var gpos = 8f
                        while (gpos < step - 6f) {
                            if (vertical) drawLine(Color(0x44000000), Offset(x + gpos, y + 5f), Offset(x + gpos, y + step - 5f), 2f)
                            else drawLine(Color(0x44000000), Offset(x + 5f, y + gpos), Offset(x + step - 5f, y + gpos), 2f)
                            gpos += 9f
                        }
                    }
                }
                when (k) {
                    1, 2 -> if (pattern == FloorPattern.PLATES) { // grate
                        var gy = y + 14f
                        while (gy < y + step - 12f) { drawLine(Color(0xFF060A12), Offset(x + 12f, gy), Offset(x + step - 12f, gy), 3f); gy += 7f }
                    }
                    5 -> { // lit service panel
                        val on = 0.5f + 0.5f * sin(time * 2f + c + r)
                        drawRect(accent.copy(alpha = 0.10f + 0.12f * on), Offset(x + 10f, y + 10f), Size(step - 20f, step - 20f))
                        drawRect(accent.copy(alpha = 0.35f), Offset(x + 10f, y + 10f), Size(step - 20f, step - 20f), style = Stroke(1.2f))
                    }
                    9 -> { // bolts
                        val bc = hi
                        drawCircle(bc, 2f, Offset(x + 9f, y + 9f)); drawCircle(bc, 2f, Offset(x + step - 9f, y + 9f))
                        drawCircle(bc, 2f, Offset(x + 9f, y + step - 9f)); drawCircle(bc, 2f, Offset(x + step - 9f, y + step - 9f))
                    }
                }
            }
        }
        // Glowing trenches with packets running along them (spacing varies per level).
        val everyX = if (boss) 4 else st.trenchEveryX
        val everyY = if (boss) 5 else st.trenchEveryY
        var tx = step * 2
        var lane = 0
        val dir = if (st.flowUp) -1f else 1f
        while (tx < w) {
            drawLine(accent.copy(alpha = 0.10f), Offset(tx, 0f), Offset(tx, h), 7f)
            drawLine(accent.copy(alpha = 0.32f), Offset(tx, 0f), Offset(tx, h), 1.8f)
            for (k in 0 until 2) {
                val travel = (time * (55f + lane * 13f) + k * h * 0.5f + lane * 211f) % (h + 120f)
                val py = if (dir > 0f) travel - 60f else h + 60f - travel
                drawCircle(accent.copy(alpha = 0.18f), 7f, Offset(tx, py))
                drawCircle(accent.copy(alpha = 0.85f), 2.6f, Offset(tx, py))
            }
            tx += step * everyX
            lane++
        }
        var ty = step * 3
        while (ty < h) {
            drawLine(accent.copy(alpha = 0.07f), Offset(0f, ty), Offset(w, ty), 6f)
            drawLine(accent.copy(alpha = 0.22f), Offset(0f, ty), Offset(w, ty), 1.4f)
            ty += step * everyY
        }
        // Light pools in front of tall hardware, in the theme's light colour.
        val pool = if (boss) Palette.Red else Color(env.light)
        for (o in g.arena.obstacles) {
            if (o.kind != ObstacleKind.SERVER_RACK && o.kind != ObstacleKind.SMALL_SERVER && o.kind != ObstacleKind.DATA_PILLAR &&
                o.kind != ObstacleKind.REACTOR && o.kind != ObstacleKind.ANTENNA_TOWER && o.kind != ObstacleKind.HOLO_WALL) continue
            val r = o.rect
            val col = if (o.kind == ObstacleKind.DATA_PILLAR) accent else pool
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
            val col = if (i % 4 == 0) accent2 else accent
            drawRect(col.copy(alpha = a), Offset(px, pyy), Size(5f, 5f))
        }
        // Side walls: a low raised kerb with a lit edge in the theme colour.
        val wall = if (boss) Palette.Red else Color(env.wallTrim).copy(alpha = 0.75f)
        val kerb = if (boss) Color(0xFF0E1830) else Color(env.wallTone)
        drawRect(kerb, Offset(-14f, 0f), Size(14f, h))
        drawRect(kerb, Offset(w, 0f), Size(14f, h))
        drawLine(wall.copy(alpha = 0.18f), Offset(0f, 0f), Offset(0f, h), 9f)
        drawLine(wall.copy(alpha = 0.18f), Offset(w, 0f), Offset(w, h), 9f)
        drawLine(wall.copy(alpha = 0.8f), Offset(0f, 0f), Offset(0f, h), 3f)
        drawLine(wall.copy(alpha = 0.8f), Offset(w, 0f), Offset(w, h), 3f)
        drawLine(wall.copy(alpha = 0.5f), Offset(0f, h), Offset(w, h), 3f)
        if (g.shopGateOpen) drawShopGate(g, time)
        if (g.shopArrowFlash > 0f) drawShopArrow(g, time)
    }

    /** After NO on "skip the shop?": a big gold arrow by the operative blinks toward the shop gate. */
    private fun DrawScope.drawShopArrow(g: GameEngine, time: Float) {
        if (((g.shopArrowFlash * 4f).toInt() % 2) == 1) return
        val tx = g.shopGateX
        val ty = g.shopGateY
        val ang = kotlin.math.atan2(ty - g.py, tx - g.px)
        val cx = g.px + cos(ang) * 70f
        val cy = g.py - 24f + sin(ang) * 70f
        rotate(Math.toDegrees(ang.toDouble()).toFloat(), Offset(cx, cy)) {
            shapePath.reset()
            shapePath.moveTo(cx - 30f, cy - 9f); shapePath.lineTo(cx + 4f, cy - 9f); shapePath.lineTo(cx + 4f, cy - 22f)
            shapePath.lineTo(cx + 32f, cy); shapePath.lineTo(cx + 4f, cy + 22f); shapePath.lineTo(cx + 4f, cy + 9f)
            shapePath.lineTo(cx - 30f, cy + 9f); shapePath.close()
            drawPath(shapePath, Palette.Gold.copy(alpha = 0.3f), style = Stroke(10f))
            drawPath(shapePath, Palette.Gold)
            drawPath(shapePath, Color(0xFF1A1200), style = Stroke(2f))
        }
    }

    /** The side gate to the upgrade shop: a gold doorway in the left wall with chevrons pointing in. */
    private fun DrawScope.drawShopGate(g: GameEngine, time: Float) {
        // Drawn for the left wall; mirrored for a right-wall gate.
        if (g.shopGateRight) {
            withTransform({ scale(-1f, 1f, Offset(g.arena.width / 2f, 0f)) }) { drawShopGateLeft(g, time, mirrored = true) }
        } else drawShopGateLeft(g, time, mirrored = false)
    }

    private fun DrawScope.drawShopGateLeft(g: GameEngine, time: Float, mirrored: Boolean) {
        val cy = g.shopGateY
        val half = GameEngine.SHOP_GATE_HALF
        val p = 0.5f + 0.5f * sin(time * 4f)
        drawRect(Color(0xFF0A0802), Offset(-14f, cy - half), Size(20f, half * 2f))
        drawRect(Palette.Gold.copy(alpha = 0.25f + 0.2f * p), Offset(-14f, cy - half), Size(20f, half * 2f))
        drawRect(Palette.Gold, Offset(-14f, cy - half - 6f), Size(26f, 6f))
        drawRect(Palette.Gold, Offset(-14f, cy + half), Size(26f, 6f))
        drawOval(Palette.Gold.copy(alpha = 0.12f + 0.08f * p), Offset(-60f, cy - half * 1.3f), Size(160f, half * 2.6f))
        for (k in 0 until 3) {
            val xx = 70f - ((time * 50f + k * 24f) % 72f)
            shapePath.reset()
            shapePath.moveTo(xx + 12f, cy - 16f); shapePath.lineTo(xx, cy); shapePath.lineTo(xx + 12f, cy + 16f)
            drawPath(shapePath, Palette.Gold.copy(alpha = 0.75f), style = Stroke(4f))
        }
        tagPaint.textSize = 14f
        tagPaint.color = Palette.Gold.toArgb()
        // Text must not read backwards on the mirrored side.
        if (mirrored) withTransform({ scale(-1f, 1f, Offset(46f, 0f)) }) { drawContext.canvas.nativeCanvas.drawText("SHOP", 46f, cy - half - 14f, tagPaint) }
        else drawContext.canvas.nativeCanvas.drawText("SHOP", 46f, cy - half - 14f, tagPaint)
    }

    private fun mix(a: Color, b: Color, t: Float) =
        Color(a.red * t + b.red * (1 - t), a.green * t + b.green * (1 - t), a.blue * t + b.blue * (1 - t), 1f)

    private fun DrawScope.drawDecor(g: GameEngine, time: Float) {
        val env = styleOf(g).env
        val themeAccent = if (g.plan.kind == LevelKind.BOSS) Palette.Red else Color(env.accent)
        val themeAccent2 = if (g.plan.kind == LevelKind.BOSS) Palette.Magenta else Color(env.accent2)
        for (d in g.arena.template.decor) {
            when (d.kind) {
                DecorKind.FLOOR_TILE -> drawRect(themeAccent.copy(alpha = 0.04f), Offset(d.x + 3f, d.y + 3f), Size(d.w - 6f, d.h - 6f))
                DecorKind.CABLE -> {
                    shapePath.reset()
                    val wob = (d.seed % 50) + 20f
                    shapePath.moveTo(d.x, d.y)
                    shapePath.cubicTo(d.x + d.w * 0.3f, d.y - wob, d.x + d.w * 0.6f, d.y + wob, d.x + d.w, d.y + wob * 0.3f)
                    drawPath(shapePath, Color(0xFF050910), style = Stroke(7f))
                    drawPath(shapePath, Color(0xFF1A2B44), style = Stroke(4f))
                    val f = ((time * 0.4f + (d.seed and 0xFF) / 255f) % 1f)
                    drawCircle(themeAccent.copy(alpha = 0.45f), 2.5f, Offset(d.x + d.w * f, d.y + wob * (0.3f * f)))
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
                        drawRect(themeAccent.copy(alpha = if (on) 0.6f else 0.12f), Offset(d.x, d.y + i * 20f), Size(d.w, 12f))
                    }
                }
                DecorKind.HOLO_PANEL -> {
                    val col = if (d.seed % 2 == 0) themeAccent else themeAccent2
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
        val env = styleOf(g).env
        val wallFace = if (boss) Color(0xFF0C1526) else mix(Color(env.wallTone), Color(0xFF0C1526), 0.6f)
        drawRect(wallFace, Offset(-14f, -WALL_HEIGHT), Size(w + 28f, WALL_HEIGHT))
        drawRect(if (boss) Color(0xFF16233A) else Color(env.wallTone), Offset(-14f, -WALL_HEIGHT - 12f), Size(w + 28f, 12f))
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
        drawLine((if (boss) Palette.Red else Color(env.wallTrim)).copy(alpha = 0.8f), Offset(-14f, 0f), Offset(w + 14f, 0f), 3f)

        // Gate. Locked red after the player declined to skip the shop.
        val open = g.portalOpen && !g.topGateLocked
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
        if (g.phase != Phase.DEAD) for (o in g.operatives) if (!o.gone) push(2, o.index, o.py + g.playerRadius * 0.5f)
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
        for (o in g.operatives) if (o.alive) g.viewAs(o.index) { drawOrbitBack(g, time) }
        for (i in 0 until sortCount) {
            when (sortKind[i]) {
                0 -> drawObstacle(g, obs[sortIndex[i]], sortIndex[i], time)
                1 -> drawEnemy(g, items[sortIndex[i]], time)
                else -> {
                    val idx = sortIndex[i]
                    val local = idx == g.primary
                    g.viewAs(idx) {
                        drawPlayer(g, time, if (local) skin else partnerSkin ?: skin, if (local) body else partnerBody ?: body, partner = !local)
                    }
                }
            }
        }
    }

    private fun push(kind: Int, index: Int, key: Float) {
        if (sortCount >= MAX_ITEMS) return
        sortKind[sortCount] = kind; sortIndex[sortCount] = index; sortKey[sortCount] = key; sortCount++
    }

    /** Faint red ground ring under every threat so they read against busy floors. */
    private fun DrawScope.drawEnemyMarkers(g: GameEngine, time: Float) {
        for (e in g.enemies.items) {
            if (!e.active || e.state == AiState.SPAWNING || e.state == AiState.HIDDEN) continue
            val col = if (e.boss != null) Color(e.boss!!.def.color) else Palette.Red
            drawOval(col.copy(alpha = 0.32f), Offset(e.x - e.radius * 1.15f, e.y - e.radius * 0.32f), Size(e.radius * 2.3f, e.radius * 0.75f), style = Stroke(2f))
        }
    }

    /**
     * X-ray (owner, 2026-10-08): a threat hidden behind a rack or crate is drawn
     * again on top as a translucent outline, so cover never hides it completely.
     */
    private fun DrawScope.drawXray(g: GameEngine, time: Float) {
        val obstacles = g.arena.obstacles
        for (e in g.enemies.items) {
            if (!e.active || e.state == AiState.SPAWNING || e.state == AiState.HIDDEN) continue
            val lift = if (e.boss != null) 18f else 12f
            val sx = e.x
            val sy = e.y - lift
            var hidden = false
            for (o in obstacles) {
                val r = o.rect
                if (e.y >= r.bottom) continue // in front of it
                if (sx < r.left - e.radius * 0.4f || sx > r.right + e.radius * 0.4f) continue
                if (sy + e.radius * 0.5f < r.top - heightOf(o.kind) || sy - e.radius * 0.5f > r.bottom) continue
                hidden = true
                break
            }
            if (!hidden) continue
            val pulse = 0.75f + 0.25f * sin(time * 5f + e.uid)
            val col = if (e.boss != null) Color(e.boss!!.def.color) else Color(0xFFFF4A6A)
            val c = Offset(sx, sy)
            drawShape(e.def.shape, c, e.radius, col.copy(alpha = 0.16f * pulse), 0f)
            drawShape(e.def.shape, c, e.radius, col.copy(alpha = 0.75f * pulse), 0f, stroke = 2f)
            drawCircle(col.copy(alpha = 0.8f * pulse), max(1.5f, e.radius * 0.1f), Offset(sx - e.radius * 0.28f, sy - e.radius * 0.05f))
            drawCircle(col.copy(alpha = 0.8f * pulse), max(1.5f, e.radius * 0.1f), Offset(sx + e.radius * 0.28f, sy - e.radius * 0.05f))
        }
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
        ObstacleKind.BLAST_WALL -> 84f
        ObstacleKind.COOLANT_PIPES -> 46f
        ObstacleKind.HOLO_WALL -> 70f
        ObstacleKind.REACTOR -> 96f
        ObstacleKind.ENERGY_BARRIER -> 22f
        ObstacleKind.ANTENNA_TOWER -> 128f
        ObstacleKind.SHOP_COUNTER -> 40f
        ObstacleKind.BARRIER_CUBE -> BARRIER_HEIGHT
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
            ObstacleKind.BLAST_WALL -> Look(Color(0xFF2A3140), Color(0xFF1A202C), Color(0xFFB8C4D6))
            ObstacleKind.COOLANT_PIPES -> Look(Color(0xFF16323C), Color(0xFF0E2128), Color(0xFF2EE6E6))
            ObstacleKind.HOLO_WALL -> Look(Color(0x3300E5FF), Color(0x2200E5FF), Palette.Cyan)
            ObstacleKind.REACTOR -> Look(Color(0xFF242A36), Color(0xFF161B24), Palette.Green)
            ObstacleKind.ENERGY_BARRIER -> Look(Color(0xFF331018), Color(0xFF220A10), Palette.Red)
            ObstacleKind.ANTENNA_TOWER -> Look(Color(0xFF26303F), Color(0xFF181F2A), Palette.Red)
            ObstacleKind.SHOP_COUNTER -> Look(Color(0xFF2B2416), Color(0xFF1C170D), Palette.Gold)
            ObstacleKind.BARRIER_CUBE -> Look(Color(0xFF26324F), Color(0xFF111A2E), Palette.Red)
        }
    }

    /** Walls pick up the theme: bodies lean toward its tone; racks, crates and bulkheads take its trim. */
    private fun themed(look: Look, kind: ObstacleKind, g: GameEngine): Look {
        if (g.plan.kind == LevelKind.BOSS) return look
        val env = styleOf(g).env
        val tone = Color(env.wallTone)
        val holo = kind == ObstacleKind.HOLO_WALL
        val trim = when (kind) {
            ObstacleKind.SERVER_RACK, ObstacleKind.SMALL_SERVER, ObstacleKind.CRATES, ObstacleKind.BLAST_WALL, ObstacleKind.HOLO_WALL -> Color(env.wallTrim)
            else -> look.trim
        }
        return if (holo) Look(trim.copy(alpha = 0.2f), trim.copy(alpha = 0.13f), trim)
        else Look(mix(tone, look.top, 0.35f), mix(tone, look.front, 0.35f), trim)
    }

    private fun DrawScope.drawObstacle(g: GameEngine, o: ObstacleSpec, index: Int, time: Float) {
        val vault = g.vaultPresent && index == g.arena.obstacles.lastIndex
        // Opening cache: it shakes harder and harder until it bursts.
        if (vault && g.vaultOpening > 0f) {
            val k = 1f - g.vaultOpening / GameEngine.VAULT_OPEN_SECONDS
            val sx = sin(time * 70f) * (2f + 7f * k)
            val sy = cos(time * 53f) * (1f + 4f * k)
            translate(sx, sy) { drawObstacleBody(g, o, index, time, vault, k) }
            return
        }
        drawObstacleBody(g, o, index, time, vault, 0f)
    }

    private fun DrawScope.drawObstacleBody(g: GameEngine, o: ObstacleSpec, index: Int, time: Float, vault: Boolean, opening: Float) {
        val r = o.rect
        // The shopkeeper stands behind the counter (drawn first so the counter hides the legs).
        if (o.kind == ObstacleKind.SHOP_COUNTER) with(OperativeFigures) {
            val look = when (g.keeperLook) {
                com.cyberoperative.game.engine.KeeperLook.GOLD -> com.cyberoperative.game.ui.common.HeroPalette.KEEPER_GOLD
                com.cyberoperative.game.engine.KeeperLook.TITANIUM -> com.cyberoperative.game.ui.common.HeroPalette.KEEPER_TITANIUM
                com.cyberoperative.game.engine.KeeperLook.BLACK -> com.cyberoperative.game.ui.common.HeroPalette.KEEPER_BLACK
                com.cyberoperative.game.engine.KeeperLook.SPECTRUM -> com.cyberoperative.game.ui.common.HeroPalette.KEEPER_SPECTRUM
            }
            drawShopkeeper(r.centerX, r.top + 8f, 1.6f, time, look)
        }
        val h = heightOf(o.kind)
        val look = if (vault) lookOf(o.kind, true) else themed(lookOf(o.kind, false), o.kind, g)
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
                    // Ready to crack: a pulsing gold halo; opening: light leaks through cracks.
                    if (g.vaultReady) {
                        val p = 0.5f + 0.5f * sin(time * 5f)
                        drawRoundRect(Palette.Gold.copy(alpha = 0.25f + 0.25f * p), Offset(r.left - 8f, topY - 8f), Size(r.width + 16f, r.height + 16f), CornerRadius(8f), style = Stroke(4f))
                    }
                    if (opening > 0f) {
                        val c = Offset(r.centerX, topY + r.height / 2f)
                        drawCircle(Palette.Gold.copy(alpha = 0.25f + 0.5f * opening), r.width * (0.6f + 0.6f * opening), c)
                        for (k in 0 until 6) {
                            val a = k * 1.05f + 0.3f
                            drawLine(Color.White.copy(alpha = 0.4f + 0.6f * opening), c, Offset(c.x + cos(a) * r.width * 0.55f, c.y + sin(a) * r.height * 0.55f), 2.5f)
                        }
                    }
                    tagPaint.textSize = 18f
                    tagPaint.color = Palette.Gold.toArgb()
                    drawContext.canvas.nativeCanvas.drawText("[\$\$\$]", r.centerX, topY + r.height / 2f + 6f, tagPaint)
                }
            }
            ObstacleKind.BLAST_WALL -> {
                // Riveted armour plates with hazard chevrons along the base.
                var px = r.left + 6f
                while (px < r.right - 6f) {
                    drawLine(Color(0x66000000), Offset(px, frontTop + 4f), Offset(px, r.bottom - 4f), 2f)
                    drawCircle(look.trim.copy(alpha = 0.5f), 1.8f, Offset(px + 6f, frontTop + 8f))
                    drawCircle(look.trim.copy(alpha = 0.5f), 1.8f, Offset(px + 6f, r.bottom - 10f))
                    px += 32f
                }
                var cx = r.left + 4f
                while (cx < r.right - 12f) {
                    shapePath.reset()
                    shapePath.moveTo(cx, r.bottom - 2f); shapePath.lineTo(cx + 6f, r.bottom - 10f)
                    shapePath.lineTo(cx + 12f, r.bottom - 10f); shapePath.lineTo(cx + 6f, r.bottom - 2f); shapePath.close()
                    drawPath(shapePath, Palette.Gold.copy(alpha = 0.45f))
                    cx += 16f
                }
            }
            ObstacleKind.COOLANT_PIPES -> {
                // Three pipes; liquid pulses flow along them.
                val horizontal = r.width >= r.height
                for (p in 0 until 3) {
                    if (horizontal) {
                        val py = frontTop + 10f + p * (h - 20f) / 2f
                        drawLine(Color(0xFF0A1418), Offset(r.left + 3f, py), Offset(r.right - 3f, py), 9f)
                        drawLine(look.trim.copy(alpha = 0.35f), Offset(r.left + 3f, py - 2f), Offset(r.right - 3f, py - 2f), 2f)
                        val f = (time * 0.5f + p * 0.33f + index * 0.1f) % 1f
                        drawCircle(look.trim.copy(alpha = 0.8f), 3.5f, Offset(r.left + 6f + f * (r.width - 12f), py))
                    } else {
                        val px = r.left + 8f + p * (r.width - 16f) / 2f
                        drawLine(Color(0xFF0A1418), Offset(px, topY + 4f), Offset(px, r.bottom - 3f), 8f)
                        val f = (time * 0.5f + p * 0.33f + index * 0.1f) % 1f
                        drawCircle(look.trim.copy(alpha = 0.8f), 3.2f, Offset(px, topY + 6f + f * (r.bottom - topY - 12f)))
                    }
                }
            }
            ObstacleKind.HOLO_WALL -> {
                // Scanlines that drift, and a flicker now and then.
                val flick = if (((time * 7f).toInt() + index) % 17 == 0) 0.3f else 1f
                var sy = frontTop + ((time * 30f) % 8f)
                while (sy < r.bottom) {
                    drawLine(look.trim.copy(alpha = 0.25f * flick), Offset(r.left + 2f, sy), Offset(r.right - 2f, sy), 1.2f)
                    sy += 8f
                }
                drawRect(look.trim.copy(alpha = 0.6f * flick), Offset(r.left, frontTop), Size(r.width, h), style = Stroke(1.5f))
            }
            ObstacleKind.REACTOR -> {
                // Cylinder: banded body with a pulsing core on top.
                val p = 0.5f + 0.5f * sin(time * 2.6f + index)
                for (b in 1..3) drawLine(Color(0x55000000), Offset(r.left + 3f, frontTop + h * b / 4f), Offset(r.right - 3f, frontTop + h * b / 4f), 3f)
                val c = Offset(r.centerX, topY + r.height / 2f)
                val rad = min(r.width, r.height) * 0.34f
                drawCircle(look.trim.copy(alpha = 0.15f + 0.2f * p), rad * 1.4f, c)
                drawCircle(look.trim.copy(alpha = 0.6f + 0.4f * p), rad * 0.7f, c)
                drawCircle(Color.White.copy(alpha = 0.7f * p), rad * 0.3f, c)
                drawLine(look.trim.copy(alpha = 0.6f), Offset(r.centerX, frontTop + 6f), Offset(r.centerX, r.bottom - 6f), 3f)
            }
            ObstacleKind.ENERGY_BARRIER -> {
                val p = 0.6f + 0.4f * sin(time * 8f + index)
                drawRect(look.trim.copy(alpha = 0.25f * p), Offset(r.left, topY - 10f), Size(r.width, r.height + 10f))
                drawLine(look.trim.copy(alpha = 0.9f * p), Offset(r.left + 2f, topY + r.height / 2f), Offset(r.right - 2f, topY + r.height / 2f), 3f)
                var ex = r.left + 10f
                while (ex < r.right - 4f) {
                    val j = sin(time * 20f + ex) * 4f
                    drawLine(Color.White.copy(alpha = 0.5f * p), Offset(ex, topY + r.height / 2f + j), Offset(ex + 10f, topY + r.height / 2f - j), 1.5f)
                    ex += 20f
                }
            }
            ObstacleKind.ANTENNA_TOWER -> {
                // Lattice mast with a blinking beacon at the top.
                var ly = frontTop + 6f
                var flip = false
                while (ly < r.bottom - 8f) {
                    drawLine(look.trim.copy(alpha = 0.35f), Offset(if (flip) r.left + 4f else r.right - 4f, ly), Offset(if (flip) r.right - 4f else r.left + 4f, ly + 12f), 1.5f)
                    ly += 12f; flip = !flip
                }
                val on = ((time * 2f).toInt() + index) % 2 == 0
                drawCircle(Palette.Red.copy(alpha = if (on) 0.35f else 0.08f), 12f, Offset(r.centerX, topY - 6f))
                drawCircle(Palette.Red.copy(alpha = if (on) 1f else 0.25f), 4f, Offset(r.centerX, topY - 6f))
            }
            ObstacleKind.BARRIER_CUBE -> {
                // Vault Sentinel's cubes: a lit seam across the face and a lock glyph on top.
                val p = 0.5f + 0.5f * sin(time * 4f + index)
                drawLine(Palette.Red.copy(alpha = 0.5f + 0.4f * p), Offset(r.left + 5f, frontTop + h * 0.45f), Offset(r.right - 5f, frontTop + h * 0.45f), 2.5f)
                drawRect(Palette.Red.copy(alpha = 0.85f), Offset(r.centerX - 7f, frontTop + h * 0.65f), Size(14f, 4f))
                val ins = r.width * 0.26f
                drawRect(Palette.Red.copy(alpha = 0.55f), Offset(r.left + ins, topY + ins * 0.8f), Size(r.width - ins * 2f, r.height - ins * 1.6f), style = Stroke(1.6f))
                drawCircle(Palette.Red.copy(alpha = 0.6f + 0.4f * p), 3.5f, Offset(r.centerX, topY + r.height / 2f))
            }
            ObstacleKind.SHOP_COUNTER -> {
                // Glowing gold edge and the mods for sale laid out on the counter.
                drawLine(Palette.Gold.copy(alpha = 0.5f), Offset(r.left + 4f, topY + 2f), Offset(r.right - 4f, topY + 2f), 2f)
                val items = g.shopItems
                val n = items.size.coerceAtLeast(1)
                for ((i, it) in items.withIndex()) {
                    val cx = r.left + r.width * (i + 0.5f) / n
                    val cy = topY + r.height / 2f
                    val col = Color(it.def.rarity.color)
                    val bob = sin(time * 2.4f + i) * 2f
                    if (!it.sold) {
                        drawCircle(col.copy(alpha = 0.22f), 22f, Offset(cx, cy - 10f + bob))
                        drawRoundRect(Color(0xFF0A0D14), Offset(cx - 16f, cy - 26f + bob), Size(32f, 26f), CornerRadius(4f))
                        drawRoundRect(col, Offset(cx - 16f, cy - 26f + bob), Size(32f, 26f), CornerRadius(4f), style = Stroke(2f))
                        tagPaint.textSize = 10f
                        tagPaint.color = col.toArgb()
                        drawContext.canvas.nativeCanvas.drawText(it.def.glyph.take(5), cx, cy - 9f + bob, tagPaint)
                    } else {
                        tagPaint.textSize = 10f
                        tagPaint.color = Palette.TextMuted.toArgb()
                        drawContext.canvas.nativeCanvas.drawText("SOLD", cx, cy - 9f, tagPaint)
                    }
                    // Price tag on the counter front.
                    tagPaint.textSize = 11f
                    tagPaint.color = (if (it.sold) Palette.TextMuted else Palette.Euro).toArgb()
                    drawContext.canvas.nativeCanvas.drawText(if (it.sold) "---" else "€${it.price}", cx, frontTop + h * 0.7f, tagPaint)
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
            val hb = e.boss
            val hiddenBody = hb?.let { BossBodies.forId(it.def.id) }
            if (hb != null && hiddenBody != null) {
                val pose = BossPose(e.x, e.y - 18f, e.radius, base, time, hb.phaseIndex, false, false, 0f, (e.hp / e.maxHp).coerceIn(0f, 1f), glitch, veiled = 1f)
                if (with(hiddenBody) { drawHidden(pose) }) return
            }
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
        // Stealth bosses (Nullshade) bring their own light; no hard aura disc behind them.
        if (boss != null && !boss.def.stealth) drawCircle(base.copy(alpha = 0.14f + 0.08f * sin(time * 3f)), e.radius * 1.7f, Offset(cx, cy))
        val customBody = boss?.let { BossBodies.forId(it.def.id) }
        if (boss != null && customBody != null) {
            val pose = BossPose(
                cx, cy, e.radius, base, time, boss.phaseIndex, e.state == AiState.WINDUP, e.hitFlash > 0f,
                kotlin.math.atan2(g.py - e.y, g.px - e.x), (e.hp / e.maxHp).coerceIn(0f, 1f), glitch,
                veiled = if (boss.def.stealth) g.bossVeil else 0f
            )
            if (glitch) {
                val j = ((time * 14f).toInt() + e.uid) % 5 - 2
                drawCircle(Color(0xFF00FFFF).copy(alpha = 0.25f), e.radius, Offset(cx - 4f + j, cy))
                drawCircle(Color(0xFFFF00FF).copy(alpha = 0.25f), e.radius, Offset(cx + 4f - j, cy))
            }
            with(customBody) { draw(pose) }
            if (e.uid == g.targetUid) {
                drawOval(Palette.Cyan.copy(alpha = 0.85f), Offset(e.x - e.radius - 6f, e.y + e.radius * 0.1f), Size(e.radius * 2f + 12f, e.radius + 6f), style = Stroke(2.5f))
            }
            return
        }
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

    private fun DrawScope.drawPlayer(g: GameEngine, time: Float, skin: OperativeSkin, body: BodyStyle, partner: Boolean = false) {
        val r = g.playerRadius
        val foot = g.py + r * 0.55f
        val me = g.operatives[if (partner) (1 - g.primary).coerceIn(0, g.operatives.size - 1) else g.primary]
        if (g.coop) {
            // Co-op: a coloured ring tells the two operatives apart (you: cyan, partner: magenta).
            val ringCol = if (partner) Palette.Magenta else Palette.Cyan
            drawOval(ringCol.copy(alpha = 0.55f), Offset(g.px - r * 1.25f, g.py + r * 0.15f), Size(r * 2.5f, r * 0.9f), style = Stroke(2.5f))
            if (me.downed) {
                // Downed: faded figure and the revive ring filling up.
                val f = (me.reviveProgress / GameEngine.REVIVE_SECONDS).coerceIn(0f, 1f)
                drawCircle(Palette.Red.copy(alpha = 0.25f + 0.2f * sin(time * 6f)), GameEngine.REVIVE_RADIUS, Offset(g.px, g.py), style = Stroke(2f))
                drawArc(Palette.Green, -90f, 360f * f, false, Offset(g.px - r * 1.6f, g.py - r * 3.4f), Size(r * 3.2f, r * 3.2f), style = Stroke(5f))
                drawFigure(body, skin, g.px, foot, FIGURE_SCALE, g.facing, false, time, alpha = 0.35f, bigGun = false)
                return
            }
        }
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

    private fun DrawScope.drawOrbitFront(g: GameEngine, time: Float) {
        for (o in g.operatives) if (o.alive) g.viewAs(o.index) { drawOrbit(g, time, back = false) }
    }

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
        for (o in g.operatives) if (o.alive) g.viewAs(o.index) { drawBeam(g, time, lift) }
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
            if (p.kind == ProjKind.NEEDLE) {
                // Static needle: a thin, faint shard with a flickering static trail.
                val sp = max(1f, kotlin.math.hypot(p.vx, p.vy))
                val dx = p.vx / sp
                val dy = p.vy / sp
                val tip = Offset(c.x + dx * 9f, c.y + dy * 9f)
                val tail = Offset(c.x - dx * 26f, c.y - dy * 26f)
                for (k in 0 until 3) {
                    val j = sin(time * 60f + k * 2f + p.x) * 3f
                    val a0 = Offset(c.x - dx * (8f + k * 7f) - dy * j, c.y - dy * (8f + k * 7f) + dx * j)
                    drawLine(Color(0xFFE08CFF).copy(alpha = 0.35f - k * 0.08f), a0, Offset(a0.x - dx * 6f, a0.y - dy * 6f), 1.4f)
                }
                drawLine(Color(0xFFD040FF).copy(alpha = 0.3f), tail, tip, 5f)
                drawLine(Color(0xFFF2D6FF).copy(alpha = 0.85f), Offset(c.x - dx * 8f, c.y - dy * 8f), tip, 2f)
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
                HazardKind.INFECTED -> drawInfected(h, col, time)
                HazardKind.BLAST -> {
                    val f = (h.timer / h.duration).coerceIn(0f, 1f)
                    drawCircle(col.copy(alpha = 0.12f), h.radius, Offset(h.x, h.y))
                    drawCircle(col.copy(alpha = 0.35f), h.radius * f, Offset(h.x, h.y))
                    drawCircle(col.copy(alpha = 0.85f), h.radius, Offset(h.x, h.y), style = Stroke(2.5f))
                }
                HazardKind.SPIKE -> if (h.timer < h.duration) {
                    // Warning: a cracked diamond filling in where the crystals will burst.
                    val f = (h.timer / h.duration).coerceIn(0f, 1f)
                    val pts = floatArrayOf(0f, -h.radius * 0.7f, h.radius, 0f, 0f, h.radius * 0.7f, -h.radius, 0f)
                    drawPath(polyPath(h.x, h.y, pts), col.copy(alpha = 0.1f + 0.3f * f))
                    drawPath(polyPath(h.x, h.y, pts), col.copy(alpha = 0.5f + 0.4f * f), style = Stroke(2f))
                    drawLine(col.copy(alpha = 0.7f * f), Offset(h.x - h.radius * 0.5f, h.y), Offset(h.x + h.radius * 0.5f, h.y), 1.5f)
                } else {
                    // Scorched crack star under the burst crystals.
                    for (i in 0 until 5) {
                        val a = i * 1.2566f + h.x
                        drawLine(col.copy(alpha = 0.6f), Offset(h.x, h.y), Offset(h.x + cos(a) * h.radius, h.y + sin(a) * h.radius * 0.6f), 1.8f)
                    }
                }
                HazardKind.MORTAR -> {
                    // Landing marker: crosshair ring that fills as the shell comes down.
                    val f = (h.timer / h.duration).coerceIn(0f, 1f)
                    drawCircle(col.copy(alpha = 0.1f), h.radius, Offset(h.x, h.y))
                    drawCircle(col.copy(alpha = 0.32f), h.radius * f, Offset(h.x, h.y))
                    drawCircle(col.copy(alpha = 0.9f), h.radius, Offset(h.x, h.y), style = Stroke(2.5f))
                    val t = h.radius * 0.35f
                    drawLine(col, Offset(h.x - t, h.y), Offset(h.x + t, h.y), 2f)
                    drawLine(col, Offset(h.x, h.y - t), Offset(h.x, h.y + t), 2f)
                    // Shell shadow sliding along the ground toward the marker.
                    val sx = h.x2 + (h.x - h.x2) * f
                    val sy = h.y2 + (h.y - h.y2) * f
                    drawOval(Color.Black.copy(alpha = 0.35f), Offset(sx - 9f, sy - 4f), Size(18f, 8f))
                }
                HazardKind.SWEEP -> if (h.timer < h.windup) {
                    // Telegraph: the arc it will sweep, with the start line pulsing.
                    val f = h.timer / h.windup
                    val start = kotlin.math.atan2(h.y2 - h.y, h.x2 - h.x)
                    val r = 150f
                    drawArc(
                        col.copy(alpha = 0.12f + 0.12f * f), Math.toDegrees(minOf(start, start + h.maxRadius).toDouble()).toFloat(),
                        Math.toDegrees(kotlin.math.abs(h.maxRadius).toDouble()).toFloat(), true,
                        Offset(h.x - r, h.y - r), Size(r * 2f, r * 2f)
                    )
                } else {}
                HazardKind.LINE -> {
                    val f = (h.timer / h.duration).coerceIn(0f, 1f)
                    drawLine(col.copy(alpha = 0.25f + 0.5f * f), Offset(h.x, h.y), Offset(h.x2, h.y2), 2f + 4f * f)
                }
                else -> {}
            }
        }
    }

    /**
     * EMP blackout (Boss Expansion S11): the room goes black except a soft light
     * bubble around each operative; it lifts a little while a stealth boss has
     * its eyes open. The boss's eyes are drawn on top so you can always track it.
     */
    private fun DrawScope.drawDarkness(g: GameEngine, time: Float) {
        val d = g.darknessNow
        val eyesOpen = 1f - g.bossVeil
        // Owner, 2026-10-10: "The level should be extremely dark".
        val alpha = (d * (0.985f - 0.05f * eyesOpen)).coerceIn(0f, 1f)
        val w = g.arena.width
        val h = g.arena.height
        val bounds = androidx.compose.ui.geometry.Rect(-400f, -600f, w + 400f, h + 600f)
        drawContext.canvas.saveLayer(bounds, androidx.compose.ui.graphics.Paint())
        drawRect(Color(0xFF020104).copy(alpha = alpha), bounds.topLeft, bounds.size)
        for (o in g.operatives) {
            if (o.gone || !o.alive) continue
            val c = Offset(o.px, o.py - 10f)
            val rad = LIGHT_BUBBLE * (0.97f + 0.03f * sin(time * 2.3f + o.index))
            drawCircle(
                Brush.radialGradient(0f to Color.Black, 0.4f to Color.Black.copy(alpha = 0.92f), 0.75f to Color.Black.copy(alpha = 0.35f), 1f to Color.Transparent, center = c, radius = rad),
                rad, c, blendMode = androidx.compose.ui.graphics.BlendMode.DstOut
            )
        }
        drawContext.canvas.restore()
        for (o in g.operatives) {
            if (o.gone || !o.alive) continue
            drawCircle(Palette.Cyan.copy(alpha = 0.07f * d), LIGHT_BUBBLE * 0.9f, Offset(o.px, o.py - 10f), style = Stroke(2f))
        }
        // The boss's eyes cut through the dark.
        val b = g.boss
        val bs = b?.boss
        val body = bs?.let { BossBodies.forId(it.def.id) }
        if (b != null && bs != null && body != null && b.active && b.state != AiState.SPAWNING && b.state != AiState.HIDDEN) {
            val lift = 18f + sin(time * 4f + b.uid) * 2.5f
            val pose = BossPose(
                b.x, b.y - lift, b.radius, Color(bs.def.color), time, bs.phaseIndex, b.state == AiState.WINDUP, false,
                kotlin.math.atan2(g.py - b.y, g.px - b.x), (b.hp / b.maxHp).coerceIn(0f, 1f), bs.glitched,
                veiled = if (bs.def.stealth) g.bossVeil else 0f
            )
            with(body) { drawOverDark(pose) }
        }
    }

    /**
     * Barrier cubes that are not solid yet or are sinking: a pulsing floor
     * outline (where it will rise — get clear) and the cube growing out of it.
     */
    private fun DrawScope.drawBarrierGhosts(g: GameEngine, time: Float) {
        for (b in g.barriers) {
            if (b.solid) continue
            val rising = b.timer < b.rise
            val k = if (rising) (b.timer / b.rise).coerceIn(0f, 1f)
            else 1f - ((b.timer - b.rise - b.life) / com.cyberoperative.game.engine.Barrier.SINK_SECONDS).coerceIn(0f, 1f)
            val w = b.half * 2f
            val blink = if (rising && ((time * 10f).toInt() % 2 == 0)) 1f else 0.6f
            drawRect(Palette.Red.copy(alpha = 0.12f + 0.18f * k), Offset(b.left, b.top), Size(w, w))
            drawRect(Palette.Red.copy(alpha = 0.9f * blink), Offset(b.left, b.top), Size(w, w), style = Stroke(2.5f))
            if (rising) {
                // Corner ticks closing in as it rises.
                val c = 8f + 10f * (1f - k)
                drawLine(Palette.Red, Offset(b.left - c, b.top - c), Offset(b.left, b.top), 2f)
                drawLine(Palette.Red, Offset(b.right + c, b.top - c), Offset(b.right, b.top), 2f)
                drawLine(Palette.Red, Offset(b.left - c, b.bottom + c), Offset(b.left, b.bottom), 2f)
                drawLine(Palette.Red, Offset(b.right + c, b.bottom + c), Offset(b.right, b.bottom), 2f)
            }
            // The cube itself, coming up (or going down) through the floor.
            val hh = BARRIER_HEIGHT * k * k
            if (hh > 2f) {
                drawRect(Color(0xFF111A2E).copy(alpha = 0.85f), Offset(b.left, b.bottom - hh), Size(w, hh))
                drawRect(Color(0xFF26324F).copy(alpha = 0.85f), Offset(b.left, b.top - hh), Size(w, w))
                drawRect(Palette.Red.copy(alpha = 0.8f), Offset(b.left, b.top - hh), Size(w, w), style = Stroke(2f))
            }
        }
    }

    /**
     * Rootkit infection: while it spreads, veins crawl out from the centre; once
     * live, a dark corrupted pool with pulsing veins, flickering code bits and
     * crystal shards around the rim.
     */
    private fun DrawScope.drawInfected(h: com.cyberoperative.game.engine.Hazard, col: Color, time: Float) {
        val c = Offset(h.x, h.y)
        val spread = if (h.timer < h.windup) (h.timer / max(0.01f, h.windup)) else 1f
        val live = h.timer >= h.windup
        val fade = ((h.duration - h.timer) / 0.5f).coerceIn(0f, 1f)
        val p = 0.5f + 0.5f * sin(time * 4f + h.x * 0.01f)
        if (!live) drawCircle(col.copy(alpha = 0.7f), h.radius, c, style = Stroke(2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f), time * 30f)))
        drawCircle(Color(0xFF14040F).copy(alpha = (if (live) 0.75f else 0.4f) * fade), h.radius * spread, c)
        drawCircle(col.copy(alpha = (0.14f + 0.12f * p) * fade * spread), h.radius * spread, c)
        // Veins.
        for (i in 0 until 9) {
            val a = i * 0.698f + h.y * 0.01f
            val l = h.radius * spread * (0.75f + 0.25f * sin(time * 2f + i))
            val mid = Offset(h.x + cos(a + 0.25f) * l * 0.5f, h.y + sin(a + 0.25f) * l * 0.5f)
            drawLine(col.copy(alpha = 0.75f * fade), c, mid, 2f)
            drawLine(col.copy(alpha = 0.55f * fade), mid, Offset(h.x + cos(a) * l, h.y + sin(a) * l), 1.5f)
        }
        if (!live) return
        // Code bits flickering across the pool.
        for (i in 0 until 14) {
            val on = ((time * 6f).toInt() + i * 7) % 5 != 0
            if (!on) continue
            val a = i * 2.39f
            val d = h.radius * (0.2f + 0.7f * ((i * 0.37f) % 1f))
            drawRect(lighter(col, 0.4f).copy(alpha = 0.8f * fade), Offset(h.x + cos(a) * d - 2f, h.y + sin(a) * d - 2f), Size(if (i % 2 == 0) 4f else 7f, 3f))
        }
        drawCircle(col.copy(alpha = (0.6f + 0.3f * p) * fade), h.radius, c, style = Stroke(2.5f))
        // Crystal shards breaking through the rim.
        for (i in 0 until 7) {
            val a = i * 0.897f + 0.4f
            val bx = h.x + cos(a) * h.radius * 0.92f
            val by = h.y + sin(a) * h.radius * 0.92f
            crystal(bx, by, -1.571f + 0.3f * cos(i * 1.3f), h.radius * (0.22f + 0.08f * (i % 3)) * fade, 8f, darker(col, 0.55f), darker(col, 0.2f), col)
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
                HazardKind.SPIKE -> if (h.timer >= h.duration) {
                    // Crystals shoot up, hold, then sink back into the floor.
                    val a = h.timer - h.duration
                    val k = when {
                        a < 0.1f -> a / 0.1f
                        a > h.maxRadius - 0.2f -> ((h.maxRadius - a) / 0.2f).coerceIn(0f, 1f)
                        else -> 1f
                    }
                    if (k > 0.02f) {
                        val lit = darker(col, 0.62f)
                        val dark = darker(col, 0.22f)
                        val hgt = h.radius * 2.1f * k
                        crystal(h.x - h.radius * 0.35f, h.y + 2f, -1.571f - 0.35f, hgt * 0.7f, h.radius * 0.5f, lit, dark, col)
                        crystal(h.x + h.radius * 0.35f, h.y + 2f, -1.571f + 0.4f, hgt * 0.65f, h.radius * 0.5f, lit, dark, col)
                        crystal(h.x, h.y + 4f, -1.571f, hgt, h.radius * 0.6f, lit, dark, col)
                    }
                } else {}
                HazardKind.MORTAR -> {
                    // The shell, high in its arc.
                    val f = (h.timer / h.duration).coerceIn(0f, 1f)
                    val sx = h.x2 + (h.x - h.x2) * f
                    val sy = h.y2 + (h.y - h.y2) * f - sin(f * Math.PI.toFloat()) * 220f
                    drawCircle(col.copy(alpha = 0.3f), 13f, Offset(sx, sy))
                    drawCircle(col, 7.5f, Offset(sx, sy))
                    drawCircle(Color.White.copy(alpha = 0.9f), 3f, Offset(sx, sy))
                }
                HazardKind.SWEEP -> {
                    if (h.timer < h.windup) {
                        val f = h.timer / h.windup
                        drawLine(col.copy(alpha = 0.2f + 0.35f * f), Offset(h.x, h.y), Offset(h.x2, h.y2), h.radius * f)
                        drawLine(col.copy(alpha = 0.75f), Offset(h.x, h.y), Offset(h.x2, h.y2), 2f)
                    } else {
                        drawLine(col.copy(alpha = 0.5f), Offset(h.x, h.y), Offset(h.x2, h.y2), h.radius * 1.4f)
                        drawLine(Color.White.copy(alpha = 0.92f), Offset(h.x, h.y), Offset(h.x2, h.y2), h.radius * 0.45f)
                        // Burn mark where cover stops it.
                        drawCircle(Color.White.copy(alpha = 0.85f), h.radius * 0.6f, Offset(h.x2, h.y2))
                        drawCircle(col.copy(alpha = 0.5f), h.radius * 1.2f, Offset(h.x2, h.y2))
                    }
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
        const val BARRIER_HEIGHT = 58f
        /** Radius of the light around each operative during a blackout. */
        const val LIGHT_BUBBLE = 175f
        private const val MAX_ITEMS = 160
    }
}
