package com.cyberoperative.game.net

import android.content.Context
import android.util.Base64
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.tasks.await

/**
 * Co-op on Firebase (owner, 2026-10-08).
 *
 * Firestore (small, durable):
 *   users/{uid}            callsign, code, createdAt
 *   codes/{code}           uid                      (friend code → player)
 *   friendRequests/{from_to}  from, fromName, to, toName, createdAt
 *   friendships/{a_b}      members [a, b], names {a, b}
 *   invites/{id}           from, fromName, to, roomId, status, createdAt
 *
 * Realtime Database (live, cheap per message):
 *   presence/{uid}         true while the app is connected
 *   rooms/{id}/meta        lobby: host, guest, players, online flags, status, seed
 *   rooms/{id}/world       host → guest snapshot (base64, ~10 Hz)
 *   rooms/{id}/input       guest → host input (base64)
 *
 * Accounts are anonymous Firebase accounts tied to this install; the callsign
 * and friend code are what other players see.
 */
class FirebaseCoopBackend private constructor(private val app: FirebaseApp) : CoopBackend {

    override val available = true
    private val auth = FirebaseAuth.getInstance(app)
    private val fs = FirebaseFirestore.getInstance(app)
    private val db = FirebaseDatabase.getInstance(app)
    private val _profile = MutableStateFlow<CoopProfile?>(null)
    override val profile: StateFlow<CoopProfile?> = _profile.asStateFlow()

    private val uid: String get() = auth.currentUser?.uid ?: error("Not signed in")
    private var presenceListener: ValueEventListener? = null

