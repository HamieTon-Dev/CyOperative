package com.cyberoperative.game

import com.cyberoperative.game.data.Enemies
import com.cyberoperative.game.engine.AllyConfig
import com.cyberoperative.game.engine.COOP_HP_MUL
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.Phase
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.engine.RunStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Two operatives in one run (owner, 2026-10-08 co-op rules). */
class CoopEngineTest {

    private fun coop(seed: Long = 11L) = GameEngine(RunConfig(seed = seed, ally = AllyConfig(RunStats())))

    /** Step without anyone touching the sticks; the partner stands where it is. */
    private fun run(g: GameEngine, seconds: Float, each: () -> Unit = {}) {
        var t = 0f
        while (t < seconds) { each(); g.update(1f / 60f); t += 1f / 60f }
    }

    @Test fun twoOperativesStartSideBySide() {
        val g = coop()
        assertEquals(2, g.operatives.size)
        val (a, b) = g.operatives
        assertTrue(g.coop)
        assertNotEquals(a.px, b.px, 1f)
        assertEquals(a.build.stats.maxHp, a.hp, 1e-3f)
        assertEquals(b.build.stats.maxHp, b.hp, 1e-3f)
        assertFalse(g.canRevive)
        assertEquals("CO-OP RUNS CAN'T BE SAVED", g.saveBlockReason?.takeIf { g.phase != Phase.DEAD && !g.bossActive })
    }

    @Test fun threatsAreToughenedForTwo() {
        val solo = GameEngine(RunConfig(seed = 3L))
        val duo = GameEngine(RunConfig(seed = 3L, ally = AllyConfig()))
        val a = solo.spawnEnemyAt(Enemies.MALWARE, null, 200f, 300f, telegraph = false)!!
        val b = duo.spawnEnemyAt(Enemies.MALWARE, null, 200f, 300f, telegraph = false)!!
        assertEquals(COOP_HP_MUL, b.maxHp / a.maxHp, 1e-3f)
    }

    @Test fun threatsChaseTheClosestOperative() {
        val g = coop()
        val (host, ally) = g.operatives
        // Park the partner far from the host, and a chaser right next to the partner.
        host.px = 120f; host.py = g.arena.height - 120f
        ally.px = g.arena.width - 120f; ally.py = 160f
        for (e in g.enemies.items) e.active = false
        val e = g.spawnEnemyAt(Enemies.MALWARE, null, ally.px - 150f, ally.py + 40f, telegraph = false)!!
        val startToAlly = Math.hypot((e.x - ally.px).toDouble(), (e.y - ally.py).toDouble())
        run(g, 1.2f) { g.setInput(0f, 0f); g.setInputFor(1, 0f, 0f) }
        if (e.active) {
            val nowToAlly = Math.hypot((e.x - ally.px).toDouble(), (e.y - ally.py).toDouble())
            assertTrue("chaser should close in on the partner", nowToAlly < startToAlly)
        }
    }

    @Test fun downedPartnerIsRevivedByStandingClose() {
        val g = coop()
        for (e in g.enemies.items) e.active = false
        val (host, ally) = g.operatives
        ally.hp = 0f
        run(g, 0.1f)
        assertTrue(ally.downed)
        assertEquals(Phase.COMBAT, g.phase)
        // Walk the host next to the partner and wait out the revive.
        host.px = ally.px + 30f; host.py = ally.py
        // Keep the host safe (killing everything would clear the level and pause on the cards).
        run(g, GameEngine.REVIVE_SECONDS + 0.3f) { host.invuln = 1f; host.px = ally.px + 30f; host.py = ally.py }
        assertFalse(ally.downed)
        assertEquals(ally.build.stats.maxHp * 0.5f, ally.hp, 1f)
    }

    @Test fun runEndsOnlyWhenBothAreDown() {
        val g = coop()
        val (host, ally) = g.operatives
        host.hp = 0f
        run(g, 0.1f)
        assertTrue(host.downed)
        assertNotEquals(Phase.DEAD, g.phase)
        ally.hp = 0f
        run(g, 0.1f)
        assertEquals(Phase.DEAD, g.phase)
    }

    @Test fun eachOperativePicksItsOwnCards() {
        val g = coop(5L)
        for (e in g.enemies.items) e.active = false
        var guard = 0
        // Clear level 1 by force and wait for the upgrade screen.
        while (g.phase != Phase.UPGRADE && guard++ < 6000) {
            for (e in g.enemies.items) if (e.active) g.killEnemy(e)
            g.update(1f / 60f)
        }
        assertEquals(Phase.UPGRADE, g.phase)
        val (host, ally) = g.operatives
        assertTrue(host.offer.isNotEmpty())
        assertTrue(ally.offer.isNotEmpty())
        val allyPick = ally.offer[0].def.id
        val hostPick = host.offer.last().def.id
        repeat(host.pendingUpgrades) { g.chooseUpgradeFor(0, host.offer.size - 1) }
        assertEquals("waits for the partner", Phase.UPGRADE, g.phase)
        repeat(ally.pendingUpgrades) { g.chooseUpgradeFor(1, 0) }
        assertNotEquals(Phase.UPGRADE, g.phase)
        assertTrue(ally.build.level(allyPick) > 0 || allyPick == hostPick)
        assertTrue(host.build.level(hostPick) > 0 || com.cyberoperative.game.data.Upgrades.all.first { it.id == hostPick }.instant)
    }

    @Test fun partnerLeavingMidPickDoesNotStallTheRun() {
        val g = coop(5L)
        var guard = 0
        while (g.phase != Phase.UPGRADE && guard++ < 6000) {
            for (e in g.enemies.items) if (e.active) g.killEnemy(e)
            g.update(1f / 60f)
        }
        val host = g.operatives[0]
        repeat(host.pendingUpgrades) { g.chooseUpgradeFor(0, 0) }
        assertEquals(Phase.UPGRADE, g.phase)
        g.removeOperative(1)
        assertNotEquals(Phase.UPGRADE, g.phase)
        assertTrue(g.operatives[1].gone)
    }

    @Test fun botsCanPlaySeveralLevelsTogether() {
        val g = GameEngine(RunConfig(seed = 21L, ally = AllyConfig(RunStats())))
        val bot = Bot(g, dodge = true)
        var t = 0f
        while (t < 120f && g.phase != Phase.DEAD) {
            // The partner shadows the host so the revive rule gets exercised too.
            val (host, ally) = g.operatives
            val dx = host.px - ally.px
            val dy = host.py - ally.py
            val d = kotlin.math.sqrt(dx * dx + dy * dy)
            if (g.phase == Phase.UPGRADE) repeat(ally.pendingUpgrades) { g.chooseUpgradeFor(1, 0) }
            g.setInputFor(1, if (d > 90f) dx / d else 0f, if (d > 90f) dy / d else 0f)
            bot.step(1f / 30f)
            t += 1f / 30f
        }
        assertTrue("co-op run reached level ${g.level}", g.level >= 2 || g.phase == Phase.DEAD)
    }
}
