# Build and release checklist

## Local Android Studio build

1. Install Android Studio and Android SDK Platform 35, Build Tools 35.0.0, and Java 17.
2. Open the `RetroCamAndroid` folder as an existing Gradle project.
3. Allow Gradle to resolve the declared plugins and dependencies.
4. Run `:app:testDebugUnitTest` and `:app:assembleDebug`.
5. Install `app/build/outputs/apk/debug/app-debug.apk` on a physical Android phone.

The project pins AGP 8.7.3, Kotlin Gradle Plugin 2.2.0 and Gradle 8.9. KGP 2.2.0 is documented as compatible with Gradle 7.6.3–8.14 and AGP 7.3.1–8.10.0; AGP 8.7.3 requires the Gradle 8.9 toolchain. Verify the exact toolchain on the CI runner before release.

## GitHub Actions build

The included `.github/workflows/android-build.yml` installs Java 17, Gradle 8.9 and Android SDK 35, runs unit tests, builds the debug APK and uploads it as a workflow artifact. Push this folder to a GitHub repository and open the Actions tab to trigger the build. A green workflow is a build check, not physical-device QA.

## Device verification gates

Test at least:

- OnePlus Nord 6.
- Each Motorola model explicitly supported (record model number, firmware and Android version; do not infer support from price range).
- Camera permissions with microphone granted and denied.
- Front/rear switching, zoom, focus, exposure compensation, flash, torch, 3s/10s timer.
- Every look on live preview, recorded video and captured photos; check frame rate, exposure, audio sync and thermal behavior.
- Imported high-resolution photos, EXIF orientation, crop positions, straighten, save and reopening.
- Video trimming and timeline export with clips from different phones and common codecs; verify audio mix, loop bed, fades, speed changes and title rendering.
- App background/foreground, incoming call interruption, low storage, low memory and device heating.

## Release signing

Do not commit a keystore or passwords. Configure a release signing key in a protected CI secret or local secure Gradle properties, create a release build, verify the signature, and retain the signing key securely. The current workflow intentionally only produces a debug APK.
