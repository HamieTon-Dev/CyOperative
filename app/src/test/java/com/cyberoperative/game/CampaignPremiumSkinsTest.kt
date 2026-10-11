package com.cyberoperative.game

import com.cyberoperative.game.data.OperativeSkins
import com.cyberoperative.game.data.Operatives
import com.cyberoperative.game.data.StoreCatalog
import com.cyberoperative.game.data.Upgrades
import com.cyberoperative.game.meta.StoreManager
import com.cyberoperative.game.save.ClassRecord
import com.cyberoperative.game.save.PlayerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** PURPLE / GLITCHED from World 15, GOLD and NEON for ◇1000, PROXY SHOT gone (owner, 2026-10-11). */
class CampaignPremiumSkinsTest {

    @Test fun beatingWorld15UnlocksPurpleAndGlitchedForEveryClass() {
        val fresh = PlayerProfile()
        val done = fresh.copy(campaignsCompleted = 1)
        for (skin in listOf(OperativeSkins.PURPLE, OperativeSkins.GLITCHED)) {
            for (c in Operatives.all) {
                assertFalse(StoreManager.skinUnlocked(fresh, skin, c.id))
                assertTrue(StoreManager.skinUnlocked(done, skin, c.id))
            }
        }
    }

    @Test fun purpleEarnedTheOldWayStaysUnlocked() {
        val cls = Operatives.all.first().id
        val p = PlayerProfile(classRecords = mapOf(cls to ClassRecord(bestHard = 100)))
        assertTrue(StoreManager.skinUnlocked(p, OperativeSkins.PURPLE, cls))
        assertFalse(StoreManager.skinUnlocked(p, OperativeSkins.GLITCHED, cls))
    }

    @Test fun goldAndNeonCostAThousandAndAreNotInTheSkinPack() {
        for (skin in listOf(OperativeSkins.GOLD, OperativeSkins.NEON)) {
            assertEquals(1000, skin.price)
            assertFalse(skin in OperativeSkins.paid)
            assertNull(StoreManager.buySkin(PlayerProfile(diamonds = 999), skin.id))
            val bought = StoreManager.buySkin(PlayerProfile(diamonds = 1000), skin.id)!!
            assertEquals(0L, bought.diamonds)
            assertTrue(skin.id in bought.ownedSkins)
        }
        // Any premium skin, now or later (priced above ◇100), stays out of the pack.
        val premium = OperativeSkins.all.filter { it.price > StoreCatalog.SKIN_PRICE }
        assertTrue(premium.size >= 2)
        assertTrue(premium.none { it in OperativeSkins.paid })
        assertEquals(1400, StoreCatalog.ALL_SKINS_PRICE)
        val pack = StoreManager.buyAllSkins(PlayerProfile(diamonds = StoreCatalog.ALL_SKINS_PRICE.toLong()))!!
        assertFalse(OperativeSkins.GOLD.id in pack.ownedSkins)
        assertFalse(OperativeSkins.NEON_ID in pack.ownedSkins)
    }

    @Test fun neonWearsThePickedColourAndCarriesItToCoop() {
        assertNull("only owners pick a colour", StoreManager.setNeonColor(PlayerProfile(), 0xFF00FFAA))
        var p = StoreManager.buySkin(PlayerProfile(diamonds = 1000), OperativeSkins.NEON_ID)!!
        p = StoreManager.setNeonColor(p, 0xFF00FFAA)!!
        val worn = StoreManager.effectiveSkin(p)
        assertEquals(OperativeSkins.NEON_ID, worn.id)
        assertEquals(0xFF00FFAA, worn.edge)
        val token = OperativeSkins.token(worn)
        assertEquals("neon_custom#00FFAA", token)
        assertEquals(0xFF00FFAA, OperativeSkins.byId(token).edge)
        assertNotNull(StoreManager.equipSkin(p, OperativeSkins.NEON_ID))
    }

    @Test fun proxyShotIsGone() {
        assertNull(Upgrades.byIdOrNull("proxy_shot"))
        val b = com.cyberoperative.game.engine.RunBuild(com.cyberoperative.game.engine.RunStats())
        b.restore(mapOf("proxy_shot" to 1, Upgrades.MULTISHOT.id to 1))
        assertEquals(0, b.level("proxy_shot"))
        assertEquals(1, b.level(Upgrades.MULTISHOT.id))
    }
}
