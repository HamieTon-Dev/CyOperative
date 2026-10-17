package com.cyberoperative.game.meta

import com.cyberoperative.game.data.ClockAnchor
import com.cyberoperative.game.data.Incubator
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

    /** Spends [realMs] of hatching time; eggs that finish come back as pets (uids from [nextUid]). */
    fun credit(inc: Incubator, realMs: Long, nextUid: Long): Triple<Incubator, List<Pet>, Long> {
        var cur = inc.current
        var progress = inc.progress
        var queue = inc.queue
        var units = (realMs / Pets.timeFactor(inc.stones)).toLong()
        val hatched = ArrayList<Pet>()
        var uid = nextUid
        while (cur != null && units > 0) {
            val need = cur.baseMs - progress
            if (units >= need) {
                units -= need
                hatched += Pet(uid++, cur.pet, cur.rarity)
                cur = queue.firstOrNull(); queue = queue.drop(1); progress = 0
            } else { progress += units; units = 0 }
        }
        // An egg already at full progress (stones added) hatches too.
        while (cur != null && progress >= cur.baseMs) {
            hatched += Pet(uid++, cur.pet, cur.rarity)
            cur = queue.firstOrNull(); queue = queue.drop(1); progress = 0
        }
        return Triple(inc.copy(current = cur, progress = progress, queue = queue), hatched, uid)
    }

    /** The save moved to [now]: hatched eggs become pets. */
    fun tick(p: PlayerProfile, now: ClockNow = TrustedClock.now()): PlayerProfile {
        val (stepped, real) = clockStep(p.incubator, now)
        val (inc, pets, next) = credit(stepped, real, p.petSeq)
        if (pets.isEmpty() && inc == p.incubator) return p
        return p.copy(incubator = inc, pets = p.pets + pets, petSeq = next)
    }

    /** Sends egg [uid] from the inventory to the incubator (hatching now, or in the queue). */
    fun send(p: PlayerProfile, uid: Long, now: ClockNow = TrustedClock.now()): PlayerProfile? {
        val q = tick(p, now)
        val egg = q.eggs.firstOrNull { it.uid == uid } ?: return null
        val inc = q.incubator
        val placed = when {
            inc.current == null -> inc.copy(current = egg, progress = 0)
            inc.queue.size < Pets.QUEUE -> inc.copy(queue = inc.queue + egg)
            else -> return null
        }
        return q.copy(eggs = q.eggs.filter { it.uid != uid }, incubator = placed)
    }

    /** Takes a waiting egg (or the hatching one, losing its progress) back to the inventory. */
    fun takeBack(p: PlayerProfile, uid: Long, now: ClockNow = TrustedClock.now()): PlayerProfile? {
        val q = tick(p, now)
        val inc = q.incubator
        if (q.eggs.size >= Pets.MAX_EGGS) return null
        return when {
            inc.current?.uid == uid -> q.copy(eggs = q.eggs + inc.current, incubator = inc.copy(current = inc.queue.firstOrNull(), queue = inc.queue.drop(1), progress = 0))
            inc.queue.any { it.uid == uid } -> q.copy(eggs = q.eggs + inc.queue.first { it.uid == uid }, incubator = inc.copy(queue = inc.queue.filter { it.uid != uid }))
            else -> null
        }
    }

    /** Socket [n] Inferno Stones (time so far is counted at the old speed first). */
    fun setStones(p: PlayerProfile, n: Int, now: ClockNow = TrustedClock.now()): PlayerProfile {
        val q = tick(p, now)
        val stones = n.coerceIn(0, minOf(Pets.MAX_STONES, q.infernoStones))
        val (inc, pets, next) = credit(q.incubator.copy(stones = stones), 0L, q.petSeq)
        return q.copy(incubator = inc, pets = q.pets + pets, petSeq = next)
    }

    /** Wall-clock time the current egg should hatch (for a reminder notification), or null. */
    fun hatchAt(p: PlayerProfile, wall: Long = PlatformClock.wall()): Long? =
        p.incubator.current?.let { wall + p.incubator.remainingMs }

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

    fun seen(p: PlayerProfile): PlayerProfile =
        if (p.pets.none { it.fresh }) p else p.copy(pets = p.pets.map { if (it.fresh) it.copy(fresh = false) else it })

    /** The equipped pet's loadout for a run, or null. */
    fun loadout(p: PlayerProfile): com.cyberoperative.game.data.PetLoadout? = p.pets.firstOrNull { it.uid == p.activePet }?.loadout

    /** Releases a pet (gone for good). */
    fun release(p: PlayerProfile, uid: Long): PlayerProfile =
        p.copy(pets = p.pets.filter { it.uid != uid }, activePet = if (p.activePet == uid) 0L else p.activePet)
}
