package com.cyberoperative.game.engine

/*
 * Co-op trading (owner, 0.35.0). Either player taps [TRADE] (only while the room has no
 * threats); the other accepts the request and a trade window opens on both screens. Each side
 * offers banked €, HP charges (potions) and backpack items; the trade goes through only when both
 * have pressed ACCEPT. Any change to either offer clears both ACCEPTs, so nobody can swap the
 * offer out from under an accept. A player who would end up over the potion limit can't accept.
 *
 * The host runs the desk ([TradeDesk]); the guest sends its actions in its input and mirrors the
 * desk from the snapshot. Potions move on the host (they're run state); € and items are profile
 * state, so each side applies its own half when it sees [TradeDesk.doneSerial] go up.
 */

/** One player's offer. [items] is [com.cyberoperative.game.data.Gear.encode] text. */
data class TradeOffer(val euros: Int = 0, val potions: Int = 0, val items: String = "", val accepted: Boolean = false) {
    val itemList get() = com.cyberoperative.game.data.Gear.decode(items)
}

enum class TradeAction { REQUEST, AGREE, CLOSE, OFFER, ACCEPT }

class TradeDesk {
    /** Operative index that asked to trade, or -1. */
    var requestedBy = -1
    var open = false
    val offers = arrayOf(TradeOffer(), TradeOffer())
    /** Bumped when a trade completes; [done] holds the final offers. */
    var doneSerial = 0
    val done = arrayOf(TradeOffer(), TradeOffer())
    /** Bumped when an ACCEPT is refused (too many potions); [refusedFor] is the player. */
    var refusedSerial = 0
    var refusedFor = -1

    fun reset() {
        open = false; requestedBy = -1
        offers[0] = TradeOffer(); offers[1] = TradeOffer()
    }

    /**
     * Applies [action] from player [op]. [allowed]: the room is clear. [charges]: each player's HP
     * charges now; [maxCharges] the cap. Returns true when the trade just completed (the caller
     * moves the potions).
     */
    fun act(op: Int, action: TradeAction, offer: TradeOffer, allowed: Boolean, charges: IntArray, maxCharges: Int): Boolean {
        if (op !in 0..1) return false
        val other = 1 - op
        when (action) {
            TradeAction.REQUEST -> if (allowed && !open) requestedBy = op
            TradeAction.AGREE -> if (allowed && requestedBy == other) { reset(); open = true }
            TradeAction.CLOSE -> reset()
            TradeAction.OFFER -> if (open) {
                val items = com.cyberoperative.game.data.Gear.decode(offer.items).take(MAX_ITEMS)
                val clean = TradeOffer(
                    euros = offer.euros.coerceAtLeast(0),
                    potions = offer.potions.coerceIn(0, charges[op]),
                    items = if (items.isEmpty()) "" else com.cyberoperative.game.data.Gear.encode(items)
                )
                if (clean.copy(accepted = false) != offers[op].copy(accepted = false)) {
                    // A changed offer cancels both accepts.
                    offers[op] = clean
                    offers[other] = offers[other].copy(accepted = false)
                }
            }
            TradeAction.ACCEPT -> if (open) {
                val after = charges[op] - offers[op].potions + offers[other].potions
                if (after > maxCharges) { refusedSerial++; refusedFor = op; return false }
                offers[op] = offers[op].copy(accepted = true)
                if (offers[other].accepted) {
                    // The partner's charges may have changed since they accepted.
                    if (charges[other] - offers[other].potions + offers[op].potions > maxCharges) {
                        offers[other] = offers[other].copy(accepted = false)
                        refusedSerial++; refusedFor = other
                        return false
                    }
                    done[0] = offers[0]; done[1] = offers[1]
                    doneSerial++
                    reset()
                    return true
                }
            }
        }
        return false
    }

    companion object {
        const val MAX_ITEMS = 8
    }
}
