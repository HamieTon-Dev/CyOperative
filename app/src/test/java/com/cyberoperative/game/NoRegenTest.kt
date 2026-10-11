package com.cyberoperative.game

import com.cyberoperative.game.data.Upgrades
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.engine.RunStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** No HP or Firewall regeneration over time (owner, 2026-10-11): HP charges replace it. */
class NoRegenTest {
    @Test fun hpAndFirewallDontComeBackByThemselvesInAFight() {
        val g = GameEngine(RunConfig(baseStats = RunStats().apply { firewallMax = 100f }, seed = 3L, consumables = true))
        val o = g.operatives[0]
        o.hp = o.build.stats.maxHp * 0.5f
        o.firewall = 10f
        var frames = 0
        // Threats parked far away: the room stays in combat, nobody takes damage.
        while (frames++ < 60 * 20) {
            for (e in g.enemies.items) if (e.active) { e.x = -9000f; e.y = -9000f }
            for (p in g.projectiles.items) if (p.active && !p.friendly) p.active = false
            val hp = o.hp; val fw = o.firewall
            g.update(1f / 60f)
            if (g.phase != com.cyberoperative.game.engine.Phase.COMBAT) break
            assertTrue("HP regenerated", o.hp <= hp + 1e-3f)
            assertTrue("Firewall regenerated", o.firewall <= fw + 1e-3f)
        }
        assertTrue("stayed in the fight long enough", frames > 60 * 5)
    }

    @Test fun selfRepairIsGone() {
        assertNull(Upgrades.byIdOrNull("self_repair"))
    }
}
