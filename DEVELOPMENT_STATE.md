# Development State

_Read this first in every new session, then README, GAME_DESIGN, BACKLOG, CHANGELOG, git log._

**Active stage: Boss Expansion Vol. 1 — progress and next step live in `docs/BOSS_EXPANSION_LOG.md`.**

| | |
|---|---|
| Version | 0.55.0 (versionCode 154) |
| Milestone | M1 vertical slice ✅ · M2 first boss & systems ✅ (pending device playtest) |
| Last completed | 0.9.5 fullscreen HUD redesign (ui/game/GameHud.kt, buff chips in UpgradeBar.kt) |
| Current task | Waiting on owner: body design (CO-094) and Power-Up Board picks (CO-090) |
| Build status | `./gradlew :app:assembleDebug` ✅ |
| Test status | `./gradlew :app:testDebugUnitTest` ✅ (32 tests + 15 opt-in screenshot renders) |

## Known issues / caveats
- **0.55.0 translations:**
  - Every player-facing line goes through `tr("English {0}", args)` (`i18n/Tr.kt`). New text must be wrapped too.
  - After adding or changing lines, regenerate `app/i18n/en.properties` with `I18N_WRITE=1` and add the line to all 25 `resources/i18n/<tag>.properties` files, or `I18nCatalogTest` fails.
  - The first-launch picker shows only when no language has been chosen (`Languages.needsPicker()`); `Languages.start(saveExisted)` marks updates over an old save as already chosen.
- **0.54.0 music:**
  - `TrackGroup.ARCADE` (10 tracks, `track_arcade_*.ogg`) comes first in `MusicLibrary.all`.
  - It is in every `poolFor` state (MENU, COMBAT/EVENT, BOSS, Inferno too), and the boss pools of both audio managers append `MusicLibrary.arcade`.
  - `MusicTrack.fixedTempo` (NEON + ARCADE) pins speed and pitch at 1. `noEq` (NEON + ARCADE) skips the boss EQ. `plain` (NEON only) still means no reverb and no boss rounds.
- **0.53.3:** the counter egg is drawn at `SHOP_EGG_PX` 68, against `SHOP_ITEM_PX` 58 for gear. versionCode jumped from 148 to 150 at the owner's request.
- **0.53.2:**
  - `ArenaGenerator.counter()` is 340 × 170 at y 236–406. `shopRoom` places 1–2 fixtures (≤ 60 wide) flush against the walls, never inside `ArenaGenerator.shopKeepOut()` (the counter ±120, from y 0 to its bottom + 320).
  - `ArenaRenderer` SHOP_COUNTER draws `GameEngine.shopGear` (`cur`) as [ : egg : ]: gear 0/1 on the left (back/front), gear 2/3 on the right, the egg in the middle, at `SHOP_ITEM_PX` 58 with `tableGlow`.
  - The chips are no longer on the table; they are only in the panel.
  - Co-op `VERSION` 37.
