package com.cyberoperative.game.save

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * Local save (§55–56). One JSON document in a private SharedPreferences file,
 * covered by Android Auto Backup. Works fully offline. Writes are `apply()`
 * (async to disk) and the in-memory copy is the source of truth.
 */
class SaveRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _profile = MutableStateFlow(load())
    val profile: StateFlow<PlayerProfile> = _profile.asStateFlow()

    val current: PlayerProfile get() = _profile.value

    private fun load(): PlayerProfile {
        val raw = prefs.getString(KEY, null) ?: return PlayerProfile()
        val decoded = decodeOrNull(raw)
        if (decoded == null) {
            // Keep the unreadable save aside instead of silently destroying it.
            prefs.edit().putString(KEY_CORRUPT, raw).apply()
            return PlayerProfile()
        }
        return decoded
    }

    fun update(transform: (PlayerProfile) -> PlayerProfile) {
        val next = transform(_profile.value)
        _profile.value = next
        prefs.edit().putString(KEY, encode(next)).apply()
    }

    companion object {
        const val FILE = "cyber_operative_save"
        private const val KEY = "profile_json"
        private const val KEY_CORRUPT = "profile_json_unreadable"

        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun encode(p: PlayerProfile): String = json.encodeToString(PlayerProfile.serializer(), p)

        /** Corrupt or unreadable data never crashes the game. */
        fun decodeOrNull(raw: String): PlayerProfile? = try {
            json.decodeFromString(PlayerProfile.serializer(), raw)
        } catch (_: Exception) {
            null
        }
    }
}
