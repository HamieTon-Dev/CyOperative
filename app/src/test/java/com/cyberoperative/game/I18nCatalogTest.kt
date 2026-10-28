package com.cyberoperative.game

import com.cyberoperative.game.i18n.Languages
import com.cyberoperative.game.i18n.Tr
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The translation catalogue (see `Tr`, 0.55.0).
 *
 * Scans the shared and PC-only source for every `tr("…")` and checks `app/i18n/en.properties`
 * holds exactly those lines. Run with `I18N_WRITE=1` to (re)write that file after adding or
 * changing text. Also checks every language file against it: no line the game no longer has,
 * every `{0}`-style and `%d`-style placeholder kept, and every language listed in the picker.
 */
class I18nCatalogTest {

    private val sources = listOf(File("src/main/java"), File("../desktop/src/main/kotlin"))
    private val english = File("i18n/en.properties")
    private val langDir = File("src/main/resources/i18n")

    @Test
    fun `the English catalogue holds exactly the lines the code asks for`() {
        val found = I18nScanner.scan(sources)
        if (found.problems.isNotEmpty()) {
            fail("tr() must be given plain string literals:\n" + found.problems.joinToString("\n"))
        }
        val text = I18nProps.write(found.lines.associateBy { Tr.keyOf(it) })
        if (System.getenv("I18N_WRITE") == "1") {
            english.parentFile.mkdirs()
            english.writeText(text)
            return
        }
        assertTrue("missing ${english.path}: run the tests with I18N_WRITE=1", english.exists())
        assertEquals("en.properties is out of date: run the tests with I18N_WRITE=1", text, english.readText())
    }

    @Test
    fun `no two English lines share a key`() {
        val lines = I18nScanner.scan(sources).lines
        val byKey = lines.groupBy { Tr.keyOf(it) }.filterValues { it.size > 1 }
        assertTrue("CRC collision: $byKey", byKey.isEmpty())
    }

