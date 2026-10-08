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

    fun buySkin(p: PlayerProfile, skinId: String): PlayerProfile? {
        val skin = OperativeSkins.all.firstOrNull { it.id == skinId } ?: return null
        if (skin.free || skinId in p.ownedSkins) return null
        if (p.diamonds < StoreCatalog.SKIN_PRICE) return null
        return p.copy(diamonds = p.diamonds - StoreCatalog.SKIN_PRICE, ownedSkins = p.ownedSkins + skinId, selectedSkin = skinId)
    }

    /** The ◇300 skin pack covers every colour skin and the NEON OPERATIVE (owner, 2026-10-08). */
    fun allSkinsOwned(p: PlayerProfile): Boolean =
        OperativeSkins.paid.all { it.id in p.ownedSkins } && NEON_OPERATIVE_ID in p.ownedOperatives

    fun buyAllSkins(p: PlayerProfile): PlayerProfile? {
        if (allSkinsOwned(p) || p.diamonds < StoreCatalog.ALL_SKINS_PRICE) return null
        return p.copy(
            diamonds = p.diamonds - StoreCatalog.ALL_SKINS_PRICE,
            ownedSkins = p.ownedSkins + OperativeSkins.all.map { it.id },
            ownedOperatives = p.ownedOperatives + NEON_OPERATIVE_ID
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
        if (id == OperativeSkins.DEFAULT.id || id in p.ownedSkins) p.copy(selectedSkin = id) else null

    fun equipBackground(p: PlayerProfile, id: String): PlayerProfile? =
        if (id == LivingBackground.NONE.id || id in p.ownedBackgrounds) p.copy(selectedBackground = id) else null

    /** NEON OPERATIVE: a premium full-body operative (body id `neon_operative`). */
    const val NEON_OPERATIVE_ID = "neon_operative"

    fun buyNeonOperative(p: PlayerProfile): PlayerProfile? {
        if (NEON_OPERATIVE_ID in p.ownedOperatives || p.diamonds < StoreCatalog.NEON_OPERATIVE_PRICE) return null
        return p.copy(
            diamonds = p.diamonds - StoreCatalog.NEON_OPERATIVE_PRICE,
            ownedOperatives = p.ownedOperatives + NEON_OPERATIVE_ID,
            operativeBody = NEON_OPERATIVE_ID
        )
    }

    /** Select a body design; premium ones must be owned. */
    fun equipBody(p: PlayerProfile, bodyId: String, premium: Boolean): PlayerProfile? =
        if (!premium || bodyId in p.ownedOperatives) p.copy(operativeBody = bodyId) else null

    /** Credit a verified ◇ purchase (from Google Play, or a debug test grant). */
    fun grantDiamonds(p: PlayerProfile, amount: Int): PlayerProfile = p.copy(diamonds = p.diamonds + amount)
}
