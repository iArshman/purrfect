package me.eternal.purrfect.ui.manager.pages.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import me.eternal.purrfect.common.bridge.wrapper.LocaleWrapper
import me.eternal.purrfect.common.ui.cardShape
import me.eternal.purrfect.common.ui.screenContentPadding
import me.eternal.purrfect.ui.components.LazyColumnBottomSheet
import me.eternal.purrfect.ui.manager.Routes
import java.util.Locale

class HomeAppearance : Routes.Section() {
    private val locales by lazy {
        LocaleWrapper.fetchAvailableLocales(context.androidContext).toMutableList().apply {
            if (!contains(LocaleWrapper.DEFAULT_LOCALE)) add(0, LocaleWrapper.DEFAULT_LOCALE)
        }
    }

    private fun getLocaleDisplayName(locale: String): String {
        return if (locale == LocaleWrapper.DEFAULT_LOCALE) {
            context.translation["setup.dialogs.select_language"]
        } else {
            locale.split("_").let {
                if (it.size > 1) Locale(it[0], it[1]).getDisplayName(Locale(it[0], it[1]))
                else Locale(it[0]).getDisplayName(Locale(it[0]))
            }
        }
    }

    override val content: @Composable (NavBackStackEntry) -> Unit = {
        var showLocaleSheet by remember { mutableStateOf(false) }
        var selectedLocale by remember {
            mutableStateOf(context.config.locale)
        }

        if (showLocaleSheet) {
            LazyColumnBottomSheet(
                onDismiss = { showLocaleSheet = false }
            ) {
                item {
                    Text(
                        text = context.translation["setup.dialogs.select_language"],
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )
                }
                itemsIndexed(locales) { index, locale ->
                    fun select() {
                        selectedLocale = locale
                        context.config.locale = locale
                        context.config.writeConfig()
                        context.translation.reload(locale)
                        showLocaleSheet = false
                    }
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        shape = cardShape(locales.size, index)
                    ) {
                        ListItem(
                            modifier = Modifier.clickable { select() },
                            headlineContent = { Text(text = getLocaleDisplayName(locale)) },
                            trailingContent = {
                                RadioButton(
                                    selected = selectedLocale == locale,
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
                            routes.homeAppTheme.navigate()
                        },
                        leadingContent = {
                            Icon(Icons.Rounded.ColorLens, contentDescription = null)
                        },
                        headlineContent = {
                            Text(text = "App Theme", fontWeight = FontWeight.SemiBold)
                        },
                        supportingContent = {
                            Text(text = "Customize color palette, dark mode, and AMOLED mode", fontSize = 12.sp)
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
                        modifier = Modifier.clickable {
                            showLocaleSheet = true
                        },
                        leadingContent = {
                            Icon(Icons.Rounded.Language, contentDescription = null)
                        },
                        headlineContent = {
                            Text(text = "Language", fontWeight = FontWeight.SemiBold)
                        },
                        supportingContent = {
                            Text(text = getLocaleDisplayName(selectedLocale), fontSize = 12.sp)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}
