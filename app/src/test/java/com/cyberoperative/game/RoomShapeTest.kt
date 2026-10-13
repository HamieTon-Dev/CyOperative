package com.cyberoperative.game

import com.cyberoperative.game.data.ArenaShape
import com.cyberoperative.game.engine.CampaignMap
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.GameMode
import com.cyberoperative.game.engine.RunConfig
import org.junit.Assert.assertEquals
import org.junit.Test

/** 0.30.3: a run started in landscape builds its next rooms (the boss room too) for portrait once the screen is portrait. */
class RoomShapeTest {
    @Test fun nextRoomFollowsTheScreen() {
        try {
            ArenaShape.wide = true
            val g = GameEngine(RunConfig(seed = 4L, mode = GameMode.CAMPAIGN, consumables = true))
            assertEquals(ArenaShape.WIDE_WIDTH, g.arena.width, 0.1f)
            g.nextRoomWide = false
            g.debugEnterRoom(CampaignMap.BOSS_INDEX)
            assertEquals(ArenaShape.PORTRAIT_WIDTH, g.arena.width, 0.1f)
            g.nextRoomWide = true
            g.debugEnterRoom(1)
            assertEquals(ArenaShape.WIDE_WIDTH, g.arena.width, 0.1f)
        } finally {
            ArenaShape.wide = false
        }
    }
}
