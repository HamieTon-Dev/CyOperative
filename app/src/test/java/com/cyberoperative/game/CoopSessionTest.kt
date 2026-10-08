package com.cyberoperative.game

import androidx.test.core.app.ApplicationProvider
import com.cyberoperative.game.audio.AudioManager
import com.cyberoperative.game.engine.Phase
import com.cyberoperative.game.net.CoopRoom
import com.cyberoperative.game.net.RoomPlayer
import com.cyberoperative.game.net.RoomState
import com.cyberoperative.game.net.RoomStatus
import com.cyberoperative.game.save.SaveRepository
import com.cyberoperative.game.ui.game.CoopLink
import com.cyberoperative.game.ui.game.GameSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** In-memory room: two ends sharing the same pipes, no server. */
class LoopbackRoom private constructor(
    override val isHost: Boolean,
    private val meta: MutableStateFlow<RoomState>,
    private val world: MutableSharedFlow<ByteArray>,
    private val input: MutableSharedFlow<ByteArray>
) : CoopRoom {
    override val roomId = "loop"
    override val state: Flow<RoomState> = meta
    override suspend fun setReady(me: RoomPlayer) = Result.success(Unit)
    override suspend fun start(seed: Long, difficulty: String, mode: String): Result<Unit> {
        meta.value = meta.value.copy(status = RoomStatus.PLAYING, seed = seed, difficulty = difficulty, mode = mode)
        return Result.success(Unit)
    }
    override fun sendWorld(bytes: ByteArray) { world.tryEmit(bytes) }
    override val worlds: Flow<ByteArray> = world
    override fun sendInput(bytes: ByteArray) { input.tryEmit(bytes) }
    override val inputs: Flow<ByteArray> = input
    override suspend fun end() { meta.value = meta.value.copy(status = RoomStatus.ENDED) }
    override suspend fun leave() {
        meta.value = if (isHost) meta.value.copy(hostOnline = false) else meta.value.copy(guestOnline = false)
    }
    fun drop() { meta.value = if (isHost) meta.value.copy(hostOnline = false) else meta.value.copy(guestOnline = false) }

    companion object {
        fun pair(state: RoomState): Pair<LoopbackRoom, LoopbackRoom> {
            val meta = MutableStateFlow(state)
            fun pipe() = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
            val world = pipe()
            val input = pipe()
            return LoopbackRoom(true, meta, world, input) to LoopbackRoom(false, meta, world, input)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CoopSessionTest {

    private fun sessions(): Triple<GameSession, GameSession, Pair<LoopbackRoom, LoopbackRoom>> {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val save = SaveRepository(ctx)
        val audio = AudioManager(ctx)
        val state = RoomState(
            roomId = "loop", status = RoomStatus.PLAYING,
            host = RoomPlayer("h", "HOSTY", mapOf("max_hp" to 10, "base_damage" to 10), 12),
            guest = RoomPlayer("g", "GUESTY", mapOf("base_damage" to 5), 4),
            hostOnline = true, guestOnline = true, seed = 99L, difficulty = "MEDIUM"
        )
        val rooms = LoopbackRoom.pair(state)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val host = GameSession(save, audio, coop = CoopLink(rooms.first, state, scope))
        val guest = GameSession(save, audio, coop = CoopLink(rooms.second, state, scope))
        return Triple(host, guest, rooms)
    }

    private fun play(host: GameSession, guest: GameSession, seconds: Float) {
        var t = 0f
        while (t < seconds && host.engine.phase != Phase.DEAD) {
            if (host.hud.phase == Phase.UPGRADE && !host.hud.waitingForPartner) host.chooseUpgrade(0)
            if (guest.hud.phase == Phase.UPGRADE && !guest.hud.waitingForPartner) guest.chooseUpgrade(0)
            // The guest trails the host.
            val g = guest.engine
            val h = g.operatives[0]
            val dx = h.px - g.localX
            val dy = h.py - g.localY
            val d = kotlin.math.sqrt(dx * dx + dy * dy)
            g.setInput(if (d > 90f) dx / d else 0f, if (d > 90f) dy / d else 0f)
            host.onFrame(1f / 30f)
            guest.onFrame(1f / 30f)
            t += 1f / 30f
        }
    }

    @Test fun hostAndGuestPlayTheSameRun() {
        val (host, guest, _) = sessions()
        assertFalse(host.showTutorial)
        assertTrue(guest.engine.mirror)
        play(host, guest, 60f)
        assertEquals(host.engine.level, guest.engine.level)
        assertEquals(host.engine.score, guest.engine.score)
        assertTrue(guest.hud.coop)
        assertEquals("HOSTY", guest.hud.partnerName)
        assertEquals("GUESTY", host.hud.partnerName)
        // Each built their own operative.
        val guestOp = host.engine.operatives[1]
        assertEquals(guestOp.build.owned(), guest.engine.operatives[1].build.owned())
        // Guest's base build comes from the guest's own permanent upgrades (no max HP bought).
        assertTrue(host.engine.operatives[0].build.stats.maxHp > guestOp.build.stats.maxHp || guestOp.build.owned().isNotEmpty())
        assertFalse(host.revive())
        assertEquals(0, host.hud.paidRevivesLeft)
    }

    @Test fun guestCarriesOnWhenTheHostDrops() {
        val (host, guest, rooms) = sessions()
        play(host, guest, 20f)
        rooms.first.drop()
        guest.onFrame(1f / 30f)
        assertFalse(guest.engine.mirror)
        assertTrue(guest.coopNotice != null)
        val level = guest.engine.level
        repeat(300) { guest.onFrame(1f / 30f) }
        assertTrue(guest.engine.level >= level)
    }

    @Test fun hostCarriesOnWhenTheGuestGoesSilent() {
        val (host, guest, _) = sessions()
        play(host, guest, 10f)
        // Guest stops sending entirely (e.g. app killed): the timeout removes it.
        repeat(30 * 10) { host.onFrame(1f / 30f) }
        assertTrue(host.engine.operatives[1].gone)
        assertTrue(host.coopNotice != null)
    }
}
