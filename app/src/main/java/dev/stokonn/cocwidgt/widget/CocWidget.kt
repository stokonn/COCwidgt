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
import dev.stokonn.cocwidgt.data.PlayerHeaderState
import dev.stokonn.cocwidgt.data.PlayerIdentity
import dev.stokonn.cocwidgt.data.PlayerProfileCodec
import dev.stokonn.cocwidgt.data.VillageJob
import dev.stokonn.cocwidgt.data.VillageSnapshot
import dev.stokonn.cocwidgt.sync.WidgetRefreshWorker
import java.text.DateFormat
import java.util.Date

private val Background = Color(0xFF281D17)
private val Frame = Color(0xFF493428)
private val Panel = Color(0xFF39281F)
private val Gold = Color(0xFFFFD36B)
private val Cream = Color(0xFFFFF2D8)
private val Muted = Color(0xFFCDB9A2)
private val Green = Color(0xFF9BCB70)

/**
 * Exact-size widget: every render reads the real launcher size, groups active upgrades into
 * categories, lays them out in as many columns as the width allows and shows as many rows as
 * the height fits. Countdowns and progress are computed from absolute timestamps on each render;
 * periodic re-renders are scheduled through WorkManager / the system update period.
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
            val tag = header.expectedTag ?: PlayerHeaderState.NO_TAG_LABEL
            val playerName = header.displayName
            val th = header.townHall
            val profileMatches = header.profileMatches
            val apiSyncedAt = header.apiSyncedAt
            val apiError = header.apiError
            val now = System.currentTimeMillis()
            val selection = WidgetContentPlanner.select(village?.jobs.orEmpty(), widthDp, heightDp, now)
            val compact = selection.compact
            val contentPadding = if (compact) 8 else 13
            val contentWidth = widthDp - contentPadding * 2
            val renderedColumns = selection.columns.filter { it.isNotEmpty() }
            val renderedColumnCount = renderedColumns.size.coerceAtLeast(1)
            val columnWidth = if (renderedColumnCount == 1) {
                contentWidth
            } else {
                (contentWidth - WidgetContentPlanner.COLUMN_GAP_DP * (renderedColumnCount - 1)) / renderedColumnCount
            }
            val cardPadding = if (renderedColumnCount == 1) 10 else 8
            val cardBarWidth = columnWidth - cardPadding * 2
            val compactBarWidth = (contentWidth - 25).coerceAtLeast(40)

            Column(
                GlanceModifier.fillMaxSize().background(Background).cornerRadius(20.dp)
                    .padding(contentPadding.dp).clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.Vertical.Top
            ) {
                PlayerHeader(playerName, tag, th, compact)
                if (village == null) {
                    Spacer(GlanceModifier.height(6.dp))
                    Text("IMPORT VILLAGE DATA", style = TextStyle(color = ColorProvider(Gold)))
                    Text("Open COCwidgt to add an export.", maxLines = 1, style = TextStyle(color = ColorProvider(Muted)))
                } else if (compact) {
                    Spacer(GlanceModifier.height(4.dp))
                    val activeCount = village.jobs.count { it.remainingMs(now) > 0 }
                    val stale = now - village.exportedAt > 24 * 60 * 60 * 1000L
                    val more = if (selection.hiddenCount > 0) " · +${selection.hiddenCount}" else ""
                    Text("$activeCount ACTIVE$more${if (stale) " · STALE" else " · EXPORT"}", maxLines = 1,
                        style = TextStyle(color = ColorProvider(Gold), fontSize = 12.sp))
                    selection.items.forEach { CompactUpgrade(it, now, compactBarWidth) }
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
                                Column(
                                    GlanceModifier.width(columnWidth.dp)
                                        .padding(end = if (columnIndex < renderedColumnCount - 1) WidgetContentPlanner.COLUMN_GAP_DP.dp else 0.dp)
                                ) {
                                    groups.forEach { group ->
                                        CategoryHeader(group.category.label, group.items.size)
                                        group.items.forEach { job ->
                                            UpgradeCard(job, now, renderedColumnCount > 1, cardPadding, cardBarWidth)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (selection.hiddenCount > 0) {
                        Text("+${selection.hiddenCount} more active upgrades · open app", maxLines = 1,
                            style = TextStyle(color = ColorProvider(Gold), fontSize = 12.sp))
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    val stale = now - village.exportedAt > 24 * 60 * 60 * 1000L
                    val exported = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(village.exportedAt))
                    Text("${if (stale) "STALE · " else "EXPORTED · "}$exported", maxLines = 1,
                        style = TextStyle(color = ColorProvider(if (stale) Gold else Muted), fontSize = 12.sp))
                    // Reserved by the planner in every non-compact size, so the layout never jumps.
                    val apiStatus = when {
                        !profileMatches -> "API · not synced for this player"
                        apiError.isNotBlank() -> "API · $apiError"
                        apiSyncedAt > 0 -> "API synced · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(apiSyncedAt))}"
                        else -> "API · not synced"
                    }
                    Text(apiStatus, maxLines = 1, style = TextStyle(color = ColorProvider(Muted), fontSize = 12.sp))
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun PlayerHeader(name: String, tag: String, townHall: Int, compact: Boolean) {
    Row(
        GlanceModifier.fillMaxWidth().background(Frame).cornerRadius(13.dp)
            .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 6.dp else 8.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Column(GlanceModifier.defaultWeight()) {
            Text(name, maxLines = 1, style = TextStyle(color = ColorProvider(Cream), fontSize = if (compact) 14.sp else 16.sp))
            Text("$tag${if (townHall > 0) "  ·  TH$townHall" else ""}", maxLines = 1,
                style = TextStyle(color = ColorProvider(Gold), fontSize = if (compact) 12.sp else 14.sp))
        }
        Text("VILLAGE", maxLines = 1, style = TextStyle(color = ColorProvider(Muted), fontSize = if (compact) 10.sp else 12.sp))
    }
}

@androidx.compose.runtime.Composable
private fun CategoryHeader(label: String, count: Int) {
    Row(GlanceModifier.fillMaxWidth().padding(bottom = 3.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
        Text(label.uppercase(), maxLines = 1,
            style = TextStyle(color = ColorProvider(Gold), fontSize = 11.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.defaultWeight())
        Text("$count", maxLines = 1, style = TextStyle(color = ColorProvider(Muted), fontSize = 11.sp))
    }
}

@androidx.compose.runtime.Composable
private fun UpgradeCard(job: VillageJob, now: Long, wide: Boolean, cardPadding: Int, barWidth: Int) {
    Column(
        GlanceModifier.fillMaxWidth().padding(bottom = if (wide) 4.dp else 5.dp)
            .background(Panel).cornerRadius(11.dp)
            .padding(horizontal = cardPadding.dp, vertical = 3.dp)
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            EntityArtwork(job, if (wide) 22.dp else 30.dp)
            Column(GlanceModifier.defaultWeight().padding(start = 6.dp)) {
                Text("${job.name}  ·  ${job.level}", maxLines = 1,
                    style = TextStyle(color = ColorProvider(Cream), fontSize = if (wide) 12.sp else 13.sp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Text(job.countdown(now), maxLines = 1,
                        style = TextStyle(color = ColorProvider(Green), fontSize = if (wide) 11.sp else 12.sp))
                    if (job.goblin == true) {
                        Spacer(GlanceModifier.width(5.dp))
                        Text("GOBLIN", maxLines = 1,
                            style = TextStyle(color = ColorProvider(Gold), fontSize = 10.sp, fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
        Spacer(GlanceModifier.height(if (wide) 2.dp else 3.dp))
        ProgressBar(barWidth, job.progress(now), if (wide) 3 else 4)
    }
}

@androidx.compose.runtime.Composable
private fun CompactUpgrade(job: VillageJob, now: Long, barWidth: Int) {
    Row(GlanceModifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
        EntityArtwork(job, 20.dp)
        Column(GlanceModifier.defaultWeight().padding(start = 5.dp)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                Text("${job.name} · ${job.countdown(now)}", maxLines = 1, modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = ColorProvider(Cream), fontSize = 12.sp))
                if (job.goblin == true) {
                    Text("G", maxLines = 1,
                        style = TextStyle(color = ColorProvider(Gold), fontSize = 10.sp, fontWeight = FontWeight.Bold))
                }
            }
            Spacer(GlanceModifier.height(2.dp))
            ProgressBar(barWidth, job.progress(now), 3)
        }
    }
}

/**
 * Green progress bar sized from the real widget geometry. Progress comes from the absolute
 * start/completion timestamps of the upgrade; a null or invalid progress renders no bar
 * rather than a fake percentage.
 */
@androidx.compose.runtime.Composable
private fun ProgressBar(widthDp: Int, progress: Float?, heightDp: Int) {
    if (progress == null) return
    val width: Dp = widthDp.dp
    val filled = if (progress <= 0f) 0.dp else (width * progress).coerceAtLeast(1.dp)
    Row(GlanceModifier.width(width).height(heightDp.dp).background(Frame).cornerRadius(2.dp)) {
        if (filled > 0.dp) {
            Spacer(GlanceModifier.width(filled).height(heightDp.dp).background(Green).cornerRadius(2.dp))
        }
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
