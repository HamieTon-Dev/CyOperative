package com.cyberoperative.game

import com.cyberoperative.game.data.Upgrades
import com.cyberoperative.game.data.Weapons
import com.cyberoperative.game.engine.CoopCodec
import com.cyberoperative.game.engine.CoopInput
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.Phase
import com.cyberoperative.game.engine.RunBuild
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.engine.RunStats
import com.cyberoperative.game.engine.UpgradeOffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Owner, 2026-10-09: at most 7 weapons; cards are power-up, power-up, weapon. */
class WeaponSlotTest {

    private val sevenWeapons = Weapons.upgrades.take(7)

    @Test fun cardsArePowerUpPowerUpWeapon() {
        val b = RunBuild(RunStats())
        val rng = Random(4)
        repeat(200) {
            val offer = b.rollOffer(rng)
            assertEquals(3, offer.size)
            assertFalse(Upgrades.isWeapon(offer[0].def))
            assertFalse(Upgrades.isWeapon(offer[1].def))
            assertTrue("third card is a weapon: ${offer[2].def.id}", Upgrades.isWeapon(offer[2].def))
            b.take(offer[rng.nextInt(2)].def)
        }
    }

    @Test fun modifiersAndNodesAreNotWeapons() {
        assertFalse(Upgrades.isWeapon(Upgrades.MULTISHOT))
        assertFalse(Upgrades.isWeapon(Upgrades.PACKET_NODES))
        assertTrue(Upgrades.isWeapon(Upgrades.PLASMA_BEAM))
        assertTrue(Upgrades.isWeapon(Upgrades.ORBITAL_STRIKE))
        assertTrue(Weapons.upgrades.all { Upgrades.isWeapon(it) })
        assertEquals(7, Upgrades.MAX_WEAPONS)
    }

    @Test fun eighthWeaponNeedsASwap() {
        val g = GameEngine(RunConfig(seed = 8L))
        for (w in sevenWeapons) g.build.take(w)
        assertEquals(7, g.build.weapons().size)
        val newWeapon = Upgrades.ORBITAL_STRIKE
        val owned = sevenWeapons[2]
        g.debugOffer(listOf(UpgradeOffer(Upgrades.PAYLOAD_BOOST, 1), UpgradeOffer(Upgrades.KERNEL_OVERCLOCK, 1), UpgradeOffer(newWeapon, 1)))
        assertTrue(g.offerNeedsSlot(2))
        assertFalse(g.offerNeedsSlot(0))
        // Without a weapon to replace nothing happens.
        g.chooseUpgrade(2)
        assertEquals(0, g.build.level(newWeapon.id))
        assertEquals(Phase.UPGRADE, g.phase)
        // A weapon that isn't equipped can't be "replaced".
        g.chooseUpgrade(2, Upgrades.PLASMA_BEAM.id)
        assertEquals(0, g.build.level(newWeapon.id))
        // Swap: the old one is gone, the new one takes its slot.
        g.chooseUpgrade(2, owned.id)
        assertEquals(1, g.build.level(newWeapon.id))
        assertEquals(0, g.build.level(owned.id))
        assertEquals(7, g.build.weapons().size)
        assertFalse(owned.id in g.stats.weapons)
    }

    @Test fun levellingAnOwnedWeaponNeedsNoSlot() {
        val g = GameEngine(RunConfig(seed = 8L))
        for (w in sevenWeapons) g.build.take(w)
        val owned = sevenWeapons[0]
        g.debugOffer(listOf(UpgradeOffer(Upgrades.PAYLOAD_BOOST, 1), UpgradeOffer(Upgrades.KERNEL_OVERCLOCK, 1), UpgradeOffer(owned, 2)))
        assertFalse(g.offerNeedsSlot(2))
        g.chooseUpgrade(2)
        assertEquals(2, g.build.level(owned.id))
        assertEquals(7, g.build.weapons().size)
    }

    @Test fun shopNeverOffersAnEighthWeapon() {
        val b = RunBuild(RunStats())
        for (w in sevenWeapons) b.take(w)
        assertTrue(b.needsSlot(Upgrades.ORBITAL_STRIKE))
        assertFalse(b.needsSlot(sevenWeapons[0]))
        assertFalse(b.needsSlot(Upgrades.MULTISHOT))
    }

    @Test fun guestSwapTravelsOverTheWire() {
        val c = CoopInput(seq = 3, pickSerial = 2, pickIndex = 2, replaceIndex = 17)
        val d = CoopCodec.decodeInput(CoopCodec.encodeInput(c))!!
        assertEquals(17, d.replaceIndex)
        assertEquals(-1, CoopCodec.decodeInput(CoopCodec.encodeInput(CoopInput()))!!.replaceIndex)
    }
}

class StatCompareTest {
    @Test fun previewShowsWhatChangesAndInWhichDirection() {
        val b = RunBuild(RunStats())
        val lines = com.cyberoperative.game.engine.StatCompare.lines(b.stats, b.preview(Upgrades.PAYLOAD_BOOST))
        val dmg = lines.first { it.label == "Damage" }
        assertTrue(dmg.better)
        assertTrue(dmg.change.startsWith("+"))
        assertTrue(lines.any { it.label == "Main gun DPS" && it.better })
        // Nothing unrelated is listed.
        assertFalse(lines.any { it.label == "Max HP" })
        // Previewing never changes the build.
        assertEquals(0, b.level(Upgrades.PAYLOAD_BOOST.id))
    }

    @Test fun swapPreviewShowsTheLostWeaponInRed() {
        val b = RunBuild(RunStats())
        val old = Weapons.upgrades[0]
        b.take(old)
        val lines = com.cyberoperative.game.engine.StatCompare.lines(b.stats, b.preview(Weapons.upgrades[1], remove = old.id))
        val lost = lines.first { it.label == Weapons.all[0].name }
        assertFalse(lost.better)
        assertEquals("REMOVED", lost.after)
        val gained = lines.first { it.label == Weapons.all[1].name }
        assertTrue(gained.better)
        assertEquals("NEW", gained.change)
    }
}
