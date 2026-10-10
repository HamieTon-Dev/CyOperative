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

Status: **ALL 24 BOSSES HAVE UNIQUE BODIES (v0.12.0).** 12 expansion bosses built (5 approved one by one, Circuit Hydra owner-directed step by step, 6 autonomous) and all 12 classic bodies (autonomous). The World Kit is complete (12/12). Open: B1 cross beams, B2 boomerang shots, F2 BOSS CODEX, F3 music/victory sequence, F4 co-op tests, F5 balance pass.

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

### Owner cards for the last 7 bosses (2026-10-10) — built autonomously
Owner: "You understand the art direction now. I think you can do the next 7 bosses autonomously…
These 7 bosses could spawn at any boss level."
Cards: `boss-concepts/packet_reaper_card.png`, `circuit_hydra_card.png`, `worm_queen_card.png`,
`boss_pack_beta_cards.webp` (Pulse Bishop, Glitch Forge, Black Ice Overlord, Botnet Monarch).

| Boss (role) | HP / SPD / DMG / ARM / BOUNTY | Abilities (from the cards) | How it's built |
|---|---|---|---|
| **Packet Reaper** (ASSASSIN, "fast, relentless, hunts backlines") | 1,200 / 10 / 32 / 8 / €12,325 | Dash Slash · Packet Scythes · Backline Dive · Trail Burst | dash with slash arcs; curving boomerang scythe shots that return; blink behind you then dive; dash path erupts after a delay |
| **Circuit Hydra** (AREA CONTROL, "splits, beams, dominates space") | 1,700 / 6 / 30 / 14 / €14,500 | Split Heads · Beam Arc · Segment Burst · Coil Crush | serpent body of glowing spheres trailing the head; splits into 2→3 beam heads by phase; fans of beams; every segment fires a ring; body coils around you and tightens, with a gap |
| **Worm Queen** (SUMMONER, "spawns, corrupts, overwhelms") | 1,800 / 5 / 26 / 16 / €13,050 | Swarm Hatch · Corruption Trail · Spike Burst · Queen Roar | eggs hatch into spiky swarmlings; leaves infected pools; spike rings; roar shockwave that speeds her swarm up |
| **Pulse Bishop** (CONTROLLER, "punishes predictable movement") | 1,400 / 7 / 30 / 10 / €13,775 | Line Warp · Cross Beam · Bishop Mines · Convergence Flash | warps across lanes leaving a light streak; + shaped beams that turn; mines arm then detonate when you come near; PULL toward it then a blinding (readable) flash blast |
| **Glitch Forge** (SUMMONER, "warps reality") | 1,550 / 4 / 24 / 18 / €13,775 | Decoy Clone · Corrupt Floor · Cube Barrage · Core Pulse | hologram clones that shoot and pop in one hit; floor tiles glitch into hazards in a grid; homing data cubes; big pulse that corrupts tiles |
| **Black Ice Overlord** (STATUS, "absolute cold") | 1,750 / 4 / 31 / 18 / €15,225 | Ice Laser · Freeze Patch · Crystal Volley · Permafrost Shell | freezing sweep laser; icy zones stack CHILL (slow; 5 stacks = FROZEN briefly); arcing crystal shells; shell phase (75% less damage, chills nearby) broken by sustained fire |
| **Botnet Monarch** (COMMANDER, "overwhelms with numbers") | 1,650 / 5 / 27 / 15 / €15,225 | Drone Ring · Summon Wave · Orbital Barrage · Sync Burst | orbiting drones that shoot (killable); drone waves; targeted orbital strikes with sky beams; drones link with lines then fire together |

**"Spawn at any boss level" (interpretation):** each keeps its planned slot (L150–220), and in
addition, from level 20 on, any boss room has a 25% chance to bring one of these seven instead of
the scheduled boss (scaled to that level like every boss). Level 10 stays the first-boss intro.

