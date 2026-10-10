# Boss Expansion Vol. 1 — build plan & progress log

_Owner brief (2026-10-10): "Use these as inspiration for the next level of this build. I want this
game to feel premium." Source sheets: `docs/boss-concepts/` (Boss Pack Alpha, Boss Pack Beta,
Nullshade Specter dossier, Expansion Vol. 1 overview — 12 bosses)._

**Every session working on this stage: read this file first, continue from the first unchecked
box, tick boxes as they land, and add a dated line to the session log at the bottom.**

Status: **BUILDING — Stage A done (v0.11.0); Vault Sentinel done early (v0.11.1) at the owner's request; next: rest of Stage B.** Owner accepted every recommendation (D1–D6): "Build all of them as you see fit."

**Design approval rule (owner, 2026-10-10):** "I want to see and approve, or request changes to the
physical look of each boss as you make them" + "show me its attacks as you work on it as well". Every boss body (new and classic) is rendered
(close-up + in-fight) and sent to the owner. A body is only **final** once the owner approves it;
mechanics can keep moving while a design waits. Track it in §6.

---

## 1. Decisions (all accepted 2026-10-10 as recommended)

| # | Question | Recommendation |
|---|---|---|
| D1 | Where do the 12 new bosses appear? | Keep the current 12 at levels 10–120; new bosses take levels 130–240 (Alpha → Beta → the rest, by threat tier), then all 24 rotate. Nullshade Specter as a rare "EXTREME" encounter from level 150 too. |
| D2 | The sheets' bounties (€11,600–17,400) are ~50× today's boss reward (€120–360 base). | Treat them as relative: new bosses pay 1.3–1.8× a classic boss at the same level, ordered by the sheet's bounty. Show the number as "BOUNTY" in the intro card. |
| D3 | Sheet stats (HP 1,200–1,800, speed 3.5–10, armor 8–20) | Map relative to today's bosses: HP order kept, speed 4 ≈ slow (40), 10 ≈ fast (120), armor as damage reduction per hit. |
| D4 | Rootkit Apostle, Ransom King, Spectral Firewall only have one-line concepts. | I design their 4 abilities + 3 phases from the one-liners (listed in §4) unless you send dossiers. |
| D5 | Art: the sheets are painted illustrations. | Draw each boss as an animated vector body in the game's 2.5D neon style, matched to the sheet silhouette. Optional later: you supply sprite PNGs and I swap them in. |
| D6 | Give the 12 existing bosses unique bodies too (backlog CO-063)? | Yes, in Stage F; same body system, so every boss looks distinct. |

---

## 2. Feasibility of every idea on the sheets

✅ = can build as described · ⚠️ = can build, adapted (note says how) · ❌ = not planned

### Boss Pack Alpha
| Boss | Ability | Verdict | Needs system |
|---|---|---|---|
| **Vault Sentinel** (Tank) | Cover Deploy | ✅ blocks rise from the floor mid-fight | S6 dynamic cover |
| | Laser Sweep | ✅ telegraphed beam that rotates | S2 sweep beams |
| | Lockdown Cube | ✅ temporary walls box the player in, one side open | S6 dynamic cover |
| | Pulse Mortar | ✅ lobbed shells land on marked spots | S4 lobbed shots |
| **Packet Reaper** (Assassin) | Dash Slash | ✅ (charge exists) with slash arc | — |
| | Packet Scythes | ✅ curved boomerang shots that return | S4 curved shots |
| | Backline Dive | ✅ blinks behind the player, short warning, then dives | S1 + charge |
| | Trail Burst | ✅ dash path explodes after a delay | S5 trails |
| **Circuit Hydra** (Area control) | Split Heads | ✅ at 60% HP splits into 2–3 heads sharing one HP bar | S8 multi-part bodies |
| | Beam Arc | ✅ fan of sweeping beams | S2 |
| | Segment Burst | ✅ each body segment fires a ring | S8 |
| | Coil Crush | ✅ body encircles the player and tightens; gap to escape | S8 |
| **Worm Queen** (Summoner) | Swarm Hatch | ✅ (summon exists) eggs hatch after a delay | S9 eggs |
| | Corruption Trail | ✅ pools left along her path | S5 trails |
| | Spike Burst | ✅ ring of spikes | — |
| | Queen Roar | ✅ shockwave + speeds up her swarm briefly | S7 buffs |

