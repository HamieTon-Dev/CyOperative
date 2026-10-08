# Firebase setup for co-op

Co-op (accounts, friend codes, friends, invites and live two-player runs) runs on
Firebase. Until `app/google-services.json` exists, the game builds and plays
normally and the CO-OP screen says co-op isn't available. The file is
git-ignored: it stays on your PC only.

Everything below is free on Firebase's **Spark** plan to start with (see "Costs").

## 1. Create the project

1. Open <https://console.firebase.google.com> → **Create a project** → name it
   (e.g. `cyber-operative`). Google Analytics: optional (not used by the game).
2. In the project, click **Add app → Android** twice, once per package name:
   - `com.cyberoperative.game` (Play / release builds)
   - `com.cyberoperative.game.debug` (debug builds)
   You can skip the SHA-1 and the "add SDK" steps; the project is already set up.

## 2. Turn on the three services

1. **Authentication** → Get started → **Sign-in method** → enable **Anonymous**.
   (Each install gets an anonymous account; players only ever see callsigns.)
2. **Firestore Database** → Create database → **production mode** → pick a
   location near your players (e.g. `nam5` or `eur3`; it can't be changed later).
3. **Realtime Database** → Create database → same region family → **locked mode**.

Do step 3 **before** step 4: the config file only includes the Realtime
Database address if the database already exists.

## 3. Paste the security rules

The rules in this repo make sure players can only read or change their own
data, and only the two players of a room can see it.

- **Firestore → Rules**: replace everything with the contents of
  [`firebase/firestore.rules`](firebase/firestore.rules) → **Publish**.
- **Realtime Database → Rules**: replace everything with the contents of
  [`firebase/database.rules.json`](firebase/database.rules.json) → **Publish**.

## 4. Download the config into the project

**Project settings** (gear icon) → **Your apps** → any of the two Android apps →
**google-services.json** → save it as:

```
C:\Cyber Operative\app\google-services.json
```

The one file covers both package names. Then build as usual:

```
cd "C:\Cyber Operative"
git pull origin ccr-2d0f7974-yuiude
.\gradlew --stop
.\gradlew bundleRelease
```

## 5. Google Play: Data safety and privacy policy

Co-op sends data off the device, so update **Play Console → App content → Data safety**:

| Data type | Collected | Shared | Purpose | Notes |
|---|---|---|---|---|
| User IDs (anonymous account ID) | Yes | No | App functionality | Created only when the player opens CO-OP |
| Other user-generated content: callsign | Yes | No | App functionality | Shown to the player's friends |
| App interactions (co-op game state) | Yes | No | App functionality | Live only during a co-op run |

- Data is encrypted in transit: **Yes**.
- Players can request deletion: **Yes**, by contacting you. Delete their
  `users/{uid}` document, their friendships and their code in Firestore.
- Co-op is optional; solo play collects nothing.

Add a paragraph like this to the privacy policy:

> **Co-op.** If you open CO-OP, the game creates an anonymous account with
> Google Firebase and stores the callsign you choose, your friend code, your
> friends list and pending invites. During a co-op run, game positions and
> actions are relayed between the two players through Firebase. No email,
> contacts or location are collected. To delete your co-op data, contact us at
> [your address].

## Costs

Spark (free) plan limits that matter here:

- Realtime Database: 100 simultaneous connections, 10 GB downloaded per month.
- Firestore: 50,000 reads and 20,000 writes per day.

A co-op run sends about **10–15 MB per hour** for the pair (tested), so 10 GB
covers roughly 700+ hours of co-op a month. Each player online uses one
connection, so 100 connections means about 100 players in CO-OP at the same time.
If the game outgrows that, switch to the **Blaze** (pay-as-you-go) plan: the
Realtime Database costs about $1 per GB downloaded.

## Troubleshooting

- **"CO-OP UNAVAILABLE"**: `app/google-services.json` is missing, or the build
  was made before it was added. Add it and rebuild.
- **"OFFLINE · …PERMISSION_DENIED"**: the rules weren't published, or Anonymous
  sign-in isn't enabled.
- **Friends never show online**: the Realtime Database was created after the
  config file was downloaded. Download `google-services.json` again.
