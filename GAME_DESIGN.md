# Cyber Operative — Game Design (source of truth)

Update this file whenever a major mechanic changes.

## 1. Definition
Portrait-format, cyber-themed **endless roguelike action RPG**. One bottom movement
control. Moving holds primary fire; stopping auto-targets and fires; orbiting weapons
always attack. Clear arenas, pick roguelike upgrades, boss every 10 levels, random cyber
events, € for permanent upgrades, optional ◇ premium content. No final level.

Genre inspiration only (mobile arena roguelikes). **Nothing** is copied from Archero:
no maps, characters, enemies, UI, art, sounds, terminology, values, names or code.

## 2. Pillars
SIMPLE CONTROL · STRATEGIC MOVEMENT · CONSTANT ACTION · BUILD VARIETY ·
PERMANENT PROGRESSION · ENDLESS CHALLENGE · CYBER IDENTITY.
Readability > effects. No unavoidable damage. Players should know why they died.

## 3. Controls (implemented)
- Touch anywhere in the arena: a floating joystick appears under the thumb (rests at
  bottom-centre when idle). Base trails the thumb for instant direction changes.
- Deadzone 0.12. Primary fire starts 0.04 s after the stick is released.
- No fire button. No active-ability buttons during early development.

## 4. Targeting
Candidates within range. Visible (line-of-sight) targets preferred; among them score =
distance², ×0.25 if within 150 units (threatening), ×0.6 for bosses, ×0.85 for the
current target (predictable stickiness). Falls back to the nearest in-range target.

## 5. World & arenas
World width 720 units, heights 1040–1300; camera scales to screen width and follows
vertically. Player enters bottom-centre, leaves via the ACCESS PORT top-centre (opens
after the clear + upgrades). 12 templates gated by level, randomly mirrored, never the
same layout twice in a row; dedicated boss arena. Obstacles block movement, player
shots and enemy shots (some boss patterns may ghost through in future).
Tested: every arena has a clear spawn, clear port and a walkable route between them.

## 6. Level flow
COMBAT (waves; next wave when ≤2 alive, after ≥1.2 s, or 14 s timeout) → CLEARED
(0.9 s; € and score) → UPGRADE (if pending) → PORTAL → TRANSITION (0.45 s) → next.
Spawns are telegraphed 0.85 s and never within 300 units of the player.

## 7. Difficulty (core/Scaling.kt)
- Enemy HP ×(1 + 0.11n + 0.0009n²), n = level−1 (L10 2.1, L100 20.7, L1000 ≈1008)
- Enemy damage ×(1 + 0.045n + 0.00018n²)
- Speed → +30% asymptote; projectile speed → +35%; attack rate → +40%
- Enemy budget 5 + 0.55n (cap 46), waves 1–4, max 32 alive, elites from L6 up to 35%
- Boss HP = enemy HP × (1 + 0.08(cycle-1)) × (1 + 0.25·rosterLoop)

## 8. Run upgrades (data/Upgrades.kt)
3 cards, pick 1. Run-level ("DATA") XP from kills; each level-up queues a card pick,
presented after the arena clears (plus +1 after bosses and events). Rarity weights:
Common 100, Uncommon 55, Rare 26, Epic 10, Legendary 4; evolutions weighted 60 when
unlocked. Evolution = parent at max level (+ optional partner). Chains:
Packet Nodes → Enhanced Nodes → Sentinel Nodes (+Node Overclock) → Autonomous Defense Node;
Firewall → Reinforced → Adaptive → Zero Trust Fortress. Instant fillers (System Restore,
Crypto Cache, Data Dump) guarantee three cards.

Categories: cone (Packet Scatter), long-range slow (Exploit Lance), rotating shield
(Encryption Blades — block packets), orbs (Packet Nodes), firewall (recharging absorb),
plus EMP Burst, Botnet Chain, Packet Split, Multishot, Diagonal Routing, Proxy Shot,
Penetration, Packet Bounce and stat modules.

## 9. Enemies (data/EnemyDefs.kt)
Malware Crawler, SQL Injector, Bot Drone (packs), Exploit Runner (telegraphed charge),
Trojan Brute (armour), Phish Lure (spread), Packet Sniffer (laser sight), Worm (splits),
DDoS Node (radial turret), Rootkit Phantom (teleport + spread). Elites: Encrypted,
Overclocked, Corrupted (death zone), Armored, Replicating, Volatile (death burst).
Every attack has a windup flash or telegraph line.

## 10. Bosses (data/Bosses.kt)
Every 10 levels: BREACH, BOTMASTER, WORM PRIME, RANSOM, ROOTKIT, SYN-STORM, KERNEL
PANIC, EXFIL, WHITE EYE, ZOMBIE, SPOOFER, GOOD GAME (identities from CyOps TD,
mechanics redesigned). 3 phases (100–60, 60–25, <25%). Movement: chase/hover/teleport/
drift. Patterns: radial, aimed, spiral, charge, summon, shock ring, blasts, zones,
teleport, beam, homing. Roster loops forever with more HP and faster patterns.

## 11. Events (data/Events.kt)
~14% of eligible non-boss levels (never right before a boss, never twice in a row).
Spectrum border + tinted floor + event music. PACKET FLOOD, FIREWALL BREACH (aggressive,
shrinking safe space via telegraphed zones), MALWARE SWARM (survive 30 s), DATA VAULT
(extra waves, ×3 reward), ZERO-DAY ANOMALY (2–3 random modifiers). ◇ chance exists in
data but is 0 pending a monetization decision.

## 12. Economy
- **€**: kills, clears, bosses, events, achievements → permanent upgrades (19 kinds,
  cost = base·(1+level)^1.55, some gated by Operative Level).
- **◇**: premium, Google Play only (not yet integrated). Skins/operatives/themes/
  revives/level-up packs. Never required to progress. No misleading store UI.
- Operative XP = 12·level reached + kills + 80·bosses; level curve 200 + 60·(L−1).

## 13. Score (engine/Scoring.kt)
Kills (×level scale, elites ×2.5, split/summon children ×0.35), level clear 100·scale +
speed bonus (capped at +100%, linear to par) + flawless bonus 60√level, ×event reward,
bosses score ×(1+0.5·loop). Nothing pays for time alive.

## 14. Revive
1 free revive per run: 50% HP, full firewall, 2.5 s invulnerability, hostile projectiles
and hazards cleared, nearby enemies pushed back. ◇ / rewarded-ad revives: later.

## 15. Startup
HamieTon.dev ident (1.9 s, tap to skip) → INITIALIZING terminal (fictional, harmless
commands only; nothing is executed) → main menu.

## 16. Audio
Synthesized SFX for every cue (rate-limited, soft). Music: menu, combat (2 tracks
alternate), boss, event, game over — reused CyOps TD tracks.
