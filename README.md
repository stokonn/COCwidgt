# COCwidgt

Android home-screen widget for Clash of Clans player data.

## Status

Project initialization. The first milestone is a native Android widget with a configurable Player Tag. API integration will follow, with unsupported village details clearly marked rather than fabricated.

## Planned stack

- Kotlin
- Jetpack Glance for the home-screen widget
- Jetpack Compose for configuration
- Room for cached player data
- WorkManager for periodic synchronization
- A server-side API proxy so the Supercell API token is never embedded in the APK

## Data limitations

The official Clash of Clans API does not expose all village details or active building-upgrade timers. The widget will display only data actually provided by the API.

## Security

Never enter Supercell ID credentials into this app. The Player Tag is not a password.