    @Test
    fun `every language file matches the catalogue and keeps its placeholders`() {
        val source = I18nProps.read(english)
        val problems = ArrayList<String>()
        for (file in translationFiles()) {
            val lang = file.nameWithoutExtension
            for ((key, text) in I18nProps.read(file)) {
                val en = source[key]
                if (en == null) {
                    problems += "$lang: $key is not in the English catalogue"
                    continue
                }
                if (placeholders(en) != placeholders(text)) {
                    problems += "$lang: $key placeholders ${placeholders(text)} != English ${placeholders(en)} ($en)"
                }
                if (formats(en) != formats(text)) {
                    problems += "$lang: $key format codes ${formats(text)} != English ${formats(en)} ($en)"
                }
            }
        }
        assertTrue(problems.take(60).joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `every language translates the whole catalogue`() {
        val source = I18nProps.read(english).keys
        val gaps = translationFiles().mapNotNull { f ->
            val missing = source - I18nProps.read(f).keys
            if (missing.isEmpty()) null else "${f.nameWithoutExtension}: ${missing.size} lines missing"
        }
        assertTrue(gaps.joinToString("\n"), gaps.isEmpty())
    }

    @Test
    fun `the picker lists exactly the languages there are files for`() {
        val files = translationFiles().map { it.nameWithoutExtension }.toSet()
        val listed = Languages.all.filter { it.tag != "en" }.map { it.tag }.toSet()
        assertEquals("listed but no file", emptySet<String>(), listed - files)
        assertEquals("file but not listed", emptySet<String>(), files - listed)
        assertEquals("a language listed twice", Languages.all.size, Languages.all.map { it.tag }.toSet().size)
        assertEquals("every language has a picker title", Languages.all.map { it.tag }.toSet(), Languages.chooseTitle.keys)
    }

    @Test
    fun `device languages map onto the listed ones`() {
        assertEquals("zh-TW", Languages.match(Locale.forLanguageTag("zh-HK")))
        assertEquals("zh-TW", Languages.match(Locale.forLanguageTag("zh-Hant")))
        assertEquals("zh-CN", Languages.match(Locale.forLanguageTag("zh")))
        assertEquals("es-419", Languages.match(Locale.forLanguageTag("es-MX")))
        assertEquals("bg", Languages.match(Locale.forLanguageTag("bg-BG")))
        assertEquals("es", Languages.match(Locale.forLanguageTag("es-ES")))
        assertEquals("pt", Languages.match(Locale.forLanguageTag("pt-PT")))
        assertEquals("nb", Languages.match(Locale.forLanguageTag("nn-NO")))
        assertEquals("id", Languages.match(Locale("in", "ID")))
        assertEquals("en", Languages.match(Locale.forLanguageTag("eu")))
    }

    @Test
    fun `the engine loads a language from the classpath and falls back to English`() {
        try {
            Tr.init("de")
            val de = I18nProps.read(File(langDir, "de.properties"))
            val en = I18nProps.read(english)
            val (key, german) = de.entries.first { !it.value.contains('{') && !it.value.contains('%') }
            assertEquals(german, Tr.t(en.getValue(key)))
            assertEquals("not in any catalogue", Tr.t("not in any catalogue"))
        } finally {
            Tr.init("en")
        }
    }

    @Test
    fun `the first-launch pick switches language straight away after the splash drew text`() {
        try {
            Tr.init("en")
            assertEquals("LANGUAGE", Tr.t("LANGUAGE"))
            assertTrue(Tr.touched)
            Languages.pickFirstLaunch(Languages.all.first { it.tag == "fr" })
            assertEquals("fr", Tr.tag)
            val fr = I18nProps.read(File(langDir, "fr.properties"))
            assertEquals(fr.getValue(Tr.keyOf("LANGUAGE")), Tr.t("LANGUAGE"))
        } finally {
            Tr.init("en")
        }
    }

    private fun translationFiles(): List<File> =
        langDir.listFiles().orEmpty().filter { it.extension == "properties" }.sortedBy { it.name }

    private fun placeholders(text: String): List<String> =
        Regex("\\{\\d+}").findAll(text).map { it.value }.sorted().toList()

    private fun formats(text: String): List<String> =
        Regex("%[-#+0,(]*\\d*(\\.\\d+)?[dfsx%]").findAll(text).map { it.value }.toList()
}

/** Finds every `tr("…")` in the Kotlin source. */
object I18nScanner {
    data class Result(val lines: Set<String>, val problems: List<String>)

    private val call = Regex("(?<![A-Za-z0-9_.])tr\\(")

    fun scan(roots: List<File>): Result {
        val lines = java.util.TreeSet<String>()
        val problems = ArrayList<String>()
        for (root in roots) root.walkTopDown().filter { it.extension == "kt" }.sortedBy { it.path }.forEach { file ->
            if (file.name == "Tr.kt") return@forEach
            val src = file.readText()
            for (m in call.findAll(src)) {
                val where = "${file.path}:${src.substring(0, m.range.first).count { it == '\n' } + 1}"
                val parsed = parseLiterals(src, m.range.last + 1)
                if (parsed == null) problems += "$where: not a plain string literal"
                else lines += parsed
            }
        }
        return Result(lines, problems)
    }

    /** One literal, or several joined with `+`, starting at [start]; null if anything else. */
    private fun parseLiterals(src: String, start: Int): String? {
        var i = skipSpace(src, start)
        val out = StringBuilder()
        while (true) {
            if (i >= src.length || src[i] != '"' || src.startsWith("\"\"\"", i)) return null
            i++
            while (true) {
                if (i >= src.length) return null
                val c = src[i]
                when {
                    c == '"' -> { i++; break }
                    c == '\n' -> return null
                    c == '$' && i + 1 < src.length && (src[i + 1] == '{' || src[i + 1].isLetter()) -> return null
                    c == '\\' -> {
                        when (val e = src[i + 1]) {
                            'n' -> out.append('\n'); 't' -> out.append('\t'); 'r' -> out.append('\r')
                            'b' -> out.append('\b')
                            '"', '\'', '\\', '$' -> out.append(e)
                            'u' -> { out.append(src.substring(i + 2, i + 6).toInt(16).toChar()); i += 4 }
                            else -> return null
                        }
                        i += 2
                    }
                    else -> { out.append(c); i++ }
                }
            }
            val j = skipSpace(src, i)
            if (j < src.length && src[j] == '+' && skipSpace(src, j + 1).let { it < src.length && src[it] == '"' }) {
                i = skipSpace(src, j + 1)
                continue
            }
            return out.toString()
        }
    }

    private fun skipSpace(src: String, from: Int): Int {
        var i = from
        while (i < src.length && src[i].isWhitespace()) i++
        return i
    }
}

/**
 * Reads and writes the `.properties` catalogues: `key=text`, UTF-8 as is (no `\u` escapes, so a
 * translator can read them), with backslashes, newlines and a leading space escaped.
 */
object I18nProps {
    fun write(lines: Map<String, String>): String {
        val sb = StringBuilder()
        sb.append("# GENERATED by I18nCatalogTest (I18N_WRITE=1) from every tr(\"…\") in the code. Do not edit.\n")
        for ((key, text) in lines.toSortedMap()) sb.append(key).append('=').append(escape(text)).append('\n')
        return sb.toString()
    }

    fun escape(text: String): String {
        val sb = StringBuilder()
        for ((i, c) in text.withIndex()) {
            when {
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\t' -> sb.append("\\t")
                c == '\r' -> sb.append("\\r")
                c == ' ' && i == 0 -> sb.append("\\ ")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    fun read(file: File): Map<String, String> {
        val p = java.util.Properties()
        file.reader(Charsets.UTF_8).use { p.load(it) }
        return p.stringPropertyNames().associateWith { p.getProperty(it) }
    }
}
