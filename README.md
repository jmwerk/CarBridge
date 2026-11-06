# CarBridge

An original GPL-3.0 Android app that makes a car copy of an installed app so Android Auto lists
it and runs its full interface on the car display while parked. Patching, signing and installing
all happen on the phone. The car-visibility entries come from AABrowser (GPL-3.0); nothing here
comes from AAAnything.

> **Status (2026-11-06):** verified on a Galaxy S24 Ultra (Android 17, Android Auto 17.9) with
> VLC 3.7.1, Firefox 159.0.1 (3 split APKs), GitHub 1.282.0, Offline Games (Unity, 2 split APKs)
> and Morphe YouTube 21.03.36.

## Install

Get it with [Obtainium](https://github.com/ImranR98/Obtainium), which installs and updates
CarBridge from this repo's GitHub releases:
**[Add to Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/jmwerk/CarBridge)**.
Or download the APK from [Releases](https://github.com/jmwerk/CarBridge/releases).

Release APKs are signed with certificate SHA-256
`a4:68:93:60:a9:1a:49:b1:6f:a7:b1:50:dc:fc:6e:2b:89:21:1a:cd:b9:63:b5:5f:1b:15:70:4a:da:43:cc:7f`.

## Requirements

- Android 15+ phone with USB debugging on
- Android Auto developer mode: Android Auto settings → tap *Version* 10× → ⋮ → Developer settings
  → enable **Unknown sources** (CarBridge's setup card walks through this)
- A head unit, or the Desktop Head Unit (DHU) at
  `~/Library/Android/sdk/extras/google/auto/desktop-head-unit`

## Build and install

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :patcher:installDebug
```

The release build (`:patcher:assembleRelease`) runs R8 and is about 5 MB. apksig's ASN.1 models
need the keep rules in `patcher/proguard-rules.pro`, or v1 signing fails.

## Use

Open CarBridge, tap **Add** next to an app, and confirm Android's install prompt. The copy installs
as `<package>.car` next to the original, named "<App> (Car)" with a bridge badge on its icon. Then
reconnect Android Auto. When the original updates, its copy gets an **UPDATE READY** tab.

| Step | Code | Notes |
|------|------|-------|
| Edit the binary manifest and resources | `patch/ManifestPatcher.kt` | ARSCLib 1.4.0 (Apache-2.0) |
| Label and icon | `patch/IconBadger.kt` | Draws the original icon plus the badge on the phone |
| Sign | `patch/KeystoreSigner.kt` | apksig 9.5.1 (Apache-2.0); RSA-3072 key in the Android Keystore, 16 KB native-lib alignment |
| Install | `Installer.kt` | `PackageInstaller` session; the user confirms each install |

A JVM check of the patch against a real APK, or a directory of `base.apk` + `split_*.apk`,
verified afterwards with `aapt2`. `-PpatchHeap=256m` mirrors a phone's heap cap:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :patcher:testDebugUnitTest -PpatchFixture=/path/app.apk
```

## Run with the DHU

On the phone: Android Auto developer settings → ⋮ → **Start head unit server**. Then:

```bash
~/Library/Android/sdk/platform-tools/adb forward tcp:5277 tcp:5277
```

```bash
~/Library/Android/sdk/extras/google/auto/desktop-head-unit
```

The DHU session drops when a car app is installed. Restart the DHU afterwards.

## How it works

The original spike (removed; its code is in commit 329fa49) tested two approaches.

**Launching unmodified apps onto the car display doesn't work.** Each car app gets its own
virtual-device display (`NativeDisplay:ComponentInfo{…}`). The system's
`GenericWindowPolicyController` rejects any other app's activity on it: `Activity launch
disallowed by policy` (result code 102), whatever the caller's permissions or target display id.

**Patching a copy works.** Android Auto shows an ordinary activity when the app has:

- `com.google.android.gms.car.application` meta-data pointing to an automotive app descriptor
- `CAR_LAUNCHER`, `androidx.car.app.category.NAVIGATION` and `android.intent.category.APP_MAPS`
  on the launcher activity
- the `androidx.car.app.ACCESS_SURFACE` and `MAP_TEMPLATES` permissions

Any activity of the patched package may then run on its car display, not just the launcher one.

### Lessons

- Apps with a `MediaBrowserService` (or `CarAppService`) open in Android Auto's template UI
  instead. The patcher strips those intent filters from the copy.
- Renaming the package means rewriting provider authorities (including `@string/` ones), declared
  permissions and `android:sharedUserId`, or the install fails with a conflict.
- The resource table's package name must be renamed too: Unity apps call
  `getIdentifier(name, type, getPackageName())` and crash on launch otherwise.
- Authorities inside `<queries>` are lookups, not declarations, and must not be renamed.
- Split APKs must carry the renamed package too; all parts are signed with the same key and
  installed in one `PackageInstaller` session.
- Apps with `minSdk < 18` get a v1 signature using SHA1withRSA, so the Keystore key must allow
  SHA-1. Keystore keys can't be changed after creation.
- ARSCLib keeps an APK's tables in memory; YouTube needs over 100 MB, so CarBridge uses
  `largeHeap`.
- The newest AndroidX libraries need AGP 9.1 and compileSdk 37; versions are pinned for AGP 8.13.

### Known gaps

- Apps protected by Play Integrity, signature checks or license verification will refuse to run
  after re-signing. Nothing can be done about that from the outside.
- Authorities hardcoded in an app's code still point at the original package. Measured on
  2026-11-06: Firefox 159 has none (it builds them from the runtime package name); VLC 3.7.1 has
  one (`org.videolan.vlc.thumbprovider`), so thumbnails in the copy may come from, or miss, the
  original. Fixing this means rewriting dex strings, and ARSCLib's dex module calls itself
  experimental, so it's deferred until a real app needs it.
- Activity aliases keep the original package prefix, so apps that build alias names from
  `getPackageName()` can't switch them. Morphe YouTube shows a harmless
  `CustomBrandingPatch: setBranding failure` toast because of this.
- The signing key dies with CarBridge's data. After an uninstall or a data clear, existing car
  copies can't be updated in place.

## Release signing

The release key is `~/.android/carbridge-release.jks` (RSA-4096, alias `carbridge`, valid to 2056,
certificate SHA-256 `a4:68:93:60:a9:1a:49:b1:6f:a7:b1:50:dc:fc:6e:2b:89:21:1a:cd:b9:63:b5:5f:1b:15:70:4a:da:43:cc:7f`).
Its password is in `~/.gradle/gradle.properties` as `CARBRIDGE_*`; neither is in the repo.
Without them, `assembleRelease` falls back to the debug key.

Publish a release with `scripts/release.sh` after bumping `versionCode` and `versionName`. It
refuses to upload an APK that isn't signed with the release key.

**Back up both files somewhere safe.** If the key is lost, CarBridge can't be updated in place.
Users would have to uninstall it, which also deletes the on-device key it uses for car copies.

Never redistribute patched APKs, and only patch apps whose license allows modification.

## License

GPL-3.0. See [LICENSE](LICENSE).
