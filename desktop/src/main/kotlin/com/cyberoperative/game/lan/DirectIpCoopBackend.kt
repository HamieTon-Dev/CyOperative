package com.cyberoperative.game.lan

import com.cyberoperative.game.net.CoopBackend
import com.cyberoperative.game.net.CoopProfile
import com.cyberoperative.game.net.CoopRoom
import com.cyberoperative.game.net.Friend
import com.cyberoperative.game.net.FriendRequest
import com.cyberoperative.game.net.Invite
import com.cyberoperative.game.net.RoomPlayer
import com.cyberoperative.game.net.RoomState
import com.cyberoperative.game.net.RoomStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicReference

/**
 * Co-op over a direct connection (PC build without Steam, owner 2026-10-11: "send my friend
 * an .exe and let them join via IP address"). One player HOSTs on [PORT]; the other JOINs by
 * typing the host's address. Plain TCP, no accounts or servers. Same [CoopRoom] contract as
 * Steam and Firebase: the host runs the simulation and sends snapshots, the guest sends input.
 *
 * Over the internet the host must forward TCP [PORT] on their router, or both players join a
 * virtual LAN (Tailscale, ZeroTier, Radmin VPN…). On the same network it just works.
 */
class DirectIpCoopBackend : CoopBackend {
    override val available = true
    override val platformFriends = true
    override val platformName = "Direct IP"
    override val directIp = true

    private val profileFlow = MutableStateFlow<CoopProfile?>(
        CoopProfile("ip-" + java.util.UUID.randomUUID().toString().take(8), defaultName(), "")
    )
    override val profile: StateFlow<CoopProfile?> = profileFlow

    override suspend fun start(): Result<Unit> = Result.success(Unit)

    /** Direct IP has no accounts: "register" just sets the name the partner sees. */
    override suspend fun register(callsign: String): Result<CoopProfile> {
        val p = profileFlow.value!!.copy(callsign = callsign.trim().take(16).ifEmpty { "OPERATIVE" })
        profileFlow.value = p
        return Result.success(p)
    }

    override fun friends(): Flow<List<Friend>> = flowOf(emptyList())
    override fun friendRequests(): Flow<List<FriendRequest>> = flowOf(emptyList())
    override suspend fun sendFriendRequest(code: String) = Result.failure<String>(UnsupportedOperationException("Direct IP has no friend list"))
    override suspend fun answerFriendRequest(request: FriendRequest, accept: Boolean) = Result.success(Unit)
    override suspend fun removeFriend(uid: String) = Result.success(Unit)
    override fun invites(): Flow<List<Invite>> = flowOf(emptyList())
    override suspend fun invite(friend: Friend, me: RoomPlayer) = Result.failure<CoopRoom>(UnsupportedOperationException("Use HOST"))
    override suspend fun answerInvite(invite: Invite, accept: Boolean, me: RoomPlayer) = Result.success<CoopRoom?>(null)

