package com.cyberoperative.game

import com.cyberoperative.game.audio.BossMusicProfiles
import com.cyberoperative.game.audio.MusicLibrary
import com.cyberoperative.game.audio.MusicTier
import com.cyberoperative.game.data.Bosses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Boss music per tier: every boss has its own sound, in its tier's tracks. */
class BossMusicTest {
    @Test fun everyBossHasAProfileOnARealTrackInItsTier() {
        for (b in Bosses.roster) {
            assertTrue("${b.id} has a profile", BossMusicProfiles.has(b.id))
            val m = BossMusicProfiles.forBoss(b)
            assertNotNull("${b.id} track ${m.track}", MusicLibrary.byId(m.track))
            assertTrue("${b.id}: signature track is in its tier", m.track in m.tier.tracks)
            for (t in m.tier.tracks) assertNotNull(MusicLibrary.byId(t))
        }
    }

    @Test fun tiersFollowTheRoster() {
        for (b in Bosses.roster) {
            val tier = BossMusicProfiles.forBoss(b).tier
            when {
                b.id in Bosses.GAMMA_IDS -> assertEquals(b.id, MusicTier.IV, tier)
                b in Bosses.classics -> assertTrue(b.id, tier == MusicTier.I || tier == MusicTier.II)
                else -> assertEquals(b.id, MusicTier.III, tier)
            }
        }
    }

    @Test fun noTwoBossesSoundTheSame() {
        val sigs = Bosses.roster.map { b -> BossMusicProfiles.forBoss(b).let { listOf(it.track, it.pitch, it.bassDb, it.trebleDb, it.reverb, it.speedAdj) } }
        assertEquals(sigs.size, sigs.toSet().size)
    }

    @Test fun safeRangesAndRisingTempo() {
        for (b in Bosses.roster) {
            val m = BossMusicProfiles.forBoss(b)
            assertTrue("${b.id} pitch", m.pitch(false) in 0.75f..1.2f && m.pitch(true) <= 1.25f)
            assertTrue("${b.id} bass/treble", m.bassDb in -12f..12f && m.trebleDb in -12f..12f)
            for (ph in 0..2) assertTrue("${b.id} speed", m.speed(ph) in 1.0f..1.85f)
            assertTrue(m.speed(2) > m.speed(1) && m.speed(1) > m.speed(0))
        }
    }

    @Test fun harderTiersRunFaster() {
        assertTrue(MusicTier.I.speed < MusicTier.II.speed && MusicTier.II.speed < MusicTier.III.speed && MusicTier.III.speed < MusicTier.IV.speed)
    }
}
