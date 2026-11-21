<div align="center">

<h1>COCwidgt</h1>

<p><strong>Your Clash of Clans village. Right on your home screen.</strong></p>

<p>An unofficial Android companion widget for tracking village upgrades from your own exported village data.</p>

<p>
<a href="https://github.com/stokonn/COCwidgt/releases/tag/v0.6.0"><strong>COCwidgt 0.6.0 — latest prerelease</strong></a> ·
<a href="docs/user-guide/GETTING-STARTED.md">Getting started</a> ·
<a href="docs/README.md">Documentation</a>
</p>

<p>
<img alt="Android 10+" src="https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white" />
<img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white" />
<img alt="Widget" src="https://img.shields.io/badge/Home%20screen-Glance-64748b" />
<img alt="Project status" src="https://img.shields.io/badge/Status-Prerelease-orange" />
</p>

</div>

---

## Your village, at a glance

COCwidgt turns a **Clash of Clans Data Export** into a compact, resizable Android home-screen widget. See active upgrades, levels and countdowns without opening the game.

The app is built around a simple boundary: **you export your village, COCwidgt imports the snapshot, and your widget presents that locally stored data.** It is not a live game connection.

## What it does

| 🏰 **Village overview** | ⏳ **Upgrade tracking** | 📱 **Adaptive widget** |
| :--- | :--- | :--- |
| Player identity, Town Hall, upgrade categories and game artwork. | Levels, remaining times and progress based on the first observed timer window. | Resizable home-screen widget that plans visible content around launcher-provided dimensions. |

| 👥 **Multiple accounts** | 🔒 **Local-first data** | 🧩 **Catalogue diagnostics** |
| :--- | :--- | :--- |
| Keep villages separate and choose an account independently for each widget. | Village exports stay on-device; public profile lookup is optional. | Unsupported IDs and catalogue mismatches remain visible instead of being silently discarded. |

### More highlights

- **Manual JSON import** from the in-game Data Export feature.
- **Optional public player lookup** through the official Clash of Clans API.
- **Town Hall 19 catalogue support** for the verified entities and artwork included in the project.
- **Scheduled widget refresh** through WorkManager, subject to Android background scheduling.
- **Per-widget account selection** so different widgets can display different saved villages.

## A look at the widget

COCwidgt's widget layout adapts to the size allocated by your launcher. The repository currently does not include a maintained set of release screenshots, so this section intentionally avoids mockups or unverified device captures.

## Get COCwidgt

<blockquote>
<strong>Latest published build: COCwidgt 0.6.0 (prerelease)</strong><br />
<a href="https://github.com/stokonn/COCwidgt/releases/download/v0.6.0/cocw0.6.0.apk">Download cocw0.6.0.apk</a> ·
<a href="https://github.com/stokonn/COCwidgt/releases/tag/v0.6.0">Release page and SHA-256 checksum</a>
</blockquote>

- **Android:** 10 or newer (API 29+).
- **Package:** `dev.stokonn.cocwidgt`.
- **Version:** 0.6.0 · version code 7.
- **Status:** prerelease development, not a stable Play Store release.

For installation and first import, follow the [Getting started guide](docs/user-guide/GETTING-STARTED.md).

## How it works

1. **Export:** copy your village JSON using Clash of Clans **Settings → More Settings → Data Export**.
2. **Import:** paste the JSON into COCwidgt. The app parses the snapshot and maps supported entity IDs through its maintained catalogue.
3. **Organize:** manage saved accounts using their Player Tags. Existing single-account data is migrated on first launch.
4. **Place:** add a COCwidgt home-screen widget and choose which saved account that widget should show.
5. **Refresh:** WorkManager periodically asks Android to re-render the widget using locally stored data.

The export is a snapshot. Import again after gameplay to update levels and upgrade timers.

## Privacy and API use

