# Cyber Operative Development Backlog

## CURRENT MILESTONE
**M2 — First Boss & Content Systems (0.2.x)**: vertical slice is done; validate feel on
device, tune balance, then fill out the store/meta phases.

## IN PROGRESS
- CO-060 On-device playtest & balance pass (needs a physical device / emulator run)

## NEXT
- CO-061 Hit-stop / screen shake + richer death & hit effects
- CO-062 Boss intro card (name, title, dossier) before the fight
- CO-063 Per-boss signature visuals (unique silhouettes instead of hexagon)
- CO-064 Run-upgrade icon art (vector icons replacing ASCII glyphs where helpful)
- CO-065 Pause menu: show current build (owned upgrades) and settings shortcut
- CO-066 Short interactive tutorial steps on level 1 (beyond the briefing card)

## PLANNED
- CO-070 Google Play Billing for ◇ (test SKUs only until production IDs are supplied)
- CO-071 Store screen: ◇ packs, skins, operatives, themes, revives, level-up packs, offers
- CO-072 Skins (cosmetic: operative tint, orb/projectile colours)
- CO-073 Themes (menu/arena palettes that never reduce readability)
- CO-074 Additional operatives with passives
- CO-075 ◇ revive / rewarded-ad revive (after monetization decision)
- CO-076 Google Play Games Services: leaderboards (level, score, kills, bosses, longest run)
- CO-077 Play Games achievements mirroring local achievements
- CO-078 Cloud save
- CO-079 Statistics screen (per-run history)
- CO-080 More arena templates + environmental hazards (lasers, moving packets)
- CO-081 More enemy types (Ransomware Elite, Data Thief, Firewall Breaker, Exploit Drone)
- CO-082 Bosses 13–20
- CO-083 Daily operations / missions / seasonal events (data-driven events exist)
- CO-084 Codex screen with unlock tracking
- CO-085 Accessibility: colour-blind palette option, reduced effects, text size
- CO-086 Performance profiling on low-end devices (pool sizes, particle caps)
- CO-087 Localisation framework
- CO-088 Release config: signing via secrets.properties, R8 rules verified, Play listing

## NEEDS TESTING
- CO-007 Stop-to-fire feel on real touchscreens (deadzone 0.12, delay 0.04 s)
- CO-030 Boss pattern fairness at high loops (L130+)
- CO-040 Event frequency (14%) and rewards
- CO-050 € pacing vs permanent upgrade costs

## BLOCKED
- CO-089 Production Play Console IDs (billing, games, ads) — waiting on owner

## COMPLETED
- CO-001 Project bootstrap (Gradle, Kotlin/Compose, portrait-only manifest)
- CO-002 GitHub repository (HamieTon-Dev/CyOperative)
- CO-003 Startup HamieTon.dev sequence
- CO-004 Initialization terminal animation
- CO-005 Main menu with living background
- CO-006 Player movement (floating joystick)
- CO-007 Stop-to-fire system with LOS-aware targeting
- CO-008 Enemy base architecture (data-driven, 7 AI kinds)
- CO-009 Rotating Packet Node orbs
- CO-010 Collision obstacles & line of sight
- CO-011 Arena templates (12 + mirrors) and selection
- CO-012 Level loop: waves, clear, access port, transition
- CO-013 Endless difficulty scaling
- CO-014 Three-choice upgrade screen + rarity + evolution
- CO-015 Weapons: cone, lance, blades, EMP, chain, split, multishot, bounce, pierce
- CO-016 Firewall system + evolution chain
- CO-017 Elite modifiers
- CO-018 HUD (HP/firewall/data/score/€/boss bar/event label)
- CO-019 Damage numbers
- CO-020 Game over + free limited revive + restart
- CO-021 Boss architecture, 12 bosses × 3 phases, boss every 10 levels, boss music/reward
- CO-022 Event architecture + 5 events + spectrum border
- CO-023 € economy, permanent upgrades, Operative XP/level
- CO-024 Save system (versioned JSON, corrupt-save quarantine, auto-backup)
- CO-025 Achievements (16) + screen
- CO-026 Local leaderboard/records, settings, about, armory codex
- CO-027 Synthesized SFX + music states
- CO-028 Score formula
- CO-029 Tutorial briefing card
- CO-031 Unit tests + automated bot playthroughs + screenshot previews

## FUTURE IDEAS
- Mythic / ZERO-DAY rarity tier
- Active ability (single button) — only if it fits the one-thumb pillar
- Weekly modifier runs, community events
- Boss rush mode

## USER DECISIONS REQUIRED
1. **Final package / application ID** (currently provisional `com.cyberoperative.game`).
   Cannot change after first Play upload.
2. **◇ pricing and pack sizes**; whether events/achievements may grant small ◇ amounts
   (currently 0).
3. **Revive policy**: keep 1 free revive per run? add ◇ revive cost and/or rewarded ads?
4. **Character art direction**: currently the existing shield logo (`>_<`) is the
   in-game operative. Commission/approve a full-body operative sprite?
5. **Ads at all?** (CyOps TD has AdMob infra; not added here yet.)
