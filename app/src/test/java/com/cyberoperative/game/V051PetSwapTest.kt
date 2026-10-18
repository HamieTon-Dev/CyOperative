package com.cyberoperative.game

import com.cyberoperative.game.data.GearRarity
import com.cyberoperative.game.data.PetLoadout
import com.cyberoperative.game.engine.CoopCodec
import com.cyberoperative.game.engine.CoopInput
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.RunConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 0.51.0: pets change mid-run (solo, and the co-op guest through the host). */
class V051PetSwapTest {
    private val a = PetLoadout(1030, GearRarity.GLITCHED)
    private val b = PetLoadout(2100, GearRarity.GLITCHED_ENCRYPTED)

    @Test fun soloSwapsAtOnceWithFreshCooldowns() {
        val g = GameEngine(RunConfig(seed = 3L, pet = a))
        g.operatives[0].petAbilityCd[0] = 5f
        g.setPet(0, b)
        assertEquals(b, g.operatives[0].pet)
        assertEquals(-1f, g.operatives[0].petAbilityCd[0], 0f)
        g.setPet(0, null)
        assertNull(g.operatives[0].pet)
    }

    @Test fun theGuestsPetChangeTravelsToTheHostOnce() {
        val sent = CoopInput(seq = 4, petSerial = 2, pet = b.encode())
        val got = CoopCodec.decodeInput(CoopCodec.encodeInput(sent))!!
        assertEquals(2, got.petSerial)
        assertEquals(b, PetLoadout.decode(got.pet))
        assertEquals(b, PetLoadout.decode(b.encode()))
    }
}
