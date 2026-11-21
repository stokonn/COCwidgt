# Accounts and widgets

COCwidgt 0.6.0 supports multiple saved Clash of Clans accounts. A Player Tag is the stable identity used to associate an account with its imported village and cached profile.

## Manage accounts

From the Accounts card in the app, you can:

- Add an account using its Player Tag.
- Rename its local display label.
- Select the active account.
- Remove an account from the local account directory.

The optional Developer API key is used to fetch the public player profile when adding an account. Account labels are local names and do not change the in-game player name.

## Data isolation

Each account's village snapshot, profile details and cached player name are stored separately using the normalized Player Tag as the account key. This prevents one account's imported village or cached profile from being shown as another account's data.

The original single-account installation is migrated on first launch. The migration copies matching legacy village/profile/name data into the account-specific stores and leaves the original preference entries in place as rollback-safe data.

Removing an account removes its directory entry and matching cached account data handled by the app. This is a local operation; it does not delete or modify the Clash of Clans account.

## Select an account per widget

Each newly added widget instance opens the native configuration flow when accounts are available. Choose the saved account that the widget should display.

- Every widget instance stores its own selected Player Tag.
- Changing the active account in the app does not silently change the account assigned to an existing widget.
- If the selected account is removed, the widget falls back to the current active account.
- If no account is available, the configuration flow opens the app so an account can be added.

This allows multiple widgets on one home screen to show different saved villages.

## Refresh behavior

Widget countdowns are calculated from stored absolute finish times. WorkManager periodically re-renders widgets; Android controls when background work actually runs.

The app requests a 15-minute periodic refresh, which is WorkManager's minimum periodic interval. This is not a guarantee of an exact refresh every 15 minutes and is not a per-second live update. Launcher and operating-system policies may delay rendering.

A widget refresh only updates the presentation of data already stored on the device. To reflect new levels or upgrade timers from the game, export and import the village again.

## Troubleshooting

**A widget shows the wrong village**

Open the widget configuration flow if available, or remove and add that widget instance again and choose the intended account. Check the Player Tag shown in the app before importing.

**A widget falls back after account removal**

This is expected. A widget cannot continue to display account data after its selected account has been removed from the local directory.

**The countdown is not changing every second**

Android home-screen widgets are not guaranteed per-second rendering. COCwidgt uses scheduled background refresh and launcher updates.

**The widget does not show recent gameplay**

Import a new Data Export snapshot. Background refresh does not fetch village state from the game.
