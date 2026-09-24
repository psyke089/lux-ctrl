# Verification

Tested on 24 September 2026 with the connected OnePlus 5T (Android 13 / API 33) and the local first-generation Hue BSB001 bridge (API 1.16.0), with six lamps and twelve existing scenes.

- Debug and optimized, unsigned release APK builds pass.
- Seven unit tests pass: private-address/key validation, wheel hit testing, radial brightness limits, HSV primaries, xy conversion, and white temperature.
- Android lint reports **no issues**.
- The opt-in Android instrumentation test passes actual lamp writes, invalid-key handling, scene create/rename/overwrite/recall/delete, and global on/off. Its temporary scene is removed and lamp states restored.
- Phone UI checks cover radial dimming, slice-to-dialog navigation, color/brightness Apply, new scene creation, selected scene overwrite, long-press rename and delete confirmation, scrolling, and portrait/landscape layout.
- The README screenshots come from the running app. Test scene changes are cleaned up; original light power, brightness, color, and effect states are restored after screenshot capture.
- No third-party runtime network/UI dependencies. Endpoint validation restricts requests to a configured private IPv4 bridge, rejects redirects, and never uses cloud discovery.

API 24 and API 37 runtime behavior has not been tested on devices or emulators. API 24 compatibility is checked by the build and lint; the API 37 local-network permission path is implemented but untested on hardware. HTTPS-only modern bridges are outside this version’s supported connection mode.
