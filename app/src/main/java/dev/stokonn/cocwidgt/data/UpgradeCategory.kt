package dev.stokonn.cocwidgt.data

/**
 * Visual grouping of upgrades shown as one column block on the widget.
 *
 * The categories are derived from the actual upgrade types present in a Clash export
 * (see [VillageExportParser]) - not from arbitrary row slicing:
 *  - [BUILDINGS] - defence/resource/army buildings and traps
 *  - [HEROES] - heroes and hero equipment
 *  - [TROOPS] - laboratory research (troops, spells, siege machines)
 *  - [PETS] - pet house upgrades (building and pets alike)
 *  - [GOBLIN] - Goblin Builder / Goblin Researcher upgrades and helper entities
 *    (Builder's Apprentice, Lab Assistant, Prospector)
 *  - [OTHER] - anything that does not fit the categories above (kept visible, never dropped)
 */
enum class UpgradeCategory(val label: String, val order: Int) {
    BUILDINGS("Buildings & Traps", 10),
    HEROES("Heroes & Equipment", 20),
    TROOPS("Troops & Research", 30),
    PETS("Pets", 40),
    GOBLIN("Goblin / Builder", 50),
    OTHER("Other", 90),
    ;

    companion object {
        /** Categories in their canonical widget display order. */
        val displayOrder: List<UpgradeCategory> = entries.sortedBy { it.order }

        /** Category of a parsed upgrade job. Precedence: goblin flag -> Pet House
         *  -> game category. Unknown section keys land in [OTHER] instead of being dropped. */
        fun of(job: VillageJob): UpgradeCategory = when {
            job.goblin == true -> GOBLIN
            job.dataId == 1000068L -> PETS // Pet House (Tome of Pets)
            else -> when (GameCatalogue.resolve(job.dataId).category) {
                GameCategory.HERO -> HEROES
                GameCategory.PET -> PETS
                GameCategory.SPELL -> TROOPS
                GameCategory.TROOP -> TROOPS
                GameCategory.SIEGE -> TROOPS
                GameCategory.HELPER -> GOBLIN
                GameCategory.BUILDING -> BUILDINGS
                GameCategory.DEFENCE -> BUILDINGS
                GameCategory.TRAP -> BUILDINGS
                GameCategory.UNKNOWN -> OTHER
            }
        }
    }
}
