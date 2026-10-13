package com.cyberoperative.game.data

import com.cyberoperative.game.save.CharacterLook
import kotlin.random.Random

/**
 * Character creator parts (owner, 2026-10-13): HEAD, FACE, BODY and LEGS, three options each.
 * Option A is the class's own look; B and C add pieces on top (an antenna or crest, a different
 * visor face, a chest reactor or plating, knee lights or jet boots). See ClassFigures.accessories.
 */
object Appearance {
    const val OPTIONS = 3
    val parts = listOf("HEAD", "FACE", "BODY", "LEGS")

    private fun short(classId: String) = when (classId) {
        "technician" -> "Tech"
        "juggernaut" -> "Juggernaut"
        "phantom" -> "Phantom"
        "packet_mage" -> "Mage"
        else -> "Agent"
    }

    private fun headNoun(classId: String) = when (classId) {
        "technician" -> "Tech Hood"
        "juggernaut" -> "Juggernaut Helm"
        "phantom" -> "Shadow Hood"
        "packet_mage" -> "Mage Cowl"
        else -> "Tactical Hood"
    }

    private val faces = listOf("Signature Face", "Digital Face", "Hunter Face")
    private val letters = listOf("A", "B", "C")

    fun partName(classId: String, part: Int, option: Int): String {
        val o = option.coerceIn(0, OPTIONS - 1)
        return when (part) {
            0 -> "${headNoun(classId)} ${letters[o]}"
            1 -> faces[o]
            2 -> "${short(classId)} Armor ${letters[o]}"
            else -> "${short(classId)} Legs ${letters[o]}"
        }
    }

    fun option(look: CharacterLook, part: Int): Int = when (part) {
        0 -> look.head
        1 -> look.face
        2 -> look.body
        else -> look.legs
    }

    fun with(look: CharacterLook, part: Int, option: Int): CharacterLook {
        val o = option.coerceIn(0, OPTIONS - 1)
        return when (part) {
            0 -> look.copy(head = o)
            1 -> look.copy(face = o)
            2 -> look.copy(body = o)
            else -> look.copy(legs = o)
        }
    }

    /** "HEAD" to "Juggernaut Helm A", and so on. */
    fun names(classId: String, look: CharacterLook): List<Pair<String, String>> =
        parts.mapIndexed { i, p -> p to partName(classId, i, option(look, i)) }

    fun random(rng: Random = Random.Default): CharacterLook =
        CharacterLook(rng.nextInt(OPTIONS), rng.nextInt(OPTIONS), rng.nextInt(OPTIONS), rng.nextInt(OPTIONS))

    private val namePool = listOf(
        "Bastion", "Relay", "Cipher", "Wraith", "Hex", "Vector", "Nova", "Static", "Kernel", "Proxy",
        "Glitch", "Echo", "Rook", "Vanta", "Pulse", "Byte", "Nyx", "Onyx", "Talon", "Zero"
    )

    fun randomName(rng: Random = Random.Default): String = namePool[rng.nextInt(namePool.size)]
}
