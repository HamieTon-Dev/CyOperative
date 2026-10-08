# Development State

_Read this first in every new session, then README, GAME_DESIGN, BACKLOG, CHANGELOG, git log._

| | |
|---|---|
| Version | 0.8.0 (versionCode 9) |
| Milestone | M1 vertical slice ✅ · M2 first boss & systems ✅ (pending device playtest) |
| Last completed | Key-art visual pass, PLASMA BEAM, NEON OPERATIVE |
| Current task | Waiting on owner: body design (CO-094) and Power-Up Board picks (CO-090) |
| Build status | `./gradlew :app:assembleDebug` ✅ |
| Test status | `./gradlew :app:testDebugUnitTest` ✅ (32 tests + 15 opt-in screenshot renders) |

## Known issues / caveats
- Not yet run on a physical device or emulator (no emulator in the cloud session).
  Rendering was verified with Robolectric native graphics (docs/screenshots/).
- Release .aab is ~120 MB: the full original 24-track soundtrack. Fine for Play (< 200 MB).
- The minified (R8) release build compiles but has not been run on a device yet.
- Bosses all draw as a rotating hexagon with their tag; unique silhouettes pending (CO-063).
- ◇ packs: debug builds grant test ◇; release shows "billing not connected" until CO-070.
- Owner picks for power-up names/icons live in the Power-Up Board artifact db (`picks/main`).
- Balance: a simple dodging bot with no permanent upgrades averages ~level 6 in campaign.
- The top wall/gate sits under the translucent HUD until the player walks up.

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
