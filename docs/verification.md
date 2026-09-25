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

## Warp 11 visual pass — 25 September 2026

- The previous orbital makeover is preserved in commit `50184e6`.
- Scene previews now depict ringed planets, distant stars, perspective grids and luminous horizons using each scene's actual palette. Stable scene IDs vary their compositions. Panel illumination follows the palette, and etched rails frame the section headings.
- The dial adds a machined collar, beveled energy cells, radial photon trails and a lens core wrapped by orbiting cyan/violet particles. The backdrop gains curved navigation trajectories, and the picker gains a glass bevel and optical encoder marks. Touch geometry and bridge operations are unchanged.
- All new animation shares the existing lifecycle-aware, reduced-motion-aware reactor animator. The static dial remains cached. A bounded hardware layer caches the visible scene viewport, with Android invalidating it on content changes and scrolling.
- The static star chart is rendered once into a texture capped at a 1280 px long edge and regenerated on bounds changes. This avoids resubmitting its many chart paths on every reactor frame. In the final on-device idle sample (831 frames, about 28 seconds), median frame time was 9 ms, 95th percentile 11 ms, and no frames missed their deadline. This is a device-specific sample, not a performance guarantee.
- Debug/release builds, all 12 existing unit tests and Android lint pass (`No issues found`). No runtime libraries or downloaded assets were added.
- Verified the final artwork in portrait, landscape and a temporary 960 × 1706 portrait viewport; all twelve scene buttons remained fully visible (118 px tall in the compact viewport). The picker opens without changing lamp state.
- Reduced-motion check: with Android's animator scale set to zero and the app relaunched, two separated captures of the app content were pixel-identical. Normal animation was restored afterward. API 24/API 37 runtime remains untested; this pass used the OnePlus 5T / API 33.
- The optimized APK is installed, and screenshots are refreshed from the running app. Original display size, automatic rotation and animator scale were restored. No lamp or scene writes were used during these UI checks.

## Instrument console refinement — 25 September 2026

- Removed section headings/counts, sector brightness badges, the gesture hint and power-core labels. Neutral spacers retain the original control positions. No new visible text was added.
- Main-screen buttons use matte fills; dialog surfaces and the color disc retain their existing styling. Scene previews now use palette-colored interference curves, spectral bars and reticles. The power core has a larger glyph, etched ticks and moving broken-ring traces.
- The center button charges while its bridge request runs, then animates confirmed lamp output with a 1.35-second staggered radial ignition/quench and three expanding/collapsing wave fronts. The first successful connection uses the ignition sequence. Offline/error state clears the pending effect. Off lamps' retained Hue brightness is normalized to zero during interpolation.
- Reachable lamps at full brightness have a faint breathing rim and traveling highlight. Decorative motion shares the existing lifecycle-aware frame loop, and all transitions honor the system animation setting.
- Debug and optimized release builds pass, all 13 unit tests pass (including on/off interpolation with retained bridge brightness), and Android lint reports `No issues found`.
- Verified on the USB-connected OnePlus 5T / Android 13: recorded and inspected startup, global off and global on; checked portrait, landscape and a temporary 960 × 1706 compact screen. All twelve scene tiles fit. Two captures taken two seconds apart with animator scale zero have pixel-identical app content.
- Exact original lamp power, brightness, color and effect states were restored and verified after global-power testing. Original display size, rotation and animator scale were restored. The optimized APK is installed on the phone. API 24/API 37 runtime behavior was not retested.

## Spectrum cartridges and action line — 25 September 2026

- Reduced the former heading/footer spacers between the dial and scene grid, reserving a fixed 32 dp bottom line for action feedback. Replaced all Toast calls with this line: a confirmation/warning icon, accent edge, subtle tinted surface, diminishing lifetime rail and final fade. Messages replace the previous action, expire after 4.2 seconds and preserve their expiry across in-place rotation. The line has a polite accessibility live region.
- Aligned the bridge and scene indicators using equal-height rows, matching icon slots and shared text insets. Connection and scene state now use distinct circular/diamond marks and teal/violet accents.
- Scene previews return to broad spectrum cartridges: actual scene colors fill the preview area, with uniform fine vertical markings and a steady horizontal light rail. Removed the interference curves and reticles.
- The power core adds an eight-blade illuminated iris, a rotating spectrum collar, opposing inner tracks, soft seam illumination and a brighter central power glyph. Animation remains lifecycle-aware and respects reduced motion; the periodic rotations wrap continuously.
- Debug/release builds, all 13 unit tests and Android lint pass. Tested on the USB-connected OnePlus 5T / Android 13: power off/on receipts, message replacement/expiry, scene activation, active-scene alignment, rotation during a receipt, landscape and 960 × 1706 compact portrait. All twelve cards fit. Two reduced-motion captures of the app are pixel-identical.
- Restored and verified the original lamp states after power/scene tests; restored physical resolution, automatic rotation and animator scale. Installed the final optimized APK. Updated screenshots show the test scene and its action receipt. Dialogs and the color disc were not changed in this pass.
