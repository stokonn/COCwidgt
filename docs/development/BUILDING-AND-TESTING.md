# Building and testing

This guide describes the current Android project as configured in the repository.

## Requirements

- JDK 17.
- Android SDK Platform 35.
- Android SDK Build Tools 35.0.0.
- The committed Gradle wrapper.

The Android Gradle Plugin version is declared in the root `build.gradle.kts`. The app module uses Kotlin's Compose compiler plugin and JVM 17 compilation.

## Open the project

Open the repository root in Android Studio, or use a terminal from the repository root.

Confirm the Java runtime:

```bash
java -version
```

Confirm that the Android SDK is installed and that `ANDROID_HOME` or the Android Studio SDK location is configured.

## Common Gradle commands

Run the unit tests:

```bash
./gradlew testDebugUnitTest
```

Run Android lint:

```bash
./gradlew lintDebug
```

Build the debug APK:

```bash
./gradlew assembleDebug
```

Build the release variant:

```bash
./gradlew assembleRelease
```

Run the standard CI checks together:

```bash
./gradlew assembleDebug testDebugUnitTest lintDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The release APK is written to:

```text
app/build/outputs/apk/release/app-release.apk
```

A successful Gradle release build does not by itself prove that the APK is signed with the project's established release certificate.

## Local release signing

The app module reads these environment variables when all four are present:

- `RELEASE_STORE_FILE`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

Keep the keystore and all passwords outside the repository. Do not paste secrets into source files, shell history, issue reports or CI logs.

Without the complete signing configuration, Gradle can still assemble a release variant, but it is not a verified distributable signed release. The published v0.6.0 workflow uses GitHub Actions repository secrets and verifies the expected certificate fingerprint.

## Continuous integration

The `.github/workflows/android.yml` workflow runs on pushes and pull requests targeting `main`. It sets up JDK 17 and Android SDK 35, then runs:

1. `assembleDebug`
2. `testDebugUnitTest`
3. `lintDebug`

The workflow uploads the debug APK as a short-lived GitHub Actions artifact. Signed release workflows are separate from ordinary pull-request CI.

## Suggested contributor workflow

1. Create a focused branch from the current `main`.
2. Read the relevant source and existing tests before changing behavior.
3. Add or update tests for data parsing, account isolation or widget planning as applicable.
4. Run unit tests, lint and a debug build.
5. Review the complete diff and check that no generated files or secrets were added.
6. Open a pull request with a concise summary and the checks performed.

For user-visible widget changes, also test on a physical device and note the Android version, launcher and widget dimensions. CI cannot validate launcher-specific rendering.
