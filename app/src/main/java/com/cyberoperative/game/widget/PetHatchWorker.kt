package com.cyberoperative.game.widget

import com.cyberoperative.game.i18n.tr
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
        val name = inputData.getString(KEY_NAME).orEmpty().ifEmpty { tr("Your egg") }
        post(applicationContext, name, inputData.getInt(KEY_SLOT, 0))
        return Result.success()
    }

    companion object {
        private const val WORK = "pet_hatch"
        private const val KEY_NAME = "egg"
        private const val KEY_SLOT = "slot"
        private const val CHANNEL = "pets"
        private const val ID = 4108

        /** Schedules incubator [slot]'s reminder for [atWall] (replacing any earlier one); null cancels. */
        fun schedule(context: Context, slot: Int, atWall: Long?, eggName: String) {
            val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
            val work = "${WORK}_$slot"
            if (atWall == null) { wm.cancelUniqueWork(work); return }
            val delay = (atWall - System.currentTimeMillis()).coerceAtLeast(0L) + 2_000L
            wm.enqueueUniqueWork(work, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<PetHatchWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf(KEY_NAME to eggName, KEY_SLOT to slot)).build())
        }

        private fun post(context: Context, eggName: String, slot: Int) {
            if (!IdleRunNotifier.canPost(context)) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, tr("Pets"), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = tr("When an egg in the incubator has hatched")
                })
            }
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_idle_run)
                .setColor(0xFFFF6A1A.toInt())
                .setContentTitle(tr("Your egg has hatched!"))
                .setContentText(tr("{0} is ready. Open the incubator to meet your new pet.", eggName))
                .setContentIntent(IdleRunWidget.openPets(context))
                .setAutoCancel(true)
                .build()
            try { NotificationManagerCompat.from(context).notify(ID + slot, n) } catch (_: SecurityException) {}
        }
    }
}
