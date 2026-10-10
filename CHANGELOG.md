# Changelog

## 0.11.17 (versionCode 39) — 2026-10-10 · Circuit Hydra: Head Shield
### Changed
- **Circuit Hydra, Head Shield** (owner pick: "Heads shield the core"):
  - **Heads:** every head is a target with its own HP bar.
  - **Shield:** while any head lives, a green shield dome covers the core. Aim and shots pass the core
    and go to the heads.
  - **Exposed core:** destroy every head and the core blazes open for 6 seconds ("CORE EXPOSED").
    Then all the heads regrow.
  - **Destroyed heads:** the neck slumps to the floor and sparks, and that head's beam dies with it.
  - **Phases:** each new phase grows another head (3 → 4 → 5).
- **Beam Arc** (owner pick): one thick sweeping beam from each living head's mouth, with neighbouring
  heads sweeping in opposite directions (it used to fire fans of 3).
- Split Heads is gone; phase 2 is now called "FOURTH HEAD".
- Co-op wire format v11 (synced head state).

## 0.11.16 (versionCode 38) — 2026-10-10 · Circuit Hydra redesign: body
### Changed
- **Circuit Hydra has a new body** (owner-directed, step by step):
  - **Look:** an armored reactor core in toxic green and gunmetal, with long necks of chained metal
    vertebrae and heavy mechanical dragon heads: split jaws with fang rows, a big brow ridge and
    swept horns.
  - **Size and heads:** 1.6× bigger than before, with 3, 4 or 5 heads by phase. The hitbox is still
    the core.
  - **Movement:** it floats slowly up and down the middle of the arena, between 50% and 30% down.
  - **Beams and bursts:** Beam Arc fires from each head's mouth, and the beams follow the head as it
    sways. Segment Burst fires from the necks.
  - **Split heads:** they sit on the body's extra heads; shooting them still drains the hydra's HP.
- Attacks are next, each one reviewed by the owner.

## 0.11.15 (versionCode 37) — 2026-10-10 · Worm Queen swarm
### Changed
- **Worm Queen is a real swarm now** (owner request):
  - She drops a fresh egg cluster next to herself every 2.4 s (1.9 s in phase 2, 1.4 s in phase 3),
    3–5 swarmlings per egg, on top of Swarm Hatch.
  - Swarm Hatch lays 6/8/10 eggs by phase (was fewer), hatching faster.
  - Swarm cap raised to 70 swarmlings (other enemies stay capped at 32).
  - Swarmlings are lighter to match the numbers: 14 HP (was 22), contact damage 6 (was 9), faster.
  - Her body shows more crawling swarmlings (5/7/9 by phase).

## 0.11.14 (versionCode 36) — 2026-10-10 · Wild bosses + World Kit
### Added
- **Any boss level** (owner request): from level 20, every boss room has a 25% chance to bring one of
  the seven Pack Alpha/Beta bosses instead of the scheduled one: Pulse Bishop, Packet Reaper, Worm
  Queen, Glitch Forge, Botnet Monarch, Circuit Hydra or Black Ice Overlord. Early in a run they're
  toned down to the scheduled boss's toughness, and they keep their premium bounty. Level 10 is
  always the first-boss intro.
- **World Kit** (owner sheet), for every room (generated and hand-made) and the boss arena:
  - **New decor:**
    - **Broken grid tiles:** cracked floor plates.
    - **Power conduits:** red energy lines with L bends and pulses running along them.
    - **Spark panels:** electric sparks, from level 15.
    - **Light beacons:** cyan pylons that light the floor around them.
    - **EMP dark-zone emitters:** purple devices that swallow the light around them, from level 40
      and in boss rooms.
  - **Upgraded pieces:**
    - **Floor tiles:** a rim glow.
    - **Vents:** recessed grates with depth.
    - **Cables:** thick bundles with red and yellow cores.
    - **Relay pillars:** a glowing square frame and lit corners.
    - **Firewall barricades:** a hex energy pane between posts.
  - The boss arena is dressed with conduits, beacons, spark panels and dark-zone emitters in the
    boss's colours.
  - Layouts and spawns are unchanged: decoration uses its own seed.

## 0.11.13 (versionCode 35) — 2026-10-10 · Black Ice Overlord
### Added
- **Black Ice Overlord** (Boss Pack Beta) is the **level 220** boss: STATUS, threat HIGH, base bounty
  €380. Its body is a dark navy armoured golem encased in cyan ice crystals (a crown of shards and
  crystal pauldrons) with glowing ice eyes, two shoulder ice cannons and drifting snowflakes.
  - **CHILL** (new status): each stack slows you 9%, shown as frost pips over your head. Five stacks
    **FREEZE** you in an ice block for about 1 s. Stacks wear off over time.
  - **Ice Laser:** sweeping freezing beams from its shoulder cannons. A hit chills you hard.
  - **Freeze Patch:** frosty zones (one under you) that chill you every half second you stand in them.
  - **Crystal Volley:** ice crystals arc onto marked spots, and a hit chills.
  - **Permafrost Shell:** a faceted ice dome cuts the damage it takes to a quarter and chills anyone
    near it. Keep firing (the boss bar shows the shell %) and it shatters into a stun window. It
    also melts on its own after 7–8 s.
  - **Phases:** PHASE 1 → DEEP FREEZE → ABSOLUTE ZERO.
