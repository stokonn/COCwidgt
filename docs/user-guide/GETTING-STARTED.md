# Getting started

COCwidgt is an Android home-screen widget for viewing village upgrades from a manually exported Clash of Clans village snapshot. It does not connect to the game to read live village data.

## Requirements

- Android 10 (API 29) or newer.
- A Clash of Clans village export in JSON format.
- A compatible Android launcher that supports home-screen widgets.

The optional player-name lookup requires a Supercell Developer API key. The core JSON import and widget display do not.

## Install

1. Open the [COCwidgt v0.6.0 GitHub prerelease](https://github.com/stokonn/COCwidgt/releases/tag/v0.6.0).
2. Download `cocw0.6.0.apk`.
3. Verify the downloaded file against the SHA-256 value published with the release when practical.
4. Install the APK on an Android 10+ device.

This is a prerelease build, not a stable Play Store release. Android may ask you to confirm that you trust the source used to install the package. Only install builds obtained from the project's official repository or release page.

## Export your village

In Clash of Clans:

1. Open **Settings**.
2. Find **More Settings → Data Export**.
3. Use the game's **Copy** action to copy the village JSON.

Labels and menu placement can change between game versions. The export is a snapshot of the village at the time it was generated.

## Import the JSON

1. Open COCwidgt.
2. Choose **Paste & import village JSON**.
3. Grant clipboard access if Android requests it.
4. Review the imported Player Tag and import status.
5. Add the widget to the home screen.

Import a fresh export after gameplay to update village levels and remaining upgrade times. Importing the same old export does not retrieve new game state.

## Add the home-screen widget

1. Long-press an empty area of the home screen.
2. Open the launcher's widget picker.
3. Find COCwidgt and add a widget instance.
4. If prompted, select the account that this widget should display.
5. Resize the widget with the launcher's handles.

The amount of information shown depends on the width and height allocated by the launcher. Different launchers may measure and render widgets differently.

## Understand upgrade countdowns

The export provides a remaining duration and export timestamp. COCwidgt calculates an expected finish time from those values and updates the displayed countdown.

The export does not provide the original total duration for every running upgrade. COCwidgt therefore does not invent a completion percentage from assumed upgrade durations. A progress indicator can use the first time the app observed a particular running upgrade.

- Re-importing a matching running upgrade can preserve its first observed start time.
- A changed timer or a different Player Tag starts a new observation window.
- If the first import happens after an upgrade started, its true start time is unknown.
- Missing or invalid timestamps prevent a meaningful progress indicator.
- An old export may contain upgrades whose timers have already elapsed; these are displayed as ready.

## Optional player-name lookup

The optional Developer API key is used for a public player-profile lookup through the official Clash of Clans API. It is not required to import village JSON or display the widget.

Supercell API keys can be restricted to allowed public IP addresses. A lookup may fail after a network or public-IP change. Village data already imported remains available locally.

See [Accounts and widgets](ACCOUNTS-AND-WIDGETS.md) for multi-account behavior and [FAQ and troubleshooting](FAQ.md) for common issues.
