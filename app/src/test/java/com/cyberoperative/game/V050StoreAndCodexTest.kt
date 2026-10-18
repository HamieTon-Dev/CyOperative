package com.cyberoperative.game

import com.cyberoperative.game.data.EggOffers
import com.cyberoperative.game.data.GearRarity
import com.cyberoperative.game.data.Pet
import com.cyberoperative.game.data.PetEgg
import com.cyberoperative.game.meta.Incubators
import com.cyberoperative.game.meta.StoreManager
import com.cyberoperative.game.save.PlayerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 0.50.0: DAILY EGGS (3 offers, ◇1500, 24 h) and the PET CODEX record. */
class V050StoreAndCodexTest {
    private val day = EggOffers.ROTATE_MS

    @Test fun threeOffersThatStayForADayThenRotate() {
        val p = StoreManager.eggOffers(PlayerProfile(), now = 1_000L, rng = Random(1))
        assertEquals(3, p.eggOffers.size)
        assertEquals(p, StoreManager.eggOffers(p, now = 1_000L + day - 1, rng = Random(2)))
        val next = StoreManager.eggOffers(p, now = 1_000L + day, rng = Random(2))
        assertEquals(1_000L + day, next.eggOffersAt)
        assertNotEquals(p.eggOffers, next.eggOffers)
    }

    @Test fun eachOfferCosts1500AndBuyingAllThreeRollsThreeNew() {
        var p = StoreManager.eggOffers(PlayerProfile(diamonds = 10_000), now = 5L, rng = Random(3))
        val first = p.eggOffers[1]
        p = StoreManager.buyEggOffer(p, 1, now = 6L)!!
        assertEquals(10_000L - EggOffers.price(first.rarity), p.diamonds)
        assertEquals(2, p.eggOffers.size)
        assertEquals(first.art, p.eggs.single().art)
        assertEquals(first.rarity, p.eggs.single().rarity)
        p = StoreManager.buyEggOffer(p, 0, now = 7L)!!
        p = StoreManager.buyEggOffer(p, 0, now = 8L, rng = Random(4))!!
        assertEquals(3, p.eggOffers.size)      // a fresh three
        assertEquals(8L, p.eggOffersAt)         // and a fresh 24 h
        assertEquals(3, p.eggs.size)
        assertNull(StoreManager.buyEggOffer(p.copy(diamonds = 99), 0))
    }

    @Test fun offersAreGlitchedOrBetterPricedByRarity() {
        val r = Random(7)
        val seen = (0 until 4000).map { EggOffers.rollRarity(r) }.toSet()
        assertEquals(GearRarity.entries.filter { it >= GearRarity.GLITCHED }.toSet(), seen)
        assertEquals(listOf(100L, 150L, 200L, 250L, 300L, 350L, 400L), GearRarity.entries.filter { it >= GearRarity.GLITCHED }.map { EggOffers.price(it) })
        // A save from 0.50.0 with lower offers gets a fresh, GLITCHED+ three.
        val old = PlayerProfile(eggOffers = listOf(PetEgg(0, GearRarity.COMMON, 1, 2)), eggOffersAt = 10L)
        assertTrue(StoreManager.eggOffers(old, now = 11L, rng = Random(1)).eggOffers.all { it.rarity >= GearRarity.GLITCHED })
        assertEquals(100f, EggOffers.weights.sumOf { it.second.toDouble() }.toFloat(), 0.01f)
    }

    @Test fun theCodexRemembersHatchedPetsAndTheirEgg() {
        val a = Pet(1, 12, GearRarity.RARE, eggArt = 40)
        val b = Pet(2, 12, GearRarity.LEGENDARY, eggArt = 41)
        val dex = Incubators.dex(emptyMap(), listOf(a))
        assertEquals(40, dex.getValue(12).egg)
        val up = Incubators.dex(dex, listOf(b))
        assertEquals(GearRarity.LEGENDARY, up.getValue(12).best)
        assertEquals(40, up.getValue(12).egg)   // the first egg stays
        // Pets owned before 0.50.0 count too, and released pets stay in the record.
        val p = PlayerProfile(pets = listOf(Pet(3, 99, GearRarity.EPIC)), petCodex = up)
        assertTrue(99 in Incubators.codex(p) && 12 in Incubators.codex(p))
        assertTrue(PetEgg(0, GearRarity.RARE, 1, 2).rarity == GearRarity.RARE)
    }
}
