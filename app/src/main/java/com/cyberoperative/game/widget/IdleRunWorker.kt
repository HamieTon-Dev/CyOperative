package com.cyberoperative.game.widget

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.cyberoperative.game.CyberOperativeApp
import java.util.concurrent.TimeUnit

/**
 * Background catch-up for an Idle Run (owner, 0.37.0). No continuous gameplay: every 15 minutes
 * (Android's minimum) the run is simulated up to now, saved, and the widget redrawn. A one-off
 * job also fires when the run is predicted to end, so the widget flips to OPERATOR DEFEATED and
 * the notification goes out on time. WorkManager keeps both across reboots.
 */
class IdleRunWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        (applicationContext as CyberOperativeApp).idle.sync(refreshWidget = true)
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "idle_run_sync"
        private const val AT_END = "idle_run_end"

        fun schedule(context: Context, endsAt: Long?) {
            // Never let background scheduling break the game (e.g. WorkManager not initialised).
            val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
            wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<IdleRunWorker>(15, TimeUnit.MINUTES).build())
            if (endsAt != null) {
                val delay = (endsAt - System.currentTimeMillis()).coerceAtLeast(0L) + 3_000L
                wm.enqueueUniqueWork(AT_END, ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<IdleRunWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build())
            }
        }

        fun cancel(context: Context) {
            val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
            wm.cancelUniqueWork(PERIODIC)
            wm.cancelUniqueWork(AT_END)
        }
    }
}
