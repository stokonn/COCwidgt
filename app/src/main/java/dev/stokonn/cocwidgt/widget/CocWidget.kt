package dev.stokonn.cocwidgt.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.stokonn.cocwidgt.MainActivity
import dev.stokonn.cocwidgt.data.GameArtwork
import dev.stokonn.cocwidgt.data.PlayerIdentity
import dev.stokonn.cocwidgt.data.AccountStore
import dev.stokonn.cocwidgt.data.WidgetAccountStore
import dev.stokonn.cocwidgt.data.PlayerProfileCodec
import dev.stokonn.cocwidgt.data.PlayerNameCache
import dev.stokonn.cocwidgt.data.StoredProfile
import dev.stokonn.cocwidgt.data.ExportStatus
import dev.stokonn.cocwidgt.data.VillageJob
import dev.stokonn.cocwidgt.data.VillageSnapshot
import dev.stokonn.cocwidgt.sync.WidgetRefreshWorker

private val Background = Color(0xFF281D17)
private val Frame = Color(0xFF28190F)
private val Track = Color(0xFF2F2212)
private val Gold = Color(0xFFFFD36B)
private val Cream = Color(0xFFFFF2D8)
private val Muted = Color(0xFFCDB9A2)
private val Green = Color(0xFFA0D362)
private val Alert = Color(0xFFFF8A78)

// The green progress fill sits inside each dark track; the planner and renderer share these slots.
private const val ROW_HEIGHT_DP = 34
private const val ROW_MARGIN_DP = 2
private const val COMPACT_ROW_HEIGHT_DP = 24
private const val COMPACT_ROW_MARGIN_DP = 2
private const val STANDARD_ROW_SLOT_DP = ROW_HEIGHT_DP + ROW_MARGIN_DP
private const val COMPACT_ROW_SLOT_DP = COMPACT_ROW_HEIGHT_DP + COMPACT_ROW_MARGIN_DP
private const val COMPACT_FOOTER_DP = 20
private const val STANDARD_FOOTER_DP = 24

/** One rendered line of the scrolling list: a category heading or an upgrade row. */
private sealed class ListEntry(val id: Long) {
    class Heading(id: Long, val group: UpgradeGroup) : ListEntry(id)
    class Upgrade(id: Long, val job: VillageJob) : ListEntry(id)
}

/**
 * Exact-size widget following the target's warm brown panel, compact player/Town Hall header,
 * separate Builders/Lab/Pets sections, green progress rows and a persistent export/status footer.
 *
 * There is exactly ONE vertical list at every widget size. The row planner uses the exact
 * launcher-reported size to fit additional rows into taller widgets, while the header and
 * import status remain visible. Countdown and progress use absolute timestamps,
 * and periodic re-renders are scheduled through WorkManager / the system update period.
 *
 * The panel and frame honour the per-widget transparency setting (see [WidgetAppearance]);
 * text, icons and progress rows keep their own fully opaque colours.
 */
class CocWidget : GlanceAppWidget() {
    // The content count and row density depend on the launcher-reported viewport. Exact asks
    // Glance to recompose on size changes instead of selecting one of two fixed buckets.
    override val sizeMode = SizeMode.Exact

