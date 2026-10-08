package com.cyberoperative.game.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cyberoperative.game.audio.AudioManager
import com.cyberoperative.game.audio.MusicState
import com.cyberoperative.game.data.Operatives
import com.cyberoperative.game.engine.Difficulty
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.RunSnapshot
import com.cyberoperative.game.engine.GameMode
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
    val bossTitle: String = "",
    val bossTag: String = "",
    val bossColor: Long = 0,
    /** Seconds into the boss entrance (quantized), or -1 when none is running. */
    val bossIntro: Float = -1f,
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
    val offerSerial: Int = 0,
    val mode: GameMode = GameMode.CAMPAIGN,
    val levelKills: Int = 0,
    val levelThreats: Int = 0,
    val rewardBatchTotal: Int = 0,
    val rewardBatchTaken: Int = 0,
    /** Null when the run can be saved; otherwise why not (e.g. "[BOSS] SAVE BLOCKED"). */
    val saveBlockReason: String? = null,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    /** Owned upgrade ids and levels, in the order they were taken (bottom icon bar). */
    val owned: List<Pair<String, Int>> = emptyList(),
    /** Plasma Beam: heat 0..1 while usable, and seconds of overheat lockout left (0 = ready). */
    val beamHeat: Float = 0f,
    val beamCooldown: Float = 0f,
    // Upgrade shop
    val shopMessageSerial: Int = 0,
    val shopGateOpen: Boolean = false,
    val inShop: Boolean = false,
    val atShopCounter: Boolean = false,
    val shopItems: List<com.cyberoperative.game.engine.ShopItem> = emptyList(),
    val shopGateRight: Boolean = false,
    /** Data Vault: the cleared room's cache is waiting to be cracked. */
    val vaultReady: Boolean = false
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
class GameSession(
    private val save: SaveRepository,
    val audio: AudioManager,
    mode: GameMode = GameMode.CAMPAIGN,
    difficulty: Difficulty = Difficulty.MEDIUM,
    /** Continue a saved operation instead of starting a new one. */
    restore: RunSnapshot? = null
) {
    val mode: GameMode = restore?.let { r -> GameMode.entries.firstOrNull { it.name == r.mode } } ?: mode
    val difficulty: Difficulty = restore?.let { Difficulty.byName(it.difficulty) } ?: difficulty


    val engine: GameEngine
    var frameTick by mutableLongStateOf(0L)
        private set
    var hud by mutableStateOf(HudSnapshot())
        private set
    var paused by mutableStateOf(false)
    var showTutorial by mutableStateOf(!save.current.tutorialDone && restore == null)
    var result by mutableStateOf<RunResult?>(null)
        private set

    private var committedEuros = 0
    private var committedKills = 0
    private var committedBosses = 0
    private var committedEvents = 0
    private var committedDiamonds = 0
    private var committedRun = false
    private var lastPhase = Phase.COMBAT
    // Declared before init {} (which calls updateMusic).
    private var musicRoundLevel = -1
    private val musicRng = kotlin.random.Random(System.nanoTime())

    val skin = OperativeSkins.byId(save.current.selectedSkin)
    val background = LivingBackground.byId(save.current.selectedBackground)
    val body = BodyStyle.byId(save.current.operativeBody).let {
        // A premium body that is not owned (e.g. a restored old save) falls back to the default.
        if (it.premium && it.id !in save.current.ownedOperatives) BodyStyle.AGENT else it
    }

    private val startBestLevel: Int
    private val startBestScore: Long

    init {
        val p = save.current
        val config = Operatives.buildConfig(p.selectedOperative, p.permanentUpgrades, restore?.seed ?: System.nanoTime())
            .copy(mode = this.mode, difficulty = this.difficulty, opLevel = com.cyberoperative.game.ui.menu.operativeLevel(p.operativeXp))
        engine = GameEngine(config, restore)
        if (restore != null) {
            // Everything up to the save was already banked when it was taken.
            val s = engine.summary()
            committedEuros = s.euros; committedKills = s.kills; committedBosses = s.bosses
            committedEvents = s.events; committedDiamonds = s.diamonds; committedRun = true
            // A save is used once: continuing consumes it (autosave re-creates it).
            save.update { it.copy(savedRun = null) }
        }
        startBestLevel = if (this.mode == GameMode.CAMPAIGN) p.highestLevel else p.endlessBestStage
        startBestScore = if (this.mode == GameMode.CAMPAIGN) p.bestScore else p.endlessBestScore
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
        // Each new normal round rolls for the slow (0.75x) mix.
        if (engine.level != musicRoundLevel) {
            musicRoundLevel = engine.level
            audio.slowRound = engine.plan.kind == LevelKind.NORMAL &&
                !com.cyberoperative.game.core.Scaling.isBossLevel(engine.level) &&
                musicRng.nextFloat() < AudioManager.SLOW_ROUND_CHANCE
        }
        val state = when {
            engine.phase == Phase.DEAD -> MusicState.GAME_OVER
            // The whole boss level (and any Endless boss) runs on boss music.
            engine.boss != null || (engine.plan.kind == LevelKind.BOSS && engine.phase == Phase.COMBAT) -> MusicState.BOSS
            engine.plan.kind == LevelKind.EVENT -> MusicState.EVENT
            else -> MusicState.COMBAT
        }
        audio.setMusic(state)
        audio.ensureSpeed()
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
            bossName = b?.boss?.displayName,
            bossTitle = b?.boss?.def?.title ?: "",
            bossTag = b?.boss?.def?.tag ?: "",
            bossColor = b?.boss?.def?.color ?: 0,
            bossIntro = g.bossIntroElapsed.let { if (it < 0f) -1f else (it * 30f).toInt() / 30f },
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
            offerSerial = g.offerSerial,
            mode = g.mode,
            levelKills = g.levelKills,
            levelThreats = g.levelThreats,
            rewardBatchTotal = g.rewardBatchTotal,
            rewardBatchTaken = g.rewardBatchTaken,
            saveBlockReason = g.saveBlockReason,
            difficulty = difficulty,
            owned = ownedList(),
            beamHeat = (g.beamHeat / GameEngine.BEAM_MAX_FIRE * 20f).toInt() / 20f,
            beamCooldown = (g.beamCooldown * 10f).toInt() / 10f,
            shopMessageSerial = g.shopMessageSerial,
            shopGateOpen = g.shopGateOpen,
            inShop = g.inShop,
            atShopCounter = g.atShopCounter,
            shopItems = g.shopItems,
            vaultReady = g.vaultReady,
            shopGateRight = g.shopGateRight
        )
    }

    // Rebuilt only when the build changes, so the HUD snapshot stays cheap.
    private var ownedCache: List<Pair<String, Int>> = emptyList()
    private var ownedKey = -1
    private fun ownedList(): List<Pair<String, Int>> {
        val o = engine.build.owned()
        val key = o.values.sum() * 31 + o.size
        if (key != ownedKey) {
            ownedKey = key
            ownedCache = o.entries.map { it.key to it.value }
        }
        return ownedCache
    }

    private fun onDeath() {
        // A run that ended can't be continued from an earlier autosave.
        save.update { it.copy(savedRun = null) }
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

    fun buyShopItem(i: Int) = engine.buyShopItem(i)

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

    /** Called when leaving the run (menu / new operation / abort from pause). Ends the run for good. */
    fun finish() {
        audio.slowRound = false
        commitProgress(final = true)
        save.update { it.copy(savedRun = null) }
    }

    /**
     * SAVE & EXIT: banks progress so far and stores the run so the menu's
     * CONTINUE picks it up exactly here. False while saving is blocked (boss).
     */
    fun saveAndExit(): Boolean {
        val snap = engine.snapshot() ?: return false
        commitProgress(final = false)
        save.update { it.copy(savedRun = snap.encode()) }
        return true
    }

    /** Silent save when the app goes to the background, so a killed app can still continue. */
    fun autosave() {
        if (result != null) return
        val snap = engine.snapshot() ?: return
        commitProgress(final = false)
        save.update { it.copy(savedRun = snap.encode()) }
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
                // Shop purchases spend run € (a negative delta); never below zero.
                euros = (p.euros + dEuros).coerceAtLeast(0),
                diamonds = p.diamonds + dDiamonds,
                operativeXp = p.operativeXp + xpGain,
                // Endless keeps its own records so the campaign ladder stays meaningful.
                highestLevel = if (mode == GameMode.CAMPAIGN) maxOf(p.highestLevel, s.levelReached) else p.highestLevel,
                bestScore = if (mode == GameMode.CAMPAIGN) maxOf(p.bestScore, s.score) else p.bestScore,
                endlessBestStage = if (mode == GameMode.ENDLESS) maxOf(p.endlessBestStage, s.levelReached) else p.endlessBestStage,
                endlessBestScore = if (mode == GameMode.ENDLESS) maxOf(p.endlessBestScore, s.score) else p.endlessBestScore,
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
