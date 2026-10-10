# Boss Expansion Vol. 1 — build plan & progress log

_Owner brief (2026-10-10): "Use these as inspiration for the next level of this build. I want this
game to feel premium." Source sheets in `docs/boss-concepts/`: Boss Pack Alpha, Boss Pack Beta,
**Boss Pack Gamma**, the Nullshade Specter dossier and the Expansion Vol. 1 overview (12 bosses)._

> **Quality bar (owner, 2026-10-10):** "These bosses can easily dominate 60% of the arena. These are
> the premium boss expansions to this game. Must be very high quality."
>
> What that means for every expansion boss:
> - **Presence:**
>   - Big, detailed, animated bodies that react to phase.
>   - Attacks and hazards that can control **up to ~60% of the floor at peak**: cube walls, laser
>     sweeps, fire rings, infected zones, darkness.
> - **Always a way out:**
>   - At least ~40% stays safe or reachable at any moment.
>   - There is always a path out. The Vault Sentinel lockdown gap rule is the template.
>   - Every pattern is telegraphed.
> - **Polish before "done":**
>   - The owner approves the body, then the attack renders.
>   - Screen FX on big moments; co-op synced; bot-beatable test; dossier abilities match the sheet.

**Every session working on this stage: read this file first, continue from the first unchecked
box, tick boxes as they land, and add a dated line to the session log at the bottom.**

Status: **BUILDING — Stage A done (v0.11.0); Vault Sentinel (v0.11.1) and Rootkit Apostle (v0.11.2) built boss-by-boss with owner approval. Next: Ransom King body (Gamma order), then the rest.** Owner accepted every recommendation (D1–D6): "Build all of them as you see fit."

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

### Boss Pack Gamma — "4 high-tier corruption & control bosses" (sheet added 2026-10-10)
`docs/boss-concepts/boss_pack_gamma.webp`. This replaces my own D4 proposals in §4 for Rootkit
Apostle, Ransom King and Spectral Firewall, and adds a role label for Nullshade.
Sheet notes: 4 unique bosses · distinct mechanics · new arena hazards · higher challenge · bigger rewards.

| Boss (role) | HP / SPD / DMG / ARMOR / BOUNTY | Ability | Verdict | Needs system |
|---|---|---|---|---|
| **Rootkit Apostle** (AMBUSHER) | 1,500 / 6 / 28 / 14 / €13,050 | Burrow Drift | ✅ dives underground (untargetable), a glowing crack trail drifts toward you | S10 + S5 |
| | | Spike Eruption | ✅ clusters/lines of crystal spikes burst from the floor after a red floor telegraph | S5 |
| | | Infected Zone | ✅ the spots you stand in longest turn into corrupted rings (it "infects safe areas"), forcing you to relocate | S5 |
| | | Rootkit Bloom | ✅ erupts in a bloom of spikes and a radial burst where it surfaces | S5 + radial |
| **Ransom King** (LOCKDOWN) | 1,650 / 5 / 29 / 17 / €14,500 | Key Zone | ✅ golden diamond zones; standing in one drains his shield | S10 |
| | | Lock Grid | ✅ padlocked cubes rise and seal sections of the arena (reuses the Vault Sentinel barrier cubes, with a padlock look) | S6 ✅ |
| | | Royal Seizure | ⚠️ "seizing attacks — poor positioning leads to confinement": a telegraphed crown slam; anyone caught is SEIZED (rooted ~1 s) inside a cube cage with one side open | S7 + S6 |
| | | Ransom Pulse | ✅ pulsing lock-rings expand from him (the ENCRYPTED movement penalty folds in here: rings tag you, then moving while tagged charges a burst) | S7 |
| **Spectral Firewall** (AREA DENIAL) | 1,700 / 5 / 31 / 18 / €15,225 | Firewall Ring | ✅ **Owner spec (2026-10-10):** "painful barriers that emerge from the boss and push outward all the way to the walls. Can set the player on fire if they touch them and punish inefficient or poorly planned movement." A ring of flame-wall segments with gaps expands from the boss to the arena walls; touching it sets you ON FIRE (burn over time) | new: expanding wall ring + BURNING status |
| | | Burn Sector | ✅ a pie slice of the arena ignites after a warning-triangle telegraph | new: sector hazard |
| | | Heat Collapse | ✅ the ring shrinks toward him and the safe space closes in, then resets | ring hazard |
| | | Purge Spin | ✅ spinning flame spiral / rotating flame arms | S2 |
| **Nullshade Specter** (BLACKOUT HUNTER) | 1,450 / 9.5 / 34 / 12 / €17,400 | EMP Blackout · Eye-Glint Lock · Ghost Dash · Static Needles | ✅ as the dossier (§2) | S11 |

