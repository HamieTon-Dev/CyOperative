package com.cyberoperative.game

import com.cyberoperative.game.data.ArenaShape
import com.cyberoperative.game.data.Operatives
import com.cyberoperative.game.data.PermanentUpgrades
import com.cyberoperative.game.engine.Difficulty
import com.cyberoperative.game.engine.GameEngine
import com.cyberoperative.game.engine.GameMode
import org.junit.Test

/** PLAY → any difficulty → START must not crash (every class, both room shapes, maxed and fresh profiles). */
class StartRunCrashTest {
    @Test fun startingACampaignRunNeverCrashes() {
        val maxed = PermanentUpgrades.all.associate { it.id to it.maxLevel }
        for (wide in listOf(false, true)) for (perm in listOf(emptyMap(), maxed)) for (c in Operatives.all) for (d in Difficulty.entries) {
            ArenaShape.pcRules = true
            ArenaShape.wide = wide
            try {
                val cfg = Operatives.buildConfig(c.id, perm, 1234L).copy(mode = GameMode.CAMPAIGN, difficulty = d, consumables = true)
                val g = GameEngine(cfg)
                repeat(60 * 5) { g.update(1f / 60f) }
            } catch (t: Throwable) {
                throw AssertionError("crash: class=${c.id} diff=$d wide=$wide maxed=${perm.isNotEmpty()}", t)
            } finally { ArenaShape.wide = false; ArenaShape.pcRules = false }
        }
    }
}
