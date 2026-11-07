> **PRE-RELEASE** — COCwidgt 0.4.0 is an unofficial Clash of Clans companion.

## Install

Download `app-release.apk`, open it on your Android device, and allow installation from the source you used to download it if Android asks. Add the COCwidgt widget from your launcher's widget picker. The APK is signed by the project's release key; compare its SHA-256 with `SHA256SUMS.txt` before installing.

## Changes

- Added a warm Clash-inspired widget palette, responsive Glance layouts, upgrade sections, export freshness, and profile sync status.
- Added an ID-backed game catalogue for supported buildings, heroes, troops, spells, pets and Builder Base entities.
- Bundled 166 ClashKingAssets WebP artwork resources and mapped supported game entity IDs to matching widget artwork. The selected fan assets are credited to ClashKing community contributors under GPL-3.0; the license is included with the app sources.
- Corrected village export timer semantics: `timer` is remaining seconds at the export timestamp.
- Removed guessed item labels and unsupported progress percentages. Unknown IDs are explicit.
- Kept profile requests on the official API and encrypted API token storage in Android Keystore.

## Limitations

- This is a prerelease and needs device/launcher verification.
- Artwork is provided for the supported catalogue subset; entities without a matching resource use a neutral fallback. The bundled ClashKingAssets fan artwork is not official Supercell artwork or an endorsement. Attribution and license details are in [THIRD_PARTY_NOTICES.md](../../THIRD_PARTY_NOTICES.md).
- The catalogue is a maintained subset. Unknown export IDs remain visible as unknown IDs.
- Exports are manual snapshots. The official API does not expose live building or research timers.
- Total upgrade durations are not present in exports, so progress bars are omitted unless a valid total duration is available.
- API requests require the caller's public egress IP to be allowlisted by Supercell.

The release workflow runs unit tests, lint, debug and signed release builds before publishing. Download verification is performed by the workflow after upload.