- Co-op wire v10 (CHILL, FROZEN and the shell are synced).

## 0.11.12 (versionCode 34) — 2026-10-10 · Circuit Hydra
### Added
- **Circuit Hydra** (Boss Pack Alpha) is the **level 200** boss: AREA CONTROL, threat HIGH, base
  bounty €362. Its body is a real serpent: a chain of dark crimson armoured spheres with glowing core
  rings that snakes after its beam head as it sweeps the arena.
  - **Split Heads:** extra beam heads sprout on necks off its body, one in phase 2 and two in phase 3.
    Every head drains the same HP bar, so shoot whichever head you can reach.
  - **Beam Arc:** every head sweeps a fan of beams, filling the arena with crossing fire lanes.
  - **Segment Burst:** its body segments fire rings of shots.
  - **Coil Crush:** its body coils around you and tightens. Slip out through the gap.
  - **Phases:** PHASE 1 → SPLIT → HYDRA STORM.
- Co-op wire v9 (the serpent body is synced).

## 0.11.11 (versionCode 33) — 2026-10-10 · Botnet Monarch
### Added
- **Botnet Monarch** (Boss Pack Beta) is the **level 190** boss: COMMANDER, threat HIGH, base bounty
  €380. Its body is a crimson spiked command sphere with a blazing core and blinking uplink
  antennas, over a glowing dashed orbit track.
  - **Drone Ring:** armoured drones ride the orbit ring around it and shoot at you: 4, then 6, then
    8 by phase. Shoot them down to break the ring; it tops the ring back up later.
  - **Summon Wave:** packs of bot drones.
  - **Orbital Barrage:** sky beams come down onto target rings (the first on you) in volleys.
  - **Sync Burst:** the ring drones link up with glowing lines, then all fire at you at once while
    the monarch adds a ring of shots.
  - **Phases:** PHASE 1 → COMMAND NETWORK → TOTAL BOTNET.
- Co-op wire v8 (Sync Burst charge is synced).

## 0.11.10 (versionCode 32) — 2026-10-10 · Glitch Forge
### Added
- **Glitch Forge** (Boss Pack Beta) is the **level 180** boss: SUMMONER, threat HIGH, base bounty
  €344. Its body is a big dark server-core cube with neon magenta edges, glitch glyphs, a glowing
  target ring on top, RGB glitch tears and hologram cubes orbiting it.
  - **Decoy Clone:** translucent hologram copies that move and shoot spreads. One hit pops them, at
    any level.
  - **Corrupt Floor:** a checkerboard of floor tiles around you blinks, then glitches into a
    hazard. The other half of the tiles stays safe.
  - **Cube Barrage:** waves of spinning homing data cubes.
  - **Core Pulse:** a big pulse ring, and the floor it passes over corrupts behind it.
  - **Phases:** PHASE 1 → REALITY FRACTURE → TOTAL CORRUPTION.

## 0.11.9 (versionCode 31) — 2026-10-10 · Worm Queen
### Added
- **Worm Queen** (Boss Pack Alpha) is the **level 170** boss: SUMMONER, threat HIGH, base bounty
  €326. Her body is a huge dark armoured brood-sphere with crimson spikes and a glowing pink core,
  sitting in a spreading corruption pool with swarmlings crawling over her.
  - **Swarm Hatch:** pulsing egg sacs land around her, then crack and hatch into packs of
    **swarmlings**, a new small spiked enemy.
  - **Corruption Trail:** she surges after you, leaving infected pools along her path.
  - **Spike Burst:** rings of crystal spikes burst outward, with safe lanes through them.
  - **Queen Roar:** a shockwave, and her whole swarm surges at extra speed for a few seconds.
  - **Phases:** PHASE 1 → BROOD SURGE → INFESTATION.

