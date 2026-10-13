# Development State

_Read this first in every new session, then README, GAME_DESIGN, BACKLOG, CHANGELOG, git log._

**Active stage: Boss Expansion Vol. 1 — progress and next step live in `docs/BOSS_EXPANSION_LOG.md`.**

| | |
|---|---|
| Version | 0.30.4 (versionCode 97) |
| Milestone | M1 vertical slice ✅ · M2 first boss & systems ✅ (pending device playtest) |
| Last completed | 0.9.5 fullscreen HUD redesign (ui/game/GameHud.kt, buff chips in UpgradeBar.kt) |
| Current task | Waiting on owner: body design (CO-094) and Power-Up Board picks (CO-090) |
| Build status | `./gradlew :app:assembleDebug` ✅ |
| Test status | `./gradlew :app:testDebugUnitTest` ✅ (32 tests + 15 opt-in screenshot renders) |

## Known issues / caveats
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
- **INFERNO I–X (0.29.0)** are `Difficulty.INFERNO_1..10` (`inferno` tier).
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
