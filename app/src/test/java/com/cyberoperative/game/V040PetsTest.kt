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
        for (rar in GearRarity.entries) repeat(40) { i ->
            val e = Pets.rollEgg(r, i.toLong(), rar)
            if (rar >= GearRarity.GLITCHED) {
                assertTrue(Pets.isNewArt(e.pet))
                assertEquals(e.pet, e.art)
                assertEquals(rar, Pets.designedTier(e.pet))
                assertTrue(e.name.endsWith("Egg"))
            } else assertTrue(e.pet < Pets.PET_COUNT)
        }
    }

    @Test fun fiveHundredNamedPetsSurviveTheLoadoutCodec() {
        val names = (0 until Pets.NEW_COUNT).map { Pets.petName(Pets.NEW_BASE + it) }
        assertEquals(500, names.toSet().size)
        val last = Pets.NEW_BASE + Pets.NEW_COUNT - 1
        assertEquals(last, PetLoadout.decode(Pet(1, last, GearRarity.ENCRYPTED).loadout.encode())!!.art)
        assertEquals(Pets.PET_COUNT - 1, Pets.validPet(Pets.PET_COUNT + 3))
    }
}
