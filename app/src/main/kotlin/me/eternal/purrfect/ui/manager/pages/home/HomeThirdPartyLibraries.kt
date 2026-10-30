package me.eternal.purrfect.ui.manager.pages.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import me.eternal.purrfect.common.ui.cardShape
import me.eternal.purrfect.common.ui.screenContentPadding
import me.eternal.purrfect.ui.manager.Routes

class HomeThirdPartyLibraries : Routes.Section() {
    private val libraries = listOf(
        "Jetpack Compose & Material 3" to "Apache-2.0",
        "Kotlin Coroutines & Gson" to "Apache-2.0",
        "OkHttp & Coil" to "Apache-2.0",
        "OSMDroid" to "Apache-2.0",
        "Rhino JS Engine" to "MPL-2.0",
        "Dobby / LSPosed" to "GPL-3.0"
    )

    override val content: @Composable (NavBackStackEntry) -> Unit = {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = screenContentPadding(),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            itemsIndexed(libraries) { index, (name, license) ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardShape(libraries.size, index)
                ) {
                    ListItem(
                        headlineContent = { Text(text = name, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(text = license, fontSize = 12.sp) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}
