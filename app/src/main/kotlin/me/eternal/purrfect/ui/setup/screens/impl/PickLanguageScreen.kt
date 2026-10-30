package me.eternal.purrfect.ui.setup.screens.impl

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.eternal.purrfect.common.bridge.wrapper.LocaleWrapper
import me.eternal.purrfect.common.ui.cardShape
import me.eternal.purrfect.common.ui.columnPadding
import me.eternal.purrfect.ui.setup.screens.SetupScreen
import java.util.Locale

class PickLanguageScreen : SetupScreen() {
    private val locales by lazy {
        LocaleWrapper.fetchAvailableLocales(context.androidContext).toMutableList().apply {
            if (!contains(LocaleWrapper.DEFAULT_LOCALE)) add(0, LocaleWrapper.DEFAULT_LOCALE)
        }
    }
    private var selectedLocale by mutableStateOf(LocaleWrapper.DEFAULT_LOCALE)

    override fun onLeave() {
        context.config.locale = selectedLocale
        context.config.writeConfig()
        context.reload()
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

    private fun setLanguage(locale: String) {
        selectedLocale = locale
        reloadTranslation(locale)
    }

    private fun reloadTranslation(selectedLanguage: String) {
        context.translation.reload(selectedLanguage, isSetup = true)
    }

    @Composable
    override fun Content() {
        LaunchedEffect(Unit) {
            allowNext(true)
        }
        DialogText(text = remember(selectedLocale) {
            context.translation["setup.dialogs.select_language"]
        })

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
                .padding(horizontal = columnPadding),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            itemsIndexed(locales) { index, locale ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardShape(locales.size, index)
                ) {
                    ListItem(
                        modifier = Modifier.clickable {
                            setLanguage(locale)
                        },
                        headlineContent = {
                            Text(text = getLocaleDisplayName(locale))
                        },
                        trailingContent = {
                            RadioButton(
                                selected = selectedLocale == locale,
                                onClick = { setLanguage(locale) }
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent
                        )
                    )
                }
            }
        }
    }
}
