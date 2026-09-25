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


## Neon console makeover — 24 September 2026

- Branch: `design/neon-reactor`. All graphics are procedural Android Canvas/shader artwork; no downloaded assets or new runtime dependencies. The header, lamp dial, three-column scene grid and dialog controls retain their existing arrangement.
- Debug and optimized release builds, all 12 unit tests and Android lint pass (`No issues found`). The optimized APK installs and runs on the OnePlus 5T / API 33.
- Screen captures and UI hierarchy bounds confirm all 12 scene buttons fit entirely inside the scene viewport without scrolling at 1080 × 2160 and a temporary 960 × 1706 screen size, in portrait and landscape. Compact rows adapt to available height; additional scenes can still scroll.
- The lamp dialog opens and closes with the new glass border and etched color-disc scale. Updated screenshots are from the running app.
- Static dial artwork is cached between state changes; gestures and scene transitions draw directly. In a roughly 24-second on-device animation sample (730 frames), median frame time was 7 ms, 95th percentile 10 ms, and 3 frames (0.41%) missed their deadline. These are device-specific measurements, not a performance guarantee.
- Decorative animation is capped near 30 fps, respects the system animator setting and is cancelled when hidden, detached or unfocused. Bitmap resources are released on detach and resize. Reduced-motion handling was reviewed in code, not toggled on hardware.
- A rotation reset caused a UI test tap to activate the existing “Konzentrieren” scene. No scenes were created, edited or deleted. Remaining dialog checks made no lamp changes. Original display size and rotation preferences were restored.

## Orbital console makeover — 25 September 2026

- Replaced the uniform grid backdrop with procedural stars, orbital chart lines and amber edge markings. Dark alloy panels, illuminated contacts and scene-colored contour ribbons carry the new visual language through the main screen and dialogs.
- The lamp dial now has segmented energy cells, individual brightness percentages, a labelled power core, targeting corners and bright orbital trace heads. Section readouts use actual lamp and scene counts. Original hit regions and bridge operations are retained.
- Decorative motion keeps the existing animation-setting and visibility guards, frame cap and cached static dial. No artwork downloads or runtime dependencies were added.
- Fixed a color-picker rotation issue found during visual QA: an open dialog now reflows from the activity configuration callback, instead of rebuilding its children during a layout callback.
- Debug and optimized release builds pass, all 12 unit tests pass, and Android lint reports `No issues found`.
- Verified on the connected OnePlus 5T / Android 13 in portrait and landscape, including a temporary 960 × 1706 display size. All 12 scene tiles fit; compact portrait reserves enough height for four 44 dp touch rows and the section heading. Small dials omit secondary readouts when space is limited.
- Verified the color picker opening in portrait and reflowing into side-by-side controls on rotation, without changing its selected color. Screenshots were refreshed from the running app; no lamp or scene write actions were used for this makeover's UI checks.
- The final optimized APK is installed on the phone. Original physical display size and automatic-rotation settings were restored after verification. API 24/API 37 runtime and reduced-motion behavior were not retested in this pass.
