package com.cyberoperative.game.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cyberoperative.game.BuildConfig
import com.cyberoperative.game.R
import com.cyberoperative.game.ui.common.GameTextPaint

/**
 * ANDROID side of the platform seam. The Steam/PC build (desktop module) provides the same
 * declarations in its own `platform` package; everything else is shared source.
 */
object AppInfo {
    const val versionName: String = BuildConfig.VERSION_NAME
    const val versionCode: Int = BuildConfig.VERSION_CODE
    val debug: Boolean = BuildConfig.DEBUG
    /** PC (Steam) build: keyboard + mouse, Steam store and co-op. */
    const val desktop: Boolean = false
    /** Textured triangle meshes (Canvas.drawVertices) work on hardware canvases from Android 10. */
    val meshDrawing: Boolean = android.os.Build.VERSION.SDK_INT >= 29
}

@Composable
fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit) = BackHandler(enabled, onBack)

private val nativePaint = Paint().apply { isAntiAlias = true }
private val mono = Typeface.MONOSPACE
private val monoBold = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

/** Draws [text] with its baseline at ([x], [y]). */
fun DrawScope.drawGameText(text: String, x: Float, y: Float, p: GameTextPaint) {
    val n = nativePaint
    n.textSize = p.textSize
    n.color = p.color
    if (p.alpha < 255) n.alpha = (((p.color ushr 24) and 0xFF) * p.alpha / 255)
    n.typeface = if (p.bold) monoBold else mono
    n.textAlign = when (p.align) {
        GameTextPaint.Align.LEFT -> Paint.Align.LEFT
        GameTextPaint.Align.CENTER -> Paint.Align.CENTER
        GameTextPaint.Align.RIGHT -> Paint.Align.RIGHT
    }
    if (p.shadowRadius > 0f) n.setShadowLayer(p.shadowRadius, 0f, p.shadowDy, p.shadowColor) else n.clearShadowLayer()
    drawContext.canvas.nativeCanvas.drawText(text, x, y, n)
}

/** Width of [text] in [p] (layout). */
fun measureGameText(text: String, p: GameTextPaint): Float {
    nativePaint.textSize = p.textSize
    nativePaint.typeface = if (p.bold) monoBold else mono
    return nativePaint.measureText(text)
}

/** Gameplay: full immersive mode (re-applied on resume). */
@Composable
fun ImmersiveGameplay() {
    val view = LocalView.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(view, lifecycle) {
        val window = view.context.findActivity()?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            val obs = LifecycleEventObserver { _, e ->
                if (e == Lifecycle.Event.ON_RESUME) controller.hide(WindowInsetsCompat.Type.systemBars())
            }
            lifecycle.addObserver(obs)
            onDispose { lifecycle.removeObserver(obs) }
        }
    }
}

/** Keeps the display awake while composed (gameplay). */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** The developer splash banner. */
@Composable
fun bannerPainter(): Painter = painterResource(R.drawable.hamieton_banner)

/** A bundled image (gear atlases, Java resources in the APK); null if missing. */
fun loadImageResource(path: String): androidx.compose.ui.graphics.ImageBitmap? {
    val stream = AppInfo::class.java.classLoader?.getResourceAsStream(path) ?: return null
    val bmp = stream.use { android.graphics.BitmapFactory.decodeStream(it) } ?: return null
    return bmp.asImageBitmap()
}

/** Share text through the system share sheet (co-op room codes); PC copies to the clipboard. */
@Composable
fun rememberShareText(): (title: String, text: String) -> Unit {
    val ctx = LocalContext.current
    return { title, text ->
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        ctx.startActivity(android.content.Intent.createChooser(send, title))
    }
}

/** Copy text to the clipboard. */
@Composable
fun rememberCopyText(): (String) -> Unit {
    val ctx = LocalContext.current
    return { text ->
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("code", text))
    }
}

