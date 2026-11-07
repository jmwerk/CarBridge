# Compatibility

Apps confirmed on a Galaxy S24 Ultra (Android 17, Android Auto 17.9) with the Desktop Head Unit,
November 2026. Other versions and phones may behave differently. To add a result, open an issue
or a pull request with the app, its version, what you used and what happened.

## CarBridge app

| App | Version | Result | Notes |
|---|---|---|---|
| VLC | 3.7.1 | Works | Thumbnails may come from the original app (hard-coded provider) |
| Firefox | 159.0.1 | Works | 3 split APKs |
| GitHub | 1.282.0 | Works | |
| Offline Games | 3.15.3 | Works | Unity, 2 split APKs |
| 2 Player Games | 7.20.4 | Works | Unity; fills the car screen, or keeps portrait from the ⋮ menu |
| Podcast Addict | current | Works | With "Use Android Auto's audio player", Android Auto shows its player |
| YouTube (Morphe) | 21.03.36 | Works, with caveats | Crashed on the first launches, then stable; shows a harmless branding error. Use CarBridge Patches instead |

## CarBridge Patches (Morphe)

| App | Version | Options | Result |
|---|---|---|---|
| YouTube | 21.03.36 | Defaults | Full app |
| YouTube | 21.03.36 | Keep media services | Full app |
| YouTube Music | 9.45.51 | Defaults | Full app |
| YouTube Music | 9.45.51 | Keep media services | Android Auto's audio player |

## Expected not to work

Apps with Google Play's copy protection (`com.pairip.licensecheck`) usually close as soon as a
re-signed build starts; CarBridge warns before copying them. The same goes for apps that check
Play Integrity or their own signature. None of these has been tested yet.
