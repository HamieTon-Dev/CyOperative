package com.cyberoperative.game.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.cyberoperative.game.data.Bosses
import com.cyberoperative.game.data.EliteModifier
import com.cyberoperative.game.data.Enemies
import com.cyberoperative.game.data.Environment
import com.cyberoperative.game.data.Environments
import com.cyberoperative.game.data.ObstacleKind
import com.cyberoperative.game.data.ObstacleSpec
import com.cyberoperative.game.data.OperativeSkin
import com.cyberoperative.game.data.OperativeSkins
import com.cyberoperative.game.data.Operatives
import com.cyberoperative.game.engine.Difficulty
import com.cyberoperative.game.idle.IdleRunState

/**
 * The Idle Run battle preview (owner, 0.37.0): the equipped operator, the ONE threat it is
 * fighting and a couple of room props. Every piece is the game's own drawing code (the same
 * figures, threat sprites, boss bodies, INFERNO paintings and props as in a run); nothing is
 * redrawn. The home-screen widget pre-renders the four [Frame]s into bitmaps and flips through
 * them; the in-app Idle Runs screen draws them live.
 */
object IdleBattleArt {
    /** The four pre-rendered states the widget cycles through. */
    enum class Frame { IDLE, ATTACK, HIT, RECOVER }

    private val renderer = ArenaRenderer()

    fun skinOf(s: IdleRunState): OperativeSkin =
        if (s.skinId == OperativeSkins.NEON_ID) OperativeSkins.neon(s.neonColor) else OperativeSkins.byId(s.skinId)

    fun bodyOf(s: IdleRunState): BodyStyle = BodyStyle.byId(Operatives.byId(s.classId).bodyId)

    /** The room theme: an INFERNO hell theme only on INFERNO runs. */
    fun envOf(s: IdleRunState): Environment {
        val tier = Difficulty.byName(s.difficulty).inferno
        return if (tier > 0) {
            val order = Environments.inferno.indices.shuffled(kotlin.random.Random(s.seed + 991))
            Environments.inferno[order[((s.level - 1).coerceAtLeast(0) / 3) % Environments.inferno.size]]
        } else Environments.forLevel(s.level, s.seed)
    }

    /**
     * Draws the fight into the whole draw area (best around 2:1). [time] drives the sprites' own
     * idle animation; [frame] picks the pose.
     */
    fun DrawScope.drawBattle(s: IdleRunState, frame: Frame, time: Float) = clipRect(0f, 0f, size.width, size.height) { battle(s, frame, time) }

    private fun DrawScope.battle(s: IdleRunState, frame: Frame, time: Float) {
        val w = size.width
        val h = size.height
        val env = envOf(s)
        val inferno = Difficulty.byName(s.difficulty).inferno > 0
        floor(env, w, h)
        // Props along the back wall, in the room's theme (the game's prop drawing).
        val u = h / 140f
        props(env, inferno, time, w, h, u)

        // The operator, left, facing the threat.
        val attacking = frame == Frame.ATTACK
        val footY = h * 0.93f
        with(OperativeFigures) {
            drawFigure(bodyOf(s), skinOf(s), w * 0.25f, footY, u * 1.25f, if (attacking) -0.12f else 0f, attacking, time, look = s.look)
        }

        // The threat, right.
        val e = s.enemy
        val hit = frame == Frame.HIT
        val winding = frame == Frame.RECOVER
        val ex = w * 0.71f
        if (e != null && e.boss) {
            val def = Bosses.byId(e.id) ?: return
            val phase = when {
                e.hp / e.maxHp > 0.66f -> 0
                e.hp / e.maxHp > 0.33f -> 1
                else -> 2
            }
            // The boss fills a box on the right half (its codex framing), clear of the operator.
            val bw = (w * 0.5f).coerceAtMost(h * 1.1f)
            translate(w * 0.98f - bw, h * 0.04f) {
                drawContext.canvas.save()
                drawBossPreview(def, phase, time, winding, hit = hit, box = Size(bw, h * 0.94f))
                drawContext.canvas.restore()
            }
        } else {
            val def = Enemies.byId(e?.id ?: "malware")
            val elite = e?.elite?.let { runCatching { EliteModifier.valueOf(it) }.getOrNull() }
            val r = h * 0.2f
            val cy = h * 0.62f
            val was = EnemySprites.hell
            EnemySprites.hell = inferno
            try {
                with(EnemySprites) {
                    if (elite != null) eliteBack(elite, Offset(ex, cy), r, time, 7)
                    draw(def, ex, cy, r, time, 7, Math.PI.toFloat(), winding, hit, elite)
                    if (elite != null) eliteFront(elite, Offset(ex, cy), r, time)
                }
            } finally {
                EnemySprites.hell = was
            }
        }
    }

    /** The defeated card: the operator down in its room (the same figure, dimmed). */
    fun DrawScope.drawDefeated(s: IdleRunState, time: Float) = clipRect(0f, 0f, size.width, size.height) { defeated(s, time) }

    private fun DrawScope.defeated(s: IdleRunState, time: Float) {
        val w = size.width
        val h = size.height
        val env = envOf(s)
        val inferno = Difficulty.byName(s.difficulty).inferno > 0
        floor(env, w, h)
        props(env, inferno, time, w, h, h / 140f)
        with(OperativeFigures) {
            drawFigure(bodyOf(s), skinOf(s), w * 0.5f, h * 0.93f, h / 140f * 1.25f, 0f, false, 0f, alpha = 0.45f, look = s.look)
        }
        drawRect(Color(0x66FF1A2E), Offset.Zero, Size(w, h))
    }

    /** The room floor in the theme's own plate colours. */
    private fun DrawScope.floor(env: Environment, w: Float, h: Float) {
        drawRect(Color(env.base), Offset.Zero, Size(w, h))
        val tile = h / 4f
        var y = h * 0.35f
        var row = 0
        while (y < h) {
            var x = -(row % 2) * tile / 2f
            var col = 0
            while (x < w) {
                drawRect(Color(if ((col + row) % 2 == 0) env.plateA else env.plateB), Offset(x + 1f, y + 1f), Size(tile - 2f, tile - 2f))
                x += tile; col++
            }
            y += tile; row++
        }
        drawRect(Color(env.accent).copy(alpha = 0.35f), Offset(0f, h * 0.35f - 1.5f), Size(w, 3f))
    }

    private fun DrawScope.props(env: Environment, inferno: Boolean, time: Float, w: Float, h: Float, u: Float) {
        val k = u * 0.9f
        val items = listOf(
            Triple(ObstacleKind.SERVER_RACK, w * 0.02f, 50f),
            Triple(ObstacleKind.CRATES, w * 0.47f, 44f),
            Triple(ObstacleKind.SMALL_SERVER, w * 0.86f, 34f)
        )
        for ((kind, x, pw) in items) {
            // Standing on the back-wall line (prop art rises above its footprint).
            translate(x, h * 0.35f - 22f * k) {
                scale(k, k, Offset.Zero) {
                    with(renderer) { drawPropAlone(ObstacleSpec(com.cyberoperative.game.core.Rect(0f, 0f, pw, 22f), kind), time, env, inferno) }
                }
            }
        }
    }
}
