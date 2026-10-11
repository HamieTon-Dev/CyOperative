package com.cyberoperative.game.save

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * PC save: the same one-document JSON profile as Android, in a file under the user's home
 * (Steam Cloud can sync this folder: see desktop/README.md). Writes go to a temp file and are
 * renamed into place, so a crash mid-write never corrupts the save.
 */
class SaveRepository(private val dir: File = defaultDir()) {

    private val file = File(dir, FILE)

    private val _profile = MutableStateFlow(load())
    val profile: StateFlow<PlayerProfile> = _profile.asStateFlow()

    val current: PlayerProfile get() = _profile.value

    private fun load(): PlayerProfile {
        if (!file.exists()) return PlayerProfile()
        val raw = try { file.readText() } catch (_: Exception) { return PlayerProfile() }
        val decoded = decodeOrNull(raw)
        if (decoded == null) {
            // Keep the unreadable save aside instead of silently destroying it.
            try { file.copyTo(File(dir, "$FILE.unreadable"), overwrite = true) } catch (_: Exception) {}
            return PlayerProfile()
        }
        return com.cyberoperative.game.meta.StoreManager.migrate(decoded)
    }

    @Synchronized
    fun update(transform: (PlayerProfile) -> PlayerProfile) {
        val next = transform(_profile.value)
        _profile.value = next
        try {
            dir.mkdirs()
            val tmp = File(dir, "$FILE.tmp")
            tmp.writeText(encode(next))
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        } catch (_: Exception) {
        }
    }

    companion object {
        const val FILE = "cyber_operative_save.json"

        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun encode(p: PlayerProfile): String = json.encodeToString(PlayerProfile.serializer(), p)

        /** Corrupt or unreadable data never crashes the game. */
        fun decodeOrNull(raw: String): PlayerProfile? = try {
            json.decodeFromString(PlayerProfile.serializer(), raw)
        } catch (_: Exception) {
            null
        }

        /**
         * %APPDATA%\CyberOperative on Windows, ~/.local/share/CyberOperative elsewhere. A debug
         * build (-PdebugBuild) keeps its own CyberOperative-Debug save so test ◇ never touch the real one.
         */
        fun defaultDir(): File {
            val name = if (com.cyberoperative.game.platform.AppInfo.debug) "CyberOperative-Debug" else "CyberOperative"
            val appData = System.getenv("APPDATA")
            return if (appData != null) File(appData, name)
            else File(System.getProperty("user.home"), ".local/share/$name")
        }
    }
}
