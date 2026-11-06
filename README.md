# aa-launcher-spike

> **Phase 1 status (2026-11-06):** the `patcher` module (**CarBridge**, working name) does the
> whole patch → sign → install flow on the phone. VLC 3.7.1 (single APK) and Firefox 159.0.1
> (base + 2 split APKs) patched by CarBridge run their full UI in Android Auto. See [CarBridge](#carbridge-phase-1) below.

Throwaway spike for an original GPL-3.0 tool that shows ordinary phone apps on Android Auto.
It answers two questions before any real code gets written:

- **A. Launcher (no patching):** can a car-visible activity launch an *unmodified* app onto the
  car display?
- **B. Patcher:** does adding the car manifest entries to an APK and re-signing it make Android
  Auto list that app?

The car-visibility entries come from AABrowser (GPL-3.0); nothing here comes from AAAnything.

## Requirements

- Android 15+ phone with USB debugging on
- Android Auto developer mode: Android Auto settings → tap *Version* 10× → ⋮ → Developer settings
  → enable **Unknown sources**
- A head unit, or the Desktop Head Unit (DHU) at
  `~/Library/Android/sdk/extras/google/auto/desktop-head-unit`

## Build and install

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:installDebug
```

## Run with the DHU

On the phone: Android Auto developer settings → ⋮ → **Start head unit server**. Then:

```bash
~/Library/Android/sdk/platform-tools/adb forward tcp:5277 tcp:5277
```

```bash
~/Library/Android/sdk/extras/google/auto/desktop-head-unit
```

## Test A: launcher

1. Open **AA Spike** from the car launcher. If it isn't listed, enable Unknown sources and reconnect.
2. The first log line should show a non-default display id. That id is the car display.
3. Tap **A1 plain** and then **A2 displayId** for a few apps (VLC, a browser, Settings).
4. Record where each app appears: car screen, phone screen, or an exception in the log.

Watch the log from the Mac as well:

```bash
~/Library/Android/sdk/platform-tools/adb logcat -s AASpike ActivityTaskManager
```

**A passes** if any unmodified app renders and responds to touch on the car screen.

## Test B: patcher

Use an app whose license allows modification, such as VLC (GPL). Split APKs aren't supported yet.

```bash
scripts/patch-apk.sh vlc.apk vlc-car.apk
```

Uninstall the original first, because the signature differs and that app's data will be lost. Then:

```bash
~/Library/Android/sdk/platform-tools/adb install vlc-car.apk
```

**B passes** if the patched app shows up in the car launcher and runs. Never redistribute
patched APKs.

## Results

Tested 2026-11-06 on a Galaxy S24 Ultra (Android 17, Android Auto 17.9) with DHU 2.0.

| App | A1 plain | A2 displayId | B patched |
|-----|----------|--------------|-----------|
| Firefox | blocked | blocked | — |
| 1Password | — | blocked | — |
| 2 Player Games | blocked | — | — |
| VLC 3.7.1 | — | — | **works** (main UI, onboarding, video player) |

**Car visibility:** `CAR_LAUNCHER` + car meta-data alone isn't enough. The launcher activity
also needs `androidx.car.app.category.NAVIGATION` + `android.intent.category.APP_MAPS`, and
the `androidx.car.app.ACCESS_SURFACE` / `MAP_TEMPLATES` permissions.

**Approach A is dead.** Each car app gets its own virtual-device display
(`NativeDisplay:ComponentInfo{…}`). The system's `GenericWindowPolicyController` rejects any
other app's activity on it: `Activity launch disallowed by policy` (result code 102). The
block comes from the display's policy, not from caller permissions, so neither a plain launch
nor targeting the display id gets around it.

**Approach B works.** `scripts/patch-apk.sh vlc.apk vlc-car.apk org.videolan.vlc.car` gives an
APK that installs next to the original, appears in the car launcher, and runs its full UI.
Lessons:

- Any activity of the patched package may run on its car display, not just the launcher one.
- Apps with a `MediaBrowserService` (or `CarAppService`) open in Android Auto's template UI
  instead. The patcher strips those intent filters from the copy.
- Renaming the package means rewriting provider authorities (including `@string/` ones) and
  declared permissions, or the install fails with a conflict.
- Known gap: authorities hardcoded in the app's code still point at the original package.
- The DHU session drops when a car app is installed. Restart the DHU afterwards.

## CarBridge (Phase 1)

An on-device app that does what `scripts/patch-apk.sh` does, without a computer.

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :patcher:installDebug
```

The release build (`:patcher:assembleRelease`) runs R8 and is about 4.3 MB instead of 31 MB.
apksig's ASN.1 models need the keep rules in `patcher/proguard-rules.pro`, or v1 signing fails.

Open CarBridge, tap **Patch** next to an app, and confirm Android's install prompt. The car copy
installs as `<package>.car` next to the original. Then reconnect Android Auto.

| Step | Code | Notes |
|------|------|-------|
| Edit the binary manifest | `patch/ManifestPatcher.kt` | ARSCLib 1.4.0 (Apache-2.0); adds a car descriptor resource if the app has none |
| Sign | `patch/KeystoreSigner.kt` | apksig 9.5.1 (Apache-2.0); RSA-3072 key in the Android Keystore, 16 KB native-lib alignment |
| Install | `Installer.kt` | `PackageInstaller` session; the user confirms each install |

A JVM check of the manifest patch against a real APK, verified afterwards with `aapt2`:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :patcher:testDebugUnitTest -PpatchFixture=/path/app.apk
```

### Lessons from Phase 1

- Apps with `minSdk < 18` get a v1 signature using SHA1withRSA, so the Keystore key must allow
  SHA-1. Keystore keys can't be changed after creation.
- Authorities inside `<queries>` are lookups, not declarations, and must not be renamed.
- Split APKs must carry the renamed package too; all parts are signed with the same key and
  installed in one `PackageInstaller` session.
- `android:sharedUserId` must be renamed as well, or the copy tries to join the original's shared
  user and fails with `INSTALL_FAILED_SHARED_USER_INCOMPATIBLE`.
- The newest AndroidX libraries need AGP 9.1 and compileSdk 37; versions are pinned for AGP 8.13.

### Known gaps

- Apps protected by Play Integrity, signature checks or license verification will refuse to run
  after re-signing. Nothing can be done about that from the outside.
- Authorities hardcoded in an app's code still point at the original package. Sharing or
  file-provider features may break.
- The signing key dies with CarBridge's data. After an uninstall or a data clear, existing car
  copies can't be updated in place.
- The release build is signed with the debug key for local testing; a public release needs its
  own signing key.

## License

GPL-3.0. See [LICENSE](LICENSE).
