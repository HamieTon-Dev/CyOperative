package com.cyberoperative.game.steam

import com.cyberoperative.game.i18n.tr
import com.codedisaster.steamworks.SteamAPI
import com.codedisaster.steamworks.SteamFriends
import com.codedisaster.steamworks.SteamFriendsCallback
import com.codedisaster.steamworks.SteamID
import com.codedisaster.steamworks.SteamLibraryLoader
import com.codedisaster.steamworks.SteamMatchmaking
import com.codedisaster.steamworks.SteamMatchmakingCallback
import com.codedisaster.steamworks.SteamNativeHandle
import com.codedisaster.steamworks.SteamNetworking
import com.codedisaster.steamworks.SteamNetworkingCallback
import com.cyberoperative.game.platform.AppInfo
import com.codedisaster.steamworks.SteamResult
import com.codedisaster.steamworks.SteamUser
import com.codedisaster.steamworks.SteamUserCallback
import kotlinx.coroutines.delay
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The Steam client connection (steamworks4j): start-up, the callback pump, and the
 * interfaces the store and co-op use. Everything degrades gracefully: when Steam isn't
 * running (or the natives can't load), [running] stays false and the game plays offline.
 *
 * App ID: read from steam_appid.txt next to the game (development), else [DEFAULT_APP_ID].
 * 480 is Valve's public test app (Spacewar) until the real App ID is assigned.
 */
object SteamService {
    const val DEFAULT_APP_ID = 480

    var running = false
        private set
    var appId = DEFAULT_APP_ID
        private set
    /** Path of the loaded steam_api library (the Inventory calls go through it with JNA). */
    var steamApiPath: String? = null
        private set
    var lastError: String? = null
        private set

    lateinit var user: SteamUser
        private set
    lateinit var friends: SteamFriends
        private set
    lateinit var matchmaking: SteamMatchmaking
        private set
    lateinit var networking: SteamNetworking
        private set

    /** Co-op listens here (set by [SteamCoopBackend]). */
    var matchmakingListener: SteamMatchmakingCallback? = null
    var friendsListener: SteamFriendsCallback? = null
    var networkingListener: SteamNetworkingCallback? = null
    /** Called once per pump (≈60 Hz) on the UI thread: P2P reads, Inventory polling. */
    val tickers = ArrayList<() -> Unit>()

    /** Lobby id from a "+connect_lobby <id>" launch (a Steam invite opened the game). */
    var launchLobby: Long = 0L
        private set

    fun start(args: Array<String> = emptyArray()) {
        val i = args.indexOf("+connect_lobby")
        if (i >= 0) launchLobby = args.getOrNull(i + 1)?.toLongOrNull() ?: 0L
        try {
            val idFile = File("steam_appid.txt")
            appId = idFile.takeIf { it.isFile }?.readText()?.trim()?.toIntOrNull() ?: DEFAULT_APP_ID
            if (!loadNatives()) { lastError = tr("Steam libraries missing"); return }
            // Release builds launched outside Steam relaunch through the client (once a real App ID exists).
            if (appId != DEFAULT_APP_ID && !idFile.isFile && SteamAPI.restartAppIfNecessary(appId)) {
                System.exit(0)
            }
            if (!SteamAPI.init()) { lastError = tr("Steam isn't running"); return }
            user = SteamUser(object : SteamUserCallback {})
            friends = SteamFriends(object : SteamFriendsCallback {
                override fun onGameLobbyJoinRequested(steamIDLobby: SteamID, steamIDFriend: SteamID) {
                    friendsListener?.onGameLobbyJoinRequested(steamIDLobby, steamIDFriend)
                }
                override fun onGameOverlayActivated(active: Boolean, userInitiated: Boolean, appID: Int) {
                    if (!active) SteamStore.onOverlayClosed()
                }
                override fun onPersonaStateChange(steamID: SteamID, change: SteamFriends.PersonaChange) {
                    friendsListener?.onPersonaStateChange(steamID, change)
                }
            })
            matchmaking = SteamMatchmaking(object : SteamMatchmakingCallback {
                override fun onLobbyInvite(steamIDUser: SteamID, steamIDLobby: SteamID, gameID: Long) {
                    matchmakingListener?.onLobbyInvite(steamIDUser, steamIDLobby, gameID)
                }
                override fun onLobbyEnter(steamIDLobby: SteamID, chatPermissions: Int, blocked: Boolean, response: SteamMatchmaking.ChatRoomEnterResponse) {
                    matchmakingListener?.onLobbyEnter(steamIDLobby, chatPermissions, blocked, response)
                }
                override fun onLobbyDataUpdate(steamIDLobby: SteamID, steamIDMember: SteamID, success: Boolean) {
                    matchmakingListener?.onLobbyDataUpdate(steamIDLobby, steamIDMember, success)
                }
                override fun onLobbyChatUpdate(steamIDLobby: SteamID, steamIDUserChanged: SteamID, steamIDMakingChange: SteamID, stateChange: SteamMatchmaking.ChatMemberStateChange) {
                    matchmakingListener?.onLobbyChatUpdate(steamIDLobby, steamIDUserChanged, steamIDMakingChange, stateChange)
                }
                override fun onLobbyKicked(steamIDLobby: SteamID, steamIDAdmin: SteamID, kickedDueToDisconnect: Boolean) {
                    matchmakingListener?.onLobbyKicked(steamIDLobby, steamIDAdmin, kickedDueToDisconnect)
                }
                override fun onLobbyCreated(result: SteamResult, steamIDLobby: SteamID) {
                    matchmakingListener?.onLobbyCreated(result, steamIDLobby)
                }
            })
            networking = SteamNetworking(object : SteamNetworkingCallback {
                override fun onP2PSessionRequest(steamIDRemote: SteamID) {
                    networkingListener?.onP2PSessionRequest(steamIDRemote)
                }
                override fun onP2PSessionConnectFail(steamIDRemote: SteamID, sessionError: SteamNetworking.P2PSessionError) {
                    networkingListener?.onP2PSessionConnectFail(steamIDRemote, sessionError)
                }
            })
            networking.allowP2PPacketRelay(true)
            running = true
            SteamStore.init()
        } catch (t: Throwable) {
            lastError = t.message ?: t.javaClass.simpleName
            running = false
        }
    }

    /** Runs Steam callbacks and the per-frame tickers for as long as the window lives. */
    suspend fun pumpCallbacks() {
        if (!running) return
        while (true) {
            try {
                SteamAPI.runCallbacks()
                for (t in tickers.toList()) t()
            } catch (_: Throwable) {
            }
            delay(16)
        }
    }

    fun shutdown() {
        if (!running) return
        running = false
        try {
            SteamAPI.shutdown()
        } catch (_: Throwable) {
        }
    }

    val mySteamId: SteamID get() = user.steamID

    fun idOf(id: SteamID): Long = SteamNativeHandle.getNativeHandle(id)
    fun idFrom(raw: Long): SteamID = SteamID.createFromNativeHandle(raw)

    /**
     * steamworks4j 1.10 ships the natives inside its jar but leaves loading to the app:
     * copy steam_api + steamworks4j for this OS into the save folder and load them.
     */
    private fun loadNatives(): Boolean {
        val os = System.getProperty("os.name").lowercase()
        val names = when {
            os.contains("win") -> listOf("steam_api64.dll", "steamworks4j64.dll")
            os.contains("mac") -> listOf("libsteam_api.dylib", "libsteamworks4j.dylib")
            else -> listOf("libsteam_api.so", "libsteamworks4j.so")
        }
        val dir = File(nativesRoot(), "steam-natives-${AppInfo.versionCode}")
        dir.mkdirs()
        val files = names.map { name ->
            val out = File(dir, name)
            if (!out.isFile) {
                val res = SteamService::class.java.classLoader.getResourceAsStream(name) ?: return false
                res.use { Files.copy(it, out.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            }
            out
        }
        steamApiPath = files[0].absolutePath
        return SteamAPI.loadLibraries(object : SteamLibraryLoader {
            override fun loadLibrary(libraryName: String): Boolean {
                val f = files.firstOrNull { it.name.contains(libraryName) } ?: return false
                return try { System.load(f.absolutePath); true } catch (_: Throwable) { false }
            }
        })
    }

    private fun nativesRoot(): File {
        val appData = System.getenv("APPDATA")
        return if (appData != null) File(appData, "CyberOperative")
        else File(System.getProperty("user.home"), ".local/share/CyberOperative")
    }
}
