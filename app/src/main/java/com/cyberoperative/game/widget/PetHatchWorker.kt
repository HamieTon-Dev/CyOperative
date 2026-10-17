package com.cyberoperative.game.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cyberoperative.game.R
import java.util.concurrent.TimeUnit

/** "Your egg has hatched!" when the incubator's egg is due (owner, 0.39.0). */
class PetHatchWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val name = inputData.getString(KEY_NAME).orEmpty().ifEmpty { "Your egg" }
        post(applicationContext, name)
        return Result.success()
    }

    companion object {
        private const val WORK = "pet_hatch"
        private const val KEY_NAME = "egg"
        private const val CHANNEL = "pets"
        private const val ID = 4108

        /** Schedules the reminder for [atWall] (replacing any earlier one); null cancels. */
        fun schedule(context: Context, atWall: Long?, eggName: String) {
            val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
            if (atWall == null) { wm.cancelUniqueWork(WORK); return }
            val delay = (atWall - System.currentTimeMillis()).coerceAtLeast(0L) + 2_000L
            wm.enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<PetHatchWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf(KEY_NAME to eggName)).build())
        }

        private fun post(context: Context, eggName: String) {
            if (!IdleRunNotifier.canPost(context)) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Pets", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "When an egg in the incubator has hatched"
                })
            }
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_idle_run)
                .setColor(0xFFFF6A1A.toInt())
                .setContentTitle("Your egg has hatched!")
                .setContentText("$eggName is ready. Open the incubator to meet your new pet.")
                .setContentIntent(IdleRunWidget.openPets(context))
                .setAutoCancel(true)
                .build()
            try { NotificationManagerCompat.from(context).notify(ID, n) } catch (_: SecurityException) {}
        }
    }
}
