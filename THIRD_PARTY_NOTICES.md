# Third-party notices

## Clash of Clans names and identifiers

Entity names and numeric export IDs in `GameCatalogue.kt` describe Clash of Clans game content and remain associated with Supercell. The mapping was cross-checked against the [community village export mapping by rahulkhatri137](https://gist.github.com/rahulkhatri137/a8449943df45100c5f1e1359cd9ec67a) and [cerealkillahh's building ID table](https://github.com/cerealkillahh/clash-of-clans-data/blob/main/data/buildings.json). The gist does not state a license; COCwidgt does not bundle or redistribute either source file.

### ClashKingAssets artwork

The WebP files in `app/src/main/res/drawable-nodpi/` are selected Clash of Clans fan assets from [ClashKingInc/ClashKingAssets](https://github.com/ClashKingInc/ClashKingAssets). The upstream repository identifies these assets as GPL-3.0 and requests credit. The complete GPL-3.0 text is included in [`LICENSES/GPL-3.0-ClashKingAssets.txt`](LICENSES/GPL-3.0-ClashKingAssets.txt). File names follow upstream asset paths, and catalogue IDs map to these resources. Credit: ClashKing community contributors. These fan assets are not official Supercell artwork or endorsements. Entities without a matching bundled file use an explicit neutral fallback.

Clash of Clans and its characters, names, and marks are owned by Supercell. COCwidgt is an unofficial fan project and is not endorsed by Supercell. See [Supercell Fan Content Policy](https://supercell.com/en/fan-content-policy/).

## Runtime and test libraries

AndroidX, Jetpack Compose, Jetpack Glance, AndroidX WorkManager, JUnit, and JSON-java are consumed from their declared Maven dependencies. Their respective upstream licenses apply; this project does not redistribute their source code.
