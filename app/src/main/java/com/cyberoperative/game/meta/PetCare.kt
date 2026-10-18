package com.cyberoperative.game.meta

import com.cyberoperative.game.data.ClockAnchor
import com.cyberoperative.game.data.Incubator
import com.cyberoperative.game.data.IncubatorSlot
import com.cyberoperative.game.data.Pet
import com.cyberoperative.game.data.PetEgg
import com.cyberoperative.game.data.Pets
import com.cyberoperative.game.platform.PlatformClock
import com.cyberoperative.game.save.PlayerProfile

/** One reading of every clock the incubator trusts (pure data, so the rules can be tested). */
data class ClockNow(val boot: String, val mono: Long, val wall: Long, val network: Long?)

/**
 * Network time, so the incubator can't be fooled by changing the device clock (owner, 0.38.0).
 * Read from an HTTPS server's Date header in the background; afterwards it runs on the
 * since-boot clock, so changing the phone's time doesn't move it.
 */
object TrustedClock {
    @Volatile private var netBoot = ""
    @Volatile private var netMono = -1L
    @Volatile private var netTime = 0L
    @Volatile private var fetching = false
    /** Tests: a fixed network time (or null for "offline"); null uses the real fetch. */
    @Volatile var override: (() -> Long?)? = null

    /** Network time now, or null when it hasn't been read since this boot. */
    fun networkNow(): Long? {
        override?.let { return it() }
        if (netMono < 0 || netBoot != PlatformClock.bootId()) return null
        return netTime + (PlatformClock.mono() - netMono)
    }

    fun now(): ClockNow = ClockNow(PlatformClock.bootId(), PlatformClock.mono(), PlatformClock.wall(), networkNow())

    /** Reads network time on a background thread (no-op while a read is running); [done] runs after. */
    fun refresh(done: (() -> Unit)? = null) {
        if (override != null || fetching) return
        fetching = true
        Thread({
            try {
                for (url in listOf("https://www.google.com/generate_204", "https://cloudflare.com/cdn-cgi/trace")) {
                    val t = runCatching {
                        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                        c.requestMethod = "HEAD"; c.connectTimeout = 5000; c.readTimeout = 5000; c.useCaches = false
                        val before = PlatformClock.mono()
                        c.connect()
                        val date = c.date
                        val after = PlatformClock.mono()
                        c.disconnect()
                        if (date > 0) date + (after - before) / 2 to after else null
                    }.getOrNull() ?: continue
                    netTime = t.first; netMono = t.second; netBoot = PlatformClock.bootId()
                    break
                }
            } finally {
                fetching = false
                done?.invoke()
            }
        }, "trusted-clock").apply { isDaemon = true }.start()
    }
}

/**
 * The incubator's rules (owner, 0.38.0). Time only counts on the since-boot clock, which the
 * date/time settings can't change; after a reboot (or on PC, a restart) the time away is
 * credited from network time, and the timer waits until network time is available.
 */
object Incubators {
    /** Time away that may be credited before the incubator's clock was ever network-checked. */
    const val UNVERIFIED_CAP_MS = 8L * 3600_000L

    /** [inc] moved to [now]: the anchor, and how many real milliseconds count. */
    fun clockStep(inc: Incubator, now: ClockNow): Pair<Incubator, Long> {
        val a = inc.clock
        if (a.boot.isEmpty()) {
            return inc.copy(clock = ClockAnchor(now.boot, now.mono, now.network ?: now.wall, now.network != null), waitingForTime = false) to 0L
        }
        if (a.boot == now.boot && now.mono >= a.mono) {
            val delta = now.mono - a.mono
            var trusted = a.trusted + delta
            var verified = a.verified
            if (!verified && now.network != null) { trusted = now.network; verified = true }
            return inc.copy(clock = ClockAnchor(now.boot, now.mono, trusted, verified), waitingForTime = false) to delta
        }
        // A reboot (or a PC restart): only network time can say how long it's been.
        val net = now.network ?: return inc.copy(waitingForTime = true) to 0L
        val gap = (net - a.trusted).coerceAtLeast(0L).let { if (a.verified) it else minOf(it, UNVERIFIED_CAP_MS) }
        return inc.copy(clock = ClockAnchor(now.boot, now.mono, net, true), waitingForTime = false) to gap
    }

    /** Spends [realMs] of hatching time in every incubator; eggs that finish come back as pets (uids from [nextUid]). */
    fun credit(inc: Incubator, realMs: Long, nextUid: Long): Triple<Incubator, List<Pet>, Long> {
        val units = (realMs / Pets.timeFactor(inc.stones)).toLong()
        val hatched = ArrayList<Pet>()
        var uid = nextUid
        val slots = List(Pets.SLOTS) { i ->
            val s = inc.slots.getOrNull(i) ?: return@List null
            val progress = s.progress + units
            // An egg at full progress (time passed, stones added, or paid for) hatches.
            if (progress >= s.egg.baseMs) { hatched += Pet(uid++, s.egg.pet, s.egg.rarity, eggArt = s.egg.art); null } else s.copy(progress = progress)
        }
        return Triple(inc.copy(slots = slots), hatched, uid)
    }