### World Kit — tiles, blocks and textures (owner sheet `boss-concepts/world_kit.webp`)
Owner: "The world kit could help every level look even better, including possibly boss levels."
| # | Item | Have today | Verdict |
|---|---|---|---|
| 01 | Grid floor tile | PLATES floor | ✅ refresh: bevelled plates with a cyan rim glow |
| 02 | Broken grid tile | — | ✅ new: cracked plate variant sprinkled to break repetition |
| 03 | Vent grate tile | small vent marks | ✅ upgrade to a full recessed grate tile |
| 04 | Power conduit tile | glowing trenches | ✅ new: red conduit lines with L/T bends between tiles |
| 05 | Server rack wall | SERVER_RACK | ✅ done 0.12.0: bevelled bays, vent grille, status light |
| 06 | Relay pillar | DATA_PILLAR | ✅ restyle: tall block with a glowing square frame on top |
| 07 | Data vault crate | CRATES | ✅ done 0.12.0: brackets, stacked seams, stencil, lock panel |
| 08 | Firewall barricade | ENERGY_BARRIER | ✅ restyle: hex-energy pane between two posts |
| 09 | Spark panel | — | ✅ new floor decor: animated electric sparks |
| 10 | Cable run | CABLE decor | ✅ restyle: thick red/yellow cable bundles |
| 11 | EMP dark-zone emitter | darkness system (Nullshade) | ✅ new decor: a local dark field for high-drama rooms (events/boss) |
| 12 | Light beacon | FLOOR_LIGHT | ✅ new: area light pylon that lights the floor around it |
Design notes from the sheet: block silhouettes for cover, tile variants break repetition, spark
panels and dark-zone emitters reserved for higher-drama rooms. **All 12 are viable.** Build after the 7 bosses.

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
- [x] B3 S3 hostile mines (HazardKind.MINE)
- [x] B4 S5 trails + tile corruption (Packet Reaper, Worm Queen, Glitch Forge)
- [x] B5 S7 status effects: SEIZED, ENCRYPTED, ON FIRE, PULLED, CHILL/FROZEN, all with on-body markers
- [x] B6 S1 lane/backline blink (Packet Reaper Backline Dive)
- [x] B7 S10 shells, untargetable windows, armor (Permafrost Shell, Nullshade, Hydra Head Shield)
- [x] B8 Tests for each system (dodgeable, telegraphed, co-op safe)

### Stage C — Boss Pack Alpha (v0.11.2)
- [x] C1 S6 dynamic cover + shots from blocks
- [x] C2 Vault Sentinel (body, 3 phases, 4 abilities)
- [x] C3 Packet Reaper (v0.11.8, autonomous)
- [x] C4 S8 multi-part bodies (bossTrail chain, hydra_head shares HP)
- [x] C5 Circuit Hydra (v0.11.12, autonomous)
- [x] C6 S9 eggs + minion buffs (HazardKind.EGG, Enemy.hasteTimer)
- [x] C7 Worm Queen (v0.11.9, autonomous)
- [x] C8 Renders + bot-beatable tests

### Stage D — Boss Pack Beta (v0.11.3)
- [x] D1 Pulse Bishop (v0.11.7, autonomous)
- [x] D2 S9 decoys (holo_clone enemy, 1 HP)
- [x] D3 Glitch Forge (v0.11.10, autonomous)
- [x] D4 Black Ice Overlord (v0.11.13, autonomous)
- [x] D5 S9 orbiting + linked drones (Enemy.orbitSlot, bossSync)
- [x] D6 Botnet Monarch (v0.11.11, autonomous)
- [x] D7 Renders + tests

### Stage E — Nullshade Specter + the last three (v0.11.4)
- [x] E1 S11 darkness, light bubble, eye glints, power-restore sequence
- [x] E2 Nullshade Specter (v0.11.3)
- [x] E3 Rootkit Apostle (built early, v0.11.2)
- [x] E4 Ransom King (v0.11.4)
- [x] E5 Spectral Firewall (v0.11.5)
- [x] E6 Renders + tests

