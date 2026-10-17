package com.cyberoperative.game.ui.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.Loot
import com.cyberoperative.game.engine.LootKind
import com.cyberoperative.game.ui.common.GameTextPaint
import com.cyberoperative.game.ui.common.argb
import com.cyberoperative.game.platform.drawGameText
import kotlin.math.ln
import kotlin.math.sin

/**
 * PC floor pickups (owner, 2026-10-11): the HP charge is a green potion flask, the Packet
 * Bomb a small bomb with a lit fuse and a pulsing cyan ring (so it reads as a pickup), and
 * kill money a gold € cash bag. Everything bobs gently and pops in when dropped.
 */
object LootSprites {
    private val path = Path()
    private const val SCALE = 1.8f
    private val euroPaint = GameTextPaint(10f, argb(255, 90, 60, 0), bold = true)

    fun DrawScope.drawLoot(g: GameEngine, time: Float) {
        // Beams first so every chip body sits on top of every beam.
        // Co-op: a partner's drops are theirs alone, so they show only as a faint colour glow.
        for (l in g.loot) if (isMine(g, l) && l.kind == LootKind.UPGRADE) beam(l, time)
        // Gear stands in a beam of its rarity colour (every rarity; brighter and taller as it climbs).
        for (l in g.loot) if (isMine(g, l) && l.kind == LootKind.GEAR) l.gear?.let { gear ->
            with(com.cyberoperative.game.ui.common.GearIcons) { drawLootBeam(gear.rarity, l.x, l.y + 8f, time + l.x * 0.01f, 1.3f) }
        }
        val abyssal = g.operatives.getOrNull(g.primary)?.build?.stats?.potionFullHeal == true
        for (l in g.loot) if (isMine(g, l)) drawOne(l, time, abyssal) else ghost(l, time)
    }

    private fun isMine(g: GameEngine, l: Loot) = l.owner < 0 || l.owner == g.primary

    private fun colourOf(l: Loot) = when (l.kind) {
        LootKind.HP_CHARGE -> Color(0xFF00FF9C)
        LootKind.PACKET_BOMB -> Color(0xFF7DF9FF)
        LootKind.CASH -> Color(0xFFFFC23D)
        LootKind.UPGRADE -> Color(rarityOf(l).color)
        LootKind.GEAR -> Color(l.gear?.rarity?.color ?: 0xFFB8C4D6)
    }

    /** Someone else's drop: just a soft pulse of its colour, no icon, beam or prompt. */
    private fun DrawScope.ghost(l: Loot, time: Float) {
        val c = colourOf(l)
        val pop = (l.age * 5f).coerceAtMost(1f)
        val a = 0.22f + 0.08f * sin(time * 3f + l.x * 0.05f)
        val r = 22f * pop
        if (r <= 0f) return
        drawCircle(com.cyberoperative.game.ui.common.SafeBrush.radialGradient(listOf(c.copy(alpha = a), Color.Transparent), Offset(l.x, l.y), r), r, Offset(l.x, l.y))
        drawCircle(c.copy(alpha = a * 1.3f), 4f * pop, Offset(l.x, l.y))
    }

    private fun rarityOf(l: Loot) =
        l.upgradeId?.let { com.cyberoperative.game.data.Upgrades.byIdOrNull(it) }?.rarity ?: com.cyberoperative.game.data.Rarity.COMMON

