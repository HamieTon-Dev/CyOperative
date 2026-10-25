package com.cyberoperative.game.data

import com.cyberoperative.game.i18n.tr

/**
 * PC class spells (owner, 2026-10-11): every class has two, cast with R and F (rebindable).
 * Damage scales with the operative's own damage, so spells keep pace with the build.
 */
enum class Spell(val label: String, val icon: String, val cooldown: Float, val text: String) {
    OVERCLOCK(tr("OVERCLOCK"), "»", 18f, tr("+60% fire rate for 5s")),
    FRAG_GRENADE(tr("FRAG GRENADE"), "✸", 9f, tr("A blast at the cursor")),
    MISSILE_BARRAGE(tr("MISSILE BARRAGE"), "➹", 12f, tr("8 homing missiles")),
    EMP_FIELD(tr("EMP FIELD"), "ϟ", 16f, tr("A big pulse that wipes enemy shots")),
    FORTIFY(tr("FORTIFY"), "▣", 20f, tr("−60% damage taken for 4s, firewall refilled")),
    GROUND_SLAM(tr("GROUND SLAM"), "◎", 11f, tr("A shockwave that knocks threats back")),
    SHADOW_STEP(tr("SHADOW STEP"), "↯", 7f, tr("Blink toward the cursor")),
    SMOKE_VEIL(tr("SMOKE VEIL"), "◌", 22f, tr("Cloak right now, for 4s")),
    FIRESTORM(tr("FIRESTORM"), "☄", 14f, tr("Fire rains on the cursor area")),
    ARCANE_NOVA(tr("ARCANE NOVA"), "✺", 12f, tr("A ring of homing fire bolts"))
}

object Spells {
    /** (R, F) for a class id. */
    fun forClass(classId: String): Pair<Spell, Spell> = when (classId) {
        Operatives.TECHNICIAN.id -> Spell.MISSILE_BARRAGE to Spell.EMP_FIELD
        Operatives.JUGGERNAUT.id -> Spell.FORTIFY to Spell.GROUND_SLAM
        Operatives.PHANTOM.id -> Spell.SHADOW_STEP to Spell.SMOKE_VEIL
        Operatives.PACKET_MAGE.id -> Spell.FIRESTORM to Spell.ARCANE_NOVA
        else -> Spell.OVERCLOCK to Spell.FRAG_GRENADE
    }

    fun of(classId: String, slot: Int): Spell = forClass(classId).let { if (slot == 0) it.first else it.second }
}
