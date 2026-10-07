package com.cyberoperative.game.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cyberoperative.game.audio.AudioManager
import com.cyberoperative.game.audio.MusicState
import com.cyberoperative.game.data.Operatives
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.LevelKind
import com.cyberoperative.game.engine.Phase
import com.cyberoperative.game.engine.Scoring
import com.cyberoperative.game.save.SaveRepository
import com.cyberoperative.game.data.LivingBackground
import com.cyberoperative.game.data.OperativeSkins
import com.cyberoperative.game.data.StoreCatalog
import com.cyberoperative.game.meta.Achievements
import com.cyberoperative.game.meta.StoreManager

/** What the HUD shows. Rebuilt every frame, assigned only when it changes. */
data class HudSnapshot(
    val level: Int = 1,
    val score: Long = 0,
    val euros: Int = 0,
    val hp: Int = 100,
    val maxHp: Int = 100,
    val firewall: Int = 0,
    val firewallMax: Int = 0,
    val xpPercent: Int = 0,
    val runLevel: Int = 1,
    val phase: Phase = Phase.COMBAT,
    val kind: LevelKind = LevelKind.NORMAL,
    val bossName: String? = null,
    val bossPercent: Int = 0,
    val bossPhase: String = "",
    val eventName: String? = null,
    val eventAccent: Long = 0,
    val timedSeconds: Int = -1,
    val banner: String = "",
    val bannerSub: String = "",
    val bannerVisible: Boolean = false,
    val canRevive: Boolean = false,
    val revivesLeft: Int = 0,
    val reviveTokens: Int = 0,
    val diamonds: Long = 0,
    val paidRevivesLeft: Int = 0,
    val rerolls: Int = 0,
    val enemiesLeft: Int = 0,
    val offerSerial: Int = 0
)

/** Outcome of the finished run for the game-over screen. */
data class RunResult(
    val level: Int,
    val score: Long,
    val kills: Int,
    val bosses: Int,
    val euros: Int,
    val newBestLevel: Boolean,
    val newBestScore: Boolean,
    val newAchievements: List<String>
)

/**
 * One operation (run). Owns the [GameEngine], forwards sounds to audio,
 * selects music, builds the HUD snapshot, and commits progress to the save
 * as *deltas* — at death, on quit and on exit — so nothing earned is lost even
 * if the app is killed on the game-over screen, and a revive never double
 * counts.
 */
class GameSession(private val save: SaveRepository, private val audio: AudioManager) {

    val engine: GameEngine
    var frameTick by mutableLongStateOf(0L)
        private set
    var hud by mutableStateOf(HudSnapshot())
        private set
    var paused by mutableStateOf(false)
    var showTutorial by mutableStateOf(!save.current.tutorialDone)
    var result by mutableStateOf<RunResult?>(null)
        private set

    private var committedEuros = 0
    private var committedKills = 0
    private var committedBosses = 0
    private var committedEvents = 0
    private var committedDiamonds = 0
    private var committedRun = false
    private var lastPhase = Phase.COMBAT

    val skin = OperativeSkins.byId(save.current.selectedSkin)
    val background = LivingBackground.byId(save.current.selectedBackground)

    private val startBestLevel: Int
    private val startBestScore: Long

    init {
        val p = save.current
        val config = Operatives.buildConfig(p.selectedOperative, p.permanentUpgrades, System.nanoTime())
        engine = GameEngine(config)
        startBestLevel = p.highestLevel
        startBestScore = p.bestScore
        updateMusic()
    }

    fun onFrame(delta: Float) {
        if (!paused && !showTutorial) engine.update(delta)
        for (s in engine.sounds) audio.play(s)
        engine.sounds.clear()
        if (engine.phase != lastPhase) {
            if (engine.phase == Phase.DEAD) onDeath()
            lastPhase = engine.phase
        }
        updateMusic()
        val next = buildHud()
        if (next != hud) hud = next
        frameTick++
    }

    private fun updateMusic() {
        val state = when {
            engine.phase == Phase.DEAD -> MusicState.GAME_OVER
            engine.plan.kind == LevelKind.BOSS && engine.boss != null -> MusicState.BOSS
            engine.plan.kind == LevelKind.EVENT -> MusicState.EVENT
            else -> MusicState.COMBAT
        }
        audio.setMusic(state)
    }

