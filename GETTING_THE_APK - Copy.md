# Get the RetroCam APK from GitHub Actions

The project is configured to build an installable **debug APK** using GitHub Actions. The APK is not included in this source ZIP because it must be created by an Android Gradle build.

## What you need

- A GitHub account.
- The `RetroCamAndroid` project folder extracted from this ZIP.
- Internet access while GitHub Actions downloads the Android toolchain and Maven dependencies.

## Build it

1. Sign in to GitHub and create a new repository named `RetroCamAndroid`.
2. Keep the repository public or private; either works for this workflow.
3. Upload the **contents of the extracted `RetroCamAndroid` folder** to the repository root. Include the hidden `.github` folder because it contains the build workflow. Do not upload only the ZIP file.
4. Open the repository's **Actions** tab.
5. Open the workflow named **Android build and unit tests**. The first push should start it automatically. If it has not started, choose **Run workflow**.
6. Open the newest run. Wait until the build job finishes. A green check means the tests and debug APK build completed successfully; a red cross means the build failed and its log must be inspected.
7. On the successful run page, scroll to **Artifacts** and select **RetroCam-debug-apk**. GitHub downloads an artifact ZIP.
8. Extract that ZIP. It contains `RetroCam-debug.apk` and its SHA-256 checksum file.
9. Move `RetroCam-debug.apk` to the Android phone and tap it to install. Android may ask you to allow the Files app or browser to install unknown apps. Allow that permission for the app you used to open the APK, then install RetroCam.

## Important notes

- This workflow creates a debug-signed APK for installation/testing. It is not a Play Store release or a production-signed APK.
- The APK artifact is retained by GitHub for 14 days. Download it before it expires.
- When installing a later debug build, Android may require uninstalling the previous debug build first if the signing key changed. Uninstalling removes that app's local data and media library state only if the files are stored in app-private storage; RetroCam's captured media is stored in shared `Pictures/RetroCam` and `Movies/RetroCam` folders.
- A successful CI build does not prove the camera GPU effect pipeline or video export works on every phone. Test on the target phone before relying on it.

## If the GitHub build fails

Open the failed workflow run, tap the red **build** job, and share a screenshot of the first actual error (not just the last line). The source can then be corrected against the real compiler/dependency diagnostic.
