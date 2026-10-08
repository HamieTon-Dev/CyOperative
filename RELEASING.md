# Releasing Cyber Operative to testers (Google Play)

You need: Android Studio **or** a JDK 17+ and the Android SDK, a Google Play Developer
account (one-time $25), and this repository.

## 0. Package name (final)
`com.cyberoperative.game` — confirmed by the owner. It can never change once uploaded.

## 1. Create an upload key (once, keep it safe forever)
```bash
keytool -genkeypair -v -keystore ~/keys/cyberoperative-upload.jks \
  -alias cyberoperative-upload -keyalg RSA -keysize 4096 -validity 10000
```
Back up the .jks file and both passwords somewhere safe (password manager + offline copy).
Never commit it. With Play App Signing, Google holds the real app-signing key; this is only
your upload key (it can be reset through Play support if lost, but that takes days).

## 2. Tell the build where the key is
Copy `secrets.properties.example` to `secrets.properties` (git-ignored) at the repo root:
```
cyberop.keystore.path=/Users/you/keys/cyberoperative-upload.jks
cyberop.keystore.password=...
cyberop.key.alias=cyberoperative-upload
cyberop.key.password=...
```
(Or put the same keys in `~/.gradle/gradle.properties`, or as env vars
`CYBEROP_KEYSTORE_PATH`, `CYBEROP_KEYSTORE_PASSWORD`, …)

## 3. Build the signed .aab
Bump `versionCode` (+1 every upload) and `versionName` in `app/build.gradle.kts`, then:
```bash
./gradlew :app:bundleRelease
```
Output: `app/build/outputs/bundle/release/app-release.aab`.
Android Studio alternative: **Build → Generate Signed App Bundle / APK → Android App Bundle**,
choose the .jks, release variant.

## 4. Create the app in Play Console (first time only)
1. https://play.google.com/console → **Create app** → name "Cyber Operative", Game, Free.
2. Accept declarations. Complete **App content** (Dashboard → "Set up your app"):
   privacy policy URL, ads (No, for now), content rating questionnaire, target audience,
   data safety (the game stores progress on-device only; no data collected yet).
3. **Play App Signing** is on by default for new apps — keep it.

## 5. Push to testers
**Fastest: Internal testing** (up to 100 testers, available in minutes, no review wait):
1. Testing → **Internal testing** → **Testers** tab → create an email list → add testers'
   Google account emails → Save.
2. **Releases** tab → **Create new release** → upload `app-release.aab` → release name
   (e.g. `0.5.0`) → release notes → **Next** → **Save and publish**.
3. Copy the **opt-in link** from the Testers tab and send it to testers. They open it, tap
   *Become a tester*, then install from the Play Store link on that page.

**Closed testing** (needed before production for personal developer accounts created after
Nov 2023: at least **12 testers opted in for 14 consecutive days**): Testing → Closed
testing → create track → same steps. Start this early.

## 6. Next updates
Increase `versionCode`, rebuild (`bundleRelease`), and create a new release on the same track.

## Notes
- The bundle is ~120 MB (full original CyOps TD soundtrack); Play's limit is 200 MB per
  download, so this is fine. Size reduction is backlogged.
- Google Play Billing (◇ packs) is not connected yet; in release builds the ◇ buttons say so.