    // `AppWidgetId` is only flagged as restricted by lint (it is a normal public Glance
    // type used here to read this widget's own transparency override); there is no
    // unrestricted accessor in Glance 1.1.1.
    @android.annotation.SuppressLint("RestrictedApi")
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val ctx = LocalContext.current
            val size = LocalSize.current
            val widthDp = size.width.value.toInt()
            val heightDp = size.height.value.toInt()
            val settings = ctx.getSharedPreferences("cocwidgt", Context.MODE_PRIVATE)
            val accounts = AccountStore.migrate(ctx)
            val widgetId = (id as? AppWidgetId)?.appWidgetId
            val widgetPrefs = ctx.getSharedPreferences("cocwidgt_widget_accounts", Context.MODE_PRIVATE)
            val accountTag = widgetId?.let { WidgetAccountStore.accountTag(widgetPrefs, it, accounts.activeTag(), accounts.accounts().map { a -> a.tag }.toSet()) }
                ?: accounts.activeTag()
            if (widgetId != null && accountTag != null && widgetPrefs.getString(WidgetAccountStore.key(widgetId), null) == null) {
                WidgetAccountStore.assign(widgetPrefs, widgetId, accountTag)
            }
            val configuredTag = accountTag
            val village = configuredTag?.let { VillageSnapshot.load(ctx, it) }
            val header = PlayerIdentity.headerState(
                exportTag = village?.tag,
                exportName = village?.name,
                exportTownHall = village?.townHall ?: 0,
                settingsTag = configuredTag,
                profile = configuredTag?.let { PlayerProfileCodec.decode(ctx.getSharedPreferences(PlayerProfileCodec.prefsName(it), Context.MODE_PRIVATE).all) }
                    ?: StoredProfile(null, null, -1, 0L, null),
            )
            val cachedName = PlayerNameCache.decode(ctx.getSharedPreferences(configuredTag?.let(PlayerNameCache::prefsName) ?: "cocwidgt_player_name", Context.MODE_PRIVATE).all)
            val displayName = PlayerIdentity.displayName(
                header.expectedTag, cachedName?.tag, cachedName?.name, village?.tag, village?.name,
                configuredTag, accounts.manualName(configuredTag)
            )
            val now = System.currentTimeMillis()
            val selection = WidgetContentPlanner.select(village?.jobs.orEmpty(), widthDp, heightDp, now)
            val compact = selection.compact
            val contentPadding = if (compact) 6 else 8
            val contentWidth = widthDp - (contentPadding + 2) * 2

            // Per-widget background transparency (global default when this widget has no
            // override). Applied to the root surface only — see WidgetAppearance.
            val appWidgetId = widgetId
            val transparency = WidgetSettingsStore(
                ctx.getSharedPreferences(WidgetSettingsCodec.PREFS, Context.MODE_PRIVATE)
            ).transparency(appWidgetId)
            val surface = Color(WidgetAppearance.backgroundArgb(transparency))
            val frameSurface = Frame.copy(alpha = (100 - transparency) / 100f)

            val statusText = if (selection.hiddenCount > 0) "${selection.hiddenCount} more in app" else "${selection.items.size} active upgrades"

            // Keep the title within the measured width after the Town Hall label and Sync chip.
            val headerTextWidth = (contentWidth - 108).coerceAtLeast(0)

            // Deterministic item ids for the list. Every entry gets its own id so duplicates
            // can never collide when the launcher re-renders after a resize.
            val entries = buildList {
                var nextId = 1L
                selection.groups.forEach { group ->
                    if (selection.showSectionHeadings) add(ListEntry.Heading(nextId++, group))
                    group.items.forEach { job -> add(ListEntry.Upgrade(nextId++, job)) }
                }
            }

            Column(
                GlanceModifier.fillMaxSize().background(frameSurface).cornerRadius(20.dp)
                    .padding(2.dp).background(surface).cornerRadius(18.dp).padding(contentPadding.dp),
                verticalAlignment = Alignment.Vertical.Top
            ) {
                PlayerHeader(displayName, header.townHall, compact, headerTextWidth)
                if (village == null) {
                    Spacer(GlanceModifier.height(6.dp))
                    Text("IMPORT VILLAGE DATA", style = TextStyle(color = ColorProvider(Gold)))
                    Text("Open COCwidgt to add an export.", maxLines = 1, style = TextStyle(color = ColorProvider(Muted)))
                } else if (compact) {
                    Spacer(GlanceModifier.height(3.dp))
                    UpgradeList(
                        GlanceModifier.fillMaxWidth().height(
                            WidgetContentPlanner.listHeight(heightDp, compact).dp
                        ),
                        entries, village.builders, now, contentWidth, compact, selection.rowHeightDp,
                    )
                    Spacer(GlanceModifier.height(3.dp))
                    BottomStatus(
                        exportedAt = village.exportedAt,
                        now = now,
                        statusText = statusText,
                        contentWidth = contentWidth,
                        compact = true,
                    )
                } else {
                    Spacer(GlanceModifier.height(5.dp))
                    UpgradeList(
                        GlanceModifier.fillMaxWidth().height(
                            WidgetContentPlanner.listHeight(heightDp, compact).dp
                        ),
                        entries, village.builders, now, contentWidth, compact, selection.rowHeightDp,
                    )
                    BottomStatus(
                        exportedAt = village.exportedAt,
                        now = now,
                        statusText = statusText,
                        contentWidth = contentWidth,
                        compact = false,
                    )
                }
            }
        }
    }

}

