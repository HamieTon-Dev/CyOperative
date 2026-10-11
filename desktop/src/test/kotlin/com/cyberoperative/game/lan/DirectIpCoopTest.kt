package com.cyberoperative.game.lan

import com.cyberoperative.game.net.RoomPlayer
import com.cyberoperative.game.net.RoomStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Direct IP co-op over a real socket on this machine: lobby, start, snapshots, input, leave. */
class DirectIpCoopTest {
    @Test fun hostAndJoinOverLocalhost() = runBlocking {
        val hostSide = DirectIpCoopBackend()
        val guestSide = DirectIpCoopBackend()
        val host = hostSide.hostDirect(RoomPlayer(uid = "h", name = "HOSTY", opLevel = 7, permanent = mapOf("max_hp" to 3))).getOrThrow()
        val guest = guestSide.joinDirect("127.0.0.1", RoomPlayer(uid = "g", name = "GUESTY", opLevel = 4)).getOrThrow()
        withTimeout(5000) {
            val hs = host.state.first { it.guest != null && it.guestOnline }
            assertEquals("GUESTY", hs.guest!!.name)
            val gs = guest.state.first { it.host != null && it.hostOnline }
            assertEquals("HOSTY", gs.host!!.name)
            assertEquals(3, gs.host!!.permanent["max_hp"])
        }
        host.start(1234L, "HARD", "CAMPAIGN")
        withTimeout(5000) {
            val gs = guest.state.first { it.status == RoomStatus.PLAYING }
            assertEquals(1234L, gs.seed)
            assertEquals("HARD", gs.difficulty)
        }
        val world = ByteArray(20_000) { (it % 251).toByte() }
        val input = byteArrayOf(1, 2, 3, 4)
        withTimeout(5000) {
            val gotWorld = async(Dispatchers.IO) { guest.worlds.first() }
            val gotInput = async(Dispatchers.IO) { host.inputs.first() }
            kotlinx.coroutines.delay(100)
            host.sendWorld(world)
            guest.sendInput(input)
            assertArrayEquals(world, gotWorld.await())
            assertArrayEquals(input, gotInput.await())
        }
        guest.leave()
        withTimeout(5000) { assertFalse(host.state.first { !it.guestOnline }.guestOnline) }
        host.leave()
    }

    @Test fun addressesParse() {
        assertEquals("1.2.3.4" to DirectIpCoopBackend.PORT, DirectIpCoopBackend.parseAddress(" 1.2.3.4 "))
        assertEquals("1.2.3.4" to 5000, DirectIpCoopBackend.parseAddress("1.2.3.4:5000"))
        assertEquals("my-pc" to DirectIpCoopBackend.PORT, DirectIpCoopBackend.parseAddress("my-pc"))
    }
}
