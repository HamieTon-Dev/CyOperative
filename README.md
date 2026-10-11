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

`0.26.1` — PC direct-IP co-op (no Steam needed); `0.26.0` co-op shop (a shop each, in and out together); `0.25.9` no HP/Firewall regen over time, MAP corner kept clear of touch buttons; `0.25.8` compact shop window; `0.25.7` room slide follows the door direction; `0.25.6` ALL COLOUR SKINS pack ◇1400; `0.25.5` skins renamed by colour; `0.25.4` fixed the crash on START; crash reporter; PURPLE/GLITCHED campaign skins, GOLD and NEON (◇1000) skins, PROXY SHOT removed; boss loot sprays out; per-player co-op loot; World-map CAMPAIGN (15 worlds); Steam build (PC + Steam Deck, landscape) added in `desktop/`; Boss Expansion Vol. 1 under way; 0.16.0 balance pass (pooled mastery, bigger Packet Flood) (see `DEVELOPMENT_STATE.md` and `docs/BOSS_EXPANSION_LOG.md`).

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
