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
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
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
import dev.stokonn.cocwidgt.R
import dev.stokonn.cocwidgt.data.GameArtwork
import dev.stokonn.cocwidgt.data.PlayerHeaderState
import dev.stokonn.cocwidgt.data.PlayerIdentity
import dev.stokonn.cocwidgt.data.PlayerProfileCodec
import dev.stokonn.cocwidgt.data.SyncStatus
import dev.stokonn.cocwidgt.data.UpgradeCategory
import dev.stokonn.cocwidgt.data.VillageJob
import dev.stokonn.cocwidgt.data.VillageSnapshot
import dev.stokonn.cocwidgt.security.TokenStore
import dev.stokonn.cocwidgt.sync.WidgetRefreshWorker

private val Background = Color(0xFF281D17)
private val Frame = Color(0xFF493428)
private val Panel = Color(0xFF39281F)
private val Gold = Color(0xFFFFD36B)
private val Cream = Color(0xFFFFF2D8)
private val Muted = Color(0xFFCDB9A2)
private val Green = Color(0xFF9BCB70)
private val Alert = Color(0xFFFF8A78)

// Row geometry of the reference design: the green progress bar IS the row (dark track with a
// green fill sized from the absolute-timestamp progress). Heights must stay in sync with the
// constants WidgetContentPlanner reserves when packing rows.
private const val ROW_HEIGHT_DP = 34
private const val ROW_MARGIN_DP = 2
private const val COMPACT_ROW_HEIGHT_DP = 24
private const val COMPACT_ROW_MARGIN_DP = 2

/**
 * Exact-size widget matching the supplied visual reference: warm dark brown rounded surface,
 * player header with avatar, Player Tag, Town Hall and a Sync chip, category sections with
 * counts and Goblin markers, green rounded progress rows with icons/names/levels/countdowns,
 * and a bottom status area with quick chips and the last successful sync time.
 *
 * Every render reads the real launcher size through LocalSize, groups active upgrades into
 * categories, lays them out in as many columns as the width allows and shows as many rows as
 * the height fits. Countdowns and progress are computed from absolute timestamps on each
 * render; periodic re-renders are scheduled through WorkManager / the system update period.
 */
class CocWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val ctx = LocalContext.current
            val size = LocalSize.current
            val widthDp = size.width.value.toInt()
            val heightDp = size.height.value.toInt()
            val village = VillageSnapshot.load(ctx)
            val profile = PlayerProfileCodec.decode(ctx.getSharedPreferences(PlayerProfileCodec.PREFS, Context.MODE_PRIVATE).all)
            val settings = ctx.getSharedPreferences("cocwidgt", Context.MODE_PRIVATE)
            val header = PlayerIdentity.headerState(
                exportTag = village?.tag,
                exportName = village?.name,
                exportTownHall = village?.townHall ?: 0,
                settingsTag = settings.getString("player_tag", null),
                profile = profile,
            )
            // Distinguishes "no token configured" from "stored but undecryptable" so a broken
            // Keystore entry is reported instead of silently looking like an unconfigured app.
            val tokenStore = TokenStore(ctx)
            val tokenUnreadable = tokenStore.hasStoredToken() && tokenStore.read() == null
            val now = System.currentTimeMillis()
            val selection = WidgetContentPlanner.select(village?.jobs.orEmpty(), widthDp, heightDp, now)
            val compact = selection.compact
            val contentPadding = if (compact) 8 else 13
            val contentWidth = widthDp - contentPadding * 2
            val renderedColumns = selection.columns.filter { it.isNotEmpty() }
            val renderedColumnCount = renderedColumns.size.coerceAtLeast(1)
            val columnWidth = WidgetFit.columnWidth(contentWidth, renderedColumnCount)

            // One status string for both sizes: failure label first, then the last successful
            // sync time (relative, derived from the absolute timestamp on every render).
            val failed = apiErrorStatus(header, tokenUnreadable)
            val statusText = failed ?: when {
                header.profileMatches && header.apiSyncedAt > 0L ->
                    "synced ${SyncStatus.age(now, header.apiSyncedAt)}"
                else -> "not synced"
            }
            val statusColor = if (failed != null) Alert else Muted

            // Header budget: content minus its own padding, the avatar, the Sync chip and the
            // gaps between them — both text lines are ellipsized to this width so a long name
            // or tag can never push into the avatar or the chip.
            val headerPaddingH = if (compact) 8 else 10
            val avatarSize = if (compact) 18 else 22
            val syncChipWidth = 56
            val headerTextWidth = (contentWidth - headerPaddingH * 2 - avatarSize - syncChipWidth - 16)
                .coerceAtLeast(40)

            Column(
                GlanceModifier.fillMaxSize().background(Background).cornerRadius(20.dp)
                    .padding(contentPadding.dp).clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.Vertical.Top
            ) {
                PlayerHeader(header.displayName, header.expectedTag ?: PlayerHeaderState.NO_TAG_LABEL,
                    header.townHall, compact, headerTextWidth)
                if (village == null) {
                    Spacer(GlanceModifier.height(6.dp))
                    Text("IMPORT VILLAGE DATA", style = TextStyle(color = ColorProvider(Gold)))
                    Text("Open COCwidgt to add an export.", maxLines = 1, style = TextStyle(color = ColorProvider(Muted)))
                } else if (compact) {
                    Spacer(GlanceModifier.height(4.dp))
                    val activeCount = village.jobs.count { it.remainingMs(now) > 0 }
                    val stale = now - village.exportedAt > 24 * 60 * 60 * 1000L
                    val more = if (selection.hiddenCount > 0) " · +${selection.hiddenCount}" else ""
                    val activeLine = "$activeCount ACTIVE$more${if (stale) " · STALE" else ""} · $statusText"
                    Text(WidgetFit.ellipsize(activeLine, WidgetFit.maxChars(contentWidth, 11)),
                        maxLines = 1, style = TextStyle(color = ColorProvider(if (failed != null) Alert else Gold), fontSize = 11.sp))
                    selection.items.forEach { CompactUpgradeRow(it, now, contentWidth) }
                    if (selection.items.isEmpty() && selection.hiddenCount == 0) {
                        Text("NO ACTIVE UPGRADES", maxLines = 1, style = TextStyle(color = ColorProvider(Muted), fontSize = 12.sp))
                    }
                } else {
                    Spacer(GlanceModifier.height(7.dp))
                    if (renderedColumns.isEmpty()) {
                        Text("NO ACTIVE UPGRADES", maxLines = 1, style = TextStyle(color = ColorProvider(Muted), fontSize = 12.sp))
                    } else {
                        Row(
                            GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Vertical.Top
                        ) {
                            renderedColumns.forEachIndexed { columnIndex, groups ->
                                // The trailing gap of a non-last column lives INSIDE its width
                                // (Android padding is part of the view width), so the visible
                                // content — and therefore every row and bar — is only
                                // columnWidth - endGap wide. Measuring against the raw
                                // columnWidth made bars overflow by exactly one gap into the
                                // next column, which is what overlapped the entries.
                                val endGap = if (columnIndex < renderedColumnCount - 1) {
                                    WidgetContentPlanner.COLUMN_GAP_DP
                                } else {
                                    0
                                }
                                val columnContent = columnWidth - endGap
                                Column(
                                    GlanceModifier.width(columnWidth.dp)
                                        .padding(end = endGap.dp)
                                ) {
                                    groups.forEach { group ->
                                        val goblinSuffix = group.items.any { it.goblin == true }
                                        // Data-driven chips: the builder count on the builders
                                        // section, always the item count. Only shown when the
                                        // measured column can afford them.
                                        val chip = if (group.category == UpgradeCategory.BUILDINGS &&
                                            columnContent >= 200
                                        ) {
                                            "${village.builders} builders"
                                        } else {
                                            null
                                        }
                                        val budgetChars = WidgetFit.maxChars(
                                            columnContent - 26 - (chip?.let { it.length * 6 + 22 } ?: 0) -
                                                (if (goblinSuffix) 46 else 0) - 12,
                                            11,
                                        )
                                        val heading = WidgetFit.ellipsize(
                                            WidgetFit.categoryLabel(group.category, columnContent).uppercase(),
                                            budgetChars,
                                        )
                                        CategoryHeader(heading, group.items.size, goblinSuffix, chip)
                                        group.items.forEach { job ->
                                            UpgradeRow(job, now, columnContent)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (selection.hiddenCount > 0) {
                        val overflowLine = "+${selection.hiddenCount} more active upgrades · open app"
                        Text(WidgetFit.ellipsize(overflowLine, WidgetFit.maxChars(contentWidth, 12)), maxLines = 1,
                            style = TextStyle(color = ColorProvider(Gold), fontSize = 12.sp))
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    BottomStatus(
                        topJob = selection.items.firstOrNull(),
                        exportedAt = village.exportedAt,
                        now = now,
                        statusText = statusText,
                        statusColor = statusColor,
                        contentWidth = contentWidth,
                    )
                }
            }
        }
    }

    /** Failure label for the status area, or null when there is no attributable failure. */
    private fun apiErrorStatus(header: PlayerHeaderState, tokenUnreadable: Boolean): String? = when {
        header.apiError.isNotBlank() -> SyncStatus.shortLabel(header.apiError)
        tokenUnreadable -> "token unreadable"
        else -> null
    }
}

@androidx.compose.runtime.Composable
private fun PlayerHeader(name: String, tag: String, townHall: Int, compact: Boolean, textWidth: Int) {
    val line1Sp = if (compact) 13 else 15
    val line2Sp = if (compact) 11 else 12
    // The reference identity line is the Player Tag + Town Hall; the nickname (the only real
    // name we have) leads when it exists — the placeholder is never rendered as the title.
    val hasRealName = PlayerIdentity.usableName(name) != null && name != PlayerHeaderState.FALLBACK_NAME
    val line1 = if (hasRealName) name else tag
    val line2 = buildList {
        if (hasRealName) add(tag)
        if (townHall > 0) add("TH$townHall")
    }.joinToString("  ·  ")
    Row(
        GlanceModifier.fillMaxWidth().background(Frame).cornerRadius(13.dp)
            .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 5.dp else 7.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Image(
            ImageProvider(R.drawable.coc_buildings_home_village_town_hall_level_1),
            contentDescription = "Village",
            modifier = GlanceModifier.size(if (compact) 18.dp else 22.dp),
        )
        Spacer(GlanceModifier.width(6.dp))
        Column(GlanceModifier.defaultWeight()) {
            // Glance cannot ellipsize, so both lines are trimmed to the measured budget first.
            Text(WidgetFit.ellipsize(line1, WidgetFit.maxChars(textWidth, line1Sp)), maxLines = 1,
                style = TextStyle(color = ColorProvider(Cream), fontSize = line1Sp.sp, fontWeight = FontWeight.Bold))
            if (line2.isNotEmpty()) {
                Text(WidgetFit.ellipsize(line2, WidgetFit.maxChars(textWidth, line2Sp)), maxLines = 1,
                    style = TextStyle(color = ColorProvider(Gold), fontSize = line2Sp.sp))
            }
        }
        Spacer(GlanceModifier.width(4.dp))
        // Reference-style Sync chip: opens the app (the widget itself already does), kept as a
        // visual affordance with a fixed measured width for the text budget above.
        Row(
            GlanceModifier.background(Gold).cornerRadius(9.dp).padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text("⟳ Sync", maxLines = 1,
                style = TextStyle(color = ColorProvider(Background), fontSize = 10.sp, fontWeight = FontWeight.Bold))
        }
    }
}

/**
 * Category heading: gold label + count chip, a green "+ Goblin" marker when the section holds
 * goblin-flagged upgrades, and an optional data chip (e.g. builder count). The label is
 * weighted so the fixed chips can never be pushed out of the column.
 */
@androidx.compose.runtime.Composable
private fun CategoryHeader(label: String, count: Int, goblinSuffix: Boolean, chip: String?) {
    Row(GlanceModifier.fillMaxWidth().padding(bottom = 3.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
        Text(label, maxLines = 1, modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = ColorProvider(Gold), fontSize = 11.sp, fontWeight = FontWeight.Bold))
        if (goblinSuffix) {
            Text(" + Goblin", maxLines = 1,
                style = TextStyle(color = ColorProvider(Green), fontSize = 11.sp, fontWeight = FontWeight.Bold))
        }
        if (chip != null) {
            Spacer(GlanceModifier.width(5.dp))
            Row(GlanceModifier.background(Panel).cornerRadius(8.dp).padding(horizontal = 5.dp, vertical = 1.dp)) {
                Text(chip, maxLines = 1,
                    style = TextStyle(color = ColorProvider(Cream), fontSize = 10.sp))
            }
        }
        Spacer(GlanceModifier.width(4.dp))
        Row(GlanceModifier.background(Panel).cornerRadius(8.dp).padding(horizontal = 5.dp, vertical = 1.dp)) {
            Text("$count", maxLines = 1,
                style = TextStyle(color = ColorProvider(Gold), fontSize = 10.sp, fontWeight = FontWeight.Bold))
        }
    }
}

/**
 * Reference-style upgrade row: a dark rounded track whose green fill is the progress bar,
 * with the icon, name, target level and a countdown derived from absolute timestamps on top.
 * The fill width is computed from the measured row width, so it can never overflow the row.
 */
@androidx.compose.runtime.Composable
private fun UpgradeRow(job: VillageJob, now: Long, rowWidth: Int) {
    val progress = job.progress(now)
    val filled = when {
        progress == null || progress <= 0f -> 0
        else -> (rowWidth * progress).toInt().coerceIn(6, rowWidth)
    }
    Column(GlanceModifier.fillMaxWidth().padding(bottom = ROW_MARGIN_DP.dp)) {
        Box(
            GlanceModifier.fillMaxWidth().height(ROW_HEIGHT_DP.dp)
                .background(Frame).cornerRadius(9.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            if (filled > 0) {
                Row(
                    GlanceModifier.width(filled.dp).height(ROW_HEIGHT_DP.dp)
                        .background(Green).cornerRadius(9.dp)
                ) {
                    Spacer(GlanceModifier.size(1.dp))
                }
            }
            Row(
                GlanceModifier.fillMaxSize().padding(horizontal = 7.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                EntityArtwork(job, 20.dp)
                Spacer(GlanceModifier.width(5.dp))
                if (job.goblin == true) {
                    GoblinBadge()
                    Spacer(GlanceModifier.width(4.dp))
                }
                val countdown = "⏱ ${job.countdown(now)}"
                // Budget the title so title + countdown (+ badge) fit the measured row.
                val budget = WidgetFit.maxChars(rowWidth - 14 - 20 - 5, 12) -
                    countdown.length - if (job.goblin == true) 10 else 0
                Text(
                    WidgetFit.ellipsize("${job.name} → ${job.level}", budget),
                    maxLines = 1, modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = ColorProvider(Cream), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.width(4.dp))
                Text(countdown, maxLines = 1,
                    style = TextStyle(color = ColorProvider(Gold), fontSize = 11.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}

/** Compact variant of [UpgradeRow]: same integrated green progress row, tighter typography. */
@androidx.compose.runtime.Composable
private fun CompactUpgradeRow(job: VillageJob, now: Long, rowWidth: Int) {
    val progress = job.progress(now)
    val filled = when {
        progress == null || progress <= 0f -> 0
        else -> (rowWidth * progress).toInt().coerceIn(6, rowWidth)
    }
    Column(GlanceModifier.fillMaxWidth().padding(bottom = COMPACT_ROW_MARGIN_DP.dp)) {
        Box(
            GlanceModifier.fillMaxWidth().height(COMPACT_ROW_HEIGHT_DP.dp)
                .background(Frame).cornerRadius(8.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            if (filled > 0) {
                Row(
                    GlanceModifier.width(filled.dp).height(COMPACT_ROW_HEIGHT_DP.dp)
                        .background(Green).cornerRadius(8.dp)
                ) {
                    Spacer(GlanceModifier.size(1.dp))
                }
            }
            Row(
                GlanceModifier.fillMaxSize().padding(horizontal = 6.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                EntityArtwork(job, 16.dp)
                Spacer(GlanceModifier.width(4.dp))
                if (job.goblin == true) {
                    GoblinBadge()
                    Spacer(GlanceModifier.width(4.dp))
                }
                // The countdown is kept whole; only the name gives up space.
                val countdown = "⏱ ${job.countdown(now)}"
                val budget = WidgetFit.maxChars(rowWidth - 12 - 16 - 4, 11) -
                    countdown.length - if (job.goblin == true) 9 else 0
                Text(WidgetFit.ellipsize(job.name, budget), maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = ColorProvider(Cream), fontSize = 11.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.width(4.dp))
                Text(countdown, maxLines = 1,
                    style = TextStyle(color = ColorProvider(Gold), fontSize = 11.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun GoblinBadge() {
    Row(
        GlanceModifier.background(Panel).cornerRadius(6.dp).padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text("GOBLIN", maxLines = 1,
            style = TextStyle(color = ColorProvider(Cream), fontSize = 8.sp, fontWeight = FontWeight.Bold))
    }
}

/**
 * Bottom status area of the reference design: quick chips on the left (next active upgrade,
 * export age / staleness) and the last successful API sync time — or the offline/failure
 * state — on the right. All labels are budgeted against the measured widget width.
 */
@androidx.compose.runtime.Composable
private fun BottomStatus(
    topJob: VillageJob?,
    exportedAt: Long,
    now: Long,
    statusText: String,
    statusColor: Color,
    contentWidth: Int,
) {
    val exportAge = if (exportedAt > 0L) SyncStatus.age(now, exportedAt) else ""
    val stale = exportedAt > 0L && now - exportedAt > 24 * 60 * 60 * 1000L
    val exportChip = when {
        exportAge.isEmpty() -> null
        stale -> "STALE · exported $exportAge"
        else -> "exported $exportAge"
    }
    // Fixed children are measured before the weighted spacer, so the active chip absorbs the
    // remainder of the budget: status + export chip + gaps are subtracted first.
    val statusChars = statusText.length + 2
    val exportChars = (exportChip?.length ?: 0) + 4
    val jobChipBudget = WidgetFit.maxChars(contentWidth, 10) - statusChars - exportChars - 8
    val jobChip = topJob?.let {
        WidgetFit.ellipsize("⟳ ${it.name} · ${it.countdown(now)}", jobChipBudget)
    }
    Row(
        GlanceModifier.fillMaxWidth().height(24.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        val chipText = jobChip ?: "NO ACTIVE UPGRADES"
        Row(GlanceModifier.background(Frame).cornerRadius(9.dp).padding(horizontal = 7.dp, vertical = 3.dp)) {
            Text(chipText, maxLines = 1,
                style = TextStyle(color = ColorProvider(if (jobChip == null) Muted else Gold), fontSize = 10.sp))
        }
        if (exportChip != null) {
            Spacer(GlanceModifier.width(5.dp))
            Row(GlanceModifier.background(Frame).cornerRadius(9.dp).padding(horizontal = 7.dp, vertical = 3.dp)) {
                Text(WidgetFit.ellipsize(exportChip, exportChars), maxLines = 1,
                    style = TextStyle(color = ColorProvider(if (stale) Alert else Cream), fontSize = 10.sp))
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        Text(WidgetFit.ellipsize(statusText, statusChars), maxLines = 1,
            style = TextStyle(color = ColorProvider(statusColor), fontSize = 10.sp, fontWeight = FontWeight.Bold))
    }
}

@androidx.compose.runtime.Composable
private fun EntityArtwork(job: VillageJob, size: Dp) {
    val resourceId = GameArtwork.drawableResId(job.dataId)
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
