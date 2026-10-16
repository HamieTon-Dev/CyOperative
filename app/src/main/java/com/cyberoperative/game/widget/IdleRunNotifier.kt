package com.cyberoperative.game.widget

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cyberoperative.game.R
import com.cyberoperative.game.idle.IdleRunState
import com.cyberoperative.game.idle.IdleStatus

/** "Operator terminated · start a new run?" when an Idle Run ends (Android 13+ asks first). */
object IdleRunNotifier {
    private const val CHANNEL = "idle_runs"
    private const val ID = 4107

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun post(context: Context, run: IdleRunState) {
        if (!canPost(context)) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Idle Runs", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When your idle operator is terminated or its run ends"
            })
        }
        val defeated = run.status == IdleStatus.DEFEATED
        val title = if (defeated) "Operator terminated" else "Idle run complete"
        val text = "Highest level ${run.highestLevel} · € ${"%,d".format(run.eurosEarned)} · XP ${"%,d".format(run.xpEarned)}. Start a new idle run?"
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_idle_run)
            .setColor(if (defeated) 0xFFFF2D55.toInt() else 0xFF00E5FF.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(IdleRunWidget.openIdleRuns(context))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID, n)
        } catch (_: SecurityException) {
            // Permission withdrawn between the check and the post.
        }
    }
}
