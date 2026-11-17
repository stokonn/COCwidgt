# COCwidgt

**COCwidgt** is an unofficial Android home-screen widget for tracking Clash of Clans village upgrades. It turns a locally exported village snapshot into a compact, resizable overview of active upgrades, levels and countdowns.

- **Current development version:** 0.6.0 (version code 7)
- **Android:** 10 (API 29) or newer
- **Package:** `dev.stokonn.cocwidgt`
- **Project status:** prerelease development
- **Game:** Clash of Clans

COCwidgt is a fan-made project. It is not affiliated with, endorsed by or sponsored by Supercell.

## Features

- **Resizable home-screen widget** built with Jetpack Glance. The layout responds to the dimensions reported by the launcher and the active upgrade count, fitting more rows when more space is available.
- **Village overview** with player tag, Town Hall, categories, game artwork, levels, countdowns and progress indicators.
- **Upgrade tracking** for supported Home Village and Builder Base buildings, heroes, troops, spells, pets, equipment, laboratory and helper-related activities.
- **Local JSON import.** Village data and upgrade timers come from the Clash of Clans Data Export; the app does not need an API connection to import or display that data.
- **Optional player-name lookup.** When an export does not contain a player name, the official Clash of Clans API can resolve the name from the Player Tag. A manually entered name can be used instead.
- **Automatic widget refresh.** WorkManager periodically refreshes the widget so countdowns and progress indicators continue to advance between app launches.
- **Explicit handling of unknown items.** Export entries without a supported catalogue mapping are shown as unknown game items instead of silently disappearing.
- **Town Hall 19 support.** The catalogue includes all TH19-updated building, defense, trap, troop, spell, hero, pet, and Guardian levels, plus Squad troops and the Town Hall 19 structures and variants.

## Getting started

### 1. Export your village

In Clash of Clans:

1. Open **Settings**.
2. Open **More Settings → Data Export**.
3. Choose **Copy** to copy the village JSON.

The exact labels can vary slightly between game versions.

### 2. Import into COCwidgt

1. Open COCwidgt.
2. Tap **Paste & import village JSON**.
3. Allow clipboard access if Android asks.
4. Check the imported Player Tag and import status.

Import again after playing to update levels and upgrade timers. The export is a snapshot, not a live connection to the game.

### 3. Add the widget

1. Long-press an empty area of your Android home screen.
2. Open the widget picker.
3. Find **COCwidgt** and add the widget.
4. Resize it using your launcher's widget handles.

The number of visible rows depends on the space the launcher gives the widget. Launcher implementations differ, so the final appearance can vary by device.

## How upgrade timers work

The village export provides the remaining seconds for an upgrade at the time the export was created. COCwidgt calculates the finish time from the export timestamp and that remaining duration.

The export does **not** provide the original total duration of every upgrade. As a result, the app does not invent a percentage based on guessed upgrade durations. Instead, a progress indicator uses the first moment COCwidgt observed that upgrade running and its calculated finish time.

- Re-importing the same running upgrade preserves its first observed start, so progress does not jump backwards.
- A changed timer or a different Player Tag starts a new observation window.
- If an upgrade is first imported after it has already started, its true start is unknown; its progress indicator begins at the first import.
- If timestamps are missing or invalid, no progress indicator is shown.
- An old export can contain timers that have already elapsed; those upgrades are shown as ready.

Countdowns and progress are based on the exported data. Import a fresh export to reflect changes made in the game.

## Optional Developer API key

The optional **Developer API key** is used only to look up the public player name through:

`GET https://api.clashofclans.com/v1/players/{playerTag}`

The key is stored using Android Keystore-backed encryption and sent to the official API over HTTPS. Supercell API keys are restricted to allowed public IP addresses, so lookups can fail when your public IP changes or when you use a different network.

Important distinctions:

- The Developer API key is **not** the in-game Data Export ownership token.
- COCwidgt does not collect or verify the ownership token.
- The API is not used to obtain village levels, upgrades or timers.
- Import and widget display continue to work when the API is unavailable.
- A successfully resolved name is cached for its matching Player Tag. A cached name from another tag is not reused.
- If no name is available, the app can show the Player Tag or a manually entered name.

