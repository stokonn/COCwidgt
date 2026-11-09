package dev.stokonn.cocwidgt.data

/**
 * Coarse widget grouping for active upgrades. Derived from the stable game data ID so the
 * same upgrade always lands in the same category regardless of which export section it came from.
 */
enum class UpgradeCategory(val label: String) {
    BUILDINGS("Buildings"),
    HEROES("Heroes"),
    TROOPS("Troops"),
    OTHER("Other");

    companion object {
        val displayOrder: List<UpgradeCategory> = listOf(BUILDINGS, HEROES, TROOPS, OTHER)

        fun of(category: GameCategory): UpgradeCategory = when (category) {
            GameCategory.BUILDING, GameCategory.DEFENCE, GameCategory.TRAP -> BUILDINGS
            GameCategory.HERO -> HEROES
            GameCategory.TROOP, GameCategory.SIEGE -> TROOPS
            GameCategory.SPELL, GameCategory.PET, GameCategory.UNKNOWN -> OTHER
        }

        fun of(job: VillageJob): UpgradeCategory = of(GameCatalogue.resolve(job.dataId).category)
    }
}