    override suspend fun start(): Result<Unit> = runCatching {
        if (auth.currentUser == null) auth.signInAnonymously().await()
        val me = uid
        val doc = fs.collection("users").document(me).get().await()
        if (doc.exists()) {
            _profile.value = CoopProfile(me, doc.getString("callsign") ?: "OPERATIVE", doc.getString("code") ?: "")
        }
        // Online dot for friends: set on every (re)connect, cleared by the server on disconnect.
        if (presenceListener == null) {
            val mine = db.getReference("presence").child(me)
            presenceListener = db.getReference(".info/connected").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    if (s.getValue(Boolean::class.java) == true) {
                        mine.onDisconnect().removeValue()
                        mine.setValue(true)
                    }
                }
                override fun onCancelled(e: DatabaseError) {}
            })
        }
    }

    override suspend fun register(callsign: String): Result<CoopProfile> = runCatching {
        Callsign.problem(callsign)?.let { error(it) }
        val name = Callsign.clean(callsign)
        if (auth.currentUser == null) start().getOrThrow()
        val me = uid
        val existing = _profile.value
        if (existing != null) {
            fs.collection("users").document(me).update("callsign", name).await()
            return@runCatching existing.copy(callsign = name).also { _profile.value = it }
        }
        // Claim a free friend code (a collision is astronomically rare; retry a few times anyway).
        repeat(6) {
            val code = FriendCode.generate()
            val codeRef = fs.collection("codes").document(code)
            val userRef = fs.collection("users").document(me)
            val claimed = fs.runTransaction { tx ->
                if (tx.get(codeRef).exists()) false
                else {
                    tx.set(codeRef, mapOf("uid" to me))
                    tx.set(userRef, mapOf("callsign" to name, "code" to code, "createdAt" to System.currentTimeMillis()))
                    true
                }
            }.await()
            if (claimed) return@runCatching CoopProfile(me, name, code).also { _profile.value = it }
        }
        error("Couldn't create a friend code, try again")
    }

    private fun pairId(a: String, b: String) = if (a < b) "${a}_$b" else "${b}_$a"

    override fun friends(): Flow<List<Friend>> = callbackFlow {
        val me = uid
        val friends = LinkedHashMap<String, String>()
        val online = HashMap<String, Boolean>()
        val presence = HashMap<String, Pair<DatabaseReference, ValueEventListener>>()
        fun emit() { trySend(friends.map { (id, name) -> Friend(id, name, online[id] == true) }.sortedWith(compareBy({ !it.online }, { it.callsign.lowercase() }))) }
        val reg: ListenerRegistration = fs.collection("friendships").whereArrayContains("members", me)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                friends.clear()
                for (d in snap.documents) {
                    @Suppress("UNCHECKED_CAST")
                    val members = d.get("members") as? List<String> ?: continue
                    val other = members.firstOrNull { it != me } ?: continue
                    @Suppress("UNCHECKED_CAST")
                    val names = d.get("names") as? Map<String, String> ?: emptyMap()
                    friends[other] = names[other] ?: "OPERATIVE"
                }
                // Watch presence of exactly the current friends.
                for (gone in presence.keys - friends.keys) presence.remove(gone)?.let { (r, l) -> r.removeEventListener(l) }
                for (id in friends.keys - presence.keys) {
                    val ref = db.getReference("presence").child(id)
                    val l = object : ValueEventListener {
                        override fun onDataChange(s: DataSnapshot) { online[id] = s.getValue(Boolean::class.java) == true; emit() }
                        override fun onCancelled(e: DatabaseError) {}
                    }
                    ref.addValueEventListener(l)
                    presence[id] = ref to l
                }
                emit()
            }
        awaitClose {
            reg.remove()
            for ((r, l) in presence.values) r.removeEventListener(l)
        }
    }

    override fun friendRequests(): Flow<List<FriendRequest>> = callbackFlow {
        val reg = fs.collection("friendRequests").whereEqualTo("to", uid).addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            trySend(snap.documents.map { FriendRequest(it.id, it.getString("from") ?: "", it.getString("fromName") ?: "OPERATIVE") })
        }
        awaitClose { reg.remove() }
    }

    override suspend fun sendFriendRequest(code: String): Result<String> = runCatching {
        val me = _profile.value ?: error("Register first")
        val c = FriendCode.normalize(code) ?: error("That isn't a friend code (XXXX-XXXX)")
        if (c == me.code) error("That's your own code")
        val target = fs.collection("codes").document(c).get().await().getString("uid") ?: error("No operative has that code")
        if (fs.collection("friendships").document(pairId(me.uid, target)).get().await().exists()) error("Already friends")
        val name = fs.collection("users").document(target).get().await().getString("callsign") ?: "OPERATIVE"
        // They already asked us: just accept theirs.
        val theirs = fs.collection("friendRequests").document("${target}_${me.uid}").get().await()
        if (theirs.exists()) {
            answerFriendRequest(FriendRequest(theirs.id, target, name), true).getOrThrow()
            return@runCatching "You're now friends with $name"
        }
        fs.collection("friendRequests").document("${me.uid}_$target").set(
            mapOf("from" to me.uid, "fromName" to me.callsign, "to" to target, "toName" to name, "createdAt" to System.currentTimeMillis())
        ).await()
        "Request sent to $name"
    }

    override suspend fun answerFriendRequest(request: FriendRequest, accept: Boolean): Result<Unit> = runCatching {
        val me = _profile.value ?: error("Register first")
        val reqRef = fs.collection("friendRequests").document(request.id)
        if (accept) {
            fs.collection("friendships").document(pairId(me.uid, request.fromUid)).set(
                mapOf(
                    "members" to listOf(me.uid, request.fromUid),
                    "names" to mapOf(me.uid to me.callsign, request.fromUid to request.fromName),
                    "createdAt" to System.currentTimeMillis()
                )
            ).await()
        }
        reqRef.delete().await()
    }

    override suspend fun removeFriend(uid: String): Result<Unit> = runCatching {
        fs.collection("friendships").document(pairId(this.uid, uid)).delete().await()
    }

    override fun invites(): Flow<List<Invite>> = callbackFlow {
        val reg = fs.collection("invites").whereEqualTo("to", uid).whereEqualTo("status", "pending")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val fresh = System.currentTimeMillis() - INVITE_TTL_MS
                trySend(snap.documents.mapNotNull {
                    val at = it.getLong("createdAt") ?: 0L
                    if (at < fresh) null
                    else Invite(it.id, it.getString("from") ?: "", it.getString("fromName") ?: "OPERATIVE", it.getString("roomId") ?: return@mapNotNull null, at)
                })
            }
        awaitClose { reg.remove() }
    }

    override suspend fun invite(friend: Friend, me: RoomPlayer): Result<CoopRoom> = runCatching {
        val roomRef = db.getReference("rooms").push()
        val roomId = roomRef.key ?: error("Couldn't open a room")
        roomRef.child("meta").setValue(
            mapOf(
                "host" to me.uid, "guest" to friend.uid, "status" to RoomStatus.LOBBY.name,
                "hostInfo" to me.toMap(), "hostOnline" to true, "guestOnline" to false,
                "createdAt" to System.currentTimeMillis()
            )
        ).await()
        fs.collection("invites").document().set(
            mapOf(
                "from" to me.uid, "fromName" to me.name, "to" to friend.uid, "roomId" to roomId,
                "status" to "pending", "createdAt" to System.currentTimeMillis()
            )
        ).await()
        FirebaseCoopRoom(roomRef, isHost = true)
    }

    override suspend fun answerInvite(invite: Invite, accept: Boolean, me: RoomPlayer): Result<CoopRoom?> = runCatching {
        fs.collection("invites").document(invite.id).update("status", if (accept) "accepted" else "declined").await()
        if (!accept) return@runCatching null
        val roomRef = db.getReference("rooms").child(invite.roomId)
        val meta = roomRef.child("meta").get().await()
        if (!meta.exists() || meta.child("status").getValue(String::class.java) != RoomStatus.LOBBY.name) error("That invite has expired")
        roomRef.child("meta").updateChildren(mapOf("guestInfo" to me.toMap(), "guestOnline" to true)).await()
        FirebaseCoopRoom(roomRef, isHost = false)
    }

    companion object {
        /** Invites older than this are ignored. */
        const val INVITE_TTL_MS = 3 * 60_000L

        /** The Firebase backend if this build has a Firebase project, else the offline stand-in. */
        fun create(context: Context): CoopBackend {
            val app = try {
                FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context)
            } catch (_: Exception) {
                null
            }
            return if (app == null) OfflineCoopBackend() else FirebaseCoopBackend(app)
        }
    }
}

