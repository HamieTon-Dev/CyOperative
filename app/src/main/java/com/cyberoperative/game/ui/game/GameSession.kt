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
import com.cyberoperative.game.engine.AllyConfig
import com.cyberoperative.game.engine.CoopCodec
import com.cyberoperative.game.engine.CoopInput
import com.cyberoperative.game.engine.GameSound
import com.cyberoperative.game.net.CoopRoom
import com.cyberoperative.game.net.RoomState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
    val bossRole: String = "",
    val bossRoleColor: Long = 0,
    /** Threat tier 1–4 (skulls on the dossier card). */
    val bossTier: Int = 0,
    val bossBounty: Int = 0,
    val bossAbilities: List<String> = emptyList(),
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
    val skipShopPrompt: Boolean = false,
    val topGateLocked: Boolean = false,
    // --- Co-op ---
    val coop: Boolean = false,
    val partnerName: String = "",
    val partnerHp: Int = 0,
    val partnerMaxHp: Int = 1,
    val partnerDowned: Boolean = false,
    val partnerGone: Boolean = false,
    /** On the card screen with nothing left to pick: waiting for the partner. */
    val waitingForPartner: Boolean = false,
    /** Own operative is down and waiting for a revive. */
    val downed: Boolean = false,
    val reviveProgress: Float = 0f,
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

/** A live co-op room this run is played in. [scope] outlives the game screen (app level). */
class CoopLink(val room: CoopRoom, val state: RoomState, val scope: CoroutineScope)