### Boss Pack Beta
| Boss | Ability | Verdict | Needs system |
|---|---|---|---|
| **Pulse Bishop** (Controller) | Line Warp | ✅ teleports along lanes, light streak | S1 |
| | Cross Beam | ✅ + shaped beams, can rotate | S2 |
| | Bishop Mines | ✅ mines arm, then detonate | S3 hostile mines |
| | Convergence Flash | ⚠️ pulls the player toward it, then a bright flash. The "blinding" is a short white-out with threats still outlined, so it stays fair | S7 pull + S12 flash |
| **Glitch Forge** (Summoner) | Decoy Clone | ✅ hologram copies that shoot; one hit pops them | S9 decoys |
| | Corrupt Floor | ✅ floor tiles turn hazardous in a grid, telegraphed | S5 tile hazards |
| | Cube Barrage | ✅ waves of homing cubes | (homing exists) + visuals |
| | Core Pulse | ✅ big pulse that corrupts tiles it passes | S5 |
| **Black Ice Overlord** (Status) | Ice Laser | ✅ sweeping freezing beam | S2 + S7 |
| | Freeze Patch | ✅ icy zones that slow and stack CHILL (max stacks = short freeze) | S7 status effects |
| | Crystal Volley | ✅ arcing crystals | S4 |
| | Permafrost Shell | ✅ shell phase: takes 75% less damage, chills nearby; break it with sustained fire | S10 shields |
| **Botnet Monarch** (Commander) | Drone Ring | ✅ drones orbit the boss and shoot | S9 orbiters |
| | Summon Wave | ✅ (summon exists) | — |
| | Orbital Barrage | ✅ targeted strikes (blasts exist) with sky-beam visuals | — |
| | Sync Burst | ✅ drones link with lines, then fire together | S9 |

### Dossier — Nullshade Specter (Extreme)
| Ability / rule | Verdict | Needs system |
|---|---|---|
| EMP Blackout (room goes dark) | ✅ darkness overlay; the player's light bubble stays | S11 darkness |
| Eye-Glint Lock Window (only targetable while eyes show) | ✅ untargetable between windows; eyes always drawn | S11 + S10 |
| Ghost Dash (faint afterimage + corruption trail) | ✅ | S5 trails |
| Static Needles (low-visibility shots with static trails) | ⚠️ dimmed but always visible inside the light bubble, so it's never unfair | S11 |
| Spark Ambush (bursts from server blocks) | ✅ shots fired from obstacles | S6 |
| Grid Reboot Surge (phase 3 frenzy) | ✅ | — |
| Room rules + visual sequence (lights out → sparks → light bubble → eyes → power restores) | ✅ | S11 |
| Threat tier EXTREME, bounty above White Eye | ✅ | S13 |

### Overview only (one line each — designed in §4)
| Boss | Concept | Verdict |
|---|---|---|
| **Rootkit Apostle** | Burrows, erupts, infects safe zones | ✅ burrow = untargetable underground with a moving dirt trail; erupt = blast at exit |
| **Ransom King** | Locks down blocks, creates key zones, punishes movement | ⚠️ "punishes movement" = an ENCRYPTED status: while it's on, moving charges a meter that bursts. Key zones: stand in them to break his shield |
| **Spectral Firewall** | Rotating flame-wall that shrinks safe space | ✅ rotating ring of fire with one or two gaps that closes in |

### Presentation ideas on the sheets
| Idea | Verdict |
|---|---|
| Role badges (Tank / Assassin / Controller / Summoner / Status / Commander / Area control) | ✅ intro card + codex |
| Threat tier skulls (Low / Medium / High / Extreme) | ✅ |
| Bounty shown per boss | ✅ (scaled per D2) |
| Boss dossier / codex screen | ✅ new BOSS CODEX screen: every boss you've met, abilities, best time |
| "New arena hazards" | ✅ cover, trails, tile corruption, darkness, fire walls |
| Painted illustration look | ⚠️ vector 2.5D bodies (D5) |

