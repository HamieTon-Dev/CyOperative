package com.cyberoperative.game

import com.cyberoperative.game.data.GearRarity
import com.cyberoperative.game.data.Incubator
import com.cyberoperative.game.data.PetLoadout
import com.cyberoperative.game.data.Pets
import com.cyberoperative.game.engine.Difficulty
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.GameMode
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.engine.RunStats
import com.cyberoperative.game.meta.ClockNow
import com.cyberoperative.game.meta.Incubators
import com.cyberoperative.game.meta.StoreManager
import com.cyberoperative.game.save.PlayerProfile
import com.cyberoperative.game.save.SaveRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Companions (0.38.0): eggs, the incubator and its clock, Inferno Stones, pets in runs. */
class V038PetsTest {
    private val min = 60_000L
    private fun egg(r: GearRarity, uid: Long = 1) = Pets.rollEgg(Random(uid), uid, r)
    private fun now(mono: Long, wall: Long = 1_000_000L + mono, net: Long? = null, boot: String = "b1") = ClockNow(boot, mono, wall, net)
    private fun withEgg(r: GearRarity) = Incubators.send(PlayerProfile(eggs = listOf(egg(r, 7)), petSeq = 10), 7, now(0))!!

    @Test fun hatchTimesAndStones() {
        assertEquals(5 * min, Pets.hatchMs(GearRarity.COMMON))
        assertEquals(60 * min, Pets.hatchMs(GearRarity.EPIC))
        assertEquals(480 * min, Pets.hatchMs(GearRarity.ABYSSAL))
        assertEquals(0.85f, Pets.timeFactor(1), 1e-4f)
        assertEquals(0.10f, Pets.timeFactor(6), 1e-4f)
        assertEquals(0.10f, Pets.timeFactor(9), 1e-4f)
        // The owner's example: a 2:00:00 egg with six stones takes 0:12:00.
        assertEquals(12 * min, (120 * min * Pets.timeFactor(6)).toLong())
    }

    @Test fun eggHatchesAfterRealTimeOnTheBootClock() {
        var p = withEgg(GearRarity.EPIC)
        assertNotNull(p.incubator.slot(0))
        p = Incubators.tick(p, now(59 * min))
        assertTrue(p.pets.isEmpty())
        assertEquals(60_000L, p.incubator.remainingMs(0))
        p = Incubators.tick(p, now(60 * min + 1))
        assertEquals(1, p.pets.size)
        assertTrue(p.pets[0].fresh)
        assertNull(p.incubator.slot(0))
    }

    @Test fun changingTheDeviceClockDoesNothing() {
        var p = withEgg(GearRarity.RARE)
        // Wall clock jumps a week ahead; the boot clock moved 1 minute.
        p = Incubators.tick(p, now(1 * min, wall = 1_000_000L + 7 * 24 * 60 * min))
        assertTrue(p.pets.isEmpty())
        assertEquals(19 * min, p.incubator.remainingMs(0))
    }

    @Test fun afterARebootTimeNeedsNetworkTime() {
        var p = withEgg(GearRarity.RARE)
        p = Incubators.tick(p, now(5 * min, net = 1_000_000L + 5 * min))  // verified
        // Rebooted, offline: paused.
        p = Incubators.tick(p, now(10, wall = 1_000_000L + 400 * min, boot = "b2"))
        assertTrue(p.incubator.waitingForTime)
        assertTrue(p.pets.isEmpty())
        // Online: the real gap (10 minutes) counts, not the clock the player set.
        p = Incubators.tick(p, now(20, wall = 1_000_000L + 400 * min, net = 1_000_000L + 15 * min, boot = "b2"))
        assertFalse(p.incubator.waitingForTime)
        assertEquals(5 * min, p.incubator.remainingMs(0))
    }

