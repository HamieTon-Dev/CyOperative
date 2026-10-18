package com.cyberoperative.game.meta

import com.cyberoperative.game.data.Gear
import com.cyberoperative.game.data.GearItem
import com.cyberoperative.game.data.GearSlot
import com.cyberoperative.game.data.Operatives
import com.cyberoperative.game.save.CharacterLook
import com.cyberoperative.game.save.CharacterSave
import com.cyberoperative.game.save.PlayerProfile

/**
 * Named characters (owner, 2026-10-13): each has its own class, look, OP level / Overclock,
 * Overclock board, gear, backpack, Data Vault and saved run. The active one lives in the profile's
 * top-level fields so the rest of the game reads it as before; switching swaps it with a stored one.
 * Everything account-wide (◇, skins, records, settings) is shared.
 */
object Characters {
    const val MAX_CHARACTERS = 8
    const val NAME_MAX = 16

    fun hasCharacter(p: PlayerProfile): Boolean = p.characterId.isNotEmpty()

    /** Letters, digits, spaces, '-' and '_'; 2–16 characters. Null if it can't be a name. */
    fun cleanName(raw: String): String? {
        val n = raw.filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }.trim().replace(Regex(" +"), " ").take(NAME_MAX)
        return n.takeIf { it.length >= 2 }
    }

    /** The active character as a stored record. The Data Vault is shared (0.34.0), so it stays on the profile. */
    fun active(p: PlayerProfile): CharacterSave = CharacterSave(
        id = p.characterId, name = p.characterName, classId = p.selectedOperative, look = p.look,
        operativeXp = p.operativeXp, permanentUpgrades = p.permanentUpgrades,
        equipped = p.equipped, backpack = p.backpack, savedRun = p.savedRun,
        lastDifficulty = p.lastDifficulty
    )

    /** Every character, the active one first. */
    fun all(p: PlayerProfile): List<CharacterSave> = (if (hasCharacter(p)) listOf(active(p)) else emptyList()) + p.otherCharacters

    private fun load(p: PlayerProfile, c: CharacterSave): PlayerProfile = p.copy(
        characterId = c.id, characterName = c.name, look = c.look,
        selectedOperative = Operatives.byId(c.classId).id, operativeBody = Operatives.byId(c.classId).bodyId,
        operativeXp = c.operativeXp, permanentUpgrades = c.permanentUpgrades,
        equipped = c.equipped, backpack = c.backpack, savedRun = c.savedRun,
        lastDifficulty = c.lastDifficulty
    )

    /**
     * Makes a new character and switches to it. The very first character takes over the progress
     * the profile already has (players from before characters keep their level and Overclock).
     */
    fun create(p: PlayerProfile, name: String, classId: String, look: CharacterLook): PlayerProfile {
        val clean = cleanName(name) ?: "Operative"
        val id = "c" + java.lang.Long.toString(System.currentTimeMillis(), 36) + (p.otherCharacters.size)
        if (!hasCharacter(p)) {
            return p.copy(characterId = id, characterName = clean, look = look,
                selectedOperative = Operatives.byId(classId).id, operativeBody = Operatives.byId(classId).bodyId)
        }
        if (all(p).size >= MAX_CHARACTERS) return p
        val stored = p.copy(otherCharacters = listOf(active(p)) + p.otherCharacters)
        return load(stored, CharacterSave(id = id, name = clean, classId = classId, look = look))
    }

    fun switchTo(p: PlayerProfile, id: String): PlayerProfile {
        if (id == p.characterId) return p
        val target = p.otherCharacters.firstOrNull { it.id == id } ?: return p
        val rest = p.otherCharacters.filter { it.id != id }
        val keep = if (hasCharacter(p)) listOf(active(p)) else emptyList()
        return load(p.copy(otherCharacters = keep + rest), target)
    }

    /** Deletes a stored (not the active) character and everything it owns. */
    fun delete(p: PlayerProfile, id: String): PlayerProfile =
        if (id == p.characterId) p else p.copy(otherCharacters = p.otherCharacters.filter { it.id != id })

    fun rename(p: PlayerProfile, name: String): PlayerProfile = cleanName(name)?.let { p.copy(characterName = it) } ?: p

    fun setLook(p: PlayerProfile, look: CharacterLook): PlayerProfile = p.copy(look = look)

    /** Co-op name: the character's name (works on every platform and offline), else the callsign. */
    fun coopName(p: PlayerProfile, fallback: String): String = p.characterName.ifBlank { fallback }
}

/** Moving gear between the gear slots, the backpack and the Data Vault (owner's mock-ups). */
object GearBank {
    /** Result of banking finds: how many went to the backpack, the vault, and how many were lost (both full). */
    data class Banked(val backpack: Int, val vault: Int, val lost: Int)

    /** Adds run finds with fresh ids: the backpack first, then the vault. */
    fun bank(p: PlayerProfile, items: List<GearItem>): Pair<PlayerProfile, Banked> {
        if (items.isEmpty()) return p to Banked(0, 0, 0)
        var seq = p.gearSeq
        val bp = p.backpack.toMutableList()
        val v = p.vault.toMutableList()
        var toBp = 0; var toV = 0; var lost = 0
        for (it in items) {
            val item = it.copy(uid = seq++, fresh = true, locked = false)
            when {
                bp.size < Gear.BACKPACK_SIZE -> { bp += item; toBp++ }
                v.size < Gear.VAULT_SIZE -> { v += item; toV++ }
                else -> lost++
            }
        }
        return p.copy(backpack = bp, vault = v, gearSeq = seq) to Banked(toBp, toV, lost)
    }

