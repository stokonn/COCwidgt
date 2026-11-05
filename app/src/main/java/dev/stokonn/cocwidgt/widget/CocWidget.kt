package dev.stokonn.cocwidgt.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.defaultWeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.unit.dp
import dev.stokonn.cocwidgt.MainActivity
import java.text.DateFormat
import java.util.Date

private val Panel = Color(0xFF49352B)
private val Gold = Color(0xFFF0CE78)
private val White = Color(0xFFFFF8EC)
private val Muted = Color(0xFFD6C5B2)
private val Green = Color(0xFF83C85B)

class CocWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val context = LocalContext.current
            val settings = context.getSharedPreferences("cocwidgt", Context.MODE_PRIVATE)
            val data = context.getSharedPreferences("cocwidgt_player", Context.MODE_PRIVATE)
            val tag = settings.getString("player_tag", null)?.ifBlank { null } ?: "SET PLAYER TAG"
            val name = data.getString("name", null)
            val th = data.getInt("town_hall", -1)
            val trophies = data.getInt("trophies", -1)
            val exp = data.getInt("exp_level", -1)
            val heroes = data.getString("heroes", "").orEmpty().split("|").filter { it.isNotBlank() }
            val syncedAt = data.getLong("synced_at", 0L)
            val error = data.getString("sync_error", "").orEmpty()

            Column(
                modifier = GlanceModifier.fillMaxSize().background(Panel).cornerRadius(22.dp)
                    .padding(14.dp).clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.Vertical.Top
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(name ?: tag, style = TextStyle(color = ColorProvider(White)))
                        Text(buildString {
                            append(tag)
                            if (th > 0) append("  ·  TH $th")
                        }, style = TextStyle(color = ColorProvider(Gold)))
                    }
                    Text("OPEN", style = TextStyle(color = ColorProvider(Gold)))
                }
                Spacer(GlanceModifier.height(10.dp))
                Text("PLAYER", style = TextStyle(color = ColorProvider(Gold)))
                WidgetRow("Trophies", if (trophies >= 0) trophies.toString() else "—")
                WidgetRow("Experience", if (exp >= 0) "Level $exp" else "—")
                Spacer(GlanceModifier.height(7.dp))
                Text("HEROES", style = TextStyle(color = ColorProvider(Gold)))
                if (heroes.isEmpty()) {
                    Text("Waiting for first sync", style = TextStyle(color = ColorProvider(Muted)))
                } else {
                    heroes.take(4).forEach { hero ->
                        WidgetRow(hero.substringBefore(" · "), hero.substringAfter(" · ", "—"))
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
                if (error.isNotBlank()) Text(error, style = TextStyle(color = ColorProvider(Gold)))
                val syncLabel = if (syncedAt > 0) {
                    "Synced ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(syncedAt))}"
                } else "Not synced yet"
                Text(syncLabel, style = TextStyle(color = ColorProvider(Muted)))
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun WidgetRow(label: String, value: String) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text(label, modifier = GlanceModifier.defaultWeight(), style = TextStyle(color = ColorProvider(White)))
        Text(value, style = TextStyle(color = ColorProvider(Green)))
    }
}

class CocWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CocWidget()
}
