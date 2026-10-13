package com.cyberoperative.game.meta

import com.cyberoperative.game.data.LivingBackground
import com.cyberoperative.game.data.OperativeSkins
import com.cyberoperative.game.data.RevivePack
import com.cyberoperative.game.data.StoreCatalog
import com.cyberoperative.game.save.PlayerProfile

/**
 * ◇ spending rules as pure functions over the profile, so every price and
 * ownership rule is unit tested. Each returns the new profile, or null if the
 * purchase is not allowed (already owned / not enough ◇).
 */
object StoreManager {

    /**
     * One-time profile fixes on load. 0.17.0: the NEON OPERATIVE became the free TECHNICIAN class,
     * so anyone who bought it gets the ◇ back; the old free body picks map onto their classes.
     */
    fun migrate(p: PlayerProfile): PlayerProfile {
        var out = p
        if (!out.neonRefunded) {
            if (NEON_OPERATIVE_ID in out.ownedOperatives && !allSkinsBundleBought(out)) out = out.copy(diamonds = out.diamonds + StoreCatalog.NEON_OPERATIVE_PRICE)
            out = out.copy(neonRefunded = true)
        }
        // LEVEL-UP REROLLS retired with the card screen (0.24.1): remove it and refund its €.
        val rerolls = out.permanentUpgrades[com.cyberoperative.game.data.PermanentUpgrades.RETIRED_REROLLS.id] ?: 0
        if (rerolls > 0 || com.cyberoperative.game.data.PermanentUpgrades.RETIRED_REROLLS.id in out.permanentUpgrades) {
            val refund = com.cyberoperative.game.data.PermanentUpgrades.spentOn(com.cyberoperative.game.data.PermanentUpgrades.RETIRED_REROLLS, rerolls)
            out = out.copy(
                euros = out.euros + refund,
                permanentUpgrades = out.permanentUpgrades - com.cyberoperative.game.data.PermanentUpgrades.RETIRED_REROLLS.id
            )
        }
        // Data Vault update (owner, 2026-10-13): € no longer buys upgrades. Everyone's € and the
        // upgrades bought with it are wiped once; the board now runs on Overclock points.
        if (!out.dataVaultWipe) out = out.copy(euros = 0, permanentUpgrades = emptyMap(), dataVaultWipe = true)
        // 0.30.2 (owner): every level needs ×10 the XP. Scale what's banked once so each character
        // keeps the level (and Overclock) it had already earned.
        if (!out.xpScaled) out = out.copy(
            operativeXp = out.operativeXp * com.cyberoperative.game.data.OpProgress.LEVEL_XP_MUL,
            otherCharacters = out.otherCharacters.map { it.copy(operativeXp = it.operativeXp * com.cyberoperative.game.data.OpProgress.LEVEL_XP_MUL) },
            xpScaled = true
        )
        // The body always follows the class.
        val body = com.cyberoperative.game.data.Operatives.byId(out.selectedOperative).bodyId
        if (out.operativeBody != body) out = out.copy(operativeBody = body)
        return out
    }

    /** The ◇300 bundle included the NEON OPERATIVE: owning every paid skin means it came from there. */
    private fun allSkinsBundleBought(p: PlayerProfile): Boolean = OperativeSkins.paid.all { it.id in p.ownedSkins }

    /** Pick a class (0.17.0): every class is free; the body follows it. */
    fun selectClass(p: PlayerProfile, classId: String): PlayerProfile {
        val c = com.cyberoperative.game.data.Operatives.byId(classId)
        return p.copy(selectedOperative = c.id, operativeBody = c.bodyId)
    }

    /** Is [skin] usable by class [classId]: free, bought, or (class skins) earned with that class. */
    fun skinUnlocked(p: PlayerProfile, skin: com.cyberoperative.game.data.OperativeSkin, classId: String): Boolean {
        val u = skin.unlock ?: return skin.free || skin.id in p.ownedSkins
        if (u.campaign && p.campaignsCompleted > 0) return true
        return u.level > 0 && (p.classRecords[classId]?.best(u.difficulty) ?: 0) >= u.level
    }

