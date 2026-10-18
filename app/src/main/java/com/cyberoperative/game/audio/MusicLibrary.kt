package com.cyberoperative.game.audio

/** Where a track came from in CyOps TD, which decides where it plays by default. */
enum class TrackGroup { MENU, LEVEL, BOSS, AMBIENT, INFERNO, INFERNO_BOSS,
    /** Owner's NEON tracks (0.37.5): any level, never a boss round, always as recorded. */
    NEON,
    /**
     * Owner's ARCADE tracks (0.54.0): everywhere (main menu, any level, boss fights) on every
     * difficulty, never sped up or slowed; reverb is the only effect they take.
     */
    ARCADE }

/** One owned CyOps TD soundtrack piece, titled with the owner's CyOps TD naming. */
/** [file]: the audio file's base name (Android raw resource / PC resource `music/<file>.ogg`). */
data class MusicTrack(val id: String, val file: String, val title: String, val group: TrackGroup) {
    /** Played exactly as recorded: never sped up or slowed, no pitch shift, EQ or reverb (owner, 0.37.5). */
    val plain: Boolean get() = group == TrackGroup.NEON
    /** Never sped up, slowed or pitch-shifted (NEON and ARCADE). */
    val fixedTempo: Boolean get() = group == TrackGroup.NEON || group == TrackGroup.ARCADE
    /** No boss EQ tilt (NEON and ARCADE); ARCADE still takes the boss reverb. */
    val noEq: Boolean get() = group == TrackGroup.NEON || group == TrackGroup.ARCADE
}

/**
 * The full CyOps TD soundtrack (owner, 2026-10-07: "use all of the music
 * assets"). 20 level tracks, 2 menu tracks, the 2 "Liminal" tracks and the
 * BOSS 2 variants (owner, 2026-10-08). Stored as Ogg Vorbis (~92 kbps) to keep
 * the download small.
 */
object MusicLibrary {

    private fun level(n: Int, part: Int, file: String) =
        MusicTrack("level${n}_$part", file, "CyOps TD - Level $n ($part)", TrackGroup.LEVEL)

    val all: List<MusicTrack> = listOf(
        // ARCADE (owner, 0.54.0): top of the playlist, above the main menu tracks.
        arcade("new_game", "New Game"),
        arcade("let_me_solo", "Let me Solo"),
        arcade("wanna_trade", "Wanna Trade?"),
        arcade("wanna_team_up", "Wanna Team Up?"),
        arcade("trade_request", "Trade Request"),
        arcade("save_and_quit", "Save and Quit?"),
        arcade("lost_my_gba", "Lost my GBA"),
        arcade("big_data_energy", "Big Data Energy"),
        arcade("big_data_energy_2", "Big Data Energy (2)"),
        arcade("memory_card", "Memory Card"),
        MusicTrack("menu_1", "track_menu", "CyOps TD - Main Menu (1)", TrackGroup.MENU),
        MusicTrack("menu_2", "track_menu_2", "CyOps TD - Main Menu (2)", TrackGroup.MENU),
        level(1, 1, "track_level1"), level(1, 2, "track_level1_2"),
        level(2, 1, "track_level2"), level(2, 2, "track_level2_2"),
        level(3, 1, "track_level3"), level(3, 2, "track_level3_2"),
        level(4, 1, "track_level4"), level(4, 2, "track_level4_2"),
        level(5, 1, "track_level5"), level(5, 2, "track_level5_2"),
        level(6, 1, "track_level6"), level(6, 2, "track_level6_2"),
        level(7, 1, "track_level7"), level(7, 2, "track_level7_2"),
        level(8, 1, "track_level8"), level(8, 2, "track_level8_2"),
        level(9, 1, "track_level9"), level(9, 2, "track_level9_2"),
        level(10, 1, "track_level10"), level(10, 2, "track_level10_2"),
        MusicTrack("boss2_1", "track_boss2_1", "CyOps - Boss 2 (1)", TrackGroup.BOSS),
        MusicTrack("boss2_2", "track_boss2_2", "CyOps - Boss 2 (2)", TrackGroup.BOSS),
        MusicTrack("boss2_remix_1", "track_boss2_remix_1", "CyOps - Boss 2 Remix (1)", TrackGroup.BOSS),
        MusicTrack("boss2_remix_2", "track_boss2_remix_2", "CyOps - Boss 2 Remix (2)", TrackGroup.BOSS),
        MusicTrack("liminal_space", "track_liminal_space", "CyOps TD - Liminal Space", TrackGroup.AMBIENT),
        MusicTrack("liminal_haze", "track_liminal_haze", "CyOps TD - Liminal Haze", TrackGroup.AMBIENT),
        // INFERNO (owner, 0.32.0): plays only on the INFERNO difficulties; listed in the player once Inferno is unlocked.
        inferno(1, 1, "track_inferno1"), inferno(1, 2, "track_inferno1_2"),
        inferno(2, 1, "track_inferno2"), inferno(2, 2, "track_inferno2_2"),
        inferno(3, 1, "track_inferno3"), inferno(3, 2, "track_inferno3_2"),
        inferno(4, 1, "track_inferno4"), inferno(4, 2, "track_inferno4_2"),
        MusicTrack("inferno_boss_1", "track_inferno_boss_1", "CyOps - Inferno Boss (1)", TrackGroup.INFERNO_BOSS),
        MusicTrack("inferno_boss_2", "track_inferno_boss_2", "CyOps - Inferno Boss (2)", TrackGroup.INFERNO_BOSS),
        // NEON (owner, 0.37.5): any level on any difficulty, never on a boss round, never altered.
        MusicTrack("neon_alley_1", "track_neon_alley_1", "Neon Alley 1987 (1)", TrackGroup.NEON),
        MusicTrack("neon_alley_2", "track_neon_alley_2", "Neon Alley 1987 (2)", TrackGroup.NEON),
        MusicTrack("neon_alley_3", "track_neon_alley_3", "Neon Alley 1987 (3)", TrackGroup.NEON),
        MusicTrack("neon_afterglow", "track_neon_afterglow", "Neon Afterglow 1987", TrackGroup.NEON),
        MusicTrack("neon_requiem", "track_neon_requiem", "Neon Requiem", TrackGroup.NEON)
    )

