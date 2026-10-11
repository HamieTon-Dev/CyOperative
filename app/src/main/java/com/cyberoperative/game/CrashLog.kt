package com.cyberoperative.game

import android.content.Context
import java.io.File

/**
 * Crash reporter: an uncaught exception is written to a file before the app closes, and the
 * next launch shows it with a COPY button so it can be sent to the developer.
 */
object CrashLog {
    private fun file(ctx: Context) = File(ctx.filesDir, "last_crash.txt")

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val info = app.packageManager.getPackageInfo(app.packageName, 0)
                file(app).writeText(
                    "Cyber Operative ${info.versionName} · Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}) · " +
                        "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n\n" + error.stackTraceToString()
                )
            } catch (_: Throwable) {}
            previous?.uncaughtException(thread, error)
        }
    }

    /** The last crash report, or null. */
    fun read(ctx: Context): String? = file(ctx).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

    fun clear(ctx: Context) { file(ctx).delete() }
}
