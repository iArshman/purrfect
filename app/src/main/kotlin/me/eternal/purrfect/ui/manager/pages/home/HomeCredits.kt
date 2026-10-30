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

class HomeCredits : Routes.Section() {
    private val contributors = listOf(
        "SnapEnhance / SE-Extended Team" to "Core architecture & clean Material 3 UI",
        "Purrfect Contributors" to "Core enhancements & RexMods features"
    )

    override val content: @Composable (NavBackStackEntry) -> Unit = {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = screenContentPadding(),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            itemsIndexed(contributors) { index, (name, role) ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardShape(contributors.size, index)
                ) {
                    ListItem(
                        headlineContent = { Text(text = name, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(text = role, fontSize = 12.sp) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}
