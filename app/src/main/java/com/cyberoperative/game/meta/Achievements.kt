package com.cyberoperative.game.meta

import com.cyberoperative.game.i18n.tr
import com.cyberoperative.game.data.Events
import com.cyberoperative.game.data.PermanentUpgrades
import com.cyberoperative.game.engine.RunSummary
import com.cyberoperative.game.save.PlayerProfile

/**
 * Achievement architecture (§59). Each achievement is a predicate over the
 * saved profile (lifetime totals) and the run just played. Rewards are small
 * € amounts; ◇ rewards are deliberately not used until monetization balance
 * is decided.
 */
data class AchievementDef(
    val id: String,
    val name: String,
    val description: String,
    val rewardEuros: Int,
    val check: (PlayerProfile, RunSummary) -> Boolean
)

object Achievements {

    val all: List<AchievementDef> = listOf(
        AchievementDef("first_connection", tr("FIRST CONNECTION"), tr("Complete your first operation."), 50) { p, _ -> p.totalRuns >= 1 },
        AchievementDef("threats_100", tr("DEFEAT 100 THREATS"), tr("Eliminate 100 threats in total."), 100) { p, _ -> p.totalKills >= 100 },
        AchievementDef("threats_1000", tr("DEFEAT 1,000 THREATS"), tr("Eliminate 1,000 threats in total."), 400) { p, _ -> p.totalKills >= 1_000 },
        AchievementDef("threats_10000", tr("DEFEAT 10,000 THREATS"), tr("Eliminate 10,000 threats in total."), 2_000) { p, _ -> p.totalKills >= 10_000 },
        AchievementDef("level_10", tr("REACH LEVEL 10"), tr("Reach level 10 in a single operation."), 150) { p, _ -> p.highestLevel >= 10 },
        AchievementDef("level_25", tr("REACH LEVEL 25"), tr("Reach level 25."), 400) { p, _ -> p.highestLevel >= 25 },
        AchievementDef("level_50", tr("REACH LEVEL 50"), tr("Reach level 50."), 1_000) { p, _ -> p.highestLevel >= 50 },
        AchievementDef("level_100", tr("REACH LEVEL 100"), tr("Reach level 100."), 3_000) { p, _ -> p.highestLevel >= 100 },
        AchievementDef("boss_1", tr("THREAT NEUTRALIZED"), tr("Defeat your first boss."), 150) { p, _ -> p.totalBosses >= 1 },
        AchievementDef("boss_10", tr("DEFEAT 10 BOSSES"), tr("Defeat 10 bosses in total."), 800) { p, _ -> p.totalBosses >= 10 },
        AchievementDef("boss_100", tr("DEFEAT 100 BOSSES"), tr("Defeat 100 bosses in total."), 5_000) { p, _ -> p.totalBosses >= 100 },
        AchievementDef("all_events", tr("COMPLETE EVERY EVENT TYPE"), tr("Complete each of the special event types."), 1_000) { p, _ ->
            Events.all.all { it.id in p.eventTypesCompleted }
        },
        AchievementDef("zero_day_survivor", tr("SURVIVE ZERO-DAY ANOMALY"), tr("Complete a Zero-Day Anomaly event."), 500) { p, _ ->
            "zero_day" in p.eventTypesCompleted
        },
        AchievementDef("maxed_stat", tr("FULLY UPGRADE AN OPERATIVE STAT"), tr("Max out any permanent upgrade."), 500) { p, _ ->
            PermanentUpgrades.all.any { (p.permanentUpgrades[it.id] ?: 0) >= it.maxLevel }
        },
        AchievementDef("score_100k", tr("HIGH VALUE TARGET"), tr("Score 100,000 in one operation."), 600) { _, r -> r.score >= 100_000 },
        AchievementDef("deep_dive", tr("DEEP DIVE"), tr("Reach level 20 in one operation."), 300) { _, r -> r.levelReached >= 20 }
    )

    /** Achievements newly satisfied by [profile] (not already owned). */
    fun evaluate(profile: PlayerProfile, run: RunSummary): List<AchievementDef> =
        all.filter { it.id !in profile.achievements && it.check(profile, run) }

    /** For screens outside a run (e.g. after buying a permanent upgrade). */
    fun evaluateProfileOnly(profile: PlayerProfile): List<AchievementDef> =
        evaluate(profile, RunSummary(0, 0, 0, 0, 0, 0, 0, 0, 0f))
}
