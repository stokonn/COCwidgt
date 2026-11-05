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
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import dev.stokonn.cocwidgt.widget.CocWidget

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = getSharedPreferences("cocwidgt", MODE_PRIVATE)

        setContent {
            var playerTag by remember {
                mutableStateOf(preferences.getString("player_tag", "").orEmpty())
            }

            MaterialTheme {
                Scaffold { contentPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("COCwidgt", style = MaterialTheme.typography.headlineLarge)
                        Text("Clash of Clans · Widget setup")
                        OutlinedTextField(
                            value = playerTag,
                            onValueChange = { input ->
                                playerTag = input.uppercase().filter {
                                    it.isLetterOrDigit() || it == '#'
                                }
                            },
                            label = { Text("Player Tag") },
                            placeholder = { Text("#Y2099VGPJ") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                preferences.edit()
                                    .putString("player_tag", playerTag.trim())
                                    .apply()
                                CocWidget().updateAll(this@MainActivity)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Player Tag")
                        }
                        Text(
                            "This foundation build does not sync game data yet. API integration " +
                                "will be added in the next milestone. Never enter your Supercell ID password here."
                        )
                    }
                }
            }
        }
    }
}
