package me.eternal.purrfect.ui.manager.pages.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import me.eternal.purrfect.common.ui.cardShape
import me.eternal.purrfect.common.ui.rememberMutableStringPreferenceState
import me.eternal.purrfect.common.ui.screenContentPadding
import me.eternal.purrfect.ui.components.LazyColumnBottomSheet
import me.eternal.purrfect.ui.manager.Routes

class HomeUpdating : Routes.Section() {
    override val content: @Composable (NavBackStackEntry) -> Unit = {
        val updateSettings = context.config.root.global.updateSettings
        var autoUpdateCheck by remember { mutableStateOf(updateSettings.autoUpdateCheck.get()) }
        var checkFrequency by rememberMutableStringPreferenceState(
            sharedPreferences = context.sharedPreferences,
            key = "update_check_frequency",
            defaultValue = "daily"
        )
        var showFrequencySheet by remember { mutableStateOf(false) }

        val frequencies = listOf("daily", "weekly", "monthly")

        if (showFrequencySheet) {
            LazyColumnBottomSheet(
                onDismiss = { showFrequencySheet = false }
            ) {
                itemsIndexed(frequencies) { index, freq ->
                    fun select() {
                        checkFrequency = freq
                        showFrequencySheet = false
                    }
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        shape = cardShape(frequencies.size, index)
                    ) {
                        ListItem(
                            modifier = Modifier.clickable { select() },
                            headlineContent = { Text(text = freq.replaceFirstChar { it.uppercase() }) },
                            trailingContent = {
                                RadioButton(
                                    selected = checkFrequency == freq,
                                    onClick = { select() }
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = screenContentPadding(),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardShape(2, 0)
                ) {
                    ListItem(
                        modifier = Modifier.clickable {
                            autoUpdateCheck = !autoUpdateCheck
                            updateSettings.autoUpdateCheck.set(autoUpdateCheck)
                            context.config.writeConfig()
                        },
                        headlineContent = {
                            Text(text = "Auto Update Check", fontWeight = FontWeight.SemiBold)
                        },
                        supportingContent = {
                            Text(text = "Automatically check for new updates on launch", fontSize = 12.sp)
                        },
                        trailingContent = {
                            Switch(
                                checked = autoUpdateCheck,
                                onCheckedChange = {
                                    autoUpdateCheck = it
                                    updateSettings.autoUpdateCheck.set(it)
                                    context.config.writeConfig()
                                }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardShape(2, 1)
                ) {
                    ListItem(
                        modifier = Modifier.clickable(enabled = autoUpdateCheck) {
                            showFrequencySheet = true
                        },
                        headlineContent = {
                            Text(text = "Update Check Frequency", fontWeight = FontWeight.SemiBold)
                        },
                        supportingContent = {
                            Text(text = checkFrequency.replaceFirstChar { it.uppercase() }, fontSize = 12.sp)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}
