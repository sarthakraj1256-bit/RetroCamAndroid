# RetroCam implementation status (2026-11-08)

This status distinguishes source code written from a successful Android build or device test. All marked source features remain **unverified at runtime** until the project is built and exercised on real phones.

## Implemented in source

| Feature | Source status | Notes |
|---|---|---|
| Camera preview | Implemented | CameraX PreviewView; normal preview fallback |
| Rear/front switching | Implemented | Checks requested lens exists; switching while recording is blocked |
| Rule-of-thirds framing grid | Implemented in source | Optional overlay on the preview; does not affect saved output |
| Tap-to-focus / AE | Implemented | CameraX metering action |
| Zoom | Implemented | Uses active camera's reported zoom range |
| Photo timer | Implemented in source | Off / 3-second / 10-second countdown with cancel |
| Exposure compensation | Implemented in source | EV slider uses the active camera's reported compensation range; manual ISO/shutter remain absent |
| Photo flash / torch | Implemented | Device capability checked |
| Photo capture | Implemented | MediaStore JPEG output |
| Still-image looks | Implemented | Off-screen GLES renderer with CPU fallback; grain, vignette, scanlines and chromatic split |
| Still photo look intensity | Implemented | Strength control applies to capture/import |
| Photo Studio | Implemented in source | Import, EXIF orientation correction, 90° rotation, ±10° straighten, aspect crops (original/1:1/4:5/16:9/3:2/2:3) with horizontal/vertical framing, film look, strength, brightness, contrast, saturation; saves a new JPEG copy |
| CameraX video recording | Implemented | CameraX Recorder requests SD/HD/FHD/UHD with supported-quality fallback |
| Live camera/video looks | Wired in source; unverified | CameraX Media3Effect bridge targets preview and video capture; HSL/RGB/contrast plus GPU overlay for timestamp-varying grain, scanlines, vignette and VHS/DV streaks. Not identical to still shader |
| Quick video trim | Implemented in source | MediaExtractor/MediaMuxer stream-copy; keyframe-bounded; validates no-empty-output and limits a single sample buffer to 64 MB |
| In-app media library | Implemented | RetroCam-owned Pictures/Movies list; open/share; edit photos in Photo Studio; refreshes on return |
| Clip timeline | Implemented in source | Multiple imports, reorder, remove, trim ranges and per-clip playback speed (0.5×–3×) |
| Combined timeline export | Wired in source; unverified | Media3 Transformer concatenates ordered clips into one MP4 |
| Timeline look | Wired in source; unverified | One GPU colour-grade preset and strength applies to all clips |
| Clip audio | Wired in source; unverified | Master clip audio plus per-clip volume and fade-in/out controls |
| Background music | Wired in source; unverified | One looping music bed, level and fade-in/out; audio normalisation is configured for 48 kHz stereo PCM |
| Title overlay | Wired in source; unverified | Optional static title overlay appears on each clip in the montage |

## Remaining engineering work

| Feature | Status | What remains |
|---|---|---|
| Live/video film texture parity | Partially implemented | GPU overlay now adds timestamp-varying grain, scanlines, vignette and VHS/DV dropout streaks. Still outstanding: true RGB-channel chromatic split, per-look texture fidelity, performance tuning and device validation |
| Advanced editing | Not complete | Split-at-playhead, transitions/crossfades, speed ramps, animated titles/stickers, keyframes, clip-specific overlays and multiple audio/SFX tracks/waveforms |
| Advanced photo editing | Not complete | Draggable freeform crop handles, curves, selective HSL, masks, healing/retouch, layers and persistent non-destructive edit history. Ratio crops, repositioning and ±10° straighten are implemented |
| Advanced camera controls | Partially implemented | Device-reported exposure compensation and 3s/10s timer are present. Manual ISO/shutter/focus/white balance, aspect-ratio guides and additional capability-gated settings remain |
| Automated tests | Partially added | Pure Kotlin timeline trim/speed unit tests are included. Camera, codec, audio composition, MediaStore and export instrumentation tests are still absent |
| CI Android build | Workflow configured; not run here | `.github/workflows/android-build.yml` installs SDK 35, runs unit tests, builds a debug APK and uploads a clearly named artifact for 14 days |
| Android build | Not verified | Clean Gradle sync and `:app:assembleDebug` on Android SDK 35; CI workflow is included but has not run |
| Physical-device QA | Not done | Test camera, live effects, recording, export, codecs, thermal and memory behavior on the exact OnePlus Nord 6 and exact Motorola models |
| Signed release APK/AAB | Not produced | Resolve build/API issues, run QA, then configure signing and release checks |

## Build and validation limitation

The authoring workspace used for this project does not contain an Android SDK, `android.jar`, Gradle executable or Gradle wrapper JAR, and outbound DNS/network access was unavailable when checked. A `kotlinc` pass stops on missing Android/AndroidX classes; this is **not** an Android compile. The pure Kotlin timeline math was separately compiled and passed smoke checks; manifest XML, styles XML, workflow YAML and ZIP integrity were also checked. A successful Gradle build, working app install or finished APK cannot be claimed from this environment. The included GitHub Actions workflow can perform the first real Android SDK build after the source is pushed to a repository.

The CameraX Media3 effect bridge uses `androidx.camera.media3:media3-effect:1.0.0-alpha04`; despite the source wiring, its compatibility and runtime behavior must be verified by a real build and test device. The bridge is not equivalent to a validated Dazz Cam-style GPU pipeline yet.

## Required release gate

1. Open in Android Studio with Android SDK Platform 35, Android Gradle Plugin 8.7.3, Kotlin Gradle Plugin 2.2.0 and Gradle 8.9; sync and run `:app:assembleDebug`.
2. Fix any actual compiler/API errors surfaced by that build.
3. Install and verify on the OnePlus Nord 6 and the exact Motorola models selected for support.
4. Test camera switching, permission combinations, photo EXIF/orientation, filter intensity, recording audio sync, live/video effect stability, trim, montage export, mixed audio and MediaStore saving.
5. Run thermal, memory, low-storage and interruption tests before signing a release APK/AAB.
6. To obtain the first installable debug APK through GitHub, follow `GETTING_THE_APK.md` and download the `RetroCam-debug-apk` Actions artifact.
