package com.cyberoperative.game

import com.cyberoperative.game.data.Arenas
import com.cyberoperative.game.data.ArenaTemplate
import com.cyberoperative.game.data.Bosses
import com.cyberoperative.game.data.Enemies
import com.cyberoperative.game.data.Events
import com.cyberoperative.game.data.PermanentUpgrades
import com.cyberoperative.game.data.Operatives
import com.cyberoperative.game.data.Upgrades
import com.cyberoperative.game.engine.Arena
import com.cyberoperative.game.engine.RunBuild
import com.cyberoperative.game.engine.RunStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DataTest {

    @Test fun idsAreUnique() {
        assertEquals(Enemies.all.size, Enemies.all.map { it.id }.toSet().size)
        assertEquals(Upgrades.all.size, Upgrades.all.map { it.id }.toSet().size)
        assertEquals(Bosses.roster.size, Bosses.roster.map { it.id }.toSet().size)
        assertEquals(Events.all.size, Events.all.map { it.id }.toSet().size)
        assertEquals(PermanentUpgrades.all.size, PermanentUpgrades.all.map { it.id }.toSet().size)
    }

    @Test fun atLeastTenBossesAndFiveEvents() {
        assertTrue(Bosses.roster.size >= 10)
        assertEquals(5, Events.all.size)
        for (b in Bosses.roster) {
            assertEquals("boss ${b.id} needs 3 phases", 3, b.phases.size)
            assertEquals(1f, b.phases[0].below, 0f)
            assertTrue(b.phases.zipWithNext().all { (a, c) -> c.below < a.below })
            assertTrue(b.phases.all { it.patterns.isNotEmpty() })
        }
    }

    @Test fun summonedEnemiesExist() {
        for (b in Bosses.roster) for (ph in b.phases) for (p in ph.patterns) {
            if (p is com.cyberoperative.game.data.Pattern.Summon) assertNotNull(Enemies.byId(p.enemyId))
        }
        for (e in Enemies.all) e.splitInto?.let { assertNotNull(Enemies.byId(it)) }
        for (ev in Events.all) ev.rules.forcedPool?.forEach { assertNotNull(Enemies.byId(it)) }
    }

    @Test fun everyArenaKeepsSpawnAndPortalClear() {
        val all = Arenas.templates + Arenas.templates.map { Arenas.mirrored(it) } + Arenas.bossArena
        for (t in all) {
            val a = Arena(t)
            assertTrue("${t.id} spawn blocked", a.isFree(a.spawnX, a.spawnY, 40f))
            assertTrue("${t.id} portal blocked", a.isFree(a.portalX, a.portalY, 50f))
            for (o in t.obstacles) {
                assertTrue("${t.id} obstacle out of bounds", o.rect.left >= 0f && o.rect.right <= ArenaTemplate.ARENA_WIDTH)
                assertTrue(o.rect.top >= 0f && o.rect.bottom <= t.height)
            }
            // A path exists between spawn and portal (coarse flood fill on a grid).
            assertTrue("${t.id} has no route to portal", reachable(a))
        }
    }

    private fun reachable(a: Arena): Boolean {
        val cell = 20f
        val cols = (a.width / cell).toInt()
        val rows = (a.height / cell).toInt()
        val seen = Array(rows) { BooleanArray(cols) }
        val q = ArrayDeque<Pair<Int, Int>>()
        val sc = (a.spawnX / cell).toInt()
        val sr = (a.spawnY / cell).toInt()
        q.add(sr to sc); seen[sr][sc] = true
        val tr = (a.portalY / cell).toInt()
        val tc = (a.portalX / cell).toInt()
        while (q.isNotEmpty()) {
            val (r, c) = q.removeFirst()
            if (r == tr && c == tc) return true
            for ((dr, dc) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                val nr = r + dr
                val nc = c + dc
                if (nr !in 0 until rows || nc !in 0 until cols || seen[nr][nc]) continue
                if (!a.isFree(nc * cell + cell / 2, nr * cell + cell / 2, 20f)) continue
                seen[nr][nc] = true
                q.add(nr to nc)
            }
        }
        return false
    }

    @Test fun offersAreDistinctAndRespectMaxLevels() {
        val b = RunBuild(RunStats())
        val rng = Random(1)
        repeat(300) {
            val offer = b.rollOffer(rng)
            // Instant fillers guarantee three cards even when the build is complete.
            assertEquals(3, offer.size)
            assertEquals(3, offer.map { it.def.id }.toSet().size)
            for (o in offer) assertTrue(b.isEligible(o.def))
            b.take(offer[rng.nextInt(offer.size)].def)
        }
        for ((id, lvl) in b.owned()) assertTrue(lvl <= Upgrades.byId(id).levelCap)
    }

    @Test fun evolutionIsGatedByMaxLevel() {
        val b = RunBuild(RunStats())
        assertFalse(b.isEligible(Upgrades.ENHANCED_NODES))
        repeat(Upgrades.PACKET_NODES.maxLevel) { b.take(Upgrades.PACKET_NODES) }
        assertTrue(b.isEligible(Upgrades.ENHANCED_NODES))
        b.take(Upgrades.ENHANCED_NODES)
        // Sentinel also needs Node Overclock.
        assertFalse(b.isEligible(Upgrades.SENTINEL_NODES))
        b.take(Upgrades.NODE_OVERCLOCK)
        assertTrue(b.isEligible(Upgrades.SENTINEL_NODES))
        assertEquals(1 + 3, b.stats.orbCount)
        b.take(Upgrades.SENTINEL_NODES)
        assertEquals(1 + 3 + 2, b.stats.orbCount)
    }

    @Test fun recomputeIsDeterministic() {
        val b = RunBuild(RunStats())
        b.take(Upgrades.PAYLOAD_BOOST); b.take(Upgrades.PAYLOAD_BOOST); b.take(Upgrades.KERNEL_OVERCLOCK)
        val dmg = b.stats.damage
        b.recompute(); b.recompute()
        assertEquals(dmg, b.stats.damage, 1e-4f)
        assertEquals(20f * 1.3f, dmg, 1e-3f)
    }

    @Test fun permanentUpgradeCostsRiseAndApply() {
        for (d in PermanentUpgrades.all) {
            for (l in 0 until d.maxLevel - 1) assertTrue(d.costFor(l + 1) > d.costFor(l))
        }
        val cfg = Operatives.buildConfig("operative", mapOf("max_hp" to 5, "rerolls" to 2, "starting_orbs" to 1), 1L)
        assertEquals(140f, cfg.baseStats.maxHp, 1e-3f)
        assertEquals(2, cfg.rerolls)
        assertEquals(2, cfg.baseStats.orbCount)
    }
}
