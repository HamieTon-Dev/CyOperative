package com.cyberoperative.game.i18n

import java.io.InputStreamReader
import java.util.Locale
import java.util.Properties
import java.util.zip.CRC32

/**
 * The translation engine, shared by the Android and Steam builds (owner, 0.55.0: "the entire game
 * translated ... all the same languages as CyOps TD").
 *
 * Every line the player sees is written in the code as English and wrapped in [tr]:
 * `tr("PAUSED")`, `tr("WORLD {0} CLEARED", n)`. `I18nCatalogTest` collects every such English line
 * into `app/i18n/en.properties`, each under a name derived from the text itself (`t_` + its
 * CRC-32), and each language is the same names in `resources/i18n/<tag>.properties`, a plain Java
 * resource both builds read from the classpath (no Android resources, so the PC build gets it
 * too). [tr] looks the English up in the chosen language and falls back to the English when a
 * language has no line for it, so a missing translation is never a crash or a blank.
 *
 * Placeholders are `{0}`, `{1}`… rather than `%1$s`, so a translator cannot break formatting.
 *
 * Proper names (pets, eggs, gear, bosses, enemies, weapons, skins, tracks) stay in English, as in
 * CyOps TD: they are never wrapped.
 *
 * Enums and objects read their text once, when first touched, so changing language after anything
 * was translated restarts the game ([Languages.apply]).
 */
object Tr {
    @Volatile
    private var table: Map<String, String> = emptyMap()

    /** English → this language's line, so text drawn every frame skips the CRC. */
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** The language being shown (a [Languages.all] tag). */
    @Volatile
    var tag: String = "en"
        private set

    /** Set by the first [t] after [init]: text may now be cached in English or the old language. */
    @Volatile
    var touched: Boolean = false
        private set

    /** Bumped by [init]; the UI keys its tree on it so a switch without a restart redraws everything. */
    @Volatile
    var generation: Int = 0
        private set

    /** The locale the game is showing, for anything that formats numbers or dates. */
    val locale: Locale get() = Locale.forLanguageTag(tag)

    /** Loads [languageTag] (a listed tag), or the device language when null. */
    fun init(languageTag: String?) {
        val chosen = languageTag?.takeIf { t -> Languages.all.any { it.tag == t } } ?: Languages.match(Locale.getDefault())
        table = if (chosen == "en") emptyMap() else load(chosen)
        cache.clear()
        tag = chosen
        touched = false
        generation++
    }

    private fun load(tag: String): Map<String, String> {
        val stream = Tr::class.java.classLoader?.getResourceAsStream("i18n/$tag.properties") ?: return emptyMap()
        val p = Properties()
        stream.use { p.load(InputStreamReader(it, Charsets.UTF_8)) }
        val out = HashMap<String, String>(p.size * 2)
        for (name in p.stringPropertyNames()) out[name] = p.getProperty(name)
        return out
    }

    /** The current language's line for [english], with `{0}`… filled from [args]. */
    fun t(english: String, vararg args: Any?): String {
        touched = true
        val t = table
        val template = if (t.isEmpty()) english else cache.getOrPut(english) { t[keyOf(english)] ?: english }
        return if (args.isEmpty()) template else fill(template, args)
    }

    /** The name an English line is stored under. */
    fun keyOf(english: String): String {
        val crc = CRC32()
        crc.update(english.toByteArray(Charsets.UTF_8))
        return "t_" + java.lang.Long.toHexString(crc.value).padStart(8, '0')
    }

    /** Replaces `{0}`, `{1}`… with [args]; anything else in braces is left alone. */
    fun fill(template: String, args: Array<out Any?>): String {
        val out = StringBuilder(template.length + 16)
        var i = 0
        while (i < template.length) {
            val c = template[i]
            if (c == '{') {
                val close = template.indexOf('}', i + 1)
                val index = if (close > i + 1) template.substring(i + 1, close).toIntOrNull() else null
                if (index != null && index in args.indices) {
                    out.append(args[index])
                    i = close + 1
                    continue
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }
}

/** Shorthand for [Tr.t]: the player's language for this English line. */
fun tr(english: String, vararg args: Any?): String = Tr.t(english, *args)
