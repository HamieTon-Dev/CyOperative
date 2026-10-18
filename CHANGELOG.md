# Changelog

## 0.54.1 (versionCode 153) — 2026-10-18 · ARCADE tracks compressed like the rest
### Changed (owner)
- The 10 ARCADE tracks are re-encoded with the **same settings as the rest of the soundtrack**: Ogg Vorbis, 96 kbps, 44.1 kHz stereo (they were about 112 kbps). All ten now take 22 MB (was 27 MB).
- **Cover art removed:** each MP3 carried an embedded picture. The game files hold only the audio stream, with no picture and no tags.
- **Checked for Android:**
  - every track maps to its `R.raw` resource (`Platform.rawMusic`, `MusicFilesTest`);
  - all ten are in the release bundle at full size, so resource shrinking leaves them alone;
  - each one decodes cleanly from start to finish.

## 0.54.0 (versionCode 152) — 2026-10-18 · 10 ARCADE tracks
### Added (owner)
- **10 new tracks**, at the **top of the playlist** above the Main Menu tracks: New Game, Let me Solo, Wanna Trade?, Wanna Team Up?, Trade Request, Save and Quit?, Lost my GBA, Big Data Energy, Big Data Energy (2) and Memory Card.
  - They play **everywhere**: the main menu, every level and **boss fights**, on **every difficulty** (Inferno included).
  - **Shuffle** picks them like any other track, in every rotation, every boss's included.
  - They are **never sped up or slowed** (no tempo or pitch change). The **only effect is reverb**: in a boss fight they take the boss's reverb, but not its EQ.
  - Stored as Ogg Vorbis (about 27 MB for all ten). They ship on Android and PC.
- Tests: `V054ArcadeMusicTest`, plus updated `MusicTest` counts.

## 0.53.4 (versionCode 151) — 2026-10-18 · Auto-sell never sells eggs
### Checked (owner)
- **AUTO-SELL never touches eggs**, in matches or Idle Runs, whatever rarity it is set to. It only ever receives gear: eggs are kept in their own list and are banked separately (`bankPetFinds` in matches, `Incubators.addEggs` for Idle Runs).
- New test: `V053AutoSellEggsTest` collects an Idle Run with one egg of every rarity, with auto-sell set to the highest rarity, and every egg is kept.

## 0.53.3 (versionCode 150) — 2026-10-18 · Bigger egg on the shop counter
### Changed (owner)
- The egg on the shop counter is slightly bigger than the gear beside it: 68 px, against 58 px for the gear.

## 0.53.2 (versionCode 148) — 2026-10-18 · Bigger shop counter showing the gear and egg for sale
### Changed (owner)
- **The counter shows the gear for sale instead of the chips**, laid out **[ : egg : ]**:
  - two weapons / armor pieces (back and front) on each side, with the egg (if any) **in the middle**;
  - every item is **drop-sized** (58 px, like gear in the world), including the egg;
  - each item stands over a low glow in its rarity's colour and animation, and no two sprites touch;
  - sold items show SOLD;
  - prices use short numbers (€1.5k, €6k): back-row prices on the table, front-row and egg prices on the counter front;
  - each co-op player still sees their own stock.
- **Bigger, squarer counter:** 340 × 170 (was 320 × 50). It leaves a wide walk round both ends on every screen, about 190 px each side in portrait.
- **Fewer barriers, pushed to the walls:** 1–2 fixtures (was 3–5), at most 60 wide and flush against the side walls.
- **A clear zone barriers can never enter:** the counter plus 120 on each side, from the top gate down to 320 below the counter. `V053ShopCounterTest` checks 300 rooms each in phone and PC widths.
- The room layout changed, so the co-op protocol is now v37 and both players need this update.

## 0.53.1 (versionCode 147) — 2026-10-18 · The shop egg sits on the counter
### Added (owner)
- **The egg for sale is on the shopkeeper's counter, front and centre**, drawn with its own egg sprite.
  - Behind it, on the table, is a small version of its rarity's loot glow, with that rarity's animation (Forged sparks, Voltaic arcs, the GLITCHED ENCRYPTED cyan↔red tear, and so on). The glow is kept low, so it never covers the shopkeeper.
  - The egg is 38 px, smaller than a gear drop in the world (58 px), and bobs gently.
  - No egg for sale means no egg on the counter. It disappears once bought.
  - **Co-op:** each player sees **their own** egg. The host sees the host's egg and the guest sees the guest's.
- Render: `docs/screenshots/shop_counter_egg.png`.

## 0.53.0 (versionCode 146) — 2026-10-18 · Pet eggs in the in-run shop
### Added (owner)
- **The in-run shop can sell 1 PET EGG**, shown in its own row under EQUIPMENT with the animated egg, its rarity and name.
  - **Price = 2× the egg's sell value** (2 × 10 × a same-rarity weapon's sell price at your OP level), paid in €.
  - **Normal levels:** about **30%** of shops offer one, of any rarity. Higher rarities are rarer, using the same odds as boss gear.
  - **INFERNO levels:** **every shop** offers one, **GLITCHED LEGENDARY or higher**. Each step up is rarer (35 / 22 / 16 / 12 / 8 / 5 / 2, the daily-egg weights).
  - A bought egg goes to your PETS (`eggsFound`, the 1,000-egg cap), so you can hatch it mid-match from INVENTORY › PETS.
- **Co-op:** each player gets their own shop egg. The guest's purchase goes through the host. Co-op protocol v36, so both players need this update.
- Render: `docs/screenshots/shop_window_egg.png`.

## 0.52.0 (versionCode 145) — 2026-10-18 · ◇5,000 pack, compact incubators, selling / vaulting / trading pets and eggs
### Changed (owner)
- **Store:** the ◇10,000 / $69.99 pack is replaced by **◇5,000 for $29.99**.
  - **Owner action:** create the product **`diamonds_5000`** in Play Console, and upload `steam/itemdefs.json` to Steamworks (new item 1005; 1004 is now hidden).
  - Any earlier 10,000 purchase still credits.
- **Mid-match PETS tab:** **compact incubators**, three small tiles showing the egg, countdown and progress bar. Pick an egg below and SEND TO INCUBATOR to start a hatch during the match.
- **Eggs held: 1,000** (was 100). The list shows 120 at a time, with SHOW MORE.
### Added (owner)
- **SELL replaces RELEASE** for pets, and eggs can be sold too.
  - **Value = 10× the sell price of a same-rarity weapon** at your OP level. The button shows the € and asks to confirm.
  - In a solo match the € go to the run's wallet, like gear; otherwise they go to your saved €.
- **DATA VAULT holds pets and eggs:**
  - TO VAULT from CHARACTER › PETS (an equipped pet is unequipped first);
  - DATA VAULT has GEAR / PETS / EGGS sections, with TAKE OUT;
  - pets and eggs share 1,500 vault slots.
- **Co-op TRADE carries eggs and pets** (up to 8 of each) next to gear, € and potions. Received pets join your PET CODEX. Co-op protocol v35, so both players need this update.
- Renders: `docs/screenshots/v051_in_run_pets.png` (compact incubators) and `v052_vault_pets.png`.

## 0.51.0 (versionCode 144) — 2026-10-18 · Change pets mid-match; cheaper store eggs
### Added (owner)
- **Change your pet in a match**, solo and co-op:
  - INVENTORY (■INV) → **PETS** tab. There's no incubator panel there, just one line on the incubators plus your pets and eggs.
  - EQUIP / UNEQUIP swaps the companion **at once**: it appears beside you with fresh ability cooldowns.
  - In co-op, the guest's choice goes to the host, and both screens show each other's new pet. This is co-op protocol v34, so both players need this update.
- **Start hatching eggs mid-match:** tap an egg in that tab to put it in a free incubator. It hatches in real time while you play, and you can equip the new pet from the same tab.
### Changed (owner)
- **Store egg prices:**

  | Rarity | Price |
  |---|---|
  | GLITCHED LEGENDARY | ◇100 |
  | FORGED | ◇150 |
  | VOLTAIC | ◇200 |
  | CELESTIAL | ◇250 |
  | ABYSSAL | ◇300 |
  | ENCRYPTED | ◇350 |
  | GLITCHED ENCRYPTED | ◇400 |

- Render: `docs/screenshots/v051_in_run_pets.png`.

## 0.50.2 (versionCode 143) — 2026-10-18 · Fewer elite potions, smaller player shots
### Changed (owner)
- **HP potions drop from elites half as often:** 33% → 16.5% in runs, and 8% → 4% in Idle Runs. Boss potions are unchanged.
- **The player's shots are drawn at half size:**
  - this covers bolts, lances, cones, node bolts, ricochet discs, railgun streaks and missiles;
  - **they hit exactly as before**, because only the drawing shrank and the hit radius is untouched;
  - enemy shots are unchanged.
  - Comparison: `docs/screenshots/player_shots_half_size.png`.

## 0.50.1 (versionCode 142) — 2026-10-18 · Store eggs: GLITCHED LEGENDARY and up, priced by rarity
### Changed (owner: "the store sells nothing less than a glitched legendary rarity egg")
- **DAILY EGGS** only roll GLITCHED LEGENDARY or better, priced by rarity:

  | Rarity | Price |
  |---|---|
  | GLITCHED LEGENDARY | ◇300 |
  | FORGED | ◇400 |
  | VOLTAIC | ◇500 |
  | CELESTIAL | ◇600 |
  | ABYSSAL | ◇700 |
  | ENCRYPTED | ◇800 |
  | GLITCHED ENCRYPTED | ◇900 |

  - Default odds, in the same order: 35 / 22 / 16 / 12 / 8 / 5 / 2 % (`EggOffers.weights`).
  - The three offers still rotate every 24 h, or all at once when the last one is bought.
  - Offers rolled under 0.50.0 that were below GLITCHED roll again.
- The old MYSTERY / EPIC / LEGENDARY egg packs are no longer sold.

## 0.50.0 (versionCode 141) — 2026-10-18 · Daily eggs, live store previews, PET CODEX
### Added (owner)
- **DAILY EGGS** (top of the STORE): three separate eggs, each with its own card and BUY button, at **◇1,500** each.
  - Rarities and egg kinds are random. Default weights, from Common to Glitched Encrypted: 6 / 8 / 12 / 17 / 20 / 13 / 8 / 6 / 4.5 / 3 / 2 / 0.5 %. Tune them in `EggOffers.weights`.
  - All three change every 24 h; the header counts down.
  - Buying the third one rolls three new eggs at once (and restarts the 24 h).
  - What you see is exactly the egg you get. The pet inside stays a surprise.
- **PET CODEX** (main menu, next to BOSS CODEX):
  - Shows all 2,355 pets, grouped by rarity, with 'x / y discovered' per group.
  - Hatched pets show and move; the rest are dark silhouettes.
  - Tap one for its page: the pet animated, its name and rarity (or COMMON – LEGENDARY for the originals), and its potential **damage, fire rate, abilities and hatch time**.
  - The page also shows **its egg**, animated. Designed pets show their own egg; originals show the egg yours came from.
  - Every hatch is recorded, so pets you release stay in the codex. Pets you already own count as discovered.
### Changed (owner)
- **Skins:** the icon on the left is now **your operator wearing that skin**, walking. All previews share one 20 fps clock, to keep it light.
- **Store eggs move like in the game:** they bob, rock and give an occasional shake, in a small glow of their rarity colour. This covers the daily eggs and the ◇ egg packs, which show an egg of their best rarity.
- Renders: `docs/screenshots/v050_store_top.png`, `v050_store.gif`, `v050_skins.png`, `v050_skin.gif`, `v050_pet_codex.png`, `v050_pet_codex_page(_original).png` and `v050_main_menu.png`.

## 0.49.1 (versionCode 140) — 2026-10-18 · 300 new pets and eggs, eggs from Idle Runs
### Added (owner)
- **300 new pets, each with its own new egg** (`tools/pets/gen_more_pets.py`):
  - **100 GLITCHED LEGENDARY** (now 190): gold, magenta, pink, purple and silver, torn and chroma-split. In the game they flicker into magenta and cyan ghosts.
  - **100 GLITCHED ENCRYPTED** (now 150): cyan and red, glitch-torn, with moving parts that switch between cyan and red and the red/cyan tear.
  - **100 ENCRYPTED** (now 670): animated, in the 14 colourways plus 6 new ones (Teal, Crimson, Lime, Indigo, Rose, Steel).
  - **Never-before-seen designs.** Every one uses at least one part no earlier pet has:
    - new bodies: cube, twin-lobed and pear;
    - new legs: wheels and tentacles;
    - new eyes: X, ring and pixel;
    - new head pieces: fin, mohawk, mini satellite and tech horns.
    Older pets are unchanged.
  - Review sheets: `docs/pets_new/glitched_legendary_new_100.png`, `glitched_encrypted_new_100.png` and `encrypted_new_100.png`, plus `new_300_pets.gif`. In-game: `docs/screenshots/pets_0491_in_game.png`.
- **Eggs drop in Idle Runs:**
  - bosses and elites drop eggs at the normal-run odds (35% / 2%) and with the normal rarity roll;
  - the Idle Run screen lists them (EGGS) and collecting adds them to your PETS;
  - the gear rules for Idle Runs are unchanged.

## 0.49.0 (versionCode 139) — 2026-10-18 · 50 GLITCHED ENCRYPTED pets and eggs
### Added (owner)
- **50 GLITCHED ENCRYPTED pets, each with its own egg** (`tools/pets/gen_gx_pets.py`):
  - Encrypted-style bodies in cyan and red, with torn glitch slices, a red/cyan fringe and scanlines, like the gear.
  - All of them are animated. Their orbs, rings, gears, shards and scanners switch between cyan and red.
  - The new GLITCH effect tears the sprite into red and cyan ghosts every so often, in runs and in menus.
  - Review sheet: `docs/pets_new/glitched_encrypted_pets.png`. Clips: `glitched_encrypted_pets.gif` and the in-game hatch, `glitched_encrypted_hatch.gif`.
- **Stats:**
  - **12 h** hatch time (Encrypted is 10 h);
  - shots deal **180%** of your payload (Encrypted 155%);
  - instant hatch from full is 5,760 ◇ with the approved formula;
  - the hatch glow is the full 2×, in the shifting colour.
- **Drops:** a boss or elite egg can now roll GLITCHED ENCRYPTED, at the same tiny rate as the gear (0.1% of boss egg rarities). The ◇ store eggs are unchanged.
- The pets sit after the existing 1,000 (art 2024–2073), on atlas page 7's free cells and a new page 8. The old pets are pixel-identical.

## 0.48.1 (versionCode 138) — 2026-10-18 · Squish polish: portrait only, opaque buttons, boss HP
### Changed (owner)
- **The compact HUD is portrait only.** Landscape phones are back to the slim strip (with the compact 3-row buffs).
- **Choose your HUD:** Settings → **COMPACT HUD (PORTRAIT)**, on by default. Off brings back the classic top panel.
- **[+] auto-hides:** it folds itself after 4 s untouched. Tapping a buff shows its description and keeps the panel open; once the description closes, the 4 s start again.
- **Opaque buttons:** the joystick, dash, spells, HP charge, Packet Bomb, ■INV and MAP are now solid. The touch-control editor (TOUCH CONTROLS LAYOUT) has a **SEE-THROUGH BUTTONS** switch to bring back the old look.
- **Boss bar** (between ■INV and MAP):
  - shows `HP 812k/1.2m` instead of %, on a dark plate, without making the bar bigger;
  - split into blocks, one per boss phase, cut exactly where the phases change;
  - never shows HP above the maximum.
- Renders: `docs/screenshots/hud_review_*` and `touch_editor_opaque.png`.

## 0.48.0 (versionCode 137) — 2026-10-18 · UI SQUISH (phones)
### Changed (owner: "make our bars and icons super compact … a cleaner layout")
- **Phones, portrait and landscape:** the top bar is replaced by a small card in the top-left corner that floats over the room:
  - `W3·R7`, then a heart with a short segmented HP bar and the HP number;
  - an enemy icon with a short threats bar and the count, then `OP37` with a tiny XP bar;
  - the co-op partner's HP and the event name/timer, when there are any;
  - a **[+ 34]** button that opens €, score and the full buff grid (tap any buff to read it). [−] folds it away again.
- The clock and a small pause button sit in the top-right corner.
- **Bosses:** a thin bar in the boss's colour between ■INV and MAP, with the boss's name in small type above it and the % beside it, both on dark backgrounds.
- Only the folded card counts as HUD height, so opening it never moves the room or the buttons. In landscape, the room now starts at the top of the screen under the card.
- PC/Steam keeps its strip above the arena, since it covers nothing.
- Renders: `docs/screenshots/hud_review_portrait(_open).png`, `hud_review_landscape(_open).png` and `hud_review_boss.png`.

## 0.47.3 (versionCode 136) — 2026-10-18 · Squished damage numbers, compact buffs everywhere, ■INV
### Changed (owner)
- **Damage numbers on Android are squished:**
  - whole units with no decimals: 11,867 → 11k, 100,826 → 100k, 7,543,247 → 7m, up to 9t;
  - from 10 trillion they switch to scientific notation (1.2e13), drawn smaller;
  - PC keeps full numbers.
  - Damage is now carried as a Long, so very big hits no longer cap at 2.1 billion.
- **Buffs on Android landscape and PC** now match the phone portrait HUD: half size, half the gap, 3 rows.
  - Landscape: they sit under the threats/boss bar so the strip stays slim.
  - PC: they run under the strip, where the score no longer gets cut off.
- **INVENTORY → ■INV.** Hint banners like "DOOR OPEN" now measure the ■INV and MAP buttons and stay clear of them, so they no longer overlap.
- The auto-sell text now says plainly that it only sells what you pick up, never your backpack, Data Vault or locked items.
- Review renders in `docs/screenshots/`: `hud_review_portrait.png`, `hud_review_landscape.png`, `hud_review_pc.png` and `hud_review_steamdeck.png`.

## 0.47.2 (versionCode 135) — 2026-10-18 · Slimmer, see-through phone HUD
### Changed (owner)
- **The top HUD panel is as thin as possible** (portrait phones):
  - the header row went from 48 dp to 30 dp, with a 24 dp pause button (34 dp touch target);
  - the bar rows went from 16 dp to 14 dp;
  - the fold handle went from 22 dp to 14 dp;
  - the top padding is gone.
- **Buffs:**
  - chips are half size (12 dp) with half the gap (2 dp);
  - they're stacked in 3 rows that scroll sideways;
  - "+N" still counts the ones out of view, and tapping a chip still shows its details.
- **See-through:** the panel's background and bottom line are gone, so the room shows behind the HUD. Only the text (room, score, €, clock, bar labels and values, event name) and the buff chips get dark plates. The divider lines between room, score and € became small gaps, so the numbers keep their room.
- Landscape and PC HUDs are unchanged. Screens: `docs/store-screenshots/04_power_ups.png` and `hud_many_buffs.png`.

## 0.47.1 (versionCode 134) — 2026-10-18 · Thinner Plasma Beam
### Changed (owner: "it covers a whole line on the screen")
- The Plasma Beam is drawn at 15% of its old thickness: its glow, core, pulses, muzzle flare and impact all shrink. What it hits and its damage are unchanged.
- `docs/store-screenshots/01_plasma_beam.png` re-rendered.

## 0.47.0 (versionCode 133) — 2026-10-18 · GLITCHED ENCRYPTED gear
### Added (owner)
- **GLITCHED ENCRYPTED**, a new top gear rarity above ENCRYPTED.
  - **650 new pieces:**
    - 50 for every slot (helmet, chest, gloves, boots, implant, core, sidearm and rifle);
    - plus 50 class weapons for each class (Operative, Technician, Juggernaut, Phantom, Packet Mage);
    - each class weapon has a small class glyph.
  - **The look:** each piece is drawn in a cyan and red split, with torn glitch slices, a red/cyan colour fringe and scanlines. It's made by `tools/gear/glitched_encrypted.py` and stored in `gear/<slot>_gx.webp`; review sheets are in `docs/gear_gx/`.
  - **Animations, like GLITCHED LEGENDARY:**
    - the colour switches between cyan and red, with a hard glitch flip now and then;
    - the outline in the gear menu and the item name follow that colour, and the outline splits into red and cyan ghosts when it glitches;
    - the card tears into red and cyan slices;
    - the drop beam splits into red and cyan columns, with glitch blocks and code streaming up.
    - Clip: `docs/gear_gx/gx_animation.gif`.
  - **Drops:** 0.1% per boss piece, on every difficulty, slightly more on INFERNO. That is under half the ENCRYPTED rate. Elites drop it at a quarter of the boss rate. It never appears in the shop, and Idle Runs give GOLDEN instead.
  - **Stats:** ×3.5 base stats and 6 affixes.
  - **Signature perks:**
    - Rifle: full fire rate on the move, +65% damage, shots pierce 1 more threat.
    - Armour: auto-drinking potions, heal 10 HP per hit, +5% dodge.
    - Sidearm, implant and core: dash cooldown −50%, double healing, companion fires twice as fast, +20% damage.
  - Shop price 280k, which also sets its sell price.
- It appears at the top of the AUTO-SELL list.
### Notes
- Pets and eggs stay ENCRYPTED at most.
- Co-op snapshots carry 10 art bits now, so the co-op version is 33 and both players need this update.

## 0.46.0 (versionCode 132) — 2026-10-18 · Auto-sell
### Added (owner)
- **SETTINGS → AUTO-SELL GEAR: "Sell everything below ___".**
  - The dropdown lists OFF, then the rarities from highest to lowest: ENCRYPTED, ABYSSAL, CELESTIAL, VOLTAIC, FORGED, GLITCHED LEGENDARY, LEGENDARY, EPIC, RARE, UNCOMMON. COMMON isn't listed because nothing is below it.
  - Each rarity is shown in its own colour and card style, including the animated ones: ENCRYPTED's code rain, ABYSSAL's flames, the glitch and holo sheens.
  - Gear found below the chosen rarity is sold at its normal sell price as it's banked during a run. Solo runs credit the run wallet; co-op credits your saved €. "AUTO-SOLD +€X" shows over your operator.
  - Idle Run gear is auto-sold the same way when you collect it.
  - Never sold: shop purchases, trades and locked items.
  - Defaults to OFF, so old saves keep everything.
- Screens: `docs/screenshots/settings_autosell_closed.png` and `settings_autosell_open.png`.

## 0.45.3 (versionCode 131) — 2026-10-18 · NEON tracks play again
### Fixed (owner: "the songs I added last make no sound when played")
- The five NEON tracks (Neon Alley 1987 1–3, Neon Afterglow 1987, Neon Requiem) were silent on Android. They were never added to the Android music file list, so the player caught an error and showed the title with no sound. They now play. PC was not affected.
- New test `MusicFilesTest`: every soundtrack track must have its Android resource and its file, so a new song can't go silent this way again.

## 0.45.2 (versionCode 130) — 2026-10-18 · Hatch sound
### Added (owner)
- **Hatch sound.** When the egg bursts there is a shell crack, a soft pop, and a rising bell chime that sparkles out. It plays once per pet, on Android and PC, and it follows the SFX volume.
  - The sound is `sfx/pet_hatch.wav`, made by `tools/sfx/pet_hatch.py`.
  - Menu screens can now play sounds through `UiSound`.
