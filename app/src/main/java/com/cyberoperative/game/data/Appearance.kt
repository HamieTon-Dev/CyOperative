package com.cyberoperative.game.data

import com.cyberoperative.game.i18n.tr
import com.cyberoperative.game.save.CharacterLook
import kotlin.random.Random

/**
 * Character creator parts (owner, 2026-10-13): HEAD, FACE, BODY and LEGS, three options each.
 * Option A is the class's own look; B and C add pieces on top (an antenna or crest, a different
 * visor face, a chest reactor or plating, knee lights or jet boots). See ClassFigures.accessories.
 */
object Appearance {
    const val OPTIONS = 3
    val parts = listOf(tr("HEAD"), tr("FACE"), tr("BODY"), tr("LEGS"))

    private fun short(classId: String) = when (classId) {
        "technician" -> "Tech"
        "juggernaut" -> "Juggernaut"
        "phantom" -> "Phantom"
        "packet_mage" -> "Mage"
        else -> "Agent"
    }

    private fun headNoun(classId: String) = when (classId) {
        "technician" -> tr("Tech Hood")
        "juggernaut" -> tr("Juggernaut Helm")
        "phantom" -> tr("Shadow Hood")
        "packet_mage" -> tr("Mage Cowl")
        else -> tr("Tactical Hood")
    }

    private val faces = listOf(tr("Signature Face"), tr("Digital Face"), tr("Hunter Face"))
    private val letters = listOf("A", "B", "C")

    fun partName(classId: String, part: Int, option: Int): String {
        val o = option.coerceIn(0, OPTIONS - 1)
        return when (part) {
            0 -> "${headNoun(classId)} ${letters[o]}"
            1 -> faces[o]
            2 -> tr("{0} Armor {1}", short(classId), letters[o])
            else -> tr("{0} Legs {1}", short(classId), letters[o])
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