    /**
     * Pre-0.45 saves had one egg hatching and a queue of three: the hatching egg keeps its progress
     * in incubator 1, the first two queued eggs go into incubators 2 and 3, any other back to the eggs.
     */
    fun migrate(p: PlayerProfile): PlayerProfile {
        val inc = p.incubator
        val slotsOk = inc.slots.size == Pets.SLOTS
        if (inc.current == null && inc.queue.isEmpty() && slotsOk) return p
        val incoming = listOfNotNull(inc.current?.let { IncubatorSlot(it, inc.progress) }) + inc.queue.map { IncubatorSlot(it) }
        val slots = MutableList<IncubatorSlot?>(Pets.SLOTS) { inc.slots.getOrNull(it) }
        val leftover = ArrayList<PetEgg>()
        for (s in incoming) {
            val i = slots.indexOfFirst { it == null }
            if (i >= 0) slots[i] = s else leftover += s.egg
        }
        return p.copy(incubator = inc.copy(current = null, progress = 0, queue = emptyList(), slots = slots), eggs = p.eggs + leftover)
    }

    /** PET CODEX: newly hatched [pets] join the record (their egg, and the best rarity seen). */
    fun dex(old: Map<Int, com.cyberoperative.game.data.PetDexEntry>, pets: List<com.cyberoperative.game.data.Pet>): Map<Int, com.cyberoperative.game.data.PetDexEntry> {
        if (pets.isEmpty()) return old
        val out = old.toMutableMap()
        for (pet in pets) {
            val cur = out[pet.art]
            if (cur == null || pet.rarity > cur.best) out[pet.art] = com.cyberoperative.game.data.PetDexEntry(cur?.egg ?: pet.hatchedFrom, maxOf(pet.rarity, cur?.best ?: pet.rarity))
        }
        return out
    }

    /** The codex including pets owned from before it existed. */
    fun codex(p: PlayerProfile): Map<Int, com.cyberoperative.game.data.PetDexEntry> = dex(p.petCodex, p.pets)

    /** The save moved to [now]: hatched eggs become pets. */
    fun tick(p: PlayerProfile, now: ClockNow = TrustedClock.now()): PlayerProfile {
        val m = migrate(p)
        val (stepped, real) = clockStep(m.incubator, now)
        val (inc, pets, next) = credit(stepped, real, m.petSeq)
        if (pets.isEmpty() && inc == m.incubator && m === p) return p
        return m.copy(incubator = inc, pets = m.pets + pets, petCodex = dex(m.petCodex, pets), petSeq = next)
    }

    /** Puts egg [uid] from the inventory into the first empty incubator (null when all three are busy). */
    fun send(p: PlayerProfile, uid: Long, now: ClockNow = TrustedClock.now()): PlayerProfile? {
        val q = tick(p, now)
        val egg = q.eggs.firstOrNull { it.uid == uid } ?: return null
        val i = q.incubator.slots.indexOfFirst { it == null }
        if (i < 0) return null
        val slots = q.incubator.slots.toMutableList().also { it[i] = IncubatorSlot(egg) }
        return q.copy(eggs = q.eggs.filter { it.uid != uid }, incubator = q.incubator.copy(slots = slots))
    }

    /** Takes egg [uid] out of its incubator, back to the inventory (its progress is lost). */
    fun takeBack(p: PlayerProfile, uid: Long, now: ClockNow = TrustedClock.now()): PlayerProfile? {
        val q = tick(p, now)
        if (q.eggs.size >= Pets.MAX_EGGS) return null
        val i = q.incubator.slots.indexOfFirst { it?.egg?.uid == uid }
        if (i < 0) return null
        val egg = q.incubator.slots[i]!!.egg
        val slots = q.incubator.slots.toMutableList().also { it[i] = null }
        return q.copy(eggs = q.eggs + egg, incubator = q.incubator.copy(slots = slots))
    }

    /** Socket [n] Inferno Stones (time so far is counted at the old speed first). */
    fun setStones(p: PlayerProfile, n: Int, now: ClockNow = TrustedClock.now()): PlayerProfile {
        val q = tick(p, now)
        val stones = n.coerceIn(0, minOf(Pets.MAX_STONES, q.infernoStones))
        val (inc, pets, next) = credit(q.incubator.copy(stones = stones), 0L, q.petSeq)
        return q.copy(incubator = inc, pets = q.pets + pets, petCodex = dex(q.petCodex, pets), petSeq = next)
    }

