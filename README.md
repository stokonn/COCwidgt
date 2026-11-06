# COCwidgt

COCwidgt is an unofficial Android home-screen companion for Clash of Clans. Version **0.4.0** (`versionCode` 4) combines the official player profile API with local village export data. It is not affiliated with Supercell.

## What it shows

- A resizable Glance widget that adapts content to its actual width and height.
- Player name, tag and Town Hall from the official API and/or export.
- Active builder, laboratory, pet and Builder Base upgrades with local countdowns.
- Export age, including a stale marker after 24 hours.
- Import parsing by stable game data ID, with explicit `Unknown game item (ID …)` labels for IDs without a catalogue entry.
- Optional Supercell API profile sync; the API does not provide live upgrade timers.

The village export supplies levels and remaining upgrade timers. An export timer is the **remaining seconds at the export timestamp**; the app calculates the finish instant as `timestamp + timer`. Old exports therefore show elapsed timers as ready. The export does not include each upgrade's original total duration, so COCwidgt does not display a made-up percentage. The UI reserves progress bars for data with a known total duration.

## Import village data

1. In Clash of Clans, open **Settings → More Settings → Data Export → Copy**.
2. Open COCwidgt and tap **Paste & import village JSON**.
3. Allow clipboard access if Android asks. Check the tag and import status.
4. Add or resize the COCwidgt widget on your launcher.

Import again after gameplay to refresh building levels and timers. The JSON is parsed and stored on the device; it is not sent to Supercell or another service.

## Supercell API setup

1. Create an API key in the official [Clash of Clans developer portal](https://developer.clashofclans.com/).
2. Allowlist the public egress IP of the network making requests.
3. Enter your player tag and API token in the app, then save and tap **Sync now**.

The token is encrypted at rest with AES-GCM using an Android Keystore key. API requests can fail on mobile data when the carrier's egress IP is not allowlisted. Never enter a Supercell ID password.

## Catalogue and visual assets

`GameCatalogue.kt` maps the supported export IDs to their known English game names and categories. Unknown IDs are retained and named explicitly; the app never invents entity names. The exported stable ID is persisted with each active job. The current build uses small category glyphs and original color/layout styling; it does not bundle or represent third-party artwork as official game icons.

Names and game identifiers are used to identify game entities. This project contains no extracted game artwork. Supercell marks and game content remain the property of Supercell. See [Supercell Fan Content Policy](https://supercell.com/en/fan-content-policy/). Mapping sources, attribution and licensing notes are in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). The sample export structure and timer behavior are described in public village export guides such as [ClashClock's import guide](https://clashclock.com/guides/user-guide/import-your-village) and [data and timer notes](https://clashclock.com/guides/how-it-works); the implementation is not affiliated with those projects.

## Build and checks

Requirements: JDK 17, Android SDK 35, Gradle 8.9. From the project root:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

Android Studio can also open and build the project.

## Signed release

Release automation runs when `v0.4.0` is pushed. Configure these GitHub Actions repository secrets before tagging:

- `RELEASE_KEYSTORE_BASE64`: base64-encoded Android release keystore.
- `RELEASE_STORE_PASSWORD`: keystore password.
- `RELEASE_KEY_ALIAS`: signing key alias.
- `RELEASE_KEY_PASSWORD`: signing key password.

The workflow fails before the build if any secret is missing, verifies the APK signature, publishes it as a GitHub prerelease with a SHA-256 file, then downloads and compares the uploaded APK. Keep the keystore and passwords out of Git and workflow logs. For local release builds, provide the same values as environment variables (`RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`).

## Known limitations

- The catalogue is a maintained subset, not a complete dump of every game entity or current level. The app labels unrecognized IDs rather than guessing.
- Icons are category glyphs, not official entity artwork.
- Upgrade totals are not present in the export; accurate countdowns are available, but progress percentages are intentionally omitted unless a known total duration is added later.
- Export data is a manual snapshot. The official API cannot refresh building levels or live upgrade timers.
- API key IP allowlisting may make sync unavailable on changing networks.
