# COCwidgt 0.6.0

- Added verified Town Hall 19 catalogue data from ClashKingAssets commit `6212dddb13ec5a0f13b5314361e49fc4a4325a61`, including new buildings, all increased building/trap/spell/troop/hero/pet/Guardian caps, and non-upgradeable Guardian Shrine handling.
- Bundled the TH19 building/trap levels, Spiky Tower, Firecracker Tower, all Squad Barracks levels, Town Hall 19 and its three variants, Heroic Spell, new Squad troop icons, upgraded troop/hero icons, and Guardian portraits.
- Timed imports for non-upgradeable entities are retained and diagnosed; unknown IDs and levels beyond the catalogue cap also remain visible with diagnostics.
- Widget rows are planned against the launcher-reported dimensions and active upgrade count. Tiny widgets hide the redundant compact status line to preserve an upgrade row.
- JSON imports accept an optional UTF-8 byte order mark.
- Imports report unsupported game IDs and catalogue level overruns without discarding the affected rows.
- Android version code is 7.

Guardian Shrine is catalogued with no upgrade levels and has no building artwork in the upstream commit. Town Hall variant artwork is mapped by the upstream variant IDs; the export representation of the selected variant is not yet validated with a physical TH19 export sample.
