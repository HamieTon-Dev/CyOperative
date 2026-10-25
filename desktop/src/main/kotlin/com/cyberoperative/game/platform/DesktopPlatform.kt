package com.cyberoperative.game.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadXmlImageVector
import com.cyberoperative.game.ui.common.GameTextPaint
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Paint
import org.jetbrains.skia.TextLine
import org.jetbrains.skia.Typeface
import org.xml.sax.InputSource
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * PC (Steam) side of the platform seam: the same declarations as the Android module's
 * platform/Platform.kt, implemented for Compose Desktop.
 */
object AppInfo {
    const val versionName: String = BuildInfo.VERSION_NAME
    const val versionCode: Int = BuildInfo.VERSION_CODE
    val debug: Boolean = System.getProperty("cyberop.debug") == "true"
    /** PC (Steam) build: keyboard + mouse, Steam store and co-op. */
    const val desktop: Boolean = true
    /** Textured triangle meshes (Canvas.drawVertices): always on Skia. */
    const val meshDrawing: Boolean = true
}

/**
 * Back on PC is the Esc key: the window forwards it to [DesktopBack], which calls the most
 * recently registered enabled handler (the same stacking as Android's BackHandler).
 */
object DesktopBack {
    private class Entry(var enabled: Boolean, var onBack: () -> Unit)
    private val stack = ArrayList<Entry>()

    /** True if a handler consumed the press. */
    fun dispatch(): Boolean {
        val e = stack.lastOrNull { it.enabled } ?: return false
        e.onBack()
        return true
    }

    @Composable
    internal fun Register(enabled: Boolean, onBack: () -> Unit) {
        val current = rememberUpdatedState(onBack)
        val entry = remember { Entry(enabled) { current.value() } }
        entry.enabled = enabled
        DisposableEffect(entry) {
            stack += entry
            onDispose { stack -= entry }
        }
    }
}

@Composable
fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit) = DesktopBack.Register(enabled, onBack)

// --- Canvas text through Skia --------------------------------------------------------

private val monoFace: Typeface = FontMgr.default.matchFamilyStyle("Consolas", FontStyle.NORMAL)
    ?: FontMgr.default.matchFamilyStyle("DejaVu Sans Mono", FontStyle.NORMAL)
    ?: FontMgr.default.matchFamilyStyle("monospace", FontStyle.NORMAL)
    ?: Typeface.makeEmpty()
private val monoBoldFace: Typeface = FontMgr.default.matchFamilyStyle("Consolas", FontStyle.BOLD)
    ?: FontMgr.default.matchFamilyStyle("DejaVu Sans Mono", FontStyle.BOLD)
    ?: FontMgr.default.matchFamilyStyle("monospace", FontStyle.BOLD)
    ?: monoFace
private val font = Font(monoFace, 14f)
private val fontBold = Font(monoBoldFace, 14f)
private val skPaint = Paint().apply { isAntiAlias = true }
private val shadowPaint = Paint().apply { isAntiAlias = true }

/** Draws [text] with its baseline at ([x], [y]), like android.graphics.Canvas.drawText. */
fun DrawScope.drawGameText(text: String, x: Float, y: Float, p: GameTextPaint) {
    if (text.isEmpty()) return
    val f = if (p.bold) fontBold else font
    f.size = p.textSize
    val line = TextLine.make(text, f)
    val w = line.width
    val left = when (p.align) {
        GameTextPaint.Align.LEFT -> x
        GameTextPaint.Align.CENTER -> x - w / 2f
        GameTextPaint.Align.RIGHT -> x - w
    }
    val a = ((p.color ushr 24) and 0xFF) * p.alpha / 255
    val canvas = drawContext.canvas.nativeCanvas
    if (p.shadowRadius > 0f) {
        val sa = ((p.shadowColor ushr 24) and 0xFF) * a / 255
        shadowPaint.color = (sa shl 24) or (p.shadowColor and 0xFFFFFF)
        canvas.drawTextLine(line, left, y + p.shadowDy, shadowPaint)
    }
    skPaint.color = (a shl 24) or (p.color and 0xFFFFFF)
    canvas.drawTextLine(line, left, y, skPaint)
}

/** Width of [text] in [p] (layout). */
fun measureGameText(text: String, p: GameTextPaint): Float {
    val f = if (p.bold) fontBold else font
    f.size = p.textSize
    return f.measureTextWidth(text)
}

/** The window is already full-screen-capable; nothing to hide on PC. */
@Composable
fun ImmersiveGameplay() {}

/** PCs don't sleep the screen mid-game the way phones do. */
@Composable
fun KeepScreenOn() {}

