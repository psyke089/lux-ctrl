# Lux Ctrl — specification

Current behavior of the app as shipped in 0.3.0. Supersedes the original build brief and the
earlier review notes (both folded in here).

## Scope

A single-activity Android app that controls Philips Hue lights over the local network. Kotlin and
native Android views only: no accounts, cloud calls, analytics, ads, discovery service, or
third-party runtime libraries. Every icon and graphic is drawn in code; the only unit-test
dependency is JUnit.

Out of scope: HTTPS-only bridges, the Hue cloud API, multiple bridges, and localization (the UI ships
in English, with strings inline in code).

- Minimum Android 7.0 (API 24); compile and target API 37.
- Gradle 9.6.0, Android Gradle Plugin 9.4.1, Java 11 source/target.

## Connectivity

The bridge is reached only through its local v1 HTTP API at `http://<ip>/api/<key>/`. Internet access
is unnecessary.

- **Configuration validation:** the IP must be a private IPv4 literal (`10/8`, `172.16/12`,
  `192.168/16`, `169.254/16`); the key must be 10–128 characters of `[A-Za-z0-9_-]`. No DNS names,
  no public addresses.
- **Requests:** `GET lights`, `GET scenes`, `PUT lights/{id}/state`, `PUT groups/0/action`
  (`on` for global power, `scene` for activation), `POST scenes`, `PUT scenes/{id}`
  (`name`, `storelightstate`), `DELETE scenes/{id}`. Connect timeout 4 s, read timeout 5 s,
  redirects disabled.
- **Errors** are translated to actionable messages: rejected key, missing lamp or scene, unreachable
  lamp, busy bridge, timeout, bridge offline.
- **Concurrency:** one single-thread request queue; all published state and callbacks stay on the
  main thread. Only one request is in flight and input is disabled while it runs.
- **Refresh:** while the activity is resumed and no dialog is open, lamps refresh on the chosen
  interval — 2, 5, 10, 30 or 60 seconds, default 10. The header refresh glyph reloads lamps and
  scenes. Going offline keeps the last known state on screen and reports the error.
- **API 37** requests the local-network permission; denial shows a retry banner.

## Screen layout

Header: the `LUX//CTRL` wordmark, a bridge status line, an active-scene indicator, then refresh,
save and settings glyphs. Status reads `SET UP YOUR BRIDGE`, `LOCAL ACCESS NEEDED`,
`CONNECTING TO BRIDGE`, `BRIDGE DISCONNECTED` or `LOCAL BRIDGE CONNECTED`. The scene line shows the
active scene, appends `· EDITED` after unsaved lamp changes, or `NO SCENE`.

Portrait: lamp field on top, scene grid below. The lamp field nominally takes 57 %, but the grid is
guaranteed 204 dp so four touch-sized rows fit on short phones. Landscape: scenes left (47 %), lamp
field right (53 %). A bottom action line reports completed actions for about 4 s.

The whole surface uses insets-aware edge-to-edge padding and a procedural starfield backdrop.

## Lamp field

- Each lamp is a labelled slice of a ring around a central power core, ordered by numeric bridge id.
- **Brightness:** drag a slice outward to brighten, inward to dim; reaching zero turns the lamp off.
  The value is sent on release, and the released value stays on screen until the bridge confirms it,
  so a slice never flickers back.
- **Color:** tap a slice to open the lamp dialog. Unreachable lamps show a warning notice instead.
- **Power:** tapping the core turns all lights off, or on when all are off (`PUT groups/0/action`).
- **Animation:** an idle orbiting highlight, radial energy waves on startup and global power changes,
  and a brightness glide when a scene is activated. Direct gestures snap. Motion follows Android's
  animator duration scale and pauses when the window loses focus or visibility.

## Lamp dialog

Title with the lamp name and a close glyph top-right. No apply/cancel buttons: edits are applied
live, coalesced into one request after 350 ms of inactivity, and flushed on dismiss. Landscape
reflows the disc beside the controls.

- Color-capable lamps: glass color disc — outer ring selects hue, inner disc blends saturation and
  brightness. Writes `hue`/`sat` (Hue scales 65535/254).
- White temperature slider for `ct`-capable lamps writes `ct` in mireds within the lamp's reported
  min/max, and switches the disc into white mode.
- Dim-only lamps: brightness slider. Non-dimmable lamps: a power switch.
- A preview swatch and a monospace readout show the pending color and brightness; brightness is
  sent only above zero, and turning off sends `on:false`.

## Scenes

- Three-column grid of cards, row height fitted so up to four rows stay in the viewport. Each card
  shows a prominent rail of the scene's stored lamp colors and its name.
- Tap activates the scene. Long-press opens rename or delete; deleting asks for confirmation and
  leaves the lights untouched. Scenes locked by the bridge refuse rename, delete and overwrite.
- The header disk glyph saves: with an active scene the name field is prefilled with that scene's
  name, saving overwrites it (`storelightstate`) and renames it when the name changed; with no active
  scene the field is empty, a name is required (32 characters max), and a new scene is created from
  all lamps.
- With no scenes, the grid shows short setup copy instead.

## Settings

Dialog titled `SETTINGS` with three controls and no explanatory prose: bridge IP, API key (masked,
with a reveal checkbox) and update-interval chips. Validation errors appear inline; `Connect`
persists the config, resets state and refreshes, while `Cancel` discards. The interval is stored as
soon as a chip is chosen and restarts the poller.

## Persistence and privacy

Only the bridge IP, API key and refresh interval are stored, in the app-private
`SharedPreferences` file `bridge`. Backups and device transfers are disabled both in the manifest and
through `data_extraction_rules.xml`, so credentials never leave the device. Nothing is bundled in the
APK or committed to the repository.

## Visual system

Dark theme only, from one token set: background `#030710`, panel `#091820`, line `#264154`, ink
`#E6F6FF`, muted `#8EA4BE`, teal `#59F7EB`, violet `#A376FF`, amber `#FFAB5B`. Panels are matte with
1 dp strokes and 12–14 dp radii, buttons carry teal ripples, micro labels are monospace with letter
spacing, and the title is an italic sans-serif-black gradient.

Every interactive target is at least 44 dp with a content description. The color disc supports
keyboard and accessibility adjustments through a virtual node provider for hue, saturation and
brightness.

## Build and release

```sh
./tools/verify                    # unit tests, lint, debug APK
./gradlew :app:assembleRelease    # R8-minified, resource-shrunk, debug-signed
```

Release APKs keep the same signing identity across versions so they upgrade in place. Pushing a
`v*` tag runs CI (unit tests, lint, release build, signature and `versionName` assertions) and
publishes a GitHub release with the APK and its SHA-256; branches only run the verification gate.

`app/src/androidTest` holds an optional live-bridge smoke test: it exercises lamp writes, scene
create/rename/overwrite/recall/delete and global power, creates only a temporary scene, and restores
the original lamp states.