    /** The light column rising off an upgrade chip, in its rarity's colour (taller for rarer chips). */
    private fun DrawScope.beam(l: Loot, time: Float) {
        val r = rarityOf(l)
        val c = Color(r.color)
        val step = r.ordinal.coerceAtMost(7)
        val h = 150f + 40f * step
        val pulse = 0.75f + 0.25f * sin(time * 3f + l.x * 0.03f)
        val w = 14f + 2f * step
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, c.copy(alpha = 0.55f * pulse)), l.y - h, l.y),
            Offset(l.x - w / 2f, l.y - h), Size(w, h)
        )
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.5f * pulse)), l.y - h * 0.8f, l.y),
            Offset(l.x - w * 0.15f, l.y - h * 0.8f), Size(w * 0.3f, h * 0.8f)
        )
        // Motes drifting up the beam.
        for (i in 0 until 3) {
            val p = (time * 0.6f + i * 0.33f + l.x * 0.001f) % 1f
            drawCircle(c.copy(alpha = (1f - p) * 0.9f), 2.2f, Offset(l.x + sin(p * 9f + i) * w * 0.3f, l.y - p * h))
        }
    }

    private fun DrawScope.drawOne(l: Loot, time: Float, abyssal: Boolean = false) {
        // Pops in over 0.2 s; never exactly 0 (a zero-size glow crashes Android, 0.25.2).
        val pop = (l.age * 5f).coerceIn(0.05f, 1f)
        val bob = sin(time * 3.2f + l.x * 0.05f) * 2.5f
        val x = l.x
        val y = l.y + bob
        if (l.kind == LootKind.GEAR) {
            // The owner's item art, life-sized enough to recognise, bobbing in its beam.
            val gear = l.gear ?: return
            drawOval(Color.Black.copy(alpha = 0.35f), Offset(x - 22f, l.y + 18f), Size(44f, 10f))
            with(com.cyberoperative.game.ui.common.GearIcons) { drawGear(gear, x, y - 6f, 58f * pop) }
            return
        }
        // Drawn larger than life so they read at the zoomed-out PC view.
        scale(SCALE, Offset(x, l.y)) {
            drawOval(Color.Black.copy(alpha = 0.35f), Offset(x - 10f, l.y + 9f), Size(20f, 6f))
            when (l.kind) {
                LootKind.HP_CHARGE -> potion(x, y, pop, time, abyssal)
                LootKind.PACKET_BOMB -> bomb(x, y, pop, time)
                LootKind.CASH -> cash(x, y, pop * (0.85f + 0.12f * ln(1f + l.amount.toFloat()).coerceAtMost(4f) / 4f))
                LootKind.UPGRADE -> {
                    val def = l.upgradeId?.let { com.cyberoperative.game.data.Upgrades.byIdOrNull(it) }
                    val sheen = rarityOf(l).ordinal >= com.cyberoperative.game.data.Rarity.LEGENDARY.ordinal
                    // Weapons drop as the shop's card; every other power-up as a chip.
                    if (def != null && com.cyberoperative.game.data.Upgrades.isWeapon(def)) weaponCard(x, y, pop, Color(rarityOf(l).color), def.glyph, sheen, time)
                    else chip(x, y, pop, Color(rarityOf(l).color), sheen, time)
                }
            }
        }
    }

    /** [abyssal]: FULL RESTORE is on: a red glowing abyssal draught (owner, 0.39.0). */
    private fun DrawScope.potion(x: Float, y: Float, k: Float, time: Float, abyssal: Boolean = false) {
        val glow = (if (abyssal) 0.4f else 0.25f) + 0.12f * sin(time * 4f)
        val liquid = if (abyssal) Color(0xFFD01020) else Color(0xFF00C878)
        val core = if (abyssal) Color(0xFFFF5A3A) else Color(0xFF00FF9C)
        drawCircle(com.cyberoperative.game.ui.common.SafeBrush.radialGradient(listOf((if (abyssal) Color(0xFFFF2B3A) else Color(0xFF00FF9C)).copy(alpha = glow), Color.Transparent), Offset(x, y), 26f * k), 26f * k, Offset(x, y))
        val r = 8f * k
        // Neck and cork.
        drawRect(Color(0xCCBFEFFF), Offset(x - 3f * k, y - r - 9f * k), Size(6f * k, 8f * k))
        drawRoundRect(Color(0xFF8A5A2B), Offset(x - 4f * k, y - r - 12f * k), Size(8f * k, 4f * k), CornerRadius(1.5f * k))
        // Bulb: glass, red-green charge liquid, highlight.
        drawCircle(Color(0x66BFEFFF), r + 1.5f * k, Offset(x, y))
        drawCircle(liquid, r, Offset(x, y + 1f * k))
        drawCircle(core, r * 0.62f, Offset(x - 1f * k, y + 2f * k))
        drawCircle(Color.White.copy(alpha = 0.7f), r * 0.22f, Offset(x - r * 0.4f, y - r * 0.3f))
        // A little "+".
        drawLine(Color.White, Offset(x - 3f * k, y + 1.5f * k), Offset(x + 3f * k, y + 1.5f * k), 1.6f * k)
        drawLine(Color.White, Offset(x, y - 1.5f * k), Offset(x, y + 4.5f * k), 1.6f * k)
        drawCircle(Color(0xFFBFEFFF), r + 1.5f * k, Offset(x, y), style = Stroke(1.2f * k))
    }

    private fun DrawScope.bomb(x: Float, y: Float, k: Float, time: Float) {
        // Pulsing pickup ring.
        val p = (time * 1.4f) % 1f
        drawCircle(Color(0xFF7DF9FF).copy(alpha = (1f - p) * 0.7f), (12f + p * 14f) * k, Offset(x, y), style = Stroke(2f))
        drawCircle(com.cyberoperative.game.ui.common.SafeBrush.radialGradient(listOf(Color(0xFF7DF9FF).copy(alpha = 0.22f), Color.Transparent), Offset(x, y), 24f * k), 24f * k, Offset(x, y))
        val r = 9f * k
        drawCircle(Color(0xFF14181F), r, Offset(x, y))
        drawCircle(Color(0xFF2E9BFF).copy(alpha = 0.55f), r, Offset(x, y), style = Stroke(1.6f * k))
        drawCircle(Color.White.copy(alpha = 0.45f), r * 0.25f, Offset(x - r * 0.4f, y - r * 0.4f))
        // Cap and fuse with a flickering spark.
        drawRect(Color(0xFF3A4252), Offset(x + r * 0.35f, y - r - 2f * k), Size(5f * k, 4f * k))
        path.reset()
        path.moveTo(x + r * 0.6f, y - r - 1f * k)
        path.quadraticTo(x + r * 1.2f, y - r - 8f * k, x + r * 1.5f, y - r - 5f * k)
        drawPath(path, Color(0xFFB0905A), style = Stroke(1.6f * k, cap = StrokeCap.Round))
        val flick = 0.6f + 0.4f * sin(time * 31f)
        drawCircle(Color(0xFFFFC233).copy(alpha = flick), 3f * k, Offset(x + r * 1.5f, y - r - 5f * k))
        drawCircle(Color.White.copy(alpha = flick), 1.4f * k, Offset(x + r * 1.5f, y - r - 5f * k))
    }

    private val glyphPaint = GameTextPaint(10f, argb(255, 255, 255, 255), bold = true)

    /**
     * A weapon drop: the upgrade shop's card (dark tile, rarity border, the weapon's glyph),
     * standing in its rarity beam with a glow halo above it.
     */
    private fun DrawScope.weaponCard(x: Float, y: Float, k: Float, c: Color, glyph: String, sheen: Boolean, time: Float) {
        val w = 30f * k
        val h = 24f * k
        val top = y - h * 0.75f
        drawCircle(com.cyberoperative.game.ui.common.SafeBrush.radialGradient(listOf(c.copy(alpha = 0.4f), Color.Transparent), Offset(x, top - 6f * k), 30f * k), 30f * k, Offset(x, top - 6f * k))
        drawRoundRect(Color(0xFF0A0D14), Offset(x - w / 2f, top), Size(w, h), CornerRadius(3.5f * k))
        drawRoundRect(c, Offset(x - w / 2f, top), Size(w, h), CornerRadius(3.5f * k), style = Stroke(2f * k))
        drawRect(c.copy(alpha = 0.35f), Offset(x - w / 2f + 2f * k, top + 2f * k), Size(w - 4f * k, 4f * k))
        glyphPaint.textSize = 9.5f * k
        glyphPaint.color = c.toArgb()
        drawGameText(glyph.take(5), x, top + h * 0.72f, glyphPaint)
        if (sheen) {
            val p = (time * 0.7f) % 1f
            val sx = x - w * 0.7f + p * w * 1.4f
            drawLine(Color.White.copy(alpha = 0.6f), Offset(sx - 4f * k, top + h), Offset(sx + 4f * k, top), 2.5f * k)
        }
    }

    /** An upgrade chip: a dark package with pins and a glowing die, all in its rarity colour. */
    private fun DrawScope.chip(x: Float, y: Float, k: Float, c: Color, sheen: Boolean, time: Float) {
        val s = 11f * k
        drawCircle(com.cyberoperative.game.ui.common.SafeBrush.radialGradient(listOf(c.copy(alpha = 0.35f), Color.Transparent), Offset(x, y), s * 2.6f), s * 2.6f, Offset(x, y))
        // Pins on all four sides.
        for (i in -1..1) {
            val o = i * s * 0.55f
            drawLine(c, Offset(x + o, y - s), Offset(x + o, y - s - 4f * k), 2f * k)
            drawLine(c, Offset(x + o, y + s), Offset(x + o, y + s + 4f * k), 2f * k)
            drawLine(c, Offset(x - s, y + o), Offset(x - s - 4f * k, y + o), 2f * k)
            drawLine(c, Offset(x + s, y + o), Offset(x + s + 4f * k, y + o), 2f * k)
        }
        drawRoundRect(Color(0xFF0B1220), Offset(x - s, y - s), Size(s * 2f, s * 2f), CornerRadius(2.5f * k))
        drawRoundRect(c, Offset(x - s, y - s), Size(s * 2f, s * 2f), CornerRadius(2.5f * k), style = Stroke(1.8f * k))
        // The die, with circuit traces.
        drawRect(c.copy(alpha = 0.85f), Offset(x - s * 0.45f, y - s * 0.45f), Size(s * 0.9f, s * 0.9f))
        drawLine(Color.White.copy(alpha = 0.6f), Offset(x - s * 0.3f, y - s * 0.15f), Offset(x + s * 0.3f, y - s * 0.15f), 1.2f * k)
        drawLine(Color.White.copy(alpha = 0.6f), Offset(x - s * 0.1f, y + s * 0.2f), Offset(x + s * 0.3f, y + s * 0.2f), 1.2f * k)
        if (sheen) {
            // GOLDEN / TITANIUM: a bright sheen sweeps across the package.
            val p = (time * 0.7f) % 1f
            val sx = x - s * 1.6f + p * s * 3.2f
            drawLine(Color.White.copy(alpha = 0.7f), Offset(sx - s * 0.3f, y + s), Offset(sx + s * 0.3f, y - s), 3f * k)
        }
    }

    private fun DrawScope.cash(x: Float, y: Float, k: Float) {
        val w = 18f * k
        val h = 16f * k
        // Sack body.
        path.reset()
        path.moveTo(x - w * 0.28f, y - h * 0.45f)
        path.cubicTo(x - w * 0.75f, y - h * 0.1f, x - w * 0.62f, y + h * 0.55f, x, y + h * 0.55f)
        path.cubicTo(x + w * 0.62f, y + h * 0.55f, x + w * 0.75f, y - h * 0.1f, x + w * 0.28f, y - h * 0.45f)
        path.close()
        drawPath(path, Brush.verticalGradient(listOf(Color(0xFFFFD86B), Color(0xFFD99A1E)), y - h * 0.5f, y + h * 0.6f))
        drawPath(path, Color(0xFF7A5200), style = Stroke(1.3f * k))
        // Tied neck.
        drawOval(Color(0xFFE8B23A), Offset(x - w * 0.3f, y - h * 0.68f), Size(w * 0.6f, h * 0.26f))
        drawLine(Color(0xFF7A5200), Offset(x - w * 0.24f, y - h * 0.45f), Offset(x + w * 0.24f, y - h * 0.45f), 1.6f * k)
        euroPaint.textSize = 10f * k
        drawGameText("€", x, y + h * 0.32f, euroPaint)
    }
}