    private fun arcade(id: String, title: String) = MusicTrack("arcade_$id", "track_arcade_$id", title, TrackGroup.ARCADE)

    /** The ARCADE tracks: they join every rotation, boss fights included. */
    val arcade: List<MusicTrack> get() = all.filter { it.group == TrackGroup.ARCADE }

    private fun inferno(n: Int, part: Int, file: String) =
        MusicTrack("inferno${n}_$part", file, "CyOps - Inferno $n ($part)", TrackGroup.INFERNO)

    /** The run is on an INFERNO difficulty: its own soundtrack plays. Set by the game session. */
    @Volatile var inferno = false
    /** INFERNO unlocked on this profile: its tracks show in the music player. */
    @Volatile var infernoUnlocked = false

    private fun isInferno(t: MusicTrack) = t.group == TrackGroup.INFERNO || t.group == TrackGroup.INFERNO_BOSS

    /** Tracks the music player lists. */
    val listed: List<MusicTrack> get() = if (infernoUnlocked) all else all.filter { !isInferno(it) }

    fun byId(id: String?): MusicTrack? = all.firstOrNull { it.id == id }

    /** Default rotation for each music state (all shuffled). */
    fun poolFor(state: MusicState): List<MusicTrack> = when (state) {
        MusicState.NONE -> emptyList()
        MusicState.MENU -> all.filter { it.group == TrackGroup.MENU || it.group == TrackGroup.ARCADE }
        // In a run, the whole soundtrack shuffles.
        // The NEON tracks join both (owner: "any level"). Boss rounds below never get them.
        MusicState.COMBAT, MusicState.EVENT -> if (inferno) all.filter { it.group == TrackGroup.INFERNO || it.group == TrackGroup.NEON || it.group == TrackGroup.ARCADE } else all.filter { !isInferno(it) }
        // Bosses get the heaviest pieces: CyOps TD's level-10 tracks and the BOSS 2 variants.
        MusicState.BOSS -> if (inferno) all.filter { it.group == TrackGroup.INFERNO_BOSS || it.group == TrackGroup.ARCADE }
            else all.filter { it.id.startsWith("level10") || it.group == TrackGroup.BOSS || it.group == TrackGroup.ARCADE }
        MusicState.GAME_OVER -> all.filter { it.group == TrackGroup.AMBIENT }
    }
}

/**
 * Shuffle order with no repeats until every track in the pool has played,
 * and never the same track twice in a row across a reshuffle. Pure logic so
 * it can be unit tested without a MediaPlayer.
 */
class ShuffleBag(private val random: kotlin.random.Random = kotlin.random.Random.Default) {
    private val queue = ArrayDeque<MusicTrack>()
    private var poolIds: List<String> = emptyList()
    private val history = ArrayList<MusicTrack>()

    fun next(pool: List<MusicTrack>, avoid: MusicTrack?): MusicTrack? {
        if (pool.isEmpty()) return null
        if (pool.map { it.id } != poolIds) {
            poolIds = pool.map { it.id }
            queue.clear()
        }
        if (queue.isEmpty()) {
            val order = pool.shuffled(random).toMutableList()
            if (order.size > 1 && order.first() == avoid) order.add(order.removeAt(0))
            queue.addAll(order)
        }
        val t = queue.removeFirst()
        history += t
        if (history.size > 50) history.removeAt(0)
        return t
    }

    /** The track before [current] in play history (for PREVIOUS). */
    fun previous(current: MusicTrack?): MusicTrack? {
        val i = history.lastIndexOf(current)
        return if (i > 0) history[i - 1].also { repeat(history.size - i) { history.removeAt(history.lastIndex) } } else null
    }
}
