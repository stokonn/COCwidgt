# Architecture overview

COCwidgt is a single-module Android application. The current project is not a Kotlin Multiplatform application and does not include an iOS target.

## Technology

| Area | Implementation |
| --- | --- |
| Language | Kotlin |
| Android application UI | Jetpack Compose |
| Home-screen widget | Jetpack Glance AppWidget |
| Background scheduling | AndroidX WorkManager |
| Local persistence | Android SharedPreferences |
| JSON parsing | Android JSON APIs and `org.json` |
| Network profile lookup | `HttpURLConnection` over HTTPS |
| Minimum Android version | Android 10 / API 29 |
| Compile and target SDK | API 35 |
| Java/Kotlin bytecode target | JVM 17 |

Plugin versions are declared in the root `build.gradle.kts`; application dependencies and Android configuration are in `app/build.gradle.kts`.

## Main source areas

```text
app/src/main/java/dev/stokonn/cocwidgt/
├── MainActivity.kt
├── data/
│   ├── AccountStore.kt
│   ├── ClashApi.kt
│   ├── GameCatalogue.kt
│   ├── PlayerIdentity.kt
│   ├── PlayerNameCache.kt
│   ├── UpgradeCategory.kt
│   └── VillageExport.kt
├── security/
│   ├── DeveloperApiKeyStore.kt
│   ├── LegacyCredentialCleanup.kt
│   └── TokenStore.kt
├── sync/
│   ├── SyncWorker.kt
│   └── WidgetRefreshWorker.kt
└── widget/
    ├── CocWidget.kt
    ├── WidgetAppearance.kt
    ├── WidgetConfigurationActivity.kt
    ├── WidgetContentPlanner.kt
    └── WidgetFit.kt
```

This is a guide to the main responsibilities, not an exhaustive list of every resource or test file.

## Data flow

### Village import

1. The user copies a village JSON export from Clash of Clans.
2. The app reads the supplied JSON and parses the supported sections.
3. Player identity is normalized and associated with the imported snapshot.
4. Export IDs are mapped through the maintained game catalogue.
5. The snapshot and upgrade rows are stored locally in the account-specific village preferences.
6. The widget reads the selected account's snapshot and renders the planned content.

The village export is the source of village levels and upgrade timers. The app does not obtain this information from the public player-profile API.

### Player profile lookup

The optional Developer API key is used by `ClashApi` to request `GET /v1/players/{playerTag}` from the official Clash of Clans API. The response parser verifies that the returned Player Tag matches the requested tag before accepting the profile. The profile lookup is optional and does not gate JSON import.

### Account storage

`AccountStore` manages the account directory, active Player Tag, local labels and manual names. The normalized Player Tag is the stable key for account-specific village snapshots, player-name cache and profile data.

On first launch after the multi-account update, migration copies matching legacy single-account data into account-specific stores. Original legacy preference entries are intentionally retained by the migration.

### Widget configuration and rendering

Each widget instance has its own account assignment. `WidgetConfigurationActivity` presents the available accounts and stores the selected tag for that widget ID. `CocWidget` reads the selected account's local snapshot and uses the widget content planner and fit logic to decide what can be shown at the available dimensions.

### Background refresh

`WidgetRefreshWorker` requests a re-render of all COCwidgt widgets. It does not call the Clash of Clans API or import a new village. WorkManager periodic work is scheduled with a 15-minute interval, subject to Android's background scheduling rules.

## Data and security boundaries

- Village export JSON is stored locally on the device.
- The app does not upload the village export to Supercell.
- The optional Developer API key is used for the public profile lookup.
- The API key is stored using the app's Android Keystore-backed encrypted storage implementation.
- Network requests use HTTPS; cleartext traffic is disabled in the manifest.
- Android backup is disabled for the application.
- Never commit API keys, signing credentials, keystores or generated secrets.

## Catalogue and third-party artwork

`GameCatalogue` maps known export IDs to supported names, categories, upgradeability and artwork keys. Unknown IDs are retained and diagnosed rather than silently dropped or assigned invented names.

Selected artwork is bundled from ClashKingAssets. Source mapping, attribution and license details are recorded in [THIRD_PARTY_NOTICES.md](../../THIRD_PARTY_NOTICES.md) and the included GPL-3.0 license text under `LICENSES/`.

## Known boundaries

- A village export is a manual snapshot, not a live game connection.
- Profile lookup does not provide village upgrade state.
- Widget rendering and scheduling are controlled partly by Android and the launcher.
- The app currently targets Android only.
