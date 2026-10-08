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
