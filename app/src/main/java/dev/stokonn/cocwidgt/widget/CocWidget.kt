package dev.stokonn.cocwidgt.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.Color
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.defaultWeight
import androidx.glance.layout.width
import dev.stokonn.cocwidgt.MainActivity

private val Panel = Color(0xFF49352B)
private val Gold = Color(0xFFF0CE78)
private val White = Color(0xFFFFF8EC)
private val Muted = Color(0xFFD6C5B2)
private val Green = Color(0xFF83C85B)

class CocWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val context = LocalContext.current
            val tag = context.getSharedPreferences("cocwidgt", Context.MODE_PRIVATE)
                .getString("player_tag", null)
                ?.takeIf { it.isNotBlank() }
                ?: "SET PLAYER TAG"

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Panel)
                    .cornerRadius(22)
                    .padding(14.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.Vertical.Top
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(tag, style = TextStyle(color = ColorProvider(White)))
                        Text("CLASH OF CLANS", style = TextStyle(color = ColorProvider(Gold)))
                    }
                    Text("SYNC", style = TextStyle(color = ColorProvider(Gold)))
                }
                Spacer(GlanceModifier.height(14.dp))
                Text("PLAYER OVERVIEW", style = TextStyle(color = ColorProvider(Gold)))
                Spacer(GlanceModifier.height(8.dp))
                WidgetRow("Profile", "Awaiting API")
                WidgetRow("Heroes & pets", "Not synced")
                WidgetRow("Troops & spells", "Not synced")
                Spacer(GlanceModifier.defaultWeight())
                Text("0.1.0 · API integration planned", style = TextStyle(color = ColorProvider(Muted)))
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun WidgetRow(label: String, value: String) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text(label, modifier = GlanceModifier.defaultWeight(), style = TextStyle(color = ColorProvider(White)))
        Text(value, style = TextStyle(color = ColorProvider(Muted)))
    }
}

class CocWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CocWidget()
}
