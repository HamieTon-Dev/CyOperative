package com.cyberoperative.game

import com.cyberoperative.game.data.Gear
import com.cyberoperative.game.data.GearRarity
import com.cyberoperative.game.data.GearSlot
import com.cyberoperative.game.data.Pets
import com.cyberoperative.game.engine.Difficulty
import com.cyberoperative.game.idle.IdleRunSimulator
import com.cyberoperative.game.idle.IdleRuns
import com.cyberoperative.game.save.PlayerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** 0.49.1: 300 more pets (100 GLITCHED, 100 GLITCHED ENCRYPTED, 100 ENCRYPTED) and Idle Run eggs. */
class V049PetsAndIdleEggsTest {
    private fun tier(r: GearRarity) = (0 until Pets.NEW_COUNT).map { Pets.NEW_BASE + it }.filter { Pets.designedTier(it) == r }

    @Test fun threeHundredNewPetsEachWithItsOwnEgg() {
        assertEquals(1350, Pets.NEW_COUNT)
        assertEquals(190, tier(GearRarity.GLITCHED).size)
        assertEquals(150, tier(GearRarity.GLITCHED_ENCRYPTED).size)
        assertEquals(670, tier(GearRarity.ENCRYPTED).size)
        // The 100 new GLITCHED flicker (magenta GLITCH part); every new one animates.
        val newOnes = (1050 until 1350).map { Pets.NEW_BASE + it }
        assertTrue(newOnes.all { Pets.anim(it) != null })
        assertTrue(newOnes.take(100).all { a -> Pets.anim(a)!!.parts.any { it.kind == com.cyberoperative.game.data.PetAnim.Kind.GLITCH && it.color == 0xFFFF3DF2 } })
        // Names stay unique across all 1,350.
        assertEquals(Pets.NEW_COUNT, (0 until Pets.NEW_COUNT).map { Pets.petName(Pets.NEW_BASE + it) }.toSet().size)
        // Eggs of a tier hatch that tier's designed pets, new ones included.
        val r = Random(4)
        val seen = (0 until 600).map { Pets.rollEgg(r, it.toLong(), GearRarity.GLITCHED).pet }.toSet()
        assertTrue(seen.any { it - Pets.NEW_BASE in 1050 until 1150 })
        val res = listOf(File("src/main/resources/pets"), File("app/src/main/resources/pets")).first { it.isDirectory }
        assertTrue(File(res, "pets_9.webp").isFile && File(res, "eggs_9.webp").isFile)
    }

    @Test fun idleRunsDropEggsAndCollectingKeepsThem() {
        val r = Random(3)
        val gear = GearSlot.entries.associateWith { Gear.roll(r, it.ordinal.toLong(), 60, GearRarity.LEGENDARY, it) }.filterValues { it.usableBy("operative") }
        val p = PlayerProfile(characterId = "c1", characterName = "ZERO", operativeXp = 30_000_000L, equipped = gear)
        val runs = (1..6).map { IdleRunSimulator.advance(IdleRunSimulator.start(p, Difficulty.MEDIUM, 0L, it.toLong()), 12 * 3_600_000L) }
        val withEggs = runs.firstOrNull { it.eggs.isNotEmpty() }
        assertTrue("eggs: ${runs.map { it.eggs.size }} bosses: ${runs.map { it.bosses }}", withEggs != null)
        val ended = withEggs!!.copy(status = com.cyberoperative.game.idle.IdleStatus.COMPLETED)
        val after = IdleRuns.collect(p, ended)
        assertEquals(withEggs.eggs.size, after.eggs.size)
        assertTrue(after.eggs.map { it.uid }.toSet().size == after.eggs.size)
    }
}
