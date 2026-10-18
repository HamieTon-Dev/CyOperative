package com.cyberoperative.game

import com.cyberoperative.game.data.ArenaShape
import com.cyberoperative.game.data.ObstacleKind
import com.cyberoperative.game.engine.Arena
import com.cyberoperative.game.engine.ArenaGenerator
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 0.53.2 (owner): a square counter, few barriers, all flush to the walls, none in the clear zone. */
class V053ShopCounterTest {
    @Test fun barriersNeverEnterTheClearZoneAndTheCounterCanBeWalkedRound() {
        for (wide in listOf(false, true)) {
            ArenaShape.wide = wide
            try {
                val zone = ArenaGenerator.shopKeepOut()
                for (seed in 1L..300L) {
                    val t = ArenaGenerator.shopRoom(Random(seed))
                    val counter = t.obstacles.single { it.kind == ObstacleKind.SHOP_COUNTER }.rect
                    assertTrue(counter.height >= 160f && counter.width <= 360f)
                    val fixtures = t.obstacles.filter { it.kind != ObstacleKind.SHOP_COUNTER }
                    assertTrue("seed $seed: ${fixtures.size} fixtures", fixtures.size <= 2)
                    for (f in fixtures) {
                        val r = f.rect
                        assertTrue("seed $seed wide=$wide: $r in the clear zone", !r.intersectsRect(zone.left, zone.top, zone.right, zone.bottom))
                        assertTrue("seed $seed: $r not on a wall", r.left <= 0.5f || r.right >= ArenaShape.width - 0.5f)
                    }
                    val a = Arena(t)
                    assertTrue(ArenaGenerator.reachable(a))
                    // Both ends of the counter leave a wide walk.
                    assertTrue(counter.left >= 150f && ArenaShape.width - counter.right >= 150f)
                }
            } finally { ArenaShape.wide = false }
        }
    }
}
