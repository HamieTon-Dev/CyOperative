package com.cyberoperative.game.net

import kotlin.random.Random

/** This device's co-op identity: a callsign and a friend code to share. */
data class CoopProfile(val uid: String, val callsign: String, val code: String)

data class Friend(val uid: String, val callsign: String, val online: Boolean = false)

data class FriendRequest(val id: String, val fromUid: String, val fromName: String)

data class Invite(val id: String, val fromUid: String, val fromName: String, val roomId: String, val createdAt: Long)

/** One side of a co-op lobby: who they are and the progression their operative brings. */
data class RoomPlayer(
    val uid: String = "",
    val name: String = "",
    val permanent: Map<String, Int> = emptyMap(),
    val opLevel: Int = 1,
    val body: String = "agent",
    val skin: String = "default",
    val ready: Boolean = false
)

enum class RoomStatus { LOBBY, PLAYING, ENDED }

data class RoomState(
    val roomId: String,
    val status: RoomStatus = RoomStatus.LOBBY,
    val host: RoomPlayer? = null,
    val guest: RoomPlayer? = null,
    val hostOnline: Boolean = false,
    val guestOnline: Boolean = false,
    /** Set by the host when the run starts. */
    val seed: Long = 0L,
    val difficulty: String = "MEDIUM",
    val mode: String = "CAMPAIGN"
)

/** Friend codes: 8 characters as XXXX-XXXX, no look-alike characters (0/O, 1/I/L). */
object FriendCode {
    const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    fun generate(random: Random = Random.Default): String {
        val raw = buildString { repeat(8) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
        return raw.substring(0, 4) + "-" + raw.substring(4)
    }

    /** Accepts what people type ("abcd efgh", "ABCD-EFGH", "abcdefgh"); null if it can't be a code. */
    fun normalize(input: String): String? {
        val raw = input.uppercase().filter { it.isLetterOrDigit() }
            .replace('O', '0').replace('I', '1').replace('L', '1')
        if (raw.length != 8 || raw.any { it !in ALPHABET }) return null
        return raw.substring(0, 4) + "-" + raw.substring(4)
    }
}

/** Callsigns: 3–16 letters, digits, spaces, '-' or '_', not all spaces. */
object Callsign {
    const val MIN = 3
    const val MAX = 16

    fun clean(input: String): String = input.trim().replace(Regex("\\s+"), " ")

    /** Null when fine, otherwise what is wrong with it. */
    fun problem(input: String): String? {
        val c = clean(input)
        return when {
            c.length < MIN -> "At least $MIN characters"
            c.length > MAX -> "At most $MAX characters"
            !c.all { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' } -> "Letters, digits, space, - and _ only"
            else -> null
        }
    }
}
