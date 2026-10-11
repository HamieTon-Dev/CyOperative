package com.cyberoperative.game.steam

import com.codedisaster.steamworks.SteamFriends
import com.codedisaster.steamworks.SteamFriendsCallback
import com.codedisaster.steamworks.SteamID
import com.codedisaster.steamworks.SteamMatchmaking
import com.codedisaster.steamworks.SteamMatchmakingCallback
import com.codedisaster.steamworks.SteamNetworking
import com.codedisaster.steamworks.SteamNetworkingCallback
import com.codedisaster.steamworks.SteamResult
import com.cyberoperative.game.net.CoopBackend
import com.cyberoperative.game.net.CoopProfile
import com.cyberoperative.game.net.CoopRoom
import com.cyberoperative.game.net.Friend
import com.cyberoperative.game.net.FriendRequest
import com.cyberoperative.game.net.Invite
import com.cyberoperative.game.net.RoomPlayer
import com.cyberoperative.game.net.RoomState
import com.cyberoperative.game.net.RoomStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.nio.ByteBuffer

/**
 * Co-op over Steam (PC build): your Steam name is your callsign, your Steam friends are the
 * friends list, invites are Steam lobby invites (also sent from the overlay, Shift+Tab), and
 * the game traffic goes peer to peer through Steam's relay network. Same [CoopRoom] contract
 * as the phone build: the host runs the simulation and sends snapshots, the guest sends input.
 *
 * Lobby data: status / seed / difficulty / mode (host-written). Member data: "player", the
 * member's [RoomPlayer] as JSON.
 */
class SteamCoopBackend(private val steam: SteamService) : CoopBackend {
    override val available = true
    override val platformFriends = true
    override val platformName = "Steam"

    private val profileFlow = MutableStateFlow<CoopProfile?>(null)
    override val profile: StateFlow<CoopProfile?> = profileFlow

    private val inviteList = MutableStateFlow<List<Invite>>(emptyList())
    private var room: SteamRoom? = null

    // Pending lobby operations (one at a time).
    private var created: CompletableDeferred<SteamID?>? = null
    private var entered: CompletableDeferred<SteamID?>? = null

    init {
        steam.matchmakingListener = object : SteamMatchmakingCallback {
            override fun onLobbyCreated(result: SteamResult, steamIDLobby: SteamID) {
                created?.complete(if (result == SteamResult.OK) steamIDLobby else null)
            }
            override fun onLobbyEnter(steamIDLobby: SteamID, chatPermissions: Int, blocked: Boolean, response: SteamMatchmaking.ChatRoomEnterResponse) {
                entered?.complete(if (response == SteamMatchmaking.ChatRoomEnterResponse.Success) steamIDLobby else null)
                room?.refresh()
            }
            override fun onLobbyInvite(steamIDUser: SteamID, steamIDLobby: SteamID, gameID: Long) = addInvite(steamIDLobby, steamIDUser)
            override fun onLobbyDataUpdate(steamIDLobby: SteamID, steamIDMember: SteamID, success: Boolean) { room?.refresh() }
            override fun onLobbyChatUpdate(steamIDLobby: SteamID, steamIDUserChanged: SteamID, steamIDMakingChange: SteamID, stateChange: SteamMatchmaking.ChatMemberStateChange) { room?.refresh() }
            override fun onLobbyKicked(steamIDLobby: SteamID, steamIDAdmin: SteamID, kickedDueToDisconnect: Boolean) { room?.refresh() }
        }
        steam.friendsListener = object : SteamFriendsCallback {
            // "Join game" from the Steam friends list / an accepted overlay invite.
            override fun onGameLobbyJoinRequested(steamIDLobby: SteamID, steamIDFriend: SteamID) = addInvite(steamIDLobby, steamIDFriend)
        }
        steam.networkingListener = object : SteamNetworkingCallback {
            override fun onP2PSessionRequest(steamIDRemote: SteamID) {
                // Only the player in our lobby may open a session with us.
                if (room?.isMember(steamIDRemote) == true) steam.networking.acceptP2PSessionWithUser(steamIDRemote)
            }
        }
        steam.tickers += { room?.pump() }
        if (steam.launchLobby != 0L) addInvite(steam.idFrom(steam.launchLobby), null)
    }

    private fun addInvite(lobby: SteamID, from: SteamID?) {
        val id = steam.idOf(lobby).toString()
        if (inviteList.value.any { it.roomId == id } || room?.roomId == id) return
        val name = from?.let { steam.friends.getFriendPersonaName(it) } ?: "A friend"
        inviteList.value = inviteList.value + Invite(id, from?.let { steam.idOf(it).toString() } ?: "", name, id, System.currentTimeMillis())
    }

    override suspend fun start(): Result<Unit> {
        val me = steam.mySteamId
        profileFlow.value = CoopProfile(steam.idOf(me).toString(), steam.friends.personaName, "STEAM")
        return Result.success(Unit)
    }

