package dev.stokonn.cocwidgt.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
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
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.stokonn.cocwidgt.MainActivity
import dev.stokonn.cocwidgt.data.GameArtwork
import dev.stokonn.cocwidgt.data.VillageJob
import dev.stokonn.cocwidgt.data.VillageSnapshot
import java.text.DateFormat
import java.util.Date

private val Background = Color(0xFF39271D)
private val Panel = Color(0xFF594031)
private val Gold = Color(0xFFFFD36B)
private val Cream = Color(0xFFFFF2D8)
private val Muted = Color(0xFFCDB9A2)
private val Green = Color(0xFF8BCB5A)

class CocWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(
        DpSize(180.dp, 110.dp), DpSize(250.dp, 180.dp), DpSize(360.dp, 280.dp), DpSize(480.dp, 600.dp)
    ))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val ctx = LocalContext.current
            val size = LocalSize.current
            val village = VillageSnapshot.load(ctx)
            val profile = ctx.getSharedPreferences("cocwidgt_player", Context.MODE_PRIVATE)
            val tag = village?.tag ?: profile.getString("tag", null) ?: profile.getString("player_tag", null) ?: "SET PLAYER TAG"
            val playerName = profile.getString("name", null)?.takeIf { it.isNotBlank() } ?: village?.name?.takeIf { it.isNotBlank() } ?: tag
            val th = village?.townHall?.takeIf { it > 0 } ?: profile.getInt("town_hall", -1)
            val apiSyncedAt = profile.getLong("synced_at", 0L)
            val apiError = profile.getString("sync_error", "").orEmpty()
            val jobs = village?.jobs.orEmpty().filter { it.remainingMs() > 0 }.sortedBy { it.remainingMs() }
            val compact = size.width < 250.dp || size.height < 150.dp
            val rowLimit = when {
                size.height < 150.dp -> 1
                size.height < 230.dp -> 2
                size.height < 360.dp -> 4
                size.height < 480.dp -> 6
                else -> 14
            }
            val title = if (compact) jobs.firstOrNull() else null

            Column(GlanceModifier.fillMaxSize().background(Background).cornerRadius(20.dp).padding(if (compact) 10.dp else 14.dp)
                .clickable(actionStartActivity<MainActivity>()), verticalAlignment = Alignment.Vertical.Top) {
                PlayerHeader(playerName, tag, th, compact)
                Spacer(GlanceModifier.height(7.dp))
                if (village == null) {
                    Text("VILLAGE EXPORT", style = TextStyle(color = ColorProvider(Gold)))
                    Text("Open the app to import your in-game JSON export.", style = TextStyle(color = ColorProvider(Cream)))
                } else if (compact) {
                    Text("BUILDERS  ${jobs.count { it.section == "Builders" || it.section == "Builder Base" }} active", style = TextStyle(color = ColorProvider(Gold)))
                    title?.let { Text("${it.name}  ·  ${it.countdown()}", maxLines = 1, style = TextStyle(color = ColorProvider(Cream))) }
                } else {
                    var remainingRows = rowLimit
                    val rowGroups = listOf(
                        "Builders" to ("BUILDERS" to 2),
                        "Laboratory" to ("LABORATORY" to 2),
                        "Pets" to ("PET HOUSE" to 4),
                        "Builder Base" to ("BUILDER BASE" to 2),
                        "Star Laboratory" to ("STAR LAB" to 2),
                        "Unknown item" to ("UNMAPPED EXPORT ITEMS" to 4)
                    )
                    rowGroups.forEach { (section, meta) ->
                        val rows = jobs.filter { it.section == section }.take(minOf(meta.second, remainingRows))
                        if (rows.isNotEmpty()) {
                            SectionHeading(meta.first, "${jobs.count { it.section == section }} active")
                            rows.forEach { UpgradeRow(it, dense = true, oneLine = size.height >= 550.dp) }
                            remainingRows -= rows.size
                        }
                    }
                    if (size.width >= 480.dp && size.height >= 550.dp) {
                        Text("${jobs.size} active upgrades  ·  Tap to manage", style = TextStyle(color = ColorProvider(Muted)))
                        Text(if (apiError.isNotBlank()) "API · $apiError" else if (apiSyncedAt > 0L) "API synced ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(apiSyncedAt))}" else "API · not synced",
                            maxLines = 1, style = TextStyle(color = ColorProvider(Muted)))
                    }
                    Spacer(GlanceModifier.height(4.dp))
                    val stale = System.currentTimeMillis() - village.exportedAt > 24 * 60 * 60 * 1000L
                    Text("${if (stale) "STALE EXPORT  ·  " else ""}${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(village.exportedAt))}  ·  local timers",
                        style = TextStyle(color = ColorProvider(Muted)))
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun PlayerHeader(name: String, tag: String, townHall: Int, compact: Boolean) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        Column(GlanceModifier.fillMaxWidth()) {
            Text(name, maxLines = 1, style = TextStyle(color = ColorProvider(Cream)))
            Text("$tag${if (townHall > 0) "  ·  TH$townHall" else ""}${if (!compact) "  ✦" else ""}", maxLines = 1, style = TextStyle(color = ColorProvider(Gold)))
        }
    }
}

@androidx.compose.runtime.Composable
private fun SectionHeading(title: String, trailing: String) {
    Row(GlanceModifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
        Text("$title  ·  $trailing", maxLines = 1, style = TextStyle(color = ColorProvider(Gold)))
    }
}

@androidx.compose.runtime.Composable
private fun UpgradeRow(job: VillageJob, dense: Boolean, oneLine: Boolean = false) {
    val iconId = GameArtwork.drawableResId(job.dataId)
    if (oneLine) {
        Row(GlanceModifier.fillMaxWidth().background(Panel).cornerRadius(8.dp).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically) {
            EntityArtwork(iconId, job.name, 28.dp)
            Text("${job.name}  →  ${job.level}  ·  ${job.countdown()}", maxLines = 1,
                modifier = GlanceModifier.defaultWeight().padding(start = 6.dp), style = TextStyle(color = ColorProvider(Cream)))
        }
    } else {
        Row(GlanceModifier.fillMaxWidth().background(Panel).cornerRadius(10.dp).padding(horizontal = 8.dp, vertical = if (dense) 4.dp else 6.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically) {
            EntityArtwork(iconId, job.name, 32.dp)
            Column(GlanceModifier.defaultWeight().padding(start = 8.dp)) {
                Text("${job.name}  →  ${job.level}", maxLines = 1, style = TextStyle(color = ColorProvider(Cream)))
                Text("${if (dense) "" else "${job.section.uppercase()}  ·  "}${job.countdown()}", maxLines = 1, style = TextStyle(color = ColorProvider(Green)))
            }
        }
        Spacer(GlanceModifier.height(if (dense) 3.dp else 5.dp))
    }
}

@androidx.compose.runtime.Composable
private fun EntityArtwork(resourceId: Int?, name: String, size: androidx.compose.ui.unit.Dp) {
    if (resourceId != null) {
        Image(ImageProvider(resourceId), contentDescription = name, modifier = GlanceModifier.size(size))
    } else {
        Text("?", modifier = GlanceModifier.size(size).background(Background).cornerRadius(8.dp).padding(6.dp),
            style = TextStyle(color = ColorProvider(Gold)))
    }
}

class CocWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = CocWidget() }