/** The developer splash banner (the Android vector drawable, loaded as-is). */
@Composable
fun bannerPainter(): Painter {
    val density = LocalDensity.current
    val vec = remember(density) {
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("images/hamieton_banner.xml")
            ?: error("banner missing")
        stream.use { loadXmlImageVector(InputSource(it), density) }
    }
    return rememberVectorPainter(vec)
}

/** A bundled image (gear atlases), decoded by Skia; null if missing. */
fun loadImageResource(path: String): androidx.compose.ui.graphics.ImageBitmap? {
    val bytes = Thread.currentThread().contextClassLoader?.getResourceAsStream(path)?.use { it.readBytes() }
        ?: AppInfo::class.java.classLoader?.getResourceAsStream(path)?.use { it.readBytes() }
        ?: return null
    return org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
}

/** PC has no share sheet: put the text on the clipboard. */
@Composable
fun rememberShareText(): (title: String, text: String) -> Unit = { _, text -> copyToClipboard(text) }

@Composable
fun rememberCopyText(): (String) -> Unit = { copyToClipboard(it) }

private fun copyToClipboard(text: String) {
    try { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) } catch (_: Throwable) {}
}

/** 24-hour clock unless the locale's short time format uses AM/PM. */
@Composable
fun rememberIs24Hour(): Boolean = remember {
    val pattern = (java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT) as? java.text.SimpleDateFormat)?.toPattern() ?: "HH:mm"
    !pattern.contains('a')
}

/** Real-money ◇ checkout: the Steam store (Steam Inventory item packs). */
object Checkout {
    const val storeName = "Steam"
    val available: Boolean get() = com.cyberoperative.game.steam.SteamService.running
    /** Opens the checkout; returns a message for the store screen. ◇ are credited when Steam confirms. */
    fun start(pack: com.cyberoperative.game.data.DiamondPack): String = com.cyberoperative.game.steam.SteamStore.startPurchase(pack)
}

/** No system edge gestures on PC. */
fun androidx.compose.ui.Modifier.platformGestureExclusion(): androidx.compose.ui.Modifier = this

/** The PC window is always landscape. */
@Composable
fun ScreenOrientation(landscape: Boolean) {}

/**
 * Clocks for anything that must not be fooled by changing the PC's time (Pet incubator,
 * 0.38.0). On PC [mono] only lasts as long as the game is open, so every launch is a new
 * "boot": time spent closed is checked against network time.
 */
object PlatformClock {
    private val process = java.util.UUID.randomUUID().toString()
    fun wall(): Long = System.currentTimeMillis()
    fun mono(): Long = System.nanoTime() / 1_000_000L
    fun bootId(): String = process
}

/**
 * The player's language pick (owner, 0.55.0): language.properties next to the save, read before
 * the save loads (see i18n.Languages).
 */
object LanguageStore {
    private val file: java.io.File get() = java.io.File(com.cyberoperative.game.save.SaveRepository.defaultDir(), "language.properties")

    private fun read(): java.util.Properties = java.util.Properties().also { p ->
        try { if (file.exists()) file.reader(Charsets.UTF_8).use { p.load(it) } } catch (_: Exception) {}
    }

    private fun write(change: (java.util.Properties) -> Unit) {
        val p = read()
        change(p)
        try {
            file.parentFile.mkdirs()
            file.writer(Charsets.UTF_8).use { p.store(it, null) }
        } catch (_: Exception) {}
    }

    /** The picked language tag, or null to follow the system language. */
    fun tag(): String? = read().getProperty("tag")
    /** The first-launch picker has been answered (or skipped for an existing install). */
    fun chosen(): Boolean = read().getProperty("chosen") == "true"
    fun markChosen() = write { it["chosen"] = "true" }
    fun save(tag: String) = write { it["tag"] = tag; it["chosen"] = "true" }
    /** One-shot: the next start skips the HamieTon.dev ident (a restart to change language). */
    fun takeSkipSplash(): Boolean {
        if (read().getProperty("skip_splash") != "true") return false
        write { it.remove("skip_splash") }
        return true
    }
    internal fun setSkipSplash() = write { it["skip_splash"] = "true" }
}

/**
 * Restarts the game (a language change). Only the packaged game can (its launcher is known from
 * jpackage.app-path); run from Gradle, the pick shows from the next start instead.
 */
object AppRestart {
    var beforeExit: () -> Unit = {}
    fun restart(): Boolean {
        val launcher = System.getProperty("jpackage.app-path")?.takeIf { java.io.File(it).exists() } ?: return false
        LanguageStore.setSkipSplash()
        // Steam lets go first, so the new process can take it over.
        beforeExit()
        try { ProcessBuilder(launcher).inheritIO().start() } catch (_: Exception) {}
        Runtime.getRuntime().exit(0)
        return true
    }
}
