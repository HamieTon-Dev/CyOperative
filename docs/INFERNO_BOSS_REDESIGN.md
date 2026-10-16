# INFERNO boss visual redesign (0.36.0)

Owner brief (2026-10-16): a major visual pass so the 15 in-game INFERNO bosses match the Inferno Boss Pack
concept art. Names, mechanics, attacks, roles, placement, stats and IDs are unchanged.

## Approach

The owner supplied three in-game reference sheets (Pack Alpha / Beta / Gamma). Each boss is painted in its
three states (phase 1 / 2 / 3) on black, in the same layout as our approval renders. Rather than re-draw
those paintings as vector shapes (the 0.35.x bodies looked like icons), the sheets **are** the bodies now:

- `resources/bosses/inferno_<pack>.webp` are 3 × 5 atlases of 320 px cells, cut out of the sheets with
  alpha keying. The background is flooded from the cell edges, and the glow is un-blended from black so the
  bloom stays soft. The art is bottom-aligned. Slicer: `boss_slice.py` (session scratchpad), re-runnable
  if the sheets change.
- `ui/game/InfernoBodies.kt` gives every boss an **InfernoRig** that animates the paintings:
  - **Phase frames:** the painting follows the HP phase (1 → 2 → 3, escalating armour, rings and fire).
  - **Wind-up:**
    - most bosses cross-fade toward the next painting;
    - jaw bosses (Furnace Maw, Searfang) switch to the open-maw / breath painting;
    - every reactor core goes white-hot.
  - **Motion:**
    - HOVER: bob.
    - DRIFT: bob and sway.
    - HEAVY: settles, and compresses before a blow.
    - STOMP: footfalls, and compresses before a blow.
  - **Split halves** (the parts outside the body strip move; the body strip stays put):
    - FLAP: wings beat, then open wide on wind-up.
    - SWAY: legs, scythes, chains and crystals counter-sway, doubling on wind-up.
    - SPREAD: pylons step outward, further on wind-up. The lava pool stays on the floor.
  - **Overlays:**
    - an additive reactor-core pulse;
    - counter-rotating ember rings for the rotating-machinery bosses;
    - drifting embers;
    - a white hit flash (tint over the painting).
  - **Facing:** Searfang (side-on beast) mirrors to face the operative.
- The hitbox centre sits on each painting's reactor core (checked with the cyan rings in
  `docs/screenshots/inferno_pack_*.png`). Attacks fire from the boss centre as before, so they come out
  of the core / maw.
- Colours: every boss is back on the inferno palette (black and charcoal metal; red, crimson and orange;
  molten yellow; white-hot cores; magenta-red only for Volcanic Null). No green, blue or cyan.

## Audit

| Boss | Current visual problem (0.35.x) | Redesign approach | New components | Animation changes |
|---|---|---|---|---|
| Furnace Maw | Wrong visual language; green; a disc | Owner painting: armoured furnace mouth, tooth rings, chimneys | Painted body ×3, core glow, embers | Wind-up → open maw / breath painting; core white-hot; heavy settle |
| Searfang Colossus | Read as a furnace building | Owner painting: side-on quadruped with lava jaws and back stacks | Painted body ×3, mirroring | Stomp gait; wind-up → breath painting; faces the player |
| Combustion Monarch | Flat glowing icon | Owner painting: winged moth-queen reactor | Left / right wing halves, body strip, core, embers | Wings beat; open fully on wind-up; core white-hot |
| Burnwire Bishop | Generic crystal | Owner painting: mitred bishop, halo, chained lantern pylons | Chain / pylon halves, body strip | Chains / pylons sway; wind-up cross-fade + core |
| Volcanic Null | Rectangular building | Owner painting: obsidian monolith, null core, suppression pylons | Pylon halves (spread), core | Pylons step out; halo phase frames |
| Ember Warden | Castle icon | Owner painting: bunker with shield pylons and furnace eye | Pylon halves (spread), core | Pylons deploy outward on wind-up; compresses before slams |
| Magma Pulse | Grid sphere icon | Owner painting: banded reactor sphere with floating plates | Two counter-rotating ember rings, core | Drift; armour opens through the phase frames |
| Redline Sovereign | Simple throne | Owner painting: crowned machine king with royal pylons | Pylon halves (spread), core | Heavy settle / compress; crown halo phases |
| Pyroclast Oracle | Generic geometric | Owner painting: hooded mask-prophet, forehead eye, crystals, halo rings | Crystal halves (sway), ember ring, eye core | Crystals sway; eye blazes on wind-up |
| Infernal Router | UFO | Owner painting: router fortress with eye core, antenna towers, port rings | Two counter-rotating port rings, core | Hover; rings spin; phase 3 shows the storm |
| Ashen Reaver | Flat | Owner painting: hooded reaper, ash cloak, twin scythes | Scythe halves (sway), eye core | Scythes swing harder on wind-up; hover |
| Smolder Hex | Wrong palette (green) | Owner painting: hex reactor with six turret nodes | Ember ring, core | Drift; turrets fire in the phase-3 painting |
| Hellcore Tyrant | Too abstract | Owner painting: floating command engine, rune pylons, reactor rings | Pylon halves (spread), two rings, core | Pylons step out; rings orbit; core pulse |
| Scorch Halo | Too simple | Owner painting: halo wheel, slit eye, blade segments | Two counter-rotating rings, eye core | Drift; blades flare in the phase-3 painting |
| Cinder Widow | OK, but flat | Owner painting: mechanical spider with molten abdomen | Leg halves (sway), small core | Legs sway, doubling on wind-up; stomp gait |

## Not yet (follow-ups)

- **Per-attack paintings.** Attack-specific poses (e.g. Monarch Dive with folded wings, Silence Field
  dimming) need either more painted frames from the owner or procedural overlays per pattern. The rig
  currently has one wind-up state per boss.
- **Death.** Bosses use the shared boss death burst. A per-boss break-apart would need cut-out part layers.
- **Fully separate parts** (individual legs, chain links, turret heads that aim). These would need the
  owner's art split into layers (or a parts sheet); the split-halves rig is the readable approximation.
