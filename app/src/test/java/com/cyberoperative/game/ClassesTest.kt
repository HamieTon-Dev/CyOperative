package com.cyberoperative.game

import com.cyberoperative.game.data.Bosses
import com.cyberoperative.game.data.Enemies
import com.cyberoperative.game.data.Operatives
import com.cyberoperative.game.data.OperativeSkins
import com.cyberoperative.game.data.Weapons
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.LevelPlanner
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.engine.RunStats
import com.cyberoperative.game.meta.StoreManager
import com.cyberoperative.game.save.ClassRecord
import com.cyberoperative.game.save.PlayerProfile
import com.cyberoperative.game.ui.game.BodyStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Classes (owner, 2026-10-11): Field Agent + Technician, Juggernaut, Phantom, Packet Mage. */
class ClassesTest {
    private fun stats(id: String) = Operatives.buildConfig(id, emptyMap(), 1L).baseStats
    private val base = RunStats()

    @Test fun rosterOrderBodiesAndStats() {
        assertEquals(listOf("operative", "technician", "juggernaut", "phantom", "packet_mage"), Operatives.all.map { it.id })
        assertEquals("FIELD AGENT", Operatives.all.first().name)
        for (c in Operatives.all) {
            assertEquals(c.id, Operatives.byBody(c.bodyId).id)
            assertEquals(c.bodyId, BodyStyle.byId(c.bodyId).id)
        }
        assertEquals("neon_operative", Operatives.TECHNICIAN.bodyId)

        assertEquals(base.damage * 1.2f, stats("operative").damage, 0.01f)
        assertEquals(base.moveSpeed, stats("operative").moveSpeed, 0.01f)

        val t = stats("technician")
        assertEquals(base.fireRate * 1.1f, t.fireRate, 0.01f)
        assertEquals(base.moveSpeed * 1.1f, t.moveSpeed, 0.01f)
        assertEquals(1, t.weapons[Weapons.TECH_BEAM.id])

        val j = stats("juggernaut")
        assertEquals(base.maxHp * 2f, j.maxHp, 0.01f)
        assertEquals(100f, j.armor, 0.01f)
        assertEquals(base.moveSpeed * 0.9f, j.moveSpeed, 0.01f)
        assertEquals(1, j.weapons[Weapons.BRUTE_FORCE.id])

        assertTrue(stats("phantom").cloak)
        assertEquals(1, stats("packet_mage").weapons[Weapons.MAGE_FIREBALL.id])
    }

    @Test fun classWeaponsTakeNoSlotFireOnScheduleAndGrowWithLevel() {
        assertEquals(1, Weapons.classWeaponLevel(1))
        assertEquals(10, Weapons.classWeaponLevel(100))
        for (w in Weapons.classWeapons) {
            assertTrue(w.innate)
            assertEquals("fixed cooldown", w.cooldown, w.cooldownAt(10), 0f)
            assertTrue(w.damageAt(10) > w.damageAt(1) * 3.5f)
            assertTrue("never offered as a pick", com.cyberoperative.game.data.Upgrades.all.none { it.id == w.id })
        }
        assertTrue(Weapons.TECH_BEAM.throughWalls)
        assertEquals(2f, Weapons.TECH_BEAM.cooldown, 0f)
        assertEquals(2f, Weapons.MAGE_FIREBALL.cooldown, 0f)
        assertTrue(Weapons.MAGE_FIREBALL.splash > 0f && Weapons.MAGE_FIREBALL.homing > 0f && !Weapons.MAGE_FIREBALL.stillOnly)
        val g = GameEngine(Operatives.buildConfig("technician", emptyMap(), 1L))
        assertTrue("the built-in weapon takes no slot", g.build.weapons().isEmpty())
    }

    @Test fun mageFiresWhileMoving() {
        val cfg = Operatives.buildConfig("packet_mage", emptyMap(), 3L).let { it.copy(baseStats = it.baseStats.apply { fireRate = 0.01f; orbCount = 0; maxHp = 1e6f }) }
        val g = GameEngine(cfg)
        g.debugStartPlan(LevelPlanner.plan(3, Random(1), null, true).copy(waves = emptyList()))
        val e = g.spawnEnemyAt(Enemies.byId("trojan"), null, g.px + 260f, g.py, telegraph = false)!!
        e.speed = 0f
        val hp0 = e.hp
        var t = 0f
        while (t < 4.5f) { g.setInput(0f, if ((t * 2).toInt() % 2 == 0) 1f else -1f); g.update(1f / 60f); t += 1f / 60f }
        assertTrue("fireball hit while moving (${e.hp} < $hp0)", e.hp < hp0 || !e.active)
    }

