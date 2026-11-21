# FAQ and troubleshooting

## Does COCwidgt connect to my Clash of Clans account?

No. Village data comes from a JSON snapshot that you export in Clash of Clans and import into COCwidgt. The optional public player-profile lookup is separate and does not read village upgrades.

## Do I need a Developer API key?

No. The key is optional and is used only to look up public player profile information such as the player name. Importing and displaying an already imported village does not require it.

## Why is the player name missing?

The export may not contain a usable player name, and the optional profile lookup may not have succeeded. You can use a local manual name where supported, or continue using the Player Tag as the account identity.

## Why did profile lookup fail?

Check that the Player Tag is valid, the key is configured correctly, the key is allowed to use your current public IP address, and the device has network access. API rate limits or service errors can also temporarily prevent lookup. Imported village data remains local.

## Why are my latest upgrades missing?

The app displays the last imported snapshot. Export and import your village again after gameplay.

## Why does the countdown not update every second?

Android widgets are not continuously rendered. COCwidgt schedules background refresh through WorkManager, but Android and launcher policies determine when work and redraws actually happen.

## Why does the widget show a different account?

Each widget instance has its own saved account assignment. Changing the active account in the app does not automatically change existing widgets. Reconfigure the widget instance or remove and add it again, then select the intended account.

## Why did a widget change accounts after I removed one?

A widget cannot keep using an account that no longer exists in the local account directory. It falls back to the current active account.

## Why is an item shown as unknown?

The export contains an entity ID that the current catalogue does not map to a supported name and artwork. Unknown rows are retained rather than silently discarded or assigned guessed names. A future catalogue update may add a verified mapping.

## Why is there no progress percentage?

The export provides remaining time but not the original total duration for every upgrade. A trustworthy percentage cannot be derived from remaining time alone. The app can show progress only from the first observation window it has recorded.

## Is the app an official Supercell product?

No. COCwidgt is an unofficial fan-made project and is not affiliated with or endorsed by Supercell. See the [Supercell Fan Content Policy](https://supercell.com/en/fan-content-policy/).
