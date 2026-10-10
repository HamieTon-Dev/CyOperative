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

## 4b. Game modes (owner, 2026-10-07)
- **CAMPAIGN (main game).** Discrete levels. Each level has a fixed threat count
  (8, 14, 17, 20, 21, … capped at 45; `Scaling.campaignThreats`). Kill them all → choose a
  power-up (one after EVERY cleared level) → the gate in the top wall opens → walk through
  → the next room slides down from above. Boss every 10 levels; random event levels.
- **ENDLESS (optional).** One generated room, threats never stop. Difficulty stage rises
  every 30 s; a boss at every 10th stage pauses normal spawns. Upgrades come from data (XP)
  and pop up mid-fight. Separate records (best stage / best score).

## 4c. Look: 2.5D
Simulation stays top-down; rendering gives height: extruded obstacles (lit top + front face,
cast shadow), hovering extruded enemies with eyes that track the operative, floor shadows,
projectiles in the air, depth sorting by floor position. Full-body operative designs
(`BodyStyle`): A FIELD AGENT, B SENTINEL MECH, C SHADOW RUNNER — all keep the >_< face and
take skin colours (owner likes all three; FIELD AGENT is the default). D NEON OPERATIVE is
the app icon as a full body, sold in the store, with its own fixed neon look.

## 5. World & arenas
World width 720 units, heights 1040–1300; camera scales to screen width and follows
vertically. Player enters bottom-centre, leaves through the GATE in the top wall (opens after the clear
+ power-up). 85% of rooms are procedurally generated (`ArenaGenerator`: 6 layout styles ×
5 hardware kits, random sizes, small blinking server units, floor decor — cables, vents,
light strips, holo panels, data pools, hazard stripes — validated for a clear spawn, clear
gate and a walkable route); 15% are the 12 hand-made templates; dedicated boss arena. Obstacles block movement, player
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
3 cards, pick 1. Campaign: one pick after every cleared level, a bonus pick with chance
min(45%, 1.2%·(L−1)) and from L25 another with min(30%, 1%·(L−24)); events +1; bosses
+2 + L/20 (rolled with +1.2 luck). Endless: data (XP) from kills; each data level-up opens a
pick mid-fight. The screen shows "REWARD n OF N" when a drop has several picks.

Tiers (owner, 2026-10-08): Common 100, Uncommon 55, BLUE RARE 26, PURPLE 7, GOLDEN 2.5,
TITANIUM 0.8. Luck = 0.015·level + difficulty (Easy 0, Medium 0.1, Hard 0.35) + boss 1.2;
each tier above BLUE is multiplied by (1+luck)^tier. Evolutions weighted 60 when unlocked.
Evolution = parent at max level (+ optional partner). Chains:
Packet Nodes → Enhanced Nodes → Sentinel Nodes (+Node Overclock) → Autonomous Defense Node;
Firewall → Reinforced → Adaptive → Zero Trust Fortress. Instant fillers (System Restore,
Crypto Cache, Data Dump) guarantee three cards. Owned mods show as icons along the bottom
of the screen (hold for details).

Categories: cone (Packet Scatter), long-range slow (Exploit Lance), rotating shield
(Encryption Blades — block packets), orbs (Packet Nodes), firewall (recharging absorb),
plus EMP Burst, Botnet Chain, Packet Split, Multishot, Diagonal Routing, Proxy Shot,
Penetration, Packet Bounce and stat modules. Added 2026-10-08: Logic Bombs (uncommon,
mines while moving), Malware Missiles (blue, homing + splash), Arc Discharge (purple, chain
lightning), Quantum Railgun (golden, pierces walls), Orbital Strike (titanium), Titanium
Chassis, Omega Overclock, Golden Protocol. Plasma Beam is TITANIUM and overheats after 5 s
of hits (3 s cooldown).

Arsenal (data/Weapons.kt): 30 auto-weapons, 5 per rarity, each a row on one of ten behaviours;
cooldown −12% and damage +35% per level. Base move speed 276 (owner: +15%).

## 8b. Difficulty & saving
New runs pick EASY / MEDIUM / HARD: threat HP ×0.7 / 1 / 1.45, damage ×0.65 / 1 / 1.4,
€ and score ×0.8 / 1 / 1.5. SAVE & EXIT (pause) stores the level seed, threats, player
and build (RunSnapshot); CONTINUE restores it once. Saving is blocked while a boss lives.
Autosave on backgrounding.

## 9. Enemies (data/EnemyDefs.kt)
Roster: 11 hand-made originals + 3 *GLITCHED* + 338 strain×code variants (EnemyVariants.kt,
weight 0.45 each, unlocking from L2 to ~L52). Glitched bosses: 12% from L20.
Navigation (engine/Pathfinder.kt): flow field toward the player, rebuilt when the player
changes cell (≤ every 0.2 s); enemies with a clear line charge straight in, others follow
the field around cover; bosses use the same movement.
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
Boss Expansion Vol. 1 (0.11.x, plan and progress in docs/BOSS_EXPANSION_LOG.md):
- 12 new bosses take levels 130–240, then all 24 rotate. Nullshade Specter is also a 5% rare
  encounter from level 150.
- Every boss has a role, a threat tier (1–4), optional armor and named abilities, shown on the
  threat dossier card during the entrance. The card shows the bounty: the boss € × (1 + 0.04·level)
  × glitch × € multipliers.
- Vault Sentinel (L130):
  - Barrier cubes (48×48, 0.9 s floor telegraph, ~8 s solid) block movement and shots.
  - Laser sweeps are clipped by any obstacle; mortars ignore cover.
  - Lockdown leaves a 2-cube gap on the side away from the boss (falls back to another side if a wall
    or block is behind it).
