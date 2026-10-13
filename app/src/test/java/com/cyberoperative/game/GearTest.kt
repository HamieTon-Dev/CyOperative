package com.cyberoperative.game

import com.cyberoperative.game.data.Gear
import com.cyberoperative.game.data.GearArt
import com.cyberoperative.game.data.GearRarity
import com.cyberoperative.game.data.GearSlot
import com.cyberoperative.game.data.GunStyle
import com.cyberoperative.game.data.OpProgress
import com.cyberoperative.game.data.Operatives
import com.cyberoperative.game.engine.CoopCodec
import com.cyberoperative.game.engine.CoopWorld
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.LootKind
import com.cyberoperative.game.engine.RunConfig
import com.cyberoperative.game.meta.Characters
import com.cyberoperative.game.meta.GearBank
import com.cyberoperative.game.save.CharacterLook
import com.cyberoperative.game.save.PlayerProfile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Data Vault update phase 2: gear rolls, drop odds, bank / equip / vault, characters, stats. */
class GearTest {

    @Test fun itemLevelStaysWithinTwoAndNeverPastMax() {
        val rng = Random(1)
        repeat(2000) {
            val l = rng.nextInt(1, 61)
            val i = Gear.itemLevel(l, rng)
            assertTrue(i in (l - 2).coerceAtLeast(1)..(l + 2).coerceAtMost(OpProgress.MAX_LEVEL))
        }
        repeat(200) { assertTrue(Gear.itemLevel(60, rng) <= 60) }
    }

    @Test fun infernoRaritiesOnlyOnInferno() {
        val normal = Gear.odds(boss = true, inferno = 0)
        for (r in GearRarity.entries) if (r.minInferno > 0) assertEquals(0f, normal[r.ordinal], 0f)
        val i1 = Gear.odds(boss = true, inferno = 1)
        assertTrue(i1[GearRarity.ENCRYPTED.ordinal] > 0f)
        assertEquals(0f, i1[GearRarity.INFERNAL.ordinal], 0f)
        val i10 = Gear.odds(boss = true, inferno = 10)
        assertTrue(i10[GearRarity.CRYPTO.ordinal] > 0f)
        // The top stays close to zero and every step is rarer.
        assertTrue(i10[GearRarity.CRYPTO.ordinal] < 1f)
        for (i in 4 until i10.size - 1) assertTrue(i10[i] > i10[i + 1])
        assertEquals(100f, i10.sum(), 0.01f)
    }

    @Test fun bossesAndLateWorldsRollBetter() {
        val elite = Gear.odds(boss = false, inferno = 0)
        val boss = Gear.odds(boss = true, inferno = 0)
        val late = Gear.odds(boss = true, inferno = 0, lateBoost = true)
        assertTrue(boss[GearRarity.LEGENDARY.ordinal] > elite[GearRarity.LEGENDARY.ordinal])
        assertTrue(late[GearRarity.LEGENDARY.ordinal] > boss[GearRarity.LEGENDARY.ordinal])
    }

    @Test fun rollsHaveArtNamesAndAffixes() {
        val rng = Random(5)
        for (slot in GearSlot.entries) for (r in GearRarity.entries) {
            val g = Gear.roll(rng, 1, 30, r, slot)
            assertTrue(g.art in 0 until GearArt.count(slot, r.artTier))
            assertEquals(r.affixes, g.affixes.size)
            assertEquals(g.affixes.size, g.affixes.map { it.stat }.toSet().size)
            assertTrue(g.name.isNotBlank())
            assertTrue(g.base > 0f)
            if (slot == GearSlot.SECONDARY) assertNotNull(g.sidearm)
        }
    }

    @Test fun sameArtHigherRarityGetsANewName() {
        val a = Gear.roll(Random(9), 1, 40, GearRarity.LEGENDARY, GearSlot.HELMET)
        val b = a.copy(rarity = GearRarity.CRYPTO)
        assertFalse(a.name == b.name)
        assertEquals(GearArt.cell(a.slot, a.rarity.artTier, a.art), GearArt.cell(b.slot, b.rarity.artTier, b.art))
    }

    @Test fun classWeaponsExistForEveryClassAndOnlyFitTheirClass() {
        val classes = (0 until 100).mapNotNull { GunStyle.forArt(it).classId }.toSet()
        assertEquals(Operatives.all.map { it.id }.toSet(), classes)
        val art = (0 until 100).first { GunStyle.forArt(it).classId == "juggernaut" }
        val gun = Gear.roll(Random(2), 1, 10, GearRarity.RARE, GearSlot.PRIMARY).copy(art = art)
        assertTrue(gun.usableBy("juggernaut"))
        assertFalse(gun.usableBy("phantom"))
    }