New roles from Gamma: AMBUSHER, LOCKDOWN, AREA DENIAL, BLACKOUT HUNTER (they replace ASSASSIN /
CONTROLLER / AREA CONTROL / HUNTER on these four).

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

## 4. ~~Designs for the one-line bosses (proposal, D4)~~ — superseded by the Boss Pack Gamma sheet (§2); kept for reference

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
- [x] E1 S11 darkness, light bubble, eye glints, power-restore sequence
- [x] E2 Nullshade Specter (v0.11.3)
- [x] E3 Rootkit Apostle (built early, v0.11.2)
- [x] E4 Ransom King (v0.11.4)
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
| Vault Sentinel | 2026-10-10 (`docs/bosses/designs/vault_sentinel_*.png`) | ✅ **FINAL**: body (20% larger) and attacks approved — "Vault sentinel looks great thank you." | "Looks great… can it be slightly larger? … show me its attacks … spawn cubes (barrier blocks out of the floor restricting player movement)" |
| Rootkit Apostle | 2026-10-10 (`docs/bosses/designs/rootkit_apostle_*`) | ✅ **FINAL**: body (rev 2) and attacks approved — "Approved; start on Nullshade Specter" | "Darker body, eye can glow dim to bright back and forth slowly and is bright when attack" |
| Nullshade Specter | 2026-10-10 (`docs/bosses/designs/nullshade_*`) | ✅ body approved (rev 2, traced from owner reference) — "those look good". Attacks built ("The level should be extremely dark") — ✅ **FINAL**: "Approved; start on Ransom King" | Rev 1 rejected: "Not a fan of that design… trace as best as possible. There shouldn't be an outline really. Think DARKNESS GHOST"; eyes blink + faint light; "Fainter glow around the eyes… sharper"; "bigger eyes please" |
| Ransom King | 2026-10-10 (`docs/bosses/designs/ransom_king_*`) | ✅ body approved (rev 3, evil cursed crown) — "Approved, build its attacks". Attacks built and sent (atk1–atk7) | "can the crown be bigger, sharper… crooked?" → "Not the orientation of the crown… looks like a cursed crown. realign and then try a different design that looks like an evil crown" |
| Spectral Firewall | 2026-10-10 (`docs/bosses/designs/spectral_firewall_*`) | ⏳ body waiting | Owner card: `boss-concepts/spectral_firewall_card.webp` |

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
- 2026-10-10 — Owner sent **Boss Pack Gamma** (Rootkit Apostle, Ransom King, Spectral Firewall,
  Nullshade Specter). §2 now has the sheet's stats and abilities, and the quality bar is at the top.
  Vault Sentinel is final.
- 2026-10-10 — Rootkit Apostle body drawn (radius 76, black orb, magenta crystal crown, mask eye, infected floor bloom) and sent for approval. Its def is in `designed` only; mechanics come next.
- 2026-10-10 — Rootkit Apostle revision 2 (darker shell and crystals, eye breathes dim↔bright over ~3 s, blazes while charging).
  - New `anim` render: 48 frames in `app/build/boss_anim/<id>/`, stitched into `docs/bosses/designs/<id>_anim.gif`
    with PIL (resize 540, 128 colours, 100 ms frames).
- 2026-10-10 — v0.11.2 (24): Rootkit Apostle attacks.
  - Patterns `Burrow` (dive → tunnel → marked exit → erupt + bloom) and `SpikeEruption`
    (HazardKind.SPIKE: warn, burst once, linger).
  - `Infect`: HazardKind.INFECTED, a zone with an infection look, placed on the operatives' dwell
    hotspots (`GameEngine.dwellHotspots`, 100-unit grid, ~10 s fade).
  - `Bloom`: spike rings with 3 safe lanes.
  - BossBody.drawHidden draws the burrowing mound.
  - In the roster at L140. Tests: RootkitApostleTest.
  - The attack render harness now places the operative on camp spots and turns boss damage down,
    so fresh operatives survive level-140 hits in screenshots.
