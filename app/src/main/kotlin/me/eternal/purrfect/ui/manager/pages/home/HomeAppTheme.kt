package me.eternal.purrfect.ui.manager.pages.home

import android.content.Context
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import me.eternal.purrfect.common.ui.ThemeMode
import me.eternal.purrfect.common.ui.cardShapeSingle
import me.eternal.purrfect.common.ui.rememberMutableBooleanPreferenceState
import me.eternal.purrfect.common.ui.rememberMutableStringPreferenceState
import me.eternal.purrfect.common.ui.screenContentPadding
import me.eternal.purrfect.common.ui.theme.Themes
import me.eternal.purrfect.ui.components.ThemeBox
import me.eternal.purrfect.ui.manager.Routes

class HomeAppTheme : Routes.Section() {
    override val content: @Composable (NavBackStackEntry) -> Unit = {
        val localContext = LocalContext.current
        val sharedPreferences = remember {
            localContext.getSharedPreferences("appearance_preferences", Context.MODE_PRIVATE)
        }

        var selectedTheme by rememberMutableStringPreferenceState(
            sharedPreferences = sharedPreferences,
            key = "app_theme",
            defaultValue = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "dynamic" else "default"
        )
        var amoledMode by rememberMutableBooleanPreferenceState(
            sharedPreferences = sharedPreferences,
            key = "amoled_theme",
            defaultValue = false
        )
        var themeModeStr by rememberMutableStringPreferenceState(
            sharedPreferences = sharedPreferences,
            key = "theme_mode",
            defaultValue = ThemeMode.SYSTEM.name
        )

        val currentThemeMode = runCatching { ThemeMode.valueOf(themeModeStr) }.getOrDefault(ThemeMode.SYSTEM)
        val palettes = remember(localContext) { Themes.getPalettes(localContext) }
        val modeOptions = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = screenContentPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardShapeSingle
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Theme Mode",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            modeOptions.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = currentThemeMode == mode,
                                    onClick = {
                                        themeModeStr = mode.name
                                        context.activity?.recreate()
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = modeOptions.size
                                    )
                                ) {
                                    Text(text = mode.displayName)
                                }
                            }
                        }
                    }
                }
            }

            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardShapeSingle
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Color Palette",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(palettes, key = { it.key }) { palette ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ThemeBox(
                                        selected = selectedTheme == palette.key,
                                        theme = palette,
                                        themeMode = currentThemeMode,
                                        isAmoled = amoledMode,
                                        onClick = {
                                            selectedTheme = palette.key
                                            context.activity?.recreate()
                                        }
                                    )
                                    Text(
                                        text = palette.key.replace("_", " ").replaceFirstChar { it.uppercase() },
                                        fontSize = 12.sp,
                                        fontWeight = if (selectedTheme == palette.key) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardShapeSingle
                ) {
                    ListItem(
                        modifier = Modifier.clickable {
                            amoledMode = !amoledMode
                            context.activity?.recreate()
                        },
                        headlineContent = {
                            Text(text = "Pure Black (AMOLED) Mode", fontWeight = FontWeight.SemiBold)
                        },
                        supportingContent = {
                            Text(text = "Use pure black background in dark theme", fontSize = 12.sp)
                        },
                        trailingContent = {
                            Switch(
                                checked = amoledMode,
                                onCheckedChange = {
                                    amoledMode = it
                                    context.activity?.recreate()
                                }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}
