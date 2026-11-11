# COCwidgt

COCwidgt is an unofficial Android home-screen companion for Clash of Clans. Version **0.5.1** (`versionCode` 6) uses local village export data. It is not affiliated with Supercell.

## What it shows

- A resizable Glance widget with a warm dark brown surface, player header, category labels, artwork, upgrade levels, countdowns and progress bars. It uses the current launcher-reported dimensions to show more rows as the widget grows, within one vertical list.
- Player Tag and Town Hall from the imported export. Player names are optional in exports; a matching official API profile name is cached locally, with manual name entry as an override and Player Tag as the fallback.
- Active builder, laboratory, hero equipment, pet, goblin and Builder Base upgrades with local countdowns. Goblin Builder / Goblin Researcher rows and helper entities (Builder's Apprentice, Lab Assistant, Prospector) form their own **Goblin / Builder** column.
- Progress bars are the green fill of each rounded row and derive from real timestamps; the bottom status shows the local export age (with a stale marker after 24 hours).
- Import parsing by stable game data ID: every top-level array of the export is read (not a fixed whitelist), and IDs without a catalogue entry are labelled explicitly as `Unknown game item (ID …)` instead of being dropped.
- The optional Developer API key is used only for the official player profile name request. It is separate from the in-game Data Export ownership token, which is never collected or verified.
- Periodic widget refresh (15 minutes) so countdowns and progress bars keep advancing without per-second updates. WorkManager refreshes the local widget state independently of app launches.

The village export supplies levels and remaining upgrade timers. An export timer is the **remaining seconds at the export timestamp**; the app calculates the finish instant as `timestamp + timer`. Old exports therefore show elapsed timers as ready. The export does not include each upgrade's original total duration, so COCwidgt does not show a made-up percentage of a guessed total: progress bars run from the **first moment the running timer was observed** (the first import that saw the upgrade) to the computed finish instant. Re-importing the same running upgrade keeps that first observation, so progress never jumps backwards; a genuinely changed timer or a different player tag starts a fresh window. When timestamps are missing or invalid, no progress bar is shown.

## Import village data

1. In Clash of Clans, open **Settings → More Settings → Data Export → Copy**.
2. Open COCwidgt and tap **Paste & import village JSON**.
3. Allow clipboard access if Android asks. Check the tag and import status.
4. Add or resize the COCwidgt widget on your launcher.

Import again after gameplay to refresh building levels and timers. Village JSON is parsed and stored on the device and is never sent to Supercell. If a Developer API key is configured, the imported Player Tag is used for a separate official profile name lookup; lookup failure does not affect import or widget data.

## Developer API player name

The optional **Developer API key** setting is encrypted with Android Keystore and sent only to `api.clashofclans.com` over HTTPS to request `GET /v1/players/{playerTag}`. Supercell keys are restricted to allowed public IP addresses, so requests from changing mobile networks may fail. The key is not the in-game Data Export ownership token. No ownership verification is performed. A successful name is cached with its Player Tag; cached names for other tags are not displayed, and village data remains available offline.

## Catalogue and visual assets

`GameCatalogue.kt` maps supported export IDs to their known English game names, categories and artwork resources. The widget shows selected building, defence, trap, troop, siege, hero, pet and spell fan assets from ClashKingAssets. Unknown IDs are named explicitly and use a neutral fallback; known entities without a matching licensed asset also use that fallback. Export-provided labels do not override catalogue names. The catalogue is a maintained subset, so unsupported IDs are never guessed.

Names and game identifiers are used to identify game entities. Supercell marks and game content remain the property of Supercell. COCwidgt is an unofficial fan app and is not endorsed by Supercell. See [Supercell Fan Content Policy](https://supercell.com/en/fan-content-policy/). Mapping sources, asset attribution and license details are in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). The sample export structure and timer behavior are described in public village export guides such as [ClashClock's import guide](https://clashclock.com/guides/user-guide/import-your-village) and [data and timer notes](https://clashclock.com/guides/how-it-works); the implementation is not affiliated with those projects.

## Build and checks

Requirements: JDK 17, Android SDK 35, Gradle 9.6.0. From the project root:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleRelease
```

Android Studio can also open and build the project.

## Signed release

The v0.4.0 release workflow is retained for that existing release. Signed v0.5.0 builds use the same GitHub Actions secrets as v0.4.0:

- `RELEASE_KEYSTORE_BASE64`: base64-encoded Android release keystore.
- `RELEASE_STORE_PASSWORD`: keystore password.
- `RELEASE_KEY_ALIAS`: signing key alias.
- `RELEASE_KEY_PASSWORD`: signing key password.

The v0.5.0 workflow fails before the build if any secret is missing, verifies the APK signature with `apksigner`, and uploads the signed `cocw0.5.0.apk` as a build artifact for manual verification before the GitHub Release is created. Keep the keystore and passwords out of Git and workflow logs. For local release builds, provide the same values as environment variables (`RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`).

## Known limitations

- The catalogue and artwork set are maintained subsets. Unsupported or unmapped entities remain explicit and use a neutral icon fallback.
- Upgrade totals are not present in the export. Countdowns are accurate, and progress bars use the first-observed start to the absolute finish instant; no percentage is computed against a guessed total duration.
- Progress bars appear only for upgrades first imported while running. An upgrade imported for the first time after it started cannot know its true start, so its bar begins at the first import.
- Widget countdown refresh relies on WorkManager's periodic worker (15 minutes) and the launcher's own update period; exact cadence is launcher-dependent and needs device testing.
- The widget's visual match to the reference design and the absence of overlaps have been verified by measurement-based layout code and unit tests, not yet on a physical device — launcher-specific rendering still needs a final on-device check.
- Export data is a manual snapshot; import again after gameplay to refresh village levels and upgrade timers.
- Official player-name and ownership verification endpoints are not used. The export may omit the name, so manual entry is available.
