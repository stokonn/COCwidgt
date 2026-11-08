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
import dev.stokonn.cocwidgt.data.PlayerIdentity
import dev.stokonn.cocwidgt.data.VillageJob
import dev.stokonn.cocwidgt.data.VillageSnapshot
import java.text.DateFormat
import java.util.Date

private val Background = Color(0xFF281D17)
private val Frame = Color(0xFF493428)
private val Panel = Color(0xFF39281F)
private val Gold = Color(0xFFFFD36B)
private val Cream = Color(0xFFFFF2D8)
private val Muted = Color(0xFFCDB9A2)
private val Green = Color(0xFF9BCB70)

class CocWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(
        DpSize(180.dp, 110.dp), DpSize(250.dp, 180.dp), DpSize(320.dp, 220.dp),
        DpSize(360.dp, 280.dp), DpSize(480.dp, 360.dp), DpSize(480.dp, 600.dp)
    ))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val ctx = LocalContext.current
            val size = LocalSize.current
            val village = VillageSnapshot.load(ctx)
            val profile = ctx.getSharedPreferences("cocwidgt_player", Context.MODE_PRIVATE)
            val profileTag = PlayerIdentity.normalizeTag(profile.getString("tag", null) ?: profile.getString("player_tag", null))
            val expectedTag = PlayerIdentity.normalizeTag(village?.tag ?: ctx.getSharedPreferences("cocwidgt", Context.MODE_PRIVATE).getString("player_tag", null)) ?: profileTag
            val tag = expectedTag ?: profileTag ?: "ADD PLAYER TAG"
            val playerName = PlayerIdentity.matchingName(expectedTag, profileTag, profile.getString("name", null), village?.tag, village?.name) ?: "Player profile"
            val profileMatches = expectedTag != null && profileTag == expectedTag
            val th = village?.townHall?.takeIf { it > 0 } ?: profile.getInt("town_hall", -1).takeIf { profileMatches } ?: -1
            val apiSyncedAt = if (profileMatches) profile.getLong("synced_at", 0L) else 0L
            val apiError = if (profileMatches) profile.getString("sync_error", "").orEmpty() else ""
            val now = System.currentTimeMillis()
            val selection = WidgetContentPlanner.select(village?.jobs.orEmpty(), size.width.value.toInt(), size.height.value.toInt(), now)
            val compact = selection.compact

            Column(
                GlanceModifier.fillMaxSize().background(Background).cornerRadius(20.dp)
                    .padding(if (compact) 10.dp else 13.dp).clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.Vertical.Top
            ) {
                PlayerHeader(playerName, tag, th)
                if (village == null) {
                    Spacer(GlanceModifier.height(6.dp))
                    Text("IMPORT VILLAGE DATA", style = TextStyle(color = ColorProvider(Gold)))
                    Text("Open COCwidgt to add an export.", maxLines = 1, style = TextStyle(color = ColorProvider(Muted)))
                } else if (compact) {
                    Spacer(GlanceModifier.height(4.dp))
                    val activeCount = village.jobs.count { it.remainingMs(now) > 0 }
                    val stale = now - village.exportedAt > 24 * 60 * 60 * 1000L
                    val more = if (selection.hiddenCount > 0) " · +${selection.hiddenCount}" else ""
                    Text("$activeCount ACTIVE$more${if (stale) " · STALE" else " · EXPORT"}", maxLines = 1, style = TextStyle(color = ColorProvider(Gold)))
                    selection.items.firstOrNull()?.let { CompactUpgrade(it) }
                } else {
                    Spacer(GlanceModifier.height(7.dp))
                    selection.items.forEach { UpgradeCard(it, size.width.value >= 420f) }
                    if (selection.hiddenCount > 0) {
                        Text("+${selection.hiddenCount} more active upgrades · open app", maxLines = 1,
                            style = TextStyle(color = ColorProvider(Gold)))
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    val stale = now - village.exportedAt > 24 * 60 * 60 * 1000L
                    val exported = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(village.exportedAt))
                    Text("${if (stale) "STALE · " else "EXPORTED · "}$exported", maxLines = 1,
                        style = TextStyle(color = ColorProvider(if (stale) Gold else Muted)))
                    if (size.width >= 420.dp && size.height >= 360.dp) {
                        val apiStatus = when {
                            !profileMatches -> "API · not synced for this player"
                            apiError.isNotBlank() -> "API · $apiError"
                            apiSyncedAt > 0 -> "API synced · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(apiSyncedAt))}"
                            else -> "API · not synced"
                        }
                        Text(apiStatus, maxLines = 1, style = TextStyle(color = ColorProvider(Muted)))
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun PlayerHeader(name: String, tag: String, townHall: Int) {
    Row(
        GlanceModifier.fillMaxWidth().background(Frame).cornerRadius(13.dp).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Column(GlanceModifier.defaultWeight()) {
            Text(name, maxLines = 1, style = TextStyle(color = ColorProvider(Cream)))
            Text("$tag${if (townHall > 0) "  ·  TH$townHall" else ""}", maxLines = 1,
                style = TextStyle(color = ColorProvider(Gold)))
        }
        Text("VILLAGE", style = TextStyle(color = ColorProvider(Muted)))
    }
}

@androidx.compose.runtime.Composable
private fun UpgradeCard(job: VillageJob, wide: Boolean) {
    Row(
        GlanceModifier.fillMaxWidth().padding(bottom = 5.dp).background(Panel).cornerRadius(11.dp)
            .padding(horizontal = 8.dp, vertical = if (wide) 5.dp else 7.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        EntityArtwork(job, if (wide) 30.dp else 34.dp)
        Column(GlanceModifier.defaultWeight().padding(start = 8.dp)) {
            Text("${job.name}  ·  ${job.level}", maxLines = 1, style = TextStyle(color = ColorProvider(Cream)))
            Text("${job.section.uppercase()}  ·  ${job.countdown()}", maxLines = 1,
                style = TextStyle(color = ColorProvider(Green)))
        }
    }
}

@androidx.compose.runtime.Composable
private fun CompactUpgrade(job: VillageJob) {
    Row(GlanceModifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
        EntityArtwork(job, 23.dp)
        Text("${job.name} · ${job.countdown()}", maxLines = 1,
            modifier = GlanceModifier.defaultWeight().padding(start = 5.dp), style = TextStyle(color = ColorProvider(Cream)))
    }
}

@androidx.compose.runtime.Composable
private fun EntityArtwork(job: VillageJob, size: androidx.compose.ui.unit.Dp) {
    val resourceId = GameArtwork.drawableResId(job.dataId)
    if (resourceId != null) {
        Image(ImageProvider(resourceId), contentDescription = job.name, modifier = GlanceModifier.size(size))
    } else {
        Text("?", modifier = GlanceModifier.size(size).background(Frame).cornerRadius(8.dp).padding(4.dp),
            style = TextStyle(color = ColorProvider(Gold)))
    }
}

class CocWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = CocWidget() }
