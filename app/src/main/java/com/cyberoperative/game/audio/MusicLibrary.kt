package com.cyberoperative.game.audio

import com.cyberoperative.game.R

/** Where a track came from in CyOps TD, which decides where it plays by default. */
enum class TrackGroup { MENU, LEVEL, BOSS, AMBIENT }

/** One owned CyOps TD soundtrack piece, titled with the owner's CyOps TD naming. */
data class MusicTrack(val id: String, val res: Int, val title: String, val group: TrackGroup)

/**
 * The full CyOps TD soundtrack (owner, 2026-10-07: "use all of the music
 * assets"). 20 level tracks, 2 menu tracks, the 2 "Liminal" tracks and the
 * BOSS 2 variants (owner, 2026-10-08). Stored as Ogg Vorbis (~92 kbps) to keep
 * the download small.
 */
object MusicLibrary {

    private fun level(n: Int, part: Int, res: Int) =
        MusicTrack("level${n}_$part", res, "CyOps TD - Level $n ($part)", TrackGroup.LEVEL)

    val all: List<MusicTrack> = listOf(
        MusicTrack("menu_1", R.raw.track_menu, "CyOps TD - Main Menu (1)", TrackGroup.MENU),
        MusicTrack("menu_2", R.raw.track_menu_2, "CyOps TD - Main Menu (2)", TrackGroup.MENU),
        level(1, 1, R.raw.track_level1), level(1, 2, R.raw.track_level1_2),
        level(2, 1, R.raw.track_level2), level(2, 2, R.raw.track_level2_2),
        level(3, 1, R.raw.track_level3), level(3, 2, R.raw.track_level3_2),
        level(4, 1, R.raw.track_level4), level(4, 2, R.raw.track_level4_2),
        level(5, 1, R.raw.track_level5), level(5, 2, R.raw.track_level5_2),
        level(6, 1, R.raw.track_level6), level(6, 2, R.raw.track_level6_2),
        level(7, 1, R.raw.track_level7), level(7, 2, R.raw.track_level7_2),
        level(8, 1, R.raw.track_level8), level(8, 2, R.raw.track_level8_2),
        level(9, 1, R.raw.track_level9), level(9, 2, R.raw.track_level9_2),
        level(10, 1, R.raw.track_level10), level(10, 2, R.raw.track_level10_2),
        MusicTrack("boss2_1", R.raw.track_boss2_1, "CyOps - Boss 2 (1)", TrackGroup.BOSS),
        MusicTrack("boss2_2", R.raw.track_boss2_2, "CyOps - Boss 2 (2)", TrackGroup.BOSS),
        MusicTrack("boss2_remix_1", R.raw.track_boss2_remix_1, "CyOps - Boss 2 Remix (1)", TrackGroup.BOSS),
        MusicTrack("boss2_remix_2", R.raw.track_boss2_remix_2, "CyOps - Boss 2 Remix (2)", TrackGroup.BOSS),
        MusicTrack("liminal_space", R.raw.track_liminal_space, "CyOps TD - Liminal Space", TrackGroup.AMBIENT),
        MusicTrack("liminal_haze", R.raw.track_liminal_haze, "CyOps TD - Liminal Haze", TrackGroup.AMBIENT)
    )

    fun byId(id: String?): MusicTrack? = all.firstOrNull { it.id == id }

    /** Default rotation for each music state (all shuffled). */
    fun poolFor(state: MusicState): List<MusicTrack> = when (state) {
        MusicState.NONE -> emptyList()
        MusicState.MENU -> all.filter { it.group == TrackGroup.MENU }
        // In a run, the whole soundtrack shuffles.
        MusicState.COMBAT, MusicState.EVENT -> all
        // Bosses get the heaviest pieces: CyOps TD's level-10 tracks and the BOSS 2 variants.
        MusicState.BOSS -> all.filter { it.id.startsWith("level10") || it.group == TrackGroup.BOSS }
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
