package com.cyberoperative.game

import com.cyberoperative.game.engine.CampaignMap
import com.cyberoperative.game.engine.Dir
import com.cyberoperative.game.engine.ExitDoor
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.GameMode
import com.cyberoperative.game.engine.LootKind
import com.cyberoperative.game.engine.Phase
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.engine.RunSnapshot
import com.cyberoperative.game.engine.RunStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The world-map campaign (owner, 2026-10-11): 15 worlds × 10 rooms + a boss, doors that follow the map. */
class WorldMapCampaignTest {

    private fun campaign(seed: Long = 5L) = GameEngine(
        RunConfig(baseStats = RunStats().apply { maxHp = 1e7f }, seed = seed, powerGovernor = false, consumables = true, mode = GameMode.CAMPAIGN)
    )

    /** Kill everything until the room's doors open. */
    private fun clear(g: GameEngine) {
        var guard = 0
        while (g.phase != Phase.PORTAL && guard++ < 60 * 90) {
            for (e in g.enemies.items) if (e.active) g.killEnemy(e)
            g.update(1f / 60f)
        }
        assertEquals("room ${g.room} cleared", Phase.PORTAL, g.phase)
    }

    /** Stand in [d] and let the transition run. */
    private fun walkThrough(g: GameEngine, d: ExitDoor) {
        val o = g.operatives[0]
        when (d.side) {
            0 -> { o.px = d.x; o.py = g.arena.portalY + 10f }
            3 -> { o.px = d.x; o.py = g.arena.height - 30f }
            1 -> { o.px = 14f; o.py = d.y }
            else -> { o.px = g.arena.width - 14f; o.py = d.y }
        }
        repeat(90) { g.update(1f / 60f) }
    }

    @Test
    fun roomSlideFollowsTheDoorYouWalkThrough() {
        // Walk the first several rooms: every room change slides along the door's wall.
        val g = campaign(seed = 21L)
        val seen = HashSet<Int>()
        repeat(6) {
            clear(g)
            val d = forward(g)
            val o = g.operatives[0]
            when (d.side) {
                0 -> { o.px = d.x; o.py = g.arena.portalY + 10f }
                3 -> { o.px = d.x; o.py = g.arena.height - 30f }
                1 -> { o.px = 14f; o.py = d.y }
                else -> { o.px = g.arena.width - 14f; o.py = d.y }
            }
            var guard = 0
            while (g.phase != Phase.TRANSITION && guard++ < 30) g.update(1f / 60f)
            assertEquals(Phase.TRANSITION, g.phase)
            assertEquals("slide follows door side", if (d.target == GameEngine.NEXT_WORLD) 0 else d.side, g.slideSide)
            seen += g.slideSide
            repeat(90) { g.update(1f / 60f) }
        }
        assertTrue("map should turn at least once: $seen", seen.size >= 2)
        // The guest sees the same direction.
        val w = g.captureWorld(1, emptyList())
        val back = com.cyberoperative.game.engine.CoopCodec.decodeWorld(com.cyberoperative.game.engine.CoopCodec.encodeWorld(w))!!
        assertEquals(g.slideSide, back.slideSide)
    }

    private fun forward(g: GameEngine): ExitDoor = g.exitDoors.first { it.target == g.room + 1 || it.target == GameEngine.NEXT_WORLD }

    @Test
    fun worldMapsAreTenConnectedRoomsThenABossToTheNorth() {
        for (seed in 1L..40L) for (w in 1..CampaignMap.WORLDS) {
            val m = CampaignMap.generate(seed, w)
            assertEquals(CampaignMap.ROOMS + 1, m.rooms.size)
            assertEquals("no two rooms share a cell", m.rooms.size, m.rooms.map { it.gx to it.gy }.toSet().size)
            for (i in 1 until m.rooms.size) m.dir(i - 1, i) // throws if not adjacent
            assertEquals("boss north of room 10", Dir.N, m.dir(CampaignMap.ROOMS - 1, CampaignMap.BOSS_INDEX))
            assertTrue(m.rooms.last().boss)
        }
        assertEquals("same seed, same map", CampaignMap.generate(9L, 3).rooms, CampaignMap.generate(9L, 3).rooms)
        // Not just a straight line: the paths turn.
        assertTrue((1L..20L).any { s -> CampaignMap.generate(s, 1).rooms.map { it.gx }.toSet().size > 1 })
    }

    @Test
    fun theOldLevelCurveMapsOntoWorlds() {
        assertEquals(1, CampaignMap.levelFor(1, 0))
        assertEquals(9, CampaignMap.levelFor(1, 9))
        assertEquals(10, CampaignMap.levelFor(1, CampaignMap.BOSS_INDEX))
        assertEquals(150, CampaignMap.levelFor(15, CampaignMap.BOSS_INDEX))
    }

    @Test
    fun doorsStayShutUntilTheRoomIsClearThenLeadOnAndBack() {
        val g = campaign()
        assertEquals(1, g.world); assertEquals(0, g.room)
        assertEquals("room 1 only has a way forward", 1, g.exitDoors.size)
        assertEquals(Phase.COMBAT, g.phase)
        assertFalse(g.portalOpen)
        clear(g)
        assertTrue(0 in g.clearedRooms)
        val f = forward(g)
        walkThrough(g, f)
        assertEquals(1, g.room)
        // You walk in on the side facing the room you came from.
        val back = g.exitDoors.first { it.target == 0 }
        assertEquals("entered by the back door's wall", g.worldMap.dir(1, 0).side, back.side)
        val o = g.operatives[0]
        val (bx, by) = when (back.side) { 0 -> back.x to 0f; 3 -> back.x to g.arena.height; 1 -> 0f to back.y; else -> g.arena.width to back.y }
        assertTrue("spawned near that door", kotlin.math.hypot(o.px - bx, o.py - by) < 260f)
        assertEquals(Phase.COMBAT, g.phase)
        clear(g)
        // Back into the first room: empty, doors open.
        walkThrough(g, back)
        assertEquals(0, g.room)
        assertTrue(g.revisit)
        assertEquals(Phase.PORTAL, g.phase)
        assertTrue(g.enemies.items.none { it.active })
    }

