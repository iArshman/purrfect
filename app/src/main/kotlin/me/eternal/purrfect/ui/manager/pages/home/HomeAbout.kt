package me.eternal.purrfect.ui.manager.pages.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import me.eternal.purrfect.common.BuildConfig
import me.eternal.purrfect.common.ui.cardShape
import me.eternal.purrfect.common.ui.cardShapeSingle
import me.eternal.purrfect.common.ui.screenContentPadding
import me.eternal.purrfect.ui.manager.Routes

class HomeAbout : Routes.Section() {
    override val content: @Composable (NavBackStackEntry) -> Unit = {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = screenContentPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = cardShapeSingle
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = context.translation["app_name"],
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Commit: ${BuildConfig.GIT_HASH.take(7)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = cardShape(3, 0)
                    ) {
                        ListItem(
                            modifier = Modifier.clickable {
                                routes.homeCredits.navigate()
                            },
                            leadingContent = {
                                Icon(Icons.Rounded.Group, contentDescription = null)
                            },
                            headlineContent = {
                                Text(text = "Credits & Contributors", fontWeight = FontWeight.SemiBold)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }

                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = cardShape(3, 1)
                    ) {
                        ListItem(
                            modifier = Modifier.clickable {
                                routes.homeThirdPartyLibraries.navigate()
                            },
                            leadingContent = {
                                Icon(Icons.Rounded.LibraryBooks, contentDescription = null)
                            },
                            headlineContent = {
                                Text(text = "Open Source Libraries", fontWeight = FontWeight.SemiBold)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }

                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = cardShape(3, 2)
                    ) {
                        ListItem(
                            modifier = Modifier.clickable {
                                runCatching {
                                    context.androidContext.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse("https://github.com/eternal-purrfect/PurrfectSnap")
                                        ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                                    )
                                }
                            },
                            leadingContent = {
                                Icon(Icons.Rounded.Code, contentDescription = null)
                            },
                            headlineContent = {
                                Text(text = "Source Code Repository", fontWeight = FontWeight.SemiBold)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}
