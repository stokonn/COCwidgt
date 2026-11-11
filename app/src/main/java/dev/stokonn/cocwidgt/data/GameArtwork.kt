package dev.stokonn.cocwidgt.data

/**
 * Compile-time mapping to the licensed artwork resources bundled with the app.
 *
 * Artwork is resolved by entity identity **and** level: buildings and traps have a
 * distinct image per level (see [GameArtworkLevels]), while heroes, troops, spells and
 * pets use a single level-independent portrait.
 */
object GameArtwork {
    /** Artwork for callers that have no level information (lowest bundled level). */
    fun drawableResId(dataId: Long): Int? = drawableResId(dataId, level = 1)

    /**
     * Artwork for [dataId] at [level].
     *
     * Resolution order:
     * 1. the exact level, when bundled;
     * 2. otherwise the highest bundled level below [level];
     * 3. otherwise the lowest bundled level (level reported below the first artwork);
     * 4. level-independent portraits are returned for any level.
     *
     * Returns null only when no artwork is bundled for the entity at all - the caller
     * shows a neutral placeholder in that case.
     */
    fun drawableResId(dataId: Long, level: Int): Int? {
        GameArtworkLevels.portraitArtwork[dataId]?.let { return it }
        val levels = GameArtworkLevels.levelArtwork[dataId] ?: return null
        levels[level]?.let { return it }
        val atOrBelow = levels.keys.filter { it <= level }.maxOrNull()
        if (atOrBelow != null) return levels[atOrBelow]
        return levels[levels.keys.min()]
    }
}
