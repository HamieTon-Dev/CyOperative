package com.cyberoperative.game.widget

import com.cyberoperative.game.i18n.tr
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.cyberoperative.game.CyberOperativeApp
import com.cyberoperative.game.MainActivity
import com.cyberoperative.game.R
import com.cyberoperative.game.data.OpProgress
import com.cyberoperative.game.engine.Difficulty
import com.cyberoperative.game.idle.IdleRunState
import com.cyberoperative.game.idle.IdleStatus
import com.cyberoperative.game.ui.common.formatAmount
import com.cyberoperative.game.ui.common.formatFull
import com.cyberoperative.game.ui.game.IdleBattleArt
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The IDLE RUN home-screen widget (2 × 3, portrait). Updates come from [AndroidIdleHost]. */
class IdleRunWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        (context.applicationContext as CyberOperativeApp).idle.sync(refreshWidget = true)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        IdleRunWidget.refresh(context, (context.applicationContext as CyberOperativeApp).idle.state.value)
    }
}

/** The IDLE RUN 3 × 1 (wide) widget (owner, 0.37.6). Same run, same updates. */
class IdleRunWideWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        (context.applicationContext as CyberOperativeApp).idle.sync(refreshWidget = true)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        IdleRunWidget.refresh(context, (context.applicationContext as CyberOperativeApp).idle.state.value)
    }
}

/**
 * Builds the widgets (owner, 0.37.0; 3 × 1 in 0.37.6). Extremely light: no game loop and no redraw timer. The
 * battle preview is four bitmaps rendered with the game's own drawing code (operator, the one
 * threat, room props) and a ViewFlipper cycles them (idle → attack → hit → recover, ~4.4 s);
 * everything else is plain text. Tapping anywhere opens the game on the Idle Runs page.
 */
object IdleRunWidget {
    const val ACTION_OPEN_IDLE_RUNS = "com.cyberoperative.game.action.OPEN_IDLE_RUNS"
    const val ACTION_OPEN_PETS = "com.cyberoperative.game.action.OPEN_PETS"

