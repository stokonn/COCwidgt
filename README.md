# COCwidgt

Native Android home-screen widget for Clash of Clans player data.

**Current version:** 0.2.0

## Features

- Kotlin + Jetpack Compose settings screen
- Resizable Jetpack Glance home-screen widget
- Player Tag configuration
- Official Clash of Clans API player-profile request
- Player name, Town Hall, trophies, XP and hero levels
- Manual sync and periodic background sync (15-minute minimum interval)
- API token encrypted at rest using Android Keystore AES-GCM

## API setup

1. Create an API key in the official Clash of Clans developer portal.
2. Add the public egress IP address of the network making requests to the key's IP allowlist.
3. Install the app, enter your Player Tag and API token, then save.
4. Tap **Sync now** and add the COCwidgt widget to the home screen.

The API token is entered on-device. It is not committed to this repository or embedded in source code.

**Important:** Supercell API IP allowlisting can prevent requests from working over a changing mobile-data IP. Test first on Wi-Fi whose public IP is allowlisted. For reliable access from anywhere, use a backend with a static egress IP.

## API limitations

The official API does not expose all village building levels, active upgrade timers, or every in-game detail. The widget only displays fields returned by the player endpoint. It does not scrape the game or access Supercell ID.

## Build

Requirements: JDK 17, Android SDK 35, Gradle 8.9+.

Open the project in Android Studio or run:

```bash
gradle assembleDebug
```

## Security

- Never enter your Supercell ID password.
- The API token is encrypted at rest using an AES key held by Android Keystore.
- Keep the API key IP allowlist restricted to the network that needs it.