    /**
     * AUTO-SELL (owner, 0.46.0): splits run / idle finds into what is kept and the € the rest sold
     * for (each at [Gear.sellPrice]). Everything below [below] goes; null sells nothing.
     * Gear only: eggs (and pets) never pass through here, so AUTO-SELL can't sell them (owner, 0.53.4).
     */
    fun autoSell(items: List<GearItem>, below: com.cyberoperative.game.data.GearRarity?): Pair<List<GearItem>, Int> {
        if (below == null) return items to 0
        val (sold, kept) = items.partition { it.rarity < below && !it.locked }
        return kept to sold.sumOf { Gear.sellPrice(it) }
    }

    fun find(p: PlayerProfile, uid: Long): GearItem? =
        p.equipped.values.firstOrNull { it.uid == uid } ?: p.backpack.firstOrNull { it.uid == uid } ?: p.vault.firstOrNull { it.uid == uid }

    fun isEquipped(p: PlayerProfile, uid: Long): Boolean = p.equipped.values.any { it.uid == uid }
    fun inVault(p: PlayerProfile, uid: Long): Boolean = p.vault.any { it.uid == uid }

    /**
     * Equips [uid] from the backpack or vault. What it replaces takes its place (same container), so
     * equipping never fails for lack of room. Class weapons only go on their class.
     */
    fun equip(p: PlayerProfile, uid: Long): PlayerProfile {
        val item = p.backpack.firstOrNull { it.uid == uid } ?: p.vault.firstOrNull { it.uid == uid } ?: return p
        if (!item.usableBy(p.selectedOperative)) return p
        val old = p.equipped[item.slot]
        val fromVault = p.vault.any { it.uid == uid }
        fun swap(list: List<GearItem>): List<GearItem> =
            if (old == null) list.filter { it.uid != uid } else list.map { if (it.uid == uid) old else it }
        return p.copy(
            equipped = p.equipped + (item.slot to item.copy(fresh = false)),
            backpack = if (fromVault) p.backpack else swap(p.backpack),
            vault = if (fromVault) swap(p.vault) else p.vault
        )
    }

    /** Takes a slot's item off into the backpack (or the vault if the backpack is full). */
    fun unequip(p: PlayerProfile, slot: GearSlot): PlayerProfile {
        val item = p.equipped[slot] ?: return p
        return when {
            p.backpack.size < Gear.BACKPACK_SIZE -> p.copy(equipped = p.equipped - slot, backpack = p.backpack + item)
            p.vault.size < Gear.VAULT_SIZE -> p.copy(equipped = p.equipped - slot, vault = p.vault + item)
            else -> p
        }
    }

    fun toVault(p: PlayerProfile, uid: Long): PlayerProfile {
        val item = p.backpack.firstOrNull { it.uid == uid } ?: return p
        if (p.vault.size >= Gear.VAULT_SIZE) return p
        return p.copy(backpack = p.backpack.filter { it.uid != uid }, vault = p.vault + item)
    }

    fun toBackpack(p: PlayerProfile, uid: Long): PlayerProfile {
        val item = p.vault.firstOrNull { it.uid == uid } ?: return p
        if (p.backpack.size >= Gear.BACKPACK_SIZE) return p
        return p.copy(vault = p.vault.filter { it.uid != uid }, backpack = p.backpack + item)
    }

    /** DEPOSIT ALL: every unlocked backpack item into the vault, as far as it has room. */
    fun depositAll(p: PlayerProfile): PlayerProfile {
        val room = Gear.VAULT_SIZE - p.vault.size
        if (room <= 0) return p
        val moving = p.backpack.filter { !it.locked }.take(room)
        val ids = moving.map { it.uid }.toSet()
        return p.copy(backpack = p.backpack.filter { it.uid !in ids }, vault = p.vault + moving)
    }

    /** Destroys an item (never a locked or equipped one). */
    /** Sells an unlocked backpack piece; [bank] adds the € to the profile (false: the run wallet takes it). */
    fun sell(p: PlayerProfile, uid: Long, bank: Boolean = true): Pair<PlayerProfile, Int> {
        val item = p.backpack.firstOrNull { it.uid == uid && !it.locked } ?: return p to 0
        val price = Gear.sellPrice(item)
        return p.copy(backpack = p.backpack.filter { it.uid != uid }, euros = if (bank) p.euros + price else p.euros) to price
    }

    fun discard(p: PlayerProfile, uid: Long): PlayerProfile {
        val item = find(p, uid) ?: return p
        if (item.locked || isEquipped(p, uid)) return p
        return p.copy(backpack = p.backpack.filter { it.uid != uid }, vault = p.vault.filter { it.uid != uid })
    }

    private fun mapItem(p: PlayerProfile, uid: Long, f: (GearItem) -> GearItem): PlayerProfile = p.copy(
        equipped = p.equipped.mapValues { (_, v) -> if (v.uid == uid) f(v) else v },
        backpack = p.backpack.map { if (it.uid == uid) f(it) else it },
        vault = p.vault.map { if (it.uid == uid) f(it) else it }
    )

    fun toggleLock(p: PlayerProfile, uid: Long): PlayerProfile = mapItem(p, uid) { it.copy(locked = !it.locked) }

    fun markSeen(p: PlayerProfile, uid: Long): PlayerProfile =
        if (find(p, uid)?.fresh == true) mapItem(p, uid) { it.copy(fresh = false) } else p

    /** Stats line for an item against what's equipped in its slot ("+35 vs equipped"). */
    fun versus(p: PlayerProfile, item: GearItem): Float? {
        val cur = p.equipped[item.slot] ?: return null
        if (cur.uid == item.uid) return null
        return item.effectiveBase - cur.effectiveBase
    }
}
