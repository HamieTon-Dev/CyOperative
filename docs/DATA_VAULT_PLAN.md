# Data Vault update — levels, Overclock, gear, loot (owner, 2026-10-13)

Reason: "the game is too easy again". The game moves from run-only power (chips, cards, the €
upgrades screen) to a slow Diablo-style loot-and-level grind.

## Owner decisions (2026-10-13)

| Question | Decision |
|---|---|
| Armor | **Gear armor with the Diablo 3 formula.** The old flat % armor is removed. DR = Armor / (Armor + 50 × monster level). |
| Firewall | **Removed; no shield.** HP and potions (HP charges) only. |
| € UPGRADES screen | **Becomes the OVERCLOCK board** (the Paragon equivalent). It is bought with Overclock points after OP Lv 60. € is wiped and is then only spent in the in-run shop. |
| Run chips / cards | **Chips become loot.** They are permanent items equipped into chip slots: 5 at OP Lv 1, rising to 15 at OP Lv 60. |

## Design

**OP Level 1–60.** XP comes from all activities, mostly kills; elites and bosses give more. Since 0.30.2 every level (and Overclock) needs ×10 the XP below (owner).
- XP to the next level:
  - L 1–49: ⌊(L+9)² × 25⌋ (Diablo IV).
  - L 50–59: flat steps.
- Each level raises base HP and damage a little. Gear is what really makes you stronger.

**OVERCLOCK 1–300** (Paragon). All XP after OP Lv 60 goes here.
- The climb is slow at first. Each level costs about 3% more from Overclock 200, and the last level costs about 40× the whole 1–60 climb.
- Each Overclock level gives 1 point to spend on the Overclock board (the old permanent upgrades).

**Monster level (ML).** ML = max(OP level, the world's depth). Enemies' HP and damage scale with ML, and kill XP grows with ML.

**Armor.** Gear rolls an armor value. DR = A / (A + 50 × ML): armor equal to 50×ML gives 50%, double that gives 66.7%.

**Gear slots:** Primary, Secondary, Helmet, Chest, Gloves, Boots, Implant, Core, plus 5–15 chip slots.
- **Rarity ladder:** Common < Uncommon < Blue < Purple < Golden < **Encrypted (orange)** < **Crypto (neon blue)** < Titanium (and higher later).
- Each step up is much rarer, and the top approaches 0%.
- **Drops:** gear drops from **elites and bosses**. Bosses after World 12 (or Endless level 120) get a slightly higher chance on every loot table.
- A **sheen sound** plays for drops better than Blue.
- **Storage:** Backpack (60) and Data Vault (1,500), managed on the Equipment and Data Vault screens (owner's mock-ups), with drag-to-equip.
- **Weapons:** the equipped primary weapon decides the attack. Rarity tints the operative's weapon (for example a Golden staff turns gold).

## Phases

1. **Levels and difficulty:**
   - OP Lv and Overclock XP curves, with OP Lv shown on the HUD by the HP bar.
   - € and upgrades wipe, and the Overclock board bought with points.
   - Monster level scaling and the armor formula.
   - Firewall removed.
2. ✅ (0.30.0) **Loot core:** the item model (slots, rarities, stats, item level), drops from elites and bosses with the late-world boost, the sheen sound, and saving the backpack and vault.
3. ✅ (0.30.0) **Equipment and Data Vault screens:** drag-to-equip, stats from equipped gear, and compare.
4. **Chips as loot:**
   - Chip slots 5→15, and the in-run chip/card system retired.
   - Weapons come from gear, rarity tints the weapon, and each weapon keeps its own attack.
5. **Balance pass and tests** for the slow grind.

## Shipped in 0.30.0
- **Rarity ladder:** COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, then ENCRYPTED (orange, Inferno I+), INFERNAL (red, Inferno IV+) and CRYPTO (neon blue, Inferno VII+, close to 0%). The last three reuse the golden art, recoloured and renamed.
- **Drops:**
  - Elites drop gear 25% of the time (+2% per Inferno tier).
  - A world boss drops 3 pieces per player (+1 every 3 Inferno tiers).
  - Late bosses (after World 12 / Endless 120) get better odds.
  - Item level is OP level ±2, never above 60.
- **Weapons:** rifles have 8 attack styles plus 5 class-only weapons; pistols carry the existing arsenal weapons.
- **Characters:** a creator, up to 8 named characters, each with its own progress and storage. Co-op shows the character name.