You do not need to configure an API key to use the core import and widget features.

## Multiple accounts

Add accounts with their Player Tags from the Accounts card in the app. Adding an account fetches its public profile using the optional Developer API key. Each account's village export and cached profile are stored separately; an existing single account is migrated automatically on first launch. Account names are local labels.

Each newly placed widget asks which saved account it should show. Existing widgets keep their saved account when the active account changes. If that account is removed, the widget displays the active account instead. Widget refreshes follow Android's scheduled update limits.

## Data and privacy

- Village JSON is parsed and stored locally on your device.
- Village export data is not sent to Supercell by COCwidgt.
- The optional Developer API key is used for the player-profile name request described above.
- The app does not use the API as a replacement for the village export.

## Catalogue and artwork

The game catalogue maps supported export IDs to known English names, categories, upgradeability and artwork resources. TH19 metadata and artwork were checked against [ClashKingAssets commit `6212dddb`](https://github.com/ClashKingInc/ClashKingAssets/commit/6212dddb13ec5a0f13b5314361e49fc4a4325a61); current upstream `main` was `fa40345986d5c862480e6fa34aa5100ab8eee345` and contains no newer TH19 entity-level metadata changes in the audited static data. Guardian Shrine is recognized as non-upgradeable; Guardian upgrades are tracked in their own group. Imports report unsupported IDs, levels over the verified cap, and timed rows for non-upgradeable IDs while preserving the rows.

TH19 building and trap level images, Town Hall 19 and its three hall variants, the new structure images, Heroic Spell, TH19 Squad troop portraits, upgraded troop/hero portraits, and all three Guardian portraits are bundled from the ClashKingAssets community project. Guardian Shrine has no standalone building image in that upstream commit and is not an upgrade row. Unknown entities, and known entities without a matching bundled asset, use a neutral fallback rather than guessed artwork or names.

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for source mapping, artwork attribution and licensing information. The relevant GPL-3.0 license text is included in [LICENSES/GPL-3.0-ClashKingAssets.txt](LICENSES/GPL-3.0-ClashKingAssets.txt).

Clash of Clans, Supercell's names, marks and game content remain the property of their respective owners. See the [Supercell Fan Content Policy](https://supercell.com/en/fan-content-policy/).

## Build from source

### Requirements

- JDK 17
- Android SDK Platform 35
- Android SDK Build Tools 35.0.0
- Gradle is provided by the included wrapper

Open the project in Android Studio, or run these commands from the repository root:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleRelease
```

The debug APK is produced at:

`app/build/outputs/apk/debug/app-debug.apk`

The release build enables code shrinking and resource shrinking. To sign a local release build, provide the signing values as environment variables:

- `RELEASE_STORE_FILE`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

Keep the keystore and passwords out of source control and build logs. A release build made without signing credentials is not a distributable signed release.

## CI and signed prereleases

The Android CI workflow runs unit tests, lint and a debug build for pull requests and pushes to `main`.

The version-specific signed prerelease workflows use GitHub Actions repository secrets:

- `RELEASE_KEYSTORE_BASE64` — base64-encoded Android release keystore
- `RELEASE_STORE_PASSWORD` — keystore password
- `RELEASE_KEY_ALIAS` — signing key alias
- `RELEASE_KEY_PASSWORD` — signing key password

The v0.6.0 workflow builds and verifies the signed APK, checks its package name, version metadata and original signing certificate, then publishes `cocw0.6.0.apk` with a SHA-256 checksum file. The release is marked as a **GitHub prerelease**, not a stable release.

## Known limitations

- The catalogue and bundled artwork cover a maintained subset of game entities. Unsupported IDs remain visible as unknown items.
- The game export is a manual snapshot. Import again after gameplay to refresh the displayed village state.
- The export does not include original total upgrade durations, so progress is based on first observation rather than a guessed total.
- Periodic refresh uses WorkManager and is subject to Android scheduling and launcher behavior; it is not a per-second real-time update.
- Widget sizing and rendering depend on the launcher. Physical-device checks are still needed across different launchers and screen sizes.

## Disclaimer

COCwidgt is an unofficial fan-made companion project and is not endorsed by Supercell. Clash of Clans and all related assets, names and marks belong to Supercell and their respective owners.
