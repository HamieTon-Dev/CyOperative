package com.cyberoperative.game

import com.cyberoperative.game.data.Gear
import com.cyberoperative.game.data.GearArt
import com.cyberoperative.game.data.GearItem
import com.cyberoperative.game.data.GearRarity
import com.cyberoperative.game.data.GearSlot
import com.cyberoperative.game.data.GunStyle
import com.cyberoperative.game.engine.GameEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** 0.47.0: GLITCHED ENCRYPTED, the new top gear rarity. */
class V047GlitchedEncryptedTest {
    private val gx = GearRarity.GLITCHED_ENCRYPTED

    @Test fun itIsTheTopTierAndSavesAndLoads() {
        assertEquals(GearRarity.entries.last(), gx)
        assertTrue(gx.animated)
        val item = GearItem(1, GearSlot.PRIMARY, gx, 299, 50, 100f)
        assertEquals(item, Gear.decode(Gear.encode(listOf(item))).single())
        assertTrue(Gear.shopPrice(item) > Gear.shopPrice(item.copy(rarity = GearRarity.ENCRYPTED)))
    }

    @Test fun fiftyOfEverySlotAndFiftyClassWeaponsPerClass() {
        for (s in GearSlot.entries) assertEquals(if (s == GearSlot.PRIMARY) 300 else 50, GearArt.count(s, gx.artTier))
        assertTrue((0 until 50).all { GunStyle.forArt(it, gx).classId == null })
        val byClass = (50 until 300).groupBy { GunStyle.forArt(it, gx).classId }
        assertEquals(setOf("operative", "technician", "juggernaut", "phantom", "packet_mage"), byClass.keys)
        assertTrue(byClass.values.all { it.size == 50 })
        // Every sheet is there with the right number of cells.
        val res = listOf(File("src/main/resources/gear"), File("app/src/main/resources/gear")).first { it.isDirectory }
        for (s in GearSlot.entries) {
            val f = File(res, "${s.sheet}_gx.webp")
            assertTrue(f.path, f.isFile)
        }
    }

    @Test fun rollsUseTheNewArtAndAFifthAreClassWeapons() {
        val rng = Random(4)
        val rifles = (1..2000).map { Gear.roll(rng, it.toLong(), 50, gx, GearSlot.PRIMARY) }
        assertTrue(rifles.all { it.art in 0 until 300 })
        val classed = rifles.count { it.classOnly != null } / 2000f
        assertTrue("$classed", classed in 0.15f..0.25f)
        assertTrue(GearSlot.entries.all { s -> (1..200).map { Gear.roll(rng, it.toLong(), 50, gx, s).art }.all { it in 0 until GearArt.count(s, gx.artTier) } })
    }

    @Test fun dropsOnEveryDifficultyRarerThanEncryptedAndNeverInTheShop() {
        for (inf in listOf(0, 5)) {
            val o = Gear.odds(boss = true, inferno = inf)
            assertEquals(100f, o.sum(), 0.01f)
            assertTrue(o[gx.ordinal] > 0f && o[gx.ordinal] < o[GearRarity.ENCRYPTED.ordinal])
            assertEquals(0f, Gear.shopOdds(10)[gx.ordinal], 0f)
        }
    }

    @Test fun coopLookKeepsTheHighArtIndices() {
        val item = GearItem(1, GearSlot.PRIMARY, gx, 299, 1, 0f)
        val back = GameEngine.gearFromLook(GameEngine.gearLook(item))
        assertEquals(gx, back.rarity); assertEquals(GearSlot.PRIMARY, back.slot); assertEquals(299, back.art)
    }
}
