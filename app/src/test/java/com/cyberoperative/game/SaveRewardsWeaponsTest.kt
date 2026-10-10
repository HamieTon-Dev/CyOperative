package com.cyberoperative.game

import com.cyberoperative.game.data.Enemies
import com.cyberoperative.game.data.Rarity
import com.cyberoperative.game.data.Upgrades
import com.cyberoperative.game.engine.Difficulty
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.GameMode
import com.cyberoperative.game.engine.Phase
import com.cyberoperative.game.engine.RunBuild
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.engine.RunSnapshot
import com.cyberoperative.game.engine.RunStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Save & continue, boss save block, mod tiers / reward counts, difficulty and the new weapons. */
class SaveRewardsWeaponsTest {

    private fun config(seed: Long = 11L, difficulty: Difficulty = Difficulty.MEDIUM, mode: GameMode = GameMode.CAMPAIGN) =
        RunConfig(baseStats = RunStats().apply { maxHp = 1e6f }, seed = seed, freeRevives = 0, mode = mode, difficulty = difficulty)

    @Test fun saveRestoresLevelRoomEnemiesAndBuild() {
        val g = GameEngine(config())
        g.debugJumpToLevel(4)
        g.build.take(Upgrades.PAYLOAD_BOOST)
        g.build.take(Upgrades.MALWARE_MISSILES)
        repeat(240) { g.update(1f / 60f) }
        val snap = g.snapshot()
        assertNotNull(snap)
        val decoded = RunSnapshot.decodeOrNull(snap!!.encode())
        assertEquals(snap, decoded)

        val r = GameEngine(config(), decoded)
        assertEquals(4, r.level)
        assertEquals(g.plan.arena.obstacles, r.plan.arena.obstacles)
        assertEquals(g.levelKills, r.levelKills)
        assertEquals(g.score, r.score)
        assertEquals(1, r.build.level(Upgrades.MALWARE_MISSILES.id))
        val before = g.enemies.items.filter { it.active }.map { it.def.id to it.hp }.sortedBy { it.second }
        val after = r.enemies.items.filter { it.active }.map { it.def.id to it.hp }.sortedBy { it.second }
        assertEquals(before, after)
        assertEquals(g.px, r.px, 0.01f)
        // Resumed enemies get a short spawn-in so nothing fires instantly.
        assertTrue(r.enemies.items.filter { it.active }.all { !it.targetable })
    }

    @Test fun saveIsBlockedDuringABossFight() {
        val g = GameEngine(config())
        g.debugJumpToLevel(10)
        repeat(60) { g.update(1f / 60f) }
        assertNotNull(g.boss)
        assertEquals("[BOSS] SAVE BLOCKED", g.saveBlockReason)
        assertNull(g.snapshot())
        // Defeating the boss unlocks saving (once the victory sequence has played).
        g.killEnemy(g.boss!!)
        repeat(((GameEngine.VICTORY_SECONDS + 3f) * 60f).toInt()) { g.update(1f / 60f) }
        assertNull(g.saveBlockReason)
        assertNotNull(g.snapshot())
    }

    @Test fun bossGivesSeveralRewardsCountedOnTheUpgradeScreen() {
        val g = GameEngine(config())
        g.debugJumpToLevel(10)
        repeat(240) { g.update(1f / 60f) }
        g.killEnemy(g.boss!!)
        var guard = 0
        while (g.phase != Phase.UPGRADE && guard++ < 600) g.update(1f / 60f)
        assertEquals(Phase.UPGRADE, g.phase)
        assertTrue("boss should pay 3+ rewards, got ${g.rewardBatchTotal}", g.rewardBatchTotal >= 3)
        assertEquals(0, g.rewardBatchTaken)
        g.chooseUpgrade(0)
        assertEquals(1, g.rewardBatchTaken)
        assertTrue(g.offerLuck >= 1f)
    }

