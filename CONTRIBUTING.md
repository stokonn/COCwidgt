# Contributing

Thanks for your interest in contributing to COCwidgt.

## Before opening a change

- Read the [README](README.md) and [documentation index](docs/README.md).
- Check existing issues and pull requests for related work.
- Keep changes focused and explain the user-visible or maintenance benefit.
- For behavior changes, inspect the current implementation and add regression tests.

## Development setup

Use JDK 17 and Android SDK Platform 35 with Build Tools 35.0.0. See [Building and testing](docs/development/BUILDING-AND-TESTING.md) for setup and Gradle commands.

Before requesting review, run:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

If a change affects release configuration, also run the relevant release checks where signing configuration is safely available. Never add signing credentials or API keys to the repository.

## Pull requests

A useful pull request includes:

- A concise explanation of the problem and solution.
- Screenshots or a short recording for user-visible UI/widget changes, where appropriate.
- Tests added or updated.
- Exact commands run and their results.
- Known limitations or manual checks that remain outstanding.

Do not claim physical-device validation based only on emulator, unit-test or CI results.

## Project-specific guidance

- Keep Player Tag normalization consistent across import, account storage, profile lookup and widget selection.
- Treat the Player Tag as the stable account identity; display labels are local metadata.
- Preserve unknown export IDs and avoid inventing catalogue names, levels, durations or artwork.
- Keep village export data local. Do not introduce uploads of village JSON without an explicit product and privacy review.
- Keep the Developer API key separate from village export data and never log or expose it.
- Preserve the existing release-signing identity. Do not replace or regenerate the established release key.
- Maintain Supercell fan-project disclaimers and third-party artwork attribution.

## Scope

COCwidgt is currently an Android-only project. Do not describe or introduce an iOS/Kotlin Multiplatform target as if it already exists.
