package com.cyberoperative.game

import com.cyberoperative.game.data.Gear
import com.cyberoperative.game.data.GearItem
import com.cyberoperative.game.data.GearRarity
import com.cyberoperative.game.data.GearSlot
import com.cyberoperative.game.data.Pet
import com.cyberoperative.game.data.PetEgg
import com.cyberoperative.game.data.Pets
import com.cyberoperative.game.data.StoreCatalog
import com.cyberoperative.game.engine.TradeOffer
import com.cyberoperative.game.meta.Incubators
import com.cyberoperative.game.save.PlayerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.52.0: ◇5,000 pack, 1,000 eggs, selling / vaulting / trading pets and eggs. */
class V052PetsEconomyTest {
    private val pet = Pet(7, 1100, GearRarity.GLITCHED, fresh = false)
    private val egg = PetEgg(9, GearRarity.ENCRYPTED, 1300, 1300)
    private val p = PlayerProfile(operativeXp = 0, pets = listOf(pet), activePet = 7, eggs = listOf(egg), euros = 0)

    @Test fun theBigPackIs5000For2999() {
        val big = StoreCatalog.diamondPacks.last()
        assertEquals("diamonds_5000", big.productId); assertEquals(5_000, big.diamonds); assertEquals("$29.99", big.usdPrice)
        assertTrue(StoreCatalog.diamondPacks.none { it.diamonds == 10_000 })
    }

    @Test fun petsAndEggsAreWorthTenSameRarityWeapons() {
        val weapon = Gear.sellPrice(GearItem(0, GearSlot.PRIMARY, GearRarity.GLITCHED, 0, 1, 0f))
        assertEquals(10 * weapon, Pets.value(GearRarity.GLITCHED, 1))
        val (sold, got) = Incubators.sellPet(p, 7)
        assertEquals(10 * weapon, got)
        assertEquals(got.toLong(), sold.euros)
        assertTrue(sold.pets.isEmpty()); assertEquals(0L, sold.activePet)
        val (q, n) = Incubators.sellEgg(p, 9, bank = false)
        assertTrue(n > 0); assertEquals(0L, q.euros); assertTrue(q.eggs.isEmpty())
        assertEquals(1000, Pets.MAX_EGGS)
    }

    @Test fun theVaultHoldsPetsAndEggs() {
        val v = Incubators.eggToVault(Incubators.petToVault(p, 7)!!, 9)!!
        assertTrue(v.pets.isEmpty() && v.eggs.isEmpty()); assertEquals(0L, v.activePet)
        assertEquals(1, v.vaultPets.size); assertEquals(1, v.vaultEggs.size)
        val back = Incubators.eggFromVault(Incubators.petFromVault(v, 7)!!, 9)!!
        assertEquals(listOf(7L), back.pets.map { it.uid }); assertEquals(listOf(9L), back.eggs.map { it.uid })
        assertNull(Incubators.petToVault(p.copy(vaultEggs = List(Pets.VAULT_SIZE) { egg.copy(uid = it.toLong()) }), 7))
    }

    @Test fun eggsAndPetsRideAlongInATradeOffer() {
        val g = GearItem(3, GearSlot.CORE, GearRarity.EPIC, 0, 10, 5f)
        val o = TradeOffer(10, 0, TradeOffer.pack(listOf(g), listOf(egg), listOf(pet)))
        assertEquals(listOf(g), o.itemList); assertEquals(listOf(egg), o.eggList); assertEquals(listOf(pet), o.petList)
        val gearOnly = TradeOffer(0, 0, TradeOffer.pack(listOf(g), emptyList(), emptyList()))
        assertEquals(Gear.encode(listOf(g)), gearOnly.items)
        val got = Incubators.addPets(PlayerProfile(petSeq = 50), listOf(pet))
        assertEquals(50L, got.pets.single().uid); assertTrue(1100 in got.petCodex)
    }
}
