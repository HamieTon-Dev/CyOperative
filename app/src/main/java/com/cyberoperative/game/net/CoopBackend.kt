package com.cyberoperative.game.net

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Everything co-op needs from a server: an account, friends, invites and a
 * live room. [FirebaseCoopBackend] is the real one; [OfflineCoopBackend] is
 * used when this build has no Firebase project (no google-services.json), so
 * the rest of the game is never affected.
 */
interface CoopBackend {
    /** False when co-op isn't set up in this build. */
    val available: Boolean
    /** The registered profile, or null before registration. */
    val profile: StateFlow<CoopProfile?>
    /** Signs in (anonymous account on this device) and loads the profile if there is one. */
    suspend fun start(): Result<Unit>
    suspend fun register(callsign: String): Result<CoopProfile>

    fun friends(): Flow<List<Friend>>
    fun friendRequests(): Flow<List<FriendRequest>>
    suspend fun sendFriendRequest(code: String): Result<String>
    suspend fun answerFriendRequest(request: FriendRequest, accept: Boolean): Result<Unit>
    suspend fun removeFriend(uid: String): Result<Unit>

    fun invites(): Flow<List<Invite>>
    /** Opens a room and invites [friend]; returns the room. */
    suspend fun invite(friend: Friend, me: RoomPlayer): Result<CoopRoom>
    /** Joins the invite's room (accept) or declines it. */
    suspend fun answerInvite(invite: Invite, accept: Boolean, me: RoomPlayer): Result<CoopRoom?>
}

/**
 * A live co-op room. The host writes world snapshots, the guest writes its
 * input; both see the lobby state and whether the other is still connected.
 */
interface CoopRoom {
    val roomId: String
    val isHost: Boolean
    val state: Flow<RoomState>
    suspend fun setReady(me: RoomPlayer): Result<Unit>
    /** Host: start the run for both. */
    suspend fun start(seed: Long, difficulty: String, mode: String): Result<Unit>
    /** Host → guest snapshot (fire and forget; a newer one replaces an unsent older one). */
    fun sendWorld(bytes: ByteArray)
    val worlds: Flow<ByteArray>
    /** Guest → host input. */
    fun sendInput(bytes: ByteArray)
    val inputs: Flow<ByteArray>
    /** Host: the run is over; [summary] lets the guest show the same numbers. */
    suspend fun end()
    suspend fun leave()
}

class OfflineCoopBackend : CoopBackend {
    override val available = false
    override val profile: StateFlow<CoopProfile?> = MutableStateFlow(null)
    private val notSetUp = IllegalStateException("Co-op isn't set up in this build")
    override suspend fun start() = Result.failure<Unit>(notSetUp)
    override suspend fun register(callsign: String) = Result.failure<CoopProfile>(notSetUp)
    override fun friends(): Flow<List<Friend>> = flowOf(emptyList())
    override fun friendRequests(): Flow<List<FriendRequest>> = flowOf(emptyList())
    override suspend fun sendFriendRequest(code: String) = Result.failure<String>(notSetUp)
    override suspend fun answerFriendRequest(request: FriendRequest, accept: Boolean) = Result.failure<Unit>(notSetUp)
    override suspend fun removeFriend(uid: String) = Result.failure<Unit>(notSetUp)
    override fun invites(): Flow<List<Invite>> = flowOf(emptyList())
    override suspend fun invite(friend: Friend, me: RoomPlayer) = Result.failure<CoopRoom>(notSetUp)
    override suspend fun answerInvite(invite: Invite, accept: Boolean, me: RoomPlayer) = Result.failure<CoopRoom?>(notSetUp)
}
