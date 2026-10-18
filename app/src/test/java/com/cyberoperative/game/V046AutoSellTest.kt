package com.cyberoperative.game

import com.cyberoperative.game.data.Gear
import com.cyberoperative.game.data.GearItem
import com.cyberoperative.game.data.GearRarity
import com.cyberoperative.game.data.GearSlot
import com.cyberoperative.game.meta.GearBank
import com.cyberoperative.game.save.GameSettings
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.46.0: AUTO-SELL "sell everything below ___". */
class V046AutoSellTest {
    private fun item(uid: Long, r: GearRarity, locked: Boolean = false) = GearItem(uid, GearSlot.CORE, r, 0, 20, 10f, locked = locked)

    @Test fun sellsOnlyWhatIsBelowThePick() {
        val items = GearRarity.entries.mapIndexed { i, r -> item(i.toLong(), r) }
        val (kept, euros) = GearBank.autoSell(items, GearRarity.EPIC)
        assertTrue(kept.all { it.rarity >= GearRarity.EPIC })
        assertEquals(GearRarity.entries.size - 3, kept.size)
        assertEquals(items.filter { it.rarity < GearRarity.EPIC }.sumOf { Gear.sellPrice(it) }, euros)
    }

    @Test fun offKeepsEverythingAndLockedIsNeverSold() {
        val items = listOf(item(1, GearRarity.COMMON), item(2, GearRarity.COMMON, locked = true))
        assertEquals(items to 0, GearBank.autoSell(items, null))
        assertEquals(listOf(items[1]), GearBank.autoSell(items, GearRarity.ENCRYPTED).first)
    }

    @Test fun theSettingSavesAndOldSettingsLoadAsOff() {
        val json = Json { ignoreUnknownKeys = true }
        val s = GameSettings(autoSellBelow = GearRarity.ENCRYPTED)
        assertEquals(GearRarity.ENCRYPTED, json.decodeFromString(GameSettings.serializer(), json.encodeToString(GameSettings.serializer(), s)).autoSellBelow)
        assertEquals(null, json.decodeFromString(GameSettings.serializer(), "{}").autoSellBelow)
    }
}