**Nothing on the sheets is ruled out.** Two things are adapted for fairness (blinding flash, near-invisible needles), and the art is redrawn in-engine.

---

## 3. New engine systems (build once, reuse across bosses)

| ID | System | Used by |
|---|---|---|
| S1 | Lane/backline blink (teleport to a chosen spot with a streak) | Pulse Bishop, Packet Reaper |
| S2 | Sweeping and rotating beams; cross beams; beam fans | Vault Sentinel, Circuit Hydra, Pulse Bishop, Black Ice |
| S3 | Hostile mines (arm → detonate) | Pulse Bishop |
| S4 | Lobbed / arcing shots and curved boomerang shots | Vault Sentinel, Packet Reaper, Black Ice |
| S5 | Trails & tile corruption (hazards left along a path or on the floor grid) | Packet Reaper, Worm Queen, Glitch Forge, Nullshade, Rootkit Apostle |
| S6 | Dynamic arena: cover blocks rise/fall mid-fight, shots fired from blocks; enemy paths rebuild | Vault Sentinel, Nullshade, Ransom King |
| S7 | Player status effects: SLOW, CHILL stacks → FREEZE, PULL, ENCRYPTED; buff auras for minions; HUD icons | Black Ice, Pulse Bishop, Worm Queen, Ransom King |
| S8 | Multi-part bosses: segments that follow the head, split into several heads sharing one HP bar | Circuit Hydra |
| S9 | Special adds: orbiting drones, decoy clones, eggs that hatch, linked drones | Botnet Monarch, Glitch Forge, Worm Queen |
| S10 | Boss defences: shell phases (damage reduction), untargetable windows, armor | Black Ice, Nullshade, Ransom King, Rootkit Apostle |
| S11 | Darkness / lighting: room blackout, player light bubble, glints, power-restore sequence | Nullshade |
| S12 | Screen effects: flash, shake, hit-stop, slow-motion kill | all |
| S13 | Boss metadata: role, threat tier, bounty, armor; intro dossier card | all |
| S14 | Boss body renderer: one draw routine per boss (vector 2.5D, animated, phase changes) | all 24 |
| S15 | Co-op sync for all of the above (wire format v3) | all |

---

## 4. Designs for the one-line bosses (proposal, D4)

- **Rootkit Apostle** — Burrow (dives underground, untargetable, trail moves toward you), Eruption (bursts out under your last spot), Infection (turns the spot you stood longest into a corruption pool), Kernel Spikes (spike lines from where it surfaces). Phases add more eruptions and faster burrows.
- **Ransom King** — Lockdown (walls lock one arena quadrant), Key Zones (2–3 golden zones appear; holding one drains his shield), Encrypted (status: moving while encrypted builds a meter that bursts on you), Ransom Note (homing locks that tighten). Phase 3: all quadrants lock in turn.
- **Spectral Firewall** — Flame Ring (rotating ring of fire with 1–2 gaps), Closing Ring (it shrinks toward the centre then resets), Firebrands (aimed fire bolts), Backdraft (ring pulses outward once). Phase 3: two counter-rotating rings.

---

## 5. Build stages (each ends with tests + renders + a version bump + push)

### Stage A — Foundations (v0.11.0)
- [x] A1 S13 boss metadata (role, tier, bounty, armor) on BossDef; existing 12 bosses get values
- [x] A2 S12 screen effects (flash, shake, hit-stop) + boss kill slow-motion
- [x] A3 Boss intro dossier card (name, role badge, threat tier, bounty) replacing the plain banner
- [x] A4 S14 boss body renderer framework (per-boss draw hook, phase-reactive)
- [x] A5 Roster/placement per D1 (levels 130+), reward scaling per D2
- [x] A6 Tests: every boss still beatable by the bot; all patterns telegraphed

### Stage B — Shared mechanics (v0.11.1)
- [ ] B1 S2 sweeping / rotating / cross beams (sweep done in 0.11.1: HazardKind.SWEEP, clipped by obstacles; cross beams still to do)
- [ ] B2 S4 lobbed shots + curved boomerang shots (mortar done in 0.11.1: HazardKind.MORTAR; boomerangs still to do)
- [ ] B3 S3 hostile mines
- [ ] B4 S5 trails + tile corruption
- [ ] B5 S7 status effects (slow, chill/freeze, pull, encrypted) + HUD icons
- [ ] B6 S1 lane/backline blink
- [ ] B7 S10 shells, untargetable windows, armor
- [ ] B8 Tests for each system (dodgeable, telegraphed, co-op safe)