    override suspend fun register(callsign: String): Result<CoopProfile> =
        profileFlow.value?.let { Result.success(it) } ?: Result.failure(IllegalStateException("Steam isn't signed in"))

    override fun friends(): Flow<List<Friend>> = flow {
        while (true) {
            emit(readFriends())
            delay(4_000)
        }
    }

    private fun readFriends(): List<Friend> {
        val f = steam.friends
        val n = f.getFriendCount(SteamFriends.FriendFlags.Immediate)
        return (0 until n).mapNotNull { i ->
            val id = f.getFriendByIndex(i, SteamFriends.FriendFlags.Immediate) ?: return@mapNotNull null
            val online = f.getFriendPersonaState(id) != SteamFriends.PersonaState.Offline
            Friend(steam.idOf(id).toString(), f.getFriendPersonaName(id), online)
        }.sortedWith(compareByDescending<Friend> { it.online }.thenBy { it.callsign.lowercase() })
    }

    override fun friendRequests(): Flow<List<FriendRequest>> = MutableStateFlow(emptyList())
    override suspend fun sendFriendRequest(code: String) = Result.failure<String>(IllegalStateException("Add friends through Steam"))
    override suspend fun answerFriendRequest(request: FriendRequest, accept: Boolean) = Result.success(Unit)
    override suspend fun removeFriend(uid: String) = Result.success(Unit)

    override fun invites(): Flow<List<Invite>> = inviteList

    override suspend fun invite(friend: Friend, me: RoomPlayer): Result<CoopRoom> {
        room?.leave()
        val c = CompletableDeferred<SteamID?>().also { created = it }
        val e = CompletableDeferred<SteamID?>().also { entered = it }
        steam.matchmaking.createLobby(SteamMatchmaking.LobbyType.FriendsOnly, 2)
        val lobby = withTimeoutOrNull(15_000) { c.await() } ?: return Result.failure(IllegalStateException("Steam couldn't create a lobby"))
        withTimeoutOrNull(10_000) { e.await() }
        val r = SteamRoom(lobby, isHost = true)
        room = r
        steam.matchmaking.setLobbyData(lobby, "status", RoomStatus.LOBBY.name)
        steam.matchmaking.setLobbyData(lobby, "game", "cyberoperative")
        steam.matchmaking.setLobbyMemberData(lobby, "player", encode(me))
        steam.matchmaking.inviteUserToLobby(lobby, steam.idFrom(friend.uid.toLong()))
        r.refresh()
        return Result.success(r)
    }

    override suspend fun answerInvite(invite: Invite, accept: Boolean, me: RoomPlayer): Result<CoopRoom?> {
        inviteList.value = inviteList.value.filterNot { it.id == invite.id }
        if (!accept) return Result.success(null)
        room?.leave()
        val lobbyId = steam.idFrom(invite.roomId.toLong())
        val e = CompletableDeferred<SteamID?>().also { entered = it }
        steam.matchmaking.joinLobby(lobbyId)
        val lobby = withTimeoutOrNull(15_000) { e.await() } ?: return Result.failure(IllegalStateException("That lobby is closed"))
        val r = SteamRoom(lobby, isHost = false)
        room = r
        steam.matchmaking.setLobbyMemberData(lobby, "player", encode(me))
        r.refresh()
        return Result.success(r)
    }

