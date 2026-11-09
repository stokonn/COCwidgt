package dev.stokonn.cocwidgt

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.stokonn.cocwidgt.data.PlayerHeaderState
import dev.stokonn.cocwidgt.data.PlayerIdentity
import dev.stokonn.cocwidgt.data.PlayerProfileCodec
import dev.stokonn.cocwidgt.data.VillageExportParser
import dev.stokonn.cocwidgt.data.VillageSnapshot
import dev.stokonn.cocwidgt.security.TokenStore
import dev.stokonn.cocwidgt.sync.SyncWorker
import dev.stokonn.cocwidgt.sync.WidgetRefreshWorker
import dev.stokonn.cocwidgt.widget.CocWidget
import kotlinx.coroutines.launch
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
        // The nickname only exists in the Clash API profile, so the automatic refresh is
        // scheduled on every launch — not only when the settings screen is opened.
        SyncWorker.enqueuePeriodic(this)
        val settings = getSharedPreferences("cocwidgt", MODE_PRIVATE)
        val player = getSharedPreferences(PlayerProfileCodec.PREFS, MODE_PRIVATE)
        val village = VillageSnapshot.load(this)
        val tokenStore = TokenStore(this)
        val storedProfile = PlayerProfileCodec.decode(player.all)
        val header = PlayerIdentity.headerState(
            exportTag = village?.tag,
            exportName = village?.name,
            exportTownHall = village?.townHall ?: 0,
            settingsTag = settings.getString("player_tag", null),
            profile = storedProfile,
        )
        val initialTag = header.expectedTag.orEmpty()
        val initialName = header.displayName
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
            var apiToken by remember { mutableStateOf("") }
            var tokenConfigured by remember { mutableStateOf(tokenStore.read() != null) }
            var importMessage by remember { mutableStateOf("") }
            var importError by remember { mutableStateOf(false) }
            var importing by remember { mutableStateOf(false) }
            var syncing by remember { mutableStateOf(false) }
            var syncState by remember { mutableStateOf(
                when {
                    initialTag.isNotBlank() && storedProfile.tag == initialTag && storedProfile.syncedAt > 0L -> "Profile synced"
                    initialTag.isNotBlank() && storedProfile.tag == initialTag -> storedProfile.syncError ?: "Profile not synced"
                    else -> "Profile not synced for this tag"
                }
            ) }
            var villageStatus by remember { mutableStateOf(lastUpdate) }

            // One shared identity rule for every header update: the display name is the stored
            // API nickname for the matching tag, else the export name, else the neutral fallback.
            fun headerFor(tagValue: String?): PlayerHeaderState = PlayerIdentity.headerState(
                exportTag = exportTag,
                exportName = exportName,
                exportTownHall = village?.townHall ?: 0,
                settingsTag = tagValue,
                profile = PlayerProfileCodec.decode(player.all),
            )

            fun startProfileSync(targetTag: String) {
                syncing = true
                syncState = "Profile sync in progress"
                enqueuePlayerSync(targetTag) { info ->
                    if (info.state.isFinished) {
                        syncing = false
                        val stored = PlayerProfileCodec.decode(player.all)
                        if (info.state == WorkInfo.State.SUCCEEDED && stored.tag == PlayerIdentity.normalizeTag(targetTag)) {
                            val state = headerFor(settings.getString("player_tag", null))
                            profileName = state.displayName
                            townHall = state.townHall.takeIf { it > 0 }
                            syncState = "Profile synced"
                        } else {
                            syncState = stored.syncError ?: "Profile sync failed"
                        }
                    }
                }
            }

            // The village export carries no player name, so the nickname can only come from the
            // API profile. Pull it automatically whenever it is missing or older than 6 hours.
            LaunchedEffect(Unit) {
                val token = tokenStore.read()
                val stored = PlayerProfileCodec.decode(player.all)
                val target = PlayerIdentity.firstValidTag(
                    settings.getString("player_tag", null), stored.tag, village?.tag
                )
                if (!token.isNullOrBlank() && target != null) {
                    val hasName = stored.tag == target && !stored.name.isNullOrBlank()
                    val stale = stored.syncedAt == 0L || System.currentTimeMillis() - stored.syncedAt > 6 * 60 * 60 * 1000L
                    if (!hasName || stale) startProfileSync(target)
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
                                    Surface(Modifier.size(8.dp), shape = MaterialTheme.shapes.small, color = if (syncState == "Profile synced") Green else Gold) {}
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
                                    var syncTag: String? = null
                                    val raw = (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                        .primaryClip?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString().orEmpty()
                                    try {
                                        val snapshot = VillageExportParser.parse(raw)
                                            .withMergedTimers(VillageSnapshot.load(this@MainActivity))
                                        snapshot.save(this@MainActivity)
                                        tag = snapshot.tag
                                        exportTag = snapshot.tag
                                        exportName = snapshot.name
                                        val state = PlayerIdentity.headerState(
                                            exportTag = snapshot.tag,
                                            exportName = snapshot.name,
                                            exportTownHall = snapshot.townHall,
                                            settingsTag = snapshot.tag,
                                            profile = PlayerProfileCodec.decode(player.all),
                                        )
                                        profileName = state.displayName
                                        townHall = state.townHall.takeIf { it > 0 }
                                        settings.edit().putString("player_tag", snapshot.tag).apply()
                                        villageStatus = "Imported ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(snapshot.exportedAt))} · ${snapshot.jobs.size} active upgrades"
                                        importMessage = "Village data imported. Widget updated."
                                        importError = false
                                        syncTag = snapshot.tag.takeIf { !tokenStore.read().isNullOrBlank() }
                                        lifecycleScope.launch { CocWidget().updateAll(this@MainActivity) }
                                    } catch (error: Exception) {
                                        importMessage = error.message ?: "Import failed. Check the copied JSON."
                                        importError = true
                                    } finally { importing = false }
                                    syncTag?.let(::startProfileSync)
                                }, enabled = !importing && !syncing, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (importing) "Importing…" else "Paste & import village JSON")
                                }
                                if (importMessage.isNotBlank()) Text(importMessage, color = if (importError) MaterialTheme.colorScheme.error else Green, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Card(colors = CardDefaults.cardColors(containerColor = Stone), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SectionTitle("02", "API connection", "Keep profile details in sync")
                                OutlinedTextField(
                                    value = tag,
                                    onValueChange = {
                                        tag = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '#' }
                                        val normalized = PlayerIdentity.normalizeTag(tag)
                                        val stored = PlayerProfileCodec.decode(player.all)
                                        // Preview resolves strictly against the tag being typed.
                                        profileName = PlayerIdentity.matchingName(
                                            normalized, stored.tag, stored.name, exportTag, exportName
                                        ) ?: PlayerHeaderState.FALLBACK_NAME
                                        if (normalized != stored.tag) syncState = "Profile not synced for this tag"
                                    },
                                    label = { Text("Player Tag") }, placeholder = { Text("#Y2099VGPJ") }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                                    enabled = !syncing,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = apiToken, onValueChange = { apiToken = it },
                                    label = { Text(if (tokenConfigured) "Replace API token (optional)" else "Supercell API token") },
                                    singleLine = true, enabled = !syncing, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
                                )
                                Text(if (tokenConfigured) "Token encrypted on this device with Android Keystore." else "Create a developer token and allowlist your public IP.", color = Muted, style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    Button(onClick = {
                                        val normalized = PlayerIdentity.normalizeTag(tag)
                                        if (normalized == null) {
                                            syncState = "Enter a valid Player Tag"
                                        } else {
                                            tag = normalized
                                            if (apiToken.isNotBlank()) {
                                                if (runCatching { tokenStore.save(apiToken) }.isFailure) {
                                                    syncState = "Token could not be encrypted. Try again."
                                                    return@Button
                                                }
                                                apiToken = ""
                                                tokenConfigured = true
                                            }
                                            settings.edit().putString("player_tag", normalized).apply()
                                            SyncWorker.enqueuePeriodic(this@MainActivity)
                                            syncState = "Settings saved"
                                            lifecycleScope.launch { CocWidget().updateAll(this@MainActivity) }
                                        }
                                    }, enabled = !importing && !syncing, modifier = Modifier.weight(1f)) { Text("Save settings") }
                                    OutlinedButton(onClick = {
                                        val normalized = PlayerIdentity.normalizeTag(tag)
                                        if (normalized == null) {
                                            syncState = "Enter a valid Player Tag"
                                        } else if (tokenStore.read().isNullOrBlank() && apiToken.isBlank()) {
                                            syncState = "Add an API token first"
                                        } else {
                                            if (apiToken.isNotBlank()) {
                                                if (runCatching { tokenStore.save(apiToken) }.isFailure) {
                                                    syncState = "Token could not be encrypted. Try again."
                                                    return@OutlinedButton
                                                }
                                                apiToken = ""
                                                tokenConfigured = true
                                            }
                                            settings.edit().putString("player_tag", normalized).apply()
                                            startProfileSync(normalized)
                                        }
                                    }, enabled = !importing && !syncing, modifier = Modifier.weight(1f)) { Text(if (syncing) "Syncing…" else "Sync now") }
                                }
                            }
                        }
                        Text("Unofficial fan app. Never enter your Supercell ID password.", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
            }
        }
    }
}

private fun MainActivity.enqueuePlayerSync(tag: String, onUpdate: (WorkInfo) -> Unit) {
    // The tag travels with the request, so SyncWorker runs against exactly this player.
    val request = SyncWorker.request(tag)
    val workManager = WorkManager.getInstance(this)
    workManager.enqueueUniqueWork("cocwidgt-manual-sync", ExistingWorkPolicy.REPLACE, request)
    workManager.getWorkInfoByIdLiveData(request.id).observe(this) { info -> info?.let(onUpdate) }
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