    /** The skin a class actually wears: the equipped one, or the default if this class hasn't earned it. */
    fun effectiveSkin(p: PlayerProfile, classId: String = p.selectedOperative): com.cyberoperative.game.data.OperativeSkin {
        val s = OperativeSkins.byId(p.selectedSkin)
        if (!skinUnlocked(p, s, classId)) return OperativeSkins.DEFAULT
        return if (s.id == OperativeSkins.NEON_ID) OperativeSkins.neon(p.neonColor) else s
    }

    fun buySkin(p: PlayerProfile, skinId: String): PlayerProfile? {
        val skin = OperativeSkins.all.firstOrNull { it.id == skinId } ?: return null
        if (skin.free || skin.unlock != null || skinId in p.ownedSkins) return null
        if (p.diamonds < skin.price) return null
        return p.copy(diamonds = p.diamonds - skin.price, ownedSkins = p.ownedSkins + skinId, selectedSkin = skinId)
    }

    /** The ◇300 skin pack covers every paid colour skin (class skins are earned, not sold). */
    fun allSkinsOwned(p: PlayerProfile): Boolean = OperativeSkins.paid.all { it.id in p.ownedSkins }

    fun buyAllSkins(p: PlayerProfile): PlayerProfile? {
        if (allSkinsOwned(p) || p.diamonds < StoreCatalog.ALL_SKINS_PRICE) return null
        return p.copy(
            diamonds = p.diamonds - StoreCatalog.ALL_SKINS_PRICE,
            ownedSkins = p.ownedSkins + OperativeSkins.paid.map { it.id }
        )
    }

    fun buyBackground(p: PlayerProfile, id: String): PlayerProfile? {
        val bg = LivingBackground.byId(id)
        if (bg == LivingBackground.NONE || id in p.ownedBackgrounds) return null
        if (p.diamonds < StoreCatalog.BACKGROUND_PRICE) return null
        return p.copy(diamonds = p.diamonds - StoreCatalog.BACKGROUND_PRICE, ownedBackgrounds = p.ownedBackgrounds + id, selectedBackground = id)
    }

    fun allBackgroundsOwned(p: PlayerProfile): Boolean = LivingBackground.purchasable.all { it.id in p.ownedBackgrounds }

    fun buyAllBackgrounds(p: PlayerProfile): PlayerProfile? {
        if (allBackgroundsOwned(p) || p.diamonds < StoreCatalog.ALL_BACKGROUNDS_PRICE) return null
        return p.copy(
            diamonds = p.diamonds - StoreCatalog.ALL_BACKGROUNDS_PRICE,
            ownedBackgrounds = p.ownedBackgrounds + LivingBackground.entries.map { it.id }
        )
    }

    fun buyRevives(p: PlayerProfile, pack: RevivePack): PlayerProfile? {
        if (p.diamonds < pack.priceDiamonds) return null
        return p.copy(diamonds = p.diamonds - pack.priceDiamonds, reviveTokens = p.reviveTokens + pack.revives)
    }

    fun useReviveToken(p: PlayerProfile): PlayerProfile? =
        if (p.reviveTokens <= 0) null else p.copy(reviveTokens = p.reviveTokens - 1)

    fun equipSkin(p: PlayerProfile, id: String): PlayerProfile? =
        if (skinUnlocked(p, OperativeSkins.byId(id), p.selectedOperative) && OperativeSkins.byId(id).id == id) p.copy(selectedSkin = id) else null

    /** NEON skin colour (owners only); stored as an opaque ARGB colour. */
    fun setNeonColor(p: PlayerProfile, color: Long): PlayerProfile? =
        if (OperativeSkins.NEON_ID in p.ownedSkins) p.copy(neonColor = color or 0xFF000000) else null

    fun equipBackground(p: PlayerProfile, id: String): PlayerProfile? =
        if (id == LivingBackground.NONE.id || id in p.ownedBackgrounds) p.copy(selectedBackground = id) else null

    /** NEON OPERATIVE: was a premium body; since 0.17.0 the TECHNICIAN class (refunded by [migrate]). */
    const val NEON_OPERATIVE_ID = "neon_operative"

    /** Credit a verified ◇ purchase (from Google Play, or a debug test grant). */
    fun grantDiamonds(p: PlayerProfile, amount: Int): PlayerProfile = p.copy(diamonds = p.diamonds + amount)
}
