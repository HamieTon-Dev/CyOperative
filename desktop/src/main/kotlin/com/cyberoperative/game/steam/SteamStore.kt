package com.cyberoperative.game.steam

import com.cyberoperative.game.data.DiamondPack
import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer

/**
 * ◇ packs through the Steam store (Steam Inventory Service).
 *
 * Each pack is a Steam Inventory item definition (see steam/itemdefs.json): BUY opens the
 * Steam overlay checkout ([startPurchase]); once Steam grants the item it shows up in the
 * player's inventory, where we consume it and credit the ◇ ([onCredit]). Unclaimed packs
 * (game closed mid-checkout) are claimed at the next start-up.
 *
 * steamworks4j has no ISteamInventory, so these few calls go straight to steam_api's flat
 * C API with JNA. Steam is the only place real money changes hands; the game only ever
 * credits ◇ for an item Steam says was granted and that it consumed successfully.
 */
object SteamStore {
    /** Item definition id for each pack (must match steam/itemdefs.json uploaded to Steamworks). */
    val itemDefs: Map<String, Int> = mapOf(
        "diamonds_150" to 1001,
        "diamonds_500" to 1002,
        "diamonds_1000" to 1003,
        "diamonds_10000" to 1004
    )
    private val diamondsByDef: Map<Int, Int> = mapOf(1001 to 150, 1002 to 500, 1003 to 1_000, 1004 to 10_000)

    /** Credits ◇ (set by the app at start-up: adds to the save and plays the sound). */
    var onCredit: (Int) -> Unit = {}

    private var lib: NativeLibrary? = null
    private var inv: Pointer? = null
    private val available: Boolean get() = inv != null

    private const val RESULT_INVALID = -1
    private const val E_OK = 1
    private const val E_PENDING = 22

    /** In-flight inventory results: a full listing, and consumes keyed by handle → ◇ to credit. */
    private var listing = RESULT_INVALID
    private val consumes = HashMap<Int, Int>()
    private val consuming = HashSet<Long>()
    /** Poll the inventory every second until this time (after a checkout opens or the overlay closes). */
    private var pollUntil = 0L
    private var nextPoll = 0L

    fun init() {
        try {
            val path = SteamService.steamApiPath ?: return
            val l = NativeLibrary.getInstance(path)
            lib = l
            val accessor = listOf("SteamAPI_SteamInventory_v003", "SteamAPI_SteamInventory_v002")
                .firstNotNullOfOrNull { name -> try { l.getFunction(name) } catch (_: Throwable) { null } }
                ?: return
            inv = accessor.invokePointer(emptyArray())
            SteamService.tickers += ::tick
            pollUntil = System.currentTimeMillis() + 15_000 // claim anything left from last time
        } catch (_: Throwable) {
            inv = null
        }
    }

    private fun fn(name: String): Function = lib!!.getFunction("SteamAPI_ISteamInventory_$name")

    /** Calls a bool-returning method (C++ bool is one byte: ignore the rest of the register). */
    private fun call(name: String, vararg args: Any?): Boolean = (fn(name).invokeInt(arrayOf(inv, *args)) and 0xFF) != 0

    /** Opens the Steam checkout for [pack]. Returns a message for the store screen. */
    fun startPurchase(pack: DiamondPack): String {
        if (!SteamService.running) return "Steam isn't running. Start Steam to buy ◇."
        if (!available) return "The Steam store isn't available right now."
        val def = itemDefs[pack.productId] ?: return "This pack isn't on the Steam store yet."
        return try {
            val defs = Memory(4).apply { setInt(0, def) }
            val qty = Memory(4).apply { setInt(0, 1) }
            val call = fn("StartPurchase").invokeLong(arrayOf(inv, defs, qty, 1))
            if (call == 0L) "Steam couldn't open the checkout. Try again in a moment."
            else {
                pollUntil = System.currentTimeMillis() + 10 * 60_000
                "Complete the purchase in the Steam overlay. ◇ arrive as soon as Steam confirms."
            }
        } catch (t: Throwable) {
            "Steam checkout failed: ${t.message ?: "unknown error"}"
        }
    }

    /** The overlay closed (maybe after paying): look for the new item right away. */
    fun onOverlayClosed() {
        if (!available) return
        pollUntil = maxOf(pollUntil, System.currentTimeMillis() + 60_000)
        nextPoll = 0L
    }

    private fun tick() {
        if (!available) return
        try {
            checkListing()
            checkConsumes()
            val now = System.currentTimeMillis()
            if (listing == RESULT_INVALID && now < pollUntil && now >= nextPoll) {
                nextPoll = now + 1_000
                val h = Memory(4).apply { setInt(0, RESULT_INVALID) }
                if (call("GetAllItems", h)) listing = h.getInt(0)
            }
        } catch (_: Throwable) {
        }
    }

    private fun status(handle: Int): Int = fn("GetResultStatus").invokeInt(arrayOf(inv, handle))
    private fun destroy(handle: Int) { fn("DestroyResult").invokeVoid(arrayOf(inv, handle)) }

    /** SteamItemDetails_t: uint64 itemId, int32 definition, uint16 quantity, uint16 flags (16 bytes). */
    private fun items(handle: Int): List<Triple<Long, Int, Int>> {
        val count = Memory(4).apply { setInt(0, 0) }
        if (!call("GetResultItems", handle, Pointer.NULL, count)) return emptyList()
        val n = count.getInt(0)
        if (n <= 0) return emptyList()
        val buf = Memory(16L * n)
        if (!call("GetResultItems", handle, buf, count)) return emptyList()
        return (0 until count.getInt(0)).map { i ->
            val o = 16L * i
            Triple(buf.getLong(o), buf.getInt(o + 8), buf.getShort(o + 12).toInt() and 0xFFFF)
        }
    }

    private fun checkListing() {
        val h = listing
        if (h == RESULT_INVALID) return
        val st = status(h)
        if (st == E_PENDING) return
        listing = RESULT_INVALID
        if (st == E_OK) {
            for ((itemId, def, quantity) in items(h)) {
                val per = diamondsByDef[def] ?: continue
                if (quantity <= 0 || itemId in consuming) continue
                val out = Memory(4).apply { setInt(0, RESULT_INVALID) }
                if (call("ConsumeItem", out, itemId, quantity)) {
                    consuming += itemId
                    consumes[out.getInt(0)] = per * quantity
                    pollUntil = maxOf(pollUntil, System.currentTimeMillis() + 5_000)
                }
            }
        }
        destroy(h)
    }

    private fun checkConsumes() {
        if (consumes.isEmpty()) return
        val done = ArrayList<Int>()
        for ((h, diamonds) in consumes) {
            val st = status(h)
            if (st == E_PENDING) continue
            done += h
            // Only a consume Steam confirmed credits ◇; a failed one stays in the inventory for next time.
            if (st == E_OK) onCredit(diamonds)
            destroy(h)
        }
        for (h in done) consumes.remove(h)
        if (consumes.isEmpty()) consuming.clear()
    }
}