- 2026-10-10 — Owner approved Rootkit Apostle's attacks: "Approved; start on Nullshade Specter".
  - Nullshade body drawn and sent: hooded wraith drawn at 1.3× its hit radius, slit eyes, a cloak
    dissolving into magenta pixels, phantom afterimages in P2, red static in P3.
  - New `BossPose.veiled` for its shadow state (only glints show). The design sheet and GIF show the
    shadow state for stealth bosses.
  - Def: L240, EXTREME, base €435, `stealth = true`, provisional patterns; in `designed` only.
- 2026-10-10 — Nullshade revision 2, traced from the owner's reference image.
  - Faceted hood with no outlines; a pitch-black face.
  - Big, sharp blade eyes with a faint glow; they blink every 3.6 s and cast faint red light.
  - The body is a voxel-block cloud with magenta light between the blocks, plus a clawed hand,
    a floor energy ring and no aura disc.
- 2026-10-10 — v0.11.3 (25): Nullshade Specter attacks.
  - Darkness (S11): `GameEngine.darkness/darknessTarget/lightFlicker/bossVeil`, `blackout()`, red
    server sparks.
  - The renderer draws the overlay (saveLayer, DstOut light bubble of 175 units, alpha 0.985),
    then telegraphs, then the boss eyes via `BossBody.drawOverDark`. Owner: "extremely dark".
  - Eye windows: `Enemy.untargetable` (BossBrain.stealth) and a HUD lock line.
  - Patterns `GhostDash`, `Needles` (ProjKind.NEEDLE), `SparkAmbush`, `GridSurge`.
  - Co-op wire v4.
  - **Fixed level slots:** `BossExpansion.SLOTS` plus `Bosses.forLevel/cycleForLevel/firstLevelOf`.
    Unbuilt slots keep their classic at cycle 1. Nullshade sits at L240.
  - Tests: NullshadeSpecterTest, expansionBossesTakeTheirPlannedLevels.
- 2026-10-10 — Owner approved Nullshade's attacks: "Approved; start on Ransom King".
  - Ransom King body drawn and sent: block golem, big cube head with angry eyes and a jagged grin,
    spiked gold crown with flames, padlocked red cubes orbiting (2/3/4 by phase), cracks in P3.
  - Def: slot 9 (L210), LOCKDOWN, HIGH, base €362; provisional patterns; `designed` only.
- 2026-10-10 — Ransom King revision 2: the crown is 1.2× head width and tilted, with 7 sharp two-tone gold spikes fanning out at uneven heights and angles, a skewed band with gems and flames behind.
- 2026-10-10 — Ransom King revision 3: the crown is level again; blackened iron-gold bent thorn spikes with red-hot cracks, a slit-pupil eye gem, smoky crimson fire and ember drips.
- 2026-10-10 — v0.11.4 (26): Ransom King attacks.
  - Key shield: `BossDef.keyShield`, BossBrain.ransomShield/keyCaptured, damage ×0.25 shielded and
    ×1.5 for 6 s once decrypted. HazardKind.KEY_ZONE has its progress in `windup` (synced).
  - LockGrid (Barrier.STYLE_LOCK → ObstacleKind.LOCK_CUBE). Walls flip inside the room, cubes can
    sit flush.
  - RoyalSeizure: `Operative.rooted`; the host ignores a seized guest's position and the guest mirror
    doesn't move.
  - RansomPulse: HazardKind.RANSOM_RING → `encrypt()`; moving fills the burst meter over 1.1 s.
  - HUD shield line and status markers over the operatives. Co-op wire v5.
  - The bot captures key zones. Tests: RansomKingTest.
- 2026-10-10 — Owner asked whether Ransom King works with a moving player, and whether "the
  lockdown" box is safe at the top of the screen or next to a block. RansomKingEdgeCaseTest covers it:
  - Moving out of the slam avoids SEIZED and no cage rises.
  - Lock Grid never leaves a moving operative inside a cube.
  - The cage leaves a way out at the top edge, all corners, the side walls, the middle and beside a
    block (flood-fill reach > 260²). Corners need only 4 cubes, edges 8, open floor 15.
- 2026-10-10 — Owner: "start on Spectral Firewall" (sent a clearer card).
  - Body drawn and sent: dark armoured spiked sphere, red-white core eye, a rotating ring of curved
    burning firewall slabs with gaps (6/7/8 of 9 by phase), heat glow and embers.
  - Def: slot 10 (L230), AREA DENIAL, HIGH, base €380, DRIFT; provisional patterns; `designed` only.
  - Owner spec for the attacks: the Firewall Ring emerges from the boss and pushes out to the walls,
    and touching it sets you on fire (log §2).