internal fun RoomPlayer.toMap(): Map<String, Any> = mapOf(
    "uid" to uid, "name" to name, "permanent" to permanent, "opLevel" to opLevel,
    "body" to body, "skin" to skin, "ready" to ready
)

internal fun DataSnapshot.toRoomPlayer(): RoomPlayer? {
    if (!exists()) return null
    val perm = HashMap<String, Int>()
    for (c in child("permanent").children) perm[c.key ?: continue] = (c.getValue(Long::class.java) ?: 0L).toInt()
    return RoomPlayer(
        uid = child("uid").getValue(String::class.java) ?: return null,
        name = child("name").getValue(String::class.java) ?: "OPERATIVE",
        permanent = perm,
        opLevel = (child("opLevel").getValue(Long::class.java) ?: 1L).toInt(),
        body = child("body").getValue(String::class.java) ?: "agent",
        skin = child("skin").getValue(String::class.java) ?: "default",
        ready = child("ready").getValue(Boolean::class.java) == true
    )
}

private class FirebaseCoopRoom(private val ref: DatabaseReference, override val isHost: Boolean) : CoopRoom {
    override val roomId: String = ref.key ?: ""
    private val meta = ref.child("meta")
    private val myOnline = meta.child(if (isHost) "hostOnline" else "guestOnline")

    init {
        // The partner sees us drop the moment the connection does.
        myOnline.onDisconnect().setValue(false)
        myOnline.setValue(true)
    }

    override val state: Flow<RoomState> = callbackFlow {
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                if (!s.exists()) {
                    trySend(RoomState(roomId, RoomStatus.ENDED))
                    return
                }
                trySend(
                    RoomState(
                        roomId = roomId,
                        status = RoomStatus.entries.firstOrNull { it.name == s.child("status").getValue(String::class.java) } ?: RoomStatus.LOBBY,
                        host = s.child("hostInfo").toRoomPlayer(),
                        guest = s.child("guestInfo").toRoomPlayer(),
                        hostOnline = s.child("hostOnline").getValue(Boolean::class.java) == true,
                        guestOnline = s.child("guestOnline").getValue(Boolean::class.java) == true,
                        seed = s.child("seed").getValue(Long::class.java) ?: 0L,
                        difficulty = s.child("difficulty").getValue(String::class.java) ?: "MEDIUM",
                        mode = s.child("mode").getValue(String::class.java) ?: "CAMPAIGN"
                    )
                )
            }
            override fun onCancelled(e: DatabaseError) { close(e.toException()) }
        }
        meta.addValueEventListener(l)
        awaitClose { meta.removeEventListener(l) }
    }

    override suspend fun setReady(me: RoomPlayer): Result<Unit> = runCatching {
        meta.child(if (isHost) "hostInfo" else "guestInfo").setValue(me.toMap()).await()
    }

    override suspend fun start(seed: Long, difficulty: String, mode: String): Result<Unit> = runCatching {
        meta.updateChildren(mapOf("status" to RoomStatus.PLAYING.name, "seed" to seed, "difficulty" to difficulty, "mode" to mode)).await()
    }

    /** One write in flight at a time; the newest waiting packet replaces older ones. */
    private class Sender(private val target: DatabaseReference) {
        private var busy = false
        private var waiting: String? = null
        @Synchronized fun send(bytes: ByteArray) {
            val s = Base64.encodeToString(bytes, Base64.NO_WRAP)
            if (busy) { waiting = s; return }
            write(s)
        }
        private fun write(s: String) {
            busy = true
            target.setValue(s).addOnCompleteListener {
                synchronized(this) {
                    busy = false
                    val next = waiting
                    waiting = null
                    if (next != null) write(next)
                }
            }
        }
    }

    private val worldSender = Sender(ref.child("world"))
    private val inputSender = Sender(ref.child("input"))
    override fun sendWorld(bytes: ByteArray) = worldSender.send(bytes)
    override fun sendInput(bytes: ByteArray) = inputSender.send(bytes)

    private fun blobs(node: DatabaseReference): Flow<ByteArray> = callbackFlow {
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val v = s.getValue(String::class.java) ?: return
                try { trySend(Base64.decode(v, Base64.NO_WRAP)) } catch (_: IllegalArgumentException) {}
            }
            override fun onCancelled(e: DatabaseError) { close(e.toException()) }
        }
        node.addValueEventListener(l)
        awaitClose { node.removeEventListener(l) }
    }.conflate()

    override val worlds: Flow<ByteArray> = blobs(ref.child("world"))
    override val inputs: Flow<ByteArray> = blobs(ref.child("input"))

    override suspend fun end() {
        runCatching { meta.child("status").setValue(RoomStatus.ENDED.name).await() }
    }

    override suspend fun leave() {
        runCatching {
            myOnline.onDisconnect().cancel()
            myOnline.setValue(false).await()
            // The host tidies up the room (it is useless once either side has left).
            if (isHost) ref.removeValue().await()
        }
    }
}
