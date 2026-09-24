# Verification

Tested on 24 September 2026 with the connected OnePlus 5T (Android 13 / API 33) and the local first-generation Hue BSB001 bridge (API 1.16.0), with six lamps and twelve existing scenes.

- Debug and optimized release APK builds pass. The release build is minified and shrunk with R8, signed with the debug key, and installs and launches on the phone without any keystore setup.
- Twelve unit tests pass: private-address/key validation, wheel hit testing, radial brightness limits, HSV primaries, xy conversion, white temperature, color-disc hue/tone round-trips, and Hue v1 colour-mode selection.
- Android lint reports **no issues**.
- The opt-in Android instrumentation test passes actual lamp writes, invalid-key handling, scene create/rename/overwrite/recall/delete, and global on/off. Its temporary scene is removed and lamp states restored.
- Phone UI checks cover radial dimming, slice-to-picker navigation, the named-and-sized light field, the three-column scene grid, the Save name dialog for both overwrite and new-scene paths, the SETTINGS update-interval options, and the color-disc dialog with its close button and no Apply/Cancel buttons, in portrait and landscape.
- Lamp colour changes were exercised on hardware: a first-generation BSB001 bridge keeps serving a stale `xy` after a hue/sat write while `colormode` says `hs`, so the app now selects colour fields by `colormode`. The wheel and the dialog’s initial values update from the very next response, without waiting for the periodic refresh. The lamp used for the check was restored to its original colour.
- The lamp dialog’s debounced live-apply write path, the scene-change glide animation, and the reduced refresh flicker were verified by code inspection and a debug build but not exercised against hardware in this revision, so that lamp state, scene list, and the phone’s configured bridge were left untouched.
- The README screenshots come from the running app. Test scene changes are cleaned up; original light power, brightness, color, and effect states are restored after screenshot capture.
- No third-party runtime network/UI dependencies. Endpoint validation restricts requests to a configured private IPv4 bridge, rejects redirects, and never uses cloud discovery.

API 24 and API 37 runtime behavior has not been tested on devices or emulators. API 24 compatibility is checked by the build and lint; the API 37 local-network permission path is implemented but untested on hardware. HTTPS-only modern bridges are outside this version’s supported connection mode.
