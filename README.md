# Cyber Operative

**A portrait, one-handed, endless roguelike action RPG set in the CyOps universe.**

Enter the network. Eliminate digital threats. Strengthen the operative.
Survive as deep into the compromised system as possible.

- **Move = survive.** Drag anywhere to move. While moving, your primary weapon holds fire.
- **Stop = shoot.** Release and the operative auto-targets and fires.
- **Orbits = continuous damage.** Packet Nodes and Encryption Blades never stop attacking.
- **Positioning = strategy.** Server racks, firewalls and pillars block movement and fire.

Clear arenas, choose 1 of 3 roguelike upgrades, defeat a boss every 10 levels,
survive random cyber events, earn **€** for permanent upgrades. There is no final level.
Play **co-op** with a friend (friend codes, invites, live two-player runs; see `FIREBASE_SETUP.md`).

## Project status

`0.36.4` — co-op € fully separate (your kills, your bags, your own payouts; trade to share); `0.36.3` one € total in co-op too; TRADE only shows when usable; short numbers (398k, 3.7m) when they don't fit; `0.36.2` the classic bosses get moving parts too (mesh rigs); `0.36.1` INFERNO bosses get real moving parts (deformable mesh rigs); `0.36.0` INFERNO bosses redrawn from the owner's pack paintings with animation rigs; recorded INFERNO drop sounds; `0.35.1` each INFERNO boss has its own colour; `0.35.0` sell backpack items; co-op TRADE; 15 INFERNO-only bosses (Packs Alpha/Beta/Gamma); Shadow Step knives, smarter Firestorm, wall-piercing Arcane Nova; signature perks on Legendary+ gear; `0.34.0` INFERNO levels 1–500 (curve A, level stepper); one Data Vault shared by every character (main-menu DATA VAULT); ABYSSAL in ~1 shop in 100; `0.33.0` four INFERNO equipment rarities (Forged, Voltaic, Celestial, Abyssal) with their own art, animated cards, auras and base × INFERNO level drop odds; `0.32.0` € carries over between runs; 4 equipment pieces (PURPLE+) in the shop; INFERNO soundtrack; `0.31.0` co-op shared XP and smoother partners; PC exit button, 2× HUD, big ability bar, keybinds in pause; `0.30.5` XP bar reads OC Lv after level 60; `0.30.4` fixed a fake left EXIT door; Circuit Hydra loses 10% HP per head destroyed; `0.30.3` white XP bar in the HUD; rooms follow the screen orientation; `0.30.2` level XP ×10 (1–60 is 29.2M XP; saves keep their level); `0.30.1` fixed the keyboard closing on the character name and the Packet Mage face parts; `0.30.0` Data Vault update phase 2: gear loot (the owner's art, 8 rarities, beams + sheen), CHARACTER / GEAR / DATA VAULT screens with drag-to-equip, character creator and named characters, in-game INVENTORY; `0.29.0` INFERNO I–X difficulties with a hell look; `0.28.0` Data Vault update phase 1: OP Lv 1–60 + Overclock, Diablo armor, no Firewall, € wipe; `0.27.0` world server: MOVE TO NEXT WORLD (co-op 1/2 ready); `0.26.3` fix: no way on to the next world after the shop; `0.26.2` slim Android landscape HUD; `0.26.1` PC direct-IP co-op (no Steam needed); `0.26.0` co-op shop (a shop each, in and out together); `0.25.9` no HP/Firewall regen over time, MAP corner kept clear of touch buttons; `0.25.8` compact shop window; `0.25.7` room slide follows the door direction; `0.25.6` ALL COLOUR SKINS pack ◇1400; `0.25.5` skins renamed by colour; `0.25.4` fixed the crash on START; crash reporter; PURPLE/GLITCHED campaign skins, GOLD and NEON (◇1000) skins, PROXY SHOT removed; boss loot sprays out; per-player co-op loot; World-map CAMPAIGN (15 worlds); Steam build (PC + Steam Deck, landscape) added in `desktop/`; Boss Expansion Vol. 1 under way; 0.16.0 balance pass (pooled mastery, bigger Packet Flood) (see `DEVELOPMENT_STATE.md` and `docs/BOSS_EXPANSION_LOG.md`).

| | |
|---|---|
| Platform | Android (portrait only), minSdk 24, target/compile SDK 36 · Steam PC/Steam Deck (landscape, `desktop/`, see `desktop/README.md`) |
| Stack | Kotlin 2.3, Jetpack Compose (UI) + Compose Canvas (arena), no game engine |
| Build | Gradle 8.13 wrapper, AGP 8.13, JDK 17+ |

## Build & run

```bash
# Android SDK path in local.properties (sdk.dir=...) or ANDROID_HOME
./gradlew :app:assembleDebug          # APK in app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest      # unit + simulation tests
./gradlew :app:testDebugUnitTest --tests '*ScreenshotPreviews*' -PrenderPreviews
                                      # renders real screens to docs/screenshots/
./gradlew :desktop:run                # Steam/PC build (see desktop/README.md)
./gradlew :desktop:createReleaseDistributable
```

## Repository structure

```
app/src/main/java/com/cyberoperative/game/
  core/      math, rectangles, endless difficulty curves (Scaling)
  data/      DATA-DRIVEN content: enemies, bosses, upgrades, arenas, events, progression
  engine/    pure-Kotlin simulation (no Android): GameEngine, EnemyAi, BossBrain,
             LevelPlanner, RunBuild, Scoring, pooled entities
  audio/     ToneSynth/SoundBank (synthesized SFX) + music via MediaPlayer
  save/      PlayerProfile (JSON in private SharedPreferences, auto-backup)
  meta/      achievements
  ui/        splash + boot terminal, main menu, game screen, overlays, meta screens
app/src/test/  unit tests, an automated Bot that plays the real engine, screenshot previews
docs/        design notes and rendered screenshots
```

Project documents: `GAME_DESIGN.md` (design source of truth), `BACKLOG.md`,
`DEVELOPMENT_STATE.md` (where development stopped), `CHANGELOG.md`.

## Asset reuse

Selected owned assets were **copied** (never linked) from CyOps TD: HamieTon.dev banner,
the Cyber Operative logo / launcher vectors, music tracks, boot chime, the ToneSynth
sound generator, palette and boss/enemy concepts. CyOps TD itself is not modified.

## Copyright

© HamieTon.dev. All rights reserved. Gameplay genre inspired by mobile arena roguelikes;
all content, names, layouts and code are original to the CyOps universe.