/** No packets for this long and the partner counts as gone. */
private const val PARTNER_TIMEOUT = 8f

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
    restore: RunSnapshot? = null,
    /** Co-op run (owner, 2026-10-08), or null for solo. */
    val coop: CoopLink? = null
) {
    val mode: GameMode = restore?.let { r -> GameMode.entries.firstOrNull { it.name == r.mode } } ?: if (coop != null) GameMode.CAMPAIGN else mode
    val difficulty: Difficulty = restore?.let { Difficulty.byName(it.difficulty) }
        ?: coop?.let { Difficulty.byName(it.state.difficulty) } ?: difficulty
    val isCoop: Boolean get() = coop != null
    private val isHost: Boolean get() = coop?.room?.isHost != false
    /** Shown over the arena when something happens to the link ("PARTNER DISCONNECTED"). */
    var coopNotice by mutableStateOf<String?>(null)
        private set


    val engine: GameEngine
    var frameTick by mutableLongStateOf(0L)
        private set
    var hud by mutableStateOf(HudSnapshot())
        private set
    var paused by mutableStateOf(false)
    var showTutorial by mutableStateOf(!save.current.tutorialDone && restore == null && coop == null)
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

    // --- Co-op link state (declared before init, which starts the link) -------------
    private val netSounds = ArrayList<GameSound>()
    private var worldSeq = 0
    private var inputSeq = 0
    private var sendTimer = 0f
    /** Seconds since the partner was last heard from (starts with a grace period). */
    private var silence = -8f
    private var partnerLeft = false
    private var pickSerial = 0
    private var pickIndex = 0
    private var replaceIndex = -1
    private var pickedOnOffer = -1
    private var rerollSerial = 0
    private var partnerWasOnline = false

    /** Partner's look in co-op (their own skin and body). */
    val partnerSkin = OperativeSkins.byId(partnerPlayer()?.skin ?: save.current.selectedSkin)
    val partnerBody = BodyStyle.byId(partnerPlayer()?.body ?: save.current.operativeBody)
    private fun partnerPlayer() = coop?.let { if (it.room.isHost) it.state.guest else it.state.host }

    init {
        val p = save.current
        val link = coop
        val config = if (link != null) {
            // Both devices build the exact same run from the room: host = operative 0, guest = 1.
            val host = link.state.host ?: error("Room has no host")
            val guest = link.state.guest ?: error("Room has no guest")
            val hostCfg = Operatives.buildConfig("operative", host.permanent, link.state.seed)
            val guestCfg = Operatives.buildConfig("operative", guest.permanent, link.state.seed)
            hostCfg.copy(
                mode = GameMode.CAMPAIGN, difficulty = this.difficulty, opLevel = maxOf(host.opLevel, guest.opLevel),
                ally = AllyConfig(guestCfg.baseStats, guestCfg.rerolls, guestCfg.upgradeQuality, guestCfg.startingUpgrades, guest.opLevel, guestCfg.weaponSlots)
            )
        } else {
            Operatives.buildConfig(p.selectedOperative, p.permanentUpgrades, restore?.seed ?: System.nanoTime())
                .copy(mode = this.mode, difficulty = this.difficulty, opLevel = com.cyberoperative.game.ui.menu.operativeLevel(p.operativeXp))
        }
        engine = GameEngine(config, restore)
        if (link != null) {
            if (!link.room.isHost) engine.enterMirror(1)
            startLink(link)
        }
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
        if (coop != null) coopFrame(delta)
        else if (!paused && !showTutorial) engine.update(delta)
        for (s in engine.sounds) {
            audio.play(s)
            if (coop != null && isHost) netSounds += s
        }
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

    // --- Co-op link -----------------------------------------------------------------

    private fun startLink(link: CoopLink) {
        val room = link.room
        if (room.isHost) {
            link.scope.launch {
                room.inputs.collect { b ->
                    if (partnerLeft) return@collect
                    CoopCodec.decodeInput(b)?.let { engine.applyInput(1, it); silence = 0f }
                }
            }
        } else {
            link.scope.launch {
                room.worlds.collect { b ->
                    if (partnerLeft || !engine.mirror) return@collect
                    CoopCodec.decodeWorld(b)?.let { engine.applyWorld(it); silence = 0f }
                }
            }
        }
        link.scope.launch {
            room.state.collect { st ->
                val online = if (room.isHost) st.guestOnline else st.hostOnline
                if (online) partnerWasOnline = true
                else if (partnerWasOnline) partnerDropped()
            }
        }
    }

    /** The other device went away: the run carries on solo for whoever is left. */
    private fun partnerDropped() {
        if (partnerLeft || engine.phase == Phase.DEAD) return
        partnerLeft = true
        if (isHost) engine.removeOperative(1) else engine.promoteToSolo()
        coopNotice = "PARTNER DISCONNECTED · CONTINUING SOLO"
        coop?.let { l -> l.scope.launch { l.room.leave() } }
    }

    private fun coopFrame(delta: Float) {
        val link = coop ?: return
        // Co-op never pauses (owner, 2026-10-08): the pause menu is just a menu.
        if (isHost || !engine.mirror) engine.update(delta) else engine.mirrorTick(delta)
        if (partnerLeft) return
        silence += delta
        if (silence > PARTNER_TIMEOUT) { partnerDropped(); return }
        sendTimer += delta
        if (isHost) {
            if (sendTimer >= 0.1f) {
                sendTimer = 0f
                link.room.sendWorld(CoopCodec.encodeWorld(engine.captureWorld(++worldSeq, netSounds)))
                netSounds.clear()
            }
        } else if (sendTimer >= 1f / 15f) {
            sendTimer = 0f
            link.room.sendInput(
                CoopCodec.encodeInput(
                    CoopInput(++inputSeq, engine.localX, engine.localY, engine.localFacing, engine.localMoving, pickSerial, pickIndex, rerollSerial, engine.localLevel, replaceIndex)
                )
            )
        }
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
            bossRole = b?.boss?.def?.role?.label ?: "",
            bossRoleColor = b?.boss?.def?.role?.color ?: 0,
            bossTier = b?.boss?.def?.tier ?: 0,
            bossBounty = if (b != null && g.bossIntroElapsed >= 0f) g.bossBounty() else 0,
            bossAbilities = if (b != null && g.bossIntroElapsed >= 0f) b.boss?.def?.abilityNames() ?: emptyList() else emptyList(),
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
            paidRevivesLeft = if (coop != null) 0 else StoreCatalog.MAX_PAID_REVIVES_PER_RUN - paidRevives,
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
            shopGateRight = g.shopGateRight,
            skipShopPrompt = g.skipShopPrompt,
            topGateLocked = g.topGateLocked,
            coop = g.coop,
            partnerName = partnerPlayer()?.name ?: "",
            partnerHp = partnerOp()?.let { kotlin.math.ceil(it.hp).toInt().coerceAtLeast(0) } ?: 0,
            partnerMaxHp = partnerOp()?.build?.stats?.maxHp?.toInt()?.coerceAtLeast(1) ?: 1,
            partnerDowned = partnerOp()?.downed == true,
            partnerGone = partnerOp()?.gone != false,
            waitingForPartner = g.coop && g.phase == Phase.UPGRADE && g.pendingUpgrades <= 0,
            downed = g.operatives.getOrNull(g.primary)?.downed == true,
            reviveProgress = g.operatives.getOrNull(g.primary)?.reviveProgress?.let { (it / GameEngine.REVIVE_SECONDS * 10f).toInt() / 10f } ?: 0f
        )
    }

    // Rebuilt only when the build changes, so the HUD snapshot stays cheap.
    private var ownedCache: List<Pair<String, Int>> = emptyList()
    private var ownedKey = -1
    private fun partnerOp() = if (engine.coop) engine.operatives.getOrNull(1 - engine.primary) else null

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

    /** True when card [i] is a new weapon and all weapon slots are full (show the swap grid first). */
    fun needsWeaponSlot(i: Int): Boolean = engine.offerNeedsSlot(i)

    /** Before → after stat lines for taking [def] (and dropping weapon [remove]), for hold-for-details. */
    fun previewLines(def: com.cyberoperative.game.data.UpgradeDef, remove: String? = null): List<com.cyberoperative.game.engine.StatLine> =
        if (def.instant) emptyList()
        else com.cyberoperative.game.engine.StatCompare.lines(engine.build.stats, engine.build.preview(def, remove))

    /** Weapon slots this operative has (7 + WEAPON SLOTS upgrade). */
    fun weaponSlots(): Int = engine.build.weaponSlots

    /** Weapons equipped now, for the swap grid. */
    fun equippedWeapons(): List<Pair<com.cyberoperative.game.data.UpgradeDef, Int>> =
        engine.build.weapons().map { com.cyberoperative.game.data.Upgrades.byId(it) to engine.build.level(it) }

    fun chooseUpgrade(i: Int, replace: String? = null) {
        if (coop != null && !isHost && engine.mirror) {
            // Guest: the host applies the pick; one pick per shown set of cards.
            if (pickedOnOffer == engine.offerSerial) return
            pickedOnOffer = engine.offerSerial
            pickIndex = i
            replaceIndex = replace?.let { id -> com.cyberoperative.game.data.Upgrades.all.indexOfFirst { it.id == id } } ?: -1
            pickSerial++
            sendTimer = 1f
            return
        }
        engine.chooseUpgrade(i, replace)
    }

    fun reroll() {
        if (coop != null && !isHost && engine.mirror) {
            if (engine.rerollsLeft > 0) { rerollSerial++; sendTimer = 1f }
            return
        }
        engine.reroll()
    }

    fun buyShopItem(i: Int, replace: String? = null) = engine.buyShopItem(i, replace)

    fun shopNeedsSlot(i: Int) = engine.shopNeedsSlot(i)

    fun answerSkipShop(skip: Boolean) = engine.answerSkipShop(skip)

    /** Paid (token / ◇) revives used this run; capped so a run can't be infinite. */
    private var paidRevives = 0

    /** Free revive first, then a revive token, then ◇100 (a 1-revive pack). */
    fun revive(): Boolean {
        if (coop != null) return false
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
        coop?.let { l ->
            partnerLeft = true
            l.scope.launch {
                if (l.room.isHost) l.room.end()
                l.room.leave()
            }
        }
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
