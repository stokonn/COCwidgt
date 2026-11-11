package dev.stokonn.cocwidgt

import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.appwidget.AppWidgetManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import dev.stokonn.cocwidgt.data.PlayerHeaderState
import dev.stokonn.cocwidgt.data.PlayerIdentity
import dev.stokonn.cocwidgt.data.PlayerNameCache
import dev.stokonn.cocwidgt.data.ApiFailureMessage
import dev.stokonn.cocwidgt.data.ClashApi
import dev.stokonn.cocwidgt.data.PlayerSnapshot
import dev.stokonn.cocwidgt.data.StoredProfile
import dev.stokonn.cocwidgt.data.VillageExportParser
import dev.stokonn.cocwidgt.data.VillageSnapshot
import dev.stokonn.cocwidgt.security.LegacyCredentialCleanup
import dev.stokonn.cocwidgt.security.DeveloperApiKeyStore
import dev.stokonn.cocwidgt.sync.WidgetRefreshWorker
import dev.stokonn.cocwidgt.widget.CocWidget
import dev.stokonn.cocwidgt.widget.CocWidgetReceiver
import dev.stokonn.cocwidgt.widget.WidgetAppearance
import dev.stokonn.cocwidgt.widget.WidgetSettingsCodec
import dev.stokonn.cocwidgt.widget.WidgetSettingsStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private val Forest = Color(0xFF241A15)
private val Stone = Color(0xFF38281F)
private val RaisedStone = Color(0xFF493528)
private val Gold = Color(0xFFFFD36B)
private val Cream = Color(0xFFFFF2D8)
private val Muted = Color(0xFFCDB9A2)
private val Green = Color(0xFF9BCB70)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WidgetRefreshWorker.enqueue(this)
        val settings = getSharedPreferences("cocwidgt", MODE_PRIVATE)
        androidx.work.WorkManager.getInstance(this).cancelUniqueWork("cocwidgt-periodic-sync")
        androidx.work.WorkManager.getInstance(this).cancelUniqueWork("cocwidgt-manual-sync")
        LegacyCredentialCleanup.clear(this)
        val apiKeyStore = DeveloperApiKeyStore(this)
        val nameCachePrefs = getSharedPreferences("cocwidgt_player_name", MODE_PRIVATE)
        val cachedName = PlayerNameCache.decode(nameCachePrefs.all)
        val configuredTag = PlayerIdentity.normalizeTag(settings.getString("player_tag", null))
        val village = VillageSnapshot.load(this)?.takeIf {
            configuredTag == null || it.tag == configuredTag
        }
        // Per-widget background transparency settings and the ids of the placed widgets, so
        // the slider can target one widget or the shared default.
        val widgetSettings = WidgetSettingsStore(getSharedPreferences(WidgetSettingsCodec.PREFS, MODE_PRIVATE))
        val widgetIds = AppWidgetManager.getInstance(this)
            .getAppWidgetIds(ComponentName(this, CocWidgetReceiver::class.java))
            .sorted()
        val header = PlayerIdentity.headerState(
            exportTag = village?.tag,
            exportName = village?.name,
            exportTownHall = village?.townHall ?: 0,
            settingsTag = settings.getString("player_tag", null),
            profile = StoredProfile(null, null, -1, 0L, null),
        )
        val initialTag = header.expectedTag.orEmpty()
        val initialName = PlayerIdentity.displayName(
            header.expectedTag, cachedName?.tag, cachedName?.name, village?.tag, village?.name,
            settings.getString("player_name_tag", null), settings.getString("player_name", null)
        )
        val initialTownHall = header.townHall.takeIf { it > 0 }
        val lastUpdate = village?.exportedAt?.takeIf { it > 0L }?.let {
            "Village imported ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))}"
        } ?: "No village export imported yet"

        setContent {
            var tag by remember { mutableStateOf(initialTag) }
            var profileName by remember { mutableStateOf(initialName) }
            var townHall by remember { mutableStateOf(initialTownHall) }
            var exportTag by remember { mutableStateOf(village?.tag) }
            var exportName by remember { mutableStateOf(village?.name) }
            var manualName by remember {
                mutableStateOf(
                    if (PlayerIdentity.normalizeTag(settings.getString("player_name_tag", null)) == initialTag)
                        settings.getString("player_name", "").orEmpty() else ""
                )
            }
            var developerKeyInput by remember { mutableStateOf("") }
            var developerKeyConfigured by remember { mutableStateOf(apiKeyStore.hasKey()) }
            var apiLookupBusy by remember { mutableStateOf(false) }
            var apiStatus by remember { mutableStateOf(if (cachedName?.tag == initialTag && cachedName.name.isNotBlank()) "Player name cached for $initialTag" else "Player name not looked up") }
            var importMessage by remember { mutableStateOf("") }
            var importError by remember { mutableStateOf(false) }
            var importing by remember { mutableStateOf(false) }
            // Widget background transparency: null selects the shared default for all widgets,
            // a concrete AppWidgetId edits only that widget's override.
            var selectedWidgetId by remember { mutableStateOf<Int?>(null) }
            var transparency by remember { mutableIntStateOf(WidgetAppearance.clamp(widgetSettings.transparency(null))) }
            var appearanceMenuOpen by remember { mutableStateOf(false) }
            LaunchedEffect(selectedWidgetId) {
                transparency = WidgetAppearance.clamp(widgetSettings.transparency(selectedWidgetId))
            }
            var syncState by remember { mutableStateOf(if (village != null) "Local village data" else "No village imported") }
            var villageStatus by remember { mutableStateOf(lastUpdate) }

            fun refreshPlayerName(targetTag: String) {
                if (apiLookupBusy) return
                val normalized = PlayerIdentity.normalizeTag(targetTag)
                val key = apiKeyStore.read()
                if (normalized == null) { apiStatus = "Enter a valid Player Tag before lookup"; return }
                if (key.isNullOrBlank()) { apiStatus = "Save a Developer API key to look up the player name"; return }
                apiLookupBusy = true
                apiStatus = "Looking up player name…"
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { ClashApi().fetchPlayer(normalized, key) }
                    }
                    apiLookupBusy = false
                    result.onSuccess { snapshot ->
                        val values = runCatching { PlayerNameCache.encode(snapshot) }.getOrElse { error ->
                            apiStatus = ApiFailureMessage.forError(error)
                            return@onSuccess
                        }
                        nameCachePrefs.edit().putString("tag", values["tag"] as String)
                            .putString("name", values["name"] as String)
                            .putLong("fetched_at", values["fetched_at"] as Long).apply()
                        if (PlayerIdentity.normalizeTag(tag) == snapshot.tag) {
                            profileName = PlayerIdentity.displayName(
                                normalized, snapshot.tag, snapshot.name, exportTag, exportName,
                                settings.getString("player_name_tag", null), manualName
                            )
                        }
                        apiStatus = "Player name updated for ${snapshot.tag}"
                        lifecycleScope.launch { CocWidget().updateAll(this@MainActivity) }
                    }.onFailure { error ->
                        apiStatus = ApiFailureMessage.forError(error)
                    }
                }
            }

            LaunchedEffect(initialTag, developerKeyConfigured) {
                if (developerKeyConfigured && initialTag.isNotBlank() && cachedName?.tag != initialTag) {
                    refreshPlayerName(initialTag)
                }
            }


            MaterialTheme(colorScheme = darkColorScheme(
                primary = Gold, secondary = Green, background = Forest, surface = Stone,
                onPrimary = Forest, onBackground = Cream, onSurface = Cream, error = Color(0xFFFF8A78)
            )) {
                Scaffold(containerColor = Forest) { inset ->
                    Column(
                        Modifier.fillMaxSize().padding(inset).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("COCWIDGT", color = Gold, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text("Your village, at a glance.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("CLASH OF CLANS COMPANION · WIDGET FIRST", color = Muted, style = MaterialTheme.typography.labelSmall)
                        }

                        Card(colors = CardDefaults.cardColors(containerColor = RaisedStone), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text("PLAYER PROFILE", color = Gold, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text(profileName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                Text(townHall?.let { "Town Hall $it" } ?: "Town Hall not available", color = Muted, style = MaterialTheme.typography.bodyMedium)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(Modifier.size(8.dp), shape = MaterialTheme.shapes.small, color = if (syncState.startsWith("Profile synced")) Green else Gold) {}
                                    Text(syncState, color = Muted, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(villageStatus, color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Card(colors = CardDefaults.cardColors(containerColor = Stone), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SectionTitle("01", "Village data", "Import the latest in-game snapshot")
                                Text("Copy your village export in Clash of Clans, then import it from the clipboard.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                                Button(onClick = {
                                    importing = true
                                    val raw = (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                        .primaryClip?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString().orEmpty()
                                    try {
                                        val parsed = VillageExportParser.parse(raw)
                                        val configuredTag = PlayerIdentity.normalizeTag(settings.getString("player_tag", null))
                                        PlayerIdentity.requireImportMatchesConfiguredTag(configuredTag, parsed.tag)
                                        val snapshot = parsed.copy(name = PlayerIdentity.usableName(parsed.name).orEmpty())
                                            .withMergedTimers(VillageSnapshot.load(this@MainActivity))
                                        snapshot.save(this@MainActivity)
                                        tag = snapshot.tag
                                        exportTag = snapshot.tag
                                        exportName = snapshot.name
                                        settings.edit().putString("player_tag", snapshot.tag)
                                            .putString("player_name", manualName.trim())
                                            .putString("player_name_tag", snapshot.tag).apply()
                                        val cached = PlayerNameCache.decode(nameCachePrefs.all)
                                        profileName = PlayerIdentity.displayName(
                                            snapshot.tag, cached?.tag, cached?.name, snapshot.tag, snapshot.name,
                                            settings.getString("player_name_tag", null), manualName
                                        )
                                        townHall = snapshot.townHall.takeIf { it > 0 }
                                        syncState = "Local village data"
                                        villageStatus = "Imported ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(snapshot.exportedAt))} · ${snapshot.jobs.size} active upgrades"
                                        importMessage = "Village data imported. Widget updated."
                                        importError = false
                                        lifecycleScope.launch { CocWidget().updateAll(this@MainActivity) }
                                        if (developerKeyConfigured) refreshPlayerName(snapshot.tag)
                                    } catch (error: Exception) {
                                        importMessage = error.message ?: "Import failed. Check the copied JSON."
                                        importError = true
                                    } finally { importing = false }
                                }, enabled = !importing, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (importing) "Importing…" else "Paste & import village JSON")
                                }
                                if (importMessage.isNotBlank()) Text(importMessage, color = if (importError) MaterialTheme.colorScheme.error else Green, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Card(colors = CardDefaults.cardColors(containerColor = Stone), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SectionTitle("02", "Player identity", "Stored locally on this device")
                                OutlinedTextField(
                                    value = tag,
                                    onValueChange = {
                                        tag = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '#' }
                                        val normalized = PlayerIdentity.normalizeTag(tag)
                                        val cached = PlayerNameCache.decode(nameCachePrefs.all)
                                        profileName = PlayerIdentity.displayName(
                                            normalized, cached?.tag, cached?.name, exportTag, exportName,
                                            settings.getString("player_name_tag", null), manualName
                                        )
                                    },
                                    label = { Text("Player Tag") }, placeholder = { Text("#Y2099VGPJ") }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = manualName, onValueChange = {
                                        manualName = it
                                        val normalized = PlayerIdentity.normalizeTag(tag)
                                        val cached = PlayerNameCache.decode(nameCachePrefs.all)
                                        profileName = PlayerIdentity.displayName(
                                            normalized, cached?.tag, cached?.name, exportTag, exportName,
                                            normalized, it
                                        )
                                    },
                                    label = { Text("Player name (optional)") },
                                    singleLine = true, modifier = Modifier.fillMaxWidth()
                                )
                                Text("Village JSON imports include the Player Tag and upgrade data. Player name may be absent; enter it here when needed. In-game ownership tokens are not used or verified.", color = Muted, style = MaterialTheme.typography.bodySmall)
                                Button(onClick = {
                                    val normalized = PlayerIdentity.normalizeTag(tag)
                                    if (normalized == null) syncState = "Enter a valid Player Tag" else {
                                        if (PlayerIdentity.normalizeTag(exportTag) != null && PlayerIdentity.normalizeTag(exportTag) != normalized) {
                                            exportTag = null
                                            exportName = null
                                            townHall = null
                                            villageStatus = "No village export imported for this Player Tag"
                                        }
                                        tag = normalized
                                        settings.edit().putString("player_tag", normalized)
                                            .putString("player_name", manualName.trim())
                                            .putString("player_name_tag", normalized).apply()
                                        syncState = "Player identity saved locally"
                                        val cached = PlayerNameCache.decode(nameCachePrefs.all)
                                        profileName = PlayerIdentity.displayName(
                                            normalized, cached?.tag, cached?.name, exportTag, exportName, normalized, manualName
                                        )
                                        lifecycleScope.launch { CocWidget().updateAll(this@MainActivity) }
                                        if (developerKeyConfigured) refreshPlayerName(normalized)
                                    }
                                }, enabled = !importing, modifier = Modifier.fillMaxWidth()) { Text("Save player identity") }
                            }
                        }

                        Card(colors = CardDefaults.cardColors(containerColor = Stone), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SectionTitle("03", "Developer API", "Player name lookup only")
                                Text("This is a Developer API key, not the in-game Data Export ownership token. Requests go only to the official Clash of Clans API over HTTPS. Keys are restricted by allowed public IP address.", color = Muted, style = MaterialTheme.typography.bodySmall)
                                OutlinedTextField(
                                    value = developerKeyInput, onValueChange = { developerKeyInput = it },
                                    label = { Text(if (developerKeyConfigured) "Replace Developer API key" else "Developer API key") },
                                    singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(if (developerKeyConfigured) "Key is encrypted with Android Keystore on this device." else "Optional. Village import and widget data work offline without a key.", color = Muted, style = MaterialTheme.typography.bodySmall)
                                Button(onClick = {
                                    runCatching { apiKeyStore.save(developerKeyInput) }
                                        .onSuccess {
                                            developerKeyInput = ""
                                            developerKeyConfigured = true
                                            apiStatus = "Developer API key saved securely"
                                            val normalized = PlayerIdentity.normalizeTag(tag)
                                            if (normalized != null && PlayerNameCache.nameFor(nameCachePrefs.all, normalized) == null) refreshPlayerName(normalized)
                                        }
                                        .onFailure { apiStatus = "Developer API key could not be saved securely" }
                                }, modifier = Modifier.fillMaxWidth()) { Text("Save Developer API key") }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    Text(apiStatus, color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    OutlinedButton(onClick = { refreshPlayerName(tag) }, enabled = !apiLookupBusy && developerKeyConfigured) {
                                        Text(if (apiLookupBusy) "Looking up…" else "Refresh name")
                                    }
                                }
                            }
                        }

                        Card(colors = CardDefaults.cardColors(containerColor = Stone), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SectionTitle("04", "Widget appearance", "Background transparency")
                                if (widgetIds.size > 1) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Box {
                                            OutlinedButton(onClick = { appearanceMenuOpen = true }) {
                                                Text(
                                                    selectedWidgetId?.let { "Widget #$it" } ?: "All widgets (default)",
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = appearanceMenuOpen,
                                                onDismissRequest = { appearanceMenuOpen = false },
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("All widgets (default)") },
                                                    onClick = {
                                                        selectedWidgetId = null
                                                        appearanceMenuOpen = false
                                                    },
                                                )
                                                widgetIds.forEach { id ->
                                                    DropdownMenuItem(
                                                        text = { Text("Widget #$id") },
                                                        onClick = {
                                                            selectedWidgetId = id
                                                            appearanceMenuOpen = false
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                Text(
                                    "Background transparency: $transparency% · 0% opaque, 100% transparent",
                                    color = Muted, style = MaterialTheme.typography.bodySmall,
                                )
                                Slider(
                                    value = transparency.toFloat(),
                                    onValueChange = { transparency = it.toInt().coerceIn(0, 100) },
                                    onValueChangeFinished = {
                                        widgetSettings.setTransparency(selectedWidgetId, transparency)
                                        lifecycleScope.launch { CocWidget().updateAll(this@MainActivity) }
                                    },
                                    valueRange = 0f..100f,
                                    steps = 99,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    when (selectedWidgetId) {
                                        null -> "Sets the default surface for every widget. Text, icons and progress rows stay fully opaque."
                                        else -> "Overrides widget #$selectedWidgetId only; other widgets keep their own setting."
                                    },
                                    color = Muted, style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        Text("Unofficial fan app. Never enter your Supercell ID password.", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun SectionTitle(number: String, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(number, color = Gold, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