- **Hatch clip:** `docs/pets_new/hatch_animation.mp4` shows a full hatch from start to finish, with sound and the stats card. It is built by `HatchClip` (`-PrenderPreviews`) and `tools/sfx/hatch_clip.sh`.

## 0.45.1 (versionCode 129) — 2026-10-18 · Hatch glow scales with rarity
### Changed (owner)
- The hatch glow is one animation, always in the rarity's colour, that grows with rarity. COMMON gets the base glow; each rarity is a step bigger, up to twice the size for ENCRYPTED. The rays, halo, sparkles, flash and the egg's glow while it cracks all scale together.
- Replaces 0.45.0's extra ray sets and shockwave rings.
- The reveal no longer clips the larger glow at the sides.

## 0.45.0 (versionCode 128) — 2026-10-18 · Three incubators and the hatch animation
### Changed (owner)
- **Three incubators.** They hatch three eggs at the same time; there is no queue any more.
  - Each incubator shows empty glass when it's free, or the egg you put in it, trembling more as it gets close to hatching. Each has its own countdown and progress bar.
  - Tap an incubator to see its egg, its INSTANT HATCH (◇ / €) buttons, and TAKE OUT (confirmed, because progress is lost).
  - Inferno Stones speed up all three.
  - Old saves: the hatching egg keeps its progress in incubator 1, the next two queued eggs go into incubators 2 and 3, and any other queued egg goes back to your eggs.
  - Each incubator sends its own "Your egg has hatched!" notification.
- The incubator pictures now have empty glass (`incubator_empty_1..6`, from `tools/pets/empty_incubators.py`), and the real egg is drawn inside.
### Added (owner: "an animation showing your new pet fully animated and its stats, rarity etc")
- **Hatch animation:**
  1. The egg shakes harder and harder as glowing cracks spread over it.
  2. It bursts in a white flash, with shell pieces flying out.
  3. The pet pops out with a bounce and floats. It is shown fully animated, including its orbs and gears for animated ENCRYPTED pets.
  4. Its card fades in: name, rarity badge (animated for the special rarities), damage, fire rate, ability count, and each ability with its cooldown.
- **Rarer eggs hatch bigger** (owner), all in the rarity's colour:
  - more, longer and brighter rays, and a second counter-turning set from GLITCHED up;
  - a wider halo and a bigger flash;
  - up to 50 sparkles;
  - shockwave rings rolling out from EPIC up (more rings for rarer pets).
- Tap anywhere to skip to the card.
- When several eggs hatch together, each pet gets its own reveal (NEXT PET).
- Pets now remember which egg they came from.

## 0.44.0 (versionCode 127) — 2026-10-17 · Operator redesign
### Changed (owner-approved redesign)
- **Every operator gets the new look.** Each class keeps its exact shapes, animations and skins, and gains the pets' shading:
  - armour panels are filled with a richer, slightly muted tone of the skin colour;
  - each panel is lit as a rounded volume from the top left, with a soft highlight;
  - limbs have a highlight and shadow along them.
- **Packet Mage:** the robe is matte cloth with soft folds that deepen toward the hem. There's no metal shine on it.
- **Phantom:** the hood and cape keep their original look.
- Neon trim, the >_< faces and the visors are unchanged.
- The look applies everywhere the operators appear: runs, co-op, the character screens, the creator, the class picker and the shopkeeper.
### Technical
- New `ui/common/FigureShade` (`lift` / `overlay` / `tube` / `folds`).
- `ClassFigures.look` now returns the lifted plates, and `rawLook` the original ones.
- The hero figure and helmet are shaded too. The menu emblem keeps its flat look.
- `OperatorRawRenders` writes both `raw/` (flat) and `ingame/` renders.

## 0.43.1 (versionCode 125) — 2026-10-17 · Technician without the blaster
### Changed (owner)
- The TECHNICIAN no longer holds the small blaster. The Plasma Beam arm cannon still shows when that power-up is taken.
- Operator redesign concept, v2 (`docs/operators_concept/v2_*.png`, `tools/operators/shade_operators.py`):
  - the real figures, shaded in the pets' style;
  - the Phantom's hood and cape are left as they were.
  - Not in the game yet.

## 0.43.0 (versionCode 124) — 2026-10-17 · Instant hatch with €
### Added (owner)
- **HATCH WITH € · €N** under the ◇ button on the incubator. It takes two taps: the second tap confirms.
  - Price: the account's highest operator level × the ◇ price × 100. It uses the highest level among all characters, so switching to a low-level character doesn't make it cheaper.
  - Like the ◇ price, it falls as the egg hatches and with Inferno Stones.
  - Fresh egg at OP 60: Common €24,000, Rare €240k, Epic €960k, Legendary €2.4M, Abyssal €19.2M, Encrypted €26.4M.

## 0.42.0 (versionCode 123) — 2026-10-17 · Instant hatch for ◇
### Added (owner-approved price: ×4)
- **INSTANT HATCH · ◇N** on the incubator.
  - Pay ◇ (diamonds only, never €) to hatch the egg now. It takes two taps: the second tap confirms.
  - Price: 4 × ⌈rarity × minutes left ÷ 6⌉ ◇, where rarity runs from 1 (Common) to 11 (Encrypted).
  - The price falls as the egg hatches, and Inferno Stones lower it too.
  - Fresh-egg prices: Common ◇4, Uncommon ◇16, Rare ◇40, Epic ◇160, Legendary ◇400, Glitched Legendary ◇600, Forged ◇840, Voltaic ◇1,280, Celestial ◇2,160, Abyssal ◇3,200, Encrypted ◇4,400.
- The button greys out, with your ◇ balance shown under it, when you can't afford it.

## 0.41.0 (versionCode 122) — 2026-10-17 · 500 animated ENCRYPTED pets
### Added (owner: "500 more encrypted pets in all of the variety of colors… special orbs rotating them or different moving mechanical parts")
- **500 new ENCRYPTED pets, each with its own egg and name** (e.g. "Cyan Kernel Seeker").
  - 14 colourways: pink, purple, blue, cyan, green, aqua, red, yellow, gold, platinum, orange, obsidian, pearl, and prism (rainbow).
  - **Live animations.** Each pet has one or two moving parts, and most also hover:
    - orbiting orbs (they pass behind and in front of the pet);
    - a spinning ring of lights;
    - twin rotating gears;
    - a spinning propeller;
    - a helix of rising motes;
    - floating crystal shards;
    - a visor scan line;
    - a pulsing core glow.
  - Prism pets cycle through every colour.
  - The animation plays in runs and in the PETS screen.