    @Test fun gearChangesRunStats() {
        val bare = Operatives.buildConfig("operative", emptyMap(), 0L, 10).baseStats
        val rng = Random(4)
        val chest = Gear.roll(rng, 1, 10, GearRarity.EPIC, GearSlot.CHEST)
        val core = Gear.roll(rng, 2, 10, GearRarity.EPIC, GearSlot.CORE)
        val sec = Gear.roll(rng, 3, 10, GearRarity.RARE, GearSlot.SECONDARY)
        val geared = Operatives.buildConfig("operative", emptyMap(), 0L, 10, listOf(chest, core, sec)).baseStats
        assertTrue(geared.armor > bare.armor)
        assertTrue(geared.maxHp > bare.maxHp)
        assertTrue(sec.sidearm!!.id in geared.weapons)
    }

    @Test fun bankFillsBackpackThenVault() {
        val rng = Random(8)
        val items = (0 until 70).map { Gear.roll(rng, 0, 5, GearRarity.COMMON) }
        val (p, res) = GearBank.bank(PlayerProfile(), items)
        assertEquals(Gear.BACKPACK_SIZE, p.backpack.size)
        assertEquals(10, p.vault.size)
        assertEquals(60, res.backpack)
        assertEquals((p.backpack + p.vault).size, (p.backpack + p.vault).map { it.uid }.toSet().size)
    }

    @Test fun equipSwapsAndVaultMoves() {
        val rng = Random(11)
        var p = GearBank.bank(PlayerProfile(), listOf(
            Gear.roll(rng, 0, 5, GearRarity.COMMON, GearSlot.HELMET),
            Gear.roll(rng, 0, 5, GearRarity.RARE, GearSlot.HELMET)
        )).first
        val (a, b) = p.backpack
        p = GearBank.equip(p, a.uid)
        assertEquals(a.uid, p.equipped[GearSlot.HELMET]?.uid)
        p = GearBank.equip(p, b.uid)
        assertEquals(b.uid, p.equipped[GearSlot.HELMET]?.uid)
        assertTrue(p.backpack.any { it.uid == a.uid })
        p = GearBank.toVault(p, a.uid)
        assertTrue(p.vault.any { it.uid == a.uid } && p.backpack.none { it.uid == a.uid })
        p = GearBank.toggleLock(p, a.uid)
        assertEquals(p, GearBank.discard(p, a.uid))
        p = GearBank.toBackpack(GearBank.toggleLock(p, a.uid), a.uid)
        p = GearBank.depositAll(p)
        assertTrue(p.backpack.isEmpty())
        p = GearBank.discard(p, a.uid)
        assertTrue(GearBank.find(p, a.uid) == null)
    }

    @Test fun charactersKeepTheirOwnProgressAndGear() {
        var p = PlayerProfile(operativeXp = 5000)
        p = Characters.create(p, "Bastion", "juggernaut", CharacterLook(1, 2, 0, 1))
        // The first character adopts the existing progress.
        assertEquals(5000, p.operativeXp)
        p = GearBank.bank(p, listOf(Gear.roll(Random(1), 0, 5, GearRarity.EPIC))).first
        val firstId = p.characterId
        p = Characters.create(p, "Wraith", "phantom", CharacterLook())
        assertEquals("Wraith", p.characterName)
        assertEquals("phantom", p.selectedOperative)
        assertEquals(0, p.operativeXp)
        assertTrue(p.backpack.isEmpty())
        p = Characters.switchTo(p, firstId)
        assertEquals("Bastion", p.characterName)
        assertEquals(5000, p.operativeXp)
        assertEquals(1, p.backpack.size)
        assertEquals(2, Characters.all(p).size)
        assertEquals("Bastion", Characters.coopName(p, "CALLSIGN"))
        // Saves round-trip with characters and gear.
        val json = Json { ignoreUnknownKeys = true }
        val back = json.decodeFromString(PlayerProfile.serializer(), json.encodeToString(PlayerProfile.serializer(), p))
        assertEquals(p, back)
    }

    @Test fun elitesAndBossesDropGearToTheirOwner() {
        val g = GameEngine(RunConfig(seed = 3L, consumables = true, opLevel = 12, powerGovernor = false))
        var found = 0
        for (i in 0 until 400) {
            val e = g.spawnEnemyAt(com.cyberoperative.game.data.Enemies.all.first(), com.cyberoperative.game.data.EliteModifier.entries.first(), g.px + 200f, g.py, false) ?: continue
            g.killEnemy(e)
        }
        for (l in g.loot) if (l.kind == LootKind.GEAR) {
            found++
            val it = l.gear!!
            assertTrue(it.level in 10..14)
            assertEquals(0, l.owner)
        }
        assertTrue("elites drop gear sometimes ($found)", found in 40..200)
    }

    @Test fun coopSnapshotCarriesGearLookAndGuestFinds() {
        val item = Gear.roll(Random(6), 7, 20, GearRarity.INFERNAL, GearSlot.GLOVES)
        val look = GameEngine.gearFromLook(GameEngine.gearLook(item))
        assertEquals(item.slot, look.slot); assertEquals(item.rarity, look.rarity); assertEquals(item.art, look.art)
        val w = CoopWorld()
        w.guestGear = Gear.encode(listOf(item))
        val back = CoopCodec.decodeWorld(CoopCodec.encodeWorld(w))!!
        assertEquals(listOf(item), Gear.decode(back.guestGear))
    }
}
