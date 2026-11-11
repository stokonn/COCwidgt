COCwidgt 0.5.0 is an unofficial Clash of Clans companion. The APK is signed by the project's release key (`cocw0.5.0.apk`, SHA-256 in `SHA256SUMS.txt`).

## Fixes

- **Player nickname**: the widget, the app header and the sync worker now resolve the Player Tag with one canonical chain (saved settings tag → imported export → stored profile), so the API nickname is stored under exactly the tag the header displays. The "Player profile" placeholder now only appears when no valid name exists, and a nickname from another player's profile is never shown.
- **Widget overlaps**: every progress bar and text is fitted to the measured widget width — bars use the column content minus the card padding and trailing gap, long names/level lines are ellipsized, category headings are budgeted with their count and Goblin chips. Compact, medium, wide and maximum sizes are all covered by layout tests.
- **Sync status**: sync failures are attributed to the Player Tag they belong to and surfaced as short labels (offline, rate limit, token/IP rejected, tag mismatch, unreadable token) instead of being hidden; the last successful sync time is always shown. A stored-but-undecryptable API token is reported instead of silently skipping every sync.

## Improvements

- Widget restyled to the reference design: warm dark brown rounded surface, avatar + Player Tag + Town Hall + Sync chip header, green rounded progress rows with icons, names, target levels and countdowns, category sections with counts and "+ Goblin" markers, and a bottom status area with quick chips and last-sync time.
- Local widget re-render (15-minute WorkManager worker, KEEP policy) is separated from API synchronization (its own periodic worker); countdowns and progress bars are recomputed from absolute timestamps on every render, with no per-second update promises.
- Automatic sync now also retries after a stored failure, and shows when the last sync succeeded.

## Verification

- 69 unit tests pass (nickname persistence, API failure attribution, Player Tag validation, layout/fit calculations, long names, countdowns, progress bars, status classification).
- `lintDebug` with 0 errors; debug and signed release APKs build; release APK signature and version (`0.5.0` / code `5`) verified with `apksigner` and `aapt`.

## Limitations

- Export data is a manual snapshot; the official API cannot refresh building levels or live upgrade timers.
- Widget refresh cadence is launcher-dependent (15-minute WorkManager minimum); the visual match and overlap fix were verified by tests and measurements and still need a final check on a physical device.
- API requests require the caller's public egress IP to be allowlisted by Supercell.