/** The device's 12/24-hour clock setting (HUD clock). */
@Composable
fun rememberIs24Hour(): Boolean = android.text.format.DateFormat.is24HourFormat(LocalContext.current)

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Android raw resource for a music file name (explicit, so the resource shrinker keeps them). */
private val rawMusic: Map<String, Int> = mapOf(
    "track_menu" to R.raw.track_menu,
    "track_menu_2" to R.raw.track_menu_2,
    "track_level1" to R.raw.track_level1,
    "track_level1_2" to R.raw.track_level1_2,
    "track_level2" to R.raw.track_level2,
    "track_level2_2" to R.raw.track_level2_2,
    "track_level3" to R.raw.track_level3,
    "track_level3_2" to R.raw.track_level3_2,
    "track_level4" to R.raw.track_level4,
    "track_level4_2" to R.raw.track_level4_2,
    "track_level5" to R.raw.track_level5,
    "track_level5_2" to R.raw.track_level5_2,
    "track_level6" to R.raw.track_level6,
    "track_level6_2" to R.raw.track_level6_2,
    "track_level7" to R.raw.track_level7,
    "track_level7_2" to R.raw.track_level7_2,
    "track_level8" to R.raw.track_level8,
    "track_level8_2" to R.raw.track_level8_2,
    "track_level9" to R.raw.track_level9,
    "track_level9_2" to R.raw.track_level9_2,
    "track_level10" to R.raw.track_level10,
    "track_level10_2" to R.raw.track_level10_2,
    "track_boss2_1" to R.raw.track_boss2_1,
    "track_boss2_2" to R.raw.track_boss2_2,
    "track_boss2_remix_1" to R.raw.track_boss2_remix_1,
    "track_boss2_remix_2" to R.raw.track_boss2_remix_2,
    "track_liminal_space" to R.raw.track_liminal_space,
    "track_liminal_haze" to R.raw.track_liminal_haze,
    "track_inferno1" to R.raw.track_inferno1,
    "track_inferno1_2" to R.raw.track_inferno1_2,
    "track_inferno2" to R.raw.track_inferno2,
    "track_inferno2_2" to R.raw.track_inferno2_2,
    "track_inferno3" to R.raw.track_inferno3,
    "track_inferno3_2" to R.raw.track_inferno3_2,
    "track_inferno4" to R.raw.track_inferno4,
    "track_inferno4_2" to R.raw.track_inferno4_2,
    "track_inferno_boss_1" to R.raw.track_inferno_boss_1,
    "track_inferno_boss_2" to R.raw.track_inferno_boss_2,
    // NEON (0.37.5): missing here until 0.45.3, so they played silently on Android.
    "track_neon_alley_1" to R.raw.track_neon_alley_1,
    "track_neon_alley_2" to R.raw.track_neon_alley_2,
    "track_neon_alley_3" to R.raw.track_neon_alley_3,
    "track_neon_afterglow" to R.raw.track_neon_afterglow,
    "track_neon_requiem" to R.raw.track_neon_requiem,
    // ARCADE (0.54.0).
    "track_arcade_new_game" to R.raw.track_arcade_new_game,
    "track_arcade_let_me_solo" to R.raw.track_arcade_let_me_solo,
    "track_arcade_wanna_trade" to R.raw.track_arcade_wanna_trade,
    "track_arcade_wanna_team_up" to R.raw.track_arcade_wanna_team_up,
    "track_arcade_trade_request" to R.raw.track_arcade_trade_request,
    "track_arcade_save_and_quit" to R.raw.track_arcade_save_and_quit,
    "track_arcade_lost_my_gba" to R.raw.track_arcade_lost_my_gba,
    "track_arcade_big_data_energy" to R.raw.track_arcade_big_data_energy,
    "track_arcade_big_data_energy_2" to R.raw.track_arcade_big_data_energy_2,
    "track_arcade_memory_card" to R.raw.track_arcade_memory_card
)

fun musicResource(file: String): Int = rawMusic[file] ?: error("Unknown music file $file")

/** Keyboard / controller input (PC only): phones steer with the on-screen stick. */
object GameKeys {
    val active: Boolean get() = false
    /** Set while a run is on screen (a controller's B button then isn't "back"). */
    var inGame = false
    val x: Float get() = 0f
    val y: Float get() = 0f
    /** Right stick held over its dead zone: aim and fire along ([aimX], [aimY]). */
    val stickAim: Boolean get() = false
    val aimX: Float get() = 0f
    val aimY: Float get() = 0f
    /** A controller is plugged in (UI hints say "press any button"). */
    val padConnected: Boolean get() = false
    /** True once per press of any controller button or Enter / Space (consumes it). */
    fun takeAnyPress(): Boolean = false
    /** HP charge / Packet Bomb key or button pressed since the last call. */
    fun takeHpCharge(): Boolean = false
    fun takePacketBomb(): Boolean = false
    fun takeDash(): Boolean = false
    /** M / controller BACK: toggle the world map. */
    fun takeMap(): Boolean = false
    /** I: open the INVENTORY (PC). */
    fun takeInventory(): Boolean = false
    fun takeSpell(slot: Int): Boolean = false
    /** A key's display name ("Q", "Space"). */
    fun keyName(code: Long): String = ""
    /** Settings: the next key pressed is handed to [onKey] (null = cancelled with Esc). */
    fun captureKey(onKey: (Long?) -> Unit) {}
}

/** Real-money ◇ checkout for this platform (Google Play Billing is not wired yet). */
object Checkout {
    const val storeName = "Google Play"
    val available: Boolean get() = false
    fun start(pack: com.cyberoperative.game.data.DiamondPack): String = "Purchases open when Google Play Billing is connected."
}

/** Keeps the system Back edge-swipe off the gameplay surface (Android 10+). */
fun androidx.compose.ui.Modifier.platformGestureExclusion(): androidx.compose.ui.Modifier =
    this.systemGestureExclusion()

/** Locks the screen to landscape (Android PC mode, landscape) or portrait (the default). */
@Composable
fun ScreenOrientation(landscape: Boolean) {
    val view = LocalView.current
    DisposableEffect(landscape) {
        view.context.findActivity()?.requestedOrientation =
            if (landscape) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { }
    }
}

/**
 * Clocks for anything that must not be fooled by changing the phone's time (Pet incubator,
 * 0.38.0). [mono] is time since boot (keeps counting while the app is closed and the phone
 * sleeps, ignores clock changes); [bootId] changes on every reboot.
 */
object PlatformClock {
    @Volatile var context: Context? = null
    fun wall(): Long = System.currentTimeMillis()
    fun mono(): Long = android.os.SystemClock.elapsedRealtime()
    fun bootId(): String {
        val boots = context?.let { runCatching { android.provider.Settings.Global.getInt(it.contentResolver, android.provider.Settings.Global.BOOT_COUNT) }.getOrNull() } ?: -1
        // Without a boot count, the boot time (to the minute) still changes on a reboot.
        return if (boots >= 0) "b$boots" else "t${(wall() - mono()) / 60_000L}"
    }
}