- **0.53.1:**
  - The counter egg is drawn in `ArenaRenderer` (SHOP_COUNTER) from `PropRoom.shopEgg`, which comes from `GameEngine.shopEgg` (`cur.shopEgg`, i.e. the local player's).
  - Sizes: `SHOP_EGG_PX` 38 and `SHOP_EGG_GLOW` 0.45, using the `drawLootBeam` glow clipped to the table.
  - `debugEnterShop()` exists for renders.
- **0.53.0 shop egg:**
  - `GameEngine.rollShopEgg` runs in `enterShop` and sets `Operative.shopEgg` (`EggShopItem`).
  - Without Inferno, `SHOP_EGG_CHANCE` (0.3) applies and the rarity uses `Gear.rollRarity(boss = true)`. With Inferno there is always an egg, using `EggOffers.rollRarity`.
  - Price = 2 × `Pets.value(rarity, gearLevel)`.
  - `buyShopEgg` / `buyShopEggFor`; the guest sends `shopIndex = SHOP_EGG_INDEX` (200).
  - `NetOp.shopEgg` / `shopEggPrice` / `shopEggSold`; co-op `VERSION` 36.
- **0.52.0:**
  - The `diamonds_5000` product and Steam itemdef 1005 must exist in Play Console and Steamworks.
  - `Pets.value(rarity, level)` = 10 × `Gear.sellPrice` of a weapon. Selling goes through `Incubators.sellPet` / `sellEgg`.
  - `PlayerProfile.vaultPets` / `vaultEggs` hold vaulted pets and eggs (`Pets.VAULT_SIZE` together).
  - Trade packs eggs and pets into `TradeOffer.items` (`TradeOffer.pack`, separated by `\u001E`; codec v35).
  - `PetsTab(inRun)` shows `CompactIncubators`. `MAX_EGGS` is 1000.
- **0.51.0 pet swap:**
  - `GameEngine.setPet(index, loadout)` swaps the pet. `GameSession.applyPet()` calls it for solo and the host; the guest instead bumps `CoopInput.petSerial` and sends `pet`.
  - `NetOp.pet` carries both pets in each snapshot (co-op v34).
  - `PetsTab(inRun = true)` hides the incubator panel and calls `onPetChanged`.
  - `EggOffers` prices: 100 + 50 per rarity above GLITCHED.
- **0.50.2:** `ArenaRenderer.PLAYER_SHOT_LOOK` (0.5) scales how friendly projectiles are drawn; `Projectile.radius` (hits) is unchanged. `ELITE_HP_DROP` is 0.165 and `IDLE_ELITE_HP_DROP` is 0.04.
- **0.50.0:**
  - Daily eggs: `PlayerProfile.eggOffers` and `eggOffersAt`, handled by `StoreManager.eggOffers` (rolls when empty or older than 24 h) and `buyEggOffer` (◇1500; buying the last one rolls three new). Wall clock: changing the time only re-rolls offers, which still cost ◇.
  - PET CODEX: `PlayerProfile.petCodex` (art → `PetDexEntry`) is recorded by `Incubators.dex` on every hatch. `Incubators.codex(p)` adds the pets already owned.
  - `AnimatedEgg` and `rememberSlowClock` live in PetArt.kt; `FigureAt` in OperativeFigures.kt.
- **0.49.1:**
  - `Pets.NEW_COUNT` is 1350.
  - Pet scripts run in this order: `gen_new_pets.py` → `gen_gx_pets.py` → `gen_more_pets.py`. The last one fills page 8 from cell 26, then page 9.
  - New draw parts in `draw_pet` (cube/twin/pear, wheels/tentacles, x/ring/pixel, fin/mohawk/sat/horns_tech) are only used by `gen_more_pets.py`.
  - The GLITCH part's colour sets the first ghost's colour in `PetArt.glitchCell`.
  - Idle eggs live in `IdleRunState.eggs`, rolled on their own stream (`rng(13, kills)`), and are collected with `Incubators.addEggs`.
- **0.49.0 GLITCHED ENCRYPTED pets:**
  - `Pets.NEW_COUNT` is 1050 and `Pets.TOP` is GLITCHED_ENCRYPTED.
  - They come from `tools/pets/gen_gx_pets.py`, which must run AFTER `gen_new_pets.py` (that script rewrites `new_pets.txt` and pages 4–7). It fills page 7's cells 232–255 and page 8.
  - `PetAnim.Part.gx` uses the `RarityFx.gxColor` colour. `Kind.GLITCH` is drawn by `PetArt.glitchCell`.
- **0.48.1:**
  - `squish` (GameScreen) is true for portrait, not desktop, and `settings.compactHud`. Landscape always pads the arena under its strip (`hudAbove = landscape`).
  - `TouchLayout.seeThrough` (from `settings.seeThroughButtons`) picks between opaque and see-through drawing for buttons, the stick and INV/MAP.
  - `HudSnapshot.bossHp`, `bossMaxHp` and `bossBreaks` (the phase `below` values) feed `SquishBossBar`.
  - SquishHud auto-folds through `UpgradeBar(onInteract, onDetails)`.
- **0.48.0 UI SQUISH:**
  - Phones use `SquishHud` (SquishHud.kt) in both orientations. `GameHud` and `CompactHudStrip` are no longer used on phones; PC still uses `PcHudStrip`.
  - `onBaseHeight` reports the folded card's bottom edge as `hudHeightPx`.
  - `hudAbove` (landscape and desktop) is the only case where the arena is padded below the HUD.
  - Bosses: `SquishBossBar` is the first item in the hint column, between ■INV and MAP.
- **0.47.3:**
  - `squishDamage` (Widgets.kt) is applied in `ArenaRenderer.drawTexts` to all-digit NORMAL/CRIT/BOSS texts when not `AppInfo.desktop`.
  - All three HUDs use `UpgradeBar(compact = true)`.
  - Hint banners take their side padding from the measured widths of the INV and MAP buttons (`cornerButtonPx` / `mapButtonPx`).
  - `DevShots` has `hud_buffs`.
- **0.47.2 portrait HUD:** `GameHud` has no panel background. `Modifier.plate()` puts a dark plate behind each piece of text. `UpgradeBar(compact = true)` is `CompactUpgradeGrid`: a `LazyHorizontalGrid`, 3 rows of 12 dp chips. Landscape and PC still use the single-row bar.
- **0.47.1:** the Plasma Beam is drawn at `BEAM_LOOK` (15%) of `GameEngine.beamWidth` in `ArenaRenderer.drawBeam`. Its hit width is unchanged.
- **0.47.0 GLITCHED ENCRYPTED:**
  - It is the last `GearRarity`, with `artTier = GearArt.GX_TIER` (9). Its art is a separate sheet, `<slot>_gx.webp` (`GearIcons.gxAtlas`), drawn at cells `0..count-1`.
  - PRIMARY art: 0–49 are shared rifles, then 50 per class in `GunStyle.classed` order. Use `GunStyle.forArt(art, rarity)`.
  - The animated colour is `RarityFx.gxColor(t)` (composable: `rarityColor(r)`). Its static `color` is only a fallback.
  - Pets cap at `Pets.TOP` (ENCRYPTED) and loop over `Pets.RARITIES`.
  - `gearLook` packs 10 art bits (co-op version 33).
- **0.46.0 auto-sell:**
  - The setting is `GameSettings.autoSellBelow: GearRarity?`. `GearBank.autoSell(items, below)` returns the kept items and the €.
  - It's applied in `GameSession.bankGear` (it skips `Operative.gearBought`, the shop purchases) and in `IdleRuns.collect`. Trades go through `GearBank.bank` directly, so they're never sold.
  - The UI is `AutoSellCard` / `AutoSellOptions` in MetaScreens. Robolectric doesn't draw popups, so `AutoSellRenders` renders the list on its own.
- **Adding music:** a new track needs its `.ogg` in `res/raw`, a `MusicLibrary` entry, AND a line in `rawMusic` in `platform/Platform.kt` (Android). `MusicFilesTest` fails if one is missing (the NEON tracks were silent until 0.45.3).
- **0.45.0 incubators:**
  - `Incubator.slots` holds 3 `IncubatorSlot`s (egg + progress). The legacy `current` / `progress` / `queue` fields are only read by `Incubators.migrate`, which runs inside `tick`.
  - `credit` advances every slot. `send` uses the first empty slot. `instantHatch` and `instantHatchWithEuros` take a slot index.
  - Reminders: `PetReminders.schedule(slot, at, name)` → WorkManager `pet_hatch_<slot>`, notification id `4108 + slot`.
  - The hatch reveal is `HatchReveal` in PetsTab (timeline: crack at 1.7 s, card at 2.4 s). Its size is `scale` = 1 + rarity ordinal / max (1× COMMON … 2× ENCRYPTED; owner 0.45.1). At the burst it plays `GameSound.PET_HATCH` through `UiSound` (0.45.2). `UiSound.player` is set in CyberOperativeApp and in desktop Main.
  - `Incubators.seenOne` handles reveals one at a time. `Pet.eggArt` / `hatchedFrom` give the egg picture.
- **0.44.0 operator redesign:**
  - `FigureShade` is applied in `ClassFigures.fillStroke`, `limb`, the lifted `look()`, and the hero figure and helmet in `OperativeEmblem`.
  - The Phantom's hood, cape and torso use `rawLook` + `flatStroke`. The Mage draws with `matte` set and has robe `folds`.
  - `FigureShade.enabled = false` brings back the flat look.
  - The concept tools live in `tools/operators/`, and the approved sheets are `docs/operators_concept/v2_*.png`.
- **0.43.0 € hatch:**
  - Price: `Pets.instantHatchEuros` = `Incubators.accountOpLevel` (max over every character) × ◇ price × `HATCH_EURO_MUL` (100).
  - Action: `Incubators.instantHatchWithEuros`.
- **0.42.0 instant hatch:**
  - Price: `Pets.instantHatchPrice` (`HATCH_PRICE_MUL` 4, owner-approved).
  - Action: `Incubators.instantHatch` ticks first, charges from the real remaining time, then hatches through `credit`.
  - The UI price is computed from the live countdown, so the amount charged is never higher than the price shown.
- **0.41.0 animated ENCRYPTED pets:**
  - New art indices 1524–2023 (pages 5–7).
  - `Pets.anim(art)` returns the moving parts. `PetFx` (Kotlin) and `render_anim` (Python, used for the review sheets) must stay in step.
  - `PetIconView` runs an infinite transition only for animated pets.
- **0.40.0 new pets:**
  - 500 generated pets and eggs on atlas pages 4–5, at art index 1024 + k. Run `python3 tools/pets/gen_new_pets.py` to regenerate them (the output is the same every time).
  - `Pets.rollEgg` gives GLITCHED+ eggs a new pet of that tier, with matching egg art.
  - The original 1,005 pets and 872 eggs are unchanged; GLITCHED/ENCRYPTED no longer borrow old egg pictures.
- **0.39.1 (owner):**
  - `GameEngine.normalLate`: MEDIUM only, past level 100, HP +1.2%/level (cap ×2.5), damage +0.6%/level (cap ×1.6).
  - `hardEase` is now uncapped above 1, so HARD stays ≥ `HARD_OVER_NORMAL` × NORMAL ("Hard rises with Normal").
  - **SECRET, keep it out of the game, store text and public notes:** on HARD, each player's boss gear has one piece forced to GLITCHED with chance `hardBossGlitched(world)`:
    - 100% on Worlds 1–3, then −20% a world (W4 80%, W5 60%, W6 40%, W7 20%, W8+ 0%);
    - past that, only the normal ~2.2% boss odds apply;
    - Endless uses world = level / 10 + 1;
    - code: `dropGear` in GameEngine.
  - Idle: HARD is selectable (`IdleRunSimulator.choices`); GLITCHED/ENCRYPTED rolls become LEGENDARY; idle uses `GameEngine.hardEase` / `normalLate`.
- **0.39.0 power tiers / Hard balance:**
  - Rarity order:
    - gear: … LEGENDARY, GLITCHED, FORGED … ABYSSAL, ENCRYPTED;
    - chips: … GOLDEN, GLITCHED, TITANIUM, FORGED … ABYSSAL, ENCRYPTED.
  - Gear ENCRYPTED is saved as `ENCRYPTED_X`, because "ENCRYPTED" on disk is FORGED.
  - Golden+ +30% is `GearRarity.statBoost`, applied in `Gear.apply` and shown via `GearItem.effectiveBase`.
  - Drop odds: `Gear.GLITCHED_BOSS` 2.2% and `ENCRYPTED_BOSS` 0.25% per boss piece (elites ×0.25, ×(1 + 0.15·INFERNO) and luck).
  - Chip weights: GLITCHED 1.5 … ENCRYPTED 0.16; `highTier` is capped at 3.
  - Engine hooks:
    - `onBoltHit`: healOnHit, primary BOLT hits only;
    - the fire gate: `moveFireRate`;
    - `dash`: `dashCooldownMul`;
    - `drinkPotion`: full heal / potionMul;
    - `damagePlayer`: FAILSAFE auto-potion (≤20% or lethal, lethal ignores the potion cooldown);
    - `updatePet`: `petRateMul` and abilities.
  - Hard: `hardEase` scales `opHpNow` / `opDamageNow`, from World 3 to −25% by World 5 (Endless: level/10 + 1 as the world). The floor is `HARD_OVER_NORMAL` 1.1 × NORMAL.
  - HARD chips: `chipBudget` 2 a room, luck +0.35, boss picks ≥ 3.
  - Potions: boss 3 per player, elite 33% (Idle keeps 0.5 / 0.08).
  - The `BalanceCurveTest` floor was lowered from 1.5 to 0.4 hits per basic threat: a damage-greedy NORMAL build can one-shot basic threats late now (owner asked for near game-breaking chips). Watch on device.
  - Pet abilities: `Pets.abilitiesFor(uid, art, rarity)` (deterministic), `GameEngine.usePetAbility`, per-op `petAbilityCd` / `petRapid`.
  - Co-op loot fix: `CoopWorld.loot` entries carry the drop's age at index 5 (codec v32), and `mirrorTick` ages it. `LootSprites` pop-in and ghost size depend on it. Test: `CoopLootOwnerTest`.
  - Hatch notification: `PetReminders.update` (PetsTab and MainActivity.onPause) → `PetHatchWorker` (WorkManager "pet_hatch", channel "pets").
- **0.38.0 companions:**
  - Pets, eggs, the incubator and stones are account-wide (top-level profile fields).
  - Incubator time:
    - `Incubators.clockStep` credits the since-boot clock (`PlatformClock.mono`) within a boot.
    - Across a reboot / PC restart it needs `TrustedClock.networkNow()` (an HTTPS Date header, read in the background) and waits until then (`waitingForTime`).
    - If the anchor was never network-verified, the time credited across a reboot is capped at 8 h.
  - Pets are drawn by the renderer from a smoothed follow position (so co-op guests see them); the host's engine fires the shots (NODE_BOLT projectiles).
  - Store egg prices and odds are in `Pets.StoreEgg` (defaults, not yet owner-approved).
- **0.37.5 NEON tracks:** `TrackGroup.NEON` tracks are in both COMBAT pools (normal and INFERNO), never in BOSS/MENU pools; `MusicTrack.plain` means 1.0× speed and pitch and no EQ or reverb; a pinned NEON track is dropped when a boss round starts.
- **0.37.0 Idle Runs (Android only):**
  - Shared: `idle/IdleRunSimulator` runs the real formulas (buildConfig + chips, LevelPlanner rooms, PowerGovernor, Scaling, boss scaling from BossBrain) one threat at a time. Tuning constants are at the top: `SKILL_MEAN` 0.70 ± 0.12, `BOSS_SKILL` 1.1, `HIT_TAKEN` 0.058, `BOSS_HIT_TAKEN` 0.029, `MAX_CROWD` 3, `ROOM_HEAL` 0.32 (eased twice on owner request: 0.37.1 ≈ ×2 duration, 0.37.2 ≈ ×2.2 more), HP charges at 40%. These were calibrated against the test `Bot` (idle reaches a bit further than the simple bot); a device playtest should confirm the "competent player" target.
  - Rewards: `EURO_SHARE` 0.4 of the €, `REWARD_SHARE` 0.5 of the XP (0.37.4).
  - Gear: normal rolls, but only `MIN_DROP_RARITY` (EPIC) and better are kept (0.37.3), and only `GEAR_KEEP` 80% of those (0.37.4).
  - Every random draw is seeded from (seed, level, event), so one big catch-up equals many small syncs (`catchingUpInOneJump…` test). Runs stop after 12 h (COMPLETED).
  - Android: `widget/` package (excluded from the desktop build). The run lives in its own prefs file (`cyber_operative_idle_run`); the profile is only touched by `IdleRuns.collect` on the main thread, when the Idle Runs page is open.
  - Widget drawing runs on the main thread (it shares EnemySprites/renderer state with the live game). `AndroidIdleHost.sync` releases its lock before drawing.
  - WorkManager: periodic 15 min plus a one-off at the predicted end (the sim is deterministic, so the end time is known ahead).
  - A widget tap during a game in progress does not leave the game (it would lose the run); from anywhere else it opens Idle Runs.
- **0.37.0 expansion mesh rigs:** `expansionRigs` in `BossMeshBodies.kt` (11 bosses). Circuit Hydra is deliberately not meshed (engine-placed heads).
- **0.36.3 co-op wallets:**
  - With `perPlayerWallets`, wallet = `startEuros` (saved €, the guest's from `RoomPlayer.euros`) + `eurosIncome` (0.36.4: per player, via `earn(amount, to)`) − `eurosSpent`.
  - Banking uses `eurosNet`. Trades adjust `eurosSpent` on the host, and the guest reads it from `NetOp` (v29).
- **0.36.2 classic mesh rigs:**
  - `BossBodies.forId` wraps a code-drawn body that has a `classicRigs` entry in `MeshedBody`, which draws it offscreen (2.2 px/unit, ≤900 px, 1.5× its preview square) and then through the mesh.
  - 0.37.0: the Boss Expansion bosses have rigs too (`expansionRigs`), except Circuit Hydra.
- **0.36.0 INFERNO boss redesign:**
  - The bodies are the owner's painted sheets (`resources/bosses/inferno_*.webp`) plus `InfernoRig` animation in `InfernoBodies.kt` (0.36.1: a deformable mesh with per-part `Deform`s, drawVertices on PC / Android 10+, tiles below).
  - Status per boss and the follow-ups: `docs/INFERNO_BOSS_REDESIGN.md`.
  - Recorded SFX live in `resources/sfx/` (`SoundBank.files`).
- **0.35.0:**
  - INFERNO bosses are `InfernoBosses.all`, kept out of `Bosses.roster`; `byId` finds them, and their bodies are in `InfernoBodies.kt` (waiting on owner approval).
  - Trading: the host runs `TradeDesk`; the guest sends `CoopInput.trade*`. Potions move on the host, while € and items are applied by each side in `GameSession.checkTrade`.
  - Guest trade actions ride on the unreliable input stream (latest serial wins), so a dropped packet can lose one click.
- **INFERNO rarities (0.33.0):**
  - FORGED / VOLTAIC / CELESTIAL / ABYSSAL replaced ENCRYPTED / INFERNAL / CRYPTO; the old names stay valid on disk via `@SerialName`.
  - Odds: `Gear.odds` uses base × INFERNO level for those four (capped at 60% together), then COMMON…LEGENDARY share the rest.
  - Waiting on the owner: the drop sound choice (A–D).
- **INFERNO 1–500 (0.34.0):**
  - `Difficulty` is a class, with levels made by `Difficulty.inferno(n)` (cached).
  - Curve A: HP 2.175·1.012^(n−1), damage 1.75·1.006^(n−1).
  - Loot extras are capped at level 10; the four INFERNO rarities saturate at the 60% cap by about level 25.
- **Shared Data Vault (0.34.0):** `p.vault` is account-wide. `Characters.active/load` no longer swap it, and `StoreManager` merges old per-character vaults.
- **Gear and characters (0.30.0, Data Vault phase 2–3):**
  - Data:
    - `data/Gear.kt` has the item model, odds, rolls and `Gear.apply` (used in `Operatives.buildConfig(…, gear)`).
    - Art comes from the `resources/gear/<slot>.webp` atlases (96 px cells, 16 columns, tiers in order). `GearArt` has the counts per tier; regenerate both together if the sheets change.
  - Characters:
    - The active character lives in the profile's top-level fields; `Characters.switchTo` swaps it with one in `otherCharacters`.
    - First boot without a character goes to `Screen.Create(first = true)`.
  - Engine and co-op:
    - Drops use `gearRng`, so existing seeds and balance are unchanged.
    - Co-op v25 sends a gear drop's look in the loot float (`gearLook`), plus the guest's last 6 finds (`guestGear`), merged by uid.
    - In-run equips re-base solo only (`GameSession.applyGear`).
  - Not done yet (Phases 4–5):
    - chips as loot (5→15 slots) and retiring the in-run chip/card system;
    - rarity tint on the operative's held weapon;
    - the partner's look in co-op;
    - a balance pass for the grind.
- **INFERNO (0.29.0, 1–500 since 0.34.0)**: `Difficulty.inferno(n)` (`inferno` level).
  - `PlayerProfile.infernoUnlocked` is raised in `GameSession` when `campaignFinished`, via `unlocksInferno`.
  - The look is renderer-only, so co-op stays in sync:
    - `Environments.inferno` themes, picked by `styleFor(…, infernoTier)`; 12% of rooms stay normal.
    - `ArenaRenderer.drawInfernoDecor` draws claw marks, broken towers and wall fire, seeded per room.
    - `hellLight` recolours fixed lights, plus the vignette.
    - Bosses get `hellTint` and `drawHellAura`; enemies use `EnemySprites.hell`.
- **Data Vault update, in progress** (see `docs/DATA_VAULT_PLAN.md`). Phase 1 shipped in 0.28.0:
  - `data/OpProgress` has the OP Lv 1–60 and Overclock 1–300 curves.
  - `engine/Armor` applies the Diablo 3 formula.
  - Monster level is `GameEngine.monsterLevel`.
  - Run XP is `opXpEarned`, banked by `GameSession`.
  - Firewall is removed.
  - The € wipe migrates via `PlayerProfile.dataVaultWipe`.
  - The UPGRADES screen is now the OVERCLOCK board, bought with points.
  - Next: Phase 2 (loot core).
- **World server (0.27.0)** replaces the next-world wall gate.
  - `placeWorldServer()` runs on boss death (`campaignCleared`), on revisiting a beaten boss room (`enterRoom`), and on restoring a save.
  - `pressNextWorld(index)` sets `Operative.worldReady`. The transition starts once every player still in the run is ready.
  - Readiness clears in `update` (PORTAL) when a player is out of `WORLD_SERVER_REACH`, and on shop entry.
  - Co-op v23 carries the server and each operative's `worldReady`; the guest presses through `CoopInput.nextWorldSerial`.
- **Co-op shop (0.26.0)**:
  - Each Operative has its own `shopItems` and `shopReady`. `GameEngine.shopItems` is the current operative's stock.
  - The guest buys through `CoopInput.shopSerial/shopIndex/shopReplace`, applied on the host by `buyShopItemFor`.
  - The snapshot (v22) carries inShop, shopSeed, keeper, the shop side gate, and each operative's stock and ready flag.
  - In the shop, `portalCheck` waits until every player still in the run stands in the exit gate.
  - The "skip the shop?" prompt is solo-only.
- **Fixed in 0.25.4: crash on every run start (0.25.x, Android).**
  - A just-dropped chip's glow had radius 0 on its first frame, and Android throws "ending radius must be > 0".
  - Every radial glow now goes through `ui/common/SafeBrush`. `ZeroRadiusGlowTest` fails if a raw `Brush.radialGradient(` comes back.
  - `CrashLog` (the crash report shown on the next launch) and `-dontobfuscate` stay in, for any future crash.
- **Skins 0.25.2**:
  - PURPLE and GLITCHED unlock for every class at `campaignsCompleted > 0` (`SkinUnlock.campaign`). PURPLE also stays unlocked for anyone who earned it at L100 on Hard.
  - GOLD and NEON are premium: `OperativeSkin.price` is ◇1000, and neither is in the ◇300 pack.
  - NEON's colour is `PlayerProfile.neonColor`. It travels to co-op as the token `neon_custom#RRGGBB` (`OperativeSkins.token` and `byId`).
- **Boss loot spray and per-player co-op loot (0.25.1)**:
  - Boss drops queue up and spray out every `GameEngine.SPEW_INTERVAL` (0.1 s), sliding 70–170 units.
  - The interval runs on game time, so the kill's slow motion stretches it a little.
  - Co-op loot has an `owner`: only the owner picks it up, and the partner's drops render as a colour-only glow (`LootSprites.ghost`).
  - CoopCodec VERSION is 20.
- **Steam / PC build (0.20.0, `desktop/`)**:
  - Compiles and runs. Offscreen renders and the packaged app booting under Xvfb were checked.
  - Steam itself (overlay checkout, Inventory claim, lobbies/P2P) and real controllers can only
    be verified on the owner's PC or Steam Deck.
  - It uses test App ID 480 until the real one exists.
  - Rooms are landscape on PC only (`ArenaShape.wide`); phones are unchanged.
- 0.16.0 balance: mastery is pooled (Upgrades.applyMastery). The hidden PowerGovernor sets HP and
  damage floors from the team's potential DPS on Normal and Hard. BalanceCurveTest guards both.
  Needs a device playtest at L100+ to tune the floors (enemy 0.3 s, boss 60 s).
- Not yet run on a physical device or emulator (no emulator in the cloud session).
  Rendering was verified with Robolectric native graphics (docs/screenshots/).
- Release .aab is ~60 MB: the full 28-track soundtrack as Ogg Vorbis (~92 kbps, re-encoded from the MP3 masters in 0.9.6).
- The minified (R8) release build compiles but has not been run on a device yet.
- Every boss (12 classic + 12 expansion) has a unique body (ui/game/BossBody*.kt, registry in BossBodies.kt).
- ◇ packs: debug builds grant test ◇; release shows "billing not connected" until CO-070.
- Owner picks for power-up names/icons live in the Power-Up Board artifact db (`picks/main`).
- Balance: a simple dodging bot with no permanent upgrades averages ~level 6 in campaign.
- The top wall/gate sits under the translucent HUD until the player walks up.
- Co-op 0.10.0 is tested end-to-end in-process (two sessions over a loopback room, real wire
  format) but not yet over real Firebase or on two devices. The owner must create the Firebase
  project (FIREBASE_SETUP.md). Guest movement is client-trusted (fine between friends).
  Backgrounding the host's app stops the simulation; after 8s the guest carries on solo.
- HUD 0.9.5: immersive mode, notification-shade fade and chip tooltips (Popup) can't be seen in
  Robolectric renders; confirm on a device. Previews: hud_upgrade_bar, hud_collapsed,
  hud_narrow_320dp, hud_upgrade_bar_boss, pause_save.

## Important architecture decisions
- **Same stack as CyOps TD**: Kotlin + Compose; no game engine. Arena drawn on a Compose
  Canvas from a pure-Kotlin simulation (`engine/`) that has no Android dependency, so
  the whole game is unit-testable and a test Bot can play it.
- Fixed 1/120 s sub-steps, frame delta clamped to 0.1 s. Clock lives in `GameScreen`
  (leaving the screen pauses the sim).
- Entities are pooled mutable classes; renderer avoids per-frame allocation.
- Content is data: `data/` holds enemies, bosses (phases/patterns), upgrades (apply
  lambdas on a stats object rebuilt from scratch), arenas, events, permanent upgrades.
- Progress commits are deltas (on death, quit, new run) so revives never double count
  and nothing is lost if the app dies on the game-over screen.
- Save: JSON `PlayerProfile` in private SharedPreferences; unreadable saves are moved
  aside, never deleted.
- Router: sealed `Screen` + one state value (`ui/AppRoot.kt`), as in CyOps TD.

## Files recently modified
Latest: `ui/common/OperativeEmblem.kt` (menu emblem), `ui/common/CyberTitleLogo.kt` (vector wordmark, used on the main menu; PNGs in `docs/brand/`).
Everything (initial build). Key files: `engine/GameEngine.kt`, `engine/EnemyAi.kt`,
`engine/BossBrain.kt`, `engine/LevelPlanner.kt`, `data/*.kt`, `ui/game/*`.

## Next recommended task
Read the owner's picks (ArtifactData get picks/main on https://claude.ai/artifact/8fjDBEvGFPJp68SNZUZgQP) and apply them (CO-090/091). Otherwise:
CO-060: install `app/build/outputs/apk/debug/app-debug.apk`, play levels 1–12, then tune
`core/Scaling.kt`, `RunStats` defaults and enemy data. Then CO-061..066.

## User decisions pending
See BACKLOG.md → USER DECISIONS REQUIRED (package ID, ◇ pricing, revive policy,
character art, ads).
