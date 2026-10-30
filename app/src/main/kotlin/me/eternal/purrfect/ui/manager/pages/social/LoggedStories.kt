package me.eternal.purrfect.ui.manager.pages.social

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import me.eternal.purrfect.common.bridge.wrapper.LoggerWrapper
import me.eternal.purrfect.common.ui.cardShapeSingle
import me.eternal.purrfect.common.ui.screenContentPadding
import me.eternal.purrfect.ui.manager.Routes
import java.text.DateFormat
import java.util.Date

class LoggedStories : Routes.Section() {
    override val content: @Composable (NavBackStackEntry) -> Unit = { navBackStackEntry ->
        val userId = navBackStackEntry.arguments?.getString("id") ?: ""
        val stories = remember(userId) {
            val logger = LoggerWrapper(context.androidContext)
            logger.getStories(userId, from = Long.MAX_VALUE)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(screenContentPadding())
        ) {
            if (stories.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = context.translation["manager.sections.logged_stories.no_stories"],
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(140.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(stories.entries.toList()) { (timestamp, story) ->
                        ElevatedCard(
                            shape = cardShapeSingle,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp)),
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    text = story.url ?: "Story Media",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
