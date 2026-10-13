# Cyber Operative for Steam (PC + Steam Deck)

The `desktop` module is the Steam build. It compiles the same game code as the Android app
(`app/src/main/java`) with Compose Desktop. Only the platform seam (`platform/`, audio, saves,
co-op) has desktop versions under `desktop/src/main/kotlin`. Android is unaffected.

## Build and run (Windows, from the repo root)

```
.\gradlew :desktop:run                          # play it straight from Gradle
.\gradlew :desktop:createReleaseDistributable   # the folder you upload to Steam
```

- The app folder lands in `desktop\build\compose\binaries\main-release\app\CyberOperative\`
  (`CyberOperative.exe` plus its own Java runtime, about 200 MB, with no install needed).
- Optional installer: `.\gradlew :desktop:packageReleaseMsi`.
- Any JDK 17+ builds it; Android Studio's bundled one is fine.
- To test with Steam from Gradle or the folder, put a `steam_appid.txt` with the App ID
  (`480` until the real one exists) next to where you launch it, and keep Steam running.
- Developer screenshots: `.\gradlew :desktop:shots` writes 1080p and Steam Deck PNGs to
  `desktop\build\shots\`.

## Debug test build (before uploading to Steam)

```
.\gradlew :desktop:run -PdebugBuild                  # play a debug build straight away
.\gradlew :desktop:createDistributable -PdebugBuild  # a debug app folder you can copy/zip
```

- The debug folder lands in `desktop\build\compose\binaries\main\app\CyberOperative\`. Run
  `CyberOperative.exe` there. It is a separate folder from the release `main-release`.
- The window title shows `[DEBUG <version>]`.
- With Steam **closed**, the STORE's ◇ packs grant free test ◇, so skins, revives and backgrounds
  can be tested. With Steam running, the real Steam checkout is used instead.
- It keeps its own save in `%APPDATA%\CyberOperative-Debug`, so test ◇ and progress never touch
  your real save. Delete that folder to start fresh.
- To test Steam features (overlay, co-op invites), put `steam_appid.txt` containing `480` next to
  `CyberOperative.exe` (or in the repo root for `run`), and keep Steam open.
- `createReleaseDistributable` refuses `-PdebugBuild`, so a debug build can't end up on Steam.

## Steam Deck

The Windows build runs on Steam Deck through Proton. In Steamworks, set the Steam Deck
compatibility to use Proton. Controls on Deck and with any controller:

| | |
|---|---|
| Left stick / D-pad | move |
| Right stick (or right trigger) | aim and fire in that direction, while moving |
| A | dash |
| BACK / VIEW | world map |
| X / Y | class spell R / F |
| LB / RB | HP charge / Packet Bomb |
| START / MENU | pause (the same as Esc) |
| B | back out of a menu |
| any button | continue the first-run briefing |

The touchscreen and trackpads work as the mouse in menus.

## Keyboard and mouse

- **WASD** or arrow keys move.
- **Hold a mouse button** to fire at the cursor while moving. Standing still auto-targets, as
  on phones.
- **M** opens the world map (campaign).
- **I** opens the INVENTORY (gear, backpack, Data Vault; also the button at the top left).
- **Space** dashes; **R** / **F** cast your class's two spells.
- **Q** drinks an HP charge (start with 3); **E** sets off a Packet Bomb (found as rare drops).
- **Esc** pauses or goes back; **F11** toggles fullscreen.
- Every key can be rebound in Settings → KEYBINDS.

## Landscape view

- PC rooms are 2160 units across (three phone rooms) and about one screen tall.
- They are drawn zoomed out, so a whole room fits a 16:9 screen.
- PC rooms hold 1.6× the threats (`ArenaShape.WIDE_CROWD`).
- The HUD is a strip above the arena (HP, boss bar, buffs) and never covers the play area.

## Steam store: ◇ packs (Steam Inventory)

All purchases go through Steam. Each ◇ pack is a Steam Inventory item; definitions are in
`steam/itemdefs.json`, and the ids must match `SteamStore.itemDefs`.

1. In Steamworks, open your App → **Inventory Service** and enable it.
2. Upload `steam/itemdefs.json`, after setting `appid` to your App ID and checking the price
   categories.
3. Publish.

**Flow:**
1. BUY opens the Steam overlay checkout.
2. Once Steam grants the item, the game consumes it and credits the ◇.
3. A pack bought while the game was closed is claimed at the next start.

## Co-op by IP address (no Steam needed)

When Steam isn't running, CO-OP shows **DIRECT IP CO-OP**:

- **Host:** press **HOST**. The screen shows your IP address(es) and the port (TCP **47615**). Tell your friend the address.
- **Friend:** type that address in **JOIN A GAME** and press **JOIN**. You're both in the normal lobby; the host picks the difficulty and starts.
- **Same network (LAN):** works as is. Use the `192.168…` / `10.…` address.
- **Over the internet**, do one of these:
  - The host forwards TCP port 47615 on their router to their PC. The friend then joins with the host's public IP (whatismyip.com).
  - Easier: you both install a virtual LAN such as Tailscale, ZeroTier or Radmin VPN, and join with the host's address on it.
- **Windows Firewall:** the first time the host presses HOST, Windows asks whether to allow the game on networks. Allow it.
- **Versions:** both players need the same game version.

**Sending the game to a friend:**
1. Build it with `.\gradlew :desktop:createReleaseDistributable`.
2. Zip the folder `desktop\build\compose\binaries\main-release\app\CyberOperative\` and send the zip.
3. They unzip it and run `CyberOperative.exe`. Nothing else needs to be installed, because the Java runtime is inside the folder.

## Co-op over Steam

- Your Steam name is your callsign, and your Steam friends are the friends list.
- INVITE creates a friends-only Steam lobby and invites them. Invites also arrive through
  Steam itself: the overlay (Shift+Tab) and "Join game".
- Game traffic is peer to peer over Steam's relay, so no server is needed.
- Phone co-op (Firebase) and PC co-op are separate: PC players play with PC players.

## Uploading (SteamPipe)

Point a depot at `desktop\build\compose\binaries\main-release\app\CyberOperative\`. The launch
option is `CyberOperative.exe`.

## Building Android vs Steam (they never mix)

The two builds are separate Gradle modules with separate commands and outputs.

| | Command | Output |
|---|---|---|
| Google Play | `.\gradlew bundleRelease` (or `:app:bundleRelease`) | `app\build\outputs\bundle\release\app-release.aab` |
| Steam | `.\gradlew :desktop:createReleaseDistributable` | `desktop\build\compose\binaries\main-release\app\CyberOperative\` |

**Why Steam code can't end up in the Android build:**
- `desktop/` (Steamworks, Steam store, controllers, cursor, PC audio and saves) is never compiled
  into `app`.
- The `app` module has no Steam libraries.
- The shared game code holds a few PC-only branches. They are switched on only by the desktop
  launcher (`ArenaShape.wide`) or by the desktop platform layer (`AppInfo.desktop`,
  `GameKeys.active`). The Android versions of those are always off.
- So phones keep portrait rooms, no cash bags, no Q/E/R/F, and Google Play wording.