### Stage F — Premium pass (v0.12.0)
- [x] F1 Unique bodies for the 12 classic bosses (CO-063) — v0.12.0, autonomous
- [x] F2 BOSS CODEX screen (met / defeated, abilities, best time, tier, bounty) — v0.13.0
- [ ] F3 Boss music per tier (victory sequence ✅ v0.13.0)
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
| Spectral Firewall | 2026-10-10 (`docs/bosses/designs/spectral_firewall_*`) | ✅ body approved (rev 3) — "that looks great, now build attacks". Attacks ✅ approved; Firewall Ring waves raised to 1/3/4 by phase ("phase three… push more than two rings… 3 on phase 2 and 4 rings on phase 3") | Owner card: `boss-concepts/spectral_firewall_card.webp`; "the barriers should have darker accents and be slightly taller"; "less of a grid pattern… solid blocks but more of a charred metal red hot metal look" |
| Pulse Bishop | 2026-10-10 | 🤖 built autonomously (owner delegated) | card: boss_pack_beta_cards.webp |
| Packet Reaper | 2026-10-10 | 🤖 built autonomously | card: packet_reaper_card.png |
| Worm Queen | 2026-10-10 | 🤖 built autonomously | card: worm_queen_card.png |
| Glitch Forge | 2026-10-10 | 🤖 built autonomously | card: boss_pack_beta_cards.webp |
| Botnet Monarch | 2026-10-10 | 🤖 built autonomously | card: boss_pack_beta_cards.webp |
| Circuit Hydra | 2026-10-10 | ✅ **FINAL**: owner-directed step by step; body, Head Shield, attacks and regrow-roar approved ("Approved, move on to the next boss") | card: circuit_hydra_card.png |
| Black Ice Overlord | 2026-10-10 | 🤖 built autonomously | card: boss_pack_beta_cards.webp |

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
- 2026-10-10 — Owner decision (Spectral Firewall): **the orbiting ring blocks shots.**
  - Slabs absorb the operative's shots with a spark, so you fire through the gaps as they rotate.
  - There are fewer gaps each phase. Touching a slab burns.
  - While the Firewall Ring attack is pushed out to the walls, the boss's own ring is gone: an
    exposed window.
  - Build this with the attacks.
- 2026-10-10 — Spectral Firewall revision 2: slabs 28% taller, with a charred outline, dark mortar seams and course line, a dark base band and a dark top cap.
- 2026-10-10 — Spectral Firewall revision 3: solid plates with no brick seams. Charred metal top and bottom, a glowing heat band and a white-hot centre, burn patches, heat cracks and rivets (all clipped to the plate), smaller flames.
- 2026-10-10 — v0.11.5 (27): Spectral Firewall attacks and boss-coloured arenas.
  - Ring shield (`BossDef.firewallRing`): `ringBlocks` in damageEnemy uses the shooter position
    (`cur`) on the drawn perspective ellipse (RING_SQUASH 0.36, centre e.y + 0.8r − 18).
    Plates: 9 slots, 6/7/8 filled, PLATE_SPAN 0.78. Touching a plate ignites.
  - Hazards: FIRE_WALL (gap angle ×1000 in x2, count in y2, half-width in windup; synced) and
    BURN_SECTOR. SWEEP with tick = 1 is a flame jet.
  - BURNING status (`ignite`), co-op wire v6, HUD ring line. The bot stands in a ring gap
    (`firewallGapPoint`).
  - Arena theme: the boss arena uses `plan.boss.color` for trim, pools and walls, plus
    `drawBossBorder`. Owner: "Can the boss arena match the boss color theme… unique to each boss."
  - Tests: SpectralFirewallTest.
- 2026-10-10 — v0.11.6 (28): Spectral Firewall's Firewall Ring now launches 1/3/4 walls by phase, then spread out further at the owner's request ("spread the rings out a little bit more"): wave gap 1.15 s / 1.05 s, about 200 units apart. Render atk9, test ringWavesBuildByPhase.
- 2026-10-10 — Owner handed over the last 7 bosses to build autonomously, plus the World Kit
  sheet. All logged in §2, including the any-boss-level rule and World Kit viability (all 12 viable).
  Building order: Pulse Bishop, Packet Reaper, Worm Queen, Glitch Forge, Botnet Monarch,
  Circuit Hydra, Black Ice Overlord, then World Kit.
- 2026-10-10 — v0.11.7 (29): Pulse Bishop (autonomous).
  - Patterns LineWarp, CrossBeam (4 or 8 sweeps), BishopMines (HazardKind.MINE: arm → trip within 70
    → 0.45 s fuse) and ConvergenceFlash (`Operative.pulled`, wire v7).
  - The body is drawn at radius 80. Tests: PulseBishopTest.
- 2026-10-10 — v0.11.8 (30): Packet Reaper (autonomous). Patterns DashSlash (+trail), Scythes (HazardKind.SCYTHE boomerang loop), BacklineDive; HazardKind.SLASH crescent. Tests: PacketReaperTest.
- 2026-10-10 — v0.11.9 (31): Worm Queen (autonomous).
  - New enemy SWARMLING, appended as `Enemies.expansion` so indices are stable.
  - Patterns SwarmHatch (HazardKind.EGG), CorruptionTrail and QueenRoar (`Enemy.hasteTimer/hasteMul`);
    Spike Burst reuses Bloom.
  - Tests: WormQueenTest.