    @Test fun rarerTiersAreRarerAndLuckHelps() {
        val b = RunBuild(RunStats())
        val blue = b.chanceOf(Upgrades.PENETRATION)
        val purple = b.chanceOf(Upgrades.MULTISHOT)
        val gold = b.chanceOf(Upgrades.GOLDEN_PROTOCOL)
        val titanium = b.chanceOf(Upgrades.OMEGA_OVERCLOCK)
        assertTrue(blue > purple && purple > gold && gold > titanium)
        assertEquals(Rarity.TITANIUM, Upgrades.PLASMA_BEAM.rarity)
        assertTrue(b.chanceOf(Upgrades.OMEGA_OVERCLOCK, luck = 1.5f) > titanium * 5f)
    }

    @Test fun hardThreatsAreTougherThanEasy() {
        fun firstHp(d: Difficulty): Float {
            val g = GameEngine(config(seed = 5L, difficulty = d))
            return g.spawnEnemyAt(Enemies.all.first(), null, 300f, 300f, telegraph = false)!!.maxHp
        }
        assertTrue(firstHp(Difficulty.HARD) > firstHp(Difficulty.MEDIUM))
        assertTrue(firstHp(Difficulty.MEDIUM) > firstHp(Difficulty.EASY))
    }

    @Test fun plasmaBeamOverheatsAfterFiveSecondsOfHits() {
        val g = GameEngine(RunConfig(baseStats = RunStats().apply { maxHp = 1e7f; range = 3000f; damage = 0.01f; orbCount = 0 }, seed = 21L, freeRevives = 0))
        g.build.take(Upgrades.PLASMA_BEAM)
        val tank = Enemies.all.maxBy { it.baseHp }
        var overheated = false
        repeat(60 * 8) {
            if (g.aliveCount() == 0) g.spawnEnemyAt(tank, null, g.px, g.py - 260f, telegraph = false)
            g.update(1f / 60f)
            if (g.beamCooldown > 0f) overheated = true
        }
        assertTrue("beam should overheat after 5s of hits", overheated)
    }

    private fun weaponHits(def: com.cyberoperative.game.data.UpgradeDef, moving: Boolean = false): Boolean {
        val g = GameEngine(RunConfig(baseStats = RunStats().apply { maxHp = 1e7f; fireRate = 0.01f; orbCount = 0 }, seed = 9L, freeRevives = 0))
        g.build.take(def)
        for (e in g.enemies.items) e.active = false
        val tank = Enemies.all.maxBy { it.baseHp }
        val e = g.spawnEnemyAt(tank, null, g.px, g.py - 200f, telegraph = false)!!
        val start = e.hp
        repeat(60 * 8) { i ->
            if (moving) g.setInput(if ((i / 30) % 2 == 0) 1f else -1f, 0f) else g.setInput(0f, 0f)
            g.update(1f / 60f)
            if (!e.active || e.hp < start) return true
        }
        return false
    }

    @Test fun newWeaponsDealDamage() {
        assertTrue("missiles", weaponHits(Upgrades.MALWARE_MISSILES))
        assertTrue("arc", weaponHits(Upgrades.ARC_DISCHARGE))
        assertTrue("railgun", weaponHits(Upgrades.QUANTUM_RAILGUN))
        assertTrue("orbital strike", weaponHits(Upgrades.ORBITAL_STRIKE))
    }

    @Test fun logicBombsDropWhileMoving() {
        val g = GameEngine(RunConfig(baseStats = RunStats().apply { maxHp = 1e7f }, seed = 4L, freeRevives = 0))
        g.build.take(Upgrades.LOGIC_BOMBS)
        repeat(60) { g.update(1f / 60f) }
        g.setInput(1f, 0f)
        repeat(120) { g.update(1f / 60f) }
        assertTrue(g.projectiles.items.any { it.active && it.kind == com.cyberoperative.game.engine.ProjKind.MINE })
    }
}