    private fun buildHud(): HudSnapshot {
        val g = engine
        val b = g.boss
        val ev = g.plan.event
        return HudSnapshot(
            level = g.level,
            score = g.score,
            euros = g.eurosEarned,
            hp = kotlin.math.ceil(g.hp).toInt().coerceAtLeast(0),
            maxHp = g.stats.maxHp.toInt(),
            firewall = g.firewall.toInt(),
            firewallMax = g.stats.firewallMax.toInt(),
            xpPercent = (g.xpFraction * 100).toInt(),
            runLevel = g.runLevel,
            phase = g.phase,
            kind = g.plan.kind,
            bossName = b?.boss?.def?.name,
            bossPercent = if (b != null) ((b.hp / b.maxHp) * 100).toInt().coerceIn(0, 100) else 0,
            bossPhase = g.bossPhaseLabel,
            eventName = ev?.name,
            eventAccent = ev?.accent ?: 0,
            timedSeconds = if (g.plan.rules.timedSeconds > 0f && g.phase == Phase.COMBAT) kotlin.math.ceil(g.timedRemaining).toInt().coerceAtLeast(0) else -1,
            banner = g.banner,
            bannerSub = g.bannerSub,
            bannerVisible = g.bannerTimer > 0f,
            canRevive = g.canRevive,
            revivesLeft = g.revivesLeft,
            reviveTokens = save.current.reviveTokens,
            diamonds = save.current.diamonds,
            paidRevivesLeft = StoreCatalog.MAX_PAID_REVIVES_PER_RUN - paidRevives,
            rerolls = g.rerollsLeft,
            enemiesLeft = g.aliveCount(),
            offerSerial = g.offerSerial
        )
    }

    private fun onDeath() {
        val newAch = commitProgress(final = false)
        val s = engine.summary()
        result = RunResult(
            level = s.levelReached, score = s.score, kills = s.kills, bosses = s.bosses,
            euros = s.euros,
            newBestLevel = s.levelReached > startBestLevel,
            newBestScore = s.score > startBestScore,
            newAchievements = newAch
        )
    }

    fun chooseUpgrade(i: Int) = engine.chooseUpgrade(i)

    fun reroll() = engine.reroll()

    /** Paid (token / ◇) revives used this run; capped so a run can't be infinite. */
    private var paidRevives = 0

    /** Free revive first, then a revive token, then ◇100 (a 1-revive pack). */
    fun revive(): Boolean {
        if (engine.canRevive) {
            val ok = engine.revive()
            if (ok) result = null
            return ok
        }
        if (paidRevives >= StoreCatalog.MAX_PAID_REVIVES_PER_RUN) return false
        var paid = false
        save.update { p ->
            val next = StoreManager.useReviveToken(p)
                ?: StoreManager.buyRevives(p, StoreCatalog.revivePacks.first())?.let { StoreManager.useReviveToken(it) }
            if (next != null) paid = true
            next ?: p
        }
        if (!paid) return false
        paidRevives++
        val ok = engine.revive(paid = true)
        if (ok) result = null
        return ok
    }

    fun dismissTutorial() {
        showTutorial = false
        save.update { it.copy(tutorialDone = true) }
    }

    /** Called when leaving the run (menu / new operation / quit from pause). */
    fun finish() {
        commitProgress(final = true)
    }

    /**
     * Writes everything earned since the last commit. Records (best level,
     * score) are maxima so they can be written any number of times.
     * Returns names of achievements unlocked by this commit.
     */
    private fun commitProgress(final: Boolean): List<String> {
        val s = engine.summary()
        val dEuros = s.euros - committedEuros
        val dKills = s.kills - committedKills
        val dBosses = s.bosses - committedBosses
        val dEvents = s.events - committedEvents
        val dDiamonds = s.diamonds - committedDiamonds
        committedEuros = s.euros
        committedKills = s.kills
        committedBosses = s.bosses
        committedEvents = s.events
        committedDiamonds = s.diamonds
        val countRun = !committedRun
        committedRun = true
        val eventIds = if (engine.eventsCompleted > 0) engine.completedEventIds else emptySet()
        var unlocked: List<String> = emptyList()
        save.update { p ->
            val xpGain = Scoring.operativeXp(if (countRun) s.levelReached else 0, dKills, dBosses)
            val updated = p.copy(
                euros = p.euros + dEuros,
                diamonds = p.diamonds + dDiamonds,
                operativeXp = p.operativeXp + xpGain,
                highestLevel = maxOf(p.highestLevel, s.levelReached),
                bestScore = maxOf(p.bestScore, s.score),
                totalKills = p.totalKills + dKills,
                totalBosses = p.totalBosses + dBosses,
                totalEvents = p.totalEvents + dEvents,
                totalRuns = p.totalRuns + if (countRun) 1 else 0,
                longestRunSeconds = maxOf(p.longestRunSeconds, s.seconds),
                eventTypesCompleted = p.eventTypesCompleted + eventIds
            )
            val newly = Achievements.evaluate(updated, s)
            unlocked = newly.map { it.name }
            updated.copy(
                achievements = updated.achievements + newly.map { it.id },
                euros = updated.euros + newly.sumOf { it.rewardEuros.toLong() }
            )
        }
        return unlocked
    }
}