- 2026-10-10 — v0.11.10 (32): Glitch Forge (autonomous).
  - Enemy HOLO_CLONE (pinned to 1 HP on summon).
  - HazardKind.TILE; patterns CorruptFloor (checkerboard), CubeBarrage (ProjKind.CUBE homing),
    CorePulse (ring plus delayed tiles).
  - Tests: GlitchForgeTest.
- 2026-10-10 — v0.11.11 (33): Botnet Monarch (autonomous).
  - Enemy ORBIT_DRONE (`Enemy.orbitSlot`; BossBrain.updateOrbiters places the drones on the ring).
  - Patterns DroneRing, OrbitalBarrage (HazardKind.ORBITAL) and SyncBurst (`GameEngine.bossSync`,
    wire v8).
  - Tests: BotnetMonarchTest.
- 2026-10-10 — v0.11.12 (34): Circuit Hydra (autonomous).
  - `BossDef.segments`; `GameEngine.bossTrail` is a chain at SEGMENT_GAP 52 (wire v9).
  - Enemy HYDRA_HEAD: damage redirects to the boss.
  - Patterns SplitHeads, BeamArc, SegmentBurst and CoilCrush (HazardKind.COIL, FIRE_WALL logic
    without burn).
  - Tests: CircuitHydraTest.
- 2026-10-10 — v0.11.13 (35): Black Ice Overlord (autonomous). **All 12 expansion bosses are now built.**
  - CHILL/FROZEN status (`Operative.chill/frozen`, chillSlow) and HazardKind.ICE.
  - Patterns IceLaser (SWEEP with tick 2), CrystalVolley (MORTAR with tick 2), FreezePatch and
    PermafrostShell (BossBrain.shellAbsorb, `GameEngine.bossIceShell`).
  - Co-op wire v10. Tests: BlackIceOverlordTest.
- 2026-10-10 — v0.11.14 (36): wild bosses and World Kit.
  - `LevelPlanner.rollWildBoss`: 25% from L20; BossBrain scales a wild boss's HP down below its
    home level. Tests: WildBossTest.
  - World Kit: DecorKind CRACKED_TILE, CONDUIT, SPARK_PANEL, BEACON and DARK_EMITTER, via
    `ArenaGenerator.worldKit` with its own seeded Random (layouts unchanged), also applied to
    hand-made rooms and the boss arena.
  - Restyled FLOOR_TILE, VENT, CABLE, DATA_PILLAR and ENERGY_BARRIER.
  - Renders: `docs/screenshots/world_kit_*.png`.
- 2026-10-10 — v0.11.15 (37): Worm Queen swarm (owner: "needs more of those mini bots/eggs").
  - BossDef.layEggs + BossState.eggTimer: egg cluster every 2.4 − 0.5·phase s, 3+phase swarmlings.
  - SwarmHatch 6/8/10 eggs; `Scaling.MAX_ALIVE_SWARM = 70` for hatching; SWARMLING 14 HP, 6 dmg, 150 speed.
  - Tests: WormQueenTest.aRealSwarm (>32 swarmlings alive).
  - Next: Circuit Hydra redesign, step by step with the owner.
- 2026-10-10 — Circuit Hydra redesign, step by step with the owner.
  - Step 1 answers: segmented metal, toxic green; silhouette and head rule left to me (core with necks
    recommended). Step 1b: much bigger, anchored, more dragon-like heads, keep the core.
  - Body v2 **approved** with two notes: "beam attacks to come from their mouths not the core", and
    the hydra "slowly floats back and forth from middle to 30% down and then back again".
- 2026-10-10 — v0.11.16 (38): Circuit Hydra body.
  - `engine/HydraRig.kt` shared by the engine and the renderer (bossTrail = 4 floats per head).
  - New `BossMove.SWAY`; `Hazard.head` lets beams follow a mouth; SegmentBurst fires from neck points.
  - `BossBody.previewSpan`/`previewDrop` frame big bodies on the sheets.
  - Tests: CircuitHydraTest updated (rig per phase, float band, beams start at heads).
  - Next: ask the owner about each attack (Beam Arc, Segment Burst, Coil Crush, Split Heads/regrowth).
- 2026-10-10 — Circuit Hydra step 2 answers: heads **shield the core** (kill all to expose it about 6 s,
  then they regrow); Beam Arc = **one beam per head**.