    @Test fun phantomCloaksAndThreatsLoseItButNullshadeSees() {
        val g = GameEngine(Operatives.buildConfig("phantom", emptyMap(), 5L).let { it.copy(baseStats = it.baseStats.apply { maxHp = 1e6f }) })
        g.debugStartPlan(LevelPlanner.plan(3, Random(1), null, true).copy(waves = emptyList()))
        // A far, harmless dummy keeps the room in combat (an empty room clears at once).
        g.spawnEnemyAt(Enemies.byId("trojan"), null, 60f, 60f, telegraph = false)!!.apply { speed = 0f; maxHp = 1e9f; hp = 1e9f }
        var t = 0f
        fun run(sec: Float) { var s = 0f; while (s < sec) { g.update(1f / 60f); s += 1f / 60f; t += 1f / 60f } }
        run(5f)
        assertFalse("visible early in the cycle", g.isCloaked())
        run(2.5f)
        assertTrue("cloaked in the last ${GameEngine.CLOAK_SECONDS}s of each ${GameEngine.CLOAK_CYCLE}s", g.isCloaked())
        // A threat on top of the cloaked operative can't hurt it.
        val hp = g.hp
        val e = g.spawnEnemyAt(Enemies.byId("malware"), null, g.px + 5f, g.py, telegraph = false)!!
        run(1.5f)
        assertEquals("no contact damage while cloaked", hp, g.hp, 0.01f)
        assertTrue(g.hiddenFrom(e))
        run(GameEngine.CLOAK_SECONDS)
        assertFalse(g.isCloaked())

        // The Nullshade Specter sees through it.
        val g2 = GameEngine(Operatives.buildConfig("phantom", emptyMap(), 5L))
        g2.debugStartPlan(LevelPlanner.bossPlan(120, Random(1)).copy(boss = Bosses.byId("nullshade"), glitchedBoss = false))
        var s = 0f
        while (s < 8f) { g2.update(1f / 60f); s += 1f / 60f }
        assertTrue(g2.isCloaked())
        val boss = assertNotNull(g2.boss).let { g2.boss!! }
        assertFalse("Nullshade sees the cloak", g2.hiddenFrom(boss))
    }

    @Test fun classSkinsAreEarnedPerClass() {
        val ti = OperativeSkins.TITANIUM
        val pu = OperativeSkins.PURPLE
        var p = PlayerProfile(classRecords = mapOf("technician" to ClassRecord(bestNormal = 100, bestHard = 99)))
        assertTrue(StoreManager.skinUnlocked(p, ti, "technician"))
        assertFalse("other classes earn it on their own", StoreManager.skinUnlocked(p, ti, "juggernaut"))
        assertFalse("PURPLE needs level 100 on Hard", StoreManager.skinUnlocked(p, pu, "technician"))
        p = p.copy(classRecords = p.classRecords + ("technician" to p.classRecords.getValue("technician").with("HARD", 100)))
        assertTrue(StoreManager.skinUnlocked(p, pu, "technician"))
        // Equip only on a class that has it; switching class falls back to the default look.
        p = StoreManager.selectClass(p, "technician")
        assertEquals("neon_operative", p.operativeBody)
        p = StoreManager.equipSkin(p, ti.id)!!
        assertEquals(ti.id, StoreManager.effectiveSkin(p).id)
        p = StoreManager.selectClass(p, "juggernaut")
        assertEquals("mech", p.operativeBody)
        assertEquals(OperativeSkins.DEFAULT.id, StoreManager.effectiveSkin(p).id)
        assertNull(StoreManager.equipSkin(p, ti.id))
        // Records keep the best per difficulty.
        val r = ClassRecord().with("MEDIUM", 40).with("MEDIUM", 30).with("HARD", 12)
        assertEquals(40, r.bestNormal); assertEquals(12, r.bestHard); assertEquals(0, r.bestEasy)
    }

    @Test fun cloakedThreatsNeitherShootNorCloseIn() {
        val cfg = Operatives.buildConfig("phantom", emptyMap(), 5L).let { it.copy(baseStats = it.baseStats.apply { maxHp = 1e6f; damage = 0.001f; orbCount = 0 }) }
        val g = GameEngine(cfg)
        g.debugStartPlan(LevelPlanner.plan(13, Random(1), null, true).copy(waves = emptyList()))
        val ids = listOf("sniffer", "sql_injector", "malware", "exploit", "poly_spitter", "enc_gunner")
        val es = ids.mapIndexed { i, id -> g.spawnEnemyAt(Enemies.byId(id), null, g.px + 250f * kotlin.math.cos(i * 1.05f), g.py + 250f * kotlin.math.sin(i * 1.05f), false)!! }
        var t = 0f
        var cloakedShots = 0
        var closestWhileCloaked = Float.MAX_VALUE
        var cloakedFor = 0f
        while (t < 20f) {
            val n0 = g.projectiles.items.count { it.active && !it.friendly }
            g.setInput(0f, 0f)
            g.update(1f / 60f); t += 1f / 60f
            if (!g.isCloaked()) continue
            cloakedFor += 1f / 60f
            cloakedShots += (g.projectiles.items.count { it.active && !it.friendly } - n0).coerceAtLeast(0)
            // Give them a second to turn away, then nobody should be on top of the operative.
            if (cloakedFor % GameEngine.CLOAK_SECONDS > 1.5f) for (e in es) if (e.active)
                closestWhileCloaked = minOf(closestWhileCloaked, com.cyberoperative.game.core.MathUtil.dist(e.x, e.y, g.px, g.py))
        }
        assertTrue("cloaked for ${cloakedFor}s", cloakedFor > 5f)
        assertEquals("no new shots at a cloaked operative", 0, cloakedShots)
        assertTrue("threats keep their distance while it is cloaked (closest $closestWhileCloaked)", closestWhileCloaked > 60f)
    }
}