    /** Opens Cyber Operative on CHARACTER › PETS (the hatch notification). */
    fun openPets(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_PETS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 8, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private val frameViews = intArrayOf(R.id.idle_frame_0, R.id.idle_frame_1, R.id.idle_frame_2, R.id.idle_frame_3)
    private val main = Handler(Looper.getMainLooper())

    /** Opens Cyber Operative straight on Main Menu › Idle Runs. */
    fun openIdleRuns(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_IDLE_RUNS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 7, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Redraws every placed widget for [run] (null: no run). Drawing happens on the main thread. */
    fun refresh(context: Context, run: IdleRunState?) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(app, IdleRunWidgetReceiver::class.java))
        val wide = manager.getAppWidgetIds(ComponentName(app, IdleRunWideWidgetReceiver::class.java))
        if (ids.isEmpty() && wide.isEmpty()) return
        onMain {
            for (id in ids) {
                val o = manager.getAppWidgetOptions(id)
                val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 } ?: 140
                manager.updateAppWidget(id, build(app, run, w))
            }
            for (id in wide) {
                val o = manager.getAppWidgetOptions(id)
                val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0).takeIf { it > 0 } ?: 60
                manager.updateAppWidget(id, buildWide(app, run, h))
            }
        }
    }

    private val wideFrames = intArrayOf(R.id.wide_frame_0, R.id.wide_frame_1, R.id.wide_frame_2, R.id.wide_frame_3)

    /**
     * The 3 × 1 widget for [run] at [heightDp] tall: the battle preview on the left (the same
     * four pre-rendered frames), and on the right the status, level / next boss, € and XP.
     */
    fun buildWide(context: Context, run: IdleRunState?, heightDp: Int): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_idle_run_wide)
        v.setOnClickPendingIntent(R.id.wide_root, openIdleRuns(context))
        val density = context.resources.displayMetrics.density.coerceAtMost(2f)
        val artH = ((heightDp - 10).coerceIn(40, 120) * density).toInt()
        val artW = artH * 2
        val ended = run != null && run.status != IdleStatus.ACTIVE
        val inferno = run != null && Difficulty.byName(run.difficulty).inferno > 0
        v.setInt(R.id.wide_root, "setBackgroundResource", if (ended) R.drawable.widget_frame_red else R.drawable.widget_frame_cyan)
        v.setViewVisibility(R.id.wide_flipper, if (run == null) View.GONE else View.VISIBLE)
        v.setViewVisibility(R.id.wide_badge, if (run == null || ended) View.GONE else View.VISIBLE)
        // A short (one-row) widget fits three lines; the hint line needs a taller one.
        val roomy = heightDp >= 64
        v.setViewVisibility(R.id.wide_hint, if (roomy || run == null) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.wide_stats, if (run == null) View.GONE else View.VISIBLE)
        v.setTextViewText(R.id.wide_badge, if (inferno) tr("INFERNO") else if (run?.difficulty == Difficulty.HARD.name) tr("HARD") else tr("STANDARD"))
        v.setInt(R.id.wide_badge, "setBackgroundResource", if (inferno) R.drawable.widget_badge_inferno else R.drawable.widget_badge_standard)
        when {
            run == null -> {
                v.setTextViewText(R.id.wide_title, tr("IDLE RUN"))
                v.setTextColor(R.id.wide_title, 0xFF00E5FF.toInt())
                v.setTextViewText(R.id.wide_line2, tr("No Active Run"))
                v.setTextViewText(R.id.wide_hint, tr("TAP TO START IDLE RUN · 40% € · 50% XP"))
            }
            ended -> {
                val art = render(artW, artH) { with(IdleBattleArt) { drawDefeated(run, 0f) } }
                for (f in wideFrames) v.setImageViewBitmap(f, art)
                val defeated = run.status == IdleStatus.DEFEATED
                v.setTextViewText(R.id.wide_title, when { !defeated -> tr("RUN COMPLETE"); roomy -> tr("OPERATOR DEFEATED"); else -> tr("DEFEATED") })
                v.setTextColor(R.id.wide_title, 0xFFFF2D55.toInt())
                v.setTextViewText(R.id.wide_line2, if (roomy) tr("Highest level {0}", run.highestLevel) else tr("LV {0} · NEW RUN?", run.highestLevel))
                v.setTextViewText(R.id.wide_euros, "€ " + short(run.eurosEarned))
                v.setTextViewText(R.id.wide_xp, tr("XP ") + short(run.xpEarned))
                v.setTextViewText(R.id.wide_hint, tr("START NEW IDLE RUN?"))
            }
            else -> {
                for ((i, f) in IdleBattleArt.Frame.entries.withIndex()) {
                    v.setImageViewBitmap(wideFrames[i], render(artW, artH) { with(IdleBattleArt) { drawBattle(run, f, i * 0.35f) } })
                }
                v.setTextViewText(R.id.wide_title, tr("IDLE RUN"))
                v.setTextColor(R.id.wide_title, 0xFF00E5FF.toInt())
                v.setTextViewText(R.id.wide_line2, if (run.bossLevel) tr("BOSS FIGHT · LV {0}", run.level) else tr("LV {0} · BOSS {1}", run.level, run.nextBoss))
                v.setTextViewText(R.id.wide_euros, "€ " + short(run.eurosEarned))
                v.setTextViewText(R.id.wide_xp, tr("XP ") + short(run.xpEarned))
                v.setTextViewText(R.id.wide_hint, tr("OP LV {0} · 40% € · 50% XP", opLevel(run)))
            }
        }
        return v
    }

    /** The widget for [run] at [widthDp] wide. */
    fun build(context: Context, run: IdleRunState?, widthDp: Int): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_idle_run)
        v.setOnClickPendingIntent(R.id.idle_root, openIdleRuns(context))
        val density = context.resources.displayMetrics.density.coerceAtMost(2f)
        val artW = ((widthDp - 16).coerceIn(104, 260) * density).toInt()
        val artH = artW / 2

        val inferno = run != null && Difficulty.byName(run.difficulty).inferno > 0
        v.setTextViewText(R.id.idle_badge, if (inferno) tr("INFERNO") else if (run?.difficulty == Difficulty.HARD.name) tr("HARD") else tr("STANDARD"))
        v.setInt(R.id.idle_badge, "setBackgroundResource", if (inferno) R.drawable.widget_badge_inferno else R.drawable.widget_badge_standard)
        v.setViewVisibility(R.id.idle_badge, if (run == null) View.GONE else View.VISIBLE)
        val ended = run != null && run.status != IdleStatus.ACTIVE
        v.setInt(R.id.idle_root, "setBackgroundResource", if (ended) R.drawable.widget_frame_red else R.drawable.widget_frame_cyan)
        v.setTextColor(R.id.idle_title, if (ended) 0xFFFF2D55.toInt() else 0xFF00E5FF.toInt())

        v.setViewVisibility(R.id.idle_active, if (run != null && !ended) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.idle_ended, if (ended) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.idle_none, if (run == null) View.VISIBLE else View.GONE)

        when {
            run == null -> {}
            ended -> {
                v.setTextViewText(R.id.idle_ended_title, if (run.status == IdleStatus.DEFEATED) tr("Operator Defeated") else tr("Idle Run Complete"))
                v.setImageViewBitmap(R.id.idle_ended_art, render(artW, artH) { with(IdleBattleArt) { drawDefeated(run, 0f) } })
                v.setTextViewText(R.id.idle_ended_level, run.highestLevel.toString())
                v.setTextViewText(R.id.idle_ended_xp, short(run.xpEarned))
                v.setTextViewText(R.id.idle_ended_euros, short(run.eurosEarned))
                v.setTextViewText(R.id.idle_ended_cta, tr("START NEW IDLE RUN?"))
            }
            else -> {
                for ((i, f) in IdleBattleArt.Frame.entries.withIndex()) {
                    v.setImageViewBitmap(frameViews[i], render(artW, artH) { with(IdleBattleArt) { drawBattle(run, f, i * 0.35f) } })
                }
                v.setTextViewText(R.id.idle_op_lv, opLevel(run))
                v.setTextViewText(R.id.idle_level, run.level.toString())
                v.setTextViewText(R.id.idle_euros, short(run.eurosEarned))
                v.setTextViewText(R.id.idle_xp, short(run.xpEarned))
                v.setTextViewText(R.id.idle_boss, if (run.bossLevel) tr("BOSS FIGHT · LV {0}", run.level) else tr("LV {0} · NEXT BOSS {1}", run.level, run.nextBoss))
                v.setImageViewBitmap(R.id.idle_strip, strip(run, artW, (10 * density).toInt()))
            }
        }
        return v
    }

    /** "28", or "OC 12" past OP Lv 60. */
    private fun opLevel(run: IdleRunState): String = OpProgress.of(run.opXp).let { if (it.level < OpProgress.MAX_LEVEL) it.level.toString() else tr("OC {0}", it.overclock) }

    /** Full number when it fits a widget cell, else 398k / 3.7m. */
    internal fun short(v: Long): String = formatFull(v).let { if (it.length <= 6) it else formatAmount(v) }

    /** Ten dots: this block's cleared levels, the current one and the boss. */
    private fun strip(run: IdleRunState, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(10), h.coerceAtLeast(4), Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val first = run.nextBoss - 9
        val step = (w - h).toFloat() / 9f
        p.color = 0xFF3A1820.toInt(); p.strokeWidth = h * 0.15f
        c.drawLine(h / 2f, h / 2f, w - h / 2f, h / 2f, p)
        for (k in 0..9) {
            val l = first + k
            p.color = when {
                l < run.level -> 0xFFFF3A4A.toInt()
                l == run.level -> 0xFFE6EEFA.toInt()
                l == run.nextBoss -> 0xFFFF8A1A.toInt()
                else -> 0xFF2A3346.toInt()
            }
            c.drawCircle(h / 2f + k * step, h / 2f, if (l == run.nextBoss) h * 0.5f else h * 0.36f, p)
        }
        return bmp
    }

    /** Draws with the game's DrawScope code into a bitmap. */
    internal fun render(w: Int, h: Int, block: DrawScope.() -> Unit): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = androidx.compose.ui.graphics.Canvas(android.graphics.Canvas(bmp))
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) { block() }
        return bmp
    }

    /** The game's drawing code shares a little state with the live renderer: draw on the main thread only. */
    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) { block(); return }
        val done = CountDownLatch(1)
        main.post { try { block() } finally { done.countDown() } }
        done.await(8, TimeUnit.SECONDS)
    }
}
