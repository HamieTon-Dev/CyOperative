package com.cyberoperative.game.i18n

import com.cyberoperative.game.platform.LanguageStore
import java.util.Locale

/** One language the game ships in. [tag] names its file, `resources/i18n/<tag>.properties`. */
data class Language(
    val tag: String,
    /** The language's name in itself, as the picker lists it. */
    val nativeName: String,
    /** Its name in English, under the native one. */
    val englishName: String
)

/**
 * The languages (the same 26 as CyOps TD), the player's pick and the first-launch picker.
 *
 * The pick is kept by [LanguageStore], outside the save on purpose: it has to be read before
 * anything shows text, and loading the save already touches enums that hold translated text.
 */
object Languages {

    val all: List<Language> = listOf(
        Language("en", "English", "English"),
        Language("ar", "العربية", "Arabic"),
        Language("zh-CN", "简体中文", "Chinese (Simplified)"),
        Language("zh-TW", "繁體中文", "Chinese (Traditional)"),
        Language("cs", "Čeština", "Czech"),
        Language("da", "Dansk", "Danish"),
        Language("nl", "Nederlands", "Dutch"),
        Language("fi", "Suomi", "Finnish"),
        Language("fr", "Français", "French"),
        Language("de", "Deutsch", "German"),
        Language("hi", "हिन्दी", "Hindi"),
        Language("id", "Bahasa Indonesia", "Indonesian"),
        Language("it", "Italiano", "Italian"),
        Language("ja", "日本語", "Japanese"),
        Language("ko", "한국어", "Korean"),
        Language("nb", "Norsk bokmål", "Norwegian"),
        Language("pl", "Polski", "Polish"),
        Language("pt", "Português (Brasil)", "Portuguese (Brazil)"),
        Language("ru", "Русский", "Russian"),
        Language("es", "Español (España)", "Spanish (Spain)"),
        Language("es-419", "Español (Latinoamérica)", "Spanish (Latin America)"),
        Language("sv", "Svenska", "Swedish"),
        Language("th", "ไทย", "Thai"),
        Language("tr", "Türkçe", "Turkish"),
        Language("uk", "Українська", "Ukrainian"),
        Language("vi", "Tiếng Việt", "Vietnamese")
    )

    /** "Choose your language" in every language, for the first-launch picker's title. */
    val chooseTitle: Map<String, String> = mapOf(
        "en" to "CHOOSE YOUR LANGUAGE", "ar" to "اختر لغتك", "zh-CN" to "选择你的语言",
        "zh-TW" to "選擇你的語言", "cs" to "VYBERTE SI JAZYK", "da" to "VÆLG DIT SPROG",
        "nl" to "KIES JE TAAL", "fi" to "VALITSE KIELI", "fr" to "CHOISISSEZ VOTRE LANGUE",
        "de" to "WÄHLE DEINE SPRACHE", "hi" to "अपनी भाषा चुनें", "id" to "PILIH BAHASAMU",
        "it" to "SCEGLI LA TUA LINGUA", "ja" to "言語を選択", "ko" to "언어를 선택하세요",
        "nb" to "VELG SPRÅK", "pl" to "WYBIERZ JĘZYK", "pt" to "ESCOLHA SEU IDIOMA",
        "ru" to "ВЫБЕРИТЕ ЯЗЫК", "es" to "ELIGE TU IDIOMA", "es-419" to "ELIGE TU IDIOMA",
        "sv" to "VÄLJ SPRÅK", "th" to "เลือกภาษาของคุณ", "tr" to "DİLİNİ SEÇ",
        "uk" to "ОБЕРІТЬ МОВУ", "vi" to "CHỌN NGÔN NGỮ"
    )

    /** The listed language closest to [locale], English when none is. */
    fun match(locale: Locale): String {
        val lang = when (locale.language) {
            "in" -> "id"
            "no", "nn" -> "nb"
            else -> locale.language
        }
        return when (lang) {
            "zh" -> if (locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO")) "zh-TW" else "zh-CN"
            "es" -> if (locale.country.isEmpty() || locale.country == "ES") "es" else "es-419"
            else -> all.firstOrNull { it.tag == lang }?.tag ?: "en"
        }
    }

    /** The saved pick, or null for "follow the device". */
    fun saved(): String? = LanguageStore.tag()

    /** The language being shown. */
    fun current(): Language = all.firstOrNull { it.tag == Tr.tag } ?: all.first()

    /**
     * Called once at start-up, before anything shows text: loads the language, and decides whether
     * this is a first launch that should show the picker. An existing install that updates to this
     * version ([saveExisted]: the save was already there) never sees it and keeps showing the
     * device language, as before.
     */
    fun start(saveExisted: Boolean) {
        if (!LanguageStore.chosen() && saveExisted) LanguageStore.markChosen()
        Tr.init(saved())
    }

    /** True when the first-launch picker should show (fresh install, nothing picked yet). */
    fun needsPicker(): Boolean = !LanguageStore.chosen()

    /**
     * Saves [language] and shows the game in it. Straight away when nothing has been translated
     * yet; otherwise the game restarts so every line changes,
     * not just most of them. Returns false when the platform could not restart by itself (the PC
     * build run outside its launcher): the pick is saved and shows from the next start.
     */
    /**
     * The first-launch picker's choice: saved and shown straight away, never a restart. Only the
     * HamieTon.dev ident has drawn text by then, and it is redrawn, so nothing is left in the old
     * language.
     */
    fun pickFirstLaunch(language: Language) {
        LanguageStore.save(language.tag)
        if (language.tag != Tr.tag || Tr.touched) Tr.init(language.tag)
    }

    fun apply(language: Language): Boolean {
        LanguageStore.save(language.tag)
        if (language.tag == Tr.tag) return true
        if (!Tr.touched) {
            Tr.init(language.tag)
            return true
        }
        return com.cyberoperative.game.platform.AppRestart.restart()
    }
}