- 2026-10-10 — v0.11.17 (39): Head Shield + single-beam Beam Arc.
  - `BossDef.headShield`; BossState headUid/headCount/exposed.
  - `GameEngine.bossHeadMask` (co-op v11); the core is untargetable while shielded.
  - Body draws the shield dome, slumped sparking stumps and the exposed core; heads have HP bars.
  - Tests: headsShieldTheCoreUntilAllAreDown, beamArcOneBeamPerLivingHead; full suite green.
  - Renders: circuit_hydra_atk1–6.
  - Next: ask the owner about Segment Burst, Coil Crush and tuning (head HP, exposed window).
- 2026-10-10 — Circuit Hydra step 3 answers: Segment Burst → **neck volleys**; Coil Crush → **a bite**;
  heads at **6% HP** with a 6 s window; "maybe increase health pool to ensure we see all 5 heads by
  phase 3".
- 2026-10-10 — v0.11.18 (40): Neck Volley, Head Bite and tuning.
  - Head Bite: BossState bite arrays, `applyBites` stretches the rig, a wide LINE lane telegraph,
    `forEachOperativeOnSegment`.
  - A phase change ends the exposure; base HP 3,400.
  - Tests: headBiteLungesDownTheLaneAndCanBeDodged, neckVolleyRipplesUpTheNecks; full suite green.
- 2026-10-10 — Owner: heads should track the player (they already do: facing is 80% toward the
  player). After a regrow: invulnerable until the animation ends; heads move toward the player, point
  straight at them with jaws open in a charging look, roar for 2 s with screen shake, then return to
  normal.
- 2026-10-10 — v0.11.19 (41): regrow-and-roar.
  - `BossState.regrow` drives grow, lean, roar and settle on the rig.
  - The head mask carries LEAN_BIT/ROAR_BIT (co-op byte 0x7F); heads are untargetable meanwhile.
  - Renders: circuit_hydra_atk8_regrow, atk9_lean_in, atk10_roar and circuit_hydra_regrow_sequence.png.
- 2026-10-10 — Owner: "Approved, move on to the next boss. autonomously build the rest of the bosses
  and the world tiles upgrade."
- 2026-10-10 — v0.12.0 (42): F1 classic bodies and World Kit finish.
  - 12 BossBody files for the classics (Breach … Good Game), registered in BossBodies.
  - Rootkit has a hidden scanline silhouette.
  - Renders: `docs/bosses/designs/<id>_sheet/_fight.png` and `classics_overview.png`; the render
    harness now covers the classics.
  - World Kit 05/07 done; `docs/screenshots/world_kit_crates.png`.
  - Full suite green.
- 2026-10-10 — v0.12.1 (43): owner flagged the conduit and cable lines as "too bright to be part of
  the floor". Dulled the CONDUIT (dark red, glow 0.12, core 0.45) and CABLE cores (0.45/0.4).
- 2026-10-10 — Owner: "dont assign bosses to set levels… boss change of everything you listed level
  10-120 should be random. the most difficult bosses from the gamma pack would make sense to be locked
  to 120 and higher. lets randomize boss spawn on boss levels."
- 2026-10-10 — v0.12.2 (44): random bosses.
  - `Bosses.randomForLevel(level, runSeed)` runs a per-run shuffle deck; `eligible(level)` excludes
    GAMMA_IDS below GAMMA_FROM = 120.
  - The wild and rare rolls are removed. BossBrain scales HP and damage to the old schedule's boss for
    that level.
  - Tests: RandomBossTest (no Gamma before 120, no repeats, runs differ, deterministic per run,
    scaling).
  - This supersedes the "any boss level" wild rule of v0.11.14 and D1's fixed slots, which are now
    only the toughness curve.
- 2026-10-10 — v0.12.3 (45): owner "keep level 10 to the classics". `Bosses.eligible(<20)` = classics.
- 2026-10-10 — Owner: "go ahead with the codex, victory sequence and pause menu".
- 2026-10-10 — v0.13.0 (46): codex, victory sequence and pause menu.
  - BOSS CODEX screen (MenuTarget.CODEX): sections, silhouettes, dossier with phase switch and
    record.
  - Victory sequence (shatter + card); the room clear waits for it; co-op v12.
  - Pause menu MENU/BUILD/SETTINGS tabs.
  - Tests: BossVictoryCodexTest; SaveRewardsWeaponsTest now waits for the victory.
  - Renders: `docs/screenshots/{boss_codex, boss_codex_dossier, victory_shatter, victory_card,
    pause_build, pause_settings}.png`.
