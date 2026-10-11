package com.cyberoperative.game

import com.cyberoperative.game.data.Enemies
import com.cyberoperative.game.data.Operatives
import com.cyberoperative.game.data.Spell
import com.cyberoperative.game.data.Spells
import com.cyberoperative.game.engine.CoopCodec
import com.cyberoperative.game.engine.CoopInput
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.engine.RunStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PC abilities: Space dash and the two class spells (R / F). */
class AbilitiesTest {
    private fun engine(classId: String = Operatives.CYBER_OPERATIVE.id) =
        GameEngine(RunConfig(baseStats = RunStats(), seed = 33L, powerGovernor = false, consumables = true, classId = classId))

    @Test
    fun dashMovesFastWithInvulnerabilityAndACooldown() {
        val g = engine()
        val o = g.operatives[0]
        repeat(30) { g.update(1f / 60f) }
        val x0 = o.px
        g.setInput(1f, 0f)
        assertTrue(g.dash())
        assertTrue("invulnerable while dashing", o.invuln > 0f)
        assertFalse("cooldown", g.dash())
        repeat(12) { g.update(1f / 60f) }
        g.setInput(0f, 0f)
        assertTrue("dashed well past a normal stride (${o.px - x0})", o.px - x0 > GameEngine.DASH_DISTANCE * 0.8f || !g.arena.isFree(o.px + 30f, o.py, 20f))
    }

    @Test
    fun phonesHaveNoAbilities() {
        val g = GameEngine(RunConfig(baseStats = RunStats(), seed = 33L))
        assertFalse(g.dash())
        assertFalse(g.castSpell(0))
    }

    @Test
    fun everyClassHasTwoDistinctSpellsThatCast() {
        for (op in Operatives.all) {
            val (r, f) = Spells.forClass(op.id)
            assertTrue(r != f)
            for (slot in 0..1) {
                val g = engine(op.id)
                repeat(30) { g.update(1f / 60f) }
                g.spawnEnemyAt(Enemies.MALWARE, null, g.px + 120f, g.py - 60f, telegraph = false)
                g.setAim(false, g.px + 200f, g.py - 100f)
                assertTrue("${op.id} slot $slot casts", g.castSpell(slot))
                assertFalse("then recharges", g.castSpell(slot))
                repeat(60) { g.update(1f / 60f) }
            }
        }
    }

    @Test
    fun fragGrenadeHitsAtTheCursor() {
        val g = engine()
        repeat(30) { g.update(1f / 60f) }
        val e = g.spawnEnemyAt(Enemies.MALWARE, null, g.px + 300f, g.py, telegraph = false)!!
        val hp0 = e.hp
        g.setAim(false, e.x, e.y)
        assertTrue(g.castSpell(1))
        assertTrue(!e.active || e.hp < hp0)
        assertEquals(Spell.FRAG_GRENADE.cooldown, g.operatives[0].spellCd[1], 0.01f)
    }

    @Test
    fun shadowStepBlinksTowardTheCursor() {
        val g = engine(Operatives.PHANTOM.id)
        repeat(30) { g.update(1f / 60f) }
        val o = g.operatives[0]
        val y0 = o.py
        g.setAim(false, o.px, o.py - 300f)
        assertTrue(g.castSpell(0))
        assertTrue("moved up (${y0 - o.py})", y0 - o.py > 60f)
    }

    @Test
    fun abilitySerialsTravel() {
        val c = CoopCodec.decodeInput(CoopCodec.encodeInput(CoopInput(dashSerial = 4, spell1Serial = 2, spell2Serial = 9)))!!
        assertEquals(4, c.dashSerial); assertEquals(2, c.spell1Serial); assertEquals(9, c.spell2Serial)
    }
}
