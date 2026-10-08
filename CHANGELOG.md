# Changelog

## 0.6.0 — 2026-10-08
### Added
- Visual pass to match the key art (no false advertising): bevelled metal floor panels,
  glowing cyan trenches with running packets, grates, lit service panels, bolts, light
  pools under hardware, drifting data pixels; taller server racks with dense glowing LED
  bays and neon edge trim; X-braced hardware crates; glowing projectile trails (red-orange
  hostile streaks); neon CYBER OPERATIVE title with the "ENDLESS CYBER ROGUELIKE ACTION" line.
- PLASMA BEAM (Rare weapon upgrade, 3 levels): an arm cannon that pours a continuous,
  piercing cyan beam into the target while the operative stands still; stopped by cover.

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
