# Device compatibility and QA plan

## Primary targets
- OnePlus Nord 6 (record exact Android build, firmware and camera module information during testing)
- The specific Motorola Android models selected for the user's under-₹40,000 budget. Price is not a technical compatibility guarantee; each exact model must be tested.

## Current source behavior
- CameraX queries runtime camera capabilities and uses fallback recording quality when the requested quality is unavailable.
- Live/video colour effects are wired through `androidx.camera.media3:media3-effect:1.0.0-alpha04`, targeting CameraX PREVIEW and VIDEO_CAPTURE. This dependency/API path has not been compiled or physically validated in the current environment.
- The camera binding has a fallback path to ordinary CameraX preview and recording if Media3 effect setup or binding fails.
- Still-image looks use a separate off-screen OpenGL ES renderer with CPU fallback. Live/video has a separate Media3 GPU effect stack with colour grading and a generated bitmap overlay for grain, scanlines, vignette and VHS/DV dropout streaks; true chromatic split and texture parity are not implemented, and neither path has been physically verified.

## QA matrix for each exact physical model
Record the model identifier, Android version, security patch, firmware/build number, camera module and chipset, then verify:
- Back/front camera availability and switching.
- Preview startup, orientation, tap focus and AE metering.
- Media3 effect initialization, selected-look updates, preview stability and fallback behavior.
- Still capture dimensions, orientation, output path and applied look strength.
- Video recording with each selected live look; check actual video dimensions, frame pacing, audio sync and encoder errors.
- SD/HD/FHD/UHD quality availability; do not advertise 4K/60fps unless confirmed on that device.
- Permission combinations, including camera granted with microphone denied.
- Multiple clip import, reorder, trim, speed changes, single-file montage export, static title overlay, per-clip audio gain/fades, background-music mix and clip audio retention for common MP4/MOV/audio codecs.
- Photo Studio EXIF orientation, crop presets, rotate, look intensity and colour controls on high-resolution images; verify memory behavior on each device.
- MediaStore saving, open/share behavior, low storage, app backgrounding and interruption recovery.
- 10-minute thermal/battery test, memory pressure, sustained frame rate and GPU renderer fallback.

## Release gates
1. Android Gradle sync succeeds and `:app:assembleDebug` completes from a clean checkout.
2. Debug app installs and launches on the OnePlus Nord 6 and at least two named Motorola models with differing chipsets.
3. CameraX Media3 bridge works on a supported configuration and gracefully falls back on unsupported configurations.
4. Live preview, recorded video and saved stills have clearly documented effect parity/limitations.
5. Timeline export and audio mixing pass tests across a representative set of device-recorded and imported media.
6. No unsupported camera feature, frame rate or output resolution is presented as guaranteed.
7. Signing, privacy review, crash handling and release checks are completed before distribution.
