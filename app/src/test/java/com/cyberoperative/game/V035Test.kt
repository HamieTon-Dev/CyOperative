package com.cyberoperative.game

import com.cyberoperative.game.data.Bosses
import com.cyberoperative.game.data.Gear
import com.cyberoperative.game.data.GearRarity
import com.cyberoperative.game.data.GearSlot
import com.cyberoperative.game.data.InfernoBosses
import com.cyberoperative.game.engine.TradeAction
import com.cyberoperative.game.engine.TradeDesk
import com.cyberoperative.game.engine.TradeOffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 0.35.0: trading, selling, INFERNO bosses. */
class V035Test {
    @Test fun tradeNeedsBothAcceptsAndAChangeClearsThem() {
        val d = TradeDesk(); val ch = intArrayOf(2, 2)
        d.act(0, TradeAction.REQUEST, TradeOffer(), true, ch, 5)
        d.act(1, TradeAction.AGREE, TradeOffer(), true, ch, 5)
        assertTrue(d.open)
        d.act(0, TradeAction.OFFER, TradeOffer(euros = 500), true, ch, 5)
        d.act(1, TradeAction.ACCEPT, TradeOffer(), true, ch, 5)
        d.act(0, TradeAction.OFFER, TradeOffer(euros = 100), true, ch, 5)
        assertFalse("changing an offer clears the partner's accept", d.offers[1].accepted)
        d.act(0, TradeAction.ACCEPT, TradeOffer(), true, ch, 5)
        assertTrue(d.act(1, TradeAction.ACCEPT, TradeOffer(), true, ch, 5))
        assertEquals(100, d.done[0].euros)
    }

    @Test fun potionCapBlocksAccept() {
        val d = TradeDesk(); val ch = intArrayOf(3, 5)
        d.act(0, TradeAction.REQUEST, TradeOffer(), true, ch, 5); d.act(1, TradeAction.AGREE, TradeOffer(), true, ch, 5)
        d.act(0, TradeAction.OFFER, TradeOffer(potions = 2), true, ch, 5)
        d.act(1, TradeAction.ACCEPT, TradeOffer(), true, ch, 5)
        assertFalse(d.offers[1].accepted)
        assertEquals(1, d.refusedFor)
        // Can't open a trade while threats are in the room.
        val e = TradeDesk(); e.act(0, TradeAction.REQUEST, TradeOffer(), false, ch, 5)
        assertEquals(-1, e.requestedBy)
    }

    @Test fun sellPriceIsTheFormulaCappedAt85PercentOfTheShop() {
        for (r in GearRarity.entries) {
            val item = Gear.roll(Random(1), 1, 30, r, GearSlot.CHEST)
            val p = Gear.sellPrice(item)
            assertTrue(p <= item.level * (r.ordinal + 1) * 50)
            assertTrue(p <= Gear.shopPrice(item) * 0.85f)
        }
    }

    @Test fun infernoBossesOnlyOnInfernoAndDeepEndless() {
        assertEquals(15, InfernoBosses.all.size)
        assertTrue(Bosses.eligible(40).none { it in InfernoBosses.all })
        assertTrue(Bosses.eligible(40, inferno = true).all { it in InfernoBosses.all })
        assertTrue(Bosses.eligible(150, endless = true).none { it in InfernoBosses.all })
        assertTrue(Bosses.eligible(160, endless = true).containsAll(InfernoBosses.all))
    }
}
