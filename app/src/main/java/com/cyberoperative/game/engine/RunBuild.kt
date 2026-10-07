package com.cyberoperative.game.engine

import com.cyberoperative.game.data.Rarity
import com.cyberoperative.game.data.UpgradeDef
import com.cyberoperative.game.data.Upgrades
import kotlin.random.Random

/** One card on the level-up screen. */
data class UpgradeOffer(val def: UpgradeDef, val nextLevel: Int) {
    val title: String get() = if (def.instant || def.maxLevel == 1) def.name else "${def.name} ${Upgrades.roman(nextLevel)}"
    val effect: String get() = def.effect(nextLevel)
    val isEvolution: Boolean get() = def.evolvesFrom != null
}

/**
 * The upgrades owned in the current run, and the rules for offering more.
 * [base] comes from the operative + permanent progression; [stats] is always
 * `base + every owned upgrade`, rebuilt by [recompute].
 */
class RunBuild(private val base: RunStats) {

    private val levels = LinkedHashMap<String, Int>()
    val stats = RunStats()

    /** Upgrade ids never offered in this run (mode-specific). */
    var excluded: Set<String> = emptySet()

    /** Extra rarity bias from permanent "Upgrade Quality" (0 = none). */
    var qualityBonus = 0f

    init { recompute() }

    fun level(id: String): Int = levels[id] ?: 0

    fun owned(): Map<String, Int> = levels

    fun isEligible(def: UpgradeDef): Boolean {
        if (def.id in excluded) return false
        if (def.instant) return true
        if (level(def.id) >= def.maxLevel) return false
        val from = def.evolvesFrom
        if (from != null) {
            val parent = Upgrades.byId(from)
            if (level(from) < parent.maxLevel) return false
        }
        val also = def.alsoRequires
        if (also != null && level(also) < 1) return false
        return true
    }

    /** Applies the choice. Returns true if it was an instant effect (e.g. heal). */
    fun take(def: UpgradeDef): Boolean {
        if (def.instant) return true
        levels[def.id] = level(def.id) + 1
        recompute()
        return false
    }

    fun recompute() {
        stats.copyFrom(base)
        for ((id, lvl) in levels) Upgrades.byId(id).apply(stats, lvl)
        stats.clampLimits()
    }

    private fun weightOf(def: UpgradeDef): Float {
        var w = def.rarity.weight
        if (qualityBonus > 0f && def.rarity.ordinal >= Rarity.RARE.ordinal) w *= 1f + qualityBonus
        // An unlocked evolution is the payoff for committing to a build:
        // make it very likely to be seen.
        if (def.evolvesFrom != null) w = maxOf(w, 60f)
        // Filler heal is rare unless nothing else remains.
        if (def.instant) w = 8f
        return w
    }

    /** Three distinct cards (fewer only if the pool is genuinely exhausted). */
    fun rollOffer(rng: Random, count: Int = 3): List<UpgradeOffer> {
        val pool = Upgrades.all.filter { isEligible(it) }.toMutableList()
        val result = ArrayList<UpgradeOffer>(count)
        while (result.size < count && pool.isNotEmpty()) {
            val total = pool.sumOf { weightOf(it).toDouble() }.toFloat()
            var roll = rng.nextFloat() * total
            var chosen = pool.last()
            for (d in pool) {
                roll -= weightOf(d)
                if (roll <= 0f) { chosen = d; break }
            }
            pool.remove(chosen)
            result += UpgradeOffer(chosen, if (chosen.instant) 1 else level(chosen.id) + 1)
        }
        return result
    }
}
