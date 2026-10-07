# Changelog

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
