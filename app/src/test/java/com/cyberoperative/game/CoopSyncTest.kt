package com.cyberoperative.game

import com.cyberoperative.game.engine.AllyConfig
import com.cyberoperative.game.engine.CoopCodec
import com.cyberoperative.game.engine.CoopInput
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.GameSound
import com.cyberoperative.game.engine.Phase
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.engine.RunStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Host engine ⇄ guest mirror over the real wire format, no network. */
class CoopSyncTest {

    private class Link(seed: Long) {
        val cfg = RunConfig(seed = seed, ally = AllyConfig(RunStats()))
        val host = GameEngine(cfg)
        val guest = GameEngine(cfg).also { it.enterMirror(1) }
        val bot = Bot(host, dodge = true)
        var seq = 0
        var inputSeq = 0
        var pickSerial = 0
        val sizes = ArrayList<Int>()
        private val pendingSounds = ArrayList<GameSound>()
        var frames = 0

        /** One 1/30 s frame on both devices; a snapshot every 3rd frame (10 Hz). */
        fun frame(guestStick: (GameEngine) -> Pair<Float, Float>) {
            val (sx, sy) = guestStick(guest)
            guest.setInput(sx, sy)
            guest.mirrorTick(1f / 30f)
            var pick = pickSerial
            val me = guest.operatives[1]
            if (guest.phase == Phase.UPGRADE && me.offer.isNotEmpty() && me.pendingUpgrades > 0) pick = pickSerial + 1
            val input = CoopCodec.decodeInput(CoopCodec.encodeInput(CoopInput(++inputSeq, guest.localX, guest.localY, guest.localFacing, guest.localMoving, pick, 0, 0, guest.localLevel)))!!
            host.applyInput(1, input)
            pickSerial = pick
            bot.step(1f / 30f)
            pendingSounds += host.sounds
            host.sounds.clear()
            if (++frames % 3 == 0) {
                val bytes = CoopCodec.encodeWorld(host.captureWorld(++seq, pendingSounds))
                pendingSounds.clear()
                sizes += bytes.size
                guest.applyWorld(CoopCodec.decodeWorld(bytes)!!)
            }
            guest.sounds.clear()
        }
    }

    @Test fun inputRoundTrips() {
        val c = CoopInput(7, 123.5f, 456f, 1.25f, true, 3, 2, 1)
        val d = CoopCodec.decodeInput(CoopCodec.encodeInput(c))
        assertNotNull(d)
        assertEquals(c.seq, d!!.seq)
        assertEquals(c.x, d.x, 0.5f)
        assertEquals(c.y, d.y, 0.5f)
        assertEquals(c.facing, d.facing, 1e-3f)
        assertEquals(c.pickSerial, d.pickSerial)
        assertEquals(c.pickIndex, d.pickIndex)
    }

    @Test fun guestMirrorsTheHostAndPlaysAlong() {
        val link = Link(31L)
        var t = 0f
        var maxLevel = 1
        while (t < 150f && link.host.phase != Phase.DEAD) {
            // The guest follows the host around.
            link.frame { g ->
                val h = g.operatives[0]
                val dx = h.px - g.localX
                val dy = h.py - g.localY
                val d = kotlin.math.sqrt(dx * dx + dy * dy)
                if (d > 80f) dx / d to dy / d else 0f to 0f
            }
            t += 1f / 30f
            if (link.frames % 3 == 0) {
                assertEquals(link.host.level, link.guest.level)
                assertEquals(link.host.phase, link.guest.phase)
                assertEquals(link.host.enemies.countActive(), link.guest.enemies.countActive())
                assertEquals(link.host.score, link.guest.score)
                // The host follows where the guest says it is.
                val ally = link.host.operatives[1]
                if (ally.alive && link.host.phase == Phase.COMBAT && link.host.slideIn <= 0f && link.host.phaseTimer > 1f) {
                    assertTrue("host ${ally.px} vs guest ${link.guest.localX}", kotlin.math.abs(ally.px - link.guest.localX) < 60f)
                }
            }
            maxLevel = maxOf(maxLevel, link.host.level)
        }
        assertTrue("reached level $maxLevel", maxLevel >= 2 || link.host.phase == Phase.DEAD)
        // The guest picked its own cards through the wire.
        assertTrue(link.host.operatives[1].build.owned().isNotEmpty())
        val avg = link.sizes.average()
        val max = link.sizes.max()
        println("CO-OP SNAPSHOT bytes: avg ${"%.0f".format(avg)}, max $max over ${link.sizes.size} snapshots " +
            "(~${"%.0f".format(avg * 10 * 3600 / 1_000_000)} MB/hour at 10 Hz)")
        assertTrue("snapshots stay small (avg $avg)", avg < 4000)
    }

    @Test fun guestTakesOverWhenTheHostLeaves() {
        val link = Link(44L)
        repeat(300) { link.frame { 0f to 0f } }
        val levelBefore = link.guest.level
        link.guest.promoteToSolo()
        assertTrue(!link.guest.mirror)
        assertTrue(link.guest.operatives[0].gone)
        // The promoted run plays on as a normal solo game.
        val bot = Bot(link.guest, dodge = true)
        var t = 0f
        while (t < 40f && link.guest.phase != Phase.DEAD) { bot.step(1f / 30f); t += 1f / 30f }
        assertTrue(link.guest.level >= levelBefore)
        assertTrue(link.guest.kills >= link.host.kills.coerceAtMost(1) || link.guest.phase == Phase.DEAD)
    }
}
