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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.unit.dp
import dev.stokonn.cocwidgt.MainActivity
import dev.stokonn.cocwidgt.data.VillageSnapshot
import java.text.DateFormat
import java.util.Date

private val Panel=Color(0xFF654B3C)
private val Gold=Color(0xFFFFD66E)
private val White=Color(0xFFFFF8EC)
private val Muted=Color(0xFFD6C5B2)
private val Green=Color(0xFF83C85B)
private val Track=Color(0xFF302219)

class CocWidget:GlanceAppWidget() {
 override suspend fun provideGlance(context:Context,id:GlanceId) {
  provideContent {
   val ctx=LocalContext.current
   val profile=ctx.getSharedPreferences("cocwidgt_player",Context.MODE_PRIVATE)
   val village=VillageSnapshot.load(ctx)
   val tag=village?.tag?:profile.getString("tag",null)?:profile.getString("player_tag",null)?:"SET PLAYER TAG"
   val playerName=profile.getString("name",null)
   val th=village?.townHall?.takeIf{it>0}?:profile.getInt("town_hall",-1)
   val trophies=profile.getInt("trophies",-1)
   val exp=profile.getInt("exp_level",-1)
   val now=System.currentTimeMillis()
   val jobs=village?.jobs.orEmpty().filter{it.remainingMs(now)>0}.sortedBy{it.remainingMs(now)}
   Column(GlanceModifier.fillMaxSize().background(Panel).cornerRadius(22.dp).padding(12.dp)
    .clickable(actionStartActivity<MainActivity>()),verticalAlignment=Alignment.Vertical.Top) {
    Row(GlanceModifier.fillMaxWidth(),verticalAlignment=Alignment.Vertical.CenterVertically) {
     Column { Text(playerName?:village?.name?.takeIf{it.isNotBlank()}?:tag,style=TextStyle(color=ColorProvider(White)))
      Text("$tag  ${if(th>0) "TH$th" else ""}",style=TextStyle(color=ColorProvider(Gold))) }
     Text("↻ Sync",style=TextStyle(color=ColorProvider(Gold)))
    }
    Spacer(GlanceModifier.height(8.dp))
    if(village==null) {
     Text("IMPORT VILLAGE JSON",style=TextStyle(color=ColorProvider(Gold)))
     Text("In game: Settings → More Settings → Data Export",style=TextStyle(color=ColorProvider(White)))
     Text("Open COCwidgt and paste your export.",style=TextStyle(color=ColorProvider(Muted)))
    } else {
     Text("BUILDERS ${village.builders}/6",style=TextStyle(color=ColorProvider(Gold)))
     jobs.filter{it.section=="Builders"||it.section=="Builder Base"}.take(7).forEach{UpgradeRow(it.name,it.level,it.countdown(now),it.progress(now))}
     Text("LAB & PETS",style=TextStyle(color=ColorProvider(Gold)))
     jobs.filter{it.section=="Laboratory"||it.section=="Pets"||it.section=="Star Laboratory"}.take(3)
      .forEach{UpgradeRow(it.name,it.level,it.countdown(now),it.progress(now))}
     Text("Export ${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(village.exportedAt))}",
      style=TextStyle(color=ColorProvider(Muted)))
    }
    if(trophies>=0||exp>=0)Text("Trophies ${if(trophies>=0)trophies else "—"}  ·  XP ${if(exp>=0)exp else "—"}",style=TextStyle(color=ColorProvider(White)))
    Text("Tap to open · local countdowns",style=TextStyle(color=ColorProvider(Muted)))
   }
  }
 }
}
@androidx.compose.runtime.Composable
private fun UpgradeRow(name:String,level:Int,time:String,progress:Float) {
 Column(GlanceModifier.fillMaxWidth().padding(vertical=3.dp)) {
  Row(GlanceModifier.fillMaxWidth(),verticalAlignment=Alignment.Vertical.CenterVertically) {
   Text("$name → $level",style=TextStyle(color=ColorProvider(White)))
   Text(time,style=TextStyle(color=ColorProvider(Gold)))
  }
  Row(GlanceModifier.fillMaxWidth().height(6.dp).background(Track).cornerRadius(4.dp)) {
   repeat(20) { index ->
    Spacer(GlanceModifier.width(7.dp).height(6.dp).background(if(index < (progress * 20).toInt()) Green else Track))
   }
  }
 }
}
class CocWidgetReceiver:GlanceAppWidgetReceiver(){override val glanceAppWidget:GlanceAppWidget=CocWidget()}
