# RetroCam — original Android retro camera

RetroCam is an original, offline-first Android camera and video-editing project inspired by the broad feature category of retro-camera apps. It does not reuse Dazz Cam source code, branding, proprietary LUTs, or extracted assets. The intended product is free to use: no account, subscription, watermark, analytics SDK, or network dependency is included in the current source.

## Present in the source snapshot

### Camera and capture
- Native Kotlin Android app, minSdk 29 / Android 10+, compileSdk 35.
- CameraX 1.5.3 preview, rear/front switching, tap-to-focus/AE, zoom, photo flash, torch when supported, and an optional rule-of-thirds composition grid.
- Still photos saved to `Pictures/RetroCam`.
- CameraX video recording saved to `Movies/RetroCam`; requests SD/HD/FHD/UHD quality with CameraX fallback to a supported quality.
- 3-second / 10-second still-photo timer and device-reported exposure compensation (EV); the app leaves ISO, shutter and manual focus to auto controls on devices where those manual request controls are not yet exposed.
- Optional microphone permission. The camera can remain usable if microphone permission is declined; video may then be silent.
- CameraX `Media3Effect` bridge is wired for `PREVIEW` and `VIDEO_CAPTURE`. The selected look and strength are sent to the live preview and recorded video path. If effect setup or camera binding fails, the app falls back to ordinary CameraX preview/recording.
- The live/video look path uses colour-grade operations (HSL, RGB scale and contrast) plus a Media3 GPU overlay for timestamp-varying grain, scanlines, vignette and VHS/DV dropout streaks. This is not yet pixel-identical to the still-photo shader; true per-channel chromatic separation and device-level effect validation remain outstanding.

### Photos
- Original still-image looks: VHS, DV, Super 8, 8mm, 16mm, 35mm, CCD, Portra-like, Gold-like, faded and monochrome.
- Off-screen OpenGL ES 2.0 still-image renderer with CPU fallback if EGL/GLES fails.
- Photo Studio for imported images: EXIF orientation correction, 90° rotation, ±10° straighten, aspect-ratio crops (original / 1:1 / 4:5 / 16:9 / 3:2 / 2:3) with horizontal and vertical framing controls, look strength, brightness, contrast and saturation.
- Photo Studio leaves the selected original untouched and saves an edited JPEG copy under `Pictures/RetroCam/Edited`.
- Photo Studio includes straighten adjustment (±10°) and horizontal/vertical crop positioning in addition to aspect-ratio presets.

### Video editing and library
- Quick imported-video trim using `MediaExtractor` / `MediaMuxer` stream-copy. Cuts are keyframe-bounded and are not guaranteed to be frame-exact.
- Timeline supports importing multiple clips, reordering, removing and assigning per-clip trim ranges.
- Timeline source uses Media3 Transformer to concatenate the arranged clips into one MP4. Embedded clip audio is retained when supported by the source media.
- One optional background-audio item can be mixed as a looping music bed over the video sequence.
- Clip-level audio gain and fade-in/out, master clip audio level, music level and music fade-in/out are wired in source.
- Optional static title overlay can be rendered over each clip.
- Combined video exports are published under `Movies/RetroCam/Edited`.
- In-app RetroCam media library lists media from the app's own `Pictures/RetroCam` and `Movies/RetroCam` folders and supports opening/sharing; photos can be sent to Photo Studio. The list refreshes automatically when reopened.

## Still outstanding

This is not yet a finished Dazz Cam replacement. The following are not complete or not validated:

- **Build and device verification:** the source has not been compiled with Android Gradle Plugin/SDK in this workspace, nor tested on a physical phone. The CameraX Media3 bridge dependency is alpha (`1.0.0-alpha04`) and needs a real Gradle build and runtime validation. The timeline audio composition and text overlay also need codec/device verification.
- **Live effect parity:** the live/video path now has a GPU overlay for film grain, scanlines, vignette and VHS/DV streaks, but its quality is not identical to the still-photo shader. True RGB-channel chromatic separation, per-look fine texture tuning and validation on each phone remain outstanding.
- **Advanced editing:** no transitions/crossfades, animated title/sticker overlays, keyframes, speed ramps, split-at-playhead, waveform editing, or multiple independently controlled music/SFX tracks. The current title is static and appears across each clip.
- **Photo editor:** Photo Studio has ratio-based crop presets with repositioning and ±10° straighten, but not draggable freeform crop handles. Curves, selective HSL, healing/retouch, masks, layers and persistent non-destructive edit history remain absent.
- **Camera controls:** 3s/10s timer and device-supported exposure compensation are implemented in source. Manual ISO/shutter/focus, white-balance controls, aspect-ratio guides and advanced capture settings remain absent.
- **Release:** no APK has been built yet in this workspace. The included GitHub Actions workflow is configured to run the unit tests, build a debug APK, and upload it as a 14-day `RetroCam-debug-apk` artifact. A production-signed APK/AAB, signing key, privacy/store review, performance/thermal testing and release QA are not included.

## Build and validation

1. Open this folder in Android Studio with Android Gradle Plugin 8.7.3, Kotlin Gradle Plugin 2.2.0 and Gradle 8.9, or push the folder contents to GitHub and use the included Actions workflow.
2. Install Android SDK Platform 35 and allow Gradle to resolve Maven dependencies.
3. Sync the project and run `:app:testDebugUnitTest` followed by `:app:assembleDebug`.
4. The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. The GitHub Actions route uploads it as `RetroCam-debug-apk`; see [GETTING_THE_APK.md](GETTING_THE_APK.md) for phone-friendly instructions.
5. Install the debug build on a physical Android phone and verify permissions, camera binding, filter preview, recording, timeline export, audio mix and media-library saving.

The authoring environment used for this snapshot did not contain an Android SDK, `android.jar`, Gradle executable or Gradle wrapper JAR, and network access to fetch them was unavailable. Consequently, a successful Gradle build cannot be claimed. The pure Kotlin timeline math can be compiled and exercised independently; unresolved Android/AndroidX symbols prevent this workspace from performing a true application compile.

## Device compatibility

Primary target: OnePlus Nord 6 and Motorola Android models selected by the user. Compatibility is capability-based, not price-based. Camera combinations, supported quality levels, codec/encoder behavior, GPU drivers and thermal limits vary by exact model, firmware and region. CameraX fallback helps with unsupported combinations but does not replace physical QA. See `docs/DEVICE_COMPATIBILITY.md`.

## Architecture snapshot

| Area | Current implementation |
|---|---|
| Camera | CameraX use cases, capability-aware controls and fallback |
| Live/video effects | CameraX `Media3Effect` bridge, colour grade plus Media3 GPU film-texture overlay; unverified on hardware |
| Still-image effects/editor | Off-screen GLES shader with CPU fallback; EXIF-correct shared image decoding; Photo Studio crop position/straighten/rotate/look/basic colour controls |
| Quick video trim | Android `MediaExtractor` / `MediaMuxer` stream copy |
| Timeline montage | Media3 Transformer sequential composition, optional music, clip audio gain/fades and static title overlay |
| Library | MediaStore query scoped to RetroCam-owned folders |
| Release | CI build workflow included; no successful CI run, signed APK or device validation yet |
