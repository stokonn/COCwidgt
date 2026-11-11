package dev.stokonn.cocwidgt.data

/**
 * Visual grouping of upgrades shown as one section of the widget's single vertical list.
 *
 * The categories are derived from the actual upgrade types present in a Clash export
 * (see [VillageExportParser]) - not from arbitrary row slicing:
 *  - [BUILDINGS] - defence/resource/army buildings and traps, including Builder Base
 *    buildings and traps
 *  - [HEROES] - heroes and hero equipment
 *  - [TROOPS] - troop upgrades that are not part of the home laboratory research
 *    (Builder Base Star Laboratory research and any troop row outside a lab section)
 *  - [PETS] - Pet House upgrades and pet upgrades
 *  - [LABORATORY] - home laboratory research (troop, spell and siege research from the
 *    `units`/`spells`/`siege_machines` sections and any lab-keyed section) plus spell and
 *    siege entities wherever they appear
 *  - [GOBLIN] - Goblin-flagged upgrades (Goblin Builder / Goblin Researcher, including
 *    goblin lab rows) and helper entities (Builder's Apprentice, Lab Assistant, Prospector);
 *    never unrelated Builder Base content, which never carries the goblin flag
 *  - [OTHER] - anything that does not fit the categories above (kept visible, never dropped)
 */
enum class UpgradeCategory(val label: String, val shortLabel: String, val order: Int) {
    BUILDINGS("Buildings", "Build", 10),
    HEROES("Heroes", "Heroes", 20),
    TROOPS("Troops", "Troops", 30),
    PETS("Pets", "Pets", 40),
    LABORATORY("Laboratory", "Lab", 50),
    GOBLIN("Goblin Workers", "Goblin", 60),
    OTHER("Other", "Other", 90),
    ;

    companion object {
        /** Categories in their canonical widget display order. */
        val displayOrder: List<UpgradeCategory> = entries.sortedBy { it.order }

        /** Section title the parser gives home laboratory research (also for lab-keyed unknown keys). */
        private const val LABORATORY_SECTION = "Laboratory"

        /** Section title the parser gives the `equipment` / `hero_equipment` arrays. */
        private const val EQUIPMENT_SECTION = "Equipment"

        /**
         * Category of a parsed upgrade job. Precedence: goblin flag -> helper entity ->
         * Pet House -> laboratory section -> equipment section -> entity category.
         * Unknown section keys and unknown-but-valid rows land in [OTHER] instead of being
         * dropped.
         */
        fun of(job: VillageJob): UpgradeCategory {
            val entityCategory = GameCatalogue.resolve(job.dataId).category
            return when {
                job.goblin == true -> GOBLIN
                entityCategory == GameCategory.HELPER -> GOBLIN
                job.dataId == 1000068L -> PETS // Pet House (Tome of Pets)
                job.section == LABORATORY_SECTION -> LABORATORY
                job.section == EQUIPMENT_SECTION -> HEROES
                else -> when (entityCategory) {
                    GameCategory.HERO -> HEROES
                    GameCategory.PET -> PETS
                    GameCategory.TROOP -> TROOPS
                    GameCategory.SPELL, GameCategory.SIEGE -> LABORATORY
                    GameCategory.HELPER -> GOBLIN
                    GameCategory.BUILDING, GameCategory.DEFENCE, GameCategory.TRAP -> BUILDINGS
                    GameCategory.UNKNOWN -> OTHER
                }
            }
        }
    }
}