- ENCRYPTED eggs now hatch from 570 ENCRYPTED designs: 500 animated and 70 static.
- Review: `docs/pets_new/encrypted_animated_1…5.png` (all 500, with each pet's moving parts listed), plus `encrypted_animated_preview.gif` and `encrypted_animated_closeup.gif`.
### Technical
- The generator now writes 1,000 new pets (pages 4–7). The first 500 are unchanged.
- `new_pets.txt` has a 4th field for each animated pet's moving parts, parsed by `PetAnim`.
- The parts are drawn by `ui/common/PetFx`, through `PetArt.drawPet(…, time)`.

## 0.40.0 (versionCode 121) — 2026-10-17 · 500 new pets (GLITCHED … ENCRYPTED)
### Added (owner: "500 more pets ranging from glitched to encrypted rarity, their eggs, and their design")
- **500 new companions, each with its own egg.**
  - Split by rarity: GLITCHED 90, FORGED 85, VOLTAIC 85, CELESTIAL 85, ABYSSAL 85, ENCRYPTED 70.
  - Each has a unique name, such as "Cinderhex Puck" or "Cipher Beetle".
  - They are built in the style of the original pets: glossy robot bodies, dark visor faces with glowing eyes, side pods, legs or thrusters, and toppers.
  - Each tier has its own look:
    - GLITCHED: gold with magenta/cyan glitch tears;
    - FORGED: molten cracks and embers;
    - VOLTAIC: lightning arcs;
    - CELESTIAL: platinum, halos and wings;
    - ABYSSAL: hellfire and horns;
    - ENCRYPTED: black with cyan circuitry.
- GLITCHED and better eggs now hatch one of the new pets designed for that rarity, in its own matching egg. Lower rarities keep the original 1,005 pets.
- Review sheets: `docs/pets_new/` has one sheet per tier with each pet, its egg and its name, plus all 500 pets and all 500 eggs.
### Technical
- `tools/pets/gen_new_pets.py` (deterministic) writes `pets_4/5.webp`, `eggs_4/5.webp` and `new_pets.txt`.
- New pet art indices are `Pets.NEW_BASE` (1024) + k, and the egg art uses the same index. Helpers: `Pets.validPet`, `validEgg`, `designedTier`.

## 0.39.1 (versionCode 120) — 2026-10-17 · Tougher NORMAL past level 100, HARD idle runs
### Changed
- **NORMAL past level 100** (campaign Worlds 11–15, and Endless):
  - Threat HP rises 1.2% a level, up to ×2.5. Damage rises 0.6% a level, up to ×1.6.
  - HARD rises with it and always stays at least 10% tougher than NORMAL (owner's choice).
- **Idle Runs:**
  - You can choose a HARD run (STANDARD / HARD / INFERNO). The widget shows a HARD badge for it.
  - Idle Runs never drop GLITCHED or ENCRYPTED gear. Those rolls come out as GOLDEN.
  - Idle threats follow the same HARD ease and NORMAL late curve as real runs.
- HARD boss loot tuning.

## 0.39.0 (versionCode 119) — 2026-10-17 · GLITCHED LEGENDARY, ENCRYPTED, new chip tiers, Hard balance, pet abilities
### Added (owner: "Hard mode is not possible past world 2")
- **Two new rarities for gear and chips:**
  - **GLITCHED LEGENDARY** (magenta) sits right after GOLDEN. It drops on every difficulty: about 2.2% per boss gear piece, a quarter of that from elites, and a little more on INFERNO and with luck. Its card is gold foil that tears into magenta/cyan glitch slices.
  - **ENCRYPTED** (electric cyan) is the new top tier, above ABYSSAL. It is very rare on every difficulty (about 0.25% per boss gear piece) and never appears in the shop. Its card has falling code and a pulsing border, and its drops have code rising up a flaming beam.
- **Gear:**
  - GOLDEN and better gear has +30% main stats and +30% perk values. This is applied when worn, so gear you already own gets it too.
  - **GLITCHED perks:**
    - rifle: fire on the move at 50% rate, +10% damage;
    - armor: heal 3 HP per hit, +3% dodge;
    - system: dash cooldown −25%, +15% damage.
  - **ENCRYPTED perks:**
    - rifle: fire at full rate on the move, +50% damage;
    - armor: HP potions drink themselves at 20% HP or on a lethal hit, and heal 8 HP per hit;
    - system: dash cooldown −50%, double healing, companion fires twice as fast.
- **Chips:** seven new chip tiers (GLITCHED, FORGED, VOLTAIC, CELESTIAL, ABYSSAL, ENCRYPTED, with TITANIUM kept between GLITCHED and FORGED) and 22 new chips:
  - **GLITCHED:**
    - GHOST TRIGGER: fire on the move at 50% rate;
    - BUFFER OVERFLOW;
    - STACK SMASH: heal per hit;
    - PHASE SKIP: shorter dash cooldown, dodge;
    - ROLLBACK: HP, healing, stronger potions.
  - **FORGED:** MOLTEN CORE, SLAG PLATING.
  - **VOLTAIC:** STORM CIRCUIT, OVERVOLT NODES.
  - **CELESTIAL:** HALO PROTOCOL, STARFALL OPTICS.
  - **ABYSSAL:**
    - INFERNO PROTOCOL: heal 10 HP per hit;
    - HELLGATE ROUNDS.
  - **ENCRYPTED** (very rare, near game-breaking):
    - ZERO LATENCY: dash cooldown −50%;
    - RUN & GUN: shoot while moving, full rate;
    - DOUBLE PAYLOAD: damage ×2;
    - REGEN MATRIX: all healing ×2, potions included;
    - FULL RESTORE: potions heal to full, and the potion turns into a red glowing abyssal draught on the floor, the HUD and the touch button;
    - FAILSAFE: a potion drinks itself at 20% HP or on a hit that would kill you, and you survive that hit;
    - PHANTOM CODE: dodge ×2, capped at 60%;
    - OVERCLOCK SYNC: your fire rate and your pet's ×2;
    - VAMPIRIC ROOT: heal 20 HP per hit.
- **Hard balance:**
  - From World 3, threat HP and damage on HARD ease off, reaching −25% by World 5. HARD threats still always stay at least 10% tougher than NORMAL's. INFERNO is untouched.
  - HARD rooms drop 2 chips (event rooms 3), with extra chip luck, and HARD bosses give at least 3 picks.
- **HP potions (all difficulties):** every boss drops 3 per player, and elites now drop one 33% of the time (was 8%). Idle Runs keep the old odds.
- **Pet abilities:**
  - Abilities: FIRE BITE, CHAIN SHOCK, HEAL PULSE, SHIELD BUBBLE, SHOCK NOVA, HOMING MISSILES, PIERCING LASER and RAPID FIRE.
  - Below EPIC a pet has 1 ability. EPIC and better may have 2. GLITCHED and better may have 3 or 4 (the cap is 4).
  - Rarer pets get shorter cooldowns.
  - Abilities are shown in PETS and on the hatch reveal.
  - A pet's abilities are fixed by its id, so every pet you already own has them now.
- **Hatch notification (Android):** "Your egg has hatched!" fires when the egg in the incubator is ready, and tapping it opens CHARACTER › PETS.
### Fixed (owner: co-op loot looked wrong on the joined player's screen)
- **Co-op drops on the guest:**
  - The guest's own drops now look solid there, and the host's show as the faint see-through glow, the same way the host sees them.
  - Cause: the guest rebuilt every drop from each snapshot with its pop-in timer reset to 0. Its own drops were drawn at 5% size, and the partner's glow at size 0.
  - Fix: the snapshot now carries each drop's age, and the guest keeps ageing drops between snapshots.
### Changed
- The fire-rate cap was raised from 8 to 12 volleys per second (to make room for OVERCLOCK SYNC).
- Co-op protocol v32: gear rarity numbers shifted, so 0.38 and 0.39 can't play together.
### Technical
- New RunStats fields: `healOnHit`, `moveFireRate`, `dashCooldownMul`, `potionFullHeal`, `potionMul`, `autoPotion`, `dodgeMul`, `petRateMul`.
- `GearRarity.GLITCHED` / `ENCRYPTED` (saved as `ENCRYPTED_X`, because `ENCRYPTED` is FORGED's old save name) plus `statBoost` and `animated`.
- `Rarity.highTier` is now an explicit value capped at 3, so the new tiers don't compound luck.
- New: `GameEngine.hardEase`, `widget/PetHatchWorker`, `PetReminders`.

## 0.38.0 (versionCode 118) — 2026-10-17 · Companions: eggs, incubator, Inferno Stones, pets
### Added (owner: companion / pet addon)
- **Pets** that follow you and fight. Equip one in CHARACTER › PETS.
  - It hovers beside your operative in every run and fires at the nearest threat.
  - Each shot deals 30% (Common) to 130% (Abyssal) of your payload, every 1.1 s down to 0.6 s.
  - Co-op shows each player's pet, and both pets fight.
- **Art:** all of it is the owner's ten pet / egg sheets cut out.
  - **1,005 pets** and **872 eggs**.
  - Each egg's rarity matches its colour (grey Common, green Uncommon, blue Rare, purple Epic, gold Legendary, orange Forged, yellow Voltaic, cyan Celestial, red Abyssal).
  - Names are generated, e.g. "Violet Nova Egg" and "Crimson Hexpup".
- **Eggs:**
  - **Drops:** a boss drops one 35% of the time, an elite 2%. Each player rolls their own, using the gear rarity odds (INFERNO rarities on INFERNO).
  - **Store:** MYSTERY EGG ◇150, EPIC EGG ◇600 and LEGENDARY EGG ◇1500 (defaults).
  - Up to 100 eggs.
- **Incubator** (CHARACTER › PETS, or **EGGS → INCUBATOR** on the inventory):
  - Hatch times by rarity: Common 5 min, Uncommon 10 min, Rare 20 min, Epic 1 h, Legendary 2 h, Forged 3 h, Voltaic 4 h, Celestial 6 h, Abyssal 8 h.
  - One egg hatches at a time, with a live countdown (h:mm:ss) and the egg trembling as it gets close. Three more wait in a queue, and a queued egg can be taken back.
  - When an egg hatches, a NEW PET HATCHED! reveal shows the pet.
- **Inferno Stones:**
  - An ultra-rare, ABYSSAL-tier find from INFERNO bosses: 0.4% × INFERNO level per boss, at most 20%.
  - Socket up to six in the incubator. Each takes 15% off the hatch time, 90% with all six.
  - The incubator picture glows hotter with each stone (the owner's six incubator states).
- **The timer can't be fooled by changing the phone's clock.**
  - Hatching only counts time on the device's since-boot clock, which the date/time settings don't touch.
  - After a reboot (or a PC restart), the time away is checked against network time. Until the phone is online the incubator shows VERIFYING TIME… and waits, and then it credits the real time that passed.
### Technical
- `data/Pets.kt` (PetEgg, Pet, Incubator, ClockAnchor, PetLoadout, odds, hatch times), `meta/PetCare.kt` (`Incubators`, `TrustedClock`), `PlatformClock` (Android: elapsedRealtime + boot count; PC: per launch).
- Save: `eggs`, `pets`, `activePet`, `infernoStones`, `incubator`, `petSeq` (old saves load with none).
- Engine: `RunConfig.pet` / `AllyConfig.pet`, `Operative.pet/eggsFound/stonesFound`, `updatePet`, `rollPetDrops` (own random, so existing seeds are unchanged). Co-op codec **v31** (`guestEggs`, `guestStones`), `RoomPlayer.pet`.
- Art: `resources/pets/` (pets_0..3, eggs_0..3, stones, incubator_1..6, hues.txt), 4.3 MB.
- Tests: `V038PetsTest` (hatch times, stone math incl. the owner's 2:00:00 → 0:12:00, real-time hatch, clock changes ignored, reboot needs network, queue, store, pet in a run, old saves); renders `PetRenders`.

## 0.37.6 (versionCode 116) — 2026-10-17 · 3 × 1 Idle Run widget
### Added (owner: "did you not make a 3 x 1 widget?")
- **IDLE RUN (wide), a 3 × 1 home-screen widget**, next to the 2 × 3 one in the widget list.
  - Left: the same animated battle preview (your operator, the one threat or boss, room props).
  - Right:
    - **Active run:** IDLE RUN plus the STANDARD / INFERNO badge, level and next boss, € and XP earned.
    - **Defeated:** OPERATOR DEFEATED with the highest level, € and XP, and START NEW IDLE RUN? on taller widgets.
    - **No run:** tap to start.
  - Resizable; on a taller widget it also shows the OP level and the idle rate.
  - Tap opens Idle Runs. Updated together with the 2 × 3.
### Technical
- `IdleRunWideWidgetReceiver`, `IdleRunWidget.buildWide`, `layout/widget_idle_run_wide.xml`, `xml/idle_run_wide_widget_info.xml`. Test: `wideWidgetLaysOutAtThreeByOne` (200 × 56 and 260 × 72 dp, ≥ 8 sp text).

## 0.37.5 (versionCode 115) — 2026-10-17 · five new NEON tracks
### Added (owner's music)
- **Five new tracks:** Neon Alley 1987 (1), (2) and (3), Neon Afterglow 1987, and Neon Requiem.
  - They shuffle into the music on any level, on every difficulty including INFERNO.
  - Never on a boss round: not even when hand-picked in the music player (it moves on when a boss arrives).
  - Always played exactly as recorded: never sped up or slowed (the 1.25× / 0.75× round tempo doesn't apply), no pitch change, no EQ and no reverb.
  - They are also listed in the pause-screen music player.
- Stored as Ogg Vorbis at about 96 kbps like the rest of the soundtrack: 10.6 MB for all five (the MP3s were 21.5 MB). This is only a format conversion; nothing else in the audio was changed.
### Technical
- `TrackGroup.NEON` and `MusicTrack.plain`. `AudioManager.speedNow/pitchNow/applyEffects/musicGain` skip plain tracks; Android and PC skip them on boss rounds.
- `res/raw/track_neon_*.ogg`. Test: `V0375MusicTest`.

## 0.37.4 (versionCode 114) — 2026-10-17 · Idle Runs pay 20% less € and gear
### Changed (owner: "reduce gear and money rewards by 20%. This doesn't need to be a better option than actually playing the game")
- **€:** idle runs now pay **40%** of a normal run's € (was 50%).
- **Gear:** idle runs keep 20% fewer pieces (still EPIC and better only).
- **XP is unchanged at 50%.** So is how long runs last.
- The widget, menu button and Idle Runs page now read "40% € · 50% XP".

## 0.37.3 (versionCode 113) — 2026-10-17 · Idle Runs keep only EPIC+ gear
### Changed (owner: "Idle rewards only epic and higher drops")
- Idle Runs now keep only **EPIC and better** gear: Epic, Legendary, and the INFERNO rarities on INFERNO runs.
- The drop rolls themselves are unchanged (same chances, same rarity odds); lower rarities are simply not kept.
- A 1–2 hour geared run now brings home about 5–27 pieces instead of 90–350, so the backpack and Data Vault no longer fill with low-tier gear.
- XP, € and how long runs last are unchanged.

## 0.37.2 (versionCode 112) — 2026-10-16 · Idle Runs last twice as long again
### Changed (owner: "make them run even longer, double it again")
- Idle operators:
  - take about 35% less damage than in 0.37.1;
  - heal 32% of max HP between rooms (was 22%);
  - land 70% of their damage (was 66%).
- In testing, runs now last about 2.2× as long as in 0.37.1. For example:
  - OP Lv 20 with Legendary gear on STANDARD: about level 73 in about 2 hours 7 minutes (was level 48 in about 1 hour).
  - OP Lv 1 with Rare gear: about level 40 in 47 minutes (was level 30 in 27 minutes).
- Rewards are unchanged: still exactly 50% of a normal run's € and XP.

## 0.37.1 (versionCode 111) — 2026-10-16 · easier Idle Runs
### Changed (owner: "make the runs a little easier so that they can run longer")
- Idle operators take less damage (−25% from threats and bosses), heal more between rooms (22% of max HP, was 16%) and land a little more of their damage (66%, was 62%).
- Result in testing: runs reach about 40% more levels and last about twice as long. For example:
  - OP Lv 20 with Legendary gear on STANDARD: about level 48 in about an hour (was level 35 in 35 minutes).
  - OP Lv 1 with Rare gear: about level 30 (was 22).
- Rewards are unchanged: still exactly 50% of a normal run's € and XP.

## 0.37.0 (versionCode 110) — 2026-10-16 · Idle Runs + home-screen widget, Boss Expansion moving parts
### Added (owner: Idle Run Widget brief)
- **IDLE RUNS** (Android; main menu › IDLE RUNS). Your equipped operator fights on its own, with the game closed:
  - One threat at a time, a boss every 10 levels (STANDARD, or INFERNO once unlocked, at any unlocked INFERNO level).
  - Uses the character's real stats: class, OP level / Overclock, permanent upgrades, equipped gear, rarity perks.
  - Upgrade chips are picked automatically: the offer is the normal roll, and the pick favours what helps the build, with randomness (no perfect build).
  - Gear drops from elites and bosses by the normal drop rules (INFERNO rules on INFERNO).
  - Pays **exactly 50%** of the € and XP the same run would pay normally. € go to the account; XP and gear go to the character that ran it, collected when you open Idle Runs.
  - Ends when the operator is terminated (or after 12 hours, or when you RECALL it). The summary shows highest level, € and XP earned, chips and gear.
  - Tuned so a run reaches about where a competent player would with that loadout: sometimes a little shorter, sometimes a little further.
- **IDLE RUN home-screen widget (2 × 3):**
  - Active run: a small battle preview (your operator, the one threat or boss, room props, INFERNO room on INFERNO runs) cycling idle → attack → hit → wind-up; OP LV, level, € and XP earned; boss progress dots and NEXT BOSS.
  - Operator Defeated: highest level achieved, XP and € earned, START NEW IDLE RUN?
  - No run: START IDLE RUN.
  - Tapping anywhere opens the game straight on Idle Runs (no splash). It never starts a run from the home screen.
  - The art is the game's own drawing code for every piece (figures, threat sprites, boss bodies, INFERNO paintings, props), rendered to bitmaps. Nothing redrawn.
- **Notification** when an idle operator is terminated ("start a new idle run?"). Android 13+ asks for permission when you start your first idle run.
### Changed
- **Boss Expansion bosses have mesh rigs** (moving parts, like the classics and INFERNO bosses): Vault Sentinel, Rootkit Apostle, Pulse Bishop, Packet Reaper, Worm Queen, Glitch Forge, Botnet Monarch, Ransom King, Black Ice Overlord, Spectral Firewall, Nullshade Specter.
  - Circuit Hydra is unchanged: its heads are placed by the engine (they are the hitboxes and fire the beams) and already move on their own.
### Technical
- `idle/` (shared): `IdleRunState` (one JSON document), `IdleRunSimulator` (seeded, deterministic per event, catch-up from any timestamp), `IdleRuns` (host seam + `collect`).
- `widget/` (Android only, excluded from the PC build): `IdleRunRepository` (own prefs file), `AndroidIdleHost`, `IdleRunWidgetReceiver` / `IdleRunWidget` (RemoteViews + ViewFlipper of 4 pre-rendered frames), `IdleRunWorker` (WorkManager: every 15 min, plus a one-off at the predicted end), `IdleRunNotifier`.
- New dependency: `androidx.work:work-runtime-ktx` 2.9.1. New permission: `POST_NOTIFICATIONS`.
- `ui/DeepLink` + `MainActivity` intent `com.cyberoperative.game.action.OPEN_IDLE_RUNS`. `MenuTarget.IDLE_RUNS` (hidden when no Idle Runs host).
- `ArenaRenderer.drawPropAlone` (props without a live engine), `drawBossPreview(box = …)`, `IdleBattleArt`, `expansionRigs`.
- Tests: `V037IdleRunTest` (50% rewards, stats/gear, chips, boss every 10, Standard/INFERNO pools, INFERNO lock, defeat, serialization, restart, catch-up, widget tap, old saves, 2 × 3 layout at two sizes with ≥ 8 sp text).

## 0.36.4 (versionCode 109) — 2026-10-16 · co-op €: everything separate
### Changed (owner: "no [shared run earnings], everything is separate unless trading")
- **Co-op € are fully separate.** Each player's wallet is their own saved total plus only what they earn:
  - **Kills:** the cash bag belongs to whoever got the kill, and only they can pick it up.
  - **Chips that pay € (Crypto Cache):** go to whoever takes them.
  - **Room-clear, vault and boss payouts:** each player gets their own full payout; nothing is split or pooled.
  - € only move between players through a trade.
- **XP is unchanged.** Each player levels their own character, and every kill by either player gives both of them the full XP.
- Co-op codec **v30** (`NetOp.eurosIncome`).

## 0.36.3 (versionCode 108) — 2026-10-16 · one € total in co-op too, hidden TRADE, short numbers
### Changed
- **One € total everywhere, co-op included** (owner: "€ needs to be persistent… the same in game as in main menu").
  - Solo already worked this way.
  - In co-op each player now has their own wallet: their saved total, plus the run's € (both players get full rewards), minus what they spend or trade away.
  - Before, co-op showed a shared pot that started at €0. Now your in-game € equals your menu € in every mode.
  - Trades move € between the two wallets. Gear can't be sold mid co-op run; sell between runs or in solo.
- **TRADE is invisible unless it can be used:** co-op only, both players standing within 170 units of each other, no threats in the room.
- **Short numbers when the full one doesn't fit** (owner): 398,765 → 398k, 3,785,245 → 3.7m, 198,465,245 → 198m, then b, t. Always rounded down.
  - Used for score and € in every HUD layout, the shop wallet and prices, the € chips on the menu, and the trade window.
### Technical
- `GameEngine.perPlayerWallets/walletOf/eurosNet`, `Operative.startEuros/eurosSpent`.
- `RoomPlayer.euros` (Firebase, Steam and direct-IP mappings).
- Co-op codec **v29** (`NetOp.eurosSpent`), `GameEngine.TRADE_RANGE`.
- `FitNumber` / `formatAmount` (lowercase k/m/b/t, one decimal).

## 0.36.2 (versionCode 107) — 2026-10-16 · classic bosses get moving parts too
### Changed (owner: "Now animate all classic bosses this way")
- **The twelve classic bosses have mesh rigs.** Each keeps all its own animation, and its parts now move on their own too:
  - **Breach:** thrusters fire and bob; hull rocks; ring breathes; drill tip sways.
  - **Botmaster:** antennas whip; drone ring turns; drones bob on their own beats.
  - **Worm Prime:** the body slithers in a travelling wave to a flicking tail; maw gapes.
  - **Ransom:** shackle jolts against the lock; chains swing; keyhole throbs; glyphs drift.
  - **Rootkit King:** tendril arms reach and curl; robe ripples; hood nods.
  - **Syn Storm:** packet ring turns; reactor breathes; dish rocks.
  - **Kernel Panic:** pin rows twitch; die throbs; stray glyphs jump.
  - **Exfil:** wings bank; twin thrusters flare; nose sways.
  - **White Eye:** spiked ring turns; pupil dilates; eye rolls.
  - **Zombie:** arms swing; legs shuffle; head lolls.
  - **Spoofer:** mask drifts; echo ghosts slide apart.
  - **Good Game:** treads judder; turret ring turns; cannon recoils; exhaust puffs.
- How: each frame the boss's own body is drawn offscreen, 1.5× its framing so tails and orbits fit, then drawn through its posed mesh.
- Technical:
  - The shared engine moved to `ui/game/BossMeshRig.kt` (`Deform` incl. the new `Zoom`, `MeshGrid`).
  - `MeshedBody` and `classicRigs` are in `ui/game/BossMeshBodies.kt`; `BossBodies.forId` wraps rigged bodies.
  - `InfernoBossAnim` renders the classics too.

## 0.36.1 (versionCode 106) — 2026-10-16 · INFERNO bosses get real moving parts
### Changed (owner: "every boss is animated and has moving parts and not just a moving image")
- **Every INFERNO boss painting is now a deformable mesh** (28 × 28 grid) with its own moving parts. Each part moves on its own beat and harder on wind-up:
  - **Hellcore Tyrant:** rune pylons bob and slide out; reactor rings turn; crown spikes sway.
  - **Ember Warden:** shield pylons deploy outward; turret tips bob; the furnace eye swells.
  - **Cinder Widow:** eight legs step in a wave; abdomen breathes; mandibles snap.
  - **Magma Pulse:** armour bands and debris belt turn against each other; core swells.
  - **Ashen Reaver:** scythes swing from the shoulders; ash cloak ripples; hood nods.
  - **Infernal Router:** port ring turns; antennas sway; eye pulses.
  - **Pyroclast Oracle:** six crystals drift on their own beats; halo rings turn; robe ripples.
  - **Searfang Colossus:** four-leg stomping gait; jaw works; stacks shudder.
  - **Burnwire Bishop:** lanterns swing on their chains; halo turns; mitre sways.
  - **Furnace Maw:** jaws chew and gape; chimneys shudder; side armour heaves.
  - **Redline Sovereign:** crown halo turns; royal pylons slide and bob.
  - **Scorch Halo:** blade ring and inner ring turn opposite ways; eye dilates.
  - **Volcanic Null:** suppression pylons slide and bob; halo turns; fragments drift.
  - **Smolder Hex:** turret ring turns, each turret kicking on its own beat; inner hex counter-turns.
  - **Combustion Monarch:** both great wings beat and spread on wind-up; tail sways; halo turns.
- Drawn as one textured triangle mesh on PC and Android 10+. Older Android draws the same grid tile by tile.
- Technical: `Deform` parts (Bend, Step, Bob, Jaw, Spin, Breathe, Slide, Ripple) in `InfernoBodies.kt`; `AppInfo.meshDrawing`. New render: `InfernoBossAnim` (frame sequences per pack).

## 0.36.0 (versionCode 105) — 2026-10-16 · INFERNO boss visual redesign, recorded drop sounds
### Changed (owner: "MAJOR VISUAL REDESIGN PASS on the 15 Inferno bosses")
- **Every INFERNO boss now uses the owner's Inferno Boss Pack paintings**, cut out of the sheets with soft glow kept, as its in-game body. Each boss has its three painted states, one per HP phase, and an animation rig on top:
  - wind-ups cross-fade toward the next painting, and the jaw bosses open their maw;
  - reactor cores pulse and go white-hot on wind-up;
  - movement: hover, drift, a heavy settle, or a stomping gait;
  - moving halves: Monarch's wings beat and open wide; Widow's legs, Reaver's scythes, Bishop's chains and Oracle's crystals sway; Warden, Sovereign, Null and Tyrant pylons step outward;
  - counter-rotating ember rings on the machine bosses, embers, and a white hit flash;
  - Searfang turns to face you.
- All 15 are back on the inferno palette: red, crimson, orange and charcoal, with white-hot cores and magenta-red only on Volcanic Null.
- Names, attacks, roles, stats, placement and IDs are unchanged. The audit and approach are in `docs/INFERNO_BOSS_REDESIGN.md`.
### Added
- **The owner's recorded drop sounds for the INFERNO rarities:** T1 Forged, T2 Voltaic, T3 Celestial, T4 Abyssal.
  - Audio change: the game could only play synthesized effects before. It now also plays recorded files from `resources/sfx/` on Android (SoundPool) and PC (Clip).
  - The files were converted to mono 44.1 kHz WAV (1.3 MB → 0.6 MB).
### Technical
- New: `resources/bosses/inferno_{alpha,beta,gamma}.webp`, `InfernoRig` / `InfernoSpriteBody`, `SoundBank.files/gain/fileBytes`, `GameSound.GEAR_FORGED…GEAR_ABYSSAL`.
- New renders: `InfernoBossRenders` sheets (with hitbox rings) and arena shots.

## 0.35.1 (versionCode 104) — 2026-10-16 · every INFERNO boss burns its own colour
### Changed (owner: "all inferno bosses will not be the same color")
- Each of the 15 INFERNO bosses has its own fire, armour and HP-bar colour, keeping the volcanic look:
  - **Alpha:** Hellcore Tyrant forge orange; Ember Warden amber/bronze; Cinder Widow crimson-magenta; Magma Pulse sun-orange; Ashen Reaver ash grey with pale embers.
  - **Beta:** Infernal Router hell red with cyan data; Pyroclast Oracle violet hellfire; Searfang Colossus blood red; Burnwire Bishop blue soul-fire; Furnace Maw sulphur-green brimstone.
  - **Gamma:** Redline Sovereign crimson and gold; Scorch Halo solar gold-white; Volcanic Null magenta on obsidian; Smolder Hex teal ritual fire; Combustion Monarch phoenix rose-orange.
- Technical: `InfernoPalette` / `InfernoPalettes` in `ui/game/InfernoBodies.kt`.

## 0.35.0 (versionCode 103) — 2026-10-16 · trading, selling, 15 INFERNO bosses, class ability upgrades
### Added
- **Sell backpack items** (owner). An unlocked backpack piece can be sold. It's worth item level × rarity tier (Common 1 … Abyssal 9) × €50, capped at 85% of its shop price, and it takes a confirming second tap.
  - Out of a run, the € is banked.
  - In a solo run, the € goes straight into the run wallet.
  - In co-op, the € is banked.
- **Co-op trading.** A small **TRADE** button sits under the MAP corner.
  - It only works while the room has no threats, and a trade in progress closes if threats arrive.
  - The partner accepts the request, then each side offers banked €, potions (HP charges) and up to 8 backpack items.
  - Both must press ACCEPT. Any change to either offer clears both accepts.
  - A player who'd end up over the potion limit can't accept.
  - Co-op codec **v28**.
- **15 INFERNO-only bosses** (owner's Pack Alpha, Beta and Gamma sheets). Each has the owner's name, role, threat tier and four named attacks, built from the arena's attack patterns, with a body drawn after its card.
  - On INFERNO, boss rooms from world 3 (Alpha) and world 4 (Beta and Gamma) draw only from these.
  - **Combustion Monarch** guards INFERNO world 15.
  - Endless boss rounds add them from level 160.
  - Approval sheets: `docs/screenshots/inferno_pack_{alpha,beta,gamma}.png`.
### Changed
- **Shadow Step** also throws 6 knives that home in on threats through walls.
- **Packet Mage:**
  - **Firestorm** lands on the thickest group of threats, whichever way you move or aim.
  - **Arcane Nova** bolts pass through walls and cover and home in for 5 seconds, from twice as far.
- **Higher-rarity gear is stronger and more distinct:**
  - Base values: Legendary ×1.6, Forged ×1.85, Voltaic ×2.1, Celestial ×2.4, Abyssal ×2.8.
  - Affixes grow 18% per tier above Legendary (was 12%).
  - Every Legendary-and-up piece has a **signature perk** by type (rifle / armor / system), shown with ★ on the item card. Examples: Forged armor sprays slag bolts when hit; Voltaic systems make Packet Nodes fire their own bolts; Celestial rifles add a parallel shot; Abyssal rifles can delete a threat outright.
### Technical
- New files: `data/InfernoBosses.kt`, `ui/game/InfernoBodies.kt`, `engine/Trade.kt`, `ui/game/TradeUi.kt`, `GearPerks`, `Gear.sellPrice`, `GearBank.sell`.
- New `Projectile.seek` field and `GameEngine.grantEuros`.
- `Bosses.eligible/randomForLevel(level, seed, inferno, endless)`.
- New tests: `V035Test`. New render: `InfernoBossRenders`.

## 0.34.0 (versionCode 102) — 2026-10-14 · INFERNO 1–500, one shared Data Vault
### Changed
- **INFERNO now has levels 1–500 (owner, curve A).** Each level is a little harder than the last:
  - threat HP ×2.175 at level 1, +1.2% per level compounding (×2.4 at 10, ×7.1 at 100, ×837 at 500);
  - damage on the same curve at half the rate (+0.6% per level);
  - rewards +0.02× per level; loot luck grows slowly.
  - Pick any unlocked level with the new −10 / −1 / +1 / +10 stepper (solo and the co-op lobby).
  - Beating World 15's boss on a level unlocks the next one.
  - Saves on INFERNO I–X keep their level number, now on the gentler curve.
- **ABYSSAL in the shop: about 1 shop in 100** (owner). That's 0.251% per equipment piece, from INFERNO 7.
- Deep INFERNO levels no longer add more boss pieces or elite drop chance past level 10 (3 + 3 pieces; elites 45%).
### Added
- **One Data Vault shared by every character** (owner). Characters keep their own equipped gear and backpack.
  - Existing per-character vaults merge into the shared vault once.
  - A **DATA VAULT** button on the main menu opens it directly.
### Technical
- `Difficulty` is now a class: `EASY/MEDIUM/HARD` plus `Difficulty.inferno(level)`. Its name is `INFERNO_n`, and `byName` still reads old saves.
- `Gear.shopOdds`, `SHOP_ABYSSAL` and `SHOP_GEAR_PIECES`. `InfernoStepper`, `MenuTarget.VAULT`.
- The `CharacterSave.vault` field is kept only so old saves load.

## 0.33.0 (versionCode 101) — 2026-10-14 · four new INFERNO equipment rarities
### Added (owner-approved names: Forged, Voltaic, Celestial, Abyssal)
- **Four INFERNO-only rarities, each with the owner's own art** (5 designs per slot):
  - **FORGED** (orange), from INFERNO I;
  - **VOLTAIC** (yellow), from INFERNO III;
  - **CELESTIAL** (platinum), from INFERNO V;
  - **ABYSSAL** (inferno red, the top tier), from INFERNO VII.
  - They replace the recoloured ENCRYPTED / INFERNAL / CRYPTO tiers. Items you already have convert to FORGED / VOLTAIC / CELESTIAL.
- **Drop chance = base × INFERNO level** (owner). Boss bases per piece: FORGED 0.8%, VOLTAIC 0.7%, CELESTIAL 0.5%, ABYSSAL 0.4%. Elites drop them at a quarter of that.
  - Example, a boss on INFERNO X: 8% / 7% / 5% / 4% per piece.
  - Together the four never take more than 60%, so deep INFERNO levels still drop the rest.
- **Animated cards** in GEAR, DATA VAULT, the backpack and the shop:
  - Forged, Voltaic and Celestial: a subtle holographic sheen in the tier colour.
  - Abyssal: low flames, rising embers and a flickering red border.
- **Their own drop auras:**
  - Forged: anvil sparks;
  - Voltaic: lightning crawling up the beam;
  - Celestial: a halo and star glints;
  - Abyssal: flames and embers.
- Shop prices: Forged €9,000, Voltaic €18,000, Celestial €35,000, Abyssal €70,000 (+3% per item level).
### Pending (owner)
- The drop sound for the new tiers: pick A–D on the sound page. Until then they use the existing golden sheen sound.
### Technical
- `GearRarity` is `@Serializable`. Old names are kept on disk through `@SerialName`, and `hue` / colour filters are gone.
- `infernoBase`, `inferno` flag, `Gear.ELITE_SHARE` and `Gear.INFERNO_CAP`.
- Atlases have 4 more art tiers (5 cells each, appended).
- New `ui/common/RarityFx.kt` (`Modifier.rarityCard`). `GearIcons.drawLootBeam` gains a per-tier extra.
- New tests: `GearTest.infernoRaritiesScaleWithTheInfernoLevel` and `oldInfernoRaritiesLoadAsTheNewOnes`. New render: `GearScreenRendersWide.infernoCards` (frame sequence).

## 0.32.0 (versionCode 100) — 2026-10-14 · € carries over, equipment in the shop, INFERNO music
### Changed (owner: "€ needs to be persistent and your total be present in every run")
- **€ carries over between runs.** A run starts with every € you have banked. Whatever you earn or spend in the shop is banked as you go, so €11.5k at the end of one run means €11.5k at the start of the next. Co-op runs still start their shared wallet at €0; what you earn is banked to each player as before.
### Added
- **Equipment in the in-run shop.**
  - The shop now shows **8 items: 4 buffs and 4 pieces of equipment**.
  - Equipment is always **PURPLE or higher**, rolled on the **boss drop table**, so the rarest pieces are the least likely to be on the counter. It's at your item level (±2).
  - Prices by rarity: EPIC €1,500, LEGENDARY €4,000, ENCRYPTED €9,000, INFERNAL €18,000, CRYPTO €35,000. Each item level adds 3%.
  - What you buy goes with the run's finds: into the backpack, then the vault. In co-op each player has their own equipment on the counter.
- **INFERNO soundtrack** (the owner's 10 tracks, cover art removed, re-encoded to ~96 kbps Ogg: 62 MB → 22 MB).
  - **Inferno level music:** 4 pairs, which only play on INFERNO difficulties.
  - **Inferno boss music:** the pair from the owner's image plays for every boss on INFERNO, at normal speed, never sped up.
  - The tracks show in the in-game music player once Inferno is unlocked.
### Technical
- `RunConfig.startEuros` and `GameEngine.wallet`. A continued run subtracts what it had already banked.
- Shop equipment: `GearShopItem`, `Operative.shopGear`, `Gear.rollShopRarity/shopPrice`, `buyShopGear(For)`. The guest buys with shop index 100+.
- Co-op codec **v27**: `NetOp.shopGear/shopGearSold`.
- Music: `TrackGroup.INFERNO/INFERNO_BOSS`, `MusicTier.INFERNO` (speed 1, no phase step), `MusicLibrary.inferno/infernoUnlocked/listed`.
- New tests: `ShopTest` equipment checks and `runStartsWithTheEurosAlreadyBanked`; `MusicTest.infernoRunsPlay…`.
- New render: `shop_with_equipment`.

## 0.31.0 (versionCode 99) — 2026-10-14 · co-op shared XP and smoother partners; PC HUD
### Added / changed
- **Shared XP in co-op (100%).**
  - Before, only the host banked OP level XP and the guest earned none.
  - Now every kill and cleared room, by either player, gives both players the full XP.
- **Smoother co-op partners.**
  - Both players' screens predict the partner's movement between network updates and glide them there, instead of jumping on every packet.
  - PC co-op (Steam and direct IP) also sends updates twice as often: 20 per second from the host, 30 from the guest. Phone co-op keeps its lighter rate.
- **PC: EXIT TO DESKTOP** on the main menu.
- **PC: top HUD text twice as big**, and a little bigger again on very wide windows.
- **PC: big ability bar** along the bottom of the screen: DASH, both class spells, HP charges and Packet Bombs. Each tile shows its key, a dark sweep that shrinks as it recharges, and the seconds left in large numbers.
- **PC: KEYBINDS** in the in-game pause menu → SETTINGS.
### Not added
- **AMD FSR 3 / Intel XeSS frame generation** can't be built into this game. Those need a DirectX 12 / Vulkan game engine, and the game draws through Compose/Skia. AMD players can turn on **AMD Fluid Motion Frames** in the Radeon driver (Adrenalin → Gaming → AFMF), which works without game support. Intel has no driver-level equivalent.
### Technical
- Co-op codec **v26**: `CoopWorld.opXp`.
- Partner smoothing: `Operative.netReport/netPredict*` (velocity plus lead), `NET_FOLLOW`, `NET_SNAP`. Applied on the host (the guest's operative) and on the guest (the host's operative).
- New: `AppExit`, `PcAbilityBar`. `KeybindsCard` is now shared.
- New tests: `CoopSyncTest.xpIsSharedWithTheGuest` and `partnerGlidesInsteadOfJumping`.

## 0.30.5 (versionCode 98) — 2026-10-13 · OC Lv on the XP bar
### Changed
- After OP Lv 60, the label beside the white XP bar reads **OC Lv N** (Overclock level), and the bar fills toward the next Overclock level. Below 60 it still reads "OP Lv N".
### Technical
- `OpProgress.State.barLabel`, used for the HUD's `opLabel`. `State.label` is unchanged everywhere else.

## 0.30.4 (versionCode 97) — 2026-10-13 · phantom door, Circuit Hydra
### Fixed
- **A fake "EXIT" on the left wall.**
  - Any room with a door in its bottom (near) wall also drew a second copy of that door on the left wall, near the bottom.
  - Walking into it did nothing, and it only appeared in rooms with a bottom door.
  - It's gone; only real doors (top, side walls, bottom) are drawn.
### Changed
- **Circuit Hydra is weaker** (owner: "way too overpowered"). Every head destroyed takes **10% of the Hydra's max HP** with it, with a "−10% HEAD SEVERED" callout. Clearing three heads is −30% before you even hit the core.
### Technical
- `ArenaRenderer` draws side doors for sides 1 and 2 only.
- `BossBrain.headSevered`, with `HEAD_KILL_HP_SHARE = 0.10`.
- New `CircuitHydraTest.eachHeadDestroyedCostsTenPercent`.

## 0.30.3 (versionCode 96) — 2026-10-13 · XP bar, room shape fix
### Added
- **White XP bar** in the HUD, under CORE HP: "OP Lv 15" then a white bar filling toward the next level, with the % at the end. Past OP Lv 60 it fills toward the next Overclock level. It shows in the phone portrait HUD, the Android landscape strip and the Steam HUD.
### Fixed
- **Wide landscape rooms (the boss room too) in portrait.**
  - A run keeps the room shape it started with. A run started in landscape (or a saved landscape run continued in portrait) kept building wide rooms, which were tiny on a portrait screen.
  - On Android, every new room now follows the current screen setting (solo runs; co-op keeps one shape for both players).
- The HP readout could show more than the maximum (222/221); both numbers now round the same way.
### Technical
- `GameEngine.nextRoomWide` is applied before a room is planned (`startLevel`, `enterRoom`); `GameSession.onFrame` sets it from `PlayMode.landscape`.
- New `RoomShapeTest`.
- `XpRow` in `GameHud`. The OP label moved out of `HpRow`.

## 0.30.2 (versionCode 95) — 2026-10-13 · ×10 level XP
### Changed (owner: "these XP numbers required to level up need to be multiplied by 10")
- **Every OP level needs 10× the XP.** Level 1 → 2 is 25,000, 49 → 50 is 841,000, 59 → 60 is 1,620,000, and the whole 1–60 climb is 29,211,000 XP.
- **OVERCLOCK costs ×10 as well** (OC 1 is 6,000,000), so it stays a longer grind than the last levels.
- Kill and room XP are unchanged:
  - normal kills give 25 + 6 × monster level;
  - elites ×5 and bosses ×60;
  - a cleared room gives 60 + 15 × monster level.
- **Existing saves keep their level.** The OP XP already banked (on every character) is multiplied by 10 once.
### Technical
- `OpProgress.LEVEL_XP_MUL` (×10 on `xpToNext` and `overclockXp`). One-time `PlayerProfile.xpScaled` migration in `StoreManager.migrate`. `OpProgressTest` updated, plus the new `XpScaleMigrationTest`.

## 0.30.1 (versionCode 94) — 2026-10-13 · fixes
### Fixed
- **The Android keyboard kept closing while typing a character name.**
  - Cause: the app re-hides the status and navigation bars whenever the window focus changes, and opening the keyboard set that off, so Android shut the keyboard again.
  - Fix: the bars are left alone while the keyboard is up or a text field has focus.
  - The creator and the Data Vault search now also move up above the keyboard.
- **Packet Mage faces lined up wrong.** The Digital and Hunter faces, the antenna or crest, and the chest reactor now sit on the mage's hood opening and robe.
### Technical
- `MainActivity.hideSystemBars` skips while the IME is visible or `TextEntry.focused` is set, which the text fields set through `typingGuard`.
- `ClassFigures.MAGE_PARTS` anchors corrected.
- New render: `create_character_mage`.

## 0.30.0 (versionCode 93) — 2026-10-13 · Data Vault update, phase 2: gear loot and characters
### Added (owner: loot sheets, character creator, character / gear / Data Vault mock-ups)
- **Gear loot from elites and bosses, drawn with the owner's art.**
  - 1,030 icons were cut from the owner's 13 sprite sheets: rifles, pistols, two helmet sets, chests, gloves, two boot sets, implants and cores.
  - Each slot has its own atlas in `resources/gear/`, sorted by rarity.
  - **Slots:** PRIMARY (rifles), SECONDARY (pistols), HELMET, CHEST, GLOVES, BOOTS, IMPLANT, CORE.
  - **Rarities:**
    - COMMON, UNCOMMON, RARE, EPIC and LEGENDARY use their own art.
    - **ENCRYPTED** (orange, Inferno I+), **INFERNAL** (red, Inferno IV+) and **CRYPTO** (neon blue, Inferno VII+) reuse the golden art recoloured, with their own names and better stats. CRYPTO is close to 0%.
  - **Item level** is your OP level ±2, never above 60.
  - **Drops:**
    - Elites drop gear 25% of the time (+2% per Inferno tier).
    - A world boss drops 3 pieces per player (+1 every 3 Inferno tiers), with better odds.
    - Bosses after World 12 or Endless level 120 roll better again.
    - In co-op each player gets their own drops.
  - **Every drop stands in a light beam** in its rarity colour (taller and brighter as rarity climbs, with motes from EPIC up). Drops above BLUE play a **sheen**, and GOLDEN and up a bigger chime.
  - A HUD line names each pickup in its rarity colour.
  - **Names:** a rarity prefix plus a slot noun, for example "Solar Railgun" or "Aegis Helmet". The same art at a higher rarity gets a new name.
  - **Stats:**
    - Weapons set your damage. Each rifle's art has its own attack style: Pulse, Burst, Scattergun, Railgun, SMG, Arc, Ricochet or Twin.
    - **One rifle in five is a class weapon** only that class can equip: Tactical Carbine (Field Agent), Signal Rifle (Technician), Siege Cannon (Juggernaut), Ghost Rifle (Phantom) and Hex Caster (Packet Mage).
    - Pistols carry one of the existing arsenal weapons at a level set by the item.
    - Armor pieces roll armor, which works through the Diablo 3 formula. Cores add max HP and implants add crit.
    - Each piece also has 0–6 rolled affixes.
- **CHARACTER screen** (replaces OPERATIVE), with **OVERVIEW / GEAR / SKILLS / DATA VAULT** tabs as in the mock-ups:
  - **OVERVIEW:** name, class, level and XP bar; class traits (Juggernaut shows +100 armor, not the old Firewall); stats with gear; APPEARANCE with EDIT APPEARANCE; CHANGE CHARACTER and DEPLOY.
  - **GEAR:**
    - The operative stands between its 8 gear slots, next to the BACKPACK (60, sortable) and the item card.
    - The item card shows the main stat "vs equipped", the affixes and the attack style, with EQUIP / UNEQUIP, STORE IN VAULT, a lock and DISCARD.
    - **Drag to equip:** drag an item onto its slot (hold, then drag, on phones). You can also drag between the backpack and the vault.
  - **DATA VAULT:**
    - 1,500 slots with a fill bar and search.
    - Filters: ALL / WEAPONS / ARMOR / IMPLANTS / CORES.
    - Sort by rarity, newest, level or slot.
    - The backpack sits beside the grid, with DEPOSIT ALL and BACK TO GEAR.
  - **SKILLS:** class spells, dash, and the Overclock board.
  - Layouts for Steam / landscape and for phone portrait.
- **CREATE CHARACTER:**
  - Class list, a turning preview, and a name of 2–16 characters.
  - **HEAD / FACE / BODY / LEGS**, three options each:
    - head: class default, antenna or crest;
    - face: >_<, digital bars or hunter eyes;
    - body: default, chest reactor or chevron plating;
    - legs: default, knee lights or jet boots.
  - RANDOMIZE, then CREATE. The look shows on your operative in runs.
- **Named characters (up to 8):**
  - Each keeps its own level, Overclock, board, gear, backpack, Data Vault and saved run.
  - First launch asks for a name, and that first character keeps the progress you already had.
  - **CHANGE CHARACTER** switches between them, creates new ones and deletes old ones.
  - Co-op pairing shows the **character name** (works on Steam, direct IP and offline).
- **INVENTORY in game:**
  - A button at the **top left** under the HUD (PC: **I**) opens the CHARACTER screen over the run. Solo runs pause while it's open.
  - Gear swaps apply at once in solo. In co-op they apply on your next run.
### Technical
- Models:
  - `data/Gear.kt`: `GearItem`, `GearRarity`, `GearSlot`, `GunStyle`, `GearArt` and the `Gear` rolls, odds and apply.
  - `data/Appearance.kt`.
  - `meta/Characters.kt`: `Characters` and `GearBank`.
- Saves: `PlayerProfile` has the active character (`characterId/Name`, `look`, `equipped`, `backpack`, `vault`, `gearSeq`), with `otherCharacters` holding the rest.
- Engine:
  - `LootKind.GEAR` carries the item; drops are rolled per operative from `gearLevel`, using its own `gearRng`.
  - `Operative.gearFound` is banked by `GameSession` every 2 s.
  - `GameEngine.reequip`; `RunBuild.replaceBase`.
- Co-op: codec **v25**. Gear drops send their look, and the guest's finds travel in `guestGear`. `RoomPlayer.gear` is carried by all three backends.
- Rendering:
  - Icons: `ui/common/GearIcons.kt` (atlas loader via `loadImageResource` on both platforms, hue-rotated INFERNO rarities, `drawLootBeam`).
  - Character parts: `ClassFigures.accessories` with per-class anchors.
- Sounds: `GameSound.GEAR_SHEEN` and `GEAR_LEGENDARY`.
- Tests and renders:
  - New `GearTest` (12 tests). `StartRunFlowTest` now goes through the creator.
  - New renders: `character_gear_wide/portrait`, `character_overview_wide`, `data_vault_wide/portrait`, `create_character_wide/portrait`, `gear_floor_drops`.

## 0.29.0 (versionCode 92) — 2026-10-13 · INFERNO I–X
### Added (owner: "10 new difficulty levels after hard")
- **Ten INFERNO difficulties past Hard.**
  - **Inferno I** unlocks by beating World 15's boss on **Hard**. Each next tier unlocks by beating World 15's boss on the tier before.
  - The picker shows your unlocked tiers, plus the next one locked with what unlocks it. The co-op lobby offers the host's unlocked tiers.
  - Each tier gives ×1.5 threat HP and ×1.25 damage over the last, with higher rewards and better loot luck. Inferno X is ×124 HP and ×13 damage over Normal-level threats.
  - Inferno runs count as Hard for records and class skins.
- **The hell look in Inferno runs:**
  - **Hell themes** (Blood Servers, Hellfire Forge, Crimson Cathedral, Void Abyss, Crypto Hell, Ash & Ember) in reds, crimson, hellfire orange (Encrypted), void purple and neon blue (Crypto).
  - Rooms are darker, with a red-edged vignette, and every server light, pylon and fixture burns in the room's hell colour.
  - About 1 room in 8 keeps a normal look.
  - **Claw marks** gouged into the floor, **broken server towers** (tilted, smashed glass, sparking), and **fire licking up the walls**.
  - **Meaner monsters:** bosses are darkened toward blood red with a flickering flame aura and rising embers, and every threat is dragged toward red.
### Technical
- New `InfernoTest`. New renders: `inferno_room`, `inferno_boss`.

## 0.28.0 (versionCode 91) — 2026-10-13 · Data Vault update, phase 1: levels and difficulty
Owner: "the game is too easy again… this needs to be a slow grind". The full plan is in `docs/DATA_VAULT_PLAN.md`.

### Added
- **OP Lv 1–60** on Diablo IV's curve.
  - XP per level is ⌊(L+9)² × 25⌋ up to 49, then flat steps to 60; about 2.9M XP in all.
  - XP comes from every kill (elites ×5, bosses ×60) and every cleared room.
  - Each level adds +4% base HP and +3% damage.
- **OP Lv shows in the game**, next to the HP bar ("OP Lv 24"; "OP Lv 60 · OC 12" past 60).
- **OVERCLOCK 1–300** after OP Lv 60 (the Paragon equivalent).
  - It's a slow climb, and each level costs 3% more from OC 200.
  - The last level costs about 40× the whole 1–60 climb.
- **The UPGRADES screen is now the OVERCLOCK board.**
  - It unlocks at OP Lv 60.
  - Every Overclock level is one point, and each upgrade level costs 1 point.
- **Monster level:** your level (plus a quarter of Overclock), or how deep the run is, whichever is higher.
  - Enemies gain +6% HP and +4.5% damage per level (was +2% and +1.2%).
- **Armor uses Diablo 3's formula:** damage cut = Armor / (Armor + 50 × monster level).
  - Armor equal to 50× the monster level cuts 50%; double that cuts 66.7%.
  - Armor upgrades now give flat armor (Encryption Armor +60, Titanium Chassis +150, Juggernaut +100, board ARMOR +25 per level).
### Removed
- **Firewall**: the whole upgrade chain (Firewall, Reinforced, Adaptive, Zero Trust Fortress), the board's Firewall Strength, the Juggernaut's 60 Firewall, the dome and the FW readout. It's HP and potions only.
- **€ wipe (one time):** everyone's € and the upgrades bought with € are reset. € is only spent in the in-run shop now.
### Technical
- Co-op VERSION 24 (the Firewall field is gone). Both players need 0.28.0.
- New `OpProgressTest` covers:
  - the curves, the 40× Overclock and chip slots 5→15;
  - armor at 50% and 66.7%;
  - the one-time wipe;
  - kill XP and enemy scaling.
- `NoRegenTest` updated.

## 0.27.0 (versionCode 90) — 2026-10-12 · The world server
### Changed (owner: "a server that glows in the center of the room after the boss is defeated")
- **After a world boss falls (Worlds 1–14), a glowing server rises in the middle of the room.**
  - It has blinking lights, a light beam, and a floor ring that spins faster as you get close.
  - It replaces the old gate to the next world on the far wall.
- **Walk up to it:** a **▶ MOVE TO NEXT WORLD** button appears.
  - **Solo:** pressing it moves on to the next world.
  - **Co-op:** pressing marks you ready, and both screens show **"1/2 PLAYERS READY TO MOVE TO NEXT WORLD"**. When both have pressed, you go together.
  - **Walking away from the server, or going into the shop, cancels that player's ready** on both screens.
- The server is there whenever you're in a beaten boss's room: right after the fight, back from the shop, after walking back in, and after loading a save.
- The door hint reads "WORLD SECURED · WALK TO THE SERVER IN THE CENTRE FOR WORLD n".
### Technical
- Co-op VERSION 23. Both players need 0.27.0.
- Tests:
  - `WorldMapCampaignTest` uses the server, including after the shop.
  - New `CoopWorldServerTest`: 1/2 ready, walking away cancels, both press and move on.
  - New render `world_server`.

## 0.26.3 (versionCode 89) — 2026-10-12 · Fix: stuck after the shop in a boss room
### Fixed (owner: "World 1 boss defeated, go to shop, after shop there is no way to move to the next world")
- **Coming back from the shop into a beaten boss's room lost the gate to the next world**, so the run could not continue.
  - The gate was only added at the moment the boss fell. Re-entering the room rebuilt its doors without it.
  - The same happened when walking back into a cleared boss room from room 10.
- The gate is now there whenever a beaten world boss's room is entered.
- New test `WorldMapCampaignTest.theShopAfterAWorldBossStillLeadsToTheNextWorld`.

## 0.26.2 (versionCode 88) — 2026-10-12 · Slim Android landscape HUD
### Changed (owner's mock-up: "not a fan of the massive black box at the top; the boss health bar is still way too big")
- **The Android landscape HUD is a slim strip**, about 12% of the screen height (was about 26%).
  - Row 1: `WORLD 1 · BOSS | score | € …`.
  - Row 2: `HP [bar] 292/292 FW 175`.
  - Row 3: small upgrade chips (two-thirds size) and the build number.
- **The boss bar** sits on the right: `NAME · PHASE · %` on one line over a thin bar with the phase marks.
  - The big box, the subtitle and the boss tag are gone.
  - The entrance animation and the status lines (shell, ring, shield) still show, in small text.
- The threats bar sits in the same place in normal rooms.
- The build number moved from the corner into the strip during landscape games.
- The Steam/PC strip is unchanged; it keeps its key chips.
### Technical
- New renders: `landscape_hud_boss`, `landscape_hud_room`.

## 0.26.1 (versionCode 87) — 2026-10-11 · PC co-op by IP address
### Added (owner: "send my friend an .exe and let them join via IP address")
- **DIRECT IP CO-OP on PC when Steam isn't running.**
  - One player presses HOST, and the screen shows their IP address and port (TCP 47615).
  - The other types that address and presses JOIN. Both then land in the normal co-op lobby.
- **How it connects:** a plain TCP connection, with no accounts or servers.
  - Same network: it works as is.
  - Over the internet: the host forwards TCP 47615, or both of you use a virtual LAN such as Tailscale, ZeroTier or Radmin VPN.
- The desktop README explains how to zip the game folder and send it to a friend.
### Technical
- `lan/DirectIpCoopBackend`: messages are framed as type, length and payload. A writer thread sends, and a newer world snapshot replaces one not yet sent. A reader thread receives.
- New `CoopBackend.directIp`, `hostDirect` and `joinDirect`. They do nothing on Android.
- New desktop test `DirectIpCoopTest`: a real localhost socket carries the lobby, START, a 20KB snapshot, input and leaving.

## 0.26.0 (versionCode 86) — 2026-10-11 · Co-op shop
### Added (owner: "both players have a separate shop, and both teleport in and out of shop together")
- **The shop now appears in co-op** (it was solo-only before). It is the same 1-in-20 side gate after a room is cleared.
- **In together:** either player walking into the shop gate brings both players into the shop.
- **A shop each:**
  - Every player gets their own 4 items, rolled from their own build, with their own SOLD marks.
  - Run € is the shared pool, as everywhere in co-op.
  - The guest's purchases are sent to the host, which runs the game, and each player sees their own stock.
- **Out together:**
  - Walking into the exit gate marks you ready, and you wait there.
  - Your partner sees "‹NAME› FINISHED SHOPPING · READY TO LEAVE 1/2 · WALK TO THE GATE"; you see "WAITING FOR ‹NAME› · READY TO LEAVE 1/2".
  - When both stand in the gate, you leave together. Stepping out of the gate cancels your ready.
- In co-op the "skip the shop?" question isn't asked. Walking out the exit door skips the shop, as the door hint says.
### Technical
- Co-op snapshot VERSION 22, and the guest input gains shop purchases. Both players need 0.26.0.
- New `CoopShopTest`. It runs a host and a mirrored guest through the codec: in together, separate stock, guest purchase, 1/2 wait, out together.

## 0.25.9 (versionCode 85) — 2026-10-11 · No regeneration; MAP button kept clear
### Removed (owner: "making the game way too easy… with the new potions these features will no longer be needed")
- **SELF-REPAIR** (HP over time) is gone from the game. A saved run that had it simply drops it.
- **Firewall no longer recharges over time.**
  - It still refills fully when a room is cleared, and ADAPTIVE FIREWALL kills still restore 8%.
  - REINFORCED FIREWALL is now just "Firewall +50%" (its "recharge 1s sooner" is gone). ADAPTIVE FIREWALL's faster recharge is gone.
  - Texts no longer say "recharging".
- The stat sheet no longer lists "Regen" or "Firewall recharge delay".
### Changed (owner)
- **Settings → TOUCH CONTROLS:** the MAP button's corner (top right) is marked "MAP (KEEP CLEAR)".
  - Buttons dragged into it are pushed back out.
  - In play, a layout saved there earlier is moved out too, so nothing can cover MAP.
### Technical
- New tests: `NoRegenTest`, `TouchLayoutMapZoneTest`.

## 0.25.8 (versionCode 84) — 2026-10-11 · Shop window you can close
### Changed (owner: "the shop screen is so large, players cannot close it")
- At the vendor's counter an **OPEN SHOP** button appears. The shop no longer pops open by itself.
- The shop is now a **small square window in the middle of the screen**.
  - It is at most 90% of the width and 80% of the height, capped at 380dp, so it fits Android portrait, Android landscape and the Steam window.
  - The item list scrolls inside it.
- **✕ at the top right** closes it. So do Back (Android), Esc (PC) and walking away from the counter. Back and Esc close the shop before pausing.
- The shop's "RECEIVING MESSAGE…" text and the pickup line no longer draw over the open window.
### Technical
- New renders: `shop_window_portrait`, `shop_window_landscape`. `upgrade_shop` now shows the OPEN SHOP button.

## 0.25.7 (versionCode 83) — 2026-10-11 · Room slide follows your direction; hint and pickup text separated
### Changed (owner)
- **The room-change slide follows the door you walk through** (it used to always slide the same way):

  | Door | Old room slides | New room comes in from |
  |---|---|---|
  | North | down | above |
  | South | up | below |
  | West | right | the left |
  | East | left | the right |

  - The side shop gate slides the same way, depending on which wall it's on. The gate to the next world counts as north.
  - Co-op partners see the same slide.
### Fixed (owner)
- **The NEXT ROOM / DOOR OPEN hint and the item-pickup line no longer overlap.** The pickup line now sits below whatever hints are showing.
- **The hints no longer run into the MAP button** on narrow portrait screens. They stay centred and wrap if needed.
### Technical
- Co-op snapshot VERSION 21: carries the slide direction.
- New `WorldMapCampaignTest.roomSlideFollowsTheDoorYouWalkThrough`.
- New `hint_and_pickup` screenshot render.

## 0.25.6 (versionCode 82) — 2026-10-11 · Skin pack ◇1400
### Changed (owner)
- **The ALL SKINS pack now costs ◇1400** (was ◇300). It is now called **ALL COLOUR SKINS**.
- The pack covers the 17 ◇100 colour skins only.
  - It never includes premium skins: GOLD, NEON, or any later skin priced above ◇100.
  - The shop now says so.
- Earned skins (TITANIUM, PURPLE, GLITCHED) are not sold, as before.
- Anyone who already bought the pack keeps everything.

## 0.25.5 (versionCode 81) — 2026-10-11 · Skins named by how they look
### Changed (owner: "most of them are related to CyOps TD and confusing")
- Shop skins renamed by their colour. Owned and equipped skins carry over, since the ids are unchanged.

  | Old | New |
  |---|---|
  | FIREWALL | MINT |
  | IDS | CYAN |
  | BLUE HAT | ROYAL BLUE |
  | ANALYST | YELLOW |
  | CRYPTOGRAPHER | DEEP PURPLE |
  | ZERO-DAY HUNTER | ORANGE |
  | RED HAT | RED |
  | ACE | WHITE |
  | ANTI DUCK HOLOGRAM | LIME HOLOGRAM |
  | SPECTRUM | RAINBOW |
  | REACTOR | AMBER |
  | MERIDIAN | SAND & VIOLET |
  | GLACIER | ICE BLUE |
  | MAINFRAME | GREEN |
  | CASCADE | EMERALD |
  | NEONGRID | ELECTRIC BLUE |
  | VOID | VIOLET |

- Subtitles now describe the look ("Colour skin", "Pulsing glow", "Cycles through every colour", …) instead of the CyOps TD agent names.

## 0.25.4 (versionCode 80) — 2026-10-11 · Fix: crash when a run starts
### Fixed
- **The game crashed the moment a run started** (PLAY or ENDLESS, any difficulty, portrait or landscape).
  - Floor loot pops in from size 0. On a chip's very first frame, its glow had radius 0, and Android refuses that ("ending radius must be > 0").
  - Every run now begins with Starting Weapon Power chips dropped around you, so every run hit it.
  - Found through the 0.25.3 crash reporter, on a Galaxy S24+ (Android 16).
- Loot now pops in from a tiny size instead of 0.
- All 51 glow effects in the game use a safe gradient that clamps the radius, so no glow can crash this way again.
### Technical
- New `ZeroRadiusGlowTest`:
  - It reproduces Android's error.
  - It checks that safe glows never throw.
  - It fails the build if a raw radial gradient is added again.

## 0.25.3 (versionCode 79) — 2026-10-11 · Crash reporter for the landscape START crash
### Added
- **Crash reporter (Android).** If the game crashes, the next launch shows what went wrong, with COPY and CONTINUE buttons.
- **Readable release crash reports.** Release builds keep class and method names and line numbers. Play Console's Android vitals traces become readable too.
### Investigating (owner: "crashes on START in landscape, both modes")
- No reproduction yet in the unit and UI tests: every class and difficulty, Android 8–15, phone landscape sizes, fresh and veteran profiles, and the full tap flow.
- The fix needs the crash report from a real device.
### Technical
- New start-of-run regression tests: `StartRunCrashTest`, `StartRunAndroidTest`, `StartRunFlowTest`.

## Unreleased
### Added
- **PC debug test build:** `.\gradlew :desktop:run -PdebugBuild`, or
  `:desktop:createDistributable -PdebugBuild` for a folder.
  - Test ◇ are free in the store while Steam is closed.
  - It uses a separate `CyberOperative-Debug` save.
  - The window title shows [DEBUG].
  - The Steam release build refuses the flag.

## 0.25.2 (versionCode 78) — 2026-10-11 · Campaign and premium skins; PROXY SHOT removed
### Added (owner)
- **PURPLE skin:** earned by beating World 15's boss, and unlocks for **every class**.
  - It used to unlock at level 100 on Hard. Anyone who already earned it that way keeps it.
- **GLITCHED skin:** also earned by beating World 15's boss, for every class.
  - Slowly runs through every colour, like a glitched boss but slower, with a brief glitch jump every couple of seconds.
- **GOLD skin, ◇1000:** polished gold with a light that sweeps across it.
  - No gold skin existed before, so nothing needed converting.
- **NEON skin, ◇1000:** pick any colour from a wheel of bright neon colours (SKINS → NEON → COLOUR, then DONE).
  - It glows and breathes.
  - Your co-op partner sees the colour you picked.
- GOLD and NEON are not part of the ◇300 ALL SKINS pack.
### Removed (owner)
- **PROXY SHOT** (the rear shot) is gone from the game.
  - A saved run that had it simply drops it.
### Technical
- New `CampaignPremiumSkinsTest` (5 tests).

## 0.25.1 (versionCode 77) — 2026-10-11 · Boss loot sprays out; co-op loot is per player
### Changed (owner)
- **Boss loot sprays out of the boss** instead of landing in a pile.
  - One piece every 0.1 s, in a random direction, sliding a short way (70–170 units) and staying inside the room.
  - This covers chips, weapon cards, HP charges and Packet Bombs, including the room-clear chip.
  - Bosses can still drop weapons: every drop is rolled from the full pick pool.
- **Co-op loot is per player.**
  - Chips, weapon cards, HP charges and Packet Bombs are rolled separately for each player, from that player's own build.
  - A cash bag belongs to whoever was closest to the kill. Run € is still shared.
  - You see your own drops normally. Your partner's drops show only as a faint glow in the item's colour (green HP, cyan bomb, gold cash, rarity colour for chips): no icon, no beam, and you can't pick them up.
  - When you leave a room, anything left on the floor goes to its owner.
  - Starting Weapon Power chips belong to the player who brought them.
- **SELF-REPAIR (heal over time) nerfed to 0.1% HP/s.**
  - It now has one level only.
  - It still only heals while the room has enemies in it.
- **The purple EMP "dark-zone emitter" decoration is now an overhead strip light.**
  - Flat housing, warm-white flickering tube and a soft floor spill.
  - No ring, no purple, nothing round, so it no longer looks like a drop.
### Fixed
- Several room-clear chips granted at once used to land on the same spot. They now fan out.
### Technical
- Co-op snapshot VERSION 20: each loot entry carries its owner.
- New tests in `BossLootSpewTest`:
  - The spray comes out one piece at a time, at least 0.1 s apart, and ends spread out.
  - Co-op ownership: the partner can't take the host's chip, and the owner can.
  - Loot ownership survives the co-op codec.

## 0.25.0 (versionCode 76) — 2026-10-11 · The world-map CAMPAIGN; Endless is the old regular game
### Added (owner: the campaign plan)
- **PLAY is now the CAMPAIGN: 15 SERVER WORLDS.** Each world is 10 rooms, then a boss in room 11.
  - You still pick the difficulty.
  - Difficulty follows the old level curve: World *w* Room *r* is about level (w−1)×10 + r, so
    World 15's boss is the old Level 150.
- **Procedural maps.** Every world's map is generated from the run's seed when the run starts.
  - Rooms are laid out on a grid as a winding path, so doors face north, south, east or west.
  - You walk in on the side you came from.
  - Every room keeps a doorway clear and walkable on all four walls.
  - Doors stay shut until the room is clear.
  - You can walk back into rooms you've cleared; they stay empty and their doors stay open.
- **The boss door** (room 10, always the far wall):
  - A big gate with a glowing skull, in a taller dark-red wall with spikes and pulsing seams.
  - It's a hand-drawn vector skull, because CoreUI's free set has no skull icon.
- **World bosses** drop at least 4 boosted upgrade chips. A gate to the next world then opens.
- **The Nullshade Specter** never appears before World 12. It can be World 12, 13 or 14's boss
  (30%) and is always World 15's.
- **The shop:** the same 1-in-20 side door. Leaving it puts you back in the room you entered
  from, at the shop's doorway, and you carry on along the map.
- **CAMPAIGN COMPLETE after World 15:**
  - Continue in ENDLESS: no map, WORLD ??? · ROOM ???, doors anywhere, from level 151.
  - Or end the run as a win.
- **[M] world map**, see-through, with the game still running underneath. Also on controller
  BACK/VIEW and a see-through MAP button on Android.
  - It shows the world's layout, cleared rooms, where you are (pulsing), the next room and the boss.
  - MAP is rebindable.
- **HUD and hints:**
  - The HUD shows "WORLD 3 · ROOM 7" / "WORLD 3 · BOSS" (Endless: "WORLD ??? · ROOM ???").
  - The door hint names the next room and its wall ("DOOR OPEN · ROOM 4 · RIGHT WALL ▶").
  - Banners announce each world and room.
### Changed
- **ENDLESS is the old regular game** (levels forever, boss every 10, random exit doors). The old
  "one room, nonstop" Endless is removed (code, data-XP picks and banners).
- **Saves (format 2)** remember world, room, cleared and visited rooms.
  - Old saved runs CONTINUE as Endless.
  - Old one-room Endless saves are dropped.
- **Records:**
  - Highest level, best score and class-skin unlocks count in both modes.
  - Endless keeps its own best level.
  - The campaign tracks your best world and campaigns completed.
- **Co-op** plays the campaign map too (wire v19 carries world/room).
### Fixed (owner: "DELETED chance for elites should always be 0%")
- **EXPLOIT CHANCE never deletes elites any more** (nor bosses, as before). The card says so.
- New tests: `WorldMapCampaignTest` (9: maps, level curve, doors and revisits, shop return, boss
  drops and the next world, Nullshade rules, campaign complete into Endless, CONTINUE, old saves)
  and `ConsumablesTest.elitesAndBossesAreNeverDeleted`.

## 0.24.1 (versionCode 75) — 2026-10-11 · LEVEL-UP REROLLS retired and refunded
### Changed (owner: option 1)
- **LEVEL-UP REROLLS** only redrew the card screen, which no longer exists. It's gone from the
  upgrades list.
- Players who bought it get back every € they spent on it, once, the first time the game loads
  their save. It doesn't show up anywhere afterwards.
- New test: `ConsumablesTest.retiredRerollsAreRefundedOnce`. Two older tests updated for the
  retired upgrade.

## 0.24.0 (versionCode 74) — 2026-10-11 · One way to play: drops everywhere, no SYSTEM UPGRADE screen
### Changed (owner: "Remove PC mode. Cash, card and chips drop on both modes always" · "SYSTEM UPGRADE screen is no longer a feature but a hassle")
- **PC MODE is gone.** Every run on every platform (phone portrait, phone landscape, Steam, solo
  and co-op) uses the drop rules:
  - upgrade chips and weapon cards drop from threats;
  - cash bags;
  - HP charges and Packet Bombs;
  - dash and class spells, with on-screen buttons on Android;
  - exit-door choices.
- **The SYSTEM UPGRADE card screen is removed from the game.**
  - Starting Weapon Power's free picks now drop as chips in a ring around you.
  - A saved run from an older version that was paused on the card screen gets those picks as
    chips when you CONTINUE.
- **Android Settings → SCREEN: PORTRAIT / LANDSCAPE** replaces PC MODE. JOYSTICK (AUTO / FREE
  FLOAT / LOCKED) and TOUCH CONTROLS LAYOUT are unchanged.
- **Phone co-op** now also uses the drop rules, with portrait-shaped rooms so both phones build
  the same rooms.
- **Permanent upgrade text:** Starting Weapon Power reads "free upgrade chips"; Upgrade Quality
  reads "upgrade drops".
- **The upgrade shop is unchanged**, and it's still the place where you choose an upgrade.
- New test: Starting Weapon Power drops chips, no card screen.

## 0.23.1 (versionCode 73) — 2026-10-11 · The shop is back under PC rules
### Fixed
- **PC rules (Steam, Android PC MODE): the upgrade shop's side door never opened.** Its 1-in-20
  roll only happened after the card screen, which the PC rules don't open any more. It now rolls
  when the room clears. Same odds, solo campaign only, as before.
  - New test: `ConsumablesTest.theShopStillTurnsUpWithChips`.

## 0.23.0 (versionCode 72) — 2026-10-11 · Upgrade chips, weapon cards, Android PC MODE
### Changed (owner: "change the SYSTEM UPGRADE thing. Players hate it")
- **PC rules (Steam, and Android PC MODE): no more card screen.** The power-ups a room would have
  given drop from threats instead.
  - Each normal room plans its pick(s) and spreads them over its kills. Anything still owed at the
    clear drops where the last threat fell. Bosses drop their picks where they die. Endless drops
    them as you earn them.
- **Drop styles.** All of them stand in a light beam in the rarity colour (taller for rarer;
  GOLDEN and TITANIUM have a sweeping sheen).
  - Power-ups are a **computer chip** in their rarity colour.
  - Weapons are **the shop's card**, with a glow halo above.
- **Pickup:** walk over a drop to take it.
  - A line under the HUD names it in its rarity colour, with no dark panel and a tight outline for
    legibility.
  - PURPLE and up glow; GOLDEN and TITANIUM shimmer.
- **Drops are not pulled in when the room clears.** Ones you leave are collected as you go
  through a door.
- **A new weapon with every slot full:**
  - During the wave you just walk over it.
  - Once the wave is over, stepping on it opens the swap screen.
  - Keeping your weapons throws it to the middle of the arena, never on top of another pickup.
- **Exit doors** now follow the PC rules, not the room shape, so they work in Android PC MODE in
  portrait and landscape.
### Added (Android)
- **Settings → PC MODE** (off by default; starts with your next run):
  - Turns on the PC game: chips, cash bags, HP charges, Packet Bombs, dash, class spells and exit
    doors.
  - **LANDSCAPE:** the whole app turns sideways. Rooms are wide and zoomed out, the HUD strip sits
    above the arena, the joystick is locked bottom-left and the buttons sit bottom-right.
  - **PORTRAIT:** the phone HUD, with the buttons bottom-right.
- **On-screen buttons, always see-through:**
  - DASH, both class SPELLS (their icons), HP CHARGE (potion, with count) and PACKET BOMB (bomb,
    with count).
  - Spells and dash show a cooldown sweep and seconds remaining.
  - Each button fires the moment it's touched, so it works with a thumb already on the joystick.
- **JOYSTICK: AUTO / FREE FLOAT / LOCKED.** AUTO is locked in landscape and floating in portrait.
- **TOUCH CONTROLS LAYOUT:** drag the joystick and every button where you want them (saved for
  each orientation); RESET restores the defaults.
- A compact landscape menu for phones held sideways, and a touch version of the briefing.
- Each run keeps the shape and rules it started with (saved for CONTINUE), and phone co-op always
  uses the phone rules so both players match.
- Narrow rooms on a wide screen are centred instead of zoomed in.
- Co-op wire v18: upgrade chips carry their power-up.
- New tests: upgrade chips (no card screen, drops not pulled in at the clear, walk over to take)
  and the weapon-chip swap / throw-to-the-middle.
- New renders: `docs/screenshots/pc_mode_*`.

## 0.22.0 (versionCode 71) — 2026-10-11 · PC: exit doors, neon cursor, dash, class spells
### Added (owner, PC build)
- **Up to 4 exit doors** open when a room is cleared.
  - The top-centre gate is always one. Extra doors go on the far wall and the side walls (green
    EXIT doorways), never on the shop door's wall.
  - Every door is checked to be clear and walkable.
  - Any door leads on, and the next level is rolled exactly as before. The shop door keeps its
    1-in-20 chance.
- **The owner's neon arrow cursor**, traced as vectors, is the system cursor everywhere; you aim
  with its tip. With a controller, the same arrow marks where the right stick aims.
- **Space: dash.** A short, quick dash where you're heading, with a moment of invulnerability and
  a 1.1s cooldown. Controller: A.
- **R / F: class spells** (controller: X / Y). Damage scales with your damage. Draft designs, open
  to tuning:

  | Class | R | F |
  |---|---|---|
  | Field Agent | Overclock: +60% fire rate for 5s | Frag Grenade: a blast at the cursor |
  | Technician | Missile Barrage: 8 homing missiles | EMP Field: big pulse that wipes enemy shots |
  | Juggernaut | Fortify: −60% damage taken for 4s, firewall refilled | Ground Slam: shockwave with knockback |
  | Phantom | Shadow Step: blink toward the cursor | Smoke Veil: cloak now, for 4s |
  | Packet Mage | Firestorm: fire rains on the cursor area | Arcane Nova: ring of homing fire bolts |

- **HUD strip:** dash and spell chips with key names and cooldown countdowns.
- **KEYBINDS:** dash and both spells can be rebound.
- **Co-op:** carries dash and spell uses (wire v17).
- New tests: `AbilitiesTest` (dash, every class's two spells, frag at the cursor, shadow step,
  co-op serials) and exit-door tests in `WideArenaTest`.

## 0.21.0 (versionCode 70) — 2026-10-11 · PC: HP charges, Packet Bombs, cash bags, keybinds
### Added (owner, PC build)
- **Q: HP charge packet.** Heals 35% of max HP.
  - You start each run with 3 and can carry up to 9.
  - Drops on the floor as a green potion flask: from elites 8% of the time, from bosses 50%.
  - Controller: LB.
- **E: Packet Bomb.** A big blast around you (radius 420) that also wipes enemy shots.
  - You start with 0 and can carry up to 3.
  - Drops as a small lit bomb with a pulsing cyan ring: from elites 2%, from bosses 25%.
  - Controller: RB.
- **Kills pay € only as cash bags on the floor.**
  - Walk near a bag to collect it.
  - When the room is clear, every bag and pickup streams into the operative.
  - Anything left when you leave is collected automatically.
- **HUD strip:** shows `[Q] ✚ n` and `[E] ✹ n` with your current keys.
- **Settings → KEYBINDS (PC):** rebind move, HP charge, Packet Bomb and pause.
  - Click an action, then press the new key. A key already in use swaps places.
  - RESET TO DEFAULTS restores the default keys.
  - Arrow keys always move and Esc always pauses.
- **Co-op:** syncs the uses (serials), floor loot and each player's counts (wire v15).
### Changed (PC)
- **SELF-REPAIR regeneration:**
  - It now starts at 0.6% HP/s and adds 0.05% per level after that. Before, it was 0.6% per
    level, so stacked levels reached 6% and more.
  - It no longer heals once the room is clear with no threats left.
- Phones are unchanged.

## 0.20.0 (versionCode 69) — 2026-10-11 · Cyber Operative for Steam (PC + Steam Deck)
### Added (owner: "build it for Steam… all purchases through the Steam store… Co-Op through Steam", "move with WASD and aim with mouse", "full landscape view… premium", "Steam Deck… left joystick move, right joystick fire")
- **New `desktop` module: the Steam build** (Windows; runs on Steam Deck through Proton).
  - It compiles the same game code as the phone app.
  - Only a small platform layer (`platform/`) differs.
  - See `desktop/README.md`.
- **Landscape, zoomed out.**
  - PC rooms are 2160 units wide (three phone rooms) and about one screen tall.
  - Every generated room, hand-made layout, the shop and a new wide boss stage are built for 16:9.
  - Rooms hold 1.6× the threats.
- **PC HUD: a strip above the arena, never over it.**
  - Left: level, score, € and HP (and the co-op partner).
  - Middle: the boss bar, or threats and the event.
  - Right: clock and pause.
  - Buffs run on a second line.
  - The arena is drawn only below the strip.
- **Landscape main menu, no scrolling:**
  - A small badge and wordmark, with every button underneath.
  - Your operative on a holo stage on the left.
  - Records and controls on the right.
- **Other PC screens:**
  - Sub-screens sit in a centred column.
  - The boot terminal is a framed console.
- **Controls:**
  - WASD or arrows move.
  - Hold a mouse button to fire at the cursor while moving, with a crosshair.
  - Esc pauses; F11 toggles fullscreen.
- **Controllers** (Xbox, PlayStation, Switch and Steam Deck through SDL):
  - Left stick moves; the right stick aims and fires while moving.
  - START pauses and B backs out.
  - Any button continues the briefing.
- **Wording:** PC text says "click" instead of "tap"; the briefing explains keyboard, mouse and
  controller.
- **Steam store:** ◇ packs are Steam Inventory items (`steam/itemdefs.json`). BUY opens the Steam
  overlay checkout, and ◇ are credited only after Steam grants the item and the game consumes it.
- **Steam co-op:** your Steam friends list, lobby invites (in-game or from the overlay), and
  peer-to-peer play over Steam's relay. The wire format v14 carries mouse/stick aim.
- New tests: `WideArenaTest` checks wide rooms (generated, hand-made, boss, shop) are inside the
  floor, clear at spawn and gate, and walkable, and that a wide boss fight runs.

## 0.19.4 (versionCode 68) — 2026-10-11 · Full-screen, fewer accidental Backs
### Changed (owner: "hide the bottom home and back buttons… swipe up to see them again… too easy to accidentally press back")
- **Full immersive mode on every screen.** The status bar and the bottom navigation bar (home, back,
  recents) are hidden.
  - A swipe in from the edge shows them for a moment, then they hide again by themselves.
  - They are re-hidden whenever the app comes back to the front.
  - Before, only the status bar was hidden, and only during gameplay.
- **During gameplay, the edge Back-swipe is blocked** in the steering area.
  - The game blocks the system gesture zone along the side edges: Android allows up to 200dp per
    edge, counted from the bottom, which is where the joystick thumb sits.
  - An accidental Back during a run only ever opens the pause menu.

## 0.19.3 (versionCode 67) — 2026-10-11 · Phantom cloak actually loses them
### Fixed (owner: "Enemies still track, shoot at, and move towards the player while cloaked")
- **No closing in:** threats that lose a cloaked Phantom no longer walk to the exact spot where it
  vanished, which is where a player standing still still is.
  - Each one backs straight out and circles its own point on a wide ring (170 units or more) around
    that spot.
  - Melee threats that were on top of you now drift away.
- **Dashes and charges in progress are cancelled** the moment you cloak.
- **Homing shots already in flight stop homing** and fly straight.
- **They visibly lose you:** eyes and gun barrels glance around instead of tracking you, and a
  pulsing **"?"** floats over each threat while you're hidden.
- They still fire nothing while you're cloaked. A new test guards all of this: no new shots, and
  nothing within 60 units of you while cloaked.
- The Nullshade Specter still sees through it.

## 0.19.2 (versionCode 66) — 2026-10-11 · Packet Mage staff orbit restored
### Fixed (owner)
- **Packet Mage staff:** the orbit animation around the orb is clearly visible again, in every skin.
  - Three sparks with short trails circle along a tilted ring.
  - They pass behind the orb on the back half and in front of it on the front half.
  - The ring gently rocks.
  - Before this, only a faint drifting ring and one stray dot were left after the redraw.

## 0.19.1 (versionCode 65) — 2026-10-11 · Bigger, more distinct elites
### Changed (owner: "make the elites bigger and more distinct")
- **Elites are drawn 30% larger** (`EnemySprites.ELITE_DRAW_SCALE`). The hitbox is unchanged, so
  balance is the same.
- **Every elite wears its modifier's colour** on the whole body, plus a strong colour halo on the floor.
- **A big hexagon badge with a symbol** floats above each elite. Each type also has its own frame:

  | Elite | Badge | Frame |
  |---|---|---|
  | Encrypted | padlock | double rotating hex cage |
  | Overclocked | lightning bolt | speed after-images and arcs |
  | Corrupted | glitched X | flickering trim, dripping pixels |
  | Armored | shield | silver body, thick riveted plate ring |
  | Replicating | copy | ghost clones peeling off, orbiting mini-copies |
  | Volatile | "!" | red-orange throbbing trim, hazard-stripe ring, rising embers |

## 0.19.0 (versionCode 64) — 2026-10-11 · Threat texture upgrade
### Changed (owner: "upgrade all of the enemy textures for the small ones and elites… many many different colors… sheen… keep the glitched ones")
- **Every regular threat redrawn** as a neon emblem creature (`ui/game/EnemySprites.kt`). Each has a
  dark core, a glowing trim and eyes that track you. **Textures only:** stats, AI, hitboxes and
  damage are unchanged.
- **Silhouette by strain**, 27 forms:
  - legged crawlers, segmented grubs, hex nanites, sucker-mouthed leeches;
  - winged mites, gnats and locusts;
  - armoured brutes, riveted husks, stacked golems;
  - turrets that aim at you: single, twin and fan barrels, mortars, gatlings and tripod sentries;
  - horned rams and bulls, spear lancers, scoped marksmen, hooded stalkers;
  - pulsing beacons, ringed spires, after-imaging blinkers, wavy-tailed wraiths;
  - propeller drones and segmented worms.
  - The 13 originals map onto these by how they fight.
- **Colour by code family**, a wide spread instead of all reds:
  - Polymorphic hot pink, Encrypted cyan, Botnet magenta, Ransom gold;
  - Zero-Day violet, Adware lime, Spyware orange, Cryptojack yellow;
  - Forkbomb red-orange, Keylogger teal, Backdoor crimson, Stealth indigo;
  - **Kernel-Mode cycles the spectrum.**
- **Finish by unlock depth:**
  - level 10+ adds a rotating inner ring;
  - level 20+ adds an orbiting spark;
  - level 30+ (and all Kernel-Mode) gets a **metallic sheen** sweeping across the body.
- ***GLITCHED*** threats keep hue-cycling with RGB-split ghosts and scanline tears. They now jump
  between the new body forms on a loop.
- **Elites** get their own animated frames plus a modifier badge:
  - Encrypted: a rotating hex cage;
  - Overclocked: lightning arcs;
  - Corrupted: dripping glitch pixels;
  - Armored: rotating armour plates;
  - Replicating: orbiting mini-copies;
  - Volatile: a flickering warning burst.
- The renderer avoids per-frame gradient and list allocations.
- `docs/screenshots/enemy_sheet.png` is regenerated.

## 0.18.0 (versionCode 63) — 2026-10-11 · Good Game's Siege Cannon
### Added (owner: "Good Game should definitely charge and shoot a shot out of the cannon that tracks you and does splash damage every 5 seconds")
- **SIEGE CANNON:** on top of its normal patterns, GOOD GAME fires every **5 s**. The first charge
  starts 3 s into the fight.
  - **Charge (1.2 s):** heat climbs both barrels, the muzzles swell with orange light, sparks are
    pulled in, a ring pulses around the turret and it growls.
  - **Shot:** a muzzle flash, a screen kick and a heavy iron shell that **tracks you**. It turns at a
    dodgeable rate and shows its blast-radius ring on the floor.
  - **Splash:** the shell bursts (radius 110) when it hits you, a wall or an Encryption Blade, or when
    it burns out after 4.5 s. Every operative inside the blast takes the damage.
  - A cloaked Phantom gives it nothing to aim at, so the charge waits.
- Hostile shells can now carry splash (`Projectile.splash` on enemy shots, `GameEngine.hostileBlast`).
- Co-op wire format is now **v13**: the cannon charge is synced so the guest sees the wind-up.

## 0.17.6 (versionCode 62) — 2026-10-11 · Cobalt Titanium for Phantom and Packet Mage
### Changed (owner)
- The **Phantom** and **Packet Mage** wear their TITANIUM (Normal L100) skin as **cobalt blue**:
  cobalt plates with light-blue trim. Field Agent, Technician and Juggernaut keep the gray-metal
  TITANIUM.

## 0.17.5 (versionCode 61) — 2026-10-11 · Mage hood clears the face
### Changed (owner)
- **Packet Mage:** the hood's right side now curves outward past the octagon face opening, so the
  outline never touches the visor.

## 0.17.4 (versionCode 60) — 2026-10-11 · Smaller Mage hood, octagon opening
### Changed (owner)
- **Packet Mage:** the whole hood is shrunk to 80% around the collar, keeping its back-leaning
  peak. The round opening is replaced by an octagon that hugs the >_< face visor.

## 0.17.3 (versionCode 59) — 2026-10-11 · Packet Mage hood reshaped
### Changed (owner)
- **Packet Mage:** back to the smooth peaked hood, now with the whole top leaning well back like a
  bent cone, replacing the 0.17.2 hooked tip.

## 0.17.2 (versionCode 58) — 2026-10-11 · Gray-metal Titanium, bent Mage hood
### Changed (owner)
- **TITANIUM is now gray metal on every class.** It was near-white:
  - gunmetal plates with steel-grey edges;
  - a new `HeroPalette.TITANIUM_METAL` for the Technician's hood;
  - the skin's own colours were retuned to match.
  - The titanium shopkeeper is unchanged.
- **Packet Mage:** the tip of the peaked hood now bends sharply back, with a glowing seam along the
  bend.

## 0.17.1 (versionCode 57) — 2026-10-11 · Class bodies redrawn from the concept sheet
### Changed (owner: "make them look as close to this as possible… Technician looks fine")
- New vector bodies for four classes (`ui/game/ClassFigures.kt`), traced from the owner's concept sheet:
  - **FIELD AGENT:** the icon hood with headset and antenna on a crouched, armoured rifleman. It has
    knee and shoulder plates, a glowing chest bar, a neon-trimmed rifle that tilts with your aim,
    and a trailing scarf.
  - **JUGGERNAUT:** a hulking frame with a round shoulder pad and glowing ring, a gauntlet with three
    lit ports, glowing V-seams, a visor helmet and a tall tower shield on the facing side.
  - **PHANTOM:** a swept-back hood, a tattered cloak that dissolves into pixels, a crouched hover,
    twin daggers and floating diamond shards.
  - **PACKET MAGE:** a tall peaked hood, a long robe with a light front panel, glowing hem runes, a
    chest gem, floating code cards and a staff whose orb and ring follow the skin colour.
- Skins follow the concept:
  - Default: dark plates with neon-green trim (the Field Agent keeps the blue icon hood).
  - Titanium: light titanium plates with cool white trim.
  - Purple: dark plates with violet trim.
- The Technician is unchanged. The old agent, mech and runner drawings were removed.

## 0.17.0 (versionCode 56) — 2026-10-11 · Classes
### Added (owner)
- **Five classes**, picked on the OPERATIVE screen (Field Agent listed first). The body follows the
  class, and every class is free.
  - **FIELD AGENT** (base, agent body): well rounded, +20% damage, standard speed.
  - **TECHNICIAN** (the former NEON OPERATIVE body):
    - +10% attack speed and +10% move speed;
    - built-in **SIGNAL BEAM**: a beam through walls every 2 s.
  - **JUGGERNAUT** (mech body):
    - ×2 max HP, starts with 60 Firewall, −10% move speed;
    - built-in **BRUTE FORCE**: a slow, heavy slam around you every 2.6 s.
  - **PHANTOM** (runner body): turns invisible for 3.5 s out of every 10 s.
    - Threats lose track of you: no attacks or contact damage, and they drift to where you vanished.
    - Bosses won't start new attacks or chase you.
    - The **Nullshade Specter still sees you**.
    - A floor arc shows the cloak timer, and the figure shimmers while cloaked.
    - In co-op, threats go after the visible partner.
  - **PACKET MAGE** (new robed body with a fire-orb staff): built-in **PACKET FIREBALL**, a tracking
    fire shot every 2 s, even while moving, that bursts with splash on threats and walls.
- **How built-in weapons work:**
  - they take no weapon slot and are never offered as picks;
  - fixed cooldown;
  - damage grows with the campaign level (weapon level 1 + level/10, about ×4 by L100);
  - counted by the hidden power governor.
- **Class skins**, earned per class and never sold:
  - **TITANIUM** for reaching level 100 on **Normal** with that class;
  - **PURPLE** for reaching level 100 on **Hard** with that class.
  - A best level per class and difficulty is saved; the class card shows it and the unlock state.
  - A newly earned skin is announced with the run's unlocks.
  - Wearing a class skin on a class that hasn't earned it falls back to the default look.
  - Every class can wear all the paid colour skins, and the Technician's neon body now takes them too.
- Co-op: each player brings their own class. It travels with the body, and the cloak is synced.
### Changed
- **NEON OPERATIVE is no longer sold.** It is now the Technician class.
  - Anyone who bought it on its own gets the ◇100 back once.
  - The ◇300 skin bundle now covers the paid colour skins only.

## 0.16.3 (versionCode 55) — 2026-10-11 · Currency always fits
### Fixed
- **Currency chips** (Upgrades screen top right, main menu and the others) use a compact format of
  at most 6 characters:
  - up to 9,999 in full;
  - then 74.7K, 747K, 1.23M, 12.3M, 125M, and on through B, T, Q and Qi.
  - Values always round down, so the chip never shows more than you have.
- The chip no longer wraps, and the header slot grows with it if needed (it was a fixed 72dp).

## 0.16.2 (versionCode 54) — 2026-10-11 · No more one-shots
### Changed (owner: "at level 210 boss 1 shots player with a substantial build… At most 4 hits")
- **All threat damage halved on Normal.** Hard is cut to ×0.75 instead. Hard builds carry 4 fewer
  weapons and stack more defense, so at ×0.5 Hard was easier to survive than Normal. Easy is
  unchanged.
- **Hit cap:** no single hit can take more than **25%** of max HP + Firewall (Normal, Easy), or
  **34%** on Hard. Nothing can one-shot you: at least 4 hits from full on Normal, 3 on Hard.
- **Late boss damage climb** cut from +2.5% to **+0.5% per level past 120**. The +2.5% was what
  made L210 bosses one-shot.
- Simulated runs, hits for a boss contact hit to kill you from full:
  - Normal: about 7 at L50, 6–11 at L100, about 5 at L210 and 4–5 at L300. Before: about 1 at L210
    and 0.6 at L300.
  - Hard: about 4–6 at L50, 3–6 at L210, about 3.5 at L300.
  - A basic enemy takes about 14–30 hits on Normal and 10–27 on Hard.

## 0.16.1 (versionCode 53) — 2026-10-11 · Build number on screen
### Added
- **Build stamp** (owner): a small, soft label in the bottom-right corner of every screen, menus and
  gameplay included, so testers can tell which build they're on. It reads like "v0.16.1 (53)",
  with "-debug" on debug builds. Code: `ui/common/BuildStamp.kt`, overlaid in `AppRoot`.

## 0.16.0 (versionCode 52) — 2026-10-11 · Balance pass, bigger Packet Flood, power governor
### Fixed (owner: "At level 180 I'm doing 26,000+ damage… Maybe we are upgrading too often?")
- **Root cause: mastery compounded.** Every upgrade levelled past its max ("MASTERY") multiplied
  damage on its own, and all those bonuses multiplied together. Forty upgrades at mastery 10 came to
  ×10,000+. A simulated damage-focused run hit about **1,000,000 per shot by level 180**, against
  about 8,400 HP for a basic threat.
- Mastery is now **pooled**. All mastery levels in a category add up to one number P, applied once
  as 1 + 0.047·P + 0.00027·P². That is about ×3 at level 50, ×8 at level 100 and ×23 at level 180.
  Old saved runs are recalculated with the new rule when loaded.
- **Fewer picks:**
  - Level-clear bonus chances cut from 45% to 30%, and from 30% to 15%.
  - Boss kills give 2 + level/40 picks (was 2 + level/20): 6 at level 180, was 11.
  - About 370 picks by level 180, was about 465.
- **Arsenal weapon levels taper:** +35% per level up to level 10, then +15% per level.
- **Result:** a basic threat takes **about 2.5–4.5 hits from level 10 through level 180**. Before, it
  took 0.1 hits at level 100.
### Changed
- **Packet Flood is more than twice as big** (owner):
  - ×5.5 threats (was ×2.6) over 3 extra waves, and up to 70 on screen.
  - The next wave rolls in while about 10 are still alive.
  - About 200 threats at level 15 (was about 96) and about 500 at level 52.
- **Threats are no longer dropped at the on-screen cap.** Extras wait and come in as room frees up,
  in every wave level. Before, anything past 32 was silently lost.
- **Stragglers:** if the last 1–3 threats of a room go 20 s without a kill, they are purged, so a
  threat wedged somewhere unreachable can't hold the room forever.
### Added (hidden; owner: "make a system… silently prevent them from becoming insanely overpowered")
- **Power governor** (`engine/PowerGovernor.kt`, Normal and Hard only, never shown in the UI).
  - It measures the team's potential damage per second: main gun, every weapon and the nodes, with
    both operatives counted in co-op.
  - **HP floors** on that output:
    - a basic threat must survive **0.3 s** of it (Hard 0.4 s); all regular threats and elites are
      lifted by the same factor;
    - a boss must survive **60 s** (Hard 80 s), so a boss fight lasts about as long at level 1,200
      as at level 120.
  - When it lifts HP, **threat and boss damage rise with the square root** of the lift. Builds with
    extreme staying power are also hit harder.
  - **Boss damage climbs +2.5% per level past 120:** ×3 at 200, ×28 at 1,200.
  - A fair build is untouched. The old "ADAPTED TO YOUR ARSENAL" banner text is gone.

## 0.15.2 (versionCode 51) — 2026-10-11 · Bigger Malware Swarm
### Changed
- **Malware Swarm spawns 2.5× as many threats** (owner: "needs more than double"), and up to 70 can
  be on screen at once (was 32).
  - On Normal and Hard the swarm also grows with the post-level-10 threat count.
  - Over the 30 seconds: about 80 at level 10 (was 32), about 105 at level 20 (was 34) and about 218
    at level 52 (was 43).
  - Swarm threats keep their lighter HP (×0.8).

## 0.15.1 (versionCode 50) — 2026-10-11 · Hard locks 4 weapon slots
### Changed
- **Hard locks 4 weapon slots for the whole run** (owner). The lock always takes 4 away from whatever
  you've unlocked: 7 base gives **3** slots, 9 (two extra unlocked) gives **5**, and all 12 gives
  **8**. Nothing unlocks them during the run.
  - The upgrade screen shows it, for example "WEAPON SLOTS 2 / 3 · 4 LOCKED (HARD)", and the Hard
    description in the difficulty picker mentions −4 weapon slots.
  - Normal and Easy are unchanged.

## 0.15.0 (versionCode 49) — 2026-10-11 · Normal and Hard get much tougher
### Changed (owner: "Normal mode is too easy. After level 10 nothing is hard anymore")
- **Enemy HP on Normal:** +60% phased in over levels 5–15, then +2% more every level past 10.
  Roughly ×1.36 at L10, ×2 at L30, ×2.44 at L52 and ×3.4 at L100 compared with before.
- **More threats per room on Normal and Hard:** the count ramps up after level 10 to **double by
  level 52**, for example 46 → 92 threats.
- **Enemy damage:** +1% per level past 10, up to +60%.
- **Bosses:** **double HP** on Normal and Hard, plus half of the new HP curve. That's ×2.4 at L10 and
  ×3.4 at L52 compared with before.
- **Elites:** **double HP** on Normal and Hard. They also take 20% less damage and have extra flat
  armour.
- **Hard:** keeps its ×1.45 HP and gets 70% of Normal's HP increase, plus the full extra threat
  count, damage, boss and elite changes. It stays harder than Normal at every level.
- **Easy is unchanged.**

## 0.14.1 (versionCode 48) — 2026-10-10 · Save keeps the shop
### Fixed
- **An offered upgrade shop survives SAVE & EXIT** (owner: "saving progress and then loading back in
  makes the shop disappear. this punishes busy players"). When you continue, the side gate is open
  in the same spot and the "UPGRADE SHOP AVAILABLE" message plays again.
- **A cracked Data Vault cache stays cracked after continuing.** Before, it came back and could be
  cracked for a second payout.
- Saves from older builds still load (the shop just isn't on offer).

## 0.14.0 (versionCode 47) — 2026-10-10 · Boss music per tier
### Added
- **Every boss sounds different** (owner: "We need some of the boss music to sound different"):
  - **Tiers:**
    - **Tier I**, low-threat classics: the Level 10 tracks at 1.35×.
    - **Tier II**, high-threat classics: Boss 2 at 1.45×.
    - **Tier III**, expansion bosses: Boss 2 and its remixes at 1.5×.
    - **Tier IV**, Pack Gamma: the Boss 2 remixes only, at 1.55×.
  - **Signature sound:** each boss opens on its own track and gets its own sound, applied live.
    - Its own **pitch**, separate from tempo: low and heavy for Good Game, Zombie and Nullshade;
      bright and frantic for Glitch Forge, Spoofer and Kernel Panic.
    - A **bass/treble EQ** tilt: thick bass for Good Game and Vault Sentinel, a muffled top for
      Rootkit and Nullshade.
    - A **reverb space**: cathedral halls for Pulse Bishop and White Eye, a metal plate for Black Ice
      and Glitch Forge, small rooms for Botmaster and Worm Prime.
  - **Phases:** the music speeds up 5% with each boss phase, and holds that pace through the victory
    sequence.
  - A *GLITCHED* boss's music is pitched slightly up.
  - Volume headroom is added automatically when a boss's EQ boosts bass or treble, so it doesn't clip.
- No new audio files: everything is applied at playback (PlaybackParams pitch and speed, Equalizer,
  PresetReverb). Devices without audio effects still get the per-boss track, tempo and pitch.

## 0.13.0 (versionCode 46) — 2026-10-10 · BOSS CODEX, victory sequence, pause menu build
### Added
- **BOSS CODEX** (main menu): every one of the 24 bosses in three sections (Classic Threats,
  Expansion Bosses, Pack Gamma at level 120+).
  - Bosses you've met show their animated body; the rest are dark silhouettes marked "???".
  - Tap a known boss for its dossier: a live preview with a PHASE 1/2/3 switch, role, threat tier,
    when it can appear, description, abilities and base bounty.
  - The dossier also shows **your record**: times encountered, times defeated and fastest kill.
  - The header shows how many you've defeated (x/24).
  - Records are kept from now on, in co-op too.
- **Victory sequence** after every boss kill:
  - The boss flashes white, swells and breaks into armour shards that tumble away, with shock rings
    and a core flare.
  - Then a **THREAT NEUTRALIZED** card: the boss's name in its colour, fight time, and the bounty
    counting up.
  - A gold **NEW RECORD** tag appears when you beat your fastest kill, and **FIRST DEFEAT · DOSSIER
    UNLOCKED** the first time you beat a boss.
  - Tap to skip. The upgrade picks follow once it has played. In Endless the fight goes on, and only
    the card itself is tappable.
- **Pause menu tabs** (CO-065): MENU, BUILD and SETTINGS.
  - **MENU** is unchanged.
  - **BUILD** shows your operative's key stats (damage, fire rate, crit, range, max HP, armour,
    firewall, dodge, move speed) and every module you own this run, with its level and what it does.
  - **SETTINGS** has music and effects volume, haptics, damage numbers and screen shake, without
    leaving the run.
### Changed
- The old "BOSS ELIMINATED" banner is replaced by the victory card.
- Saving unlocks once the victory sequence has played.
- Co-op wire format v12 (victory sequence synced to the guest).

## 0.12.3 (versionCode 45) — 2026-10-10 · First boss is a classic
### Changed
- **First boss is always a classic** (owner): level 10 brings one of the 12 classic bosses at random.
  From level 20 the full random deck applies, with the Gamma pack from level 120.

## 0.12.2 (versionCode 44) — 2026-10-10 · Random bosses
### Changed
- **Bosses are random** (owner: "dont assign bosses to set levels… randomize boss spawn on boss
  levels"):
  - Every boss level brings a random boss, so every run meets them in a different order. The same
    boss won't come back until you've met all the others.
  - Levels 10–110 can bring any of the 20 non-Gamma bosses (all 12 classics and 8 expansion
    bosses). Level 10 can be anything.
  - From level 120 the four hardest, Pack Gamma (Rootkit Apostle, Ransom King, Spectral Firewall
    and Nullshade Specter), join the mix.
  - Every boss is scaled to the level it shows up on, so a heavy hitter early is still fair. It
    keeps a little of its own toughness.
  - The old 25% "wild boss" roll and the rare Nullshade roll are gone; the random deck covers both.
  - Endless mode uses the same random deck.

## 0.12.1 (versionCode 43) — 2026-10-10 · Dimmer floor lines
### Changed
- **Floor power conduits and cable bundles are toned down** (owner: "too bright to be part of the
  floor"). Conduits are a darker red with a faint glow and softer pulses. Cable cores are muted red
  and yellow. They now read as part of the floor instead of as hazard lines.

## 0.12.0 (versionCode 42) — 2026-10-10 · Classic boss bodies + World Kit finish
### Added
- **Every classic boss has its own body** (Stage F1, built autonomously at the owner's request). The
  rotating hexagon is retired. Each body reacts to its phase (3 stages) and to charging an attack:
  - **BREACH:** a hovering breaching pod with thrusters, a red visor and a rotating drill ram that
    turns to face you. Its armour cracks open as it weakens.
  - **BOTMASTER:** a command spire with an ∞ screen and an antenna crown. Its botnet nodes orbit on
    a holo ring (6/8/10) linked by data lines.
  - **WORM PRIME:** an armoured worm with a maw of counter-rotating teeth and a curling, dripping
    tail.
  - **RANSOM:** a giant gold padlock with a spinning combination dial, a burning keyhole, chains and
    encrypted glyphs. The shackle lifts open each phase.
  - **ROOTKIT:** a hooded phantom with a terminal ">_" face and trailing code ribbons. While hidden
    it shows only as a flickering scanline silhouette.
  - **SYN-STORM:** a turbine in the eye of a storm, with spiral arms of SYN packets (3/4/5).
  - **KERNEL PANIC:** a cracking CPU with gold pins and a warning triangle. The die splits apart and
    sparks jump between the pins.
  - **EXFIL:** a stealth dart that always points at you, with afterburners, stolen-data pods and a
    trail of leaked bits.
  - **WHITE EYE:** a huge eyeball in a shuttered housing. The pupil follows you, and circuit veins
    spread across it each phase.
  - **ZOMBIE:** a hulking robot corpse with exposed cable ribs, a hanging jaw, one burning eye and
    dragging claws.
  - **SPOOFER:** a cracked porcelain mask in a hood of static, with chromatic ghost forgeries
    drifting off it.
  - **GOOD GAME:** a tracked siege tank with "GG" stencilled on it, smokestacks and twin cannons
    that track you. Its armour plates fall away each phase.
- **World Kit, last two pieces:**
  - **05 Server rack:** bevelled bays with handle notches, a vent grille on top and a status light
    running down the side.
  - **07 Data-vault crate:** X-braced lid, steel corner brackets, stacked-crate seams, hazard stencil
    and a blinking lock panel.

## 0.11.19 (versionCode 41) — 2026-10-10 · Circuit Hydra: regrow and roar
### Changed
- **Regrow and roar** (owner design): after the exposed window, the heads regrow in a sequence that
  lasts about 3.6 s:
  - The heads grow back out of their neck roots.
  - They lean in toward you, stare straight at you with their jaws open, and roar for 2 seconds
    while the screen shakes and shockwaves pour from their mouths.
  - Then they settle back and the fight goes on.
- **Invulnerable:** nothing can be hurt during the sequence, and no attacks start until it ends.
- **Tracking:** heads keep turning to follow you during the fight, and lock straight onto you
  during the roar.

## 0.11.18 (versionCode 40) — 2026-10-10 · Circuit Hydra: Neck Volley + Head Bite
### Changed
- **Neck Volley** (was Segment Burst; owner pick): a surge runs up every living neck, vertebra by
  vertebra. Each vertebra spits a small ring of toxic-green sparks.
- **Head Bite** (replaces Coil Crush; owner pick):
  - The head nearest you marks a wide strike lane toward you. In phases 2 and 3 a second head follows.
  - It then lunges down the lane on a stretching neck and snaps its jaws for a big hit, then pulls
    back. Step out of the lane to dodge it.
- **Tuning** (owner): each head has 6% of the hydra's HP (was 3.5%), the core stays exposed for 6 s,
  and the hydra's HP is up 25% (2,720 → 3,400 base).
- **Phase changes:** a new phase closes the exposed window at once, and every head regrows with the
  new one, so the fight always shows 3, then 4, then 5 heads.
- Hostile shots can now be tinted per attack.

## 0.11.17 (versionCode 39) — 2026-10-10 · Circuit Hydra: Head Shield
### Changed
- **Circuit Hydra, Head Shield** (owner pick: "Heads shield the core"):
  - **Heads:** every head is a target with its own HP bar.
  - **Shield:** while any head lives, a green shield dome covers the core. Aim and shots pass the core
    and go to the heads.
  - **Exposed core:** destroy every head and the core blazes open for 6 seconds ("CORE EXPOSED").
    Then all the heads regrow.
  - **Destroyed heads:** the neck slumps to the floor and sparks, and that head's beam dies with it.
  - **Phases:** each new phase grows another head (3 → 4 → 5).
- **Beam Arc** (owner pick): one thick sweeping beam from each living head's mouth, with neighbouring
  heads sweeping in opposite directions (it used to fire fans of 3).
- Split Heads is gone; phase 2 is now called "FOURTH HEAD".
- Co-op wire format v11 (synced head state).

## 0.11.16 (versionCode 38) — 2026-10-10 · Circuit Hydra redesign: body
### Changed
- **Circuit Hydra has a new body** (owner-directed, step by step):
  - **Look:** an armored reactor core in toxic green and gunmetal, with long necks of chained metal
    vertebrae and heavy mechanical dragon heads: split jaws with fang rows, a big brow ridge and
    swept horns.
  - **Size and heads:** 1.6× bigger than before, with 3, 4 or 5 heads by phase. The hitbox is still
    the core.
  - **Movement:** it floats slowly up and down the middle of the arena, between 50% and 30% down.
  - **Beams and bursts:** Beam Arc fires from each head's mouth, and the beams follow the head as it
    sways. Segment Burst fires from the necks.
  - **Split heads:** they sit on the body's extra heads; shooting them still drains the hydra's HP.
- Attacks are next, each one reviewed by the owner.

## 0.11.15 (versionCode 37) — 2026-10-10 · Worm Queen swarm
### Changed
- **Worm Queen is a real swarm now** (owner request):
  - She drops a fresh egg cluster next to herself every 2.4 s (1.9 s in phase 2, 1.4 s in phase 3),
    3–5 swarmlings per egg, on top of Swarm Hatch.
  - Swarm Hatch lays 6/8/10 eggs by phase (was fewer), hatching faster.
  - Swarm cap raised to 70 swarmlings (other enemies stay capped at 32).
  - Swarmlings are lighter to match the numbers: 14 HP (was 22), contact damage 6 (was 9), faster.
  - Her body shows more crawling swarmlings (5/7/9 by phase).

## 0.11.14 (versionCode 36) — 2026-10-10 · Wild bosses + World Kit
### Added
- **Any boss level** (owner request): from level 20, every boss room has a 25% chance to bring one of
  the seven Pack Alpha/Beta bosses instead of the scheduled one: Pulse Bishop, Packet Reaper, Worm
  Queen, Glitch Forge, Botnet Monarch, Circuit Hydra or Black Ice Overlord. Early in a run they're
  toned down to the scheduled boss's toughness, and they keep their premium bounty. Level 10 is
  always the first-boss intro.
- **World Kit** (owner sheet), for every room (generated and hand-made) and the boss arena:
  - **New decor:**
    - **Broken grid tiles:** cracked floor plates.
    - **Power conduits:** red energy lines with L bends and pulses running along them.
    - **Spark panels:** electric sparks, from level 15.
    - **Light beacons:** cyan pylons that light the floor around them.
    - **EMP dark-zone emitters:** purple devices that swallow the light around them, from level 40
      and in boss rooms.
  - **Upgraded pieces:**
    - **Floor tiles:** a rim glow.
    - **Vents:** recessed grates with depth.
    - **Cables:** thick bundles with red and yellow cores.
    - **Relay pillars:** a glowing square frame and lit corners.
    - **Firewall barricades:** a hex energy pane between posts.
  - The boss arena is dressed with conduits, beacons, spark panels and dark-zone emitters in the
    boss's colours.
  - Layouts and spawns are unchanged: decoration uses its own seed.

## 0.11.13 (versionCode 35) — 2026-10-10 · Black Ice Overlord
### Added
- **Black Ice Overlord** (Boss Pack Beta) is the **level 220** boss: STATUS, threat HIGH, base bounty
  €380. Its body is a dark navy armoured golem encased in cyan ice crystals (a crown of shards and
  crystal pauldrons) with glowing ice eyes, two shoulder ice cannons and drifting snowflakes.
  - **CHILL** (new status): each stack slows you 9%, shown as frost pips over your head. Five stacks
    **FREEZE** you in an ice block for about 1 s. Stacks wear off over time.
  - **Ice Laser:** sweeping freezing beams from its shoulder cannons. A hit chills you hard.
  - **Freeze Patch:** frosty zones (one under you) that chill you every half second you stand in them.
  - **Crystal Volley:** ice crystals arc onto marked spots, and a hit chills.
  - **Permafrost Shell:** a faceted ice dome cuts the damage it takes to a quarter and chills anyone
    near it. Keep firing (the boss bar shows the shell %) and it shatters into a stun window. It
    also melts on its own after 7–8 s.
  - **Phases:** PHASE 1 → DEEP FREEZE → ABSOLUTE ZERO.
- Co-op wire v10 (CHILL, FROZEN and the shell are synced).

## 0.11.12 (versionCode 34) — 2026-10-10 · Circuit Hydra
### Added
- **Circuit Hydra** (Boss Pack Alpha) is the **level 200** boss: AREA CONTROL, threat HIGH, base
  bounty €362. Its body is a real serpent: a chain of dark crimson armoured spheres with glowing core
  rings that snakes after its beam head as it sweeps the arena.
  - **Split Heads:** extra beam heads sprout on necks off its body, one in phase 2 and two in phase 3.
    Every head drains the same HP bar, so shoot whichever head you can reach.
  - **Beam Arc:** every head sweeps a fan of beams, filling the arena with crossing fire lanes.
  - **Segment Burst:** its body segments fire rings of shots.
  - **Coil Crush:** its body coils around you and tightens. Slip out through the gap.
  - **Phases:** PHASE 1 → SPLIT → HYDRA STORM.
- Co-op wire v9 (the serpent body is synced).

## 0.11.11 (versionCode 33) — 2026-10-10 · Botnet Monarch
### Added
- **Botnet Monarch** (Boss Pack Beta) is the **level 190** boss: COMMANDER, threat HIGH, base bounty
  €380. Its body is a crimson spiked command sphere with a blazing core and blinking uplink
  antennas, over a glowing dashed orbit track.
  - **Drone Ring:** armoured drones ride the orbit ring around it and shoot at you: 4, then 6, then
    8 by phase. Shoot them down to break the ring; it tops the ring back up later.
  - **Summon Wave:** packs of bot drones.
  - **Orbital Barrage:** sky beams come down onto target rings (the first on you) in volleys.
  - **Sync Burst:** the ring drones link up with glowing lines, then all fire at you at once while
    the monarch adds a ring of shots.
  - **Phases:** PHASE 1 → COMMAND NETWORK → TOTAL BOTNET.
- Co-op wire v8 (Sync Burst charge is synced).

## 0.11.10 (versionCode 32) — 2026-10-10 · Glitch Forge
### Added
- **Glitch Forge** (Boss Pack Beta) is the **level 180** boss: SUMMONER, threat HIGH, base bounty
  €344. Its body is a big dark server-core cube with neon magenta edges, glitch glyphs, a glowing
  target ring on top, RGB glitch tears and hologram cubes orbiting it.
  - **Decoy Clone:** translucent hologram copies that move and shoot spreads. One hit pops them, at
    any level.
  - **Corrupt Floor:** a checkerboard of floor tiles around you blinks, then glitches into a
    hazard. The other half of the tiles stays safe.
  - **Cube Barrage:** waves of spinning homing data cubes.
  - **Core Pulse:** a big pulse ring, and the floor it passes over corrupts behind it.
  - **Phases:** PHASE 1 → REALITY FRACTURE → TOTAL CORRUPTION.

## 0.11.9 (versionCode 31) — 2026-10-10 · Worm Queen
### Added
- **Worm Queen** (Boss Pack Alpha) is the **level 170** boss: SUMMONER, threat HIGH, base bounty
  €326. Her body is a huge dark armoured brood-sphere with crimson spikes and a glowing pink core,
  sitting in a spreading corruption pool with swarmlings crawling over her.
  - **Swarm Hatch:** pulsing egg sacs land around her, then crack and hatch into packs of
    **swarmlings**, a new small spiked enemy.
  - **Corruption Trail:** she surges after you, leaving infected pools along her path.
  - **Spike Burst:** rings of crystal spikes burst outward, with safe lanes through them.
  - **Queen Roar:** a shockwave, and her whole swarm surges at extra speed for a few seconds.
  - **Phases:** PHASE 1 → BROOD SURGE → INFESTATION.

## 0.11.8 (versionCode 30) — 2026-10-10 · Packet Reaper
### Added
- **Packet Reaper** (Boss Pack Alpha) is the **level 160** boss: ASSASSIN, threat HIGH, base bounty
  €308. Its body is a hunched crimson assassin mech with a red visor, magenta thrusters and two huge
  glowing packet scythes for arms. A third scythe blade appears on its back in phase 3.
  - **Dash Slash:** a telegraphed lane dash that ends in a crescent slash. In later phases it chains
    2–3 dashes.
  - **Trail Burst:** in phases 2–3 its dash path erupts behind it after a short delay.
  - **Packet Scythes:** spinning scythes loop out and swing back to where it threw them, crossing
    each other.
  - **Backline Dive:** it vanishes, marks a spot behind you (the side you're not facing), then dives
    through you.
  - **Phases:** PHASE 1 → LANE HUNT → NO ESCAPE.

## 0.11.7 (versionCode 29) — 2026-10-10 · Pulse Bishop
### Added
- **Pulse Bishop** (Boss Pack Beta) is the **level 150** boss: CONTROLLER, threat HIGH, base bounty
  €344. Its body is a floating dark prelate with a split mitre and a glowing cross, and a burning +
  sigil on the floor beneath it.
  - **Line Warp:** blinks onto your row or column, leaving a light streak, then fires a beam down
    the lane.
  - **Cross Beam:** + shaped beams. They turn in phase 2, and in phase 3 diagonals are added (an
    8-way star). Cover stops them.
  - **Bishop Mines:** spiked mines arm after about 1 s. Once armed they trip when you come close and
    blow after a short fuse.
  - **Convergence Flash:** a tether of light drags you toward it, then a blinding white flash blasts
    the area around it. Run against the pull.
  - **Phases:** PHASE 1 → SACRED GEOMETRY → EXCOMMUNICATION.
- Co-op wire v7 (PULLED is synced; the guest is dragged too).

## 0.11.6 (versionCode 28) — 2026-10-10
### Changed
- **Spectral Firewall's Firewall Ring builds up** (owner request): phase 1 launches 1 wall, phase 2
  launches 3 and phase 3 launches 4, spread about 200 units apart (1.15 s and 1.05 s between walls)
  so there's room to line up between them. Each wall keeps its 2 drifting gaps, offset from the wall
  before, so you weave between them.

## 0.11.5 (versionCode 27) — 2026-10-10 · Spectral Firewall + boss-coloured arenas
### Added
- **Spectral Firewall** (Boss Pack Gamma) is the **level 230** boss: AREA DENIAL, threat HIGH, base
  bounty €380. Owner-approved body: a spiked core and an orbiting ring of charred, red-hot metal
  plates.
  - **Ring shield** (owner's choice): the orbiting plates soak up your fire, so you shoot through the
    gaps as they rotate past. There are 3 gaps in phase 1, 2 in phase 2 and 1 in phase 3. Walking
    into a plate sets you on fire. Blocking matches what you see on screen.
  - **Firewall Ring** (owner spec): burning walls with gaps burst out of the boss and push all the way
    past the arena walls. The gaps drift, so you have to move to stay lined up, and touching a wall
    sets you **ON FIRE**. While the walls are out, its own ring is gone and the core is exposed. The
    boss bar shows "RING UP — FIRE THROUGH THE GAPS" or "RING LAUNCHED — CORE EXPOSED".
  - **Burn Sector:** wedges of the arena, the first aimed at you, show dashed edges and warning signs,
    then ignite.
  - **Heat Collapse:** a fire wall closes in from the arena edges toward the boss. Slip through a gap.
  - **Purge Spin:** roaring flame jets spin around it. They stop at cover and set you on fire.
  - **ON FIRE:** a 2.5 s burn that ignores invulnerability, with flames shown on the operative.
  - **Phases:** PHASE 1 → HEAT RISING → MELTDOWN.
- **Boss-coloured arenas** (owner request): every boss arena (classic and new) takes its boss's
  colours. The floor trim, light pools and walls are tinted, and a glowing border in the boss's accent
  colour pulses around the edge of the screen.
### Changed
- Co-op wire format v6: the ring and ON FIRE are synced. Both players need this version.

## 0.11.4 (versionCode 26) — 2026-10-10 · Ransom King
### Added
- **Ransom King** (Boss Pack Gamma) is the **level 210** boss: LOCKDOWN, threat HIGH, base bounty
  €362. Owner-approved body: a block-golem king with an evil cursed crown.
  - **Key Zone:** he fights ENCRYPTED, with a red lock-shield dome that cuts his damage taken to a
    quarter.
    - Golden key zones appear around the arena. Each one shows a fill meter and a timer.
    - Stand in a zone to unlock it. Once every zone in the wave is unlocked, he's DECRYPTED for
      6 s and takes 50% extra damage, then the shield reforms.
    - The boss bar tells you which state he's in.
  - **Lock Grid:** walls of padlocked red cubes rise across the arena near you, sealing it into
    sections. Every wall has openings, and walls never rise on top of you.
  - **Royal Seizure:** a golden crown-slam circle marks your spot. If it catches you, you're
    SEIZED (can't move, gold chains) for about a second, inside a padlock cage with one side
    open, away from him.
  - **Ransom Pulse:** rings of lock-light with padlocks ride outward. A ring that tags you makes
    you ENCRYPTED for 3.5 s, shown by a padlock and a meter over your head. Standing still is
    safe, but moving fills the meter, and when it's full the ransom bursts on you.
  - **Phases:** PHASE 1 → RANSOM DEMAND → TOTAL LOCKDOWN.
### Changed
- Boss cubes can now sit flush against each other to form solid walls.
- Co-op wire format v5: shield, cube style and SEIZED/ENCRYPTED states are synced, and a seized
  guest can't move. Both players need this version.

## 0.11.3 (versionCode 25) — 2026-10-10 · Nullshade Specter
### Added
- **Nullshade Specter**, the EXTREME "Blackout Hunter", is the **level 240** boss. It also turns up
  as a rare 5% stalker in boss rooms from level 150. Base bounty €435.
  - **Body:** a darkness ghost traced from the owner's reference.
    - A faceted hood with no outlines, over a pitch-black face.
    - Big, sharp red eyes that blink and cast a faint red light.
    - A body that is a cloud of black voxel blocks with magenta light between them, a clawed hand
      and a magenta ring on the floor.
  - **EMP Blackout:** the fight opens with an EMP and the room goes **extremely dark**. You keep a
    small light bubble. Its eyes, every attack warning and the sparking server blocks stay visible.
    When it dies, the power comes back on.
  - **Eye-Glint Lock:** you can only lock on to it, and only hurt it, while its eyes are open. The
    boss bar shows "EYES OPEN — LOCK ON" or "IN THE SHADOWS — NO LOCK". The windows get shorter but
    come more often in each phase, and the room lifts very slightly while its eyes are open.
  - **Ghost Dash:** a telegraphed dash that hits once and leaves a short corruption trail.
  - **Static Needles:** spreads of faint, fast needles with static trails.
  - **Spark Ambush:** it melts into the shadows. A server block is marked, then sparks spray out
    of it and the specter steps out beside it.
  - **Grid Reboot Surge (phase 3):** rapid EMP rings while the lights stutter on and off.
  - **Phases:** BLACKOUT INITIATION → PHANTOM HUNT → GRID REBOOT FRENZY.
### Changed
- **Expansion bosses keep their planned levels** (130–240), whatever order they're built in.
  A level whose new boss isn't finished yet keeps its old boss.
- Co-op wire format v4 (darkness and eye state are synced). Both players need this version.

## 0.11.2 (versionCode 24) — 2026-10-10 · Rootkit Apostle
### Added
- **Rootkit Apostle** (Boss Pack Gamma) joins the roster at **level 140**: AMBUSHER, threat HIGH,
  bounty base €326.
  - **Body:** a black armoured orb with a dark magenta crystal crown and a mask eye. The eye
    breathes slowly from dim to bright and blazes when it attacks (owner-approved design).
  - **Burrow Drift:** it dives underground (it can't be hit there) and tunnels toward you as a
    heaving, cracked mound with a glowing trail. Its exit is marked before it erupts. In later
    phases it erupts with rings of spikes.
  - **Spike Eruption:** lines of crystal spikes ripple out of the floor toward you: one line,
    then three, then all around.
    - Each spot shows a cracked diamond warning first.
    - Each spike hits once as it bursts.
  - **Infected Zone:** it infects where you stand and the spots you've stood in longest lately,
    so camping gets punished and you have to keep relocating.
  - **Rootkit Bloom:** rings of spikes burst outward from it in waves, with three straight safe
    lanes cut through every ring.
  - **Phases:** PHASE 1 → INFECTION → ROOTKIT BLOOM.

## 0.11.1 (versionCode 23) — 2026-10-10 · Vault Sentinel
### Added
- **Vault Sentinel** joins the roster at **level 130**: TANK, threat HIGH, bounty base €326. Its body
  is 20% larger than the first draft, as the owner asked.
  - **Cover Deploy:** barrier cubes rise out of the floor around you in short wall segments.
    - Each spot flashes on the floor first, so you can step clear.
    - Once risen, the cubes block movement and shots for about 8 s, then sink.
  - **Laser Sweep:** a telegraphed laser sweeps an arc toward you (two or three beams in later
    phases). The cubes stop it, so you can hide behind them.
  - **Lockdown Cube:** a box of cubes rises around you. One side is always open, the side away
    from the boss, or the next side that leads out if a wall is in the way.
  - **Pulse Mortar:** shells lob onto marked spots, the first one on you. They fly over the cubes.
  - **Phases:** PHASE 1 → LOCKDOWN → VAULT BREACHED. It drops its own cover modules as the fight
    goes on, and the cracked core blazes in the last phase.
- Co-op: barrier cubes are synced to the partner (wire format v3, so both players need this version).

## 0.11.0 (versionCode 22) — 2026-10-10 · Boss Expansion Vol. 1, Stage A
### Added
- **Threat dossier card** during every boss entrance: role badge (TANK, ASSASSIN, …),
  threat-tier skulls (LOW / MEDIUM / HIGH / EXTREME), up to four signature abilities and the
  **bounty** in € the kill pays. It sits mid-screen so the boss arriving at the top stays in view.
- **Screen effects:**
  - Boss kill: a short freeze, white flash, heavy shake, then 1.4 s of slow motion.
  - Boss phase change: a quick freeze with a flash in the boss's colour and a shake.
  - Heavy hits on you (12%+ of max HP) shake the screen a little.
- **SCREEN SHAKE** toggle in Settings (the setting existed but was never wired up).
- Every boss now has a role and a threat tier. The 12 current bosses are tagged.
- Groundwork for the 12 new bosses at levels 130–240:
  - a per-boss body renderer, so each boss gets its own look instead of the hexagon;
  - Nullshade Specter can turn up as a rare boss (5%) from level 150 once it is built.
- First new boss body drawn, **Vault Sentinel**, waiting for owner approval. It doesn't
  appear in runs until its mechanics are built.


## 0.10.3 (versionCode 21) — 2026-10-09
### Added
- **WEAPON SLOTS** permanent upgrade on the UPGRADES (OP level-up) screen, for € earned in runs.
  It has 5 levels; each adds one weapon slot (7 → 12):

  | Slot | OP level | Cost |
  |---|---|---|
  | 8 | 10 | €5,000 |
  | 9 | 20 | €15,000 |
  | 10 | 30 | €40,000 |
  | 11 | 50 | €100,000 |
  | 12 | 80 | €250,000 |

  - Until the OP level is reached, the card shows a grey padlock with the level it needs.
  - In runs, the slot counter, the card screen, the swap grid and the shop use your own slot
    count (co-op partners bring theirs).
### Changed
- **Difficulty now scales with OP level only** (owner, 2026-10-09):
  - Removed the late ramp (the extra +HP per level past 30) and build-size threat scaling on
    every difficulty. The base curves are back to the original.
  - OP-level threat scaling (+2% HP / +1.2% damage per OP level, ×3 / ×2.2 at OP 101, then
    slow growth; mastery included) applies from level 1, halved on Easy.
  - Hard stays ×1.45 HP / ×1.4 damage over Normal at the same OP level.
  - Deep levels keep getting busier: up to 70 threats per level and up to 50% elites past
    level 50.

## 0.10.2 (versionCode 20) — 2026-10-09
### Added
- **Hold for details** (owner, 2026-10-09) on upgrade cards, shop items and the weapon swap grid:
  the full description plus **what changes**, each stat current → after with the change.
  Green means it goes up, red means it goes down. This includes main gun DPS, all-weapons DPS,
  damage, attack speed, crits, HP, armor, speed, nodes and blades. Weapons gained, lost or
  levelled show their level and rough damage per second.
- The swap confirmation shows every stat change of the swap plus the full description of the
  weapon removed (red) and the new one (green).
### Changed
- The upgrade shop offers new weapons even with all 7 slots full; buying one opens the swap grid
  (tap a weapon, YES / NO). It only charges if you confirm.
- **Difficulty** (owner: "hard was fine how it was; quite hard by 30"): every extra on top of
  the base curve now does nothing up to level 30, fades in from 31 and reaches full strength at
  level 80. That covers OP-level/mastery threat bonus, build-size adaptive threat and the late
  ramp.
  - Levels 1–30 play exactly as before 0.9.1 on every difficulty.
  - The extras are gentler: late ramp +2% HP / +1.5% damage per level; adaptive +1% HP per pick
    and +8% per weapon.
  - Easy gets half of the extras. Hard is always tougher than Normal.
  - Hard, OP 50, typical build, enemy HP vs the base curve: ×1.45 to level 30, ×2.6 at 40,
    ×4.5 at 50, ×7.7 at 60 (0.10.1 was ×4.6 at 30, ×9.8 at 40, ×14 at 50).

## 0.10.1 (versionCode 19) — 2026-10-09
### Changed
- Weapon slots (owner, 2026-10-09): at most **7 weapons** equipped. Weapons are the 30 arsenal
  weapons plus Packet Scatter, Exploit Lance, Plasma Beam, Encryption Blades, EMP Burst,
  Logic Bombs, Malware Missiles, Arc Discharge, Quantum Railgun and Orbital Strike. Gun
  modifiers (Multishot, Penetration…) and Packet Nodes are power-ups.
- Every card screen now shows **power-up, power-up, weapon** (instead of three random cards,
  which were often all weapons). If one kind runs out, the other fills the gap.
- Picking a new weapon with all 7 slots full opens the weapon grid:
  - Tap a weapon to replace it, or hold it to see what it does.
  - The confirm screen shows [old] → [new] and asks "You want to replace ___ with ___?"
  - YES swaps them; NO goes back to the grid. KEEP MY WEAPONS returns to the cards.
- Levelling up a weapon you already have never needs a slot.
- The upgrade shop only offers new weapons while a slot is free.
- Runs saved with more than 7 weapons keep them; new ones need a swap.
- Co-op: the guest's weapon swaps go to the host (wire format v2; both players need this
  version).

## 0.10.0 (versionCode 18) — 2026-10-08
### Added
- **CO-OP** (owner, 2026-10-08), on the main menu. Needs a Firebase project; see FIREBASE_SETUP.md.
  Without one, the screen says co-op is unavailable and solo play is unaffected.
  - Register a callsign and get a friend code (XXXX-XXXX) to copy or share.
  - Add friends by code; accept or decline friend requests. The friends list shows who's online.
  - Invite an online friend; they JOIN or DECLINE. In the two-player lobby the host picks the
    difficulty and starts.
  - Live two-player campaign run:
    - Each operative has its own permanent upgrades, skin and body, and picks its own cards.
    - Threats chase whoever is closest.
    - A downed operative is revived by the partner standing next to them for 3s. Anyone
      downed is back at half HP on the next level. The run ends only when both are down.
    - Threats have ×1.4 HP (bosses ×1.7), and there are about 35% more of them.
    - Threat scaling uses the higher OP level of the two.
    - Both players keep full rewards (€, Operative XP, achievements, records).
    - Co-op can't be paused or saved; no upgrade shop in co-op.
  - If either player disconnects, the other carries on solo from the same moment.
  - HUD: partner HP bar (magenta), partner ring on the field, revive ring and progress,
    "waiting for partner" on the card screen.
- Networking: the host runs the game and sends compact snapshots ~10×/s. The guest moves its own
  operative locally (no input lag) and sends its position and picks. Measured ~10–15 MB per hour
  per pair.
- Security rules for Firestore and the Realtime Database (`firebase/`).
- INTERNET permission (co-op only).

## 0.9.7 (versionCode 17) — 2026-10-08
### Changed
- Endless progression (owner: "scale it to where play never ends"). OP level now goes to 9,999
  (was 999).
- Permanent upgrades keep going past their max as MASTERY levels, up to level 9,999. Each
  mastery level needs one OP level (mastery 50 needs OP LVL 50), and costs keep rising.
  - Endless with diminishing returns (√ of mastery): max HP, damage, crit damage, firewall,
    node damage, healing, boss damage, data gain, € gain.
  - Soft-capped, creeping toward a ceiling: attack speed (max +120%), move speed (+60%),
    range (+80%), packet speed (+100%), crit chance (+45%), damage reduction (40%),
    card quality.
  - Still hard-capped, because more would break runs: starting nodes, starting upgrades,
    rerolls.
- Threat scaling keeps going past OP 101 on a log curve: HP ×3 at OP 101, ×7 at OP 1,000 and
  ×11 at OP 9,999; damage ×2.2 / ×4.2 / ×6.2. Threats also answer mastery: for every ×4 that
  mastery adds to your damage (or staying power), threat HP (or damage) rises ×2. Mastery
  always pays off, and fights stay real.
- The run picker shows the combined threat bonus; upgrade costs show thousands separators.

## 0.9.6 (versionCode 16) — 2026-10-08
### Added
- Four BOSS 2 music variants (Boss 2, Boss 2 alt, Boss 2 Remix ×2). They join the boss rotation
  with the level-10 tracks and shuffle into normal combat like every other track.
### Changed
- Smaller download: the whole soundtrack is re-encoded from MP3 (~195 kbps, with embedded cover
  art) to Ogg Vorbis (~92 kbps, no artwork). Music went from ~116 MB (24 tracks) to ~56 MB
  (28 tracks); the debug APK dropped from ~137 MB to ~73 MB.

## 0.9.5 (versionCode 15) — 2026-10-08
### Changed
- Fullscreen gameplay: the status bar is hidden while playing (swipe down from the top edge to
  peek at it; a second swipe still opens notifications / Quick Settings). Navigation bar and
  gestures are untouched; menus get the normal bars back.
- New compact HUD: one header row (LVL · SCORE · € | device clock · pause), thin CORE HP and
  THREATS bars with labels and values, buff chips in one scrolling row with a "+N" counter for
  chips out of view. Tap a chip for its name, effect, description and duration.
- Pull handle under the HUD folds it to a small pill (health diamond + clock + pause) for more
  battlefield; tap it again to unfold. The HUD fades out while the notification shade is open,
  and stays visible over the pause menu (its button turns into ▶ RESUME there).
- The arena now starts right under the HUD as measured (no fixed pixel offsets), so it gains
  space on every phone; gate / shop / vault hints and banners stack below the HUD.
- The HUD keeps clear of camera cutouts on all sides.

## 0.9.4 (versionCode 14) — 2026-10-08
### Changed
- NEON OPERATIVE and the shopkeeper are now traced from the app icon at its exact proportions —
  the same hood, face shield, headset/antenna, shoulders, strap, collar tab and button as the
  main-menu emblem (shared drawing code) — standing on a tapered robe with sleeves and legs.
- Shopkeeper looks match the rarest stock on the counter: all GOLDEN → gold; one TITANIUM →
  titanium silver; two or three TITANIUM → menacing black with blood-red trim; all four
  TITANIUM → cycling spectrum. Shop stock is now drawn by rarity weight, so TITANIUM stays rare.
- Skip-shop prompt: walking into the top gate while a shop is on offer asks "SKIP THE UPGRADE
  SHOP?" — green [NO] locks the top gate red and flashes a gold arrow toward the shop gate;
  red [YES] moves on to the next level and closes the shop.
### Fixed
- Shop gate placement verified clear in generated, Data Vault and boss rooms (tests).

## 0.9.3 (versionCode 13) — 2026-10-08
### Changed
- Adaptive threat (owner: "levels 50+ scale too slowly if you have many weapons"): threat and
  boss HP rise +1.5% per upgrade pick and +10% per weapon fighting for you; damage +0.6% per pick
  and +3.5% per weapon. Fades in from level 21 (full by 40); levels 1–20 are unchanged. Fixed per
  level; the level banner shows "ADAPTED TO YOUR ARSENAL ×N" when it is significant.
- Deep levels are busier: threat count keeps rising past level 50 (to 70 per level), and the
  elite chance climbs from 35% toward 50%.

## 0.9.2 (versionCode 12) — 2026-10-08
### Fixed
- The shop's side gate can no longer be blocked: when a shop is offered the game picks a spot on
  the left or right wall whose doorway (and the space in front of it) is free of walls and blocks
  and that the operative can walk to; mid-height first. The gate, hint and terminal message show
  which wall. Tested on 150 generated rooms, walking in each time.

## 0.9.1 (versionCode 11) — 2026-10-08
### Added
- Data Vault cache: once the vault room is clear, walk up to the gold cache block in the middle —
  it glows, opens (light leaking through cracks), shakes harder and harder for 1.3 s, then bursts
  in a gold explosion and pays out €100 × level (minimum €1,000; level 70 → €7,000).
### Changed
- Harder late game (owner: "after level 60 I feel invincible"): from level 31 enemy HP compounds
  +3% per level and damage +2.2% per level on top of the old curve (level 60: HP ×25.8 instead of
  ×10.6, damage ×8.2 instead of ×4.3). Bosses follow the same curve.
- OP level scaling: every account OP level above 1 adds +2% threat HP and +1.2% threat damage,
  bosses included (up to ×3 / ×2.2 at OP 101). The difficulty picker shows the current bonus.

## 0.9.0 (versionCode 10) — 2026-10-08
### Added
- Upgrade shop: after a cleared level's upgrades are picked there is a 1-in-20 chance the
  shopkeeper calls in — a terminal-style "> RECEIVING MESSAGE … > UPGRADE SHOP AVAILABLE"
  pop-up types out and a gold side gate opens in the left wall. It leads to a small, randomly
  furnished shop room (no threats). The shopkeeper is the main-menu hooded operative with a gold
  shield and the face X,.,.X. Four GOLDEN / TITANIUM mods sit on the counter (GOLDEN from €1,000,
  TITANIUM from €2,500, +€25/+€50 per level); stand at the counter and tap one to buy it with this
  run's €. Leave through the top gate to the next level. Saving is paused inside the shop.

## 0.8.0 (versionCode 9) — 2026-10-08
### Added
- Environments (data/Environments.kt): 12 colour themes (Cyan Grid, Emerald Dataworks,
  Amethyst Core, Magma Forge, Arctic Server Farm, Toxic Lab, Rose Neon District, Golden Vault,
  Deep Ocean Relay, Solar Flare Array, Ghost Protocol, Ultraviolet Mainframe), each with its own
  floor, plate, light, trench, wall and trim colours. The theme changes every 3 levels in a
  per-run shuffled order ("ENTERING MAGMA FORGE" on the banner).
- 8 floor tile patterns (plates, hex, grid, circuit, diamond, brick, perforated dots, grooves);
  every level also rolls its own tile size, trench spacing, flow direction and shade.
- 6 new wall types: Blast Wall, Coolant Pipes, Holo Wall, Reactor Core, Energy Barrier,
  Antenna Tower, used by 5 new room kits and mixed into corridor/row layouts. Existing walls
  and racks take on each theme's tone and trim colour.
- BUFFS bar: blue-green outline with a "BUFFS:" label.
### Changed
- Upgrade icons are smaller (24dp) and sit under the HP bar instead of over the joystick area;
  the arena, banners and the boss bar move down to make room. Hold an icon: details pop up below.
- Firebase libraries added for the upcoming accounts / friends / co-op (inactive until
  app/google-services.json is added; the game builds and plays without it).

## 0.7.0 (versionCode 8)
### Added
- SAVE & EXIT from the pause menu; the main menu's PLAY becomes CONTINUE (level, room,
  every threat's position and HP, player HP/Firewall and the whole build are restored).
  Autosaves when the app is backgrounded. Saving is locked during boss fights
  ("[BOSS] SAVE BLOCKED") until the boss is defeated. A save is consumed on CONTINUE and
  cleared when a run ends.
- Difficulty picker (EASY / MEDIUM / HARD) for every new run: threat HP and damage,
  € / score payout (x0.8 / x1 / x1.5) and mod luck.
- Mod tiers: BLUE RARE, then PURPLE, GOLDEN and the new TITANIUM, each rarer than the last.
  Luck from depth, difficulty and boss rewards compounds per tier. GOLDEN/TITANIUM cards shimmer.
- More rewards: bosses pay 2 + level/20 picks (plus the level clear) with extra luck; deeper
  levels roll bonus picks. The upgrade screen shows "REWARD 2 OF 4 · 2 more to pick".
- New weapons: LOGIC BOMBS (mines while moving), MALWARE MISSILES (homing, splash),
  ARC DISCHARGE (purple chain lightning), QUANTUM RAILGUN (golden, pierces walls),
  ORBITAL STRIKE (titanium). New titanium/golden mods: TITANIUM CHASSIS, OMEGA OVERCLOCK,
  GOLDEN PROTOCOL.
- 338 new enemy types (data/EnemyVariants.kt): 26 strains (crawler, leech, brute, husk, grub,
  golem, mite, gnat, nanite, locust, spitter, gunner, scatterer, mortar, repeater, ram, lancer,
  bull, marksman, stalker, beacon, spire, sentry, blinker, phantom, glitch) × 13 code families
  (Polymorphic, Encrypted, Botnet, Ransom, Zero-Day, Adware, Spyware, Cryptojack, Forkbomb,
  Keylogger, Backdoor, Stealth, Kernel-Mode). Each has its own name, tag, colour, body accent,
  stats, behaviour twist (packs, splitting, armour, extra shots…) and unlock level, so the roster
  keeps widening through the run. New shapes: pentagon, octagon, star.
- *GLITCHED* enemies (from L25/L32/L42): Daemon, Shard, Hydra. Colour and shape cycle constantly
  with an RGB-split flicker; every attack rolls a different pattern (aimed burst, spread, ring,
  or blink + spread). Sentries and other aimed turrets now track the player.
- *GLITCHED* bosses: 12% of bosses from L20 on. +35% HP, extra random volleys, colour/shape
  flicker, "*GLITCHED*" in the boss bar, ×1.5 €/score and one extra reward pick.
- Arsenal (data/Weapons.kt): 30 new auto-weapons, five per rarity, built on ten behaviours
  (volley, ring, spiral, nova, laser, arc, strike, mines, field, boomerang).
  COMMON: Ping Blaster, Spam Shotgun, Bit Spinner, Static Shock, Cable Whip.
  UNCOMMON: Heartbeat Pulse, Captcha Mines, Traceroute, Packet Fire, Boomerang Byte.
  BLUE: Bluescreen Nova, Subnet Laser, Cluster Bomb, Data Spiral, Tesla Relay.
  PURPLE: Helix Cannon, Malware Swamp, Seeker Swarm, Shockwave, Satellite Lance.
  GOLDEN: Supernova, Prism Laser, Golden Boomerang, Chain Storm, Carpet Bomb.
  TITANIUM: Black Ice, Root Access, Titan Railstorm, Doomsday Daemon, Kernel Panic.
- Upgrades level to 10,000: past a card's designed max (which still unlocks evolutions) every
  pick adds a MASTERY level (weapon +6% damage, orbit +8% node/blade damage, defense +6% HP &
  Firewall, stat +5% damage +1.5% attack speed, utility +5% € and data). Arsenal weapons keep
  scaling damage every level; cooldown and projectile counts level off so the screen stays readable.
- Threat visibility: a faint red ring under every enemy, and enemies hidden behind racks or
  crates show as a pulsing outline on top (x-ray).
- Owned-upgrade icon bar along the bottom of the screen; press and hold an icon for details.
### Changed
- Base movement speed +15% (240 → 276).
- Slow mix: each normal round (not boss or event levels) has a 20% chance to play its music
  at 0.75x instead of 1.25x. Endless rolls per stage.
- PLASMA BEAM is now TITANIUM (ultra rare) and overheats after 5 s of hits into a 3 s
  cooldown, shown on its icon and above the bar.
### Fixed
- Smarter enemy pathing: a flow field toward the player (20-unit grid, Dijkstra, wall-
  avoiding costs, no corner cutting) steers enemies around cover instead of into it; they
  aim at the farthest point down the path they can still see. Stuck sidesteps now pick the
  side that is closer by path. Test: 144/144 chasers placed behind cover in 24 generated
  rooms reach the player within budget, using ~52% of it (93% / 66% before).
- Auto-aim only picks targets its shots can actually reach (no endless volleys into a
  corner of cover).
- Music tempo now reliably returns from boss speed (1.5x) once the boss falls; the rate is
  re-checked until the player confirms it.
### Changed
- Launcher icon is now the store icon art (hooded operative in a neon hexagon) instead of
  the old CyOps-TD-style PCB shield: adaptive icon (art inset to the safe zone on its own
  background colour, feathered edge), themed monochrome layer, and legacy square/round
  PNGs for Android 7. Master art in `docs/brand/app_icon_master.png` (+ 512 px copy).

## 0.6.0 — 2026-10-08
### Added
- Visual pass to match the key art (no false advertising): bevelled metal floor panels,
  glowing cyan trenches with running packets, grates, lit service panels, bolts, light
  pools under hardware, drifting data pixels; taller server racks with dense glowing LED
  bays and neon edge trim; X-braced hardware crates; glowing projectile trails (red-orange
  hostile streaks); neon CYBER OPERATIVE title with the "ENDLESS CYBER ROGUELIKE ACTION" line.
- PLASMA BEAM (Rare weapon upgrade, 3 levels): an arm cannon that pours a continuous,
  piercing cyan beam into the target while the operative stands still; stopped by cover.
- CYBER OPERATIVE wordmark (`ui/common/CyberTitleLogo.kt`): vector letterforms with
  gradient faces, scan cuts, extruded depth, neon halo and a circuit-trace frame; scales to
  any size and pulses on the main menu. PNG exports in `docs/brand/` (3200x1344, transparent
  and on dark) via `LogoRender` (`-PrenderPreviews`).
- Main-menu emblem (`ui/common/OperativeEmblem.kt`) after the app icon: hooded operative
  with headset and antenna in a neon hexagon with circuit traces; the face shield is the
  equipped skin. Replaces the bare shield above the title. Export: `docs/brand/menu_emblem.png`.

## 0.5.1 — 2026-10-08
### Added
- NEON OPERATIVE — a fourth full-body operative built on the app icon's geometry:
  faceted neon-blue hood with a pointed crown, glowing green >_< shield face, antenna with a
  lit tip, ring earpiece, shoulder plate with the green power button, raised collar, cyan
  neon aura. Bought in STORE → OPERATIVES (◇100) or included in the ◇300 skin pack; selected on OPERATIVE.
### Changed
- Package name confirmed final: com.cyberoperative.game.

## 0.5.0 — 2026-10-08
### Added
- Music runs faster in a fight: 1.25x in levels and events, 1.5x on boss levels (time-
  stretched at playback, pitch unchanged); full original CyOps TD tracks ship untouched.
- Big boss health bar at the top: boss tag, name, title, phase, percent, phase markers at
  60% / 25%, white damage trail.
- Boss entrance: the bar grows from the centre and fills, the name glitches in, a broken
  8-bit growl plays with a screen shake, then the fight starts (boss inactive until then).
- Release signing from `secrets.properties` and RELEASING.md (how to build the .aab and
  push it to Play testers).

## 0.4.1 — 2026-10-07
### Added
- Full CyOps TD soundtrack (24 tracks: 20 level, 2 menu, 2 Liminal), re-encoded to 128 kbps.
- Shuffle playback with no repeats until every track has played; menu, combat, boss
  and game-over rotations.
- Music player on the pause screen: previous / play-pause / next, SHUFFLE, AUTO, and a
  tap-to-play track list.
### Fixed
- Music and effects can no longer keep playing when the app is minimized: every start
  path checks foreground + audio focus; SFX streams pause with the app; calls/other apps
  (audio focus) and unplugged headphones pause music; leaving mid-fight opens the pause menu.

## 0.4.0 — 2026-10-07
### Added
- CAMPAIGN as the main mode: fixed threats per level (8 → 14 → … capped at 45) with a
  THREATS x/N bar, a power-up after every clear, a gate in the top wall that opens, and
  the next room sliding in from above.
- Procedural rooms: 6 layout styles × 5 hardware kits, small blinking server units,
  hardware crates and floor decoration, validated walkable every time.
- ENDLESS mode (menu button): one room, continuous spawns, stage every 30 s, boss every
  10 stages, data-driven upgrades mid-fight, separate records.
- 2.5D rendering: extruded obstacles with shadows, hovering enemies with tracking eyes,
  airborne projectiles and orbs, depth sorting, wall of server racks with the exit gate.
- Three full-body operative designs (Field Agent, Sentinel Mech, Shadow Runner), all
  skinnable; chooser on the OPERATIVE screen.

## 0.3.0 — 2026-10-07
### Added
- Store with the owner's structure: ◇ packs (◇150 $1.00 · ◇500 $4.50 · ◇1000 $8.50 ·
  ◇10000 $69.99), revive packs (1/◇100, 7/◇500, 20/◇800, 250/◇6500), operative skins
  (◇100 each, all for ◇300) and living backgrounds.
- 17 operative skins: the >_< shield recoloured in every CyOps TD agent colour (Firewall,
  IDS, Blue Hat, Analyst, Cryptographer, Zero-Day Hunter, Red Hat, Ace, Anti Duck hologram,
  Spectrum) and the CyOps TD core-skin palettes (Reactor, Meridian, Glacier, Mainframe,
  Cascade, Neongrid, Void). Shown in game, on the menu and in the SKINS screen.
- 7 living backgrounds ported from CyOps TD (Drift, Lattice, Aurora, Rainfall, Pulse,
  Orbit, Heatmap) drawn on the arena floor.
- Revive tokens: game over offers the free revive, then a token, then ◇100; at most 3 paid
  revives per run.
- Store tests (price table, ownership, old-save migration).
### Notes
- ◇ packs credit test ◇ in debug builds only; release builds wait for Google Play Billing.

## 0.2.0 — 2026-10-07
### Added
- Project bootstrap (Kotlin/Compose, portrait-only, Gradle wrapper, version catalog).
- Startup: HamieTon.dev ident → animated INITIALIZING terminal (fictional boot log,
  progress bar, glitch, ACCESS GRANTED) → main menu with living network background.
- Core gameplay: floating bottom joystick, stop-to-fire auto-targeting (LOS-aware,
  threat/boss priority), orbiting Packet Nodes, obstacle collision and line of sight.
- 11 data-driven enemy types with 7 AI behaviours (chaser, swarmer, shooter, charger,
  sniper, turret, teleporter) and 6 elite modifiers.
- 12 arena templates (+ mirrored variants) with animated server racks, firewall nodes,
  pillars, cooling units, routers, terminals, power units, fiber junctions.
- Endless level flow: waves → clear → upgrades → access port → next arena; endless scaling.
- 38 run upgrades across 5 rarities incl. evolution chains (Packet Nodes → Autonomous
  Defense Node, Firewall → Zero Trust Fortress), cone attack, Exploit Lance, Encryption
  Blades, EMP, chain, pierce, bounce, multishot…
- 12 bosses (every 10 levels) with 3 phases each and 11 pattern types (radial, aimed,
  spiral, charge, summon, shock ring, blasts, corruption zones, teleport, beams, homing).
- 5 random event levels (Packet Flood, Firewall Breach, Malware Swarm, Data Vault,
  Zero-Day Anomaly) with spectrum border.
- € economy + 19 permanent upgrades, Operative XP/level, 16 achievements, local records,
  settings, about, armory codex, free limited revive, score formula, save system.
- Synthesized SFX for all cues, music states (menu/combat/boss/event/game over).
- Tests: scaling, data integrity, arena reachability, upgrade rules, bot playthroughs
  (every boss and every event completed by the automated player), screenshot previews.

## 0.1.0 — 2026-10-07
- Repository created.