    @Test fun stonesSpeedUpTheRestAndQueueRunsOn() {
        var p = withEgg(GearRarity.EPIC).copy(infernoStones = 6)
        p = Incubators.tick(p, now(30 * min))
        p = Incubators.setStones(p, 6, now(30 * min))
        assertEquals(6, p.incubator.stones)
        assertEquals(3 * min, p.incubator.remainingMs(0))
        // Can't socket more than owned.
        assertEquals(2, Incubators.setStones(p.copy(infernoStones = 2), 6, now(30 * min)).incubator.stones)
        // Two more eggs go into incubators 2 and 3 and hatch alongside (0.45.0).
        p = p.copy(eggs = listOf(egg(GearRarity.COMMON, 20), egg(GearRarity.COMMON, 21)))
        p = Incubators.send(p, 20, now(30 * min))!!
        p = Incubators.send(p, 21, now(30 * min))!!
        assertEquals(3, p.incubator.eggsIn.size)
        p = Incubators.tick(p, now(30 * min + 31_000L))
        assertEquals(2, p.pets.size)                 // the commons (5 min × 0.1)
        p = Incubators.tick(p, now(30 * min + 3 * min + 10))
        assertEquals(3, p.pets.size)
        assertFalse(p.incubator.busy)
    }

    @Test fun threeIncubatorsAndTakeBack() {
        var p = withEgg(GearRarity.RARE).copy(eggs = (20L..24L).map { egg(GearRarity.COMMON, it) })
        for (u in 20L..21L) p = Incubators.send(p, u, now(0))!!
        assertNull(Incubators.send(p, 22, now(0)))   // all three busy
        p = Incubators.takeBack(p, 20, now(0))!!
        assertEquals(null, p.incubator.slot(1))
        assertTrue(p.eggs.any { it.uid == 20L })
        assertNotNull(Incubators.send(p, 22, now(0)))
    }

    @Test fun oldSavesMoveIntoTheThreeIncubators() {
        val old = com.cyberoperative.game.data.Incubator(current = egg(GearRarity.EPIC, 1), progress = 5 * min,
            queue = listOf(egg(GearRarity.RARE, 2), egg(GearRarity.RARE, 3), egg(GearRarity.RARE, 4)), slots = emptyList())
        val p = Incubators.migrate(PlayerProfile(incubator = old))
        assertEquals(listOf(1L, 2L, 3L), p.incubator.eggsIn.map { it.uid })
        assertEquals(5 * min, p.incubator.slot(0)!!.progress)
        assertEquals(listOf(4L), p.eggs.map { it.uid })
        assertNull(p.incubator.current)
    }

    @Test fun eggArtMatchesRarityColourAndNamesRead() {
        for (r in GearRarity.entries) {
            val e = Pets.rollEgg(Random(3), 1, r)
            assertEquals(r, Pets.eggArtRarity(e.art))
            assertTrue(e.name.endsWith("Egg"))
        }
        assertTrue(Pets.petName(0).contains(' '))
        assertEquals(PetLoadout(5, GearRarity.EPIC), PetLoadout.decode(PetLoadout(5, GearRarity.EPIC).encode()))
        assertNull(PetLoadout.decode("junk"))
    }

    @Test fun storeEggsCostDiamonds() {
        val p = PlayerProfile(diamonds = 700)
        val q = StoreManager.buyEgg(p, Pets.StoreEgg.EPIC, Random(1))!!
        assertEquals(100L, q.diamonds)
        assertTrue(q.eggs.single().rarity >= GearRarity.EPIC)
        assertNull(StoreManager.buyEgg(q, Pets.StoreEgg.EPIC))
    }

    @Test fun equippedPetFollowsAndFights() {
        val cfg = RunConfig(baseStats = RunStats(), seed = 3L, mode = GameMode.ENDLESS, difficulty = Difficulty.MEDIUM, pet = PetLoadout(10, GearRarity.LEGENDARY))
        val g = GameEngine(cfg)
        val hpBefore = g.enemies.items.filter { it.active }.sumOf { it.hp.toDouble() }
        repeat(600) { g.setInput(0f, 0f); g.update(1f / 60f) }
        val o = g.operatives[0]
        assertNotNull(o.pet)
        assertTrue("pet near operative", kotlin.math.abs(o.petX - o.px) < 80f && kotlin.math.abs(o.petY - o.py) < 80f)
        assertTrue(hpBefore >= 0)
    }

    @Test fun oldSavesLoadWithNoPets() {
        val p = SaveRepository.decodeOrNull("""{"version":1,"euros":5}""")!!
        assertTrue(p.eggs.isEmpty() && p.pets.isEmpty() && p.incubator == Incubator())
        val back = SaveRepository.decodeOrNull(SaveRepository.encode(withEgg(GearRarity.EPIC)))!!
        assertEquals(withEgg(GearRarity.EPIC).incubator, back.incubator)
    }
}
