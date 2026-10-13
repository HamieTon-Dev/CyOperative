package com.cyberoperative.game

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.cyberoperative.game.data.Gear
import com.cyberoperative.game.data.GearRarity
import com.cyberoperative.game.data.GearSlot
import com.cyberoperative.game.meta.Characters
import com.cyberoperative.game.meta.GearBank
import com.cyberoperative.game.save.CharacterLook
import com.cyberoperative.game.save.PlayerProfile
import com.cyberoperative.game.save.SaveRepository
import com.cyberoperative.game.ui.menu.CharTab
import com.cyberoperative.game.ui.menu.CharacterCreateScreen
import com.cyberoperative.game.ui.menu.CharacterScreen
import com.cyberoperative.game.ui.theme.CyberOperativeTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

/** Data Vault update renders (character, gear, vault, creator); `-PrenderPreviews` only. */
abstract class GearRenderBase {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    protected val enabled = System.getProperty("cyberop.renderPreviews") == "true"
    private val outDir = File(System.getProperty("cyberop.previewDir") ?: "build/screens")

    protected fun save(name: String) {
        outDir.mkdirs()
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** A Technician "Relay" at OP Lv 24 with a full kit, a filled backpack and some vault. */
    protected fun repo(): SaveRepository {
        val r = SaveRepository(ApplicationProvider.getApplicationContext<android.app.Application>())
        r.update { p0 ->
            var p = Characters.create(PlayerProfile(), "Relay", "technician", CharacterLook(1, 0, 1, 0))
            p = p.copy(operativeXp = com.cyberoperative.game.data.OpProgress.totalFor(24) + 500)
            val rng = Random(7)
            val loot = (0 until 140).map { i ->
                val r1 = GearRarity.entries[minOf(7, (rng.nextFloat() * rng.nextFloat() * 9).toInt())]
                Gear.roll(rng, 0, 22 + rng.nextInt(5), r1)
            }
            p = GearBank.bank(p, loot).first
            for (s in GearSlot.entries) {
                val pick = p.backpack.firstOrNull { it.slot == s && it.usableBy("technician") } ?: p.vault.first { it.slot == s && it.usableBy("technician") }
                p = GearBank.equip(p, pick.uid)
            }
            p.copy(diamonds = p0.diamonds, tutorialDone = true)
        }
        return r
    }

    protected fun show(name: String, tab: CharTab, select: Boolean = true) {
        assumeTrue(enabled)
        val r = repo()
        compose.mainClock.autoAdvance = false
        // The best piece in the backpack (or vault) selected, so the item card shows.
        val pool = if (tab == CharTab.VAULT) r.current.vault else r.current.backpack
        val best = if (select) pool.maxByOrNull { it.rarity.ordinal * 100 + it.level }?.uid else null
        compose.setContent { CyberOperativeTheme { CharacterScreen(r, onBack = {}, startTab = tab, onDeploy = {}, onEditLook = {}, onNewCharacter = {}, initialSelected = best) } }
        compose.mainClock.advanceTimeBy(1200)
        save(name)
    }


    protected fun creator(name: String) {
        assumeTrue(enabled)
        val r = repo()
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { CharacterCreateScreen(r, editing = false, onBack = {}, onDone = {}) } }
        compose.mainClock.advanceTimeBy(800)
        save(name)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w900dp-h416dp-land-xhdpi", sdk = [35])
class GearScreenRendersWide : GearRenderBase() {
    @Test fun overview() = show("character_overview_wide", CharTab.OVERVIEW, select = false)
    @Test fun gear() = show("character_gear_wide", CharTab.GEAR)
    @Test fun vault() = show("data_vault_wide", CharTab.VAULT)
    @Test fun create() = creator("create_character_wide")
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [35])
class GearScreenRendersPortrait : GearRenderBase() {
    @Test fun gear() = show("character_gear_portrait", CharTab.GEAR)
    @Test fun vault() = show("data_vault_portrait", CharTab.VAULT)
    @Test fun create() = creator("create_character_portrait")

    /** Gear on the floor, each in its rarity beam, around a cleared room. */
    @Test fun floorDrops() {
        assumeTrue(enabled)
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val r = repo()
        val session = com.cyberoperative.game.ui.game.GameSession(r, com.cyberoperative.game.audio.AudioManager(ctx), com.cyberoperative.game.engine.GameMode.CAMPAIGN)
        val g = session.engine
        g.purgeHostiles()
        val rng = Random(3)
        for ((i, rar) in GearRarity.entries.withIndex()) {
            val a = i * 0.785f
            val item = Gear.roll(rng, i.toLong(), 20, rar)
            g.loot += com.cyberoperative.game.engine.Loot(com.cyberoperative.game.engine.LootKind.GEAR, g.px + kotlin.math.cos(a) * 170f, g.py - 60f + kotlin.math.sin(a) * 150f)
                .also { it.gear = item; it.age = 0.5f; it.owner = 0 }
        }
        compose.mainClock.autoAdvance = false
        compose.setContent { CyberOperativeTheme { com.cyberoperative.game.ui.game.GameScreen(session, true, {}, {}) } }
        compose.mainClock.advanceTimeBy(300)
        save("gear_floor_drops")
    }
}