- Rootkit Apostle (L140):
  - Burrow: untargetable while underground; exit telegraphed 0.7–0.8 s.
  - Spikes: 0.75 s warning, one hit, rippling 0.08 s per step.
  - Infected Zone: on you plus your top dwell cells (100-unit grid, ~10 s memory).
  - Bloom: rings every 88 units with 3 operative-wide lanes.
- Nullshade Specter (L240; rare 5% from L150):
  - Blackout overlay at alpha 0.985 (0.935 while its eyes are open); light bubble 175 units.
  - Eye windows: open 2.8/2.0/1.7 s, shut 2.6/1.9/1.5 s by phase; it can't be hit while shut.
  - Ghost Dash hits once per pass.
- Ransom King (L210):
  - Key shield: ×0.25 damage while up; every key zone of a wave (1.3 s each to unlock) breaks it,
    then ×1.5 for 6 s.
  - Lock Grid walls 7 s with 2 gaps of 3 cubes.
  - Royal Seizure roots 1.0–1.2 s plus a 3.5 s cage.
  - ENCRYPTED 3.5 s; 1.1 s of movement bursts it (1.8× ring damage).
- Expansion bosses use fixed slots (BossExpansion.SLOTS), L130–240.
- Kill beat: 0.14 s hit-stop, a white flash, a shake, then 1.4 s of slow motion at 0.3×.
- Phase change: 0.08 s hit-stop, a flash in the boss colour, a shake.

## 11. Events (data/Events.kt)
~14% of eligible non-boss levels (never right before a boss, never twice in a row).
Spectrum border + tinted floor + event music. PACKET FLOOD, FIREWALL BREACH (aggressive,
shrinking safe space via telegraphed zones), MALWARE SWARM (survive 30 s), DATA VAULT
(extra waves, ×3 reward), ZERO-DAY ANOMALY (2–3 random modifiers). ◇ chance exists in
data but is 0 pending a monetization decision.

## 12. Economy
- **€**: kills, clears, bosses, events, achievements → permanent upgrades (19 kinds,
  cost = base·(1+level)^1.55, some gated by Operative Level).
- **◇**: premium, Google Play only. Packs: ◇150 $1.00 · ◇500 $4.50 · ◇1000 $8.50 ·
  ◇10000 $69.99. Revives: 1 ◇100 · 7 ◇500 · 20 ◇800 · 250 ◇6500 (max 3 paid revives per run).
  Operative skins ◇100 each, all ◇300 (17 skins from CyOps TD agent/core colours).
  Living backgrounds (from CyOps TD) provisional ◇150 each / ◇600 all.
  Never required to progress. No misleading store UI.
- Operative XP = 12·level reached + kills + 80·bosses; level curve 200 + 60·(L−1).
  Operative Level caps at 9,999.
- **Mastery** (0.9.7): permanent upgrades continue past their max up to level 9,999; mastery m
  needs OP LVL m. Effective level = max + 2·√m (ENDLESS stats) or max + extra·(1−e^(−m/60))
  (SOFT stats: speed, range, crit chance, armor, packet speed, card quality). Starting nodes,
  starting upgrades and rerolls stay hard-capped.
- **OP threat scaling** (the only difficulty extra since 0.10.3; from level 1, halved on Easy): +2% HP / +1.2% damage per OP level to OP 101 (×3 / ×2.2), then
  × (1 + 0.6·ln(1 + (OP−101)/100)) for HP and × (1 + 0.4·ln(…)) for damage. Threat HP also
  × √(mastery DPS ratio) and threat damage × √(mastery survival ratio).

## 8c. Weapon slots (0.10.1)
- 7 weapons per operative (`Upgrades.MAX_WEAPONS`), +1 per WEAPON SLOTS level (OP 10/20/30/50/80,
  €5k/15k/40k/100k/250k) up to 12. Weapons = arsenal + built-in attacks;
  modifiers and Packet Nodes are power-ups.
- Card screen: power-up, power-up, weapon. A new weapon with full slots needs a swap (grid → confirm).
- Shop: no new weapons while slots are full.

## 12b. Co-op (0.10.0, engine/Operative.kt, engine/CoopNet.kt, net/)
- Two operatives in one campaign run: host = operative 0, guest = 1. Each brings its own permanent
  upgrades, skin and body, and picks its own cards; the card screen waits for both.
- Threats chase the closest operative still up; hostile shots and hazards hit either one.
- HP 0 → downed. The partner standing within 70 px for 3 s revives at 50% HP. Downed operatives
  return at 50% on the next level. Both down → run over. No paid/free revives in co-op.
- Scaling: threat HP ×1.4, boss HP ×1.7, each wave spawn has a 35% chance of an extra copy;
  OP scaling uses the higher OP level.
- Rewards: both players bank the whole run (€, XP, records, achievements).
- No pause, no save, no upgrade shop.
- Net: the host simulates and sends snapshots at 10 Hz (deflated binary, positions at 0.5 px);
  the guest mirrors them, moves itself locally and sends input at 15 Hz. A partner that goes
  offline or silent for 8 s is dropped; the other carries on solo.
- Accounts: anonymous Firebase accounts + callsign (3–16 chars) + friend code XXXX-XXXX
  (no 0/O/1/I/L). Invites expire after 3 minutes.

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
Tempo: menus 1x, normal rounds 1.25x (20% of them roll a 0.75x slow mix), events 1.25x, bosses 1.5x.
Synthesized SFX for every cue (rate-limited, soft). Music: the whole CyOps TD soundtrack
(24 tracks) shuffled — menu tracks on menus, all tracks in a run, the level-10 pieces for
bosses, Liminal tracks on game over. Pause-screen player lets the player pick tracks,
skip, pause, toggle shuffle or return to AUTO. Audio never plays in the background.