- Village JSON is parsed and stored locally on your device.
- COCwidgt does not upload village export data to Supercell.
- The optional Developer API key is used only for a public player-profile lookup, such as resolving a player name.
- The API lookup does not retrieve village levels, upgrades or timers.
- The key is stored using Android Keystore-backed encrypted storage and sent over HTTPS.
- Import and widget display continue to work without a configured API key.

Read the [architecture overview](docs/architecture/OVERVIEW.md) for data-flow and security boundaries.

## Honest timing and progress

The export provides remaining seconds at the time of export, not the original total duration of every upgrade. COCwidgt calculates an expected finish time from the export timestamp and remaining duration.

It does not invent a percentage using guessed upgrade durations. Progress can be shown from the first moment the app observed a particular upgrade running. If an upgrade is first imported after it started, its true start is unknown.

Widget refresh is scheduled, not continuous. WorkManager uses a 15-minute periodic interval, but Android may delay background work and launcher redraws. This is not a per-second live countdown.

## Documentation

| Guide | Description |
| --- | --- |
| [Documentation index](docs/README.md) | Complete documentation map. |
| [Getting started](docs/user-guide/GETTING-STARTED.md) | Install, export, import and add the widget. |
| [Accounts and widgets](docs/user-guide/ACCOUNTS-AND-WIDGETS.md) | Multi-account storage and per-widget selection. |
| [FAQ and troubleshooting](docs/user-guide/FAQ.md) | Common questions and limitations. |
| [Architecture overview](docs/architecture/OVERVIEW.md) | Components, data flow and security boundaries. |
| [Building and testing](docs/development/BUILDING-AND-TESTING.md) | Development setup, Gradle commands and CI. |
| [0.6.0 release notes](docs/releases/0.6.0.md) | User-facing changes in the current prerelease. |
| [0.6.0 verification](docs/verification/0.6.0.md) | Automated release checks and manual acceptance areas. |
| [Changelog](CHANGELOG.md) | Version history. |
| [Contributing](CONTRIBUTING.md) | How to propose and validate changes. |
| [Security policy](SECURITY.md) | Responsible vulnerability reporting. |

## Built with Kotlin

COCwidgt is a native Android application using **Kotlin**, **Jetpack Compose**, **Jetpack Glance AppWidget**, **AndroidX WorkManager** and Android's local storage APIs.

- **Minimum Android:** API 29.
- **Compile / target SDK:** API 35.
- **JDK and JVM target:** 17.
- **Gradle:** use the committed wrapper.

To build and test locally:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. See the [development guide](docs/development/BUILDING-AND-TESTING.md) for SDK setup and release-signing notes.

## Project status and limitations

- COCwidgt is currently **Android-only**.
- Village state comes from manual exports; there is no live game synchronization.
- Catalogue coverage is maintained and may not include every game entity or future game update.
- Unknown catalogue IDs are retained and shown with neutral fallback presentation.
- Widget rendering and refresh timing depend on Android and the installed launcher.
- The TH19 export representation for selecting a Town Hall visual variant has not yet been validated against a physical TH19 export sample.

## Credits, assets and licensing

Selected Clash of Clans artwork is bundled from the community-maintained [ClashKingAssets](https://github.com/ClashKingInc/ClashKingAssets) project. TH19 additions are attributed to the audited upstream revision documented in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

The corresponding GPL-3.0 license text is included at [LICENSES/GPL-3.0-ClashKingAssets.txt](LICENSES/GPL-3.0-ClashKingAssets.txt). See [third-party notices](THIRD_PARTY_NOTICES.md) for source mapping and dependency notes.

Clash of Clans, Supercell's names, marks and game content remain the property of their respective owners.

## Disclaimer

COCwidgt is an unofficial fan-made project. It is not affiliated with, endorsed by, sponsored by or specifically approved by Supercell. Use of Supercell game content is subject to the [Supercell Fan Content Policy](https://supercell.com/en/fan-content-policy/).

---

<div align="center">
<sub>Made by <a href="https://github.com/stokonn">stokonn</a>.</sub>
</div>
