# Development State

_Read this first in every new session, then README, GAME_DESIGN, BACKLOG, CHANGELOG, git log._

**Active stage: Boss Expansion Vol. 1 — progress and next step live in `docs/BOSS_EXPANSION_LOG.md`.**

| | |
|---|---|
| Version | 0.12.0 (versionCode 42) |
| Milestone | M1 vertical slice ✅ · M2 first boss & systems ✅ (pending device playtest) |
| Last completed | 0.9.5 fullscreen HUD redesign (ui/game/GameHud.kt, buff chips in UpgradeBar.kt) |
| Current task | Waiting on owner: body design (CO-094) and Power-Up Board picks (CO-090) |
| Build status | `./gradlew :app:assembleDebug` ✅ |
| Test status | `./gradlew :app:testDebugUnitTest` ✅ (32 tests + 15 opt-in screenshot renders) |

## Known issues / caveats
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