## 0.11.8 (versionCode 30) — 2026-10-10 · Packet Reaper
### Added
- **Packet Reaper** (Boss Pack Alpha) is the **level 160** boss: ASSASSIN, threat HIGH, base bounty
  €308. Its body is a hunched crimson assassin mech with a red visor, magenta thrusters and two huge
  glowing packet scythes for arms. A third scythe blade appears on its back in phase 3.
  - **Dash Slash:** a telegraphed lane dash that ends in a crescent slash. In later phases it chains
    2–3 dashes.
  - **Trail Burst:** in phases 2–3 its dash path erupts behind it after a short delay.
  - **Packet Scythes:** spinning scythes loop out and swing back to where it threw them, crossing
    each other.
  - **Backline Dive:** it vanishes, marks a spot behind you (the side you're not facing), then dives
    through you.
  - **Phases:** PHASE 1 → LANE HUNT → NO ESCAPE.

## 0.11.7 (versionCode 29) — 2026-10-10 · Pulse Bishop
### Added
- **Pulse Bishop** (Boss Pack Beta) is the **level 150** boss: CONTROLLER, threat HIGH, base bounty
  €344. Its body is a floating dark prelate with a split mitre and a glowing cross, and a burning +
  sigil on the floor beneath it.
  - **Line Warp:** blinks onto your row or column, leaving a light streak, then fires a beam down
    the lane.
  - **Cross Beam:** + shaped beams. They turn in phase 2, and in phase 3 diagonals are added (an
    8-way star). Cover stops them.
  - **Bishop Mines:** spiked mines arm after about 1 s. Once armed they trip when you come close and
    blow after a short fuse.
  - **Convergence Flash:** a tether of light drags you toward it, then a blinding white flash blasts
    the area around it. Run against the pull.
  - **Phases:** PHASE 1 → SACRED GEOMETRY → EXCOMMUNICATION.
- Co-op wire v7 (PULLED is synced; the guest is dragged too).

## 0.11.6 (versionCode 28) — 2026-10-10
### Changed
- **Spectral Firewall's Firewall Ring builds up** (owner request): phase 1 launches 1 wall, phase 2
  launches 3 and phase 3 launches 4, spread about 200 units apart (1.15 s and 1.05 s between walls)
  so there's room to line up between them. Each wall keeps its 2 drifting gaps, offset from the wall
  before, so you weave between them.

## 0.11.5 (versionCode 27) — 2026-10-10 · Spectral Firewall + boss-coloured arenas
### Added
- **Spectral Firewall** (Boss Pack Gamma) is the **level 230** boss: AREA DENIAL, threat HIGH, base
  bounty €380. Owner-approved body: a spiked core and an orbiting ring of charred, red-hot metal
  plates.
  - **Ring shield** (owner's choice): the orbiting plates soak up your fire, so you shoot through the
    gaps as they rotate past. There are 3 gaps in phase 1, 2 in phase 2 and 1 in phase 3. Walking
    into a plate sets you on fire. Blocking matches what you see on screen.
  - **Firewall Ring** (owner spec): burning walls with gaps burst out of the boss and push all the way
    past the arena walls. The gaps drift, so you have to move to stay lined up, and touching a wall
    sets you **ON FIRE**. While the walls are out, its own ring is gone and the core is exposed. The
    boss bar shows "RING UP — FIRE THROUGH THE GAPS" or "RING LAUNCHED — CORE EXPOSED".
  - **Burn Sector:** wedges of the arena, the first aimed at you, show dashed edges and warning signs,
    then ignite.
  - **Heat Collapse:** a fire wall closes in from the arena edges toward the boss. Slip through a gap.
  - **Purge Spin:** roaring flame jets spin around it. They stop at cover and set you on fire.
  - **ON FIRE:** a 2.5 s burn that ignores invulnerability, with flames shown on the operative.
  - **Phases:** PHASE 1 → HEAT RISING → MELTDOWN.
- **Boss-coloured arenas** (owner request): every boss arena (classic and new) takes its boss's
  colours. The floor trim, light pools and walls are tinted, and a glowing border in the boss's accent
  colour pulses around the edge of the screen.
### Changed
- Co-op wire format v6: the ring and ON FIRE are synced. Both players need this version.

## 0.11.4 (versionCode 26) — 2026-10-10 · Ransom King
### Added
- **Ransom King** (Boss Pack Gamma) is the **level 210** boss: LOCKDOWN, threat HIGH, base bounty
  €362. Owner-approved body: a block-golem king with an evil cursed crown.
  - **Key Zone:** he fights ENCRYPTED, with a red lock-shield dome that cuts his damage taken to a
    quarter.
    - Golden key zones appear around the arena. Each one shows a fill meter and a timer.
    - Stand in a zone to unlock it. Once every zone in the wave is unlocked, he's DECRYPTED for
      6 s and takes 50% extra damage, then the shield reforms.
    - The boss bar tells you which state he's in.
  - **Lock Grid:** walls of padlocked red cubes rise across the arena near you, sealing it into
    sections. Every wall has openings, and walls never rise on top of you.
  - **Royal Seizure:** a golden crown-slam circle marks your spot. If it catches you, you're
    SEIZED (can't move, gold chains) for about a second, inside a padlock cage with one side
    open, away from him.
  - **Ransom Pulse:** rings of lock-light with padlocks ride outward. A ring that tags you makes
    you ENCRYPTED for 3.5 s, shown by a padlock and a meter over your head. Standing still is
    safe, but moving fills the meter, and when it's full the ransom bursts on you.
  - **Phases:** PHASE 1 → RANSOM DEMAND → TOTAL LOCKDOWN.
### Changed
- Boss cubes can now sit flush against each other to form solid walls.
- Co-op wire format v5: shield, cube style and SEIZED/ENCRYPTED states are synced, and a seized
  guest can't move. Both players need this version.

## 0.11.3 (versionCode 25) — 2026-10-10 · Nullshade Specter
### Added
- **Nullshade Specter**, the EXTREME "Blackout Hunter", is the **level 240** boss. It also turns up
  as a rare 5% stalker in boss rooms from level 150. Base bounty €435.
  - **Body:** a darkness ghost traced from the owner's reference.
    - A faceted hood with no outlines, over a pitch-black face.
    - Big, sharp red eyes that blink and cast a faint red light.
    - A body that is a cloud of black voxel blocks with magenta light between them, a clawed hand
      and a magenta ring on the floor.
  - **EMP Blackout:** the fight opens with an EMP and the room goes **extremely dark**. You keep a
    small light bubble. Its eyes, every attack warning and the sparking server blocks stay visible.
    When it dies, the power comes back on.
  - **Eye-Glint Lock:** you can only lock on to it, and only hurt it, while its eyes are open. The
    boss bar shows "EYES OPEN — LOCK ON" or "IN THE SHADOWS — NO LOCK". The windows get shorter but
    come more often in each phase, and the room lifts very slightly while its eyes are open.
  - **Ghost Dash:** a telegraphed dash that hits once and leaves a short corruption trail.
  - **Static Needles:** spreads of faint, fast needles with static trails.
  - **Spark Ambush:** it melts into the shadows. A server block is marked, then sparks spray out
    of it and the specter steps out beside it.
  - **Grid Reboot Surge (phase 3):** rapid EMP rings while the lights stutter on and off.
  - **Phases:** BLACKOUT INITIATION → PHANTOM HUNT → GRID REBOOT FRENZY.
### Changed
- **Expansion bosses keep their planned levels** (130–240), whatever order they're built in.
  A level whose new boss isn't finished yet keeps its old boss.
- Co-op wire format v4 (darkness and eye state are synced). Both players need this version.

## 0.11.2 (versionCode 24) — 2026-10-10 · Rootkit Apostle
### Added
- **Rootkit Apostle** (Boss Pack Gamma) joins the roster at **level 140**: AMBUSHER, threat HIGH,
  bounty base €326.
  - **Body:** a black armoured orb with a dark magenta crystal crown and a mask eye. The eye
    breathes slowly from dim to bright and blazes when it attacks (owner-approved design).
  - **Burrow Drift:** it dives underground (it can't be hit there) and tunnels toward you as a
    heaving, cracked mound with a glowing trail. Its exit is marked before it erupts. In later
    phases it erupts with rings of spikes.
  - **Spike Eruption:** lines of crystal spikes ripple out of the floor toward you: one line,
    then three, then all around.
    - Each spot shows a cracked diamond warning first.
    - Each spike hits once as it bursts.
  - **Infected Zone:** it infects where you stand and the spots you've stood in longest lately,
    so camping gets punished and you have to keep relocating.
  - **Rootkit Bloom:** rings of spikes burst outward from it in waves, with three straight safe
    lanes cut through every ring.
  - **Phases:** PHASE 1 → INFECTION → ROOTKIT BLOOM.

## 0.11.1 (versionCode 23) — 2026-10-10 · Vault Sentinel
### Added
- **Vault Sentinel** joins the roster at **level 130**: TANK, threat HIGH, bounty base €326. Its body
  is 20% larger than the first draft, as the owner asked.
  - **Cover Deploy:** barrier cubes rise out of the floor around you in short wall segments.
    - Each spot flashes on the floor first, so you can step clear.
    - Once risen, the cubes block movement and shots for about 8 s, then sink.
  - **Laser Sweep:** a telegraphed laser sweeps an arc toward you (two or three beams in later
    phases). The cubes stop it, so you can hide behind them.
  - **Lockdown Cube:** a box of cubes rises around you. One side is always open, the side away
    from the boss, or the next side that leads out if a wall is in the way.
  - **Pulse Mortar:** shells lob onto marked spots, the first one on you. They fly over the cubes.
  - **Phases:** PHASE 1 → LOCKDOWN → VAULT BREACHED. It drops its own cover modules as the fight
    goes on, and the cracked core blazes in the last phase.
- Co-op: barrier cubes are synced to the partner (wire format v3, so both players need this version).

## 0.11.0 (versionCode 22) — 2026-10-10 · Boss Expansion Vol. 1, Stage A
### Added
- **Threat dossier card** during every boss entrance: role badge (TANK, ASSASSIN, …),
  threat-tier skulls (LOW / MEDIUM / HIGH / EXTREME), up to four signature abilities and the
  **bounty** in € the kill pays. It sits mid-screen so the boss arriving at the top stays in view.
- **Screen effects:**
  - Boss kill: a short freeze, white flash, heavy shake, then 1.4 s of slow motion.
  - Boss phase change: a quick freeze with a flash in the boss's colour and a shake.
  - Heavy hits on you (12%+ of max HP) shake the screen a little.
- **SCREEN SHAKE** toggle in Settings (the setting existed but was never wired up).
- Every boss now has a role and a threat tier. The 12 current bosses are tagged.
- Groundwork for the 12 new bosses at levels 130–240:
  - a per-boss body renderer, so each boss gets its own look instead of the hexagon;
  - Nullshade Specter can turn up as a rare boss (5%) from level 150 once it is built.
- First new boss body drawn, **Vault Sentinel**, waiting for owner approval. It doesn't
  appear in runs until its mechanics are built.


## 0.10.3 (versionCode 21) — 2026-10-09
### Added
- **WEAPON SLOTS** permanent upgrade on the UPGRADES (OP level-up) screen, for € earned in runs.
  It has 5 levels; each adds one weapon slot (7 → 12):

  | Slot | OP level | Cost |
  |---|---|---|
  | 8 | 10 | €5,000 |
  | 9 | 20 | €15,000 |
  | 10 | 30 | €40,000 |
  | 11 | 50 | €100,000 |
  | 12 | 80 | €250,000 |

  - Until the OP level is reached, the card shows a grey padlock with the level it needs.
  - In runs, the slot counter, the card screen, the swap grid and the shop use your own slot
    count (co-op partners bring theirs).
### Changed
- **Difficulty now scales with OP level only** (owner, 2026-10-09):
  - Removed the late ramp (the extra +HP per level past 30) and build-size threat scaling on
    every difficulty. The base curves are back to the original.
  - OP-level threat scaling (+2% HP / +1.2% damage per OP level, ×3 / ×2.2 at OP 101, then
    slow growth; mastery included) applies from level 1, halved on Easy.
  - Hard stays ×1.45 HP / ×1.4 damage over Normal at the same OP level.
  - Deep levels keep getting busier: up to 70 threats per level and up to 50% elites past
    level 50.

## 0.10.2 (versionCode 20) — 2026-10-09
### Added
- **Hold for details** (owner, 2026-10-09) on upgrade cards, shop items and the weapon swap grid:
  the full description plus **what changes**, each stat current → after with the change.
  Green means it goes up, red means it goes down. This includes main gun DPS, all-weapons DPS,
  damage, attack speed, crits, HP, armor, speed, nodes and blades. Weapons gained, lost or
  levelled show their level and rough damage per second.
- The swap confirmation shows every stat change of the swap plus the full description of the
  weapon removed (red) and the new one (green).
### Changed
- The upgrade shop offers new weapons even with all 7 slots full; buying one opens the swap grid
  (tap a weapon, YES / NO). It only charges if you confirm.
- **Difficulty** (owner: "hard was fine how it was; quite hard by 30"): every extra on top of
  the base curve now does nothing up to level 30, fades in from 31 and reaches full strength at
  level 80. That covers OP-level/mastery threat bonus, build-size adaptive threat and the late
  ramp.
  - Levels 1–30 play exactly as before 0.9.1 on every difficulty.
  - The extras are gentler: late ramp +2% HP / +1.5% damage per level; adaptive +1% HP per pick
    and +8% per weapon.
  - Easy gets half of the extras. Hard is always tougher than Normal.
  - Hard, OP 50, typical build, enemy HP vs the base curve: ×1.45 to level 30, ×2.6 at 40,
    ×4.5 at 50, ×7.7 at 60 (0.10.1 was ×4.6 at 30, ×9.8 at 40, ×14 at 50).

## 0.10.1 (versionCode 19) — 2026-10-09
### Changed
- Weapon slots (owner, 2026-10-09): at most **7 weapons** equipped. Weapons are the 30 arsenal
  weapons plus Packet Scatter, Exploit Lance, Plasma Beam, Encryption Blades, EMP Burst,
  Logic Bombs, Malware Missiles, Arc Discharge, Quantum Railgun and Orbital Strike. Gun
  modifiers (Multishot, Penetration…) and Packet Nodes are power-ups.
- Every card screen now shows **power-up, power-up, weapon** (instead of three random cards,
  which were often all weapons). If one kind runs out, the other fills the gap.
- Picking a new weapon with all 7 slots full opens the weapon grid:
  - Tap a weapon to replace it, or hold it to see what it does.
  - The confirm screen shows [old] → [new] and asks "You want to replace ___ with ___?"
  - YES swaps them; NO goes back to the grid. KEEP MY WEAPONS returns to the cards.
- Levelling up a weapon you already have never needs a slot.
- The upgrade shop only offers new weapons while a slot is free.
- Runs saved with more than 7 weapons keep them; new ones need a swap.
- Co-op: the guest's weapon swaps go to the host (wire format v2; both players need this
  version).

## 0.10.0 (versionCode 18) — 2026-10-08
### Added
- **CO-OP** (owner, 2026-10-08), on the main menu. Needs a Firebase project; see FIREBASE_SETUP.md.
  Without one, the screen says co-op is unavailable and solo play is unaffected.
  - Register a callsign and get a friend code (XXXX-XXXX) to copy or share.
  - Add friends by code; accept or decline friend requests. The friends list shows who's online.
  - Invite an online friend; they JOIN or DECLINE. In the two-player lobby the host picks the
    difficulty and starts.
  - Live two-player campaign run:
    - Each operative has its own permanent upgrades, skin and body, and picks its own cards.
    - Threats chase whoever is closest.
    - A downed operative is revived by the partner standing next to them for 3s. Anyone
      downed is back at half HP on the next level. The run ends only when both are down.
    - Threats have ×1.4 HP (bosses ×1.7), and there are about 35% more of them.
    - Threat scaling uses the higher OP level of the two.
    - Both players keep full rewards (€, Operative XP, achievements, records).
    - Co-op can't be paused or saved; no upgrade shop in co-op.
  - If either player disconnects, the other carries on solo from the same moment.
  - HUD: partner HP bar (magenta), partner ring on the field, revive ring and progress,
    "waiting for partner" on the card screen.
- Networking: the host runs the game and sends compact snapshots ~10×/s. The guest moves its own
  operative locally (no input lag) and sends its position and picks. Measured ~10–15 MB per hour
  per pair.
- Security rules for Firestore and the Realtime Database (`firebase/`).
- INTERNET permission (co-op only).

## 0.9.7 (versionCode 17) — 2026-10-08
### Changed
- Endless progression (owner: "scale it to where play never ends"). OP level now goes to 9,999
  (was 999).
- Permanent upgrades keep going past their max as MASTERY levels, up to level 9,999. Each
  mastery level needs one OP level (mastery 50 needs OP LVL 50), and costs keep rising.
  - Endless with diminishing returns (√ of mastery): max HP, damage, crit damage, firewall,
    node damage, healing, boss damage, data gain, € gain.
  - Soft-capped, creeping toward a ceiling: attack speed (max +120%), move speed (+60%),
    range (+80%), packet speed (+100%), crit chance (+45%), damage reduction (40%),
    card quality.
  - Still hard-capped, because more would break runs: starting nodes, starting upgrades,
    rerolls.
- Threat scaling keeps going past OP 101 on a log curve: HP ×3 at OP 101, ×7 at OP 1,000 and
  ×11 at OP 9,999; damage ×2.2 / ×4.2 / ×6.2. Threats also answer mastery: for every ×4 that
  mastery adds to your damage (or staying power), threat HP (or damage) rises ×2. Mastery
  always pays off, and fights stay real.
- The run picker shows the combined threat bonus; upgrade costs show thousands separators.

## 0.9.6 (versionCode 16) — 2026-10-08
### Added
- Four BOSS 2 music variants (Boss 2, Boss 2 alt, Boss 2 Remix ×2). They join the boss rotation
  with the level-10 tracks and shuffle into normal combat like every other track.
### Changed
- Smaller download: the whole soundtrack is re-encoded from MP3 (~195 kbps, with embedded cover
  art) to Ogg Vorbis (~92 kbps, no artwork). Music went from ~116 MB (24 tracks) to ~56 MB
  (28 tracks); the debug APK dropped from ~137 MB to ~73 MB.

## 0.9.5 (versionCode 15) — 2026-10-08
### Changed
- Fullscreen gameplay: the status bar is hidden while playing (swipe down from the top edge to
  peek at it; a second swipe still opens notifications / Quick Settings). Navigation bar and
  gestures are untouched; menus get the normal bars back.
- New compact HUD: one header row (LVL · SCORE · € | device clock · pause), thin CORE HP and
  THREATS bars with labels and values, buff chips in one scrolling row with a "+N" counter for
  chips out of view. Tap a chip for its name, effect, description and duration.
- Pull handle under the HUD folds it to a small pill (health diamond + clock + pause) for more
  battlefield; tap it again to unfold. The HUD fades out while the notification shade is open,
  and stays visible over the pause menu (its button turns into ▶ RESUME there).
- The arena now starts right under the HUD as measured (no fixed pixel offsets), so it gains
  space on every phone; gate / shop / vault hints and banners stack below the HUD.
- The HUD keeps clear of camera cutouts on all sides.

## 0.9.4 (versionCode 14) — 2026-10-08
### Changed
- NEON OPERATIVE and the shopkeeper are now traced from the app icon at its exact proportions —
  the same hood, face shield, headset/antenna, shoulders, strap, collar tab and button as the
  main-menu emblem (shared drawing code) — standing on a tapered robe with sleeves and legs.
- Shopkeeper looks match the rarest stock on the counter: all GOLDEN → gold; one TITANIUM →
  titanium silver; two or three TITANIUM → menacing black with blood-red trim; all four
  TITANIUM → cycling spectrum. Shop stock is now drawn by rarity weight, so TITANIUM stays rare.
- Skip-shop prompt: walking into the top gate while a shop is on offer asks "SKIP THE UPGRADE
  SHOP?" — green [NO] locks the top gate red and flashes a gold arrow toward the shop gate;
  red [YES] moves on to the next level and closes the shop.
### Fixed
- Shop gate placement verified clear in generated, Data Vault and boss rooms (tests).

## 0.9.3 (versionCode 13) — 2026-10-08
### Changed
- Adaptive threat (owner: "levels 50+ scale too slowly if you have many weapons"): threat and
  boss HP rise +1.5% per upgrade pick and +10% per weapon fighting for you; damage +0.6% per pick
  and +3.5% per weapon. Fades in from level 21 (full by 40); levels 1–20 are unchanged. Fixed per
  level; the level banner shows "ADAPTED TO YOUR ARSENAL ×N" when it is significant.
- Deep levels are busier: threat count keeps rising past level 50 (to 70 per level), and the
  elite chance climbs from 35% toward 50%.

## 0.9.2 (versionCode 12) — 2026-10-08
### Fixed
- The shop's side gate can no longer be blocked: when a shop is offered the game picks a spot on
  the left or right wall whose doorway (and the space in front of it) is free of walls and blocks
  and that the operative can walk to; mid-height first. The gate, hint and terminal message show
  which wall. Tested on 150 generated rooms, walking in each time.

## 0.9.1 (versionCode 11) — 2026-10-08
### Added
- Data Vault cache: once the vault room is clear, walk up to the gold cache block in the middle —
  it glows, opens (light leaking through cracks), shakes harder and harder for 1.3 s, then bursts
  in a gold explosion and pays out €100 × level (minimum €1,000; level 70 → €7,000).
### Changed
- Harder late game (owner: "after level 60 I feel invincible"): from level 31 enemy HP compounds
  +3% per level and damage +2.2% per level on top of the old curve (level 60: HP ×25.8 instead of
  ×10.6, damage ×8.2 instead of ×4.3). Bosses follow the same curve.
- OP level scaling: every account OP level above 1 adds +2% threat HP and +1.2% threat damage,
  bosses included (up to ×3 / ×2.2 at OP 101). The difficulty picker shows the current bonus.

## 0.9.0 (versionCode 10) — 2026-10-08
### Added
- Upgrade shop: after a cleared level's upgrades are picked there is a 1-in-20 chance the
  shopkeeper calls in — a terminal-style "> RECEIVING MESSAGE … > UPGRADE SHOP AVAILABLE"
  pop-up types out and a gold side gate opens in the left wall. It leads to a small, randomly
  furnished shop room (no threats). The shopkeeper is the main-menu hooded operative with a gold
  shield and the face X,.,.X. Four GOLDEN / TITANIUM mods sit on the counter (GOLDEN from €1,000,
  TITANIUM from €2,500, +€25/+€50 per level); stand at the counter and tap one to buy it with this
  run's €. Leave through the top gate to the next level. Saving is paused inside the shop.

## 0.8.0 (versionCode 9) — 2026-10-08
### Added
- Environments (data/Environments.kt): 12 colour themes (Cyan Grid, Emerald Dataworks,
  Amethyst Core, Magma Forge, Arctic Server Farm, Toxic Lab, Rose Neon District, Golden Vault,
  Deep Ocean Relay, Solar Flare Array, Ghost Protocol, Ultraviolet Mainframe), each with its own
  floor, plate, light, trench, wall and trim colours. The theme changes every 3 levels in a
  per-run shuffled order ("ENTERING MAGMA FORGE" on the banner).
- 8 floor tile patterns (plates, hex, grid, circuit, diamond, brick, perforated dots, grooves);
  every level also rolls its own tile size, trench spacing, flow direction and shade.
- 6 new wall types: Blast Wall, Coolant Pipes, Holo Wall, Reactor Core, Energy Barrier,
  Antenna Tower, used by 5 new room kits and mixed into corridor/row layouts. Existing walls
  and racks take on each theme's tone and trim colour.
- BUFFS bar: blue-green outline with a "BUFFS:" label.
### Changed
- Upgrade icons are smaller (24dp) and sit under the HP bar instead of over the joystick area;
  the arena, banners and the boss bar move down to make room. Hold an icon: details pop up below.
- Firebase libraries added for the upcoming accounts / friends / co-op (inactive until
  app/google-services.json is added; the game builds and plays without it).

## 0.7.0 (versionCode 8)
### Added
- SAVE & EXIT from the pause menu; the main menu's PLAY becomes CONTINUE (level, room,
  every threat's position and HP, player HP/Firewall and the whole build are restored).
  Autosaves when the app is backgrounded. Saving is locked during boss fights
  ("[BOSS] SAVE BLOCKED") until the boss is defeated. A save is consumed on CONTINUE and
  cleared when a run ends.
- Difficulty picker (EASY / MEDIUM / HARD) for every new run: threat HP and damage,
  € / score payout (x0.8 / x1 / x1.5) and mod luck.
- Mod tiers: BLUE RARE, then PURPLE, GOLDEN and the new TITANIUM, each rarer than the last.
  Luck from depth, difficulty and boss rewards compounds per tier. GOLDEN/TITANIUM cards shimmer.
- More rewards: bosses pay 2 + level/20 picks (plus the level clear) with extra luck; deeper
  levels roll bonus picks. The upgrade screen shows "REWARD 2 OF 4 · 2 more to pick".
- New weapons: LOGIC BOMBS (mines while moving), MALWARE MISSILES (homing, splash),
  ARC DISCHARGE (purple chain lightning), QUANTUM RAILGUN (golden, pierces walls),
  ORBITAL STRIKE (titanium). New titanium/golden mods: TITANIUM CHASSIS, OMEGA OVERCLOCK,
  GOLDEN PROTOCOL.
- 338 new enemy types (data/EnemyVariants.kt): 26 strains (crawler, leech, brute, husk, grub,
  golem, mite, gnat, nanite, locust, spitter, gunner, scatterer, mortar, repeater, ram, lancer,
  bull, marksman, stalker, beacon, spire, sentry, blinker, phantom, glitch) × 13 code families
  (Polymorphic, Encrypted, Botnet, Ransom, Zero-Day, Adware, Spyware, Cryptojack, Forkbomb,
  Keylogger, Backdoor, Stealth, Kernel-Mode). Each has its own name, tag, colour, body accent,
  stats, behaviour twist (packs, splitting, armour, extra shots…) and unlock level, so the roster
  keeps widening through the run. New shapes: pentagon, octagon, star.
- *GLITCHED* enemies (from L25/L32/L42): Daemon, Shard, Hydra. Colour and shape cycle constantly
  with an RGB-split flicker; every attack rolls a different pattern (aimed burst, spread, ring,
  or blink + spread). Sentries and other aimed turrets now track the player.
- *GLITCHED* bosses: 12% of bosses from L20 on. +35% HP, extra random volleys, colour/shape
  flicker, "*GLITCHED*" in the boss bar, ×1.5 €/score and one extra reward pick.
- Arsenal (data/Weapons.kt): 30 new auto-weapons, five per rarity, built on ten behaviours
  (volley, ring, spiral, nova, laser, arc, strike, mines, field, boomerang).
  COMMON: Ping Blaster, Spam Shotgun, Bit Spinner, Static Shock, Cable Whip.
  UNCOMMON: Heartbeat Pulse, Captcha Mines, Traceroute, Packet Fire, Boomerang Byte.
  BLUE: Bluescreen Nova, Subnet Laser, Cluster Bomb, Data Spiral, Tesla Relay.
  PURPLE: Helix Cannon, Malware Swamp, Seeker Swarm, Shockwave, Satellite Lance.
  GOLDEN: Supernova, Prism Laser, Golden Boomerang, Chain Storm, Carpet Bomb.
  TITANIUM: Black Ice, Root Access, Titan Railstorm, Doomsday Daemon, Kernel Panic.
- Upgrades level to 10,000: past a card's designed max (which still unlocks evolutions) every
  pick adds a MASTERY level (weapon +6% damage, orbit +8% node/blade damage, defense +6% HP &
  Firewall, stat +5% damage +1.5% attack speed, utility +5% € and data). Arsenal weapons keep
  scaling damage every level; cooldown and projectile counts level off so the screen stays readable.
- Threat visibility: a faint red ring under every enemy, and enemies hidden behind racks or
  crates show as a pulsing outline on top (x-ray).
- Owned-upgrade icon bar along the bottom of the screen; press and hold an icon for details.
### Changed
- Base movement speed +15% (240 → 276).
- Slow mix: each normal round (not boss or event levels) has a 20% chance to play its music
  at 0.75x instead of 1.25x. Endless rolls per stage.
- PLASMA BEAM is now TITANIUM (ultra rare) and overheats after 5 s of hits into a 3 s
  cooldown, shown on its icon and above the bar.
### Fixed
- Smarter enemy pathing: a flow field toward the player (20-unit grid, Dijkstra, wall-
  avoiding costs, no corner cutting) steers enemies around cover instead of into it; they
  aim at the farthest point down the path they can still see. Stuck sidesteps now pick the
  side that is closer by path. Test: 144/144 chasers placed behind cover in 24 generated
  rooms reach the player within budget, using ~52% of it (93% / 66% before).
- Auto-aim only picks targets its shots can actually reach (no endless volleys into a
  corner of cover).
- Music tempo now reliably returns from boss speed (1.5x) once the boss falls; the rate is
  re-checked until the player confirms it.
### Changed
- Launcher icon is now the store icon art (hooded operative in a neon hexagon) instead of
  the old CyOps-TD-style PCB shield: adaptive icon (art inset to the safe zone on its own
  background colour, feathered edge), themed monochrome layer, and legacy square/round
  PNGs for Android 7. Master art in `docs/brand/app_icon_master.png` (+ 512 px copy).

## 0.6.0 — 2026-10-08
### Added
- Visual pass to match the key art (no false advertising): bevelled metal floor panels,
  glowing cyan trenches with running packets, grates, lit service panels, bolts, light
  pools under hardware, drifting data pixels; taller server racks with dense glowing LED
  bays and neon edge trim; X-braced hardware crates; glowing projectile trails (red-orange
  hostile streaks); neon CYBER OPERATIVE title with the "ENDLESS CYBER ROGUELIKE ACTION" line.
- PLASMA BEAM (Rare weapon upgrade, 3 levels): an arm cannon that pours a continuous,
  piercing cyan beam into the target while the operative stands still; stopped by cover.
- CYBER OPERATIVE wordmark (`ui/common/CyberTitleLogo.kt`): vector letterforms with
  gradient faces, scan cuts, extruded depth, neon halo and a circuit-trace frame; scales to
  any size and pulses on the main menu. PNG exports in `docs/brand/` (3200x1344, transparent
  and on dark) via `LogoRender` (`-PrenderPreviews`).
- Main-menu emblem (`ui/common/OperativeEmblem.kt`) after the app icon: hooded operative
  with headset and antenna in a neon hexagon with circuit traces; the face shield is the
  equipped skin. Replaces the bare shield above the title. Export: `docs/brand/menu_emblem.png`.

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