    @Test
    fun theShopLeadsBackIntoTheRoomItOpenedIn() {
        val g = campaign()
        clear(g)
        walkThrough(g, forward(g))
        clear(g)
        g.offerShop()
        assertTrue(g.shopGateOpen)
        val shopY = g.shopGateY
        val right = g.shopGateRight
        val o = g.operatives[0]
        o.px = if (right) g.arena.width - 14f else 14f; o.py = shopY
        repeat(90) { g.update(1f / 60f) }
        assertTrue("in the shop", g.inShop)
        // Leave by the shop's gate.
        o.px = g.arena.portalX; o.py = g.arena.portalY + 10f
        repeat(90) { g.update(1f / 60f) }
        assertFalse(g.inShop)
        assertEquals("back in room 2", 1, g.room)
        assertTrue(g.revisit)
        assertEquals("at the shop doorway", shopY, o.py, 60f)
        assertFalse("the shop is used up", g.shopGateOpen)
    }

    @Test
    fun aWorldBossPaysFourDropsAndOpensTheNextWorld() {
        val g = campaign()
        g.debugEnterRoom(CampaignMap.BOSS_INDEX)
        assertNotNull(g.boss)
        g.killEnemy(g.boss!!)
        var guard = 0
        while (g.phase != Phase.PORTAL && guard++ < 60 * 30) g.update(1f / 60f)
        assertTrue("4+ upgrade drops", g.loot.count { it.kind == LootKind.UPGRADE } >= GameEngine.CAMPAIGN_BOSS_PICKS)
        val gate = g.exitDoors.first { it.target == GameEngine.NEXT_WORLD }
        walkThrough(g, gate)
        assertEquals(2, g.world)
        assertEquals(0, g.room)
        assertTrue(g.clearedRooms.isEmpty())
    }

    @Test
    fun nullshadeOnlyFromWorldTwelveAndAlwaysInWorldFifteen() {
        var late = 0
        for (seed in 1L..60L) {
            val g = campaign(seed)
            for (w in 1..11) assertTrue(g.campaignPlan(w, CampaignMap.BOSS_INDEX, false).boss!!.id != "nullshade")
            assertEquals("nullshade", g.campaignPlan(15, CampaignMap.BOSS_INDEX, false).boss!!.id)
            if ((12..14).any { g.campaignPlan(it, CampaignMap.BOSS_INDEX, false).boss!!.id == "nullshade" }) late++
        }
        assertTrue("worlds 12–14 sometimes ($late / 60)", late in 5..58)
    }

    @Test
    fun finishingWorldFifteenOffersEndless() {
        val g = campaign()
        g.debugEnterWorld(15)
        g.debugEnterRoom(CampaignMap.BOSS_INDEX)
        g.killEnemy(g.boss!!)
        var guard = 0
        while (!g.campaignComplete && guard++ < 60 * 30) g.update(1f / 60f)
        assertTrue(g.campaignComplete)
        assertTrue("no next-world gate", g.exitDoors.none { it.target == GameEngine.NEXT_WORLD })
        assertTrue(g.answerCampaignComplete(continueEndless = true))
        assertEquals(GameMode.ENDLESS, g.mode)
        assertEquals("WORLD ??? · ROOM ???", g.roomLabel())
        val o = g.operatives[0]
        o.px = g.arena.portalX; o.py = g.arena.portalY + 10f
        repeat(90) { g.update(1f / 60f) }
        assertEquals("on into endless", 151, g.level)
    }

    @Test
    fun continueRestoresTheWorldRoomAndClearedRooms() {
        val g = campaign()
        clear(g)
        walkThrough(g, forward(g))
        repeat(60) { g.update(1f / 60f) }
        val snap = RunSnapshot.decodeOrNull(g.snapshot()!!.encode())!!
        val r = GameEngine(g.config, snap)
        assertEquals(GameMode.CAMPAIGN, r.mode)
        assertEquals(1, r.room)
        assertEquals(setOf(0), r.clearedRooms)
        assertEquals(g.worldMap.rooms, r.worldMap.rooms)
        assertEquals(g.plan.arena.obstacles, r.plan.arena.obstacles)
        assertEquals(g.exitDoors.map { it.side to it.target }, r.exitDoors.map { it.side to it.target })
    }

    @Test
    fun oldSavesContinueAsEndlessAndOneRoomEndlessIsDropped() {
        val g = GameEngine(RunConfig(baseStats = RunStats(), seed = 3L))
        val raw = g.snapshot()!!.copy(format = 1, mode = "CAMPAIGN").encode()
        assertEquals("ENDLESS", RunSnapshot.decodeOrNull(raw)!!.mode)
        val oldEndless = g.snapshot()!!.copy(format = 1, mode = "ENDLESS").encode()
        assertEquals(null, RunSnapshot.decodeOrNull(oldEndless))
    }
}
