<p align="center"><img src="docs/lux-mark.svg" alt="Lux Ctrl radial light icon" width="100"></p>
<h1 align="center">Lux / Ctrl</h1>
<p align="center">A local instrument for your Hue lights.</p>
<p align="center">
  <a href="https://github.com/psyke089/lux-ctrl/actions/workflows/ci.yml"><img src="https://github.com/psyke089/lux-ctrl/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="https://github.com/psyke089/lux-ctrl/releases"><img src="https://img.shields.io/badge/release-v0.3.0-blue?logo=github&logoColor=white" alt="Release v0.3.0"></a>
  <img src="https://img.shields.io/badge/vibe_coded-100%25-critical?logo=github&logoColor=white" alt="No human review">
  <img src="https://img.shields.io/badge/human_review-none-green?logo=github&logoColor=white" alt="Human review: none">
</p>
<p align="center"><img src="docs/screenshot-portrait.png" alt="Lux Ctrl on a phone: six segmented reactor controls above luminous Hue scene tiles" width="300"></p>

A dark Android console for Philips Hue: lamp slices around a power core, plus a scene grid. Kotlin with native views — no accounts, cloud calls, analytics, ads or third-party runtime libraries. All artwork is drawn in the project; motion follows Android's animation setting and pauses when the app loses focus.

## Controls

- **Lamps:** drag a slice outward to brighten, inward to dim; zero turns the lamp off. Tap a slice for the color disc — outer ring for hue, inner disc for saturation and brightness, white temperature on supported lamps.
- **Power:** tap the center to turn all lights off; tap again when all are off to turn them on.
- **Scenes:** tap to activate, hold to rename or delete. The header disk icon saves the current lamps; with a scene active it overwrites that scene.
- **Landscape:** scenes move left, the lamp field right.

## Setup

Install the APK and open settings: bridge IPv4 address and API key. The local Hue v1 HTTP API is used (tested on a first-generation BSB001); HTTPS-only bridges are unsupported and internet access is unnecessary. Credentials stay in app-private storage, excluded from backups and device transfers. Use a trusted local network.

## Build

JDK 17+, Android SDK 37, and the included wrapper.

```sh
./tools/verify                    # unit tests, lint, debug APK
./gradlew :app:assembleRelease    # R8-minified, signed with the release key
```

The release APK lands at `app/build/outputs/apk/release/lux-ctrl-0.3.0.apk`. Minimum Android 7.0 (API 24); Gradle 9.6.0, AGP 9.4.1.

Optional live bridge test — it briefly changes lights, creates one temporary scene, then restores the original states:

```sh
./gradlew :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e live true app.luxctrl.test/app.luxctrl.BridgeSmokeInstrumentation
```

## Download

Download: [latest release](https://github.com/psyke089/lux-ctrl/releases).

Also see the [color disc](docs/screenshot-color-disc.png), [landscape screenshot](docs/screenshot-landscape.png), [verification notes](docs/verification.md) and [SPEC.md](docs/SPEC.md).
