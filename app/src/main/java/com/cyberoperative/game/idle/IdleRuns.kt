package com.cyberoperative.game.idle

import com.cyberoperative.game.data.Gear
import com.cyberoperative.game.save.PlayerProfile
import kotlinx.coroutines.flow.StateFlow

/**
 * IDLE RUNS entry point for the shared UI. The Android build installs a [Host] (saved run file,
 * background worker, home-screen widget, notifications); the PC build has none, so the Idle Runs
 * menu entry only shows on Android (owner, 0.37.0: "will not be used for desktop for now").
 */
object IdleRuns {
    interface Host {
        /** The current run (null: none), kept in step with the saved file. */
        val state: StateFlow<IdleRunState?>
        /** Advances the run to now and saves it ([refreshWidget]: redraw the home-screen widget too). */
        fun sync(refreshWidget: Boolean = true)
        /** Saves a brand-new run, schedules the background worker and refreshes the widget. */
        fun begin(run: IdleRunState)
        /** Replaces the saved run (e.g. ended by the player, or rewards collected). */
        fun store(run: IdleRunState?)
        /** Asks for the notification permission (Android 13+), once. */
        fun requestNotifications()
    }

    @Volatile
    var host: Host? = null

    val supported: Boolean get() = host != null

    /** The player recalls the operator: the run ends now and keeps what it has earned. */
    fun recall(s: IdleRunState, now: Long): IdleRunState =
        if (!s.active) s else IdleRunSimulator.advance(s, now).let {
            if (it.active) it.copy(status = IdleStatus.COMPLETED, endedAt = it.simulatedTo, endReason = "RECALLED BY PLAYER") else it
        }

    /**
     * Moves an ended run's rewards into the save, once: the € (shared by every character), the
     * XP to the character that ran it (active or not), and its gear into that character's
     * backpack, then the shared Data Vault when the backpack is full.
     */
    fun collect(p: PlayerProfile, s: IdleRunState): PlayerProfile {
        if (s.collected || s.active) return p
        val all = IdleRunSimulator.dropsWithIds(s, p.gearSeq)
        // AUTO-SELL (0.46.0) applies to idle finds too.
        val (items, sold) = com.cyberoperative.game.meta.GearBank.autoSell(all, p.settings.autoSellBelow)
        var out = p.copy(euros = p.euros + s.eurosEarned + sold, gearSeq = p.gearSeq + all.size)
        val mine = s.characterId.isEmpty() || s.characterId == p.characterId
        if (mine) {
            val room = (Gear.BACKPACK_SIZE - out.backpack.size).coerceAtLeast(0)
            val toPack = items.take(room)
            val toVault = items.drop(room).take((Gear.VAULT_SIZE - out.vault.size).coerceAtLeast(0))
            out = out.copy(operativeXp = out.operativeXp + s.xpEarned, backpack = out.backpack + toPack, vault = out.vault + toVault)
        } else {
            val other = out.otherCharacters.firstOrNull { it.id == s.characterId }
            if (other == null) {
                // The character was deleted: its gear still lands in the shared vault.
                out = out.copy(vault = out.vault + items.take((Gear.VAULT_SIZE - out.vault.size).coerceAtLeast(0)))
            } else {
                val room = (Gear.BACKPACK_SIZE - other.backpack.size).coerceAtLeast(0)
                val updated = other.copy(operativeXp = other.operativeXp + s.xpEarned, backpack = other.backpack + items.take(room))
                out = out.copy(
                    otherCharacters = out.otherCharacters.map { if (it.id == other.id) updated else it },
                    vault = out.vault + items.drop(room).take((Gear.VAULT_SIZE - out.vault.size).coerceAtLeast(0))
                )
            }
        }
        return out
    }
}