/**
 * The single vertical upgrade list. Visible rows are sized by [WidgetContentPlanner] to fill
 * the measured viewport without pushing the footer out of the widget.
 */
@androidx.compose.runtime.Composable
private fun UpgradeList(
    modifier: GlanceModifier,
    entries: List<ListEntry>,
    builders: Int,
    now: Long,
    contentWidth: Int,
    compact: Boolean,
    rowHeightDp: Int,
) {
    if (entries.isEmpty()) {
        Column(modifier) {
            Text("NO ACTIVE UPGRADES", maxLines = 1,
                style = TextStyle(color = ColorProvider(Muted), fontSize = if (compact) 11.sp else 12.sp))
            Spacer(GlanceModifier.defaultWeight())
        }
        return
    }
    LazyColumn(modifier) {
        items(entries, itemId = { it.id }) { entry ->
            when (entry) {
                is ListEntry.Heading -> {
                    val group = entry.group
                    // Data-driven chip: the builder count on the Buildings section. Only shown
                    // when the measured width can afford it.
                    val chip = if (group.section == WidgetSection.BUILDERS && contentWidth >= 170) {
                        "$builders/6"
                    } else {
                        null
                    }
                    CategoryHeader(
                        WidgetFit.ellipsize(
                            group.section.label.uppercase(),
                            WidgetFit.maxChars(contentWidth - 26 - (chip?.let { it.length * 6 + 22 } ?: 0) - 12, 11),
                        ),
                        group.items.size,
                        chip,
                        group.items.any { it.goblin == true },
                    )
                }
                is ListEntry.Upgrade -> if (compact) {
                    CompactUpgradeRow(entry.job, now, contentWidth, rowHeightDp)
                } else {
                    UpgradeRow(entry.job, now, contentWidth, rowHeightDp)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun PlayerHeader(name: String, townHall: Int, compact: Boolean, textWidth: Int) {
    val titleSize = if (compact) 12 else 14
    val hallLabel = townHall.takeIf { it > 0 }?.let { "TH$it" }.orEmpty()
    Row(
        GlanceModifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = if (compact) 3.dp else 4.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text(
            WidgetFit.ellipsize(name, WidgetFit.maxChars(textWidth, titleSize)),
            maxLines = 1, modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = ColorProvider(Cream), fontSize = titleSize.sp, fontWeight = FontWeight.Bold),
        )
        if (hallLabel.isNotEmpty()) {
            Spacer(GlanceModifier.width(5.dp))
            Text(hallLabel, maxLines = 1,
                style = TextStyle(color = ColorProvider(Gold), fontSize = titleSize.sp, fontWeight = FontWeight.Bold))
        }
        Spacer(GlanceModifier.width(8.dp))
        Row(
            GlanceModifier.background(Gold).cornerRadius(9.dp).padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Text("⟳ Sync", maxLines = 1,
                style = TextStyle(color = ColorProvider(Background), fontSize = 10.sp, fontWeight = FontWeight.Bold))
        }
    }
}

/**
 * Category heading: gold label + count chip and an optional data chip (e.g. progress count).
 * The label is weighted so the fixed chips can never be pushed out of the row.
 */
@androidx.compose.runtime.Composable
private fun CategoryHeader(label: String, count: Int, chip: String?, hasGoblin: Boolean) {
    Row(GlanceModifier.fillMaxWidth().padding(bottom = 3.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
        Text(label, maxLines = 1, modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = ColorProvider(Gold), fontSize = 11.sp, fontWeight = FontWeight.Bold))
        if (hasGoblin) {
            Spacer(GlanceModifier.width(4.dp))
            Text("+ Goblin", maxLines = 1,
                style = TextStyle(color = ColorProvider(Green), fontSize = 11.sp, fontWeight = FontWeight.Bold))
        }
        if (chip != null) {
            Spacer(GlanceModifier.width(5.dp))
            Row(GlanceModifier.background(Frame).cornerRadius(8.dp).padding(horizontal = 5.dp, vertical = 1.dp)) {
                Text(chip, maxLines = 1,
                    style = TextStyle(color = ColorProvider(Cream), fontSize = 10.sp))
            }
        }
        Spacer(GlanceModifier.width(4.dp))
        Row(GlanceModifier.background(Frame).cornerRadius(8.dp).padding(horizontal = 5.dp, vertical = 1.dp)) {
            Text("$count", maxLines = 1,
                style = TextStyle(color = ColorProvider(Gold), fontSize = 10.sp, fontWeight = FontWeight.Bold))
        }
    }
}

/**
 * Reference-style upgrade row: a dark rounded track whose green fill is the progress bar,
 * with the level-resolved artwork, name, target level and a countdown derived from absolute
 * timestamps on top. The fill width is computed from the measured row width, so it can
 * never overflow the row.
 */
@androidx.compose.runtime.Composable
private fun UpgradeRow(job: VillageJob, now: Long, rowWidth: Int, rowHeightDp: Int) {
    val iconSize = (20 + (rowHeightDp - STANDARD_ROW_SLOT_DP) / 4).coerceIn(20, 36).dp
    val titleSize = (12 + (rowHeightDp - STANDARD_ROW_SLOT_DP) / 16).coerceIn(12, 15)
    val timerSize = (titleSize - 1).coerceAtLeast(11)
    val cardHeight = (rowHeightDp - ROW_MARGIN_DP).coerceAtLeast(ROW_HEIGHT_DP)
    val progress = job.progress(now)
    val filled = when {
        progress == null || progress <= 0f -> 0
        else -> (rowWidth * progress).toInt().coerceIn(6, rowWidth)
    }
    Column(GlanceModifier.fillMaxWidth().padding(bottom = ROW_MARGIN_DP.dp)) {
        Box(
            GlanceModifier.fillMaxWidth().height(cardHeight.dp)
                .background(Track).cornerRadius(9.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            if (filled > 0) {
                Row(
                    GlanceModifier.width(filled.dp).height(cardHeight.dp)
                        .background(Green).cornerRadius(9.dp)
                ) {
                    Spacer(GlanceModifier.size(1.dp))
                }
            }
            Row(
                GlanceModifier.fillMaxSize().padding(horizontal = 7.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                EntityArtwork(job, iconSize)
                Spacer(GlanceModifier.width(5.dp))
                if (job.goblin == true) {
                    GoblinBadge()
                    Spacer(GlanceModifier.width(4.dp))
                }
                val countdown = "◷ ${WidgetFit.countdown(job.remainingMs(now))}"
                // Budget the title so title + countdown (+ badge) fit the measured row.
                val budget = WidgetFit.maxChars(rowWidth - 14 - iconSize.value.toInt() - 5, titleSize) -
                    countdown.length - if (job.goblin == true) 10 else 0
                Text(
                    WidgetFit.ellipsize("${job.name}${job.count.takeIf { it > 1 }?.let { " ×$it" }.orEmpty()} · ${job.currentLevel?.let { "$it → " }.orEmpty()}${job.level}", budget),
                    maxLines = 1, modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = ColorProvider(Cream), fontSize = titleSize.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.width(4.dp))
                Text(countdown, maxLines = 1,
                    style = TextStyle(color = ColorProvider(Gold), fontSize = timerSize.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}

/** Compact variant of [UpgradeRow]: same integrated green progress row, tighter typography. */
@androidx.compose.runtime.Composable
private fun CompactUpgradeRow(job: VillageJob, now: Long, rowWidth: Int, rowHeightDp: Int) {
    val iconSize = (16 + (rowHeightDp - COMPACT_ROW_SLOT_DP) / 4).coerceIn(16, 32).dp
    val titleSize = (11 + (rowHeightDp - COMPACT_ROW_SLOT_DP) / 16).coerceIn(11, 14)
    val timerSize = titleSize
    val cardHeight = (rowHeightDp - COMPACT_ROW_MARGIN_DP).coerceAtLeast(COMPACT_ROW_HEIGHT_DP)
    val progress = job.progress(now)
    val filled = when {
        progress == null || progress <= 0f -> 0
        else -> (rowWidth * progress).toInt().coerceIn(6, rowWidth)
    }
    Column(GlanceModifier.fillMaxWidth().padding(bottom = COMPACT_ROW_MARGIN_DP.dp)) {
        Box(
            GlanceModifier.fillMaxWidth().height(cardHeight.dp)
                .background(Track).cornerRadius(8.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            if (filled > 0) {
                Row(
                    GlanceModifier.width(filled.dp).height(cardHeight.dp)
                        .background(Green).cornerRadius(8.dp)
                ) {
                    Spacer(GlanceModifier.size(1.dp))
                }
            }
            Row(
                GlanceModifier.fillMaxSize().padding(horizontal = 6.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                EntityArtwork(job, iconSize)
                Spacer(GlanceModifier.width(4.dp))
                if (job.goblin == true) {
                    GoblinBadge()
                    Spacer(GlanceModifier.width(4.dp))
                }
                // The countdown is kept whole; only the name gives up space.
                val countdown = "◷ ${WidgetFit.countdown(job.remainingMs(now))}"
                val budget = WidgetFit.maxChars(rowWidth - 12 - iconSize.value.toInt() - 4, titleSize) -
                    countdown.length - if (job.goblin == true) 9 else 0
                val levelText = job.currentLevel?.let { "$it→${job.level}" } ?: "${job.level}"
                Text(WidgetFit.ellipsize("${job.name}${job.count.takeIf { it > 1 }?.let { " ×$it" }.orEmpty()} · $levelText", budget), maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = ColorProvider(Cream), fontSize = titleSize.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.width(4.dp))
                Text(countdown, maxLines = 1,
                    style = TextStyle(color = ColorProvider(Gold), fontSize = timerSize.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun GoblinBadge() {
    Row(
        GlanceModifier.background(Frame).cornerRadius(6.dp).padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text("GOBLIN", maxLines = 1,
            style = TextStyle(color = ColorProvider(Cream), fontSize = 8.sp, fontWeight = FontWeight.Bold))
    }
}

/**
 * Bottom status: the visible/hidden upgrade count and the age of the imported export.
 */
@androidx.compose.runtime.Composable
private fun BottomStatus(
    exportedAt: Long,
    now: Long,
    statusText: String,
    contentWidth: Int,
    compact: Boolean,
) {
    val exportAge = ExportStatus.age(now, exportedAt)
    val stale = exportedAt > 0L && now - exportedAt > 24 * 60 * 60 * 1000L
    val exportLabel = when {
        exportAge.isEmpty() -> "Export time unknown"
        stale -> "Export stale · $exportAge"
        else -> "Exported $exportAge"
    }
    if (compact) {
        val line = "$statusText · $exportLabel"
        Row(
            GlanceModifier.fillMaxWidth().height(COMPACT_FOOTER_DP.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                WidgetFit.ellipsize(line, WidgetFit.maxChars(contentWidth - 8, 9)),
                maxLines = 1,
                modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp),
                style = TextStyle(color = ColorProvider(if (stale) Alert else Muted), fontSize = 9.sp, fontWeight = FontWeight.Bold),
            )
        }
        return
    }
    val rightBudget = minOf(contentWidth / 2, 110)
    val leftBudget = WidgetFit.maxChars(contentWidth - rightBudget - 8, 10)
    Row(
        GlanceModifier.fillMaxWidth().height(STANDARD_FOOTER_DP.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text(WidgetFit.ellipsize(statusText, leftBudget), maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = ColorProvider(if (statusText.endsWith("in app")) Alert else Cream), fontSize = 10.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.width(6.dp))
        Text(WidgetFit.ellipsize(exportLabel, WidgetFit.maxChars(rightBudget, 9)), maxLines = 1,
            style = TextStyle(color = ColorProvider(if (stale) Alert else Muted), fontSize = 9.sp))
    }
}

/** Level-resolved entity artwork: exact level, else the closest bundled lower level. */
@androidx.compose.runtime.Composable
private fun EntityArtwork(job: VillageJob, size: Dp) {
    val resourceId = GameArtwork.drawableResId(job.dataId, job.level)
    if (resourceId != null) {
        Image(ImageProvider(resourceId), contentDescription = job.name, modifier = GlanceModifier.size(size))
    } else {
        Text("?", modifier = GlanceModifier.size(size).background(Frame).cornerRadius(8.dp).padding(4.dp),
            style = TextStyle(color = ColorProvider(Gold)))
    }
}

class CocWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CocWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshWorker.enqueue(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: android.appwidget.AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetRefreshWorker.enqueue(context)
    }
}
