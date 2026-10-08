package com.cyberoperative.game

import com.cyberoperative.game.data.LivingBackground
import com.cyberoperative.game.data.OperativeSkins
import com.cyberoperative.game.data.StoreCatalog
import com.cyberoperative.game.meta.StoreManager
import com.cyberoperative.game.save.PlayerProfile
import com.cyberoperative.game.save.SaveRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreTest {

    @Test fun ownerPriceTable() {
        assertEquals(listOf(150, 500, 1000, 10000), StoreCatalog.diamondPacks.map { it.diamonds })
        assertEquals(listOf("$1.00", "$4.50", "$8.50", "$69.99"), StoreCatalog.diamondPacks.map { it.usdPrice })
        assertEquals(listOf(1 to 100, 7 to 500, 20 to 800, 250 to 6500), StoreCatalog.revivePacks.map { it.revives to it.priceDiamonds })
        assertEquals(100, StoreCatalog.SKIN_PRICE)
        assertEquals(300, StoreCatalog.ALL_SKINS_PRICE)
        assertTrue(OperativeSkins.paid.size >= 10)
    }

    @Test fun skinPurchaseRules() {
        val p = PlayerProfile(diamonds = 250)
        assertNull(StoreManager.buySkin(p, "default"))
        val a = StoreManager.buySkin(p, "red_hat")!!
        assertEquals(150, a.diamonds)
        assertEquals("red_hat", a.selectedSkin)
        assertNull("can't buy twice", StoreManager.buySkin(a, "red_hat"))
        assertNull("not enough for bundle", StoreManager.buyAllSkins(a))
        val all = StoreManager.buyAllSkins(a.copy(diamonds = 300))!!
        assertEquals(0, all.diamonds)
        assertTrue(StoreManager.allSkinsOwned(all))
        assertNull(StoreManager.buyAllSkins(all.copy(diamonds = 999)))
        assertNull(StoreManager.equipSkin(PlayerProfile(), "void"))
    }

    @Test fun revivesAndBackgrounds() {
        var p = PlayerProfile(diamonds = 1000)
        p = StoreManager.buyRevives(p, StoreCatalog.revivePacks[2])!!
        assertEquals(20, p.reviveTokens); assertEquals(200, p.diamonds)
        p = StoreManager.useReviveToken(p)!!
        assertEquals(19, p.reviveTokens)
        assertNull(StoreManager.useReviveToken(PlayerProfile()))
        p = StoreManager.buyBackground(p, LivingBackground.AURORA.id)!!
        assertEquals("aurora", p.selectedBackground)
        assertNull(StoreManager.buyBackground(p, "none"))
        assertNotNull(StoreManager.equipBackground(p, "none"))
    }

    @Test fun oldSavesStillLoad() {
        // A 0.2.0 save without the new store fields decodes with defaults.
        val old = """{"version":1,"euros":42,"diamonds":7}"""
        val p = SaveRepository.decodeOrNull(old)!!
        assertEquals(42, p.euros); assertEquals(0, p.reviveTokens); assertEquals("none", p.selectedBackground)
    }

    @Test fun neonOperativePurchase() {
        val poor = PlayerProfile(diamonds = 50)
        assertNull(StoreManager.buyNeonOperative(poor))
        assertNull("premium body can't be equipped unowned", StoreManager.equipBody(poor, StoreManager.NEON_OPERATIVE_ID, premium = true))
        val p = StoreManager.buyNeonOperative(PlayerProfile(diamonds = 150))!!
        assertEquals(150 - StoreCatalog.NEON_OPERATIVE_PRICE.toLong(), p.diamonds)
        assertEquals(StoreManager.NEON_OPERATIVE_ID, p.operativeBody)
        assertNull("can't buy twice", StoreManager.buyNeonOperative(p))
        assertNotNull(StoreManager.equipBody(p, "agent", premium = false))
    }

    @Test fun skinPackIncludesNeonOperative() {
        val p = StoreManager.buyAllSkins(PlayerProfile(diamonds = 300))!!
        assertTrue(StoreManager.NEON_OPERATIVE_ID in p.ownedOperatives)
        assertTrue(StoreManager.allSkinsOwned(p))
        assertNull(StoreManager.buyNeonOperative(p.copy(diamonds = 999)))
        // Owning every colour skin but not the operative still offers the pack.
        val skinsOnly = p.copy(ownedOperatives = setOf("operative"))
        assertTrue(!StoreManager.allSkinsOwned(skinsOnly))
    }
}