    private inner class SteamRoom(val lobby: SteamID, override val isHost: Boolean) : CoopRoom {
        override val roomId: String = steam.idOf(lobby).toString()
        private val stateFlow = MutableStateFlow(RoomState(roomId))
        override val state: Flow<RoomState> = stateFlow
        private val worldFlow = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
        private val inputFlow = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
        override val worlds: Flow<ByteArray> = worldFlow
        override val inputs: Flow<ByteArray> = inputFlow
        private var left = false
        private val sendBuf = ByteBuffer.allocateDirect(MAX_PACKET)
        private val readBuf = ByteBuffer.allocateDirect(MAX_PACKET)
        private val size = IntArray(1)
        private val sender = com.codedisaster.steamworks.SteamID()

        fun isMember(id: SteamID): Boolean = !left && members().any { steam.idOf(it) == steam.idOf(id) }

        private fun members(): List<SteamID> {
            val mm = steam.matchmaking
            return (0 until mm.getNumLobbyMembers(lobby)).mapNotNull { mm.getLobbyMemberByIndex(lobby, it) }
        }

        private fun partner(): SteamID? {
            val me = steam.idOf(steam.mySteamId)
            return members().firstOrNull { steam.idOf(it) != me }
        }

        fun refresh() {
            if (left) return
            val mm = steam.matchmaking
            val owner = mm.getLobbyOwner(lobby)
            val ms = members()
            val ownerId = owner?.let { steam.idOf(it) }
            val hostId = ms.firstOrNull { steam.idOf(it) == ownerId }
            val guestId = ms.firstOrNull { steam.idOf(it) != ownerId }
            stateFlow.value = RoomState(
                roomId = roomId,
                status = runCatching { RoomStatus.valueOf(mm.getLobbyData(lobby, "status")) }.getOrDefault(RoomStatus.LOBBY),
                host = hostId?.let { decode(mm.getLobbyMemberData(lobby, it, "player")) },
                guest = guestId?.let { decode(mm.getLobbyMemberData(lobby, it, "player")) },
                hostOnline = hostId != null,
                guestOnline = guestId != null,
                seed = mm.getLobbyData(lobby, "seed").toLongOrNull() ?: 0L,
                difficulty = mm.getLobbyData(lobby, "difficulty").ifEmpty { "MEDIUM" },
                mode = mm.getLobbyData(lobby, "mode").ifEmpty { "CAMPAIGN" }
            )
        }

        override suspend fun setReady(me: RoomPlayer): Result<Unit> {
            steam.matchmaking.setLobbyMemberData(lobby, "player", encode(me))
            refresh()
            return Result.success(Unit)
        }

        override suspend fun start(seed: Long, difficulty: String, mode: String): Result<Unit> {
            val mm = steam.matchmaking
            mm.setLobbyData(lobby, "seed", seed.toString())
            mm.setLobbyData(lobby, "difficulty", difficulty)
            mm.setLobbyData(lobby, "mode", mode)
            mm.setLobbyData(lobby, "status", RoomStatus.PLAYING.name)
            mm.setLobbyJoinable(lobby, false)
            refresh()
            return Result.success(Unit)
        }

        override fun sendWorld(bytes: ByteArray) = send(CHANNEL_WORLD, bytes)
        override fun sendInput(bytes: ByteArray) = send(CHANNEL_INPUT, bytes)

        private fun send(channel: Int, bytes: ByteArray) {
            if (left || bytes.size > MAX_PACKET) return
            val to = partner() ?: return
            try {
                sendBuf.clear()
                sendBuf.put(bytes)
                sendBuf.flip()
                steam.networking.sendP2PPacket(to, sendBuf, SteamNetworking.P2PSend.Reliable, channel)
            } catch (_: Throwable) {
            }
        }

        /** Drains incoming packets (called every frame on the UI thread). */
        fun pump() {
            if (left) return
            for (channel in intArrayOf(CHANNEL_WORLD, CHANNEL_INPUT)) {
                var guard = 0
                while (guard++ < 64 && steam.networking.isP2PPacketAvailable(channel, size)) {
                    try {
                        readBuf.clear()
                        val n = steam.networking.readP2PPacket(sender, readBuf, channel)
                        if (n <= 0) break
                        if (!isMember(sender)) continue
                        val bytes = ByteArray(n)
                        readBuf.position(0)
                        readBuf.get(bytes, 0, n)
                        if (channel == CHANNEL_WORLD) worldFlow.tryEmit(bytes) else inputFlow.tryEmit(bytes)
                    } catch (_: Throwable) {
                        break
                    }
                }
            }
        }

        override suspend fun end() {
            if (isHost) steam.matchmaking.setLobbyData(lobby, "status", RoomStatus.ENDED.name)
        }

        override suspend fun leave() {
            if (left) return
            partner()?.let { steam.networking.closeP2PSessionWithUser(it) }
            left = true
            steam.matchmaking.leaveLobby(lobby)
            if (room === this) room = null
        }
    }

    private fun encode(p: RoomPlayer): String = buildJsonObject {
        put("uid", p.uid); put("name", p.name); put("opLevel", p.opLevel)
        put("body", p.body); put("skin", p.skin); put("ready", p.ready)
        putJsonObject("permanent") { for ((k, v) in p.permanent) put(k, v) }
    }.toString()

    private fun decode(s: String?): RoomPlayer? {
        if (s.isNullOrEmpty()) return null
        return try {
            val o = Json.parseToJsonElement(s).jsonObject
            RoomPlayer(
                uid = o["uid"]?.jsonPrimitive?.content ?: "",
                name = o["name"]?.jsonPrimitive?.content ?: "",
                permanent = o["permanent"]?.jsonObject?.mapValues { it.value.jsonPrimitive.int } ?: emptyMap(),
                opLevel = o["opLevel"]?.jsonPrimitive?.int ?: 1,
                body = o["body"]?.jsonPrimitive?.content ?: "agent",
                skin = o["skin"]?.jsonPrimitive?.content ?: "default",
                ready = o["ready"]?.jsonPrimitive?.content == "true"
            )
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val CHANNEL_WORLD = 0
        private const val CHANNEL_INPUT = 1
        /** Reliable P2P messages may be up to 1 MB; snapshots are a few KB. */
        private const val MAX_PACKET = 512 * 1024
    }
}
