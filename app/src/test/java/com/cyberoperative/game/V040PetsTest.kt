package com.cyberoperative.game

import com.cyberoperative.game.data.GearRarity
import com.cyberoperative.game.data.Pet
import com.cyberoperative.game.data.PetLoadout
import com.cyberoperative.game.data.Pets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 0.40.0: 500 new GLITCHED … ENCRYPTED pets, each with its own egg. */
class V040PetsTest {
    @Test fun highRarityEggsHatchTheNewPetsOfThatTier() {
        val r = Random(3)
        for (rar in Pets.RARITIES) repeat(40) { i ->
            val e = Pets.rollEgg(r, i.toLong(), rar)
            if (rar >= GearRarity.GLITCHED) {
                assertTrue(Pets.isNewArt(e.pet))
                assertEquals(e.pet, e.art)
                assertEquals(rar, Pets.designedTier(e.pet))
                assertTrue(e.name.endsWith("Egg"))
            } else assertTrue(e.pet < Pets.PET_COUNT)
        }
    }

    @Test fun aThousandNamedPetsSurviveTheLoadoutCodec() {
        val names = (0 until Pets.NEW_COUNT).map { Pets.petName(Pets.NEW_BASE + it) }
        assertEquals(Pets.NEW_COUNT, names.toSet().size) // 1,050 since 0.49.0, every name unique
        val last = Pets.NEW_BASE + Pets.NEW_COUNT - 1
        assertEquals(last, PetLoadout.decode(Pet(1, last, GearRarity.ENCRYPTED).loadout.encode())!!.art)
        assertEquals(Pets.PET_COUNT - 1, Pets.validPet(Pets.PET_COUNT + 3))
    }

    @Test fun fiveHundredAnimatedEncryptedPets() {
        val animated = (0 until Pets.NEW_COUNT).map { Pets.NEW_BASE + it }.filter { Pets.anim(it) != null && Pets.designedTier(it) == GearRarity.ENCRYPTED }
        assertEquals(600, animated.size) // 500 (0.41.0) + 100 (0.49.1)
        assertTrue(animated.all { Pets.designedTier(it) == GearRarity.ENCRYPTED })
        assertTrue(animated.all { a -> Pets.anim(a)!!.parts.any { it.kind != com.cyberoperative.game.data.PetAnim.Kind.FLOAT } })
        // ENCRYPTED eggs are mostly the animated ones now (600 of 670).
        val r = Random(9)
        val hits = (0 until 200).count { Pets.anim(Pets.rollEgg(r, it.toLong(), GearRarity.ENCRYPTED).pet) != null }
        assertTrue("$hits", hits in 150..195)
    }
}