### Stage C — Boss Pack Alpha (v0.11.2)
- [x] C1 S6 dynamic cover + shots from blocks
- [x] C2 Vault Sentinel (body, 3 phases, 4 abilities)
- [ ] C3 Packet Reaper
- [ ] C4 S8 multi-part bodies
- [ ] C5 Circuit Hydra
- [ ] C6 S9 eggs + minion buffs
- [ ] C7 Worm Queen
- [ ] C8 Renders + reference page update + bot-beatable tests

### Stage D — Boss Pack Beta (v0.11.3)
- [ ] D1 Pulse Bishop
- [ ] D2 S9 decoys
- [ ] D3 Glitch Forge
- [ ] D4 Black Ice Overlord
- [ ] D5 S9 orbiting + linked drones
- [ ] D6 Botnet Monarch
- [ ] D7 Renders + tests

### Stage E — Nullshade Specter + the last three (v0.11.4)
- [ ] E1 S11 darkness, light bubble, eye glints, power-restore sequence
- [ ] E2 Nullshade Specter
- [ ] E3 Rootkit Apostle
- [ ] E4 Ransom King
- [ ] E5 Spectral Firewall
- [ ] E6 Renders + tests

### Stage F — Premium pass (v0.12.0)
- [ ] F1 Unique bodies for the 12 classic bosses (CO-063)
- [ ] F2 BOSS CODEX screen (met / defeated, abilities, best time, tier, bounty)
- [ ] F3 Boss music per tier, victory sequence
- [ ] F4 S15 co-op wire format v3 for all new effects; co-op tests
- [ ] F5 Balance pass across difficulties and OP levels; full renders; docs; reference page

---

## 6. Design approvals (owner sign-off per boss body)

| Boss | Sent | Status | Owner notes |
|---|---|---|---|
| Vault Sentinel | 2026-10-10 (`docs/bosses/designs/vault_sentinel_*.png`) | ✅ body approved; 20% larger as asked. Attacks sent for review (atk1–atk6) | "Looks great… can it be slightly larger? … show me its attacks … spawn cubes (barrier blocks out of the floor restricting player movement)" |

---

## 7. Session log
- 2026-10-10 — Plan written from the four concept sheets; waiting for owner decisions D1–D6.
- 2026-10-10 — Owner: "Build all of them as you see fit" (D1–D6 accepted) + per-boss design approval rule. Started Stage A.
- 2026-10-10 — Stage A done, v0.11.0 (22):
  - ScreenFx (S12) and the SCREEN SHAKE setting; BossRole/ThreatTier/armor/abilities (S13);
    threat dossier card (A3, mid-screen overlay).
  - BossBodies registry + BossBodyPreview (S14); rare Nullshade roll from L150; BossExpansionTest.
  - Vault Sentinel def (provisional patterns, not in the roster yet) and its body, sent for approval.
  - Approval renders: `./gradlew testDebugUnitTest -PrenderPreviews --tests '*BossDesignRenders*'`
    → `docs/bosses/designs/`.
- 2026-10-10 — v0.11.1 (23): Vault Sentinel built early (owner wanted to see its attacks).
  - Owner approved the body and asked for it slightly larger: radius 60 → 72.
  - New systems:
    - Barrier cubes: `GameEngine.barriers` / `addBarrier`, ObstacleKind.BARRIER_CUBE; the arena is rebuilt on a state change.
    - Patterns `CoverDeploy`, `Lockdown` (gap side falls back if blocked), `SweepBeam` (HazardKind.SWEEP), `Mortar` (HazardKind.MORTAR).
    - Co-op wire v3 carries barriers.
  - VAULT_SENTINEL is in `BossExpansion.all` (level 130).
  - Tests: VaultSentinelTest. Attack renders: `docs/bosses/designs/vault_sentinel_atk*.png`
    (`BossDesignRenders.ATTACKS`).
  - Every later boss: after its body is approved, send its attack renders too.
