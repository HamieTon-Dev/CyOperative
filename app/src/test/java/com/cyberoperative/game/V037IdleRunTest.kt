package com.cyberoperative.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.cyberoperative.game.data.Gear
import com.cyberoperative.game.data.GearRarity
import com.cyberoperative.game.data.GearSlot
import com.cyberoperative.game.data.InfernoBosses
import com.cyberoperative.game.data.OpProgress
import com.cyberoperative.game.engine.Difficulty
import com.cyberoperative.game.idle.IdleEnemy
import com.cyberoperative.game.idle.IdleRunSimulator
import com.cyberoperative.game.idle.IdleRunState
import com.cyberoperative.game.idle.IdleRuns
import com.cyberoperative.game.idle.IdleStatus
import com.cyberoperative.game.save.PlayerProfile
import com.cyberoperative.game.save.SaveRepository
import com.cyberoperative.game.ui.DeepLink
import com.cyberoperative.game.ui.menu.MenuTarget
import com.cyberoperative.game.widget.IdleRunRepository
import com.cyberoperative.game.widget.IdleRunWidget
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Idle Runs and the home-screen widget (0.37.0). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi", sdk = [35])
class V037IdleRunTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val hour = 3_600_000L
    private fun profile(xp: Long = 0, rarity: GearRarity? = null, inferno: Int = 0): PlayerProfile {
        val lv = OpProgress.of(xp).level
        val r = kotlin.random.Random(3)
        val gear = if (rarity == null) emptyMap() else GearSlot.entries.associateWith { Gear.roll(r, it.ordinal.toLong(), lv, rarity, it) }.filterValues { it.usableBy("operative") }
        return PlayerProfile(characterId = "c1", characterName = "ZERO", operativeXp = xp, equipped = gear, infernoUnlocked = inferno)
    }
    private fun run(p: PlayerProfile = profile(), d: Difficulty = Difficulty.MEDIUM, seed: Long = 1) = IdleRunSimulator.start(p, d, 0L, seed)

    @Test fun rewardsAreExactlyHalfOfTheNormalRun() {
        val s = IdleRunSimulator.advance(run(), 12 * hour)
        assertTrue(s.normalEuros > 0 && s.normalXp > 0)
        assertEquals(kotlin.math.floor(s.normalEuros * 0.4).toLong(), s.eurosEarned)
        assertEquals(kotlin.math.floor(s.normalXp * 0.5).toLong(), s.xpEarned)
        assertEquals(40L, IdleRunState(normalEuros = 101.0, normalXp = 100.0).eurosEarned)
        assertEquals(50L, IdleRunState(normalEuros = 101.0, normalXp = 100.0).xpEarned)
    }

    @Test fun operatorStatsAndGearChangeHowFarItGets() {
        fun avg(p: PlayerProfile) = (1..8).map { IdleRunSimulator.advance(run(p, seed = it.toLong()), 12 * hour).highestLevel }.average()
        val bare = avg(profile())
        val geared = avg(profile(rarity = GearRarity.LEGENDARY))
        assertTrue("legendary gear $geared vs none $bare", geared > bare)
        assertTrue(IdleRunSimulator.config(run(profile(rarity = GearRarity.LEGENDARY))).baseStats.damage > IdleRunSimulator.config(run()).baseStats.damage)
    }

    @Test fun chipsArePickedForTheBuildAndKeptWithTheRun() {
        val s = IdleRunSimulator.advance(run(profile(rarity = GearRarity.RARE)), 12 * hour)
        val cleared = s.highestLevel - 1
        assertTrue("chips ${s.chipLog.size} for $cleared levels", s.chipLog.size in cleared..(cleared + 2 * (cleared / 10) + 2))
        assertTrue(s.chips.isNotEmpty())
        val back = IdleRunState.decodeOrNull(IdleRunState.encode(s))!!
        assertEquals(s.chips, back.chips)
        // Different seeds pick different builds (not one perfect deterministic build).
        val other = IdleRunSimulator.advance(run(profile(rarity = GearRarity.RARE), seed = 99), 12 * hour)
        assertTrue(s.chipLog.take(5) != other.chipLog.take(5) || s.highestLevel != other.highestLevel)
    }

    @Test fun aBossEveryTenLevelsAndOneThreatAtATime() {
        val nine = run().copy(level = 9, queue = emptyList(), roomSize = 5, enemy = null, pause = 0f, simulatedTo = 0)
        val s = IdleRunSimulator.advance(nine, 10_000L)
        assertEquals(10, s.level)
        assertTrue(s.bossLevel)
        assertTrue("boss up: ${s.enemy}", s.enemy?.boss == true || s.pause > 0f)
        assertTrue(s.queue.size <= 1)
        assertEquals(20, run().copy(level = 11).nextBoss)
        assertEquals(10, run().copy(level = 10).nextBoss)
    }

    @Test fun standardAndInfernoPickTheirOwnThreatsAndBosses() {
        val std = run()
        val hell = run(profile(inferno = 3), Difficulty.inferno(2)!!)
        assertFalse(IdleRunSimulator.bossFor(std, 30) in InfernoBosses.all)
        assertTrue(IdleRunSimulator.bossFor(hell, 30) in InfernoBosses.all)
        assertTrue(com.cyberoperative.game.data.Environments.isInferno(com.cyberoperative.game.ui.game.IdleBattleArt.envOf(hell)))
        assertFalse(com.cyberoperative.game.data.Environments.isInferno(com.cyberoperative.game.ui.game.IdleBattleArt.envOf(std)))
        // Inferno threats are tougher at the same level.
        val a = IdleRunSimulator.advance(std.copy(pause = 0f), 100L).enemy!!
        val b = IdleRunSimulator.advance(hell.copy(pause = 0f), 100L).enemy!!
        assertTrue(b.maxHp > a.maxHp)
    }

    @Test fun infernoNeedsToBeUnlocked() {
        val locked = profile()
        assertNotNull(IdleRunSimulator.cannotStart(locked, Difficulty.inferno(1)!!))
        assertEquals(listOf(Difficulty.MEDIUM), IdleRunSimulator.choices(locked))
        assertNotNull(IdleRunSimulator.cannotStart(profile(inferno = 2), Difficulty.inferno(3)!!))
        assertNull(IdleRunSimulator.cannotStart(profile(inferno = 2), Difficulty.inferno(2)!!))
        assertTrue(runCatching { IdleRunSimulator.start(locked, Difficulty.inferno(1)!!, 0L) }.isFailure)
    }

    @Test fun defeatStopsTheRunAndPaysOutOnce() {
        val s = IdleRunSimulator.advance(run(), 12 * hour)
        assertEquals(IdleStatus.DEFEATED, s.status)
        assertTrue(s.endedAt in 1..(12 * hour))
        assertEquals(s, IdleRunSimulator.advance(s, 13 * hour))
        val p = profile().copy(euros = 10)
        val paid = IdleRuns.collect(p, s)
        assertEquals(10 + s.eurosEarned, paid.euros)
        assertEquals(s.xpEarned, paid.operativeXp)
        assertEquals(s.drops.size, paid.backpack.size)
        // Idle Runs keep only EPIC and better gear.
        val rich = IdleRunSimulator.advance(run(profile(rarity = GearRarity.LEGENDARY)), 12 * hour)
        assertTrue(rich.drops.isNotEmpty() && rich.drops.all { it.rarity >= GearRarity.EPIC })
        assertEquals(paid, IdleRuns.collect(paid, s.copy(collected = true)))
        // Recalled runs keep what they earned.
        val r = IdleRuns.recall(run(profile(rarity = GearRarity.RARE)), 60_000L)
        assertEquals(IdleStatus.COMPLETED, r.status)
    }

    @Test fun stateSerializesAndBadFilesAreIgnored() {
        val s = IdleRunSimulator.advance(run(), 90_000L).copy(enemy = IdleEnemy("malware", hp = 3f, maxHp = 9f, dps = 1f, euros = 2f, xp = 3f))
        assertEquals(s, IdleRunState.decodeOrNull(IdleRunState.encode(s)))
        assertNull(IdleRunState.decodeOrNull("{broken"))
        assertNotNull(IdleRunState.decodeOrNull("{}"))
    }

    @Test fun survivesAnAppRestart() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val s = IdleRunSimulator.advance(run(), 30_000L)
        IdleRunRepository(ctx).save(s)
        assertEquals(s, IdleRunRepository(ctx).load())
        IdleRunRepository(ctx).save(null)
        assertNull(IdleRunRepository(ctx).load())
    }

    @Test fun catchingUpInOneJumpMatchesManySmallSyncs() {
        val start = run(profile(rarity = GearRarity.RARE), seed = 7)
        val jump = IdleRunSimulator.advance(start, 20 * 60_000L)
        var steps = start
        var t = 0L
        while (t < 20 * 60_000L) { t += 15_000L; steps = IdleRunSimulator.advance(steps, t) }
        assertEquals(jump.level, steps.level)
        assertEquals(jump.kills, steps.kills)
        assertEquals(jump.status, steps.status)
    }

    @Test fun tappingTheWidgetOpensIdleRuns() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val pi = IdleRunWidget.openIdleRuns(ctx)
        val intent = Shadows.shadowOf(pi).savedIntent
        assertEquals(IdleRunWidget.ACTION_OPEN_IDLE_RUNS, intent.action)
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        // The app opens straight on the Idle Runs page.
        val save = SaveRepository(ctx)
        save.update { it.copy(characterId = "c1", characterName = "ZERO") }
        val host = FakeHost()
        IdleRuns.host = host
        try {
            DeepLink.open(MenuTarget.IDLE_RUNS)
            compose.mainClock.autoAdvance = false
            compose.setContent { com.cyberoperative.game.ui.AppRoot(save, com.cyberoperative.game.audio.AudioManager(ctx)) }
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithText("START IDLE RUN").assertExists()
            assertNull(DeepLink.pending.value)
        } finally { IdleRuns.host = null }
    }

    @Test fun oldSavesStillLoad() {
        val old = """{"version":1,"euros":120,"operativeXp":5000,"characterId":"a","characterName":"OLD"}"""
        val p = SaveRepository.decodeOrNull(old)!!
        assertEquals(120L, p.euros)
        assertEquals(MenuTarget.IDLE_RUNS, MenuTarget.valueOf("IDLE_RUNS"))
        assertFalse(MenuTarget.IDLE_RUNS in MenuTarget.grid)
    }

    /** The widget at real 2 × 3 sizes: everything visible and readable (≥ 8 sp text). */
    @Test fun widgetLaysOutAtTwoByThree() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val active = IdleRunSimulator.advance(run(profile(rarity = GearRarity.RARE)), 75_000L)
        val boss = IdleRunSimulator.advance(run(profile(rarity = GearRarity.LEGENDARY)).copy(level = 9, queue = emptyList(), roomSize = 5, pause = 0f), 6_000L)
        val hell = IdleRunSimulator.advance(run(profile(inferno = 3, rarity = GearRarity.LEGENDARY), Difficulty.inferno(3)!!), 40_000L)
        val dead = IdleRunSimulator.advance(run(), 12 * hour)
        val shots = listOf("active" to active, "boss" to boss, "inferno" to hell, "defeated" to dead, "none" to null)
        for ((w, h) in listOf(130 to 200, 160 to 250)) for ((name, s) in shots) {
            val v = IdleRunWidget.build(ctx, s, w).apply(ctx, FrameLayout(ctx))
            val d = ctx.resources.displayMetrics.density
            v.measure(View.MeasureSpec.makeMeasureSpec((w * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((h * d).toInt(), View.MeasureSpec.EXACTLY))
            v.layout(0, 0, v.measuredWidth, v.measuredHeight)
            for (t in texts(v)) if (t.isShown) {
                assertTrue("${t.text} is ${t.textSize / ctx.resources.displayMetrics.scaledDensity} sp", t.textSize / ctx.resources.displayMetrics.scaledDensity >= 8f)
                assertTrue("'${t.text}' fits ($name ${w}x$h)", t.bottom <= (t.parent as View).height + 1 && t.height > 0)
            }
            if (System.getProperty("cyberop.renderPreviews") == "true") {
                val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                v.draw(Canvas(bmp))
                val out = File(System.getProperty("cyberop.previewDir") ?: "build/screens").also { it.mkdirs() }
                File(out, "idle_widget_${name}_${w}x$h.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    @Test fun renderIdleRunsScreen() {
        assumeTrue(System.getProperty("cyberop.renderPreviews") == "true")
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val save = SaveRepository(ctx)
        save.update { it.copy(characterId = "c1", characterName = "ZERO", infernoUnlocked = 4) }
        val now = System.currentTimeMillis()
        val host = FakeHost(IdleRunSimulator.advance(run(profile(rarity = GearRarity.RARE)), 150_000L).let { it.copy(startedAt = now - 150_000L, simulatedTo = now) })
        IdleRuns.host = host
        try {
            compose.mainClock.autoAdvance = false
            compose.setContent { com.cyberoperative.game.ui.theme.CyberOperativeTheme { com.cyberoperative.game.ui.menu.IdleRunScreen(save, {}) {} } }
            compose.mainClock.advanceTimeBy(500)
            val view = compose.activity.window.decorView
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            compose.runOnUiThread { view.draw(Canvas(bmp)) }
            val out = File(System.getProperty("cyberop.previewDir") ?: "build/screens").also { it.mkdirs() }
            File(out, "idle_runs_screen.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { IdleRuns.host = null }
    }

    private fun texts(v: View): List<TextView> = when (v) {
        is TextView -> listOf(v)
        is ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }
        else -> emptyList()
    }

    /** A host without the worker or the widget (the screen only needs the state). */
    private class FakeHost(run: IdleRunState? = null) : IdleRuns.Host {
        override val state = MutableStateFlow(run)
        override fun sync(refreshWidget: Boolean) {}
        override fun begin(run: IdleRunState) { state.value = run }
        override fun store(run: IdleRunState?) { state.value = run }
        override fun requestNotifications() {}
    }
}
