package com.cyberoperative.game

import com.cyberoperative.game.data.Enemies
import com.cyberoperative.game.data.Rarity
import com.cyberoperative.game.data.Upgrades
import com.cyberoperative.game.data.Weapons
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.engine.RunStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner, 2026-10-08: at least five new weapons of every rarity, and +15% base move speed. */
class ArsenalTest {

    @Test fun fiveWeaponsPerRarityAllOfferable() {
        for (r in Rarity.entries) {
            val n = Weapons.all.count { it.rarity == r }
            assertTrue("$r has only $n weapons", n >= 5)
        }
        for (w in Weapons.all) assertEquals(w.id, Upgrades.byId(w.id).id)
        assertEquals(Upgrades.all.size, Upgrades.all.map { it.id }.toSet().size)
    }

    @Test fun everyArsenalWeaponDealsDamage() {
        val failed = ArrayList<String>()
        for (w in Weapons.all) {
            val g = GameEngine(RunConfig(baseStats = RunStats().apply { maxHp = 1e9f; fireRate = 0.01f; orbCount = 0 }, seed = 13L, freeRevives = 0))
            g.build.take(Upgrades.byId(w.id))
            assertEquals(1, g.stats.weapons[w.id])
            for (e in g.enemies.items) e.active = false
            // A tough chaser that walks into range (so mines and fields trigger too).
            val e = g.spawnEnemyAt(Enemies.TROJAN, null, g.px, g.py - 240f, telegraph = false)!!
            e.hp = 1e7f; e.maxHp = 1e7f
            val start = e.hp
            var hit = false
            for (i in 0 until 60 * 10) {
                g.update(1f / 60f)
                if (e.hp < start) { hit = true; break }
            }
            if (!hit) failed += w.id
        }
        assertTrue("weapons that never hit: $failed", failed.isEmpty())
    }

    @Test fun baseMoveSpeedRaisedFifteenPercent() {
        assertEquals(240f * 1.15f, RunStats().moveSpeed, 0.01f)
    }
}

class MasteryTest {
    @Test fun upgradesLevelToTenThousandAndEvolutionsStillUnlock() {
        val b = com.cyberoperative.game.engine.RunBuild(RunStats())
        val U = Upgrades
        repeat(3) { b.take(U.PACKET_NODES) }
        // Evolutions unlock at the designed max, as before.
        assertTrue(b.isEligible(U.ENHANCED_NODES))
        // ...and the base card keeps going as mastery levels.
        assertTrue(b.isEligible(U.PACKET_NODES))
        val dmg0 = b.stats.damage
        repeat(20) { b.take(U.PAYLOAD_BOOST) }
        assertEquals(20, b.level(U.PAYLOAD_BOOST.id))
        assertTrue(b.stats.damage > dmg0 * 3f)
        // Straight to the cap: stats stay finite and the card stops being offered.
        b.restore(mapOf(U.PAYLOAD_BOOST.id to 10000, U.EXPLOIT_LANCE.id to 10000, "ping_blaster" to 10000))
        assertTrue(b.stats.damage.isFinite() && b.stats.damage > 0f)
        assertEquals(3, b.stats.lanceLevel)
        assertTrue(!b.isEligible(U.PAYLOAD_BOOST))
        val w = com.cyberoperative.game.data.Weapons.byId("ping_blaster")!!
        assertTrue(w.cooldownAt(10000) >= 0.25f && w.countAt(10000) <= 40)
        val offer = com.cyberoperative.game.engine.UpgradeOffer(U.PAYLOAD_BOOST, 7)
        assertTrue(offer.mastery && offer.effect.startsWith("MASTERY 2"))
    }
}
