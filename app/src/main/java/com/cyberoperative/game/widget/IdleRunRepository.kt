package com.cyberoperative.game.widget

import android.content.Context
import com.cyberoperative.game.idle.IdleRunState

/**
 * The Idle Run, saved on its own (owner, 0.37.0): one JSON document in a private
 * SharedPreferences file, separate from the player save, so the background worker and the
 * widget never write the profile. Survives process death, reboots and app updates; a file from
 * a newer or broken build reads as "no run" instead of crashing.
 */
class IdleRunRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    @Synchronized
    fun load(): IdleRunState? = IdleRunState.decodeOrNull(prefs.getString(KEY, null))

    /** [durable]: written before returning (a worker may be stopped right after it returns). */
    @Synchronized
    fun save(run: IdleRunState?, durable: Boolean = true) {
        val e = prefs.edit()
        if (run == null) e.remove(KEY) else e.putString(KEY, IdleRunState.encode(run))
        if (durable) e.commit() else e.apply()
    }

    companion object {
        const val FILE = "cyber_operative_idle_run"
        private const val KEY = "run_json"
    }
}