    override fun localAddresses(): List<String> = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .map { it.hostAddress }
            .sortedBy { if (it.startsWith("192.168.") || it.startsWith("10.")) 0 else 1 }
    } catch (_: Exception) {
        emptyList()
    }

    override val directPort: Int get() = PORT

    private var room: DirectRoom? = null

    override suspend fun hostDirect(me: RoomPlayer): Result<CoopRoom> = withContext(Dispatchers.IO) {
        runCatching {
            room?.close()
            val server = ServerSocket()
            server.reuseAddress = true
            server.bind(InetSocketAddress(PORT))
            DirectRoom(isHost = true, me = me, server = server, socket = null).also { room = it; it.acceptInBackground() }
        }.recoverCatching { e ->
            throw IllegalStateException("Can't host on port $PORT (${e.message ?: "in use"}). Close other copies of the game and try again.")
        }
    }

    override suspend fun joinDirect(address: String, me: RoomPlayer): Result<CoopRoom> = withContext(Dispatchers.IO) {
        runCatching {
            room?.close()
            val (host, port) = parseAddress(address)
            val s = Socket()
            s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            DirectRoom(isHost = false, me = me, server = null, socket = s).also { room = it; it.startConnection(s) }
        }.recoverCatching { e ->
            throw IllegalStateException("Couldn't reach $address (${e.message ?: "no answer"}). Check the address, and that the host pressed HOST.")
        }
    }

    /**
     * One direct link. Messages are framed as: type byte, length int, payload. A writer
     * thread sends (a newer world snapshot replaces one not yet sent); a reader thread receives.
     */
    private inner class DirectRoom(
        override val isHost: Boolean,
        private val me: RoomPlayer,
        private val server: ServerSocket?,
        @Volatile private var socket: Socket?
    ) : CoopRoom {
        override val roomId: String = "direct"
        private val stateFlow = MutableStateFlow(
            if (isHost) RoomState(roomId, host = me, hostOnline = true) else RoomState(roomId, guest = me, guestOnline = true)
        )
        override val state: Flow<RoomState> = stateFlow
        private val worldFlow = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
        private val inputFlow = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
        override val worlds: Flow<ByteArray> = worldFlow
        override val inputs: Flow<ByteArray> = inputFlow

        private val queue = LinkedBlockingQueue<Pair<Int, ByteArray>>()
        private val latestWorld = AtomicReference<ByteArray?>(null)
        private val wake = Object()
        @Volatile private var closed = false

        fun acceptInBackground() {
            Thread({
                try {
                    val s = server!!.accept()
                    if (closed) { s.close(); return@Thread }
                    socket = s
                    startConnection(s)
                } catch (_: Exception) {
                }
            }, "coop-accept").apply { isDaemon = true }.start()
        }

        fun startConnection(s: Socket) {
            s.tcpNoDelay = true
            s.keepAlive = true
            val out = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
            val inp = DataInputStream(BufferedInputStream(s.getInputStream()))
            Thread({ writeLoop(out) }, "coop-write").apply { isDaemon = true }.start()
            Thread({ readLoop(inp) }, "coop-read").apply { isDaemon = true }.start()
            enqueue(MSG_HELLO, encode(me).toByteArray())
        }

        private fun enqueue(type: Int, bytes: ByteArray) {
            if (closed) return
            queue.add(type to bytes)
            synchronized(wake) { wake.notifyAll() }
        }

        private fun writeLoop(out: DataOutputStream) {
            try {
                while (!closed) {
                    var msg = queue.poll()
                    val world = if (msg == null) latestWorld.getAndSet(null) else null
                    if (msg == null && world != null) msg = MSG_WORLD to world
                    if (msg == null) {
                        synchronized(wake) { if (queue.isEmpty() && latestWorld.get() == null) wake.wait(50) }
                        continue
                    }
                    out.writeByte(msg.first)
                    out.writeInt(msg.second.size)
                    out.write(msg.second)
                    if (queue.isEmpty()) out.flush()
                }
            } catch (_: Exception) {
                partnerLost()
            }
        }

        private fun readLoop(inp: DataInputStream) {
            try {
                while (!closed) {
                    val type = inp.readUnsignedByte()
                    val len = inp.readInt()
                    if (len < 0 || len > MAX_MESSAGE) throw IllegalStateException("bad message")
                    val bytes = ByteArray(len)
                    inp.readFully(bytes)
                    when (type) {
                        MSG_HELLO -> {
                            val partner = decode(String(bytes)) ?: continue
                            stateFlow.value = if (isHost) stateFlow.value.copy(guest = partner, guestOnline = true)
                            else stateFlow.value.copy(host = partner, hostOnline = true)
                        }
                        MSG_START -> {
                            val d = DataInputStream(bytes.inputStream())
                            stateFlow.value = stateFlow.value.copy(status = RoomStatus.PLAYING, seed = d.readLong(), difficulty = d.readUTF(), mode = d.readUTF())
                        }
                        MSG_WORLD -> worldFlow.tryEmit(bytes)
                        MSG_INPUT -> inputFlow.tryEmit(bytes)
                        MSG_END -> stateFlow.value = stateFlow.value.copy(status = RoomStatus.ENDED)
                        MSG_LEAVE -> { partnerLost(); return }
                    }
                }
            } catch (_: Exception) {
                partnerLost()
            }
        }

        private fun partnerLost() {
            if (closed) return
            stateFlow.value = if (isHost) stateFlow.value.copy(guestOnline = false) else stateFlow.value.copy(hostOnline = false)
        }

        override suspend fun setReady(me: RoomPlayer): Result<Unit> {
            enqueue(MSG_HELLO, encode(me).toByteArray())
            stateFlow.value = if (isHost) stateFlow.value.copy(host = me) else stateFlow.value.copy(guest = me)
            return Result.success(Unit)
        }

        override suspend fun start(seed: Long, difficulty: String, mode: String): Result<Unit> {
            val b = java.io.ByteArrayOutputStream()
            DataOutputStream(b).use { it.writeLong(seed); it.writeUTF(difficulty); it.writeUTF(mode) }
            enqueue(MSG_START, b.toByteArray())
            stateFlow.value = stateFlow.value.copy(status = RoomStatus.PLAYING, seed = seed, difficulty = difficulty, mode = mode)
            return Result.success(Unit)
        }

        override fun sendWorld(bytes: ByteArray) {
            if (closed) return
            // Only the newest snapshot matters: replace one still waiting.
            latestWorld.set(bytes)
            synchronized(wake) { wake.notifyAll() }
        }

        override fun sendInput(bytes: ByteArray) = enqueue(MSG_INPUT, bytes)

        override suspend fun end() {
            if (isHost) enqueue(MSG_END, ByteArray(0))
            stateFlow.value = stateFlow.value.copy(status = RoomStatus.ENDED)
        }

        override suspend fun leave() {
            enqueue(MSG_LEAVE, ByteArray(0))
            withContext(Dispatchers.IO) { Thread.sleep(100) }
            close()
            if (room === this) room = null
        }

        fun close() {
            if (closed) return
            closed = true
            synchronized(wake) { wake.notifyAll() }
            runCatching { socket?.close() }
            runCatching { server?.close() }
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
        /** TCP port the host listens on (forward this one on the router for internet play). */
        const val PORT = 47615
        private const val CONNECT_TIMEOUT_MS = 8000
        private const val MAX_MESSAGE = 4 * 1024 * 1024
        private const val MSG_HELLO = 1
        private const val MSG_START = 2
        private const val MSG_WORLD = 3
        private const val MSG_INPUT = 4
        private const val MSG_END = 5
        private const val MSG_LEAVE = 6

        /** "1.2.3.4", "1.2.3.4:5000", "my-pc.local" → host and port. */
        fun parseAddress(input: String): Pair<String, Int> {
            val t = input.trim()
            val colon = t.lastIndexOf(':')
            if (colon > 0 && t.count { it == ':' } == 1) {
                val port = t.substring(colon + 1).toIntOrNull()
                if (port != null && port in 1..65535) return t.substring(0, colon) to port
            }
            return t to PORT
        }

        private fun defaultName(): String =
            (System.getProperty("user.name") ?: "").filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }
                .take(16).ifEmpty { "OPERATIVE" }.uppercase()
    }
}
