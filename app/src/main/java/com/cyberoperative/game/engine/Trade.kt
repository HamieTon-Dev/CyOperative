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

/**
 * One player's offer. [items] is [com.cyberoperative.game.data.Gear.encode] text, followed (0.52.0,
 * owner: eggs and pets "can be sold and traded") by the eggs and pets on offer, each part JSON,
 * separated by [SEP] — see [pack].
 */
data class TradeOffer(val euros: Int = 0, val potions: Int = 0, val items: String = "", val accepted: Boolean = false) {
    private val parts: List<String> get() = items.split(SEP)
    val itemList get() = com.cyberoperative.game.data.Gear.decode(parts[0])
    val eggList: List<com.cyberoperative.game.data.PetEgg> get() = parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.let {
        runCatching { com.cyberoperative.game.data.Gear.json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(com.cyberoperative.game.data.PetEgg.serializer()), it) }.getOrNull()
    }.orEmpty()
    val petList: List<com.cyberoperative.game.data.Pet> get() = parts.getOrNull(2)?.takeIf { it.isNotBlank() }?.let {
        runCatching { com.cyberoperative.game.data.Gear.json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(com.cyberoperative.game.data.Pet.serializer()), it) }.getOrNull()
    }.orEmpty()

    companion object {
        const val SEP = '\u001E'
        fun pack(gear: List<com.cyberoperative.game.data.GearItem>, eggs: List<com.cyberoperative.game.data.PetEgg>, pets: List<com.cyberoperative.game.data.Pet>): String {
            val j = com.cyberoperative.game.data.Gear.json
            val g = if (gear.isEmpty()) "" else com.cyberoperative.game.data.Gear.encode(gear)
            if (eggs.isEmpty() && pets.isEmpty()) return g
            val e = if (eggs.isEmpty()) "" else j.encodeToString(kotlinx.serialization.builtins.ListSerializer(com.cyberoperative.game.data.PetEgg.serializer()), eggs)
            val p = if (pets.isEmpty()) "" else j.encodeToString(kotlinx.serialization.builtins.ListSerializer(com.cyberoperative.game.data.Pet.serializer()), pets)
            return "$g$SEP$e$SEP$p"
        }
    }
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
                val clean = TradeOffer(
                    euros = offer.euros.coerceAtLeast(0),
                    potions = offer.potions.coerceIn(0, charges[op]),
                    items = TradeOffer.pack(offer.itemList.take(MAX_ITEMS), offer.eggList.take(MAX_ITEMS), offer.petList.take(MAX_ITEMS))
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
