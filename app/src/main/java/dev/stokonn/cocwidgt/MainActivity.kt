package dev.stokonn.cocwidgt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.stokonn.cocwidgt.security.TokenStore
import dev.stokonn.cocwidgt.sync.SyncWorker
import dev.stokonn.cocwidgt.widget.CocWidget
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("cocwidgt", MODE_PRIVATE)
        val tokenStore = TokenStore(this)

        setContent {
            var tag by remember { mutableStateOf(prefs.getString("player_tag", "").orEmpty()) }
            var apiToken by remember { mutableStateOf("") }
            var tokenConfigured by remember { mutableStateOf(tokenStore.read() != null) }

            MaterialTheme {
                Scaffold { contentPadding ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(contentPadding).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("COCwidgt", style = MaterialTheme.typography.headlineLarge)
                        Text("Clash of Clans · Widget setup")
                        OutlinedTextField(
                            value = tag,
                            onValueChange = { input -> tag = input.uppercase().filter { it.isLetterOrDigit() || it == '#' } },
                            label = { Text("Player Tag") },
                            placeholder = { Text("#Y2099VGPJ") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = apiToken,
                            onValueChange = { apiToken = it },
                            label = { Text(if (tokenConfigured) "Replace API token (optional)" else "Supercell API token") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(if (tokenConfigured) "API token is encrypted with Android Keystore." else "Create a token in the official developer portal and allowlist your public IP.")
                        Button(
                            onClick = {
                                prefs.edit().putString("player_tag", tag.trim()).apply()
                                if (apiToken.isNotBlank()) {
                                    tokenStore.save(apiToken)
                                    apiToken = ""
                                    tokenConfigured = true
                                }
                                WorkManager.getInstance(this@MainActivity).enqueueUniquePeriodicWork(
                                    "cocwidgt-periodic-sync",
                                    ExistingPeriodicWorkPolicy.UPDATE,
                                    PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build()
                                )
                                lifecycleScope.launch { CocWidget().updateAll(this@MainActivity) }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Save settings") }
                        Button(
                            onClick = {
                                WorkManager.getInstance(this@MainActivity).enqueueUniqueWork(
                                    "cocwidgt-manual-sync",
                                    ExistingWorkPolicy.REPLACE,
                                    OneTimeWorkRequestBuilder<SyncWorker>().build()
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Sync now") }
                        Text(
                            "The token is encrypted on this device and is not committed to GitHub. " +
                                "Supercell requests require the public IP address making the request to be allowlisted. " +
                                "Never enter your Supercell ID password.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