    /** Hatches incubator [slot] now (paid for). */
    private fun hatchNow(q: PlayerProfile, slot: Int): PlayerProfile {
        val s = q.incubator.slot(slot)!!
        val slots = q.incubator.slots.toMutableList().also { it[slot] = s.copy(progress = s.egg.baseMs) }
        val (inc, pets, next) = credit(q.incubator.copy(slots = slots), 0L, q.petSeq)
        return q.copy(incubator = inc, pets = q.pets + pets, petCodex = dex(q.petCodex, pets), petSeq = next)
    }

    /** Pays ◇ to hatch the egg in incubator [slot] now; null when it's empty or there isn't enough ◇. */
    fun instantHatch(p: PlayerProfile, slot: Int = 0, now: ClockNow = TrustedClock.now()): PlayerProfile? {
        val q = tick(p, now)
        val egg = q.incubator.slot(slot)?.egg ?: return null
        val price = Pets.instantHatchPrice(egg.rarity, q.incubator.remainingMs(slot))
        if (price <= 0 || q.diamonds < price) return null
        return hatchNow(q.copy(diamonds = q.diamonds - price), slot)
    }

    /** The highest operator level on the account (every character), for the € hatch price. */
    fun accountOpLevel(p: PlayerProfile): Int =
        (listOf(p.operativeXp) + p.otherCharacters.map { it.operativeXp }).maxOf { com.cyberoperative.game.data.OpProgress.of(it).level }

    /** Pays € to hatch the egg in incubator [slot] now; null when it's empty or there isn't enough €. */
    fun instantHatchWithEuros(p: PlayerProfile, slot: Int = 0, now: ClockNow = TrustedClock.now()): PlayerProfile? {
        val q = tick(p, now)
        val egg = q.incubator.slot(slot)?.egg ?: return null
        val price = Pets.instantHatchEuros(Pets.instantHatchPrice(egg.rarity, q.incubator.remainingMs(slot)), accountOpLevel(q))
        if (price <= 0L || q.euros < price) return null
        return hatchNow(q.copy(euros = q.euros - price), slot)
    }

    // --- Collection -----------------------------------------------------------------------------

    /** Adds found eggs (fresh uids); eggs past the [Pets.MAX_EGGS] limit are lost. */
    fun addEggs(p: PlayerProfile, found: List<PetEgg>): PlayerProfile {
        if (found.isEmpty()) return p
        val room = (Pets.MAX_EGGS - p.eggs.size).coerceAtLeast(0)
        val add = found.take(room).mapIndexed { i, e -> e.copy(uid = p.petSeq + i) }
        return p.copy(eggs = p.eggs + add, petSeq = p.petSeq + add.size)
    }

    fun equip(p: PlayerProfile, uid: Long): PlayerProfile =
        if (p.pets.any { it.uid == uid }) p.copy(activePet = if (p.activePet == uid) 0L else uid) else p

    /** One pet's reveal is done (several can hatch at once; each gets its own). */
    fun seenOne(p: PlayerProfile, uid: Long): PlayerProfile =
        p.copy(pets = p.pets.map { if (it.uid == uid && it.fresh) it.copy(fresh = false) else it })

    fun seen(p: PlayerProfile): PlayerProfile =
        if (p.pets.none { it.fresh }) p else p.copy(pets = p.pets.map { if (it.fresh) it.copy(fresh = false) else it })

    /** The equipped pet's loadout for a run, or null. */
    fun loadout(p: PlayerProfile): com.cyberoperative.game.data.PetLoadout? = p.pets.firstOrNull { it.uid == p.activePet }?.loadout

    /** Releases a pet (gone for good). */
    fun release(p: PlayerProfile, uid: Long): PlayerProfile =
        p.copy(pets = p.pets.filter { it.uid != uid }, activePet = if (p.activePet == uid) 0L else p.activePet)
}

/**
 * Hatch reminder (owner, 0.39.0): Android schedules a notification for when the egg in the
 * incubator will be ready. The countdown itself never trusts the device clock (see
 * [Incubators]); this only decides when to say so. PC: no host, nothing scheduled.
 */
object PetReminders {
    /** Set by the Android app: (incubator, wall-clock time it hatches or null to cancel, the egg's name). */
    @Volatile var schedule: ((Int, Long?, String) -> Unit)? = null

    fun update(p: PlayerProfile) {
        val s = schedule ?: return
        val inc = p.incubator
        for (i in 0 until Pets.SLOTS) {
            val egg = inc.slot(i)?.egg
            if (egg == null || inc.waitingForTime) s(i, null, "") else s(i, PlatformClock.wall() + inc.remainingMs(i), egg.name)
        }
    }
}
