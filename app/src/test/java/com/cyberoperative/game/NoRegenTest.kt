package com.cyberoperative.game

import com.cyberoperative.game.data.Upgrades
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.engine.RunStats
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** No HP regeneration over time (owner, 2026-10-11), and no Firewall at all (2026-10-13). */
class NoRegenTest {
    @Test fun hpDoesntComeBackByItselfInAFight() {
        val g = GameEngine(RunConfig(baseStats = RunStats(), seed = 3L, consumables = true))
        val o = g.operatives[0]
        o.hp = o.build.stats.maxHp * 0.5f
        var frames = 0
        while (frames++ < 60 * 20) {
            for (e in g.enemies.items) if (e.active) { e.x = -9000f; e.y = -9000f }
            for (p in g.projectiles.items) if (p.active && !p.friendly) p.active = false
            val hp = o.hp
            g.update(1f / 60f)
            if (g.phase != com.cyberoperative.game.engine.Phase.COMBAT) break
            assertTrue("HP regenerated", o.hp <= hp + 1e-3f)
        }
        assertTrue("stayed in the fight long enough", frames > 60 * 5)
    }

    @Test fun selfRepairAndTheFirewallChainAreGone() {
        for (id in listOf("self_repair", "firewall", "reinforced_firewall", "adaptive_firewall", "zero_trust")) assertNull(id, Upgrades.byIdOrNull(id))
        assertNull(com.cyberoperative.game.data.PermanentUpgrades.byId("firewall"))
    }
}
