package dev.stokonn.cocwidgt.data
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class VillageJob(val id:String,val name:String,val section:String,val level:Int,val startedAt:Long,val durationMs:Long,val goblin:Boolean=false) {
 fun remainingMs(now:Long=System.currentTimeMillis())=(startedAt+durationMs-now).coerceAtLeast(0L)
 fun progress(now:Long=System.currentTimeMillis()):Float=if(durationMs<=0)1f else (1f-remainingMs(now).toFloat()/durationMs).coerceIn(0f,1f)
 fun countdown(now:Long=System.currentTimeMillis()):String { val m=(remainingMs(now)+59999L)/60000L;if(m<=0)return "Ready";val d=m/1440;val h=(m%1440)/60;val min=m%60;return buildString{if(d>0)append(d).append("d ");if(h>0)append(h).append("h ");append(min).append("m")} }
}
data class VillageSnapshot(val tag:String,val name:String,val townHall:Int,val builders:Int,val exportedAt:Long,val jobs:List<VillageJob>) {
 fun save(context:Context) { val a=JSONArray();jobs.forEach{u->a.put(JSONObject().apply{put("id",u.id);put("name",u.name);put("section",u.section);put("level",u.level);put("startedAt",u.startedAt);put("durationMs",u.durationMs);put("goblin",u.goblin)})};context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("tag",tag).putString("name",name).putInt("townHall",townHall).putInt("builders",builders).putLong("exportedAt",exportedAt).putString("jobs",a.toString()).apply() }
 companion object { const val PREFS="cocwidgt_village"
  fun load(context:Context):VillageSnapshot? { val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);val tag=p.getString("tag",null)?:return null;val a=runCatching{JSONArray(p.getString("jobs","[]"))}.getOrNull()?:JSONArray();val jobs=(0 until a.length()).mapNotNull{i->runCatching{val j=a.getJSONObject(i);VillageJob(j.getString("id"),j.getString("name"),j.getString("section"),j.optInt("level"),j.getLong("startedAt"),j.getLong("durationMs"),j.optBoolean("goblin"))}.getOrNull()};return VillageSnapshot(tag,p.getString("name","").orEmpty(),p.getInt("townHall",0),p.getInt("builders",0),p.getLong("exportedAt",0),jobs) }
 }
}
object VillageExportParser {
 private val sections=listOf("buildings" to "Builders","traps" to "Builders","heroes" to "Builders","units" to "Laboratory","siege_machines" to "Laboratory","spells" to "Laboratory","pets" to "Pets","buildings2" to "Builder Base","traps2" to "Builder Base","heroes2" to "Builder Base","units2" to "Star Laboratory")
 fun parse(raw:String):VillageSnapshot {
  require(raw.length<=1_000_000){"Export je příliš velký (maximum 1 MB)."}
  val root=runCatching{JSONObject(raw.trim())}.getOrElse{throw IllegalArgumentException("Tohle není platný JSON export vesnice.")}
  val tag=root.optString("tag").trim().uppercase(Locale.ROOT);require(tag.matches(Regex("#[A-Z0-9]{3,20}"))){"V exportu chybí platný Player Tag."}
  val stamp=root.optLong("timestamp",System.currentTimeMillis()/1000L)*1000L;require(stamp<=System.currentTimeMillis()+300000L){"Export má datum v budoucnosti."}
  val buildings=root.optJSONArray("buildings");require(buildings!=null){"V JSON chybí sekce buildings – nejde o export vesnice."}
  val hall=findLevel(buildings,1000001L);val builders=findCount(buildings,1000015L).coerceIn(1,6);val jobs=mutableListOf<VillageJob>()
  sections.forEach{pair->val a=root.optJSONArray(pair.first)?:return@forEach;for(i in 0 until a.length()){val item=a.optJSONObject(i)?:continue;val sec=item.optLong("timer",0);if(sec<=0)continue;require(sec<=366L*86400){"Čas upgradu je mimo podporovaný rozsah."};val data=item.optLong("data",-1);val name=item.optString("name").takeIf{it.isNotBlank()}?:item.optString("type").takeIf{it.isNotBlank()}?:"Item $data";jobs+=VillageJob(pair.first+":"+i,name,pair.second,item.optInt("lvl")+1,stamp,sec*1000,item.optBoolean("extra"))}}
  require(jobs.size<=300){"Export obsahuje příliš mnoho aktivních upgradu."}
  return VillageSnapshot(tag,root.optString("name",""),hall,builders,stamp,jobs)
 }
 private fun findLevel(a:JSONArray,id:Long):Int{for(i in 0 until a.length()){val x=a.optJSONObject(i)?:continue;if(x.optLong("data")==id)return x.optInt("lvl")};return 0}
 private fun findCount(a:JSONArray,id:Long):Int{var n=0;for(i in 0 until a.length()){val x=a.optJSONObject(i)?:continue;if(x.optLong("data")==id)n+=x.optInt("cnt",1)};return n}
}
