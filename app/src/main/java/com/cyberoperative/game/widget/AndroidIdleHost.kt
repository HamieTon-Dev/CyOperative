package com.cyberoperative.game.widget

import android.content.Context
import com.cyberoperative.game.idle.IdleRunSimulator
import com.cyberoperative.game.idle.IdleRunState
import com.cyberoperative.game.idle.IdleRuns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android's Idle Runs host: the saved run, its background worker, the home-screen widget and the
 * end-of-run notification. One per process (CyberOperativeApp.idle).
 */
class AndroidIdleHost(context: Context) : IdleRuns.Host {
    private val app = context.applicationContext
    private val repo = IdleRunRepository(app)
    private val _state = MutableStateFlow(repo.load())
    override val state: StateFlow<IdleRunState?> = _state.asStateFlow()

    /** Set by MainActivity: shows the Android 13+ notification permission prompt. */
    @Volatile
    var permissionAsker: (() -> Unit)? = null

    private val lock = Any()

    // The widget is drawn outside the lock: drawing happens on the main thread, which may be
    // waiting on this lock for its own once-a-second sync.
    override fun sync(refreshWidget: Boolean) {
        var draw = refreshWidget
        val after = synchronized(lock) {
            val before = repo.load() ?: run { _state.value = null; null }
            if (before == null) null else {
                var a = IdleRunSimulator.advance(before, System.currentTimeMillis())
                val justEnded = before.active && a.ended
                if (a.ended && !a.notified) {
                    // From the background: tell the player. On the Idle Runs page they can already see it.
                    if (refreshWidget) IdleRunNotifier.post(app, a)
                    a = a.copy(notified = true)
                }
                if (a != before) repo.save(a, durable = refreshWidget || justEnded)
                _state.value = a
                if (a.active) { if (refreshWidget) IdleRunWorker.schedule(app, predictEnd(a)) } else IdleRunWorker.cancel(app)
                if (justEnded) draw = true
                a
            }
        }
        if (draw) IdleRunWidget.refresh(app, after)
    }

    override fun begin(run: IdleRunState) {
        synchronized(lock) {
            repo.save(run)
            _state.value = run
            IdleRunWorker.schedule(app, predictEnd(run))
        }
        IdleRunWidget.refresh(app, run)
    }

    override fun store(run: IdleRunState?) {
        synchronized(lock) {
            repo.save(run)
            _state.value = run
            if (run == null || run.ended) IdleRunWorker.cancel(app)
        }
        IdleRunWidget.refresh(app, run)
    }

    override fun requestNotifications() {
        if (!IdleRunNotifier.canPost(app)) permissionAsker?.invoke()
    }

    /** When the run will end (the simulation is deterministic, so it can be run ahead). */
    private fun predictEnd(run: IdleRunState): Long? {
        val end = IdleRunSimulator.advance(run, run.startedAt + IdleRunSimulator.MAX_RUN_SECONDS * 1000L + 1000L)
        return if (end.ended) end.endedAt else null
    }
}
